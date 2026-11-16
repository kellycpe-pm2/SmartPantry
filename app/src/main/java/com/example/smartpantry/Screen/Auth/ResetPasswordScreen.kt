package com.example.smartpantry.Screen.Auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.smartpantry.viewmodel.AuthViewModel
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun ResetPasswordScreen(
    vm: AuthViewModel,
    loggedIn: Boolean,            // true = opened from Account & Security
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    var show by remember { mutableStateOf(false) }

    val pwProblem = when {
        vm.resetPassword.length < 8 -> "At least 8 characters"
        vm.resetPassword != vm.resetConfirm -> "Passwords don't match"
        else -> null
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (vm.resetStep) {
                0 -> {
                    Text(if (loggedIn) "Change password" else "Forgot password",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Enter your email and we'll send you a code.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = vm.resetEmail, onValueChange = { vm.resetEmail = it },
                        label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = vm::sendResetCode,
                        enabled = vm.resetEmail.isNotBlank() && !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Send code") }
                }

                1 -> {
                    Text("Enter the code", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("We sent a code to ${vm.resetEmail}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = vm.resetCode,
                        onValueChange = { v -> vm.resetCode = v.filter { it.isDigit() }.take(10) },
                        label = { Text("Code") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = vm::verifyResetCode,
                        enabled = vm.resetCode.length >= 6 && !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Verify") }
                    TextButton(onClick = vm::sendResetCode, enabled = !isLoading) { Text("Resend code") }
                    if (!loggedIn) TextButton(onClick = { vm.resetStep = 0 }) { Text("Change email") }
                }

                2 -> {
                    Text("New password", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    val visual = if (show) VisualTransformation.None else PasswordVisualTransformation()
                    OutlinedTextField(
                        value = vm.resetPassword, onValueChange = { vm.resetPassword = it },
                        label = { Text("New password") }, singleLine = true,
                        visualTransformation = visual,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = vm.resetConfirm, onValueChange = { vm.resetConfirm = it },
                        label = { Text("Confirm new password") }, singleLine = true,
                        visualTransformation = visual,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
                    if (vm.resetPassword.isNotEmpty() && vm.resetConfirm.isNotEmpty()) {
                        pwProblem?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { vm.saveNewPassword(signOutAfter = !loggedIn) },
                        enabled = pwProblem == null && !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save password") }
                }

                else -> {
                    Text("Password changed", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(if (loggedIn) "Your password has been updated."
                    else "Please log in with your new password.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loggedIn) "Done" else "Back to login")
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            vm.notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            if (isLoading) CircularProgressIndicator(Modifier.padding(top = 16.dp))
            if (vm.resetStep != 3) TextButton(onClick = onBack) { Text("Cancel") }
        }
    }
}