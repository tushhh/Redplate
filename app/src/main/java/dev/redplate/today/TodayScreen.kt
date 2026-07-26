package dev.redplate.today

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import dev.redplate.ui.components.CoachHeadline
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.VolumeBar
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.PlexMono
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

@Composable
fun TodayRoute(
    onStartWorkout: (Long, String) -> Unit,
    onPickExercise: () -> Unit,
) {
    val viewModel: TodayViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    TodayScreen(
        state = state,
        onStartWorkout = { viewModel.startSession(onStartWorkout) },
        onPickExercise = onPickExercise,
    )
}

@Composable
fun TodayScreen(
    state: TodayState,
    onStartWorkout: () -> Unit,
    onPickExercise: () -> Unit,
) {
    val colors = RedplateTheme.colors

    when (state) {
        TodayState.Loading -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.ground),
                contentAlignment = Alignment.Center,
            ) {}
        }

        TodayState.NoProgramYet -> {
            NoProgramScreen(onPickExercise = onPickExercise)
        }

        is TodayState.TrainingDay -> {
            TrainingDayScreen(state = state, onStartWorkout = onStartWorkout)
        }

        is TodayState.RestDay -> {
            RestDayScreen(state = state)
        }
    }
}

@Composable
private fun TrainingDayScreen(
    state: TodayState.TrainingDay,
    onStartWorkout: () -> Unit,
) {
    val colors = RedplateTheme.colors
    var showTimeDialog by remember { mutableStateOf(false) }

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
            Spacer(Modifier.height(22.dp))

            // Eyebrow
            MonoLabel(text = state.eyebrow)
            Spacer(Modifier.height(10.dp))

            // Coach headline
            CoachHeadline(text = state.headline)
            Spacer(Modifier.height(5.dp))

            // Coach body
            Text(
                text = state.coachBody,
                style = RedplateType.body,
                color = colors.inkSecondary,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(10.dp))

            // Session card
            SessionCardView(card = state.sessionCard)
            Spacer(Modifier.height(10.dp))

            // Volume footer
            VolumeFooter(
                rows = state.volumeRows,
                coachLine = state.volumeCoachLine,
            )
            Spacer(Modifier.height(16.dp))
        }

        // Primary bar
        PrimaryBar(
            label = state.primaryLabel,
            onClick = { showTimeDialog = true },
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }

    if (showTimeDialog) {
        SessionTimeDialog(
            estimatedMinutes = state.sessionCard.estimatedMinutes,
            onDismiss = { showTimeDialog = false },
            onConfirm = {
                showTimeDialog = false
                onStartWorkout()
            },
        )
    }
}

@Composable
private fun SessionTimeDialog(
    estimatedMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val timeOptions = listOf(30, 45, 60, 75, 90)
    var selectedMinutes by remember { mutableStateOf(
        timeOptions.minByOrNull { kotlin.math.abs(it - estimatedMinutes) } ?: 60
    ) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "How much time today?",
                style = RedplateType.title.copy(fontSize = 22.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Session will be planned to fit.",
                style = RedplateType.body.copy(fontSize = 13.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                timeOptions.forEach { minutes ->
                    val isSelected = minutes == selectedMinutes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) colors.live else colors.ground)
                            .clickable { selectedMinutes = minutes },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${minutes}m",
                            style = RedplateType.body.copy(
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (isSelected) colors.ground else colors.ink,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            PrimaryBar(
                label = "Start · ${selectedMinutes} min",
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun SessionCardView(card: SessionCard) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .padding(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = card.label,
                style = RedplateType.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
            Text(
                text = "${card.totalSets} SETS · ${card.estimatedMinutes} MIN",
                style = RedplateType.mono,
                color = colors.inkMuted,
            )
        }

        Spacer(Modifier.height(14.dp))

        // Exercise rows
        card.exercises.forEach { exercise ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Order badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%02d".format(exercise.orderIndex),
                        style = RedplateType.body.copy(
                            fontFamily = PlexCondensed,
                            fontSize = 16.sp,
                        ),
                        color = colors.inkMuted,
                    )
                }

                Spacer(Modifier.width(13.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        style = RedplateType.body.copy(fontSize = 14.5.sp),
                        color = colors.ink,
                    )
                    Text(
                        text = exercise.prescription,
                        style = RedplateType.mono.copy(fontSize = 10.5.sp),
                        color = colors.inkMuted,
                    )
                }

                // Weight change indicator
                if (exercise.loadNote != null) {
                    Text(
                        text = exercise.loadNote,
                        style = RedplateType.mono.copy(fontSize = 10.sp, letterSpacing = 0.08.em),
                        color = colors.live,
                    )
                }
            }
        }

        // "N more · edit today" row
        if (card.remainingCount > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceRaised)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${card.remainingCount} more exercises · edit today",
                    style = RedplateType.body.copy(fontSize = 14.5.sp),
                    color = colors.inkBright,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "›",
                    style = RedplateType.title,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun VolumeFooter(
    rows: List<VolumeRow>,
    coachLine: String,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        MonoLabel(text = "THIS WEEK · SETS VS TARGET")
        Spacer(Modifier.height(11.dp))

        rows.forEach { row ->
            VolumeBar(
                label = row.label,
                current = row.current,
                target = row.target,
            )
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = coachLine,
            style = RedplateType.body.copy(fontSize = 12.5.sp),
            color = colors.inkMuted,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun RestDayScreen(state: TodayState.RestDay) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(22.dp))
        MonoLabel(text = state.eyebrow)
        Spacer(Modifier.height(10.dp))
        CoachHeadline(text = state.headline)
        Spacer(Modifier.height(5.dp))
        Text(
            text = state.coachBody,
            style = RedplateType.body,
            color = colors.inkSecondary,
        )
        if (state.nextSessionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Next: ${state.nextSessionLabel}",
                    style = RedplateType.body.copy(fontSize = 15.sp),
                    color = colors.inkSubtle,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NoProgramScreen(onPickExercise: () -> Unit) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        CoachHeadline(text = "No program yet.")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Set one up in the Plan tab, or just pick some exercises and train.",
            style = RedplateType.body,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .clickable(onClick = onPickExercise),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Pick exercises",
                style = RedplateType.action,
                color = colors.ink,
            )
        }
    }
}

