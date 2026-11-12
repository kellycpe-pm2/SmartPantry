package com.example.smartpantry.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.IngredientUser
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.local.AppDatabase
import com.example.smartpantry.storage.local.toEntity
import com.example.smartpantry.storage.local.toModel
import com.example.smartpantry.storage.remote.createIngredientUser
import com.example.smartpantry.storage.remote.getIngredientUsers
import com.example.smartpantry.storage.remote.getIngredients
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.map

class IngredientUserViewModel(app: Application) : AndroidViewModel(app) {

    private val dao by lazy { AppDatabase.get(app).ingredientUserDao() }
    private val ingredientDao by lazy { AppDatabase.get(app).ingredientDao() }

    private val _inventory = MutableStateFlow<List<IngredientUser>>(emptyList())
    val inventory: StateFlow<List<IngredientUser>> = _inventory.asStateFlow()

    private val _allIngredients = MutableStateFlow<List<Ingredient>>(emptyList())
    val allIngredients: StateFlow<List<Ingredient>> = _allIngredients.asStateFlow()

    private val _stagedItems = MutableStateFlow<List<IngredientUser>>(emptyList())
    val stagedItems: StateFlow<List<IngredientUser>> = _stagedItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { entities ->
                _inventory.value = entities.map { it.toModel() }
            }
        }
        viewModelScope.launch {
            ingredientDao.observeAll().collect { entities ->
                _allIngredients.value = entities.map { it.toModel() }
            }
        }
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val remoteIngredients = getIngredients()
                if (remoteIngredients.isNotEmpty()) {
                    ingredientDao.insertAll(remoteIngredients.map { it.toEntity() })
                }
            } catch (e: Exception) {
                Log.e("Inventory", "Failed to sync ingredients", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- STAGING ITEMS (Now includes userId) ---
    fun stageItem(
        ingredientId: Long,
        qty: Double,
        unit: String,
        location: String,
        expiredDate: Long,
        userId: String?
    ) {
        val newItem = IngredientUser(
            ingredientId = ingredientId,
            quantity = qty,
            quantityUnit = unit,
            locationStorage = location,
            expiredDate = expiredDate,
            userId = userId // 👈 Set it here
        )
        _stagedItems.update { it + newItem }
    }

    fun removeStagedItem(item: IngredientUser) {
        _stagedItems.update { it - item }
    }

    // --- SAVE ALL STAGED ITEMS ---
    fun saveAllToInventory() {
        val itemsToSave = _stagedItems.value
        if (itemsToSave.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entities = itemsToSave.map { it.toEntity() }
                dao.insertAll(entities) // Save to Room

                // Push to Supabase
                for (item in itemsToSave) {
                    try {
                        createIngredientUser(item)
                    } catch (e: Exception) {
                        Log.e("Inventory", "Supabase push failed for item", e)
                    }
                }

                // Clear staging area
                _stagedItems.value = emptyList()
            } catch (e: Exception) {
                Log.e("Inventory", "Failed to save items locally", e)
            }
        }
    }

    // --- CREATE NEW INGREDIENT ---
    fun createNewIngredient(name: String, category: String, weight: Double, unit: String, callback: (Ingredient) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newIngredient = Ingredient(
                    ingredientName = name,
                    category = category,
                    weight = weight,
                    unit = unit,
                    createAt = System.currentTimeMillis(),
                    updateAt = System.currentTimeMillis()
                )

                val localId = ingredientDao.insert(newIngredient.toEntity())
                val savedIngredient = newIngredient.copy(ingredientId = localId)

                try {
                    com.example.smartpantry.storage.remote.createIngredient(savedIngredient)
                } catch (e: Exception) {
                    Log.e("Inventory", "Failed to sync new ingredient to Supabase", e)
                }

                withContext(Dispatchers.Main) {
                    callback(savedIngredient)
                }
            } catch (e: Exception) {
                Log.e("Inventory", "Failed to create ingredient", e)
            }
        }
    }

    fun refreshInventory(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val remote = getIngredientUsers()
                if (remote.isNotEmpty()) {
                    dao.insertAll(remote.map { it.toEntity() })
                }
            } catch (e: Exception) {
                Log.e("Inventory", "Refresh failed", e)
            }
        }
    }

    fun deleteInventoryItem(id: Long) {
        viewModelScope.launch {
            try {
                // 1. Delete Locally from Room
                dao.deleteById(id)

                // 2. Delete Remotely from Supabase
                try {
                    supabase.from("Ingredient_User").delete {
                        filter {
                            eq("IngredientUser_Id", id)
                        }
                    }
                } catch (e: Exception) {
                }
            } catch (e: Exception) {
            }
        }
    }
}