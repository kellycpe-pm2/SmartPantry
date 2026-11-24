package com.example.smartpantry.Screen.IngredientUI

import android.net.Uri
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.Ai.IngredientAiAssistantDialog
import com.example.smartpantry.community.RecipePhoto
import com.example.smartpantry.community.SmartPantrySectionHeader
import com.example.smartpantry.hardware.rememberCameraCapture
import com.example.smartpantry.hardware.rememberGalleryPicker
import com.example.smartpantry.viewmodel.IngredientViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditIngredientScreen(
    ingredientId: Long,
    onBackClick: () -> Unit,
    vm: IngredientViewModel = viewModel()
) {
    val ingredients by vm.ingredients.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val original = ingredients.find { it.ingredientId == ingredientId }

    var name by remember(original) { mutableStateOf(original?.ingredientName ?: "") }
    var category by remember(original) { mutableStateOf(original?.category ?: "Vegetable") }
    var weight by remember(original) { mutableStateOf(original?.weight?.toString() ?: "") }
    var unit by remember(original) { mutableStateOf(original?.unit ?: "pcs") }
    var description by remember(original) { mutableStateOf(original?.description ?: "") }
    var imagePath by remember(original) { mutableStateOf(original?.localImagePath ?: original?.image ?: "") }
    var minExpiry by remember(original) { mutableStateOf(original?.minExpiredDuration?.toString() ?: "") }
    var maxExpiry by remember(original) { mutableStateOf(original?.maxExpiredDuration?.toString() ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var photoSheetVisible by rememberSaveable { mutableStateOf(false) }

    val openCamera = rememberCameraCapture { uri: Uri ->
        imagePath = uri.toString()
        vm.onImagePicked(uri.toString())
    }
    val openGallery = rememberGalleryPicker { uri: Uri ->
        imagePath = uri.toString()
        vm.onImagePicked(uri.toString())
    }

    val unitOptions = listOf("g", "kg", "mL", "L", "pcs", "bag", "carton", "bunch")
    val categoryOptions = listOf("Vegetable", "Fruit", "Meat", "Dairy", "Grain", "Spice", "Other")
    val aiState by vm.uiState.collectAsState()
    if (aiState.aiDialogVisible) {
        IngredientAiAssistantDialog(
            state = aiState,
            onDismiss = vm::closeAiAssistant,
            onSelectTab = vm::setAiTab,
            onGenerate = vm::requestAiSuggestions,
            onApply = {
                vm.applyAiSuggestion()
                val updatedAi = vm.uiState.value
                if (updatedAi.ingredientName.isNotBlank()) name = updatedAi.ingredientName
                if (updatedAi.category.isNotBlank()) category = updatedAi.category
                if (updatedAi.quantityText.isNotBlank()) weight = updatedAi.quantityText
                if (updatedAi.unit.isNotBlank()) unit = updatedAi.unit
                if (updatedAi.minExpiryDays.isNotBlank()) minExpiry = updatedAi.minExpiryDays
                if (updatedAi.maxExpiryDays.isNotBlank()) maxExpiry = updatedAi.maxExpiryDays
                if (updatedAi.description.isNotBlank()) description = updatedAi.description
            },
            onChatInputChange = vm::onAiChatInputChange,
            onSendChat = vm::sendAiChatMessage
        )
    }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        SmartPantrySectionHeader(
            title = "Edit ingredient",
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
                        modifier = Modifier.size(116.dp).clip(RoundedCornerShape(18.dp)),
                        recipeFallback = false
                    )
                    FilledIconButton(
                        onClick = { photoSheetVisible = true },
                        enabled = !isSaving,
                        modifier = Modifier.align(Alignment.BottomEnd).size(40.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, "Change ingredient photo")
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(100); errorMsg = null },
                        label = { Text("Ingredient name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    CompactDropdownField(
                        label = "Category",
                        value = category.ifBlank { "Vegetable" },
                        options = categoryOptions,
                        onSelected = { category = it }
                    )
                }
            }



            // AI Helper Trigger (Matches Community "Prepare a recipe draft" button)
            FilledTonalButton(
                onClick = vm::openAiAssistant,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Auto Fill & Ask Ai")
            }

            Text("Default measurement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.take(12) },
                    label = { Text("Unit qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Box(Modifier.weight(1f)) {
                    CompactDropdownField(
                        label = "Unit",
                        value = unit.ifBlank { "pcs" },
                        options = unitOptions,
                        onSelected = { unit = it }
                    )
                }
            }

            Text("Typical shelf life (Days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = minExpiry,
                    onValueChange = { minExpiry = it.filter(Char::isDigit).take(4) },
                    label = { Text("Min days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Text("–", color = scheme.onSurfaceVariant)
                OutlinedTextField(
                    value = maxExpiry,
                    onValueChange = { maxExpiry = it.filter(Char::isDigit).take(4) },
                    label = { Text("Max days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(1000) },
                label = { Text("Storage notes & description (optional)") },
                supportingText = { Text("${description.length}/1000") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )

            errorMsg?.let {
                Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMsg = "Name cannot be empty."
                        return@Button
                    }
                    isSaving = true
                    val updated = original.copy(
                        ingredientName = name.trim(),
                        category = category.trim().takeIf { it.isNotEmpty() },
                        weight = weight.toDoubleOrNull(),
                        unit = unit.trim().takeIf { it.isNotEmpty() },
                        description = description.trim().takeIf { it.isNotEmpty() },
                        localImagePath = if (imagePath.startsWith("content://") || imagePath.startsWith("/")) imagePath else original.localImagePath,
                        minExpiredDuration = minExpiry.toShortOrNull(),
                        maxExpiredDuration = maxExpiry.toShortOrNull(),
                        updateAt = System.currentTimeMillis()
                    )
                    vm.updateIngredient(original.ingredientId!!, updated)
                    onBackClick()
                },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                } else {
                    Text("Save changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (photoSheetVisible) {
        ModalBottomSheet(onDismissRequest = { photoSheetVisible = false }) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ingredient photo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
}