package com.example.smartpantry.storage.local

import com.example.smartpantry.model.Ingredient

fun IngredientEntity.toModel() = Ingredient(
    ingredientId = id,
    ingredientName = ingredientName,
    weight = weight,
    unit = unit,
    image = image,
    localImagePath = localImagePath,
    description = description,
    category = category,
    minExpiredDuration = minExpiredDuration,
    maxExpiredDuration = maxExpiredDuration,
    createAt = createAt,
    updateAt = updateAt,
    isPublish = isPublish,
    userId = userId
)

fun Ingredient.toEntity() = IngredientEntity(
    id = ingredientId ?: 0,
    ingredientName = ingredientName,
    weight = weight,
    unit = unit,
    image = image,
    localImagePath = localImagePath,
    description = description,
    category = category,
    minExpiredDuration = minExpiredDuration,
    maxExpiredDuration = maxExpiredDuration,
    createAt = createAt,
    updateAt = updateAt,
    isPublish = isPublish,
    userId = userId
)
