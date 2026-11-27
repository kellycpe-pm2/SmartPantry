package com.example.smartpantry.Screen.FoodLog

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items          // must be this import
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.model.FoodLog
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
// import ....supabase      <- copy the same `supabase` import used in ProfileRepository.kt

object FoodLogRepository {
    private val table get() = supabase.postgrest["food_log"]

    suspend fun between(from: LocalDate, toExclusive: LocalDate): List<FoodLog> =
        table.select {
            filter { gte("record_date", from.toString()); lt("record_date", toExclusive.toString()) }
            order("record_date", Order.DESCENDING)
        }.decodeList()

    suspend fun add(r: FoodLog) { table.insert(r) }

    /** record type and created date are never sent (also locked by a DB trigger) */
    suspend fun update(id: Long, r: FoodLog) {
        table.update(buildJsonObject {
            put("food_name", r.foodName); put("food_category", r.category)
            put("food_quantity", r.quantity); put("unit", r.unit)
            put("record_date", r.date); put("food_log_notes", r.notes)
            put("wasted_cost", r.wastedCost)
        }) { filter { eq("food_log_id", id) } }
    }

    suspend fun delete(id: Long) { table.delete { filter { eq("food_log_id", id) } } }

    /** FN-F-P-1 / C-3 : call with the user's expired pantry items. The unique index
     *  (ingredient_user_id where is_auto) stops duplicates, so a second call is ignored. */
    suspend fun addExpired(items: List<FoodLog>) {
        items.forEach { runCatching { table.insert(it.copy(type = "Wasted", isAuto = true)) } }
    }
}