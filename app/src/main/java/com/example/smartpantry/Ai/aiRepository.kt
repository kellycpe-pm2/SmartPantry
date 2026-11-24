
package com.example.smartpantry.Ai

import com.example.smartpantry.storage.remote.SUPABASE_PUBLISHABLE_KEY
import com.example.smartpantry.storage.remote.SUPABASE_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class IngredientAiSuggestion(
    val normalizedName: String,
    val category: String,
    val unit: String,
    val minExpiryDays: Int,
    val maxExpiryDays: Int,
    val description: String,
    val storageAdvice: String
)

data class IngredientAiChatMessage(
    val role: String,
    val text: String
)

object IngredientAiRepository {

    private const val FUNCTION_NAME = "ingredient-assistant"

    suspend fun suggest(
        accessToken: String,
        ingredientName: String
    ): IngredientAiSuggestion = withContext(Dispatchers.IO) {

        require(accessToken.isNotBlank()) {
            "Supabase access token is empty. Please sign in again."
        }

        val request = JSONObject().apply {
            put("action", "suggest")
            put("ingredientName", ingredientName)
        }

        val response = post(accessToken, request)
        val suggestion = response.getJSONObject("suggestion")

        IngredientAiSuggestion(
            normalizedName = suggestion.optString("normalizedName"),
            category = suggestion.optString("category"),
            unit = suggestion.optString("unit"),
            minExpiryDays = suggestion.optInt("minExpiryDays"),
            maxExpiryDays = suggestion.optInt("maxExpiryDays"),
            description = suggestion.optString("description"),
            storageAdvice = suggestion.optString("storageAdvice")
        )
    }

    suspend fun chat(
        accessToken: String,
        ingredientName: String,
        message: String,
        history: List<IngredientAiChatMessage>
    ): String = withContext(Dispatchers.IO) {

        require(accessToken.isNotBlank()) {
            "Supabase access token is empty. Please sign in again."
        }

        val historyArray = JSONArray()

        history.forEach { item ->
            historyArray.put(
                JSONObject().apply {
                    put("role", item.role)
                    put("text", item.text)
                }
            )
        }

        val request = JSONObject().apply {
            put("action", "chat")
            put("ingredientName", ingredientName)
            put("message", message)
            put("history", historyArray)
        }

        val response = post(accessToken, request)

        response.optString("reply")
    }

    private fun post(
        accessToken: String,
        body: JSONObject
    ): JSONObject {

        require(accessToken.isNotBlank()) {
            "Supabase access token is empty."
        }

        require(SUPABASE_URL.isNotBlank()) {
            "Supabase URL is missing."
        }

        require(SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Supabase publishable key is missing."
        }

        val endpoint =
            "${SUPABASE_URL.trimEnd('/')}/functions/v1/$FUNCTION_NAME"

        val connection =
            URL(endpoint).openConnection() as HttpURLConnection

        try {
            connection.apply {
                requestMethod = "POST"
                connectTimeout = 30000
                readTimeout = 60000
                doOutput = true

                // Do not silently follow redirects.
                instanceFollowRedirects = false

                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                setRequestProperty(
                    "apikey",
                    SUPABASE_PUBLISHABLE_KEY
                )

                // IMPORTANT:
                // This must be the signed-in user's Supabase JWT.
                setRequestProperty(
                    "Authorization",
                    "Bearer $accessToken"
                )

                setRequestProperty(
                    "Accept",
                    "application/json"
                )
            }

            // Send JSON request body.
            connection.outputStream.use { output ->
                output.write(
                    body.toString().toByteArray(Charsets.UTF_8)
                )
                output.flush()
            }

            val statusCode = connection.responseCode

            // Detect redirects instead of silently following them.
            if (statusCode in 300..399) {
                val redirectUrl =
                    connection.getHeaderField("Location")

                throw Exception(
                    "Supabase redirected the request ($statusCode). " +
                            "Check SUPABASE_URL. Redirect: $redirectUrl"
                )
            }

            val responseStream =
                if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText = responseStream?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }.orEmpty()

            if (statusCode !in 200..299) {

                val errorMessage = try {
                    val errorJson = JSONObject(responseText)

                    errorJson.optString(
                        "message",
                        errorJson.optString(
                            "error",
                            errorJson.optString(
                                "msg",
                                "Unknown server error"
                            )
                        )
                    )
                } catch (_: Exception) {
                    responseText.ifBlank {
                        "Unknown server error"
                    }
                }

                throw Exception(
                    "Supabase request failed ($statusCode): $errorMessage"
                )
            }

            return JSONObject(responseText)

        } finally {
            connection.disconnect()
        }
    }
}
