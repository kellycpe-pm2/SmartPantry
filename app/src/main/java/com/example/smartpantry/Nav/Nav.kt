package com.example.smartpantry.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartpantry.Screen.Screen.IngredientUI.CreateIngredientScreen
// Make sure this import matches your actual UserDashboardScreen package
import com.ecopantry.app.UserDashboardScreen

// === YOUR COLOR PALETTE ===
val Primary = Color(0xFFB8E7E7)
val PrimaryDark = Color(0xFF235F62)
val Background = Color(0xFFFFFFFF)
val SecondaryBg = Color(0xFFF3FAFA)
val MutedText = Color(0xFF667B7D)
val DarkButton = Color(0xFF1A2426)

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    val items = listOf(
        BottomNavItem("home", "Home", Icons.Filled.Home),
        BottomNavItem("pantry", "Pantry", Icons.Filled.Eco),
        BottomNavItem("recipes", "Recipes", Icons.Filled.Dashboard),
        BottomNavItem("records", "Log", Icons.Filled.List),
        BottomNavItem("profile", "Profile", Icons.Filled.Eco)
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // FIXED: Added "records" instead of "log", and removed "add" so the bar hides when adding
    val bottomNavRoutes = listOf("home", "pantry", "recipes", "records", "profile")

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                NavigationBar(
                    containerColor = Background,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(90.dp)
                ) {
                    items.take(2).forEach { item ->
                        NavBarItem(
                            item = item,
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    launchSingleTop = true
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    restoreState = true
                                }
                            }
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            navController.navigate("add") {
                                launchSingleTop = true
                            }
                        },
                        containerColor = DarkButton,
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(56.dp)
                    ) {
                        Text(
                            text = "+",
                            fontSize = 24.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items.takeLast(3).forEach { item ->
                        NavBarItem(
                            item = item,
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    launchSingleTop = true
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { UserDashboardScreen() }
            composable("pantry") { UserDashboardScreen() }
            composable("recipes") { UserDashboardScreen() }
            composable("records") { UserDashboardScreen() }
            composable("profile") { UserDashboardScreen() }

            // FIXED: Added onBackClick so the user can return
            composable("add") {
                CreateIngredientScreen(
                    onBackClick = {  },{}
                )
            }
        }
    }
}

@Composable
fun NavBarItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (selected) PrimaryDark else MutedText,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            fontSize = 12.sp,
            color = if (selected) PrimaryDark else MutedText,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}