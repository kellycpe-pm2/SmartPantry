package com.example.smartpantry.Screen.Profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartpantry.model.User
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProfileScreen(
    profile: User,
    onChangePassword: () -> Unit,
    onAccountSecurity: () -> Unit,
    onMyFavourite: () -> Unit,
    onShoppingList: () -> Unit,
    onLogout: () -> Unit,
) {
    val isAdmin = profile.role == "admin"

    val menu = buildList {
        if (isAdmin) {
            add(MenuItem(Icons.Filled.Lock, "Change Password", onChangePassword))
        } else {
            add(MenuItem(Icons.Filled.Lock, "Account & Security", onAccountSecurity))
            add(MenuItem(Icons.Filled.Star, "My Favourite", onMyFavourite))
            add(MenuItem(Icons.Filled.ShoppingCart, "Shopping List", onShoppingList))
        }
        add(MenuItem(Icons.AutoMirrored.Filled.ExitToApp, "Log Out", onLogout))
    }

    // Surface sets the background AND the default text/icon colour from the theme,
    // so Text and Icon use BodyText instead of black, wherever this screen is shown.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("My Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            LazyColumn(Modifier.weight(1f)) {
                item { Header(profile) }
                item { DetailsCard(profile) }
                item { Spacer(Modifier.height(16.dp)) }
                items(menu, key = { it.title }) { MenuRow(it); HorizontalDivider() }
            }

            Text(
                "App version 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

// ---------- Private pieces(for UI stuff) ----------
private data class MenuItem(val icon: ImageVector, val title: String, val onClick: () -> Unit)

@Composable
private fun Header(profile: User) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                profile.displayName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer // PrimaryDark on Primary
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(profile.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Member since ${formatMemberSince(profile.memberSince)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailsCard(profile: User) {
    val isAdmin = profile.role == "admin"
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (isAdmin) "Admin details" else "Personal details", fontWeight = FontWeight.Bold)
            InfoRow("Phone", profile.phoneNo)
            if (isAdmin) {
                InfoRow("Role", "Admin")
            } else {
                InfoRow("Household size", profile.householdSize?.toString() ?: "Not set")
                InfoRow("Date of birth", profile.dateOfBirth ?: "Not set")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MenuRow(item: MenuItem) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = item.onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(item.title, Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMemberSince(raw: String): String =
    runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.take(10))!!
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(parsed)
    }.getOrDefault(raw)

/*
// ---------- Previews (uses the colors from theme) ----------
@Preview(showBackground = true, name = "User")
@Composable
private fun UserPreview() {
    SmartPantryTheme {
        ProfileScreen(
            profile = User(
                userCode = "SP01001",
                displayName = "Maya Patel", email = "maya.patel@gmail.com",
                phoneNo = "0123456789",
                role = "user", memberSince = "September 2026",
                gender = "Female", dateOfBirth = "2000-05-21"
            ),
            onChangePassword = {}, onAccountSecurity = {},
            onMyFavourite = {}, onShoppingList = {},
            onLogout = {}
        )
    }
}

@Preview(showBackground = true, name = "Admin")
@Composable
private fun AdminPreview() {
    SmartPantryTheme {
        ProfileScreen(
            profile = User(
                userCode = "SP01001",
                displayName = "Admin", email = "admin@gmail.com",
                phoneNo = "0198765432",
                role = "admin", memberSince = "September 2026"
            ),
            onChangePassword = {}, onAccountSecurity = {},
            onMyFavourite = {}, onShoppingList = {},
            onLogout = {}
        )
    }
}

 */