package com.example.smartpantry.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.model.RecipeIngredient
import com.example.smartpantry.remote.CommunityRepository
import com.example.smartpantry.storage.local.AppDatabase
import com.example.smartpantry.storage.local.deleteLocalImage
import com.example.smartpantry.storage.local.saveImageToInternalStorage
import com.example.smartpantry.storage.local.toEntity
import com.example.smartpantry.storage.local.toModel
import com.example.smartpantry.storage.remote.createRecipe
import com.example.smartpantry.storage.remote.createRecipeIngredient
import com.example.smartpantry.storage.remote.deleteRecipe as deleteRecipeRemote
import com.example.smartpantry.storage.remote.deleteRecipeIngredient as deleteIngredientRemote
import com.example.smartpantry.storage.remote.deleteRecipeIngredients as deleteIngredientsRemote
import com.example.smartpantry.storage.remote.getRecipeIngredientsFromRemote
import com.example.smartpantry.storage.remote.getRecipes
import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.storage.remote.updateIngredientUser
import com.example.smartpantry.storage.remote.updateRecipe as updateRecipeRemote
import com.example.smartpantry.storage.remote.uploadRecipeImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

data class CreateRecipeUiState(
    val recipeName: String = "",
    val category: String = "",
    val description: String = "",
    val cookingSteps: List<String> = listOf(""),
    val isVegan: Boolean = false,
    val imagePath: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val canSubmit: Boolean get() = recipeName.isNotBlank() && !isSaving
}

data class RecipeDetailIngredient(
    val ingredientId: Long,
    val ingredientName: String,
    val category: String?,
    val description: String?,
    val image: String?,
    val requiredQuantity: Double,
    val requiredUnit: String,
    val availableQuantity: Double,
    val availableUnit: String,
    val expiryDate: Long?
) {
    val isAvailable: Boolean
        get() = availableQuantity >= requiredQuantity
}

data class RecommendedRecipeResult(
    val recipe: Recipe,
    val expiredRate: Double,
    val totalScore: Double,
    val totalQuantity: Double,
    val matchingCount: Int,
    val totalIngredients: Int,
    val missingIngredients: List<String> = emptyList()
) {
    val displayPercentage: Int get() = (expiredRate * 100).toInt()
}

@OptIn(ExperimentalCoroutinesApi::class)
class recipe_vm(app: Application) : AndroidViewModel(app) {

    private val dao by lazy { AppDatabase.get(app).recipeDao() }
    private val ingredientDao by lazy { AppDatabase.get(app).recipeIngredientDao() }
    private val masterIngredientDao by lazy { AppDatabase.get(app).ingredientDao() }
    private val ingredientUserDao by lazy { AppDatabase.get(app).ingredientUserDao() }

