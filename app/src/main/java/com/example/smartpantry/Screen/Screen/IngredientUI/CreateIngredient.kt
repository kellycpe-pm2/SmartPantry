package com.example.smartpantry.Screen.Screen.IngredientUI

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
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.ui.theme.ImageInput
import com.example.smartpantry.ui.theme.OutlineDropdownField
import com.example.smartpantry.ui.theme.OutlineTextField

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

    val scheme = MaterialTheme.colorScheme

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
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.primary
                        )
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
            Spacer(modifier = Modifier.height(16.dp))

            ImageInput()

            Spacer(modifier = Modifier.height(24.dp))

            // Ingredient name
            OutlineTextField(
                value = ingredientName,
                onValueChange = { ingredientName = it },
                label = "Ingredient Name *",
                image = Icons.Default.Inventory
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category dropdown
            OutlineDropdownField(
                value = category,
                onValueChange = { category = it },
                options = categoryOptions,
                label = "Ingredient Category",
                image = Icons.Default.Category
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quantity + Unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlineTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
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

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = "Expired Duration (Days)",
                    color = scheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Min / Max fields below the title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlineTextField(
                    value = minExpiryDays,
                    onValueChange = { minExpiryDays = it },
                    label = "Min",
                    modifier = Modifier.weight(1f)
                )
                Text("-", color = scheme.onSurfaceVariant)
                OutlineTextField(
                    value = maxExpiryDays,
                    onValueChange = { maxExpiryDays = it },
                    label = "Max",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            OutlineTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description",
                image = Icons.Default.Description,
                singleLine = false,
                minLines = 5,
                maxLines = 5
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
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                )
            ) {
                Text(
                    text = "Create",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}