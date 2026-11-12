package com.example.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class IngredientUser(
    @SerialName("IngredientUser_Id") val id: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val quantity: Double? = null,
    @SerialName("quantity_unit") val quantityUnit: String? = null,
    @SerialName("location_storage") val locationStorage: String? = null,
    @SerialName("expiredDate") val expiredDate: Long,
    @SerialName("user_id") val userId: String? = null, // UUID as String in Kotlin
    @SerialName("ingredientId") val ingredientId: Long? = null
)