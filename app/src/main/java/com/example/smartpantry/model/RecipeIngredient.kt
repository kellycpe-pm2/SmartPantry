package com.example.smartpantry.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeIngredient(
    @SerialName("recipe_ingredient_id") val id: Long? = null,
    @SerialName("ingredientId") val ingredientId: Long,
    @SerialName("recipeId") val recipeId: Long,
    val quantity: Double? = null,
    val unit: String? = null,
)