package com.example.smartpantry.Screen.InventoryUI

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.Screen.IngredientUI.CompactDropdownField
import com.example.smartpantry.community.RecipePhoto
import com.example.smartpantry.community.SmartPantrySectionHeader
import com.example.smartpantry.community.quantityText
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInventoryFlowScreen(
    onBackClick: () -> Unit,
    vm: IngredientUserViewModel = viewModel()
) {
    val allIngredients by vm.allIngredients.collectAsState()
    val stagedItems by vm.stagedItems.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val scheme = MaterialTheme.colorScheme

    var searchQuery by remember { mutableStateOf("") }
    var configuringIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showCreateAgainPrompt by remember { mutableStateOf(false) }
    var newlyCreatedIngredient by remember { mutableStateOf<Ingredient?>(null) }

    val filteredIngredients = allIngredients.filter {
        it.ingredientName.contains(searchQuery, ignoreCase = true) ||
                (it.category ?: "").contains(searchQuery, ignoreCase = true)
    }
    val exactMatchExists = allIngredients.any { it.ingredientName.equals(searchQuery.trim(), ignoreCase = true) }

    val currentUserId: String = supabase.auth.currentUserOrNull()?.id
        ?: "facad5cd-98f6-4d8a-b4b5-cf13f0515e0e"
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val onRefresh: () -> Unit = {
        coroutineScope.launch {
            isRefreshing = true
            try {
                vm.refreshData()
                vm.refreshInventory(force = true)
                delay(500)
            } finally {
                isRefreshing = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        SmartPantrySectionHeader(
            title = "Add to pantry",
            showBack = true,
            onBack = onBackClick,
            action = {
                IconButton(onClick = { vm.refreshData() }, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, "Refresh catalogue", tint = scheme.primary)
                }
            }
        )

        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        PullToRefreshBox(
            isRefreshing = isRefreshing || isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // weight(1f) belongs on PullToRefreshBox (inside the outer Column)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(), // fillMaxSize() belongs on LazyColumn (inside PullToRefreshBox)
                contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search Field (Matches Community Feed Search)
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search ingredient catalogue") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (searchQuery.isNotBlank()) ({
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    "Clear"
                                )
                            }
                        }) else null,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Quick Create Card when ingredient isn't found
                if (searchQuery.isNotBlank() && !exactMatchExists) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = scheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().clickable { showCreateSheet = true }
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AddCircleOutline, null, tint = scheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Create \"${searchQuery.trim()}\" in catalogue",
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Staged Batch Tray
                if (stagedItems.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = scheme.surfaceVariant,
                            border = BorderStroke(1.dp, scheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Ready to add (${stagedItems.size})",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Tap Save below",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.primary
                                    )
                                }
                                stagedItems.forEach { staged ->
                                    val ing =
                                        allIngredients.find { it.ingredientId == staged.ingredientId }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                ing?.ingredientName ?: "Item",
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "${quantityText(staged.quantity ?: 0.0)} ${staged.quantityUnit.orEmpty()} · ${staged.locationStorage.orEmpty()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = scheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { vm.removeStagedItem(staged) }) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                "Remove",
                                                tint = scheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Ingredient catalogue (${filteredIngredients.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(filteredIngredients, key = { it.ingredientId ?: 0L }) { ing ->
                    val imgUrl =
                        ing.localImagePath?.takeIf { File(it).exists() } ?: ing.image.orEmpty()
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surface,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clickable { configuringIngredient = ing }
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RecipePhoto(
                                url = imgUrl,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                                recipeFallback = false
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ing.ingredientName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    ing.category ?: "Uncategorized",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            FilledTonalIconButton(onClick = { configuringIngredient = ing }) {
                                Icon(Icons.Default.Add, "Add ${ing.ingredientName}")
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sticky Action Bar
        if (stagedItems.isNotEmpty()) {
            Surface(shadowElevation = 8.dp, color = scheme.surface) {
                Button(
                    onClick = {
                        vm.saveAllToInventory()
                        onBackClick()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save ${stagedItems.size} item(s) to pantry", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Bottom Sheet 1: Configure Batch Quantity, Storage & Expiry
    configuringIngredient?.let { ingredient ->
        var qty by remember { mutableStateOf("1") }
        var unit by remember { mutableStateOf(ingredient.unit ?: "pcs") }
        var location by remember { mutableStateOf("Pantry") }
        var days by remember { mutableStateOf((ingredient.maxExpiredDuration ?: 7).toString()) }
        var fieldError by remember { mutableStateOf("") }

        ModalBottomSheet(onDismissRequest = { configuringIngredient = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add ${ingredient.ingredientName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it.take(12); fieldError = "" },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
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

                Text("Storage location", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Pantry", "Fridge", "Freezer").forEach { spot ->
                        FilterChip(
                            selected = location.equals(spot, ignoreCase = true),
                            onClick = { location = spot },
                            label = { Text(spot) }
                        )
                    }
                }

                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it.filter(Char::isDigit).take(4) },
                    label = { Text("Expires in (days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (fieldError.isNotBlank()) {
                    Text(fieldError, color = scheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        val parsedQty = qty.toDoubleOrNull()
                        if (parsedQty == null || parsedQty <= 0.0) {
                            fieldError = "Enter a positive quantity."
                            return@Button
                        }
                        val expiryTimestamp = System.currentTimeMillis() + ((days.toLongOrNull() ?: 7L) * 24 * 60 * 60 * 1000)
                        vm.stageItem(
                            ingredientId = ingredient.ingredientId!!,
                            qty = parsedQty,
                            unit = unit,
                            location = location,
                            expiredDate = expiryTimestamp,
                            userId = currentUserId
                        )
                        configuringIngredient = null
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add to batch list", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Bottom Sheet 2: Quick Create New Ingredient
    if (showCreateSheet) {
        var newCategory by remember { mutableStateOf("Vegetable") }
        var newWeight by remember { mutableStateOf("") }
        var newUnit by remember { mutableStateOf("pcs") }

        ModalBottomSheet(onDismissRequest = { showCreateSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Create \"${searchQuery.trim()}\"", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                CompactDropdownField(
                    label = "Category",
                    value = newCategory,
                    options = listOf("Vegetable", "Fruit", "Meat", "Dairy", "Grain", "Spice", "Other"),
                    onSelected = { newCategory = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newWeight,
                        onValueChange = { newWeight = it.take(12) },
                        label = { Text("Default weight") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Box(Modifier.weight(1f)) {
                        CompactDropdownField(
                            label = "Unit",
                            value = newUnit,
                            options = listOf("g", "kg", "mL", "L", "pcs", "bag", "carton", "bunch"),
                            onSelected = { newUnit = it }
                        )
                    }
                }
                Button(
                    onClick = {
                        vm.createNewIngredient(searchQuery.trim(), newCategory, newWeight.toDoubleOrNull() ?: 0.0, newUnit) { created ->
                            newlyCreatedIngredient = created
                            showCreateSheet = false
                            showCreateAgainPrompt = true
                            searchQuery = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create ingredient", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCreateAgainPrompt) {
        AlertDialog(
            onDismissRequest = { showCreateAgainPrompt = false },
            title = { Text("Ingredient created") },
            text = { Text("Do you want to create another ingredient, or add this one to your pantry?") },
            confirmButton = {
                TextButton(onClick = {
                    showCreateAgainPrompt = false
                    configuringIngredient = newlyCreatedIngredient
                }) { Text("Add to pantry") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateAgainPrompt = false
                    showCreateSheet = true
                }) { Text("Create another") }
            }
        )
    }
}