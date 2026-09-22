package com.example.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NuPurple,
    onPrimary = Color.White,
    primaryContainer = NuPurpleDark,
    onPrimaryContainer = Color.White,
    secondary = NuPurpleLight,
    onSecondary = NuPurpleDark,
    background = NuBackgroundDark,
    surface = NuSurfaceDark,
    onBackground = NuTextPrimaryDark,
    onSurface = NuTextPrimaryDark,
    error = NuError
)

private val LightColorScheme = lightColorScheme(
    primary = NuPurple,
    onPrimary = Color.White,
    primaryContainer = NuPurpleLight,
    onPrimaryContainer = NuPurpleDark,
    secondary = NuPurpleDark,
    onSecondary = Color.White,
    background = NuBackgroundLight,
    surface = NuSurfaceLight,
    onBackground = NuTextPrimary,
    onSurface = NuTextPrimary,
    error = NuError
)

@Composable
fun MobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}