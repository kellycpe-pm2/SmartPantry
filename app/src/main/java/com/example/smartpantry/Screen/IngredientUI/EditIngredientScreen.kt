package com.example.smartpantry.Screen.IngredientUI

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
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

    // Find the ingredient to edit
    val original = ingredients.find { it.ingredientId == ingredientId }

    // Local form state

    val state by vm.uiState.collectAsState()

    var name by remember(original) { mutableStateOf(original?.ingredientName ?: "") }
    var category by remember(original) { mutableStateOf(original?.category ?: "") }
    var weight by remember(original) { mutableStateOf(original?.weight?.toString() ?: "") }
    var unit by remember(original) { mutableStateOf(original?.unit ?: "") }
    var description by remember(original) { mutableStateOf(original?.description ?: "") }
    var imagePath by remember(original) { mutableStateOf(original?.localImagePath ?: original?.image) }
    var minExpiry by remember(original) { mutableStateOf(original?.minExpiredDuration?.toString() ?: "") }
    var maxExpiry by remember(original) { mutableStateOf(original?.maxExpiredDuration?.toString() ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val unitOptions = listOf("g", "kg", "ml", "L", "pcs", "tbsp", "tsp")
    val categoryOptions = listOf("Vegetable", "Fruit", "Meat", "Dairy", "Grain", "Spice", "Other")

    // Handle not-found
    if (original == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Ingredient", fontWeight = FontWeight.Bold, color = scheme.primary) },
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
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = scheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading ingredient...", color = scheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    // Only published ingredients can be edited
    if (original.isPublish != true) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Ingredient", fontWeight = FontWeight.Bold, color = scheme.primary) },
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
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(64.dp), tint = scheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text("Cannot edit drafts", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Publish this ingredient first to edit it", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onBackClick, colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)) {
                        Text("Go back")
                    }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Edit Ingredient", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
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
            Spacer(Modifier.height(8.dp))

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
                imagePath = imagePath?.toString() ?: state.imagePath,
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
                value = name,
                onValueChange = { name = it; errorMsg = null },
                label = "Ingredient Name",
                image = Icons.Default.Inventory
            )
            Spacer(Modifier.height(16.dp))

            OutlineDropdownField(
                value = category,
                onValueChange = { category = it },
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
                    value = weight,
                    onValueChange = { weight = it },
                    label = "Weight",
                    image = Icons.Default.Scale,
                    modifier = Modifier.weight(0.60f),
                    supportingText = "*Quantity in a ingredient"
                )
                OutlineDropdownField(
                    value = unit,
                    onValueChange = { unit = it },
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
                Icon(Icons.Default.CalendarToday, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Text("Expired Duration (Days)", color = scheme.onBackground, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlineTextField(
                    value = minExpiry,
                    onValueChange = { minExpiry = it },
                    label = "Min",
                    modifier = Modifier.weight(1f)
                )
                Text("-", color = scheme.onSurfaceVariant)
                OutlineTextField(
                    value = maxExpiry,
                    onValueChange = { maxExpiry = it },
                    label = "Max",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))

            OutlineTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description",
                image = Icons.Default.Description,
                singleLine = false,
                minLines = 5,
                maxLines = 5
            )

            errorMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = scheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMsg = "Name cannot be empty"
                        return@Button
                    }
                    isSaving = true
                    val updated = original.copy(
                        ingredientName = name.trim(),
                        category = category.trim().takeIf { it.isNotEmpty() },
                        weight = weight.toDoubleOrNull(),
                        unit = unit.trim().takeIf { it.isNotEmpty() },
                        description = description.trim().takeIf { it.isNotEmpty() },
                        minExpiredDuration = minExpiry.toShortOrNull(),
                        maxExpiredDuration = maxExpiry.toShortOrNull(),
                        updateAt = System.currentTimeMillis()
                    )
                    vm.updateIngredient(original.ingredientId!!, updated)
                    onBackClick()
                },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = scheme.onPrimary
                    )
                } else {
                    Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}