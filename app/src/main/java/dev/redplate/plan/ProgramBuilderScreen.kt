package dev.redplate.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.redplate.data.ProgressionRule
import dev.redplate.data.TemplateSlotEntity
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

@Composable
fun ProgramBuilderRoute(
    onBack: () -> Unit = {},
) {
    val viewModel: ProgramBuilderViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    ProgramBuilderScreen(
        state = state,
        onIncrementSets = viewModel::incrementSets,
        onDecrementSets = viewModel::decrementSets,
        onSave = onBack,
    )
}

@Composable
fun ProgramBuilderScreen(
    state: ProgramBuilderState,
    onIncrementSets: (Long) -> Unit = {},
    onDecrementSets: (Long) -> Unit = {},
    onSave: () -> Unit = {},
) {
    val colors = RedplateTheme.colors

    if (state.isLoading) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // Header
            Text(
                text = state.sessionName,
                style = RedplateType.headline,
                color = colors.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Editing the template, not just today",
                style = RedplateType.body.copy(fontSize = 14.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(20.dp))

            // Exercise rows with set steppers
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.slots.forEachIndexed { index, row ->
                    ExerciseSlotRow(
                        index = index + 1,
                        row = row,
                        onIncrement = { onIncrementSets(row.slot.id) },
                        onDecrement = { onDecrementSets(row.slot.id) },
                    )
                }

                // "Add an exercise" dashed row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                        .clickable {},
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+ Add an exercise",
                        style = RedplateType.body.copy(fontSize = 15.sp),
                        color = colors.inkMuted,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Weekly effect panel
            if (state.volumeDeltas.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = "WEEKLY EFFECT OF THIS EDIT",
                            style = RedplateType.mono.copy(fontSize = 10.sp),
                            color = colors.inkMuted,
                        )
                        Spacer(Modifier.height(10.dp))
                        state.volumeDeltas.forEach { delta ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = delta.muscleName.uppercase(),
                                    style = RedplateType.mono.copy(fontSize = 10.sp),
                                    color = colors.inkMuted,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (delta.delta > 0) "+${delta.delta}" else "${delta.delta}",
                                    style = RedplateType.mono.copy(fontSize = 10.sp),
                                    color = if (delta.delta > 0) colors.safe else colors.live,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // Primary bar
        PrimaryBar(
            label = "Save the change",
            onClick = onSave,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ExerciseSlotRow(
    index: Int,
    row: SlotRow,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val slot = row.slot

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Order number
        Text(
            text = "$index",
            style = RedplateType.mono.copy(fontSize = 12.sp),
            color = colors.inkMuted,
            modifier = Modifier.width(24.dp),
        )

        // Exercise name + rep range
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.exerciseName,
                style = RedplateType.body.copy(fontSize = 15.sp),
                color = colors.ink,
            )
            Text(
                text = "${slot.repRangeLow}\u2013${slot.repRangeHigh} REPS",
                style = RedplateType.mono.copy(fontSize = 10.sp),
                color = colors.inkSubtle,
            )
        }

        // Set stepper: − [count] +
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Minus button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceRaised)
                    .clickable(onClick = onDecrement),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u2212",
                    style = RedplateType.title.copy(fontSize = 20.sp),
                    color = colors.inkSecondary,
                )
            }

            // Count
            Text(
                text = "${slot.targetSets}",
                style = RedplateType.figure.copy(fontSize = 20.sp),
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
            )

            // Plus button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceRaised)
                    .clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    style = RedplateType.title.copy(fontSize = 20.sp),
                    color = colors.inkSecondary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ProgramBuilderScreenPreview() {
    RedplateTheme {
        ProgramBuilderScreen(
            state = ProgramBuilderState(
                sessionName = "Upper A",
                slots = listOf(
                    SlotRow(
                        slot = TemplateSlotEntity(
                            id = 1, templateId = 1, exerciseId = "bench",
                            orderIndex = 0, targetSets = 4, repRangeLow = 6,
                            repRangeHigh = 8, targetRir = 2, restSeconds = 180,
                            progression = ProgressionRule.DOUBLE_PROGRESSION,
                        ),
                        exerciseName = "Bench Press",
                    ),
                    SlotRow(
                        slot = TemplateSlotEntity(
                            id = 2, templateId = 1, exerciseId = "ohp",
                            orderIndex = 1, targetSets = 3, repRangeLow = 8,
                            repRangeHigh = 12, targetRir = 2, restSeconds = 120,
                            progression = ProgressionRule.DOUBLE_PROGRESSION,
                        ),
                        exerciseName = "Overhead Press",
                    ),
                ),
                volumeDeltas = listOf(
                    VolumeDelta("Chest", +2),
                    VolumeDelta("Triceps", +1),
                ),
                isLoading = false,
            ),
        )
    }
}
