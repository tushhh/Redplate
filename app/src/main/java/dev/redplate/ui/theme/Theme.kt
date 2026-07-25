package dev.redplate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalRedplateColors = staticCompositionLocalOf { RedplateColors() }

object RedplateTheme {
    val colors: RedplateColors
        @Composable @ReadOnlyComposable
        get() = LocalRedplateColors.current
}

private val RedplateColorScheme = darkColorScheme(
    primary = Color(0xFFFF5C1A),
    onPrimary = Color(0xFF101317),
    primaryContainer = Color(0xFF3D1400),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFF8B939E),
    onSecondary = Color(0xFF101317),
    background = Color(0xFF101317),
    onBackground = Color(0xFFF5F5F0),
    surface = Color(0xFF1A1E24),
    onSurface = Color(0xFFF5F5F0),
    surfaceVariant = Color(0xFF1A1E24),
    onSurfaceVariant = Color(0xFF8B939E),
    surfaceContainerLowest = Color(0xFF101317),
    surfaceContainerLow = Color(0xFF151920),
    surfaceContainer = Color(0xFF1A1E24),
    surfaceContainerHigh = Color(0xFF242A32),
    surfaceContainerHighest = Color(0xFF2A2F36),
    outline = Color(0xFF2A2F36),
    outlineVariant = Color(0xFF2A2F36),
    inverseSurface = Color(0xFFF5F5F0),
    inverseOnSurface = Color(0xFF101317),
    inversePrimary = Color(0xFFB84000),
    scrim = Color(0xFF101317),
)

@Composable
fun RedplateTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRedplateColors provides RedplateColors()) {
        MaterialTheme(
            colorScheme = RedplateColorScheme,
            typography = RedplateTypography,
            content = content,
        )
    }
}
