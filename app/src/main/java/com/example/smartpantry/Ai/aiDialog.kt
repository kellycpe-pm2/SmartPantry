package com.example.smartpantry.Ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smartpantry.viewmodel.CreateIngredientUiState

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = scheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = scheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            "SmartPantry AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            state.ingredientName.ifBlank { "New ingredient" },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // 2. Segmented Tab Switcher
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.aiTab != "chat",
                    onClick = { onSelectTab("suggestions") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text("Auto-fill") }
                )
                SegmentedButton(
                    selected = state.aiTab == "chat",
                    onClick = { onSelectTab("chat") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text("Ask AI") }
                )
            }

            // Error Banner
            state.aiError?.let { error ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = scheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            error,
                            color = scheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // 3. Body Content
            if (state.aiTab != "chat") {
                // ==========================================
                // TAB 1: AUTO-FILL SUGGESTIONS
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.aiLoading) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = scheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(strokeWidth = 3.dp)
                                Text(
                                    "Analyzing ingredient details…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Estimating shelf life, unit, and storage tips",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val suggestion = state.aiSuggestion
                        if (suggestion == null) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = scheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, scheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Ready to auto-fill",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Tap Generate below to automatically populate category, default measurement unit, estimated shelf life, and storage notes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            // Normalized Name Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = scheme.primaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        "SUGGESTED NAME",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.primary
                                    )
                                    Text(
                                        suggestion.normalizedName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 3-Pill Metric Row: Category | Unit | Expiry
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricChipCard(
                                    icon = Icons.Default.Category,
                                    label = "Category",
                                    value = suggestion.category,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricChipCard(
                                    icon = Icons.Default.Scale,
                                    label = "Unit",
                                    value = suggestion.unit,
                                    modifier = Modifier.weight(0.8f)
                                )
                                MetricChipCard(
                                    icon = Icons.Default.Schedule,
                                    label = "Shelf life",
                                    value = "${suggestion.minExpiryDays}–${suggestion.maxExpiryDays}d",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Description Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = scheme.surface,
                                border = BorderStroke(1.dp, scheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Description",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurfaceVariant
                                    )
                                    Text(
                                        suggestion.description,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            // Storage Advice Callout
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = scheme.secondaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Kitchen,
                                            contentDescription = null,
                                            tint = scheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Storage Advice",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = scheme.onSecondaryContainer
                                        )
                                    }
                                    Text(
                                        suggestion.storageAdvice,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSecondaryContainer
                                    )
                                }
                            }

                            Text(
                                "Expiry is an estimate, not a food-safety guarantee. Always check product labels and freshness.",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Bottom Action Buttons for Auto-fill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onGenerate,
                        enabled = !state.aiLoading,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.aiSuggestion == null) "Generate" else "Regenerate")
                    }

                    if (state.aiSuggestion != null) {
                        Button(
                            onClick = onApply,
                            enabled = !state.aiLoading,
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apply All", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // ==========================================
                // TAB 2: ASK AI CHAT
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.aiChatMessages.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = scheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Ask anything about ${state.ingredientName.ifBlank { "this ingredient" }}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val quickPrompts = listOf(
                                        "Best way to store it?",
                                        "Can I freeze this?",
                                        "Signs it went bad?"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        quickPrompts.forEach { prompt ->
                                            SuggestionChip(
                                                onClick = {
                                                    onChatInputChange(prompt)
                                                    onSendChat()
                                                },
                                                label = { Text(prompt, style = MaterialTheme.typography.labelMedium) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        state.aiChatMessages.forEach { message ->
                            val isUser = message.role == "user"
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (isUser) 18.dp else 4.dp,
                                        bottomEnd = if (isUser) 4.dp else 18.dp
                                    ),
                                    color = if (isUser) scheme.primary else scheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(0.86f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = if (isUser) "You" else "SmartPantry AI",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isUser) scheme.onPrimary.copy(alpha = 0.8f) else scheme.primary
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = message.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isUser) scheme.onPrimary else scheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (state.aiChatSending) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    "Gemini is thinking…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Rounded Chat Input Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.aiChatInput,
                            onValueChange = onChatInputChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask about storage, expiry, usage…") },
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(24.dp)
                        )
                        FilledIconButton(
                            onClick = onSendChat,
                            enabled = state.aiChatInput.isNotBlank() && !state.aiChatSending,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChipCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}