package com.example.smartpantry.viewmodel

import android.app.Application
import android.content.ContentValues.TAG
import android.net.Uri
import android.util.Log
import android.util.Log.d
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.local.AppDatabase
import com.example.smartpantry.storage.local.saveImageToInternalStorage
import com.example.smartpantry.storage.local.toEntity
import com.example.smartpantry.storage.local.toModel
import com.example.smartpantry.storage.remote.createIngredient
import com.example.smartpantry.storage.remote.getIngredients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

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

    // Loading state for the pull-to-refresh UI
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Throttle to prevent 429 errors (5 minutes)
    private var lastSyncTime = 0L
    private val syncIntervalMs = 5 * 60 * 1000L

    init {
        loadIngredients()
    }

    private fun loadIngredients() {
        viewModelScope.launch {
            dao.observeAll().collect { entities ->
                _ingredients.value = entities.map { it.toModel() }
            }
        }
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

    // --- Create Ingredient ---
    fun create() {
        val s = _uiState.value
        if (s.ingredientName.isBlank() || s.isSaving) return

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                // 1. Save image (Camera or Gallery) permanently to internal storage
                val persistedPath = s.imagePath?.let { path ->
                    withContext(Dispatchers.IO) {
                        try {
                            if (path.startsWith("/data")) path
                            else saveImageToInternalStorage(getApplication(), Uri.parse(path))
                        } catch (e: Exception) {
                            android.util.Log.e("CreateIngredient", "Image copy failed", e)
                            null
                        }
                    }
                }

                // 2. Build the model
                val ingredient = Ingredient(
                    ingredientName = s.ingredientName.trim(),
                    category = s.category.takeIf { it.isNotEmpty() },
                    weight = s.quantityText.toDoubleOrNull(),
                    unit = s.unit.takeIf { it.isNotEmpty() },
                    description = s.description.takeIf { it.isNotEmpty() },
                    image = persistedPath,
                    minExpiredDuration = s.minExpiryDays.toShortOrNull(),
                    maxExpiredDuration = s.maxExpiryDays.toShortOrNull(),
                    createAt = System.currentTimeMillis(),
                    updateAt = System.currentTimeMillis()
                )

                // 3. Save locally to Room
                dao.insert(ingredient.toEntity())

                // 4. Sync to Supabase (best effort)
                try { createIngredient(ingredient) } catch (_: Exception) {}

                // 5. Reset UI
                _uiState.update { it.copy(isSaving = false, saved = true) }
                yield()
                _uiState.update {
                    it.copy(
                        ingredientName = "", category = "", quantityText = "",
                        unit = "", description = "", imagePath = null,
                        minExpiryDays = "", maxExpiryDays = ""
                    )
                }
            } catch (local: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save: ${local.message}")
                }
            }
        }
    }

    fun refreshIngredients(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastSyncTime) < syncIntervalMs) {
            d(TAG, "Skipped — throttled (5 min)")
            return
        }
        if (_isRefreshing.value) return

        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                d(TAG, "→ Calling getIngredients()")
                val remote = getIngredients()
                d(TAG, "← Received ${remote.size} rows from Supabase")

                if (remote.isNotEmpty()) {
                    dao.insertAll(remote.map { it.toEntity() })
                    d(TAG, "✅ Merged ${remote.size} rows into Room")
                } else {
                    Log.w(TAG, "⚠ Empty remote — local data untouched")
                }
                lastSyncTime = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}