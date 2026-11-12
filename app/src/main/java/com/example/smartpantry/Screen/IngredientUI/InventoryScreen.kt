package com.example.smartpantry.Screen.IngredientUI

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.IngredientUser
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import com.example.smartpantry.viewmodel.IngredientViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onAddInventoryClick: () -> Unit,
    vm: IngredientUserViewModel = viewModel(),
    ingredientVm: IngredientViewModel = viewModel()
) {
    val inventory by vm.inventory.collectAsState()
    val availableIngredients by ingredientVm.ingredients.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("My Inventory", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                },
                actions = {
                    IconButton(onClick = {
                        // You can add a refresh function in IngredientUserViewModel if needed
                        // vm.refreshInventory()
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = scheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.primary,
                    actionIconContentColor = scheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddInventoryClick,
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add to Inventory")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (inventory.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Kitchen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = scheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Inventory is empty", fontWeight = FontWeight.Bold, color = scheme.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap + to add items to your pantry", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 0.dp)
                ) {
                    items(inventory, key = { it.id ?: 0L }) { item ->
                        val ingredientInfo = availableIngredients.find { it.ingredientId == item.ingredientId }
                        InventoryRow(item, ingredientInfo, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryRow(
    item: IngredientUser,
    ingredientInfo: Ingredient?,
    vm: IngredientUserViewModel
) {
    val scheme = MaterialTheme.colorScheme

    // Format expiration date safely
    val dateFormat = SimpleDateFormat("dd MMM yyyy", LocalLocale.current.platformLocale)
    val expiryString = dateFormat.format(Date(item.expiredDate))

    // Check if expiring soon (within 3 days) or already expired
    val timeLeft = item.expiredDate - System.currentTimeMillis()
    val isExpiringSoon = timeLeft in 0..(3 * 24 * 60 * 60 * 1000)
    val isExpired = timeLeft < 0

    val badgeColor = when {
        isExpired -> scheme.error
        isExpiringSoon -> Color(0xFFF57C00) // Orange
        else -> scheme.primary
    }

    val badgeTextColor = when {
        isExpired -> scheme.onError
        isExpiringSoon -> Color.White
        else -> scheme.onPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon Placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Kitchen, contentDescription = null, tint = scheme.primary)
        }

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ingredientInfo?.ingredientName ?: "Unknown Ingredient",
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${item.quantity ?: 0.0} ${item.quantityUnit ?: ""} • ${item.locationStorage ?: "Pantry"}",
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant
            )
        }

        // Expiry Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = if (isExpired || isExpiringSoon) 1f else 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isExpired) "Expired" else expiryString,
                fontSize = 11.sp,
                color = if (isExpired || isExpiringSoon) badgeTextColor else badgeColor,
                fontWeight = FontWeight.Medium
            )
        }

        // Delete Button
        IconButton(onClick = {
            item.id?.let { vm.deleteInventoryItem(it) }
        }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = scheme.error)
        }
    }
}