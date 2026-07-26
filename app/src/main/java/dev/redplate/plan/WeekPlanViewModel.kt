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
import dev.redplate.data.SetLogEntity
import dev.redplate.data.VolumeDao
import dev.redplate.data.VolumeLandmarks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
)

@HiltViewModel
class WeekPlanViewModel @Inject constructor(
    private val programDao: ProgramDao,
    private val sessionDao: SessionDao,
    private val volumeDao: VolumeDao,
    private val exerciseDao: ExerciseDao,
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
        val allSets = sessionDao.getAllSetLogs()
        val sessions = sessionDao.getAllSessions().associateBy { it.id }

        val balance = buildBalance(allSets, exercises)

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
            days = buildDayCards(templates, allSets, sessions),
            balance = balance,
            balanceCoachLine = buildBalanceCoachLine(balance),
            blockNote = buildBlockNote(meso),
            isLoading = false,
        )
    }

    // ── The week ────────────────────────────────────────────────────

    private suspend fun buildDayCards(
        templates: List<SessionTemplateEntity>,
        allSets: List<SetLogEntity>,
        sessions: Map<Long, SessionEntity>,
    ): List<DayCard> {
        val today = LocalDate.now()
        val weekStart = today.with(DayOfWeek.MONDAY)
        val dayLabels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

        // Tonnage per template for sessions logged this week, so a finished day reports
        // what it actually cost rather than what it was supposed to.
        val tonnageByTemplate = allSets
            .filter { !it.isWarmup && localDate(it.completedAt) >= weekStart }
            .groupBy { sessions[it.sessionId]?.templateId }
            .mapValues { (_, sets) -> sets.sumOf { it.loadKg * it.reps } }

        return dayLabels.mapIndexed { index, label ->
            val template = templates.find { it.dayIndex == index }
                ?: return@mapIndexed DayCard(label, null, null, 0, DayStatus.REST)

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

                    DayStatus.TODAY -> "$sets SETS · ${sets * MINUTES_PER_SET} MIN · TODAY"
                    else -> "$sets SETS · PLANNED"
                },
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
    ): List<VolumeTarget> {
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
        val credited = allSets.filter { it.countsTowardVolume }

        val thisWeek = creditPerMuscle(
            credited.filter { localDate(it.completedAt) >= weekStart },
            exercises,
        )

        val priorWeeks = (1..PRIOR_WEEKS).map { back ->
            val start = weekStart.minusWeeks(back.toLong())
            val end = start.plusWeeks(1)
            creditPerMuscle(
                credited.filter {
                    val d = localDate(it.completedAt)
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

    private fun creditPerMuscle(
        sets: List<SetLogEntity>,
        exercises: Map<String, ExerciseEntity>,
    ): Map<MuscleGroup, Double> {
        val perMuscle = mutableMapOf<MuscleGroup, Double>()
        for (set in sets) {
            val exercise = exercises[set.exerciseId] ?: continue
            perMuscle.merge(exercise.primaryMuscle, 1.0, Double::plus)
            exercise.secondaryMuscles.forEach { perMuscle.merge(it, 0.5, Double::plus) }
        }
        return perMuscle
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

    /**
     * Restores the default landmarks. "Adjust targets" is a real action rather than a
     * dead button; per-muscle tuning is a screen that does not exist yet, so this does
     * the one thing that is unambiguous and reversible.
     */
    fun resetTargetsToDefaults() {
        viewModelScope.launch {
            volumeDao.upsertLandmarks(VolumeLandmarks.DEFAULTS)
            load()
        }
    }

    private fun localDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    private companion object {
        const val MINUTES_PER_SET = 3
        const val PRIOR_WEEKS = 4

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
