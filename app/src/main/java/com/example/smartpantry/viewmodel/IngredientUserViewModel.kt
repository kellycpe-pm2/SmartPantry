package com.example.smartpantry.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.IngredientUser
import com.example.smartpantry.hardware.NotificationHelper
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.local.AppDatabase
import com.example.smartpantry.storage.local.toEntity
import com.example.smartpantry.storage.local.toModel
import com.example.smartpantry.storage.remote.createIngredientUser
import com.example.smartpantry.storage.remote.deleteIngredientUser
import com.example.smartpantry.storage.remote.getIngredientUsers
import com.example.smartpantry.storage.remote.getIngredients
import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.storage.remote.updateIngredientUser
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
        // READ (Local reactive stream)
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

    // ==========================================
    // READ (Sync from Supabase)
    // ==========================================
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

    // Shopping creates a real remote batch. Sync its ingredient before the
    // inventory row so Room joins cannot briefly miss the newly created item.
    fun refreshAfterShoppingTransfer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ingredients=getIngredients()
                if(ingredients.isNotEmpty()) ingredientDao.insertAll(ingredients.map{it.toEntity()})
                val batches=getIngredientUsers()
                if(batches.isNotEmpty()) dao.insertAll(batches.map{it.toEntity()})
            } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled } catch(problem:Exception) { Log.e("Inventory","Shopping transfer refresh failed",problem) }
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

    // ==========================================
    // CREATE (Staging + Save All)
    // ==========================================
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
            userId = userId
        )
        _stagedItems.update { it + newItem }
    }

    fun removeStagedItem(item: IngredientUser) {
        _stagedItems.update { it - item }
    }

    fun saveAllToInventory() {
        val itemsToSave = _stagedItems.value
        if (itemsToSave.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entities = itemsToSave.map { it.toEntity() }
                dao.insertAll(entities) // Save to Room first

                // Trigger immediate notification check for new items
                NotificationHelper.triggerImmediateCheck(getApplication())

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

    fun createNewIngredient(
        name: String,
        category: String,
        weight: Double,
        unit: String,
        callback: (Ingredient) -> Unit
    ) {
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

    // ==========================================
    // UPDATE
    // ==========================================
    fun updateInventoryItem(id: Long, item: IngredientUser) {
        viewModelScope.launch {
            try {
                // 1. Update Locally in Room
                dao.insert(item.toEntity())
                NotificationHelper.triggerImmediateCheck(getApplication())

                // 2. Update Remotely in Supabase
                withContext(Dispatchers.IO) {
                    try {
                        updateIngredientUser(id, item)
                    } catch (e: Exception) {
                        Log.e("Inventory", "Supabase update failed", e)
                    }
                }

                // 3. Refresh the list
                refreshInventory()
            } catch (e: Exception) {
                Log.e("Inventory", "Failed to update item", e)
            }
        }
    }


    // ==========================================
    // USE AN AMOUNT FROM ONE INVENTORY ITEM
    // ==========================================
    fun useInventoryAmount(
        item: IngredientUser,
        amountToUse: Double,
        onResult: (message: String, success: Boolean) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val itemId = item.id
                val currentQuantity = item.quantity ?: 0.0

                if (itemId == null) {
                    withContext(Dispatchers.Main) {
                        onResult("This inventory item has no saved ID.", false)
                    }
                    return@launch
                }
                if (amountToUse <= 0.0 || amountToUse > currentQuantity) {
                    withContext(Dispatchers.Main) {
                        onResult("Please enter an amount greater than zero and no more than the available quantity.", false)
                    }
                    return@launch
                }

                val remaining = (currentQuantity - amountToUse).coerceAtLeast(0.0)
                val updatedItem = item.copy(quantity = remaining)

                // Update Room first. The inventory Flow updates the screen automatically.
                dao.insert(updatedItem.toEntity())
                NotificationHelper.triggerImmediateCheck(getApplication())

                // Then update the matching Supabase inventory row.
                val remoteUpdated = try {
                    updateIngredientUser(itemId, updatedItem)
                } catch (e: Exception) {
                    Log.e("Inventory", "Failed to sync ingredient usage", e)
                    null
                }

                withContext(Dispatchers.Main) {
                    if (remoteUpdated != null) {
                        onResult("Used $amountToUse ${item.quantityUnit.orEmpty()}. Remaining: $remaining ${item.quantityUnit.orEmpty()}.", true)
                    } else {
                        onResult("Quantity updated on this device, but cloud sync failed. Please refresh and check your connection.", false)
                    }
                }
            } catch (e: Exception) {
                Log.e("Inventory", "Failed to use inventory amount", e)
                withContext(Dispatchers.Main) {
                    onResult("Could not update the ingredient quantity: ${e.message ?: "Unknown error"}", false)
                }
            }
        }
    }

    // ==========================================
    // DELETE
    // ==========================================
    fun deleteInventoryItem(id: Long) {
        viewModelScope.launch {
            try {
                // 1. Delete Locally from Room
                dao.deleteById(id)

                // 2. Delete Remotely from Supabase
                withContext(Dispatchers.IO) {
                    try {
                        deleteIngredientUser(id)
                    } catch (e: Exception) {
                        Log.e("Inventory", "Supabase delete failed", e)
                    }
                }

                // 3. Refresh the list
                refreshInventory()
            } catch (e: Exception) {
                Log.e("Inventory", "Failed to delete item", e)
            }
        }
    }
}