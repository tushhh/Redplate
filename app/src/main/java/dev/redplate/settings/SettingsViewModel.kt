package dev.redplate.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.EquipmentDao
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.ExerciseDao
import dev.redplate.data.Goal
import dev.redplate.data.LoadingScheme
import dev.redplate.data.ProfileDao
import dev.redplate.data.ProfileEntity
import dev.redplate.data.ProgramDao
import dev.redplate.data.SessionDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * The You tab — design 9a. Settings that are all consequences, not preferences.
 *
 * Every row carries the sentence describing what it changes about the training, and the
 * two that can silently ruin a session — plates and units — sit at the top with their
 * current value visible without tapping in.
 */
data class SettingsState(
    val sinceLabel: String = "YOU",
    /** "Four months in." — what the header says, derived from what has been logged. */
    val headline: String = "Your setup.",
    val sessionCountLabel: String = "0",
    val prCountLabel: String = "0",
    val bodyweightLabel: String = "—",
    /** "Build muscle · 4 days · 60 min" — the plan, readable without tapping in. */
    val planSummary: String = "—",
    val plateSummary: String = "—",
    val useMetric: Boolean = true,
    val equipmentSummary: String = "—",
    val restSummary: String = "—",
    val deloadPromptsEnabled: Boolean = true,
    val backupSummary: String = "Export & restore",
    val isLoading: Boolean = true,
)

data class EquipmentListState(
    val equipment: List<EquipmentEntity> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val equipmentDao: EquipmentDao,
    private val exerciseDao: ExerciseDao,
    private val sessionDao: SessionDao,
    private val programDao: ProgramDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _equipmentState = MutableStateFlow(EquipmentListState())
    val equipmentState: StateFlow<EquipmentListState> = _equipmentState.asStateFlow()

    init {
        refresh()
        loadEquipment()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = profileDao.get() ?: return@launch
            val equipment = equipmentDao.getAll()
            val available = equipment.filter { it.isAvailable }
            // Counted and queried, not loaded: this screen should not scale with history.
            val sessionCount = sessionDao.countSessions()
            val firstSessionAt = sessionDao.firstSessionStartedAt()
            val meso = programDao.getActiveMesocycle()
            val prCount = countPrsThisBlock(meso?.startedAt)

            _state.value = SettingsState(
                sinceLabel = firstSessionAt
                    ?.let { "TRAINING SINCE ${monthYear(it)}" }
                    ?: "YOUR SETUP",
                headline = buildHeadline(sessionCount),
                sessionCountLabel = sessionCount.toString(),
                prCountLabel = if (meso == null) "—" else prCount.toString(),
                bodyweightLabel = "${formatKg(profile.bodyweightKg)} kg",
                planSummary = describePlan(profile),
                plateSummary = describePlates(available),
                useMetric = profile.useMetric,
                equipmentSummary = "${available.size} item${plural(available.size)}",
                restSummary = "Set by your plan",
                deloadPromptsEnabled = _state.value.deloadPromptsEnabled,
                backupSummary = if (sessionCount == 0) {
                    "Nothing logged yet"
                } else {
                    "$sessionCount session${plural(sessionCount)} on this phone"
                },
                isLoading = false,
            )
        }
    }

    private fun loadEquipment() {
        viewModelScope.launch {
            equipmentDao.observeAll().collect { equipment ->
                _equipmentState.value = EquipmentListState(equipment, isLoading = false)
            }
        }
    }

    /** Says something true about how much history there is, rather than a stock greeting. */
    private fun buildHeadline(sessions: Int): String = when {
        sessions == 0 -> "Nothing logged yet."
        sessions == 1 -> "One session in."
        sessions < 10 -> "$sessions sessions in."
        else -> "$sessions sessions and counting."
    }

    private fun describePlan(profile: ProfileEntity): String {
        val goal = when (profile.goal) {
            Goal.STRENGTH -> "Get stronger"
            Goal.HYPERTROPHY -> "Build muscle"
            Goal.LEAN -> "Lean"
            Goal.GENERAL -> "Generally fitter"
        }
        return "$goal · ${profile.daysPerWeek} days · ${profile.sessionCeilingMinutes} min"
    }

    /** "25·20·15·10·5·2.5·1.25" — what the plate stack can round to. */
    private fun describePlates(available: List<EquipmentEntity>): String {
        val plates = available
            .filter { it.loadingScheme == LoadingScheme.PLATE_LOADED }
            .flatMap { it.platePairs.keys }
            .distinct()
            .sortedDescending()
        return if (plates.isEmpty()) "No plate-loaded kit" else plates.joinToString("·", transform = ::formatKg)
    }

    /**
     * PRs since the block started: an estimated 1RM that beat everything logged before
     * it, counted in order so a session with three climbing sets scores once per beat.
     */
    private suspend fun countPrsThisBlock(blockStartedAt: Long?): Int {
        if (blockStartedAt == null) return 0
        val byExercise = exerciseDao.getTrainedExerciseIds().associateWith { exerciseId ->
            sessionDao.getWorkingSetsForExercise(exerciseId).filter { it.reps in 1..12 }
        }

        var count = 0
        for ((_, sets) in byExercise) {
            var best = sets.filter { it.completedAt < blockStartedAt }
                .maxOfOrNull { it.estimated1Rm() } ?: 0.0
            for (set in sets.filter { it.completedAt >= blockStartedAt }.sortedBy { it.completedAt }) {
                val e1rm = set.estimated1Rm()
                if (e1rm > best + 1e-6) {
                    count++
                    best = e1rm
                }
            }
        }
        return count
    }

    fun toggleUnits() {
        viewModelScope.launch {
            val profile = profileDao.get() ?: return@launch
            profileDao.upsert(profile.copy(useMetric = !profile.useMetric))
            refresh()
        }
    }

    fun setDeloadPrompts(enabled: Boolean) {
        _state.value = _state.value.copy(deloadPromptsEnabled = enabled)
    }

    fun toggleEquipment(equipmentId: String) {
        viewModelScope.launch {
            val eq = equipmentDao.getById(equipmentId) ?: return@launch
            equipmentDao.update(eq.copy(isAvailable = !eq.isAvailable))
            refresh()
        }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    private fun monthYear(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            .uppercase()

    private fun formatKg(kg: Double): String =
        if (kg == kg.toLong().toDouble()) {
            kg.toLong().toString()
        } else {
            "%.2f".format(kg).trimEnd('0').trimEnd('.')
        }
}
