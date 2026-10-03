package dev.redplate.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.redplate.ui.components.BorderedCard
import dev.redplate.ui.components.Chevron
import dev.redplate.ui.components.CoachHeadline
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.components.VolumeBar
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import dev.redplate.ui.theme.StateColor

/**
 * Today — design 2a, with the day-one variant (9d) and the stall state (9c).
 *
 * The shape is always the same: one coach sentence answering "what do I do today?",
 * the session card underneath it, and the week's volume as a quiet footer. Status is
 * context, never the headline — nobody opens a gym app to read a dashboard.
 */
@Composable
fun TodayRoute(
    onStartWorkout: (Long, String) -> Unit,
    onPickExercise: () -> Unit,
    onEditSession: (Long) -> Unit = {},
    onSeeFullWeek: () -> Unit = {},
    onSeeSummary: (Long) -> Unit = {},
    onSetStartingWeights: () -> Unit = {},
) {
    val viewModel: TodayViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Today summarises state other screens change — a finished session, a new program.
    // Refreshing on resume keeps it from offering a workout that is already done.
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
        onSeeFullWeek = onSeeFullWeek,
        onTakeDeload = viewModel::takeDeload,
        onDismissStall = viewModel::pushOnThroughStall,
        onSeeSummary = onSeeSummary,
        onTrainAgain = { templateId -> viewModel.startAnotherSession(templateId, onStartWorkout) },
        onTrainAnyway = { viewModel.startTrainAnyway(onStartWorkout) },
        onSetStartingWeights = onSetStartingWeights,
        onResume = { viewModel.resume(onStartWorkout) },
        onFinishOpen = { viewModel.finishOpenSession(onSeeSummary) },
        onBuildPlan = viewModel::buildPlan,
    )
}

@Composable
fun TodayScreen(
    state: TodayState,
    onStartWorkout: () -> Unit,
    onPickExercise: () -> Unit,
    onEditSession: (Long) -> Unit = {},
    onSeeFullWeek: () -> Unit = {},
    onTakeDeload: () -> Unit = {},
    onDismissStall: () -> Unit = {},
    onSeeSummary: (Long) -> Unit = {},
    onTrainAgain: (Long) -> Unit = {},
    onTrainAnyway: () -> Unit = {},
    onSetStartingWeights: () -> Unit = {},
    onResume: () -> Unit = {},
    onFinishOpen: () -> Unit = {},
    onBuildPlan: () -> Unit = {},
) {
    val colors = RedplateTheme.colors

    when (state) {
        TodayState.Loading -> Box(
            Modifier
                .fillMaxSize()
                .background(colors.ground),
        )

        is TodayState.NoProgramYet -> NoProgramScreen(
            state = state,
            onPickExercise = onPickExercise,
            onBuildPlan = onBuildPlan,
            onSeeSummary = onSeeSummary,
        )

        is TodayState.InProgress -> InProgressScreen(
            state = state,
            onResume = onResume,
            onFinish = onFinishOpen,
        )

        is TodayState.Stalled -> StallScreen(
            state = state,
            onTakeDeload = onTakeDeload,
            onSwapLift = onEditSession,
            onPushOn = onDismissStall,
        )

        is TodayState.TrainingDay -> TrainingDayScreen(
            state = state,
            onStartWorkout = onStartWorkout,
            onEditSession = { onEditSession(state.sessionCard.templateId) },
            onSeeFullWeek = onSeeFullWeek,
            onSetStartingWeights = onSetStartingWeights,
            onPickExercise = onPickExercise,
        )

        is TodayState.Completed -> CompletedScreen(
            state = state,
            onSeeSummary = { onSeeSummary(state.sessionId) },
            onTrainAgain = { onTrainAgain(state.templateId) },
            onSeeFullWeek = onSeeFullWeek,
        )

        // Reuses the rest-day layout: both are "nothing to do today", and giving them one
        // shape keeps the tab from feeling like three different screens.
        is TodayState.NotStartedYet -> RestDayScreen(
            state = TodayState.RestDay(
                eyebrow = state.eyebrow,
                headline = state.headline,
                coachBody = state.coachBody,
                nextSessionLabel = state.firstSessionLabel,
            ),
            onSeeFullWeek = onSeeFullWeek,
        )

        is TodayState.RestDay -> RestDayScreen(
            state = state,
            onSeeFullWeek = onSeeFullWeek,
            onTrainAnyway = onTrainAnyway,
            onPickExercise = onPickExercise,
        )
    }
}

// ── Training day — 2a, and 9d when there is no history yet ──────────

