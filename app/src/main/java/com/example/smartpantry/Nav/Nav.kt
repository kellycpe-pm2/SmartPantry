package com.example.smartpantry.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.smartpantry.notifications.NotificationRepository
import com.example.smartpantry.notifications.NotificationBadge
import com.example.smartpantry.notifications.NotificationScreen
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.smartpantry.community.CommunityScreen
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.smartpantry.R
import com.example.smartpantry.Screen.Admin.AdminScreen
import com.example.smartpantry.Screen.Auth.CreateAccountScreen
import com.example.smartpantry.Screen.Auth.LoginScreen
import com.example.smartpantry.Screen.Auth.ResetPasswordScreen
import com.example.smartpantry.Screen.IngredientUI.CreateIngredientScreen
import com.example.smartpantry.Screen.IngredientUI.EditIngredientScreen
import com.example.smartpantry.Screen.IngredientUI.EditInventoryScreen
import com.example.smartpantry.Screen.IngredientUI.IngredientDetailScreen
import com.example.smartpantry.Screen.Profile.AccountSecurityScreen
import com.example.smartpantry.Screen.Profile.ProfileScreen
import com.example.smartpantry.viewmodel.ProfileViewModel
import com.example.smartpantry.Screen.InventoryUI.AddInventoryFlowScreen
import com.example.smartpantry.Screen.InventoryUI.InventoryScreen
import com.example.smartpantry.Screen.RecipeUI.CreateRecipeScreen
import com.example.smartpantry.Screen.RecipeUI.EditRecipeScreen
import com.example.smartpantry.Screen.RecipeUI.RecipeDetailScreen
import com.example.smartpantry.Screen.RecipeUI.RecipeScreen
import com.example.smartpantry.Screen.ShoppingListScreen
import com.example.smartpantry.Screen.SplashScreen
import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.ui.home.UserDashboardScreen
import com.example.smartpantry.ui.home.WasteLogScreen
import com.example.smartpantry.viewmodel.AdminViewModel
import com.example.smartpantry.viewmodel.AuthViewModel
import com.example.smartpantry.viewmodel.IngredientUserViewModel
import com.example.smartpantry.viewmodel.IngredientViewModel
import com.example.smartpantry.viewmodel.recipe_vm
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
    navController: NavHostController = rememberNavController(),
    externalLink: String = "",
    onLinkHandled: () -> Unit = {}
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

    val goHome: () -> Unit = {
        val returned = navController.popBackStack("home", false, true)
        if (!returned) navController.navigate("home") {
            launchSingleTop = true
            popUpTo(navController.graph.id)
        }
    }

    // ViewModels
    val authViewModel: AuthViewModel = viewModel()
    val adminVm: AdminViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val profile by profileViewModel.profile.collectAsState()

    val ingredientVm: IngredientViewModel = viewModel()
    val recipeVm: recipe_vm = viewModel()
    val ingredientUserVm: IngredientUserViewModel = viewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var communityRoot by rememberSaveable { mutableStateOf(true) }
    val bottomNavRoutes = listOf("home", "pantry", "recipes", "records?week={week}", "profile")

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

    val appContext=LocalContext.current
    val owner=supabase.auth.currentUserOrNull()?.id
    LaunchedEffect(owner,currentRoute){
        NotificationBadge.count.value=0
        if(owner!=null){try{NotificationRepository(appContext).call()}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){}}
    }
    LaunchedEffect(externalLink,sessionStatus){
        if(externalLink.isNotBlank() && sessionStatus is SessionStatus.Authenticated){
            val uri=android.net.Uri.parse(externalLink)
            when(uri.host){
                "recipe"->uri.lastPathSegment?.toLongOrNull()?.takeIf{it>0}?.let{navController.navigate("community_post/$it")}
                "notifications"->navController.navigate("notifications")
                "batch"->{val parts=uri.pathSegments;val ing=parts.getOrNull(0)?.toLongOrNull();val batch=parts.getOrNull(1)?.toLongOrNull();if(ing!=null&&batch!=null)navController.navigate("ingredient_detail/$ing?batchId=$batch")}
            }
            onLinkHandled()
        }
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(140)) }
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

            composable("home") {
                if (profile?.role == "admin") {
                    LaunchedEffect(Unit) {
                        navController.navigate("admin") { popUpTo("home") { inclusive = true } }
                    }
                } else {
                    UserDashboardScreen(
                        onOpenCommunityPost = { id -> navController.navigate("community_post/$id") },
                        onShoppingList = { navController.navigate("shopping_list") },
                        onPantry = { navController.navigate("pantry") },
                        onRecipes = { navController.navigate("recipes") },
                        onRecords = { navController.navigate("records?week=true") },
                        onIngredient = { id -> navController.navigate("ingredient_detail/$id") },
                        onPantryBatch = { id,batch -> navController.navigate("ingredient_detail/$id?batchId=$batch") },
                        onCreateIngredient = {navController.navigate("add")},
                        onAddPantry = {navController.navigate("add_ingredient")},
                        onCreateRecipe = {navController.navigate("community_create")},
                        onProfile = {navController.navigate("profile")},
                        userName = profile?.displayName.orEmpty()
                    )
                }
            }

            composable("pantry") {
                InventoryScreen(
                    onAddInventoryClick = { navController.navigate("add_ingredient") },
                    onCreateIngredientClick = { navController.navigate("add") },
                    onEditIngredientClick = { id -> navController.navigate("edit_ingredient/$id") },
                    onIngredientClick = { id -> navController.navigate("ingredient_detail/$id") },
                    onEditClick = { id -> navController.navigate("edit_inventory/$id") },
                    vm = ingredientUserVm,
                    ingredientVm = ingredientVm,
                    onBack = goHome
                )
            }

            composable(
                route = "ingredient_detail/{ingredientId}?batchId={batchId}",
                arguments = listOf(navArgument("ingredientId") { type = NavType.LongType },navArgument("batchId"){type=NavType.LongType;defaultValue=0L})
            ) { entry ->
                val id = entry.arguments?.getLong("ingredientId") ?: 0L
                IngredientDetailScreen(
                    ingredientId = id,
                    focusBatchId=entry.arguments?.getLong("batchId") ?: 0L,
                    onBackClick = { navController.popBackStack() },
                    onEditClick = { navController.navigate("edit_ingredient/$id") },
                    onAddInventoryClick = { _ -> navController.navigate("add_ingredient") },
                    onRecipeClick = { recipeId -> navController.navigate("recipe_detail/$recipeId") },
                    ingredientVm = ingredientVm,
                    inventoryVm = ingredientUserVm,
                    recipeVm = recipeVm
                )
            }

            composable("edit_ingredient/{ingredientId}") { entry ->
                val id = entry.arguments?.getString("ingredientId")?.toLongOrNull() ?: 0L
                EditIngredientScreen(
                    ingredientId = id,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("edit_inventory/{itemId}") { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L
                EditInventoryScreen(
                    itemId = itemId,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // --- RECIPES TAB ---
            composable("recipes") {
                CommunityScreen(
                    onBack = goHome,
                    onRootChanged = { communityRoot = it },
                    onPersonalRecipes = { navController.navigate("personal_recipes") },
                    onShoppingList = { navController.navigate("shopping_list") }
                )
            }
            composable(
                "community_post/{postId}",
                arguments = listOf(navArgument("postId") { type = NavType.LongType })
            ) { entry ->
                CommunityScreen(
                    initialPostId = entry.arguments!!.getLong("postId"),
                    onPersonalRecipes = { navController.navigate("personal_recipes") },
                    onShoppingList = { navController.navigate("shopping_list") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("community_create") {
                CommunityScreen(
                    startCreate = true,
                    onPersonalRecipes = { navController.navigate("personal_recipes") },
                    onShoppingList = { navController.navigate("shopping_list") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("personal_recipes") {
                Column(Modifier.fillMaxSize()) {
                    TextButton(onClick = { navController.popBackStack() }) { Text("‹ Back to community") }
                    Box(Modifier.weight(1f)) {
                        RecipeScreen(
                            onCreateClick = { navController.navigate("create_recipe") },
                            onRecipeClick = { id -> navController.navigate("recipe_detail/$id") },
                            onEditClick = { id -> navController.navigate("editRecipe/$id") },
                            recipeVm = recipeVm,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }

            composable(
                route = "recipe_detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments!!.getLong("id")
                RecipeDetailScreen(
                    recipeId = id,
                    onBackClick = { navController.popBackStack() },
                    onEditClick = { navController.navigate("editRecipe/$id") },
                    recipeVm = recipeVm
                )
            }

            composable(
                route = "editRecipe/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                EditRecipeScreen(
                    recipeId = entry.arguments!!.getLong("id"),
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("records?week={week}",arguments=listOf(navArgument("week"){type=NavType.BoolType;defaultValue=false})) { entry ->
                WasteLogScreen(onBack=goHome,initialWeek=entry.arguments?.getBoolean("week")==true)
            }

            composable("admin") {
                if (profile?.role == "admin") {
                    AdminScreen(
                        vm = adminVm,
                        onLogout = {
                            adminVm.reset()
                            profileViewModel.logout()
                            navController.navigate("login") { popUpTo(0) { inclusive = true } }
                        }
                    )
                } else {
                    LaunchedEffect(Unit) { navController.navigate("home") { popUpTo(0) } }
                }
            }

            composable("notifications") {
                NotificationScreen(onBack={navController.popBackStack()},
                    onPost={post,comment->navController.navigate("notification_post/$post?comment=$comment")},
                    onBatch={ingredient,batch->navController.navigate("ingredient_detail/$ingredient?batchId=$batch")},
                    onRecipes={query->navController.navigate("recipe_search?query=${android.net.Uri.encode(query)}")},
                    onShopping={navController.navigate("shopping_list")})
            }
            composable("notification_post/{postId}?comment={comment}",arguments=listOf(navArgument("postId"){type=NavType.LongType},navArgument("comment"){type=NavType.LongType;defaultValue=0L})){entry->
                CommunityScreen(initialPostId=entry.arguments!!.getLong("postId"),initialComments=true,initialCommentId=entry.arguments!!.getLong("comment"),onShoppingList={navController.navigate("shopping_list")},onBack={navController.popBackStack()})
            }
            composable("recipe_search?query={query}",arguments=listOf(navArgument("query"){type=NavType.StringType;defaultValue=""})){entry->
                CommunityScreen(initialSearch=entry.arguments?.getString("query").orEmpty(),onShoppingList={navController.navigate("shopping_list")},onBack={navController.popBackStack()})
            }
            composable("my_recipes") {
                CommunityScreen(initialMine=true,onShoppingList={navController.navigate("shopping_list")},onBack={navController.popBackStack()})
            }
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
                        onLogout = { profileViewModel.logout() },
                        onNotifications = {navController.navigate("notifications")},
                        onMyRecipes = {navController.navigate("my_recipes")}
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

            composable("favourites") {
                CommunityScreen(
                    initialSaved = true,
                    onPersonalRecipes = { navController.navigate("personal_recipes") },
                    onShoppingList = { navController.navigate("shopping_list") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("shopping_list") {
                ShoppingListScreen(
                    onBack = { navController.popBackStack() },
                    onInventoryChanged = { ingredientUserVm.refreshAfterShoppingTransfer() }
                )
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

            // --- ADD INGREDIENT TO INVENTORY ---
            composable("add_ingredient") {
                var popped by remember { mutableStateOf(false) }

                AddInventoryFlowScreen(
                    {
                        if (!popped) {
                            popped = true
                            if (navController.currentBackStackEntry?.destination?.route == "add_ingredient") {
                                navController.popBackStack(route = "add_ingredient", inclusive = true)
                            }
                        }
                    },
                    ingredientUserVm
                )
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
                    recipeVm,
                    ingredientVm
                )
            }
        }
        if(currentRoute in bottomNavRoutes && (currentRoute!="recipes" || communityRoot)) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                FloatingBottomBar(items,currentRoute,{route->
                    if(route=="home")goHome() else if(route!=navController.currentBackStackEntry?.destination?.route)navController.navigate(route){launchSingleTop=true;popUpTo("home"){saveState=true};restoreState=true}
                },{})
            }
        }
    }
}

@Composable
fun AppRoot(externalLink:String="",onLinkHandled:()->Unit={}) {
    val navController = rememberNavController()
    val status by supabase.auth.sessionStatus.collectAsState()
    var initialRoute by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(status) {
        if (initialRoute == null && status !is SessionStatus.Initializing) {
            initialRoute = if (status is SessionStatus.Authenticated) "home" else "login"
        }
    }
    var splashFinished by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(900); splashFinished = true }
    val start = initialRoute
    Box(Modifier.fillMaxSize()) {
        if (start != null) AppNavHost(startRoute = start, navController = navController,externalLink=externalLink,onLinkHandled=onLinkHandled)
        AnimatedVisibility(
            visible = !splashFinished || start == null,
            enter = fadeIn(tween(180)), exit = fadeOut(tween(220))
        ) { SplashScreen() }
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
            items.forEach { item ->
                NavBarTab(item=item,selected=currentRoute?.substringBefore('?')==item.route,onClick={onTabSelected(item.route)},modifier=Modifier.weight(1f))
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