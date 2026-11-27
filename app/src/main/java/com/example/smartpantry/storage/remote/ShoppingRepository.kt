package com.example.smartpantry.storage.remote

import android.content.Context
import android.net.Uri
import com.example.models.IngredientUser
import com.example.smartpantry.model.Ingredient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.smartpantry.core.isConnectionFailure
import com.example.smartpantry.remote.CommunityRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

@Serializable
internal data class RemoteShoppingItem(
    val id: Long,
    @SerialName("ingredient_id") val ingredientId: Long? = null,
    @SerialName("custom_ingredient_id") val customIngredientId: Long? = null,
    @SerialName("planned_quantity") val plannedQuantity: Double,
    @SerialName("purchase_quantity") val purchaseQuantity: Double,
    val unit: String,
    @SerialName("is_purchased") val isPurchased: Boolean = false,
    @SerialName("unit_price") val unitPrice: Double? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("review_handled") val reviewHandled: Boolean = false,
    @SerialName("ingredient_name") val displayName: String? = null
)
@Serializable
internal data class RemoteCustomIngredient(val id:Long,val name:String,@SerialName("image_path")val imagePath:String?=null)

@Serializable
internal data class ShoppingPending(val action: String, val item: RemoteShoppingItem, val name: String, val requestId: String)