// ── Previews ──

@Preview
@Composable
private fun TodayTrainingDayPreview() {
    RedplateTheme {
        TodayScreen(
            state = TodayState.TrainingDay(
                eyebrow = "FRIDAY MORNING · WEEK 3 OF 5",
                headline = "Push day. About an hour.",
                coachBody = "Bench goes up to 102.5 kg — you finished every set with two left on Tuesday.",
                sessionCard = SessionCard(
                    label = "Upper A",
                    totalSets = 20,
                    estimatedMinutes = 58,
                    exercises = listOf(
                        ExerciseRow(1, "Barbell Bench Press", "4 × 6–10 · 102.5 kg", "+2.5"),
                        ExerciseRow(2, "Barbell Row", "4 × 6–10 · 80 kg", null),
                        ExerciseRow(3, "Overhead Press", "3 × 8–12 · 45 kg", null),
                    ),
                    remainingCount = 3,
                    templateId = 1L,
                ),
                volumeRows = listOf(
                    VolumeRow("Chest", 11, 18),
                    VolumeRow("Back", 16, 20),
                    VolumeRow("Shoulders", 13, 24),
                ),
                volumeCoachLine = "Quads are light this week — Saturday covers it.",
                primaryLabel = "Let's go",
                isFirstSession = false,
            ),
            onStartWorkout = {},
            onPickExercise = {},
        )
    }
}

@Preview
@Composable
private fun TodayFirstDayPreview() {
    RedplateTheme {
        TodayScreen(
            state = TodayState.TrainingDay(
                eyebrow = "SATURDAY · WEEK 1, SESSION 1",
                headline = "First one. Go light on purpose.",
                coachBody = "Pick a weight you could manage two more reps with. Today sets the baseline — every number after this is built off it.",
                sessionCard = SessionCard(
                    label = "Upper A",
                    totalSets = 14,
                    estimatedMinutes = 45,
                    exercises = listOf(
                        ExerciseRow(1, "Barbell Bench Press", "4 × 6–10 · you choose", null),
                        ExerciseRow(2, "Barbell Row", "4 × 6–10 · you choose", null),
                        ExerciseRow(3, "Overhead Press", "3 × 8–12 · you choose", null),
                    ),
                    remainingCount = 2,
                    templateId = 1L,
                ),
                volumeRows = listOf(
                    VolumeRow("Chest", 0, 18),
                    VolumeRow("Back", 0, 20),
                ),
                volumeCoachLine = "Fills in as you log. Trends need three sessions.",
                primaryLabel = "Start Upper A",
                isFirstSession = true,
            ),
            onStartWorkout = {},
            onPickExercise = {},
        )
    }
}

@Preview
@Composable
private fun TodayNoProgramPreview() {
    RedplateTheme {
        TodayScreen(
            state = TodayState.NoProgramYet,
            onStartWorkout = {},
            onPickExercise = {},
        )
    }
}
