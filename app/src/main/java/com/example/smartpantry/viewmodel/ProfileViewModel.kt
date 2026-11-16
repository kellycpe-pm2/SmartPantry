package com.example.smartpantry.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.Screen.Profile.ProfileRepository
import com.example.smartpantry.model.User
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val _profile = MutableStateFlow<User?>(null)
    val profile: StateFlow<User?> = _profile

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "loading profile")
            runCatching { ProfileRepository.loadMyProfile() }
                .onSuccess { Log.d("ProfileViewModel", "loaded ${it.email}"); _profile.value = it }
                .onFailure {
                    Log.e("ProfileViewModel", "Could not load profile", it)
                    _error.value = "Could not load your profile."
                }//offline and online
        }
    }

    fun save(updated: User) {
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
                .onFailure { runCatching { supabase.auth.clearSession() } }  // wipe the local session anyway
            _profile.value = null
            _error.value = null
        }
    }
}