package com.example.smartpantry.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.model.RecipeIngredient
import com.example.smartpantry.storage.local.AppDatabase
import com.example.smartpantry.storage.local.saveImageToInternalStorage
import com.example.smartpantry.storage.local.toEntity
import com.example.smartpantry.storage.local.toModel
import com.example.smartpantry.storage.remote.createRecipe
import com.example.smartpantry.storage.remote.createRecipeIngredients
import com.example.smartpantry.storage.remote.getRecipes
import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.storage.remote.updateRecipe as updateRecipeRemote
import com.example.smartpantry.storage.remote.deleteRecipe as deleteRecipeRemote
import com.example.smartpantry.storage.remote.uploadRecipeImage
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

data class CreateRecipeUiState(
    val recipeName: String = "",
    val category: String = "",
    val description: String = "",
    val isVegan: Boolean = false,
    val imagePath: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val canSubmit: Boolean get() = recipeName.isNotBlank() && !isSaving
}

class recipe_vm(app: Application) : AndroidViewModel(app) {

    private val dao by lazy { AppDatabase.get(app).recipeDao() }
    private val ingredientDao by lazy { AppDatabase.get(app).recipeIngredientDao() }

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    private val _uiState = MutableStateFlow(CreateRecipeUiState())
    val uiState: StateFlow<CreateRecipeUiState> = _uiState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { entities ->
                _recipes.value = entities.map { it.toModel() }
            }
        }
        syncPendingItems()
    }

    // --- Form Updaters ---
    fun onNameChange(v: String)        = _uiState.update { it.copy(recipeName = v) }
    fun onCategoryChange(v: String)    = _uiState.update { it.copy(category = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onVeganChange(v: Boolean)      = _uiState.update { it.copy(isVegan = v) }
    fun onImagePicked(path: String?)   = _uiState.update { it.copy(imagePath = path) }
    fun clearError()                   = _uiState.update { it.copy(error = null) }
    fun consumeSaved()                 = _uiState.update { it.copy(saved = false) }

    // --- 1. CREATE ---
    fun createFullRecipe(ingredients: List<RecipeIngredient> = emptyList()) {
        val s = _uiState.value
        if (s.recipeName.isBlank() || s.isSaving) return

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val currentUserId = supabase.auth.currentUserOrNull()?.id
                    ?: "facad5cd-98f6-4d8a-b4b5-cf13f0515e0e"

                val localPath = s.imagePath?.let { path ->
                    withContext(Dispatchers.IO) {
                        try {
                            if (path.startsWith("/data")) path
                            else saveImageToInternalStorage(getApplication(), Uri.parse(path))
                        } catch (e: Exception) {
                            Log.e("RecipeSync", "Local image save failed", e)
                            null
                        }
                    }
                }

                val newRecipe = Recipe(
                    recipeName = s.recipeName.trim(),
                    recipeCategory = s.category.takeIf { it.isNotEmpty() },
                    isVegan = s.isVegan,
                    description = s.description.takeIf { it.isNotEmpty() },
                    createAt = System.currentTimeMillis(),
                    localRecipeImage = localPath,
                    recipeImage = null,
                    userId = currentUserId
                )

                val localRecipeId = dao.insert(newRecipe.toEntity())

                if (ingredients.isNotEmpty()) {
                    val withId = ingredients.map { it.copy(recipeId = localRecipeId) }
                    ingredientDao.insertAll(withId.map { it.toEntity() })
                }

                _uiState.update { it.copy(isSaving = false, saved = true) }
                yield()
                _uiState.update {
                    it.copy(recipeName = "", category = "", description = "", isVegan = false, imagePath = null)
                }

                uploadRecipeToSupabase(localRecipeId)
            } catch (e: Exception) {
                Log.e("RecipeSync", "Save failed", e)
                _uiState.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }

    // --- 2. UPDATE ---
    fun updateRecipe(recipeId: Long, updated: Recipe) {
        viewModelScope.launch {
            try {
                val existing = dao.getById(recipeId) ?: return@launch

                // Resolve new image: content:// -> local file; /data -> keep; null -> keep
                val finalLocalPath = when {
                    updated.localRecipeImage == null -> existing.localRecipeImage
                    updated.localRecipeImage.startsWith("/data") -> updated.localRecipeImage
                    else -> withContext(Dispatchers.IO) {
                        try {
                            saveImageToInternalStorage(
                                getApplication(),
                                Uri.parse(updated.localRecipeImage)
                            )
                        } catch (e: Exception) {
                            Log.e("RecipeSync", "Edit: local image save failed", e)
                            existing.localRecipeImage
                        }
                    }
                }

                val imageChanged = finalLocalPath != existing.localRecipeImage

                val merged = updated.copy(
                    recipeId = recipeId,
                    userId = existing.userId,
                    createAt = existing.createAt,
                    localRecipeImage = finalLocalPath,
                    recipeImage = if (imageChanged) null else existing.recipeImage
                )

                // Local first — Flow re-emits, UI updates instantly
                dao.update(merged.toEntity())

                // Remote push in background
                pushRecipeUpdate(recipeId)
            } catch (e: Exception) {
                Log.e("RecipeSync", "Update failed", e)
            }
        }
    }

    private fun pushRecipeUpdate(localRecipeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(localRecipeId) ?: return@launch
                var remoteUrl = entity.recipeImage
                val localPath = entity.localRecipeImage

                if (remoteUrl == null && localPath != null) {
                    val file = File(localPath)
                    if (file.exists()) {
                        remoteUrl = uploadRecipeImage(Uri.fromFile(file), getApplication())
                        dao.update(entity.copy(recipeImage = remoteUrl))
                    }
                }

                val finalModel = entity.toModel().copy(recipeImage = remoteUrl)
                updateRecipeRemote(localRecipeId, finalModel)
            } catch (e: Exception) {
                Log.e("RecipeSync", "Remote update failed (offline?)", e)
            }
        }
    }

    // --- 3. DELETE ---
    fun deleteRecipe(recipeId: Long) {
        viewModelScope.launch {
            try {
                val existing = dao.getById(recipeId)
                dao.deleteById(recipeId)                    // Room Flow emits → row gone
                if (existing != null) deleteRecipeRemote(recipeId)
            } catch (e: Exception) {
                Log.e("RecipeSync", "Delete failed", e)
            }
        }
    }

    // --- 4. Background upload (create) ---
    private fun uploadRecipeToSupabase(localRecipeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(localRecipeId) ?: return@launch
                val localPath = entity.localRecipeImage
                var remoteUrl = entity.recipeImage

                if (remoteUrl == null && localPath != null) {
                    val file = File(localPath)
                    if (file.exists()) {
                        remoteUrl = uploadRecipeImage(Uri.fromFile(file), getApplication())
                    }
                }

                val updatedModel = entity.toModel().copy(recipeImage = remoteUrl)
                dao.insert(updatedModel.toEntity())

                val savedRemoteRecipe = createRecipe(updatedModel)
                val remoteId = savedRemoteRecipe.recipeId ?: return@launch

                val localIngredients = ingredientDao.getIngredientsForRecipe(localRecipeId).first()
                if (localIngredients.isNotEmpty()) {
                    val remoteIngredients = localIngredients.map { e ->
                        e.toModel().copy(id = null, recipeId = remoteId)
                    }
                    createRecipeIngredients(remoteIngredients)
                }
            } catch (e: Exception) {
                Log.e("RecipeSync", "Background upload failed", e)
            }
        }
    }

    // --- 5. Sync pending images ---
    fun syncPendingItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val all = dao.observeAll().first()
                val pending = all.filter { it.localRecipeImage != null && it.recipeImage == null }
                for (e in pending) uploadRecipeToSupabase(e.id)
            } catch (e: Exception) {
                Log.e("RecipeSync", "Pending sync failed", e)
            }
        }
    }

    // --- 6. Refresh from Supabase ---
    fun refreshRecipes(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val remote = getRecipes()
                if (remote.isNotEmpty()) {
                    dao.insertAll(remote.map { it.toEntity() })
                }
            } catch (e: Exception) {
                Log.e("RecipeSync", "Refresh failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}