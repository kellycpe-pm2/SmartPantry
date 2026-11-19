package com.example.smartpantry.Screen.IngredientUI
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.ui.theme.ImageInput
import com.example.smartpantry.ui.theme.OutlineDropdownField
import com.example.smartpantry.ui.theme.OutlineTextField
import com.example.smartpantry.viewmodel.recipe_vm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeScreen(
    recipeId: Long,
    onBackClick: () -> Unit,
    vm: recipe_vm = viewModel()
) {
    val recipes by vm.recipes.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val original = recipes.find { it.recipeId == recipeId }

    var name by remember(recipeId) { mutableStateOf(original?.recipeName ?: "") }
    var category by remember(recipeId) { mutableStateOf(original?.recipeCategory ?: "") }
    var description by remember(recipeId) { mutableStateOf(original?.description ?: "") }
    var isVegan by remember(recipeId) { mutableStateOf(original?.isVegan ?: false) }
    var imagePath by remember(recipeId) {
        mutableStateOf(original?.localRecipeImage ?: original?.recipeImage)
    }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) imagePath = uri.toString()
    }

    val categoryOptions = listOf("Breakfast", "Lunch", "Dinner", "Dessert", "Snack", "Other")

    if (original == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Recipe", fontWeight = FontWeight.Bold, color = scheme.primary) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = scheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = scheme.background,
                        titleContentColor = scheme.primary
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = scheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading recipe...", color = scheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Edit Recipe", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = scheme.primary)
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
            Spacer(Modifier.height(8.dp))

            ImageInput(
                imagePath = imagePath,
                onClick = { picker.launch("image/*") }
            )

            Spacer(Modifier.height(24.dp))

            OutlineTextField(
                value = name,
                onValueChange = { name = it; errorMsg = null },
                label = "Recipe Name",
                image = Icons.Default.Restaurant
            )
            Spacer(Modifier.height(16.dp))

            OutlineDropdownField(
                value = category,
                onValueChange = { category = it },
                options = categoryOptions,
                label = "Recipe Category",
                image = Icons.Default.Category
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Vegan", color = scheme.onBackground, fontWeight = FontWeight.Medium)
                Switch(checked = isVegan, onCheckedChange = { isVegan = it })
            }
            Spacer(Modifier.height(16.dp))

            OutlineTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description",
                image = Icons.Default.Description,
                singleLine = false,
                minLines = 5,
                maxLines = 5
            )

            errorMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = scheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMsg = "Name cannot be empty"
                        return@Button
                    }
                    isSaving = true

                    val updated = original.copy(
                        recipeName = name.trim(),
                        recipeCategory = category.trim().takeIf { it.isNotEmpty() },
                        description = description.trim().takeIf { it.isNotEmpty() },
                        isVegan = isVegan,
                        localRecipeImage = imagePath
                    )
                    vm.updateRecipe(original.recipeId!!, updated)
                    onBackClick()
                },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = scheme.onPrimary
                    )
                } else {
                    Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}