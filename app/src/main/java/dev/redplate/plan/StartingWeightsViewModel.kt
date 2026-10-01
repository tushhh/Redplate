package dev.redplate.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.LoadUnit
import dev.redplate.data.LoadingScheme
import dev.redplate.data.ProgramDao
import dev.redplate.data.ProgramGenerator
import dev.redplate.data.WorkoutRepository
import dev.redplate.data.loadUnit
import dev.redplate.workout.formatLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StartingWeightRow(
    val exerciseId: String,
    val name: String,
    /** "Chest Press Machine", "Half Rack · Barbell" — where the number is read. */
    val station: String?,
    val loadLabel: String?,
    /** "KG", "KG EACH", "LEVEL" — what the number on that equipment means. */
    val unitLabel: String,
    val wholeNumbersOnly: Boolean,
    /** Which sessions it appears in: "UPPER A · UPPER B". */
    val sessions: String,
)

data class StartingWeightsState(
    val isLoading: Boolean = true,
    val rows: List<StartingWeightRow> = emptyList(),
    val editing: StartingWeightRow? = null,
    val entry: String = "",
) {
    val setCount: Int get() = rows.count { it.loadLabel != null }
    val canCommit: Boolean get() = entry.toDoubleOrNull()?.let { it >= 0.0 } == true
}

/**
 * "What do you already lift?" — every lift in the block, with the weight it opens at.
 *
 * Without this, the first session of every lift opened at the empty bar or the lightest
 * pin, and the user typed their real working weight over it set by set. Answering it once
 * here writes the weight to every session that lift appears in.
 */
@HiltViewModel
class StartingWeightsViewModel @Inject constructor(
    private val programDao: ProgramDao,
    private val programGenerator: ProgramGenerator,
    private val repo: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StartingWeightsState())
    val state: StateFlow<StartingWeightsState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val meso = programDao.getActiveMesocycle()
        if (meso == null) {
            _state.value = StartingWeightsState(isLoading = false)
            return
        }
        val templates = programDao.getAllTemplates()
            .filter { it.mesocycleId == meso.id && it.dayIndex >= 0 }
            .sortedBy { it.dayIndex }

        // Running order across the week, each lift once, with every session it is in.
        val seen = linkedMapOf<String, MutableList<String>>()
        val loads = mutableMapOf<String, Double?>()
        for (template in templates) {
            for (slot in programDao.getSlots(template.id)) {
                if (slot.isCardioFinisher) continue
                seen.getOrPut(slot.exerciseId) { mutableListOf() } += template.label.uppercase()
                loads[slot.exerciseId] = loads[slot.exerciseId] ?: slot.workingLoadKg
            }
        }

        val rows = seen.mapNotNull { (exerciseId, sessions) ->
            val exercise = repo.getExercise(exerciseId) ?: return@mapNotNull null
            val equipment = repo.getPrimaryEquipment(exercise)
            val unit = equipment?.loadUnit ?: LoadUnit.KILOGRAMS
            StartingWeightRow(
                exerciseId = exerciseId,
                name = exercise.name,
                station = repo.describeStation(exercise),
                loadLabel = loads[exerciseId]?.let(::formatLoad),
                unitLabel = when {
                    equipment?.perLimb == true -> "${unit.label} EACH"
                    equipment?.isAssistance == true -> "${unit.label} ASSIST"
                    else -> unit.label
                },
                wholeNumbersOnly = equipment?.loadingScheme == LoadingScheme.RESISTANCE_LEVEL,
                sessions = sessions.distinct().joinToString(" · "),
            )
        }
        _state.value = StartingWeightsState(isLoading = false, rows = rows)
    }

    fun edit(row: StartingWeightRow) = _state.update { it.copy(editing = row, entry = "") }

    fun cancel() = _state.update { it.copy(editing = null, entry = "") }

    fun appendDigit(key: Char) = _state.update { s ->
        val row = s.editing ?: return@update s
        val next = when {
            key.isDigit() -> s.entry + key
            key == '.' && !row.wholeNumbersOnly && !s.entry.contains('.') ->
                if (s.entry.isEmpty()) "0." else "${s.entry}."
            else -> s.entry
        }
        if (next.length > MAX_DIGITS) s else s.copy(entry = next)
    }

    fun backspace() = _state.update { it.copy(entry = it.entry.dropLast(1)) }

    /** Stored exactly as typed, like every other load the user reports. */
    fun commit() {
        val s = _state.value
        val row = s.editing ?: return
        val value = s.entry.toDoubleOrNull() ?: return
        _state.update { it.copy(editing = null, entry = "") }
        viewModelScope.launch {
            programGenerator.setStartingLoad(row.exerciseId, value)
            load()
        }
    }

    private companion object {
        const val MAX_DIGITS = 6
    }
}
