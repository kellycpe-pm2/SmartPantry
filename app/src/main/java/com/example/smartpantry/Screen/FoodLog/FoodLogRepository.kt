package com.example.smartpantry.Screen.FoodLog

import com.example.smartpantry.model.FoodLog
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
// import ....supabase   <- copy the same `supabase` import used in ProfileRepository.kt

object FoodLogRepository {
    private val table get() = supabase.postgrest["food_log"]

    /** records from [from] up to (not including) [until] */
    suspend fun between(from: LocalDate, until: LocalDate): List<FoodLog> =
        table.select {
            filter { gte("record_date", from.toString()); lt("record_date", until.toString()) }
            order("record_date", Order.DESCENDING)
        }.decodeList()

    suspend fun add(r: FoodLog) { table.insert(r) }

    /** record type and created date are not sent, so they stay read-only */
    suspend fun update(id: Long, r: FoodLog) {
        table.update(buildJsonObject {
            put("food_name", r.foodName); put("food_category", r.category)
            put("food_quantity", r.quantity); put("unit", r.unit)
            put("record_date", r.date); put("food_log_notes", r.notes)
        }) { filter { eq("food_log_id", id) } }
    }

    suspend fun delete(id: Long) { table.delete { filter { eq("food_log_id", id) } } }

    /** auto Wasted records for expired pantry items; the unique index ignores duplicates */
    suspend fun addExpired(items: List<FoodLog>) {
        items.forEach { runCatching { table.insert(it.copy(type = "Wasted", isAuto = true)) } }
    }
}