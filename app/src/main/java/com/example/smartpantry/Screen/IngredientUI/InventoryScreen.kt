package com.example.smartpantry.Screen.InventoryUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.models.IngredientUser
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import com.example.smartpantry.viewmodel.IngredientViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onAddInventoryClick: () -> Unit,
    onCreateIngredientClick: () -> Unit,
    onEditIngredientClick: (Long) -> Unit,
    vm: IngredientUserViewModel = viewModel(),
    ingredientVm: IngredientViewModel = viewModel(),
    onEditClick: (Long) -> Unit = {}
) {
    val inventory by vm.inventory.collectAsState()
    val availableIngredients by ingredientVm.ingredients.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var selectedView by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredIngredients = availableIngredients.filter {
        it.ingredientName.contains(searchQuery, ignoreCase = true) ||
                (it.category ?: "").contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text("Pantry", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                    },
                    actions = {
                        IconButton(onClick = {
                            vm.refreshInventory(force = true)
                            ingredientVm.refreshIngredients(force = true)
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

                TabRow(
                    selectedTabIndex = selectedView,
                    containerColor = scheme.background,
                    contentColor = scheme.primary
                ) {
                    Tab(
                        selected = selectedView == 0,
                        onClick = { selectedView = 0 },
                        text = { Text("My Inventory") }
                    )
                    Tab(
                        selected = selectedView == 1,
                        onClick = { selectedView = 1 },
                        text = { Text("My Ingredients") }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedView == 0) onAddInventoryClick()
                    else onCreateIngredientClick()
                },
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary
            ) {
                Icon(
                    if (selectedView == 0) Icons.Default.AddShoppingCart else Icons.Default.CreateNewFolder,
                    contentDescription = "Add"
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ---------- Search bar (only on My Ingredients tab) ----------
            if (selectedView == 1) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search ingredient...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (selectedView == 0) {
                    // ==========================================
                    // VIEW 1: MY INVENTORY
                    // ==========================================
                    if (inventory.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Inventory2,
                            title = "Inventory is empty",
                            subtitle = "Tap + to add items to your pantry",
                            scheme = scheme
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            items(inventory, key = { it.id ?: 0L }) { item ->
                                val info = availableIngredients.find { it.ingredientId == item.ingredientId }
                                InventoryRow(
                                    item = item,
                                    ingredientInfo = info,
                                    onDelete = { item.id?.let { vm.deleteInventoryItem(it) } },
                                    onEdit = { item.id?.let { onEditClick(it) } },
                                    onUse = { amount ->
                                        vm.useInventoryAmount(item, amount) { message, _ ->
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // VIEW 2: MY INGREDIENTS (CRUD)
                    // ==========================================
                    if (availableIngredients.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Category, null, modifier = Modifier.size(64.dp), tint = scheme.primary.copy(alpha = 0.5f))
                            Spacer(Modifier.height(16.dp))
                            Text("No ingredients yet", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("Create your first ingredient", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = onCreateIngredientClick,
                                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.Default.CreateNewFolder, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Create Ingredient", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else if (filteredIngredients.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp), tint = scheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(12.dp))
                            Text("No results for \"$searchQuery\"", color = scheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = onCreateIngredientClick) {
                                Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Create \"$searchQuery\"")
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            // Inline create button
                            item {
                                OutlinedButton(
                                    onClick = onCreateIngredientClick,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, scheme.primary)
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create new ingredient", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            items(filteredIngredients, key = { it.ingredientId ?: 0L }) { ing ->
                                IngredientMasterRow(
                                    ing = ing,
                                    scheme = scheme,
                                    onEdit = { ing.ingredientId?.let { onEditIngredientClick(it) } },   // ⬅️ navigate to edit page
                                    onDelete = { ing.ingredientId?.let { ingredientVm.deleteIngredient(it) } },
                                    onTogglePublish = { ing.ingredientId?.let { id -> ingredientVm.togglePublish(id, ing) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// EMPTY STATE
// ==========================================
@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    scheme: ColorScheme
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = scheme.primary.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold, color = scheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = scheme.onSurfaceVariant)
    }
}

// ==========================================
// INVENTORY ROW
// ==========================================
@Composable
private fun InventoryRow(
    item: IngredientUser,
    ingredientInfo: Ingredient?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onUse: (Double) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var showUseDialog by remember(item.id) { mutableStateOf(false) }
    var useAmountText by remember(item.id) { mutableStateOf("") }
    val currentQuantity = item.quantity ?: 0.0
    val localFile = ingredientInfo?.localImagePath
    val remoteUrl = ingredientInfo?.image
    val imageToLoad = if (localFile != null && localFile.isNotEmpty() && File(localFile).exists())
        File(localFile) else remoteUrl

    val dateFormat = SimpleDateFormat("dd MMM yyyy", LocalLocale.current.platformLocale)
    val expiryString = dateFormat.format(Date(item.expiredDate))
    val timeLeft = item.expiredDate - System.currentTimeMillis()
    val isExpiringSoon = timeLeft in 0..(3L * 24 * 60 * 60 * 1000)
    val isExpired = timeLeft < 0

    val badgeColor = when {
        isExpired -> scheme.error
        isExpiringSoon -> Color(0xFFF57C00)
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
            .clickable { onEdit() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (imageToLoad != null) {
                AsyncImage(model = imageToLoad, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(ingredientInfo?.ingredientName?.firstOrNull()?.uppercase() ?: "?", color = scheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(ingredientInfo?.ingredientName ?: "Unknown Ingredient", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                "${item.quantity ?: 0.0} ${item.quantityUnit ?: ""} • ${item.locationStorage ?: "Pantry"}",
                fontSize = 12.sp, color = scheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = if (isExpired || isExpiringSoon) 1f else 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                if (isExpired) "Expired" else expiryString,
                fontSize = 11.sp,
                color = if (isExpired || isExpiringSoon) badgeTextColor else badgeColor,
                fontWeight = FontWeight.Medium
            )
        }
        TextButton(
            onClick = {
                useAmountText = ""
                showUseDialog = true
            },
            enabled = currentQuantity > 0.0
        ) {
            Text("Use", fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete", tint = scheme.error)
        }
    }

    if (showUseDialog) {
        val amount = useAmountText.toDoubleOrNull()
        val invalidAmount = amount == null || amount <= 0.0 || amount > currentQuantity

        AlertDialog(
            onDismissRequest = { showUseDialog = false },
            title = { Text("Use ${ingredientInfo?.ingredientName ?: "ingredient"}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Available: $currentQuantity ${item.quantityUnit.orEmpty()}")
                    OutlinedTextField(
                        value = useAmountText,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d*(\\.\\d*)?$"))) {
                                useAmountText = value
                            }
                        },
                        label = { Text("Amount to use") },
                        placeholder = { Text("Enter amount") },
                        suffix = { Text(item.quantityUnit.orEmpty()) },
                        singleLine = true,
                        isError = useAmountText.isNotBlank() && invalidAmount
                    )
                    if (useAmountText.isNotBlank() && invalidAmount) {
                        Text(
                            if (amount == null || amount <= 0.0) "Enter an amount greater than zero."
                            else "You only have $currentQuantity ${item.quantityUnit.orEmpty()} available.",
                            color = scheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !invalidAmount,
                    onClick = {
                        amount?.let(onUse)
                        showUseDialog = false
                    }
                ) { Text("Confirm use") }
            },
            dismissButton = {
                TextButton(onClick = { showUseDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ==========================================
// MASTER INGREDIENT ROW (editable only if published)
// ==========================================
@Composable
private fun IngredientMasterRow(
    ing: Ingredient,
    scheme: ColorScheme,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePublish: () -> Unit
) {
    val localFile = ing.localImagePath
    val remoteUrl = ing.image
    val imageToLoad = if (localFile != null && File(localFile).exists()) File(localFile) else remoteUrl
    val isPublished = ing.isPublish == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant)
            .clickable(enabled = isPublished) { onEdit() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (imageToLoad != null) {
                AsyncImage(model = imageToLoad, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(ing.ingredientName.firstOrNull()?.uppercase() ?: "?", color = scheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(ing.ingredientName, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                "${ing.category ?: "Uncategorized"} • ${ing.weight ?: 0.0} ${ing.unit ?: ""}",
                fontSize = 12.sp, color = scheme.onSurfaceVariant
            )
        }

        // Publish toggle
        AssistChip(
            onClick = onTogglePublish,
            label = { Text(if (isPublished) "Published" else "Draft", fontSize = 10.sp) },
            leadingIcon = {
                Icon(
                    if (isPublished) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    null, modifier = Modifier.size(14.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (isPublished) scheme.primary.copy(alpha = 0.15f) else scheme.surfaceVariant,
                labelColor = if (isPublished) scheme.primary else scheme.onSurfaceVariant,
                leadingIconContentColor = if (isPublished) scheme.primary else scheme.onSurfaceVariant
            )
        )

        // Edit icon — only for published
        if (isPublished) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit", tint = scheme.primary)
            }
        }

        // Delete
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete", tint = scheme.error)
        }
    }
}