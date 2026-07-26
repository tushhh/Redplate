package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.CoachHeadline
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.components.VolumeBar
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.PlexMono
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

@Composable
fun SessionSummaryScreen(
    state: SessionSummaryState,
    onSeeLog: () -> Unit,
    onAddNote: () -> Unit,
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
                style = RedplateType.body,
                color = colors.inkSecondary,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(16.dp))

            // Stats row: SETS / LIFTED / PRs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    label = "SETS",
                    value = state.totalSets.toString(),
                    valueColor = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "LIFTED",
                    value = state.totalTonnage,
                    valueColor = colors.ink,
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

            // Progression changes card
            if (state.progressionChanges.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .padding(horizontal = 17.dp, vertical = 15.dp),
                ) {
                    Text(
                        text = "WHAT CHANGES NEXT TIME",
                        style = RedplateType.mono.copy(fontSize = 9.5.sp),
                        color = colors.inkMuted,
                    )
                    Spacer(Modifier.height(10.dp))

                    state.progressionChanges.forEach { change ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.5.dp),
                        ) {
                            Text(
                                text = change.deltaLabel,
                                style = RedplateType.mono.copy(fontSize = 11.sp),
                                color = if (change.isUp) colors.live else colors.inkMuted,
                                modifier = Modifier.padding(end = 11.dp),
                            )
                            Text(
                                text = change.description,
                                style = RedplateType.body.copy(fontSize = 13.5.sp, lineHeight = 21.sp),
                                color = colors.inkBright,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Volume card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(horizontal = 17.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "WEEK SO FAR",
                    style = RedplateType.mono.copy(fontSize = 9.5.sp),
                    color = colors.inkMuted,
                )
                Spacer(Modifier.height(9.dp))

                state.volumeRows.forEach { row ->
                    VolumeBar(
                        label = row.label,
                        current = row.current,
                        target = row.target,
                    )
                    Spacer(Modifier.height(7.dp))
                }

                Text(
                    text = state.volumeCoachLine,
                    style = RedplateType.body.copy(fontSize = 12.5.sp),
                    color = colors.inkMuted,
                    lineHeight = 19.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // Secondary buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton(
                label = "See the log",
                onClick = onSeeLog,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                label = "Add a note",
                onClick = onAddNote,
                modifier = Modifier.weight(1f),
            )
        }

        // Primary bar
        PrimaryBar(
            label = "Done",
            onClick = onDone,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Text(
            text = label,
            style = RedplateType.mono.copy(fontSize = 9.5.sp),
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            style = RedplateType.title.copy(fontSize = 25.sp),
            color = valueColor,
        )
    }
}

@Preview(widthDp = 384, heightDp = 824, backgroundColor = 0xFF101317, showBackground = true)
@Composable
private fun SessionSummaryPreview() {
    RedplateTheme {
        SessionSummaryScreen(
            state = SessionSummaryState(
                eyebrow = "UPPER A · 61 MINUTES",
                headline = "That was a good one.",
                coachBody = "Twenty sets, one PR, and every set inside its rep range — which is exactly what week 3 is supposed to look like.",
                totalSets = 20,
                totalTonnage = "8.6 t",
                prCount = 1,
                progressionChanges = listOf(
                    ProgressionChange("+2.5", "Bench press — you finished at 1 rep left, so it goes to 105 kg", true),
                    ProgressionChange("HOLD", "Overhead press — 8 reps twice in a row, stay at 45 kg", false),
                    ProgressionChange("−5", "Lat pulldown — fell 4 reps short, dropping to 59 kg", false),
                ),
                volumeRows = listOf(
                    VolumeRow("Chest", 16, 18),
                    VolumeRow("Back", 20, 20),
                ),
                volumeCoachLine = "Back is at its cap — Saturday drops a row.",
            ),
            onSeeLog = {},
            onAddNote = {},
            onDone = {},
        )
    }
}
