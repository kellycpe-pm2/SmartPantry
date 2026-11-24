package com.example.smartpantry.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.Aipackage.GroqDraftRepository
import com.example.smartpantry.Screen.CommunityScreen.cleanInput
import com.example.smartpantry.Screen.CommunityScreenpackage.RecipeDraftHelper
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
    private var loadJob: Job? = null
    private var detailJob: Job? = null
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
            _posts.value = emptyList(); _catalog.value = emptyList(); _mine.value = emptyList(); _detail.value = null
            boundUser = id
        }
    }
    fun feed(mode: String = activeMode, search: String = activeSearch, category: String = activeCategory, append: Boolean = false, keepVisible: Boolean = false) {
        bindUser(); activeMode = mode; activeSearch = search.trim(); activeCategory = category
        loadJob?.cancel()
        if (!append) { page = 0; if(!keepVisible) _posts.value = emptyList() }
        val targetPage = if (append) page + 1 else 0
        loadJob = viewModelScope.launch {
            _loading.value = true
            try {
                val result = repo.call("feed", buildJsonObject { put("mode", mode); put("search", search.trim()); put("category", category); put("page", targetPage) })
                val rows = result.rows("posts")
                _posts.value = if (append) (_posts.value + rows).distinctBy { it.long("id") } else rows
                page = targetPage; _more.value = rows.size == 30
                val data = repo.call("catalog"); _catalog.value = data.rows("ingredients"); _mine.value = data.rows("recipes")
            } catch (t: Throwable) { failure(t) } finally { _loading.value = false }
        }
    }
    fun loadDetail(id: Long) {
        detailJob?.cancel(); _detail.value = null
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
    fun openDraft(id: Long, ready: () -> Unit) = mutate {
        val r = repo.call("draft", buildJsonObject { put("recipeId", id) })
        editDraft(RecipeDraft(recipeId=id,title=r.str("title"),category=r.str("category","Malaysian"),description=cleanInput(r.str("description"),5000),image=r.str("image"),vegan=r.flag("vegan"),
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
                put("description",value.description.trim()); put("image",image); put("vegan",value.vegan)
                put("steps",JsonArray(value.steps.map { JsonPrimitive(it.trim()) }))
                put("ingredients",JsonArray(value.ingredients.map { buildJsonObject { put("id",it.id); put("quantity",it.quantity.toDouble()); put("unit",it.unit) } }))
            })
            val id = result.long("recipeId"); check(id > 0) { "Recipe could not be saved." }
            editDraft(value.copy(recipeId=id,requestId=UUID.randomUUID().toString()))
            refreshCatalog(); _notice.value = "Recipe saved. You can now publish it."; done(id)
        }
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
        val data = repo.call(action,buildJsonObject { put("postId",postId); extra.forEach { (k,v)->put(k,v) } })
        if(action=="add_missing") _notice.value = if(data.long("added")==0L) "Everything needed is already in your pantry or shopping list." else "${data.long("added")} missing ingredients added to your shopping list."
        if(_detail.value?.get("post")?.jsonObject?.long("id")==postId) {
            _detail.value=repo.call("detail",buildJsonObject { put("postId",postId) })
        }
        if(action=="favourite") _posts.value=_posts.value.map { row -> if(row.long("id")==postId) buildJsonObject { row.forEach { (k,v)->put(k,v) }; put("favourite",extra.flag("enabled")) } else row }
        done()
    }
    fun prepareByName(name: String, done: () -> Unit) = mutate {
        val query = name.trim()
        require(query.length in 1..150) { "Enter a dish name with 1–150 characters." }
        if (_catalog.value.isEmpty()) {
            try { refreshCatalog() } catch(e: Exception) {
                if(!com.example.smartpantry.core.isConnectionFailure(e))throw e
                _catalog.value=com.example.smartpantry.storage.remote.ShoppingRepository(getApplication()).catalog(true).mapNotNull { row ->
                    row.ingredientId?.let { id -> buildJsonObject { put("id",id);put("name",row.ingredientName);put("unit",row.unit.orEmpty());put("image",row.image.orEmpty()) } }
                }
                require(_catalog.value.isNotEmpty()) { "No saved ingredient catalogue is available yet. Connect once to load it, or create your recipe manually." }
            }
        }
        // Prefer a real, published recipe with the same title. Network failure may
        // fall back to an explicitly labelled local template, never a fake AI result.
        val published = try { repo.call("feed", buildJsonObject { put("search",query) }).rows("posts") } catch (cancelled: CancellationException) { throw cancelled } catch (unavailable: Exception) { emptyList() }
        val match = published.firstOrNull { RecipeDraftHelper.key(it.str("title")) == RecipeDraftHelper.key(query) }
        if (match != null) {
            val data = repo.call("detail",buildJsonObject {put("postId",match.long("id"))})
            require(data.rows("ingredients").all { ingredient -> _catalog.value.any { it.long("id")==ingredient.long("id") } }) { "This recipe uses ingredients not available in your catalogue. Choose a built-in template or create it manually." }
            editDraft(
                RecipeDraft(
                    title = match.str("title"), category = match.str("category", "Other"),
                    description = ("Adapted from ${match.str("author")}'s community recipe.\n" + match.str(
                        "description"
                    )).take(5000),
                    source = "Community recipe by ${match.str("author")} · Review your copy before saving.",
                    ingredients = data.rows("ingredients").map { row ->
                        DraftIngredient(
                            row.long("id"),
                            row.str("name"),
                            quantityText(row.number("quantity")),
                            normalizedUnit(row.str("unit")),
                            row.str("image")
                        )
                    },
                    steps = data.words("steps")
                )
            )
        } else if (RecipeDraftHelper.find(query)==null) {
            editDraft(GroqDraftRepository().create(query,_catalog.value))
        } else {
            val prepared = RecipeDraftHelper.create(query,_catalog.value)
            val withPhoto = if (prepared.title == "Nasi Goreng") {
                val uri=Uri.parse("android.resource://${getApplication<Application>().packageName}/${com.example.smartpantry.R.drawable.home_nasi_goreng}")
                prepared.copy(image=repo.importImage(getApplication(),uri))
            } else prepared
            editDraft(withPhoto)
        }
        _notice.value="Draft prepared. Check ingredients, portions and steps before saving."
        done()
    }
    private fun JsonObject.long(key: String): Long {
        return this[key]?.jsonPrimitive?.long ?: 0L
    }
}