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

    // State for selected ingredients
    var selectedIngredients by remember { mutableStateOf<List<RecipeIngredient>>(emptyList()) }
    var showIngredientPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            recipeVm.consumeSaved()
            onBackClick()
        }
    }

    // Error Dialog
    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { recipeVm.clearError() },
            confirmButton = {
                TextButton(onClick = { recipeVm.clearError() }) { Text("OK") }
            },
            text = { Text(msg) },
            title = { Text("Couldn't save") }
        )
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Recipe",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary
                    )
                },
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

            // Image Picker Setup
            var pickedUri by remember { mutableStateOf<Uri?>(null) }
            var showSourceDialog by remember { mutableStateOf(false) }

            val openCamera = rememberCameraCapture { uri ->
                pickedUri = uri
                recipeVm.onImagePicked(uri.toString())
            }

            val openGallery = rememberGalleryPicker { uri ->
                pickedUri = uri
                recipeVm.onImagePicked(uri.toString())
            }

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
                            TextButton(onClick = {
                                showSourceDialog = false
                                openCamera()
                            }) { Text("Take a photo") }
                            TextButton(onClick = {
                                showSourceDialog = false
                                openGallery()
                            }) { Text("Choose from gallery") }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showSourceDialog = false }) { Text("Cancel") }
                    }
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

            // Vegan Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Eco,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Vegan Recipe",
                        color = scheme.onBackground,
                        fontWeight = FontWeight.Medium
                    )
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

            // Selected Ingredients Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ingredients",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = scheme.onBackground
                )
                TextButton(onClick = { showIngredientPicker = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Ingredient")
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }

            // Display Selected Ingredients
            if (selectedIngredients.isEmpty()) {
                Text(
                    "No ingredients added yet",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                selectedIngredients.forEach { item ->
                    val ingredientName = availableIngredients.find { it.ingredientId == item.ingredientId }?.ingredientName ?: "Unknown"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(scheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(ingredientName, fontWeight = FontWeight.Medium)
                            Text("${item.quantity} ${item.unit}", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { selectedIngredients = selectedIngredients - item }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = scheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { recipeVm.createFullRecipe(ingredients = selectedIngredients) },
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                )
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = scheme.onPrimary
                    )
                } else {
                    Text("Create Recipe", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // --- INGREDIENT PICKER DIALOG ---
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

                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        val filtered = availableIngredients.filter { it.ingredientName.contains(searchQuery, ignoreCase = true) }
                        items(filtered) { ing ->
                            ListItem(
                                headlineContent = { Text(ing.ingredientName) },
                                supportingContent = { Text(ing.category ?: "Uncategorized") },
                                modifier = Modifier.clickable {
                                    // Defaulting to quantity 1 and unit "pcs" for simplicity
                                    selectedIngredients = selectedIngredients + RecipeIngredient(
                                        ingredientId = ing.ingredientId!!,
                                        recipeId = 0,
                                        quantity = 1.0,
                                        unit = "pcs"
                                    )
                                    showIngredientPicker = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIngredientPicker = false }) { Text("Done") }
            }
        )
    }
}