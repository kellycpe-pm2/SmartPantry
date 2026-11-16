package com.example.smartpantry.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartpantry.R
import com.example.smartpantry.Screen.Auth.CreateAccountScreen
import com.example.smartpantry.Screen.Auth.LoginScreen
import com.example.smartpantry.Screen.Auth.ResetPasswordScreen
import com.example.smartpantry.Screen.IngredientUI.CreateIngredientScreen
import com.example.smartpantry.Screen.Profile.AccountSecurityScreen
import com.example.smartpantry.Screen.Profile.ProfileScreen
import com.example.smartpantry.viewmodel.ProfileViewModel
import com.example.smartpantry.Screen.IngredientUI.RecipeScreen
import com.example.smartpantry.Screen.InventoryUI.AddInventoryFlowScreen
import com.example.smartpantry.Screen.InventoryUI.InventoryScreen
import com.example.smartpantry.Screen.RecipeUI.CreateRecipeScreen
import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.ui.home.UserDashboardScreen
import com.example.smartpantry.viewmodel.AuthViewModel
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import com.example.smartpantry.viewmodel.IngredientViewModel
import com.example.smartpantry.viewmodel.recipe_vm
import com.example.smartpantry.ShoppingListScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

data class BottomNavItem(
    val route: String,
    val label: String,
    val iconPath: String
)

