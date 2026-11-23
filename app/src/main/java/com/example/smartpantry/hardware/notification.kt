package com.example.smartpantry.hardware

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.smartpantry.storage.local.AppDatabase

object NotificationHelper {
    private const val CHANNEL_ID = "expiry_alerts_channel_v3"
    private const val CHANNEL_NAME = "Ingredient Expiry Alerts"
    private const val CHANNEL_DESC = "Notifications for ingredients expiring soon or already expired"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, id: Int, title: String, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, builder.build())
    }

    fun triggerImmediateCheck(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

class ExpiryCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val inventory = db.ingredientUserDao().getAll()
        val allIngredients = db.ingredientDao().getAll()
        val ingredientMap = allIngredients.associateBy { it.id }

        val currentTime = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val threeDaysMs = 3 * oneDayMs

        inventory.forEach { item ->
            val timeLeft = item.expiredDate - currentTime
            val name = ingredientMap[item.ingredientId]?.ingredientName ?: "An ingredient"

            if (timeLeft <= 0) {
                NotificationHelper.showNotification(
                    applicationContext,
                    item.id.toInt(),
                    "Item Expired!",
                    "$name has expired."
                )
            } else if (timeLeft <= threeDaysMs) {
                val daysRemaining = (timeLeft / oneDayMs).coerceAtLeast(1)
                NotificationHelper.showNotification(
                    applicationContext,
                    item.id.toInt(),
                    "Expiring Soon!",
                    "$name will expire in $daysRemaining day(s)."
                )
            }
        }

        return Result.success()
    }
}
