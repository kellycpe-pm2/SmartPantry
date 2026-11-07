package com.example.smartpantry.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class IngredientUser(
    val ingredientUserId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: Long,
    val ingredientId: Long,
    val quantity: BigDecimal? = null,
    val quantityUnit: String? = null,
    val locationStorage: String? = null,
    val expiredDate: Long
)
