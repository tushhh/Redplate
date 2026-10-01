package dev.redplate.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.ExerciseDao
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.FinisherProgression
import dev.redplate.data.ProfileDao
import dev.redplate.data.ProgramGenerator
import dev.redplate.data.WorkoutRepository
import dev.redplate.data.isConditioning
import dev.redplate.data.MuscleGroup
import dev.redplate.data.ProgramDao
import dev.redplate.data.SessionDao
import dev.redplate.data.TrainingClock
import dev.redplate.data.VolumeCredit
import dev.redplate.data.TemplateSlotEntity
import dev.redplate.data.VolumeLandmarks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    /** The exercise picker, when it is open — for adding a lift or swapping one. */
    val picker: PickerState? = null,
)

/** One exercise the picker can offer. */
data class PickOption(
    val exerciseId: String,
    val name: String,
    /** "CHEST PRESS MACHINE · CHEST" */
    val detail: String,
)

data class PickerState(
    /** Null when adding; the slot being replaced when swapping. */
    val swapSlotId: Long?,
    val title: String,
    val query: String = "",
    val options: List<PickOption> = emptyList(),
) {
    val visible: List<PickOption>
        get() = if (query.isBlank()) {
            options
        } else {
            options.filter {
                it.name.contains(query.trim(), ignoreCase = true) ||
                    it.detail.contains(query.trim(), ignoreCase = true)
            }
        }
}

@HiltViewModel
class ProgramBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programDao: ProgramDao,
    private val exerciseDao: ExerciseDao,
    private val sessionDao: SessionDao,
    private val trainingClock: TrainingClock,
    private val profileDao: ProfileDao,
    private val programGenerator: ProgramGenerator,
    private val repo: WorkoutRepository,
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
                    picker = _state.value.picker,
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

        // This week's training only, queried by range rather than by loading every set
        // ever logged and filtering it in Kotlin.
        val dayStartHour = trainingClock.dayStartHour()
        val weekStart = trainingClock.weekStart(trainingClock.todayDate())
        val bounds = trainingClock.weekBounds(weekStart, dayStartHour)
        val logged = VolumeCredit.perMuscle(
            sessionDao.getSetLogsBetween(bounds.first, bounds.last + 1),
            exercises,
        ).toMutableMap()

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

    /** Sets on a lift; minutes on a finisher, which is always one block of time. */
    fun incrementSets(slotId: Long) {
        viewModelScope.launch {
            val slot = programDao.getSlotById(slotId) ?: return@launch
            if (slot.isCardioFinisher) {
                val minutes = (slot.repRangeLow + 1).coerceAtMost(FinisherProgression.MAX_MINUTES)
                programDao.updateSlot(slot.copy(repRangeLow = minutes, repRangeHigh = minutes))
            } else if (slot.targetSets < MAX_SETS) {
                programDao.updateSlot(slot.copy(targetSets = slot.targetSets + 1))
            }
        }
    }

    fun decrementSets(slotId: Long) {
        viewModelScope.launch {
            val slot = programDao.getSlotById(slotId) ?: return@launch
            if (slot.isCardioFinisher) {
                val minutes = (slot.repRangeLow - 1).coerceAtLeast(FinisherProgression.MIN_MINUTES)
                programDao.updateSlot(slot.copy(repRangeLow = minutes, repRangeHigh = minutes))
            } else if (slot.targetSets > 1) {
                programDao.updateSlot(slot.copy(targetSets = slot.targetSets - 1))
            }
        }
    }

    /** Removes the slot being swapped, closing the picker. Order indices stay dense. */
    fun removeSwappedSlot() {
        val slotId = _state.value.picker?.swapSlotId ?: return
        _state.value = _state.value.copy(picker = null)
        viewModelScope.launch {
            val slot = programDao.getSlotById(slotId) ?: return@launch
            programDao.deleteSlot(slot)
            programDao.getSlots(templateId).forEachIndexed { index, s ->
                if (s.orderIndex != index) programDao.updateSlot(s.copy(orderIndex = index))
            }
        }
    }

    // ── Adding and swapping (plan §04: "swap exercises, change set counts") ──

    /** Every lift the gym can support that is not already in this session. */
    fun openAdd() {
        viewModelScope.launch {
            val inSession = _state.value.slots.mapTo(mutableSetOf()) { it.slot.exerciseId }
            val strength = MuscleGroup.entries.flatMap { repo.availableExercisesForMuscle(it) }
            val options = (strength + repo.availableConditioning())
                .filter { it.id !in inSession }
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.isConditioning }, { it.primaryMuscle.ordinal }, { it.name }))
            _state.value = _state.value.copy(
                picker = PickerState(
                    swapSlotId = null,
                    title = "Add to ${_state.value.sessionName}",
                    options = options.map { it.toOption() },
                ),
            )
        }
    }

    /**
     * What could replace a slot: lifts for the same muscle, or other conditioning for a
     * finisher. The slot keeps its sets and progression rule (see
     * [ProgramGenerator.replaceSlotExercise]).
     */
    fun openSwap(slotId: Long) {
        viewModelScope.launch {
            val slot = programDao.getSlotById(slotId) ?: return@launch
            val current = exerciseDao.getById(slot.exerciseId) ?: return@launch
            val inSession = _state.value.slots.mapTo(mutableSetOf()) { it.slot.exerciseId }
            val candidates = if (slot.isCardioFinisher || current.isConditioning) {
                repo.availableConditioning()
            } else {
                repo.availableExercisesForMuscle(current.primaryMuscle)
            }
            _state.value = _state.value.copy(
                picker = PickerState(
                    swapSlotId = slotId,
                    title = "Swap ${current.name}",
                    options = candidates
                        .filter { it.id !in inSession }
                        .sortedWith(compareByDescending<ExerciseEntity> { it.isCompound == current.isCompound }.thenBy { it.name })
                        .map { it.toOption() },
                ),
            )
        }
    }

    fun setPickerQuery(query: String) {
        val picker = _state.value.picker ?: return
        _state.value = _state.value.copy(picker = picker.copy(query = query))
    }

    fun closePicker() {
        _state.value = _state.value.copy(picker = null)
    }

    fun pick(exerciseId: String) {
        val picker = _state.value.picker ?: return
        _state.value = _state.value.copy(picker = null)
        viewModelScope.launch {
            val profile = profileDao.get() ?: return@launch
            val swap = picker.swapSlotId
            if (swap != null) {
                programGenerator.replaceSlotExercise(swap, exerciseId, profile)
            } else {
                programGenerator.appendSlot(templateId, exerciseId, profile)
            }
        }
    }

    private suspend fun ExerciseEntity.toOption(): PickOption {
        val station = repo.describeStation(this)?.uppercase()
        val muscle = if (isConditioning) "CARDIO" else primaryMuscle.name.replace('_', ' ')
        return PickOption(
            exerciseId = id,
            name = name,
            detail = listOfNotNull(station, muscle).joinToString(" · "),
        )
    }

    private companion object {
        const val MAX_SETS = 10
        const val MAX_EFFECT_ROWS = 3
    }
}
