package com.example.smartpantry.Screen.Profile
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "start")
            runCatching { ProfileRepository.signIn("maya.test@example.com", "123456") }
                .onSuccess { Log.d("ProfileViewModel", "signIn OK") }
                .onFailure { Log.e("ProfileViewModel", "signIn failed", it) }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "loading profile")
            runCatching { ProfileRepository.loadMyProfile() }
                .onSuccess { Log.d("ProfileViewModel", "loaded ${it.email}"); _profile.value = it }
                .onFailure { Log.e("ProfileViewModel", "Could not load profile", it) }
        }
    }

    fun save(updated: UserProfile) {
        viewModelScope.launch {
            runCatching { ProfileRepository.updateMyProfile(updated) }
                .onSuccess { _profile.value = updated }
                .onFailure {
                    _error.value = it.message ?: "Failed to save"
                    refresh()
                }
        }
    }

    fun changeEmail(newEmail: String) {
        viewModelScope.launch {
            runCatching { ProfileRepository.changeEmail(newEmail) }
                .onFailure { _error.value = it.message ?: "Failed to change email" }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { ProfileRepository.signOut() }
            _profile.value = null
            _error.value = null
        }
    }
}
