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

    var name by remember(original) { mutableStateOf(original?.recipeName ?: "") }
    var category by remember(original) { mutableStateOf(original?.recipeCategory ?: "Malaysian") }
    var description by remember(original) { mutableStateOf(original?.description ?: "") }
    var cookingSteps by remember(original) {
        mutableStateOf(original?.cookingSteps?.takeIf { it.isNotEmpty() } ?: listOf(""))
    }
    var isVegan by remember(original) { mutableStateOf(original?.isVegan ?: false) }
    var imagePath by remember(original) { mutableStateOf(original?.localRecipeImage ?: original?.recipeImage ?: "") }

    val recipeIngredients by remember(recipeId) {
        recipeVm.getIngredientsFlow(recipeId)
    }.collectAsState(initial = emptyList())

    var pickingIngredient by rememberSaveable { mutableStateOf(false) }
    var editingRecipeIngredient by remember { mutableStateOf<RecipeIngredient?>(null) }
    var configuringIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var photoSheetVisible by rememberSaveable { mutableStateOf(false) }

    val openCamera = rememberCameraCapture { uri: Uri -> imagePath = uri.toString() }
    val openGallery = rememberGalleryPicker { uri: Uri -> imagePath = uri.toString() }

    LaunchedEffect(Unit) { ingredientVm.refreshIngredients(force = true) }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        SmartPantrySectionHeader(
            title = "Edit recipe",
            showBack = true,
            onBack = onBackClick
        )

        if (original == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = scheme.primary)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    RecipePhoto(
                        url = imagePath,
                        modifier = Modifier.size(116.dp).clip(RoundedCornerShape(18.dp))
                    )
                    FilledIconButton(
                        onClick = { photoSheetVisible = true },
                        modifier = Modifier.align(Alignment.BottomEnd).size(40.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, "Change recipe photo")
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(150) },
                        label = { Text("Recipe title *") },
                        supportingText = { Text("${name.length}/150") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    CompactDropdownField(
                        label = "Category",
                        value = category.ifBlank { "Malaysian" },
                        options = listOf("Malaysian", "Asian", "Western", "Breakfast", "Lunch", "Dinner", "Dessert", "Other"),
                        onSelected = { category = it }
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(5000) },
                label = { Text("Description") },
                supportingText = { Text("${description.length}/5000") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isVegan, onCheckedChange = { isVegan = it })
                Text("Vegan (no animal ingredients)", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ingredients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${recipeIngredients.size}/50", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }

            recipeIngredients.forEach { item ->
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
                            editingRecipeIngredient = item
                            configuringIngredient = ingInfo
                            pickingIngredient = true
                        }) {
                            Icon(Icons.Default.Edit, "Edit ingredient", Modifier.size(19.dp))
                        }
                        IconButton(onClick = {
                            item.id?.let { recipeVm.deleteRecipeIngredient(it) }
                        }) {
                            Icon(Icons.Default.DeleteOutline, "Remove ingredient", Modifier.size(19.dp))
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    editingRecipeIngredient = null
                    configuringIngredient = null
                    pickingIngredient = true
                },
                enabled = recipeIngredients.size < 50,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add ingredient")
            }

            // Cooking Steps Section
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cooking steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${cookingSteps.size}/30", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }

            cookingSteps.forEachIndexed { idx, step ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = step,
                        onValueChange = { updatedText ->
                            cookingSteps = cookingSteps.mapIndexed { i, s ->
                                if (i == idx) updatedText.take(500) else s
                            }
                        },
                        label = { Text("Step ${idx + 1}") },
                        supportingText = { Text("${step.length}/500") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (cookingSteps.size > 1) {
                        IconButton(
                            onClick = {
                                cookingSteps = cookingSteps.filterIndexed { i, _ -> i != idx }
                            }
                        ) {
                            Icon(Icons.Default.DeleteOutline, "Remove step ${idx + 1}")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { cookingSteps = cookingSteps + "" },
                enabled = cookingSteps.size < 30,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add step")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val cleanSteps = cookingSteps.map { it.trim() }.filter { it.isNotEmpty() }
                    recipeVm.updateRecipe(
                        recipeId,
                        original.copy(
                            recipeName = name.trim(),
                            recipeCategory = category.trim().takeIf { it.isNotEmpty() },
                            description = description.trim().takeIf { it.isNotEmpty() },
                            cookingSteps = cleanSteps,
                            isVegan = isVegan,
                            localRecipeImage = if (imagePath.startsWith("content://") || imagePath.startsWith("/")) imagePath else original.localRecipeImage
                        )
                    )
                    onBackClick()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save changes", fontWeight = FontWeight.Bold)
            }
        }
    }

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

    if (pickingIngredient) {
        var query by remember { mutableStateOf("") }
        val isEditing = editingRecipeIngredient != null
        var qty by remember(editingRecipeIngredient, configuringIngredient) {
            mutableStateOf(editingRecipeIngredient?.quantity?.let { quantityText(it) } ?: "1")
        }
        var unit by remember(editingRecipeIngredient, configuringIngredient) {
            mutableStateOf(
                editingRecipeIngredient?.unit
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
                    if (isEditing) "Edit ingredient" else "Add ingredient",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (!isEditing) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; configuringIngredient = null },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Search your ingredient catalogue") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    val filtered = availableIngredients.filter {
                        it.ingredientName.contains(query.trim(), ignoreCase = true)
                    }.take(30)

                    Column(Modifier.heightIn(max = 210.dp).verticalScroll(rememberScrollState())) {
                        filtered.forEach { ing ->
                            val selected = configuringIngredient?.ingredientId == ing.ingredientId
                            val imgUrl = ing.localImagePath?.takeIf { File(it).exists() } ?: ing.image.orEmpty()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) scheme.primaryContainer else Color.Transparent)
                                    .clickable {
                                        configuringIngredient = ing
                                        unit = normalizedUnit(ing.unit.orEmpty()).takeIf { it in recipeUnits } ?: "pcs"
                                        fieldError = ""
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RecipePhoto(imgUrl, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), false)
                                Text(ing.ingredientName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                if (selected) Icon(Icons.Default.Check, null, tint = scheme.primary)
                            }
                        }
                    }
                } else {
                    Text(configuringIngredient?.ingredientName ?: "Ingredient", fontWeight = FontWeight.Bold)
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
                        val parsedQty = qty.toDoubleOrNull()
                        val existing = editingRecipeIngredient
                        val chosen = configuringIngredient
                        fieldError = when {
                            !isEditing && chosen?.ingredientId == null -> "Select an ingredient."
                            parsedQty == null || !parsedQty.isFinite() || parsedQty <= 0.0 -> "Enter a positive quantity."
                            unit in listOf("pcs", "bag", "carton", "bunch") && parsedQty % 1 != 0.0 -> "This unit needs a whole number."
                            !isEditing && recipeIngredients.any { it.ingredientId == chosen?.ingredientId } -> "Already in recipe. Edit its quantity instead."
                            else -> ""
                        }
                        if (fieldError.isEmpty()) {
                            if (existing?.id != null) {
                                recipeVm.updateRecipeIngredient(existing.id, parsedQty, unit)
                            } else if (chosen?.ingredientId != null) {
                                recipeVm.addIngredientToRecipe(recipeId, chosen.ingredientId, parsedQty, unit)
                            }
                            pickingIngredient = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isEditing) "Save ingredient" else "Add ingredient")
                }
            }
        }
    }
}