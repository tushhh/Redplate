package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Two-row grid of difficulty chips that map to RIR values.
 * Tapping the already-selected chip deselects (returns null).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DifficultyChips(
    selected: Difficulty?,
    onSelect: (Difficulty?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Difficulty.entries.forEach { difficulty ->
            val isSelected = difficulty == selected
            val shape = RoundedCornerShape(12.dp)

            Box(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (isSelected) Modifier.background(colors.live)
                        else Modifier.border(1.dp, colors.line, shape)
                    )
                    .clickable {
                        onSelect(if (isSelected) null else difficulty)
                    }
                    .semantics { role = Role.RadioButton }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = difficulty.label,
                    style = RedplateType.label,
                    color = if (isSelected) colors.inkOnLight else colors.inkSecondary,
                )
            }
        }
    }
}
