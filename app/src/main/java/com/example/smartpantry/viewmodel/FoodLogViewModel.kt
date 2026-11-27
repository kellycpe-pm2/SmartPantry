package com.example.smartpantry.Screen.FoodLog

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.model.FoodLog
import com.example.smartpantry.model.base
import com.example.smartpantry.model.totalText
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

class FoodLogViewModel : ViewModel() {
    // history
    var month by mutableStateOf(YearMonth.now()); private set
    var records by mutableStateOf<List<FoodLog>>(emptyList()); private set
    var typeFilter by mutableStateOf("All"); private set           // All / Consumed / Wasted
    var error by mutableStateOf<String?>(null); private set

    // analysis
    var days by mutableStateOf(30); private set
    var analysis by mutableStateOf<List<FoodLog>>(emptyList()); private set

    val shown get() = records.filter { typeFilter == "All" || it.type == typeFilter }

    init { loadMonth() }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            error = null
            try { block() } catch (e: Exception) { error = e.message ?: "Something went wrong" }
        }
    }

    fun changeMonth(delta: Long) { month = month.plusMonths(delta); loadMonth() }   // one month at a time
    fun changeType(v: String) { typeFilter = v }
    fun loadMonth() = run { records = FoodLogRepository.between(month.atDay(1), month.plusMonths(1).atDay(1)) }

    fun save(r: FoodLog, done: () -> Unit) = run {
        if (r.id == null) FoodLogRepository.add(r) else FoodLogRepository.update(r.id, r)
        loadMonth(); done()
    }
    fun delete(r: FoodLog) = run { r.id?.let { FoodLogRepository.delete(it) }; loadMonth() }
    fun expiredToWaste(items: List<FoodLog>) = run { FoodLogRepository.addExpired(items); loadMonth() }

    fun loadAnalysis(d: Int = days) = run {
        days = d
        analysis = FoodLogRepository.between(LocalDate.now().minusDays(d - 1L), LocalDate.now().plusDays(1))
    }

    // analysis data
    fun consumed() = analysis.filter { it.type == "Consumed" }
    fun wasted() = analysis.filter { it.type == "Wasted" }
    fun wasteByCategory() = wasted().groupBy { it.category }.mapValues { e -> e.value.sumOf { it.base() } }.toList()
    fun weekly() = analysis.groupBy { LocalDate.parse(it.date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSortedMap().map { (week, l) -> Triple(week, l.filter { it.type == "Consumed" }.sumOf { it.base() }, l.filter { it.type == "Wasted" }.sumOf { it.base() }) }
    fun monthly() = wasted().groupBy { YearMonth.from(LocalDate.parse(it.date)) }.toSortedMap().map { (m, l) -> m to l.sumOf { it.base() } }

    /** FN-F-P-8 : returns true if saved */
    fun exportPdf(context: Context): Boolean = try {
        val lines = buildList {
            add("SmartPantry Food Analysis" to 20f)
            add("Last $days days" to 14f)
            add("Consumed: ${consumed().totalText()}" to 14f)
            add("Wasted: ${wasted().totalText()}" to 14f)
            add("Waste by category" to 16f); wasteByCategory().forEach { add("  ${it.first}: ${"%.0f".format(it.second)}" to 14f) }
            add("Weekly (consumed / wasted)" to 16f); weekly().forEach { add("  ${it.first}: ${"%.0f".format(it.second)} / ${"%.0f".format(it.third)}" to 14f) }
            add("Monthly waste trend" to 16f); monthly().forEach { add("  ${it.first}: ${"%.0f".format(it.second)}" to 14f) }
        }
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val paint = Paint(); var y = 50f
        lines.forEach { (text, size) -> paint.textSize = size; page.canvas.drawText(text, 40f, y, paint); y += size + 10 }
        doc.finishPage(page)

        val name = "smartpantry_report_${LocalDate.now()}.pdf"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use { doc.writeTo(it) }
        } else {   // API 24-28: app's own Downloads folder, no permission needed
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name).outputStream().use { doc.writeTo(it) }
        }
        doc.close(); true
    } catch (e: Exception) { error = e.message; false }
}