package dev.redplate.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.EquipmentDao
import dev.redplate.data.ExerciseDao
import dev.redplate.data.MuscleGroup
import dev.redplate.data.ProgramDao
import dev.redplate.data.ProgressionEngine
import dev.redplate.data.ProgressionOutcome
import dev.redplate.data.ProgressionRule
import dev.redplate.data.SessionDao
import dev.redplate.data.SessionEntity
import dev.redplate.data.SessionOutcomeReader
import dev.redplate.data.SetLogEntity
import dev.redplate.data.TemplateSlotEntity
import dev.redplate.data.VolumeDao
import dev.redplate.data.VolumeLandmarks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Builds the post-session summary from what was actually logged.
 *
 * Everything here is derived, never stored: tonnage, PRs and weekly volume all fall out
 * of the set logs, so the summary cannot drift from the history it describes.
 */
@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val sessionDao: SessionDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val programDao: ProgramDao,
    private val volumeDao: VolumeDao,
    private val outcomeReader: SessionOutcomeReader,
) : ViewModel() {

    private val sessionId: Long = savedState.get<Long>(ARG_SESSION_ID) ?: 0L

    private val _state = MutableStateFlow<SessionSummaryState?>(null)
    val state: StateFlow<SessionSummaryState?> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val session = sessionDao.getSessionById(sessionId) ?: return
        val sets = sessionDao.getSetsForSession(sessionId)
        val working = sets.filter { !it.isWarmup }
        val slots = session.templateId?.let { programDao.getSlots(it) }.orEmpty()

        // Shared with Today's completed card, so the two screens cannot disagree about
        // how many sets were done or how long it took.
        val outcome = outcomeReader.read(session)
        val prs = outcome.prCount
        val volumeRows = buildVolumeRows(working)

        val templateLabel = session.templateId
            ?.let { programDao.getTemplateById(it)?.label }
            ?: "Freestyle"

        _state.value = SessionSummaryState(
            eyebrow = buildEyebrow(templateLabel, outcome.durationMinutes, outcome.workingSets),
            headline = buildHeadline(prs, outcome.workingSets),
            coachBody = buildCoachBody(working, prs),
            totalSets = outcome.workingSets,
            totalTonnage = formatTonnage(outcome.tonnageKg),
            prCount = prs,
            progressionChanges = applyProgression(session, working, slots),
            volumeRows = volumeRows,
            volumeCoachLine = buildVolumeCoachLine(volumeRows),
        )
    }

    /**
     * Weekly hard sets per muscle, secondaries at half credit per COACHING.md §3,
     * counting only sets logged at 0–3 RIR.
     */
    private suspend fun buildVolumeRows(working: List<SetLogEntity>): List<VolumeRow> {
        val credited = working.filter { it.countsTowardVolume }
        if (credited.isEmpty()) return emptyList()

        val perMuscle = mutableMapOf<MuscleGroup, Double>()
        for (set in credited) {
            val exercise = exerciseDao.getById(set.exerciseId) ?: continue
            perMuscle.merge(exercise.primaryMuscle, 1.0, Double::plus)
            exercise.secondaryMuscles.forEach { perMuscle.merge(it, 0.5, Double::plus) }
        }

        val landmarks = volumeDao.getAllLandmarks().associateBy { it.muscle }
        return perMuscle.entries
            .sortedByDescending { it.value }
            .take(4)
            .map { (muscle, sets) ->
                val target = landmarks[muscle]?.mavHigh ?: VolumeLandmarks.forMuscle(muscle).mavHigh
                VolumeRow(
                    label = muscle.name.lowercase().replace('_', ' ')
                        .replaceFirstChar { it.uppercaseChar() },
                    current = sets.roundToInt(),
                    target = target,
                )
            }
    }

    /**
     * What this session changes about the next one, per lift — decided, rendered, and
     * written back to the slot.
     *
     * This is the write half of the progression loop. `TemplateSlotEntity.workingLoadKg`
     * was read in five places and written in none, so the next session always restarted at
     * bar weight and stall detection could never fire. The decision itself lives in
     * [ProgressionEngine], which honours the slot's own [ProgressionRule] rather than
     * treating everything as double progression.
     *
     * Only a finished session writes: an open summary is a preview of a session still
     * being logged. The write is idempotent — the outcome is derived from the set logs and
     * the slot's prescription, never from the load already stored — so reopening the
     * summary re-decides to the same number instead of stacking another increase on top.
     */
    private suspend fun applyProgression(
        session: SessionEntity,
        working: List<SetLogEntity>,
        slots: List<TemplateSlotEntity>,
    ): List<ProgressionChange> =
        working.groupBy { it.exerciseId }.mapNotNull { (exerciseId, sets) ->
            val exercise = exerciseDao.getById(exerciseId) ?: return@mapNotNull null
            val slot = slots.firstOrNull { it.exerciseId == exerciseId }
            val equipment = exercise.requiredEquipmentIds
                .firstNotNullOfOrNull { equipmentDao.getById(it) }

            // A freestyle session has no slot to progress, but the user still gets told
            // what the numbers mean — judged against the default range.
            val basis = slot ?: freestyleBasis(exerciseId, sets.size)
            val outcome = ProgressionEngine.decide(basis.progression, sets, basis, equipment)

            if (slot != null && session.endedAt != null && slot.workingLoadKg != outcome.nextLoadKg) {
                programDao.updateSlot(slot.copy(workingLoadKg = outcome.nextLoadKg))
            }
            outcome.toChange(exercise.name)
        }

    /** A stand-in prescription so an unprogrammed lift can still be judged. Never persisted. */
    private fun freestyleBasis(exerciseId: String, setCount: Int) = TemplateSlotEntity(
        templateId = 0,
        exerciseId = exerciseId,
        orderIndex = 0,
        targetSets = setCount,
        repRangeLow = DEFAULT_REP_LOW,
        repRangeHigh = DEFAULT_REP_HIGH,
        targetRir = DEFAULT_TARGET_RIR,
        restSeconds = DEFAULT_REST_SECONDS,
        progression = ProgressionRule.DOUBLE_PROGRESSION,
    )

    private fun ProgressionOutcome.toChange(exerciseName: String): ProgressionChange = when (this) {
        is ProgressionOutcome.Up -> ProgressionChange(
            deltaLabel = "+${formatKg(nextLoadKg - fromKg)}",
            description = "$exerciseName — $reason, so it goes to ${formatKg(nextLoadKg)} kg",
            isUp = true,
        )

        is ProgressionOutcome.Down -> ProgressionChange(
            deltaLabel = "−${formatKg(fromKg - nextLoadKg)}",
            description = "$exerciseName — $reason, dropping to ${formatKg(nextLoadKg)} kg",
            isUp = false,
        )

        is ProgressionOutcome.Hold -> ProgressionChange(
            deltaLabel = "HOLD",
            description = "$exerciseName — $reason",
            isUp = false,
        )
    }

    private fun formatKg(kg: Double): String =
        if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(Locale.getDefault(), "%.1f", kg)

    private fun buildEyebrow(label: String, minutes: Int, sets: Int): String = when {
        minutes > 0 -> "${label.uppercase()} · $minutes MINUTES"
        else -> "${label.uppercase()} · $sets SETS"
    }

    private fun buildHeadline(prs: Int, sets: Int): String = when {
        sets == 0 -> "Nothing logged this time."
        prs > 0 -> "Logged, and $prs of those were bests."
        else -> "Logged. That's the work done."
    }

    private fun buildCoachBody(working: List<SetLogEntity>, prs: Int): String = when {
        working.isEmpty() ->
            "No sets went in, so nothing changes. Start the session again when you're ready."

        prs > 0 ->
            "New bests are the signal to keep the weight climbing. Next session starts from " +
                "these numbers, not the old ones."

        else ->
            "Consistency is what moves the numbers. This session is now part of what the " +
                "next prescription is built from."
    }

    private fun buildVolumeCoachLine(rows: List<VolumeRow>): String {
        if (rows.isEmpty()) return "Log a set at 3 reps in reserve or harder and it counts here."
        val lowest = rows.minByOrNull { it.current.toFloat() / it.target.coerceAtLeast(1) }
            ?: return "Volume is on track."
        return if (lowest.current < lowest.target) {
            "${lowest.label} is still short of target this week — later sessions cover it."
        } else {
            "Every muscle you trained today is at or above target for the week."
        }
    }

    private fun formatTonnage(kg: Double): String = when {
        kg >= 1000 -> "${String.format(Locale.getDefault(), "%.1f", kg / 1000)} t"
        else -> "${kg.roundToInt()} kg"
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"

        private const val DEFAULT_REP_LOW = 8
        private const val DEFAULT_REP_HIGH = 12
        private const val DEFAULT_TARGET_RIR = 2
        private const val DEFAULT_REST_SECONDS = 120
    }
}
