package com.example.smartpantry.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class FoodLog(
    @SerialName("food_log_id") val id: Long? = null,
    @SerialName("record_type") val type: String,                 // Consumed / Wasted
    @SerialName("food_name") val foodName: String,
    @SerialName("food_category") val category: String,
    @SerialName("food_quantity") val quantity: Double,
    val unit: String,
    @SerialName("record_date") val date: String,                 // yyyy-MM-dd
    @SerialName("food_log_notes") val notes: String? = null,
    @SerialName("wasted_cost") val wastedCost: Double = 0.0,      // RM, 0.00 when Consumed
    @SerialName("ingredient_user_id") val ingredientUserId: Long? = null,
    @SerialName("is_auto") val isAuto: Boolean = false
)

val UNITS = listOf("g", "kg", "mL", "L", "pcs")
val CATEGORIES = listOf("Fruit", "Vegetable", "Meat", "Seafood", "Dairy", "Grain", "Snack", "Drink", "Other")

/** quantity in g / mL / pcs so kg and L can be added to g and mL */
fun FoodLog.base() = if (unit == "kg" || unit == "L") quantity * 1000 else quantity

/** e.g. "1200 g · 500 mL · 3 pcs" */
fun List<FoodLog>.totalText(): String {
    fun sum(vararg u: String) = filter { it.unit in u }.sumOf { it.base() }
    val parts = listOf("g" to sum("g", "kg"), "mL" to sum("mL", "L"), "pcs" to sum("pcs"))
    return parts.filter { it.second > 0 }.joinToString(" · ") { "%.0f %s".format(it.second, it.first) }.ifEmpty { "0" }
}

/** FN-F-C-2: returns an error message, or null if valid */
fun validate(type: String, name: String, cat: String, qty: String, unit: String, date: String): String? {
    val d = runCatching { LocalDate.parse(date) }.getOrNull()
    return when {
        type !in listOf("Consumed", "Wasted") -> "Select record type"
        name.isBlank() -> "Enter food name"
        cat !in CATEGORIES -> "Select food category"
        (qty.toDoubleOrNull() ?: 0.0) <= 0.0 -> "Quantity must be greater than 0.00"
        unit !in UNITS -> "Select a unit"
        d == null -> "Date must be yyyy-MM-dd"
        d.isAfter(LocalDate.now()) -> "Date cannot be in the future"
        else -> null
    }
}

/** total RM lost, e.g. "RM 12.50" */
fun List<FoodLog>.costText() = "RM %.2f".format(sumOf { it.wastedCost })