package com.example.smartpantry.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.local.AppDatabase
import com.example.smartpantry.storage.local.IngredientDao
import com.example.smartpantry.storage.local.toEntity
import com.example.smartpantry.storage.remote.createIngredient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class CreateIngredientViewModel(
    app: Application,
    private val dao: IngredientDao = AppDatabase.get(app).ingredientDao()
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(CreateIngredientUiState())
    val uiState: StateFlow<CreateIngredientUiState> = _uiState.asStateFlow()

    // field updaters (unchanged)
    fun onNameChange(v: String)        = _uiState.update { it.copy(ingredientName = v) }
    fun onCategoryChange(v: String)    = _uiState.update { it.copy(category = v) }
    fun onQuantityChange(v: String)    = _uiState.update { it.copy(quantityText = v) }
    fun onUnitChange(v: String)        = _uiState.update { it.copy(unit = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onMinExpiryChange(v: String)   = _uiState.update { it.copy(minExpiryDays = v) }
    fun onMaxExpiryChange(v: String)   = _uiState.update { it.copy(maxExpiryDays = v) }
    fun onImagePicked(path: String?)   = _uiState.update { it.copy(imagePath = path) }
    fun clearError()                   = _uiState.update { it.copy(error = null) }

    fun create() {
        val s = _uiState.value
        if (s.ingredientName.isBlank() || s.isSaving) return

        val ingredient = Ingredient(
            ingredientName = s.ingredientName.trim(),
            category = s.category.takeIf { it.isNotEmpty() },
            weight = s.quantityText.toDoubleOrNull(),
            unit = s.unit.takeIf { it.isNotEmpty() },
            description = s.description.takeIf { it.isNotEmpty() },
            image = s.imagePath,
            minExpiredDuration = s.minExpiryDays.toShortOrNull(),
            maxExpiredDuration = s.maxExpiryDays.toShortOrNull(),
            createAt = System.currentTimeMillis(),
            updateAt = System.currentTimeMillis()
        )

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                // 1. Save to local Room DB first (offline-first)
                dao.insert(ingredient.toEntity())

                // 2. Try to sync to Supabase
                try {
                    createIngredient(ingredient)
                } catch (remote: Exception) {
                    // Local save succeeded; remote failed → keep it, retry later
                    android.util.Log.w("CreateIngredient", "Remote sync failed", remote)
                }

                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (local: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save: ${local.message}")
                }
            }
        }
    }
}