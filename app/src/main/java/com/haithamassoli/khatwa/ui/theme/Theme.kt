package com.haithamassoli.khatwa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    primaryContainer = TealContainer80,
    secondary = Slate80,
    tertiary = Amber80,
    background = Surface80,
    surface = Surface80,
)

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    primaryContainer = TealContainer40,
    secondary = Slate40,
    tertiary = Amber40,
    background = Surface40,
    surface = Surface40,
)

// Fixed brand palette (no dynamic color) so map marker colors and UI stay consistent across devices.
@Composable
fun KhatwaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
