package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.components.Chevron
import dev.redplate.ui.components.MovementWindow
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SheetHandle
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/** What the guidance sheet renders. Missing pieces are normal and are simply omitted. */
data class GuidanceState(
    val exerciseName: String,
    val muscleTags: List<String>,
    val instructions: List<String>,
    val primaryMuscle: MuscleGroup,
    val imageUri: String? = null,
    val endImageUri: String? = null,
    val substituteCount: Int = 0,
)

/** An exercise the user could do instead, with the kit it needs and how much it covers. */
data class SubstituteOption(
    val exerciseId: String,
    val name: String,
    val equipmentLabel: String,
    /** Share of the original's muscles this also trains, 0–100. */
    val overlapPercent: Int,
    val startImageUri: String? = null,
    val endImageUri: String? = null,
    val primaryMuscle: MuscleGroup = MuscleGroup.CHEST,
)

/**
 * Guidance — design 8b.
 *
 * One window alternating start and finish rather than two stills side by side: the same
 * two files, twice the information, with the written cues under it where they can be read
 * while it plays. A bottom sheet, never a centred dialog — on a 162 mm phone a dialog puts
 * its content *and* its dismiss control above the thumb arc.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuidanceSheet(
    state: GuidanceState,
    onDismiss: () -> Unit,
    onOpenSwap: () -> Unit,
    onGotIt: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = { SheetHandle(Modifier.padding(top = 12.dp, bottom = 12.dp)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = state.exerciseName,
                style = RedplateType.title.copy(fontSize = 25.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(9.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                state.muscleTags.forEachIndexed { index, tag ->
                    Text(
                        text = tag.uppercase(),
                        style = RedplateType.mono.copy(fontSize = 10.sp),
                        color = if (index == 0) colors.inkBright else colors.inkMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.surfaceRaised)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            MovementWindow(
                startImageUri = state.imageUri,
                endImageUri = state.endImageUri,
                muscle = state.primaryMuscle,
                contentDescription = state.exerciseName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(186.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(12.dp))

            state.instructions.forEachIndexed { index, step ->
                Row(Modifier.padding(bottom = 5.dp)) {
                    Text(
                        text = "${index + 1}",
                        style = RedplateType.body.copy(fontSize = 14.sp),
                        color = colors.inkMuted,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        text = step,
                        style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                        color = colors.inkBright,
                    )
                }
            }
            if (state.instructions.isNotEmpty()) Spacer(Modifier.height(7.dp))

            // A rack being occupied is the most common reason to open this sheet.
            if (state.substituteCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surfaceRaised)
                        .clickable(onClick = onOpenSwap)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Swap for something else",
                        style = RedplateType.body.copy(fontSize = 14.5.sp),
                        color = colors.inkBright,
                        modifier = Modifier.weight(1f),
                    )
                    Chevron()
                }
                Spacer(Modifier.height(10.dp))
            }

            PrimaryBar(label = "Got it — first set", onClick = onGotIt)
            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * Swap — design 8d.
 *
 * A thumbnail per substitute, which is the fastest way to tell a machine press from a dip
 * without reading the equipment tag. Sets already logged stay logged; swapping changes
 * what comes next, never what happened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapSheet(
    exerciseName: String,
    loggedSetCount: Int,
    substitutes: List<SubstituteOption>,
    onDismiss: () -> Unit,
    onSwap: (exerciseId: String) -> Unit,
) {
    val colors = RedplateTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = { SheetHandle(Modifier.padding(top = 12.dp, bottom = 12.dp)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "Swap the ${exerciseName.lowercase()}",
                style = RedplateType.title.copy(fontSize = 25.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (loggedSetCount > 0) {
                    "$loggedSetCount set${if (loggedSetCount == 1) "" else "s"} already " +
                        "logged — they stay. Same muscles, kit you have."
                } else {
                    "Same muscles, kit you have."
                },
                style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(12.dp))

            substitutes.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surfaceRaised)
                        .clickable { onSwap(option.exerciseId) }
                        .padding(start = 9.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MovementWindow(
                        startImageUri = option.startImageUri,
                        endImageUri = option.endImageUri,
                        muscle = option.primaryMuscle,
                        attribution = null,
                        modifier = Modifier
                            .size(width = 76.dp, height = 60.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = option.name,
                            style = RedplateType.body.copy(fontSize = 15.sp),
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${option.overlapPercent}% OVERLAP · " +
                                option.equipmentLabel.uppercase(),
                            style = RedplateType.mono.copy(fontSize = 10.5.sp),
                            color = colors.inkMuted,
                        )
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(colors.surface),
                        contentAlignment = Alignment.Center,
                    ) { Chevron(colors.inkSecondary) }
                }
            }

            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surfaceRaised)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Keep the ${exerciseName.lowercase()}",
                    style = RedplateType.action.copy(fontSize = 19.sp),
                    color = colors.inkSecondary,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Preview(name = "8b · guidance content", widthDp = 384, showBackground = true, backgroundColor = 0xFF1A1E24)
@Composable
private fun GuidanceContentPreview() {
    RedplateTheme {
        // ModalBottomSheet cannot render in a preview, so this shows the sheet's body.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RedplateTheme.colors.surface)
                .padding(20.dp),
        ) {
            Text(
                text = "Incline Dumbbell Press",
                style = RedplateType.title.copy(fontSize = 25.sp),
                color = RedplateTheme.colors.ink,
            )
            Spacer(Modifier.height(12.dp))
            MovementWindow(
                startImageUri = null,
                endImageUri = null,
                muscle = MuscleGroup.CHEST,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(186.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
    }
}
