package com.example.smartpantry.Screen.RecipeUI

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.community.RecipePhoto
import com.example.smartpantry.community.SmartPantrySectionHeader
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.viewmodel.recipe_vm
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RecipeScreen(
    onCreateClick: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onEditClick: (Long) -> Unit,
    recipeVm: recipe_vm = viewModel(),
    onBackClick: () -> Unit
) {
    val recipes by recipeVm.recipes.collectAsState()
    val isSyncing by recipeVm.isSyncing.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var publishingId by remember { mutableStateOf<Long?>(null) }
    var publishConfirmRecipe by remember { mutableStateOf<Recipe?>(null) }
    var deleteConfirmRecipe by remember { mutableStateOf<Recipe?>(null) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerIsError by remember { mutableStateOf(false) }

    val categories = remember(recipes) {
        listOf("All") + recipes.mapNotNull { it.recipeCategory?.takeIf(String::isNotBlank) }.distinct()
    }

    val filteredRecipes = remember(recipes, searchQuery, selectedCategory) {
        recipes.filter { r ->
            val matchesSearch = searchQuery.isBlank() ||
                    r.recipeName.contains(searchQuery, ignoreCase = true) ||
                    (r.description ?: "").contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategory == "All" ||
                    r.recipeCategory.equals(selectedCategory, ignoreCase = true)
            matchesSearch && matchesCat
        }
    }

    Scaffold(
        topBar = {
            SmartPantrySectionHeader(
                title = "My recipes",
                showBack = false,
                onBack = onBackClick,
                action = {
                    IconButton(onClick = { recipeVm.refreshRecipes() }, enabled = !isSyncing) {
                        Icon(Icons.Default.Refresh, "Sync recipes", tint = scheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClick,
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, "Create recipe")
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(scheme.background)
                .padding(innerPadding)
        ) {
            if (isSyncing) LinearProgressIndicator(Modifier.fillMaxWidth())

            LazyColumn(
                contentPadding = PaddingValues(18.dp, 10.dp, 18.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                bannerMessage?.let { msg ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (bannerIsError) scheme.errorContainer else scheme.primaryContainer
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    msg,
                                    Modifier.weight(1f),
                                    color = if (bannerIsError) scheme.onErrorContainer else scheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                IconButton(onClick = { bannerMessage = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, "Dismiss", Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search my recipes") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (searchQuery.isNotBlank()) ({
                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Clear") }
                        }) else null,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                if (categories.size > 1) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }
                    }
                }

                if (filteredRecipes.isEmpty()) {
                    item {
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
                                Icon(Icons.Default.MenuBook, null, tint = scheme.primary, modifier = Modifier.size(36.dp))
                                Text("No personal recipes yet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Tap + to create a recipe, then publish it to Community anytime.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredRecipes, key = { it.recipeId ?: 0L }) { recipe ->
                        val rId = recipe.recipeId ?: 0L
                        val isPublishing = publishingId == rId
                        val imgUrl = recipe.localRecipeImage?.takeIf { File(it).exists() }
                            ?: recipe.recipeImage.orEmpty()

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = scheme.surface,
                            border = BorderStroke(1.dp, scheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (rId > 0) onRecipeClick(rId) }
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RecipePhoto(
                                        url = imgUrl,
                                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            recipe.recipeName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            recipe.recipeCategory?.takeIf { it.isNotBlank() }?.let {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = scheme.primaryContainer
                                                ) {
                                                    Text(
                                                        it,
                                                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = scheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                            if (recipe.isVegan) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFE4F4EA)
                                                ) {
                                                    Text(
                                                        "🌱 Vegan",
                                                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF216B43)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "${recipe.cookingSteps.size} step(s)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = scheme.onSurfaceVariant
                                        )
                                    }
                                }

                                HorizontalDivider(color = scheme.outlineVariant)

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = { publishConfirmRecipe = recipe },
                                        enabled = !isPublishing && rId > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        if (isPublishing) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Public, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Publish to Community", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    Row {
                                        IconButton(onClick = { if (rId > 0) onEditClick(rId) }) {
                                            Icon(Icons.Default.Edit, "Edit recipe", tint = scheme.primary)
                                        }
                                        IconButton(onClick = { deleteConfirmRecipe = recipe }) {
                                            Icon(Icons.Default.DeleteOutline, "Delete recipe", tint = scheme.error)
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

    publishConfirmRecipe?.let { target ->
        AlertDialog(
            onDismissRequest = { publishConfirmRecipe = null },
            icon = { Icon(Icons.Default.Public, null) },
            title = { Text("Publish to Community?") },
            text = {
                Text("Share \"${target.recipeName}\" with the SmartPantry community so other users can view and cook it.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val rId = target.recipeId ?: return@TextButton
                    publishConfirmRecipe = null
                    publishingId = rId
                    scope.launch {
                        val res = recipeVm.publishPersonalRecipe(rId)
                        publishingId = null
                        res.onSuccess {
                            bannerMessage = it
                            bannerIsError = false
                        }.onFailure {
                            bannerMessage = it.message ?: "Failed to publish recipe."
                            bannerIsError = true
                        }
                    }
                }) { Text("Publish") }
            },
            dismissButton = {
                TextButton(onClick = { publishConfirmRecipe = null }) { Text("Cancel") }
            }
        )
    }

    deleteConfirmRecipe?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmRecipe = null },
            title = { Text("Delete recipe?") },
            text = { Text("Delete \"${target.recipeName}\" permanently from your personal recipes?") },
            confirmButton = {
                TextButton(onClick = {
                    target.recipeId?.let { recipeVm.deleteRecipeById(it) }
                    deleteConfirmRecipe = null
                }) { Text("Delete", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmRecipe = null }) { Text("Cancel") }
            }
        )
    }
}