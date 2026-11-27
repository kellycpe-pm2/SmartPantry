package com.example.smartpantry.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecipeAiSheet(
    name: String, onName: (String) -> Unit,
    servings: String, onServings: (String) -> Unit,
    dietary: String, onDietary: (String) -> Unit,
    preparing: Boolean, error: String?, preview: RecipeDraft?,
    onDismiss: () -> Unit, onCancel: () -> Unit, onGenerate: () -> Unit, onApply: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.surface
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.size(40.dp), shape = CircleShape, color = colors.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = colors.primary)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("SmartPantry AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Recipe auto-fill", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }
            OutlinedTextField(name, { onName(it.replace('\n', ' ').replace('\r', ' ').take(150)) },
                Modifier.fillMaxWidth(), label = { Text("Dish name") }, singleLine = true, enabled = !preparing)
            OutlinedTextField(servings, { onServings(it.filter(Char::isDigit).take(2)) },
                Modifier.fillMaxWidth(), label = { Text("Servings (1–12)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !preparing)
            UnitDropdown("Dietary preference", dietary, listOf("Unspecified", "Halal", "Vegetarian", "Vegan"), !preparing, onDietary)
            error?.let {
                Surface(shape = RoundedCornerShape(14.dp), color = colors.errorContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = colors.onErrorContainer)
                        Text(it, color = colors.onErrorContainer)
                    }
                }
            }
            if (preparing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Generating your draft…", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            } else {
                if (preview != null) {
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(preview.title, fontWeight = FontWeight.Bold)
                            Text("${preview.ingredients.size} matched · ${preview.unresolved.size} to match · ${preview.steps.size} steps")
                            if (preview.description.isNotBlank()) Text(preview.description, maxLines = 3)
                            if (preview.unresolved.isNotEmpty()) {
                                Text("Match after applying: " + preview.unresolved.take(5).joinToString { it.name }, maxLines = 2)
                            }
                        }
                    }
                    Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Apply draft") }
                }
                OutlinedButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank() && servings.toIntOrNull() in 1..12) {
                    Text(if (preview == null) "Generate AI draft" else "Generate again")
                }
            }
        }
    }
}