@Composable
private fun TrainingDayScreen(
    state: TodayState.TrainingDay,
    onStartWorkout: () -> Unit,
    onEditSession: () -> Unit,
    onSeeFullWeek: () -> Unit,
    onSetStartingWeights: () -> Unit,
    onPickExercise: () -> Unit = {},
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
            MonoLabel(text = state.eyebrow)
            Spacer(Modifier.height(10.dp))
            CoachHeadline(text = state.headline)
            Spacer(Modifier.height(5.dp))
            Text(
                text = state.coachBody,
                style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(10.dp))

            SessionCardView(card = state.sessionCard, onEditSession = onEditSession)
            Spacer(Modifier.height(10.dp))

            // Before the first session: put in what you already lift, so set one opens
            // at a real weight instead of an empty bar you have to type over.
            if (state.liftsWithoutLoad > 0) {
                NavRow(
                    text = "Set starting weights · ${state.liftsWithoutLoad} lift" +
                        if (state.liftsWithoutLoad == 1) "" else "s",
                    onClick = onSetStartingWeights,
                    emphasised = true,
                )
                Spacer(Modifier.height(10.dp))
            }

            if (state.weekStrip.isNotEmpty()) {
                WeekStrip(days = state.weekStrip)
                Spacer(Modifier.height(10.dp))
            }

            VolumeFooter(
                rows = state.volumeRows,
                coachLine = state.volumeCoachLine,
                onSeeFullWeek = onSeeFullWeek,
            )
            Spacer(Modifier.height(10.dp))
            // The plan fork promises mixing: follow the plan Monday, freestyle Saturday.
            // This is the door that promise needed.
            NavRow(text = "Train something else today", onClick = onPickExercise)
            Spacer(Modifier.height(16.dp))
        }

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
                text = card.summaryLine,
                style = RedplateType.mono.copy(fontSize = 11.sp),
                color = colors.inkMuted,
            )
        }

        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            card.exercises.forEach { exercise ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

                    // The load change, stated where the load is. This is the whole
                    // reason the card lists the lifts rather than just naming the day.
                    if (exercise.loadNote != null) {
                        Text(
                            text = exercise.loadNote,
                            style = RedplateType.mono.copy(
                                fontSize = 10.sp,
                                letterSpacing = 0.08.em,
                            ),
                            color = colors.live,
                        )
                    }
                }
            }
        }

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
                Chevron()
            }
        }
    }
}

/**
 * The quiet footer. Three rows, then one sentence, then the way through to the full
 * eleven-group chart on the Plan tab (design 10a's "see the full week").
 */
@Composable
private fun VolumeFooter(
    rows: List<VolumeRow>,
    coachLine: String,
    onSeeFullWeek: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        SectionLabel(text = "This week · sets vs target")
        Spacer(Modifier.height(11.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                VolumeBar(label = row.label, current = row.current, target = row.target)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = coachLine,
            style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
            color = colors.inkMuted,
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onSeeFullWeek),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "See the full week",
                style = RedplateType.body.copy(fontSize = 13.5.sp),
                color = colors.inkSecondary,
                modifier = Modifier.weight(1f),
            )
            Chevron()
        }
    }
}

// ── Stall / deload — 9c ─────────────────────────────────────────────

/**
 * Fires on Today rather than as a notification, and leads with the evidence rather
 * than a verdict. The deload is spelled out in kilos so it reads as a plan, not as
 * quitting — and "Push on" is always available.
 */
@Composable
private fun StallScreen(
    state: TodayState.Stalled,
    onTakeDeload: () -> Unit,
    onSwapLift: (Long) -> Unit,
    onPushOn: () -> Unit,
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
            MonoLabel(text = state.eyebrow)
            Spacer(Modifier.height(10.dp))
            CoachHeadline(text = state.headline)
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.coachBody,
                style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                SectionLabel(text = "Estimated one-rep max · last ${state.e1rmWeeks.size} weeks")
                Spacer(Modifier.height(14.dp))
                E1rmBars(weeks = state.e1rmWeeks)
            }
            Spacer(Modifier.height(12.dp))

            BorderedCard {
                Column {
                    SectionLabel(text = "A deload week means")
                    Spacer(Modifier.height(8.dp))
                    state.deloadEffects.forEach { effect ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = effect.label,
                                style = RedplateType.body.copy(fontSize = 13.5.sp),
                                color = colors.inkBright,
                            )
                            Text(
                                text = effect.value,
                                style = RedplateType.mono.copy(fontSize = 12.5.sp),
                                color = if (effect.isOutcome) colors.live else colors.inkBright,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryBar(label = "Take the deload week", onClick = onTakeDeload)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    label = "Swap the lift instead",
                    onClick = { onSwapLift(state.templateId) },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    label = "Push on",
                    onClick = onPushOn,
                    modifier = Modifier.width(112.dp),
                )
            }
        }
    }
}

