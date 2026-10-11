package com.haithamassoli.khatwa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = OnTeal80,
    primaryContainer = TealContainer80,
    onPrimaryContainer = OnTealContainer80,
    inversePrimary = Teal40,
    secondary = Slate80,
    onSecondary = OnSlate80,
    secondaryContainer = SlateContainer80,
    onSecondaryContainer = OnSlateContainer80,
    tertiary = Amber80,
    onTertiary = OnAmber80,
    tertiaryContainer = AmberContainer80,
    onTertiaryContainer = OnAmberContainer80,
    background = Surface80,
    onBackground = OnSurface80,
    surface = Surface80,
    onSurface = OnSurface80,
    surfaceVariant = SurfaceVariant80,
    onSurfaceVariant = OnSurfaceVariant80,
    surfaceTint = Teal80,
    inverseSurface = OnSurface80,
    inverseOnSurface = InverseSurface40,
    error = Error80,
    onError = OnError80,
    errorContainer = ErrorContainer80,
    onErrorContainer = OnErrorContainer80,
    outline = Outline80,
    outlineVariant = OutlineVariant80,
    scrim = Color.Black,
    surfaceBright = SurfaceBright80,
    surfaceDim = Surface80,
    surfaceContainerLowest = SurfaceContainerLowest80,
    surfaceContainerLow = SurfaceContainerLow80,
    surfaceContainer = SurfaceContainer80,
    surfaceContainerHigh = SurfaceContainerHigh80,
    surfaceContainerHighest = SurfaceContainerHighest80,
)

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = OnTeal40,
    primaryContainer = TealContainer40,
    onPrimaryContainer = OnTealContainer40,
    inversePrimary = Teal80,
    secondary = Slate40,
    onSecondary = OnSlate40,
    secondaryContainer = SlateContainer40,
    onSecondaryContainer = OnSlateContainer40,
    tertiary = Amber40,
    onTertiary = OnAmber40,
    tertiaryContainer = AmberContainer40,
    onTertiaryContainer = OnAmberContainer40,
    background = Surface40,
    onBackground = OnSurface40,
    surface = Surface40,
    onSurface = OnSurface40,
    surfaceVariant = SurfaceVariant40,
    onSurfaceVariant = OnSurfaceVariant40,
    surfaceTint = Teal40,
    inverseSurface = InverseSurface40,
    inverseOnSurface = InverseOnSurface40,
    error = Error40,
    onError = Color.White,
    errorContainer = ErrorContainer40,
    onErrorContainer = OnErrorContainer40,
    outline = Outline40,
    outlineVariant = OutlineVariant40,
    scrim = Color.Black,
    surfaceBright = Surface40,
    surfaceDim = SurfaceDim40,
    surfaceContainerLowest = SurfaceContainerLowest40,
    surfaceContainerLow = SurfaceContainerLow40,
    surfaceContainer = SurfaceContainer40,
    surfaceContainerHigh = SurfaceContainerHigh40,
    surfaceContainerHighest = SurfaceContainerHighest40,
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
