package com.example.smartpantry.Screen.Admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartpantry.ui.theme.SmartPantryTheme
import com.example.smartpantry.viewmodel.AdminViewModel

@Composable
fun AdminScreen(vm: AdminViewModel,
                onLogout: () -> Unit
) {
    val listState = rememberLazyListState()

    // First time the admin opens this screen: load the list
    LaunchedEffect(Unit) { if (!vm.searched) vm.search() }

    // Load the next 20 when the admin scrolls near the end
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= vm.users.size - 3
        }
    }
    LaunchedEffect(nearEnd, vm.users.size) { if (nearEnd && vm.users.isNotEmpty()) vm.loadMore() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Column {
            Text("ADMIN WORKSPACE", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Users", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            value = vm.query, onValueChange = { vm.query = it },
            placeholder = { Text("Search users") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All", "active" to "Active", "inactive" to "Inactive").forEach { (value, label) ->
                FilterChip(
                    selected = vm.filter == value,
                    onClick = { vm.changeFilter(value) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(50)
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Community members", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${vm.total} users", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        vm.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (vm.searched && vm.users.isEmpty() && !vm.isLoading) Text("No users found.")

        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            items(vm.users, key = { it.userId }) { row -> UserCard(row) { vm.open(row) } }
            if (vm.isLoading) item {
                Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    vm.selected?.let { UserDetailDialog(it, onClose = vm::close, onSetActive = vm::setActive) }
}

@Composable
private fun UserCard(row: AdminUserRow, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(row.displayName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(row.email, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusChip(row.isActive)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initials = name.trim().split(" ").filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercase() }
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun StatusChip(active: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            if (active) "Active" else "Inactive",
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

// Temporary dialog. It becomes a full screen in your profile layout in the next step.
@Composable
private fun UserDetailDialog(
    user: AdminUserDetail,
    onClose: () -> Unit,
    onSetActive: (Boolean) -> Unit
) {
    var confirm by remember { mutableStateOf(false) }
    val newStatus = !user.isActive
    val verb = if (user.isActive) "Deactivate" else "Reactivate"

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(user.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("User code: ${user.userCode}")
                Text("Email: ${user.email}")
                Text("Household size: ${user.householdSize ?: "-"}")
                Text("Date of birth: ${user.dateOfBirth ?: "-"}")
                Text("Member since: ${user.createdAt?.take(10) ?: "-"}")
                Text("Status: ${if (user.isActive) "Active" else "Inactive"}")
            }
        },
        confirmButton = { TextButton(onClick = { confirm = true }) { Text(verb) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } }
    )

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("$verb this user?") },
            text = { Text("${user.displayName}'s data is kept.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onSetActive(newStatus) }) { Text(verb) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

