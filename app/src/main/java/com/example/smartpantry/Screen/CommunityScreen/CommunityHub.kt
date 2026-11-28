package com.example.smartpantry.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
 var publication by rememberSaveable {mutableStateOf("All")}
 var filterOpen by remember { mutableStateOf(false) }
 var showTools by remember { mutableStateOf(true) }
 LaunchedEffect(listState) {
  var previousIndex=0;var previousOffset=0
  snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.distinctUntilChanged().collect { (index,offset)->
   if(index==0 && offset<24)showTools=true
   else if(index!=previousIndex || kotlin.math.abs(offset-previousOffset)>6)showTools=index<previousIndex || (index==previousIndex && offset<previousOffset)
   previousIndex=index;previousOffset=offset
  }
 }
 val snackbar=remember{SnackbarHostState()};val scope=rememberCoroutineScope()
 val c=MaterialTheme.colorScheme
 fun open(row:JsonObject) { if(row.flag("published"))onOpenPost(row.long("id")) else onOpenPersonal(row.long("recipeId")) }
 LaunchedEffect(mode,search,filters) { if(mode=="post" || mode=="for_me")vm.loadRecommendations(if(mode=="for_me")search else "",mealTypes=filters.words("mealTypes")) }
 Box(Modifier.fillMaxSize()) {
  Column {
   AnimatedVisibility(visible=showTools || mode!="post") {
    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=4.dp)) {
     Row(verticalAlignment=Alignment.CenterVertically) {
     OutlinedTextField(search,{onSearch(it.take(80))},Modifier.weight(1f),singleLine=true,placeholder={Text("Search recipes or ingredients")},
      leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={Row(verticalAlignment=Alignment.CenterVertically){if(search.isNotBlank())IconButton(onClick={onSearch("")}){Icon(Icons.Default.Close,"Clear search")};if(mode in setOf("post","saved"))IconButton(onClick={filterOpen=true}){Icon(Icons.Default.Tune,if(mode=="saved")"Filter favourites" else "Filter recipes",tint=c.primary)}}},shape=RoundedCornerShape(12.dp),textStyle=MaterialTheme.typography.bodySmall)
     }
     if(mode=="post") Row(verticalAlignment=Alignment.CenterVertically) {
      Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
       listOf("All","Malaysian","Malay","Chinese","Indian").forEach { label->FilterChip(selected=category==label && (label!="All" || filters.isEmpty()),onClick={onCategory(label)},label={Text(label)}) }
      }
     }
     if(mode=="personal")Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      listOf("All","Published","Private").forEach{label->FilterChip(selected=publication==label,onClick={publication=label},label={Text(label)})}
     }
     if(mode in setOf("post","saved") && filters.isNotEmpty())Text("Filters: "+filters.entries.joinToString(" · "){if(it.value is JsonArray)it.value.jsonArray.map{v->v.jsonPrimitive.content}.joinToString(", ") else it.value.jsonPrimitive.content},style=MaterialTheme.typography.labelSmall,color=c.primary)
    }
   }
   PullToRefreshBox(isRefreshing=loading || recLoading,onRefresh={vm.feed(if(mode=="saved")"saved" else "feed",search,category,keepVisible=true,filters=filters);vm.loadRecommendations(if(mode=="for_me")search else "",mealTypes=filters.words("mealTypes"))}) {
   LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(18.dp,4.dp,18.dp,154.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    if(mode=="post" || mode=="for_me") {
     item {
      Row(verticalAlignment=Alignment.CenterVertically) {
       Text("From your pantry",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
       if(mode=="post")TextButton(onClick={onMode("for_me")}){Text("See all")}
      }
     }
     when {
      recLoading && recs.isEmpty()->item { LinearProgressIndicator(Modifier.fillMaxWidth());Text("Checking pantry quantities and recipe ingredients…",style=MaterialTheme.typography.bodySmall) }
      recFailed->item { HubNotice("Recommendations could not be refreshed. Check your connection and pull down to retry. Previously loaded results may be out of date.") }
      recs.isEmpty()->item { HubNotice(if(pantryEmpty)"There is no available stock synced to the server. Add or sync stock in Pantry, then pull down to refresh." else if(filters.words("mealTypes").isNotEmpty())"No pantry recommendations match the selected meal types. Clear the Meal type filter or add meal types to your recipes." else if(search.isNotBlank() && mode=="for_me")"No pantry recommendations match this search. Try another recipe name." else "No saved or published recipes match your available stock. Browse the community or add a recipe with ingredients.") }
     }
     if(recs.isNotEmpty()) {
      if(mode=="post")item {
       Row(Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        pantryCarousel(recs).forEach { row->PantryImageCard(row,Modifier.width(176.dp)){open(row)} }

       }
      } else {
       items(recs,key={"recommend_${it.long("recipeId")}"}) { row->HubPost(row,vm.userId,busy,{open(row)},{vm.action("favourite",row.long("id"),buildJsonObject{put("enabled",!row.flag("favourite"))}){vm.loadRecommendations(search)}},{onEditPost(row)},{onEditRecipe(row)},{onRemovePost(row)},compact=mode=="saved") }
       if(recMore)item { OutlinedButton(onClick={vm.loadRecommendations(search,true)},enabled=!recLoading,modifier=Modifier.fillMaxWidth()){Text("Load more recommendations")} }
      }
     }
    }
    if(mode=="post" || mode=="saved") {
     if(mode=="post")item{Text("Community posts",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
     if(failed)item { HubNotice("Recipes could not be refreshed. Check your connection and pull down to retry. Your saved forms are unchanged.") }
     if(posts.isEmpty() && !loading && !failed)item { HubNotice(if(mode=="saved")"No favourites match this search. Save a community recipe using its bookmark." else if(search.isNotBlank() || filters.isNotEmpty() || category!="All")"No published recipes match your search and filters. Try Reset in the filter panel." else "No recipes have been published yet. Tap + to create your first recipe.") }
     items(posts,key={"post_${it.long("id")}"}) { row->HubPost(row,vm.userId,busy,{onOpenPost(row.long("id"))},
      {vm.action("favourite",row.long("id"),buildJsonObject { put("enabled",!row.flag("favourite")) }) {
       if(mode=="saved")vm.feed("saved",search,category,keepVisible=true,filters=filters)
       if(row.flag("favourite"))scope.launch{if(snackbar.showSnackbar("Removed from favourites","Undo")==SnackbarResult.ActionPerformed)vm.action("favourite",row.long("id"),buildJsonObject{put("enabled",true)}){if(mode=="saved")vm.feed("saved",search,category,keepVisible=true,filters=filters)}}
      }},
      {onEditPost(row)},{onEditRecipe(row)},{onRemovePost(row)},compact=mode=="saved") }
     if(more)item { OutlinedButton(onClick={vm.feed(if(mode=="saved")"saved" else "feed",search,category,true,filters=filters)},enabled=!loading,modifier=Modifier.fillMaxWidth()){Text("Load more recipes")} }
    }
    if(mode=="personal") {
     val rows=mine.filter{publication=="All" || (publication=="Published" && it.flag("published")) || (publication=="Private" && !it.flag("published"))}.filter { search.isBlank() || it.str("title").contains(search,true) || it.words("ingredientNames").any{name->name.contains(search,true)} }
     if(rows.isEmpty() && !loading)item { HubNotice("No personal recipes match. Tap + to create a recipe, then publish it when you are ready.") }
     items(rows,key={"mine_${it.long("id")}"}) { row->
      val card=buildJsonObject { row.forEach{(k,v)->put(k,v)};put("id",row.long("postId"));put("recipeId",row.long("id"));put("author","You");put("owner",vm.userId);put("showFavourite",false) }
      HubPost(card,vm.userId,busy,{if(row.flag("published"))onOpenPost(row.long("postId")) else onOpenPersonal(row.long("id"))},{},{},{},{},compact=true)
     }

    }
   }
  }
  }
  SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(bottom=110.dp))
  FloatingActionButton(onClick=onCreate,modifier=Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=104.dp),containerColor=c.primary,contentColor=c.onPrimary,shape=CircleShape){Icon(Icons.Default.Add,"Create recipe")}
 }
 if(filterOpen)RecipeFilterSheet(vm,filters,onDismiss={filterOpen=false},onApply={onFilters(it);filterOpen=false})
}

