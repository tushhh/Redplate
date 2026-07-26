package dev.redplate.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.ExerciseDao
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.SessionDao
import dev.redplate.data.SetLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TimeRange(val label: String) {
    TWELVE_WEEKS("12 WEEKS"),
    SIX_MONTHS("6 MONTHS"),
    ALL("ALL"),
}

data class E1rmPoint(
    val dateMillis: Long,
    val e1rm: Double,
    val isPr: Boolean,
)

data class SessionLogEntry(
    val dateLabel: String,
    val setsText: String,  // "3×8 @ 80kg"
    val isPr: Boolean = false,
)

data class HistoryState(
    val exercises: List<ExerciseEntity> = emptyList(),
    val selectedExercise: ExerciseEntity? = null,
    val timeRange: TimeRange = TimeRange.TWELVE_WEEKS,
    val e1rmPoints: List<E1rmPoint> = emptyList(),
    val bestSetText: String = "—",
    val heaviestText: String = "—",
    val sessionLog: List<SessionLogEntry> = emptyList(),
    val hasAnyHistory: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val sessionDao: SessionDao,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state

    init {
        viewModelScope.launch {
            val allExercises = exerciseDao.getAll().filter { !it.isExcluded }
            // Start with the first exercise that has logged sets
            val exercisesWithHistory = allExercises.filter { ex ->
                val pr = sessionDao.getPrSet(ex.id)
                pr != null
            }
            val displayList = exercisesWithHistory.ifEmpty { allExercises }
            val selected = exercisesWithHistory.firstOrNull() ?: allExercises.firstOrNull()
            _state.value = HistoryState(
                exercises = displayList,
                selectedExercise = selected,
                hasAnyHistory = exercisesWithHistory.isNotEmpty(),
                isLoading = false,
            )
            if (selected != null && exercisesWithHistory.isNotEmpty()) {
                loadExerciseHistory(selected.id)
            }
        }
    }

    fun selectExercise(exerciseId: String) {
        viewModelScope.launch {
            val exercise = exerciseDao.getById(exerciseId) ?: return@launch
            _state.value = _state.value.copy(selectedExercise = exercise)
            loadExerciseHistory(exerciseId)
        }
    }

    fun setTimeRange(range: TimeRange) {
        _state.value = _state.value.copy(timeRange = range)
        val exerciseId = _state.value.selectedExercise?.id ?: return
        viewModelScope.launch { loadExerciseHistory(exerciseId) }
    }

    private suspend fun loadExerciseHistory(exerciseId: String) {
        val range = _state.value.timeRange
        val cutoff = when (range) {
            TimeRange.TWELVE_WEEKS -> System.currentTimeMillis() - 12L * 7 * 24 * 3600 * 1000
            TimeRange.SIX_MONTHS -> System.currentTimeMillis() - 180L * 24 * 3600 * 1000
            TimeRange.ALL -> 0L
        }

        // Get all working sets for this exercise
        val allSets = sessionDao.getAllSetLogs()
            .filter { it.exerciseId == exerciseId && !it.isWarmup && it.completedAt >= cutoff }
            .sortedBy { it.completedAt }

        // Build e1RM points — group by session (day)
        var maxE1rm = 0.0
        val points = allSets
            .filter { it.reps in 1..12 }
            .map { set ->
                val e1rm = set.estimated1Rm()
                val isPr = e1rm > maxE1rm
                if (isPr) maxE1rm = e1rm
                E1rmPoint(set.completedAt, e1rm, isPr)
            }

        // Best set (highest e1RM)
        val bestSet = allSets
            .filter { it.reps in 1..12 }
            .maxByOrNull { it.estimated1Rm() }
        val bestSetText = if (bestSet != null) {
            "${formatKg(bestSet.loadKg)} × ${bestSet.reps}"
        } else "—"

        // Heaviest single
        val heaviest = allSets.maxByOrNull { it.loadKg }
        val heaviestText = if (heaviest != null) {
            "${formatKg(heaviest.loadKg)} kg"
        } else "—"

        // Session log entries
        val sessionLog = allSets
            .groupBy { it.sessionId }
            .entries
            .sortedByDescending { it.value.first().completedAt }
            .take(20)
            .map { (_, sets) ->
                val date = java.time.Instant.ofEpochMilli(sets.first().completedAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                val dateLabel = "${date.dayOfMonth} ${date.month.name.take(3)}"
                val summary = sets.joinToString(", ") { "${formatKg(it.loadKg)}×${it.reps}" }
                SessionLogEntry(dateLabel, summary)
            }

        _state.value = _state.value.copy(
            e1rmPoints = points,
            bestSetText = bestSetText,
            heaviestText = heaviestText,
            sessionLog = sessionLog,
        )
    }

    private fun formatKg(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) "${kg.toLong()}" else "%.1f".format(kg)
    }
}
