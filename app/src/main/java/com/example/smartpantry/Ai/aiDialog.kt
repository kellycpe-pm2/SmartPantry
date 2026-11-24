package com.example.smartpantry.Ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartpantry.viewmodel.CreateIngredientUiState

@Composable
fun IngredientAiAssistantDialog(
    state: CreateIngredientUiState,
    onDismiss: () -> Unit,
    onSelectTab: (String) -> Unit,
    onGenerate: () -> Unit,
    onApply: () -> Unit,
    onChatInputChange: (String) -> Unit,
    onSendChat: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = scheme.primary)
                Text("SmartPantry AI", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = if (state.aiTab == "chat") 1 else 0) {
                    Tab(
                        selected = state.aiTab == "suggestions",
                        onClick = { onSelectTab("suggestions") },
                        text = { Text("Auto-fill") }
                    )
                    Tab(
                        selected = state.aiTab == "chat",
                        onClick = { onSelectTab("chat") },
                        text = { Text("Ask AI") }
                    )
                }
                Spacer(Modifier.height(12.dp))

                state.aiError?.let { error ->
                    Text(error, color = scheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }

                if (state.aiTab == "suggestions") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Ingredient: ${state.ingredientName.ifBlank { "Not entered" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.aiLoading) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Text("  Asking Gemini for suggestions…")
                            }
                        } else {
                            val suggestion = state.aiSuggestion
                            if (suggestion == null) {
                                Text("Gemini can suggest the ingredient category, default unit, description, expiry range and storage advice.")
                            } else {
                                SuggestionLine("Ingredient name", suggestion.normalizedName)
                                SuggestionLine("Category", suggestion.category)
                                SuggestionLine("Suggested unit", suggestion.unit)
                                SuggestionLine("Estimated expiry", "${suggestion.minExpiryDays}–${suggestion.maxExpiryDays} days")
                                SuggestionLine("Description", suggestion.description)
                                HorizontalDivider()
                                Text("Storage advice", fontWeight = FontWeight.Bold, color = scheme.primary)
                                Text(suggestion.storageAdvice, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Expiry is an estimate, not a food-safety guarantee. Check the product label and actual condition.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.aiChatMessages.isEmpty()) {
                                Text("Ask about ingredient storage, expiry, freshness or how to use it.", style = MaterialTheme.typography.bodySmall)
                            }
                            state.aiChatMessages.forEach { message ->
                                val isUser = message.role == "user"
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = if (isUser) scheme.primaryContainer else scheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Text(if (isUser) "You" else "SmartPantry AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(3.dp))
                                    Text(message.text, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (state.aiChatSending) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("  Gemini is replying…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.aiChatInput,
                            onValueChange = onChatInputChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ask about this ingredient…") },
                            minLines = 1,
                            maxLines = 3,
                            trailingIcon = {
                                TextButton(onClick = onSendChat, enabled = state.aiChatInput.isNotBlank() && !state.aiChatSending) {
                                    Icon(Icons.Default.Send, contentDescription = "Send")
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.aiTab == "suggestions" && state.aiSuggestion != null) {
                Button(onClick = onApply, enabled = !state.aiLoading) { Text("Apply suggestions") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (state.aiTab == "suggestions") {
                OutlinedButton(onClick = onGenerate, enabled = !state.aiLoading) { Text("Generate again") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun SuggestionLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
