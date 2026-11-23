package com.example.smartpantry.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.smartpantry.R

@Composable
fun SplashScreen() {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    Column(Modifier.fillMaxSize().background(colors.background),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // The supplied logo includes a light background; give it an intentional
        // rounded light surface in dark mode rather than recolouring the asset.
        Surface(shape = RoundedCornerShape(28.dp), color = if (dark) Color.White else colors.background) {
            Image(painterResource(R.drawable.smartpantry_logo), contentDescription = "SmartPantry",
                modifier = Modifier.width(250.dp).padding(if (dark) 18.dp else 0.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text("Make the most of what you have.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(modifier = Modifier.width(120.dp), color = colors.primary, trackColor = colors.primaryContainer)
    }
}
