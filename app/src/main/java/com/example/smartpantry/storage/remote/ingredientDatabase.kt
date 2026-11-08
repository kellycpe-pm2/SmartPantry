package com.example.smartpantry.storage.remote


import com.example.smartpantry.model.Ingredient
import io.github.jan.supabase.postgrest.from

suspend fun createIngredient(ingredient: Ingredient) {
    supabase
        .from("Ingredient")
        .insert(ingredient)
}