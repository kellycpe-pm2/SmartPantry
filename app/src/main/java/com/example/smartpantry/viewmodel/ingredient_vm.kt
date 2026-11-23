package com.example.smartpantry.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.local.AppDatabase
import com.example.smartpantry.storage.local.saveImageToInternalStorage
import com.example.smartpantry.storage.local.toEntity
import com.example.smartpantry.storage.local.toModel
import com.example.smartpantry.storage.remote.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

data class CreateIngredientUiState(
    val ingredientName: String = "",
    val category: String = "",
    val quantityText: String = "",
    val unit: String = "",
    val description: String = "",
    val imagePath: String? = null,
    val minExpiryDays: String = "",
    val maxExpiryDays: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val canSubmit: Boolean get() = ingredientName.isNotBlank() && !isSaving
}

class IngredientViewModel(app: Application) : AndroidViewModel(app) {
    private val dao by lazy { AppDatabase.get(app).ingredientDao() }

    private val _ingredients = MutableStateFlow<List<Ingredient>>(emptyList())
    val ingredients: StateFlow<List<Ingredient>> = _ingredients.asStateFlow()

    private val _uiState = MutableStateFlow(CreateIngredientUiState())
    val uiState: StateFlow<CreateIngredientUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var lastSyncTime = 0L
    private val syncIntervalMs = 5 * 60 * 1000L
    private var publishOnCreate: Boolean = false

    init {
        viewModelScope.launch {
            dao.observeAll().collect { entities ->
                _ingredients.value = entities.map { it.toModel() }
            }
        }
        syncPendingImages()
    }

    // --- Form Updaters ---
    fun onNameChange(v: String) = _uiState.update { it.copy(ingredientName = v) }
    fun onCategoryChange(v: String) = _uiState.update { it.copy(category = v) }
    fun onQuantityChange(v: String) = _uiState.update { it.copy(quantityText = v) }
    fun onUnitChange(v: String) = _uiState.update { it.copy(unit = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onMinExpiryChange(v: String) = _uiState.update { it.copy(minExpiryDays = v) }
    fun onMaxExpiryChange(v: String) = _uiState.update { it.copy(maxExpiryDays = v) }
    fun onImagePicked(path: String?) = _uiState.update { it.copy(imagePath = path) }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun consumeSaved() = _uiState.update { it.copy(saved = false) }
    fun setPublishOnCreate(publish: Boolean) { publishOnCreate = publish }

    private fun currentUserId(): String? =
        try { supabase.auth.currentUserOrNull()?.id } catch (_: Exception) { null }

    // ==========================================
    // CREATE
    // ==========================================
    fun create() {
        val s = _uiState.value
        if (s.ingredientName.isBlank() || s.isSaving) return

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                // 1. Persist image locally (if picked)
                val persistedPath = s.imagePath?.let { path ->
                    withContext(Dispatchers.IO) {
                        try {
                            if (path.startsWith("/data")) path
                            else saveImageToInternalStorage(getApplication(), Uri.parse(path))
                        } catch (e: Exception) {
                            Log.e("IngredientSync", "Image save failed", e)
                            null
                        }
                    }
                }

                // 2. Build model
                val ingredient = Ingredient(
                    ingredientName = s.ingredientName.trim(),
                    category = s.category.takeIf { it.isNotEmpty() },
                    weight = s.quantityText.toDoubleOrNull(),
                    unit = s.unit.takeIf { it.isNotEmpty() },
                    description = s.description.takeIf { it.isNotEmpty() },
                    image = null,
                    localImagePath = persistedPath,
                    minExpiredDuration = s.minExpiryDays.toShortOrNull(),
                    maxExpiredDuration = s.maxExpiryDays.toShortOrNull(),
                    createAt = System.currentTimeMillis(),
                    updateAt = System.currentTimeMillis(),
                    isPublish = publishOnCreate,
                    userId = currentUserId()
                )

                // 3. Save to Room
                val localId = dao.insert(ingredient.toEntity())
                Log.d("IngredientSync", "Saved locally: id=$localId, publish=$publishOnCreate, image=$persistedPath")

                // 4. Reset form
                _uiState.update { it.copy(isSaving = false, saved = true) }
                yield()
                _uiState.update {
                    it.copy(
                        ingredientName = "", category = "", quantityText = "",
                        unit = "", description = "", imagePath = null,
                        minExpiryDays = "", maxExpiryDays = ""
                    )
                }

                // 5. Upload image ALWAYS (even for drafts) — only data is publish-gated
                uploadImageOnly(localId)

                // 6. If publish, push full data to Supabase
                if (publishOnCreate) {
                    publishIngredientToSupabase(localId)
                }
            } catch (e: Exception) {
                Log.e("IngredientSync", "Save failed", e)
                _uiState.update { it.copy(isSaving = false, error = "Failed: ${e.message}") }
            }
        }
    }

