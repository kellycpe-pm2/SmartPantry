package com.example.smartpantry.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.Screen.Admin.AdminRepository
import com.example.smartpantry.Screen.Admin.AdminUserDetail
import com.example.smartpantry.Screen.Admin.AdminUserRow
import kotlinx.coroutines.launch

class AdminViewModel : ViewModel() {

    // State lives here so it survives rotation
    var query by mutableStateOf("")
    var filter by mutableStateOf("all")            // "all", "active" or "inactive"
    var users by mutableStateOf<List<AdminUserRow>>(emptyList())
    var total by mutableLongStateOf(0L)
    var hasMore by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selected by mutableStateOf<AdminUserDetail?>(null)
    private var page = 0

    fun search() {
        if (query.trim().length == 1) { error = "Type at least 2 characters"; return }
        page = 0
        load(reset = true)
    }

    fun changeFilter(value: String) {
        if (filter == value) return
        filter = value
        search()
    }

    fun reset() {
        query = ""; filter = "all"; users = emptyList(); total = 0
        hasMore = false; searched = false; selected = null; error = null
    }

    fun loadMore() {
        if (!hasMore || isLoading) return
        page++
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        viewModelScope.launch {
            isLoading = true
            error = null
            runCatching { AdminRepository.searchUsers(query.trim(), filter, page) }
                .onSuccess {
                    users = if (reset) it else users + it
                    total = it.firstOrNull()?.totalCount ?: if (reset) 0L else total
                    hasMore = it.size == AdminRepository.PAGE_SIZE
                    searched = true
                }
                .onFailure {
                    Log.e("AdminViewModel", "search failed", it)
                    if (!reset) page--
                    error = "Could not load users. Please try again."
                }
            isLoading = false
        }
    }

    fun open(row: AdminUserRow) {
        viewModelScope.launch {
            error = null
            runCatching { AdminRepository.getUser(row.userId) }
                .onSuccess { selected = it }
                .onFailure {
                    Log.e("AdminViewModel", "get user failed", it)
                    error = "Could not open this user."
                }
        }
    }

    fun close() { selected = null }

    /** Called after the admin confirmed in the dialog. */
    fun setActive(active: Boolean) {
        val user = selected ?: return
        viewModelScope.launch {
            runCatching { AdminRepository.setUserActive(user.userId, active) }
                .onSuccess {
                    selected = user.copy(isActive = active)       // new status shows immediately
                    users = users.map { if (it.userId == user.userId) it.copy(isActive = active) else it }
                }
                .onFailure {
                    Log.e("AdminViewModel", "set status failed", it)
                    error = "Could not change the status. Please try again."
                }
        }
    }
}
