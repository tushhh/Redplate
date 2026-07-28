package dev.redplate.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class ExerciseRow(
    val orderIndex: Int,
    val name: String,
    val prescription: String,
    val loadNote: String?,
)

data class SessionCard(
    val label: String,
    /** "20 SETS · 58 MIN", or "14 SETS · ~45 MIN" before there is any history. */
    val summaryLine: String,
    val totalSets: Int,
    val estimatedMinutes: Int,
    val exercises: List<ExerciseRow>,
    val remainingCount: Int,
    val templateId: Long,
)

/** One bar of the six-week estimated-1RM chart on the stall screen (design 9c). */
data class E1rmWeek(
    val label: String,
    val e1rm: Double,
    /** Flat weeks carry the colour — they are the evidence the headline claims. */
    val isFlat: Boolean,
)

/** A line of "A DELOAD WEEK MEANS". [isOutcome] is the one that reads as the payoff. */
data class DeloadEffect(
    val label: String,
    val value: String,
    val isOutcome: Boolean = false,
)

data class VolumeRow(
    val label: String,
    val current: Int,
    val target: Int,
)

sealed interface TodayState {
    data object Loading : TodayState

    data object NoProgramYet : TodayState

    data class TrainingDay(
        val eyebrow: String,
        val headline: String,
        val coachBody: String,
        val sessionCard: SessionCard,
        val volumeRows: List<VolumeRow>,
        val volumeCoachLine: String,
        val primaryLabel: String,
        val isFirstSession: Boolean,
    ) : TodayState

    data class RestDay(
        val eyebrow: String,
        val headline: String,
        val coachBody: String,
        val nextSessionLabel: String?,
    ) : TodayState

