package dev.redplate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Always dark. minSdk 36 > S so no dynamic-color branch needed.
// live (#FF5C1A) is primary; ground (#000000) is the background.
private val RedplateColorScheme = darkColorScheme(
    primary = Color(0xFFFF5C1A),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF3D1400),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFF8B939E),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F0),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFF5F5F0),
    onSurfaceVariant = Color(0xFF8B939E),
    outline = Color(0xFF2A2F36),
    outlineVariant = Color(0xFF2A2F36),
)

@Composable
fun RedplateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RedplateColorScheme,
        typography = RedplateTypography,
        content = content
    )
}
