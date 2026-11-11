package com.example.smartpantry.storage.local

import com.example.smartpantry.model.Ingredient

fun IngredientEntity.toModel() = Ingredient(
    ingredientId = id,
    ingredientName = ingredientName,
    category = category,
    weight = weight,
    unit = unit,
    description = description,
    image = image,
    localImagePath = localImagePath, 
    minExpiredDuration = minExpiredDuration,
    maxExpiredDuration = maxExpiredDuration,
    createAt = createAt,
    updateAt = updateAt,
)

fun Ingredient.toEntity() = IngredientEntity(
    id = ingredientId ?: 0,
    ingredientName = ingredientName,
    category = category,
    weight = weight,
    unit = unit,
    description = description,
    image = image,
    localImagePath = localImagePath,
    minExpiredDuration = minExpiredDuration,
    maxExpiredDuration = maxExpiredDuration,
    createAt = createAt ?: System.currentTimeMillis(),
    updateAt = updateAt ?: System.currentTimeMillis(),
)