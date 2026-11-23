package com.example.smartpantry.Screen.IngredientUI

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import com.example.smartpantry.viewmodel.IngredientViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInventoryScreen(
    itemId: Long,
    onBackClick: () -> Unit,
    vm: IngredientUserViewModel = viewModel(),
    ingredientVm: IngredientViewModel = viewModel()
) {
    val inventory by vm.inventory.collectAsState()
    val allIngredients by ingredientVm.ingredients.collectAsState()
    val scheme = MaterialTheme.colorScheme

    // Find the item being edited
    val item = inventory.find { it.id == itemId }
    val ingredientInfo = allIngredients.find { it.ingredientId == item?.ingredientId }

    // Form state (initialized once the item is found)
    var qty by remember(item) { mutableStateOf(item?.quantity?.toString() ?: "1") }
    var unit by remember(item) { mutableStateOf(item?.quantityUnit ?: "pcs") }
    var location by remember(item) { mutableStateOf(item?.locationStorage ?: "Pantry") }

    // Calculate days left
    var days by remember(item) {
        mutableStateOf(
            ((item?.expiredDate ?: 0L) - System.currentTimeMillis())
                .let { if (it < 0) 0 else it / (24 * 60 * 60 * 1000) }
                .toString()
        )
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (item == null) {
        // Loading or not found state
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val currentExpiry = dateFormat.format(Date(item.expiredDate))

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Edit Item", fontWeight = FontWeight.Bold) },
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
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = scheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Delete Button
                    OutlinedButton(
                        onClick = {
                            item.id?.let { id ->
                                vm.deleteInventoryItem(id)
                                onBackClick()
                            }
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                        border = BorderStroke(1.dp, scheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                    }

                    // Save Button
                    Button(
                        onClick = {
                            val qtyDouble = qty.toDoubleOrNull()
                            if (qtyDouble == null || qtyDouble <= 0) {
                                errorMessage = "Quantity must be a positive number"
                                return@Button
                            }

                            val newExpiry = System.currentTimeMillis() +
                                    ((days.toLongOrNull() ?: 7L) * 24 * 60 * 60 * 1000)

                            val updated = item.copy(
                                quantity = qtyDouble,
                                quantityUnit = unit.trim(),
                                locationStorage = location.trim(),
                                expiredDate = newExpiry
                            )

                            item.id?.let { id ->
                                vm.updateInventoryItem(id, updated)
                            }
                            onBackClick()
                        },
                        modifier = Modifier.weight(2f).height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header (Image + Name)
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val localFile = ingredientInfo?.localImagePath
                    val remoteUrl = ingredientInfo?.image
                    val imageToLoad = if (localFile != null && File(localFile).exists()) File(localFile) else remoteUrl

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
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
                            Text(
                                ingredientInfo?.ingredientName?.firstOrNull()?.uppercase() ?: "?",
                                color = scheme.primary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            ingredientInfo?.ingredientName ?: "Unknown",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Currently expires: $currentExpiry",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Error Message
            errorMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = scheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = scheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = scheme.onErrorContainer, fontSize = 12.sp)
                    }
                }
            }

            // 3. Form Fields
            Text("Quantity", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it; errorMessage = null },
                    label = { Text("Amount") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = errorMessage != null
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("g, ml, pcs") }
                )
            }

            Text("Storage Location", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Where is it stored?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Place, null, tint = scheme.primary) }
            )

            Text("Expiration", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = days,
                onValueChange = { days = it },
                label = { Text("Expires in (days)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.DateRange, null, tint = scheme.primary) },
                supportingText = {
                    val previewDays = days.toLongOrNull() ?: 0
                    val previewDate = System.currentTimeMillis() + previewDays * 24 * 60 * 60 * 1000
                    Text("Will expire on: ${dateFormat.format(Date(previewDate))}")
                }
            )
        }
    }
}