@Composable private fun HubNotice(message:String) { Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) { Text(message,Modifier.fillMaxWidth().padding(14.dp),style=MaterialTheme.typography.bodyMedium) } }

internal fun pantryCarousel(rows:List<JsonObject>):List<JsonObject> {
 val urgent=rows.filter{it.flag("useSoon")};val normal=rows.filterNot{it.flag("useSoon")}
 val first=urgent.take(5)+normal.take(5)
 return (first+urgent.drop(5)+normal.drop(5)).distinctBy{it.long("recipeId")}.take(10)
}
@Composable private fun RecommendationLabel(row:JsonObject) {
 val c=MaterialTheme.colorScheme
 if(row.flag("useSoon")) {
  Surface(shape=RoundedCornerShape(7.dp),color=androidx.compose.ui.graphics.Color(0xFFFFF1BA)) {
   Text("Use up ${row.long("soonCount").coerceAtLeast(1)} ingredient${if(row.long("soonCount")==1L)"" else "s"} expiring soon",Modifier.padding(6.dp,3.dp),style=MaterialTheme.typography.labelMedium,color=androidx.compose.ui.graphics.Color(0xFF76520C),maxLines=2)
  }
  Text(row.words("soonNames").joinToString(" · "),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
 } else Text("${if(row.long("required")>0)row.long("covered")*100/row.long("required") else 0}% pantry match",style=MaterialTheme.typography.labelMedium,color=c.primary)
}
@Composable private fun HubPost(row:JsonObject,userId:String,busy:Boolean,onOpen:()->Unit,onFavourite:()->Unit,onEditPost:()->Unit,onEditRecipe:()->Unit,onRemove:()->Unit,compact:Boolean=false) {
 val c=MaterialTheme.colorScheme
 if(compact){
  Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
   RecipePhoto(row.str("image"),Modifier.size(78.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick=onOpen))
   Column(Modifier.weight(1f).clickable(onClick=onOpen),verticalArrangement=Arrangement.spacedBy(4.dp)){
    Text(row.str("title"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
    Text(row.str("description").ifBlank{row.str("caption")},style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis,color=c.onSurfaceVariant)
    Text(String.format(java.util.Locale.US,"★ %.1f",row.number("rating"))+" · "+row.str("author","You"),style=MaterialTheme.typography.labelSmall,color=c.primary)
   }
   if(row["showFavourite"]?.jsonPrimitive?.booleanOrNull!=false && row.flag("published"))IconButton(onClick=onFavourite,enabled=!busy){Icon(Icons.Default.Bookmark,"Remove favourite",tint=c.primary)}
   else Surface(shape=RoundedCornerShape(8.dp),color=c.surfaceContainerLow){Text(if(row.flag("published"))"Published" else "Private",Modifier.padding(7.dp,4.dp),style=MaterialTheme.typography.labelSmall,color=c.primary)}
  }}
  return
 }
 Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface) {
  Column {
   Box {
    RecipePhoto(row.str("image"),Modifier.fillMaxWidth().height(164.dp).clickable(onClick=onOpen))
    if(row.flag("published") && row["showFavourite"]?.jsonPrimitive?.booleanOrNull!=false)Surface(Modifier.align(Alignment.TopEnd).padding(8.dp),shape=CircleShape,color=c.surface){IconButton(onClick=onFavourite,enabled=!busy){Icon(if(row.flag("favourite"))Icons.Default.Bookmark else Icons.Default.BookmarkBorder,if(row.flag("favourite"))"Remove favourite" else "Add favourite",tint=c.primary)}}
   }
   Column(Modifier.fillMaxWidth().clickable(onClick=onOpen).padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Text(row.str("title"),Modifier.weight(1f),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
     Text(String.format(java.util.Locale.US,"★ %.1f",row.number("rating")),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,color=c.onSurface)
    }
    val description=row.str("description").ifBlank{row.str("caption")}
    if(description.isNotBlank())Text(cleanDisplay(description),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
    if(row.flag("forYou"))RecommendationLabel(row)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Box(Modifier.size(30.dp).clip(CircleShape),contentAlignment=Alignment.Center) {
      if(row.str("avatar").isNotBlank())RecipePhoto(row.str("avatar"),Modifier.fillMaxSize(),false)
      else Surface(shape=CircleShape,color=c.primaryContainer,modifier=Modifier.fillMaxSize()){Box(contentAlignment=Alignment.Center){Text(row.str("author","Member").take(1).uppercase(),fontWeight=FontWeight.Bold)}}
     }
     Column(Modifier.weight(1f)){Text(if(row.str("owner")==userId)"You" else row.str("author","Member"),style=MaterialTheme.typography.labelMedium);Text(row.str("createdAt").take(10),style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
     if(row["showFavourite"]?.jsonPrimitive?.booleanOrNull==false)Surface(shape=RoundedCornerShape(8.dp),color=if(row.flag("published"))c.primaryContainer else c.surfaceContainerLow){Text(if(row.flag("published"))"Published" else "Private",Modifier.padding(7.dp,4.dp),style=MaterialTheme.typography.labelSmall,color=c.primary)}
    }
   }
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RecipeFilterSheet(vm:CommunityViewModel,initial:JsonObject,onDismiss:()->Unit,onApply:(JsonObject)->Unit) {
 var cuisine by rememberSaveable { mutableStateOf(initial.str("cuisine","Any")) };var dish by rememberSaveable { mutableStateOf(initial.str("dishType","Any")) }
 var meals by rememberSaveable { mutableStateOf(initial.words("mealTypes")) }
 var diet by rememberSaveable { mutableStateOf(initial.str("dietary","Any")) };var ingredient by rememberSaveable { mutableLongStateOf(initial.long("ingredientId")) }
 var query by rememberSaveable { mutableStateOf("") };val catalog by vm.catalog.collectAsState()
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(rememberScrollState()).imePadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   Text("Filter recipes",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
   FilterGroup("Cuisine",listOf("Any","Malaysian","Malay","Chinese","Indian","Asian","Western"),cuisine){cuisine=it}
   FilterGroup("Dish type",listOf("Any","Rice dishes","Noodles","Light meals","Desserts"),dish){dish=it}
   MealTypeSelector(meals,{meals=it},label="Meal type · match any selected",hint=false)
   Text("Unclassified recipes are excluded only while a meal type filter is selected.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   FilterGroup("Dietary preference",listOf("Any","Halal","Vegetarian","Vegan"),diet){diet=it}
   Text("Groups combine. Halal information is provided by the author; it is not certification.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Row(Modifier.fillMaxWidth().padding(bottom=24.dp).navigationBarsPadding(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
    OutlinedButton(onClick={cuisine="Any";dish="Any";diet="Any";meals=emptyList();ingredient=0;query=""},modifier=Modifier.weight(1f)){Text("Reset")}
    Button(onClick={onApply(buildJsonObject {if(cuisine!="Any")put("cuisine",cuisine);if(dish!="Any")put("dishType",dish);if(diet!="Any")put("dietary",diet);if(meals.isNotEmpty())put("mealTypes",JsonArray(meals.map{JsonPrimitive(it)}))})},modifier=Modifier.weight(2f)){Text("Apply filters")}
   }
  }
 }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FilterGroup(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
 Column { Text(label,fontWeight=FontWeight.SemiBold);FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) { options.forEach { value->FilterChip(selected=value==selected,onClick={onSelect(value)},label={Text(value)}) } } }
}
