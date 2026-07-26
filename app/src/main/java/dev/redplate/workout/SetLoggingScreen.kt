package dev.redplate.workout

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import dev.redplate.data.PlateMath
import dev.redplate.ui.components.PlateStack
import dev.redplate.ui.theme.KeepScreenOn
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.PlexMono
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
        onToggleWarmup = viewModel::toggleWarmup,
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
        onAddRest = { viewModel.adjustRest(30) },
        onSubRest = { viewModel.adjustRest(-15) },
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
    onToggleWarmup: () -> Unit,
    onCompleteSet: () -> Unit,
    onRestPrimary: () -> Unit,
    onAddRest: () -> Unit,
    onSubRest: () -> Unit,
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
                onToggleWarmup = onToggleWarmup,
                onCompleteSet = onCompleteSet,
                modifier = modifier,
            )
        }
    }
}

// ── INPUT SCREEN ──
// Header → Movement window (placeholder) → Load/plates → Reps → Difficulty → Primary bar

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
    onToggleWarmup: () -> Unit,
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
        // ── Header ──
        Header(
            exerciseName = state.exerciseName,
            subtitle = listOf(state.exercisePositionLabel, state.headerSubtitle)
                .filter { it.isNotEmpty() }
                .joinToString("  ·  "),
            hasGuidance = state.hasGuidance,
            onBack = onBack,
            onOpenGuidance = onOpenGuidance,
        )

        // ── Scrollable content ──
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Movement window — start-position still, or a muscle-group placeholder
            ExerciseImage(
                imageUri = state.imageUri,
                muscle = state.primaryMuscle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .height(164.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentDescription = state.exerciseName,
            )

            // Load section
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                // Coach reasoning line
                if (state.coachReasoningLine.isNotEmpty()) {
                    Text(
                        text = state.coachReasoningLine,
                        style = RedplateType.body.copy(fontSize = 15.5.sp),
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(2.dp))
                }

                // Weight readout and its stepper. The steppers flank the number so the
                // thumb lands either side of what it is changing, and each step is a
                // load the equipment can actually make — never a 6 kg dumbbell.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StepButton("−", "Decrease weight", onClick = onLoadDown)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = formatKg(state.loadKg),
                                style = RedplateType.load.copy(fontSize = 64.sp),
                                color = colors.ink,
                                modifier = Modifier.semantics {
                                    contentDescription = "Weight ${formatKg(state.loadKg)} kilograms"
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "KG",
                                style = RedplateType.mono.copy(fontSize = 14.sp),
                                color = colors.inkMuted,
                                modifier = Modifier.padding(bottom = 9.dp),
                            )
                        }
                        // Plate maths could not hit the number asked for. Say so, rather
                        // than silently showing a weight that cannot be loaded.
                        if (!state.isExactLoad) {
                            Text(
                                text = "closest the plates allow",
                                style = RedplateType.mono.copy(fontSize = 10.sp),
                                color = colors.inkMuted,
                            )
                        }
                    }

                    StepButton("+", "Increase weight", onClick = onLoadUp)
                }
            }

            // Plate stack
            if (state.isPlateLoaded && state.plateLoad != null) {
                PlateStack(
                    plateLoad = state.plateLoad,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Rep counter
            RepCounter(
                reps = state.reps,
                onDown = onRepsDown,
                onUp = onRepsUp,
            )

            Spacer(Modifier.height(12.dp))

            // Difficulty chips
            Column(Modifier.padding(horizontal = 16.dp)) {
                DifficultyChips(
                    selected = state.difficulty,
                    onSelect = onSetDifficulty,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Warm-up toggle. This used to render only when already on, so there was no
            // way to turn it back on once cleared — the control disappeared with the state
            // it controlled. It is now always present and always says which way it is set.
            WarmupToggle(isWarmup = state.isWarmup, onToggle = onToggleWarmup)

            Spacer(Modifier.height(12.dp))
        }

        // ── Primary bar ──
        InputPrimaryBar(
            enabled = state.canCompleteSet,
            onClick = onCompleteSet,
        )
    }
}

// ── REST SCREEN ──
// Header → PR badge → Timer → Progress → Coach text → Set history → Controls → Primary

@Composable
private fun RestScreen(
    state: SetLoggingUiState,
    onBack: () -> Unit,
    onSub: () -> Unit,
    onAdd: () -> Unit,
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
        // Header
        Header(
            exerciseName = state.exerciseName,
            subtitle = "SET ${state.setNumber - 1} LOGGED · ${(state.targetSets - state.setNumber + 1).coerceAtLeast(0)} TO GO",
            hasGuidance = false,
            onBack = onBack,
            onOpenGuidance = {},
        )

        // Scrollable rest content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            // PR badge
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
                                fontWeight = FontWeight.Medium,
                            ),
                            color = Color(0xFF0C0E11),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = state.prBadgeText,
                        style = RedplateType.body,
                        color = colors.ink,
                    )
                }
            }

            // REST label
            Text(
                text = "REST",
                style = RedplateType.mono,
                color = colors.inkMuted,
            )

            // Timer
            Text(
                text = formatClock(running.remainingSeconds),
                style = RedplateType.timer,
                color = colors.live,
            )

            // Progress bar
            val progress = if (running.totalSeconds > 0) {
                1f - (running.remainingSeconds.toFloat() / running.totalSeconds)
            } else 0f

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.surfaceRaised),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.live),
                )
            }

            Spacer(Modifier.height(20.dp))

            // Coach text
            if (state.restCoachText.isNotEmpty()) {
                Text(
                    text = state.restCoachText,
                    style = RedplateType.body.copy(fontSize = 15.5.sp, lineHeight = 24.sp),
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.height(16.dp))
            }

            // Set history card
            if (state.loggedSets.isNotEmpty()) {
                SetHistoryCard(sets = state.loggedSets)
            }
        }

        // Rest controls
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Two controls, both real. There used to be a third "+15s" pill with an
            // empty handler sitting next to a "+30s" one, so the row had a button that
            // did nothing and two that looked like they did the same thing.
            RestPill("−15s", "Take 15 seconds off the rest", onClick = onSub,
                modifier = Modifier.weight(1f))
            RestPill("+30s", "Add 30 seconds to the rest", onClick = onAdd,
                modifier = Modifier.weight(1f))
        }

        // Primary bar
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.live)
                .clickable(onClick = onPrimary)
                .semantics {
                    contentDescription = state.restPrimaryLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.restPrimaryLabel,
                style = RedplateType.action.copy(fontSize = 20.sp),
                color = Color(0xFF0C0E11),
            )
        }
    }
}

