package com.example.smartpantry.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class RecipeIngredient(
    val recipeIngredientId: Long? = null,
    val ingredientId: Long,
    val recipeId: Long? = null,
    val quantity: BigDecimal? = null,
    val unit: String? = null
)