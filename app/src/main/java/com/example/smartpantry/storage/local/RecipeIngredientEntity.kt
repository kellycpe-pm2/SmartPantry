package com.example.smartpantry.storage.local


import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.smartpantry.model.RecipeIngredient

@Entity(tableName = "recipe_ingredient")
data class Recipe_IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long,
    val recipeId: Long,
    val quantity: Double?,
    val unit: String?
)

fun RecipeIngredient.toModel() = RecipeIngredient(
    id = id,
    ingredientId = ingredientId,
    recipeId = recipeId,
    quantity = quantity,
    unit = unit
)

fun RecipeIngredient.toEntity() = Recipe_IngredientEntity(
    id = id ?: 0,
    ingredientId = ingredientId,
    recipeId = recipeId,
    quantity = quantity,
    unit = unit
)
