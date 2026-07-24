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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import dev.redplate.data.PlateMath
import dev.redplate.ui.components.PlateStack
import dev.redplate.ui.theme.KeepScreenOn
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
    onOpenGuidance: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetLoggingViewModel = hiltViewModel(),
) {
    KeepScreenOn()

    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptics = remember(context) { WorkoutHaptics(context) }
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutEvent.SetLogged -> haptics.setLogged()
                WorkoutEvent.PrHit -> haptics.prHit()
                WorkoutEvent.RestComplete -> haptics.restComplete()
            }
        }
    }

    SetLoggingScreen(
        state = state,
        onBack = onBack,
        onOpenGuidance = onOpenGuidance,
        onLoadDown = viewModel::loadDown,
        onLoadUp = viewModel::loadUp,
        onRepsDown = viewModel::repsDown,
        onRepsUp = viewModel::repsUp,
        onRirDown = viewModel::rirDown,
        onRirUp = viewModel::rirUp,
        onToggleWarmup = viewModel::toggleWarmup,
        onCompleteSet = viewModel::completeSet,
        onSkipRest = viewModel::skipRest,
        onAddRest = { viewModel.adjustRest(15) },
        onSubRest = { viewModel.adjustRest(-15) },
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────
// Stateless screen. Layout obeys CLAUDE.md §4:
//   • read-only content lives in the top, weighted region
//   • every control lives in the bottom region
//   • primary action is a full-width bar ≥88 dp, never a corner FAB
//   • every touch target is ≥64 dp
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
    onRirDown: () -> Unit,
    onRirUp: () -> Unit,
    onToggleWarmup: () -> Unit,
    onCompleteSet: () -> Unit,
    onSkipRest: () -> Unit,
    onAddRest: () -> Unit,
    onSubRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    val resting = state.rest is RestState.Running

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .systemBarsPadding()
    ) {
        // ── READ-ONLY ZONE ──
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Header(
                exerciseName = state.exerciseName,
                supersetLabel = state.supersetLabel,
                hasGuidance = state.hasGuidance,
                onBack = onBack,
                onOpenGuidance = onOpenGuidance,
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                PrescriptionMeta(state)
                if (state.previousSets.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    PreviousSession(state.previousSets)
                }
                if (state.loggedSets.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    LoggedThisSession(state.loggedSets)
                }
                Spacer(Modifier.height(8.dp))
            }

            // Signature readout: plate stack while inputting, running timer while resting.
            Readout(state = state)
        }

        // ── CONTROL ZONE ──
        Column(Modifier.fillMaxWidth().background(colors.ground)) {
            if (resting) {
                RestControls(
                    remaining = (state.rest as RestState.Running).remainingSeconds,
                    onSub = onSubRest,
                    onSkip = onSkipRest,
                    onAdd = onAddRest,
                )
            } else {
                WarmupToggle(isWarmup = state.isWarmup, onToggle = onToggleWarmup)
            }

            LoadStepper(
                loadKg = state.loadKg,
                onDown = onLoadDown,
                onUp = onLoadUp,
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CountStepper(
                    label = "REPS",
                    value = state.reps.toString(),
                    decDescription = "Decrease reps",
                    incDescription = "Increase reps",
                    onDown = onRepsDown,
                    onUp = onRepsUp,
                    modifier = Modifier.weight(1f),
                )
                CountStepper(
                    label = "RIR",
                    value = state.rir?.toString() ?: "—",
                    decDescription = "Decrease reps in reserve",
                    incDescription = "Increase reps in reserve",
                    onDown = onRirDown,
                    onUp = onRirUp,
                    modifier = Modifier.weight(1f),
                )
            }

            PrimaryBar(
                enabled = state.canCompleteSet,
                emphasized = !resting,
                onClick = onCompleteSet,
            )
        }
    }
}

// ── Header (top zone; back + name + sanctioned guidance affordance) ──

@Composable
private fun Header(
    exerciseName: String,
    supersetLabel: String?,
    hasGuidance: Boolean,
    onBack: () -> Unit,
    onOpenGuidance: () -> Unit,
) {
    val colors = RedplateTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapTarget(description = "Back", onClick = onBack) {
            Text("‹", style = RedplateType.load, color = colors.ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                text = exerciseName,
                style = RedplateType.exerciseName,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (supersetLabel != null) {
                Text(supersetLabel, style = RedplateType.label, color = colors.inkMuted)
            }
        }
        if (hasGuidance) {
            TapTarget(description = "Exercise guidance", onClick = onOpenGuidance) {
                Text("?", style = RedplateType.figure, color = colors.inkMuted)
            }
        }
    }
}

// ── Prescription: SET / REPS / RIR as instrument readouts ──

@Composable
private fun PrescriptionMeta(state: SetLoggingUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Meta("SET", "${state.setNumber}/${state.targetSets}")
        Meta("REPS", "${state.repRangeLow}–${state.repRangeHigh}")
        Meta("RIR", state.targetRir?.toString() ?: "—")
    }
}

