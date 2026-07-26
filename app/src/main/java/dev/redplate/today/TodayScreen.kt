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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    onEditSession: (Long) -> Unit = {},
) {
    val viewModel: TodayViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // Today is a summary of state other screens change — a finished session, a new
    // program. It used to load once in init and then never again, so it still showed
    // "Let's go" for a workout already done. Refreshing on resume keeps it honest.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TodayScreen(
        state = state,
        onStartWorkout = { viewModel.startSession(onStartWorkout) },
        onPickExercise = onPickExercise,
        onEditSession = onEditSession,
    )
}

@Composable
fun TodayScreen(
    state: TodayState,
    onStartWorkout: () -> Unit,
    onPickExercise: () -> Unit,
    onEditSession: (Long) -> Unit = {},
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
            TrainingDayScreen(
                state = state,
                onStartWorkout = onStartWorkout,
                onEditSession = onEditSession,
            )
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
    onEditSession: (Long) -> Unit,
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
            SessionCardView(
                card = state.sessionCard,
                onEditSession = { onEditSession(state.sessionCard.templateId) },
            )
            Spacer(Modifier.height(10.dp))

            // Volume footer
            VolumeFooter(
                rows = state.volumeRows,
                coachLine = state.volumeCoachLine,
            )
            Spacer(Modifier.height(16.dp))
        }

        // Primary bar. This used to open a "How much time today?" dialog whose answer
        // was then thrown away — a centred dialog, above the thumb arc, that added a tap
        // and changed nothing. The session length already comes from the profile ceiling,
        // so the primary action starts the workout.
        PrimaryBar(
            label = state.primaryLabel,
            onClick = onStartWorkout,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}

@Composable
private fun SessionCardView(card: SessionCard, onEditSession: () -> Unit) {
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

        // "N more · edit today" row. It had a chevron and no click handler, so it read
        // as a link and behaved as decoration; it now opens the session in the builder.
        if (card.remainingCount > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceRaised)
                    .clickable(onClick = onEditSession)
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
