package com.example.smartpantry.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class Ingredient(
    val ingredientId: Long? = null,
    val ingredientName: String,
    val weight: BigDecimal? = null,
    val unit: String? = null,
    val image: String? = null,
    val description: String? = null,
    val category: String? = null,
    val minExpiredDuration: Short? = null,
    val maxExpiredDuration: Short? = null,
    val createAt: Long? = null,
    val updateAt: Long? = null
)