@Composable
private fun Meta(label: String, value: String) {
    val colors = RedplateTheme.colors
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = RedplateType.label, color = colors.inkMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = RedplateType.figure, color = colors.ink)
    }
}

@Composable
private fun PreviousSession(sets: List<PreviousSetLine>) {
    val colors = RedplateTheme.colors
    Column {
        Text("LAST SESSION", style = RedplateType.label, color = colors.inkMuted)
        Spacer(Modifier.height(4.dp))
        Text(
            text = sets.joinToString("   ") { "${formatKg(it.loadKg)}×${it.reps}" },
            style = RedplateType.data,
            color = colors.ink,
        )
    }
}

@Composable
private fun LoggedThisSession(sets: List<LoggedSetLine>) {
    val colors = RedplateTheme.colors
    Column {
        Text("THIS SESSION", style = RedplateType.label, color = colors.inkMuted)
        Spacer(Modifier.height(4.dp))
        sets.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        if (line.isWarmup) append("W  ") else append("${line.setIndex + 1}  ")
                        append("${formatKg(line.loadKg)} kg × ${line.reps}")
                        line.rir?.let { append("  @${it}") }
                    },
                    style = RedplateType.data,
                    color = if (line.isWarmup) colors.inkMuted else colors.ink,
                )
                if (line.isPr) {
                    Spacer(Modifier.width(8.dp))
                    PrTag()
                }
            }
        }
    }
}

@Composable
private fun PrTag() {
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(StateColor.pr)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        // The word "PR" is the label — meaning is never carried by colour alone.
        Text("PR", style = RedplateType.label, color = Color(0xFF000000))
    }
}

// ── Readout: the one loud element. Plate stack or the running clock. ──

@Composable
private fun Readout(state: SetLoggingUiState) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = state.rest is RestState.Running,
            animationSpec = tween(120),
            label = "readout",
        ) { resting ->
            if (resting && state.rest is RestState.Running) {
                RestClock(state.rest)
            } else {
                LoadReadout(state)
            }
        }
    }
}

@Composable
private fun LoadReadout(state: SetLoggingUiState) {
    val colors = RedplateTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.isPlateLoaded && state.plateLoad != null) {
            PlateStack(plateLoad = state.plateLoad, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            if (!state.isExactLoad) {
                Text("NEAREST LOADABLE", style = RedplateType.label, color = colors.inkMuted)
            }
        } else {
            Text(formatKg(state.loadKg), style = RedplateType.timer, color = colors.ink)
            Text("KG", style = RedplateType.label, color = colors.inkMuted)
        }
    }
}

@Composable
private fun RestClock(running: RestState.Running) {
    val colors = RedplateTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("REST", style = RedplateType.label, color = colors.inkMuted)
        // The running timer is this screen's single warm accent.
        Text(formatClock(running.remainingSeconds), style = RedplateType.timer, color = colors.live)
    }
}

// ── Controls ──

@Composable
private fun WarmupToggle(isWarmup: Boolean, onToggle: () -> Unit) {
    val colors = RedplateTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box(
            Modifier
                .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isWarmup) colors.surface else Color.Transparent)
                .border(1.dp, if (isWarmup) colors.ink else colors.line, RoundedCornerShape(10.dp))
                .clickable(onClickLabel = if (isWarmup) "Warm-up set, on" else "Warm-up set, off") { onToggle() }
                .semantics { role = Role.Switch }
                .padding(horizontal = 18.dp),
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
private fun LoadStepper(loadKg: Double, onDown: () -> Unit, onUp: () -> Unit) {
    val colors = RedplateTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepButton("−", "Decrease load", onClick = onDown)
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("LOAD", style = RedplateType.label, color = colors.inkMuted)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatKg(loadKg), style = RedplateType.load, color = colors.ink)
                Spacer(Modifier.width(6.dp))
                Text("KG", style = RedplateType.label, color = colors.inkMuted)
            }
        }
        StepButton("+", "Increase load", onClick = onUp)
    }
}

@Composable
private fun CountStepper(
    label: String,
    value: String,
    decDescription: String,
    incDescription: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = RedplateType.label, color = colors.inkMuted)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepButton("−", decDescription, onClick = onDown)
            Text(value, style = RedplateType.figure, color = colors.ink)
            StepButton("+", incDescription, onClick = onUp)
        }
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
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
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
private fun RestControls(
    remaining: Int,
    onSub: () -> Unit,
    onSkip: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RestPill("−15s", "Subtract 15 seconds", onClick = onSub)
        RestPill("SKIP REST", "Skip rest", onClick = onSkip, modifier = Modifier.weight(1f))
        RestPill("+15s", "Add 15 seconds", onClick = onAdd)
    }
}

@Composable
private fun RestPill(text: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RedplateTheme.colors
    Box(
        modifier
            .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = RedplateType.label, color = colors.ink)
    }
}

