package dev.redplate.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.ExerciseDao
import dev.redplate.data.MuscleGroup
import dev.redplate.data.ProgramDao
import dev.redplate.data.SessionDao
import dev.redplate.data.TemplateSlotEntity
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

data class SlotRow(
    val slot: TemplateSlotEntity,
    val exerciseName: String,
    /** Non-zero while this row has been changed but not yet saved (design 6b). */
    val setsDelta: Int = 0,
)

/** A muscle's week, plus what this unsaved edit would add to it. */
data class MuscleEffect(
    val muscleName: String,
    val current: Int,
    val added: Int,
    val target: Int,
)

data class ProgramBuilderState(
    val sessionName: String = "",
    val templateId: Long = 0,
    val slots: List<SlotRow> = emptyList(),
    val volumeEffect: List<MuscleEffect> = emptyList(),
    val effectSummary: String = "",
    val hasChanges: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ProgramBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programDao: ProgramDao,
    private val exerciseDao: ExerciseDao,
    private val sessionDao: SessionDao,
) : ViewModel() {

    private val templateId: Long = savedStateHandle["templateId"] ?: 0L

    private val _state = MutableStateFlow(ProgramBuilderState())
    val state: StateFlow<ProgramBuilderState> = _state.asStateFlow()

    /** Set counts as they were when the screen opened, so a delta can be shown. */
    private var baseline: Map<Long, Int> = emptyMap()

    init {
        viewModelScope.launch {
            val template = programDao.getTemplateById(templateId)
            if (template == null) {
                _state.value = ProgramBuilderState(isLoading = false)
                return@launch
            }

            programDao.observeSlots(templateId).collect { slots ->
                if (baseline.isEmpty()) {
                    baseline = slots.associate { it.id to it.targetSets }
                }
                val rows = slots.map { slot ->
                    SlotRow(
                        slot = slot,
                        exerciseName = exerciseDao.getById(slot.exerciseId)?.name ?: slot.exerciseId,
                        setsDelta = slot.targetSets - (baseline[slot.id] ?: slot.targetSets),
                    )
                }
                val effect = buildEffect(rows)
                _state.value = ProgramBuilderState(
                    sessionName = template.label,
                    templateId = templateId,
                    slots = rows,
                    volumeEffect = effect,
                    effectSummary = summarise(effect),
                    hasChanges = rows.any { it.setsDelta != 0 },
                    isLoading = false,
                )
            }
        }
    }

    /**
     * What this edit does to the week, per muscle: sets already logged this week, plus
     * what the changed slots would add, against the cap. Secondaries count half, matching
     * every other volume readout in the app.
     */
    private suspend fun buildEffect(rows: List<SlotRow>): List<MuscleEffect> {
        val changed = rows.filter { it.setsDelta != 0 }
        if (changed.isEmpty()) return emptyList()

        val exercises = exerciseDao.getAll().associateBy { it.id }
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)

        val logged = mutableMapOf<MuscleGroup, Double>()
        sessionDao.getAllSetLogs()
            .filter {
                it.countsTowardVolume &&
                    Instant.ofEpochMilli(it.completedAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate() >= weekStart
            }
            .forEach { set ->
                val exercise = exercises[set.exerciseId] ?: return@forEach
                logged.merge(exercise.primaryMuscle, 1.0, Double::plus)
                exercise.secondaryMuscles.forEach { logged.merge(it, 0.5, Double::plus) }
            }

        val added = mutableMapOf<MuscleGroup, Double>()
        changed.forEach { row ->
            val exercise = exercises[row.slot.exerciseId] ?: return@forEach
            added.merge(exercise.primaryMuscle, row.setsDelta.toDouble(), Double::plus)
            exercise.secondaryMuscles.forEach {
                added.merge(it, row.setsDelta * 0.5, Double::plus)
            }
        }

        return added.keys
            .sortedByDescending { added[it] ?: 0.0 }
            .take(MAX_EFFECT_ROWS)
            .map { muscle ->
                MuscleEffect(
                    muscleName = muscle.name.lowercase().replace('_', ' ')
                        .replaceFirstChar { it.uppercaseChar() },
                    current = (logged[muscle] ?: 0.0).roundToInt(),
                    added = (added[muscle] ?: 0.0).roundToInt(),
                    target = VolumeLandmarks.forMuscle(muscle).mrv,
                )
            }
    }

    /** Says whether the edit stays inside the cap, which is the question being asked. */
    private fun summarise(effect: List<MuscleEffect>): String {
        if (effect.isEmpty()) return ""
        val lead = effect.first()
        val total = lead.current + lead.added
        val direction = if (lead.added >= 0) "more" else "fewer"
        val magnitude = kotlin.math.abs(lead.added)

        return if (total > lead.target) {
            "$magnitude $direction ${lead.muscleName.lowercase()} set${plural(magnitude)} a week — " +
                "that puts you over the cap at $total of ${lead.target}."
        } else {
            "$magnitude $direction ${lead.muscleName.lowercase()} set${plural(magnitude)} a week — " +
                "still inside your cap at $total of ${lead.target}."
        }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    /**
     * Edits are written straight to the slot as they are made, so the primary action is
     * an acknowledgement rather than a save. Clearing the baseline is what makes the
     * "just changed" highlight and the effect panel settle.
     */
    fun commit() {
        baseline = _state.value.slots.associate { it.slot.id to it.slot.targetSets }
        _state.value = _state.value.copy(
            slots = _state.value.slots.map { it.copy(setsDelta = 0) },
            volumeEffect = emptyList(),
            effectSummary = "",
            hasChanges = false,
        )
    }

    fun incrementSets(slotId: Long) {
        viewModelScope.launch {
            val slot = programDao.getSlotById(slotId) ?: return@launch
            if (slot.targetSets < MAX_SETS) {
                programDao.updateSlot(slot.copy(targetSets = slot.targetSets + 1))
            }
        }
    }

    fun decrementSets(slotId: Long) {
        viewModelScope.launch {
            val slot = programDao.getSlotById(slotId) ?: return@launch
            if (slot.targetSets > 1) {
                programDao.updateSlot(slot.copy(targetSets = slot.targetSets - 1))
            }
        }
    }

    fun deleteSlot(slotId: Long) {
        viewModelScope.launch {
            val slot = programDao.getSlotById(slotId) ?: return@launch
            programDao.deleteSlot(slot)
        }
    }

    private companion object {
        const val MAX_SETS = 10
        const val MAX_EFFECT_ROWS = 3
    }
}
