package com.example.smartpantry.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
    item { SmartPantrySectionHeader("",false,null) }
    message?.let { item {Text(it,color=c.error,style=MaterialTheme.typography.bodySmall)} }
    item { Surface(Modifier.fillMaxWidth().clickable(onClick=onRecords),shape=RoundedCornerShape(22.dp)) {
     Box(Modifier.fillMaxWidth().heightIn(min=164.dp).background(Brush.horizontalGradient(listOf(Color(0xFF235F62),Color(0xFF73CBC7))))) {
      Image(painterResource(R.drawable.home_vegetables),null,Modifier.align(Alignment.TopEnd).width(154.dp).height(168.dp),contentScale=ContentScale.Crop)
      Column(Modifier.padding(start=18.dp,end=148.dp,top=18.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
       Text("This week",color=Color.White,style=MaterialTheme.typography.labelLarge)
       Text("Waste rate",fontWeight=FontWeight.Bold,color=Color.White)
       Text(String.format(java.util.Locale.US,"%.1f%%",data.number("wasteRate")),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=Color.White)
       Text("Of recorded food entries",color=Color.White.copy(alpha=.9f),style=MaterialTheme.typography.labelSmall)
      }
     }
    } }
    item { Surface(Modifier.fillMaxWidth().clickable(onClick=onPantry),shape=RoundedCornerShape(12.dp),color=c.surfaceContainerLow) {
     Row(Modifier.padding(horizontal=12.dp,vertical=6.dp).heightIn(min=36.dp),verticalAlignment=Alignment.CenterVertically) {
      Text(if(data.isEmpty())"Pantry stock unavailable" else "${pantry.map{it.long("ingredientId")}.filter{it>0}.distinct().size} ingredients · ${pantry.size} batches",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
      Icon(Icons.Default.ChevronRight,"View pantry",Modifier.size(24.dp),tint=c.primary)
     }
    } }
    item { Row(verticalAlignment=Alignment.CenterVertically){Text("Use these first",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);TextButton(onClick=onPantry){Text("See all");Icon(Icons.Default.ChevronRight,null,Modifier.size(24.dp))}} }
    if(soon.isEmpty())item { Text(if(data.isEmpty())"Pantry stock has not loaded yet. Pull down to retry." else "No available stock expires within the next 3 days.",style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant) }
    if(soon.isNotEmpty())item {
     Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
      soon.forEach { row->Surface(Modifier.width(224.dp).clickable {onPantryBatch(row.long("ingredientId"),row.long("id"))},shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface) {
       Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
         RecipePhoto(row.str("image").ifBlank{row.str("name").lowercase()},Modifier.size(42.dp).clip(CircleShape),false)
         Text(row.str("name","Ingredient"),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,fontWeight=FontWeight.Bold)
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
         Text(displayAmount(row.number("quantity"),row.str("unit"))+" ·",style=MaterialTheme.typography.labelSmall)
         val days=java.time.temporal.ChronoUnit.DAYS.between(today,LocalDate.parse(row.str("expiry")))
         Surface(shape=RoundedCornerShape(6.dp),color=Color(0xFFFFF1BA)) {
          Text(when(days){0L->"Expires today";1L->"Expires tomorrow";else->"Expires in $days days"},Modifier.padding(4.dp,2.dp),color=Color(0xFF76520C),style=MaterialTheme.typography.labelSmall)
         }
        }
       }
      } }
     }
    }
    item { Spacer(Modifier.height(3.dp));HorizontalDivider(color=c.outlineVariant);Spacer(Modifier.height(5.dp)) }
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
