package com.example.smartpantry.Screen.IngredientUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.IngredientUser
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import com.example.smartpantry.viewmodel.IngredientViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    vm: IngredientUserViewModel = viewModel(),
    ingredientVm: IngredientViewModel = viewModel()
) {
    val inventory by vm.inventory.collectAsState()
    val availableIngredients by ingredientVm.ingredients.collectAsState()
    val scheme = MaterialTheme.colorScheme

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("My Inventory", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (inventory.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Your inventory is empty", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap + to add items to your pantry", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(inventory, key = { it.id ?: 0L }) { item ->
                        // Look up the actual ingredient name from the other list
                        val ingredientInfo = availableIngredients.find { it.ingredientId == item.ingredientId }
                        InventoryRow(item, ingredientInfo)
                    }
                }
            }
        }
    }

    // --- ADD INVENTORY DIALOG ---
    if (showAddDialog) {
        AddInventoryDialog(
            availableIngredients = availableIngredients,
            onDismiss = { showAddDialog = false },
            onSave = { ingredientId, qty, unit, location, expiry ->
                vm.addToInventory(ingredientId, qty, unit, location, expiry)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun InventoryRow(item: IngredientUser, ingredientInfo: Ingredient?) {
    val scheme = MaterialTheme.colorScheme

    // Format expiration date
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val expiryString = dateFormat.format(Date(item.expiredDate))

    // Check if expiring soon (within 3 days)
    val isExpiringSoon = item.expiredDate - System.currentTimeMillis() < (3 * 24 * 60 * 60 * 1000)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon Placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Kitchen, contentDescription = null, tint = scheme.primary)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ingredientInfo?.ingredientName ?: "Unknown Ingredient",
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${item.quantity ?: 0.0} ${item.quantityUnit ?: ""} • ${item.locationStorage ?: "Pantry"}",
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant
            )
        }

        // Expiry Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isExpiringSoon) scheme.errorContainer else scheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = expiryString,
                fontSize = 11.sp,
                color = if (isExpiringSoon) scheme.onErrorContainer else scheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInventoryDialog(
    availableIngredients: List<Ingredient>,
    onDismiss: () -> Unit,
    onSave: (Long, Double, String, String, Long) -> Unit
) {
    var selectedIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var quantityText by remember { mutableStateOf("") }
    var unitText by remember { mutableStateOf("pcs") }
    var locationText by remember { mutableStateOf("Pantry") }
    var daysToExpireText by remember { mutableStateOf("7") } // Default to 7 days

    var showIngredientPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Inventory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // Ingredient Selector
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { showIngredientPicker = true },
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedIngredient?.ingredientName ?: "Select Ingredient")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = locationText,
                    onValueChange = { locationText = it },
                    label = { Text("Storage Location (e.g., Fridge)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = daysToExpireText,
                    onValueChange = { daysToExpireText = it },
                    label = { Text("Expires in (days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ing = selectedIngredient
                    val qty = quantityText.toDoubleOrNull() ?: 0.0
                    val days = daysToExpireText.toLongOrNull() ?: 7L
                    val expiryTimestamp = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000)

                    if (ing?.ingredientId != null) {
                        onSave(ing.ingredientId!!, qty, unitText, locationText, expiryTimestamp)
                    }
                },
                enabled = selectedIngredient != null && quantityText.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    // Picker Dialog inside the Add Dialog
    if (showIngredientPicker) {
        AlertDialog(
            onDismissRequest = { showIngredientPicker = false },
            title = { Text("Select Ingredient") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(availableIngredients) { ing ->
                        ListItem(
                            headlineContent = { Text(ing.ingredientName) },
                            modifier = Modifier.clickable {
                                selectedIngredient = ing
                                unitText = ing.unit ?: "pcs" // Auto-fill unit from base ingredient
                                showIngredientPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIngredientPicker = false }) { Text("Close") }
            }
        )
    }
}