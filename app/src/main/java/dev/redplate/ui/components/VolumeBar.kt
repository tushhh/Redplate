package dev.redplate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Horizontal volume progress bar with 3 visual states:
 * - Under target: info blue fill
 * - Near cap: info blue fill (approaching full)
 * - Over cap: live orange fill (exceeds target)
 */
@Composable
fun VolumeBar(
    label: String,
    current: Int,
    target: Int,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    val fraction = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1.5f) else 0f
    val barColor = if (fraction > 1f) colors.live else colors.info

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                style = RedplateType.mono,
                color = colors.inkMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$current/$target",
                style = RedplateType.mono,
                color = colors.inkMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surfaceRaised),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtMost(1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
    }
}
