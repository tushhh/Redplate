package dev.redplate.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.LoadingScheme
import dev.redplate.data.MediaResolver
import dev.redplate.data.PlateMath
import dev.redplate.data.SetLogEntity
import dev.redplate.data.TemplateSlotEntity
import dev.redplate.data.WorkoutRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetLoggingViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    savedState: SavedStateHandle,
    val mediaResolver: MediaResolver,
) : ViewModel() {

    private val sessionId: Long = savedState.get<Long>(ARG_SESSION_ID) ?: 0L
    private val exerciseId: String = savedState.get<String>(ARG_EXERCISE_ID) ?: ""
    private val slotId: Long? = savedState.get<Long>(ARG_SLOT_ID)?.takeIf { it > 0L }

    private val _state = MutableStateFlow(SetLoggingUiState())
    val state: StateFlow<SetLoggingUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WorkoutEvent> = _events.asSharedFlow()

    /** Set-log row ids that were PRs when logged. Not persisted on the entity, so tracked here. */
    private val prSetIds = MutableStateFlow<Set<Long>>(emptySet())

    private var exercise: ExerciseEntity? = null
    private var equipment: EquipmentEntity? = null
    private var slot: TemplateSlotEntity? = null

    private var restJob: Job? = null

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val ex = repo.getExercise(exerciseId)
            val sl = slotId?.let { repo.getSlot(it) }
            val eq = ex?.let { repo.getPrimaryEquipment(it) }
            exercise = ex
            slot = sl
            equipment = eq

            val repHigh = sl?.repRangeHigh ?: DEFAULT_REP_HIGH
            val startLoad = resolveStartLoad(sl, eq)

            val targetSets = sl?.targetSets ?: DEFAULT_TARGET_SETS
            val repLow = sl?.repRangeLow ?: DEFAULT_REP_LOW

            _state.update {
                it.copy(
                    isLoading = false,
                    exerciseId = exerciseId,
                    exerciseName = ex?.name ?: "Exercise",
                    primaryMuscle = ex?.primaryMuscle ?: dev.redplate.data.MuscleGroup.CHEST,
                    imageUri = mediaResolver.startImage(exerciseId),
                    supersetLabel = supersetLabel(sl?.supersetGroup),
                    hasGuidance = ex?.instructions != null,
                    targetSets = targetSets,
                    repRangeLow = repLow,
                    repRangeHigh = repHigh,
                    targetRir = sl?.targetRir,
                    headerSubtitle = buildHeaderSubtitle(1, targetSets, repLow, repHigh, targetSets),
                    coachReasoningLine = if (sl?.workingLoadKg != null) "Prescribed weight —" else "",
                    reps = repHigh,
                    rir = sl?.targetRir,
                    loadKg = startLoad,
                    isPlateLoaded = eq?.loadingScheme == LoadingScheme.PLATE_LOADED,
                )
            }
            recomputePlates()
            observeSets()
        }
    }

    private fun observeSets() {
        viewModelScope.launch {
            combine(
                repo.observeSetsForSession(sessionId),
                repo.observeHistory(exerciseId),
                prSetIds,
            ) { sessionSets, history, prIds ->
                val mine = sessionSets.filter { it.exerciseId == exerciseId }
                val logged = mine.map { s ->
                    LoggedSetLine(
                        setIndex = s.setIndex,
                        loadKg = s.loadKg,
                        reps = s.reps,
                        rir = s.rir,
                        isWarmup = s.isWarmup,
                        isPr = s.id in prIds,
                    )
                }
                val workingCount = mine.count { !it.isWarmup }

                // The most recent session that isn't this one, in chronological order.
                val prevSessionId = history.firstOrNull { it.sessionId != sessionId }?.sessionId
                val previous = if (prevSessionId == null) {
                    emptyList()
                } else {
                    history.filter { it.sessionId == prevSessionId }
                        .sortedBy { it.completedAt }
                        .map { PreviousSetLine(it.loadKg, it.reps, it.rir) }
                }

                Triple(logged, previous, workingCount)
            }.collect { (logged, previous, workingCount) ->
                val setNum = workingCount + 1
                val ts = _state.value.targetSets
                val remaining = (ts - workingCount).coerceAtLeast(0)
                val lastLogged = logged.lastOrNull { !it.isWarmup }
                val hasPr = lastLogged?.isPr == true

                _state.update {
                    it.copy(
                        loggedSets = logged,
                        previousSets = previous,
                        setNumber = setNum,
                        headerSubtitle = buildHeaderSubtitle(
                            setNum, ts, it.repRangeLow, it.repRangeHigh, remaining
                        ),
                        coachReasoningLine = buildReasoningLine(logged, previous),
                        prBadgeText = if (hasPr && lastLogged != null)
                            "Best set you've done at ${formatKg(lastLogged.loadKg)} kg."
                        else null,
                        restCoachText = buildRestCoachText(remaining, it.loadKg),
                        restPrimaryLabel = if (remaining > 0)
                            "I'm ready — set $setNum"
                        else "Done",
                    )
                }
            }
        }
    }

    // ── Load stepper (delegates to PlateMath so we never offer an unloadable weight) ──

    fun loadUp() = setLoad(
        equipment?.let { PlateMath.nextLoadUp(_state.value.loadKg, it) } ?: (_state.value.loadKg + 2.5)
    )

    fun loadDown() = setLoad(
        equipment?.let { PlateMath.nextLoadDown(_state.value.loadKg, it) }
            ?: (_state.value.loadKg - 2.5).coerceAtLeast(0.0)
    )

    private fun setLoad(kg: Double) {
        _state.update { it.copy(loadKg = kg) }
        recomputePlates()
    }

    private fun recomputePlates() {
        val eq = equipment
        if (eq != null && eq.loadingScheme == LoadingScheme.PLATE_LOADED) {
            val pl = PlateMath.load(_state.value.loadKg, eq)
            _state.update { it.copy(plateLoad = pl, isExactLoad = pl.exact, isPlateLoaded = true) }
        } else {
            _state.update { it.copy(plateLoad = null, isExactLoad = true, isPlateLoaded = false) }
        }
    }

    // ── Rep / RIR steppers ──

    fun repsUp() = _state.update { it.copy(reps = it.reps + 1) }
    fun repsDown() = _state.update { it.copy(reps = (it.reps - 1).coerceAtLeast(0)) }

    /** null (unreported) → 0 → … → MAX_RIR, then clamps; stepping down off 0 returns to unreported. */
    fun rirUp() = _state.update {
        it.copy(rir = when (val r = it.rir) {
            null -> 0
            else -> (r + 1).coerceAtMost(MAX_RIR)
        })
    }

    fun rirDown() = _state.update {
        it.copy(rir = when (val r = it.rir) {
            null -> null
            0 -> null
            else -> r - 1
        })
    }

    /** Set difficulty via chips (replaces RIR stepper in revamped UI). */
    fun setDifficulty(difficulty: Difficulty?) {
        _state.update {
            it.copy(
                difficulty = difficulty,
                rir = difficulty?.rir,
            )
        }
    }

    fun toggleWarmup() = _state.update { it.copy(isWarmup = !it.isWarmup) }

    // ── Completing a set ──

    fun completeSet() {
        val s = _state.value
        if (exercise == null || !s.canCompleteSet) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            val priorBest = if (!s.isWarmup) repo.priorBestE1rm(exerciseId) else null
            val e1rm = s.loadKg * (1 + s.reps / 30.0)
            val isPr = !s.isWarmup &&
                s.loadKg > 0.0 &&
                s.reps in 1..12 &&
                (priorBest == null || e1rm > priorBest + 1e-6)

            val id = repo.logSet(
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    setIndex = s.loggedSets.size,
                    loadKg = s.loadKg,
                    reps = s.reps,
                    rir = s.rir,
                    isWarmup = s.isWarmup,
                    completedAt = now,
                    restTakenSeconds = null,
                )
            )
            if (isPr) prSetIds.update { it + id }

            _events.tryEmit(if (isPr) WorkoutEvent.PrHit else WorkoutEvent.SetLogged)

            // After a warmup, default the next set back to working.
            if (s.isWarmup) _state.update { it.copy(isWarmup = false) }

            startRest()
        }
    }

    // ── Rest timer (auto-starts on completion at the prescribed interval) ──

    private fun startRest() {
        val seconds = slot?.restSeconds ?: DEFAULT_REST_SECONDS
        restJob?.cancel()
        _state.update { it.copy(rest = RestState.Running(seconds, seconds)) }
        restJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val running = _state.value.rest as? RestState.Running ?: break
                val next = running.remainingSeconds - 1
                if (next <= 0) {
                    _state.update { it.copy(rest = RestState.Idle) }
                    _events.tryEmit(WorkoutEvent.RestComplete)
                    break
                }
                _state.update { it.copy(rest = RestState.Running(next, running.totalSeconds)) }
            }
        }
    }

    fun skipRest() {
        restJob?.cancel()
        _state.update { it.copy(rest = RestState.Idle) }
    }

    /** ±15 s while resting; grows the total so a progress readout stays consistent. */
    fun adjustRest(deltaSeconds: Int) {
        val running = _state.value.rest as? RestState.Running ?: return
        val newRemaining = (running.remainingSeconds + deltaSeconds).coerceIn(0, MAX_REST_SECONDS)
        _state.update {
            it.copy(rest = RestState.Running(newRemaining, maxOf(running.totalSeconds, newRemaining)))
        }
    }

    override fun onCleared() {
        restJob?.cancel()
    }

    // ── Helpers ──

    private fun resolveStartLoad(slot: TemplateSlotEntity?, eq: EquipmentEntity?): Double {
        val desired = slot?.workingLoadKg
            ?: eq?.barWeightKg
            ?: eq?.availableLoads?.firstOrNull()
            ?: 20.0
        return eq?.nearestAchievable(desired) ?: desired
    }

    private fun supersetLabel(group: Int?): String? =
        group?.takeIf { it >= 1 }?.let { "SUPERSET " + ('A' + (it - 1)) }

    private fun buildHeaderSubtitle(setNum: Int, total: Int, repLow: Int, repHigh: Int, remaining: Int): String {
        return "SET $setNum OF $total · $repLow–$repHigh REPS · $remaining LEFT"
    }

    private fun buildReasoningLine(logged: List<LoggedSetLine>, previous: List<PreviousSetLine>): String {
        val lastWorking = logged.lastOrNull { !it.isWarmup }
        return when {
            lastWorking != null -> {
                if (lastWorking.loadKg == _state.value.loadKg)
                    "Same weight as your last set —"
                else
                    "Bumped from your last set —"
            }
            previous.isNotEmpty() -> "Based on your last session —"
            else -> ""
        }
    }

    private fun buildRestCoachText(remaining: Int, loadKg: Double): String {
        val restSeconds = slot?.restSeconds ?: DEFAULT_REST_SECONDS
        val restMinutes = restSeconds / 60
        val restLabel = if (restMinutes >= 1) "$restMinutes minute${if (restMinutes > 1) "s" else ""}" else "$restSeconds seconds"
        return if (remaining > 0) {
            "$restLabel is the prescription. Next set: same ${formatKg(loadKg)} kg."
        } else {
            "Last set done. Nice work."
        }
    }

    private fun formatKg(kg: Double): String =
        if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString().trimEnd('0').trimEnd('.')

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        const val ARG_EXERCISE_ID = "exerciseId"
        const val ARG_SLOT_ID = "slotId"

        private const val DEFAULT_TARGET_SETS = 3
        private const val DEFAULT_REP_LOW = 8
        private const val DEFAULT_REP_HIGH = 12
        private const val DEFAULT_REST_SECONDS = 120
        private const val MAX_REST_SECONDS = 60 * 60
        private const val MAX_RIR = 5
    }
}
