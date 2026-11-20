package com.example.smartpantry.storage.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.smartpantry.model.RecipeIngredient

@Entity(tableName = "recipe_ingredient")
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipeId: Long? = null,
    val ingredientId: Long,
    val quantity: Double? = null,
    val unit: String? = null
)

fun RecipeIngredientEntity.toModel() = RecipeIngredient(
    id = id,
    ingredientId = ingredientId,
    recipeId = recipeId,
    quantity = quantity,
    unit = unit
)

fun RecipeIngredient.toEntity() = RecipeIngredientEntity(
    id = id ?: 0,
    recipeId = recipeId,
    ingredientId = ingredientId,
    quantity = quantity,
    unit = unit
)