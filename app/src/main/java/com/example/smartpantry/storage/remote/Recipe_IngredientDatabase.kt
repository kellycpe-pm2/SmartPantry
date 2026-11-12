package com.example.smartpantry.storage.remote

import android.util.Log
import com.example.smartpantry.model.RecipeIngredient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun createRecipeIngredients(recipeIngredients: List<RecipeIngredient>) =
    withContext(Dispatchers.IO) {
        try {
            if (recipeIngredients.isNotEmpty()) {
                // Insert list of ingredients linked to a recipe
                supabase.from("Recipe_Ingredient").insert(recipeIngredients)
            }
        } catch (e: Exception) {
            throw e
        }
    }

suspend fun getRecipeIngredients(recipeId: Long): List<RecipeIngredient> =
    withContext(Dispatchers.IO) {
        try {
            supabase.from("Recipe_Ingredient")
                .select {
                    filter { eq("recipeId", recipeId) }
                }
                .decodeList<RecipeIngredient>()
        } catch (e: Exception) {
            emptyList()
        }
    }
