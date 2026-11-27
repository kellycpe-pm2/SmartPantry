package com.example.smartpantry.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smartpantry.viewmodel.CommunityViewModel
import com.example.smartpantry.Screen.CommunityScreen.cleanDisplay
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityHub(vm:CommunityViewModel,mode:String,search:String,onSearch:(String)->Unit,category:String,onCategory:(String)->Unit,
 filters:JsonObject,onFilters:(JsonObject)->Unit,onMode:(String)->Unit,onOpenPost:(Long)->Unit,onOpenPersonal:(Long)->Unit,
 onEditPost:(JsonObject)->Unit,onEditRecipe:(JsonObject)->Unit,onRemovePost:(JsonObject)->Unit,onCreate:()->Unit,
 onEditPersonal:(Long)->Unit,onPublish:(Long,String)->Unit,onDeletePersonal:(Long)->Unit,listState:LazyListState) {
 val posts by vm.posts.collectAsState();val mine by vm.mine.collectAsState();val recs by vm.recommendations.collectAsState()
 val loading by vm.loading.collectAsState();val recLoading by vm.recommendationLoading.collectAsState();val failed by vm.feedFailed.collectAsState()
 val recFailed by vm.recommendationFailed.collectAsState();val pantryEmpty by vm.pantryEmpty.collectAsState();val busy by vm.busy.collectAsState()
 val more by vm.more.collectAsState();val recMore by vm.recommendationMore.collectAsState()
 var collection by remember { mutableStateOf(false) };var filterOpen by remember { mutableStateOf(false) }
 var deleteTarget by remember { mutableStateOf<JsonObject?>(null) }
 val c=MaterialTheme.colorScheme
 fun open(row:JsonObject) { if(row.flag("published"))onOpenPost(row.long("id")) else onOpenPersonal(row.long("recipeId")) }
 LaunchedEffect(mode,search) { if(mode=="for_me")vm.loadRecommendations(search) }
 Box(Modifier.fillMaxSize()) {
  PullToRefreshBox(isRefreshing=loading || recLoading,onRefresh={vm.feed(if(mode=="saved")"saved" else "feed",search,category,keepVisible=true,filters=filters);vm.loadRecommendations(if(mode=="for_me")search else "")}) {
   LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(18.dp,4.dp,18.dp,154.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    item {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      if(mode!="post") TextButton(onClick={onMode("post")}){Icon(Icons.Default.ArrowBack,"Back to community");Text("Community")}
      Spacer(Modifier.weight(1f))
      Box { IconButton(onClick={collection=true}){Icon(Icons.Default.Bookmarks,"Favourites and my recipes",tint=c.primary)}
       DropdownMenu(expanded=collection,onDismissRequest={collection=false}) {
        DropdownMenuItem(text={Text("Favourites")},leadingIcon={Icon(Icons.Default.BookmarkBorder,null)},onClick={collection=false;onMode("saved")})
        DropdownMenuItem(text={Text("My recipes")},leadingIcon={Icon(Icons.Default.FolderOpen,null)},onClick={collection=false;onMode("personal")})
       }
      }
     }
     OutlinedTextField(search,{onSearch(it.take(80))},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text(when(mode){"saved"->"Search favourites";"personal"->"Search my recipes";"for_me"->"Search pantry recommendations";else->"Search community recipes"})},
      leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(mode=="post") IconButton(onClick={filterOpen=true}){Icon(Icons.Default.Tune,"Filter recipes")} else if(search.isNotBlank())IconButton(onClick={onSearch("")}){Icon(Icons.Default.Close,"Clear search")}},shape=RoundedCornerShape(16.dp))
    }
    if(mode=="post") item {
     Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      listOf("All","Malaysian","Asian","Vegan").forEach { label->FilterChip(selected=category==label && (label!="All" || filters.isEmpty()),onClick={onCategory(label)},label={Text(label)}) }
     }
     if(filters.isNotEmpty()) Text("Filters: ${filters.entries.filter { it.key!="ingredientId" }.joinToString(" · "){it.value.jsonPrimitive.content}}${if(filters.long("ingredientId")>0)" · Selected ingredient" else ""}",style=MaterialTheme.typography.bodySmall,color=c.primary)
    }
    if(mode=="post" || mode=="for_me") {
     item {
      Row(verticalAlignment=Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) { Text("From your pantry",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("Use what you have before it expires.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant) }
       if(mode=="post")TextButton(onClick={onMode("for_me")}){Text("See all")}
      }
     }
     when {
      recLoading && recs.isEmpty()->item { LinearProgressIndicator(Modifier.fillMaxWidth());Text("Checking pantry quantities and recipe ingredients…",style=MaterialTheme.typography.bodySmall) }
      recFailed->item { HubNotice("Recommendations could not be refreshed. Check your connection and pull down to retry. Previously loaded results may be out of date.") }
      recs.isEmpty()->item { HubNotice(if(pantryEmpty)"There is no available stock synced to the server. Add or sync stock in Pantry, then pull down to refresh." else if(search.isNotBlank() && mode=="for_me")"No pantry recommendations match this search. Try another recipe name." else "No saved or published recipes match your available stock. Browse the community or add a recipe with ingredients.") }
     }
     if(recs.isNotEmpty()) {
      if(mode=="post")item {
       Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        recs.take(6).forEach { row->Surface(Modifier.width(210.dp).clickable { open(row) },shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface) {
         Column { RecipePhoto(row.str("image"),Modifier.fillMaxWidth().height(100.dp));Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
          Text(row.str("title"),fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
          Text("${row.long("covered")}/${row.long("required")} ingredients fully available",style=MaterialTheme.typography.labelMedium,color=c.primary)
          if(row.flag("useSoon"))Text("Use stock by ${row.str("soonDate")}",style=MaterialTheme.typography.labelSmall,color=c.primary)
         } }
        } }
       }
      } else {
       items(recs,key={"recommend_${it.long("recipeId")}"}) { row->HubPost(row,vm.userId,busy,{open(row)},{vm.action("favourite",row.long("id"),buildJsonObject{put("enabled",!row.flag("favourite"))}){vm.loadRecommendations(search)}},{onEditPost(row)},{onEditRecipe(row)},{onRemovePost(row)}) }
       if(recMore)item { OutlinedButton(onClick={vm.loadRecommendations(search,true)},enabled=!recLoading,modifier=Modifier.fillMaxWidth()){Text("Load more recommendations")} }
      }
     }
    }
    if(mode=="post" || mode=="saved") {
     if(mode=="post")item{Text("Community posts",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
     if(failed)item { HubNotice("Recipes could not be refreshed. Check your connection and pull down to retry. Your saved forms are unchanged.") }
     if(posts.isEmpty() && !loading && !failed)item { HubNotice(if(mode=="saved")"No favourites match this search. Save a community recipe using its bookmark." else if(search.isNotBlank() || filters.isNotEmpty() || category!="All")"No published recipes match your search and filters. Try Reset in the filter panel." else "No recipes have been published yet. Tap + to create your first recipe.") }
     items(posts,key={"post_${it.long("id")}"}) { row->HubPost(row,vm.userId,busy,{onOpenPost(row.long("id"))},
      {vm.action("favourite",row.long("id"),buildJsonObject { put("enabled",!row.flag("favourite")) }) { if(mode=="saved")vm.feed("saved",search,category,keepVisible=true,filters=filters) }},
      {onEditPost(row)},{onEditRecipe(row)},{onRemovePost(row)}) }
     if(more)item { OutlinedButton(onClick={vm.feed(if(mode=="saved")"saved" else "feed",search,category,true,filters=filters)},enabled=!loading,modifier=Modifier.fillMaxWidth()){Text("Load more recipes")} }
    }
    if(mode=="personal") {
     val rows=mine.filter { search.isBlank() || it.str("title").contains(search,true) }
     if(rows.isEmpty() && !loading)item { HubNotice("No personal recipes match. Tap + to create a recipe, then publish it when you are ready.") }
     items(rows,key={"mine_${it.long("id")}"}) { row->Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface) {
      Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
       Row(verticalAlignment=Alignment.CenterVertically) {
        RecipePhoto(row.str("image"),Modifier.size(70.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f).padding(horizontal=10.dp)) { Text(row.str("title"),fontWeight=FontWeight.Bold);Text(if(row.flag("published"))"Published" else "Private recipe",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant) }
        IconButton(onClick={onEditPersonal(row.long("id"))},enabled=!busy){Icon(Icons.Default.Edit,"Edit recipe")}
       }
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
        if(row.flag("published"))TextButton(onClick={onOpenPost(row.long("postId"))}){Text("View post")}
        else TextButton(onClick={onPublish(row.long("id"),row.str("title"))},enabled=!busy){Text("Publish")}
        TextButton(onClick={deleteTarget=row},enabled=!busy){Text("Delete recipe",color=c.error)}
       }
      }
     } }
    }
   }
  }
  FloatingActionButton(onClick=onCreate,modifier=Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=104.dp),containerColor=c.primary,contentColor=c.onPrimary,shape=CircleShape){Icon(Icons.Default.Add,"Create recipe")}
 }
 deleteTarget?.let { row->AlertDialog(onDismissRequest={deleteTarget=null},title={Text("Delete ${row.str("title")}?")},
  text={Text("This permanently deletes your recipe and any linked community post, comments, ratings and favourites. Remove only the post instead if you want to keep the recipe.")},
  confirmButton={TextButton(onClick={deleteTarget=null;onDeletePersonal(row.long("id"))},enabled=!busy){Text("Delete recipe",color=c.error)}},dismissButton={TextButton(onClick={deleteTarget=null}){Text("Cancel")}}) }
 if(filterOpen)RecipeFilterSheet(vm,filters,onDismiss={filterOpen=false},onApply={onFilters(it);filterOpen=false})
}

