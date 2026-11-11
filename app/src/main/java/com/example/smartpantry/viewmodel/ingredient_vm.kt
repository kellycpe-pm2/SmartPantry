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
import com.example.smartpantry.storage.remote.createIngredient
import com.example.smartpantry.storage.remote.getIngredients
import com.example.smartpantry.storage.remote.uploadIngredientImage
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

    init {
        viewModelScope.launch {
            dao.observeAll().collect { entities ->
                _ingredients.value = entities.map { it.toModel() }
            }
        }
        syncPendingImages()
    }

    // --- Form Updaters ---
    fun onNameChange(v: String)        = _uiState.update { it.copy(ingredientName = v) }
    fun onCategoryChange(v: String)    = _uiState.update { it.copy(category = v) }
    fun onQuantityChange(v: String)    = _uiState.update { it.copy(quantityText = v) }
    fun onUnitChange(v: String)        = _uiState.update { it.copy(unit = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onMinExpiryChange(v: String)   = _uiState.update { it.copy(minExpiryDays = v) }
    fun onMaxExpiryChange(v: String)   = _uiState.update { it.copy(maxExpiryDays = v) }
    fun onImagePicked(path: String?)   = _uiState.update { it.copy(imagePath = path) }
    fun clearError()                   = _uiState.update { it.copy(error = null) }
    fun consumeSaved()                 = _uiState.update { it.copy(saved = false) }

    // --- 1. SAVE LOCALLY (Works 100% Offline) ---
    fun create() {
        val s = _uiState.value
        if (s.ingredientName.isBlank() || s.isSaving) return

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                // Save image permanently (downscaled)
                val persistedPath = s.imagePath?.let { path ->
                    withContext(Dispatchers.IO) {
                        try {
                            if (path.startsWith("/data")) path
                            else saveImageToInternalStorage(getApplication(), Uri.parse(path))
                        } catch (e: Exception) {
                            null
                        }
                    }

                }

                // Build the model (Remote URL is null for now)
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
                    updateAt = System.currentTimeMillis()
                )

                // Save to Room
                val localId = dao.insert(ingredient.toEntity())

                // Reset UI immediately (User sees success instantly)
                _uiState.update { it.copy(isSaving = false, saved = true) }
                yield()
                _uiState.update {
                    it.copy(
                        ingredientName = "", category = "", quantityText = "",
                        unit = "", description = "", imagePath = null,
                        minExpiryDays = "", maxExpiryDays = ""
                    )
                }

                // Upload to Supabase in the background
                uploadIngredientToSupabase(localId)

            } catch (local: Exception) {
                Log.e("IngredientSync", "Local save failed", local)
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save: ${local.message}")
                }
            }
        }
    }

    // --- 2. BACKGROUND UPLOAD (Runs silently) ---
    private fun uploadIngredientToSupabase(localId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(localId) ?: return@launch
                val localPath = entity.localImagePath
                var remoteUrl = entity.image

                // Upload image if it exists locally and we don't have a remote URL yet
                if (remoteUrl == null && localPath != null) {
                    val file = File(localPath)
                    if (file.exists()) {
                        remoteUrl = uploadIngredientImage(Uri.fromFile(file), getApplication())
                    }
                }

                // Update Room with the new remote URL
                val updatedModel = entity.toModel().copy(image = remoteUrl)
                dao.insert(updatedModel.toEntity())

                // Push the full ingredient to Supabase Database
                createIngredient(updatedModel)

            } catch (e: Exception) {
                Log.e("IngredientSync", "Background upload failed (probably offline)", e)
            }
        }
    }

    // --- 3. AUTO SYNC MISSING IMAGES ---
    fun syncPendingImages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allIngredients = dao.observeAll().first()
                val pending = allIngredients.filter { it.localImagePath != null && it.image == null }
                for (entity in pending) {
                    uploadIngredientToSupabase(entity.id)
                }
            } catch (e: Exception) {
                Log.e("IngredientSync", "Pending sync failed", e)
            }
        }
    }

    // --- 4. REFRESH FROM SUPABASE ---
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
}