    // ==========================================
    // UPLOAD IMAGE ONLY (runs for both draft & published)
    // ==========================================
    private fun uploadImageOnly(localId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(localId) ?: return@launch
                val localPath = entity.localImagePath ?: return@launch
                if (entity.image != null) return@launch  // already uploaded

                val file = File(localPath)
                if (!file.exists()) {
                    Log.e("IngredientSync", "Local image file missing: $localPath")
                    return@launch
                }

                Log.d("IngredientSync", "Uploading image: ${file.length()} bytes")
                val remoteUrl = uploadIngredientImage(Uri.fromFile(file), getApplication())
                Log.d("IngredientSync", "Image uploaded: $remoteUrl")

                if (remoteUrl != null) {
                    val updated = entity.toModel().copy(image = remoteUrl)
                    dao.insert(updated.toEntity())
                }
            } catch (e: Exception) {
                Log.e("IngredientSync", "Image upload failed", e)
            }
        }
    }

    // ==========================================
    // PUBLISH FULL INGREDIENT (data to Supabase)
    // ==========================================
    private fun publishIngredientToSupabase(localId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(localId) ?: return@launch
                val model = entity.toModel().copy(isPublish = true)
                dao.insert(model.toEntity())
                createIngredient(model)
                Log.d("IngredientSync", "Published to Supabase: id=$localId")
            } catch (e: Exception) {
                Log.e("IngredientSync", "Publish failed", e)
            }
        }
    }

    // ==========================================
    // SYNC PENDING IMAGES (retry on startup)
    // ==========================================
    fun syncPendingImages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val all = dao.observeAll().first()
                // Retry any ingredient that has a local image but no remote URL
                all.filter { it.localImagePath != null && it.image == null }
                    .forEach { uploadImageOnly(it.id) }
            } catch (e: Exception) {
                Log.e("IngredientSync", "Pending sync failed", e)
            }
        }
    }

    // ==========================================
    // REFRESH
    // ==========================================
    fun refreshIngredients(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastSyncTime) < syncIntervalMs) return
        if (_isRefreshing.value) return

        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val remote = getIngredients()
                if (remote.isNotEmpty()) {
                    dao.insertAll(remote.map { it.toEntity() })
                }
                lastSyncTime = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e("IngredientSync", "Refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ==========================================
    // UPDATE
    // ==========================================
    fun updateIngredient(id: Long, updated: Ingredient) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.insert(updated.copy(ingredientId = id).toEntity())
                com.example.smartpantry.storage.remote.updateIngredient(id, updated)
                refreshIngredients(force = true)
            } catch (e: Exception) {
                Log.e("IngredientSync", "Update failed", e)
            }
        }
    }

    // ==========================================
    // DELETE
    // ==========================================
    fun deleteIngredient(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.deleteById(id)
                com.example.smartpantry.storage.remote.deleteIngredient(id)
                refreshIngredients(force = true)
            } catch (e: Exception) {
                Log.e("IngredientSync", "Delete failed", e)
            }
        }
    }

    // ==========================================
    // TOGGLE PUBLISH (Draft <-> Published)
    // ==========================================
    fun togglePublish(id: Long, ing: Ingredient) {
        viewModelScope.launch(Dispatchers.IO) {
            val newState = !(ing.isPublish ?: false)
            val updated = ing.copy(isPublish = newState, updateAt = System.currentTimeMillis())
            dao.insert(updated.copy(ingredientId = id).toEntity())

            if (newState) {
                // Publishing: upload image if not yet, then push data
                uploadImageOnly(id)
                try {
                    createIngredient(updated.copy(ingredientId = id))
                } catch (e: Exception) {
                    Log.e("IngredientSync", "Publish failed", e)
                }
            } else {
                // Unpublishing: delete from Supabase
                try {
                    com.example.smartpantry.storage.remote.deleteIngredient(id)
                } catch (e: Exception) {
                    Log.e("IngredientSync", "Unpublish failed", e)
                }
            }
        }
    }
}