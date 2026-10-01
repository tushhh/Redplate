package dev.redplate.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.redplate.ui.components.CoachHeadline
import dev.redplate.ui.components.InfoNote
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.ScreenHeader
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import dev.redplate.workout.LoadEntrySheet

@Composable
fun StartingWeightsRoute(onDone: () -> Unit) {
    val viewModel: StartingWeightsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    StartingWeightsScreen(state = state, onBack = onDone, onEdit = viewModel::edit, onDone = onDone)

    state.editing?.let { row ->
        LoadEntrySheet(
            entry = state.entry,
            unitLabel = row.unitLabel,
            allowsDecimal = !row.wholeNumbersOnly,
            canCommit = state.canCommit,
            onDigit = viewModel::appendDigit,
            onBackspace = viewModel::backspace,
            onCommit = viewModel::commit,
            onDismiss = viewModel::cancel,
            title = row.name.uppercase(),
        )
    }
}

/**
 * Every lift in the block and the weight it opens at. Tap one, type the number you
 * already work with. Lifts left blank open light and set themselves from the first set.
 */
@Composable
fun StartingWeightsScreen(
    state: StartingWeightsState,
    onBack: () -> Unit,
    onEdit: (StartingWeightRow) -> Unit,
    onDone: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .systemBarsPadding(),
    ) {
        ScreenHeader(
            title = "Starting weights",
            subtitle = "${state.setCount} OF ${state.rows.size} SET",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            CoachHeadline(text = "What do you already lift?")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Your working weight for each — what you'd do for a solid set of " +
                    "8 with a couple left in the tank. Read it off the machine as printed.",
                style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.rows.forEach { row -> WeightRow(row = row, onClick = { onEdit(row) }) }
            }

            Spacer(Modifier.height(12.dp))
            InfoNote(
                text = "Not sure about one? Leave it. It opens light, and the first set you " +
                    "log becomes its starting point.",
            )
            Spacer(Modifier.height(16.dp))
        }

        PrimaryBar(
            label = "Done",
            onClick = onDone,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun WeightRow(row: StartingWeightRow, onClick: () -> Unit) {
    val colors = RedplateTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "${row.name}, " +
                    (row.loadLabel?.let { "$it ${row.unitLabel}" } ?: "no starting weight") +
                    ". Tap to type it."
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = RedplateType.body.copy(fontSize = 15.sp),
                color = colors.ink,
            )
            Text(
                text = listOfNotNull(row.station?.uppercase(), row.sessions).joinToString(" · "),
                style = RedplateType.mono.copy(fontSize = 9.5.sp),
                color = colors.inkMuted,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (row.loadLabel != null) {
            Text(
                text = row.loadLabel,
                style = RedplateType.figure.copy(fontFamily = PlexCondensed, fontSize = 26.sp),
                color = colors.ink,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = row.unitLabel,
                style = RedplateType.mono.copy(fontSize = 10.sp),
                color = colors.inkMuted,
            )
        } else {
            Text(
                text = "Set",
                style = RedplateType.body.copy(fontSize = 14.sp),
                color = colors.inkSecondary,
            )
        }
    }
}

@Preview(name = "Starting weights", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun StartingWeightsPreview() {
    RedplateTheme {
        StartingWeightsScreen(
            state = StartingWeightsState(
                isLoading = false,
                rows = listOf(
                    StartingWeightRow("machine_chest_press", "Machine Chest Press", "Chest Press Machine", "55", "KG", false, "UPPER A · UPPER B"),
                    StartingWeightRow("lat_pulldown_wide", "Wide-Grip Lat Pulldown", "Multi-Gym · Lat Pulldown", "70", "KG", false, "UPPER A"),
                    StartingWeightRow("machine_shoulder_press", "Machine Shoulder Press", "Shoulder Press Machine", "45", "KG", false, "UPPER A"),
                    StartingWeightRow("db_lateral_raise", "Dumbbell Lateral Raise", "Dumbbells", "10", "KG EACH", false, "UPPER A"),
                    StartingWeightRow("leg_press", "Leg Press", "Leg Press", null, "KG", false, "LOWER A"),
                ),
            ),
            onBack = {},
            onEdit = {},
            onDone = {},
        )
    }
}
