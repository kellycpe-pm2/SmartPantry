package com.example.smartpantry.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF176F6C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F4F4),
    onPrimaryContainer = Color(0xFF17383A),
    secondary = Color(0xFF426E70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F4F4),
    onSecondaryContainer = Color(0xFF17383A),
    tertiary = Color(0xFF875511),
    background = Color(0xFFFCFEFE),
    onBackground = Color(0xFF17383A),
    surface = Color.White,
    onSurface = Color(0xFF17383A),
    surfaceVariant = Color(0xFFEAF3F3),
    onSurfaceVariant = Color(0xFF53696B),
    surfaceContainerLow = Color(0xFFF0F8F8),
    surfaceContainer = Color(0xFFEAF4F4),
    surfaceContainerHigh = Color(0xFFE4EFEF),
    outline = Color(0xFF73898A),
    outlineVariant = Color(0xFFDCEAEA)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF85D6D1),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF204A49),
    onPrimaryContainer = Color(0xFFD8F4F4),
    secondary = Color(0xFFA7CECC),
    onSecondary = Color(0xFF193737),
    secondaryContainer = Color(0xFF204A49),
    onSecondaryContainer = Color(0xFFD8F4F4),
    tertiary = Color(0xFFF2BD73),
    background = Color(0xFF101C1D),
    onBackground = Color(0xFFE0EEEE),
    surface = Color(0xFF182728),
    onSurface = Color(0xFFE0EEEE),
    surfaceVariant = Color(0xFF2A3D3E),
    onSurfaceVariant = Color(0xFFB5C9C9),
    surfaceContainerLow = Color(0xFF1C2D2E),
    surfaceContainer = Color(0xFF223435),
    surfaceContainerHigh = Color(0xFF293B3C),
    outline = Color(0xFF829A9B),
    outlineVariant = Color(0xFF3A5051)
)

@Composable
fun SmartPantryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 默认使用 SmartPantry 配色。手机深浅色模式决定用哪一套。
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}