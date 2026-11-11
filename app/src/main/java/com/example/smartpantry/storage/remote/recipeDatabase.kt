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

suspend fun createRecipe(recipe: Recipe): Recipe =
    withContext(Dispatchers.IO) {
        supabase.from("Recipe").insert(recipe) { select() }.decodeSingle<Recipe>()
    }

suspend fun getRecipes(): List<Recipe> =
    withContext(Dispatchers.IO) {
        supabase.from("Recipe").select().decodeList<Recipe>()
    }

suspend fun uploadRecipeImage(uri: Uri, context: Context): String? = withContext(Dispatchers.IO) {
    return@withContext try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        val fileName = "recipes/${UUID.randomUUID()}.jpg"
        supabase.storage.from("recipe-images").upload(fileName, bytes) { // Assuming bucket is 'recipe-images'
            contentType = ContentType.Image.JPEG
        }
        supabase.storage.from("recipe-images").publicUrl(fileName)
    } catch (e: Exception) {
        Log.e("RecipeSync", "Upload failed", e)
        null
    }
}