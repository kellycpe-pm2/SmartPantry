package com.example.smartpantry.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.local.AppDatabase
import com.example.smartpantry.storage.local.toEntity
import com.example.smartpantry.storage.local.toModel
import com.example.smartpantry.storage.remote.createIngredient
import com.example.smartpantry.storage.remote.getIngredients
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

class IngredientViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).ingredientDao()

    private val _ingredients = MutableStateFlow<List<Ingredient>>(emptyList())
    val ingredients: StateFlow<List<Ingredient>> = _ingredients.asStateFlow()

    private val _uiState = MutableStateFlow(CreateIngredientUiState())
    val uiState: StateFlow<CreateIngredientUiState> = _uiState.asStateFlow()

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

    // ---- Field updaters ----
    fun onNameChange(v: String)        = _uiState.update { it.copy(ingredientName = v) }
    fun onCategoryChange(v: String)    = _uiState.update { it.copy(category = v) }
    fun onQuantityChange(v: String)    = _uiState.update { it.copy(quantityText = v) }
    fun onUnitChange(v: String)        = _uiState.update { it.copy(unit = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onMinExpiryChange(v: String)   = _uiState.update { it.copy(minExpiryDays = v) }
    fun onMaxExpiryChange(v: String)   = _uiState.update { it.copy(maxExpiryDays = v) }
    fun onImagePicked(path: String?)   = _uiState.update { it.copy(imagePath = path) }
    fun clearError()                   = _uiState.update { it.copy(error = null) }

    /** Call after success navigation so the screen doesn't pop twice. */
    fun consumeSaved() = _uiState.update { it.copy(saved = false) }

    // ---- Create ----
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
                dao.insert(ingredient.toEntity())
                try {
                    createIngredient(ingredient)
                } catch (remote: Exception) {
                    android.util.Log.w("CreateIngredient", "Remote sync failed", remote)
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saved = true,
                        ingredientName = "",
                        category = "",
                        quantityText = "",
                        unit = "",
                        description = "",
                        imagePath = null,
                        minExpiryDays = "",
                        maxExpiryDays = ""
                    )
                }
            } catch (local: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save: ${local.message}")
                }
            }
        }
    }

    // ---- Refresh ----
    fun refreshIngredients() {
        viewModelScope.launch {
            try {
                val remote = getIngredients()
                dao.clear()
                remote.forEach { dao.insert(it.toEntity()) }
                android.util.Log.d("PantryRefresh", "Refreshed ${remote.size} rows")
            } catch (e: Exception) {
                android.util.Log.w("PantryRefresh", "Refresh failed: ${e.message}", e)
            }
        }
    }
}