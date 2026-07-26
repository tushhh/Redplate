package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.redplate.ui.components.CoachHeadline
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.components.StatCard
import dev.redplate.ui.components.VolumeBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import dev.redplate.ui.theme.StateColor

data class SessionSummaryState(
    val eyebrow: String,
    val headline: String,
    val coachBody: String,
    val totalSets: Int,
    val totalTonnage: String,
    val prCount: Int,
    val progressionChanges: List<ProgressionChange>,
    val volumeRows: List<VolumeRow>,
    val volumeCoachLine: String,
)

/**
 * One line of "what changes next time". [deltaLabel] is the decision — "+2.5", "HOLD",
 * "−5" — and [description] is why, which is the part other trackers never show.
 */
data class ProgressionChange(
    val deltaLabel: String,
    val description: String,
    val isUp: Boolean,
)

data class VolumeRow(
    val label: String,
    val current: Int,
    val target: Int,
)

/**
 * Session summary — design 7d. Not a trophy cabinet: what happened, and what it changed.
 * Every number here is a consequence rather than a stat.
 */
@Composable
fun SessionSummaryRoute(
    onSeeLog: () -> Unit,
    onDone: () -> Unit,
    viewModel: SessionSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state?.let {
        SessionSummaryScreen(state = it, onSeeLog = onSeeLog, onDone = onDone)
    }
}

@Composable
fun SessionSummaryScreen(
    state: SessionSummaryState,
    onSeeLog: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            MonoLabel(text = state.eyebrow)
            Spacer(Modifier.height(10.dp))
            CoachHeadline(text = state.headline)
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.coachBody,
                style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    label = "Sets",
                    value = state.totalSets.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Lifted",
                    value = state.totalTonnage,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "PRs",
                    value = state.prCount.toString(),
                    valueColor = if (state.prCount > 0) StateColor.pr else colors.ink,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))

            // The part that makes this a coaching screen rather than a receipt.
            if (state.progressionChanges.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .padding(horizontal = 17.dp, vertical = 15.dp),
                ) {
                    SectionLabel(text = "What changes next time")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        state.progressionChanges.forEach { change ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = change.deltaLabel,
                                    style = RedplateType.mono.copy(fontSize = 11.sp),
                                    color = if (change.isUp) colors.live else colors.inkMuted,
                                    modifier = Modifier.width(38.dp),
                                )
                                Spacer(Modifier.width(11.dp))
                                Text(
                                    text = change.description,
                                    style = RedplateType.body.copy(
                                        fontSize = 13.5.sp,
                                        lineHeight = 20.sp,
                                    ),
                                    color = colors.inkBright,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (state.volumeRows.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .padding(horizontal = 17.dp, vertical = 14.dp),
                ) {
                    SectionLabel(text = "Week so far")
                    Spacer(Modifier.height(9.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        state.volumeRows.forEach { row ->
                            VolumeBar(
                                label = row.label,
                                current = row.current,
                                target = row.target,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.volumeCoachLine,
                        style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                        color = colors.inkMuted,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            SecondaryButton(
                label = "See the log",
                onClick = onSeeLog,
                modifier = Modifier.weight(1f),
            )
        }

        PrimaryBar(
            label = "Done",
            onClick = onDone,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(name = "7d · session done", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun SessionSummaryPreview() {
    RedplateTheme {
        SessionSummaryScreen(
            state = SessionSummaryState(
                eyebrow = "UPPER A · 61 MINUTES",
                headline = "That was a good one.",
                coachBody = "Twenty sets, one PR, and every set inside its rep range — " +
                    "which is exactly what week 3 is supposed to look like.",
                totalSets = 20,
                totalTonnage = "8.6 t",
                prCount = 1,
                progressionChanges = listOf(
                    ProgressionChange(
                        "+2.5",
                        "Bench press — you finished at 1 rep left, so it goes to 105 kg",
                        isUp = true,
                    ),
                    ProgressionChange(
                        "HOLD",
                        "Overhead press — 8 reps twice in a row, stay at 45 kg",
                        isUp = false,
                    ),
                    ProgressionChange(
                        "−5",
                        "Lat pulldown — fell 4 reps short, dropping to 59 kg",
                        isUp = false,
                    ),
                ),
                volumeRows = listOf(
                    VolumeRow("Chest", 16, 18),
                    VolumeRow("Back", 20, 20),
                ),
                volumeCoachLine = "Back is at its cap — Saturday drops a row.",
            ),
            onSeeLog = {},
            onDone = {},
        )
    }
}
