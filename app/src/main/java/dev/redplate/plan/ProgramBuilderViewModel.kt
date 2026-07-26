package dev.redplate.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.ExerciseDao
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.ProgramDao
import dev.redplate.data.TemplateSlotEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SlotRow(
    val slot: TemplateSlotEntity,
    val exerciseName: String,
    val isEditing: Boolean = false,
)

data class VolumeDelta(
    val muscleName: String,
    val delta: Int,  // +2 or -1, etc.
)

data class ProgramBuilderState(
    val sessionName: String = "",
    val templateId: Long = 0,
    val slots: List<SlotRow> = emptyList(),
    val volumeDeltas: List<VolumeDelta> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ProgramBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programDao: ProgramDao,
    private val exerciseDao: ExerciseDao,
) : ViewModel() {

    private val templateId: Long = savedStateHandle["templateId"] ?: 0L

    private val _state = MutableStateFlow(ProgramBuilderState())
    val state: StateFlow<ProgramBuilderState> = _state

    init {
        viewModelScope.launch {
            val template = programDao.getTemplateById(templateId)
            if (template == null) {
                _state.value = ProgramBuilderState(isLoading = false)
                return@launch
            }

            programDao.observeSlots(templateId).collect { slots ->
                val rows = slots.map { slot ->
                    val exercise = exerciseDao.getById(slot.exerciseId)
                    SlotRow(
                        slot = slot,
                        exerciseName = exercise?.name ?: slot.exerciseId,
                    )
                }
                _state.value = ProgramBuilderState(
                    sessionName = template.label,
                    templateId = templateId,
                    slots = rows,
                    isLoading = false,
                )
            }
        }
    }

    fun incrementSets(slotId: Long) {
        viewModelScope.launch {
            val slot = programDao.getSlotById(slotId) ?: return@launch
            programDao.updateSlot(slot.copy(targetSets = slot.targetSets + 1))
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
}
