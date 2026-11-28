package com.example.smartpantry.Screen.FoodLog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartpantry.model.CATEGORIES
import com.example.smartpantry.model.FoodLog
import com.example.smartpantry.model.UNITS
import com.example.smartpantry.model.validate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shownDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

/* ===================== Add / Edit Record screen ===================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLogRecordScreen(vm: FoodLogViewModel, onBack: () -> Unit) {
    val initial = remember { vm.editing }          // null = Add, otherwise Edit
    val edit = initial != null
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)

    var type by remember { mutableStateOf(initial?.type ?: "Consumed") }
    var name by remember { mutableStateOf(initial?.foodName ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: "") }
    var qty by remember { mutableStateOf(initial?.quantity?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "kg") }
    var date by remember { mutableStateOf(initial?.date?.let { LocalDate.parse(it) } ?: LocalDate.now()) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // title row with back button
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(if (edit) "Edit Record" else "Add Record", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        if (edit) {      // type badge + created date (both read-only)
            val added = initial?.createdAt?.take(10)?.let { runCatching { LocalDate.parse(it).format(shownDate) }.getOrNull() }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = scheme.primaryContainer) {
                    Text(type, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                if (added != null) Text("Added $added", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        } else {
            TypeToggle(type) { type = it }
        }

        Labelled("Food Item Name") {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, shape = shape,
                placeholder = { Text("e.g. Baby spinach") })
        }
        Labelled("Category") { Dropdown(cat, "Select category", CATEGORIES, Modifier.fillMaxWidth()) { cat = it } }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Labelled("Quantity") {
                    OutlinedTextField(qty, { qty = it }, Modifier.fillMaxWidth(), singleLine = true, shape = shape, placeholder = { Text("0.00") })
                }
            }
            Column(Modifier.weight(0.6f)) { Labelled("Unit") { Dropdown(unit, "Unit", UNITS) { unit = it } } }
        }

        Labelled("Date") {
            OutlinedTextField(date.format(shownDate), {}, Modifier.fillMaxWidth(), readOnly = true, singleLine = true, shape = shape,
                trailingIcon = { IconButton({ pickDate = true }) { Icon(Icons.Default.CalendarMonth, "Pick date") } })
        }

        Labelled("Notes (optional)") {
            OutlinedTextField(notes, { if (it.length <= 255) notes = it }, Modifier.fillMaxWidth(), minLines = 3, shape = shape,
                placeholder = { Text("Anything you'd like to remember...") })
        }

        error?.let { Text(it, color = scheme.error) }
        vm.error?.let { Text(it, color = scheme.error) }

        Button(
            onClick = {
                error = validate(type, name, cat, qty, unit, date.toString())
                if (error == null) {
                    vm.save(FoodLog(
                        id = initial?.id, type = type, foodName = name.trim(), category = cat,
                        quantity = qty.toDouble(), unit = unit, date = date.toString(), notes = notes.ifBlank { null },
                        ingredientUserId = initial?.ingredientUserId, createdAt = initial?.createdAt
                    )) { onBack() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer)
        ) { Text(if (edit) "Save Changes" else "Save Record", fontWeight = FontWeight.SemiBold) }

        if (edit) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, scheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error)
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete Record")
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickDate = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }

    if (confirmDelete) {
        DeleteDialog(
            onConfirm = { initial?.let { vm.delete(it) }; confirmDelete = false; onBack() },
            onCancel = { confirmDelete = false }
        )
    }
}

@Composable
private fun TypeToggle(type: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp)).padding(4.dp)
    ) {
        listOf("Consumed", "Wasted").forEach { t ->
            val selected = type == t
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (selected) scheme.primaryContainer else Color.Transparent)
                    .clickable { onChange(t) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text(t, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(value: String, placeholder: String, options: List<String>, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(
            value, {}, Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(), readOnly = true, singleLine = true,
            shape = RoundedCornerShape(12.dp), placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }
        )
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { DropdownMenuItem({ Text(it) }, { onPick(it); open = false }) }
        }
    }
}