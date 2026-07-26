package dev.redplate.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Bordered contextual info card with "i" prefix.
 */
@Composable
fun InfoCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Row(
        modifier = modifier
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "i",
            style = RedplateType.body,
            color = colors.inkMuted,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = RedplateType.body,
            color = colors.inkSecondary,
        )
    }
}
