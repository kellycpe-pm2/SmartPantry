package com.example.smartpantry.model

import kotlinx.serialization.Serializable

@Serializable

data class RecipeUser(
    val recipeUserId: Long? = null,
    val recipeId: Long,
    val userId: Long? = null
)