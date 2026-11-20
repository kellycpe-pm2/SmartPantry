package com.example.smartpantry.storage.remote

import android.util.Log
import com.example.smartpantry.model.RecipeIngredient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "RecipeIngredientSync"
private const val TABLE = "Recipe_Ingredient"
private const val ID_COL = "recipe_ingredient_id"

suspend fun createRecipeIngredients(items: List<RecipeIngredient>): Boolean =
    withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext true
        try {
            val payload = items.map { it.copy(id = null) }
            payload.forEach {
                Log.d(TAG, "SENDING recipeId=${it.recipeId} ingredientId=${it.ingredientId}")
            }
            val created = supabase.from(TABLE)
                .insert(payload) { select() }
                .decodeList<RecipeIngredient>()
            Log.d(TAG, "Bulk insert ${created.size}/${payload.size}")
            created.size == payload.size
        } catch (e: Exception) {
            Log.e(TAG, "Bulk insert failed", e); false
        }
    }

suspend fun createRecipeIngredient(item: RecipeIngredient): RecipeIngredient? =
    withContext(Dispatchers.IO) {
        try {
            supabase.from(TABLE)
                .insert(item.copy(id = null)) { select() }
                .decodeSingleOrNull<RecipeIngredient>()
        } catch (e: Exception) {
            Log.e(TAG, "Insert failed", e); null
        }
    }

/** Remote-only: get all ingredients for a recipe, straight from Supabase. */
suspend fun getRecipeIngredientsFromRemote(recipeId: Long): List<RecipeIngredient> =
    withContext(Dispatchers.IO) {
        try {
            supabase.from(TABLE)
                .select { filter { eq("recipeId", recipeId) } }
                .decodeList<RecipeIngredient>()
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed", e); emptyList()
        }
    }

suspend fun updateRecipeIngredient(id: Long, item: RecipeIngredient): RecipeIngredient? =
    withContext(Dispatchers.IO) {
        try {
            supabase.from(TABLE)
                .update(item.copy(id = id)) {
                    filter { eq(ID_COL, id) }
                    select()
                }
                .decodeSingleOrNull<RecipeIngredient>()
        } catch (e: Exception) {
            Log.e(TAG, "Update failed", e); null
        }
    }

suspend fun deleteRecipeIngredient(id: Long): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val deleted = supabase.from(TABLE)
                .delete {
                    filter { eq(ID_COL, id) }
                    select()
                }
                .decodeList<RecipeIngredient>()
            Log.d(TAG, "deleteRecipeIngredient id=$id → ${deleted.size} rows deleted")
            deleted.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed", e); false
        }
    }

suspend fun deleteRecipeIngredients(recipeId: Long): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val deleted = supabase.from(TABLE)
                .delete {
                    filter { eq("recipeId", recipeId) }
                    select()
                }
                .decodeList<RecipeIngredient>()
            Log.d(TAG, "deleteRecipeIngredients recipeId=$recipeId → ${deleted.size} rows deleted")
            deleted.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Delete all failed", e); false
        }
    }