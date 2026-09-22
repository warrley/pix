package com.example.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RuRubyRed,
    onPrimary = Color.White,
    primaryContainer = RuRubyDark,
    onPrimaryContainer = Color.White,
    secondary = RuRubyLight,
    onSecondary = RuRubyDark,
    background = RuBackgroundDark,
    surface = RuSurfaceDark,
    onBackground = RuTextPrimaryDark,
    onSurface = RuTextPrimaryDark,
    error = RuError
)

private val LightColorScheme = lightColorScheme(
    primary = RuRubyRed,
    onPrimary = Color.White,
    primaryContainer = RuRubyLight,
    onPrimaryContainer = RuRubyDark,
    secondary = RuRubyDark,
    onSecondary = Color.White,
    background = RuBackgroundLight,
    surface = RuSurfaceLight,
    onBackground = RuTextPrimary,
    onSurface = RuTextPrimary,
    error = RuError
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