package dev.redplate.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Moves one session to another day.
 *
 * The schedule was entirely derived from the split, so a week that did not suit could only
 * be changed by regenerating the whole block. Dropping a session onto an occupied day
 * swaps the two, which is what the user means and the only outcome that leaves a week the
 * app can still represent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSessionSheet(
    sessionName: String,
    days: List<DayCard>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            MonoLabel(text = "MOVE $sessionName")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pick the day it should happen on. Landing on a day that's already " +
                    "taken swaps the two sessions.",
                style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { day ->
                    val occupied = day.templateId != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceRaised)
                            .clickable { onPick(day.weekdayIndex) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = day.dayLabel,
                            style = RedplateType.mono.copy(fontSize = 11.sp),
                            color = colors.inkMuted,
                            modifier = Modifier.width(38.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (occupied) "${day.sessionName} — swap" else "Free",
                            style = RedplateType.body.copy(fontSize = 15.sp),
                            color = if (occupied) colors.inkSecondary else colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SecondaryButton(label = "Keep it where it is", onClick = onDismiss)
        }
    }
}

/**
 * Edits the weekly cap per muscle.
 *
 * "Adjust targets" used to call a reset that wrote the defaults back over the defaults, so
 * pressing it changed nothing and looked broken. These are the numbers every volume
 * readout in the app compares against, so they are worth being able to actually set —
 * saved rows are marked user-adjusted and survive a block rebuild.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeTargetSheet(
    targets: Map<MuscleGroup, Int>,
    onAdjust: (MuscleGroup, Int) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            MonoLabel(text = "WEEKLY SET CAP")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The most hard sets a week you want each muscle to take. Every volume " +
                    "bar in the app measures against these.",
                style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                targets.entries.sortedBy { it.key.name }.forEach { (muscle, cap) ->
                    TargetRow(muscle = muscle, cap = cap, onAdjust = onAdjust)
                }
            }

            Spacer(Modifier.height(12.dp))
            SecondaryButton(label = "Back to the defaults", onClick = onReset)
            Spacer(Modifier.height(8.dp))
            PrimaryBar(label = "Save targets", onClick = onSave)
        }
    }
}

@Composable
private fun TargetRow(muscle: MuscleGroup, cap: Int, onAdjust: (MuscleGroup, Int) -> Unit) {
    val colors = RedplateTheme.colors
    val name = muscle.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceRaised)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = RedplateType.body.copy(fontSize = 14.5.sp),
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        StepKey(label = "−", description = "Fewer $name sets") { onAdjust(muscle, -1) }
        Text(
            text = cap.toString(),
            style = RedplateType.figure.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            modifier = Modifier.width(44.dp).padding(horizontal = 6.dp),
        )
        StepKey(label = "+", description = "More $name sets") { onAdjust(muscle, 1) }
    }
}

@Composable
private fun StepKey(label: String, description: String, onClick: () -> Unit) {
    val colors = RedplateTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RedplateType.body.copy(fontSize = 20.sp),
            color = colors.ink,
        )
    }
}

@Preview(name = "Volume target row", widthDp = 384, showBackground = true, backgroundColor = 0xFF1A1E24)
@Composable
private fun TargetRowPreview() {
    RedplateTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetRow(muscle = MuscleGroup.CHEST, cap = 20, onAdjust = { _, _ -> })
            TargetRow(muscle = MuscleGroup.HAMSTRINGS, cap = 18, onAdjust = { _, _ -> })
        }
    }
}
