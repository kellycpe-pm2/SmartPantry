package com.example.smartpantry.Screen.FoodLog   // change to your package

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items          // must be this import
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartpantry.model.FoodLog
import com.example.smartpantry.model.totalText
import java.time.YearMonth

@Composable
fun FoodLogScreen(vm: FoodLogViewModel, onAnalysis: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FoodLog?>(null) }
    var deleting by remember { mutableStateOf<FoodLog?>(null) }
    val list = vm.shown

    Scaffold(floatingActionButton = {
        FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, "Add record") }
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
                val wastedRecords = vm.records.filter { it.type == "Wasted" }

                SummaryCard(
                    "Wasted",
                    "${wastedRecords.totalText()} · RM ${"%.2f".format(wastedRecords.sumOf { it.wastedCost })}",
                    Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Consumed", "Wasted").forEach { FilterChip(vm.typeFilter == it, { vm.changeType(it) }, { Text(it) }) }
            }
            Text("${list.size} entries", style = MaterialTheme.typography.labelLarge)
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.foodName, style = MaterialTheme.typography.titleSmall)
                                Text("${r.type} · ${r.category} · ${r.quantity} ${r.unit} · ${r.date}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ editing = r }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton({ deleting = r }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }

    if (adding) FoodLogDialog(null, { adding = false }) { vm.save(it) { adding = false } }
    editing?.let { r -> FoodLogDialog(r, { editing = null }) { vm.save(it) { editing = null } } }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text("Delete this record? This action cannot be undone.") },
            confirmButton = { TextButton({ vm.delete(r); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } }
        )
    }
}