package com.example.smartpantry.storage.remote

import com.example.smartpantry.model.Ingredient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


suspend fun createIngredient(ingredient: Ingredient): Ingredient =
    withContext(Dispatchers.IO) {
        supabase
            .from("ingredient")
            .insert(ingredient) {
                select()
            }
            .decodeSingle<Ingredient>()
    }
suspend fun getIngredients(): List<Ingredient> =
    withContext(Dispatchers.IO) {
        supabase
            .from("Ingredient")
            .select()
            .decodeList<Ingredient>()
    }