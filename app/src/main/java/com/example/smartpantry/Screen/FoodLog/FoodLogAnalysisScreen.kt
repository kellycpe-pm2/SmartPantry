package com.example.smartpantry.Screen.FoodLog

import com.example.smartpantry.model.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun FoodLogAnalysisScreen(vm: FoodLogViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var downloaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadAnalysis() }

    val consumedColor = MaterialTheme.colorScheme.primary
    val wastedColor = MaterialTheme.colorScheme.error
    val c = MaterialTheme.colorScheme
    // theme has one teal, so make donut slices from shades between primary and primaryContainer
    val palette = List(6) { i -> lerp(c.primary, c.primaryContainer, i / 6f) }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Analysis", style = MaterialTheme.typography.titleLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {                      // period (always valid)
            listOf(7, 30, 90).forEach { FilterChip(vm.days == it, { vm.loadAnalysis(it) }, { Text("$it days") }) }
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("Consumed", vm.consumed().totalText(), Modifier.weight(1f))
            SummaryCard("Wasted", "${vm.wasted().totalText()} · ${vm.wasted().costText()}", Modifier.weight(1f))
        }

        // waste by category (donut)
        val cat = vm.wasteByCategory(); val catTotal = cat.sumOf { it.second }
        Text("Waste by category", style = MaterialTheme.typography.titleSmall)
        Canvas(Modifier.size(160.dp).align(Alignment.CenterHorizontally)) {
            var start = -90f
            cat.forEachIndexed { i, (_, v) ->
                val sweep = (v / catTotal * 360).toFloat()
                drawArc(palette[i % palette.size], start, sweep, false, Offset(18f, 18f), Size(size.width - 36f, size.height - 36f), style = Stroke(36f))
                start += sweep
            }
        }
        cat.forEachIndexed { i, (name, v) -> Text("● $name  ${"%.0f".format(v / catTotal * 100)}%", color = palette[i % palette.size]) }

        // weekly bars: consumed vs wasted
        val weeks = vm.weekly(); val weekMax = weeks.maxOfOrNull { maxOf(it.second, it.third) }?.takeIf { it > 0 } ?: 1.0
        Text("Weekly: consumed vs wasted", style = MaterialTheme.typography.titleSmall)
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / maxOf(weeks.size, 1); val bar = slot / 3
            weeks.forEachIndexed { i, (_, cons, waste) ->
                val h1 = (cons / weekMax * size.height).toFloat(); val h2 = (waste / weekMax * size.height).toFloat()
                drawRect(consumedColor, Offset(i * slot + bar / 2, size.height - h1), Size(bar, h1))
                drawRect(wastedColor, Offset(i * slot + bar * 1.5f, size.height - h2), Size(bar, h2))
            }
        }

        // monthly trend (line)
        val months = vm.monthly(); val monthMax = months.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
        Text("Monthly waste trend", style = MaterialTheme.typography.titleSmall)
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val step = if (months.size > 1) size.width / (months.size - 1) else 0f
            val path = Path()
            months.forEachIndexed { i, (_, v) ->
                val p = Offset(i * step, size.height - (v / monthMax * size.height).toFloat())
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                drawCircle(wastedColor, 6f, p)
            }
            drawPath(path, wastedColor, style = Stroke(4f))
        }

        Button({ downloaded = vm.exportPdf(context) }, enabled = vm.analysis.isNotEmpty()) { Text("Download PDF Report") }
        if (downloaded) Text("Report downloaded successfully")
    }
}