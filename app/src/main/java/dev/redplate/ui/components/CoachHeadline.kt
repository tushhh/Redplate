package dev.redplate.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Large condensed coach messaging headline.
 * "Push day. About an hour." — 32sp PlexCondensed SemiBold.
 */
@Composable
fun CoachHeadline(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = RedplateType.headline,
        color = RedplateTheme.colors.ink,
        modifier = modifier,
    )
}
