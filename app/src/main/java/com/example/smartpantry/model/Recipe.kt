package com.example.smartpantry.model

import kotlinx.serialization.Serializable

@Serializable
data class Recipe(
    val recipeId: Long? = null,
    val recipeName: String,
    val recipeCategory: String? = null,
    val image: String? = null,
    val isVegan: Boolean? = null,
    val description: String? = null,
    val createAt: Long? = null
)
