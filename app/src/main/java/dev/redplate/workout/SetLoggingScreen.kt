package dev.redplate.workout

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.redplate.data.PlateMath
import dev.redplate.ui.components.MovementWindow
import dev.redplate.ui.components.PLATE_HEIGHT_COMPACT
import dev.redplate.ui.components.PlateStack
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.ScreenHeader
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.theme.KeepScreenOn
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import dev.redplate.ui.theme.StateColor

// ─────────────────────────────────────────────────────────────────────
// Route — connects the ViewModel, keeps the screen awake, turns one-shot
// events into haptics. The screen below stays stateless and previewable.
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SetLoggingRoute(
    onBack: () -> Unit,
    onNextExercise: (sessionId: Long, exerciseId: String) -> Unit,
    onSwapExercise: (sessionId: Long, exerciseId: String) -> Unit,
    onSessionFinished: (sessionId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetLoggingViewModel = hiltViewModel(),
) {
    KeepScreenOn()

    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptics = remember(context) { WorkoutHaptics(context) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutEvent.SetLogged -> haptics.setLogged()
                WorkoutEvent.PrHit -> haptics.prHit()
                WorkoutEvent.RestComplete -> haptics.restComplete()
            }
        }
    }

    var showGuidance by rememberSaveable { mutableStateOf(false) }

    SetLoggingScreen(
        state = state,
        onBack = onBack,
        onOpenGuidance = { showGuidance = true },
        onLoadDown = viewModel::loadDown,
        onLoadUp = viewModel::loadUp,
        onRepsDown = viewModel::repsDown,
        onRepsUp = viewModel::repsUp,
        onSetDifficulty = viewModel::setDifficulty,
        onCompleteSet = viewModel::completeSet,
        // One button, three jobs, chosen by the ViewModel so the label and the
        // behaviour cannot disagree: another set, the next lift, or close the session.
        onRestPrimary = {
            when (state.restPrimaryAction) {
                RestAction.NEXT_SET -> viewModel.skipRest()
                RestAction.NEXT_EXERCISE -> viewModel.goToNextExercise(onNextExercise)
                RestAction.FINISH_SESSION -> viewModel.finishSession(onSessionFinished)
            }
        },
        // Three real controls, as drawn: −15s, Add 30s, +15s.
        onSubRest = { viewModel.adjustRest(-15) },
        onAddRest = { viewModel.adjustRest(30) },
        onAddShortRest = { viewModel.adjustRest(15) },
        modifier = modifier,
    )

    if (showGuidance) {
        GuidanceSheet(
            state = GuidanceState(
                exerciseName = state.exerciseName,
                muscleTags = state.guidanceMuscleTags,
                instructions = state.instructionSteps,
                primaryMuscle = state.primaryMuscle,
                imageUri = state.imageUri,
                substitutes = state.substitutes,
            ),
            onDismiss = {
                showGuidance = false
                viewModel.markGuidanceSeen()
            },
            onSwap = { exerciseId ->
                showGuidance = false
                viewModel.markGuidanceSeen()
                onSwapExercise(viewModel.sessionId, exerciseId)
            },
            onGotIt = {
                showGuidance = false
                viewModel.markGuidanceSeen()
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Stateless screen. Two modes: INPUT (logging a set) and REST (resting).
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SetLoggingScreen(
    state: SetLoggingUiState,
    onBack: () -> Unit,
    onOpenGuidance: () -> Unit,
    onLoadDown: () -> Unit,
    onLoadUp: () -> Unit,
    onRepsDown: () -> Unit,
    onRepsUp: () -> Unit,
    onSetDifficulty: (Difficulty?) -> Unit,
    onCompleteSet: () -> Unit,
    onRestPrimary: () -> Unit,
    onSubRest: () -> Unit,
    onAddRest: () -> Unit,
    onAddShortRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resting = state.rest is RestState.Running

    Crossfade(
        targetState = resting,
        animationSpec = tween(120),
        label = "mode",
    ) { isResting ->
        if (isResting) {
            RestScreen(
                state = state,
                onBack = onBack,
                onSub = onSubRest,
                onAdd = onAddRest,
                onAddShort = onAddShortRest,
                onPrimary = onRestPrimary,
                modifier = modifier,
            )
        } else {
            InputScreen(
                state = state,
                onBack = onBack,
                onOpenGuidance = onOpenGuidance,
                onLoadDown = onLoadDown,
                onLoadUp = onLoadUp,
                onRepsDown = onRepsDown,
                onRepsUp = onRepsUp,
                onSetDifficulty = onSetDifficulty,
                onCompleteSet = onCompleteSet,
                modifier = modifier,
            )
        }
    }
}
// ─────────────────────────────────────────────────────────────────────
// INPUT — design 8a
// Header → movement window → readout + plates → reps → difficulty → primary
//
// Note on warm-ups: the revamp draws no warm-up control on this screen, so there
// isn't one. `isWarmup` stays in the model — import/export and volume credit still
// honour it — but nothing on the set screen sets it.
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun InputScreen(
    state: SetLoggingUiState,
    onBack: () -> Unit,
    onOpenGuidance: () -> Unit,
    onLoadDown: () -> Unit,
    onLoadUp: () -> Unit,
    onRepsDown: () -> Unit,
    onRepsUp: () -> Unit,
    onSetDifficulty: (Difficulty?) -> Unit,
    onCompleteSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .systemBarsPadding(),
    ) {
        SetHeader(
            exerciseName = state.exerciseName,
            subtitle = state.headerSubtitle,
            hasGuidance = state.hasGuidance,
            onBack = onBack,
            onOpenGuidance = onOpenGuidance,
        )

        // Movement window: start and end cross-fading, so the picture shows the
        // movement rather than a pose.
        MovementWindow(
            startImageUri = state.imageUri,
            endImageUri = state.endImageUri,
            muscle = state.primaryMuscle,
            contentDescription = state.exerciseName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 2.dp)
                .height(164.dp)
                .clip(RoundedCornerShape(18.dp)),
        )

        // Readout. Centre band flexes so the load sits in the optical middle
        // whatever the header and window take.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (state.coachReasoningLine.isNotEmpty()) {
                Text(
                    text = state.coachReasoningLine,
                    style = RedplateType.body.copy(fontSize = 15.sp),
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.height(2.dp))
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Working weight ${formatKg(state.loadKg)} kilograms"
                },
            ) {
                Text(
                    text = formatKg(state.loadKg),
                    style = RedplateType.load.copy(fontSize = 64.sp, lineHeight = 64.sp),
                    color = colors.ink,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "KG",
                    style = RedplateType.mono.copy(fontSize = 14.sp),
                    color = colors.inkMuted,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }

            if (!state.isExactLoad) {
                Text(
                    text = "Closest the plates allow.",
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = colors.inkMuted,
                )
            }

            if (state.isPlateLoaded && state.plateLoad != null) {
                PlateStack(
                    plateLoad = state.plateLoad,
                    plateHeight = PLATE_HEIGHT_COMPACT,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }

        // Controls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            RepCounter(
                reps = state.reps,
                onDown = onRepsDown,
                onUp = onRepsUp,
                onLoadDown = onLoadDown,
                onLoadUp = onLoadUp,
            )
            Spacer(Modifier.height(10.dp))
            DifficultyChips(
                selected = state.difficulty,
                onSelect = onSetDifficulty,
            )
        }

        PrimaryBar(
            label = "Done — start rest",
            onClick = onCompleteSet,
            enabled = state.canCompleteSet,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// REST — design 2b
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun RestScreen(
    state: SetLoggingUiState,
    onBack: () -> Unit,
    onSub: () -> Unit,
    onAdd: () -> Unit,
    onAddShort: () -> Unit,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    val running = state.rest as? RestState.Running ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .systemBarsPadding(),
    ) {
        SetHeader(
            exerciseName = state.exerciseName,
            subtitle = state.restSubtitle,
            hasGuidance = false,
            onBack = onBack,
            onOpenGuidance = {},
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            // The one earned celebration in the app.
            if (state.prBadgeText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 14.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StateColor.pr)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "★ PR",
                            style = RedplateType.mono.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = colors.inkOnLight,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = state.prBadgeText,
                        style = RedplateType.body.copy(fontSize = 15.sp),
                        color = colors.ink,
                    )
                }
            }

            Text(
                text = "REST",
                style = RedplateType.mono.copy(fontSize = 10.5.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(2.dp))

            // 112sp, tabular — readable with the phone on the floor.
            Text(
                text = formatClock(running.remainingSeconds),
                style = RedplateType.timer.copy(textAlign = TextAlign.Start),
                color = colors.live,
                modifier = Modifier.semantics {
                    contentDescription = "${running.remainingSeconds} seconds of rest left"
                },
            )

            Spacer(Modifier.height(14.dp))
            val progress = if (running.totalSeconds > 0) {
                1f - (running.remainingSeconds.toFloat() / running.totalSeconds)
            } else {
                0f
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.surfaceRaised),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(colors.live),
                )
            }
            Spacer(Modifier.height(20.dp))

            if (state.restCoachText.isNotEmpty()) {
                Text(
                    text = state.restCoachText,
                    style = RedplateType.body.copy(fontSize = 15.5.sp, lineHeight = 24.sp),
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (state.loggedSets.any { !it.isWarmup }) {
                SetHistoryCard(sets = state.loggedSets)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RestPill("−15s", "Take 15 seconds off the rest", onClick = onSub,
                modifier = Modifier.width(92.dp), mono = true)
            RestPill("Add 30s", "Add 30 seconds to the rest", onClick = onAdd,
                modifier = Modifier.weight(1f))
            RestPill("+15s", "Add 15 seconds to the rest", onClick = onAddShort,
                modifier = Modifier.width(92.dp), mono = true)
        }

        PrimaryBar(
            label = state.restPrimaryLabel,
            onClick = onPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

// ── Shared ──────────────────────────────────────────────────────────

@Composable
private fun SetHeader(
    exerciseName: String,
    subtitle: String,
    hasGuidance: Boolean,
    onBack: () -> Unit,
    onOpenGuidance: () -> Unit,
) {
    val colors = RedplateTheme.colors
    ScreenHeader(
        title = exerciseName,
        subtitle = subtitle.ifEmpty { null },
        onBack = onBack,
        trailing = if (!hasGuidance) {
            null
        } else {
            {
                Box(
                    Modifier
                        .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .clickable(onClick = onOpenGuidance)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "How to do this exercise"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "?",
                        style = RedplateType.body.copy(fontSize = 14.sp),
                        color = colors.inkMuted,
                    )
                }
            }
        },
    )
}

/**
 * Reps in the middle with its own steppers, and the load steppers on the outside.
 *
 * 8a draws only the rep steppers, because the engine prescribes the load. The load
 * pair is kept because COACHING.md requires an override to always be possible and the
 * plan doc is explicit that "steppers stay for overriding" — they sit outboard so the
 * rep question still reads as the primary one.
 */
@Composable
private fun RepCounter(
    reps: Int,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onLoadDown: () -> Unit,
    onLoadUp: () -> Unit,
) {
    val colors = RedplateTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepButton("−", "Decrease reps", onClick = onDown)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = reps.toString(),
                style = RedplateType.figure.copy(
                    fontSize = 38.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.ink,
            )
            Text(
                text = "reps done",
                style = RedplateType.body.copy(fontSize = 12.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadNudge("− kg", "Decrease weight", onLoadDown)
                LoadNudge("+ kg", "Increase weight", onLoadUp)
            }
        }

        StepButton("+", "Increase reps", onClick = onUp)
    }
}

@Composable
private fun LoadNudge(label: String, description: String, onClick: () -> Unit) {
    val colors = RedplateTheme.colors
    Box(
        Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RedplateType.mono.copy(fontSize = 11.sp),
            color = colors.inkMuted,
        )
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    val colors = RedplateTheme.colors
    Box(
        Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = RedplateType.figure.copy(fontFamily = PlexCondensed, fontSize = 26.sp),
            color = colors.ink,
        )
    }
}

