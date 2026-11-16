package com.example.smartpantry.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.Screen.Profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ---- Form values live here, so they survive rotation ----
    var loginEmail by mutableStateOf("")
    var loginPassword by mutableStateOf("")

    // Registration steps: 0 = email + password, 1 = enter the emailed code, 2 = your details
    var regStep by mutableIntStateOf(0)
    var regEmail by mutableStateOf("")
    var regPassword by mutableStateOf("")
    var regCode by mutableStateOf("")
    var regName by mutableStateOf("")
    var regPhone by mutableStateOf("")
    var regSize by mutableIntStateOf(1)
    var regDob by mutableStateOf("")
    var regSubmitted by mutableStateOf(false)   // show errors after the button was pressed
    var notice by mutableStateOf<String?>(null) // small info line, e.g. "A new code was sent."

    private class InactiveAccount : Exception()

    fun clearError() {
        _error.value = null
    }

    /** Start a fresh registration (clears everything typed before). */
    fun resetRegistration() {
        _error.value = null
        notice = null
        regStep = 0
        regEmail = ""; regPassword = ""; regCode = ""
        regName = ""; regPhone = ""; regSize = 1; regDob = ""
        regSubmitted = false
    }

    /** From the code step: go back and fix the email. */
    fun editEmail() {
        _error.value = null
        notice = null
        regCode = ""
        regStep = 0
    }

    // ---------- Login ----------
    fun login(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                ProfileRepository.signIn(loginEmail.trim(), loginPassword)
                if (!ProfileRepository.loadMyProfile().isActive) {
                    ProfileRepository.signOut()
                    throw InactiveAccount()
                }
            }.onSuccess {
                loginPassword = ""   // do not keep the password in memory
                onSuccess()
            }.onFailure {
                Log.e("AuthViewModel", "login failed", it)
                _error.value = loginMessage(it)
            }
            _isLoading.value = false
        }
    }

    // ---------- Registration step 1: create the account and email the code ----------
    fun sendCode() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            notice = null
            runCatching {
                val email = regEmail.trim().lowercase()
                if (ProfileRepository.emailExists(email)) error("already registered")
                ProfileRepository.signUp(email, regPassword)
            }
                .onSuccess { regStep = 1; regSubmitted = false }
                .onFailure {
                    Log.e("AuthViewModel", "signUp failed", it)
                    _error.value =
                        if (it.message?.contains("already registered", true) == true)
                            "This email is already registered."
                        else "Could not send the code. Please check your internet and try again."
                }
            _isLoading.value = false
        }
    }

    // ---------- Registration step 2: check the code ----------
    fun verifyCode() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                ProfileRepository.verifySignUpCode(regEmail.trim().lowercase(), regCode)
            }.onSuccess {
                regCode = ""
                regPassword = ""   // not needed any more
                regStep = 2        // email confirmed and signed in -> ask for the details
            }.onFailure {
                Log.e("AuthViewModel", "verify failed", it)
                _error.value = "Wrong or expired code. Please try again."
            }
            _isLoading.value = false
        }
    }

    fun resendCode() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            notice = null
            runCatching { ProfileRepository.resendSignUpCode(regEmail.trim().lowercase()) }
                .onSuccess { notice = "A new code was sent." }
                .onFailure {
                    Log.e("AuthViewModel", "resend failed", it)
                    _error.value = "Could not send the code. Please wait a moment and try again."
                }
            _isLoading.value = false
        }
    }

    // ---------- Registration step 3: save the details ----------
    fun saveDetails(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                ProfileRepository.completeProfile(
                    regName.trim(), regPhone, regSize, regDob.ifBlank { null }
                )
            }.onSuccess {
                resetRegistration()
                onSuccess()
            }.onFailure {
                Log.e("AuthViewModel", "saveDetails failed", it)
                _error.value = "Could not save your details. Please try again."
            }
            _isLoading.value = false
        }
    }

    private fun loginMessage(e: Throwable): String = when {
        e is InactiveAccount ->
            "Your account has been deactivated. Please contact the administrator."

        e.message?.contains("invalid login credentials", true) == true ->
            "Incorrect email or password"

        else -> "Could not log in. Check your internet connection and try again."
    }


    // ---- Password reset: 0 = email, 1 = code, 2 = new password, 3 = done ----
    var resetStep by mutableIntStateOf(0)
    var resetEmail by mutableStateOf("")
    var resetCode by mutableStateOf("")
    var resetPassword by mutableStateOf("")
    var resetConfirm by mutableStateOf("")

    /** knownEmail = from Account & Security (sends the code straight away). null = from Login. */
    fun startReset(knownEmail: String? = null) {
        _error.value = null; notice = null
        resetCode = ""; resetPassword = ""; resetConfirm = ""
        resetStep = 0
        resetEmail = knownEmail ?: loginEmail
        if (knownEmail != null) sendResetCode()
    }

    fun sendResetCode() {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null; notice = null
            runCatching { ProfileRepository.sendResetCode(resetEmail.trim().lowercase()) }
                .onSuccess {
                    if (resetStep == 1) notice = "A new code was sent."
                    resetStep = 1
                }
                .onFailure {
                    Log.e("AuthViewModel", "send reset code failed", it)
                    _error.value =
                        "Could not send the code. Please check your internet and try again."
                }
            _isLoading.value = false
        }
    }

    fun verifyResetCode() {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            runCatching {
                ProfileRepository.verifyResetCode(resetEmail.trim().lowercase(), resetCode)
                // a correct code signs the user in, so keep your deactivated-account rule
                if (!ProfileRepository.loadMyProfile().isActive) {
                    ProfileRepository.signOut()
                    throw InactiveAccount()
                }
            }.onSuccess {
                resetCode = ""; resetStep = 2
            }.onFailure {
                Log.e("AuthViewModel", "verify reset code failed", it)
                _error.value = if (it is InactiveAccount)
                    "Your account has been deactivated. Please contact the administrator."
                else "Wrong or expired code. Please try again."
            }
            _isLoading.value = false
        }
    }

    /** signOutAfter = true from Login (so they log in with the new password), false when already logged in. */
    fun saveNewPassword(signOutAfter: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            runCatching {
                ProfileRepository.changePassword(resetPassword)
                if (signOutAfter) ProfileRepository.signOut()
            }.onSuccess {
                resetPassword = ""; resetConfirm = ""; resetStep = 3
            }.onFailure {
                Log.e("AuthViewModel", "change password failed", it)
                _error.value =
                    if (it.message?.contains("different from the old", true) == true)
                        "New password must be different from your current one."
                    else "Could not change password. Please try again."
            }
            _isLoading.value = false
        }
    }
}