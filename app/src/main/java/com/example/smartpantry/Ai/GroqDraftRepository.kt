package com.example.smartpantry.Aipackage

import com.example.smartpantry.Screen.CommunityScreen.cleanDisplay
import com.example.smartpantry.Screen.CommunityScreen.cleanInput
import com.example.smartpantry.community.DraftIngredient
import com.example.smartpantry.community.RecipeDraft
import com.example.smartpantry.community.flag
import com.example.smartpantry.community.normalizedUnit
import com.example.smartpantry.community.number
import com.example.smartpantry.community.quantityText
import com.example.smartpantry.community.rows
import com.example.smartpantry.community.str
import com.example.smartpantry.community.words
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Only the user's Supabase session goes to our backend. The Groq key never enters the APK. */
internal class GroqDraftRepository {
    suspend fun create(name: String, catalog: List<JsonObject>): RecipeDraft = withContext(Dispatchers.IO) {
        val token=supabase.auth.currentSessionOrNull()?.accessToken ?: error("Please sign in again.")
        val connection=URL(supabase.supabaseUrl.trimEnd('/')+"/functions/v1/recipe-draft").openConnection() as HttpURLConnection
        try {
            connection.requestMethod="POST";connection.connectTimeout=12000;connection.readTimeout=40000;connection.doOutput=true
            connection.setRequestProperty("Authorization","Bearer $token")
            connection.setRequestProperty("apikey",supabase.supabaseKey)
            connection.setRequestProperty("Content-Type","application/json")
            connection.outputStream.use { it.write(buildJsonObject { put("name",name) }.toString().toByteArray(Charsets.UTF_8)) }
            val status=connection.responseCode
            val body=(if(status in 200..299)connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result=runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            if(status !in 200..299) error(when(status) {
                401,403 -> "Please sign in again to prepare an AI draft."
                404,503 -> "AI drafts are not available yet. Use a built-in template or create your recipe manually."
                429 -> "AI draft limit reached. Wait a while, or use a built-in recipe template."
                422 -> "A complete recipe could not be matched to your ingredient catalogue. Try another dish or create it manually."
                else -> "AI could not prepare this recipe right now. Please try again or choose a template."
            })
            val data=requireNotNull(result) { "The AI draft could not be read. Please try again." }
            val rows=data.rows("ingredients")
            val draft= RecipeDraft(
                title = cleanDisplay(data.str("title")).take(150),
                category = data.str("category", "Other"),
                description = cleanInput(data.str("description"), 5000, 8),
                vegan = data.flag("vegan"),
                source = "AI-generated draft via Groq · Check ingredients, quantities and cooking instructions before saving.",
                ingredients = rows.map { row ->
                    val rowId = row["id"]?.jsonPrimitive?.long
                        ?: error("Invalid ingredient ID in AI response.")

                    val known = catalog.firstOrNull {
                        it["id"]?.jsonPrimitive?.long == rowId
                    } ?: error("The draft contains an unavailable ingredient. Try another dish.")

                    DraftIngredient(
                        known["id"]?.jsonPrimitive?.long
                            ?: error("Invalid ingredient ID in catalogue."),
                        known.str("name"),
                        quantityText(row.number("quantity")),
                        normalizedUnit(row.str("unit")),
                        known.str("image")
                    )
                },
                steps = data.words("steps").map { cleanInput(it, 500, 2) })
            draft.problem()?.let { error("The AI draft was incomplete. Please try again or use a template.") }
            draft
        } finally { connection.disconnect() }
    }
}