    private val activeUserId: Flow<String?> = supabase.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> status.session.user?.id
                else -> supabase.auth.currentUserOrNull()?.id
            }
        }
        .distinctUntilChanged()

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    private val _recommendedRecipes = MutableStateFlow<List<RecommendedRecipeResult>>(emptyList())
    val recommendedRecipes: StateFlow<List<RecommendedRecipeResult>> = _recommendedRecipes.asStateFlow()

    private val _uiState = MutableStateFlow(CreateRecipeUiState())
    val uiState: StateFlow<CreateRecipeUiState> = _uiState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val isSyncing: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // 1. Purge other users' cached data on login/account switch
        viewModelScope.launch {
            activeUserId.collect { uid ->
                if (uid.isNullOrBlank()) {
                    _recipes.value = emptyList()
                    _recommendedRecipes.value = emptyList()
                    _uiState.value = CreateRecipeUiState()
                } else {
                    withContext(Dispatchers.IO) {
                        dao.deleteOtherUsers(uid)
                        ingredientUserDao.deleteOtherUsers(uid)
                        masterIngredientDao.deleteOtherUsersPrivate(uid)
                    }
                    refreshRecipes()
                }
            }
        }

        // 2. Observe recipes strictly scoped to the active user
        viewModelScope.launch {
            activeUserId
                .flatMapLatest { uid ->
                    if (uid.isNullOrBlank()) flowOf(emptyList())
                    else dao.observeByUser(uid)
                }
                .collect { entities ->
                    _recipes.value = entities.map { it.toModel() }
                }
        }
    }

    // Form updaters
    fun onNameChange(v: String)              = _uiState.update { it.copy(recipeName = v, error = null) }
    fun onCategoryChange(v: String)          = _uiState.update { it.copy(category = v, error = null) }
    fun onDescriptionChange(v: String)       = _uiState.update { it.copy(description = v, error = null) }
    fun onStepsChange(steps: List<String>)   = _uiState.update { it.copy(cookingSteps = steps.take(30), error = null) }
    fun onVeganChange(v: Boolean = false)    = _uiState.update { it.copy(isVegan = v, error = null) }
    fun onImagePicked(path: String?)         = _uiState.update { it.copy(imagePath = path, error = null) }
    fun clearError()                         = _uiState.update { it.copy(error = null) }
    fun consumeSaved()                       = _uiState.update { it.copy(saved = false) }

    fun getIngredientsFlow(recipeId: Long): Flow<List<RecipeIngredient>> =
        ingredientDao.getIngredientsForRecipe(recipeId)
            .map { list -> list.map { it.toModel() } }

    suspend fun getRecipeDetailIngredients(recipeId: Long): List<RecipeDetailIngredient> =
        withContext(Dispatchers.IO) {
            val uid = supabase.auth.currentUserOrNull()?.id.orEmpty()
            var recipeItems = ingredientDao.getIngredientsForRecipeOnce(recipeId).map { it.toModel() }
            if (recipeItems.isEmpty()) {
                val remote = getRecipeIngredientsFromRemote(recipeId)
                if (remote.isNotEmpty()) {
                    ingredientDao.insertAll(remote.map { it.toEntity() })
                    recipeItems = ingredientDao.getIngredientsForRecipeOnce(recipeId).map { it.toModel() }
                }
            }

            val masterIngredients = if (uid.isNotBlank()) {
                masterIngredientDao.getAllForUser(uid).associateBy { it.id }
            } else {
                masterIngredientDao.getAll().associateBy { it.id }
            }
            val pantryItems = if (uid.isNotBlank()) {
                ingredientUserDao.getAllByUser(uid)
            } else {
                ingredientUserDao.getAll()
            }
            val pantryByIngredient = pantryItems.groupBy { it.ingredientId }

            recipeItems.map { item ->
                val master = masterIngredients[item.ingredientId]
                val stocks = pantryByIngredient[item.ingredientId].orEmpty()
                val availableQuantity = stocks.sumOf { it.quantity ?: 0.0 }
                val expiry = stocks.map { it.expiredDate }.filter { it > 0L }.minOrNull()

                RecipeDetailIngredient(
                    ingredientId = item.ingredientId,
                    ingredientName = master?.ingredientName ?: "Ingredient #${item.ingredientId}",
                    category = master?.category,
                    description = master?.description,
                    image = master?.image,
                    requiredQuantity = item.quantity ?: 1.0,
                    requiredUnit = item.unit.orEmpty(),
                    availableQuantity = availableQuantity,
                    availableUnit = stocks.firstOrNull()?.quantityUnit.orEmpty(),
                    expiryDate = expiry
                )
            }
        }

    /** Deducts a user-entered amount from pantry batches, earliest expiry first. */
    suspend fun useIngredientStock(
        ingredientId: Long,
        amount: Double,
        unit: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val uid = supabase.auth.currentUserOrNull()?.id.orEmpty()
        if (!amount.isFinite() || amount <= 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Enter an amount greater than 0."))
        }
        val allBatches = if (uid.isNotBlank()) ingredientUserDao.getAllByUser(uid) else ingredientUserDao.getAll()
        val batches = allBatches
            .filter { it.ingredientId == ingredientId && (it.quantity ?: 0.0) > 0.0 }
            .sortedBy { if (it.expiredDate > 0L) it.expiredDate else Long.MAX_VALUE }
        if (batches.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("There is no stock for this ingredient."))
        }
        val available = batches.sumOf { it.quantity ?: 0.0 }
        val stockUnit = batches.firstOrNull()?.quantityUnit.orEmpty()
        if (!stockUnit.equals(unit, ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Unit mismatch. Pantry unit is '$stockUnit'. Please use the same unit."))
        }
        if (available + 0.000001 < amount) {
            return@withContext Result.failure(IllegalStateException("Not enough stock. Available: $available $stockUnit."))
        }

        var remaining = amount
        for (batch in batches) {
            if (remaining <= 0.000001) break
            val current = batch.quantity ?: 0.0
            val deducted = minOf(current, remaining)
            val updated = batch.copy(quantity = (current - deducted).coerceAtLeast(0.0))
            ingredientUserDao.insert(updated)
            try {
                updateIngredientUser(updated.id, updated.toModel())
            } catch (e: Exception) {
                Log.e("RecipeUsage", "Remote stock update failed for ${updated.id}", e)
            }
            remaining -= deducted
        }
        Result.success("Used $amount $unit successfully.")
    }

    /** Uses the recipe's required amount for every ingredient, only if all stock is available. */
    suspend fun useWholeRecipe(recipeId: Long): Result<String> = withContext(Dispatchers.IO) {
        val uid = supabase.auth.currentUserOrNull()?.id.orEmpty()
        val recipeItems = ingredientDao.getIngredientsForRecipeOnce(recipeId).map { it.toModel() }
        if (recipeItems.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("This recipe has no ingredients."))
        }
        val requirements = recipeItems.groupBy { it.ingredientId }.mapValues { (_, rows) ->
            rows.sumOf { it.quantity ?: 1.0 } to rows.firstOrNull()?.unit.orEmpty()
        }
        val allBatches = if (uid.isNotBlank()) ingredientUserDao.getAllByUser(uid) else ingredientUserDao.getAll()
        val masterList = if (uid.isNotBlank()) masterIngredientDao.getAllForUser(uid) else masterIngredientDao.getAll()

        for ((ingredientId, requirement) in requirements) {
            val batches = allBatches.filter { it.ingredientId == ingredientId && (it.quantity ?: 0.0) > 0.0 }
            val available = batches.sumOf { it.quantity ?: 0.0 }
            val unit = batches.firstOrNull()?.quantityUnit.orEmpty()
            if (available + 0.000001 < requirement.first) {
                val name = masterList.firstOrNull { it.id == ingredientId }?.ingredientName ?: "Ingredient $ingredientId"
                return@withContext Result.failure(IllegalStateException("Not enough $name. Need ${requirement.first} ${requirement.second}, available $available $unit."))
            }
            if (!unit.equals(requirement.second, ignoreCase = true)) {
                val name = masterList.firstOrNull { it.id == ingredientId }?.ingredientName ?: "Ingredient $ingredientId"
                return@withContext Result.failure(IllegalArgumentException("Unit mismatch for $name. Recipe uses '${requirement.second}' but pantry uses '$unit'."))
            }
        }
        // Validation completed for every ingredient. Now deduct each requirement.
        for ((ingredientId, requirement) in requirements) {
            var remaining = requirement.first
            val batches = allBatches.filter { it.ingredientId == ingredientId && (it.quantity ?: 0.0) > 0.0 }
                .sortedBy { if (it.expiredDate > 0L) it.expiredDate else Long.MAX_VALUE }
            for (batch in batches) {
                if (remaining <= 0.000001) break
                val current = batch.quantity ?: 0.0
                val deducted = minOf(current, remaining)
                val updated = batch.copy(quantity = (current - deducted).coerceAtLeast(0.0))
                ingredientUserDao.insert(updated)
                try {
                    updateIngredientUser(updated.id, updated.toModel())
                } catch (e: Exception) {
                    Log.e("RecipeUsage", "Remote stock update failed for ${updated.id}", e)
                }
                remaining -= deducted
            }
        }
        Result.success("Recipe ingredients deducted from your pantry.")
    }

    // ==========================================
    // PUBLISH PERSONAL RECIPE TO COMMUNITY
    // ==========================================
    suspend fun publishPersonalRecipe(recipeId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uid = supabase.auth.currentUserOrNull()?.id
                ?: return@withContext Result.failure(IllegalStateException("Please sign in again."))

            val entity = dao.getById(recipeId)
                ?: return@withContext Result.failure(IllegalStateException("Recipe not found."))
            val recipe = entity.toModel()

            val stepsToPublish = recipe.cookingSteps.ifEmpty {
                listOfNotNull(recipe.description?.takeIf { it.isNotBlank() })
            }
            if (stepsToPublish.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Add at least one cooking step or description before publishing to Community.")
                )
            }

            var ings = ingredientDao.getIngredientsForRecipeOnce(recipeId)
            if (ings.isEmpty()) {
                val remote = getRecipeIngredientsFromRemote(recipeId)
                if (remote.isNotEmpty()) {
                    ingredientDao.insertAll(remote.map { it.toEntity() })
                    ings = ingredientDao.getIngredientsForRecipeOnce(recipeId)
                }
            }
            if (ings.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Add at least one ingredient before publishing to Community.")
                )
            }

            val repo = CommunityRepository()
            val uploadedUrl = when {
                !recipe.recipeImage.isNullOrBlank() && recipe.recipeImage.startsWith("https://") -> recipe.recipeImage
                !recipe.localRecipeImage.isNullOrBlank() -> repo.uploadImage(recipe.localRecipeImage)
                else -> ""
            }

            val requestId = UUID.randomUUID().toString()

            val payload = buildJsonObject {
                put("recipe_id", recipeId)
                put("title", recipe.recipeName)
                put("category", recipe.recipeCategory ?: "Malaysian")
                put("description", recipe.description.orEmpty())
                put("image", uploadedUrl)
                put("vegan", recipe.isVegan)
                put("request_id", requestId)
                put("ingredients", buildJsonArray {
                    ings.forEach { row ->
                        add(buildJsonObject {
                            put("ingredient_id", row.ingredientId)
                            put("quantity", row.quantity ?: 1.0)
                            put("unit", row.unit ?: "pcs")
                        })
                    }
                })
                put("steps", buildJsonArray {
                    stepsToPublish.forEach { step ->
                        add(JsonPrimitive(step))
                    }
                })
            }

            val response = repo.call("publish", payload)
            val ok = (response["ok"] as? JsonPrimitive)?.booleanOrNull ?: true
            if (!ok) {
                val msg = (response["message"] as? JsonPrimitive)?.contentOrNull
                    ?: "Unable to publish recipe."
                return@withContext Result.failure(IllegalStateException(msg))
            }

            Result.success("Published \"${recipe.recipeName}\" to Community!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // CREATE — Local Room + Remote Supabase
    // ==========================================
    fun createFullRecipe(ingredients: List<RecipeIngredient> = emptyList()) {
        val s = _uiState.value
        if (s.recipeName.isBlank() || s.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val currentUserId = supabase.auth.currentUserOrNull()?.id
                if (currentUserId.isNullOrBlank()) {
                    _uiState.update { it.copy(isSaving = false, error = "Please sign in first.") }
                    return@launch
                }

                val localPath = s.imagePath?.let { path ->
                    withContext(Dispatchers.IO) {
                        try {
                            if (path.startsWith("/data")) path
                            else saveImageToInternalStorage(getApplication(), Uri.parse(path))
                        } catch (e: Exception) { null }
                    }
                }

                val cleanSteps = s.cookingSteps.map { it.trim() }.filter { it.isNotEmpty() }

                val newRecipe = Recipe(
                    recipeId = null,
                    recipeName = s.recipeName.trim(),
                    recipeCategory = s.category.takeIf { it.isNotEmpty() },
                    isVegan = s.isVegan,
                    description = s.description.takeIf { it.isNotEmpty() },
                    cookingSteps = cleanSteps,
                    createAt = System.currentTimeMillis(),
                    localRecipeImage = localPath,
                    recipeImage = null,
                    userId = currentUserId
                )

                // 1) Save locally — Room auto-generates id
                val localRecipeId = dao.insert(newRecipe.toEntity())

                if (ingredients.isNotEmpty()) {
                    val withFk = ingredients.map { it.copy(id = null, recipeId = localRecipeId) }
                    ingredientDao.insertAll(withFk.map { it.toEntity() })
                }

                _uiState.update { it.copy(isSaving = false, saved = true) }
                yield()
                _uiState.update {
                    it.copy(
                        recipeName = "",
                        category = "",
                        description = "",
                        cookingSteps = listOf(""),
                        isVegan = false,
                        imagePath = null
                    )
                }

                // 2) Push to Supabase
                pushRecipeToSupabase(localRecipeId)
            } catch (e: Exception) {
                Log.e("RecipeSync", "Create failed", e)
                _uiState.update { it.copy(isSaving = false, error = "Failed: ${e.message}") }
            }
        }
    }

    private suspend fun pushRecipeToSupabase(localRecipeId: Long) = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getById(localRecipeId) ?: run {
                Log.e("RecipeSync", "push: local recipe $localRecipeId missing")
                return@withContext
            }

            // 1) image
            var remoteUrl = entity.recipeImage
            val localPath = entity.localRecipeImage
            if (remoteUrl == null && localPath != null) {
                val file = File(localPath)
                if (file.exists()) {
                    remoteUrl = uploadRecipeImage(Uri.fromFile(file), getApplication())
                }
            }

            // 2) create recipe on Supabase (recipeId = null → server auto-generates)
            val savedRemote = try {
                createRecipe(entity.toModel().copy(recipeId = null, recipeImage = remoteUrl))
            } catch (e: Exception) {
                Log.e("RecipeSync", "createRecipe failed", e)
                return@withContext
            }
            val serverRecipeId = savedRemote.recipeId ?: run {
                Log.e("RecipeSync", "createRecipe returned null recipeId")
                return@withContext
            }
            Log.d("RecipeSync", "createRecipe: $localRecipeId → $serverRecipeId")

            // 3) push ingredients with the server recipe id, and re-key each one
            val localIngredients = ingredientDao.getIngredientsForRecipeOnce(localRecipeId)
            localIngredients.forEach { local ->
                val created = createRecipeIngredient(
                    local.toModel().copy(id = null, recipeId = serverRecipeId)
                )
                val serverIngredientId = created?.id
                if (serverIngredientId != null) {
                    ingredientDao.replaceId(local.id, local.copy(id = serverIngredientId))
                    Log.d(
                        "RecipeSync",
                        "ingredient: ${local.id} → $serverIngredientId (recipeId=$serverRecipeId)"
                    )
                } else {
                    Log.e("RecipeSync", "ingredient ${local.id} not returned from Supabase")
                }
            }

            // 4) re-key recipe
            dao.replaceId(localRecipeId, entity.copy(id = serverRecipeId, recipeImage = remoteUrl))
            Log.d("RecipeSync", "recipe re-keyed: $localRecipeId → $serverRecipeId")
        } catch (e: Exception) {
            Log.e("RecipeSync", "push failed", e)
        }
    }

    // ==========================================
    // UPDATE
    // ==========================================
    fun updateRecipe(recipeId: Long, updated: Recipe) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = supabase.auth.currentUserOrNull()?.id
                val existing = dao.getById(recipeId) ?: return@launch
                if (uid != null && existing.userId != null && existing.userId != uid) return@launch

                val finalLocalPath = when {
                    updated.localRecipeImage == null -> existing.localRecipeImage
                    updated.localRecipeImage.startsWith("/data") -> updated.localRecipeImage
                    else -> try {
                        saveImageToInternalStorage(getApplication(), Uri.parse(updated.localRecipeImage))
                    } catch (e: Exception) { existing.localRecipeImage }
                }
                val imageChanged = finalLocalPath != existing.localRecipeImage
                if (imageChanged) deleteLocalImage(existing.localRecipeImage)

                var remoteUrl = if (imageChanged) null else existing.recipeImage
                if (remoteUrl == null && finalLocalPath != null) {
                    val f = File(finalLocalPath)
                    if (f.exists()) remoteUrl = uploadRecipeImage(Uri.fromFile(f), getApplication())
                }

                val merged = updated.copy(
                    recipeId = existing.id,
                    userId = existing.userId ?: uid,
                    createAt = existing.createAt,
                    localRecipeImage = finalLocalPath,
                    recipeImage = remoteUrl
                )
                dao.update(merged.toEntity())
                updateRecipeRemote(existing.id, merged)
            } catch (e: Exception) {
                Log.e("RecipeSync", "Update failed", e)
            }
        }
    }

    // ==========================================
    // DELETE
    // ==========================================
    fun deleteRecipeById(recipeId: Long) = deleteRecipe(recipeId)

    fun deleteRecipe(recipeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = supabase.auth.currentUserOrNull()?.id
                val existing = dao.getById(recipeId) ?: return@launch
                if (uid != null && existing.userId != null && existing.userId != uid) return@launch

                deleteLocalImage(existing.localRecipeImage)
                ingredientDao.deleteByRecipe(recipeId)
                dao.deleteById(recipeId)

                deleteIngredientsRemote(existing.id)
                deleteRecipeRemote(existing.id)
            } catch (e: Exception) {
                Log.e("RecipeSync", "Delete failed", e)
            }
        }
    }

    fun addIngredientToRecipe(
        recipeId: Long,
        ingredientId: Long,
        quantity: Double?,
        unit: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recipe = dao.getById(recipeId) ?: run {
                    Log.e("RecipeSync", "addIngredient: recipe $recipeId missing")
                    return@launch
                }
                val serverRecipeId = recipe.id

                val newItem = RecipeIngredient(
                    id = null,
                    recipeId = recipeId,
                    ingredientId = ingredientId,
                    quantity = quantity,
                    unit = unit
                )
                val localId = ingredientDao.insert(newItem.toEntity())

                val created = createRecipeIngredient(
                    newItem.copy(id = null, recipeId = serverRecipeId)
                )
                val serverIngredientId = created?.id
                if (serverIngredientId != null) {
                    ingredientDao.replaceId(localId, newItem.copy(id = serverIngredientId).toEntity())
                    Log.d("RecipeSync", "addIngredient: $localId → $serverIngredientId (recipeId=$serverRecipeId)")
                } else {
                    Log.e("RecipeSync", "addIngredient: createRecipeIngredient returned null")
                }
            } catch (e: Exception) {
                Log.e("RecipeSync", "addIngredient failed", e)
            }
        }
    }

    fun updateRecipeIngredient(recipeIngredientId: Long, quantity: Double?, unit: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = ingredientDao.getById(recipeIngredientId) ?: return@launch
                val merged = existing.toModel().copy(quantity = quantity, unit = unit)
                ingredientDao.update(merged.toEntity())
                com.example.smartpantry.storage.remote
                    .updateRecipeIngredient(recipeIngredientId, merged)
            } catch (e: Exception) {
                Log.e("RecipeSync", "Update ingredient failed", e)
            }
        }
    }

    fun deleteRecipeIngredient(recipeIngredientId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ingredientDao.deleteById(recipeIngredientId)
                deleteIngredientRemote(recipeIngredientId)
            } catch (e: Exception) {
                Log.e("RecipeSync", "Delete ingredient failed", e)
            }
        }
    }

    // ==========================================
    // SYNC / REFRESH
    // ==========================================
    fun syncPendingItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
                val all = dao.observeByUser(uid).first()
                all.filter { it.recipeImage == null }
                    .forEach { pushRecipeToSupabase(it.id) }
            } catch (e: Exception) {
                Log.e("RecipeSync", "sync failed", e)
            }
        }
    }

    fun refreshRecipes(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
            _isLoading.value = true
            try {
                dao.deleteOtherUsers(uid)
                val remote = getRecipes().filter { it.userId == uid }
                if (remote.isNotEmpty()) {
                    dao.insertAll(remote.map { it.toEntity() })
                    remote.forEach { r ->
                        val rId = r.recipeId ?: return@forEach
                        val remoteIngs = getRecipeIngredientsFromRemote(rId)
                        ingredientDao.deleteByRecipe(rId)
                        if (remoteIngs.isNotEmpty()) {
                            ingredientDao.insertAll(remoteIngs.map { it.toEntity() })
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RecipeSync", "Refresh failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getIngredientsForRecipeOnce(recipeId: Long): List<RecipeIngredient> =
        withContext(Dispatchers.IO) {
            try {
                ingredientDao.getIngredientsForRecipeOnce(recipeId).map { it.toModel() }
            } catch (e: Exception) { emptyList() }
        }

    // ==========================================
    // RECOMMENDATION ALGORITHM (Calculates Expired Rate)
    // ==========================================
    fun loadRecommendedRecipes() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val uid = supabase.auth.currentUserOrNull()?.id.orEmpty()

                // 1. Get active user's pantry (grouped so multiple batches sum properly)
                val userPantry = if (uid.isNotBlank()) {
                    ingredientUserDao.getAllByUser(uid)
                } else {
                    ingredientUserDao.getAll()
                }
                val pantryGrouped = userPantry.groupBy { it.ingredientId }
                val masterIngredients = if (uid.isNotBlank()) {
                    masterIngredientDao.getAllForUser(uid).associateBy { it.id }
                } else {
                    masterIngredientDao.getAll().associateBy { it.id }
                }

                val currentTime = System.currentTimeMillis()
                val oneDayMs = 24 * 60 * 60 * 1000L

                // 2. Get active user's recipes & recipe ingredients
                val allRecipes = _recipes.value
                val allRecipeIngredients = ingredientDao.getAll().map { it.toModel() }
                val ingredientsByRecipe = allRecipeIngredients.groupBy { it.recipeId }

                val results = mutableListOf<RecommendedRecipeResult>()

                // 3. Loop each recipe
                for (recipe in allRecipes) {
                    val recipeId = recipe.recipeId ?: continue
                    val ingredients = ingredientsByRecipe[recipeId] ?: emptyList()
                    if (ingredients.isEmpty()) continue

                    var totalScore = 0.0
                    var totalQuantityIngredients = 0.0
                    var matchingCount = 0
                    val missingList = mutableListOf<String>()

                    // Loop recipe ingredients
                    for (item in ingredients) {
                        val requiredQty = item.quantity ?: 1.0
                        totalQuantityIngredients += requiredQty

                        val batches = pantryGrouped[item.ingredientId]
                            ?.filter { (it.quantity ?: 0.0) > 0.0 }
                            .orEmpty()

                        val score = if (batches.isNotEmpty()) {
                            val availableQty = batches.sumOf { it.quantity ?: 0.0 }
                            val earliestExpiry = batches.map { it.expiredDate }
                                .filter { it > 0L }
                                .minOrNull() ?: Long.MAX_VALUE

                            // 1. Calculate how much of the ingredient you have (0.0 to 1.0)
                            val quantityRatio = if (requiredQty > 0) {
                                (availableQty / requiredQty).coerceIn(0.0, 1.0)
                            } else 1.0

                            if (quantityRatio >= 1.0) {
                                matchingCount++
                            } else {
                                val name = masterIngredients[item.ingredientId]?.ingredientName ?: "Ingredient #${item.ingredientId}"
                                missingList.add(name)
                            }

                            // 2. Calculate Expiry Base Score
                            val diffMs = earliestExpiry - currentTime
                            val leftDay = diffMs / oneDayMs

                            val baseScore = when {
                                leftDay < 0 -> 0
                                leftDay == 0L -> 5
                                leftDay < 4 -> 4
                                leftDay < 7 -> 2
                                else -> 1
                            }

                            // 3. Scale score by quantity ratio
                            baseScore * quantityRatio
                        } else {
                            val name = masterIngredients[item.ingredientId]?.ingredientName ?: "Ingredient #${item.ingredientId}"
                            missingList.add(name)
                            0.0 // Missing from pantry
                        }

                        totalScore += requiredQty * score
                    }

                    // Formula: rate = total_score / (total_quantity_ingredients * 5)
                    val maxScore = totalQuantityIngredients * 5.0
                    val expiredRate = if (maxScore > 0) totalScore / maxScore else 0.0

                    results.add(
                        RecommendedRecipeResult(
                            recipe = recipe,
                            expiredRate = expiredRate,
                            totalScore = totalScore,
                            totalQuantity = totalQuantityIngredients,
                            matchingCount = matchingCount,
                            totalIngredients = ingredients.size,
                            missingIngredients = missingList
                        )
                    )
                }

                // 4. Sort by descending (highest expiredRate first)
                val sortedList = results.sortedByDescending { it.expiredRate }
                _recommendedRecipes.value = sortedList
            } catch (e: Exception) {
                Log.e("RecipeSync", "Failed to calculate recommended recipes", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}