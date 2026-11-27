package com.example.smartpantry.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.IngredientUser
import com.example.smartpantry.Ai.IngredientAiChatMessage
import com.example.smartpantry.Ai.IngredientAiRepository
import com.example.smartpantry.Ai.IngredientAiSuggestion
import com.example.smartpantry.hardware.NotificationHelper
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.local.AppDatabase
import com.example.smartpantry.storage.local.deleteLocalImage
import com.example.smartpantry.storage.local.saveImageToInternalStorage
import com.example.smartpantry.storage.local.toEntity
import com.example.smartpantry.storage.local.toModel
import com.example.smartpantry.storage.remote.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
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
    val aiDialogVisible: Boolean = false,
    val aiLoading: Boolean = false,
    val aiError: String? = null,
    val aiSuggestion: IngredientAiSuggestion? = null,
    val aiTab: String = "suggestions",
    val aiChatInput: String = "",
    val aiChatMessages: List<IngredientAiChatMessage> = emptyList(),
    val aiChatSending: Boolean = false,
) {
    val canSubmit: Boolean get() = ingredientName.isNotBlank() && !isSaving
}

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientViewModel(app: Application) : AndroidViewModel(app) {
    private val dao by lazy { AppDatabase.get(app).ingredientDao() }

    private val activeUserId: Flow<String?> = supabase.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> status.session.user?.id
                else -> supabase.auth.currentUserOrNull()?.id
            }
        }
        .distinctUntilChanged()

    private val _ingredients = MutableStateFlow<List<Ingredient>>(emptyList())
    val ingredients: StateFlow<List<Ingredient>> = _ingredients.asStateFlow()

    private val _uiState = MutableStateFlow(CreateIngredientUiState())
    val uiState: StateFlow<CreateIngredientUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var lastSyncTime = 0L
    private val syncIntervalMs = 5 * 60 * 1000L
    private var publishOnCreate: Boolean = false

    init {
        // 1. Purge other users' private ingredients on login/account switch
        viewModelScope.launch {
            activeUserId.collect { uid ->
                if (uid.isNullOrBlank()) {
                    _ingredients.value = emptyList()
                    _uiState.value = CreateIngredientUiState()
                } else {
                    withContext(Dispatchers.IO) {
                        dao.deleteOtherUsersPrivate(uid)
                    }
                    refreshIngredients(force = true)
                    syncPendingImages()
                }
            }
        }

        // 2. Observe published ingredients + private ingredients created by the active user
        viewModelScope.launch {
            activeUserId
                .flatMapLatest { uid ->
                    if (uid.isNullOrBlank()) flowOf(emptyList())
                    else dao.observeForUser(uid)
                }
                .collect { entities ->
                    _ingredients.value = entities.map { it.toModel() }
                }
        }
    }

    // --- Form Updaters ---
    fun onNameChange(v: String) = _uiState.update { it.copy(ingredientName = v) }
    fun onCategoryChange(v: String) = _uiState.update { it.copy(category = v) }
    fun onQuantityChange(v: String) = _uiState.update { it.copy(quantityText = v) }
    fun onUnitChange(v: String) = _uiState.update { it.copy(unit = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onMinExpiryChange(v: String) = _uiState.update { it.copy(minExpiryDays = v) }
    fun onMaxExpiryChange(v: String) = _uiState.update { it.copy(maxExpiryDays = v) }
    fun onImagePicked(path: String?) = _uiState.update { it.copy(imagePath = path) }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun consumeSaved() = _uiState.update { it.copy(saved = false) }
    fun setPublishOnCreate(publish: Boolean) { publishOnCreate = publish }

    /** True only if the active user created this ingredient. */
    fun canEditIngredient(ingredient: Ingredient): Boolean {
        val uid = currentUserId() ?: return false
        return ingredient.userId == uid
    }

    // ==========================================
    // GEMINI AI INGREDIENT ASSISTANT
    // ==========================================
    fun openAiAssistant() {
        _uiState.update { it.copy(aiDialogVisible = true, aiError = null, aiTab = "suggestions") }
        if (_uiState.value.ingredientName.isNotBlank()) requestAiSuggestions()
    }

    fun closeAiAssistant() {
        _uiState.update { it.copy(aiDialogVisible = false, aiError = null) }
    }

    fun setAiTab(tab: String) {
        _uiState.update { it.copy(aiTab = tab, aiError = null) }
    }

    fun onAiChatInputChange(value: String) {
        _uiState.update { it.copy(aiChatInput = value) }
    }

    fun requestAiSuggestions() {
        val ingredientName = _uiState.value.ingredientName.trim()
        if (ingredientName.isBlank()) {
            _uiState.update { it.copy(aiError = "Enter an ingredient name first, then ask AI.") }
            return
        }
        if (_uiState.value.aiLoading) return

        _uiState.update { it.copy(aiLoading = true, aiError = null, aiSuggestion = null, aiTab = "suggestions") }
        viewModelScope.launch {
            try {
                val accessToken = supabase.auth.currentSessionOrNull()?.accessToken
                    ?: throw IllegalStateException("Please sign in again before using the AI assistant.")
                val suggestion = IngredientAiRepository.suggest(accessToken, ingredientName)
                _uiState.update { it.copy(aiSuggestion = suggestion, aiLoading = false) }
            } catch (e: Exception) {
                Log.e("IngredientAI", "Suggestion request failed", e)
                _uiState.update { it.copy(aiLoading = false, aiError = e.message ?: "Unable to get AI suggestions.") }
            }
        }
    }

    fun applyAiSuggestion() {
        val suggestion = _uiState.value.aiSuggestion ?: return
        _uiState.update {
            it.copy(
                ingredientName = suggestion.normalizedName,
                category = suggestion.category,
                unit = suggestion.unit,
                minExpiryDays = suggestion.minExpiryDays.toString(),
                maxExpiryDays = suggestion.maxExpiryDays.toString(),
                description = buildString {
                    append(suggestion.description.trim())
                    if (suggestion.storageAdvice.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append("Storage advice: ")
                        append(suggestion.storageAdvice.trim())
                    }
                },
                aiDialogVisible = false,
                aiError = null
            )
        }
        // Storage advice is appended to the existing description field so no new
        // database column or migration is required.
    }

    fun sendAiChatMessage() {
        val current = _uiState.value
        val message = current.aiChatInput.trim()
        if (message.isBlank() || current.aiChatSending) return
        val ingredientName = current.ingredientName.trim()
            .ifBlank { current.aiSuggestion?.normalizedName.orEmpty() }
            .ifBlank { "general pantry ingredients" }
        val previousHistory = current.aiChatMessages
        val userMessage = IngredientAiChatMessage(role = "user", text = message)
        _uiState.update {
            it.copy(
                aiChatMessages = it.aiChatMessages + userMessage,
                aiChatInput = "",
                aiChatSending = true,
                aiError = null,
                aiTab = "chat"
            )
        }
        viewModelScope.launch {
            try {
                val accessToken = supabase.auth.currentSessionOrNull()?.accessToken
                    ?: throw IllegalStateException("Please sign in again before chatting with AI.")
                val reply = IngredientAiRepository.chat(accessToken, ingredientName, message, previousHistory)
                _uiState.update {
                    it.copy(
                        aiChatMessages = it.aiChatMessages + IngredientAiChatMessage("assistant", reply),
                        aiChatSending = false
                    )
                }
            } catch (e: Exception) {
                Log.e("IngredientAI", "Chat request failed", e)
                _uiState.update { it.copy(aiChatSending = false, aiError = e.message ?: "Unable to send your question.") }
            }
        }
    }

    private fun currentUserId(): String? =
        try { supabase.auth.currentUserOrNull()?.id } catch (_: Exception) { null }

    // ==========================================
    // CREATE
    // ==========================================
    fun create() {
        val s = _uiState.value
        if (s.ingredientName.isBlank() || s.isSaving) return

        val uid = currentUserId()
        if (uid.isNullOrBlank()) {
            _uiState.update { it.copy(error = "Please sign in before creating an ingredient.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                // 1. Persist image locally (if picked)
                val persistedPath = s.imagePath?.let { path ->
                    withContext(Dispatchers.IO) {
                        try {
                            if (path.startsWith("/data")) path
                            else saveImageToInternalStorage(getApplication(), Uri.parse(path))
                        } catch (e: Exception) {
                            Log.e("IngredientSync", "Image save failed", e)
                            null
                        }
                    }
                }

                // 2. Build model scoped to active user
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
                    updateAt = System.currentTimeMillis(),
                    isPublish = publishOnCreate,
                    userId = uid
                )

                // 3. Save to Room
                val localId = dao.insert(ingredient.toEntity())
                Log.d("IngredientSync", "Saved locally: id=$localId, publish=$publishOnCreate, image=$persistedPath")

                // 4. Reset form
                _uiState.update { it.copy(isSaving = false, saved = true) }
                yield()
                _uiState.update {
                    it.copy(
                        ingredientName = "", category = "", quantityText = "",
                        unit = "", description = "", imagePath = null,
                        minExpiryDays = "", maxExpiryDays = ""
                    )
                }

                // 5. Upload image ALWAYS (even for drafts) — only data is publish-gated
                uploadImageOnly(localId)

                // 6. If publish, push full data to Supabase
                if (publishOnCreate) {
                    publishIngredientToSupabase(localId)
                }
            } catch (e: Exception) {
                Log.e("IngredientSync", "Save failed", e)
                _uiState.update { it.copy(isSaving = false, error = com.example.smartpantry.core.userMessage(e)) }
            }
        }
    }

    // ==========================================
    // UPLOAD IMAGE ONLY (runs for both draft & published)
    // ==========================================
    private fun uploadImageOnly(localId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(localId) ?: return@launch
                val localPath = entity.localImagePath ?: return@launch
                if (entity.image != null) return@launch  // already uploaded

                val file = File(localPath)
                if (!file.exists()) {
                    Log.e("IngredientSync", "Local image file missing: $localPath")
                    return@launch
                }

                Log.d("IngredientSync", "Uploading image: ${file.length()} bytes")
                val remoteUrl = uploadIngredientImage(Uri.fromFile(file), getApplication())
                Log.d("IngredientSync", "Image uploaded: $remoteUrl")

                if (remoteUrl != null) {
                    val updated = entity.toModel().copy(image = remoteUrl)
                    dao.insert(updated.toEntity())
                }
            } catch (e: Exception) {
                Log.e("IngredientSync", "Image upload failed", e)
            }
        }
    }

    // ==========================================
    // PUBLISH FULL INGREDIENT (data to Supabase)
    // ==========================================
    private fun publishIngredientToSupabase(localId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(localId) ?: return@launch
                val model = entity.toModel().copy(isPublish = true)
                dao.insert(model.toEntity())
                val created = createIngredient(model)
                if (created.ingredientId != null && created.ingredientId > 0) {
                    dao.deleteById(localId)
                    dao.insert(
                        created.copy(localImagePath = model.localImagePath ?: created.localImagePath)
                            .toEntity()
                    )
                }
                Log.d("IngredientSync", "Published to Supabase: id=$localId")
            } catch (e: Exception) {
                Log.e("IngredientSync", "Publish failed", e)
            }
        }
    }

    // ==========================================
    // SYNC PENDING IMAGES (retry on startup)
    // ==========================================
    fun syncPendingImages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = currentUserId() ?: return@launch
                val all = dao.observeForUser(uid).first()
                // Retry any ingredient owned by this user that has a local image but no remote URL
                all.filter { it.userId == uid && it.localImagePath != null && it.image == null }
                    .forEach { uploadImageOnly(it.id) }
            } catch (e: Exception) {
                Log.e("IngredientSync", "Pending sync failed", e)
            }
        }
    }

    // ==========================================
    // REFRESH
    // ==========================================
    fun refreshIngredients(force: Boolean = false) {
        val uid = currentUserId() ?: return
        val now = System.currentTimeMillis()
        if (!force && (now - lastSyncTime) < syncIntervalMs) return
        if (_isRefreshing.value) return

        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    dao.deleteOtherUsersPrivate(uid)
                }
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

    // ==========================================
    // UPDATE (Creator-Only)
    // ==========================================
    fun updateIngredient(id: Long, updated: Ingredient) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = currentUserId() ?: return@launch
                val existing = dao.getById(id) ?: return@launch
                if (existing.userId != uid) return@launch

                val merged = updated.copy(
                    ingredientId = id,
                    userId = uid,
                    updateAt = System.currentTimeMillis()
                )
                dao.insert(merged.toEntity())
                com.example.smartpantry.storage.remote.updateIngredient(id, merged)
                refreshIngredients(force = true)
            } catch (e: Exception) {
                Log.e("IngredientSync", "Update failed", e)
            }
        }
    }

    // ==========================================
    // DELETE (Creator-Only)
    // ==========================================
    fun deleteIngredient(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = currentUserId() ?: return@launch
                val existing = dao.getById(id) ?: return@launch
                if (existing.userId != uid) return@launch

                deleteLocalImage(existing.localImagePath)
                dao.deleteById(id)
                com.example.smartpantry.storage.remote.deleteIngredient(id)
                refreshIngredients(force = true)
            } catch (e: Exception) {
                Log.e("IngredientSync", "Delete failed", e)
            }
        }
    }

    // ==========================================
    // TOGGLE PUBLISH (Draft <-> Published, Creator-Only)
    // ==========================================
    fun togglePublish(id: Long, ing: Ingredient) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId() ?: return@launch
            val existing = dao.getById(id) ?: return@launch
            if (existing.userId != uid) return@launch

            val newState = !(ing.isPublish ?: false)
            val updated = ing.copy(
                isPublish = newState,
                userId = uid,
                updateAt = System.currentTimeMillis()
            )
            dao.insert(updated.copy(ingredientId = id).toEntity())

            if (newState) {
                // Publishing: upload image if not yet, then push data
                uploadImageOnly(id)
                try {
                    createIngredient(updated.copy(ingredientId = id))
                } catch (e: Exception) {
                    Log.e("IngredientSync", "Publish failed", e)
                }
            } else {
                // Unpublishing: delete from Supabase
                try {
                    com.example.smartpantry.storage.remote.deleteIngredient(id)
                } catch (e: Exception) {
                    Log.e("IngredientSync", "Unpublish failed", e)
                }
            }
        }
    }



}