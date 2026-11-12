package com.example.smartpantry.Screen.IngredientUI

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.viewmodel.recipe_vm
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(
    vm: recipe_vm,
    onAddRecipeClick: () -> Unit
) {
    val items by vm.recipes.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Recipes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                },
                actions = {
                    IconButton(onClick = { vm.refreshRecipes(force = true) }) {
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
                onClick = onAddRecipeClick,
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary
            ) {
                Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(items, key = { it.recipeId ?: 0L }) { item ->
                        RecipeRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeRow(item: Recipe) {
    val scheme = MaterialTheme.colorScheme

    // Smart Image Logic (Local first, then Remote)
    val localFile: String? = item.localRecipeImage
    val remoteUrl: String? = item.recipeImage
    val imageToLoad = if (localFile != null && localFile.isNotEmpty() && File(localFile).exists()) {
        File(localFile)
    } else {
        remoteUrl
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