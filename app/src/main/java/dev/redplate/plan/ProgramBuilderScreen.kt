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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.components.VolumeDeltaBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Program builder — design 6b. Editing what the engine wrote.
 *
 * The point of this screen is the panel at the bottom: change a set count and watch the
 * weekly total move against your cap, before you commit. Without it a set counter is
 * just a number going up.
 */
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
        onSave = {
            viewModel.commit()
            onBack()
        },
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
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
            Text(
                text = state.sessionName,
                style = RedplateType.headline.copy(fontSize = 28.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Editing the template, not just today",
                style = RedplateType.body.copy(fontSize = 13.sp),
                color = colors.inkMuted,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.slots.forEach { row ->
                SlotRow(
                    row = row,
                    onIncrement = { onIncrementSets(row.slot.id) },
                    onDecrement = { onDecrementSets(row.slot.id) },
                )
            }

            // Adding a slot needs an exercise picker scoped to this template, which does
            // not exist yet. The row is drawn as the design has it but says so rather
            // than opening nothing.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        style = RedplateType.figure.copy(fontSize = 20.sp),
                        color = colors.inkSubtle,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Adding exercises is coming",
                    style = RedplateType.body.copy(fontSize = 14.5.sp),
                    color = colors.inkSubtle,
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        // The whole reason for this screen.
        if (state.volumeEffect.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(horizontal = 17.dp, vertical = 12.dp),
            ) {
                SectionLabel(text = "Weekly effect of this edit")
                Spacer(Modifier.height(11.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.volumeEffect.forEach { effect ->
                        VolumeDeltaBar(
                            label = effect.muscleName,
                            current = effect.current,
                            added = effect.added,
                            target = effect.target,
                        )
                    }
                }
                if (state.effectSummary.isNotEmpty()) {
                    Spacer(Modifier.height(11.dp))
                    Text(
                        text = state.effectSummary,
                        style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                        color = colors.inkMuted,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        PrimaryBar(
            label = if (state.hasChanges) "Save the change" else "Done",
            onClick = onSave,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SlotRow(
    row: SlotRow,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val changed = row.setsDelta != 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (changed) colors.surfaceRaised else colors.surface)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.exerciseName,
                style = RedplateType.body.copy(fontSize = 15.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (changed) {
                    "${row.slot.targetSets - row.setsDelta} → ${row.slot.targetSets} SETS · JUST CHANGED"
                } else {
                    "${row.slot.repRangeLow}–${row.slot.repRangeHigh} REPS · " +
                        row.slot.progression.name.replace('_', ' ')
                },
                style = RedplateType.mono.copy(fontSize = 10.5.sp),
                color = if (changed) colors.live else colors.inkMuted,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SetStepper("−", "Fewer sets of ${row.exerciseName}", onDecrement, changed)
            Text(
                text = row.slot.targetSets.toString(),
                style = RedplateType.figure.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                color = if (changed) colors.live else colors.ink,
                modifier = Modifier.width(26.dp),
            )
            SetStepper("+", "More sets of ${row.exerciseName}", onIncrement, changed)
        }
    }
}

@Composable
private fun SetStepper(
    symbol: String,
    description: String,
    onClick: () -> Unit,
    onRaisedRow: Boolean,
) {
    val colors = RedplateTheme.colors
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (onRaisedRow) colors.surface else colors.surfaceRaised)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = RedplateType.figure.copy(fontSize = 22.sp),
            color = colors.ink,
        )
    }
}

@Preview(name = "6b · program builder", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun ProgramBuilderPreview() {
    RedplateTheme {
        ProgramBuilderScreen(
            state = ProgramBuilderState(
                sessionName = "Upper A",
                templateId = 1L,
                slots = emptyList(),
                volumeEffect = listOf(
                    MuscleEffect("Chest", 11, 2, 18),
                    MuscleEffect("Shoulders", 13, 2, 24),
                ),
                effectSummary = "Two more chest sets a week — still inside your cap.",
                hasChanges = true,
                isLoading = false,
            ),
        )
    }
}
