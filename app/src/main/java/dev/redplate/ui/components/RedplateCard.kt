package dev.redplate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.redplate.ui.theme.RedplateTheme

/**
 * Standard card: surface bg (#1A1E24), 18dp radius, optional border.
 */
@Composable
fun RedplateCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    borderColor: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(RedplateTheme.colors.surface)
            .then(
                if (borderColor != Color.Transparent)
                    Modifier.border(1.dp, borderColor, shape)
                else Modifier
            )
            .padding(16.dp),
    ) {
        content()
    }
}
