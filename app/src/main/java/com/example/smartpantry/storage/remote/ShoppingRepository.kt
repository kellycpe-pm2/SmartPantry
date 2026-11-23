package com.example.smartpantry.storage.remote

import com.example.models.IngredientUser
import com.example.smartpantry.model.Ingredient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
internal data class RemoteShoppingItem(
    val id: Long,
    @SerialName("ingredient_id") val ingredientId: Long? = null,
    @SerialName("custom_ingredient_id") val customIngredientId: Long? = null,
    @SerialName("planned_quantity") val plannedQuantity: Double,
    @SerialName("purchase_quantity") val purchaseQuantity: Double,
    val unit: String,
    @SerialName("is_purchased") val isPurchased: Boolean = false
)

@Serializable
internal data class RemoteCustomIngredient(
    val id: Long,
    val name: String,
    @SerialName("image_path") val imagePath: String? = null
)

internal class ShoppingRepository {
    private fun userId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("Sign in to use your shopping list.")

    suspend fun items(): List<RemoteShoppingItem> {
        val owner = userId()
        return supabase.from("shopping_list_items")
            .select { filter { eq("user_id", owner) } }
            .decodeList<RemoteShoppingItem>()
            .sortedBy { it.id }
    }

    suspend fun catalog(): List<Ingredient> =
        supabase.from("ingredient").select().decodeList<Ingredient>()

    suspend fun customIngredients(): List<RemoteCustomIngredient> {
        val owner = userId()
        return supabase.from("custom_ingredients")
            .select { filter { eq("user_id", owner) } }
            .decodeList<RemoteCustomIngredient>()
    }

    suspend fun createCustomIngredient(name: String): RemoteCustomIngredient {
        val trimmed = name.trim()
        require(trimmed.length in 1..50)
        val payload = buildJsonObject {
            put("user_id", userId())
            put("name", trimmed)
        }
        return supabase.from("custom_ingredients")
            .insert(payload) { select() }
            .decodeSingle<RemoteCustomIngredient>()
    }

    suspend fun pantry(): List<IngredientUser> {
        val owner = userId()
        return supabase.from("Ingredient_User")
            .select { filter { eq("user_id", owner) } }
            .decodeList<IngredientUser>()
    }

    suspend fun save(item: RemoteShoppingItem): RemoteShoppingItem {
        require((item.ingredientId == null) != (item.customIngredientId == null))
        require(item.unit in setOf("g", "kg", "mL", "L", "pcs", "bag", "carton"))
        for (quantity in listOf(item.plannedQuantity, item.purchaseQuantity)) {
            require(quantity.isFinite() && quantity > 0)
            if (item.unit in setOf("pcs", "bag", "carton")) {
                require(quantity % 1 == 0.0)
            }
        }

        val owner = userId()
        val payload = buildJsonObject {
            val ingredientId = item.ingredientId
            val customIngredientId = item.customIngredientId
            if (ingredientId == null) put("ingredient_id", JsonNull)
            else put("ingredient_id", ingredientId)
            if (customIngredientId == null) put("custom_ingredient_id", JsonNull)
            else put("custom_ingredient_id", customIngredientId)
            put("planned_quantity", item.plannedQuantity)
            put("purchase_quantity", item.purchaseQuantity)
            put("unit", item.unit)
            put("is_purchased", item.isPurchased)
        }

        val table = supabase.from("shopping_list_items")
        return if (item.id == 0L) {
            table.insert(buildJsonObject {
                payload.forEach { (column, value) -> put(column, value) }
                put("user_id", owner)
            }) { select() }.decodeSingle<RemoteShoppingItem>()
        } else {
            table.update(payload) {
                filter {
                    eq("id", item.id)
                    eq("user_id", owner)
                }
                select()
            }.decodeSingle<RemoteShoppingItem>()
        }
    }

    suspend fun remove(id: Long) {
        val owner = userId()
        supabase.from("shopping_list_items").delete {
            filter {
                eq("id", id)
                eq("user_id", owner)
            }
            select()
        }.decodeSingle<RemoteShoppingItem>()
    }
}
