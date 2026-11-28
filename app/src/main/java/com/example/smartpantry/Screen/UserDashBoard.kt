package com.example.smartpantry.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.R
import com.example.smartpantry.community.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun displayAmount(q:Double,u:String):String {
 val unit=normalizedUnit(u)
 return if(unit=="g"&&q>=1000)"${quantityText(q/1000)} kg" else if(unit=="mL"&&q>=1000)"${quantityText(q/1000)} L" else "${quantityText(q)} $unit"
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun UserDashboardScreen(onOpenCommunityPost:(Long)->Unit={},onShoppingList:()->Unit={},onPantry:()->Unit={},onRecipes:()->Unit={},onRecords:()->Unit={},
 onIngredient:(Long)->Unit={},onPantryBatch:(Long,Long)->Unit={id,_->onIngredient(id)},onCreateIngredient:()->Unit={},onAddPantry:()->Unit={},onCreateRecipe:()->Unit={},userName:String="",onProfile:()->Unit={}) {
 val vm:DashboardViewModel=viewModel();val data by vm.data.collectAsState();val loading by vm.loading.collectAsState();val message by vm.message.collectAsState()
 val recommendations by vm.recommendations.collectAsState();val recError by vm.recommendationError.collectAsState()
 val zone=ZoneId.of("Asia/Kuala_Lumpur")
 var now by remember{mutableStateOf(java.time.LocalDateTime.now(zone))}
 LaunchedEffect(Unit){while(true){now=java.time.LocalDateTime.now(zone);kotlinx.coroutines.delay(60000)}}
 val today=now.toLocalDate();val mealSlot=currentMealType(now.hour);val c=MaterialTheme.colorScheme
 val pantry=data.rows("pantry");val soon=pantry.filter{it.str("expiry")>=today.toString()&&it.str("expiry")<=today.plusDays(3).toString()}.sortedBy{it.str("expiry")}.take(3)
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 var rescueIndex by rememberSaveable{mutableIntStateOf(0)}
 LaunchedEffect(mealSlot,today){rescueIndex=0;vm.refresh()}
 val ranked=homeRescueCandidates(recommendations,mealSlot)
 val rescue=ranked.getOrNull(if(ranked.isEmpty())0 else rescueIndex.mod(ranked.size))
 fun openRecipe(row:kotlinx.serialization.json.JsonObject){onOpenCommunityPost(if(row.flag("published"))row.long("id") else -row.long("recipeId"))}
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_RESUME){now=java.time.LocalDateTime.now(zone);vm.refresh()}};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
 val weekTotal=data.long("weekTotal");val saved=if(weekTotal>0)(100.0-data.number("wasteRate")).coerceIn(0.0,100.0) else 0.0
 Box(Modifier.fillMaxSize()) {
  PullToRefreshBox(isRefreshing=loading,onRefresh={vm.refresh()}) {
   LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,8.dp,18.dp,158.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
    item {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Image(painterResource(R.drawable.smartpantry_logo_horizontal),"SmartPantry",Modifier.weight(1f).height(36.dp),alignment=Alignment.CenterStart,contentScale=ContentScale.Fit)
      Surface(Modifier.size(40.dp).clickable(onClick=onProfile),shape=CircleShape,color=c.primaryContainer){Box(contentAlignment=Alignment.Center){Text(userName.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1)}.uppercase().ifBlank{"You"},fontWeight=FontWeight.Bold,color=c.primary)}}
     }
     Spacer(Modifier.height(12.dp))
     Text(today.format(DateTimeFormatter.ofPattern("EEEE · d MMM",Locale.ENGLISH)).uppercase(),style=MaterialTheme.typography.labelSmall,color=c.primary,fontWeight=FontWeight.Bold)
     val greeting=when(now.hour){in 5..11->"Good morning";in 12..17->"Good afternoon";else->"Good evening"}
     Text("$greeting, ${userName.trim().ifBlank{"there"}}.",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
    }
    message?.let{item{Text(it,color=c.error,style=MaterialTheme.typography.bodySmall)}}
    item {
     Surface(Modifier.fillMaxWidth().clickable(onClick=onRecords),shape=RoundedCornerShape(24.dp),shadowElevation=3.dp){
      Row(Modifier.background(Brush.linearGradient(listOf(Color(0xFF235F62),Color(0xFF3D8C90)))).padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
       Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Surface(shape=RoundedCornerShape(20.dp),color=Color.White.copy(alpha=.12f)){Text("Weekly Waste Rate",Modifier.padding(8.dp,4.dp),style=MaterialTheme.typography.labelSmall,color=Color(0xFFB8E7E7),fontWeight=FontWeight.Bold)}
        Text(if(data.isEmpty())if(loading)"Loading…" else "Unavailable" else String.format(Locale.US,"%.1f%%",data.number("wasteRate")),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold,color=Color.White)
        Text(if(weekTotal>0)"${data.long("weekCount")} wasted / $weekTotal recorded entries" else if(data.isEmpty())"Pull down to retry" else if(data.containsKey("weekTotal"))"No entries recorded this week" else "Saved percentage unavailable",style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.85f))
        Text("${soon.size} items to use soon · Food Log",style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.85f))
       }
       Box(Modifier.size(78.dp),contentAlignment=Alignment.Center){
        CircularProgressIndicator(progress={if(weekTotal>0)(saved/100).toFloat() else 0f},modifier=Modifier.fillMaxSize(),color=Color(0xFFB8E7E7),trackColor=Color.White.copy(alpha=.18f),strokeWidth=6.dp)
        Column(horizontalAlignment=Alignment.CenterHorizontally){Text(if(weekTotal>0)String.format(Locale.US,"%.0f%%",saved) else "—",fontWeight=FontWeight.Bold,color=Color.White);Text("SAVED",style=MaterialTheme.typography.labelSmall,color=Color(0xFFB8E7E7))}
       }
      }
     }
    }
    item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
     HomeMetricTile("My Pantry",if(data.isEmpty())"Open pantry" else "${pantry.map{it.long("ingredientId")}.filter{it>0}.distinct().size} items · ${pantry.size} batches",Icons.Default.Inventory2,Modifier.weight(1f),onPantry)
     HomeMetricTile("Shopping List",if(data.isEmpty())"Open your list" else "${data.long("toBuy")} to buy · ${data.long("purchased")} done",Icons.Default.ShoppingCart,Modifier.weight(1f),onShoppingList)
    }}
    if(rescue!=null)item{
     Surface(shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFF83D1D1)),color=c.surfaceContainerLow){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.LocalFireDepartment,null,Modifier.size(20.dp),tint=Color(0xFFE89B25));Spacer(Modifier.width(6.dp));Text(rescueMealHeading(mealSlot,rescue.words("mealTypes")),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
       TextButton(onClick={rescueIndex=(rescueIndex+1).mod(ranked.size)},enabled=ranked.size>1,contentPadding=PaddingValues(4.dp)){Icon(Icons.Default.Shuffle,null,Modifier.size(16.dp));Spacer(Modifier.width(4.dp));Text("Shuffle",style=MaterialTheme.typography.labelSmall)}
      }
      Surface(Modifier.fillMaxWidth().clickable{openRecipe(rescue)},shape=RoundedCornerShape(12.dp),color=c.surface,border=BorderStroke(1.dp,c.outlineVariant)){
       Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        RecipePhoto(rescue.str("image"),Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(rescue.str("title"),fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis);Text(if(rescue.flag("useSoon"))"Uses ${rescue.long("soonCount").coerceAtLeast(1)} expiring ingredient(s)" else pantryMatchLabel(rescue),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
        Icon(Icons.Default.ChevronRight,"Open recipe",Modifier.size(20.dp),tint=c.primary)
       }
      }
     }}
    }
    item{HomeSectionTitle("Use these first",onPantry)}
    if(soon.isEmpty())item{Text(if(data.isEmpty())"Pull down to load your pantry stock." else "No available stock expires within 3 days.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
    items(soon,key={it.long("id")}){row->Surface(Modifier.fillMaxWidth().clickable{onPantryBatch(row.long("ingredientId"),row.long("id"))},shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface){
     Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
      RecipePhoto(row.str("image").ifBlank{row.str("name").lowercase()},Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),false)
      Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
       Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        Text(row.str("name","Ingredient"),Modifier.weight(1f),fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
        val days=java.time.temporal.ChronoUnit.DAYS.between(today,LocalDate.parse(row.str("expiry")))
        Surface(shape=RoundedCornerShape(6.dp),color=Color(0xFFFFF1BA)){Text(when(days){0L->"Expires today";1L->"Tomorrow";else->"In $days days"},Modifier.padding(5.dp,3.dp),style=MaterialTheme.typography.labelSmall,color=Color(0xFF76520C))}
       }
       Text(displayAmount(row.number("quantity"),row.str("unit")),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
       val remaining=runCatching{val purchase=LocalDate.parse(row.str("purchaseDate"));val expiry=LocalDate.parse(row.str("expiry"));val span=java.time.temporal.ChronoUnit.DAYS.between(purchase,expiry);if(span<=0)null else (java.time.temporal.ChronoUnit.DAYS.between(today,expiry).toFloat()/span).coerceIn(0f,1f)}.getOrNull()
       if(remaining!=null)LinearProgressIndicator(progress={remaining},modifier=Modifier.fillMaxWidth().height(4.dp),color=Color(0xFFE0B744),trackColor=c.surfaceContainerLow)

      }
      Icon(Icons.Default.ChevronRight,"View batch",Modifier.size(20.dp),tint=c.primary)
     }
    }}
    item{HomeSectionTitle("Recommended recipes",onRecipes)}
    if(recError!=null && recommendations.isNotEmpty())item{Text(recError+" Showing saved recommendations; stock may have changed.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
    if(recommendations.isEmpty())item{Text(recError?:"No recipes match your usable pantry stock yet.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
    else item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)){ranked.take(10).forEach{row->PantryImageCard(row,Modifier.width(176.dp)){openRecipe(row)}}}}
    item{CommunityDiscover(onOpenCommunityPost,onRecipes)}
   }
  }
  ContextAddButton(Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=104.dp),onCreateRecipe,onCreateIngredient,onAddPantry)
 }
}
@Composable private fun HomeMetricTile(title:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,onClick:()->Unit){
 val c=MaterialTheme.colorScheme
 Surface(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surfaceContainerLow){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(10.dp),color=c.surface){Icon(icon,null,Modifier.padding(8.dp).size(20.dp),tint=c.primary)};Spacer(Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null,Modifier.size(18.dp),tint=c.onSurfaceVariant)}
  Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall);Text(value,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
 }}
}
@Composable internal fun HomeSectionTitle(title:String,onSeeAll:()->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);TextButton(onClick=onSeeAll,contentPadding=PaddingValues(start=8.dp,end=0.dp)){Text("See all",style=MaterialTheme.typography.labelLarge)}}}
