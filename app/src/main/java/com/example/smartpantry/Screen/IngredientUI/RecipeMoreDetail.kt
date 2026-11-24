package com.example.smartpantry.Screen.RecipeUI

import com.example.smartpantry.community.ReadableText
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.smartpantry.Screen.CommunityScreen.cleanDisplay
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.viewmodel.RecipeDetailIngredient
import com.example.smartpantry.viewmodel.recipe_vm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.platform.LocalLocale
import kotlin.text.isNotBlank


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    recipeVm: recipe_vm
) {
    val context = LocalContext.current
    val recipes by recipeVm.recipes.collectAsState()
    val recipe = recipes.firstOrNull { it.recipeId == recipeId }
    var ingredients by remember(recipeId) { mutableStateOf<List<RecipeDetailIngredient>>(emptyList()) }
    var loading by remember(recipeId) { mutableStateOf(true) }
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var exportMime by remember { mutableStateOf("application/pdf") }
    var exportName by remember { mutableStateOf("recipe.pdf") }
    var selectedForUse by remember { mutableStateOf<RecipeDetailIngredient?>(null) }
    var useAmount by remember { mutableStateOf("") }
    var showUseRecipeConfirm by remember { mutableStateOf(false) }
    var usingStock by remember { mutableStateOf(false) }
    var useMessage by remember { mutableStateOf<String?>(null) }
    var useMessageIsError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(recipeId) {
        loading = true
        ingredients = recipeVm.getRecipeDetailIngredients(recipeId)
        loading = false
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            com.example.smartpantry.community.SmartPantrySectionHeader(
                title ="Recipe details", showBack =true, onBack =onBackClick,
                action ={IconButton(onClick=onEditClick){Icon(Icons.Default.Edit,"Edit recipe",tint=scheme.primary)}}
            )
        }
    ) { padding ->
        if (recipe == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val availableCount = ingredients.count { it.isAvailable }
            val percent = if (ingredients.isEmpty()) 0 else (availableCount * 100 / ingredients.size)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    val imageModel: Any? = recipe.localRecipeImage?.takeIf { File(it).exists() }?.let(::File)
                        ?: recipe.recipeImage
                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = recipe.recipeName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(160.dp).background(scheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🍽️", fontSize = 54.sp)
                        }
                    }
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(recipe.recipeName, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            recipe.recipeCategory?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                            if (recipe.isVegan) AssistChip(onClick = {}, label = { Text("🌱 Vegan") })
                        }
                        recipe.description?.takeIf { it.isNotBlank() }?.let {
                            Text("Instructions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            ReadableText(it)
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Pantry availability", fontWeight = FontWeight.Bold)
                                Text("$percent%", color = scheme.primary, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = percent / 100f,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                                color = scheme.primary
                            )
                            Text("$availableCount of ${ingredients.size} ingredients available", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                            if (ingredients.isNotEmpty() && availableCount < ingredients.size) {
                                Text("${ingredients.size - availableCount} ingredient(s) missing or insufficient", color = scheme.error, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            } else if (ingredients.isNotEmpty()) {
                                Text("You have everything needed to cook this recipe.", color = Color(0xFF278653), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    Text("Ingredients", modifier = Modifier.padding(horizontal = 20.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (loading) {
                    item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (ingredients.isEmpty()) {
                    item { Text("No ingredients have been added to this recipe yet.", modifier = Modifier.padding(horizontal = 20.dp), color = scheme.onSurfaceVariant) }
                } else {
                    items(ingredients, key = { it.ingredientId }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = scheme.surface)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    if (item.isAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (item.isAvailable) Color(0xFF278653) else scheme.error
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(item.ingredientName, fontWeight = FontWeight.SemiBold)
                                    Text("Required: ${item.requiredQuantity} ${item.requiredUnit}".trim(), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                    Text("In pantry: ${item.availableQuantity} ${item.availableUnit}".trim(), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                    item.category?.let { Text(cleanDisplay(it), fontSize = 11.sp, color = scheme.onSurfaceVariant) }
                                    item.description?.takeIf { it.isNotBlank() }?.let { Text(cleanDisplay(it), fontSize = 11.sp, color = scheme.onSurfaceVariant) }
                                    item.expiryDate?.let {
                                        val date = SimpleDateFormat("dd MMM yyyy", LocalLocale.current.platformLocale).format(Date(it))
                                        Text("Nearest expiry: $date", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            selectedForUse = item
                                            useAmount = item.requiredQuantity.toString()
                                            useMessage = null
                                        },
                                        enabled = item.availableQuantity > 0.0,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Use this ingredient", fontSize = 12.sp)
                                    }
                                }
                                Surface(
                                    color = if (item.isAvailable) Color(0xFFE4F4EA) else scheme.errorContainer,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        if (item.isAvailable) "Available" else "Missing",
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        color = if (item.isAvailable) Color(0xFF216B43) else scheme.onErrorContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = { showUseRecipeConfirm = true },
                        enabled = !loading && ingredients.isNotEmpty() && ingredients.all { it.isAvailable },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text("Use this recipe – deduct all ingredients")
                    }
                    useMessage?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = if (useMessageIsError) scheme.error else Color(0xFF278653),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    selectedForUse?.let { selected ->
        AlertDialog(
            onDismissRequest = { if (!usingStock) selectedForUse = null },
            title = { Text("Use ${selected.ingredientName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Available: ${selected.availableQuantity} ${selected.availableUnit}")
                    Text("Recipe requirement: ${selected.requiredQuantity} ${selected.requiredUnit}")
                    OutlinedTextField(
                        value = useAmount,
                        onValueChange = { useAmount = it },
                        label = { Text("Amount to use (${selected.availableUnit})") },
                        singleLine = true,
                        enabled = !usingStock
                    )
                    useMessage?.let {
                        Text(cleanDisplay(it), color = if (useMessageIsError) scheme.error else Color(0xFF278653))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !usingStock && (useAmount.toDoubleOrNull()?.let { it > 0.0 } == true),
                    onClick = {
                        val amount = useAmount.toDoubleOrNull() ?: return@Button
                        usingStock = true
                        scope.launch {
                            val result = recipeVm.useIngredientStock(
                                selected.ingredientId,
                                amount,
                                selected.availableUnit
                            )
                            usingStock = false
                            result.onSuccess {
                                useMessage = it
                                useMessageIsError = false
                                selectedForUse = null
                                ingredients = recipeVm.getRecipeDetailIngredients(recipeId)
                            }.onFailure {
                                useMessage = it.message ?: "Unable to use this ingredient."
                                useMessageIsError = true
                            }
                        }
                    }
                ) { Text(if (usingStock) "Updating..." else "Confirm use") }
            },
            dismissButton = {
                TextButton(onClick = { selectedForUse = null }, enabled = !usingStock) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUseRecipeConfirm && recipe != null) {
        AlertDialog(
            onDismissRequest = { if (!usingStock) showUseRecipeConfirm = false },
            title = { Text("Use this recipe?") },
            text = {
                Text("SmartPantry will deduct the required quantity of every ingredient from your pantry. This action cannot be automatically undone.")
            },
            confirmButton = {
                Button(
                    enabled = !usingStock,
                    onClick = {
                        usingStock = true
                        scope.launch {
                            val result = recipeVm.useWholeRecipe(recipeId)
                            usingStock = false
                            showUseRecipeConfirm = false
                            result.onSuccess {
                                useMessage = it
                                useMessageIsError = false
                                ingredients = recipeVm.getRecipeDetailIngredients(recipeId)
                            }.onFailure {
                                useMessage = it.message ?: "Unable to use this recipe."
                                useMessageIsError = true
                            }
                        }
                    }
                ) { Text(if (usingStock) "Updating..." else "Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showUseRecipeConfirm = false }, enabled = !usingStock) {
                    Text("Cancel")
                }
            }
        )
    }
}