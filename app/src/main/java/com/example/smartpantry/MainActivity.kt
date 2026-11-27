package com.example.smartpantry

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.smartpantry.hardware.ExpiryCheckWorker
import com.example.smartpantry.hardware.NotificationHelper
import com.example.smartpantry.ui.navigation.AppRoot
import com.example.smartpantry.ui.theme.SmartPantryTheme

import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private var externalLink by mutableStateOf("")
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);externalLink=intent.dataString.orEmpty()}
    //notification setup
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("SmartPantry", "Notification permission granted")
            } else {
                Log.d("SmartPantry", "Notification permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannel(this)
        externalLink=intent?.dataString.orEmpty()
        // Ask for device-notification permission only when the user enables it in settings.
        scheduleExpiryCheckWorker()

        setContent {
            SmartPantryTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AppRoot(externalLink){externalLink=""} }
            }
        }
    }

    private fun scheduleExpiryCheckWorker() {
        // Run an immediate check on app startup for testing
        NotificationHelper.triggerImmediateCheck(this)

        WorkManager.getInstance(this).cancelUniqueWork("ExpiryCheckWork")
        val workRequest = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SmartPantryNotificationsV6",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
