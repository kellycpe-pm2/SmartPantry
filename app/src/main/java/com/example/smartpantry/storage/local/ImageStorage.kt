package com.example.smartpantry.storage.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Copies the picked image (from camera OR gallery) into the app's internal filesDir.
 * Returns the absolute file path (e.g. /data/user/0/.../files/ingredient_images/abc.jpg).
 * This ensures the image is never deleted by the OS.
 */
fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "ingredient_images").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")

        // 1. Open the input stream from the URI (works for content://, file://, etc.)
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Log.e("ImageStorage", "Failed to open input stream for URI: $uri")
            return null
        }

        // 2. Decode the image into a Bitmap
        val original = inputStream.use { BitmapFactory.decodeStream(it) }
        if (original == null) {
            Log.e("ImageStorage", "Failed to decode bitmap from URI: $uri")
            return null
        }

        // 3. Downscale to max 800px to prevent UI jank
        val maxSide = 800
        val scale = maxSide.toFloat() / maxOf(original.width, original.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true
            )
        } else original

        // 4. Write the bitmap to the permanent local file
        file.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        // 5. Clean up memory
        if (scaled !== original) original.recycle()
        scaled.recycle()

        Log.d("ImageStorage", "Successfully saved image to: ${file.absolutePath}")
        file.absolutePath

    } catch (e: Exception) {
        Log.e("ImageStorage", "Error saving image", e)
        null
    }
}

/** Resolves a saved path back to a File or Uri for Coil to display. */
fun imageModelFor(path: String?): Any? {
    if (path.isNullOrBlank()) return null
    return when {
        path.startsWith("content://") || path.startsWith("file://") || path.startsWith("http") ->
            Uri.parse(path)
        else -> File(path) // Local file path saved by saveImageToInternalStorage
    }
}

/** Deletes a local image file if it exists. */
fun deleteLocalImage(path: String?) {
    if (path.isNullOrBlank()) return
    if (path.startsWith("content://") || path.startsWith("http")) return
    try { File(path).delete() } catch (_: Exception) {}
}