@Composable
private fun RestPill(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (mono) {
                RedplateType.mono.copy(fontSize = 12.sp)
            } else {
                RedplateType.body.copy(fontSize = 14.5.sp)
            },
            color = colors.inkSecondary,
        )
    }
}

/** "SO FAR" — the session's own sets, in mono so the columns line up. */
@Composable
private fun SetHistoryCard(sets: List<LoggedSetLine>) {
    val colors = RedplateTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        SectionLabel("So far")
        Spacer(Modifier.height(7.dp))
        sets.filter { !it.isWarmup }.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${line.setIndex + 1}  ${formatKg(line.loadKg)} × ${line.reps}",
                    style = RedplateType.data.copy(fontSize = 12.5.sp, lineHeight = 21.sp),
                    color = colors.inkSecondary,
                )
                if (line.rir != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${line.rir} left",
                        style = RedplateType.data.copy(fontSize = 12.5.sp, lineHeight = 21.sp),
                        color = colors.inkMuted,
                    )
                }
                if (line.isPr) {
                    Spacer(Modifier.width(8.dp))
                    Text("★", style = RedplateType.data, color = StateColor.pr)
                }
            }
        }
    }
}

// ── Formatting ──────────────────────────────────────────────────────

private fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString().trimEnd('0').trimEnd('.')

