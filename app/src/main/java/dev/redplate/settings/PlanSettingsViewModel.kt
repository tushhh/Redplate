package dev.redplate.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.coach.CoachCopy
import dev.redplate.data.Goal
import dev.redplate.data.MovementPattern
import dev.redplate.data.MuscleGroup
import dev.redplate.data.PlanRevision
import dev.redplate.data.PlanRevisionResult
import dev.redplate.data.PlanSettings
import dev.redplate.data.ScheduleEditor
import dev.redplate.data.TrainingClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PlanSettingsState(
    val saved: PlanSettings? = null,
    val draft: PlanSettings? = null,
    val isSaving: Boolean = false,
    /** The training day the block begins on, or null when it simply runs from creation. */
    val startDate: LocalDate? = null,
    val today: LocalDate? = null,
    /** First day of the training week, 0 = Monday. */
    val weekStartsOn: Int = TrainingClock.DEFAULT_WEEK_STARTS_ON,
    /** Shown after a save, so the screen says what it did rather than just closing. */
    val message: String? = null,
    val isLoading: Boolean = true,
) {
    val hasChanges: Boolean get() = saved != null && draft != null && draft != saved

    /** True when confirming would rebuild the rest of the block. */
    val rebuildsBlock: Boolean
        get() = saved != null && draft != null && draft.normalised().needsRebuild(saved)

    /** The weekday picker only makes sense once the right number of days is chosen. */
    val weekdaySelectionValid: Boolean
        get() = draft?.trainingDays?.let { it.size == draft.daysPerWeek } ?: true
}

/**
 * Editing the plan after intake.
 *
 * Goal, days per week, session length and the split could be answered exactly once, during
 * onboarding, and never revisited — changing your mind meant wiping the app. Everything
 * here goes through [PlanSettings] and [PlanRevision], the same model the intake writes
 * through, so there is no second and weaker code path for "changed my mind".
 */
@HiltViewModel
class PlanSettingsViewModel @Inject constructor(
    private val planRevision: PlanRevision,
    private val scheduleEditor: ScheduleEditor,
    private val trainingClock: TrainingClock,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanSettingsState())
    val state: StateFlow<PlanSettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val current = planRevision.preview()
            _state.value = PlanSettingsState(
                saved = current,
                draft = current,
                startDate = scheduleEditor.startDate(),
                today = trainingClock.todayDate(),
                weekStartsOn = trainingClock.weekStartsOn(),
                isLoading = false,
            )
        }
    }

    // ── Schedule. Applied immediately: these move a calendar, not a plan. ──

    /**
     * Sets the day the block begins. Nothing about the training changes, so unlike goal or
     * days-per-week this does not wait for a confirm and does not rebuild anything.
     */
    fun setStartDate(date: LocalDate) {
        viewModelScope.launch {
            scheduleEditor.setStartDate(date)
            _state.update { it.copy(startDate = date, message = CoachCopy.Plan.startsOn(date)) }
        }
    }

    fun startToday() {
        viewModelScope.launch {
            scheduleEditor.startNow()
            val today = trainingClock.todayDate()
            _state.update { it.copy(startDate = today, message = CoachCopy.Plan.STARTS_TODAY) }
        }
    }

    /** First day of the training week, 0 = Monday. */
    fun setWeekStartsOn(weekdayIndex: Int) {
        viewModelScope.launch {
            scheduleEditor.setWeekStartsOn(weekdayIndex)
            _state.update {
                it.copy(
                    weekStartsOn = weekdayIndex,
                    message = CoachCopy.Plan.weekStartsOn(weekdayIndex),
                )
            }
        }
    }

    // ── Editing the draft. Nothing is written until confirm. ────────────

    fun setGoal(goal: Goal) = edit { it.copy(goal = goal) }

    fun setDaysPerWeek(days: Int) = edit {
        // A weekday selection that no longer matches the day count is stale rather than
        // wrong; dropping it falls back to the split's layout until they pick again.
        val kept = it.trainingDays?.takeIf { chosen -> chosen.size == days }
        it.copy(daysPerWeek = days.coerceIn(PlanSettings.DAYS_RANGE), trainingDays = kept)
    }

    fun setSessionMinutes(minutes: Int) = edit { it.copy(sessionCeilingMinutes = minutes) }

    fun setDayStartHour(hour: Int) = edit {
        it.copy(dayStartHour = hour.coerceIn(TrainingClock.DAY_START_HOURS))
    }

    /** Two at most: a priority every muscle shares is not a priority. */
    fun togglePriorityMuscle(muscle: MuscleGroup) = edit { draft ->
        val current = draft.priorityMuscles
        val next = when {
            muscle in current -> current - muscle
            current.size < PlanSettings.MAX_PRIORITY_MUSCLES -> current + muscle
            else -> listOf(current.last(), muscle)   // oldest choice makes way
        }
        draft.copy(priorityMuscles = next)
    }

    fun toggleExcludedPattern(pattern: MovementPattern) = edit { draft ->
        val current = draft.excludedPatterns
        draft.copy(
            excludedPatterns = if (pattern in current) current - pattern else current + pattern,
        )
    }

    fun toggleTrainingDay(weekdayIndex: Int) = edit { draft ->
        val current = draft.trainingDays ?: draft.weekdayIndices()
        val next = if (weekdayIndex in current) current - weekdayIndex else current + weekdayIndex
        draft.copy(trainingDays = next.sorted())
    }

    /** Back to the split's own layout. */
    fun clearTrainingDays() = edit { it.copy(trainingDays = null) }

    fun discardChanges() = _state.update { it.copy(draft = it.saved) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ── Committing ──────────────────────────────────────────────────────

    fun confirm(onApplied: () -> Unit) {
        val draft = _state.value.draft ?: return
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val result = planRevision.apply(draft)
            val current = planRevision.preview()
            _state.update {
                it.copy(
                    saved = current,
                    draft = current,
                    isSaving = false,
                    message = describe(result),
                    isLoading = false,
                )
            }
            onApplied()
        }
    }

    private fun describe(result: PlanRevisionResult): String = when (result) {
        PlanRevisionResult.NoProfile -> CoachCopy.Plan.NO_PROFILE_TO_CHANGE

        PlanRevisionResult.SettingsOnly -> CoachCopy.Plan.SETTINGS_ONLY

        is PlanRevisionResult.Adjusted ->
            CoachCopy.Plan.adjusted(result.daysMoved, result.templatesRefitted)

        is PlanRevisionResult.Rebuilt -> CoachCopy.Plan.REBUILT
    }

    private fun edit(block: (PlanSettings) -> PlanSettings) = _state.update { state ->
        state.draft?.let { state.copy(draft = block(it)) } ?: state
    }
}
