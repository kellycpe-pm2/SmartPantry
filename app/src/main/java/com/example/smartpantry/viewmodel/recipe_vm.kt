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
        // Observe local database
        viewModelScope.launch {
            dao.observeAll().collect { entities ->
                _recipes.value = entities.map { it.toModel() }
            }
        }
        // Sync on launch
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

    // --- 1. SAVE LOCALLY (Works 100% Offline) ---
    fun createFullRecipe(ingredients: List<RecipeIngredient> = emptyList()) {
        val s = _uiState.value
        if (s.recipeName.isBlank() || s.isSaving) return

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val currentUserId = supabase.auth.currentUserOrNull()?.id
                    ?: "facad5cd-98f6-4d8a-b4b5-cf13f0515e0e"

                // Save Image Locally
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

                // Build the local model
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

                // Save to Room
                val localRecipeId = dao.insert(newRecipe.toEntity())

                // Save Ingredients to Room
                if (ingredients.isNotEmpty()) {
                    val ingredientsWithId = ingredients.map { it.copy(recipeId = localRecipeId) }
                    ingredientDao.insertAll(ingredientsWithId.map { it.toEntity() })
                }

                // Reset UI immediately
                _uiState.update { it.copy(isSaving = false, saved = true) }
                yield()
                _uiState.update {
                    it.copy(recipeName = "", category = "", description = "", isVegan = false, imagePath = null)
                }

                // Upload to Supabase in background
                uploadRecipeToSupabase(localRecipeId)

            } catch (e: Exception) {
                Log.e("RecipeSync", "Save failed", e)
                _uiState.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }

    // --- 2. BACKGROUND UPLOAD ---
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

                // Push ingredients to Supabase
                val localIngredients = ingredientDao.getIngredientsForRecipe(localRecipeId).first()
                if (localIngredients.isNotEmpty()) {
                    val remoteIngredients = localIngredients.map { entity ->
                        entity.toModel().copy(id = null, recipeId = remoteId)
                    }
                    createRecipeIngredients(remoteIngredients)
                }
            } catch (e: Exception) {
                Log.e("RecipeSync", "Background upload failed (probably offline)", e)
            }
        }
    }

    // --- 3. AUTO SYNC MISSING IMAGES ---
    fun syncPendingItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allRecipes = dao.observeAll().first()
                val pending = allRecipes.filter { it.localRecipeImage != null && it.recipeImage == null }
                for (entity in pending) {
                    uploadRecipeToSupabase(entity.id)
                }
            } catch (e: Exception) {
                Log.e("RecipeSync", "Pending sync failed", e)
            }
        }
    }

    // --- 4. REFRESH FROM SUPABASE ---
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