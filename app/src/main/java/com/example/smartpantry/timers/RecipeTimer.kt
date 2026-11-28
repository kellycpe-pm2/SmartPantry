package com.example.smartpantry.timers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.smartpantry.MainActivity
import com.example.smartpantry.notifications.NotificationRepository
import com.example.smartpantry.hardware.NotificationHelper
import java.util.UUID

internal data class RecipeTimerState(val owner:String="",val session:String="",val title:String="",val step:Int=-1,val total:Int=0,val left:Int=0,val status:String="ready",val token:String="",val deadline:Long=0L,val wallEnd:Long=0L){
 fun remaining(now:Long=SystemClock.elapsedRealtime()):Int=if(status=="running")((deadline-now+999L)/1000L).coerceIn(0,total.toLong()).toInt()else left
}
/** One user-started timer on the device. No server, Groq call, or inventory write. */
internal object RecipeTimers {
 private const val PREF="recipe_step_timer_v1"
 private const val CHANNEL="recipe_step_timer_alerts_v1"
 private const val ID=783401
 private fun prefs(c:Context)=c.getSharedPreferences(PREF,Context.MODE_PRIVATE)
 private fun manager(c:Context)=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
 fun exactAllowed(c:Context)=Build.VERSION.SDK_INT<31||manager(c).canScheduleExactAlarms()
 fun settingsIntent(c:Context)=if(Build.VERSION.SDK_INT>=31)Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${c.packageName}"))else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}"))
 @Synchronized fun bindOwner(c:Context,owner:String){
  if(prefs(c).getString("activeOwner","")!=owner){reset(c);prefs(c).edit().putString("activeOwner",owner).commit()}
 }
 fun read(c:Context):RecipeTimerState {
  val p=prefs(c)
  return RecipeTimerState(p.getString("owner","").orEmpty(),p.getString("session","").orEmpty(),p.getString("title","").orEmpty(),p.getInt("step",-1),p.getInt("total",0),p.getInt("left",0),p.getString("status","ready").orEmpty(),p.getString("token","").orEmpty(),p.getLong("deadline",0),p.getLong("wallEnd",0))
 }
 private fun write(c:Context,s:RecipeTimerState){check(prefs(c).edit().putString("owner",s.owner).putString("session",s.session).putString("title",s.title).putInt("step",s.step).putInt("total",s.total).putInt("left",s.left).putString("status",s.status).putString("token",s.token).putLong("deadline",s.deadline).putLong("wallEnd",s.wallEnd).commit()){"Could not save this timer. Please try again."}}
 private fun alarm(c:Context,token:String,flags:Int=PendingIntent.FLAG_UPDATE_CURRENT):PendingIntent?=PendingIntent.getBroadcast(c,ID,Intent(c,RecipeTimerReceiver::class.java).setAction("com.example.smartpantry.STEP_TIMER").putExtra("token",token),flags or PendingIntent.FLAG_IMMUTABLE)
 private fun cancelAlarm(c:Context){alarm(c,"",PendingIntent.FLAG_NO_CREATE)?.let{manager(c).cancel(it);it.cancel()}}
 private fun link(s:RecipeTimerState, completed:Boolean):String {
  val id=s.session.substringAfter(':').toLongOrNull()?:0L
  val host=if(s.session.startsWith("private:"))"private_recipe" else "recipe"
  val base = "smartpantry://$host/$id?timerOwner=${Uri.encode(s.owner)}"
  return if (completed) "$base&notificationEvent=${Uri.encode("timer:${s.token}")}" else base
 }
 private fun openIntent(c:Context,s:RecipeTimerState,completed:Boolean=false)= PendingIntent.getActivity(c,ID,Intent(c,MainActivity::class.java).setData(Uri.parse(link(s,completed))).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun schedule(c:Context,s:RecipeTimerState){
  check(exactAllowed(c)){"Allow Alarms & reminders for SmartPantry, then start the timer again."}
  // AlarmClock is appropriate for the explicitly started cooking timer and wakes during idle.
  manager(c).setAlarmClock(AlarmManager.AlarmClockInfo(s.wallEnd,openIntent(c,s)),checkNotNull(alarm(c,s.token)))
 }
 @Synchronized fun start(c:Context,owner:String,session:String,title:String,step:Int,seconds:Int){
  require(owner.isNotBlank()){ "Sign in before starting a timer." };require(seconds in 1..86400)
  check(exactAllowed(c)){"Allow Alarms & reminders for SmartPantry, then start the timer again."}
  bindOwner(c,owner)
  val previous=read(c)
  val s=RecipeTimerState(owner,session,title.take(150),step,seconds,seconds,"running",UUID.randomUUID().toString(),SystemClock.elapsedRealtime()+seconds*1000L,System.currentTimeMillis()+seconds*1000L)
  write(c,s)
  try{schedule(c,s)}catch(e:Exception){write(c,previous);if(previous.status=="running")runCatching{schedule(c,previous)};throw e}
  NotificationManagerCompat.from(c).cancel(ID)
 }
 @Synchronized fun pause(c:Context){val s=read(c);if(s.status!="running")return;val left=s.remaining();if(left==0){complete(c,s.token);return};cancelAlarm(c);write(c,s.copy(status="paused",left=left,token=UUID.randomUUID().toString()))}
 @Synchronized fun resume(c:Context){val s=read(c);if(s.status!="paused")return;check(exactAllowed(c)){"Allow Alarms & reminders, then resume the timer."};val next=s.copy(status="running",token=UUID.randomUUID().toString(),deadline=SystemClock.elapsedRealtime()+s.left*1000L,wallEnd=System.currentTimeMillis()+s.left*1000L);write(c,next);try{schedule(c,next)}catch(e:Exception){write(c,s);throw e}}
 @Synchronized fun reset(c:Context){cancelAlarm(c);write(c,RecipeTimerState());NotificationManagerCompat.from(c).cancel(ID)}
 fun notificationsEnabled(c:Context,owner:String):Boolean {
  val settings = c.getSharedPreferences("notifications_$owner", Context.MODE_PRIVATE)
  if (!settings.getBoolean("push", false) || !settings.getBoolean("timers_enabled", true)) return false
  if(!NotificationManagerCompat.from(c).areNotificationsEnabled())return false
  return Build.VERSION.SDK_INT<26||(c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
 }
 @Synchronized fun complete(c:Context,token:String){
  val s=read(c);if(s.status!="running"||s.token!=token||s.owner!=prefs(c).getString("activeOwner",""))return
  if(System.currentTimeMillis()+500<s.wallEnd)return
  val title = s.title.ifBlank { "SmartPantry cooking timer" }
  val body = "Step ${s.step+1} timer finished — check your food."
  val recipe = s.session.substringAfter(':').toLongOrNull() ?: 0L
  val target = if (s.session.startsWith("private:")) -recipe else recipe
  val saved = runCatching {
   NotificationRepository(c, s.owner).recordTimer(s.token, title, body, target, s.wallEnd)
  }.isSuccess
  write(c,s.copy(status="finished",left=0));cancelAlarm(c)
  if (saved) runCatching { NotificationHelper.triggerImmediateCheck(c) }
  val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if(Build.VERSION.SDK_INT>=26){val channel=NotificationChannel(CHANNEL,"Cooking step timers",NotificationManager.IMPORTANCE_HIGH).apply{description="Reminders for cooking timers you start";enableVibration(true)};nm.createNotificationChannel(channel)}
  if(!notificationsEnabled(c,s.owner))return
  val notification=NotificationCompat.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(s.title.ifBlank{"SmartPantry cooking timer"}).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(openIntent(c,s,completed=true)).setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM).setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE).setAutoCancel(true).build()
  try{NotificationManagerCompat.from(c).notify(ID,notification)}catch(_:SecurityException){}
 }
 @Synchronized fun recover(c:Context){
  val s=read(c);if(s.status!="running")return
  if(s.owner!=prefs(c).getString("activeOwner","")){reset(c);return}
  if(s.wallEnd<=System.currentTimeMillis()){complete(c,s.token);return}
  val recovered=s.copy(deadline=SystemClock.elapsedRealtime()+(s.wallEnd-System.currentTimeMillis()))
  write(c,recovered)
  if(exactAllowed(c))runCatching{schedule(c,recovered)}
 }
}
class RecipeTimerReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){
 if(intent.action==Intent.ACTION_BOOT_COMPLETED||intent.action==AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)RecipeTimers.recover(context)
 else RecipeTimers.complete(context,intent.getStringExtra("token").orEmpty())
}}
