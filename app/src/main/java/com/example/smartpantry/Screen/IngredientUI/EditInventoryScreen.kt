package com.example.smartpantry.Screen.IngredientUI

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.community.RecipePhoto
import com.example.smartpantry.community.SmartPantrySectionHeader
import com.example.smartpantry.community.quantityText
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import com.example.smartpantry.viewmodel.IngredientViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EditInventoryScreen(
    itemId: Long,
    onBackClick: () -> Unit,
    vm: IngredientUserViewModel = viewModel(),
    ingredientVm: IngredientViewModel = viewModel()
) {
    val inventory by vm.inventory.collectAsState()
    val allIngredients by ingredientVm.ingredients.collectAsState()
    val scheme = MaterialTheme.colorScheme

    val item = inventory.find { it.id == itemId }
    val ingredientInfo = allIngredients.find { it.ingredientId == item?.ingredientId }

    var qty by remember(item) { mutableStateOf(item?.quantity?.let { quantityText(it) } ?: "1") }
    var unit by remember(item) { mutableStateOf(item?.quantityUnit ?: "pcs") }
    var location by remember(item) { mutableStateOf(item?.locationStorage ?: "Pantry") }
    var days by remember(item) {
        mutableStateOf(
            ((item?.expiredDate ?: 0L) - System.currentTimeMillis())
                .let { if (it < 0) 0 else it / (24 * 60 * 60 * 1000) }
                .toString()
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        SmartPantrySectionHeader(
            title = "Edit pantry item",
            showBack = true,
            onBack = onBackClick,
            action = {
                if (item?.id != null) {
                    IconButton(onClick = {
                        vm.deleteInventoryItem(item.id)
                        onBackClick()
                    }) {
                        Icon(Icons.Default.DeleteOutline, "Delete item", tint = scheme.error)
                    }
                }
            }
        )

        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val currentExpiry = dateFormat.format(Date(item.expiredDate))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Item Summary Surface
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = scheme.surfaceVariant,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val imgUrl = ingredientInfo?.localImagePath?.takeIf { File(it).exists() }
                        ?: ingredientInfo?.image.orEmpty()
                    RecipePhoto(
                        url = imgUrl,
                        modifier = Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)),
                        recipeFallback = false
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            ingredientInfo?.ingredientName ?: "Pantry Item",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Current expiry: $currentExpiry",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }

            errorMessage?.let { msg ->
                Surface(shape = RoundedCornerShape(12.dp), color = scheme.errorContainer) {
                    Text(msg, Modifier.fillMaxWidth().padding(12.dp), color = scheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("Quantity & unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.take(12); errorMessage = null },
                    label = { Text("Amount") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Box(Modifier.weight(1f)) {
                    CompactDropdownField(
                        label = "Unit",
                        value = unit,
                        options = listOf("g", "kg", "mL", "L", "pcs", "bag", "carton", "bunch"),
                        onSelected = { unit = it }
                    )
                }
            }

            Text("Storage location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Pantry", "Fridge", "Freezer").forEach { spot ->
                    FilterChip(
                        selected = location.equals(spot, ignoreCase = true),
                        onClick = { location = spot },
                        label = { Text(spot) }
                    )
                }
            }

            Text("Expiration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = days,
                onValueChange = { days = it.filter(Char::isDigit).take(4) },
                label = { Text("Expires in (days)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                supportingText = {
                    val previewDays = days.toLongOrNull() ?: 0
                    val previewDate = System.currentTimeMillis() + previewDays * 24 * 60 * 60 * 1000
                    Text("New expiry date: ${dateFormat.format(Date(previewDate))}")
                }
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val qtyDouble = qty.toDoubleOrNull()
                    if (qtyDouble == null || qtyDouble <= 0) {
                        errorMessage = "Quantity must be a positive number."
                        return@Button
                    }
                    val newExpiry = System.currentTimeMillis() + ((days.toLongOrNull() ?: 7L) * 24 * 60 * 60 * 1000)
                    val updated = item.copy(
                        quantity = qtyDouble,
                        quantityUnit = unit.trim(),
                        locationStorage = location.trim(),
                        expiredDate = newExpiry
                    )
                    item.id?.let { id -> vm.updateInventoryItem(id, updated) }
                    onBackClick()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}