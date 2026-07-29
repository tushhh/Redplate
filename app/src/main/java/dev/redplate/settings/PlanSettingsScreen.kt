package dev.redplate.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import dev.redplate.coach.CoachCopy
import dev.redplate.data.Goal
import dev.redplate.data.MovementPattern
import dev.redplate.data.MuscleGroup
import dev.redplate.data.PlanSettings
import dev.redplate.data.TrainingClock
import dev.redplate.ui.components.CoachHeadline
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

@Composable
fun PlanSettingsRoute(onDone: () -> Unit) {
    val viewModel: PlanSettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    PlanSettingsScreen(
        state = state,
        onSetGoal = viewModel::setGoal,
        onSetDays = viewModel::setDaysPerWeek,
        onSetMinutes = viewModel::setSessionMinutes,
        onSetDayStartHour = viewModel::setDayStartHour,
        onTogglePriority = viewModel::togglePriorityMuscle,
        onToggleExcluded = viewModel::toggleExcludedPattern,
        onToggleTrainingDay = viewModel::toggleTrainingDay,
        onClearTrainingDays = viewModel::clearTrainingDays,
        onSetStartDate = viewModel::setStartDate,
        onStartToday = viewModel::startToday,
        onSetWeekStartsOn = viewModel::setWeekStartsOn,
        onDiscard = viewModel::discardChanges,
        onConfirm = { viewModel.confirm(onDone) },
    )
}

/**
 * "Your plan" — the answers the intake asks, made changeable.
 *
 * The rule the whole screen turns on: nothing here writes until the bar at the bottom is
 * pressed, and the bar says up front whether pressing it rebuilds the block. A plan that
 * silently regenerated itself when you nudged a toggle would be a plan you could not trust.
 */
