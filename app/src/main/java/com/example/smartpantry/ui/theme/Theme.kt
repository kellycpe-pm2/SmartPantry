package com.example.smartpantry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    //anything with on____ is text
    primary = LightPrimary,
    onPrimary = LightOnPrimary,

    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightPrimary,

    background = LightBackground,
    onBackground = LightText,

    surface = LightBackground,
    onSurface = LightText,

    surfaceVariant = LightCard,
    onSurfaceVariant = LightMutedText,

    outline = LightMutedText,
    outlineVariant = LightBorder,

    // Copies of the above, so no Material default purple shows up in other components
    secondary = LightPrimary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightPrimaryContainer,
    onSecondaryContainer = LightPrimary,
    surfaceContainer = LightCard,        // nav bar
    surfaceContainerHigh = LightCard,    // dialogs
    surfaceContainerHighest = LightCard
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,

    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkPrimary,

    background = DarkBackground,
    onBackground = DarkText,

    surface = DarkBackground,
    onSurface = DarkText,

    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkMutedText,

    outline = DarkMutedText,
    outlineVariant = DarkBorder,

    // (same with light)Copies of the above, so no Material default purple shows up in other components
    secondary = DarkPrimary,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkPrimaryContainer,
    onSecondaryContainer = DarkPrimary,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkCard,
    surfaceContainerHighest = DarkCard
)

@Composable
fun SmartPantryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // follows the phone's setting
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}