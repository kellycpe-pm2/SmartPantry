package com.example.smartpantry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.smartpantry.ui.navigation.AppNavHost
import com.example.smartpantry.ui.theme.SmartPantryTheme
import com.example.smartpantry.uicomponent.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

            SmartPantryTheme {
                AppTheme{
                    Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onBackground)) {
                        AppNavHost()
                }
                }
            }
        }
    }
}