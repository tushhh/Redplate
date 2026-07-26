package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * 2×3 grid of plain-language difficulty chips.
 * Selected = white bg with bold text, unselected = surface bg.
 *
 * Row 1: Easy / 3 more in me / 2 more in me
 * Row 2: 1 more, maybe / All I had / Failed the rep
 */
@Composable
fun DifficultyChips(
    selected: Difficulty?,
    onSelect: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    val row1 = listOf(Difficulty.EASY, Difficulty.THREE_LEFT, Difficulty.TWO_LEFT)
    val row2 = listOf(Difficulty.ONE_LEFT, Difficulty.ALL_OUT, Difficulty.FAILED)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChipRow(chips = row1, selected = selected, onSelect = onSelect)
        ChipRow(chips = row2, selected = selected, onSelect = onSelect)
    }
}

@Composable
private fun ChipRow(
    chips: List<Difficulty>,
    selected: Difficulty?,
    onSelect: (Difficulty) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { difficulty ->
            val isSelected = difficulty == selected
            DifficultyChip(
                label = difficulty.label,
                isSelected = isSelected,
                onClick = { onSelect(difficulty) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DifficultyChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.ink else colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RedplateType.body.copy(
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (isSelected) colors.inkOnLight else colors.inkSecondary,
        )
    }
}

@Preview
@Composable
private fun DifficultyChipsPreview() {
    RedplateTheme {
        DifficultyChips(
            selected = Difficulty.TWO_LEFT,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
