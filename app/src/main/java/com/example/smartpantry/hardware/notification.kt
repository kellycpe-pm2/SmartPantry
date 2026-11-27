package com.example.smartpantry.hardware

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.smartpantry.MainActivity
import com.example.smartpantry.community.*
import com.example.smartpantry.notifications.NotificationRepository
import kotlinx.coroutines.CancellationException
import java.time.LocalTime
import java.time.ZoneId

object NotificationHelper {
 private const val CHANNEL_ID="smartpantry_updates_v6"
 fun createNotificationChannel(context:Context){
  if(Build.VERSION.SDK_INT>=26)(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL_ID,"SmartPantry updates",NotificationManager.IMPORTANCE_DEFAULT))
 }
 fun showNotification(context:Context,id:Int,title:String,message:String,link:String="smartpantry://notifications"){
  if(!NotificationManagerCompat.from(context).areNotificationsEnabled())return
  val intent=Intent(context,MainActivity::class.java).setData(Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
  val pending=PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=NotificationCompat.Builder(context,CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(pending).setAutoCancel(true).build()
  try{NotificationManagerCompat.from(context).notify(id,notification)}catch(_:SecurityException){}
 }
 fun triggerImmediateCheck(context:Context){WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build())}
}
/** Auth-scoped server snapshot; never scans all local users' inventory. Periodic, not instant remote push. */
class ExpiryCheckWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result{
  val repo=NotificationRepository(applicationContext)
  val owner=runCatching{repo.owner()}.getOrNull()?:return Result.success()
  try{
   val data=repo.call("refresh");if(owner!=repo.owner())return Result.success()
   val settings=data["settings"] as? kotlinx.serialization.json.JsonObject?:return Result.success()
   if(!settings.flag("push") || !NotificationManagerCompat.from(applicationContext).areNotificationsEnabled())return Result.success()
   val hour=LocalTime.now(ZoneId.of("Asia/Kuala_Lumpur")).hour
   val start=settings.long("quiet_start").toInt();val end=settings.long("quiet_end").toInt()
   if(settings.flag("quiet") && (if(start>end)hour>=start||hour<end else hour>=start&&hour<end))return Result.success()
   data.rows("items").filter{it.str("read_at").isBlank() && !repo.sent(it.long("id"))}.take(5).forEach{row->
    val link=when(row.str("category")){"Interactions"->"smartpantry://recipe/${row.long("post_id")}";"Pantry"->"smartpantry://batch/${row.long("ingredient_id")}/${row.long("batch_id")}";else->"smartpantry://notifications"}
    NotificationHelper.showNotification(applicationContext,(owner+row.long("id")).hashCode(),row.str("title"),row.str("body"),link);repo.markSent(row.long("id"))
   }
   return Result.success()
  }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){return Result.retry()}
 }
}
