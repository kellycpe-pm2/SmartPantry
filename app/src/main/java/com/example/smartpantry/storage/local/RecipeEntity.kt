package com.example.smartpantry.storage.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.smartpantry.model.Recipe

@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeName: String,
    val recipeCategory: String?,
    val isVegan: Boolean,
    val description: String?,
    val createAt: Long,
    val localRecipeImage: String?,
    val recipeImage: String?,
    val userId: String?
)

fun RecipeEntity.toModel() = Recipe(
    recipeId = id,
    recipeName = recipeName,
    recipeCategory = recipeCategory,
    isVegan = isVegan,
    description = description,
    createAt = createAt,
    localRecipeImage = localRecipeImage,
    recipeImage = recipeImage,
    userId = userId
)

fun Recipe.toEntity() = RecipeEntity(
    id = recipeId ?: 0,
    recipeName = recipeName,
    recipeCategory = recipeCategory,
    isVegan = isVegan,
    description = description,
    createAt = createAt ?: System.currentTimeMillis(),
    localRecipeImage = localRecipeImage,
    recipeImage = recipeImage,
    userId = userId
)
