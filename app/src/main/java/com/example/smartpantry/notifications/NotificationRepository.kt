package com.example.smartpantry.notifications

import android.content.Context
import com.example.smartpantry.community.*
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal object NotificationBadge { val count=MutableStateFlow(0L) }
internal class NotificationRepository(private val context:Context) {
 fun owner()=supabase.auth.currentUserOrNull()?.id ?: error("Please sign in again.")
 private fun prefs()=context.getSharedPreferences("notifications_${owner()}",Context.MODE_PRIVATE)
 fun cached():JsonObject=runCatching{Json.parseToJsonElement(prefs().getString("snapshot","{}")!!).jsonObject}.getOrDefault(buildJsonObject{})
 fun pushEnabled()=prefs().getBoolean("push",false)
 fun settings():JsonObject=cached()["settings"]?.jsonObject ?: buildJsonObject{}
 fun recordSyncIssue(){
  val key="sync_"+java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kuala_Lumpur"))
  if(prefs().getBoolean(key,false))return
  val old=cached();val row=buildJsonObject{put("id",-1);put("category","System");put("kind","sync");put("title","Shopping changes are waiting to sync");put("body","Your changes are saved on this device. Open your shopping list and retry when connected.");put("created_at",java.time.Instant.now().toString())}
  val data=buildJsonObject{old.forEach{(k,v)->put(k,v)};put("items",JsonArray(old.rows("items").filter{it.long("id")>0}+row));put("unread",old.long("unread")+1)}
  prefs().edit().putBoolean(key,true).putBoolean("pending_sync_issue",true).putString("snapshot",data.toString()).apply();NotificationBadge.count.value=data.long("unread")
 }
 fun markReadLocal(id:Long){
  val old=cached();val rows=old.rows("items").map{row->if(row.long("id")==id)buildJsonObject{row.forEach{(k,v)->put(k,v)};put("read_at",java.time.Instant.now().toString())}else row}
  val result=buildJsonObject{old.forEach{(k,v)->put(k,v)};put("items",JsonArray(rows));put("unread",rows.count{it.str("read_at").isBlank()})}
  val editor=prefs().edit().putString("snapshot",result.toString());if(id>0)editor.putStringSet("pending_reads",(prefs().getStringSet("pending_reads",emptySet())?:emptySet())+id.toString());editor.apply();NotificationBadge.count.value=result.long("unread")
 }
 suspend fun call(action:String="list",payload:JsonObject=buildJsonObject{}):JsonObject=withContext(Dispatchers.IO){
  supabase.auth.sessionStatus.first{it !is SessionStatus.Initializing};val uid=owner()
  if(action in listOf("list","refresh")){
   if(prefs().getBoolean("pending_sync_issue",false)){supabase.postgrest.rpc("notifications_api",buildJsonObject{put("p_action","sync_issue");put("p_payload",buildJsonObject{})});prefs().edit().putBoolean("pending_sync_issue",false).apply()}
   val ids=prefs().getStringSet("pending_reads",emptySet())?.toSet().orEmpty()
   ids.forEach{id->supabase.postgrest.rpc("notifications_api",buildJsonObject{put("p_action","read");put("p_payload",buildJsonObject{put("id",id.toLong())})})}
   if(ids.isNotEmpty())prefs().edit().putStringSet("pending_reads",emptySet()).apply()
  }
  val result=Json.parseToJsonElement(supabase.postgrest.rpc("notifications_api",buildJsonObject{put("p_action",action);put("p_payload",payload)}).data).jsonObject
  check(uid==owner()){"Please reopen notifications after switching accounts."}
  if(action=="save_settings" || action=="settings"){
   val snapshot=buildJsonObject{cached().forEach{(k,v)->put(k,v)};put("settings",result)}
   prefs().edit().putString("snapshot",snapshot.toString()).putBoolean("push",result.flag("push")).apply()
  } else {prefs().edit().putString("snapshot",result.toString()).putBoolean("push",result["settings"]?.jsonObject?.flag("push")==true).apply();NotificationBadge.count.value=result.long("unread")}
  result
 }
 fun sent(id:Long)=prefs().getBoolean("sent_$id",false)
 fun markSent(id:Long){prefs().edit().putBoolean("sent_$id",true).apply()}
 fun setLocalPush(enabled:Boolean){prefs().edit().putBoolean("push",enabled).apply()}
}
