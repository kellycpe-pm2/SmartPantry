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

// 👇 MUST start with Recipe_IngredientEntity (Room) and return RecipeIngredient (Model)
fun Recipe_IngredientEntity.toModel() = RecipeIngredient(
    id = id,
    ingredientId = ingredientId,
    recipeId = recipeId,
    quantity = quantity,
    unit = unit
)

// 👇 MUST start with RecipeIngredient (Model) and return Recipe_IngredientEntity (Room)
fun RecipeIngredient.toEntity() = Recipe_IngredientEntity(
    id = id ?: 0,
    ingredientId = ingredientId,
    recipeId = recipeId,
    quantity = quantity,
    unit = unit
)