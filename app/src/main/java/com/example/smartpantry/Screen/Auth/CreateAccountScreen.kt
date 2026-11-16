package com.example.smartpantry.Screen.Auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.smartpantry.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Composable
fun CreateAccountScreen(
    vm: AuthViewModel,
    onVerified: () -> Unit,   // called when the whole registration is finished
    onBack: () -> Unit
) {
    // Steps: 0 = email + password, 1 = enter the emailed code, 2 = your details.
    // Everything typed lives in the ViewModel (vm.reg...), so it survives rotation.
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
    val step = vm.regStep

    val emailError = if (!vm.regEmail.trim().matches(EMAIL_REGEX)) "Enter a valid email" else null
    val passwordError = if (vm.regPassword.length < 8) "Password must be at least 8 characters" else null
    val nameError = if (vm.regName.trim().length !in 3..20) "Name must be 3 to 20 characters" else null
    val phoneError = if (!vm.regPhone.matches(Regex("\\d{9,11}"))) "Phone must be 9 to 11 digits" else null
    val dobError = dobError(vm.regDob)

    // Show an error only after the user typed something or pressed the button
    fun shown(error: String?, text: String) = if (vm.regSubmitted || text.isNotEmpty()) error else null

    // Back button: code step -> email step. On the details step the email is already
    // confirmed, so going back is blocked.
    BackHandler(enabled = step >= 1) { if (step == 1) vm.editEmail() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step < 2) {
                    IconButton(onClick = { if (step == 1) vm.editEmail() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Spacer(Modifier.width(16.dp))
                }
                Text("Create account", style = MaterialTheme.typography.headlineSmall)
            }

            when (step) {
                // ---------- Step 1: email + password ----------
                0 -> {
                    Text("Step 1 of 3: your login", style = MaterialTheme.typography.labelMedium)
                    FormField("Email", vm.regEmail, { vm.regEmail = it },
                        shown(emailError, vm.regEmail), KeyboardType.Email)
                    FormField("Password", vm.regPassword, { vm.regPassword = it },
                        shown(passwordError, vm.regPassword), KeyboardType.Password,
                        isPassword = true, showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword })
                    error?.let { ErrorText(it) }
                    Button(
                        onClick = {
                            vm.regSubmitted = true
                            if (emailError == null && passwordError == null) vm.sendCode()
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { ButtonLabel(isLoading, "Send code") }
                }

                // ---------- Step 2: enter the code that was emailed ----------
                1 -> {
                    Text("Step 2 of 3: confirm your email", style = MaterialTheme.typography.labelMedium)
                    Text("We sent a code to ${vm.regEmail.trim()}. Enter it below.")
                    FormField("Verification code", vm.regCode,
                        { vm.regCode = it.filter(Char::isDigit).take(10) }, null, KeyboardType.Number)
                    vm.notice?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { ErrorText(it) }
                    Button(
                        onClick = { vm.verifyCode() },
                        enabled = !isLoading && vm.regCode.length >= 6,
                        modifier = Modifier.fillMaxWidth()
                    ) { ButtonLabel(isLoading, "Verify") }
                    TextButton(onClick = { vm.resendCode() }, enabled = !isLoading) { Text("Resend code") }
                    TextButton(onClick = { vm.editEmail() }, enabled = !isLoading) { Text("Wrong email? Go back") }
                }

                // ---------- Step 3: your details ----------
                else -> {
                    Text("Step 3 of 3: about you", style = MaterialTheme.typography.labelMedium)
                    FormField("Display name", vm.regName, { vm.regName = it }, shown(nameError, vm.regName))
                    FormField("Phone number", vm.regPhone, { vm.regPhone = it },
                        shown(phoneError, vm.regPhone), KeyboardType.Phone)

                    Text("Household size", style = MaterialTheme.typography.titleSmall)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(onClick = { vm.regSize-- }, enabled = vm.regSize > 1) { Text("−") }
                        Text("${vm.regSize} people", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { vm.regSize++ }, enabled = vm.regSize < 20) { Text("+") }
                    }

                    FormField("Date of birth, yyyy-MM-dd (optional)", vm.regDob, { vm.regDob = it },
                        shown(dobError, vm.regDob))

                    error?.let { ErrorText(it) }

                    Button(
                        onClick = {
                            vm.regSubmitted = true
                            if (nameError == null && phoneError == null && dobError == null) {
                                vm.saveDetails(onVerified)
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { ButtonLabel(isLoading, "Save") }
                }
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ButtonLabel(isLoading: Boolean, text: String) {
    if (isLoading) CircularProgressIndicator(
        Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary
    ) else Text(text)
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: () -> Unit = {}
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation()
        else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            { TextButton(onClick = onTogglePassword) { Text(if (showPassword) "Hide" else "Show") } }
        } else null,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth()
    )
}

// Date of birth is optional: blank is fine, otherwise a real date, not in the future
private fun dobError(input: String): String? {
    if (input.isBlank()) return null
    if (!input.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return "Use format yyyy-MM-dd"
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val date = runCatching { format.parse(input) }.getOrNull() ?: return "Not a valid date"
    return if (date.after(Date())) "Date cannot be in the future" else null
}
