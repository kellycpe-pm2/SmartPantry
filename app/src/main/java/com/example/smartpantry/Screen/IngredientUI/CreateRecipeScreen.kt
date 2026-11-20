package com.example.smartpantry.Screen.RecipeUI

import android.net.Uri
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.hardware.rememberCameraCapture
import com.example.smartpantry.hardware.rememberGalleryPicker
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.model.RecipeIngredient
import com.example.smartpantry.ui.theme.ImageInput
import com.example.smartpantry.ui.theme.OutlineDropdownField
import com.example.smartpantry.ui.theme.OutlineTextField
import com.example.smartpantry.viewmodel.IngredientViewModel
import com.example.smartpantry.viewmodel.recipe_vm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRecipeScreen(
    onBackClick: () -> Unit,
    recipeVm: recipe_vm = viewModel(),
    ingredientVm: IngredientViewModel = viewModel()
) {
    val state by recipeVm.uiState.collectAsState()
    val availableIngredients by ingredientVm.ingredients.collectAsState()
    val scheme = MaterialTheme.colorScheme

    val categoryOptions = listOf("Breakfast", "Lunch", "Dinner", "Dessert", "Snack", "Drink", "Other")

    var selectedIngredients by remember { mutableStateOf<List<RecipeIngredient>>(emptyList()) }
    var showIngredientPicker by remember { mutableStateOf(false) }
    var configuringIngredient by remember { mutableStateOf<Ingredient?>(null) }

    LaunchedEffect(Unit) {
        ingredientVm.refreshIngredients(force = true)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            recipeVm.consumeSaved()
            onBackClick()
        }
    }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { recipeVm.clearError() },
            confirmButton = { TextButton(onClick = { recipeVm.clearError() }) { Text("OK") } },
            text = { Text(msg) },
            title = { Text("Couldn't save") }
        )
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Create Recipe", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = scheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.primary,
                    navigationIconContentColor = scheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            var pickedUri by remember { mutableStateOf<Uri?>(null) }
            var showSourceDialog by remember { mutableStateOf(false) }

            val openCamera = rememberCameraCapture { uri -> pickedUri = uri; recipeVm.onImagePicked(uri.toString()) }
            val openGallery = rememberGalleryPicker { uri -> pickedUri = uri; recipeVm.onImagePicked(uri.toString()) }

            ImageInput(
                imagePath = pickedUri?.toString() ?: state.imagePath,
                onClick = { showSourceDialog = true }
            )

            if (showSourceDialog) {
                AlertDialog(
                    onDismissRequest = { showSourceDialog = false },
                    title = { Text(if (pickedUri == null) "Add image" else "Replace image") },
                    text = {
                        Column {
                            TextButton(onClick = { showSourceDialog = false; openCamera() }) { Text("Take a photo") }
                            TextButton(onClick = { showSourceDialog = false; openGallery() }) { Text("Choose from gallery") }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { showSourceDialog = false }) { Text("Cancel") } }
                )
            }

            Spacer(Modifier.height(24.dp))

            OutlineTextField(
                value = state.recipeName,
                onValueChange = recipeVm::onNameChange,
                label = "Recipe Name",
                image = Icons.Default.Restaurant
            )
            Spacer(Modifier.height(16.dp))

            OutlineDropdownField(
                value = state.category,
                onValueChange = recipeVm::onCategoryChange,
                options = categoryOptions,
                label = "Recipe Category",
                image = Icons.Default.Category
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Eco, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                    Text("Vegan Recipe", color = scheme.onBackground, fontWeight = FontWeight.Medium)
                }
                Switch(
                    checked = state.isVegan,
                    onCheckedChange = recipeVm::onVeganChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = scheme.primary)
                )
            }
            Spacer(Modifier.height(16.dp))

            OutlineTextField(
                value = state.description,
                onValueChange = recipeVm::onDescriptionChange,
                label = "Instructions / Description",
                image = Icons.Default.Description,
                singleLine = false,
                minLines = 5,
                maxLines = 5
            )

            Spacer(Modifier.height(24.dp))

            // INGREDIENTS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ingredients", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = scheme.onBackground)
                TextButton(onClick = { showIngredientPicker = true }) {
                    Icon(Icons.Default.Add, "Add Ingredient")
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }

            if (selectedIngredients.isEmpty()) {
                Text("No ingredients added yet", fontSize = 12.sp, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp))
            } else {
                selectedIngredients.forEachIndexed { index, item ->
                    val ingredientName = availableIngredients
                        .find { it.ingredientId == item.ingredientId }?.ingredientName ?: "Unknown"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(scheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ingredientName, fontWeight = FontWeight.Medium)
                            Text("${item.quantity ?: 0.0} ${item.unit ?: ""}", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            val ing = availableIngredients.find { it.ingredientId == item.ingredientId }
                            if (ing != null) {
                                selectedIngredients = selectedIngredients.toMutableList().also { it.removeAt(index) }
                                configuringIngredient = ing
                            }
                        }) {
                            Icon(Icons.Default.Edit, "Edit", tint = scheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            selectedIngredients = selectedIngredients.toMutableList().also { it.removeAt(index) }
                        }) {
                            Icon(Icons.Default.Delete, "Remove", tint = scheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { recipeVm.createFullRecipe(ingredients = selectedIngredients) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                } else {
                    Text("Create Recipe", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // PICKER DIALOG
    if (showIngredientPicker) {
        var searchQuery by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showIngredientPicker = false },
            title = { Text("Select Ingredient") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    if (availableIngredients.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = scheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("No ingredients available", fontSize = 14.sp)
                            Text("Create ingredients first in Pantry", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            val filtered = availableIngredients
                                .filter { it.ingredientName.contains(searchQuery, ignoreCase = true) }
                            items(filtered, key = { it.ingredientId ?: 0L }) { ing ->
                                ListItem(
                                    headlineContent = { Text(ing.ingredientName) },
                                    supportingContent = { Text(ing.category ?: "Uncategorized") },
                                    modifier = Modifier.clickable {
                                        configuringIngredient = ing
                                        showIngredientPicker = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIngredientPicker = false }) { Text("Done") } }
        )
    }

    // QTY/UNIT DIALOG
    configuringIngredient?.let { ing ->
        var qty by remember { mutableStateOf("1") }
        var unit by remember { mutableStateOf(ing.unit ?: "pcs") }

        AlertDialog(
            onDismissRequest = { configuringIngredient = null },
            title = { Text("Add ${ing.ingredientName}") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Qty") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedIngredients = selectedIngredients + RecipeIngredient(
                        id = null,                                   // VM assigns on save
                        recipeId = null,                             // VM assigns on save
                        ingredientId = ing.ingredientId!!,
                        quantity = qty.toDoubleOrNull(),
                        unit = unit.trim().takeIf { it.isNotEmpty() }
                    )
                    configuringIngredient = null
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { configuringIngredient = null }) { Text("Cancel") }
            }
        )
    }
}