package com.example.smartpantry.community

import com.example.smartpantry.Screen.RecipeUI.CommunityExportMenu
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.smartpantry.R
import com.example.smartpantry.Screen.CommunityScreen.cleanDisplay
import com.example.smartpantry.Screen.CommunityScreen.cleanInput
import com.example.smartpantry.Screen.CommunityScreenpackage.RecipeDraftHelper
import com.example.smartpantry.viewmodel.CommunityViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

/** Community owns its temporary screens; the app's NavController still owns top-level routes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(initialPostId: Long = 0L, initialSaved: Boolean = false, startCreate: Boolean = false, onRootChanged: (Boolean) -> Unit = {}, onPersonalRecipes: () -> Unit = {}, onShoppingList: () -> Unit, onBack: (() -> Unit)? = null) {
    val vm: CommunityViewModel = viewModel()
    val posts by vm.posts.collectAsState(); val mine by vm.mine.collectAsState()
    val loading by vm.loading.collectAsState(); val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState(); val notice by vm.notice.collectAsState(); val more by vm.more.collectAsState()
    var screen by rememberSaveable { mutableStateOf(if(startCreate) "editor" else if(initialPostId>0L) "detail" else if(initialSaved) "saved" else "feed") }
    var origin by rememberSaveable { mutableStateOf(if(initialSaved) "saved" else "feed") }
    var postId by rememberSaveable { mutableLongStateOf(initialPostId) }
    var search by rememberSaveable { mutableStateOf("") }; var category by rememberSaveable { mutableStateOf("All") }
    var dialog by rememberSaveable { mutableStateOf("") }; var dialogId by rememberSaveable { mutableLongStateOf(0L) }
    var dialogTitle by rememberSaveable { mutableStateOf("") }; var caption by rememberSaveable { mutableStateOf("") }
    var editingCaption by rememberSaveable { mutableStateOf(false) }
    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    val detail by vm.detail.collectAsState()
    val root = screen in listOf("feed","saved","mine")
    LaunchedEffect(root) { onRootChanged(root) }
    val back: () -> Unit = {
        if(!busy) {
            when(screen) { "reviews" -> screen="detail"; "detail","editor" -> if((initialPostId>0L && screen=="detail") || (startCreate && screen=="editor")) onBack?.invoke() else screen=origin; "mine","saved" -> if(initialSaved) onBack?.invoke() else { screen="feed"; search="" }; else -> onBack?.invoke() }
        }
    }
    BackHandler { back() }
    LaunchedEffect(Unit) { vm.bindUser(); if(startCreate) vm.feed() }
    LaunchedEffect(screen,search,category) {
        if(root) { delay(if(search.isBlank()) 0 else 250); vm.feed(if(screen=="saved") "saved" else "feed",search,category) }
    }
    LaunchedEffect(screen in listOf("detail","reviews"),postId) { if(screen in listOf("detail","reviews")) vm.loadDetail(postId) }
    val openPost: (Long) -> Unit = { id -> origin=screen; postId=id; screen="detail" }
    val publish: (Long,String) -> Unit = { id,title -> dialogId=id; dialogTitle=title; caption=""; editingCaption=false; dialog="publish" }
    val scheme=MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SmartPantrySectionHeader(
                title=when(screen) { "detail"->"Recipe details";"reviews"->"Reviews";"editor" -> if(vm.draft.value.recipeId > 0) "Edit recipe" else "Create recipe";"mine"->"My recipes";"saved"->"Favourites";else->"Community recipes" },
                showBack=screen!="feed" || initialSaved || initialPostId>0L || startCreate, onBack=back,
                action=null
            )
            if(loading&&!root) LinearProgressIndicator(Modifier.fillMaxWidth())
            Crossfade(targetState=screen,modifier=Modifier.weight(1f),animationSpec=tween(180),label="community-page") { visibleScreen ->
                when(visibleScreen) {
                    "feed","saved","mine" -> {
                        PullToRefreshBox(isRefreshing=loading,onRefresh={vm.feed(if(screen=="saved") "saved" else "feed",search,category,keepVisible=true)},modifier=Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,14.dp,18.dp,26.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                item {
                                    if(screen!="mine") OutlinedTextField(value=search,onValueChange={ search=it.take(80) },modifier=Modifier.fillMaxWidth(),singleLine=true,
                                        placeholder={Text(if(screen=="saved") "Search saved recipes" else "Search recipes")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(16.dp),trailingIcon=if(search.isNotBlank()) ({ IconButton(onClick={ search="" }) { Icon(Icons.Default.Close,"Clear search") } }) else null)
                                }
                                if(screen=="feed") item {
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("All","Malaysian","Vegan").forEach { name -> FilterChip(selected=category==name,onClick={category=name},label={Text(name)}) } }
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                        TextButton(onClick={screen="mine"}) { Icon(Icons.Default.FolderOpen,null,Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("My recipes") }
                                        FilledTonalButton(onClick={ origin="feed"; if(vm.startNew()) dialog="resume" else { vm.resetDraft(); screen="editor" } },enabled=!busy) { Icon(Icons.Default.Add,null,Modifier.size(18.dp)); Text("Create recipe") }
                                    }
                                }
                                if(screen=="mine") {
                                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Button(onClick={origin="mine"; if(vm.startNew()) dialog="resume" else { vm.resetDraft(); screen="editor" }},enabled=!busy) { Icon(Icons.Default.Add,null);Text("New recipe") }
                                        OutlinedButton(onClick={screen="saved"}) { Icon(Icons.Default.BookmarkBorder,null);Text("Saved") }
                                    };Text("Publish a saved recipe, or edit ingredients and steps first.",style=MaterialTheme.typography.bodySmall,color=scheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
                                        TextButton(onClick=onPersonalRecipes) { Icon(Icons.Default.Kitchen,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp));Text("Pantry recipe finder →") }
                                    }
                                    if(mine.isEmpty()&&!loading) item { EmptyCommunity("Your recipe collection starts here","Create a recipe, save it, then share it with the community.",Icons.Default.MenuBook) }
                                    items(mine,key={it.long("id")}) { row ->
                                        Surface(shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,scheme.outlineVariant),color=scheme.surface) {
                                            Column(Modifier.padding(12.dp)) {
                                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                                    RecipePhoto(row.str("image"),Modifier.size(78.dp).clip(RoundedCornerShape(12.dp)))
                                                    Column(Modifier.weight(1f)) { Text(row.str("title"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(if(row.flag("published")) "Published" else "Personal recipe",color=scheme.primary,style=MaterialTheme.typography.labelMedium) }
                                                    IconButton(onClick={ vm.openDraft(row.long("id")) { origin="mine"; screen="editor" } },enabled=!busy) { Icon(Icons.Default.Edit,"Edit ${row.str("title")}") }
                                                }
                                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                                                    if(row.flag("published")) {
                                                        TextButton(onClick={openPost(row.long("postId"))}) { Text("View post") }
                                                        TextButton(onClick={dialogId=row.long("postId");dialogTitle=row.str("title");dialog="unpublish"},enabled=!busy) { Text("Remove post") }
                                                    } else Button(onClick={publish(row.long("id"),row.str("title"))},enabled=!busy) { Text("Publish") }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    if(posts.isEmpty()&&!loading) item { EmptyCommunity(if(screen=="saved") "No saved recipes" else if(search.isNotBlank()) "No matching recipes" else "Be the first to share",if(screen=="saved") "Tap a bookmark on a community recipe to keep it here." else "Try another search, or publish one of your recipes.",Icons.Default.RestaurantMenu) }
                                    items(posts,key={it.long("id")}) { row -> RecipeFeedCard(row,enabled=!busy,onOpen={openPost(row.long("id"))},onFavourite={vm.action("favourite",row.long("id"),buildJsonObject {put("enabled",!row.flag("favourite"))}) { if(screen=="saved") vm.feed("saved",search,category) }}) }
                                    if(more) item { OutlinedButton(onClick={vm.feed(if(screen=="saved") "saved" else "feed",search,category,true)},enabled=!loading,modifier=Modifier.fillMaxWidth()) { Text("Load more recipes") } }
                                }
                            }
                        }
                    }
                    "editor" -> RecipeEditor(vm,Modifier.fillMaxSize(),onSaved={id-> publish(id,vm.draft.value.title)})
                    "detail" -> {
                        val current=detail
                        if(current!=null) RecipeDetails(current,vm.userId,busy,Modifier.fillMaxSize(),
                            onFavourite={row->vm.action("favourite",postId,buildJsonObject {put("enabled",!row.flag("favourite"))})},
                            onReviews={screen="reviews"},onMissing={dialog="missing"},onShoppingList=onShoppingList,
                            onCaption={row->dialogId=postId;caption=cleanInput(row.str("caption"),500);editingCaption=true;dialogTitle=row.str("title");dialog="publish"},
                            onEdit={row->vm.openDraft(row.long("recipeId")) {origin="mine";screen="editor"}},
                            onUnpublish={row->dialogId=postId;dialogTitle=row.str("title");dialog="unpublish"})
                        else if(!loading) EmptyCommunity("Recipe unavailable","Refresh the community feed to check whether this post is still published.",Icons.Default.Info)
                    }
                    "reviews" -> detail?.let { RecipeReviews(it,vm,Modifier.fillMaxSize()) }
                }
            }
        }
        if(notice!=null) Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp),shape=RoundedCornerShape(16.dp),color=scheme.inverseSurface,shadowElevation=5.dp) {
            Row(Modifier.padding(start=16.dp,end=4.dp,top=5.dp,bottom=5.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(notice.orEmpty(),Modifier.weight(1f),color=scheme.inverseOnSurface,style=MaterialTheme.typography.bodyMedium)
                IconButton(onClick=vm::clearNotice) { Icon(Icons.Default.Close,"Dismiss message",tint=scheme.inverseOnSurface) }
            }
        }
        if(busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.08f)).clickable(enabled=true,onClick={}),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
    }
    if(error!=null) AlertDialog(onDismissRequest=vm::clearError,icon={Icon(Icons.Default.Info,null)},title={Text("Unable to finish")},text={Text(error.orEmpty())},confirmButton={TextButton(onClick=vm::clearError){Text("OK")}})
    when(dialog) {
        "resume" -> AlertDialog(onDismissRequest={dialog=""},title={Text("Continue your draft?")},text={Text("Your unsaved recipe is still here.")},confirmButton={TextButton(onClick={dialog="";screen="editor"}){Text("Continue draft")}},dismissButton={TextButton(onClick={vm.resetDraft();dialog="";screen="editor"}){Text("Start new")}})
        "publish" -> AlertDialog(onDismissRequest={if(!busy)dialog=""},title={Text(if(editingCaption) "Edit post" else "Publish recipe?")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text(dialogTitle,fontWeight=FontWeight.Bold);Text(if(editingCaption) "Update the caption shown with your recipe." else "Your saved recipe will be visible to other members.");OutlinedTextField(value=caption,onValueChange={caption=cleanInput(it,500,4)},label={Text("Caption (optional)")},supportingText={Text("${caption.length}/500")},minLines=2,maxLines=4)}},
            confirmButton={TextButton(enabled=!busy,onClick={if(editingCaption) vm.caption(dialogId,caption){dialog="";vm.loadDetail(postId)} else vm.publish(dialogId,caption){id->dialog="";origin="feed";postId=id;screen="detail"}}){Text(if(editingCaption) "Save" else "Publish")}},dismissButton={TextButton(onClick={dialog=""},enabled=!busy){Text("Not now")}})
        "unpublish" -> AlertDialog(onDismissRequest={if(!busy)dialog=""},title={Text("Remove this post?")},text={Text("Remove “$dialogTitle” from the community? Your personal recipe is kept, and you can publish it again.")},confirmButton={TextButton(onClick={vm.unpublish(dialogId){dialog="";screen="mine";vm.feed()}},enabled=!busy){Text("Remove post")}},dismissButton={TextButton(onClick={dialog=""},enabled=!busy){Text("Cancel")}})
        "missing" -> AlertDialog(onDismissRequest={if(!busy)dialog=""},title={Text("Add missing ingredients?")},text={Text("Add only the shortfall after available pantry stock and ingredients already on your shopping list are counted. Existing compatible entries will be combined.")},confirmButton={TextButton(onClick={vm.action("add_missing",postId,buildJsonObject {put("requestId",requestId)}){requestId=UUID.randomUUID().toString();dialog=""}},enabled=!busy){Text("Add to list")}},dismissButton={TextButton(onClick={dialog=""},enabled=!busy){Text("Cancel")}})
    }
}

@Composable
internal fun SmartPantrySectionHeader(title: String,showBack: Boolean,onBack:()->Unit,action:(@Composable ()->Unit)?=null) {
    val c=MaterialTheme.colorScheme
    Column {
        Box(Modifier.fillMaxWidth().height(60.dp).background(c.background)) {
            androidx.compose.foundation.Image(
                painter=androidx.compose.ui.res.painterResource(com.example.smartpantry.R.drawable.smartpantry_logo_horizontal),
                contentDescription="SmartPantry",
                modifier=Modifier.align(Alignment.Center).width(200.dp).height(46.dp),contentScale=ContentScale.Fit
            )
            if(showBack) IconButton(onClick=onBack,modifier=Modifier.align(Alignment.CenterStart).padding(start=8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")
            }
            Row(Modifier.align(Alignment.CenterEnd).padding(end=8.dp),verticalAlignment=Alignment.CenterVertically) { action?.invoke() }
        }
        Text(title,Modifier.padding(start=18.dp,end=18.dp,top=17.dp,bottom=7.dp),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    }
}
@Composable
internal fun RecipePhoto(url:String,modifier:Modifier,recipeFallback:Boolean=true) {
    val c=MaterialTheme.colorScheme
    Box(modifier.background(c.surfaceVariant),contentAlignment=Alignment.Center) {
        Icon(if(recipeFallback) Icons.Default.RestaurantMenu else Icons.Default.Eco,null,tint=c.primary,modifier=Modifier.size(36.dp))
        if(url.isNotBlank()) AsyncImage(model=if(url.startsWith("/")) File(url) else url,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
    }
}
@Composable
private fun EmptyCommunity(title:String,message:String,icon:ImageVector) {
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical=34.dp,horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(68.dp).background(c.primaryContainer,CircleShape),contentAlignment=Alignment.Center){Icon(icon,null,tint=c.primary,modifier=Modifier.size(30.dp))}
        Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
        Text(message,style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant)
    }
}
@Composable
private fun RatingSummary(rating:Double,count:Long) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.Star,null,tint=Color(0xFFF5B921),modifier=Modifier.size(18.dp))
        Text(if(count==0L) "No ratings yet" else String.format(java.util.Locale.US,"%.1f",rating)+" ($count)",style=MaterialTheme.typography.labelLarge)
    }
}
@Composable
private fun RecipeFeedCard(row:JsonObject,enabled:Boolean,onOpen:()->Unit,onFavourite:()->Unit) {
    val c=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(20.dp),shadowElevation=2.dp,border=BorderStroke(1.dp,c.outlineVariant),color=c.surface,modifier=Modifier.fillMaxWidth().clickable(onClick=onOpen)) {
        Column {
            Box { RecipePhoto(row.str("image"),Modifier.fillMaxWidth().height(155.dp));Surface(Modifier.align(Alignment.TopEnd).padding(9.dp),shape=RoundedCornerShape(12.dp),color=c.surface.copy(alpha=.95f)) {
                IconButton(onClick=onFavourite,enabled=enabled){Icon(if(row.flag("favourite")) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,if(row.flag("favourite")) "Remove favourite" else "Save recipe",tint=c.primary)}
            } }
            Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(row.str("title"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Row(verticalAlignment=Alignment.CenterVertically) { Text("by ${row.str("author")}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis);RatingSummary(row.number("rating"),row.long("ratingCount")) }
                if(row.str("caption").isNotBlank()) Text(cleanDisplay(row.str("caption")),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RecipeDetails(data:JsonObject,userId:String,busy:Boolean,modifier:Modifier,onFavourite:(JsonObject)->Unit,onReviews:()->Unit,onMissing:()->Unit,onShoppingList:()->Unit,onCaption:(JsonObject)->Unit,onEdit:(JsonObject)->Unit,onUnpublish:(JsonObject)->Unit) {
    val c=MaterialTheme.colorScheme;val post=data["post"]!!.jsonObject
    var export by rememberSaveable { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom=24.dp)) {
        RecipePhoto(post.str("image"),Modifier.fillMaxWidth().height(215.dp))
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.Top) {
                Column(Modifier.weight(1f)) { Text(post.str("title"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("by ${post.str("author")}",style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant,modifier=Modifier.padding(top=4.dp)) }
                IconButton(onClick={onFavourite(post)},enabled=!busy){Icon(if(post.flag("favourite"))Icons.Default.Bookmark else Icons.Default.BookmarkBorder,"Save recipe",tint=c.primary)}
                IconButton(onClick={export=true}){Icon(Icons.Default.Share,"Share and export",tint=c.primary)}
            }
            Row(Modifier.fillMaxWidth().clickable(onClick=onReviews),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) { RatingSummary(post.number("rating"),post.long("ratingCount"));Text("${post.long("commentCount")} comments ›",color=c.primary,style=MaterialTheme.typography.labelLarge) }
            if(post.str("caption").isNotBlank()) ReadableText(post.str("caption"))
            if(post.str("description").isNotBlank()) ReadableText(post.str("description"))
            HorizontalDivider(color=c.outlineVariant)
            Text("Ingredients",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            data.rows("ingredients").forEach { row ->
                val missing=(row.number("quantity")-row.number("stock")).coerceAtLeast(0.0)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("•",color=c.primary);Text(row.str("name"),Modifier.weight(1f),fontWeight=FontWeight.Medium)
                    Text("${quantityText(row.number("quantity"))} ${row.str("unit")}",style=MaterialTheme.typography.bodySmall)
                    Surface(shape=RoundedCornerShape(9.dp),color=if(!row.flag("comparable"))c.surfaceVariant else if(missing>0)c.errorContainer else c.primaryContainer) {
                        Text(if(!row.flag("comparable")) "Check unit" else if(missing>0) "Missing ${quantityText(missing)} ${row.str("unit")}" else "Available",Modifier.padding(6.dp,4.dp),style=MaterialTheme.typography.labelSmall,color=if(row.flag("comparable")&&missing>0)c.onErrorContainer else c.onSurface)
                    }
                }
                if(row.number("inList")>0) Text("${quantityText(row.number("inList"))} ${row.str("unit")} already on your shopping list",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant,modifier=Modifier.padding(start=15.dp))
            }
            FilledTonalButton(onClick=onMissing,enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.AddShoppingCart,null);Spacer(Modifier.width(8.dp));Text("Add missing ingredients")}
            TextButton(onClick=onShoppingList,modifier=Modifier.align(Alignment.End)){Text("Open shopping list →")}
            Text("Cooking steps",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            data.words("steps").forEachIndexed { index,step ->
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(30.dp).background(c.primaryContainer,CircleShape),contentAlignment=Alignment.Center){Text("${index+1}",fontWeight=FontWeight.Bold,color=c.primary)}
                    Column(Modifier.weight(1f)){ReadableText(step)}
                }
            }
            OutlinedButton(onClick=onReviews,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.ChatBubbleOutline,null);Spacer(Modifier.width(8.dp));Text("View reviews (${post.long("commentCount")})")}
            if(post.str("owner")==userId) {
                HorizontalDivider(color=c.outlineVariant)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onClick={onCaption(post)},enabled=!busy){Text("Edit post")};TextButton(onClick={onEdit(post)},enabled=!busy){Text("Edit recipe")};TextButton(onClick={onUnpublish(post)},enabled=!busy){Text("Remove")}}
            }
        }
    }
    if(export)CommunityExportMenu(data){export=false}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeEditor(vm:CommunityViewModel,modifier:Modifier,onSaved:(Long)->Unit) {
    val draft by vm.draft.collectAsState();val busy by vm.busy.collectAsState();val catalog by vm.catalog.collectAsState()
    var helperVisible by rememberSaveable { mutableStateOf(false) }
    var helperName by rememberSaveable { mutableStateOf("") }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val prepare: () -> Unit = { vm.prepareByName(helperName) { helperVisible=false; confirmReplace=false } }
    var pickingIngredient by rememberSaveable { mutableStateOf(false) };var ingredientQuery by rememberSaveable { mutableStateOf("") }
    var selectedIngredient by rememberSaveable { mutableLongStateOf(0L) };var ingredientName by rememberSaveable { mutableStateOf("") }
    var ingredientQuantity by rememberSaveable { mutableStateOf("1") };var ingredientUnit by rememberSaveable { mutableStateOf("pcs") }
    var ingredientImage by rememberSaveable { mutableStateOf("") };var editingIngredient by rememberSaveable { mutableIntStateOf(-1) }
    var fieldError by rememberSaveable { mutableStateOf("") };var photos by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf("") };var photoError by rememberSaveable { mutableStateOf("") }
    val context=LocalContext.current;val c=MaterialTheme.colorScheme
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->if(uri!=null)vm.photo(uri)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {success->if(success&&cameraUri.isNotBlank())vm.photo(Uri.parse(cameraUri));cameraUri=""}
    Column(modifier.verticalScroll(rememberScrollState()).imePadding().padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(draft.recipeId==0L) {
            FilledTonalButton(onClick={helperVisible=true},enabled=!busy&&catalog.isNotEmpty(),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.AutoAwesome,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Prepare a recipe draft")
            }
        }
        if(draft.source.isNotBlank()) Surface(shape=RoundedCornerShape(12.dp),color=c.primaryContainer) {
            Text(draft.source,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,color=c.onPrimaryContainer)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Box {RecipePhoto(draft.image,Modifier.size(116.dp).clip(RoundedCornerShape(18.dp)));FilledIconButton(onClick={photos=true},enabled=!busy,modifier=Modifier.align(Alignment.BottomEnd).size(40.dp)){Icon(Icons.Default.PhotoCamera,"Change recipe photo")}}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value=draft.title,onValueChange={vm.editDraft(draft.copy(title=it.take(150)))},label={Text("Recipe title *")},supportingText={Text("${draft.title.length}/150")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
                UnitDropdown("Category",draft.category,listOf("Malaysian","Asian","Western","Dessert","Other")){vm.editDraft(draft.copy(category=it))}
            }
        }
        OutlinedTextField(value=draft.description,onValueChange={vm.editDraft(draft.copy(description=cleanInput(it,5000,8)))},label={Text("Description (optional)")},supportingText={Text("${draft.description.length}/5000")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=5,shape=RoundedCornerShape(12.dp))
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=draft.vegan,onCheckedChange={vm.editDraft(draft.copy(vegan=it))});Text("Vegan (no animal ingredients)",style=MaterialTheme.typography.bodyMedium)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Ingredients",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("${draft.ingredients.size}/50",style=MaterialTheme.typography.labelMedium,color=c.onSurfaceVariant)}
        draft.ingredients.forEachIndexed {index,row->
            Surface(shape=RoundedCornerShape(12.dp),color=c.surfaceVariant,border=BorderStroke(1.dp,c.outlineVariant)) {
                Row(Modifier.padding(start=12.dp,end=2.dp,top=3.dp,bottom=3.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(row.name,Modifier.weight(1f),fontWeight=FontWeight.Medium,maxLines=2);Text("${row.quantity} ${row.unit}",style=MaterialTheme.typography.bodySmall)
                    IconButton(onClick={editingIngredient=index;selectedIngredient=row.id;ingredientName=row.name;ingredientQuantity=row.quantity;ingredientUnit=row.unit;ingredientImage=row.image;fieldError="";pickingIngredient=true}){Icon(Icons.Default.Edit,"Edit ${row.name}",Modifier.size(19.dp))}
                    IconButton(onClick={vm.editDraft(draft.copy(ingredients=draft.ingredients.filterIndexed{i,_->i!=index}))}){Icon(Icons.Default.DeleteOutline,"Remove ${row.name}",Modifier.size(19.dp))}
                }
            }
        }
        OutlinedButton(onClick={editingIngredient=-1;selectedIngredient=0;ingredientQuery="";ingredientName="";ingredientQuantity="1";ingredientUnit="pcs";fieldError="";pickingIngredient=true},enabled=draft.ingredients.size<50&&!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Add,null);Text("Add ingredient")}
        Text("Cooking steps",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        draft.steps.forEachIndexed {index,step->
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top) {
                Box(Modifier.padding(top=12.dp).size(28.dp).background(c.primaryContainer,CircleShape),contentAlignment=Alignment.Center){Text("${index+1}",fontWeight=FontWeight.Bold,color=c.primary)}
                OutlinedTextField(value=step,onValueChange={text->vm.editDraft(draft.copy(steps=draft.steps.mapIndexed{i,s->if(i==index)cleanInput(text,500,2) else s}))},maxLines=5,placeholder={Text("Describe this cooking step")},modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp),supportingText={Text("${step.length}/500")})
                IconButton(onClick={vm.editDraft(draft.copy(steps=if(draft.steps.size==1)listOf("") else draft.steps.filterIndexed{i,_->i!=index}))}){Icon(Icons.Default.DeleteOutline,"Remove step ${index+1}",Modifier.size(20.dp))}
            }
        }
        OutlinedButton(onClick={vm.editDraft(draft.copy(steps=draft.steps+""))},enabled=draft.steps.size<30&&!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Add,null);Text("Add step")}
        Button(onClick={vm.saveDraft(onSaved)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(14.dp)){Text("Save & continue",fontWeight=FontWeight.Bold)}
        Text("Save your recipe first, then confirm publication. “Not now” keeps it in My recipes.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)

    }
    if(helperVisible) ModalBottomSheet(onDismissRequest={if(!busy)helperVisible=false}) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Recipe draft helper",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Enter a dish name for an AI draft, or choose a built-in template. Review all details before saving.",style=MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value=helperName,onValueChange={helperName=it.take(150)},label={Text("What would you like to cook?")},placeholder={Text("e.g. Ayam Kicap or 番茄炒蛋")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
            Text("Built-in recipes",fontWeight=FontWeight.SemiBold)
            RecipeDraftHelper.templates.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    pair.forEach { template ->
                        FilterChip(selected=RecipeDraftHelper.key(helperName)==RecipeDraftHelper.key(template.title),onClick={helperName=template.title},label={Text(template.title)},modifier=Modifier.weight(1f))
                    }
                }
            }
            Text("Uses community recipes or curated templates first; Groq can prepare other dishes when connected. AI may make mistakes. Nothing is saved or published automatically.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            Button(onClick={
                if(draft.title.isNotBlank() || draft.ingredients.isNotEmpty() || draft.steps.any{it.isNotBlank()}) confirmReplace=true else prepare()
            },enabled=helperName.trim().isNotEmpty()&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(12.dp)) { Text("Prepare editable draft") }
        }
    }
    if(confirmReplace) AlertDialog(onDismissRequest={if(!busy)confirmReplace=false},title={Text("Replace this unsaved form?")},text={Text("Prepare a new draft for “$helperName”? Your saved recipes will stay unchanged.")},confirmButton={TextButton(onClick=prepare,enabled=!busy){Text("Replace draft")}},dismissButton={TextButton(onClick={confirmReplace=false},enabled=!busy){Text("Cancel")}})
    if(photos) ModalBottomSheet(onDismissRequest={photos=false}) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Recipe photo",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            FilledTonalButton(onClick={photos=false;gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PhotoLibrary,null);Spacer(Modifier.width(8.dp));Text("Choose from gallery")}
            OutlinedButton(onClick={
                photos=false
                try {
                    val file=File(context.cacheDir,"community_camera_${UUID.randomUUID()}.jpg")
                    val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
                    cameraUri=uri.toString();camera.launch(uri)
                } catch(e:Exception){photoError="Unable to open camera. Check the FileProvider cache path, or choose from gallery."}
            },modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PhotoCamera,null);Spacer(Modifier.width(8.dp));Text("Take photo")}
            if(draft.image.isNotBlank()) TextButton(onClick={vm.editDraft(draft.copy(image=""));photos=false}){Icon(Icons.Default.DeleteOutline,null);Text("Remove photo")}
        }
    }
    if(photoError.isNotBlank()) AlertDialog(onDismissRequest={photoError=""},title={Text("Photo unavailable")},text={Text(photoError)},confirmButton={TextButton(onClick={photoError=""}){Text("OK")}})
    if(pickingIngredient) ModalBottomSheet(onDismissRequest={pickingIngredient=false}) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(editingIngredient>=0) "Edit ingredient" else "Add ingredient",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            if(editingIngredient<0) {
                OutlinedTextField(value=ingredientQuery,onValueChange={ingredientQuery=it;selectedIngredient=0},leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text("Search your ingredient catalogue")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
                val matches=catalog.filter{it.str("name").contains(ingredientQuery.trim(),true)}.take(30)
                if(matches.isEmpty()) Text("No matching ingredient. Your shared catalogue is used for pantry matching.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                Column(Modifier.heightIn(max=210.dp).verticalScroll(rememberScrollState())) {
                    matches.forEach {row->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if(selectedIngredient==row.long("id"))c.primaryContainer else Color.Transparent).clickable{selectedIngredient=row.long("id");ingredientName=row.str("name");ingredientImage=row.str("image");ingredientUnit=normalizedUnit(row.str("unit")).takeIf{it in recipeUnits}?:"pcs"}.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            RecipePhoto(row.str("image"),Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),false);Text(row.str("name"),Modifier.weight(1f),fontWeight=FontWeight.Medium);if(selectedIngredient==row.long("id"))Icon(Icons.Default.Check,null,tint=c.primary)
                        }
                    }
                }
            } else Text(ingredientName,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
                OutlinedTextField(value=ingredientQuantity,onValueChange={ingredientQuantity=it.take(12)},label={Text("Quantity")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp))
                Box(Modifier.weight(1f).padding(top=8.dp)){UnitDropdown("Unit",ingredientUnit,recipeUnits){ingredientUnit=it}}
            }
            if(fieldError.isNotBlank()) Text(fieldError,color=c.error,style=MaterialTheme.typography.bodySmall)
            Button(onClick={
                val value=ingredientQuantity.toDoubleOrNull()
                fieldError=when {
                    selectedIngredient==0L -> "Select an ingredient."
                    value==null||!value.isFinite()||value<=0||value>1000000 -> "Enter a positive quantity."
                    ingredientUnit in listOf("pcs","bag","carton","bunch")&&value%1!=0.0 -> "This unit needs a whole number."
                    editingIngredient<0&&draft.ingredients.any{it.id==selectedIngredient} -> "Already added. Edit its existing quantity instead."
                    else -> ""
                }
                if(fieldError.isEmpty()) {
                    val row=DraftIngredient(selectedIngredient,ingredientName,ingredientQuantity,ingredientUnit,ingredientImage)
                    vm.editDraft(draft.copy(ingredients=if(editingIngredient<0)draft.ingredients+row else draft.ingredients.mapIndexed{i,old->if(i==editingIngredient)row else old}))
                    pickingIngredient=false
                }
            },modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(12.dp)){Text(if(editingIngredient>=0) "Save ingredient" else "Add ingredient")}
        }
    }
}
@Composable
private fun UnitDropdown(label:String,value:String,options:List<String>,onSelected:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={expanded=true},shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),contentPadding=PaddingValues(start=12.dp,end=6.dp)) {
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.Start){Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,style=MaterialTheme.typography.bodyLarge)}
            Icon(Icons.Default.ExpandMore,"Choose $label",Modifier.size(32.dp))
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){options.forEach{option->DropdownMenuItem(text={Text(option)},onClick={onSelected(option);expanded=false})}}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeReviews(data:JsonObject,vm:CommunityViewModel,modifier:Modifier) {
    val post=data["post"]!!.jsonObject;val busy by vm.busy.collectAsState();val c=MaterialTheme.colorScheme
    var editor by rememberSaveable { mutableStateOf(false) };var commentId by rememberSaveable { mutableLongStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") };var rating by rememberSaveable { mutableIntStateOf(0) }
    var commentRequest by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var deleteId by rememberSaveable { mutableLongStateOf(0) }
    val myRating=data.long("myRating").toInt()
    LazyColumn(modifier,contentPadding=PaddingValues(18.dp,12.dp,18.dp,30.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                RecipePhoto(post.str("image"),Modifier.size(80.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f)){Text(post.str("title"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text("${post.long("commentCount")} comments",color=c.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
                Column(horizontalAlignment=Alignment.End){Text(if(post.long("ratingCount")==0L) "—" else String.format(java.util.Locale.US,"%.1f",post.number("rating")),fontSize=30.sp,fontWeight=FontWeight.Bold);RatingSummary(post.number("rating"),post.long("ratingCount"))}
            }
        }
        item {
            Surface(shape=RoundedCornerShape(18.dp),color=c.surfaceVariant) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(if(myRating>0) "Your rating · $myRating / 5" else "How was this recipe?",fontWeight=FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){(1..5).forEach{value->IconButton(onClick={rating=value},enabled=!busy){Icon(if(value<=(if(rating>0)rating else myRating))Icons.Default.Star else Icons.Default.StarBorder,"$value stars",tint=Color(0xFFF5B921),modifier=Modifier.size(31.dp))}}}
                    Button(onClick={vm.action("rating",post.long("id"),buildJsonObject{put("value",rating)}){rating=0}},enabled=rating in 1..5&&!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text(if(myRating>0) "Update rating" else "Submit rating")}
                }
            }
        }
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("Community comments",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);TextButton(onClick={commentId=0;text="";editor=true},enabled=!busy){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Text("Comment")}}}
        if(data.rows("comments").isEmpty()) item {EmptyCommunity("Start the conversation","Share a helpful tip or tell others how your cooking went.",Icons.Default.ChatBubbleOutline)}
        items(data.rows("comments"),key={it.long("id")}){row->
            Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface) {
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(c.primaryContainer),contentAlignment=Alignment.Center){if(row.str("avatar").isNotBlank()) AsyncImage(model=row.str("avatar"),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) else Text(row.str("author").take(1).uppercase(),fontWeight=FontWeight.Bold,color=c.primary)}
                        Column(Modifier.weight(1f)){Text(row.str("author"),fontWeight=FontWeight.Bold);Text(row.str("createdAt").take(10)+(if(row.str("createdAt")!=row.str("updatedAt"))" · edited" else ""),style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
                        if(row.str("owner")==vm.userId){IconButton(onClick={commentId=row.long("id");text=row.str("text");editor=true},enabled=!busy){Icon(Icons.Default.Edit,"Edit your comment",Modifier.size(20.dp))};IconButton(onClick={deleteId=row.long("id")},enabled=!busy){Icon(Icons.Default.DeleteOutline,"Delete your comment",Modifier.size(20.dp))}}
                    }
                    ReadableText(row.str("text"))
                    TextButton(onClick={vm.action("like",post.long("id"),buildJsonObject{put("commentId",row.long("id"));put("enabled",!row.flag("liked"))})},enabled=!busy,modifier=Modifier.align(Alignment.End)){
                        Icon(if(row.flag("liked"))Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,if(row.flag("liked")) "Unlike comment" else "Like comment",Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("${row.long("likes")}")
                    }
                }
            }
        }
    }
    if(editor) ModalBottomSheet(onDismissRequest={if(!busy)editor=false}) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(if(commentId==0L) "Write a comment" else "Edit your comment",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Ratings and comments are saved separately. You can leave more than one comment.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            OutlinedTextField(value=text,onValueChange={text=cleanInput(it,1000,6)},placeholder={Text("Share a cooking tip or your experience…")},supportingText={Text("${text.trim().length}/1000")},modifier=Modifier.fillMaxWidth(),minLines=4,maxLines=6,shape=RoundedCornerShape(14.dp))
            Button(onClick={vm.action("comment",post.long("id"),buildJsonObject{put("commentId",commentId);put("text",text.trim());put("requestId",commentRequest)}){editor=false;text="";commentRequest=UUID.randomUUID().toString()}},enabled=text.trim().isNotEmpty()&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(12.dp)){Text(if(commentId==0L) "Post comment" else "Save comment")}
        }
    }
    if(deleteId>0) AlertDialog(onDismissRequest={if(!busy)deleteId=0},title={Text("Delete your comment?")},text={Text("The comment and its likes will be removed.")},confirmButton={TextButton(onClick={vm.action("delete_comment",post.long("id"),buildJsonObject{put("commentId",deleteId)}){deleteId=0}},enabled=!busy){Text("Delete")}},dismissButton={TextButton(onClick={deleteId=0},enabled=!busy){Text("Cancel")}})
}

@Composable
internal fun ReadableText(value:String) {
    val text=cleanDisplay(value)
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Column {
        if(expanded) Box(Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState())) { Text(text,style=MaterialTheme.typography.bodyMedium) }
        else Text(text,style=MaterialTheme.typography.bodyMedium,maxLines=4,overflow=TextOverflow.Ellipsis)
        if(text.length>220 || text.count{it=='\n'}>=4) TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Show less" else "Read more") }
    }
}