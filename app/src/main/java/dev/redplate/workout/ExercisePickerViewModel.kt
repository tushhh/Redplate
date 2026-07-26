package dev.redplate.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.ExerciseDao
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.MediaResolver
import dev.redplate.data.MuscleGroup
import dev.redplate.data.VolumeLandmarkEntity
import dev.redplate.data.VolumeLandmarks
import dev.redplate.data.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether the picker is in muscle-selection or exercise-selection phase. */
enum class PickerPhase { MUSCLES, EXERCISES }

@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val repo: WorkoutRepository,
    private val savedState: SavedStateHandle,
    val mediaResolver: MediaResolver,
) : ViewModel() {

    // ── Phase ─────────────────────────────────────────────────────────────────

    private val _phase = MutableStateFlow(PickerPhase.MUSCLES)
    val phase: StateFlow<PickerPhase> = _phase.asStateFlow()

    // ── Body map state ────────────────────────────────────────────────────────

    private val _isFrontView = MutableStateFlow(true)
    val isFrontView: StateFlow<Boolean> = _isFrontView.asStateFlow()

    fun toggleView() { _isFrontView.value = !_isFrontView.value }

    // ── Multi-selection (5a design: pick muscles, then "Build the session") ───

    private val _pickedMuscles = MutableStateFlow<Set<MuscleGroup>>(emptySet())
    val pickedMuscles: StateFlow<Set<MuscleGroup>> = _pickedMuscles.asStateFlow()

    val pickedCount: StateFlow<Int> = _pickedMuscles
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Toggle a muscle in/out of the picked set. */
    fun toggleMuscle(muscle: MuscleGroup) {
        _pickedMuscles.value = _pickedMuscles.value.toMutableSet().apply {
            if (contains(muscle)) remove(muscle) else add(muscle)
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun search(query: String) {
        _searchQuery.value = query
    }

    /**
     * Weekly trained volume per muscle, shading the body map against each muscle's own
     * landmarks. This was stubbed to an empty map, so every region rendered untrained and
     * the map's whole reason for existing — seeing what is undertrained *while* choosing
     * what to train — did nothing.
     */
    private val _muscleVolume = MutableStateFlow<Map<MuscleGroup, VolumeLevel>>(emptyMap())
    val muscleVolume: StateFlow<Map<MuscleGroup, VolumeLevel>> = _muscleVolume.asStateFlow()

    init {
        refreshVolume()
    }

    /** Recomputes shading. Called on load and again whenever the picker is resumed. */
    fun refreshVolume() {
        viewModelScope.launch {
            val hardSets = repo.weeklyHardSetsPerMuscle()
            _muscleVolume.value = MuscleGroup.entries.associateWith { muscle ->
                classify(hardSets[muscle] ?: 0.0, VolumeLandmarks.forMuscle(muscle))
            }
        }
    }

    private fun classify(hardSets: Double, landmark: VolumeLandmarkEntity): VolumeLevel = when {
        hardSets <= 0.0 -> VolumeLevel.NONE
        hardSets < landmark.mev -> VolumeLevel.BELOW_MEV
        hardSets < landmark.mavHigh -> VolumeLevel.MEV_TO_MAV
        hardSets < landmark.mrv -> VolumeLevel.APPROACHING_MRV
        else -> VolumeLevel.AT_MRV
    }

    // ── Exercise list (used by search and exercise-selection phase) ───────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val exercises: StateFlow<List<ExerciseEntity>> =
        combine(_searchQuery, _pickedMuscles, _phase) { q, muscles, phase ->
            Triple(q, muscles, phase)
        }.flatMapLatest { (q, muscles, phase) ->
            when {
                q.isNotBlank() -> exerciseDao.search(q)
                phase == PickerPhase.EXERCISES && muscles.isNotEmpty() -> {
                    // Combine exercises for all picked muscles
                    val flows = muscles.map { repo.observeExercisesByMuscleWithAvailableEquipment(it) }
                    combine(flows) { arrays -> arrays.flatMap { it }.distinctBy { it.id } }
                }
                else -> flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Selected exercise (exercise-selection phase) ──────────────────────────

    private val _selectedExerciseId = MutableStateFlow<String?>(null)
    val selectedExerciseId: StateFlow<String?> = _selectedExerciseId.asStateFlow()

    fun selectExercise(id: String) {
        _selectedExerciseId.value = if (_selectedExerciseId.value == id) null else id
    }

    // ── Session management ────────────────────────────────────────────────────

    private var activeSessionId: Long
        get() = savedState.get<Long>("activeSessionId") ?: 0L
        set(value) { savedState["activeSessionId"] = value }

    /** Transition from muscle selection to exercise selection. */
    fun buildSession() {
        viewModelScope.launch {
            getOrCreateSession()
            _phase.value = PickerPhase.EXERCISES
        }
    }

    fun goBackToMuscles() {
        _phase.value = PickerPhase.MUSCLES
        _selectedExerciseId.value = null
        _searchQuery.value = ""
    }

    suspend fun getOrCreateSession(): Long {
        val existing = activeSessionId
        if (existing > 0L) return existing
        val id = repo.startFreestyleSession(System.currentTimeMillis())
        activeSessionId = id
        return id
    }
}
