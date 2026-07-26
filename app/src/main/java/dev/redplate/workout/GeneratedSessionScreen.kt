package dev.redplate.workout

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.Chevron
import dev.redplate.ui.components.CoachHeadline
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/** One row of the generated session, with the one-line reason it is there. */
data class GeneratedSlotRow(
    val orderIndex: Int,
    val exerciseId: String,
    val name: String,
    /** "3 × 6–10 · 72.5 KG · 2 RIR" */
    val prescription: String,
    val reason: String?,
)

data class GeneratedSessionState(
    val eyebrow: String = "",
    val headline: String = "",
    val coachBody: String = "",
    val rows: List<GeneratedSlotRow> = emptyList(),
    val minutesLeft: Int = 0,
    val isLoading: Boolean = true,
)

/**
 * What the map produced — design 3d.
 *
 * A real session, ordered compounds-first and fitted to the time and equipment the user
 * has, with a one-line reason beside every exercise and everything swappable before the
 * first set. The point is that the plan is inspectable before it starts, not after.
 */
@Composable
fun GeneratedSessionScreen(
    state: GeneratedSessionState,
    onSwapRow: (exerciseId: String) -> Unit,
    onSave: () -> Unit,
    onStart: () -> Unit,
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
            MonoLabel(text = state.eyebrow)
            Spacer(Modifier.height(8.dp))
            CoachHeadline(text = state.headline)
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.coachBody,
                style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = colors.inkSecondary,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.rows.forEach { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .clickable { onSwapRow(row.exerciseId) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(colors.surfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "%02d".format(row.orderIndex),
                                style = RedplateType.body.copy(
                                    fontFamily = PlexCondensed,
                                    fontSize = 15.sp,
                                ),
                                color = colors.inkMuted,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = row.name,
                                style = RedplateType.body.copy(fontSize = 15.sp),
                                color = colors.ink,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = row.prescription,
                                style = RedplateType.mono.copy(fontSize = 10.5.sp),
                                color = colors.inkMuted,
                            )
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(colors.surfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) { Chevron(colors.inkSecondary) }
                    }

                    if (row.reason != null) {
                        Spacer(Modifier.height(9.dp))
                        Text(
                            text = row.reason,
                            style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                            color = colors.inkSubtle,
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }
                }
            }

            // Drawn as the design has it, and honest about not being built: adding needs
            // a picker scoped to this session, which does not exist yet.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Fitted to your time",
                    style = RedplateType.body.copy(fontSize = 14.5.sp),
                    color = colors.inkSubtle,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${state.minutesLeft} MIN LEFT",
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = colors.inkMuted,
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .clickable(onClick = onSave),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Save it",
                    style = RedplateType.body.copy(fontSize = 14.5.sp),
                    color = colors.inkSecondary,
                )
            }
            PrimaryBar(
                label = "Start training",
                onClick = onStart,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(name = "3d · what the map produced", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun GeneratedSessionPreview() {
    RedplateTheme {
        GeneratedSessionScreen(
            state = GeneratedSessionState(
                eyebrow = "CHEST + BICEPS · YOUR PICK",
                headline = "11 sets, 47 minutes.",
                coachBody = "Presses first while you're fresh, curls after. " +
                    "Tap any row to swap it.",
                rows = listOf(
                    GeneratedSlotRow(
                        1, "incline_barbell_bench", "Incline Barbell Press",
                        "3 × 6–10 · 72.5 KG · 2 RIR",
                        "Chest is 7 sets under target for the week.",
                    ),
                    GeneratedSlotRow(
                        2, "machine_pec_fly", "Machine Chest Fly",
                        "2 × 10–15 · 15 KG · 1 RIR",
                        "Cheap sets — low fatigue with chest already loaded.",
                    ),
                    GeneratedSlotRow(
                        3, "db_bicep_curl", "Dumbbell Bicep Curl",
                        "2 × 10–15 · 17.5 KG · 1 RIR",
                        "Biceps are 6 sets under target for the week.",
                    ),
                ),
                minutesLeft = 13,
                isLoading = false,
            ),
            onSwapRow = {},
            onSave = {},
            onStart = {},
        )
    }
}
