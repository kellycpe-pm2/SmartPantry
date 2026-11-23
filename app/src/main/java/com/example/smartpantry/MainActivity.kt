package com.example.smartpantry

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import com.example.smartpantry.uicomponent.AppTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scheduleExpiryCheckWorker()

        setContent {
            SmartPantryTheme {
                AppTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot()
                    }
                }
            }
        }
    }

    private fun scheduleExpiryCheckWorker() {
        // Run an immediate check on app startup for testing
        NotificationHelper.triggerImmediateCheck(this)

        val workRequest = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ExpiryCheckWork",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }
}
