package dev.redplate.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.coach.CoachCopy
import dev.redplate.data.ExerciseDao
import dev.redplate.data.MuscleGroup
import dev.redplate.data.ProgramDao
import dev.redplate.data.LiftDecision
import dev.redplate.data.ProgressionApplier
import dev.redplate.data.ProgressionOutcome
import dev.redplate.data.SessionDao
import dev.redplate.data.SessionEntity
import dev.redplate.data.SessionOutcomeReader
import dev.redplate.data.SetLogEntity
import dev.redplate.data.isConditioning
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
    private val programDao: ProgramDao,
    private val volumeDao: VolumeDao,
    private val outcomeReader: SessionOutcomeReader,
    private val progressionApplier: ProgressionApplier,
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
            coachBody = buildCoachBody(working, prs) + tonnageFootnote(outcome.excludedFromTonnage),
            totalSets = outcome.workingSets,
            totalTonnage = formatTonnage(outcome.tonnageKg, outcome.excludedFromTonnage),
            prCount = prs,
            progressionChanges = describeProgression(session),
            volumeRows = volumeRows,
            volumeCoachLine = buildVolumeCoachLine(volumeRows),
            note = session.notes,
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
            if (exercise.isConditioning) continue
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
     * What this session changes about the next one, per lift — decided and rendered, but
     * never written here.
     *
     * The write used to live on this screen, so a session's progress was only saved if
     * its summary was opened, and reopening an old summary re-wrote slots the weekly
     * assessment had since moved. [ProgressionApplier] now writes once, when the session
     * ends; this reads the same pure decision back for display.
     */
    private suspend fun describeProgression(session: SessionEntity): List<ProgressionChange> =
        progressionApplier.preview(session).map { decision ->
            when (decision) {
                is LiftDecision.Strength -> decision.outcome.toChange(decision.exerciseName)
                is LiftDecision.Conditioning -> {
                    val o = decision.outcome
                    ProgressionChange(
                        deltaLabel = if (o.isIncrease) "+${o.nextMinutes - o.targetMinutes} MIN" else "HOLD",
                        description = "${decision.exerciseName} — ${o.reason}",
                        isUp = o.isIncrease,
                    )
                }
            }
        }

    /** Saves the note, or clears it when what is left is blank. */
    fun saveNote(text: String) {
        viewModelScope.launch {
            val session = sessionDao.getSessionById(sessionId) ?: return@launch
            val note = text.trim().ifEmpty { null }
            sessionDao.updateSession(session.copy(notes = note))
            _state.value = _state.value?.copy(note = note)
        }
    }

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
        sets == 0 -> CoachCopy.Summary.NOTHING_LOGGED_HEADLINE
        prs > 0 -> CoachCopy.Summary.bestsHeadline(prs)
        else -> CoachCopy.Summary.LOGGED_HEADLINE
    }

    private fun buildCoachBody(working: List<SetLogEntity>, prs: Int): String = when {
        working.isEmpty() ->
            CoachCopy.Summary.NOTHING_LOGGED_BODY

        prs > 0 ->
            CoachCopy.Summary.PR_BODY

        else ->
            CoachCopy.Summary.CONSISTENCY_BODY
    }

    private fun buildVolumeCoachLine(rows: List<VolumeRow>): String {
        if (rows.isEmpty()) return CoachCopy.Summary.NO_VOLUME_YET
        val lowest = rows.minByOrNull { it.current.toFloat() / it.target.coerceAtLeast(1) }
            ?: return CoachCopy.Today.VOLUME_ON_TRACK
        return if (lowest.current < lowest.target) {
            CoachCopy.Summary.volumeShort(lowest.label)
        } else {
            CoachCopy.Summary.VOLUME_MET
        }
    }

    private fun tonnageFootnote(excluded: Int): String = when (excluded) {
        0 -> ""
        1 -> "\n\n* One set was logged in resistance levels, which aren't kilograms, so it " +
            "isn't in the total lifted."
        else -> "\n\n* $excluded sets were logged in resistance levels, which aren't " +
            "kilograms, so they aren't in the total lifted."
    }

    /**
     * Tonnage counts only what was lifted in kilograms.
     *
     * A resistance level is ordinal — level 8 is harder than level 6, but it is not eight
     * kilograms — so those sets are left out rather than added to a barbell total. The
     * asterisk is there because a number that quietly excludes half a session is worse
     * than one that says it does.
     */
    private fun formatTonnage(kg: Double, excluded: Int): String {
        val figure = when {
            kg >= 1000 -> "${String.format(Locale.getDefault(), "%.1f", kg / 1000)} t"
            else -> "${kg.roundToInt()} kg"
        }
        return if (excluded > 0) "$figure*" else figure
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"

    }
}
