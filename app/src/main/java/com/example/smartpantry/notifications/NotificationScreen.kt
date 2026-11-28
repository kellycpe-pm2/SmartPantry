package com.example.smartpantry.notifications

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.smartpantry.community.*
import com.example.smartpantry.core.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NotificationScreen(onBack:()->Unit,onPost:(Long,Long)->Unit,onBatch:(Long,Long)->Unit,onRecipes:(String)->Unit,onShopping:()->Unit) {
 val context = LocalContext.current
 val session by supabase.auth.sessionStatus.collectAsState()
 val owner = remember(session) { supabase.auth.currentUserOrNull()?.id }
 val repo = remember(context, owner) { NotificationRepository(context, owner) }
 val lifecycle = LocalLifecycleOwner.current.lifecycle
 val scope = rememberCoroutineScope()
 val c = MaterialTheme.colorScheme
 val revision by repo.updates.collectAsState()
 val data = remember(repo, revision) { repo.cached() }
 var loading by remember(repo) { mutableStateOf(false) }
 var error by remember(repo) { mutableStateOf<String?>(null) }
 var category by rememberSaveable{mutableStateOf("All")};var settingsOpen by rememberSaveable{mutableStateOf(false)}
 var pageMenu by remember { mutableStateOf(false) }
 var confirmClear by remember(repo) { mutableStateOf<String?>(null) }
 fun refresh() {
  if (loading || owner == null) return
  loading = true
  scope.launch {
   try { repo.call(); error = null }
   catch (t: CancellationException) { throw t }
   catch (e: Exception) { error = userMessage(e) + " Your saved notifications are still shown." }
   finally { loading = false }
  }
 }
 fun change(rows: List<JsonObject>, clear: Boolean) {
  try {
   repo.changeLocal(rows, clear)
   refresh()
  } catch (e: Exception) { error = userMessage(e) }
 }
 LaunchedEffect(repo, lifecycle) {
  lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
   while (true) {
    refresh()
    delay(30_000)
   }
  }
 }
 com.example.smartpantry.ui.theme.DirectionalPage(targetState=if(settingsOpen)"settings" else "home",modifier=Modifier.fillMaxSize()){page->
 if(page=="settings") key(repo) { NotificationSettings(repo,onBack={settingsOpen=false;refresh()}) } else {
 Column(Modifier.fillMaxSize()) {
  SmartPantrySectionHeader("Notifications",true,onBack){
   TextButton(onClick={change(data.rows("items").filter { it.str("read_at").isBlank() }, false)},
    enabled=data.long("unread") > 0){Text("Mark all read",style=MaterialTheme.typography.labelSmall)}
   IconButton(onClick={settingsOpen=true}){Icon(Icons.Default.Settings,"Notification settings")}
   Box {
    IconButton(onClick={pageMenu=true}){Icon(Icons.Default.MoreVert,"Notification actions")}
    DropdownMenu(pageMenu, { pageMenu=false }) {
     DropdownMenuItem(text={Text("Clear read notifications")}, onClick={pageMenu=false;confirmClear=category})
    }
   }
  }
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
   listOf("All","Interactions","Pantry","System").forEach{tab->FilterChip(category==tab,{category=tab},label={Text(if(tab=="Interactions")"Activity" else tab,style=MaterialTheme.typography.labelMedium)})}
  }
  error?.let{Text(it,Modifier.padding(16.dp),color=c.error,style=MaterialTheme.typography.bodySmall)}
  val rows=data.rows("items").filter{category=="All" || it.str("category")==category}
  confirmClear?.let { selected ->
   val label = if (selected == "Interactions") "Activity" else selected
   AlertDialog(onDismissRequest={confirmClear=null}, title={Text("Clear read notifications?")},
    text={Text("Clear read notifications in $label.")},
    confirmButton={TextButton(onClick={
     change(data.rows("items").filter {
      it.str("read_at").isNotBlank() && (selected=="All" || it.str("category")==selected)
     }, true)
     confirmClear=null
    }){Text("Clear")}},
    dismissButton={TextButton(onClick={confirmClear=null}){Text("Cancel")}})
  }
  PullToRefreshBox(isRefreshing=loading,onRefresh={refresh()},modifier=Modifier.weight(1f)) {
   LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,8.dp,16.dp,32.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    if(rows.isEmpty() && !loading)item{Column(Modifier.fillMaxWidth().padding(vertical=64.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Default.NotificationsNone,null,Modifier.size(48.dp),tint=c.primary);Text("You're all caught up",style=MaterialTheme.typography.titleMedium);Text(if(error!=null)"Connect and pull down to load your notifications." else "New ${if(category=="All")"" else category.lowercase()+" "}notifications will appear here.",style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant)}}
    items(rows,key={it.str("event_key")}){row->
     var rowMenu by remember { mutableStateOf(false) }
     val unread=row.str("read_at").isBlank()
     fun markRead() { if (unread) change(listOf(row), false) }
     val open:()->Unit={
      markRead()
      when(row.str("kind")){
       "timer" -> onPost(row.long("recipe_target"), 0L)
       "sync" -> onShopping()
       "expiry" -> onBatch(row.long("ingredient_id"),row.long("batch_id"))
       else -> onPost(row.long("post_id"),row.long("comment_id"))
      }
     }
     Surface(Modifier.fillMaxWidth().clickable(onClick=open),shape=RoundedCornerShape(16.dp),color=if(unread)c.primaryContainer.copy(alpha=.4f) else c.surface,border=BorderStroke(1.dp,c.outlineVariant)){
      Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
       Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Box(Modifier.size(40.dp).clip(CircleShape),contentAlignment=Alignment.Center){
         if(row.str("avatar").isNotBlank())RecipePhoto(row.str("avatar"),Modifier.fillMaxSize(),false)
         else Icon(when(row.str("kind")){"comment"->Icons.Default.ChatBubbleOutline;"like"->Icons.Default.FavoriteBorder;"expiry","timer"->Icons.Default.Schedule;else->Icons.Default.CloudOff},null,tint=c.primary)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
         Text(row.str("title"),fontWeight=if(unread)FontWeight.Bold else FontWeight.Medium,
          maxLines=2,overflow=TextOverflow.Ellipsis)
         val body = row.str("body")
         val summary = if (row.str("category")=="Interactions" && body.length>90) body.take(90).trimEnd()+"…" else body
         if (summary.isNotBlank()) Text(summary,style=MaterialTheme.typography.bodySmall,
          color=c.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
         val stamp=runCatching{DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.of("Asia/Kuala_Lumpur")).format(Instant.parse(row.str("created_at")))}.getOrDefault("")
         Text(stamp,style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
        }
        if(unread)Badge(Modifier.padding(top=10.dp),containerColor=c.primary)
        Box {
         IconButton(onClick={rowMenu=true},modifier=Modifier.size(40.dp)){
          Icon(Icons.Default.MoreVert,"Notification actions", tint=c.onSurfaceVariant)
         }
         DropdownMenu(rowMenu,{rowMenu=false}){
          DropdownMenuItem(text={Text("Clear notification")},onClick={rowMenu=false;change(listOf(row),true)})
         }
        }
       }
       if(row.str("category")=="Pantry")Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(onClick=open,colors=ButtonDefaults.outlinedButtonColors(contentColor=c.onSurface),modifier=Modifier.weight(1f)){Icon(Icons.Default.Inventory2,null,Modifier.size(17.dp));Spacer(Modifier.width(4.dp));Text("View batch",style=MaterialTheme.typography.labelMedium)}
        OutlinedButton(colors=ButtonDefaults.outlinedButtonColors(contentColor=c.onSurface),onClick={markRead();onRecipes(row.str("title").substringBefore(" expire").substringBefore(" has expired"))},enabled=!row.str("title").contains("has expired"),modifier=Modifier.weight(1f)){Icon(Icons.Default.RestaurantMenu,null,Modifier.size(17.dp));Spacer(Modifier.width(4.dp));Text("Find recipes",style=MaterialTheme.typography.labelMedium)}
       }
      }
     }
    }
   }
  }
 }
 }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun NotificationSettings(repo:NotificationRepository,onBack:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope();val c=MaterialTheme.colorScheme
 var settings by remember{mutableStateOf(repo.settings())};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 fun save(key:String,value:JsonPrimitive){if(busy)return;val old=settings;settings=buildJsonObject{old.forEach{(k,v)->put(k,v)};put(key,value)};busy=true;scope.launch{try{settings=repo.call("save_settings",buildJsonObject { put(key,value) });error=null;if(settings.flag("push"))com.example.smartpantry.hardware.NotificationHelper.triggerImmediateCheck(context)}catch(t:Throwable){if(t is CancellationException)throw t;settings=old;error=userMessage(t)+" Your previous settings are kept."}finally{busy=false}}}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(allowed)save("push",JsonPrimitive(true))else error="Notifications are blocked by Android. Enable them in your phone's app settings."}
 LaunchedEffect(Unit){try{settings=repo.call("settings")}catch(t:Throwable){if(t is CancellationException)throw t;error=userMessage(t)}}
 Column(Modifier.fillMaxSize()){
  SmartPantrySectionHeader("Notification settings",true,onBack)
  LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{Text("In-app notifications",fontWeight=FontWeight.Bold)}
   items(listOf(Triple("comments","Comments","When someone comments on your recipe"),Triple("likes","Comment likes","When someone likes your comment"),Triple("favourites","Recipe favourites","When someone saves your recipe"),Triple("ratings","Recipe ratings","When someone rates your recipe"),Triple("timers","Cooking timers","Timer history and device alerts"),Triple("pantry","Pantry reminders","Expiry reminders for your own batches"),Triple("sync","Sync issues","When shopping changes cannot be synced"))){(key,label,help)->
    SettingToggle(label,help,settings[key]?.jsonPrimitive?.booleanOrNull?:true,!busy){save(key,JsonPrimitive(it))}
   }
   item{HorizontalDivider();Text("Device notifications",fontWeight=FontWeight.Bold)}
   item{SettingToggle("Enable notifications","Periodic checks on this device; delivery time depends on Android background scheduling",settings.flag("push"),!busy){enabled->
    if(!enabled)save("push",JsonPrimitive(false)) else if(Build.VERSION.SDK_INT>=33 && !NotificationManagerCompat.from(context).areNotificationsEnabled())permission.launch(Manifest.permission.POST_NOTIFICATIONS) else if(!NotificationManagerCompat.from(context).areNotificationsEnabled())error="Enable notifications in your phone's app settings." else save("push",JsonPrimitive(true))
   }}
   item{var expanded by remember{mutableStateOf(false)};Box{OutlinedButton(onClick={expanded=true},enabled=!busy){Text("Expiry reminder: ${settings.long("reminder_days").takeIf{it>0}?:3} days before")};DropdownMenu(expanded,{expanded=false}){listOf(1,2,3,7).forEach{days->DropdownMenuItem(text={Text("$days days before expiry")},onClick={expanded=false;save("reminder_days",JsonPrimitive(days))})}}}}
   item{SettingToggle("Quiet hours","10 pm–8 am (Malaysia time)",settings.flag("quiet"),!busy){save("quiet",JsonPrimitive(it))}}
   item{Text("Notifications remain available in the app when device notifications are off.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
   error?.let{item{Text(it,color=c.error)}}
   if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
  }
 }
}
@Composable private fun SettingToggle(label:String,help:String,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  Column(Modifier.weight(1f)){Text(label,fontWeight=FontWeight.SemiBold);Text(help,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  Switch(checked,onChange,enabled=enabled)
 }
}
