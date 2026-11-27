package com.example.smartpantry.Screen.InventoryUI

import android.R.attr.scheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.smartpantry.ui.home.ContextAddButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.IngredientUser
import com.example.smartpantry.Screen.IngredientUI.IngredientExportMenu
import com.example.smartpantry.community.RecipePhoto
import com.example.smartpantry.community.SmartPantrySectionHeader
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import com.example.smartpantry.viewmodel.IngredientViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

fun formatShelfLife(minDays: Short?, maxDays: Short?): String? {
    val min = minDays?.toInt()?.takeIf { it > 0 }
    val max = maxDays?.toInt()?.takeIf { it > 0 }
    return when {
        min != null && max != null && min == max -> "$min day${if (min == 1) "" else "s"}"
        min != null && max != null -> "$min–$max days"
        min != null -> "At least $min day${if (min == 1) "" else "s"}"
        max != null -> "Up to $max day${if (max == 1) "" else "s"}"
        else -> null
    }
}

private data class GroupedPantryItem(
    val ingredientId: Long?,
    val ingredient: Ingredient?,
    val batches: List<IngredientUser>,
    val totalQuantity: Double,
    val primaryUnit: String,
    val locations: List<String>,
    val earliestExpiry: Long
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onAddInventoryClick: () -> Unit,
    onCreateIngredientClick: () -> Unit,
    onEditIngredientClick: (Long) -> Unit,
    onIngredientClick: (Long) -> Unit,
    onEditClick: (Long) -> Unit,
    vm: IngredientUserViewModel = viewModel(),
    ingredientVm: IngredientViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    val inventory by vm.inventory.collectAsState()
    val allIngredients by ingredientVm.ingredients.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val currentLocale = LocalLocale.current.platformLocale

    val currentUserId = remember { supabase.auth.currentUserOrNull()?.id }
    val ingredientMap = remember(allIngredients) {
        allIngredients.associateBy { it.ingredientId ?: 0L }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    // Long-press action sheet & export targets
    var longPressedPantryGroup by remember { mutableStateOf<GroupedPantryItem?>(null) }
    var longPressedCreatedItem by remember { mutableStateOf<Ingredient?>(null) }
    var exportTarget by remember { mutableStateOf<Pair<Ingredient, List<IngredientUser>>?>(null) }

    // Swipe-to-delete / action confirmation targets
    var deleteConfirmGroup by remember { mutableStateOf<GroupedPantryItem?>(null) }
    var deleteConfirmCreated by remember { mutableStateOf<Ingredient?>(null) }

    val now = System.currentTimeMillis()
    val threeDaysMs = TimeUnit.DAYS.toMillis(3)

    val pantryFilters = listOf("All", "Fridge", "Freezer", "Pantry", "Expiring Soon")
    val createdFilters = listOf("All", "Published", "Private")

    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val onRefresh: () -> Unit = {
        coroutineScope.launch {
            isRefreshing = true
            try {
                vm.refreshData()
                vm.refreshInventory(force = true)
                ingredientVm.refreshIngredients(force = true)
                delay(500)
            } finally {
                isRefreshing = false
            }
        }
    }
    val groupedPantry = remember(inventory, ingredientMap, searchQuery, selectedFilter) {
        inventory
            .filter { (it.quantity ?: 0.0) > 0.0 && (it.ingredientId ?: 0L) > 0L }
            .groupBy { it.ingredientId }
            .map { (ingId, batches) ->
                val sortedBatches = batches.sortedBy {
                    if (it.expiredDate > 0L) it.expiredDate else Long.MAX_VALUE
                }
                GroupedPantryItem(
                    ingredientId = ingId,
                    ingredient = ingredientMap[ingId ?: 0L],
                    batches = sortedBatches,
                    totalQuantity = sortedBatches.sumOf { it.quantity ?: 0.0 },
                    primaryUnit = sortedBatches.firstOrNull()?.quantityUnit.orEmpty(),
                    locations = sortedBatches.mapNotNull { it.locationStorage?.takeIf(String::isNotBlank) }.distinct(),
                    earliestExpiry = sortedBatches.map { it.expiredDate }.filter { it > 0L }.minOrNull() ?: 0L
                )
            }
            .filter { group ->
                val name = group.ingredient?.ingredientName.orEmpty()
                val matchesSearch = searchQuery.isBlank() ||
                        name.contains(searchQuery, ignoreCase = true) ||
                        group.locations.any { it.contains(searchQuery, ignoreCase = true) }

                val matchesFilter = when (selectedFilter) {
                    "All" -> true
                    "Expiring Soon" -> group.batches.any { it.expiredDate in 1L..(now + threeDaysMs) }
                    else -> group.locations.any { it.equals(selectedFilter, ignoreCase = true) }
                }
                matchesSearch && matchesFilter
            }
            .sortedBy { if (it.earliestExpiry > 0L) it.earliestExpiry else Long.MAX_VALUE }
    }

    val myCreatedIngredients = remember(allIngredients, currentUserId, searchQuery, selectedFilter) {
        allIngredients
            .filter { !currentUserId.isNullOrBlank() && it.userId == currentUserId }
            .filter { ing ->
                val matchesSearch = searchQuery.isBlank() ||
                        ing.ingredientName.contains(searchQuery, ignoreCase = true) ||
                        (ing.category ?: "").contains(searchQuery, ignoreCase = true)

                val matchesFilter = when (selectedFilter) {
                    "Published" -> ing.isPublish == true
                    "Private" -> ing.isPublish != true
                    else -> true
                }
                matchesSearch && matchesFilter
            }
    }

    Scaffold(
        topBar = {
            SmartPantrySectionHeader(
                title = "My Pantry",
                showBack = onBack != null,
                onBack = onBack,
                action = {
                    IconButton(onClick = {
                        vm.refreshData()
                        vm.refreshInventory(force = true)
                        ingredientVm.refreshIngredients(force = true)
                    }) {
                        Icon(Icons.Default.Refresh, "Sync pantry", tint = scheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            ContextAddButton(Modifier.padding(bottom=92.dp),onIngredient=onCreateIngredientClick,onPantry=onAddInventoryClick)
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(scheme.background)
                .padding(innerPadding)
        ) {
            if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = scheme.background
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; selectedFilter = "All" },
                    text = { Text("Pantry Stock (${groupedPantry.size})", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; selectedFilter = "All" },
                    text = { Text("Created by Me (${myCreatedIngredients.size})", maxLines = 1) }
                )
            }


                PullToRefreshBox(
                    isRefreshing = isRefreshing || isLoading
                    ,
                    onRefresh =onRefresh
                    ,
                    modifier = Modifier
                        .fillMaxSize()
                ) {

                    LazyColumn(
                        contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 154.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it.take(80) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        if (selectedTab == 0) "Search pantry items or location"
                                        else "Search ingredients I created",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = if (searchQuery.isNotBlank()) ({
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            "Clear"
                                        )
                                    }
                                }) else null,
                                shape = RoundedCornerShape(16.dp)
                            )

                        }

                        // Horizontal Filter Row
                        item {
                            val activeFilters =
                                if (selectedTab == 0) pantryFilters else createdFilters
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                activeFilters.forEach { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = { Text(filter, maxLines = 1) }
                                    )
                                }
                            }
                        }



                        if (selectedTab == 0) {
                            // ==========================================
                            // TAB 0: PANTRY STOCK (Tap -> Detail, Long Press -> Actions, Swipe -> Delete)
                            // ==========================================
                            if (groupedPantry.isEmpty()) {
                                item {
                                    EmptyPantryCard(
                                        title = "No pantry items found",
                                        subtitle = "Tap + to add ingredients to your Fridge, Freezer, or Pantry.",
                                        buttonLabel = "Add Stock",
                                        onAction = onAddInventoryClick
                                    )
                                }
                            } else {
                                items(
                                    groupedPantry,
                                    key = { it.ingredientId ?: it.hashCode().toLong() }) { group ->
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { dismissValue ->
                                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                                deleteConfirmGroup = group
                                            }
                                            false
                                        }
                                    )

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = false,
                                        backgroundContent = {
                                            val bgColor by animateColorAsState(
                                                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                                    scheme.error
                                                } else {
                                                    scheme.errorContainer
                                                },
                                                label = "swipe_bg_pantry"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .background(bgColor)
                                                    .padding(horizontal = 20.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    ) {
                                        val ing = group.ingredient
                                        val imgUrl =
                                            ing?.localImagePath?.takeIf { File(it).exists() }
                                                ?: ing?.image.orEmpty()

                                        val daysLeft = if (group.earliestExpiry > 0L) {
                                            TimeUnit.MILLISECONDS.toDays(group.earliestExpiry - now)
                                        } else null

                                        val expiryColor = when {
                                            daysLeft == null -> scheme.onSurfaceVariant
                                            daysLeft < 0 -> scheme.error
                                            daysLeft <= 3 -> Color(0xFFB45309)
                                            else -> Color(0xFF216B43)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = scheme.surface,
                                            border = BorderStroke(1.dp, scheme.outlineVariant),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(18.dp))
                                                .combinedClickable(
                                                    onClick = {
                                                        group.ingredientId?.let(
                                                            onIngredientClick
                                                        )
                                                    },
                                                    onLongClick = {
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        longPressedPantryGroup = group
                                                    }
                                                )
                                        ) {
                                            Row(
                                                Modifier.padding(14.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RecipePhoto(
                                                    url = imgUrl,
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                )

                                                Column(
                                                    Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Text(
                                                        text = ing?.ingredientName
                                                            ?: "Ingredient #${group.ingredientId}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            6.dp
                                                        ),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = scheme.primaryContainer
                                                        ) {
                                                            Text(
                                                                "${group.totalQuantity} ${group.primaryUnit}",
                                                                Modifier.padding(
                                                                    horizontal = 8.dp,
                                                                    vertical = 2.dp
                                                                ),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = scheme.onPrimaryContainer,
                                                                maxLines = 1
                                                            )
                                                        }
                                                        if (group.batches.size > 1) {
                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = scheme.secondaryContainer
                                                            ) {
                                                                Text(
                                                                    "${group.batches.size} batches",
                                                                    Modifier.padding(
                                                                        horizontal = 8.dp,
                                                                        vertical = 2.dp
                                                                    ),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = scheme.onSecondaryContainer,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }
                                                        group.locations.firstOrNull()?.let { loc ->
                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = scheme.surfaceVariant
                                                            ) {
                                                                Text(
                                                                    if (group.locations.size > 1) "$loc +${group.locations.size - 1}" else loc,
                                                                    Modifier.padding(
                                                                        horizontal = 8.dp,
                                                                        vertical = 2.dp
                                                                    ),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = scheme.onSurfaceVariant,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }
                                                    }

                                                    if (group.earliestExpiry > 0L) {
                                                        val formatted = SimpleDateFormat(
                                                            "dd MMM yyyy",
                                                            currentLocale
                                                        )
                                                            .format(Date(group.earliestExpiry))
                                                        val prefix =
                                                            if (group.batches.size > 1) "Earliest: " else "Expires "
                                                        val label = when {
                                                            daysLeft != null && daysLeft < 0 -> "Expired ($formatted)"
                                                            daysLeft == 0L -> "Expires today"
                                                            daysLeft != null && daysLeft <= 3 -> "Expires in $daysLeft day(s)"
                                                            else -> "$prefix$formatted"
                                                        }
                                                        Text(
                                                            label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Medium,
                                                            color = expiryColor,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Icon(
                                                    Icons.Default.ChevronRight,
                                                    contentDescription = "View details",
                                                    tint = scheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // ==========================================
                            // TAB 1: CREATED BY ME (Tap -> Detail, Long Press -> Actions, Swipe -> Delete)
                            // ==========================================
                            if (myCreatedIngredients.isEmpty()) {
                                item {
                                    EmptyPantryCard(
                                        title = "No custom ingredients created yet",
                                        subtitle = "Tap + to create your own ingredients with AI storage tips and shelf-life estimates.",
                                        buttonLabel = "Create Ingredient",
                                        onAction = onCreateIngredientClick
                                    )
                                }
                            } else {
                                items(
                                    myCreatedIngredients,
                                    key = { it.ingredientId ?: it.hashCode().toLong() }) { ing ->
                                    val id = ing.ingredientId ?: 0L
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { dismissValue ->
                                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                                deleteConfirmCreated = ing
                                            }
                                            false
                                        }
                                    )

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = false,
                                        backgroundContent = {
                                            val bgColor by animateColorAsState(
                                                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                                    scheme.error
                                                } else {
                                                    scheme.errorContainer
                                                },
                                                label = "swipe_bg_created"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .background(bgColor)
                                                    .padding(horizontal = 20.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    ) {
                                        val imgUrl =
                                            ing.localImagePath?.takeIf { File(it).exists() }
                                                ?: ing.image.orEmpty()
                                        val shelfLifeText = formatShelfLife(
                                            ing.minExpiredDuration,
                                            ing.maxExpiredDuration
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = scheme.surface,
                                            border = BorderStroke(1.dp, scheme.outlineVariant),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(18.dp))
                                                .combinedClickable(
                                                    onClick = { if (id > 0) onIngredientClick(id) },
                                                    onLongClick = {
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        longPressedCreatedItem = ing
                                                    }
                                                )
                                        ) {
                                            Row(
                                                Modifier.padding(14.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RecipePhoto(
                                                    url = imgUrl,
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                )

                                                Column(
                                                    Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Text(
                                                        text = ing.ingredientName,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            6.dp
                                                        ),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (ing.isPublish == true) Color(
                                                                0xFFE4F4EA
                                                            ) else scheme.surfaceVariant
                                                        ) {
                                                            Text(
                                                                text = if (ing.isPublish == true) "Published" else "Private",
                                                                Modifier.padding(
                                                                    horizontal = 8.dp,
                                                                    vertical = 2.dp
                                                                ),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                color = if (ing.isPublish == true) Color(
                                                                    0xFF216B43
                                                                ) else scheme.onSurfaceVariant
                                                            )
                                                        }

                                                        ing.category?.takeIf { it.isNotBlank() }
                                                            ?.let { cat ->
                                                                Surface(
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    color = scheme.primaryContainer
                                                                ) {
                                                                    Text(
                                                                        text = cat,
                                                                        Modifier.padding(
                                                                            horizontal = 8.dp,
                                                                            vertical = 2.dp
                                                                        ),
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis,
                                                                        color = scheme.onPrimaryContainer
                                                                    )
                                                                }
                                                            }
                                                    }

                                                    if (shelfLifeText != null) {
                                                        Text(
                                                            text = "Typical shelf life: $shelfLifeText",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = scheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Icon(
                                                    Icons.Default.ChevronRight,
                                                    contentDescription = "View details",
                                                    tint = scheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    // ==========================================
    // LONG-PRESS ACTION SHEET: PANTRY STOCK ITEM
    // ==========================================
    longPressedPantryGroup?.let { group ->
        ModalBottomSheet(
            onDismissRequest = { longPressedPantryGroup = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    group.ingredient?.ingredientName ?: "Ingredient #${group.ingredientId}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${group.totalQuantity} ${group.primaryUnit} • ${group.batches.size} batch(es)",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                // 1. View Details & Manage Batches
                OutlinedButton(
                    onClick = {
                        longPressedPantryGroup = null
                        group.ingredientId?.let(onIngredientClick)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View Details & Manage Batches")
                }

                // 2. Share & Export
                Button(
                    onClick = {
                        val ing = group.ingredient ?: Ingredient(
                            ingredientId = group.ingredientId,
                            ingredientName = "Ingredient #${group.ingredientId}"
                        )
                        longPressedPantryGroup = null
                        exportTarget = ing to group.batches
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share & Export")
                }

                // 3. Remove All Stock
                OutlinedButton(
                    onClick = {
                        longPressedPantryGroup = null
                        deleteConfirmGroup = group
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove All Stock")
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ==========================================
    // LONG-PRESS ACTION SHEET: CREATED INGREDIENT
    // ==========================================
    longPressedCreatedItem?.let { ing ->
        val id = ing.ingredientId ?: 0L
        ModalBottomSheet(
            onDismissRequest = { longPressedCreatedItem = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    ing.ingredientName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    ing.category ?: "Custom Ingredient",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                // 1. View Details & Batches
                OutlinedButton(
                    onClick = {
                        longPressedCreatedItem = null
                        if (id > 0) onIngredientClick(id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View Details & Batches")
                }

                FilledTonalButton(
                    onClick = {
                        ingredientVm.togglePublish(id,ing)
                        longPressedCreatedItem = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = if (ing.isPublish == true) Icons.Default.PublicOff else Icons.Default.Public,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (ing.isPublish == true) "Make Private (Unpublish)" else "Publish to Community")
                }

                // 2. Edit & Share Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            longPressedCreatedItem = null
                            if (id > 0) onEditIngredientClick(id)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }

                    Button(
                        onClick = {
                            val matchingBatches = inventory.filter {
                                it.ingredientId == id && (it.quantity ?: 0.0) > 0.0
                            }
                            longPressedCreatedItem = null
                            exportTarget = ing to matchingBatches
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }

                // 3. Delete Ingredient
                OutlinedButton(
                    onClick = {
                        longPressedCreatedItem = null
                        deleteConfirmCreated = ing
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Ingredient")
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Share & Export Menu (Image, Text, PDF, TXT)
    exportTarget?.let { (ing, itemBatches) ->
        IngredientExportMenu(
            ingredient = ing,
            batches = itemBatches,
            onDismiss = { exportTarget = null }
        )
    }

    // Confirm Remove All Batches of Pantry Item
    deleteConfirmGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteConfirmGroup = null },
            title = { Text("Remove from pantry?") },
            text = { Text("Remove all ${group.batches.size} batch(es) of \"${group.ingredient?.ingredientName ?: "this item"}\" from your pantry?") },
            confirmButton = {
                TextButton(onClick = {
                    group.batches.forEach { batch ->
                        batch.id?.let { vm.deleteInventoryItem(it) }
                    }
                    deleteConfirmGroup = null
                }) { Text("Remove", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmGroup = null }) { Text("Cancel") }
            }
        )
    }

    // Confirm Delete Created Ingredient
    deleteConfirmCreated?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmCreated = null },
            title = { Text("Delete created ingredient?") },
            text = { Text("Delete \"${target.ingredientName}\" permanently?") },
            confirmButton = {
                TextButton(onClick = {
                    target.ingredientId?.let { ingredientVm.deleteIngredient(it) }
                    deleteConfirmCreated = null
                }) { Text("Delete", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmCreated = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyPantryCard(
    title: String,
    subtitle: String,
    buttonLabel: String,
    onAction: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceVariant,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Kitchen,
                null,
                tint = scheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(12.dp)) {
                Text(buttonLabel)
            }
        }
    }

}
