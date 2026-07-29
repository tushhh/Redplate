package dev.redplate.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.BlockPhase
import dev.redplate.data.ExerciseDao
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.MesocycleEntity
import dev.redplate.data.MuscleGroup
import dev.redplate.data.ProgramDao
import dev.redplate.data.SessionDao
import dev.redplate.data.SessionEntity
import dev.redplate.data.SessionTemplateEntity
import dev.redplate.data.ScheduleEdit
import dev.redplate.data.ScheduleEditor
import dev.redplate.data.SessionEstimate
import dev.redplate.data.SetLogEntity
import dev.redplate.data.TrainingClock
import dev.redplate.data.VolumeCredit
import dev.redplate.data.VolumeDao
import dev.redplate.data.VolumeLandmarks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

data class DayCard(
    val dayLabel: String,
    val sessionName: String?,
    val templateId: Long?,
    val setCount: Int,
    val status: DayStatus,
    /** "18 SETS · 9.1 T" when done, "20 SETS · 58 MIN · TODAY" today, else "N SETS · PLANNED". */
    val detailLine: String = "",
    /** 0 = Monday. What a move targets, and what tells a rest day where it sits. */
    val weekdayIndex: Int = 0,
)

enum class DayStatus { DONE, TODAY, PLANNED, REST }

data class VolumeTarget(
    val muscleName: String,
    val current: Int,
    val target: Int,
    /** The faint tick. Null until there are past weeks to average. */
    val fourWeekAverage: Int? = null,
)

data class WeekPlanState(
    val weekNumber: Int = 1,
    val totalWeeks: Int = 5,
    val splitName: String = "",
    val splitDescription: String = "",
    val phase: BlockPhase = BlockPhase.ACCUMULATION,
    val days: List<DayCard> = emptyList(),
    /** All eleven groups, for the chart below the week list (design 10a). */
    val balance: List<VolumeTarget> = emptyList(),
    val balanceCoachLine: String = "",
    /** What happens to the block next — deload stated up front, never sprung. */
    val blockNote: String = "",
    val isLoading: Boolean = true,

    // ── Rescheduling ────────────────────────────────────────────────
    /** Non-null while the user is picking a new day for a session. */
    val movingTemplateId: Long? = null,
    val movingSessionName: String? = null,

    // ── Volume targets ──────────────────────────────────────────────
    val isEditingTargets: Boolean = false,
    /** Working copy of each muscle's weekly cap, uncommitted until saved. */
    val targetEdits: Map<MuscleGroup, Int> = emptyMap(),

    /** One-shot confirmation of the last edit. Cleared once shown. */
    val message: String? = null,
)

