package dev.redplate.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.ExerciseDao
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.SessionDao
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

/** A row of "Every PR": the lift, the set that did it, and when. */
data class PrEntry(
    val exerciseName: String,
    val setText: String,
    val dateLabel: String,
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
    /** Headline figure on the chart card. */
    val currentE1rmText: String = "—",
    /** One sentence reading the curve: "Up 14 kg in twelve weeks." */
    val trendLine: String = "",
    val bestSetText: String = "—",
    val bestSetWhen: String = "",
    val heaviestText: String = "—",
    val heaviestWhen: String = "",
    val sessionLog: List<SessionLogEntry> = emptyList(),
    /** "Every PR" swaps the body of the screen for this list. */
    val showingPrs: Boolean = false,
    val allPrs: List<PrEntry> = emptyList(),
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

    /** Swaps between the selected lift and the full PR list. */
    fun togglePrView() {
        val showing = !_state.value.showingPrs
        _state.value = _state.value.copy(showingPrs = showing)
        if (showing) viewModelScope.launch { loadAllPrs() }
    }

    /**
     * Every set that beat the best estimated 1RM before it, newest first. Computed in
     * chronological order per lift so a session with three climbing sets records each
     * genuine improvement rather than only the last.
     */
    private suspend fun loadAllPrs() {
        val names = exerciseDao.getAll().associate { it.id to it.name }
        val prs = mutableListOf<Pair<Long, PrEntry>>()

        sessionDao.getAllSetLogs()
            .filter { !it.isWarmup && it.reps in 1..12 }
            .groupBy { it.exerciseId }
            .forEach { (exerciseId, sets) ->
                var best = 0.0
                for (set in sets.sortedBy { it.completedAt }) {
                    val e1rm = set.estimated1Rm()
                    if (e1rm > best + 1e-6) {
                        best = e1rm
                        prs += set.completedAt to PrEntry(
                            exerciseName = names[exerciseId] ?: exerciseId,
                            setText = "${formatKg(set.loadKg)} × ${set.reps}",
                            dateLabel = dayMonth(set.completedAt),
                        )
                    }
                }
            }

        _state.value = _state.value.copy(
            allPrs = prs.sortedByDescending { it.first }.map { it.second },
        )
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
        val prSessionIds = run {
            var best = 0.0
            val ids = mutableSetOf<Long>()
            for (set in allSets.filter { it.reps in 1..12 }.sortedBy { it.completedAt }) {
                val e1rm = set.estimated1Rm()
                if (e1rm > best + 1e-6) {
                    best = e1rm
                    ids += set.sessionId
                }
            }
            ids
        }

        val sessionLog = allSets
            .groupBy { it.sessionId }
            .entries
            .sortedByDescending { it.value.first().completedAt }
            .take(20)
            .map { (_, sets) ->
                val dateLabel = dayMonth(sets.first().completedAt)
                // "102.5 × 10 · 9 · 9" — the load once, then the reps, as the design
                // writes it. Falls back to per-set loads when the weight changed.
                val loads = sets.map { it.loadKg }.distinct()
                val summary = if (loads.size == 1) {
                    "${formatKg(loads.first())} × ${sets.joinToString(" · ") { it.reps.toString() }}"
                } else {
                    sets.joinToString(" · ") { "${formatKg(it.loadKg)}×${it.reps}" }
                }
                SessionLogEntry(dateLabel, summary, isPr = sets.first().sessionId in prSessionIds)
            }

        _state.value = _state.value.copy(
            e1rmPoints = points,
            currentE1rmText = points.lastOrNull()?.let { formatKg(it.e1rm) } ?: "—",
            trendLine = buildTrendLine(points),
            bestSetText = bestSetText,
            bestSetWhen = bestSet?.let { relativeDay(it.completedAt) } ?: "",
            heaviestText = heaviestText,
            heaviestWhen = heaviest?.let { relativeDay(it.completedAt) } ?: "",
            sessionLog = sessionLog,
        )
    }

    /** Reads the curve in a sentence, including whether it has gone flat. */
    private fun buildTrendLine(points: List<E1rmPoint>): String {
        if (points.size < 2) return "One session in. The curve needs a few more."
        val change = points.last().e1rm - points.first().e1rm
        val span = when (_state.value.timeRange) {
            TimeRange.TWELVE_WEEKS -> "twelve weeks"
            TimeRange.SIX_MONTHS -> "six months"
            TimeRange.ALL -> "all your training"
        }
        val recentPeak = points.takeLast(3).maxOf { it.e1rm }
        val earlierPeak = points.dropLast(3).maxOfOrNull { it.e1rm } ?: 0.0
        val stalled = points.size >= 6 && recentPeak <= earlierPeak + 0.5

        return when {
            change > 0.5 && stalled ->
                "Up ${formatKg(change)} kg across $span, but flat for the last three."
            change > 0.5 -> "Up ${formatKg(change)} kg across $span. The ring is your best set."
            change < -0.5 -> "Down ${formatKg(-change)} kg across $span — worth a look at recovery."
            else -> "Level across $span. The ring is your best set."
        }
    }

    private fun relativeDay(epochMillis: Long): String {
        val date = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        return when (val days = java.time.temporal.ChronoUnit.DAYS.between(date, java.time.LocalDate.now())) {
            0L -> "TODAY"
            1L -> "YESTERDAY"
            in 2L..13L -> "$days DAYS AGO"
            in 14L..60L -> "${days / 7} WEEKS AGO"
            else -> dayMonth(epochMillis)
        }
    }

    private fun dayMonth(epochMillis: Long): String {
        val date = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        return "${date.dayOfMonth} ${date.month.name.take(3)}"
    }

    private fun formatKg(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) "${kg.toLong()}" else "%.1f".format(kg)
    }
}
