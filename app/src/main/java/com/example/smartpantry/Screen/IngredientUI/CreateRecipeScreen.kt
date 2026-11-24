package com.example.smartpantry.Screen.RecipeUI

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.Screen.IngredientUI.CompactDropdownField
import com.example.smartpantry.community.*
import com.example.smartpantry.hardware.rememberCameraCapture
import com.example.smartpantry.hardware.rememberGalleryPicker
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.model.RecipeIngredient
import com.example.smartpantry.viewmodel.IngredientViewModel
import com.example.smartpantry.viewmodel.recipe_vm
import java.io.File

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

    val categoryOptions = listOf("Malaysian", "Asian", "Western", "Breakfast", "Lunch", "Dinner", "Dessert", "Other")

    var selectedIngredients by remember { mutableStateOf<List<RecipeIngredient>>(emptyList()) }
    var pickingIngredient by rememberSaveable { mutableStateOf(false) }
    var editingIndex by rememberSaveable { mutableIntStateOf(-1) }
    var configuringIngredient by remember { mutableStateOf<Ingredient?>(null) }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var photoSheetVisible by rememberSaveable { mutableStateOf(false) }

    val openCamera = rememberCameraCapture { uri -> pickedUri = uri; recipeVm.onImagePicked(uri.toString()) }
    val openGallery = rememberGalleryPicker { uri -> pickedUri = uri; recipeVm.onImagePicked(uri.toString()) }

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
            icon = { Icon(Icons.Default.Info, null) },
            title = { Text("Couldn't save") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { recipeVm.clearError() }) { Text("OK") } }
        )
    }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        SmartPantrySectionHeader(
            title = "Create recipe",
            showBack = true,
            onBack = onBackClick
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Photo + Title + Category Row (Matches Community RecipeEditor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    RecipePhoto(
                        url = pickedUri?.toString() ?: state.imagePath.orEmpty(),
                        modifier = Modifier.size(116.dp).clip(RoundedCornerShape(18.dp))
                    )
                    FilledIconButton(
                        onClick = { photoSheetVisible = true },
                        enabled = !state.isSaving,
                        modifier = Modifier.align(Alignment.BottomEnd).size(40.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, "Change recipe photo")
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.recipeName,
                        onValueChange = { recipeVm.onNameChange(it.take(150)) },
                        label = { Text("Recipe title *") },
                        supportingText = { Text("${state.recipeName.length}/150") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    CompactDropdownField(
                        label = "Category",
                        value = state.category.ifBlank { "Malaysian" },
                        options = categoryOptions,
                        onSelected = recipeVm::onCategoryChange
                    )
                }
            }

            // Description
            OutlinedTextField(
                value = state.description,
                onValueChange = { recipeVm.onDescriptionChange(it.take(5000)) },
                label = { Text("Description") },
                supportingText = { Text("${state.description.length}/5000") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )

            // Vegan Checkbox
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.isVegan, onCheckedChange = recipeVm::onVeganChange)
                Text("Vegan (no animal ingredients)", style = MaterialTheme.typography.bodyMedium)
            }

            // Ingredients Section
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ingredients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${selectedIngredients.size}/50", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }

            selectedIngredients.forEachIndexed { index, item ->
                val ingInfo = availableIngredients.find { it.ingredientId == item.ingredientId }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.surfaceVariant,
                    border = BorderStroke(1.dp, scheme.outlineVariant)
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            ingInfo?.ingredientName ?: "Ingredient #${item.ingredientId}",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 2
                        )
                        Text(
                            "${quantityText(item.quantity ?: 0.0)} ${item.unit.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = {
                            if (ingInfo != null) {
                                editingIndex = index
                                configuringIngredient = ingInfo
                                pickingIngredient = true
                            }
                        }) {
                            Icon(Icons.Default.Edit, "Edit ingredient", Modifier.size(19.dp))
                        }
                        IconButton(onClick = {
                            selectedIngredients = selectedIngredients.filterIndexed { i, _ -> i != index }
                        }) {
                            Icon(Icons.Default.DeleteOutline, "Remove ingredient", Modifier.size(19.dp))
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    editingIndex = -1
                    configuringIngredient = null
                    pickingIngredient = true
                },
                enabled = selectedIngredients.size < 50 && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add ingredient")
            }

            // Cooking Steps Section (Matches Community RecipeEditor)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cooking steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${state.cookingSteps.size}/30", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }

            state.cookingSteps.forEachIndexed { idx, step ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = step,
                        onValueChange = { updatedText ->
                            val updatedSteps = state.cookingSteps.mapIndexed { i, s ->
                                if (i == idx) updatedText.take(500) else s
                            }
                            recipeVm.onStepsChange(updatedSteps)
                        },
                        label = { Text("Step ${idx + 1}") },
                        supportingText = { Text("${step.length}/500") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (state.cookingSteps.size > 1) {
                        IconButton(
                            onClick = {
                                recipeVm.onStepsChange(state.cookingSteps.filterIndexed { i, _ -> i != idx })
                            },
                            enabled = !state.isSaving
                        ) {
                            Icon(Icons.Default.DeleteOutline, "Remove step ${idx + 1}")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { recipeVm.onStepsChange(state.cookingSteps + "") },
                enabled = state.cookingSteps.size < 30 && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add step")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { recipeVm.createFullRecipe(ingredients = selectedIngredients) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                } else {
                    Text("Save recipe", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Bottom Sheet: Photo Picker
    if (photoSheetVisible) {
        ModalBottomSheet(onDismissRequest = { photoSheetVisible = false }) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recipe photo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FilledTonalButton(
                    onClick = { photoSheetVisible = false; openGallery() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose from gallery")
                }
                OutlinedButton(
                    onClick = { photoSheetVisible = false; openCamera() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Take photo")
                }
            }
        }
    }

    // Bottom Sheet: Ingredient Picker + Quantity/Unit Configurator
    if (pickingIngredient) {
        var ingredientQuery by remember { mutableStateOf("") }
        val existingItem = if (editingIndex >= 0) selectedIngredients.getOrNull(editingIndex) else null
        var qty by remember(configuringIngredient, existingItem) {
            mutableStateOf(existingItem?.quantity?.let { quantityText(it) } ?: "1")
        }
        var unit by remember(configuringIngredient, existingItem) {
            mutableStateOf(
                existingItem?.unit
                    ?: normalizedUnit(configuringIngredient?.unit.orEmpty()).takeIf { it in recipeUnits }
                    ?: "pcs"
            )
        }
        var fieldError by remember { mutableStateOf("") }

        ModalBottomSheet(onDismissRequest = { pickingIngredient = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (editingIndex >= 0) "Edit ingredient" else "Add ingredient",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (editingIndex < 0) {
                    OutlinedTextField(
                        value = ingredientQuery,
                        onValueChange = { ingredientQuery = it; configuringIngredient = null },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Search your ingredient catalogue") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    val matches = availableIngredients.filter {
                        it.ingredientName.contains(ingredientQuery.trim(), ignoreCase = true)
                    }.take(30)

                    Column(Modifier.heightIn(max = 210.dp).verticalScroll(rememberScrollState())) {
                        matches.forEach { row ->
                            val isSelected = configuringIngredient?.ingredientId == row.ingredientId
                            val imgUrl = row.localImagePath?.takeIf { File(it).exists() } ?: row.image.orEmpty()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) scheme.primaryContainer else Color.Transparent)
                                    .clickable {
                                        configuringIngredient = row
                                        unit = normalizedUnit(row.unit.orEmpty()).takeIf { it in recipeUnits } ?: "pcs"
                                        fieldError = ""
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RecipePhoto(imgUrl, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), false)
                                Text(row.ingredientName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                if (isSelected) Icon(Icons.Default.Check, null, tint = scheme.primary)
                            }
                        }
                    }
                } else {
                    Text(configuringIngredient?.ingredientName.orEmpty(), fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
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
                            options = recipeUnits,
                            onSelected = { unit = it }
                        )
                    }
                }

                if (fieldError.isNotBlank()) {
                    Text(fieldError, color = scheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        val chosen = configuringIngredient
                        val parsedQty = qty.toDoubleOrNull()
                        fieldError = when {
                            chosen?.ingredientId == null -> "Select an ingredient."
                            parsedQty == null || !parsedQty.isFinite() || parsedQty <= 0.0 -> "Enter a positive quantity."
                            unit in listOf("pcs", "bag", "carton", "bunch") && parsedQty % 1 != 0.0 -> "This unit needs a whole number."
                            editingIndex < 0 && selectedIngredients.any { it.ingredientId == chosen.ingredientId } -> "Already added. Edit its quantity instead."
                            else -> ""
                        }
                        if (fieldError.isEmpty() && chosen != null) {
                            val newRow = RecipeIngredient(
                                id = existingItem?.id,
                                recipeId = null,
                                ingredientId = chosen.ingredientId!!,
                                quantity = parsedQty,
                                unit = unit
                            )
                            selectedIngredients = if (editingIndex < 0) {
                                selectedIngredients + newRow
                            } else {
                                selectedIngredients.mapIndexed { i, old -> if (i == editingIndex) newRow else old }
                            }
                            pickingIngredient = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (editingIndex >= 0) "Save ingredient" else "Add ingredient")
                }
            }
        }
    }
}