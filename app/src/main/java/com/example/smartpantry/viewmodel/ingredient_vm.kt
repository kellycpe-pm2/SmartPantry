package com.example.smartpantry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.remote.createIngredient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** UI state for the Create Ingredient screen. */
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
    val canSubmit: Boolean
        get() = ingredientName.isNotBlank() && !isSaving
}

class ingredient_vm : ViewModel() {

    private val _uiState = MutableStateFlow(CreateIngredientUiState())
    val uiState: StateFlow<CreateIngredientUiState> = _uiState.asStateFlow()

    // --- field updaters ---
    fun onNameChange(v: String)          = _uiState.update { it.copy(ingredientName = v) }
    fun onCategoryChange(v: String)      = _uiState.update { it.copy(category = v) }
    fun onQuantityChange(v: String)      = _uiState.update { it.copy(quantityText = v) }
    fun onUnitChange(v: String)          = _uiState.update { it.copy(unit = v) }
    fun onDescriptionChange(v: String)   = _uiState.update { it.copy(description = v) }
    fun onMinExpiryChange(v: String)     = _uiState.update { it.copy(minExpiryDays = v) }
    fun onMaxExpiryChange(v: String)     = _uiState.update { it.copy(maxExpiryDays = v) }
    fun onImagePicked(path: String?)     = _uiState.update { it.copy(imagePath = path) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun create() {
        val s = _uiState.value
        if (!s.canSubmit) return

        val newIngredient = Ingredient(
            ingredientName = s.ingredientName.trim(),
            category = s.category.takeIf { it.isNotEmpty() },
            weight = s.quantityText.toDoubleOrNull() as BigDecimal? as Double?,
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
                createIngredient(newIngredient)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Failed to save ingredient")
                }
            }
        }
    }
}