@Composable
private fun PrimaryBar(enabled: Boolean, emphasized: Boolean, onClick: () -> Unit) {
    val colors = RedplateTheme.colors
    val background = when {
        !enabled -> colors.surface
        emphasized -> colors.live
        else -> colors.surface
    }
    val textColor = when {
        !enabled -> colors.inkMuted
        emphasized -> Color(0xFF000000)
        else -> colors.ink
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .background(background)
            .clickable(enabled = enabled) { onClick() }
            .semantics(mergeDescendants = true) {
                contentDescription = "Complete set"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "COMPLETE SET",
            style = RedplateType.exerciseName,
            color = textColor,
            textAlign = TextAlign.Center,
        )
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
// Previews — every meaningful screen state, on true black at 384×824 dp.
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
            onRirDown = {}, onRirUp = {},
            onToggleWarmup = {}, onCompleteSet = {},
            onSkipRest = {}, onAddRest = {}, onSubRest = {},
        )
    }
}

@Preview(name = "First set", showBackground = true, backgroundColor = 0xFF000000, widthDp = 384, heightDp = 824)
@Composable
private fun FirstSetPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Barbell Bench Press",
            hasGuidance = true,
            setNumber = 1, targetSets = 4, repRangeLow = 6, repRangeHigh = 10, targetRir = 2,
            loadKg = 100.0, reps = 10, rir = 2,
            isPlateLoaded = true,
            plateLoad = PlateMath.PlateLoad(100.0, listOf(20.0, 20.0), true),
            previousSets = PREVIOUS,
        )
    )
}

@Preview(name = "Mid-workout · PR", showBackground = true, backgroundColor = 0xFF000000, widthDp = 384, heightDp = 824)
@Composable
private fun MidWorkoutPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Barbell Bench Press",
            hasGuidance = true,
            setNumber = 3, targetSets = 4, repRangeLow = 6, repRangeHigh = 10, targetRir = 2,
            loadKg = 105.0, reps = 8, rir = 1,
            isPlateLoaded = true,
            plateLoad = PlateMath.PlateLoad(105.0, listOf(20.0, 20.0, 2.5), true),
            previousSets = PREVIOUS,
            loggedSets = listOf(
                LoggedSetLine(0, 60.0, 10, null, isWarmup = true, isPr = false),
                LoggedSetLine(1, 100.0, 10, 2, isWarmup = false, isPr = false),
                LoggedSetLine(2, 105.0, 9, 1, isWarmup = false, isPr = true),
            ),
        )
    )
}

@Preview(name = "Resting", showBackground = true, backgroundColor = 0xFF000000, widthDp = 384, heightDp = 824)
@Composable
private fun RestingPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Barbell Bench Press",
            setNumber = 3, targetSets = 4, repRangeLow = 6, repRangeHigh = 10, targetRir = 2,
            loadKg = 105.0, reps = 8, rir = 1,
            isPlateLoaded = true,
            plateLoad = PlateMath.PlateLoad(105.0, listOf(20.0, 20.0, 2.5), true),
            previousSets = PREVIOUS,
            loggedSets = listOf(
                LoggedSetLine(1, 100.0, 10, 2, isWarmup = false, isPr = false),
                LoggedSetLine(2, 105.0, 8, 1, isWarmup = false, isPr = false),
            ),
            rest = RestState.Running(remainingSeconds = 132, totalSeconds = 180),
        )
    )
}

@Preview(name = "Warm-up · non-exact", showBackground = true, backgroundColor = 0xFF000000, widthDp = 384, heightDp = 824)
@Composable
private fun WarmupPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Barbell Back Squat",
            hasGuidance = true,
            setNumber = 1, targetSets = 5, repRangeLow = 4, repRangeHigh = 6, targetRir = 3,
            loadKg = 62.5, reps = 5, rir = null,
            isWarmup = true,
            isPlateLoaded = true,
            plateLoad = PlateMath.PlateLoad(62.5, listOf(20.0, 1.25), false),
            isExactLoad = false,
            previousSets = PREVIOUS,
        )
    )
}

@Preview(name = "Superset · dumbbell", showBackground = true, backgroundColor = 0xFF000000, widthDp = 384, heightDp = 824)
@Composable
private fun DumbbellSupersetPreview() {
    PreviewScreen(
        SetLoggingUiState(
            isLoading = false,
            exerciseName = "Incline Dumbbell Curl",
            supersetLabel = "SUPERSET A",
            setNumber = 2, targetSets = 3, repRangeLow = 10, repRangeHigh = 15, targetRir = 1,
            loadKg = 17.5, reps = 12, rir = 1,
            isPlateLoaded = false,
            previousSets = listOf(
                PreviousSetLine(15.0, 14, 1),
                PreviousSetLine(15.0, 12, 0),
            ),
            loggedSets = listOf(
                LoggedSetLine(0, 17.5, 13, 2, isWarmup = false, isPr = false),
            ),
        )
    )
}