@Composable private fun HubNotice(message:String) { Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) { Text(message,Modifier.fillMaxWidth().padding(14.dp),style=MaterialTheme.typography.bodyMedium) } }

@Composable private fun HubPost(row:JsonObject,userId:String,busy:Boolean,onOpen:()->Unit,onFavourite:()->Unit,onEditPost:()->Unit,onEditRecipe:()->Unit,onRemove:()->Unit) {
 val c=MaterialTheme.colorScheme;var options by remember { mutableStateOf(false) }
 Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface) {
  Column {
   Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    if(row.str("avatar").isNotBlank())RecipePhoto(row.str("avatar"),Modifier.size(34.dp).clip(CircleShape),false)
    else Surface(shape=CircleShape,color=c.primaryContainer){Text(row.str("author","Member").take(1).uppercase(),Modifier.padding(10.dp),fontWeight=FontWeight.Bold,color=c.onPrimaryContainer)}
    Column(Modifier.weight(1f)) {Text(if(row.str("owner")==userId)"You" else row.str("author","Member"),style=MaterialTheme.typography.labelLarge);Text(if(row.flag("published"))row.str("createdAt").take(10) else "Private recipe",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
    if(row.str("owner")==userId && row.flag("published")) {
     IconButton(onClick=onEditPost,enabled=!busy){Icon(Icons.Default.Edit,"Edit post")}
     Box {IconButton(onClick={options=true}){Icon(Icons.Default.MoreVert,"Post options")}
      DropdownMenu(expanded=options,onDismissRequest={options=false}){
       DropdownMenuItem(text={Text("Edit recipe")},onClick={options=false;onEditRecipe()})
       DropdownMenuItem(text={Text("Remove post",color=c.error)},onClick={options=false;onRemove()},leadingIcon={Icon(Icons.Default.DeleteOutline,null,tint=c.error)})
      }
     }
    }
   }
   Box { RecipePhoto(row.str("image"),Modifier.fillMaxWidth().height(if(row.flag("forYou"))140.dp else 168.dp).clickable(onClick=onOpen))
    if(row.flag("published"))Surface(Modifier.align(Alignment.TopEnd).padding(8.dp),shape=RoundedCornerShape(12.dp),color=c.surface){IconButton(onClick=onFavourite,enabled=!busy){Icon(if(row.flag("favourite"))Icons.Default.Bookmark else Icons.Default.BookmarkBorder,if(row.flag("favourite"))"Remove favourite" else "Add favourite",tint=c.primary)}}
   }
   Column(Modifier.fillMaxWidth().clickable(onClick=onOpen).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Text(row.str("title"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
    if(row.flag("published"))Text("★ ${quantityText(row.number("rating"))} (${row.long("ratingCount")} ratings)",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
    if(row.str("caption").isNotBlank())Text(cleanDisplay(row.str("caption")),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
    if(row.flag("forYou")) {
     Text(if(row.flag("allAvailable"))"${row.long("covered")}/${row.long("required")} ingredients fully available" else "${row.long("covered")}/${row.long("required")} fully available · ${row.long("missing")} need topping up",fontWeight=FontWeight.SemiBold,color=c.primary,style=MaterialTheme.typography.bodySmall)
     if(row.words("missingNames").isNotEmpty())Text("Check quantities: ${row.words("missingNames").joinToString()}",style=MaterialTheme.typography.bodySmall)
     if(row.flag("useSoon"))Text("Use pantry stock by ${row.str("soonDate")}. Check that you can cook it in time.",style=MaterialTheme.typography.bodySmall,color=c.primary)
    }
   }
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RecipeFilterSheet(vm:CommunityViewModel,initial:JsonObject,onDismiss:()->Unit,onApply:(JsonObject)->Unit) {
 var cuisine by rememberSaveable { mutableStateOf(initial.str("cuisine","Any")) };var dish by rememberSaveable { mutableStateOf(initial.str("dishType","Any")) }
 var diet by rememberSaveable { mutableStateOf(initial.str("dietary","Any")) };var ingredient by rememberSaveable { mutableLongStateOf(initial.long("ingredientId")) }
 var query by rememberSaveable { mutableStateOf("") };val catalog by vm.catalog.collectAsState()
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(rememberScrollState()).imePadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   Text("Filter recipes",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
   FilterGroup("Cuisine",listOf("Any","Malaysian","Chinese","Asian","Western","Other"),cuisine){cuisine=it}
   FilterGroup("Dish type",listOf("Any","Rice dishes","Noodles","Light meals","Desserts","Other"),dish){dish=it}
   FilterGroup("Dietary preference",listOf("Any","Vegetarian","Vegan"),diet){diet=it}
   Text("Main ingredient",fontWeight=FontWeight.SemiBold)
   OutlinedTextField(query,{query=it.take(50)},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search ingredients")},leadingIcon={Icon(Icons.Default.Search,null)})
   if(ingredient>0)TextButton(onClick={ingredient=0}){Text("Clear selected ingredient")}
   if(query.isNotBlank() && catalog.none { it.str("name").contains(query,true) })Text(if(catalog.isEmpty())"Ingredients have not loaded. Close this panel and pull down to refresh." else "No ingredients match. Try another name.",style=MaterialTheme.typography.bodySmall)
   if(query.isNotBlank())catalog.filter { it.str("name").contains(query,true) }.take(12).forEach { row->FilterChip(selected=ingredient==row.long("id"),onClick={ingredient=if(ingredient==row.long("id"))0 else row.long("id")},label={Text(row.str("name"))}) }
   Text("Sections combine: for example Malaysian + Rice dishes + Vegan. Recipes without these recorded classifications will not match.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Row(Modifier.fillMaxWidth().padding(bottom=24.dp).navigationBarsPadding(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
    OutlinedButton(onClick={cuisine="Any";dish="Any";diet="Any";ingredient=0;query=""},modifier=Modifier.weight(1f)){Text("Reset")}
    Button(onClick={onApply(buildJsonObject {if(cuisine!="Any")put("cuisine",cuisine);if(dish!="Any")put("dishType",dish);if(diet!="Any")put("dietary",diet);if(ingredient>0)put("ingredientId",ingredient)})},modifier=Modifier.weight(2f)){Text("Apply filters")}
   }
  }
 }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FilterGroup(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
 Column { Text(label,fontWeight=FontWeight.SemiBold);FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) { options.forEach { value->FilterChip(selected=value==selected,onClick={onSelect(value)},label={Text(value)}) } } }
}
