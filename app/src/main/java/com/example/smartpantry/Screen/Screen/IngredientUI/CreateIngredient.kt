package com.example.smartpantry.Screen.Screen.IngredientUI

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.hardware.rememberCameraCapture
import com.example.smartpantry.hardware.rememberGalleryPicker
import com.example.smartpantry.ui.theme.ImageInput
import com.example.smartpantry.ui.theme.OutlineDropdownField
import com.example.smartpantry.ui.theme.OutlineTextField
import com.example.smartpantry.viewmodel.CreateIngredientViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIngredientScreen(
    onBackClick: () -> Unit,
    vm: CreateIngredientViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val scheme = MaterialTheme.colorScheme

    val unitOptions = listOf("g", "kg", "ml", "L", "pcs", "tbsp", "tsp")
    val categoryOptions = listOf("Vegetable", "Fruit", "Meat", "Dairy", "Grain", "Spice", "Other")

    LaunchedEffect(state.saved) {
        if (state.saved) onBackClick()
    }

    // Error dialog
    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) { Text("OK") }
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
                        text = "Create Ingredient",
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(16.dp))

            var pickedUri by remember { mutableStateOf<Uri?>(null) }
            var showSourceDialog by remember { mutableStateOf(false) }

            val openCamera = rememberCameraCapture { uri ->
                pickedUri = uri
                vm.onImagePicked(uri.toString())
            }

            val openGallery = rememberGalleryPicker { uri ->
                pickedUri = uri
                vm.onImagePicked(uri.toString())
            }


            Spacer(Modifier.height(16.dp))

            ImageInput(
                imagePath = pickedUri?.toString() ?: state.imagePath,
                onClick = { showSourceDialog = true }       // opens chooser
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
                value = state.ingredientName,
                onValueChange = vm::onNameChange,
                label = "Ingredient Name",
                image = Icons.Default.Inventory
            )
            Spacer(Modifier.height(16.dp))

            OutlineDropdownField(
                value = state.category,
                onValueChange = vm::onCategoryChange,
                options = categoryOptions,
                label = "Ingredient Category",
                image = Icons.Default.Category
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlineTextField(
                    value = state.quantityText,
                    onValueChange = vm::onQuantityChange,
                    label = "Weight",
                    image = Icons.Default.Scale,
                    modifier = Modifier.weight(0.60f),
                    supportingText = "*Quantity in a ingredient"
                )
                OutlineDropdownField(
                    value = state.unit,
                    onValueChange = vm::onUnitChange,
                    options = unitOptions,
                    label = "Unit",
                    modifier = Modifier.weight(0.40f)
                )
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Expired Duration (Days)",
                    color = scheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlineTextField(
                    value = state.minExpiryDays,
                    onValueChange = vm::onMinExpiryChange,
                    label = "Min",
                    modifier = Modifier.weight(1f)
                )
                Text("-", color = scheme.onSurfaceVariant)
                OutlineTextField(
                    value = state.maxExpiryDays,
                    onValueChange = vm::onMaxExpiryChange,
                    label = "Max",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))

            OutlineTextField(
                value = state.description,
                onValueChange = vm::onDescriptionChange,
                label = "Description",
                image = Icons.Default.Description,
                singleLine = false,
                minLines = 5,
                maxLines = 5
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = vm::create,
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
                    Text("Create", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}