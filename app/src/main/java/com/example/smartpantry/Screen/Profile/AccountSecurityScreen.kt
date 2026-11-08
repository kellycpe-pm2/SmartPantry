package com.example.smartpantry.Screen.Profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.smartpantry.ui.theme.SmartPantryTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private enum class Field { NAME, GENDER, DOB, PHONE, EMAIL }
private data class FieldRow(val label: String, val value: String, val field: Field?)

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Composable
fun AccountSecurityScreen(
    profile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onChangeEmail: (String) -> Unit,   // separate on purpose: email lives in Supabase Auth, not the profile table
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: () -> Unit
) {
    var current by remember(profile) { mutableStateOf(profile) }
    var editing by remember { mutableStateOf<Field?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun save(updated: UserProfile) {
        current = updated
        onSave(updated)
        editing = null
    }

    val profileRows = listOf(
        FieldRow("Name", current.displayName, Field.NAME),
        FieldRow("Gender", current.gender ?: "Not set", Field.GENDER),
        FieldRow("Date of birth", current.dateOfBirth ?: "Not set", Field.DOB),
    )

    val securityRows = listOf(
        FieldRow("Phone", current.phoneNo, Field.PHONE),
        FieldRow("Email", current.email, Field.EMAIL)
    )

    // Surface = theme background + theme text colour (light AND dark mode)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Account & Security", style = MaterialTheme.typography.headlineSmall)
            }

            LazyColumn(Modifier.weight(1f)) {
                item { SectionTitle("Profile") }
                items(profileRows, key = { it.label }) { row ->
                    SettingRow(row.label, row.value, clickable = row.field != null) { editing = row.field }
                    HorizontalDivider()
                }

                item { SectionTitle("Security", topPadding = 56.dp) }
                items(securityRows, key = { it.label }) { row ->
                    SettingRow(row.label, row.value, clickable = row.field != null) { editing = row.field }
                    HorizontalDivider()
                }
                item {
                    SettingRow("Change Password", onClick = onChangePassword)
                    HorizontalDivider()
                }
                item {
                    DeleteRow { showDeleteConfirm = true }
                    HorizontalDivider()
                }
            }
        }
    }

    when (editing) {
        Field.NAME -> TextFieldDialog(
            title = "Name",
            initial = current.displayName,
            validate = { if (it.trim().length !in 3..20) "Name must be 3 to 20 characters" else null },
            onDismiss = { editing = null },
            onConfirm = { save(current.copy(displayName = it.trim())) }
        )
        Field.PHONE -> TextFieldDialog(
            title = "Phone",
            initial = current.phoneNo,
            keyboardType = KeyboardType.Phone,
            validate = { if (!it.matches(Regex("\\d{9,11}"))) "Phone must be 9 to 11 digits" else null },
            onDismiss = { editing = null },
            onConfirm = { save(current.copy(phoneNo = it)) }
        )
        Field.EMAIL -> TextFieldDialog(
            title = "Email",
            initial = current.email,
            keyboardType = KeyboardType.Email,
            note = "We'll send a confirmation link to the new email.",
            validate = { if (!it.trim().matches(EMAIL_REGEX)) "Enter a valid email" else null },
            onDismiss = { editing = null },
            onConfirm = {
                val newEmail = it.trim().lowercase()
                if (newEmail != current.email.lowercase()) onChangeEmail(newEmail)
                editing = null
            }
        )
        Field.DOB -> TextFieldDialog(
            title = "Date of birth",
            initial = current.dateOfBirth ?: "",
            hint = "yyyy-MM-dd",
            validate = ::dobError,
            onDismiss = { editing = null },
            onConfirm = { save(current.copy(dateOfBirth = it.ifBlank { null })) }
        )
        Field.GENDER -> ChoiceDialog(
            title = "Gender",
            options = listOf("Female", "Male", "Prefer not to say"),
            selected = current.gender ?: "Prefer not to say",
            onDismiss = { editing = null },
            onConfirm = { save(current.copy(gender = it)) }
        )
        null -> Unit
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes your account and personal data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDeleteAccount() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

// ---------- Rows ----------
@Composable
private fun SectionTitle(text: String, topPadding: Dp = 24.dp) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = topPadding, bottom = 8.dp)
    )
}

@Composable
private fun SettingRow(
    label: String,
    value: String = "",
    clickable: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (clickable) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeleteRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(12.dp))
        Text("Delete Account", color = MaterialTheme.colorScheme.error)
    }
}

// ---------- Dialogs ----------
@Composable
private fun TextFieldDialog(
    title: String,
    initial: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    hint: String = "",
    note: String? = null,   // grey helper text shown while there is no error
    validate: (String) -> String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val error = validate(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                isError = error != null,
                supportingText = {
                    val message = error ?: note
                    if (message != null) Text(message)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = error == null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var choice by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = choice == option,
                                onClick = { choice = option },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = choice == option, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(option)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choice) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- Validation (birth date will prob change later to maybe calander)----------
private fun dobError(input: String): String? {
    if (input.isBlank()) return null // optional
    if (!input.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return "Use format yyyy-MM-dd"
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val date = runCatching { format.parse(input) }.getOrNull() ?: return "Not a valid date"
    return if (date.after(Date())) "Date cannot be in the future" else null
}

// ---------- Previews (YOUR theme, light + dark) ----------
@Preview(showBackground = true, name = "Light")
@Composable
private fun AccountSecurityLightPreview() {
    SmartPantryTheme(darkTheme = false) {
        AccountSecurityPreviewContent()
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun AccountSecurityDarkPreview() {
    SmartPantryTheme(darkTheme = true) {
        AccountSecurityPreviewContent()
    }
}

@Composable
private fun AccountSecurityPreviewContent() {
    AccountSecurityScreen(
        profile = UserProfile(
            displayName = "Maya Patel", email = "maya.patel@email.com",
            phoneNo = "0123456789",
            role = "user", memberSince = "September 2026",
            gender = "Female", dateOfBirth = "2000-05-21"
        ),
        onSave = {}, onChangeEmail = {}, onChangePassword = {}, onDeleteAccount = {}, onBack = {}
    )
}