// ── Shared composables ──

@Composable
private fun Header(
    exerciseName: String,
    subtitle: String,
    hasGuidance: Boolean,
    onBack: () -> Unit,
    onOpenGuidance: () -> Unit,
) {
    val colors = RedplateTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapTarget(description = "Back", onClick = onBack) {
            Text("‹", style = RedplateType.load, color = colors.inkMuted)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = exerciseName,
                style = RedplateType.exerciseName.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = colors.inkMuted,
                )
            }
        }
        if (hasGuidance) {
            TapTarget(description = "Exercise guidance", onClick = onOpenGuidance) {
                Box(
                    Modifier
                        .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", style = RedplateType.body.copy(fontSize = 14.sp), color = colors.inkMuted)
                }
            }
        } else {
            Spacer(Modifier.width(64.dp))
        }
    }
}

@Composable
private fun RepCounter(
    reps: Int,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    val colors = RedplateTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StepButton("−", "Decrease reps", onClick = onDown)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = reps.toString(),
                    style = RedplateType.figure.copy(fontSize = 38.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
                Text(
                    text = "reps done",
                    style = RedplateType.mono,
                    color = colors.inkMuted,
                )
            }
            StepButton("+", "Increase reps", onClick = onUp)
        }
    }
}

@Composable
private fun WarmupToggle(isWarmup: Boolean, onToggle: () -> Unit) {
    val colors = RedplateTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            Modifier
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isWarmup) colors.surfaceRaised else colors.ground)
                .border(
                    1.dp,
                    if (isWarmup) colors.ink else colors.line,
                    RoundedCornerShape(10.dp),
                )
                .toggleable(
                    value = isWarmup,
                    role = Role.Switch,
                    onValueChange = { onToggle() },
                )
                .semantics {
                    contentDescription =
                        "Warm-up set. ${if (isWarmup) "On" else "Off"}. " +
                            "Warm-up sets are logged but do not count toward weekly volume."
                }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isWarmup) "WARM-UP  ●" else "WARM-UP  ○",
                style = RedplateType.label,
                color = if (isWarmup) colors.ink else colors.inkMuted,
            )
        }
    }
}

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
        Text(
            text = "SO FAR",
            style = RedplateType.mono.copy(fontSize = 9.5.sp, letterSpacing = 0.14.sp),
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(7.dp))
        sets.filter { !it.isWarmup }.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${line.setIndex + 1}  ${formatKg(line.loadKg)} × ${line.reps}  ${line.rir?.let { "$it left" } ?: ""}",
                    style = RedplateType.data.copy(fontSize = 12.5.sp, lineHeight = 24.sp),
                    color = colors.inkSecondary,
                )
                if (line.isPr) {
                    Spacer(Modifier.width(8.dp))
                    Text("★", style = RedplateType.data, color = StateColor.pr)
                }
            }
        }
    }
}

