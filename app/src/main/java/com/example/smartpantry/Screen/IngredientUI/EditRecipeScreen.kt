package com.example.smartpantry.Screen.RecipeUI

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
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.ui.theme.OutlineDropdownField
import com.example.smartpantry.ui.theme.OutlineTextField
import com.example.smartpantry.viewmodel.IngredientViewModel
import com.example.smartpantry.viewmodel.recipe_vm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeScreen(
    recipeId: Long,
    onBackClick: () -> Unit,
    recipeVm: recipe_vm = viewModel(),
    ingredientVm: IngredientViewModel = viewModel()
) {
    val recipes by recipeVm.recipes.collectAsState()
    val availableIngredients by ingredientVm.ingredients.collectAsState()
    val scheme = MaterialTheme.colorScheme

    val original = recipes.find { it.recipeId == recipeId }

    var name by remember(recipeId) { mutableStateOf(original?.recipeName ?: "") }
    var category by remember(recipeId) { mutableStateOf(original?.recipeCategory ?: "") }
    var description by remember(recipeId) { mutableStateOf(original?.description ?: "") }
    var isVegan by remember(recipeId) { mutableStateOf(original?.isVegan ?: false) }

    // Auto-updating list — no refreshKey needed
    val recipeIngredients by remember(recipeId) {
        recipeVm.getIngredientsFlow(recipeId)
    }.collectAsState(initial = emptyList())

    var showIngredientPicker by remember { mutableStateOf(false) }
    var configuringIngredient by remember { mutableStateOf<Ingredient?>(null) }

    LaunchedEffect(Unit) { ingredientVm.refreshIngredients(force = true) }

    if (original == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Recipe", fontWeight = FontWeight.Bold, color = scheme.primary) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = scheme.primary)
                        }
                    }
                )
            }
        ) { p ->
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = scheme.primary)
            }
        }
        return
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Edit Recipe", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = scheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.primary
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            OutlineTextField(
                value = name,
                onValueChange = { name = it },
                label = "Recipe Name",
                image = Icons.Default.Restaurant
            )
            Spacer(Modifier.height(16.dp))

            OutlineDropdownField(
                value = category,
                onValueChange = { category = it },
                options = listOf("Breakfast", "Lunch", "Dinner", "Dessert", "Snack", "Drink", "Other"),
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
                    Text("Vegan", fontWeight = FontWeight.Medium)
                }
                Switch(checked = isVegan, onCheckedChange = { isVegan = it })
            }
            Spacer(Modifier.height(16.dp))

            OutlineTextField(
                value = description,
                onValueChange = { description = it },
                label = "Instructions",
                image = Icons.Default.Description,
                singleLine = false,
                minLines = 5,
                maxLines = 5
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ingredients", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = { showIngredientPicker = true }) {
                    Icon(Icons.Default.Add, "Add")
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }

            if (recipeIngredients.isEmpty()) {
                Text(
                    "No ingredients yet",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                recipeIngredients.forEach { item ->
                    val ingName = availableIngredients
                        .find { it.ingredientId == item.ingredientId }?.ingredientName
                        ?: "Ingredient #${item.ingredientId}"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(scheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ingName, fontWeight = FontWeight.Medium)
                            Text(
                                "${item.quantity ?: 0.0} ${item.unit ?: ""}",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            // flow picks up the change automatically
                            item.id?.let { recipeVm.deleteRecipeIngredient(it) }
                        }) {
                            Icon(Icons.Default.Delete, "Remove", tint = scheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    recipeVm.updateRecipe(
                        recipeId,
                        original.copy(
                            recipeName = name.trim(),
                            recipeCategory = category.trim().takeIf { it.isNotEmpty() },
                            description = description.trim().takeIf { it.isNotEmpty() },
                            isVegan = isVegan
                        )
                    )
                    onBackClick()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
            ) {
                Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showIngredientPicker) {
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showIngredientPicker = false },
            title = { Text("Add Ingredient") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    val filtered = availableIngredients
                        .filter { it.ingredientName.contains(query, ignoreCase = true) }
                    if (filtered.isEmpty()) {
                        Text("No ingredients found", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
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
                    recipeVm.addIngredientToRecipe(
                        recipeId = recipeId,
                        ingredientId = ing.ingredientId!!,
                        quantity = qty.toDoubleOrNull(),
                        unit = unit.trim().takeIf { it.isNotEmpty() }
                    )
                    configuringIngredient = null
                    // no refreshKey — flow handles it
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { configuringIngredient = null }) { Text("Cancel") }
            }
        )
    }
}