@Composable
fun PlanSettingsScreen(
    state: PlanSettingsState,
    onSetGoal: (Goal) -> Unit = {},
    onSetDays: (Int) -> Unit = {},
    onSetMinutes: (Int) -> Unit = {},
    onSetDayStartHour: (Int) -> Unit = {},
    onTogglePriority: (MuscleGroup) -> Unit = {},
    onToggleExcluded: (MovementPattern) -> Unit = {},
    onToggleTrainingDay: (Int) -> Unit = {},
    onClearTrainingDays: () -> Unit = {},
    onSetStartDate: (LocalDate) -> Unit = {},
    onStartToday: () -> Unit = {},
    onSetWeekStartsOn: (Int) -> Unit = {},
    onDiscard: () -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    val colors = RedplateTheme.colors
    val draft = state.draft

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
            MonoLabel(text = "YOUR PLAN")
            Spacer(Modifier.height(10.dp))
            CoachHeadline(text = CoachCopy.Plan.HEADLINE)
            Spacer(Modifier.height(5.dp))
            Text(
                text = state.message
                    ?: CoachCopy.Plan.INTRO,
                style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                color = if (state.message != null) colors.live else colors.inkSecondary,
            )
            Spacer(Modifier.height(18.dp))

            if (draft == null) {
                Text(
                    text = CoachCopy.Plan.NO_PROFILE,
                    style = RedplateType.body.copy(fontSize = 15.sp),
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            SectionLabel(text = "What you're training for")
            Spacer(Modifier.height(8.dp))
            ChoiceRows(
                options = Goal.entries.map { it to goalLabel(it) },
                selected = draft.goal,
                consequence = ::goalConsequence,
                onSelect = onSetGoal,
            )
            Spacer(Modifier.height(22.dp))

            SectionLabel(text = "Days a week")
            Spacer(Modifier.height(8.dp))
            ChipRow(
                labels = PlanSettings.DAYS_RANGE.map { it.toString() },
                selectedIndex = draft.daysPerWeek - PlanSettings.DAYS_RANGE.first,
                onSelect = { onSetDays(PlanSettings.DAYS_RANGE.first + it) },
            )
            Spacer(Modifier.height(6.dp))
            Caption("Changing this rebuilds the rest of your block.")
            Spacer(Modifier.height(22.dp))

            SectionLabel(text = "Longest a session may run")
            Spacer(Modifier.height(8.dp))
            ChipRow(
                labels = PlanSettings.SESSION_MINUTES.map { "$it" },
                selectedIndex = PlanSettings.SESSION_MINUTES.indexOf(draft.sessionCeilingMinutes),
                onSelect = { onSetMinutes(PlanSettings.SESSION_MINUTES[it]) },
            )
            Spacer(Modifier.height(6.dp))
            Caption("Minutes, rest included. Sessions are re-fitted, not rebuilt.")
            Spacer(Modifier.height(22.dp))

            SectionLabel(text = "When your block starts")
            Spacer(Modifier.height(8.dp))
            StartDatePicker(
                startDate = state.startDate,
                today = state.today ?: LocalDate.now(),
                onPick = onSetStartDate,
                onStartToday = onStartToday,
            )
            Spacer(Modifier.height(6.dp))
            Caption(
                state.startDate?.let { "Week 1 begins ${describeDate(it)}." }
                    ?: "Running from the day the plan was built. Pick a day to start it on.",
            )
            Spacer(Modifier.height(22.dp))

            SectionLabel(text = "First day of your week")
            Spacer(Modifier.height(8.dp))
            WeekStartPicker(selected = state.weekStartsOn, onSelect = onSetWeekStartsOn)
            Spacer(Modifier.height(6.dp))
            Caption(
                "Where the week rolls over. Train Thursday to Wednesday and the plan should " +
                    "say so, rather than splitting your week across two Mondays.",
            )
            Spacer(Modifier.height(22.dp))

            SectionLabel(text = "Which days you train")
            Spacer(Modifier.height(8.dp))
            WeekdayPicker(
                selected = draft.weekdayIndices().toSet(),
                isCustom = draft.trainingDays != null,
                onToggle = onToggleTrainingDay,
            )
            Spacer(Modifier.height(6.dp))
            Caption(
                if (!state.weekdaySelectionValid) {
                    "Pick exactly ${draft.daysPerWeek} days, or reset to the split's own layout."
                } else if (draft.trainingDays != null) {
                    "Your own days. Reset to use the split's layout instead."
                } else {
                    "The split's own layout. Tap a day to choose your own."
                },
            )
            if (draft.trainingDays != null) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(label = "Reset to the split's days", onClick = onClearTrainingDays)
            }
            Spacer(Modifier.height(22.dp))

            SectionLabel(text = "Muscles to prioritise")
            Spacer(Modifier.height(8.dp))
            ToggleGrid(
                options = PRIORITY_MUSCLES.map { it to muscleLabel(it) },
                selected = draft.priorityMuscles.toSet(),
                onToggle = onTogglePriority,
            )
            Spacer(Modifier.height(6.dp))
            Caption("Up to two. Each one gets an extra set per session.")
            Spacer(Modifier.height(22.dp))

            SectionLabel(text = "Movements to leave out")
            Spacer(Modifier.height(8.dp))
            ToggleGrid(
                options = MovementPattern.entries.map { it to patternLabel(it) },
                selected = draft.excludedPatterns.toSet(),
                onToggle = onToggleExcluded,
            )
            Spacer(Modifier.height(6.dp))
            Caption("Nothing that trains through these will be programmed.")
            Spacer(Modifier.height(22.dp))

            SectionLabel(text = "When a training day starts")
            Spacer(Modifier.height(8.dp))
            ChipRow(
                labels = TrainingClock.DAY_START_HOURS.map { "%02d".format(it) },
                selectedIndex = draft.dayStartHour - TrainingClock.DAY_START_HOURS.first,
                onSelect = { onSetDayStartHour(TrainingClock.DAY_START_HOURS.first + it) },
            )
            Spacer(Modifier.height(6.dp))
            Caption(
                "A session logged before this hour counts against the day before — so a " +
                    "workout that runs past midnight stays on the day it started.",
            )
            Spacer(Modifier.height(18.dp))

            if (state.rebuildsBlock) {
                RebuildWarning()
                Spacer(Modifier.height(10.dp))
            }
            if (state.hasChanges) {
                SecondaryButton(label = "Discard changes", onClick = onDiscard)
            }
            Spacer(Modifier.height(16.dp))
        }

        PrimaryBar(
            label = when {
                state.isSaving -> "Saving…"
                state.rebuildsBlock -> "Rebuild my block"
                state.hasChanges -> "Save changes"
                else -> "Done"
            },
            onClick = onConfirm,
            enabled = !state.isSaving && state.weekdaySelectionValid,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}

/** States the trade plainly, so confirming is informed rather than brave. */
@Composable
private fun RebuildWarning() {
    val colors = RedplateTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(16.dp),
    ) {
        MonoLabel(text = "THIS REBUILDS YOUR BLOCK")
        Spacer(Modifier.height(6.dp))
        Text(
text = CoachCopy.Plan.REBUILD_WARNING,
            style = RedplateType.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
            color = colors.inkSecondary,
        )
    }
}