@HiltViewModel
class WeekPlanViewModel @Inject constructor(
    private val programDao: ProgramDao,
    private val sessionDao: SessionDao,
    private val volumeDao: VolumeDao,
    private val exerciseDao: ExerciseDao,
    private val trainingClock: TrainingClock,
    private val scheduleEditor: ScheduleEditor,
) : ViewModel() {

    private val _state = MutableStateFlow(WeekPlanState())
    val state: StateFlow<WeekPlanState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val meso = programDao.getActiveMesocycle()
        if (meso == null) {
            _state.value = WeekPlanState(isLoading = false)
            return
        }

        val templates = programDao.getAllTemplates().filter { it.mesocycleId == meso.id }
        val exercises = exerciseDao.getAll().associateBy { it.id }

        // Only the window the screen actually draws: this week plus the four the average
        // is taken over. This used to load the entire set-log table on every resume.
        val dayStartHour = trainingClock.dayStartHour()
        val weekStartsOn = trainingClock.weekStartsOn()
        val weekStart = trainingClock.weekStart(trainingClock.todayDate(), weekStartsOn)
        val window = trainingClock.weekBounds(weekStart.minusWeeks(PRIOR_WEEKS.toLong()), dayStartHour)
        val until = trainingClock.weekBounds(weekStart, dayStartHour).last + 1
        val recentSets = sessionDao.getSetLogsBetween(window.first, until)
        val sessions = sessionDao.getSessionsStartedBetween(window.first, until)
            .associateBy { it.id }

        val balance = buildBalance(recentSets, exercises, dayStartHour, weekStart)

        val carried = _state.value
        _state.value = WeekPlanState(
            weekNumber = meso.currentWeek,
            totalWeeks = meso.lengthWeeks,
            splitName = meso.name,
            splitDescription = describeSplit(meso, templates),
            phase = if (meso.currentWeek >= meso.lengthWeeks) {
                BlockPhase.DELOAD
            } else {
                BlockPhase.ACCUMULATION
            },
            days = buildDayCards(templates, recentSets, sessions, dayStartHour, weekStart, weekStartsOn),
            balance = balance,
            balanceCoachLine = buildBalanceCoachLine(balance),
            blockNote = buildBlockNote(meso),
            isLoading = false,
            // Carried across the reload: a confirmation the user has not read yet, and an
            // editor they are still in the middle of, are not stale state.
            movingTemplateId = carried.movingTemplateId,
            movingSessionName = carried.movingSessionName,
            isEditingTargets = carried.isEditingTargets,
            targetEdits = carried.targetEdits,
            message = carried.message,
        )
    }

    // ── The week ────────────────────────────────────────────────────

    private suspend fun buildDayCards(
        templates: List<SessionTemplateEntity>,
        allSets: List<SetLogEntity>,
        sessions: Map<Long, SessionEntity>,
        dayStartHour: Int,
        weekStart: LocalDate,
        weekStartsOn: Int,
    ): List<DayCard> {
        // Training days, not calendar days: a session logged at 01:00 belongs to the day
        // before, and to last week if that day was a Sunday.
        val today = trainingClock.todayDate()
        // The week runs from the user's own first day, not from Monday. Someone who
        // started training on a Thursday has a Thursday-to-Wednesday week.
        val order = trainingClock.weekdayOrder(weekStartsOn)

        // Tonnage per template for sessions logged this week, so a finished day reports
        // what it actually cost rather than what it was supposed to.
        val tonnageByTemplate = allSets
            .filter {
                !it.isWarmup &&
                    trainingClock.trainingDate(it.completedAt, dayStartHour) >= weekStart
            }
            .groupBy { sessions[it.sessionId]?.templateId }
            .mapValues { (_, sets) -> sets.sumOf { it.loadKg * it.reps } }

        return order.map { index ->
            val label = DAY_LABELS[index]
            val template = templates.find { it.dayIndex == index }
                ?: return@map DayCard(label, null, null, 0, DayStatus.REST, weekdayIndex = index)

            val slots = programDao.getSlots(template.id)
            val sets = slots.sumOf { it.targetSets }
            val dayOfWeek = DayOfWeek.of(index + 1)
            val done = tonnageByTemplate.containsKey(template.id)

            val status = when {
                done -> DayStatus.DONE
                dayOfWeek == today.dayOfWeek -> DayStatus.TODAY
                else -> DayStatus.PLANNED
            }

            DayCard(
                dayLabel = label,
                sessionName = template.label,
                templateId = template.id,
                setCount = sets,
                status = status,
                detailLine = when (status) {
                    DayStatus.DONE -> {
                        val tonnes = (tonnageByTemplate[template.id] ?: 0.0) / 1000.0
                        "$sets SETS · ${"%.1f".format(tonnes)} T"
                    }

                    DayStatus.TODAY ->
                        "$sets SETS · ${SessionEstimate.minutes(slots)} MIN · TODAY"
                    else -> "$sets SETS · PLANNED"
                },
                weekdayIndex = index,
            )
        }
    }

    private fun describeSplit(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
    ): String {
        val phase = if (meso.currentWeek >= meso.lengthWeeks) "deload week" else "building volume"
        return "${meso.name} · $phase · ${templates.size} days"
    }

    /** Deload stated up front rather than sprung on you in week five. */
    private fun buildBlockNote(meso: MesocycleEntity): String = when {
        meso.currentWeek >= meso.lengthWeeks ->
            "This is the deload — same movements, about 10% lighter, stopping well short. " +
                "This is the week the growth actually lands."

        meso.currentWeek == meso.lengthWeeks - 1 ->
            "Sets climb once more, then next week is a deload — same movements, about 10% " +
                "lighter, stopping well short. That week is where the growth actually lands."

        else ->
            "Sets climb again next week, then week ${meso.lengthWeeks} is a deload — same " +
                "movements, about 10% lighter, stopping well short. That week is where the " +
                "growth actually lands."
    }

    // ── Balance (design 10a) ────────────────────────────────────────

    /**
     * Every charted muscle, this week against its cap, with the four-week average as the
     * tick. Secondaries count half and only 0–3 RIR sets count, so this agrees with
     * Today's footer and the session summary by construction rather than by coincidence.
     */
    private fun buildBalance(
        allSets: List<SetLogEntity>,
        exercises: Map<String, ExerciseEntity>,
        dayStartHour: Int,
        weekStart: LocalDate,
    ): List<VolumeTarget> {
        val credited = allSets.filter { it.countsTowardVolume }
        fun dateOf(set: SetLogEntity) = trainingClock.trainingDate(set.completedAt, dayStartHour)

        val thisWeek = VolumeCredit.perMuscle(
            credited.filter { dateOf(it) >= weekStart },
            exercises,
        )

        val priorWeeks = (1..PRIOR_WEEKS).map { back ->
            val start = weekStart.minusWeeks(back.toLong())
            val end = start.plusWeeks(1)
            VolumeCredit.perMuscle(
                credited.filter {
                    val d = dateOf(it)
                    d >= start && d < end
                },
                exercises,
            )
        }
        val hasHistory = priorWeeks.any { it.isNotEmpty() }

        return CHART_MUSCLES.map { muscle ->
            val landmark = VolumeLandmarks.forMuscle(muscle)
            VolumeTarget(
                muscleName = muscle.displayName(),
                current = (thisWeek[muscle] ?: 0.0).roundToInt(),
                target = landmark.mrv,
                fourWeekAverage = if (!hasHistory) {
                    null
                } else {
                    priorWeeks.map { it[muscle] ?: 0.0 }.average().roundToInt()
                },
            )
        }
    }

    /**
     * Names the group that went over and the one genuinely behind — the two facts a
     * glance at eleven bars cannot produce on its own.
     */
    private fun buildBalanceCoachLine(rows: List<VolumeTarget>): String {
        if (rows.all { it.current == 0 }) {
            return "Nothing logged this week yet. Bars fill as you train."
        }

        val over = rows.filter { it.target > 0 && it.current > it.target }
        val gap = rows
            .filter { it.target > 0 }
            .minByOrNull { it.current.toFloat() / it.target }

        val overClause = when {
            over.isEmpty() -> null
            over.size == 1 ->
                "${over.first().muscleName} is over its cap — a later day drops a row."

            else ->
                "${over.joinToString(" and ") { it.muscleName }} are over cap — later days drop a row."
        }
        val gapClause = gap
            ?.takeIf { it.current < it.target }
            ?.let { "${it.muscleName} is the real gap: ${it.current} of ${it.target}." }

        return listOfNotNull(overClause, gapClause)
            .joinToString(" ")
            .ifEmpty { "Every group is inside its cap this week." }
    }

    // ── Moving a session to another day ─────────────────────────────

    /**
     * Opens the day picker for one session. The schedule is the user's to arrange; the
     * split's layout is a suggestion it opens with, not a cage.
     */
    fun beginMove(templateId: Long) {
        val day = _state.value.days.firstOrNull { it.templateId == templateId } ?: return
        _state.value = _state.value.copy(
            movingTemplateId = templateId,
            movingSessionName = day.sessionName,
        )
    }

    fun cancelMove() {
        _state.value = _state.value.copy(movingTemplateId = null, movingSessionName = null)
    }

    fun moveTo(weekdayIndex: Int) {
        val templateId = _state.value.movingTemplateId ?: return
        viewModelScope.launch {
            val result = scheduleEditor.moveSession(templateId, weekdayIndex)
            _state.value = _state.value.copy(
                movingTemplateId = null,
                movingSessionName = null,
                message = describeMove(result),
            )
            load()
        }
    }

    private fun describeMove(edit: ScheduleEdit): String? = when (edit) {
        is ScheduleEdit.Moved -> "${edit.sessionLabel} moved to ${DAY_NAMES[edit.toWeekdayIndex]}."
        is ScheduleEdit.Swapped -> "${edit.first} and ${edit.second} swapped days."
        is ScheduleEdit.DayTaken -> "${edit.occupiedBy} is already on that day."
        ScheduleEdit.NoActiveBlock -> "There's no active block to reschedule."
        is ScheduleEdit.StartDateSet -> null
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    // ── Volume targets ──────────────────────────────────────────────

    /** Opens the editor with the landmarks as they stand. */
    fun beginEditingTargets() {
        viewModelScope.launch {
            val stored = volumeDao.getAllLandmarks().associateBy { it.muscle }
            _state.value = _state.value.copy(
                targetEdits = CHART_MUSCLES.associateWith { muscle ->
                    (stored[muscle] ?: VolumeLandmarks.forMuscle(muscle)).mrv
                },
                isEditingTargets = true,
            )
        }
    }

    fun cancelEditingTargets() {
        _state.value = _state.value.copy(isEditingTargets = false, targetEdits = emptyMap())
    }

    fun adjustTarget(muscle: MuscleGroup, delta: Int) {
        val current = _state.value.targetEdits[muscle] ?: return
        _state.value = _state.value.copy(
            targetEdits = _state.value.targetEdits +
                (muscle to (current + delta).coerceIn(MIN_TARGET, MAX_TARGET)),
        )
    }

    /**
     * Writes the edited caps, marking each row [VolumeLandmarkEntity.userAdjusted] so
     * regenerating a program never stamps back over a number the user chose themselves.
     */
    fun saveTargets() {
        val edits = _state.value.targetEdits
        if (edits.isEmpty()) return
        viewModelScope.launch {
            val stored = volumeDao.getAllLandmarks().associateBy { it.muscle }
            volumeDao.upsertLandmarks(
                edits.map { (muscle, mrv) ->
                    val base = stored[muscle] ?: VolumeLandmarks.forMuscle(muscle)
                    base.copy(
                        mrv = mrv,
                        // The adaptive ceiling cannot sit above the recoverable maximum.
                        mavHigh = base.mavHigh.coerceAtMost(mrv),
                        userAdjusted = true,
                    )
                }
            )
            _state.value = _state.value.copy(
                isEditingTargets = false,
                targetEdits = emptyMap(),
                message = "Targets saved. They won't be overwritten when a block is rebuilt.",
            )
            load()
        }
    }

    /** Back to the values from COACHING.md §3, clearing the user-adjusted flag. */
    fun resetTargetsToDefaults() {
        viewModelScope.launch {
            volumeDao.upsertLandmarks(VolumeLandmarks.DEFAULTS)
            _state.value = _state.value.copy(
                isEditingTargets = false,
                targetEdits = emptyMap(),
                message = "Targets back to the defaults.",
            )
            load()
        }
    }

    private companion object {
        const val PRIOR_WEEKS = 4

        /** Indexed 0 = Monday, matching SessionTemplateEntity.dayIndex. */
        val DAY_LABELS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        val DAY_NAMES = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        )

        /** A cap below this is not a training week; above it is not a recoverable one. */
        const val MIN_TARGET = 2
        const val MAX_TARGET = 40

        /** The eleven groups the chart draws, in the design's order. */
        val CHART_MUSCLES = listOf(
            MuscleGroup.CHEST,
            MuscleGroup.UPPER_BACK,
            MuscleGroup.SIDE_DELTS,
            MuscleGroup.BICEPS,
            MuscleGroup.TRICEPS,
            MuscleGroup.QUADS,
            MuscleGroup.HAMSTRINGS,
            MuscleGroup.GLUTES,
            MuscleGroup.CALVES,
            MuscleGroup.ABS,
            MuscleGroup.FOREARMS,
        )
    }
}

/** Chart labels use the everyday word, not the enum's anatomical one. */
private fun MuscleGroup.displayName(): String = when (this) {
    MuscleGroup.UPPER_BACK -> "Back"
    MuscleGroup.SIDE_DELTS -> "Shoulders"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
}
