package dev.redplate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Segmented toggle: FRONT/BACK, 12 WEEKS/6 MONTHS/ALL, etc.
 * Selected = ink bg (white), unselected = transparent within surface container.
 * Design 5a: 64dp tall segments, 15dp radius, within surface bg container.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) colors.ink else colors.surface)
                    .clickable { onOptionSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.uppercase(),
                    style = RedplateType.mono.copy(
                        fontSize = 11.sp,
                        letterSpacing = 0.06.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (selected) colors.inkOnLight else colors.inkMuted,
                )
            }
        }
    }
}