/** Six weeks of estimated 1RM. Flat weeks are the evidence, so they carry the colour. */
@Composable
private fun E1rmBars(weeks: List<E1rmWeek>) {
    val colors = RedplateTheme.colors
    val peak = weeks.maxOfOrNull { it.e1rm }?.takeIf { it > 0 } ?: 1.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        weeks.forEach { week ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        // Floor the bar so an early low week is still a visible column.
                        .fillMaxHeight(((week.e1rm / peak).toFloat() * 0.82f).coerceIn(0.18f, 0.82f))
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (week.isFlat) StateColor.pr else colors.surfaceRaised),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = week.label,
                    style = RedplateType.mono.copy(fontSize = 9.5.sp),
                    color = if (week.isFlat) StateColor.pr else colors.inkMuted,
                )
            }
        }
    }
}

// ── Today is done (2.2) ─────────────────────────────────────────────

/**
 * The day's session is behind you.
 *
 * Finishing a workout used to leave Today showing the same "Let's go" card, which said
 * nothing about what had happened and made a duplicate session one mistaken tap away.
 * The primary action here is to look at what was achieved; training again is possible but
 * secondary, so it is always a decision rather than a slip.
 */
@Composable
private fun CompletedScreen(
    state: TodayState.Completed,
    onSeeSummary: () -> Unit,
    onTrainAgain: () -> Unit,
    onSeeFullWeek: () -> Unit,
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
            MonoLabel(text = state.eyebrow)
            Spacer(Modifier.height(10.dp))
            CoachHeadline(text = state.headline)
            Spacer(Modifier.height(5.dp))
            Text(
                text = state.summaryLine,
                style = RedplateType.mono.copy(fontSize = 13.sp),
                color = colors.live,
            )
            Spacer(Modifier.height(14.dp))

            if (state.weekStrip.isNotEmpty()) {
                WeekStrip(days = state.weekStrip)
                Spacer(Modifier.height(10.dp))
            }

            VolumeFooter(
                rows = state.volumeRows,
                coachLine = state.volumeCoachLine,
                onSeeFullWeek = onSeeFullWeek,
            )

            if (state.nextSessionLabel != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .clickable(onClick = onSeeFullWeek)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.nextSessionLabel,
                        style = RedplateType.body.copy(fontSize = 15.sp),
                        color = colors.inkSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Chevron()
                }
            }

            Spacer(Modifier.height(10.dp))
            SecondaryButton(label = state.trainAgainLabel, onClick = onTrainAgain)
            Spacer(Modifier.height(16.dp))
        }

        PrimaryBar(
            label = "See what changed",
            onClick = onSeeSummary,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}

// ── Rest day and empty ──────────────────────────────────────────────

@Composable
private fun RestDayScreen(
    state: TodayState.RestDay,
    onSeeFullWeek: () -> Unit,
    onTrainAnyway: () -> Unit = {},
    onPickExercise: () -> Unit = {},
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
            MonoLabel(text = state.eyebrow)
            Spacer(Modifier.height(10.dp))
            CoachHeadline(text = state.headline)
            Spacer(Modifier.height(5.dp))
            Text(
                text = state.coachBody,
                style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(16.dp))

            if (state.weekStrip.isNotEmpty()) {
                WeekStrip(days = state.weekStrip)
                Spacer(Modifier.height(10.dp))
            }

            NavRow(
                text = state.nextSessionLabel?.let { "Next: $it" } ?: "See the full week",
                onClick = onSeeFullWeek,
            )
            Spacer(Modifier.height(10.dp))
            NavRow(text = "Pick something to train", onClick = onPickExercise)
            Spacer(Modifier.height(16.dp))
        }

        // Rest is the answer, so the way past it is secondary — but it is there. Training
        // on a day off is a decision, not a mistake, and the rotation simply carries on.
        if (state.trainAnywayLabel != null) {
            SecondaryButton(
                label = state.trainAnywayLabel,
                onClick = onTrainAnyway,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

/** A 64 dp row that leads somewhere. [emphasised] lifts it a surface step. */
@Composable
private fun NavRow(text: String, onClick: () -> Unit, emphasised: Boolean = false) {
    val colors = RedplateTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (emphasised) colors.surfaceRaised else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = RedplateType.body.copy(fontSize = 15.sp),
            color = if (emphasised) colors.ink else colors.inkSecondary,
            modifier = Modifier.weight(1f),
        )
        Chevron()
    }
}