private fun formatClock(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

// ─────────────────────────────────────────────────────────────────────
// Previews — device-sized, so the 370/490 dp zones can be checked by eye
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun PreviewScreen(state: SetLoggingUiState) {
    RedplateTheme {
        SetLoggingScreen(
            state = state,
            onBack = {}, onOpenGuidance = {},
            onLoadDown = {}, onLoadUp = {},
            onRepsDown = {}, onRepsUp = {},
            onSetDifficulty = {}, onCompleteSet = {},
            onRestPrimary = {}, onSubRest = {}, onAddRest = {}, onAddShortRest = {},
        )
    }
}

@Preview(name = "8a · logging a set", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun InputPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Bench Press",
            hasGuidance = true,
            setNumber = 3, targetSets = 4, repRangeLow = 6, repRangeHigh = 10, targetRir = 2,
            headerSubtitle = "SET 3 OF 4 · 6–10 REPS · 2 LEFT",
            coachReasoningLine = "Same weight as your last set —",
            loadKg = 102.5, reps = 9,
            difficulty = Difficulty.ONE_LEFT,
            isPlateLoaded = true,
            plateLoad = PlateMath.PlateLoad(102.5, listOf(25.0, 20.0, 10.0), true),
        )
    )
}

@Preview(name = "8a · weight not loadable", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun InexactLoadPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Barbell Back Squat",
            hasGuidance = true,
            setNumber = 1, targetSets = 4, repRangeLow = 6, repRangeHigh = 10,
            headerSubtitle = "SET 1 OF 4 · 6–10 REPS · 4 LEFT",
            coachReasoningLine = "Based on your last session —",
            loadKg = 97.5, reps = 8,
            isPlateLoaded = true, isExactLoad = false,
            plateLoad = PlateMath.PlateLoad(97.5, listOf(25.0, 10.0, 3.75), false),
        )
    )
}

