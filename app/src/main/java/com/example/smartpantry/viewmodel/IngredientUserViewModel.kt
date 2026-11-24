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
import com.example.smartpantry.storage.remote.createIngredient
import com.example.smartpantry.storage.remote.createIngredientUser
import com.example.smartpantry.storage.remote.deleteIngredientUser
import com.example.smartpantry.storage.remote.getIngredientUsers
import com.example.smartpantry.storage.remote.getIngredients
import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.storage.remote.updateIngredientUser
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientUserViewModel(app: Application) : AndroidViewModel(app) {

    private val dao by lazy { AppDatabase.get(app).ingredientUserDao() }
    private val ingredientDao by lazy { AppDatabase.get(app).ingredientDao() }

    private val activeUserId: Flow<String?> = supabase.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> status.session.user?.id
                else -> supabase.auth.currentUserOrNull()?.id
            }
        }
        .distinctUntilChanged()

    private val _inventory = MutableStateFlow<List<IngredientUser>>(emptyList())
    val inventory: StateFlow<List<IngredientUser>> = _inventory.asStateFlow()

    private val _allIngredients = MutableStateFlow<List<Ingredient>>(emptyList())
    val allIngredients: StateFlow<List<Ingredient>> = _allIngredients.asStateFlow()

    private val _stagedItems = MutableStateFlow<List<IngredientUser>>(emptyList())
    val stagedItems: StateFlow<List<IngredientUser>> = _stagedItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // 1. Purge other accounts' cached rows when signing in / switching accounts
        viewModelScope.launch {
            activeUserId.collect { uid ->
                _stagedItems.value = emptyList()
                if (uid.isNullOrBlank()) {
                    _inventory.value = emptyList()
                    _allIngredients.value = emptyList()
                } else {
                    withContext(Dispatchers.IO) {
                        dao.deleteOtherUsers(uid)
                        ingredientDao.deleteOtherUsersPrivate(uid)
                    }
                    refreshData()
                    refreshInventory(force = true)
                }
            }
        }

        // 2. READ (User-scoped local reactive stream for pantry items)
        viewModelScope.launch {
            activeUserId
                .flatMapLatest { uid ->
                    if (uid.isNullOrBlank()) flowOf(emptyList())
                    else dao.observeByUser(uid)
                }
                .collect { entities ->
                    _inventory.value = entities.map { it.toModel() }
                }
        }

        // 3. READ (User-scoped local reactive stream for published + user-created ingredients)
        viewModelScope.launch {
            activeUserId
                .flatMapLatest { uid ->
                    if (uid.isNullOrBlank()) flowOf(emptyList())
                    else ingredientDao.observeForUser(uid)
                }
                .collect { entities ->
                    _allIngredients.value = entities.map { it.toModel() }
                }
        }
    }

    // ==========================================
    // READ (Sync from Supabase)
    // ==========================================
    fun refreshData() {
        viewModelScope.launch {
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    dao.deleteOtherUsers(uid)
                    ingredientDao.deleteOtherUsersPrivate(uid)
                }
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
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
            try {
                dao.deleteOtherUsers(uid)
                ingredientDao.deleteOtherUsersPrivate(uid)
                val ingredients = getIngredients()
                if (ingredients.isNotEmpty()) ingredientDao.insertAll(ingredients.map { it.toEntity() })
                val batches = getIngredientUsers().filter { it.userId == uid }
                if (batches.isNotEmpty()) dao.insertAll(batches.map { it.toEntity() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (problem: Exception) {
                Log.e("Inventory", "Shopping transfer refresh failed", problem)
            }
        }
    }

    fun refreshInventory(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
            try {
                dao.deleteOtherUsers(uid)
                val remote = getIngredientUsers().filter { it.userId == uid }
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
        userId: String? = null
    ) {
        val activeUid = supabase.auth.currentUserOrNull()?.id ?: userId
        val newItem = IngredientUser(
            ingredientId = ingredientId,
            quantity = qty,
            quantityUnit = unit,
            locationStorage = location,
            expiredDate = expiredDate,
            userId = activeUid
        )
        _stagedItems.update { it + newItem }
    }

    fun removeStagedItem(item: IngredientUser) {
        _stagedItems.update { it - item }
    }

    fun saveAllToInventory() {
        val itemsToSave = _stagedItems.value
        val uid = supabase.auth.currentUserOrNull()?.id ?: return
        if (itemsToSave.isEmpty()) return

        _stagedItems.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scopedItems = itemsToSave.map { it.copy(userId = uid) }

                // Trigger immediate notification check for new items
                NotificationHelper.triggerImmediateCheck(getApplication())

                // Save locally & push to Supabase with server ID reconciliation
                for (item in scopedItems) {
                    val localId = dao.insert(item.toEntity())
                    try {
                        val created = createIngredientUser(item)
                        if (created.id != null) {
                            dao.deleteById(localId)
                            dao.insert(created.toEntity())
                        }
                    } catch (e: Exception) {
                        Log.e("Inventory", "Supabase push failed for item", e)
                    }
                }
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
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
            try {
                val newIngredient = Ingredient(
                    ingredientName = name,
                    category = category,
                    weight = weight.takeIf { it > 0.0 },
                    unit = unit,
                    createAt = System.currentTimeMillis(),
                    updateAt = System.currentTimeMillis(),
                    isPublish = false,
                    userId = uid
                )

                val localId = ingredientDao.insert(newIngredient.toEntity())
                var savedIngredient = newIngredient.copy(ingredientId = localId)

                try {
                    val remoteCreated = createIngredient(newIngredient)
                    if (remoteCreated.ingredientId != null) {
                        ingredientDao.deleteById(localId)
                        ingredientDao.insert(remoteCreated.toEntity())
                        savedIngredient = remoteCreated
                    }
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
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
            try {
                val scopedItem = item.copy(id = id, userId = uid)

                // 1. Update Locally in Room
                dao.insert(scopedItem.toEntity())
                NotificationHelper.triggerImmediateCheck(getApplication())

                // 2. Update Remotely in Supabase
                withContext(Dispatchers.IO) {
                    try {
                        updateIngredientUser(id, scopedItem)
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
                val uid = supabase.auth.currentUserOrNull()?.id
                if (uid.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        onResult("Please sign in again.", false)
                    }
                    return@launch
                }

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
                val updatedItem = item.copy(quantity = remaining, userId = uid)

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
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
            try {
                val existing = dao.getById(id) ?: return@launch
                if (existing.userId != null && existing.userId != uid) return@launch

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


    fun addOrMergeInventoryBatch(
        ingredientId: Long,
        quantity: Double,
        unit: String,
        location: String,
        expiredDate: Long,
        onResult: (message: String, success: Boolean) -> Unit
    ) {
        val uid = supabase.auth.currentUserOrNull()?.id
        if (uid.isNullOrBlank()) {
            onResult("Please sign in again.", false)
            return
        }
        if (!quantity.isFinite() || quantity <= 0.0) {
            onResult("Enter an amount greater than 0.", false)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingBatches = dao.getAllByUser(uid)
                    .filter { it.ingredientId == ingredientId && (it.quantity ?: 0.0) > 0.0 }

                val matchingBatch = existingBatches.firstOrNull { batch ->
                    isSameCalendarDay(batch.expiredDate, expiredDate) &&
                            batch.locationStorage.equals(location.trim(), ignoreCase = true) &&
                            batch.quantityUnit.equals(unit.trim(), ignoreCase = true)
                }

                if (matchingBatch != null) {
                    val newQty = (matchingBatch.quantity ?: 0.0) + quantity
                    val merged = matchingBatch.copy(quantity = newQty)
                    dao.insert(merged)
                    NotificationHelper.triggerImmediateCheck(getApplication())
                    try {
                        updateIngredientUser(matchingBatch.id, merged.toModel())
                    } catch (e: Exception) {
                        Log.e("Inventory", "Remote merge update failed", e)
                    }
                    withContext(Dispatchers.Main) {
                        onResult("Combined +$quantity $unit into existing batch (Total: $newQty $unit).", true)
                    }
                } else {
                    val newItem = IngredientUser(
                        ingredientId = ingredientId,
                        quantity = quantity,
                        quantityUnit = unit.trim(),
                        locationStorage = location.trim(),
                        expiredDate = expiredDate,
                        userId = uid
                    )
                    val localId = dao.insert(newItem.toEntity())
                    NotificationHelper.triggerImmediateCheck(getApplication())
                    try {
                        val created = createIngredientUser(newItem)
                        if (created.id != null) {
                            dao.deleteById(localId)
                            dao.insert(created.toEntity())
                        }
                    } catch (e: Exception) {
                        Log.e("Inventory", "Remote batch insert failed", e)
                    }
                    withContext(Dispatchers.Main) {
                        onResult("Added new batch: $quantity $unit in $location.", true)
                    }
                }
            } catch (e: Exception) {
                Log.e("Inventory", "Failed adding/merging stock", e)
                withContext(Dispatchers.Main) {
                    onResult(e.message ?: "Failed to add stock.", false)
                }
            }
        }
    }

    private fun isSameCalendarDay(ts1: Long, ts2: Long): Boolean {
        if (ts1 <= 0L && ts2 <= 0L) return true
        if (ts1 <= 0L || ts2 <= 0L) return false
        val c1 = java.util.Calendar.getInstance().apply { timeInMillis = ts1 }
        val c2 = java.util.Calendar.getInstance().apply { timeInMillis = ts2 }
        return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
                c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun updateAndMergeInventoryBatch(
        batch: IngredientUser,
        newQuantity: Double,
        newUnit: String,
        newLocation: String,
        newExpiredDate: Long,
        onResult: (message: String, success: Boolean) -> Unit
    ) {
        val uid = supabase.auth.currentUserOrNull()?.id
        if (uid.isNullOrBlank()) {
            onResult("Please sign in again.", false)
            return
        }
        val batchId = batch.id
        if (batchId == null) {
            onResult("Invalid pantry batch.", false)
            return
        }
        if (!newQuantity.isFinite() || newQuantity <= 0.0) {
            onResult("Enter a quantity greater than 0.", false)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val otherBatches = dao.getAllByUser(uid)
                    .filter { it.id != batchId && it.ingredientId == batch.ingredientId && (it.quantity ?: 0.0) > 0.0 }

                val duplicateTarget = otherBatches.firstOrNull { other ->
                    isSameCalendarDay(other.expiredDate, newExpiredDate) &&
                            other.locationStorage.equals(newLocation.trim(), ignoreCase = true) &&
                            other.quantityUnit.equals(newUnit.trim(), ignoreCase = true)
                }

                if (duplicateTarget != null) {
                    val combinedQty = (duplicateTarget.quantity ?: 0.0) + newQuantity
                    val merged = duplicateTarget.copy(quantity = combinedQty)
                    dao.insert(merged)
                    dao.deleteById(batchId)
                    NotificationHelper.triggerImmediateCheck(getApplication())
                    try {
                        updateIngredientUser(duplicateTarget.id, merged.toModel())
                        deleteIngredientUser(batchId)
                    } catch (e: Exception) {
                        Log.e("Inventory", "Remote merge on edit failed", e)
                    }
                    withContext(Dispatchers.Main) {
                        onResult("Merged into existing batch with same location & expiry ($combinedQty$newUnit).", true)
                    }
                } else {
                    val updated = batch.copy(
                        quantity = newQuantity,
                        quantityUnit = newUnit.trim(),
                        locationStorage = newLocation.trim(),
                        expiredDate = newExpiredDate,
                        userId = uid
                    )
                    dao.insert(updated.toEntity())
                    NotificationHelper.triggerImmediateCheck(getApplication())
                    try {
                        updateIngredientUser(batchId, updated)
                    } catch (e: Exception) {
                        Log.e("Inventory", "Remote batch update failed", e)
                    }
                    withContext(Dispatchers.Main) {
                        onResult("Batch updated.", true)
                    }
                }
            } catch (e: Exception) {
                Log.e("Inventory", "Failed updating batch", e)
                withContext(Dispatchers.Main) {
                    onResult(e.message ?: "Failed to update batch.", false)
                }
            }
        }
    }
}