@Composable
private fun InputPrimaryBar(enabled: Boolean, onClick: () -> Unit) {
    val colors = RedplateTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) colors.live else colors.surface)
            .clickable(enabled = enabled) { onClick() }
            .semantics {
                contentDescription = "Done — start rest"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Done — start rest",
            style = RedplateType.action.copy(fontSize = 20.sp),
            color = if (enabled) Color(0xFF0C0E11) else colors.inkMuted,
        )
    }
}

@Composable
private fun TapTarget(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
            .clickable { onClick() }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    val colors = RedplateTheme.colors
    Box(
        Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceRaised)
            .clickable { onClick() }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = RedplateType.figure, color = colors.ink)
    }
}

@Composable
private fun RestPill(text: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RedplateTheme.colors
    Box(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable { onClick() }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = RedplateType.body.copy(fontSize = 14.5.sp), color = colors.inkSecondary)
    }
}

// ── Formatting ──

private fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString().trimEnd('0').trimEnd('.')

private fun formatClock(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

// ─────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────

private val PREVIOUS = listOf(
    PreviousSetLine(80.0, 8, 2),
    PreviousSetLine(80.0, 7, 1),
    PreviousSetLine(80.0, 6, 0),
)

@Composable
private fun PreviewScreen(state: SetLoggingUiState) {
    RedplateTheme {
        SetLoggingScreen(
            state = state,
            onBack = {}, onOpenGuidance = {},
            onLoadDown = {}, onLoadUp = {},
            onRepsDown = {}, onRepsUp = {},
            onSetDifficulty = {},
            onToggleWarmup = {}, onCompleteSet = {},
            onRestPrimary = {}, onAddRest = {}, onSubRest = {},
        )
    }
}

@Preview(name = "Input · first set", showBackground = true, backgroundColor = 0xFF101317, widthDp = 384, heightDp = 824)
@Composable
private fun FirstSetPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Bench Press",
            hasGuidance = true,
            exercisePositionLabel = "EXERCISE 1 OF 5",
            setNumber = 3, targetSets = 4, repRangeLow = 6, repRangeHigh = 10, targetRir = 2,
            headerSubtitle = "SET 3 OF 4 · 6–10 REPS · 2 LEFT",
            coachReasoningLine = "Same weight as your last set —",
            loadKg = 102.5, reps = 8,
            difficulty = Difficulty.TWO_LEFT,
            isPlateLoaded = true,
            plateLoad = PlateMath.PlateLoad(102.5, listOf(20.0, 20.0, 1.25), false),
            previousSets = PREVIOUS,
        )
    )
}

