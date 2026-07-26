package dev.redplate.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Mono eyebrow label: "FRIDAY MORNING · WEEK 3 OF 5".
 * Always uppercase, PlexMono, inkMuted by default.
 */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = RedplateType.mono,
        color = RedplateTheme.colors.inkMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