/**
 * Where you are in the week (plan §03, "a 7-day strip"). Read-only.
 *
 * Every state carries a mark as well as a shade — ✓ done, ○ planned, – missed, · rest —
 * so it reads without colour (CLAUDE.md §3).
 */
@Composable
private fun WeekStrip(days: List<WeekStripDay>) {
    val colors = RedplateTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            val description = when (day.status) {
                StripStatus.DONE -> "trained"
                StripStatus.PLANNED -> "session planned"
                StripStatus.MISSED -> "planned, not trained"
                StripStatus.REST -> "rest day"
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (day.isToday) colors.surfaceRaised else colors.surface)
                    .padding(vertical = 7.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = (if (day.isToday) "Today, " else "") + description
                    },
            ) {
                Text(
                    text = day.label,
                    style = RedplateType.mono.copy(
                        fontSize = 10.5.sp,
                        fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (day.isToday) colors.ink else colors.inkMuted,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .then(
                            when (day.status) {
                                StripStatus.DONE -> Modifier.background(colors.ink)
                                StripStatus.PLANNED -> Modifier.border(1.5.dp, colors.inkMuted, CircleShape)
                                else -> Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (day.status) {
                            StripStatus.DONE -> "✓"
                            StripStatus.PLANNED -> ""
                            StripStatus.MISSED -> "–"
                            StripStatus.REST -> "·"
                        },
                        style = RedplateType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = if (day.status == StripStatus.DONE) colors.inkOnLight else colors.inkMuted,
                    )
                }
            }
        }
    }
}

/**
 * No weekly plan — either because the user chose to pick each day (3a) or because they
 * have not built one yet. It reads as an invitation rather than a report of absence, and
 * its action is the same 88 dp bar every other Today state ends with: this is a state you
 * can be in on purpose, so it should not feel like a screen you have to get out of.
 */
