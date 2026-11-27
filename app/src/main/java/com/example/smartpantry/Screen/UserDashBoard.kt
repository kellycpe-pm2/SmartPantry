package com.example.smartpantry.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

internal fun displayAmount(q:Double,u:String):String {
 val unit=normalizedUnit(u)
 return if(unit=="g"&&q>=1000)"${quantityText(q/1000)} kg" else if(unit=="mL"&&q>=1000)"${quantityText(q/1000)} L" else "${quantityText(q)} $unit"
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun UserDashboardScreen(onOpenCommunityPost:(Long)->Unit={},onShoppingList:()->Unit={},onPantry:()->Unit={},onRecipes:()->Unit={},onRecords:()->Unit={},
 onIngredient:(Long)->Unit={},onPantryBatch:(Long,Long)->Unit={id,_->onIngredient(id)},onCreateIngredient:()->Unit={},onAddPantry:()->Unit={},onCreateRecipe:()->Unit={},userName:String="",onProfile:()->Unit={}) {
 val vm:DashboardViewModel=viewModel();val data by vm.data.collectAsState();val loading by vm.loading.collectAsState();val message by vm.message.collectAsState()
 val zone=ZoneId.of("Asia/Kuala_Lumpur");val today=LocalDate.now(zone);val c=MaterialTheme.colorScheme
 val pantry=data.rows("pantry");val soon=pantry.filter{it.str("expiry")>=today.toString()&&it.str("expiry")<=today.plusDays(3).toString()}.sortedBy{it.str("expiry")}.take(5)
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_RESUME)vm.refresh()};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
 Box(Modifier.fillMaxSize()) {
  PullToRefreshBox(isRefreshing=loading,onRefresh={vm.refresh()}) {
   LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,8.dp,20.dp,158.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    item {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Image(painterResource(R.drawable.smartpantry_logo_horizontal),"SmartPantry",Modifier.weight(1f).height(38.dp),alignment=Alignment.CenterStart,contentScale=ContentScale.Fit)
      Surface(Modifier.size(42.dp).clickable(onClick=onProfile),shape=CircleShape,color=c.primaryContainer){
       Box(contentAlignment=Alignment.Center){Text(userName.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1)}.uppercase().ifBlank{"You"},fontWeight=FontWeight.Bold,color=c.primary)}
      }
     }
     Spacer(Modifier.height(10.dp))
     val greeting=when(LocalTime.now(zone).hour){in 5..11->"Good morning";in 12..17->"Good afternoon";else->"Good evening"}
     Text("$greeting, ${userName.trim().ifBlank{"there"}}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
     Text("Make the most of what you have.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
    }
    message?.let{item{Text(it,color=c.error,style=MaterialTheme.typography.bodySmall)}}
    item {
     Surface(Modifier.fillMaxWidth().clickable(onClick=onRecords),shape=RoundedCornerShape(22.dp)){
      Box(Modifier.fillMaxWidth().heightIn(min=164.dp).background(Brush.horizontalGradient(listOf(Color(0xFFE5FAFA),Color(0xFFB6ECEB))))) {
       Canvas(Modifier.align(Alignment.CenterEnd).size(160.dp)){
        for(i in 1..4)drawCircle(Color.White.copy(alpha=.45f),radius=size.minDimension*(.18f+i*.09f),style=Stroke(1.dp.toPx()))
       }
       Image(painterResource(R.drawable.home_vegetables),null,Modifier.align(Alignment.BottomEnd).width(180.dp).height(164.dp),contentScale=ContentScale.Fit,alignment=Alignment.BottomEnd)
       Column(Modifier.fillMaxWidth().padding(start=16.dp,end=160.dp,top=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
        Text("THIS WEEK",style=MaterialTheme.typography.labelMedium,color=c.primary,fontWeight=FontWeight.Bold)
        Text("Waste rate",color=c.primary,style=MaterialTheme.typography.bodyMedium)
        Text(if(data.isEmpty())if(loading)"Loading…" else "Unavailable" else String.format(java.util.Locale.US,"%.1f%%",data.number("wasteRate")),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=c.primary)
        Text("Based on recorded entries",style=MaterialTheme.typography.labelSmall,color=c.primary)
       }
      }
     }
    }
    item{Surface(Modifier.fillMaxWidth().clickable(onClick=onPantry),shape=RoundedCornerShape(12.dp),color=c.surfaceContainerLow){Row(Modifier.heightIn(min=38.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
     Text(if(data.isEmpty())"Pantry stock unavailable" else "${pantry.map{it.long("ingredientId")}.filter{it>0}.distinct().size} ingredients · ${pantry.size} batches",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Medium)
     Icon(Icons.Default.ChevronRight,"View pantry",Modifier.size(24.dp),tint=c.primary)
    }}}
    item{HomeSectionTitle("Use these first",onPantry)}
    if(soon.isEmpty())item{Text(if(data.isEmpty())"Pull down to load your pantry stock." else "No available stock expires within 3 days.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
    itemsIndexed(soon,key={_,row->row.long("id")}){index,row->
     Column{
      Row(Modifier.fillMaxWidth().clickable{onPantryBatch(row.long("ingredientId"),row.long("id"))}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
       RecipePhoto(row.str("image").ifBlank{row.str("name").lowercase()},Modifier.size(48.dp),false)
       Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(row.str("name","Ingredient"),fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
        val days=java.time.temporal.ChronoUnit.DAYS.between(today,LocalDate.parse(row.str("expiry")))
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
         Text(displayAmount(row.number("quantity"),row.str("unit"))+" ·",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
         Surface(shape=RoundedCornerShape(8.dp),color=Color(0xFFFFF1BA)){
          Text(when(days){0L->"Expires today";1L->"Expires tomorrow";else->"Expires in $days days"},Modifier.padding(5.dp,2.dp),style=MaterialTheme.typography.labelSmall,color=Color(0xFF76520C))
         }
        }
       }
       Icon(Icons.Default.ChevronRight,"View item",Modifier.size(24.dp),tint=c.primary)
      }
      if(index<soon.lastIndex)HorizontalDivider(color=c.outlineVariant)
     }
    }
    item{Spacer(Modifier.height(3.dp));Surface(Modifier.fillMaxWidth().clickable(onClick=onShoppingList),shape=RoundedCornerShape(16.dp),color=c.surfaceContainerLow,border=androidx.compose.foundation.BorderStroke(1.dp,c.outlineVariant)){
     Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
      Icon(Icons.Default.ShoppingBasket,null,Modifier.size(28.dp),tint=c.primary)
      Column(Modifier.weight(1f)){Text("Shopping list",fontWeight=FontWeight.Bold);Text(if(data.isEmpty())"Open your list" else "${data.long("toBuy")} to buy · ${data.long("purchased")} purchased",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
      Icon(Icons.Default.ChevronRight,"Open shopping list",Modifier.size(24.dp),tint=c.primary)
     }
    }}
    item{CommunityDiscover(onOpenCommunityPost,onRecipes)}
   }
  }
  ContextAddButton(Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=104.dp),onCreateRecipe,onCreateIngredient,onAddPantry)
 }
}
@Composable internal fun HomeSectionTitle(title:String,onSeeAll:()->Unit){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
  Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
  TextButton(onClick=onSeeAll,contentPadding=PaddingValues(start=8.dp,end=0.dp)){
   Text("See all",style=MaterialTheme.typography.labelLarge);Icon(Icons.Default.ChevronRight,null,Modifier.size(24.dp))
  }
 }
}