// ── Small controls ──────────────────────────────────────────────────

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = RedplateType.body.copy(fontSize = 13.sp, lineHeight = 19.sp),
        color = RedplateTheme.colors.inkMuted,
    )
}

/** A stack of 64 dp rows, each stating what choosing it does. */
@Composable
private fun <T> ChoiceRows(
    options: List<Pair<T, String>>,
    selected: T,
    consequence: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = RedplateTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) colors.surfaceRaised else colors.surface)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = RedplateType.body.copy(
                            fontSize = 14.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = consequence(value),
                        style = RedplateType.body.copy(fontSize = 12.5.sp),
                        color = colors.inkMuted,
                    )
                }
                if (isSelected) {
                    Text(
                        text = "SELECTED",
                        style = RedplateType.mono.copy(fontSize = 9.5.sp),
                        color = colors.live,
                    )
                }
            }
        }
    }
}

/** One row of 64 dp square chips — the minimum touch target CLAUDE.md §4 sets. */
@Composable
private fun ChipRow(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = RedplateTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) colors.ink else colors.surface)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = RedplateType.mono.copy(fontSize = 13.sp),
                    color = if (isSelected) colors.inkOnLight else colors.inkSecondary,
                )
            }
        }
    }
}

@Composable
private fun WeekdayPicker(selected: Set<Int>, isCustom: Boolean, onToggle: (Int) -> Unit) {
    val colors = RedplateTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAY_LABELS.forEachIndexed { index, label ->
            val on = index in selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            on && isCustom -> colors.live
                            on -> colors.surfaceRaised
                            else -> colors.surface
                        },
                    )
                    .clickable { onToggle(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = RedplateType.mono.copy(fontSize = 11.sp),
                    color = if (on && isCustom) colors.inkOnLight else colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Wrapping toggles, laid out in rows of three so labels are never clipped. */
@Composable
private fun <T> ToggleGrid(
    options: List<Pair<T, String>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
) {
    val colors = RedplateTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label) ->
                    val on = value in selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) colors.ink else colors.surface)
                            .clickable { onToggle(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = RedplateType.body.copy(fontSize = 12.5.sp),
                            color = if (on) colors.inkOnLight else colors.inkSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // Keeps the last row's chips the same width as every other row's.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f).width(0.dp)) }
            }
        }
    }
}

/**
 * Picks the day the block begins, from today out to a fortnight.
 *
 * A block used to be assumed to have started the moment it was generated, which made
 * "when do I begin?" a question the app answered for you — usually with the Monday of
 * whatever week you happened to install it. Two weeks of choices is enough to cover
 * "tomorrow", "next Monday" and "after this trip" without becoming a calendar widget.
 */
@Composable
private fun StartDatePicker(
    startDate: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onStartToday: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val options = (0..13).map { today.plusDays(it.toLong()) }

    Column {
        SecondaryButton(label = "Start today", onClick = onStartToday)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { date ->
                val selected = date == startDate
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colors.live else colors.surface)
                        .clickable { onPick(date) }
                        .semantics { contentDescription = "Start on ${describeDate(date)}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = date.dayOfWeek
                            .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            .uppercase(),
                        style = RedplateType.mono.copy(fontSize = 9.5.sp),
                        color = if (selected) colors.inkOnLight else colors.inkMuted,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = RedplateType.figure.copy(fontSize = 20.sp),
                        color = if (selected) colors.inkOnLight else colors.ink,
                    )
                }
            }
        }
    }
}

