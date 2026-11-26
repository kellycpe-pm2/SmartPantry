package com.example.smartpantry.storage.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.smartpantry.model.Ingredient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.UUID

private const val TAG = "PantrySync"
private const val TABLE = "ingredient"

suspend fun createIngredient(ingredient: Ingredient): Ingredient =
    withContext(Dispatchers.IO) {
        val userId = ingredient.userId ?: supabase.auth.currentUserOrNull()?.id
        supabase.from(TABLE)
            .insert(ingredient.copy(userId = userId)) { select() }
            .decodeSingle<Ingredient>()
    }

suspend fun getIngredients(): List<Ingredient> =
    withContext(Dispatchers.IO) {
        val uid = supabase.auth.currentUserOrNull()?.id
        supabase.from(TABLE)
            .select {
                filter {
                    if (uid != null) {
                        or {
                            eq("is_publish", true)
                            eq("userId", uid)
                        }
                    } else {
                        eq("is_publish", true)
                    }
                }
            }
            .decodeList()
    }

suspend fun updateIngredient(id: Long, ingredient: Ingredient): Ingredient? =
    withContext(Dispatchers.IO) {
        try {
            supabase.from(TABLE)
                .update(ingredient) { filter { eq("ingredientId", id) }; select() }
                .decodeSingleOrNull<Ingredient>()
        } catch (e: Exception) { Log.e(TAG, "Update failed", e); null }
    }

suspend fun deleteIngredient(id: Long): Boolean =
    withContext(Dispatchers.IO) {
        try {
            supabase.from(TABLE).delete { filter { eq("ingredientId", id) } }
            true
        } catch (e: Exception) { Log.e(TAG, "Delete failed", e); false }
    }

suspend fun downloadImageToFiles(context: Context, url: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val bytes = URL(url).openStream().use { it.readBytes() }
            val dir = File(context.filesDir, "ingredient_images").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.writeBytes(bytes); file.absolutePath
        } catch (e: Exception) { Log.e(TAG, "Download failed", e); null }
    }

suspend fun uploadIngredientImage(uri: Uri, context: Context): String? =
    withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val fileName = "ingredients/${UUID.randomUUID()}.jpg"
            supabase.storage.from("ingredient-images").upload(fileName, bytes) {
                contentType = ContentType.Image.JPEG
            }
            supabase.storage.from("ingredient-images").publicUrl(fileName)
        } catch (e: Exception) { Log.e(TAG, "Upload failed", e); null }
    }