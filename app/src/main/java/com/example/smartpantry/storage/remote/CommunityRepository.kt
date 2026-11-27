package com.example.smartpantry.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

internal class CommunityRepository {
    fun userId(): String = supabase.auth.currentUserOrNull()?.id ?: error("Please sign in again.")
    suspend fun call(action: String, payload: JsonObject = buildJsonObject {}): JsonObject = withContext(Dispatchers.IO) {
        supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        userId()
        val response = supabase.postgrest.rpc(if(action in setOf("feed","draft","save_recipe","catalog")) "community_v6" else "community_api", buildJsonObject {
            put("p_action", action); put("p_payload", payload)
        })
        Json.parseToJsonElement(response.data) as? JsonObject ?: error("The server returned an unexpected response.")
    }
    suspend fun v5(action:String,payload:JsonObject=buildJsonObject{}):JsonObject=withContext(Dispatchers.IO) {
        supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }; userId()
        Json.parseToJsonElement(supabase.postgrest.rpc("community_v6",buildJsonObject { put("p_action",action);put("p_payload",payload) }).data).jsonObject
    }
    suspend fun recommendations(search:String="",page:Int=0):JsonObject=withContext(Dispatchers.IO) {
        supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }; userId()
        Json.parseToJsonElement(supabase.postgrest.rpc("community_for_you_v6",buildJsonObject { put("p_search",search);put("p_page",page) }).data).jsonObject
    }
    /** Copy immediately: no dependency on a gallery permission surviving Activity recreation. */
    suspend fun importImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "community-photos").apply { mkdirs() }
        val raw = File.createTempFile("source_", ".image", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                raw.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 20 * 1024 * 1024) { "Choose an image smaller than 20 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Cannot open the selected image. Try another photo.")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(raw.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image format is not supported." }
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / inSampleSize > 1600 || bounds.outHeight / inSampleSize > 1600) inSampleSize *= 2
            }
            val decoded = BitmapFactory.decodeFile(raw.path, options) ?: error("Cannot read the photo.")
            val orientation = runCatching { ExifInterface(raw.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f,1f) }
                    ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f,1f) }
                }
            }
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val destination = File(dir, "${UUID.randomUUID()}.jpg")
            destination.outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
            destination.absolutePath
        } finally { raw.delete() }
    }
    suspend fun uploadImage(path: String): String = withContext(Dispatchers.IO) {
        if (path.isBlank() || path.startsWith("https://")) return@withContext path
        val file = File(path)
        require(file.exists() && file.length() <= 5 * 1024 * 1024) { "Choose a photo smaller than 5 MB." }
        val objectPath = "${userId()}/${UUID.randomUUID()}.jpg"
        val bucket = supabase.storage.from("community-images")
        bucket.upload(objectPath, file.readBytes()) { contentType = ContentType.Image.JPEG }
        bucket.publicUrl(objectPath)
    }
}