/** Which weekday the training week rolls over on. */
@Composable
private fun WeekStartPicker(selected: Int, onSelect: (Int) -> Unit) {
    val colors = RedplateTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAY_LABELS.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) colors.ink else colors.surface)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = if (on) colors.inkOnLight else colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun describeDate(date: LocalDate): String {
    val day = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$day ${date.dayOfMonth} $month"
}

// ── Labels ──────────────────────────────────────────────────────────

private val WEEKDAY_LABELS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** The groups worth singling out. Every muscle is trained either way. */
private val PRIORITY_MUSCLES = listOf(
    MuscleGroup.CHEST,
    MuscleGroup.UPPER_BACK,
    MuscleGroup.LATS,
    MuscleGroup.SIDE_DELTS,
    MuscleGroup.BICEPS,
    MuscleGroup.TRICEPS,
    MuscleGroup.QUADS,
    MuscleGroup.HAMSTRINGS,
    MuscleGroup.GLUTES,
    MuscleGroup.CALVES,
    MuscleGroup.ABS,
)

private fun goalLabel(goal: Goal): String = when (goal) {
    Goal.STRENGTH -> "Get stronger"
    Goal.HYPERTROPHY -> "Build muscle"
    Goal.LEAN -> "Lean and conditioned"
    Goal.GENERAL -> "Generally fitter"
}

private fun goalConsequence(goal: Goal): String = when (goal) {
    Goal.STRENGTH -> "Low reps, long rests, small weekly jumps"
    Goal.HYPERTROPHY -> "Moderate reps close to failure"
    Goal.LEAN -> "Same reps, shorter rests, denser sessions"
    Goal.GENERAL -> "A middle rep range for both"
}

private fun muscleLabel(muscle: MuscleGroup): String = when (muscle) {
    MuscleGroup.UPPER_BACK -> "Back"
    MuscleGroup.SIDE_DELTS -> "Shoulders"
    else -> muscle.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
}

private fun patternLabel(pattern: MovementPattern): String = when (pattern) {
    MovementPattern.HORIZONTAL_PUSH -> "Flat press"
    MovementPattern.VERTICAL_PUSH -> "Overhead"
    MovementPattern.HORIZONTAL_PULL -> "Row"
    MovementPattern.VERTICAL_PULL -> "Pull-up"
    MovementPattern.SQUAT -> "Squat"
    MovementPattern.HINGE -> "Hinge"
    MovementPattern.LUNGE -> "Lunge"
    MovementPattern.CARRY -> "Carry"
    MovementPattern.ISOLATION -> "Isolation"
    MovementPattern.CORE -> "Core"
}

// ── Previews ────────────────────────────────────────────────────────

private val previewPlan = PlanSettings(
    goal = Goal.HYPERTROPHY,
    daysPerWeek = 4,
    sessionCeilingMinutes = 60,
    priorityMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.LATS),
    excludedPatterns = listOf(MovementPattern.LUNGE),
    trainingDays = null,
    dayStartHour = 4,
)

@Preview(name = "Your plan", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun PlanSettingsPreview() {
    RedplateTheme {
        PlanSettingsScreen(
            state = PlanSettingsState(saved = previewPlan, draft = previewPlan, isLoading = false),
        )
    }
}

@Preview(name = "Your plan · rebuilding", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun PlanSettingsRebuildPreview() {
    RedplateTheme {
        PlanSettingsScreen(
            state = PlanSettingsState(
                saved = previewPlan,
                draft = previewPlan.copy(daysPerWeek = 5, goal = Goal.STRENGTH),
                isLoading = false,
            ),
        )
    }
}

@Preview(name = "Your plan · no profile", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun PlanSettingsEmptyPreview() {
    RedplateTheme {
        PlanSettingsScreen(state = PlanSettingsState(isLoading = false))
    }
}
