package com.example.smartpantry.Screen.Screen.IngredientUI

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.uicomponent.*
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIngredientScreen(
    onBackClick: () -> Unit,
    onCreateIngredient: (Ingredient) -> Unit
) {
    var ingredientName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf<String?>(null) }
    var minExpiryDays by remember { mutableStateOf("") }
    var maxExpiryDays by remember { mutableStateOf("") }

    val unitOptions = listOf("g", "kg", "ml", "L", "pcs", "tbsp", "tsp")
    val categoryOptions = listOf("Vegetable", "Fruit", "Meat", "Dairy", "Grain", "Spice", "Other")

    var unitExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Ingredient",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPrimaryDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = BrandPrimaryDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandBackground,
                    titleContentColor = BrandPrimaryDark,
                    navigationIconContentColor = BrandPrimaryDark
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .border(
                        border = BorderStroke(1.dp, BrandBorder),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { /* Handle image selection */ },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Select Image",
                        tint = BrandMutedText,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (imagePath != null) "Image Selected" else "*Select Image",
                        fontSize = 14.sp,
                        color = if (imagePath != null) BrandPrimaryDark else BrandMutedText
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = ingredientName,
                onValueChange = { ingredientName = it },
                label = { Text("Ingredient Name *") },
                leadingIcon = {
                    Icon(Icons.Default.Inventory, null, tint = BrandPrimaryDark)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandPrimaryDark,
                    unfocusedBorderColor = BrandBorder,
                    focusedLabelColor = BrandPrimaryDark,
                    unfocusedLabelColor = BrandMutedText,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Ingredient Category") },
                    leadingIcon = {
                        Icon(Icons.Default.Category, null, tint = BrandPrimaryDark)
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimaryDark,
                        unfocusedBorderColor = BrandBorder,
                        focusedLabelColor = BrandPrimaryDark,
                        unfocusedLabelColor = BrandMutedText,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categoryOptions.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Ingredient Quantity") },
                    leadingIcon = {
                        Icon(Icons.Default.Scale, null, tint = BrandPrimaryDark)
                    },
                    modifier = Modifier.weight(0.65f),
                    shape = RoundedCornerShape(8.dp),
                    supportingText = { Text("Quantity in a ingredient", fontSize = 10.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimaryDark,
                        unfocusedBorderColor = BrandBorder,
                        focusedLabelColor = BrandPrimaryDark,
                        unfocusedLabelColor = BrandMutedText,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = !unitExpanded },
                    modifier = Modifier.weight(0.35f)
                ) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPrimaryDark,
                            unfocusedBorderColor = BrandBorder,
                            focusedLabelColor = BrandPrimaryDark,
                            unfocusedLabelColor = BrandMutedText,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        unitOptions.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = {
                                    unit = u
                                    unitExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = minExpiryDays,
                    onValueChange = { minExpiryDays = it },
                    label = { Text("Min Expiry (days)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimaryDark,
                        unfocusedBorderColor = BrandBorder,
                        focusedLabelColor = BrandPrimaryDark,
                        unfocusedLabelColor = BrandMutedText,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                OutlinedTextField(
                    value = maxExpiryDays,
                    onValueChange = { maxExpiryDays = it },
                    label = { Text("Max Expiry (days)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimaryDark,
                        unfocusedBorderColor = BrandBorder,
                        focusedLabelColor = BrandPrimaryDark,
                        unfocusedLabelColor = BrandMutedText,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                leadingIcon = {
                    Icon(Icons.Default.Description, null, tint = BrandPrimaryDark)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(8.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandPrimaryDark,
                    unfocusedBorderColor = BrandBorder,
                    focusedLabelColor = BrandPrimaryDark,
                    unfocusedLabelColor = BrandMutedText,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val newIngredient = Ingredient(
                        ingredientName = ingredientName.trim(),
                        category = category.takeIf { it.isNotEmpty() },
                        weight = quantityText.toBigDecimalOrNull(),
                        unit = unit.takeIf { it.isNotEmpty() },
                        description = description.takeIf { it.isNotEmpty() },
                        image = imagePath,
                        minExpiredDuration = minExpiryDays.toShortOrNull(),
                        maxExpiredDuration = maxExpiryDays.toShortOrNull(),
                        createAt = System.currentTimeMillis(),
                        updateAt = System.currentTimeMillis()
                    )
                    onCreateIngredient(newIngredient)
                },
                enabled = ingredientName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandPrimaryDark,
                    contentColor = BrandBackground
                )
            ) {
                Text(
                    text = "Create",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
