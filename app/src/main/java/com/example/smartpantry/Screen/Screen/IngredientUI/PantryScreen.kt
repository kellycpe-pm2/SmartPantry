package com.example.smartpantry.Screen.Screen.IngredientUI

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import com.example.smartpantry.model.Ingredient
import com.example.smartpantry.viewmodel.IngredientViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(vm: IngredientViewModel) {
    val items by vm.ingredients.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Pantry", fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, color = scheme.primary)
                },
                actions = {
                    IconButton(onClick = { vm.refreshIngredients() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = scheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.primary,
                    actionIconContentColor = scheme.primary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Your pantry is empty",
                        fontWeight = FontWeight.Bold, color = scheme.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap + to add an ingredient",
                        fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(items, key = { it.ingredientId ?: 0L }) { item ->
                        PantryRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun PantryRow(item: Ingredient) {
    val scheme = MaterialTheme.colorScheme

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
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!item.image.isNullOrBlank()) {
                AsyncImage(
                    model = item.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = item.ingredientName.firstOrNull()?.uppercase() ?: "?",
                    color = scheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(item.ingredientName,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground)
            val subtitle = buildString {
                if (!item.category.isNullOrBlank()) append(item.category).append(" · ")
                if (item.weight != null) append(item.weight).append(item.unit ?: "")
            }.trim().trimEnd('·').trim()

            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }

        if (item.maxExpiredDuration != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("${item.maxExpiredDuration}d",
                    fontSize = 11.sp, color = scheme.primary,
                    fontWeight = FontWeight.Medium)
            }
        }
    }
}