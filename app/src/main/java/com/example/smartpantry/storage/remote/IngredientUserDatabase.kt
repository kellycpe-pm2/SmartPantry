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

suspend fun getIngredientUserById(id: Long): IngredientUser? =
    withContext(Dispatchers.IO) {
        try {
            supabase.from("Ingredient_User")
                .select {
                    filter { eq("IngredientUser_Id", id) }
                }
                .decodeSingleOrNull<IngredientUser>()
        } catch (e: Exception) {
            null
        }
    }

suspend fun updateIngredientUser(id: Long, ingredientUser: IngredientUser): IngredientUser? =
    withContext(Dispatchers.IO) {
        try {
            supabase.from("Ingredient_User")
                .update(ingredientUser) {
                    filter { eq("IngredientUser_Id", id) }
                    select()
                }
                .decodeSingleOrNull<IngredientUser>()
        } catch (e: Exception) {
            null
        }
    }

suspend fun deleteIngredientUser(id: Long): Boolean =
    withContext(Dispatchers.IO) {
        try {
            supabase.from("Ingredient_User")
                .delete {
                    filter { eq("IngredientUser_Id", id) }
                }
            true
        } catch (e: Exception) {
            false
        }
    }
