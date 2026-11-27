package com.example.smartpantry.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.R
import com.example.smartpantry.community.*
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneId

internal fun displayAmount(q:Double,u:String):String {
 val unit=normalizedUnit(u)
 return if(unit=="g" && q>=1000) "${quantityText(q/1000)} kg" else if(unit=="mL" && q>=1000)"${quantityText(q/1000)} L" else "${quantityText(q)} $unit"
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDashboardScreen(onOpenCommunityPost:(Long)->Unit={},onShoppingList:()->Unit={},onPantry:()->Unit={},onRecipes:()->Unit={},onRecords:()->Unit={},
 onIngredient:(Long)->Unit={},onPantryBatch:(Long,Long)->Unit={id,_->onIngredient(id)},onCreateIngredient:()->Unit={},onAddPantry:()->Unit={},onCreateRecipe:()->Unit={},userName:String="") {
 val vm:DashboardViewModel=viewModel();val data by vm.data.collectAsState();val loading by vm.loading.collectAsState();val message by vm.message.collectAsState()
 val today=LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur"));val c=MaterialTheme.colorScheme
 val pantry=data.rows("pantry");val active=pantry.filter { it.str("expiry")>=today.toString() }
 val soon=active.filter {it.str("expiry")<=today.plusDays(3).toString()}.take(5)
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_RESUME)vm.refresh()};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
 Box(Modifier.fillMaxSize()) {
  PullToRefreshBox(isRefreshing=loading,onRefresh={vm.refresh()}) {
   LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,10.dp,20.dp,158.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    item { SmartPantrySectionHeader("",false,null);Text(if(userName.isBlank())"Welcome back" else "Hi, ${userName.substringBefore(' ')}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Make the most of what you have.",color=c.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium) }
    message?.let { item {Text(it,color=c.error,style=MaterialTheme.typography.bodySmall)} }
    item { Surface(Modifier.fillMaxWidth().clickable(onClick=onRecords),shape=RoundedCornerShape(22.dp),color=c.primaryContainer) {
     Box { Image(painterResource(R.drawable.home_vegetables),null,Modifier.align(Alignment.BottomEnd).width(110.dp))
      Column(Modifier.fillMaxWidth().padding(start=16.dp,end=112.dp,top=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
       Text("Waste this week",fontWeight=FontWeight.Bold)
       Text(if(data.isEmpty())"—" else data.rows("weekWaste").joinToString(" · "){displayAmount(it.number("quantity"),it.str("unit"))}.ifBlank{"No waste recorded"},style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=c.primary)
       Text(if(data.isEmpty())"${if(loading)"Loading" else "Unable to load"} records" else "${data.long("weekCount")} waste records",style=MaterialTheme.typography.bodySmall)
       Text("View waste log →",style=MaterialTheme.typography.labelLarge,color=c.primary)
      }
     }
    } }
    item { DashboardRow(Icons.Default.Inventory2,if(data.isEmpty())"Pantry overview" else "${pantry.map{it.long("ingredientId")}.filter{it>0}.distinct().size} ingredients · ${pantry.size} batches","View pantry",onPantry) }
    item { Row(verticalAlignment=Alignment.CenterVertically){Text("Use these first",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);TextButton(onClick=onPantry){Text("See all")}} }
    if(soon.isEmpty())item { Text(if(data.isEmpty())"Pantry stock has not loaded yet. Pull down to retry." else "No available stock expires within the next 3 days.",style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant) }
    items(soon,key={it.long("id")}) { row->Surface(Modifier.fillMaxWidth().clickable {onPantryBatch(row.long("ingredientId"),row.long("id"))},shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface) {
     Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
      RecipePhoto(row.str("image").ifBlank { row.str("name").lowercase() },Modifier.size(50.dp),false)
      Column(Modifier.weight(1f)) { Text(row.str("name","Ingredient"),fontWeight=FontWeight.Bold);Text(displayAmount(row.number("quantity"),row.str("unit")),style=MaterialTheme.typography.bodyMedium)
       val days=java.time.temporal.ChronoUnit.DAYS.between(today,LocalDate.parse(row.str("expiry")))
       Text(when(days){0L->"Expires today";1L->"Expires tomorrow";else->"Expires in $days days"},color=if(days<=1)c.error else c.primary,style=MaterialTheme.typography.labelMedium)
      };Icon(Icons.Default.ChevronRight,"View pantry batches")
     }
    } }
    item { DashboardRow(Icons.Default.ShoppingCart,"Shopping list",if(data.isEmpty())"Open your list" else "${data.long("toBuy")} to buy · ${data.long("purchased")} purchased",onShoppingList) }
    item { CommunityDiscover(onOpenCommunityPost,onRecipes) }
   }
  }
  ContextAddButton(Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=104.dp),onCreateRecipe,onCreateIngredient,onAddPantry)
 }
}
@Composable private fun DashboardRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String,onClick:()->Unit) {
 val c=MaterialTheme.colorScheme
 Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surfaceContainerLow) {
  Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Icon(icon,null,tint=c.primary);Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)};Icon(Icons.Default.ChevronRight,null)}
 }
}
