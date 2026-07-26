package dev.redplate.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.ExerciseDao
import dev.redplate.data.MuscleGroup
import dev.redplate.data.SessionDao
import dev.redplate.data.SetLogEntity
import dev.redplate.data.VolumeDao
import dev.redplate.data.VolumeLandmarks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
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
    private val volumeDao: VolumeDao,
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

        val tonnage = working.sumOf { it.loadKg * it.reps }
        val durationMinutes = session.endedAt
            ?.let { TimeUnit.MILLISECONDS.toMinutes(it - session.startedAt) }
            ?.toInt()
            ?: 0

        val prs = countPrs(working)
        val volumeRows = buildVolumeRows(working)

        _state.value = SessionSummaryState(
            eyebrow = buildEyebrow(durationMinutes, working.size),
            headline = buildHeadline(prs, working.size),
            coachBody = buildCoachBody(working, prs),
            totalSets = working.size,
            totalTonnage = formatTonnage(tonnage),
            prCount = prs,
            progressionChanges = emptyList(),
            volumeRows = volumeRows,
            volumeCoachLine = buildVolumeCoachLine(volumeRows),
        )
    }

    /**
     * A set is a PR if its estimated 1RM beats everything logged for that exercise
     * before this session. Compared against prior history only, so two good sets in
     * one session do not both get counted as beating each other.
     */
    private suspend fun countPrs(working: List<SetLogEntity>): Int {
        var count = 0
        for ((exerciseId, exerciseSets) in working.groupBy { it.exerciseId }) {
            val priorBest = sessionDao.getAllSetLogs()
                .filter {
                    it.exerciseId == exerciseId &&
                        !it.isWarmup &&
                        it.sessionId != sessionId &&
                        it.reps in 1..12
                }
                .maxOfOrNull { it.estimated1Rm() }
                ?: 0.0
            count += exerciseSets.count { it.reps in 1..12 && it.estimated1Rm() > priorBest + 1e-6 }
        }
        return count
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

    private fun buildEyebrow(minutes: Int, sets: Int): String = when {
        minutes > 0 -> "SESSION COMPLETE · $minutes MIN · $sets SETS"
        else -> "SESSION COMPLETE · $sets SETS"
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
        kg >= 1000 -> "${"%.1f".format(kg / 1000)} t"
        else -> "${kg.roundToInt()} kg"
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}
