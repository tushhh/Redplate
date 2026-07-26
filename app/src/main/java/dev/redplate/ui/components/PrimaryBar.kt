package dev.redplate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Full-width 88dp primary action bar. CLAUDE.md §4:
 * "Primary action is a full-width bar, 88 dp tall, spanning all 384 dp."
 */
@Composable
fun PrimaryBar(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(88.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) colors.live else colors.surface)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RedplateType.action.copy(fontSize = 20.sp),
            color = if (enabled) colors.inkOnLight else colors.inkMuted,
        )
    }
}

@Preview
@Composable
private fun PrimaryBarPreview() {
    RedplateTheme {
        PrimaryBar(label = "Let's go", onClick = {})
    }
}

@Preview
@Composable
private fun PrimaryBarDisabledPreview() {
    RedplateTheme {
        PrimaryBar(label = "Next", onClick = {}, enabled = false)
    }
}