/** Private, account-scoped persistent cache. Queue and visible rows commit atomically. */
internal class ShoppingRepository(
    context: Context,
    private val account: () -> String = { supabase.auth.currentUserOrNull()?.id ?: error("Sign in to use your shopping list.") },
    private val networkAvailable: (() -> Boolean)? = null
) {
    private val app = context.applicationContext
    private val owner = userId()
    private val prefs = app.getSharedPreferences("shopping_offline_$owner", Context.MODE_PRIVATE)
    companion object { private val mutex = Mutex() }
    var syncStatus: String? = null
        private set
    var offline: Boolean = false
        private set
    val pendingCount: Int get() = pending().size
    val hasCache: Boolean get() = prefs.contains("items")
    val hasPantryCache: Boolean get() = prefs.contains("pantry")
    val pantryUpdatedAt: Long get() = prefs.getLong("updated_pantry",0L)
    fun reviewDecision(id: Long): String? = prefs.getString("review_$id",null)
    fun rememberReview(id: Long, fingerprint: String) {
        checkOwner()
        check(prefs.edit().putString("review_$id",fingerprint).commit()) { "Could not save your review decision. Please retry." }
    }
    fun beginRefresh() { checkOwner(); offline = false }
    private fun connected(): Boolean = networkAvailable?.invoke() ?: try {
        val manager=app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
    } catch(_: SecurityException) { true }
    private fun checkOwner() { check(userId() == owner) { "Please reopen the shopping list after switching accounts." } }
    private inline fun <reified T> cached(key: String, fallback: T): T =
        prefs.getString(key, null)?.let { codec.decodeFromString<T>(it) } ?: fallback
    private inline fun <reified T> store(key: String, value: T) {
        val editor=prefs.edit().putString(key, codec.encodeToString(value))
        if(key=="pantry") editor.putLong("updated_pantry",System.currentTimeMillis())
        check(editor.commit()) { "Could not save on this device. Free up storage and retry." }
    }
    private fun pending(): List<ShoppingPending> = cached("pending", emptyList())
    private fun commit(rows: List<RemoteShoppingItem>, queue: List<ShoppingPending>, resolved: Pair<Long,Long>? = null, localCatalog: List<RemoteShoppingItem>? = null) {
        val editor=prefs.edit().putString("items",codec.encodeToString(rows)).putString("pending",codec.encodeToString(queue))
        localCatalog?.let { editor.putString("local_catalog",codec.encodeToString(it)) }
        resolved?.let {
            editor.putLong("resolved_${it.first}",it.second)
            prefs.getString("review_${it.first}",null)?.let { decision -> editor.putString("review_${it.second}",decision).remove("review_${it.first}") }
        }
        check(editor.commit()) { "Could not save on this device. Free up storage and retry." }
    }
    private suspend inline fun <reified T> read(key: String, fallback: T, cacheOnly: Boolean, crossinline fetch: suspend () -> T): T = withContext(Dispatchers.IO) {
        checkOwner()
        if(!cacheOnly && !connected())offline=true
        if(cacheOnly || offline) return@withContext cached(key,fallback)
        try { fetch().also { checkOwner();store(key,it) } }
        catch(e: Exception) { if(!isConnectionFailure(e)) throw e; offline=true; cached(key,fallback) }
    }

    private val uploadedPhotos=mutableMapOf<String,String>()
    private val codec=Json{ignoreUnknownKeys=true}
    private fun userId() = account()
    private suspend fun rpc(action:String,payload:JsonObject=buildJsonObject{}):JsonObject = withContext(Dispatchers.IO) {
        checkOwner()
        Json.parseToJsonElement(supabase.postgrest.rpc(if(action=="transfer")"shopping_v5" else "shopping_api",buildJsonObject{put("p_action",action);put("p_payload",buildJsonObject{payload.forEach{(k,v)->put(k,v)};put("owner_id",owner)})}).data).jsonObject
    }
    suspend fun items(cacheOnly: Boolean = false): List<RemoteShoppingItem> = withContext(Dispatchers.IO) {
        checkOwner()
        if(!cacheOnly && !connected())offline=true
        if(cacheOnly || offline) return@withContext cached("items",emptyList())
        mutex.withLock {
            try {
                var rows = supabase.from("shopping_list_items").select { filter { eq("user_id",owner) } }.decodeList<RemoteShoppingItem>()
                pending().forEach { op -> rows=rows.filterNot{it.id==op.item.id} + if(op.action=="save") listOf(op.item) else emptyList() }
                checkOwner();store("items",rows.sortedBy{it.id}); rows.sortedBy{it.id}
            } catch(e: Exception) { if(!isConnectionFailure(e))throw e;offline=true;cached("items",emptyList()) }
        }
    }
    suspend fun localIngredients(): List<RemoteShoppingItem> = withContext(Dispatchers.IO) { checkOwner();cached("local_catalog",emptyList()) }
    suspend fun catalog(cacheOnly: Boolean = false): List<Ingredient> = read("catalog",emptyList(),cacheOnly) { codec.decodeFromJsonElement(rpc("catalog")["rows"] ?: JsonArray(emptyList())) }
    suspend fun customIngredients(cacheOnly: Boolean = false): List<RemoteCustomIngredient> = read("custom",emptyList(),cacheOnly) { supabase.from("custom_ingredients").select { filter { eq("user_id",owner) } }.decodeList<RemoteCustomIngredient>() }
    suspend fun pantry(cacheOnly: Boolean = false): List<IngredientUser> = read("pantry",emptyList(),cacheOnly) { supabase.from("Ingredient_User").select { filter { eq("user_id",owner) } }.decodeList<IngredientUser>() }
    suspend fun storedIds(cacheOnly: Boolean = false): Set<Long> = read("stored",emptySet(),cacheOnly) { (rpc("stored")["ids"] as? JsonArray)?.mapNotNull{it.jsonPrimitive.longOrNull}?.toSet() ?: emptySet() }

    /** New offline rows keep a stable negative ID until the server acknowledges them. */
    suspend fun save(item: RemoteShoppingItem, name: String, requestId: String): RemoteShoppingItem = withContext(Dispatchers.IO) {
        checkOwner()
        mutex.withLock {
            val queue=pending()
            val existing=queue.firstOrNull{it.item.id==item.id && item.id!=0L}
            val localId=if(item.id==0L) minOf(-1L,((cached<List<RemoteShoppingItem>>("items",emptyList()).map{it.id}+queue.map{it.item.id}).minOrNull() ?: 0L)-1) else item.id
            val local=item.copy(id=localId,displayName=name)
            // Persist before trying the network: process death cannot lose an accepted edit.
            val op=ShoppingPending("save",local,name,existing?.requestId ?: requestId)
            val localCatalogue=cached<List<RemoteShoppingItem>>("local_catalog",emptyList())
            val catalogue=if(item.ingredientId==null && item.customIngredientId==null)
                localCatalogue.filterNot{it.displayName?.trim().equals(name.trim(),true)}+local.copy(id=0,isPurchased=false)
            else localCatalogue
            commit(cached<List<RemoteShoppingItem>>("items",emptyList()).filterNot{it.id==localId}+local,
                queue.filterNot{it.item.id==localId}+op,localCatalog=catalogue)
            try { flushLocked() } catch(e: Exception) { if(!isConnectionFailure(e))throw e;offline=true }
            val resolved=prefs.getLong("resolved_$localId",localId)
            cached<List<RemoteShoppingItem>>("items",emptyList()).firstOrNull { it.id==localId }
                ?: cached<List<RemoteShoppingItem>>("items",emptyList()).first { it.id==resolved }
        }
    }
    suspend fun syncPending(force: Boolean = false) = withContext(Dispatchers.IO) {
        checkOwner(); mutex.withLock { flushLocked(force=force) }
    }
    private suspend fun flushLocked(onlyId: Long? = null, force: Boolean = false) {
        if(!force && !connected()) { offline=true;syncStatus="Offline. Your changes are saved on this device.";if(pendingCount>0)com.example.smartpantry.notifications.NotificationRepository(app).recordSyncIssue();return }
        val attempted=mutableSetOf<Long>()
        val failed=mutableListOf<String>()
        var synced=0
        while(true) {
            checkOwner()
            val op=pending().firstOrNull{it.item.id !in attempted && (onlyId==null || it.item.id==onlyId)} ?: break
            attempted+=op.item.id
            try {
                var row=op.item
                val photo=if(op.action=="remove" && row.id>0)null else uploadPhoto(app,row.photoPath)
                if(row.id<0) {
                    val created=remoteSave(row.copy(id=0,photoPath=photo),op.name,op.requestId)
                    row=row.copy(id=created.id,ingredientId=created.ingredientId,customIngredientId=created.customIngredientId)
                }
                val saved=if(op.action=="save") remoteSave(row.copy(photoPath=photo),op.name,op.requestId) else {
                    supabase.from("shopping_list_items").delete { filter { eq("id",row.id);eq("user_id",owner) } }
                    null
                }
                val rows=cached<List<RemoteShoppingItem>>("items",emptyList()).filterNot{it.id==op.item.id || it.id==row.id} + listOfNotNull(saved)
                val localCatalogue=cached<List<RemoteShoppingItem>>("local_catalog",emptyList()).map { entry ->
                    if(entry.displayName?.trim().equals(op.name.trim(),true)) row.copy(id=0,displayName=op.name,photoPath=photo,isPurchased=false) else entry
                }
                commit(rows,pending().filterNot{it.item.id==op.item.id},op.item.id to row.id,localCatalogue)
                offline=false
                synced++
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) {
                android.util.Log.e("ShoppingSync","Sync failed for ${op.name}",e)
                val reason=com.example.smartpantry.core.userMessage(e)
                failed+="${op.name}: $reason"
                if(isConnectionFailure(e)) { offline=true;break }
            }
        }
        syncStatus=when {
            failed.isNotEmpty() -> "$synced synced · ${pendingCount} still pending. " + failed.take(3).joinToString("\n")
            pendingCount==0 -> if(synced>0) "$synced change(s) synced successfully." else "Your shopping list is up to date."
            else -> "$synced synced · $pendingCount other change(s) still pending."
        }
        if(failed.isNotEmpty())com.example.smartpantry.notifications.NotificationRepository(app).recordSyncIssue()
        if(onlyId!=null && failed.isNotEmpty())throw IllegalStateException(syncStatus)
    }
    private suspend fun remoteSave(item: RemoteShoppingItem,name: String,requestId: String): RemoteShoppingItem {

        val payload=buildJsonObject {
            put("id",item.id);put("name",name);put("request_id",requestId)
            put("ingredient_id",item.ingredientId?.let(::JsonPrimitive) ?: JsonNull)
            put("custom_ingredient_id",item.customIngredientId?.let(::JsonPrimitive) ?: JsonNull)
            put("planned_quantity",item.plannedQuantity);put("purchase_quantity",item.purchaseQuantity);put("unit",item.unit);put("is_purchased",item.isPurchased)
            put("unit_price",item.unitPrice?.let(::JsonPrimitive) ?: JsonNull)
            put("photo_path",item.photoPath?.let(::JsonPrimitive) ?: JsonNull);put("review_handled",item.reviewHandled)
        }
        return codec.decodeFromJsonElement(rpc("save",payload))
    }
    /** Resolve and sync THIS item; unrelated pending rows must not prevent a transfer. */
    suspend fun transfer(id:Long,name:String,quantity:Double,unit:String,purchaseDate:String,expiryDate:String,note:String,photo:String?,location:String="Pantry"):Long = withContext(Dispatchers.IO) {
        checkOwner()
        mutex.withLock {
            // A real server request is authoritative; a stale UI offline flag is not.
            flushLocked(onlyId=id,force=true)
            val serverId=if(id<0)prefs.getLong("resolved_$id",id) else id
            require(serverId>0) { "This item has not been uploaded yet. Check your connection and retry." }
            require(pending().none{it.item.id==id || it.item.id==serverId}) { "This item's changes could not be synced. Edit it and retry." }
            val row=cached<List<RemoteShoppingItem>>("items",emptyList()).firstOrNull{it.id==serverId}
                ?: error("This shopping item is no longer available. Refresh your list.")
            require(row.isPurchased) { "Mark this item Purchased before adding it to the pantry." }
            val uploaded=uploadPhoto(app,photo)
            rpc("transfer",buildJsonObject{put("id",serverId);put("name",name);put("quantity",quantity);put("unit",unit);put("purchase_date",purchaseDate);put("expiry_date",expiryDate);put("location",location);put("note",note);put("photo_path",uploaded?.let(::JsonPrimitive) ?: JsonNull)})
            checkOwner()
            store("stored",cached<Set<Long>>("stored",emptySet())+serverId)
            offline=false
            serverId
        }
    }
    suspend fun uploadPhoto(context:Context,path:String?):String? = withContext(Dispatchers.IO) {
        val inherited=cached<List<Ingredient>>("catalog",emptyList()).mapNotNull{it.image}.toSet() +
                cached<List<RemoteCustomIngredient>>("custom",emptyList()).mapNotNull{it.imagePath}
        val override=com.example.smartpantry.core.shoppingPhotoOverride(path,inherited)
        if(override.isNullOrBlank() || override.startsWith("https://",true) || override.startsWith("http://",true))return@withContext override
        val uploadPath=override
        val cacheKey="${userId()}:$path"
        uploadedPhotos[cacheKey]?.let{return@withContext it}
        val uri=if(uploadPath.startsWith("/"))Uri.fromFile(File(uploadPath)) else Uri.parse(uploadPath)
        val local= CommunityRepository().importImage(context,uri)
        val file=File(local);require(file.length()<=5*1024*1024){"Choose a photo smaller than 5 MB."}
        val key="${userId()}/${UUID.randomUUID()}.jpg";val bucket=supabase.storage.from("shopping-images")
        bucket.upload(key,file.readBytes()){contentType=ContentType.Image.JPEG};bucket.publicUrl(key).also{uploadedPhotos[cacheKey]=it}
    }
    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        checkOwner(); mutex.withLock {
        val rows=cached<List<RemoteShoppingItem>>("items",emptyList())
        val row=rows.firstOrNull{it.id==id} ?: return@withLock
        val queue=pending();val existing=queue.firstOrNull{it.item.id==id}
        val op=ShoppingPending("remove",row,row.displayName.orEmpty(),existing?.requestId ?: UUID.randomUUID().toString())
        commit(rows.filterNot{it.id==id},queue.filterNot{it.item.id==id}+op)
        try { flushLocked() } catch(e: Exception) { if(!isConnectionFailure(e))throw e;offline=true }
    }
    }
}