package com.example.smartpantry.storage.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.smartpantry.model.Ingredient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.UUID

private const val TAG = "PantrySync"

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
            .from("ingredient")
            .select()
            .decodeList<Ingredient>()
    }


suspend fun downloadImageToFiles(context: Context, url: String): String? = withContext(Dispatchers.IO) {
    return@withContext try {
        val bytes = URL(url).openStream().use { it.readBytes() }
        val dir = File(context.filesDir, "ingredient_images").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.writeBytes(bytes)
        file.absolutePath
    } catch (e: Exception) {
        Log.e(TAG, "Failed to download image", e)
        null
    }
}


suspend fun uploadIngredientImage(uri: Uri, context: Context): String? = withContext(Dispatchers.IO) {
    return@withContext try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext null

        val fileName = "ingredients/${UUID.randomUUID()}.jpg"

        // Upload to bucket
        supabase.storage.from("ingredient-images").upload(fileName, bytes) {
            contentType = ContentType.Image.JPEG
        }

        // Obtain public URL
        supabase.storage.from("ingredient-images").publicUrl(fileName)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to upload image", e)
        null
    }
}