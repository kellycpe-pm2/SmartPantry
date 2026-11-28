package com.example.smartpantry.Screen.FoodLog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartpantry.model.FoodLog
import com.example.smartpantry.model.costText
import com.example.smartpantry.model.totalText
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shownDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val cardColors @Composable get() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)

/* ===================== Main screen (monthly list) ===================== */
@Composable
fun FoodLogScreen(
    vm: FoodLogViewModel,
    onAnalysis: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (FoodLog) -> Unit
) {
    var deleting by remember { mutableStateOf<FoodLog?>(null) }
    val list = vm.shown

    Scaffold(floatingActionButton = {
        FloatingActionButton(onAdd) { Icon(Icons.Default.Add, "Add record") }
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ vm.changeMonth(-1) }) { Icon(Icons.Default.ChevronLeft, "Previous month") }
                Text("${vm.month.month} ${vm.month.year}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton({ vm.changeMonth(1) }, enabled = vm.month < YearMonth.now()) { Icon(Icons.Default.ChevronRight, "Next month") }
                TextButton(onAnalysis) { Text("Analysis") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Consumed", vm.records.filter { it.type == "Consumed" }.totalText(), Modifier.weight(1f))
                SummaryCard("Wasted", vm.records.filter { it.type == "Wasted" }.let { "${it.totalText()} · ${it.costText()}" }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Consumed", "Wasted").forEach { FilterChip(vm.typeFilter == it, { vm.changeType(it) }, { Text(it) }) }
            }
            Text("${list.size} entries", style = MaterialTheme.typography.labelLarge)
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { r ->
                    Card(Modifier.fillMaxWidth(), colors = cardColors) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.foodName, style = MaterialTheme.typography.titleSmall)
                                Text("${r.type} · ${r.category} · ${r.quantity} ${r.unit} · ${r.date}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ onEdit(r) }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton({ deleting = r }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { r -> DeleteDialog(onConfirm = { vm.delete(r); deleting = null }, onCancel = { deleting = null }) }
}

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = cardColors) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun DeleteDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        text = { Text("Delete this record? This action cannot be undone.") },
        confirmButton = { TextButton(onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onCancel) { Text("Cancel") } }
    )
}