package com.example.smartpantry.Screen.IngredientUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.viewmodel.RecommendedRecipeResult
import com.example.smartpantry.viewmodel.recipe_vm
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(
    vm: recipe_vm,
    onAddRecipeClick: () -> Unit,
    onRecipeClick: (Long) -> Unit
) {
    val items by vm.recipes.collectAsState()
    val recommendedItems by vm.recommendedRecipes.collectAsState()
    val scheme = MaterialTheme.colorScheme
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            vm.loadRecommendedRecipes()
        }
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text("Recipes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                    },
                    actions = {
                        IconButton(onClick = {
                            vm.refreshRecipes(force = true)
                            if (selectedTab == 1) vm.loadRecommendedRecipes()
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
                    selectedTabIndex = selectedTab,
                    containerColor = scheme.background,
                    contentColor = scheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("All Recipes") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            vm.loadRecommendedRecipes()
                        },
                        text = { Text("Recommended") }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRecipeClick,
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary
            ) {
                Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedTab == 0) {
                if (items.isEmpty()) {
                    EmptyRecipesView(scheme)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(items, key = { it.recipeId ?: 0L }) { item ->
                            RecipeRow(
                                item = item,
                                onClick = { item.recipeId?.let(onRecipeClick) },
                                onDelete = { item.recipeId?.let(vm::deleteRecipe) }
                            )
                        }
                    }
                }
            } else {
                if (recommendedItems.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = scheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No recommendations available", fontWeight = FontWeight.Bold, color = scheme.onBackground)
                        Spacer(Modifier.height(4.dp))
                        Text("Add ingredients with expiry dates and recipes first", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(recommendedItems, key = { it.recipe.recipeId ?: 0L }) { rec ->
                            RecommendedRecipeRow(
                                item = rec,
                                onClick = { rec.recipe.recipeId?.let(onRecipeClick) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRecipesView(scheme: ColorScheme) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = scheme.primary.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text("No recipes yet", fontWeight = FontWeight.Bold, color = scheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Text("Tap + to create your first recipe", fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecommendedRecipeRow(
    item: RecommendedRecipeResult,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val recipe = item.recipe

    val localFile: String? = recipe.localRecipeImage
    val remoteUrl: String? = recipe.recipeImage
    val imageToLoad = if (!localFile.isNullOrEmpty() && File(localFile).exists()) {
        File(localFile)
    } else {
        remoteUrl
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
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
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = scheme.primary)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                recipe.recipeName,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground
            )
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (item.displayPercentage > 50) scheme.primaryContainer else scheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Expiry Priority: ${item.displayPercentage}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.displayPercentage > 50) scheme.onPrimaryContainer else scheme.onSecondaryContainer
                    )
                }

                Surface(
                    color = if (item.missingIngredients.isEmpty()) scheme.tertiaryContainer else scheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (item.missingIngredients.isEmpty()) "Ready to cook!" else "Missing ${item.missingIngredients.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.missingIngredients.isEmpty()) scheme.onTertiaryContainer else scheme.onErrorContainer
                    )
                }
            }

            if (item.missingIngredients.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Need to buy: ${item.missingIngredients.joinToString(", ")}",
                    fontSize = 12.sp,
                    color = scheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "✓ You have all ingredients in pantry!",
                    fontSize = 12.sp,
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeRow(
    item: Recipe,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    val localFile: String? = item.localRecipeImage
    val remoteUrl: String? = item.recipeImage
    val imageToLoad = if (!localFile.isNullOrEmpty() && File(localFile).exists()) {
        File(localFile)
    } else {
        remoteUrl
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, "Delete", tint = scheme.onErrorContainer)
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = scheme.primary)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.recipeName,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onBackground
                )
                val subtitle = buildString {
                    if (!item.recipeCategory.isNullOrBlank()) append(item.recipeCategory)
                    if (item.isVegan) {
                        if (isNotEmpty()) append(" • ")
                        append("Vegan 🌱")
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