@Preview(name = "Resting · PR", showBackground = true, backgroundColor = 0xFF101317, widthDp = 384, heightDp = 824)
@Composable
private fun RestingPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Bench Press",
            setNumber = 4, targetSets = 4, repRangeLow = 6, repRangeHigh = 10, targetRir = 2,
            headerSubtitle = "SET 3 LOGGED · 1 TO GO",
            loadKg = 102.5, reps = 9,
            isPlateLoaded = true,
            plateLoad = PlateMath.PlateLoad(102.5, listOf(20.0, 20.0, 1.25), false),
            loggedSets = listOf(
                LoggedSetLine(0, 102.5, 10, 2, isWarmup = false, isPr = false),
                LoggedSetLine(1, 102.5, 9, 1, isWarmup = false, isPr = false),
                LoggedSetLine(2, 102.5, 9, 1, isWarmup = false, isPr = true),
            ),
            rest = RestState.Running(remainingSeconds = 134, totalSeconds = 180),
            prBadgeText = "Best set you've done at 102.5 kg.",
            restCoachText = "Three minutes is the prescription for a heavy compound. Next set: same 102.5 kg, and 8 reps is a good day.",
            restPrimaryLabel = "I'm ready — set 4",
        )
    )
}

@Preview(name = "Input · unloadable weight", showBackground = true, backgroundColor = 0xFF101317, widthDp = 384, heightDp = 824)
@Composable
private fun InexactLoadPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Barbell Back Squat",
            hasGuidance = true,
            exercisePositionLabel = "EXERCISE 1 OF 5",
            setNumber = 1, targetSets = 4, repRangeLow = 6, repRangeHigh = 10, targetRir = 2,
            headerSubtitle = "SET 1 OF 4 · 6–10 REPS · 4 LEFT",
            coachReasoningLine = "Based on your last session —",
            loadKg = 97.5, reps = 8,
            isPlateLoaded = true,
            isExactLoad = false,
            plateLoad = PlateMath.PlateLoad(97.5, listOf(25.0, 10.0, 3.75), false),
            isWarmup = true,
        )
    )
}

@Preview(name = "Resting · last set of lift", showBackground = true, backgroundColor = 0xFF101317, widthDp = 384, heightDp = 824)
@Composable
private fun RestingNextExercisePreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Bench Press",
            exercisePositionLabel = "EXERCISE 1 OF 5",
            setNumber = 5, targetSets = 4, repRangeLow = 6, repRangeHigh = 10, targetRir = 2,
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

@Preview(name = "Resting · session end", showBackground = true, backgroundColor = 0xFF101317, widthDp = 384, heightDp = 824)
@Composable
private fun RestingFinishPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Hanging Leg Raise",
            exercisePositionLabel = "EXERCISE 5 OF 5",
            setNumber = 4, targetSets = 3, repRangeLow = 10, repRangeHigh = 15,
            loadKg = 0.0, reps = 12,
            loggedSets = listOf(
                LoggedSetLine(0, 0.0, 14, 2, isWarmup = false, isPr = false),
                LoggedSetLine(1, 0.0, 12, 1, isWarmup = false, isPr = false),
                LoggedSetLine(2, 0.0, 11, 0, isWarmup = false, isPr = false),
            ),
            rest = RestState.Running(remainingSeconds = 42, totalSeconds = 90),
            restCoachText = "Last set of the session. Nice work.",
            restPrimaryLabel = "Finish session",
            restPrimaryAction = RestAction.FINISH_SESSION,
        )
    )
}

@Preview(name = "Input · loading", showBackground = true, backgroundColor = 0xFF101317, widthDp = 384, heightDp = 824)
@Composable
private fun LoadingPreview() {
    PreviewScreen(SetLoggingUiState())
}
