package com.example.smartpantry.community

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

internal fun JsonObject.str(key: String, fallback: String = ""): String = (get(key) as? JsonPrimitive)?.contentOrNull ?: fallback
internal fun JsonObject.long(key: String): Long = (get(key) as? JsonPrimitive)?.longOrNull ?: 0L
internal fun JsonObject.number(key: String): Double = (get(key) as? JsonPrimitive)?.doubleOrNull ?: 0.0
internal fun JsonObject.flag(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull ?: false
internal fun JsonObject.rows(key: String): List<JsonObject> = (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
internal fun JsonObject.words(key: String): List<String> = (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
internal fun quantityText(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
internal val recipeUnits = listOf("g", "kg", "mL", "L", "pcs", "bag", "carton", "bunch")
internal fun normalizedUnit(unit: String): String = when(unit.trim().lowercase()) {
    "pc", "piece", "pieces", "pcs" -> "pcs"
    "ml" -> "mL"
    "l" -> "L"
    "cartons" -> "carton"
    "bags" -> "bag"
    else -> unit.trim().lowercase()
}
@Serializable
internal data class DraftIngredient(val id: Long, val name: String, val quantity: String = "1", val unit: String = "pcs", val image: String = "")
@Serializable
internal data class UnresolvedIngredient(val name:String,val quantity:String="1",val unit:String="pcs")
@Serializable
internal data class RecipeDraft(
    val recipeId: Long = 0, val title: String = "", val category: String = "Malaysian",
    val description: String = "", val image: String = "", val videoUrl:String="", val vegan: Boolean = false, val source: String = "",
    val ingredients: List<DraftIngredient> = emptyList(), val steps: List<String> = listOf(""),
    val unresolved: List<UnresolvedIngredient> = emptyList(),
    val stepTimers:List<Int?> = emptyList(),
    val requestId: String = UUID.randomUUID().toString(),
    val publish: Boolean = false, val wasPublished: Boolean = false,
    val cuisine: String = category.takeIf { it in listOf("Malaysian","Chinese","Asian","Western","Other") }.orEmpty(), val dishType: String = "", val dietary: String = "Unspecified"
) {
    fun problem(allowUnresolved:Boolean=false): String? {
        if(unresolved.isNotEmpty() && !allowUnresolved)return "Match the ${unresolved.size} unresolved ingredient(s) before saving."

        if (title.trim().length !in 1..150) return "Enter a title with 1–150 characters."
        if(videoUrl.isNotBlank() && !validRecipeVideo(videoUrl))return "Use a complete HTTPS cooking-video link without login details (maximum 2048 characters)."
        if (description.trim().length > 5000) return "Description must be 5000 characters or fewer."
        if (category.trim().length !in 1..500) return "Choose a category."
        if (ingredients.size+unresolved.size !in 1..50) return "Add 1–50 ingredients."
        if (ingredients.map { it.id }.distinct().size != ingredients.size) return "Combine repeated ingredients."
        ingredients.forEach { row ->
            val q = row.quantity.toDoubleOrNull()
            if (q == null || !q.isFinite() || q <= 0 || q > 1000000) return "Enter a positive quantity for ${row.name}."
            if (row.unit !in recipeUnits) return "Choose a unit for ${row.name}."
            if (row.unit in listOf("pcs", "bag", "carton", "bunch") && q % 1 != 0.0) return "Use a whole number for ${row.name}."
        }
        if(stepTimers.isNotEmpty() && (stepTimers.size!=steps.size || stepTimers.any{it!=null && it !in 1..86400}))return "Set each enabled step timer to 1 second–24 hours, or turn it off."
        if (steps.size !in 1..30 || steps.any { it.trim().length !in 1..500 }) return "Add 1–30 steps, each with 1–500 characters."
        return null
    }
}
internal fun validRecipeVideo(value:String):Boolean = value.length<=2048 && runCatching{val uri=java.net.URI(value);uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null && (uri.port==-1 || uri.port==443)}.getOrDefault(false)
