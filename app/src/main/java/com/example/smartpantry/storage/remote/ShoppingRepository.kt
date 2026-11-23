package com.example.smartpantry.storage.remote

import android.content.Context
import android.net.Uri
import com.example.models.IngredientUser
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.community.CommunityRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

internal class ShoppingRepository {
    private val uploadedPhotos=mutableMapOf<String,String>()
    private val codec=Json{ignoreUnknownKeys=true}
    private fun userId() = supabase.auth.currentUserOrNull()?.id ?: error("Sign in to use your shopping list.")
    private suspend fun rpc(action:String,payload:JsonObject=buildJsonObject{}):JsonObject = withContext(Dispatchers.IO) {
        userId()
        Json.parseToJsonElement(supabase.postgrest.rpc("shopping_api",buildJsonObject{put("p_action",action);put("p_payload",payload)}).data).jsonObject
    }
    suspend fun items():List<RemoteShoppingItem> = supabase.from("shopping_list_items").select{filter{eq("user_id",userId())}}.decodeList<RemoteShoppingItem>().sortedBy{it.id}
    suspend fun catalog():List<Ingredient> = codec.decodeFromJsonElement(rpc("catalog")["rows"] ?: JsonArray(emptyList()))
    suspend fun customIngredients():List<RemoteCustomIngredient> = supabase.from("custom_ingredients").select{filter{eq("user_id",userId())}}.decodeList<RemoteCustomIngredient>()
    suspend fun pantry():List<IngredientUser> = supabase.from("Ingredient_User").select{filter{eq("user_id",userId())}}.decodeList<IngredientUser>()
    suspend fun storedIds():Set<Long> = (rpc("stored")["ids"] as? JsonArray)?.mapNotNull{it.jsonPrimitive.longOrNull}?.toSet() ?: emptySet()
    suspend fun save(item:RemoteShoppingItem,name:String,requestId:String):RemoteShoppingItem {
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
    suspend fun transfer(id:Long,name:String,quantity:Double,unit:String,purchaseDate:String,expiryDate:String,note:String,photo:String?) {
        rpc("transfer",buildJsonObject{put("id",id);put("name",name);put("quantity",quantity);put("unit",unit);put("purchase_date",purchaseDate);put("expiry_date",expiryDate);put("note",note);put("photo_path",photo?.let(::JsonPrimitive) ?: JsonNull)})
    }
    suspend fun uploadPhoto(context:Context,path:String?):String? = withContext(Dispatchers.IO) {
        if(path.isNullOrBlank() || path.startsWith("https://")) return@withContext path
        val cacheKey="${userId()}:$path"
        uploadedPhotos[cacheKey]?.let{return@withContext it}
        val local=CommunityRepository().importImage(context,Uri.parse(path))
        val file=File(local);require(file.length()<=5*1024*1024){"Choose a photo smaller than 5 MB."}
        val key="${userId()}/${UUID.randomUUID()}.jpg";val bucket=supabase.storage.from("shopping-images")
        bucket.upload(key,file.readBytes()){contentType=ContentType.Image.JPEG};bucket.publicUrl(key).also{uploadedPhotos[cacheKey]=it}
    }
    suspend fun remove(id:Long) {
        supabase.from("shopping_list_items").delete{filter{eq("id",id);eq("user_id",userId())};select()}.decodeSingle<RemoteShoppingItem>()
    }
}