@Composable
private fun NoProgramScreen(
    state: TodayState.NoProgramYet,
    onPickExercise: () -> Unit,
    onBuildPlan: () -> Unit,
    onSeeSummary: (Long) -> Unit,
) {
    val colors = RedplateTheme.colors
    val done = state.doneToday

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
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(22.dp))
            MonoLabel(text = "YOU PICK EACH SESSION")
            Spacer(Modifier.height(10.dp))
            CoachHeadline(
                text = if (done != null) "${done.label}. Done." else "What do you feel like training?",
            )
            Spacer(Modifier.height(8.dp))
            if (done != null) {
                Text(
                    text = done.summaryLine,
                    style = RedplateType.mono.copy(fontSize = 13.sp),
                    color = colors.live,
                )
                Spacer(Modifier.height(12.dp))
                NavRow(text = "See what changed", onClick = { onSeeSummary(done.sessionId) })
            } else {
                Text(
                    text = "Tap the muscles you want to train and you get a real session built " +
                        "around them — compounds first, fitted to your time, and counted " +
                        "towards the week like anything else.",
                    style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                    color = colors.inkSecondary,
                )
            }
            Spacer(Modifier.height(20.dp))

            // The way back to app-managed training, from the screen you are on, rather
            // than a tab that sent you back here.
            BorderedCard {
                Column {
                    SectionLabel(text = "Rather have a plan?")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Let the app plan your week from your goal and schedule. It sets " +
                            "the weights and adjusts them from what you log. You can switch " +
                            "back any time in You → Your plan.",
                        style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(label = "Let the app plan my week", onClick = onBuildPlan)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        PrimaryBar(
            label = if (done != null) "Train again" else "Pick what to train",
            onClick = onPickExercise,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** A workout left open. Resume is the answer; finishing where it stands is the other. */
@Composable
private fun InProgressScreen(
    state: TodayState.InProgress,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = RedplateTheme.colors

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
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(22.dp))
            MonoLabel(text = state.eyebrow)
            Spacer(Modifier.height(10.dp))
            CoachHeadline(text = state.headline)
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.summaryLine,
                style = RedplateType.mono.copy(fontSize = 13.sp),
                color = colors.live,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Everything you logged is saved, and so is the weight you had dialled " +
                    "in. Next up: ${state.resumeExerciseName}.",
                style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                color = colors.inkSecondary,
            )
            if (state.loggedLines.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    SectionLabel(text = "Logged so far")
                    Spacer(Modifier.height(8.dp))
                    state.loggedLines.forEach { line ->
                        Text(
                            text = line,
                            style = RedplateType.data.copy(fontSize = 13.sp, lineHeight = 22.sp),
                            color = colors.inkBright,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        SecondaryButton(
            label = "Finish it here",
            onClick = onFinish,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )
        PrimaryBar(
            label = "Resume workout",
            onClick = onResume,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

// ── Previews ────────────────────────────────────────────────────────

@Preview(name = "2a · training day", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun TodayTrainingDayPreview() {
    RedplateTheme {
        TodayScreen(
            state = TodayState.TrainingDay(
                eyebrow = "FRIDAY MORNING · WEEK 3 OF 5",
                headline = "Push day. About an hour.",
                coachBody = "Bench goes up to 102.5 kg — you finished every set with two " +
                    "left on Tuesday.",
                sessionCard = SessionCard(
                    label = "Upper A",
                    summaryLine = "20 SETS · 58 MIN",
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

@Preview(name = "9d · day one", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun TodayFirstDayPreview() {
    RedplateTheme {
        TodayScreen(
            state = TodayState.TrainingDay(
                eyebrow = "SATURDAY · WEEK 1, SESSION 1",
                headline = "First one. Go light on purpose.",
                coachBody = "Pick a weight you could manage two more reps with. Today sets " +
                    "the baseline — every number after this is built off it.",
                sessionCard = SessionCard(
                    label = "Upper A",
                    summaryLine = "14 SETS · ~45 MIN",
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
                volumeRows = listOf(VolumeRow("Chest", 0, 18), VolumeRow("Back", 0, 20)),
                volumeCoachLine = "Fills in as you log. Trends need three sessions.",
                primaryLabel = "Start Upper A",
                isFirstSession = true,
            ),
            onStartWorkout = {},
            onPickExercise = {},
        )
    }
}

@Preview(name = "9c · stall detected", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun TodayStalledPreview() {
    RedplateTheme {
        TodayScreen(
            state = TodayState.Stalled(
                eyebrow = "BENCH PRESS · WEEK 6 OF 5",
                headline = "Bench hasn't moved in three weeks.",
                coachBody = "Same 102.5 kg, and the last rep got harder each time. " +
                    "That's a stall, not a bad day.",
                e1rmWeeks = listOf(
                    E1rmWeek("W1", 112.0, isFlat = false),
                    E1rmWeek("W2", 118.0, isFlat = false),
                    E1rmWeek("W3", 126.0, isFlat = false),
                    E1rmWeek("W4", 128.0, isFlat = true),
                    E1rmWeek("W5", 127.5, isFlat = true),
                    E1rmWeek("W6", 127.0, isFlat = true),
                ),
                deloadEffects = listOf(
                    DeloadEffect("Bench", "102.5 → 82.5 kg"),
                    DeloadEffect("Sets per lift", "4 → 2"),
                    DeloadEffect("Then week 1 restarts at", "105 kg", isOutcome = true),
                ),
                templateId = 1L,
            ),
            onStartWorkout = {},
            onPickExercise = {},
        )
    }
}

@Preview(name = "Today · rest day", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun TodayRestDayPreview() {
    RedplateTheme {
        TodayScreen(
            state = TodayState.RestDay(
                eyebrow = "THURSDAY · WEEK 3 OF 5",
                headline = "Rest day. You've earned it.",
                coachBody = "Next session is Upper A, tomorrow.",
                nextSessionLabel = "Upper A",
            ),
            onStartWorkout = {},
            onPickExercise = {},
        )
    }
}

@Preview(name = "Today · done", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun TodayCompletedPreview() {
    RedplateTheme {
        TodayScreen(
            state = TodayState.Completed(
                eyebrow = "TUESDAY · WEEK 3 OF 5",
                headline = "Upper A. Done.",
                summaryLine = "18 sets · 47 min · 2 PRs",
                volumeRows = listOf(
                    VolumeRow("Chest", 12, 18),
                    VolumeRow("Lats", 10, 20),
                    VolumeRow("Triceps", 8, 16),
                ),
                volumeCoachLine = "Lats is still short of target this week — Thursday covers it.",
                nextSessionLabel = "Next: Lower A, Thursday",
                sessionId = 42L,
                templateId = 7L,
            ),
            onStartWorkout = {},
            onPickExercise = {},
        )
    }
}

@Preview(name = "Today · no program", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun TodayNoProgramPreview() {
    RedplateTheme {
        TodayScreen(
            state = TodayState.NoProgramYet(),
            onStartWorkout = {},
            onPickExercise = {},
        )
    }
}
