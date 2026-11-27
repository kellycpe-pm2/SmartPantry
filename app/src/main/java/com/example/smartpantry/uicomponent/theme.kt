package com.example.smartpantry.uicomponent

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = BrandPrimary,
    onPrimaryContainer = BrandBodyText,

    secondary = BrandMutedText,
    onSecondary = Color.White,
    secondaryContainer = BrandSecondaryBg,
    onSecondaryContainer = BrandBodyText,

    background = BrandBackground,
    onBackground = BrandBodyText,
    surface = BrandBackground,
    onSurface = BrandBodyText,
    surfaceVariant = BrandSecondaryBg,
    onSurfaceVariant = BrandMutedText,


    outline = BrandBorder,
    outlineVariant = BrandBorder,

    surfaceContainerLowest=BrandBackground, surfaceContainerLow=BrandSecondaryBg,
    surfaceContainer=BrandSecondaryBg, surfaceContainerHigh=BrandSecondaryBg, surfaceContainerHighest=BrandSecondaryBg,
    surfaceTint=BrandPrimaryDark, surfaceBright=BrandBackground, surfaceDim=BrandSecondaryBg,
    tertiary=BrandPrimaryDark, onTertiary=Color.White, tertiaryContainer=BrandPrimary, onTertiaryContainer=BrandBodyText,
    inverseSurface=BrandBodyText, inverseOnSurface=BrandSecondaryBg, inversePrimary=BrandPrimary,
    errorContainer=Color(0xFFFFEDEA), onErrorContainer=Color(0xFF8C2424),
    error = BrandError,
    onError = Color.White
)


 // Dark Color Scheme

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandPrimaryDark,
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = Color.White,

    secondary = BrandBorder,
    onSecondary = BrandBodyText,
    secondaryContainer = BrandMutedText,
    onSecondaryContainer = Color.White,

    background = BrandBodyText,
    onBackground = BrandSecondaryBg,
    surface = BrandBodyText,
    onSurface = BrandSecondaryBg,
    surfaceVariant = BrandMutedText,
    onSurfaceVariant = BrandBorder,

    outline = BrandMutedText,
    outlineVariant = BrandMutedText,

    surfaceContainerLowest=Color(0xFF14282A), surfaceContainerLow=Color(0xFF203638),
    surfaceContainer=Color(0xFF263B3D), surfaceContainerHigh=Color(0xFF2D4547), surfaceContainerHighest=Color(0xFF365153),
    surfaceTint=BrandPrimary, surfaceBright=Color(0xFF365153), surfaceDim=Color(0xFF14282A),
    tertiary=BrandPrimary, onTertiary=BrandBodyText, tertiaryContainer=BrandPrimaryDark, onTertiaryContainer=Color.White,
    inverseSurface=BrandSecondaryBg, inverseOnSurface=BrandBodyText, inversePrimary=BrandPrimaryDark,
    errorContainer=Color(0xFF5C2828), onErrorContainer=Color(0xFFFFDAD6),
    error = Color(0xFFFFB4AB),
    onError = BrandError
)


//  The Theme Wrapper

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Make the top status bar match your background color
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
