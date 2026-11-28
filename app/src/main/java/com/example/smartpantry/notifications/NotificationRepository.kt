package com.example.smartpantry.notifications

import android.content.Context
import com.example.smartpantry.community.*
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

internal object NotificationBadge { val count = MutableStateFlow(0L) }

internal class NotificationRepository(
    context: Context,
    private val account: String? = supabase.auth.currentUserOrNull()?.id
) {
    private companion object {
        val localLock = Any()
        val requests = Mutex()
        val changes = MutableStateFlow(0L)
    }

    val updates get() = changes
    fun owner(): String = checkNotNull(account) { "Please sign in again." }
    private val prefs = account?.let { context.getSharedPreferences("notifications_$it", Context.MODE_PRIVATE) }

    private fun read(name: String): JsonObject {
        val stored = prefs?.getString(name, null) ?: return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(stored).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    }

    private fun visible(
        snapshot: JsonObject = read("snapshot"),
        timers: JsonObject = read("timers_pending"),
        decisions: JsonObject = read("decisions_pending")
    ): JsonObject {
        val settings = snapshot["settings"] as? JsonObject ?: JsonObject(emptyMap())
        val localTimers = if (settings["timers"]?.jsonPrimitive?.booleanOrNull != false)
            timers.values.map { it.jsonObject } else emptyList()
        val sync = if (settings["sync"]?.jsonPrimitive?.booleanOrNull != false)
            read("shopping_issue").takeIf { it.isNotEmpty() && it.str("cleared_at").isBlank() } else null
        val rows = (snapshot.rows("items").filter { it.str("kind") != "sync" } + localTimers + listOfNotNull(sync))
            .distinctBy { it.str("event_key") }
            .mapNotNull { row ->
                when (decisions[row.str("event_key")]?.jsonPrimitive?.contentOrNull) {
                    "clear" -> null
                    "read" -> JsonObject(row + ("read_at" to JsonPrimitive("local")))
                    else -> row
                }
            }
            .sortedByDescending { runCatching { Instant.parse(it.str("created_at")) }.getOrDefault(Instant.EPOCH) }
        return JsonObject(snapshot + mapOf("items" to JsonArray(rows), "unread" to JsonPrimitive(rows.count { it.str("read_at").isBlank() })))
    }

    fun cached(): JsonObject = synchronized(localLock) { visible() }
    fun settings(): JsonObject = cached()["settings"] as? JsonObject ?: JsonObject(emptyMap())

    private fun publish() {
        if (account != null && account == supabase.auth.currentUserOrNull()?.id) {
            NotificationBadge.count.value = visible().long("unread")
        }
        changes.value++
    }

    fun updateShoppingIssue(hasPending: Boolean) = synchronized(localLock) {
        val store = prefs ?: return@synchronized
        val previous = read("shopping_issue")
        if (hasPending == previous.isNotEmpty()) return@synchronized
        val editor = store.edit().remove("pending_sync_issue")
        if (hasPending) {
            val row = buildJsonObject {
                put("event_key", "sync:${UUID.randomUUID()}")
                put("category", "System")
                put("kind", "sync")
                put("title", "Shopping changes are waiting to sync")
                put("body", "Open your shopping list to retry when connected.")
                put("created_at", Instant.now().toString())
            }
            editor.putString("shopping_issue", row.toString())
        } else editor.remove("shopping_issue")
        if (editor.commit()) publish()
    }

    fun recordTimer(token: String, title: String, body: String, recipeTarget: Long, ended: Long) = synchronized(localLock) {
        val store = prefs ?: return@synchronized
        val key = "timer:$token"
        val pending = read("timers_pending")
        if (key in pending || read("snapshot").rows("items").any { it.str("event_key") == key }) return@synchronized
        val row = buildJsonObject {
            put("event_key", key)
            put("category", "System")
            put("kind", "timer")
            put("title", title)
            put("body", body)
            put("recipe_target", recipeTarget)
            put("created_at", Instant.ofEpochMilli(ended).toString())
        }
        check(store.edit().putString("timers_pending", JsonObject(pending + (key to row)).toString()).commit()) {
            "Could not save the timer notification."
        }
        publish()
    }

    fun changeLocal(rows: List<JsonObject>, clear: Boolean) = synchronized(localLock) {
        if (account != supabase.auth.currentUserOrNull()?.id) return@synchronized
        val store = prefs ?: return@synchronized
        val pending = read("decisions_pending").toMutableMap()
        var issue = read("shopping_issue")
        rows.forEach { row ->
            val key = row.str("event_key")
            if (key.isBlank()) return@forEach
            if (row.str("kind") == "sync") {
                if (key == issue.str("event_key")) {
                    val field = if (clear) "cleared_at" else "read_at"
                    issue = JsonObject(issue + (field to JsonPrimitive(Instant.now().toString())))
                }
            } else if (pending[key]?.jsonPrimitive?.content != "clear") {
                pending[key] = JsonPrimitive(if (clear) "clear" else "read")
            }
        }
        check(store.edit().putString("decisions_pending", JsonObject(pending).toString())
            .putString("shopping_issue", issue.toString()).commit()) { "Could not save notification changes." }
        publish()
    }

    suspend fun call(action: String = "list", payload: JsonObject = JsonObject(emptyMap())): JsonObject =
        withContext(Dispatchers.IO) {
            requests.withLock {
                val uid = owner()
                val store = checkNotNull(prefs)
                suspend fun rpc(operation: String, values: JsonObject = JsonObject(emptyMap())): JsonObject {
                    check(supabase.auth.currentUserOrNull()?.id == uid) { "Account changed." }
                    val response = supabase.postgrest.rpc("notifications_api", buildJsonObject {
                        put("p_action", operation)
                        put("p_payload", JsonObject(values + ("owner_id" to JsonPrimitive(uid))))
                    })
                    check(supabase.auth.currentUserOrNull()?.id == uid) { "Account changed." }
                    return Json.parseToJsonElement(response.data).jsonObject
                }

                if (action == "settings" || action == "save_settings") {
                    val result = rpc(action, payload)
                    synchronized(localLock) {
                        store.edit().putString("snapshot", JsonObject(read("snapshot") + ("settings" to result)).toString())
                            .putBoolean("push", result.flag("push"))
                            .putBoolean("timers_enabled", result["timers"]?.jsonPrimitive?.booleanOrNull != false)
                            .apply()
                        publish()
                    }
                    return@withLock result
                }

                val (timers, decisions, oldReads) = synchronized(localLock) {
                    Triple(read("timers_pending"), read("decisions_pending"),
                        store.getStringSet("pending_reads", emptySet())?.toSet().orEmpty())
                }
                timers.values.forEach { rpc("timer_complete", it.jsonObject) }
                oldReads.mapNotNull(String::toLongOrNull).forEach { id -> rpc("read", buildJsonObject { put("id", id) }) }
                val result = if (decisions.isEmpty()) rpc(action, payload)
                else rpc("apply_decisions", buildJsonObject { put("decisions", JsonObject(decisions)) })
                synchronized(localLock) {
                    val stillPending = read("decisions_pending").filter { (key, value) -> decisions[key] != value }
                    val settings = result["settings"]?.jsonObject ?: JsonObject(emptyMap())
                    store.edit()
                        .putString("snapshot", result.toString())
                        .putString("timers_pending", JsonObject(read("timers_pending").filterKeys { it !in timers }).toString())
                        .putString("decisions_pending", JsonObject(stillPending).toString())
                        .remove("pending_reads").remove("pending_sync_issue")
                        .putBoolean("push", settings.flag("push"))
                        .putBoolean("timers_enabled", settings["timers"]?.jsonPrimitive?.booleanOrNull != false)
                        .apply()
                    publish()
                    visible()
                }
            }
        }

    fun claimDeviceAlert(row: JsonObject): Boolean = synchronized(localLock) {
        val store = prefs ?: return@synchronized false
        if (account != supabase.auth.currentUserOrNull()?.id) return@synchronized false
        val event = row.str("event_key")
        if (visible().rows("items").none { it.str("event_key") == event && it.str("read_at").isBlank() })
            return@synchronized false
        val key = if (row.long("id") > 0) "sent_${row.long("id")}" else "sent_$event"
        if (store.getBoolean(key, false)) return@synchronized false
        store.edit().putBoolean(key, true).commit()
    }
}
