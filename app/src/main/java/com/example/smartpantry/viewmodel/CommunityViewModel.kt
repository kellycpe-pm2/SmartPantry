package com.example.smartpantry.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.Aipackage.GroqDraftRepository
import com.example.smartpantry.Screen.CommunityScreen.cleanInput
import com.example.smartpantry.community.DraftIngredient
import com.example.smartpantry.community.RecipeDraft
import com.example.smartpantry.community.flag
import com.example.smartpantry.community.normalizedUnit
import com.example.smartpantry.community.number
import com.example.smartpantry.community.quantityText
import com.example.smartpantry.community.rows
import com.example.smartpantry.community.str
import com.example.smartpantry.community.words
import com.example.smartpantry.remote.CommunityRepository
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.remote.createIngredient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

internal class CommunityViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val repo = CommunityRepository()
    private val codec = Json { ignoreUnknownKeys = true }
    private val _posts = MutableStateFlow<List<JsonObject>>(emptyList()); val posts = _posts.asStateFlow()
    private val _catalog = MutableStateFlow<List<JsonObject>>(emptyList()); val catalog = _catalog.asStateFlow()
    private val _mine = MutableStateFlow<List<JsonObject>>(emptyList()); val mine = _mine.asStateFlow()
    private val _detail = MutableStateFlow<JsonObject?>(null); val detail = _detail.asStateFlow()
    private val _draft = MutableStateFlow(runCatching { codec.decodeFromString<RecipeDraft>(saved["draft"] ?: "") }.getOrDefault(RecipeDraft())); val draft = _draft.asStateFlow()
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _loading = MutableStateFlow(false); val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error = _error.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null); val notice = _notice.asStateFlow()
    private val _more = MutableStateFlow(false); val more = _more.asStateFlow()
    private val _recommendations=MutableStateFlow<List<JsonObject>>(emptyList());val recommendations=_recommendations.asStateFlow()
    private val _recommendationLoading=MutableStateFlow(false);val recommendationLoading=_recommendationLoading.asStateFlow()
    private val _recommendationFailed=MutableStateFlow(false);val recommendationFailed=_recommendationFailed.asStateFlow()
    private val _pantryEmpty=MutableStateFlow(false);val pantryEmpty=_pantryEmpty.asStateFlow()
    private val _recommendationMore=MutableStateFlow(false);val recommendationMore=_recommendationMore.asStateFlow()
    private val _feedFailed=MutableStateFlow(false);val feedFailed=_feedFailed.asStateFlow()
    private var recommendationJob:Job?=null
    private var recommendationPage=0
    private var activeFilters:JsonObject=buildJsonObject{}
    fun loadRecommendations(search:String="",append:Boolean=false) {
        recommendationJob?.cancel()
        val target=if(append) recommendationPage+1 else 0
        recommendationJob=viewModelScope.launch {
            _recommendationLoading.value=true;_recommendationFailed.value=false
            try {
                val data=repo.recommendations(search.trim(),target)
                _recommendations.value=if(append)(_recommendations.value+data.rows("posts")).distinctBy { it.long("recipeId") } else data.rows("posts")
                _pantryEmpty.value=data.flag("pantryEmpty");_recommendationMore.value=data.flag("hasMore");recommendationPage=target
            } catch(t:Throwable) { if(t !is CancellationException) _recommendationFailed.value=true;failure(t) }
            finally { if(kotlin.coroutines.coroutineContext[Job]?.isActive==true)_recommendationLoading.value=false }
        }
    }
    private var loadJob: Job? = null
    private var detailJob: Job? = null
    private val _privateNeeds = MutableStateFlow<List<JsonObject>>(emptyList())
    val privateNeeds = _privateNeeds.asStateFlow()
    private var privateNeedsJob: Job? = null

    fun loadPrivateNeeds(recipeId: Long, keepVisible: Boolean = false) {
        privateNeedsJob?.cancel()
        if (!keepVisible) _privateNeeds.value = emptyList()
        privateNeedsJob = viewModelScope.launch {
            try {
                val owner = repo.userId()
                val result = repo.call("personal_needs", buildJsonObject { put("recipeId", recipeId) })
                if (repo.userId() == owner) _privateNeeds.value = result.rows("ingredients")
            } catch (t: Throwable) {
                failure(t)
            }
        }
    }

    private var page = 0
    private var activeMode = "feed"; private var activeSearch = ""; private var activeCategory = "All"
    private var boundUser: String? = null
    val userId: String get() = runCatching { repo.userId() }.getOrDefault("")
    fun clearError() { _error.value = null }
    fun clearNotice() { _notice.value = null }
    private fun failure(t: Throwable) {
        if (t is CancellationException) throw t
        android.util.Log.e("Community", "Operation failed", t)
        _error.value = com.example.smartpantry.core.userMessage(t)
    }
    fun bindUser() {
        val id = userId
        if (id.isBlank()) return
        if (boundUser != id) {
            val savedOwner: String? = saved["draftOwner"]
            if (savedOwner != null && savedOwner != id) editDraft(RecipeDraft())
            saved["draftOwner"] = id
            clearRecipePreview(); _posts.value = emptyList(); _catalog.value = emptyList(); _mine.value = emptyList(); _detail.value = null
            _privateNeeds.value = emptyList()
            _recommendations.value=emptyList();_pantryEmpty.value=false;_feedFailed.value=false
            boundUser = id
        }
    }
    fun feed(mode: String = activeMode, search: String = activeSearch, category: String = activeCategory, append: Boolean = false, keepVisible: Boolean = false, filters:JsonObject=activeFilters) {
        bindUser();activeFilters=filters; activeMode = mode; activeSearch = search.trim(); activeCategory = category
        loadJob?.cancel()
        if (!append) { page = 0; if(!keepVisible) _posts.value = emptyList() }
        val targetPage = if (append) page + 1 else 0
        loadJob = viewModelScope.launch {
            _loading.value = true;_feedFailed.value=false
            try {
                val result = repo.call("feed", buildJsonObject { put("mode", mode); put("search", search.trim()); put("category", category); put("page", targetPage);filters.forEach { (k,v)->put(k,v) }
                    if(category in listOf("Malaysian","Malay","Chinese","Indian","Asian","Western"))put("cuisine",category)
                    if(category in listOf("Vegan","Halal"))put("dietary",category) })
                val rows = result.rows("posts")
                _posts.value = if (append) (_posts.value + rows).distinctBy { it.long("id") } else rows
                page = targetPage; _more.value = rows.size == 30
                val data = repo.call("catalog"); _catalog.value = data.rows("ingredients"); _mine.value = data.rows("recipes")
            } catch (t: Throwable) { if(t !is CancellationException)_feedFailed.value=true;failure(t) }
            finally { if(kotlin.coroutines.coroutineContext[Job]?.isActive==true)_loading.value=false }
        }
    }
    fun loadDetail(id: Long, keepVisible: Boolean = false) {
        detailJob?.cancel(); if (!keepVisible) _detail.value = null
        detailJob = viewModelScope.launch {
            _loading.value = true
            try { _detail.value = repo.call("detail", buildJsonObject { put("postId", id) }) }
            catch (t: Throwable) { failure(t) } finally { _loading.value = false }
        }
    }
    fun editDraft(value: RecipeDraft, keepRequest: Boolean = false) {
        val sanitized=value.copy(description=cleanInput(value.description,5000,8),steps=value.steps.map{cleanInput(it,500,2)})
        val next = if (!keepRequest && sanitized != _draft.value && sanitized.requestId == _draft.value.requestId) sanitized.copy(requestId=UUID.randomUUID().toString()) else sanitized
        _draft.value = next; saved["draft"] = codec.encodeToString(next); if (userId.isNotBlank()) saved["draftOwner"] = userId
    }
    fun startNew(): Boolean {
        bindUser()
        return _draft.value.title.isNotBlank() || _draft.value.ingredients.isNotEmpty()
    }
    fun resetDraft() { editDraft(RecipeDraft()) }
    private fun mutate(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true; _error.value = null
        viewModelScope.launch {
            try { block() } catch(t: Throwable) { failure(t) } finally { _busy.value = false }
        }
    }
    private suspend fun refreshCatalog() { val data = repo.call("catalog"); _catalog.value = data.rows("ingredients"); _mine.value = data.rows("recipes") }
    fun createRecipeIngredient(name: String, unit: String, category: String, imagePath: String = "", done: (DraftIngredient) -> Unit) = mutate {
        val owner = repo.userId()
        val trimmed = name.trim()
        require(trimmed.length in 1..50 && trimmed.none { it.isISOControl() }) {
            "Enter an ingredient name with 1–50 characters on one line."
        }
        require(unit in com.example.smartpantry.community.recipeUnits) { "Choose a supported unit." }
        refreshCatalog()
        check(repo.userId() == owner) { "Please reopen this page after switching accounts." }
        val existing = _catalog.value.firstOrNull { it.str("name").trim().equals(trimmed, ignoreCase = true) }
        if (existing != null) {
            done(DraftIngredient(existing.long("id"), existing.str("name"), unit = unit, image = existing.str("image")))
            return@mutate
        }
        val uploadedImage=repo.uploadImage(imagePath)
        check(repo.userId()==owner){"Please reopen this page after switching accounts."}
        val now = System.currentTimeMillis()
        val created = createIngredient(Ingredient(
            ingredientName = trimmed, unit = unit, category = category, userId = owner, image=uploadedImage.takeIf{it.isNotBlank()},
            isPublish = false, createAt = now, updateAt = now
        ))
        check(repo.userId() == owner) { "Please reopen this page after switching accounts." }
        val id = checkNotNull(created.ingredientId) { "The server did not return an ingredient ID. Refresh the catalogue before retrying." }
        _catalog.value = (_catalog.value + buildJsonObject {
            put("id", id)
            put("name", created.ingredientName)
            put("unit", unit)
            put("image", created.image.orEmpty())
        }).distinctBy { it.long("id") }
        done(DraftIngredient(id, created.ingredientName, unit = unit, image = created.image.orEmpty()))
    }

    fun openDraft(id: Long, ready: () -> Unit) = mutate {
        val r = repo.call("draft", buildJsonObject { put("recipeId", id) })
        editDraft(RecipeDraft(recipeId=id,title=r.str("title"),category=r.str("category","Malaysian"),description=cleanInput(r.str("description"),5000),videoUrl=r.str("videoUrl"),stepTimers=(r["stepTimers"] as? JsonArray)?.map{it.jsonPrimitive.intOrNull}.orEmpty(),image=r.str("image"),vegan=r.flag("vegan"),
            cuisine=r.str("cuisine"),dishType=r.str("dishType"),dietary=r.str("dietary","Unspecified"),publish=r.flag("published"),wasPublished=r.flag("published"),
            ingredients=r.rows("ingredients").map {
                DraftIngredient(
                    it.long("id"),
                    it.str("name"),
                    quantityText(it.number("quantity")),
                    normalizedUnit(it.str("unit")),
                    it.str("image")
                )
            }, steps=r.words("steps").map{cleanInput(it,500)}.ifEmpty { listOf("") }))
        ready()
    }
    fun ingredientPhoto(uri:Uri,done:(String)->Unit)=mutate {
        try{done(repo.importImage(getApplication(),uri))}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){throw IllegalStateException("Could not read this ingredient photo. Choose another image smaller than 20 MB, or continue without a photo.")}
    }
    fun photo(uri: Uri) = mutate {
        val path = repo.importImage(getApplication(), uri)
        editDraft(_draft.value.copy(image=path))
    }
    fun saveDraft(done: (Long) -> Unit) {
        _draft.value.problem()?.let { _error.value = it; return }
        mutate {
            var value = _draft.value
            val image = repo.uploadImage(value.image)
            value = value.copy(image=image); editDraft(value, keepRequest=true)
            val result = repo.call("save_recipe", buildJsonObject {
                put("requestId", value.requestId); put("recipeId",value.recipeId); put("title",value.title.trim()); put("category",value.category)
                put("publish",value.publish);put("cuisine",value.cuisine);put("dishType",value.dishType);put("dietary",if(value.vegan)"Vegan" else value.dietary);
                put("videoUrl",value.videoUrl.trim());put("description",value.description.trim()); put("image",image); put("vegan",value.vegan)
                put("steps",JsonArray(value.steps.map { JsonPrimitive(it.trim()) }));put("stepTimers",JsonArray(value.steps.indices.map{value.stepTimers.getOrNull(it)?.let{seconds->JsonPrimitive(seconds)}?:JsonNull}))
                put("ingredients",JsonArray(value.ingredients.map { buildJsonObject { put("id",it.id); put("quantity",it.quantity.toDouble()); put("unit",it.unit) } }))
            })
            val id = result.long("recipeId"); check(id > 0) { "Recipe could not be saved." }
            editDraft(value.copy(recipeId=id,requestId=UUID.randomUUID().toString()))
            _notice.value = if(value.publish)"Recipe saved and published." else "Recipe saved in My recipes."; done(id); feed(keepVisible=true)
        }
    }
    fun deletePersonal(id:Long,done:()->Unit)=mutate {
        repo.v5("delete_recipe",buildJsonObject{put("recipeId",id)});refreshCatalog();loadRecommendations()
        _notice.value="Recipe deleted, including its linked community post if it was published.";done()
    }
    fun publish(recipeId: Long, caption: String, done: (Long) -> Unit) = mutate {
        require(caption.trim().length <= 500) { "Caption must have at most 500 characters." }
        val result = repo.call("publish", buildJsonObject { put("recipeId",recipeId); put("caption",caption.trim()) })
        refreshCatalog(); if (_draft.value.recipeId == recipeId) resetDraft(); _notice.value = "Recipe published to the community."; done(result.long("postId"))
    }
    fun caption(postId: Long, text: String, done: () -> Unit) = mutate {
        repo.call("caption",buildJsonObject { put("postId",postId); put("caption",text.trim()) }); _notice.value="Post updated."; done()
    }
    fun unpublish(postId: Long, done: () -> Unit) = mutate {
        repo.call("unpublish",buildJsonObject { put("postId",postId) }); refreshCatalog(); _notice.value="Removed from community. Your personal recipe is kept."; done()
    }
    fun action(action: String, postId: Long, extra: JsonObject = buildJsonObject {}, done: () -> Unit = {}) = mutate {
        val data = repo.call(action, buildJsonObject {
            put("postId", postId)
            extra.forEach { (key, value) -> put(key, value) }
        })
        if (action == "add_missing") {
            _notice.value = if (data.long("added") == 0L) "Everything needed is already in your pantry or shopping list."
            else "${data.long("added")} missing ingredients added to your shopping list."
        }
        if (action == "favourite") _posts.value = _posts.value.map { row ->
            if (row.long("id") != postId) row else buildJsonObject {
                row.forEach { (key, value) -> put(key, value) }
                put("favourite", extra.flag("enabled"))
            }
        }
        val showingPost = postId > 0 && _detail.value?.get("post")?.jsonObject?.long("id") == postId
        done()
        if (showingPost) loadDetail(postId, keepVisible = true)
        if (action == "add_missing" && postId == 0L) loadPrivateNeeds(extra.long("recipeId"), keepVisible = true)
    }
    private val _preparing = MutableStateFlow(false)
    val preparing = _preparing.asStateFlow()
    private val _preview = MutableStateFlow<RecipeDraft?>(null)
    val preview = _preview.asStateFlow()
    private var prepareJob: Job? = null

    fun cancelPrepare() { prepareJob?.cancel() }
    fun clearRecipePreview() { _preview.value = null }

    fun applyRecipePreview() {
        val generated = _preview.value ?: return
        val original = _draft.value
        editDraft(generated.copy(recipeId = original.recipeId, publish = original.publish, wasPublished = original.wasPublished))
        clearRecipePreview()
    }

    private fun JsonObject.long(key: String): Long {
        return this[key]?.jsonPrimitive?.long ?: 0L
    }
}