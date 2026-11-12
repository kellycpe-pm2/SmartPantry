@file:JvmName("AddInventoryScreenKt")

package com.example.smartpantry.Screen.InventoryUI

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import io.github.jan.supabase.auth.auth
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

    // Dialog States
    var configuringIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCreateAgainPrompt by remember { mutableStateOf(false) }
    var newlyCreatedIngredient by remember { mutableStateOf<Ingredient?>(null) }

    val filteredIngredients = allIngredients.filter { it.ingredientName.contains(searchQuery, ignoreCase = true) }
    val exactMatchExists = allIngredients.any { it.ingredientName.equals(searchQuery, ignoreCase = true) }

    // 👇 GET THE REAL USER ID FROM SUPABASE AUTH
    // If the user is not logged in, it uses a test UUID so you can still test the flow
    val currentUserId: String = supabase.auth.currentUserOrNull()?.id
        ?: "facad5cd-98f6-4d8a-b4b5-cf13f0515e0e"

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Add Inventory Items", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = scheme.primary)
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp))
                    } else {
                        IconButton(onClick = { vm.refreshData() }) {
                            Icon(Icons.Default.Refresh, "Refresh from Supabase", tint = scheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.primary
                )
            )
        },
        bottomBar = {
            if (stagedItems.isNotEmpty()) {
                Surface(shadowElevation = 8.dp, color = scheme.surface) {
                    Button(
                        onClick = {
                            vm.saveAllToInventory()
                            onBackClick()
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
                    ) {
                        Text("Save ${stagedItems.size} Items to Inventory", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {

            // --- 1. Search Bar ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search ingredient...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // --- 2. Create Button (if not found) ---
            if (searchQuery.isNotBlank() && !exactMatchExists) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        showCreateDialog = true
                    },
                    colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = scheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Create \"$searchQuery\"", fontWeight = FontWeight.Bold, color = scheme.primary)
                    }
                }
            }

            // --- 3. Staged Items List ---
            if (stagedItems.isNotEmpty()) {
                Text("Items to add:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(stagedItems) { staged ->
                        val ing = allIngredients.find { it.ingredientId == staged.ingredientId }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${ing?.ingredientName} - ${staged.quantity} ${staged.quantityUnit}")
                            IconButton(onClick = { vm.removeStagedItem(staged) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = scheme.error)
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // --- 4. Search Results (With Smart Image Logic) ---
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredIngredients) { ing ->
                    ListItem(
                        headlineContent = { Text(ing.ingredientName) },
                        supportingContent = { Text(ing.category ?: "Uncategorized") },
                        leadingContent = {
                            // SMART IMAGE LOGIC: Local if exists, else Supabase URL
                            val localFile: String? = ing.localImagePath
                            val remoteUrl: String? = ing.image
                            val imageToLoad = if (localFile != null && localFile.isNotEmpty() && File(localFile).exists()) {
                                File(localFile)
                            } else {
                                remoteUrl
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(scheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (imageToLoad != null) {
                                    AsyncImage(
                                        model = imageToLoad,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(ing.ingredientName.firstOrNull()?.uppercase() ?: "?", color = scheme.primary)
                                }
                            }
                        },
                        modifier = Modifier.clickable { configuringIngredient = ing }
                    )
                }
            }
        }
    }

    // --- DIALOG 1: Configure Quantity & Date ---
    configuringIngredient?.let { ingredient ->
        var qty by remember { mutableStateOf("1") }
        var unit by remember { mutableStateOf(ingredient.unit ?: "pcs") }
        var location by remember { mutableStateOf("Pantry") }
        var days by remember { mutableStateOf("7") }

        AlertDialog(
            onDismissRequest = { configuringIngredient = null },
            title = { Text("Add ${ingredient.ingredientName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Storage Location") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Expires in (days)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val expiryTimestamp = System.currentTimeMillis() + ((days.toLongOrNull() ?: 7L) * 24 * 60 * 60 * 1000)

                    // PASS THE USER ID TO THE VIEWMODEL
                    vm.stageItem(
                        ingredientId = ingredient.ingredientId!!,
                        qty = qty.toDoubleOrNull() ?: 1.0,
                        unit = unit,
                        location = location,
                        expiredDate = expiryTimestamp,
                        userId = currentUserId
                    )
                    configuringIngredient = null
                }) { Text("Add to List") }
            },
            dismissButton = { TextButton(onClick = { configuringIngredient = null }) { Text("Cancel") } }
        )
    }

    // --- DIALOG 2: Create New Ingredient ---
    if (showCreateDialog) {
        var newCategory by remember { mutableStateOf("") }
        var newWeight by remember { mutableStateOf("") }
        var newUnit by remember { mutableStateOf("g") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create \"$searchQuery\"") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category (e.g. Fruit, Veg, Meat)")
                    OutlinedTextField(value = newCategory, onValueChange = { newCategory = it }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = newWeight, onValueChange = { newWeight = it }, label = { Text("Default Weight") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = newUnit, onValueChange = { newUnit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.createNewIngredient(searchQuery, newCategory, newWeight.toDoubleOrNull() ?: 0.0, newUnit) { createdIngredient ->
                        newlyCreatedIngredient = createdIngredient
                        showCreateDialog = false
                        showCreateAgainPrompt = true // Trigger the "Create Again?" prompt
                        searchQuery = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }

    // --- DIALOG 3: Create Again? Prompt ---
    if (showCreateAgainPrompt) {
        AlertDialog(
            onDismissRequest = { showCreateAgainPrompt = false },
            title = { Text("Ingredient Created!") },
            text = { Text("Do you want to create another ingredient?") },
            confirmButton = {
                TextButton(onClick = {
                    showCreateAgainPrompt = false
                    showCreateDialog = true // Reopen create dialog
                }) { Text("Yes, Create Again") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateAgainPrompt = false
                    // Proceed to configure the item we just created
                    configuringIngredient = newlyCreatedIngredient
                }) { Text("No, Add to Inventory") }
            }
        )
    }
}