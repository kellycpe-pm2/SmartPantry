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
import androidx.compose.ui.text.font.FontVariation.weight
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
fun CreateIngredientScreen(
    onBackClick: () -> Unit,
    vm: IngredientViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val scheme = MaterialTheme.colorScheme

    val unitOptions = listOf("g", "kg", "mL", "L", "pcs", "bag", "carton", "bunch")
    val categoryOptions = listOf("Vegetable", "Fruit", "Meat", "Dairy", "Grain", "Spice", "Other")

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var photoSheetVisible by rememberSaveable { mutableStateOf(false) }

    val openCamera = rememberCameraCapture { uri ->
        pickedUri = uri
        vm.onImagePicked(uri.toString())
    }
    val openGallery = rememberGalleryPicker { uri ->
        pickedUri = uri
        vm.onImagePicked(uri.toString())
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            vm.consumeSaved()
            onBackClick()
        }
    }

    if (state.aiDialogVisible) {
        IngredientAiAssistantDialog(
            state = state,
            onDismiss = vm::closeAiAssistant,
            onSelectTab = vm::setAiTab,
            onGenerate = vm::requestAiSuggestions,
            onApply = vm::applyAiSuggestion,
            onChatInputChange = vm::onAiChatInputChange,
            onSendChat = vm::sendAiChatMessage
        )
    }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            icon = { Icon(Icons.Default.Info, null) },
            title = { Text("Couldn't save") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK") } }
        )
    }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        SmartPantrySectionHeader(
            title = "Create ingredient",
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
            // Photo + Name + Category Row (Matches Community RecipeEditor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    val displayImage = pickedUri?.toString() ?: state.imagePath.orEmpty()
                    RecipePhoto(
                        url = displayImage,
                        modifier = Modifier.size(116.dp).clip(RoundedCornerShape(18.dp)),
                        recipeFallback = false
                    )
                    FilledIconButton(
                        onClick = { photoSheetVisible = true },
                        enabled = !state.isSaving,
                        modifier = Modifier.align(Alignment.BottomEnd).size(40.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, "Change ingredient photo")
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.ingredientName,
                        onValueChange = { vm.onNameChange(it.take(100)) },
                        label = { Text("Ingredient name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    CompactDropdownField(
                        label = "Category",
                        value = state.category.ifBlank { "Vegetable" },
                        options = categoryOptions,
                        onSelected = vm::onCategoryChange
                    )
                }
            }

            // AI Helper Trigger (Matches Community "Prepare a recipe draft" button)
            FilledTonalButton(
                onClick = vm::openAiAssistant,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Auto Fill & Ask Ai")
            }

            // Default Package Weight & Unit
            Text("Default measurement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = state.quantityText,
                    onValueChange = { vm.onQuantityChange(it.take(12))},
                    label = { Text("Unit qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Box(Modifier.weight(1f)) {
                    CompactDropdownField(
                        label = "Unit",
                        value = state.unit.ifBlank { "pcs" },
                        options = unitOptions,
                        onSelected = vm::onUnitChange
                    )
                }
            }
            // Shelf Life Duration
            Text("Typical shelf life (Days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.minExpiryDays,
                    onValueChange = { vm.onMinExpiryChange(it.filter(Char::isDigit).take(4)) },
                    label = { Text("Min days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Text("–", color = scheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.maxExpiryDays,
                    onValueChange = { vm.onMaxExpiryChange(it.filter(Char::isDigit).take(4)) },
                    label = { Text("Max days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Description / Storage Notes
            OutlinedTextField(
                value = state.description,
                onValueChange = { vm.onDescriptionChange(it.take(1000)) },
                label = { Text("Storage notes & description (optional)") },
                supportingText = { Text("${state.description.length}/1000") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = vm::create,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                } else {
                    Text("Save ingredient", fontWeight = FontWeight.Bold)
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
                if (pickedUri != null || !state.imagePath.isNullOrBlank()) {
                    TextButton(onClick = {
                        pickedUri = null
                        vm.onImagePicked(null)
                        photoSheetVisible = false
                    }) {
                        Icon(Icons.Default.DeleteOutline, null)
                        Text("Remove photo")
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 6.dp)
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(Icons.Default.ExpandMore, "Choose $label", Modifier.size(28.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}