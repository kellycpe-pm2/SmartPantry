package com.example.smartpantry.storage.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.smartpantry.model.Recipe
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "RecipeSync"
private const val TABLE = "Recipe"

suspend fun createRecipe(recipe: Recipe): Recipe =
    withContext(Dispatchers.IO) {
        supabase.from(TABLE)
            .insert(recipe.copy(recipeId = null)) { select() }
            .decodeSingle<Recipe>()
    }

suspend fun getRecipes(): List<Recipe> =
    withContext(Dispatchers.IO) {
        supabase.from(TABLE).select().decodeList<Recipe>()
    }

suspend fun getRecipeById(id: Long): Recipe? =
    withContext(Dispatchers.IO) {
        try {
            supabase.from(TABLE)
                .select { filter { eq("recipeId", id) } }
                .decodeSingleOrNull<Recipe>()
        } catch (e: Exception) {
            Log.e(TAG, "getRecipeById failed", e); null
        }
    }

/** Look up a recipe on Supabase by name + userId (used when the local id is not the server id). */
suspend fun getRecipeByNameAndUser(name: String, userId: String?): Recipe? =
    withContext(Dispatchers.IO) {
        try {
            supabase.from(TABLE)
                .select {
                    filter {
                        eq("recipeName", name)
                        eq("userId", userId ?: "")
                    }
                }
                .decodeSingleOrNull<Recipe>()
        } catch (e: Exception) {
            Log.e(TAG, "getRecipeByNameAndUser failed", e); null
        }
    }

suspend fun updateRecipe(id: Long, recipe: Recipe): Recipe? =
    withContext(Dispatchers.IO) {
        try {
            supabase.from(TABLE)
                .update(recipe.copy(recipeId = id)) {
                    filter { eq("recipeId", id) }
                    select()
                }
                .decodeSingleOrNull<Recipe>()
        } catch (e: Exception) {
            Log.e(TAG, "updateRecipe failed", e); null
        }
    }

suspend fun deleteRecipe(id: Long): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val deleted = supabase.from(TABLE)
                .delete {
                    filter { eq("recipeId", id) }
                    select()
                }
                .decodeList<Recipe>()
            Log.d(TAG, "deleteRecipe id=$id → ${deleted.size} rows deleted")
            deleted.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "deleteRecipe failed", e); false
        }
    }

suspend fun uploadRecipeImage(uri: Uri, context: Context): String? =
    withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val fileName = "recipes/${UUID.randomUUID()}.jpg"
            supabase.storage.from("recipe-images").upload(fileName, bytes) {
                contentType = ContentType.Image.JPEG
            }
            supabase.storage.from("recipe-images").publicUrl(fileName)
        } catch (e: Exception) {
            Log.e(TAG, "upload failed", e); null
        }
    }