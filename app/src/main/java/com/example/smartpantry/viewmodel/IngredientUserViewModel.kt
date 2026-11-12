package com.example.smartpantry.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.IngredientUser
import com.example.smartpantry.storage.local.AppDatabase
import com.example.smartpantry.storage.local.toEntity
import com.example.smartpantry.storage.local.toModel
import com.example.smartpantry.storage.remote.createIngredientUser
import com.example.smartpantry.storage.remote.getIngredientUsers
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IngredientUserViewModel(app: Application) : AndroidViewModel(app) {

    private val dao by lazy { AppDatabase.get(app).ingredientUserDao() }

    private val _inventory = MutableStateFlow<List<IngredientUser>>(emptyList())
    val inventory: StateFlow<List<IngredientUser>> = _inventory.asStateFlow()

    init {
        // Observe local Room database
        viewModelScope.launch {
            dao.observeAll().collect { entities ->
                _inventory.value = entities.map { it.toModel() }
            }
        }

        // Sync pending items on launch
        syncPendingItems()
    }

    fun addToInventory(
        ingredientId: Long,
        quantity: Double,
        unit: String,
        location: String,
        expiredDate: Long
    ) {
        viewModelScope.launch {
            try {
                // 1. Build the model
                val newItem = IngredientUser(
                    ingredientId = ingredientId,
                    quantity = quantity,
                    quantityUnit = unit,
                    locationStorage = location,
                    expiredDate = expiredDate
                )

                // 2. Save locally (Room)
                val localId = dao.insert(newItem.toEntity())

                // 3. Try to upload to Supabase in background
                uploadItemToSupabase(localId)

            } catch (e: Exception) {
                Log.e("IngredientUserSync", "Failed to add to local inventory", e)
            }
        }
    }

    private fun uploadItemToSupabase(localId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(localId) ?: return@launch
                val model = entity.toModel()

                createIngredientUser(model)
            } catch (e: Exception) {
            }
        }
    }

    fun syncPendingItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allItems = dao.observeAll().first()
                for (item in allItems) {
                    uploadItemToSupabase(item.id)
                }
            } catch (e: Exception) {
                Log.e("IngredientUserSync", "Pending sync failed", e)
            }
        }
    }


    fun deleteInventoryItem(id: Long) {
        viewModelScope.launch {
            try {
                // 1. Delete Locally
                dao.deleteById(id)

                // 2. Delete Remotely (best effort)
                try {
                    supabase.from("Ingredient_User").delete {
                        filter { eq("IngredientUser_Id", id) }
                    }
                } catch (e: Exception) {
                    Log.e("Inventory", "Remote delete failed", e)
                }
            } catch (e: Exception) {
                Log.e("Inventory", "Local delete failed", e)
            }
        }
    }

    fun saveInventoryItems(items: List<IngredientUser>) {
        viewModelScope.launch {
            try {
                val entities = items.map { it.toEntity() }
                // Save to Room
                dao.insertAll(entities)

                // Sync to Supabase
                for (entity in entities) {
                    uploadItemToSupabase(entity.id)
                }
            } catch (e: Exception) {
                Log.e("Inventory", "Failed to save inventory", e)
            }
        }
    }
}