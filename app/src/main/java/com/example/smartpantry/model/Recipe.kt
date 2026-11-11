package com.example.smartpantry.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Recipe(
    @SerialName("recipeId") val recipeId: Long? = null,
    @SerialName("recipeName") val recipeName: String,
    @SerialName("recipeCategory") val recipeCategory: String? = null,
    @SerialName("is_vegan") val isVegan: Boolean = false,
    val description: String? = null,
    @SerialName("create_at") val createAt: Long? = null,
    @SerialName("local_recipe_image") val localRecipeImage: String? = null,
    @SerialName("recipe_image") val recipeImage: String? = null
)