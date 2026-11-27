package com.example.smartpantry.community

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.smartpantry.Screen.CommunityScreen.cleanDisplay
import com.example.smartpantry.Screen.CommunityScreen.cleanInput
import com.example.smartpantry.Screen.CommunityScreenpackage.RecipeDraftHelper
import com.example.smartpantry.Screen.RecipeUI.CommunityExportMenu
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.viewmodel.CommunityViewModel
import com.example.smartpantry.viewmodel.RecommendedRecipeResult
import com.example.smartpantry.viewmodel.recipe_vm
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

private data class RecipeActionTarget(
    val recipeId: Long,
    val title: String,
    val isPublished: Boolean,
    val postId: Long,
    val localRecipe: Recipe?,
    val matchInfo: RecommendedRecipeResult?
)

/** Community owns its temporary screens; the app's NavController still owns top-level routes. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CommunityScreen(
    initialPostId: Long = 0L,
    initialSaved: Boolean = false,
    initialMine: Boolean = false,
    startCreate: Boolean = false,
    onRootChanged: (Boolean) -> Unit = {},
    onPersonalRecipes: () -> Unit = {},
    onShoppingList: () -> Unit,
    onBack: (() -> Unit)? = null,
    recipeVm: recipe_vm = viewModel()
) {
    val vm: CommunityViewModel = viewModel()
    val posts by vm.posts.collectAsState()
    val mine by vm.mine.collectAsState()
    val loading by vm.loading.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val notice by vm.notice.collectAsState()
    val more by vm.more.collectAsState()
    val draft by vm.draft.collectAsState()

    val localRecipes by recipeVm.recipes.collectAsState()
    val recommendedRecipes by recipeVm.recommendedRecipes.collectAsState()
    val haptics = LocalHapticFeedback.current

    // Top-level filter tab: "for_me", "post", "saved", "personal"
    var activeFilter by rememberSaveable {
        mutableStateOf(
            when {
                initialSaved -> "saved"
                initialMine -> "personal"
                else -> "post"
            }
        )
    }

    var screen by rememberSaveable {
        mutableStateOf(
            when {
                startCreate -> "editor"
                initialPostId > 0L -> "detail"
                else -> "home"
            }
        )
    }
    var postId by rememberSaveable { mutableLongStateOf(initialPostId) }
    var selectedLocalRecipeId by rememberSaveable { mutableLongStateOf(0L) }

    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var filterSettings by rememberSaveable { mutableStateOf("{}") }
    var mineCategory by rememberSaveable { mutableStateOf("All") }

    var deleteConfirmRecipe by remember { mutableStateOf<Recipe?>(null) }
    var longPressedRecipe by remember { mutableStateOf<RecipeActionTarget?>(null) }

    var dialog by rememberSaveable { mutableStateOf("") }
    var dialogId by rememberSaveable { mutableLongStateOf(0L) }
    var dialogTitle by rememberSaveable { mutableStateOf("") }
    var caption by rememberSaveable { mutableStateOf("") }
    var editingCaption by rememberSaveable { mutableStateOf(false) }
    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    val detail by vm.detail.collectAsState()
    val postScroll=rememberLazyListState();val favouriteScroll=rememberLazyListState();val myScroll=rememberLazyListState();val recommendationScroll=rememberLazyListState()
    val root = screen == "home"

    val matchByRecipeId = remember(recommendedRecipes) {
        recommendedRecipes.associateBy { it.recipe.recipeId ?: -1L }
    }
    val topRecommendations = remember(recommendedRecipes) {
        recommendedRecipes.take(6)
    }
    val filteredForMe = remember(recommendedRecipes, search) {
        recommendedRecipes.filter { item ->
            search.isBlank() ||
                    item.recipe.recipeName.contains(search, ignoreCase = true) ||
                    (item.recipe.description ?: "").contains(search, ignoreCase = true)
        }
    }
    val mineCategories = remember(localRecipes, mine) {
        (listOf("All") +
                localRecipes.mapNotNull { it.recipeCategory?.takeIf(String::isNotBlank) } +
                mine.mapNotNull { it.str("category").takeIf(String::isNotBlank) }
                ).distinct()
    }
    val syncedMap = remember(mine) {
        mine.associateBy { it.long("id") }
    }
    val filteredLocalRecipes = remember(localRecipes, search, mineCategory) {
        localRecipes.filter { r ->
            val matchesSearch = search.isBlank() ||
                    r.recipeName.contains(search, ignoreCase = true) ||
                    (r.description ?: "").contains(search, ignoreCase = true)
            val matchesCat = mineCategory == "All" ||
                    r.recipeCategory.equals(mineCategory, ignoreCase = true)
            matchesSearch && matchesCat
        }
    }
    val filteredRemoteOnly = remember(mine, localRecipes, search, mineCategory) {
        val localIds = localRecipes.mapNotNull { it.recipeId }.toSet()
        mine.filter { it.long("id") !in localIds }.filter { row ->
            val matchesSearch = search.isBlank() ||
                    row.str("title").contains(search, ignoreCase = true)
            val matchesCat = mineCategory == "All" ||
                    row.str("category").equals(mineCategory, ignoreCase = true)
            matchesSearch && matchesCat
        }
    }
    val selectedLocalRecipe = remember(localRecipes, selectedLocalRecipeId) {
        localRecipes.find { (it.recipeId ?: 0L) == selectedLocalRecipeId }
    }

    LaunchedEffect(root) { onRootChanged(root) }
    val back: () -> Unit = {
        if (!busy) {
            when (screen) {
                "reviews" -> screen = "detail"
                "detail", "editor", "personal_detail" -> {
                    if ((initialPostId > 0L && screen == "detail") || (startCreate && screen == "editor")) {
                        onBack?.invoke()
                    } else {
                        postId = 0L
                        selectedLocalRecipeId = 0L
                        screen = "home"
                    }
                }
                else -> if(initialSaved || initialMine)onBack?.invoke() else if(activeFilter!="post"){activeFilter="post";search=""} else onBack?.invoke()
            }
        }
    }
    BackHandler { back() }

    LaunchedEffect(Unit) {
        vm.bindUser()
        recipeVm.refreshRecipes()
        vm.loadRecommendations()
        vm.feed("feed", "", "All")
    }
    LaunchedEffect(activeFilter, search, category, screen,filterSettings) {
        if (screen == "home" && activeFilter in listOf("post", "saved")) {
            if (search.isNotBlank()) delay(250.milliseconds)
            vm.feed(if (activeFilter == "saved") "saved" else "feed", search, category,filters=Json.parseToJsonElement(filterSettings).jsonObject)
        }
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,screen,activeFilter,search,category,filterSettings) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME && screen=="home") {
            vm.loadRecommendations();vm.feed(if(activeFilter=="saved")"saved" else "feed",search,category,keepVisible=true,filters=Json.parseToJsonElement(filterSettings).jsonObject)
        } }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(screen, postId) {
        if (screen in listOf("detail", "reviews") && postId > 0L) {
            vm.loadDetail(postId)
        }
    }

    val openPost: (Long) -> Unit = { id ->
        selectedLocalRecipeId = 0L
        postId = id
        screen = "detail"
    }
    val openLocalDetail: (Long) -> Unit = { recipeId ->
        val communityRow = syncedMap[recipeId]
        val publishedId = communityRow?.long("postId") ?: 0L
        if (communityRow?.flag("published") == true && publishedId > 0L) {
            openPost(publishedId)
        } else {
            postId = 0L
            selectedLocalRecipeId = recipeId
            screen = "detail"
        }
    }
    val publish: (Long, String) -> Unit = { id, title ->
        dialogId = id
        dialogTitle = title
        caption = ""
        editingCaption = false
        dialog = "publish"
    }
    val scheme = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SmartPantrySectionHeader(
                title = when (screen) {
                    "detail" -> "Recipe details"
                    "reviews" -> "Reviews"
                    "editor" -> if (draft.recipeId > 0) "Edit recipe" else "Create recipe"
                    else -> when (activeFilter) {
                        "for_me" -> "For you"
                        "saved" -> "Favourites"
                        "personal" -> "My recipes"
                        else -> "Community recipes"
                    }
                },
                showBack = screen != "home" || initialSaved || initialMine || initialPostId > 0L || startCreate,
                onBack = back,
                action = null
            )
            if (loading && !root) LinearProgressIndicator(Modifier.fillMaxWidth())
            Crossfade(
                targetState = screen,
                modifier = Modifier.weight(1f),
                animationSpec = tween(180),
                label = "community-page"
            ) { visibleScreen ->
                when (visibleScreen) {
                    "home" -> CommunityHub(
                        vm=vm,mode=activeFilter,search=search,onSearch={search=it},category=category,onCategory={category=it;filterSettings="{}"},
                        filters=Json.parseToJsonElement(filterSettings).jsonObject,
                        onFilters={ filterSettings=it.toString();category="All" },
                        onMode={activeFilter=it;search=""},onOpenPost=openPost,
                        onOpenPersonal={id->vm.openDraft(id){screen="personal_detail"}},onEditPost={row->dialogId=row.long("id");dialogTitle=row.str("title");caption=row.str("caption");editingCaption=true;dialog="publish"},
                        onEditRecipe={row->vm.openDraft(row.long("recipeId")){screen="editor"}},
                        onRemovePost={row->dialogId=row.long("id");dialogTitle=row.str("title");dialog="unpublish"},
                        onCreate={if(vm.startNew())dialog="resume" else {vm.resetDraft();screen="editor"}},
                        onEditPersonal={id->vm.openDraft(id){screen="editor"}},onPublish=publish,
                        onDeletePersonal={id->vm.deletePersonal(id){recipeVm.refreshRecipes()}},
                        listState=when(activeFilter){"saved"->favouriteScroll;"personal"->myScroll;"for_me"->recommendationScroll;else->postScroll}
                    )
                    "personal_detail" -> LocalRecipeDetails(
                        recipe=Recipe(recipeId=draft.recipeId,recipeName=draft.title,recipeCategory=draft.category,isVegan=draft.vegan,
                          description=draft.description,cookingSteps=draft.steps,recipeImage=draft.image,userId=vm.userId),
                        matchInfo=null,busy=busy,modifier=Modifier.fillMaxSize(),onEdit={screen="editor"},
                        onPublish={publish(draft.recipeId,draft.title)},onShoppingList=onShoppingList
                    )
                    "editor" -> RecipeEditor(
                        vm = vm,
                        modifier = Modifier.fillMaxSize(),
                        onSaved = { id ->
                            recipeVm.refreshRecipes()
                            publish(id, draft.title)
                        }
                    )

                    "detail" -> {
                        val current = detail
                        val localRec = selectedLocalRecipe
                        when {
                            postId > 0L && current != null -> RecipeDetails(
                                current,
                                vm.userId,
                                busy,
                                Modifier.fillMaxSize(),
                                onFavourite = { row ->
                                    vm.action("favourite", postId, buildJsonObject { put("enabled", !row.flag("favourite")) })
                                },
                                onReviews = { screen = "reviews" },
                                onMissing = { dialog = "missing" },
                                onShoppingList = onShoppingList,
                                onCaption = { row ->
                                    dialogId = postId
                                    caption = cleanInput(row.str("caption"), 500)
                                    editingCaption = true
                                    dialogTitle = row.str("title")
                                    dialog = "publish"
                                },
                                onEdit = { row ->
                                    vm.openDraft(row.long("recipeId")) { screen = "editor" }
                                },
                                onUnpublish = { row ->
                                    dialogId = postId
                                    dialogTitle = row.str("title")
                                    dialog = "unpublish"
                                }
                            )
                            localRec != null -> LocalRecipeDetails(
                                recipe = localRec,
                                matchInfo = matchByRecipeId[localRec.recipeId ?: -1L],
                                busy = busy,
                                modifier = Modifier.fillMaxSize(),
                                onEdit = {
                                    localRec.recipeId?.let { id ->
                                        vm.openDraft(id) { screen = "editor" }
                                    }
                                },
                                onPublish = {
                                    localRec.recipeId?.let { id ->
                                        publish(id, localRec.recipeName)
                                    }
                                },
                                onShoppingList = onShoppingList
                            )
                            !loading -> EmptyCommunity(
                                "Recipe unavailable",
                                "Refresh to check whether this recipe is still available.",
                                Icons.Default.Info
                            )
                        }
                    }

                    "reviews" -> detail?.let { RecipeReviews(it, vm, Modifier.fillMaxSize()) }
                }
            }
        }
        if (notice != null) Surface(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = scheme.inverseSurface,
            shadowElevation = 5.dp
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    notice.orEmpty(),
                    Modifier.weight(1f),
                    color = scheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = vm::clearNotice) {
                    Icon(Icons.Default.Close, "Dismiss message", tint = scheme.inverseOnSurface)
                }
            }
        }
        if (busy) Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .08f))
                .clickable(enabled = true, onClick = {}),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
    }

    // Long-press action bottom sheet
    longPressedRecipe?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { longPressedRecipe = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    target.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (target.isPublished) "Published to Community" else "Personal Recipe",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )

                target.matchInfo?.takeIf { it.missingIngredients.isNotEmpty() }?.let { match ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Missing: ${match.missingIngredients.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        longPressedRecipe = null
                        if (target.isPublished && target.postId > 0L) openPost(target.postId)
                        else if (target.recipeId > 0L) openLocalDetail(target.recipeId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Visibility, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View Recipe Details")
                }

                if (!target.isPublished) {
                    FilledTonalButton(
                        onClick = {
                            longPressedRecipe = null
                            if (target.recipeId > 0L) publish(target.recipeId, target.title)
                        },
                        enabled = !busy && target.recipeId > 0L,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Public, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Publish to Community")
                    }
                }

                OutlinedButton(
                    onClick = {
                        longPressedRecipe = null
                        if (target.recipeId > 0L) {
                            vm.openDraft(target.recipeId) { screen = "editor" }
                        }
                    },
                    enabled = !busy && target.recipeId > 0L,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Recipe")
                }

                if (target.isPublished && target.postId > 0L) {
                    OutlinedButton(
                        onClick = {
                            longPressedRecipe = null
                            dialogId = target.postId
                            dialogTitle = target.title
                            dialog = "unpublish"
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.PublicOff, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remove from Community")
                    }
                }

                target.localRecipe?.let { localRec ->
                    OutlinedButton(
                        onClick = {
                            longPressedRecipe = null
                            deleteConfirmRecipe = localRec
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Recipe")
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (error != null) AlertDialog(
        onDismissRequest = vm::clearError,
        icon = { Icon(Icons.Default.Info, null) },
        title = { Text("Unable to finish") },
        text = { Text(error.orEmpty()) },
        confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }
    )

    deleteConfirmRecipe?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmRecipe = null },
            title = { Text("Delete recipe?") },
            text = { Text("Delete \"${target.recipeName}\" permanently from your personal recipes?") },
            confirmButton = {
                TextButton(onClick = {
                    target.recipeId?.let { recipeVm.deleteRecipeById(it) }
                    deleteConfirmRecipe = null
                }) { Text("Delete", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmRecipe = null }) { Text("Cancel") }
            }
        )
    }

    when (dialog) {
        "resume" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("Continue your draft?") },
            text = { Text("Your unsaved recipe is still here.") },
            confirmButton = { TextButton(onClick = { dialog = ""; screen = "editor" }) { Text("Continue draft") } },
            dismissButton = {
                TextButton(onClick = { vm.resetDraft(); dialog = ""; screen = "editor" }) { Text("Start new") }
            }
        )
        "publish" -> AlertDialog(
            onDismissRequest = { if (!busy) dialog = "" },
            title = { Text(if (editingCaption) "Edit post" else "Publish recipe?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(dialogTitle, fontWeight = FontWeight.Bold)
                    Text(
                        if (editingCaption) "Update the caption shown with your recipe."
                        else "Your saved recipe will be visible to other members."
                    )
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = cleanInput(it, 500, 4) },
                        label = { Text("Caption (optional)") },
                        supportingText = { Text("${caption.length}/500") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (editingCaption) {
                            vm.caption(dialogId, caption) { dialog = ""; vm.loadDetail(postId) }
                        } else {
                            vm.publish(dialogId, caption) { id ->
                                dialog = ""
                                selectedLocalRecipeId = 0L
                                postId = id
                                screen = "detail"
                            }
                        }
                    }
                ) { Text(if (editingCaption) "Save" else "Publish") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = "" }, enabled = !busy) { Text("Not now") }
            }
        )
        "unpublish" -> AlertDialog(
            onDismissRequest = { if (!busy) dialog = "" },
            title = { Text("Remove this post?") },
            text = {
                Text("Remove “$dialogTitle” from the community? Your personal recipe is kept, and you can publish it again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.unpublish(dialogId) {
                            dialog = ""
                            postId = 0L
                            screen = "home"
                            activeFilter = "post"
                            vm.feed("feed",search,category,filters=Json.parseToJsonElement(filterSettings).jsonObject)
                            vm.loadRecommendations()
                        }
                    },
                    enabled = !busy
                ) { Text("Remove post") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = "" }, enabled = !busy) { Text("Cancel") }
            }
        )
        "missing" -> AlertDialog(
            onDismissRequest = { if (!busy) dialog = "" },
            title = { Text("Add missing ingredients?") },
            text = {
                Text("Add only the shortfall after available pantry stock and ingredients already on your shopping list are counted. Existing compatible entries will be combined.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.action("add_missing", postId, buildJsonObject { put("requestId", requestId) }) {
                            requestId = UUID.randomUUID().toString()
                            dialog = ""
                        }
                    },
                    enabled = !busy
                ) { Text("Add to list") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = "" }, enabled = !busy) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LocalRecipeDetails(
    recipe: Recipe,
    matchInfo: RecommendedRecipeResult?,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onPublish: () -> Unit,
    onShoppingList: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val imgUrl = recipe.localRecipeImage?.takeIf { File(it).exists() } ?: recipe.recipeImage.orEmpty()

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        RecipePhoto(imgUrl, Modifier.fillMaxWidth().height(215.dp))
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(recipe.recipeName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = c.secondaryContainer) {
                            Text(
                                "Personal recipe",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.onSecondaryContainer
                            )
                        }
                        recipe.recipeCategory?.takeIf { it.isNotBlank() }?.let {
                            Surface(shape = RoundedCornerShape(8.dp), color = c.primaryContainer) {
                                Text(
                                    it,
                                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.onPrimaryContainer
                                )
                            }
                        }
                        if (recipe.isVegan) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE4F4EA)) {
                                Text(
                                    "🌱 Vegan",
                                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF216B43)
                                )
                            }
                        }
                    }
                }
            }

            if (!recipe.description.isNullOrBlank()) {
                ReadableText(recipe.description!!)
            }

            if (matchInfo != null && matchInfo.totalIngredients > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Pantry readiness: ${matchInfo.matchingCount}/${matchInfo.totalIngredients} ingredients ready",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (matchInfo.missingIngredients.isNotEmpty()) {
                            Text(
                                "Missing: ${matchInfo.missingIngredients.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = c.outlineVariant)

            Text("Cooking steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (recipe.cookingSteps.isEmpty()) {
                Text("No cooking steps added yet.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            } else {
                recipe.cookingSteps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(c.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("${index + 1}", fontWeight = FontWeight.Bold, color = c.primary) }
                        Column(Modifier.weight(1f)) { ReadableText(step) }
                    }
                }
            }

            HorizontalDivider(color = c.outlineVariant)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onPublish,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Public, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Publish")
                }
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit recipe")
                }
            }
            TextButton(onClick = onShoppingList, modifier = Modifier.align(Alignment.End)) {
                Text("Open shopping list →")
            }
        }
    }
}

@Composable
internal fun SmartPantrySectionHeader(
    title: String,
    showBack: Boolean,
    onBack: (() -> Unit)?,
    action: @Composable (() -> Unit)? = null
) {
    val c = MaterialTheme.colorScheme
    var lastBackClickTime by remember { mutableLongStateOf(0L) }
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(c.background)
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.example.smartpantry.R.drawable.smartpantry_logo_horizontal),
                contentDescription = "SmartPantry",
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(200.dp)
                    .height(46.dp),
                contentScale = ContentScale.Fit
            )
            if (showBack) onBack?.let {
                IconButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastBackClickTime > 500L) {
                            lastBackClickTime = now
                            it()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
            Row(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { action?.invoke() }
        }
        if(title.isNotBlank()) Text(
            title,
            Modifier.padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 7.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun RecipePhoto(url: String, modifier: Modifier, recipeFallback: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val drawable=if(url.contains("://") || url.startsWith("/"))null else when(url.substringAfterLast('/').substringBeforeLast('.')) {
        "home_eggs","eggs"->com.example.smartpantry.R.drawable.home_eggs
        "home_tomatoes","tomatoes"->com.example.smartpantry.R.drawable.home_tomatoes
        "ingredient_milk","milk","fresh milk"->com.example.smartpantry.R.drawable.ingredient_milk
        "ingredient_onion","onion"->com.example.smartpantry.R.drawable.ingredient_onion
        "ingredient_rice","rice","white rice"->com.example.smartpantry.R.drawable.ingredient_rice
        "ingredient_orange","orange"->com.example.smartpantry.R.drawable.ingredient_orange
        else->null
    }
    Box(modifier.background(c.surfaceVariant), contentAlignment = Alignment.Center) {
        if(drawable!=null) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(drawable),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            return@Box
        }
        Icon(
            if (recipeFallback) Icons.Default.RestaurantMenu else Icons.Default.Eco,
            null,
            tint = c.primary,
            modifier = Modifier.size(36.dp)
        )
        if (url.isNotBlank()) AsyncImage(
            model = if (url.startsWith("/")) File(url) else url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun EmptyCommunity(title: String, message: String, icon: ImageVector) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(68.dp)
                .background(c.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = c.primary, modifier = Modifier.size(30.dp)) }
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
    }
}

@Composable
private fun RatingSummary(rating: Double, count: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.Star, null, tint = Color(0xFFF5B921), modifier = Modifier.size(18.dp))
        Text(
            if (count == 0L) "No ratings yet" else String.format(java.util.Locale.US, "%.1f", rating) + " ($count)",
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun RecipeFeedCard(
    row: JsonObject,
    matchInfo: RecommendedRecipeResult? = null,
    enabled: Boolean,
    onOpen: () -> Unit,
    onFavourite: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val urgentExpiry = (matchInfo?.expiredRate ?: 0.0) >= 0.6
    Surface(
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, c.outlineVariant),
        color = c.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column {
            Box {
                RecipePhoto(row.str("image"), Modifier.fillMaxWidth().height(155.dp))
                if (matchInfo != null && matchInfo.totalIngredients > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(9.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (urgentExpiry) Color(0xFFFEF3C7) else c.primaryContainer
                    ) {
                        Text(
                            text = if (urgentExpiry) {
                                "Use soon · ${matchInfo.matchingCount}/${matchInfo.totalIngredients}"
                            } else {
                                "${matchInfo.matchingCount}/${matchInfo.totalIngredients} in pantry"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (urgentExpiry) Color(0xFFB45309) else c.onPrimaryContainer
                        )
                    }
                }
                Surface(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = c.surface.copy(alpha = .95f)
                ) {
                    IconButton(onClick = onFavourite, enabled = enabled) {
                        Icon(
                            if (row.flag("favourite")) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            if (row.flag("favourite")) "Remove favourite" else "Save recipe",
                            tint = c.primary
                        )
                    }
                }
            }
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    row.str("title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "by ${row.str("author")}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    RatingSummary(row.number("rating"), row.long("ratingCount"))
                }
                if (row.str("caption").isNotBlank()) Text(
                    cleanDisplay(row.str("caption")),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecipeDetails(
    data: JsonObject,
    userId: String,
    busy: Boolean,
    modifier: Modifier,
    onFavourite: (JsonObject) -> Unit,
    onReviews: () -> Unit,
    onMissing: () -> Unit,
    onShoppingList: () -> Unit,
    onCaption: (JsonObject) -> Unit,
    onEdit: (JsonObject) -> Unit,
    onUnpublish: (JsonObject) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val post = data["post"]!!.jsonObject
    var export by rememberSaveable { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if(post.str("owner")==userId) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.End) {
            IconButton(onClick={onCaption(post)},enabled=!busy){Icon(Icons.Default.Edit,"Edit post")}
            var menu by remember { mutableStateOf(false) }
            Box { IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Post options")}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text("Edit recipe")},onClick={menu=false;onEdit(post)})
                    DropdownMenuItem(text={Text("Remove post")},onClick={menu=false;onUnpublish(post)},leadingIcon={Icon(Icons.Default.DeleteOutline,null)})
                }
            }
        }
        RecipePhoto(post.str("image"), Modifier.fillMaxWidth().height(215.dp))
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(post.str("title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "by ${post.str("author")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                IconButton(onClick = { onFavourite(post) }, enabled = !busy) {
                    Icon(
                        if (post.flag("favourite")) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        "Save recipe",
                        tint = c.primary
                    )
                }
                IconButton(onClick = { export = true }) {
                    Icon(Icons.Default.Share, "Share and export", tint = c.primary)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReviews),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RatingSummary(post.number("rating"), post.long("ratingCount"))
                Text("${post.long("commentCount")} comments ›", color = c.primary, style = MaterialTheme.typography.labelLarge)
            }
            if (post.str("caption").isNotBlank()) ReadableText(post.str("caption"))
            if (post.str("description").isNotBlank()) ReadableText(post.str("description"))
            HorizontalDivider(color = c.outlineVariant)
            Text("Ingredients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            data.rows("ingredients").forEach { row ->
                val missing = (row.number("quantity") - row.number("stock")).coerceAtLeast(0.0)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("•", color = c.primary)
                    Text(row.str("name"), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("${quantityText(row.number("quantity"))} ${row.str("unit")}", style = MaterialTheme.typography.bodySmall)
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (!row.flag("comparable")) c.surfaceVariant else if (missing > 0) c.errorContainer else c.primaryContainer
                    ) {
                        Text(
                            if (!row.flag("comparable")) "Check unit"
                            else if (missing > 0) "Missing ${quantityText(missing)} ${row.str("unit")}"
                            else "Available",
                            Modifier.padding(6.dp, 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.flag("comparable") && missing > 0) c.onErrorContainer else c.onSurface
                        )
                    }
                }
                if (row.number("inList") > 0) Text(
                    "${quantityText(row.number("inList"))} ${row.str("unit")} already on your shopping list",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(start = 15.dp)
                )
            }
            FilledTonalButton(onClick = onMissing, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.AddShoppingCart, null)
                Spacer(Modifier.width(8.dp))
                Text("Add missing ingredients")
            }
            TextButton(onClick = onShoppingList, modifier = Modifier.align(Alignment.End)) {
                Text("Open shopping list →")
            }
            Text("Cooking steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            data.words("steps").forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(c.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("${index + 1}", fontWeight = FontWeight.Bold, color = c.primary) }
                    Column(Modifier.weight(1f)) { ReadableText(step) }
                }
            }
            OutlinedButton(onClick = onReviews, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.ChatBubbleOutline, null)
                Spacer(Modifier.width(8.dp))
                Text("View reviews (${post.long("commentCount")})")
            }

        }
    }
    if (export) CommunityExportMenu(data) { export = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeEditor(vm: CommunityViewModel, modifier: Modifier, onSaved: (Long) -> Unit) {
    val draft by vm.draft.collectAsState()
    val busy by vm.busy.collectAsState()
    val catalog by vm.catalog.collectAsState()
    var helperVisible by rememberSaveable { mutableStateOf(false) }
    var helperName by rememberSaveable { mutableStateOf("") }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val prepare: () -> Unit = { vm.prepareByName(helperName) { helperVisible = false; confirmReplace = false } }
    var pickingIngredient by rememberSaveable { mutableStateOf(false) }
    var ingredientQuery by rememberSaveable { mutableStateOf("") }
    var selectedIngredient by rememberSaveable { mutableLongStateOf(0L) }
    var ingredientName by rememberSaveable { mutableStateOf("") }
    var ingredientQuantity by rememberSaveable { mutableStateOf("1") }
    var ingredientUnit by rememberSaveable { mutableStateOf("pcs") }
    var ingredientImage by rememberSaveable { mutableStateOf("") }
    var editingIngredient by rememberSaveable { mutableIntStateOf(-1) }
    var fieldError by rememberSaveable { mutableStateOf("") }
    var photos by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf("") }
    var photoError by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.photo(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri.isNotBlank()) vm.photo(Uri.parse(cameraUri))
        cameraUri = ""
    }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (draft.recipeId == 0L) {
            FilledTonalButton(
                onClick = { helperVisible = true },
                enabled = !busy && catalog.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Prepare a recipe draft")
            }
        }
        if (draft.source.isNotBlank()) Surface(shape = RoundedCornerShape(12.dp), color = c.primaryContainer) {
            Text(draft.source, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = c.onPrimaryContainer)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                RecipePhoto(draft.image, Modifier.size(116.dp).clip(RoundedCornerShape(18.dp)))
                FilledIconButton(
                    onClick = { photos = true },
                    enabled = !busy,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(40.dp)
                ) { Icon(Icons.Default.PhotoCamera, "Change recipe photo") }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { vm.editDraft(draft.copy(title = it.take(150))) },
                    label = { Text("Recipe title *") },
                    supportingText = { Text("${draft.title.length}/150") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

            }
        }
        OutlinedTextField(
            value = draft.description,
            onValueChange = { vm.editDraft(draft.copy(description = cleanInput(it, 5000, 8))) },
            label = { Text("Description (optional)") },
            supportingText = { Text("${draft.description.length}/5000") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(12.dp)
        )
        UnitDropdown("Cuisine",draft.cuisine.ifBlank { "Unspecified" },listOf("Unspecified","Malaysian","Chinese","Asian","Western","Other")) {
            vm.editDraft(draft.copy(cuisine=if(it=="Unspecified")"" else it,category=if(it=="Unspecified")"Unspecified" else it))
        }
        UnitDropdown("Dish type",draft.dishType.ifBlank { "Unspecified" },listOf("Unspecified","Rice dishes","Noodles","Light meals","Desserts","Other")) {
            vm.editDraft(draft.copy(dishType=if(it=="Unspecified")"" else it))
        }
        UnitDropdown("Dietary preference",if(draft.vegan)"Vegan" else draft.dietary,listOf("Unspecified","Vegetarian","Vegan")) {
            vm.editDraft(draft.copy(dietary=it,vegan=it=="Vegan"))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ingredients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${draft.ingredients.size}/50", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
        draft.ingredients.forEachIndexed { index, row ->
            Surface(shape = RoundedCornerShape(12.dp), color = c.surfaceVariant, border = BorderStroke(1.dp, c.outlineVariant)) {
                Row(
                    Modifier.padding(start = 12.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(row.name, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 2)
                    Text("${row.quantity} ${row.unit}", style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = {
                        editingIngredient = index
                        selectedIngredient = row.id
                        ingredientName = row.name
                        ingredientQuantity = row.quantity
                        ingredientUnit = row.unit
                        ingredientImage = row.image
                        fieldError = ""
                        pickingIngredient = true
                    }) { Icon(Icons.Default.Edit, "Edit ${row.name}", Modifier.size(19.dp)) }
                    IconButton(onClick = {
                        vm.editDraft(draft.copy(ingredients = draft.ingredients.filterIndexed { i, _ -> i != index }))
                    }) { Icon(Icons.Default.DeleteOutline, "Remove ${row.name}", Modifier.size(19.dp)) }
                }
            }
        }
        OutlinedButton(
            onClick = {
                editingIngredient = -1
                selectedIngredient = 0
                ingredientQuery = ""
                ingredientName = ""
                ingredientQuantity = "1"
                ingredientUnit = "pcs"
                fieldError = ""
                pickingIngredient = true
            },
            enabled = draft.ingredients.size < 50 && !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) { Icon(Icons.Default.Add, null); Text("Add ingredient") }
        Text("Cooking steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        draft.steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .size(28.dp)
                        .background(c.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("${index + 1}", fontWeight = FontWeight.Bold, color = c.primary) }
                OutlinedTextField(
                    value = step,
                    onValueChange = { text ->
                        vm.editDraft(draft.copy(steps = draft.steps.mapIndexed { i, s -> if (i == index) cleanInput(text, 500, 2) else s }))
                    },
                    maxLines = 5,
                    placeholder = { Text("Describe this cooking step") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    supportingText = { Text("${step.length}/500") }
                )
                IconButton(onClick = {
                    vm.editDraft(draft.copy(steps = if (draft.steps.size == 1) listOf("") else draft.steps.filterIndexed { i, _ -> i != index }))
                }) { Icon(Icons.Default.DeleteOutline, "Remove step ${index + 1}", Modifier.size(20.dp)) }
            }
        }
        OutlinedButton(
            onClick = { vm.editDraft(draft.copy(steps = draft.steps + "")) },
            enabled = draft.steps.size < 30 && !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) { Icon(Icons.Default.Add, null); Text("Add step") }
        Button(
            onClick = { vm.saveDraft(onSaved) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Save & continue", fontWeight = FontWeight.Bold) }
        Text(
            "Save your recipe first, then confirm publication. “Not now” keeps it in Personal.",
            style = MaterialTheme.typography.bodySmall,
            color = c.onSurfaceVariant
        )
    }
    if (helperVisible) ModalBottomSheet(onDismissRequest = { if (!busy) helperVisible = false }) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Recipe draft helper", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Enter a dish name for an AI draft, or choose a built-in template. Review all details before saving.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = helperName,
                onValueChange = { helperName = it.take(150) },
                label = { Text("What would you like to cook?") },
                placeholder = { Text("e.g. Ayam Kicap or 番茄炒蛋") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Text("Built-in recipes", fontWeight = FontWeight.SemiBold)
            RecipeDraftHelper.templates.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { template ->
                        FilterChip(
                            selected = RecipeDraftHelper.key(helperName) == RecipeDraftHelper.key(template.title),
                            onClick = { helperName = template.title },
                            label = { Text(template.title) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Text(
                "Uses community recipes or curated templates first; Groq can prepare other dishes when connected. AI may make mistakes. Nothing is saved or published automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant
            )
            Button(
                onClick = {
                    if (draft.title.isNotBlank() || draft.ingredients.isNotEmpty() || draft.steps.any { it.isNotBlank() }) {
                        confirmReplace = true
                    } else {
                        prepare()
                    }
                },
                enabled = helperName.trim().isNotEmpty() && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Prepare editable draft") }
        }
    }
    if (confirmReplace) AlertDialog(
        onDismissRequest = { if (!busy) confirmReplace = false },
        title = { Text("Replace this unsaved form?") },
        text = { Text("Prepare a new draft for “$helperName”? Your saved recipes will stay unchanged.") },
        confirmButton = { TextButton(onClick = prepare, enabled = !busy) { Text("Replace draft") } },
        dismissButton = { TextButton(onClick = { confirmReplace = false }, enabled = !busy) { Text("Cancel") } }
    )
    if (photos) ModalBottomSheet(onDismissRequest = { photos = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Recipe photo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FilledTonalButton(
                onClick = {
                    photos = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Choose from gallery") }
            OutlinedButton(
                onClick = {
                    photos = false
                    try {
                        val file = File(context.cacheDir, "community_camera_${UUID.randomUUID()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraUri = uri.toString()
                        camera.launch(uri)
                    } catch (e: Exception) {
                        photoError = "Unable to open camera. Check the FileProvider cache path, or choose from gallery."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Take photo") }
            if (draft.image.isNotBlank()) TextButton(onClick = { vm.editDraft(draft.copy(image = "")); photos = false }) {
                Icon(Icons.Default.DeleteOutline, null)
                Text("Remove photo")
            }
        }
    }
    if (photoError.isNotBlank()) AlertDialog(
        onDismissRequest = { photoError = "" },
        title = { Text("Photo unavailable") },
        text = { Text(photoError) },
        confirmButton = { TextButton(onClick = { photoError = "" }) { Text("OK") } }
    )
    if (pickingIngredient) ModalBottomSheet(onDismissRequest = { pickingIngredient = false }) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (editingIngredient >= 0) "Edit ingredient" else "Add ingredient",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (editingIngredient < 0) {
                OutlinedTextField(
                    value = ingredientQuery,
                    onValueChange = { ingredientQuery = it; selectedIngredient = 0 },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search your ingredient catalogue") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                val matches = catalog.filter { it.str("name").contains(ingredientQuery.trim(), true) }.take(30)
                if (matches.isEmpty()) Text(
                    "No matching ingredient. Your shared catalogue is used for pantry matching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant
                )
                Column(Modifier.heightIn(max = 210.dp).verticalScroll(rememberScrollState())) {
                    matches.forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selectedIngredient == row.long("id")) c.primaryContainer else Color.Transparent)
                                .clickable {
                                    selectedIngredient = row.long("id")
                                    ingredientName = row.str("name")
                                    ingredientImage = row.str("image")
                                    ingredientUnit = normalizedUnit(row.str("unit")).takeIf { it in recipeUnits } ?: "pcs"
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RecipePhoto(row.str("image"), Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), false)
                            Text(row.str("name"), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            if (selectedIngredient == row.long("id")) Icon(Icons.Default.Check, null, tint = c.primary)
                        }
                    }
                }
            } else Text(ingredientName, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = ingredientQuantity,
                    onValueChange = { ingredientQuantity = it.take(12) },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Box(Modifier.weight(1f).padding(top = 8.dp)) {
                    UnitDropdown("Unit", ingredientUnit, recipeUnits) { ingredientUnit = it }
                }
            }
            if (fieldError.isNotBlank()) Text(fieldError, color = c.error, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    val value = ingredientQuantity.toDoubleOrNull()
                    fieldError = when {
                        selectedIngredient == 0L -> "Select an ingredient."
                        value == null || !value.isFinite() || value <= 0 || value > 1000000 -> "Enter a positive quantity."
                        ingredientUnit in listOf("pcs", "bag", "carton", "bunch") && value % 1 != 0.0 -> "This unit needs a whole number."
                        editingIngredient < 0 && draft.ingredients.any { it.id == selectedIngredient } -> "Already added. Edit its existing quantity instead."
                        else -> ""
                    }
                    if (fieldError.isEmpty()) {
                        val row = DraftIngredient(selectedIngredient, ingredientName, ingredientQuantity, ingredientUnit, ingredientImage)
                        vm.editDraft(
                            draft.copy(
                                ingredients = if (editingIngredient < 0) draft.ingredients + row
                                else draft.ingredients.mapIndexed { i, old -> if (i == editingIngredient) row else old }
                            )
                        )
                        pickingIngredient = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (editingIngredient >= 0) "Save ingredient" else "Add ingredient") }
        }
    }
}

@Composable
private fun UnitDropdown(label: String, value: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 6.dp)
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Default.ExpandMore, "Choose $label", Modifier.size(32.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeReviews(data: JsonObject, vm: CommunityViewModel, modifier: Modifier) {
    val post = data["post"]!!.jsonObject
    val busy by vm.busy.collectAsState()
    val c = MaterialTheme.colorScheme
    var editor by rememberSaveable { mutableStateOf(false) }
    var commentId by rememberSaveable { mutableLongStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var commentRequest by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var deleteId by rememberSaveable { mutableLongStateOf(0) }
    val myRating = data.long("myRating").toInt()
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RecipePhoto(post.str("image"), Modifier.size(80.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f)) {
                    Text(post.str("title"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${post.long("commentCount")} comments", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (post.long("ratingCount") == 0L) "—" else String.format(java.util.Locale.US, "%.1f", post.number("rating")),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    RatingSummary(post.number("rating"), post.long("ratingCount"))
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = c.surfaceVariant) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (myRating > 0) "Your rating · $myRating / 5" else "How was this recipe?", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        (1..5).forEach { value ->
                            IconButton(onClick = { rating = value }, enabled = !busy) {
                                Icon(
                                    if (value <= (if (rating > 0) rating else myRating)) Icons.Default.Star else Icons.Default.StarBorder,
                                    "$value stars",
                                    tint = Color(0xFFF5B921),
                                    modifier = Modifier.size(31.dp)
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { vm.action("rating", post.long("id"), buildJsonObject { put("value", rating) }) { rating = 0 } },
                        enabled = rating in 1..5 && !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(if (myRating > 0) "Update rating" else "Submit rating") }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Community comments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { commentId = 0; text = ""; editor = true }, enabled = !busy) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text("Comment")
                }
            }
        }
        if (data.rows("comments").isEmpty()) item {
            EmptyCommunity("Start the conversation", "Share a helpful tip or tell others how your cooking went.", Icons.Default.ChatBubbleOutline)
        }
        items(data.rows("comments"), key = { it.long("id") }) { row ->
            Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, c.outlineVariant), color = c.surface) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (row.str("avatar").isNotBlank()) AsyncImage(
                                model = row.str("avatar"),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            ) else Text(row.str("author").take(1).uppercase(), fontWeight = FontWeight.Bold, color = c.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(row.str("author"), fontWeight = FontWeight.Bold)
                            Text(
                                row.str("createdAt").take(10) + (if (row.str("createdAt") != row.str("updatedAt")) " · edited" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.onSurfaceVariant
                            )
                        }
                        if (row.str("owner") == vm.userId) {
                            IconButton(
                                onClick = { commentId = row.long("id"); text = row.str("text"); editor = true },
                                enabled = !busy
                            ) { Icon(Icons.Default.Edit, "Edit your comment", Modifier.size(20.dp)) }
                            IconButton(onClick = { deleteId = row.long("id") }, enabled = !busy) {
                                Icon(Icons.Default.DeleteOutline, "Delete your comment", Modifier.size(20.dp))
                            }
                        }
                    }
                    ReadableText(row.str("text"))
                    TextButton(
                        onClick = {
                            vm.action(
                                "like",
                                post.long("id"),
                                buildJsonObject { put("commentId", row.long("id")); put("enabled", !row.flag("liked")) }
                            )
                        },
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            if (row.flag("liked")) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                            if (row.flag("liked")) "Unlike comment" else "Like comment",
                            Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${row.long("likes")}")
                    }
                }
            }
        }
    }
    if (editor) ModalBottomSheet(onDismissRequest = { if (!busy) editor = false }) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (commentId == 0L) "Write a comment" else "Edit your comment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Ratings and comments are saved separately. You can leave more than one comment.",
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = cleanInput(it, 1000, 6) },
                placeholder = { Text("Share a cooking tip or your experience…") },
                supportingText = { Text("${text.trim().length}/1000") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp)
            )
            Button(
                onClick = {
                    vm.action(
                        "comment",
                        post.long("id"),
                        buildJsonObject {
                            put("commentId", commentId)
                            put("text", text.trim())
                            put("requestId", commentRequest)
                        }
                    ) {
                        editor = false
                        text = ""
                        commentRequest = UUID.randomUUID().toString()
                    }
                },
                enabled = text.trim().isNotEmpty() && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (commentId == 0L) "Post comment" else "Save comment") }
        }
    }
    if (deleteId > 0) AlertDialog(
        onDismissRequest = { if (!busy) deleteId = 0 },
        title = { Text("Delete your comment?") },
        text = { Text("The comment and its likes will be removed.") },
        confirmButton = {
            TextButton(
                onClick = { vm.action("delete_comment", post.long("id"), buildJsonObject { put("commentId", deleteId) }) { deleteId = 0 } },
                enabled = !busy
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { deleteId = 0 }, enabled = !busy) { Text("Cancel") } }
    )
}

@Composable
internal fun ReadableText(value: String) {
    val text = cleanDisplay(value)
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Column {
        if (expanded) Box(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        } else Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (text.length > 220 || text.count { it == '\n' } >= 4) TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Show less" else "Read more")
        }
    }
}