@Composable
fun AppNavHost(
    startRoute: String,
    navController: NavHostController = rememberNavController()
) {
    val items = listOf(
        BottomNavItem(
            "home", stringResource(R.string.nav_home),
            "M3 10 L12 3 L21 10 M5 9 V21 H10 V15 H14 V21 H19 V9"
        ),
        BottomNavItem(
            "pantry", stringResource(R.string.nav_pantry),
            "M7 3 H17 V6 H7 Z M8 6 L5 10 V19 Q5 21 7 21 H17 Q19 21 19 19 V10 L16 6 M5 12 H19"
        ),
        BottomNavItem(
            "recipes", stringResource(R.string.nav_recipes),
            "M7 14 C2 14 2 7 7 7 C8 2 16 2 17 7 C22 7 22 14 17 14 V21 H7 Z M7 17 H17"
        ),
        BottomNavItem(
            "records", stringResource(R.string.nav_log),
            "M5 20 V14 M10 20 V10 M15 20 V6 M20 20 V3"
        ),
        BottomNavItem(
            "profile", stringResource(R.string.nav_profile),
            "M16 8 A4 4 0 1 1 8 8 A4 4 0 1 1 16 8 M4 21 C4 13 20 13 20 21"
        )
    )

    // ViewModels
    val authViewModel: AuthViewModel = viewModel()

    val profileViewModel: ProfileViewModel = viewModel()
    val profile by profileViewModel.profile.collectAsState()


    val ingredientVm: IngredientViewModel = viewModel()
    val recipeVm: recipe_vm = viewModel()
    val ingredientUserVm: IngredientUserViewModel = viewModel()


    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomNavRoutes = listOf("home", "pantry", "recipes", "records", "profile")

    val sessionStatus by supabase.auth.sessionStatus.collectAsState()

    LaunchedEffect(sessionStatus) {
        when (sessionStatus) {
            is SessionStatus.Authenticated -> profileViewModel.refresh()
            is SessionStatus.NotAuthenticated -> {
                val route = navController.currentBackStackEntry?.destination?.route
                if (route !in listOf("login", "register", "forgot_password")) {
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                }
            }
            else -> {}
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
                            popUpTo("home") { saveState = true }
                            restoreState = true
                        }
                    },
                    onAddClick = {
                        // Navigate to create screen based on current tab
                        val routeToAdd = if (currentRoute == "recipes") "create_recipe" else "add"
                        navController.navigate(routeToAdd) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(padding)
        ) {
            composable("login") {
                LaunchedEffect(Unit) { authViewModel.clearError() }
                LoginScreen(
                    vm = authViewModel,
                    onLoggedIn = {
                        profileViewModel.refresh()
                        navController.navigate("home") { popUpTo("login") { inclusive = true } }
                    },
                    onCreateAccount = { authViewModel.resetRegistration(); navController.navigate("register") },
                    onForgotPassword = { navController.navigate("forgot_password") }
                )
            }
            composable("forgot_password") {
                LaunchedEffect(Unit) { authViewModel.startReset() }
                ResetPasswordScreen(
                    vm = authViewModel,
                    loggedIn = false,
                    onBack = { navController.popBackStack() },
                    onFinished = { navController.navigate("login") { popUpTo(0) { inclusive = true } } }
                )
            }

            composable("change_password") {
                val email = profile?.email
                LaunchedEffect(email) { if (email != null) authViewModel.startReset(knownEmail = email) }
                ResetPasswordScreen(
                    vm = authViewModel,
                    loggedIn = true,
                    onBack = { navController.popBackStack() },
                    onFinished = { navController.popBackStack() }
                )
            }
            composable("register") {
                LaunchedEffect(Unit) { authViewModel.clearError() }
                CreateAccountScreen(
                    vm = authViewModel,
                    onVerified = {
                        profileViewModel.refresh()
                        navController.navigate("home") { popUpTo(0) { inclusive = true } }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("home")    { UserDashboardScreen() }


            composable("pantry")  {
                InventoryScreen(onAddInventoryClick = {navController.navigate("add_ingredient")},ingredientUserVm, ingredientVm)
            }

            // --- RECIPES TAB ---


            composable("recipes") {
                RecipeScreen(recipeVm,onAddRecipeClick = {navController.navigate("create_recipe")})
            }

            composable("records") { UserDashboardScreen() }

            composable("profile") {
                val p = profile
                if (p == null) {
                    LoadingBox()
                } else {
                    ProfileScreen(
                        profile = p,
                        onChangePassword = { navController.navigate("change_password") },
                        onAccountSecurity = { navController.navigate("account_security") },
                        onMyFavourite = { navController.navigate("favourites") },
                        onShoppingList = { navController.navigate("shopping_list") },
                        onLogout = { profileViewModel.logout() }
                    )
                }
            }

            composable("account_security") {
                val p = profile
                if (p == null) {
                    LoadingBox()
                } else {
                    AccountSecurityScreen(
                        profile = p,
                        onSave = { profileViewModel.save(it) },
                        onChangeEmail = { newEmail -> profileViewModel.changeEmail(newEmail) },
                        onChangePassword = { navController.navigate("change_password") },
                        onDeleteAccount = { /* TODO */ },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("favourites")    { UserDashboardScreen() }
            composable("shopping_list") {
                ShoppingListScreen(onBack = { navController.popBackStack() })
            }

            // --- ADD INGREDIENT ---
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
                    },
                    vm = ingredientVm
                )
            }


            //--ADD INGREDIENT TO INVENTORY
            composable("add_ingredient"){

                var popped by remember { mutableStateOf(false) }

                AddInventoryFlowScreen({

                        if (!popped) {
                            popped = true
                            if (navController.currentBackStackEntry?.destination?.route == "add_ingredient") {
                                navController.popBackStack(route = "add_ingredient", inclusive = true)
                            }
                        }
                },ingredientUserVm)
            }

            // --- ADD RECIPE ---

            composable("create_recipe") {
                var popped by remember { mutableStateOf(false) }
                CreateRecipeScreen(
                    onBackClick = {
                        if (!popped) {
                            popped = true
                            if (navController.currentBackStackEntry?.destination?.route == "create_recipe") {
                                navController.popBackStack(route = "create_recipe", inclusive = true)
                            }
                        }
                    },
                     recipeVm,ingredientVm
                )
            }
        }
    }
}
@Composable
fun AppRoot() {
    val status by supabase.auth.sessionStatus.collectAsState()
    when (status) {
        is SessionStatus.Initializing -> LoadingBox()
        else -> {
            val start = remember { if (status is SessionStatus.Authenticated) "home" else "login" }
            AppNavHost(startRoute = start)
        }
    }
}
@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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