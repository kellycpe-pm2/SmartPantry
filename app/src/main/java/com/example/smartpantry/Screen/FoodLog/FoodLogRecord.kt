package com.example.smartpantry.Screen.FoodLog

import com.example.smartpantry.model.FoodLog
import com.example.smartpantry.model.validate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.smartpantry.model.CATEGORIES
import com.example.smartpantry.model.UNITS
import java.time.LocalDate

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Add Record (initial = null) and Edit Record (initial = existing) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLogDialog(initial: FoodLog?, onDismiss: () -> Unit, onSave: (FoodLog) -> Unit) {
    val edit = initial != null
    var type by remember { mutableStateOf(initial?.type ?: "Consumed") }
    var name by remember { mutableStateOf(initial?.foodName ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: "") }
    var qty by remember { mutableStateOf(initial?.quantity?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var cost by remember { mutableStateOf(initial?.wastedCost?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (edit) "Edit Record" else "Add Record") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Consumed", "Wasted").forEach {
                        FilterChip(type == it, { type = it }, { Text(it) }, enabled = !edit)   // type is read-only when editing
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Food name") }, singleLine = true)
                Dropdown("Food category", cat, CATEGORIES) { cat = it }
                OutlinedTextField(qty, { qty = it }, label = { Text("Quantity") }, singleLine = true)
                Dropdown("Unit", unit, UNITS) { unit = it }
                OutlinedTextField(
                    date,
                    { date = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    singleLine = true
                )

                if (type == "Wasted") {
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { cost = it },
                        label = { Text("Wasted Cost (RM)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    notes,
                    { if (it.length <= 255) notes = it },
                    label = { Text("Note (optional)") }
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button({
                val parsedCost = cost.toDoubleOrNull()

                error = validate(type, name, cat, qty, unit, date)
                    ?: if (
                        type == "Wasted" &&
                        cost.isNotBlank() &&
                        (parsedCost == null || !parsedCost.isFinite() || parsedCost < 0)
                    ) {
                        "Please enter a valid cost (0.00 or more)"
                    } else {
                        null
                    }
                if (error == null) onSave(
                    FoodLog(
                        id = initial?.id,
                        type = type,
                        foodName = name,
                        category = cat,
                        quantity = qty.toDouble(),
                        unit = unit,
                        date = date,
                        notes = notes.ifBlank { null },
                        wastedCost = if (type == "Wasted") cost.toDoubleOrNull() ?: 0.0 else 0.0,
                        ingredientUserId = initial?.ingredientUserId,
                        isAuto = initial?.isAuto ?: false
                    )
                )
            }) { Text(if (edit) "Save Changes" else "Save Record") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { DropdownMenuItem({ Text(it) }, { onPick(it); open = false }) }
        }
    }
}