@Preview(name = "2b · rest with a PR", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun RestingPrPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Bench Press",
            setNumber = 4, targetSets = 4, repRangeLow = 6, repRangeHigh = 10,
            restSubtitle = "SET 3 LOGGED · 1 TO GO",
            loadKg = 102.5, reps = 9,
            loggedSets = listOf(
                LoggedSetLine(0, 102.5, 10, 2, isWarmup = false, isPr = false),
                LoggedSetLine(1, 102.5, 9, 1, isWarmup = false, isPr = false),
                LoggedSetLine(2, 102.5, 9, 1, isWarmup = false, isPr = true),
            ),
            rest = RestState.Running(remainingSeconds = 134, totalSeconds = 180),
            prBadgeText = "Best set you've done at 102.5 kg.",
            restCoachText = "Three minutes is the prescription for a heavy compound. " +
                "Next set: same 102.5 kg, and 8 reps is a good day.",
            restPrimaryLabel = "I'm ready — set 4",
            restPrimaryAction = RestAction.NEXT_SET,
        )
    )
}

@Preview(name = "2b · rest, lift done", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun RestingNextExercisePreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Bench Press",
            setNumber = 5, targetSets = 4,
            restSubtitle = "SET 4 LOGGED · LAST ONE",
            loadKg = 102.5, reps = 8,
            loggedSets = listOf(
                LoggedSetLine(0, 102.5, 10, 2, isWarmup = false, isPr = false),
                LoggedSetLine(1, 102.5, 9, 1, isWarmup = false, isPr = false),
                LoggedSetLine(2, 102.5, 9, 1, isWarmup = false, isPr = false),
                LoggedSetLine(3, 102.5, 8, 0, isWarmup = false, isPr = false),
            ),
            rest = RestState.Running(remainingSeconds = 96, totalSeconds = 150),
            nextExerciseName = "Wide-Grip Lat Pulldown",
            restCoachText = "That's this lift done. Wide-Grip Lat Pulldown is up next.",
            restPrimaryLabel = "Next — Wide-Grip Lat Pulldown",
            restPrimaryAction = RestAction.NEXT_EXERCISE,
        )
    )
}

@Preview(name = "8a · loading", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun LoadingPreview() {
    PreviewScreen(SetLoggingUiState())
}
