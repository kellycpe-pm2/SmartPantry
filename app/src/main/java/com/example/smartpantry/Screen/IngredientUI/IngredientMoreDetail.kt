package com.example.smartpantry.Screen.IngredientUI

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.IngredientUser
import com.example.smartpantry.Screen.InventoryUI.formatShelfLife
import com.example.smartpantry.community.RecipePhoto
import com.example.smartpantry.community.SmartPantrySectionHeader
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import com.example.smartpantry.viewmodel.IngredientViewModel
import com.example.smartpantry.viewmodel.recipe_vm
import io.github.jan.supabase.auth.auth
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private fun calendarDayKey(ts: Long): String {
    if (ts <= 0L) return "NO_EXPIRY"
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}

private data class BatchDisplayGroup(
    val representative: IngredientUser,
    val memberIds: List<Long>,
    val totalQuantity: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientDetailScreen(
    ingredientId: Long,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onAddInventoryClick: (Long) -> Unit = {},
    onRecipeClick: (Long) -> Unit = {},
    ingredientVm: IngredientViewModel = viewModel(),
    inventoryVm: IngredientUserViewModel = viewModel(),
    recipeVm: recipe_vm = viewModel()
) {
    val ingredients by ingredientVm.ingredients.collectAsState()
    val inventory by inventoryVm.inventory.collectAsState()
    val recipes by recipeVm.recipes.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val currentLocale = LocalLocale.current.platformLocale

    val ingredient = remember(ingredients, ingredientId) {
        ingredients.firstOrNull { it.ingredientId == ingredientId }
    }

    // Group batches ONLY when location, calendar expiry day, AND unit match
    val groupedBatches = remember(inventory, ingredientId) {
        inventory
            .filter { it.ingredientId == ingredientId && (it.quantity ?: 0.0) > 0.0 }
            .groupBy { batch ->
                val locKey = batch.locationStorage.orEmpty().trim().lowercase()
                val unitKey = batch.quantityUnit.orEmpty().trim().lowercase()
                val dayKey = calendarDayKey(batch.expiredDate)
                "$locKey|$dayKey|$unitKey"
            }
            .values
            .map { group ->
                val primary = group.first()
                val combinedQty = group.sumOf { it.quantity ?: 0.0 }
                BatchDisplayGroup(
                    representative = primary.copy(quantity = combinedQty),
                    memberIds = group.mapNotNull { it.id },
                    totalQuantity = combinedQty
                )
            }
            .sortedBy {
                if (it.representative.expiredDate > 0L) it.representative.expiredDate else Long.MAX_VALUE
            }
    }

    val deduplicatedBatches = remember(groupedBatches) {
        groupedBatches.map { it.representative }
    }

    // Related recipes using this ingredient
    val relatedRecipes by produceState<List<Recipe>>(
        initialValue = emptyList(),
        recipes,
        ingredientId
    ) {
        value = recipes.filter { recipe ->
            val rId = recipe.recipeId ?: return@filter false
            recipeVm.getIngredientsForRecipeOnce(rId).any { it.ingredientId == ingredientId }
        }
    }

    val currentUserId = remember { supabase.auth.currentUserOrNull()?.id }
    val isOwner =
        ingredient != null && !currentUserId.isNullOrBlank() && ingredient.userId == currentUserId

    // Expand / collapse states for > 3 items
    var showAllBatches by remember { mutableStateOf(false) }
    var showAllRecipes by remember { mutableStateOf(false) }

    // Top-level sheet & dialog states
    var showExportMenu by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var editingBatch by remember { mutableStateOf<IngredientUser?>(null) }
    var deleteDialogGroup by remember { mutableStateOf<BatchDisplayGroup?>(null) }

    var sheetQty by remember { mutableStateOf("") }
    var sheetUnit by remember { mutableStateOf("pcs") }
    var sheetLocation by remember { mutableStateOf("Fridge") }
    var sheetExpiryDaysText by remember { mutableStateOf("") }
    var sheetExpiryMillis by remember { mutableStateOf(0L) }

    var unitMenuExpanded by remember { mutableStateOf(false) }
    var locationMenuExpanded by remember { mutableStateOf(false) }

    // Use Stock Dialog
    var useDialogBatch by remember { mutableStateOf<IngredientUser?>(null) }
    var useAmountInput by remember { mutableStateOf("") }

    var feedbackBanner by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val totalStock = deduplicatedBatches.sumOf { it.quantity ?: 0.0 }
    val primaryUnit = deduplicatedBatches.firstOrNull()?.quantityUnit ?: ingredient?.unit.orEmpty()

    val visibleBatches = remember(groupedBatches, showAllBatches) {
        if (showAllBatches || groupedBatches.size <= 3) groupedBatches else groupedBatches.take(3)
    }
    val visibleRecipes = remember(relatedRecipes, showAllRecipes) {
        if (showAllRecipes || relatedRecipes.size <= 3) relatedRecipes else relatedRecipes.take(3)
    }

    Scaffold(
        topBar = {
            SmartPantrySectionHeader(
                title = "Ingredient details",
                onBack = onBackClick,
                showBack = true,
                action = {
                    if (ingredient != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Outlined.Share, "Share & export", tint = scheme.primary)
                            }
                        }
                    }

                }
            )
        }
    ) { innerPadding ->
        if (ingredient == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val imgUrl = ingredient.localImagePath?.takeIf { File(it).exists() }
            ?: ingredient.image.orEmpty()
        val shelfLife =
            formatShelfLife(ingredient.minExpiredDuration, ingredient.maxExpiredDuration)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            feedbackBanner?.let { msg ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (feedbackIsError) scheme.errorContainer else scheme.primaryContainer
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                msg,
                                Modifier.weight(1f),
                                color = if (feedbackIsError) scheme.onErrorContainer else scheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            IconButton(
                                onClick = { feedbackBanner = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "Dismiss", Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // 1. Hero Image
            item {
                RecipePhoto(
                    url = imgUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(22.dp))
                )
            }

            // 2. Title & Status Pills
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = ingredient.ingredientName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ingredient.category?.takeIf { it.isNotBlank() }?.let { cat ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = scheme.primaryContainer
                            ) {
                                Text(
                                    cat,
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onPrimaryContainer
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                !isOwner -> Color(0xFFE0F2FE)
                                ingredient.isPublish == true -> Color(0xFFE4F4EA)
                                else -> scheme.surfaceVariant
                            }
                        ) {
                            Text(
                                when {
                                    !isOwner -> "Community"
                                    ingredient.isPublish == true -> "Published"
                                    else -> "Private"
                                },
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    !isOwner -> Color(0xFF0369A1)
                                    ingredient.isPublish == true -> Color(0xFF216B43)
                                    else -> scheme.onSurfaceVariant
                                }
                            )
                        }

                        if (shelfLife != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = scheme.surfaceVariant
                            ) {
                                Text(
                                    "Shelf life: $shelfLife",
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. Summary Metric Strip
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Total Stock",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                            Text(
                                "$totalStock $primaryUnit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Batches",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                            Text(
                                "${groupedBatches.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Default Pack",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                            val packStr = buildString {
                                ingredient.weight?.takeIf { it > 0.0 }?.let { append("$it ") }
                                append(ingredient.unit?.ifBlank { "pcs" } ?: "pcs")
                            }
                            Text(
                                packStr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. Ingredient Information & Description
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Ingredient Information",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        val dateFormatter = remember(currentLocale) {
                            SimpleDateFormat(
                                "dd MMM yyyy",
                                currentLocale
                            )
                        }
                        ingredient.createAt?.takeIf { it > 0L }?.let { created ->
                            Text(
                                "Created on: ${dateFormatter.format(Date(created))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        ingredient.updateAt?.takeIf { it > 0L }?.let { updated ->
                            Text(
                                "Last updated: ${dateFormatter.format(Date(updated))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }

                        if (!ingredient.description.isNullOrBlank()) {
                            HorizontalDivider(color = scheme.outlineVariant)
                            Text(
                                ingredient.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. Pantry Batches Section + "+ Add Stock"
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Pantry Batches (${groupedBatches.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(
                        onClick = {
                            // Always start fresh so previous batch values never leak into a new batch
                            editingBatch = null
                            sheetQty = ""
                            sheetUnit =
                                ingredient.unit?.ifBlank { "pcs" } ?: primaryUnit.ifBlank { "pcs" }
                            sheetLocation = "Fridge"
                            val defaultDays = (ingredient.maxExpiredDuration
                                ?: ingredient.minExpiredDuration)?.toInt() ?: 0
                            sheetExpiryDaysText =
                                if (defaultDays > 0) defaultDays.toString() else ""
                            sheetExpiryMillis = if (defaultDays > 0) {
                                Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, defaultDays)
                                    set(Calendar.HOUR_OF_DAY, 23)
                                    set(Calendar.MINUTE, 59)
                                    set(Calendar.SECOND, 0)
                                }.timeInMillis
                            } else 0L
                            showAddSheet = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Stock", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (groupedBatches.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = scheme.surfaceVariant,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "No stock in your pantry right now",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tap \"Add Stock\" above to record quantity, storage location, and expiry days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    visibleBatches,
                    key = { it.representative.id ?: it.hashCode().toLong() }) { group ->
                    val batch = group.representative
                    val daysLeft = if (batch.expiredDate > 0L) {
                        TimeUnit.MILLISECONDS.toDays(batch.expiredDate - now)
                    } else null

                    val expiryText = if (batch.expiredDate > 0L) {
                        val dateStr = SimpleDateFormat("dd MMM yyyy", currentLocale)
                            .format(Date(batch.expiredDate))
                        when {
                            daysLeft != null && daysLeft < 0 -> "Expired on $dateStr"
                            daysLeft == 0L -> "Expires today ($dateStr)"
                            daysLeft != null && daysLeft <= 3 -> "Expires in $daysLeft day(s) ($dateStr)"
                            else -> "Expires $dateStr"
                        }
                    } else "No expiry date"

                    val expiryColor = when {
                        daysLeft == null -> scheme.onSurfaceVariant
                        daysLeft < 0 -> scheme.error
                        daysLeft <= 3 -> Color(0xFFB45309)
                        else -> Color(0xFF216B43)
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surface,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "${batch.quantity ?: 0.0} ${batch.quantityUnit.orEmpty()} • ${batch.locationStorage ?: "Pantry"}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    expiryText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = expiryColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Edit batch
                            IconButton(
                                onClick = {
                                    editingBatch = batch
                                    sheetQty = (batch.quantity ?: 0.0).toString()
                                    sheetUnit = batch.quantityUnit.orEmpty()
                                        .ifBlank { primaryUnit.ifBlank { "pcs" } }
                                    sheetLocation =
                                        batch.locationStorage.orEmpty().ifBlank { "Pantry" }
                                    sheetExpiryMillis = batch.expiredDate
                                    val remDays = if (batch.expiredDate > 0L) {
                                        TimeUnit.MILLISECONDS.toDays(batch.expiredDate - System.currentTimeMillis())
                                            .coerceAtLeast(0L)
                                    } else null
                                    sheetExpiryDaysText = remDays?.toString().orEmpty()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit batch",
                                    tint = scheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Delete batch
                            IconButton(
                                onClick = { deleteDialogGroup = group },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete batch",
                                    tint = scheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Use stock
                            FilledTonalButton(
                                onClick = {
                                    useDialogBatch = batch
                                    useAmountInput = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Use")
                            }
                        }
                    }
                }

                if (groupedBatches.size > 3) {
                    item {
                        TextButton(
                            onClick = { showAllBatches = !showAllBatches },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (showAllBatches) "Show fewer batches"
                                else "See all ${groupedBatches.size} batches"
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (showAllBatches) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                    }
                }
            }

            // 6. Related Recipes Using This Ingredient
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Related Recipes (${relatedRecipes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (relatedRecipes.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surfaceVariant,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No personal recipes use ${ingredient.ingredientName} yet.",
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(visibleRecipes, key = { it.recipeId ?: it.hashCode().toLong() }) { recipe ->
                    val rId = recipe.recipeId ?: 0L
                    val rImg = recipe.localRecipeImage?.takeIf { File(it).exists() }
                        ?: recipe.recipeImage.orEmpty()

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surface,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (rId > 0) onRecipeClick(rId) }
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RecipePhoto(
                                url = rImg,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    recipe.recipeName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                recipe.recipeCategory?.takeIf { it.isNotBlank() }?.let { cat ->
                                    Text(
                                        cat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Open recipe",
                                tint = scheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (relatedRecipes.size > 3) {
                    item {
                        TextButton(
                            onClick = { showAllRecipes = !showAllRecipes },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (showAllRecipes) "Show fewer recipes"
                                else "See all ${relatedRecipes.size} recipes"
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (showAllRecipes) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                    }
                }

            }
        }




                // ==========================================
                // SLIDE-UP BOTTOM SHEET: ADD STOCK & EDIT BATCH
                // ==========================================
                val isSheetOpen = showAddSheet || editingBatch != null
                if (isSheetOpen && ingredient != null) {
                    val isEditing = editingBatch != null
                    val dateFormat =
                        remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                    val unitOptions =
                        listOf("pcs", "g", "kg", "ml", "L", "pack", "box", "can", "bottle")
                    val locationOptions =
                        listOf("Fridge", "Freezer", "Pantry", "Counter", "Spice Rack")

                    ModalBottomSheet(
                        onDismissRequest = {
                            showAddSheet = false
                            editingBatch = null
                            unitMenuExpanded = false
                            locationMenuExpanded = false
                        },
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        containerColor = scheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 8.dp)
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = if (isEditing) "Edit ${ingredient.ingredientName}" else "Add ${ingredient.ingredientName}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Batches with the same storage location, unit, and expiry date merge automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )

                            // 1. Quantity + Unit Dropdown Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = sheetQty,
                                    onValueChange = { sheetQty = it },
                                    label = { Text("Quantity") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1.1f)
                                )

                                ExposedDropdownMenuBox(
                                    expanded = unitMenuExpanded,
                                    onExpandedChange = { unitMenuExpanded = !unitMenuExpanded },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = sheetUnit,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Unit") },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(
                                                expanded = unitMenuExpanded
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = unitMenuExpanded,
                                        onDismissRequest = { unitMenuExpanded = false }
                                    ) {
                                        unitOptions.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option) },
                                                onClick = {
                                                    sheetUnit = option
                                                    unitMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Storage Location Dropdown
                            ExposedDropdownMenuBox(
                                expanded = locationMenuExpanded,
                                onExpandedChange = { locationMenuExpanded = !locationMenuExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = sheetLocation,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Storage Location") },
                                    leadingIcon = { Icon(Icons.Default.Kitchen, null) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = locationMenuExpanded
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = locationMenuExpanded,
                                    onDismissRequest = { locationMenuExpanded = false }
                                ) {
                                    locationOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                sheetLocation = option
                                                locationMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 3. Expired Days Input + Calendar Picker Icon
                            OutlinedTextField(
                                value = sheetExpiryDaysText,
                                onValueChange = { input ->
                                    val digitsOnly = input.filter { it.isDigit() }.take(4)
                                    sheetExpiryDaysText = digitsOnly
                                    val days = digitsOnly.toLongOrNull()
                                    sheetExpiryMillis = if (days != null) {
                                        val cal = Calendar.getInstance().apply {
                                            add(Calendar.DAY_OF_YEAR, days.toInt())
                                            set(Calendar.HOUR_OF_DAY, 23)
                                            set(Calendar.MINUTE, 59)
                                            set(Calendar.SECOND, 0)
                                        }
                                        cal.timeInMillis
                                    } else {
                                        0L
                                    }
                                },
                                label = { Text("Expires in (days)") },
                                placeholder = { Text("e.g. 7") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = {
                                    Text(
                                        if (sheetExpiryMillis > 0L) {
                                            "Expiry date: ${dateFormat.format(Date(sheetExpiryMillis))}"
                                        } else {
                                            "Leave blank for no expiry date, or tap calendar to pick date"
                                        }
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val cal = Calendar.getInstance().apply {
                                            if (sheetExpiryMillis > 0L) timeInMillis =
                                                sheetExpiryMillis
                                        }
                                        DatePickerDialog(
                                            context,
                                            { _, y, m, d ->
                                                val picked = Calendar.getInstance().apply {
                                                    set(y, m, d, 23, 59, 0)
                                                }
                                                sheetExpiryMillis = picked.timeInMillis
                                                val diffDays = TimeUnit.MILLISECONDS.toDays(
                                                    picked.timeInMillis - System.currentTimeMillis()
                                                ).coerceAtLeast(0L)
                                                sheetExpiryDaysText = diffDays.toString()
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }) {
                                        Icon(
                                            Icons.Default.Event,
                                            contentDescription = "Pick expiry date"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 4. Confirm Button
                            Button(
                                onClick = {
                                    val qty = sheetQty.toDoubleOrNull() ?: 0.0
                                    val targetEdit = editingBatch
                                    showAddSheet = false
                                    editingBatch = null

                                    if (targetEdit != null) {
                                        inventoryVm.updateAndMergeInventoryBatch(
                                            batch = targetEdit,
                                            newQuantity = qty,
                                            newUnit = sheetUnit.ifBlank { "pcs" },
                                            newLocation = sheetLocation.ifBlank { "Pantry" },
                                            newExpiredDate = sheetExpiryMillis
                                        ) { msg, success ->
                                            feedbackBanner = msg
                                            feedbackIsError = !success
                                        }
                                    } else {
                                        inventoryVm.addOrMergeInventoryBatch(
                                            ingredientId = ingredientId,
                                            quantity = qty,
                                            unit = sheetUnit.ifBlank { "pcs" },
                                            location = sheetLocation.ifBlank { "Pantry" },
                                            expiredDate = sheetExpiryMillis
                                        ) { msg, success ->
                                            feedbackBanner = msg
                                            feedbackIsError = !success
                                        }
                                    }
                                },
                                enabled = (sheetQty.toDoubleOrNull() ?: 0.0) > 0.0,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(
                                    if (isEditing) "Save Changes" else "Add to Pantry",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                        }
                    }

                }

                // Delete Batch Confirmation Dialog
                deleteDialogGroup?.let { group ->
                    val batch = group.representative
                    AlertDialog(
                        onDismissRequest = { deleteDialogGroup = null },
                        title = { Text("Delete batch?") },
                        text = {
                            Text("Remove ${batch.quantity ?: 0.0} ${batch.quantityUnit.orEmpty()} in ${batch.locationStorage ?: "Pantry"}?")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                group.memberIds.forEach { batchId ->
                                    inventoryVm.deleteInventoryItem(batchId)
                                }
                                deleteDialogGroup = null
                            }) { Text("Delete", color = scheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { deleteDialogGroup = null }) { Text("Cancel") }
                        }
                    )
                }

                // Deduct Stock Dialog
                useDialogBatch?.let { targetBatch ->
                    val maxAvail = targetBatch.quantity ?: 0.0
                    val unit = targetBatch.quantityUnit.orEmpty()
                    AlertDialog(
                        onDismissRequest = { useDialogBatch = null },
                        title = { Text("Use ${ingredient?.ingredientName}") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Available in this batch: $maxAvail $unit")
                                OutlinedTextField(
                                    value = useAmountInput,
                                    onValueChange = { useAmountInput = it },
                                    label = { Text("Amount to use ($unit)") },
                                    singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val amt = useAmountInput.toDoubleOrNull() ?: 0.0
                                useDialogBatch = null
                                inventoryVm.useInventoryAmount(targetBatch, amt) { msg, success ->
                                    feedbackBanner = msg
                                    feedbackIsError = !success
                                }
                            }) { Text("Confirm") }
                        },
                        dismissButton = {
                            TextButton(onClick = { useDialogBatch = null }) { Text("Cancel") }
                        }
                    )
                }

                // Share & Export Menu
                if (showExportMenu && ingredient != null) {
                    IngredientExportMenu(
                        ingredient = ingredient,
                        batches = deduplicatedBatches,
                        onDismiss = { showExportMenu = false }
                    )
                }
            }
        }