    /**
     * Three flat weeks on one lift. Shown here rather than as a notification, and as
     * the evidence rather than a verdict (design 9c).
     */
    data class Stalled(
        val eyebrow: String,
        val headline: String,
        val coachBody: String,
        val e1rmWeeks: List<E1rmWeek>,
        val deloadEffects: List<DeloadEffect>,
        val templateId: Long,
    ) : TodayState
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val programDao: ProgramDao,
    private val sessionDao: SessionDao,
    private val volumeDao: VolumeDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val mesocycleAdvancer: MesocycleAdvancer,
) : ViewModel() {

    private val _state = MutableStateFlow<TodayState>(TodayState.Loading)
    val state: StateFlow<TodayState> = _state.asStateFlow()

    // Holds the template ID + meso ID when there's a training day
    private var pendingTemplateId: Long? = null
    private var pendingMesoId: Long? = null
    private var pendingMesoWeek: Int? = null

    /** "Push on" holds for the life of this ViewModel — never a recurring nag. */
    private var stallDismissed = false

    init {
        refresh()
    }

    /**
     * Recomputes the day. Called on every resume, because finishing a session or editing
     * a program happens on another screen and Today is a summary of both.
     */
    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val profile = profileDao.get()
        if (profile == null) {
            _state.value = TodayState.NoProgramYet
            return
        }

        val active = programDao.getActiveMesocycle()
        if (active == null) {
            _state.value = TodayState.NoProgramYet
            return
        }

        // A block moves forward here, on the screen that asks "what do I do today?".
        // Nothing else was ever going to notice that a week had been completed.
        mesocycleAdvancer.advanceIfDue(active, profile)
        val meso = programDao.getActiveMesocycle() ?: active

        val templates = programDao.observeTemplates(meso.id).first()
        if (templates.isEmpty()) {
            _state.value = TodayState.NoProgramYet
            return
        }

        val today = LocalDate.now()
        val todayDayOfWeek = today.dayOfWeek
        val dayName = todayDayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()

        // Determine which template is today based on rotation
        val todayTemplate = findTodayTemplate(meso, templates, today)

        if (todayTemplate == null) {
            // Rest day
            val nextTemplate = findNextTemplate(meso, templates, today)
            _state.value = TodayState.RestDay(
                eyebrow = "$dayName · WEEK ${meso.currentWeek} OF ${meso.lengthWeeks}",
                headline = "Rest day. You've earned it.",
                coachBody = if (nextTemplate != null) {
                    "Next session is ${nextTemplate.label}."
                } else {
                    "No more sessions scheduled this week."
                },
                nextSessionLabel = nextTemplate?.label,
            )
            return
        }

        pendingTemplateId = todayTemplate.id
        pendingMesoId = meso.id
        pendingMesoWeek = meso.currentWeek

        // A stall takes over the whole screen — the session is still there behind it,
        // but the decision in front of the user is what to do about the flat lift.
        if (!stallDismissed) {
            detectStall(todayTemplate.id)?.let {
                _state.value = it
                return
            }
        }

        val slots = programDao.getSlots(todayTemplate.id)
        val exerciseRows = slots.take(VISIBLE_SLOTS).map { slot ->
            val exercise = exerciseDao.getById(slot.exerciseId)
            val loadText = if (slot.workingLoadKg != null) {
                "${formatKg(slot.workingLoadKg)} kg"
            } else {
                "you choose"
            }
            ExerciseRow(
                orderIndex = slot.orderIndex + 1,
                name = exercise?.name ?: slot.exerciseId,
                prescription = "${slot.targetSets} × ${slot.repRangeLow}–${slot.repRangeHigh} · $loadText",
                loadNote = loadDeltaFor(slot),
            )
        }

        val totalSets = slots.sumOf { it.targetSets }
        // The real number, rest included, and no longer clamped to the ceiling — reporting
        // the ceiling when the session runs past it is just a lie with extra steps.
        val estimatedMinutes = SessionEstimate.minutes(slots)
        val remaining = (slots.size - 3).coerceAtLeast(0)

        val isFirst = sessionDao.getLatestSession() == null

        // Volume data
        val volumeRows = buildVolumeRows(meso.id, meso.currentWeek)

        val volumeCoachLine = if (isFirst) {
            "Fills in as you log. Trends need three sessions."
        } else {
            buildVolumeCoachLine(volumeRows)
        }

        val eyebrow = "$dayName · WEEK ${meso.currentWeek} OF ${meso.lengthWeeks}"

        _state.value = TodayState.TrainingDay(
            eyebrow = eyebrow,
            headline = if (isFirst) {
                "First one. Go light on purpose."
            } else {
                "${todayTemplate.label}. About ${SessionEstimate.spokenMinutes(estimatedMinutes)} minutes."
            },
            coachBody = if (isFirst) {
                "Pick a weight you could manage two more reps with. Today sets the baseline — every number after this is built off it."
            } else {
                buildCoachBody(slots)
            },
            sessionCard = SessionCard(
                label = todayTemplate.label,
                summaryLine = if (isFirst) {
                    "$totalSets SETS · ~$estimatedMinutes MIN"
                } else {
                    "$totalSets SETS · $estimatedMinutes MIN"
                },
                totalSets = totalSets,
                estimatedMinutes = estimatedMinutes,
                exercises = exerciseRows,
                remainingCount = remaining,
                templateId = todayTemplate.id,
            ),
            volumeRows = volumeRows,
            volumeCoachLine = volumeCoachLine,
            primaryLabel = if (isFirst) "Start ${todayTemplate.label}" else "Let's go",
            isFirstSession = isFirst,
        )
    }

    fun startSession(onNavigate: (Long, String) -> Unit) {
        viewModelScope.launch {
            val templateId = pendingTemplateId ?: return@launch
            val mesoId = pendingMesoId
            val week = pendingMesoWeek

            val slots = programDao.getSlots(templateId)
            if (slots.isEmpty()) return@launch

            val meso = mesoId?.let { programDao.getMesocycleById(it) }
            val session = SessionEntity(
                templateId = templateId,
                mesocycleId = mesoId,
                weekNumber = week,
                // Was hardcoded to ACCUMULATION, so a deload week logged as a hard one.
                phase = if (meso != null && week != null) {
                    meso.phaseForWeek(week)
                } else {
                    BlockPhase.ACCUMULATION
                },
                startedAt = System.currentTimeMillis(),
            )
            val sessionId = sessionDao.insertSession(session)
            val firstExerciseId = slots.first().exerciseId
            onNavigate(sessionId, firstExerciseId)
        }
    }

    // ── Stall detection (COACHING.md §4, design 9c) ─────────────────

    /**
     * Three consecutive weeks without an estimated-1RM improvement on a primary lift.
     * Deliberately conservative: it needs [STALL_WEEKS_REQUIRED] flat weeks on top of
     * enough history to be sure, so a single bad session never triggers it.
     */
    private suspend fun detectStall(templateId: Long): TodayState.Stalled? {
        val slots = programDao.getSlots(templateId)
        val primary = slots.firstOrNull { it.progression != ProgressionRule.NONE } ?: return null
        val exercise = exerciseDao.getById(primary.exerciseId) ?: return null

        val weeks = weeklyBests(primary.exerciseId)
        if (weeks.size < E1RM_WEEKS) return null

        val recent = weeks.takeLast(E1RM_WEEKS)
        val best = recent.dropLast(STALL_WEEKS_REQUIRED).maxOfOrNull { it } ?: return null
        val flatTail = recent.takeLast(STALL_WEEKS_REQUIRED)
        if (flatTail.any { it > best + STALL_TOLERANCE_KG }) return null

        val current = primary.workingLoadKg ?: return null
        val equipment = equipmentFor(exercise)
        val deloaded = equipment
            ?.let { PlateMath.deload(current, DELOAD_FRACTION, it) }
            ?: (current * (1 - DELOAD_FRACTION))
        val restartAt = equipment
            ?.let { PlateMath.nextLoadUp(current, it) }
            ?: (current + 2.5)

        return TodayState.Stalled(
            eyebrow = "${exercise.name.uppercase()} · ${STALL_WEEKS_REQUIRED} FLAT WEEKS",
            headline = "${exercise.name} hasn't moved in $STALL_WEEKS_REQUIRED weeks.",
            coachBody = "Same ${formatKg(current)} kg, and the last rep got harder each " +
                "time. That's a stall, not a bad day.",
            e1rmWeeks = recent.mapIndexed { index, value ->
                E1rmWeek(
                    label = "W${index + 1}",
                    e1rm = value,
                    isFlat = index >= recent.size - STALL_WEEKS_REQUIRED,
                )
            },
            deloadEffects = listOf(
                DeloadEffect(exercise.name, "${formatKg(current)} → ${formatKg(deloaded)} kg"),
                DeloadEffect("Sets per lift", "${primary.targetSets} → ${(primary.targetSets / 2).coerceAtLeast(1)}"),
                DeloadEffect("Then week 1 restarts at", "${formatKg(restartAt)} kg", isOutcome = true),
            ),
            templateId = templateId,
        )
    }

    /** Best estimated 1RM per calendar week for one lift, oldest first. */
    private suspend fun weeklyBests(exerciseId: String): List<Double> {
        val sets = sessionDao.getAllSetLogs()
            .filter { it.exerciseId == exerciseId && !it.isWarmup && it.reps in 1..12 }
        if (sets.isEmpty()) return emptyList()

        return sets
            .groupBy {
                Instant.ofEpochMilli(it.completedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .with(DayOfWeek.MONDAY)
            }
            .toSortedMap()
            .map { (_, weekSets) -> weekSets.maxOf { it.estimated1Rm() } }
    }

    private suspend fun equipmentFor(exercise: ExerciseEntity): EquipmentEntity? =
        exercise.requiredEquipmentIds
            .firstNotNullOfOrNull { equipmentDao.getById(it) }
            ?.takeIf { it.isAvailable }

    /**
     * Accepts the deload: halves the sets, drops the loads, and moves the block into its
     * deload week. This used to relabel the week and change nothing at all, so the screen
     * promised a lighter week and then prescribed the same one.
     */
    fun takeDeload() {
        viewModelScope.launch {
            val meso = programDao.getActiveMesocycle() ?: return@launch
            mesocycleAdvancer.forceDeload(meso)
            stallDismissed = true
            load()
        }
    }

    /** "Push on" — the user's call, honoured without argument for this session. */
    fun pushOnThroughStall() {
        stallDismissed = true
        refresh()
    }

    private fun findTodayTemplate(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        today: LocalDate,
    ): SessionTemplateEntity? {
        // dayIndex maps to weekday: 0=MON, 1=TUE, ... 6=SUN
        val todayIndex = today.dayOfWeek.value - 1 // DayOfWeek.MONDAY = 1
        return scheduled(templates).find { it.dayIndex == todayIndex }
    }

    private fun findNextTemplate(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        today: LocalDate,
    ): SessionTemplateEntity? {
        val todayIndex = today.dayOfWeek.value - 1
        val scheduled = scheduled(templates)
        // Look for the next template after today
        return scheduled
            .filter { it.dayIndex > todayIndex }
            .minByOrNull { it.dayIndex }
            ?: scheduled.minByOrNull { it.dayIndex } // wrap to next week
    }

    /**
     * Ad-hoc body-map sessions are parked at a negative day index. Without this filter
     * `minByOrNull` picked one up and the rest-day card announced a one-off as the next
     * scheduled session.
     */
    private fun scheduled(templates: List<SessionTemplateEntity>): List<SessionTemplateEntity> =
        templates.filter { it.dayIndex >= 0 }

    /**
     * The muscles most in need of work this week, worst first.
     *
     * This used to take the first three rows the database returned, from a query with no
     * `ORDER BY` — so "arbitrary" was literal, and because nothing ever wrote a snapshot
     * they all read zero. Ranking by shortfall against MEV makes the footer answer the
     * only question it can usefully answer: what is this week still missing?
     */
    private suspend fun buildVolumeRows(mesoId: Long, week: Int): List<VolumeRow> {
        val landmarks = volumeDao.getAllLandmarks().ifEmpty { VolumeLandmarks.DEFAULTS }
        val credited = volumeDao.getSnapshots(mesoId, week).associateBy { it.muscle }

        return landmarks
            .map { landmark ->
                val done = credited[landmark.muscle]?.hardSets ?: 0.0
                val shortfall = landmark.mev - done
                VolumeRow(
                    label = landmark.muscle.displayName(),
                    current = done.roundToInt(),
                    target = credited[landmark.muscle]?.mav ?: landmark.mavHigh,
                ) to shortfall
            }
            .sortedByDescending { (_, shortfall) -> shortfall }
            .take(VOLUME_ROWS)
            .map { (row, _) -> row }
    }

    private fun buildVolumeCoachLine(rows: List<VolumeRow>): String {
        val lowest = rows.minByOrNull { it.current.toFloat() / it.target.coerceAtLeast(1) }
        return if (lowest != null && lowest.current < lowest.target) {
            "${lowest.label} is light this week — later sessions cover it."
        } else {
            "Volume is on track this week."
        }
    }

    private suspend fun buildCoachBody(slots: List<TemplateSlotEntity>): String {
        // Find a slot with a load change to highlight
        val slot = slots.firstOrNull { it.workingLoadKg != null }
        if (slot != null) {
            val load = slot.workingLoadKg ?: return "Same plan as last time — stay focused on form."
            val exercise = exerciseDao.getById(slot.exerciseId)
            val name = exercise?.name ?: "First exercise"
            return "${name} is at ${formatKg(load)} kg."
        }
        return "Same plan as last time — stay focused on form."
    }

    private fun formatKg(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) {
            kg.toLong().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", kg)
        }
    }

    /** "+2.5" beside a lift whose prescription moved since it was last trained. */
    private suspend fun loadDeltaFor(slot: TemplateSlotEntity): String? {
        val prescribed = slot.workingLoadKg ?: return null
        val lastLogged = sessionDao.getAllSetLogs()
            .filter { it.exerciseId == slot.exerciseId && !it.isWarmup }
            .maxByOrNull { it.completedAt }
            ?.loadKg
            ?: return null

        val delta = prescribed - lastLogged
        if (kotlin.math.abs(delta) < 0.01) return null
        return if (delta > 0) "+${formatKg(delta)}" else "−${formatKg(-delta)}"
    }

    private companion object {
        const val VISIBLE_SLOTS = 3
        const val VOLUME_ROWS = 3
        const val E1RM_WEEKS = 6
        const val STALL_WEEKS_REQUIRED = 3
        const val STALL_TOLERANCE_KG = 0.5
        const val DELOAD_FRACTION = 0.2
    }
}

private fun MuscleGroup.displayName(): String = when (this) {
    MuscleGroup.CHEST -> "Chest"
    MuscleGroup.UPPER_BACK -> "Upper Back"
    MuscleGroup.LATS -> "Lats"
    MuscleGroup.LOWER_BACK -> "Lower Back"
    MuscleGroup.FRONT_DELTS -> "Front Delts"
    MuscleGroup.SIDE_DELTS -> "Side Delts"
    MuscleGroup.REAR_DELTS -> "Rear Delts"
    MuscleGroup.BICEPS -> "Biceps"
    MuscleGroup.TRICEPS -> "Triceps"
    MuscleGroup.FOREARMS -> "Forearms"
    MuscleGroup.QUADS -> "Quads"
    MuscleGroup.HAMSTRINGS -> "Hamstrings"
    MuscleGroup.GLUTES -> "Glutes"
    MuscleGroup.ADDUCTORS -> "Adductors"
    MuscleGroup.CALVES -> "Calves"
    MuscleGroup.ABS -> "Abs"
    MuscleGroup.OBLIQUES -> "Obliques"
    MuscleGroup.TRAPS -> "Traps"
    MuscleGroup.NECK -> "Neck"
}
