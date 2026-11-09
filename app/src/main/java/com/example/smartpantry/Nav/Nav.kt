package com.example.smartpantry.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartpantry.Screen.Screen.IngredientUI.CreateIngredientScreen
import com.example.smartpantry.Screen.Profile.AccountSecurityScreen
import com.example.smartpantry.Screen.Profile.ProfileScreen
import com.example.smartpantry.Screen.Profile.UserProfile
import com.example.smartpantry.Screen.Screen.IngredientUI.PantryScreen
import com.example.smartpantry.ui.home.UserDashboardScreen
import com.example.smartpantry.viewmodel.IngredientViewModel

data class BottomNavItem(
    val route: String,
    val label: String,
    val iconPath: String
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    val items = listOf(
        BottomNavItem(
            "home", "Home",
            "M3 10 L12 3 L21 10 M5 9 V21 H10 V15 H14 V21 H19 V9"
        ),
        BottomNavItem(
            "pantry", "Pantry",
            "M7 3 H17 V6 H7 Z M8 6 L5 10 V19 Q5 21 7 21 H17 Q19 21 19 19 V10 L16 6 M5 12 H19"
        ),
        BottomNavItem(
            "recipes", "Recipes",
            "M7 14 C2 14 2 7 7 7 C8 2 16 2 17 7 C22 7 22 14 17 14 V21 H7 Z M7 17 H17"
        ),
        BottomNavItem(
            "records", "Record",
            "M5 20 V14 M10 20 V10 M15 20 V6 M20 20 V3"
        ),
        BottomNavItem(
            "profile", "Profile",
            "M16 8 A4 4 0 1 1 8 8 A4 4 0 1 1 16 8 M4 21 C4 13 20 13 20 21"
        )
    )

    // temporary, will change it to real user in the future
    val profile = remember {
        UserProfile(
            displayName = "Maya Patel", email = "maya.patel@gmail.com",
            phoneNo = "0123456789",
            role = "user", memberSince = "September 2026",
            gender = "Female", dateOfBirth = "2000-05-21"
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomNavRoutes = listOf("home", "pantry", "recipes", "records", "profile")
    val ingredientVm: IngredientViewModel = viewModel()

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            if (entry == null) {
                navController.navigate("home") {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                FloatingBottomBar(
                    items = items,
                    currentRoute = currentRoute,
                    onTabSelected = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            restoreState = true
                        }
                    },
                    onAddClick = {
                        // prevent stacking multiple "add" screens
                        navController.navigate("add") {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home")    { UserDashboardScreen() }
            composable("pantry")  { PantryScreen(ingredientVm) }
            composable("recipes") { UserDashboardScreen() }
            composable("records") { UserDashboardScreen() }

            composable("profile") {
                ProfileScreen(
                    profile = profile,
                    onChangePassword = { /* TODO: admin change password screen */ },
                    onAccountSecurity = { navController.navigate("account_security") },
                    onMyFavourite = { navController.navigate("favourites") },
                    onShoppingList = { navController.navigate("shopping_list") },
                    onLogout = { /* TODO: sign out, then go to login */ },
                )
            }
            composable("account_security") {
                AccountSecurityScreen(
                    profile = profile,
                    onSave = { /* TODO: save to backend */ },
                    onChangeEmail = { newEmail -> /* TODO: Supabase */ },
                    onChangePassword = { /* TODO: change password screen */ },
                    onDeleteAccount = { /* TODO: delete account, then go to login */ },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("favourites")    { UserDashboardScreen() }
            composable("shopping_list") { UserDashboardScreen() }

            composable("add") {
                var popped by remember { mutableStateOf(false) }

                CreateIngredientScreen(
                    onBackClick = {
                        if (!popped) {
                            popped = true
                            if (navController.currentBackStackEntry?.destination?.route == "add") {
                                navController.popBackStack(route = "add", inclusive = true)
                            }
                        }
                    },ingredientVm
                )
            }
        }
    }
}

@Composable
private fun FloatingBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(26.dp),
        color = scheme.background,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val leftTabs  = items.take(2)
            val rightTabs = items.takeLast(3)

            leftTabs.forEach { item ->
                NavBarTab(
                    item = item,
                    selected = currentRoute == item.route,
                    onClick = { onTabSelected(item.route) },
                    modifier = Modifier.weight(1f)
                )
            }

            FloatingActionButton(
                onClick = onAddClick,
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(52.dp)
            ) {
                Text(
                    text = "+",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            rightTabs.forEach { item ->
                NavBarTab(
                    item = item,
                    selected = currentRoute == item.route,
                    onClick = { onTabSelected(item.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavBarTab(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (selected) scheme.primary else scheme.onSurfaceVariant

    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 30.dp)
                .background(
                    if (selected) scheme.primaryContainer else Color.Transparent,
                    RoundedCornerShape(15.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            OutlineIcon(item.iconPath, tint)
        }

        Text(
            text = item.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun OutlineIcon(iconPath: String, tint: Color) {
    val path = remember(iconPath) {
        PathParser().parsePathString(iconPath).toPath()
    }

    Canvas(modifier = Modifier.size(21.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = center.copy(x = 0f, y = 0f)) {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(
                    width = 1.7f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}