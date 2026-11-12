package com.example.smartpantry.storage.remote

import android.util.Log
import com.example.models.IngredientUser
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun createIngredientUser(ingredientUser: IngredientUser): IngredientUser =
    withContext(Dispatchers.IO) {
        supabase.from("Ingredient_User")
            .insert(ingredientUser) { select() }
            .decodeSingle<IngredientUser>()
    }

suspend fun getIngredientUsers(): List<IngredientUser> =
    withContext(Dispatchers.IO) {
        try {
            supabase.from("Ingredient_User").select().decodeList<IngredientUser>()
        } catch (e: Exception) {
            Log.e("IngredientUserSync", "Fetch failed", e)
            emptyList()
        }
    }