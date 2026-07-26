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
import kotlin.math.roundToInt

@HiltViewModel
class SetLoggingViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    savedState: SavedStateHandle,
    val mediaResolver: MediaResolver,
) : ViewModel() {

    val sessionId: Long = savedState.get<Long>(ARG_SESSION_ID) ?: 0L
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

    /** Running order of the whole session, so the screen can move on when this lift is done. */
    private var sessionSlots: List<TemplateSlotEntity> = emptyList()
    private var slotIndex: Int = -1

    private var restJob: Job? = null
    private var restDeadlineMillis: Long = 0L
    private var restTotalSeconds: Int = 0

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val ex = repo.getExercise(exerciseId)
            val session = repo.getSession(sessionId)

            // The session already knows its template, so the prescription is derivable
            // from (session, exerciseId). Previously this depended on a slotId argument
            // that navigation never supplied, so every set fell back to the generic
            // 3 × 8-12 at 120 s default even when a program said otherwise.
            sessionSlots = session?.templateId
                ?.let { repo.getSlotsForTemplate(it) }
                .orEmpty()
            slotIndex = sessionSlots.indexOfFirst { it.exerciseId == exerciseId }

            val sl = sessionSlots.getOrNull(slotIndex) ?: slotId?.let { repo.getSlot(it) }
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
                    endImageUri = mediaResolver.endImage(exerciseId),
                    supersetLabel = supersetLabel(sl?.supersetGroup),
                    // Guidance is worth opening whenever there is anything to show:
                    // stills, the muscles worked, or equipment-valid swaps. Gating it on
                    // instructions alone hid the sheet permanently, because the curated
                    // seed carries no instruction text.
                    hasGuidance = ex != null,
                    exercisePositionLabel = positionLabel(),
                    nextExerciseName = null, // resolved below once names are loaded
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
            val nextName = nextSlot()?.let { repo.getExercise(it.exerciseId)?.name }
            _state.update {
                it.copy(
                    nextExerciseName = nextName,
                    substitutes = ex?.let { e -> loadSubstitutes(e) }.orEmpty(),
                    guidanceMuscleTags = ex?.let { e ->
                        listOf(e.primaryMuscle) + e.secondaryMuscles
                    }?.map { m -> m.name.replace('_', ' ') }.orEmpty(),
                    instructionSteps = ex?.instructions
                        ?.split('\n')
                        ?.map(String::trim)
                        ?.filter(String::isNotEmpty)
                        .orEmpty(),
                )
            }

            recomputePlates()
            observeSets()
        }
    }

    /**
     * Alternatives the user could actually perform right now: same primary muscle, kit
     * they own, ranked by how much of the secondary work they also cover. This is what
     * makes an occupied rack a one-tap problem instead of a session-ending one.
     */
    private suspend fun loadSubstitutes(current: ExerciseEntity): List<SubstituteOption> =
        repo.availableExercisesForMuscle(current.primaryMuscle)
            .filter { it.id != current.id }
            .sortedWith(
                compareByDescending<ExerciseEntity> {
                    it.secondaryMuscles.count { m -> m in current.secondaryMuscles }
                }.thenByDescending { it.isCompound == current.isCompound }
                    .thenBy { it.name }
            )
            .take(MAX_SUBSTITUTES)
            .map { candidate ->
                SubstituteOption(
                    exerciseId = candidate.id,
                    name = candidate.name,
                    equipmentLabel = repo.getPrimaryEquipment(candidate)?.displayName
                        ?: "No equipment",
                    overlapPercent = overlapPercent(current, candidate),
                    startImageUri = mediaResolver.startImage(candidate.id),
                    endImageUri = mediaResolver.endImage(candidate.id),
                    primaryMuscle = candidate.primaryMuscle,
                )
            }

    /**
     * Share of the original's muscles a candidate also trains, as a percentage.
     *
     * Primary counts double: an exercise that hits the same primary muscle is a far
     * closer substitute than one that merely shares two secondaries.
     */
    private fun overlapPercent(current: ExerciseEntity, candidate: ExerciseEntity): Int {
        val wanted = buildMap {
            put(current.primaryMuscle, PRIMARY_WEIGHT)
            current.secondaryMuscles.forEach { put(it, SECONDARY_WEIGHT) }
        }
        val covered = buildSet {
            add(candidate.primaryMuscle)
            addAll(candidate.secondaryMuscles)
        }
        val total = wanted.values.sum()
        if (total == 0.0) return 0
        val matched = wanted.filterKeys { it in covered }.values.sum()
        return ((matched / total) * 100).roundToInt().coerceIn(0, 100)
    }

    private fun nextSlot(): TemplateSlotEntity? =
        if (slotIndex >= 0) sessionSlots.getOrNull(slotIndex + 1) else null

    private fun positionLabel(): String =
        if (slotIndex >= 0 && sessionSlots.isNotEmpty()) {
            "EXERCISE ${slotIndex + 1} OF ${sessionSlots.size}"
        } else {
            ""
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

                // What the rest screen's primary button should do once this set's rest
                // ends: another set, the next lift, or close out the session. Before this
                // the button always just ended the rest, so a session could be started
                // but never advanced past its first exercise and never finished.
                val next = nextSlot()
                val action = when {
                    remaining > 0 -> RestAction.NEXT_SET
                    next != null -> RestAction.NEXT_EXERCISE
                    else -> RestAction.FINISH_SESSION
                }

                _state.update {
                    it.copy(
                        loggedSets = logged,
                        previousSets = previous,
                        setNumber = setNum,
                        headerSubtitle = buildHeaderSubtitle(
                            setNum, ts, it.repRangeLow, it.repRangeHigh, remaining
                        ),
                        restSubtitle = buildRestSubtitle(workingCount, remaining),
                        coachReasoningLine = buildReasoningLine(logged, previous),
                        prBadgeText = if (hasPr && lastLogged != null)
                            "Best set you've done at ${formatKg(lastLogged.loadKg)} kg."
                        else null,
                        restCoachText = buildRestCoachText(remaining, it.loadKg, it.nextExerciseName),
                        restPrimaryAction = action,
                        restPrimaryLabel = when (action) {
                            RestAction.NEXT_SET -> "I'm ready — set $setNum"
                            RestAction.NEXT_EXERCISE -> "Next — ${it.nextExerciseName.orEmpty()}"
                            RestAction.FINISH_SESSION -> "Finish session"
                        },
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
                rir = difficulty?.rir?.coerceAtLeast(0),
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

    /**
     * Counts down to a wall-clock deadline rather than subtracting one per `delay(1000)`.
     * A loop of one-second sleeps drifts, and drifts badly if the coroutine is ever
     * descheduled — so a two-minute rest could read 2:00 while nearly three had passed.
     * Ticking against a deadline means the number is right no matter what the process did.
     */
    private fun startRest() {
        val seconds = slot?.restSeconds ?: DEFAULT_REST_SECONDS
        restJob?.cancel()
        restDeadlineMillis = System.currentTimeMillis() + seconds * 1000L
        restTotalSeconds = seconds
        _state.update { it.copy(rest = RestState.Running(seconds, seconds)) }

        restJob = viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                if (_state.value.rest !is RestState.Running) break

                val remaining = remainingRestSeconds()
                if (remaining <= 0) {
                    _state.update { it.copy(rest = RestState.Idle) }
                    _events.tryEmit(WorkoutEvent.RestComplete)
                    break
                }
                _state.update {
                    it.copy(rest = RestState.Running(remaining, restTotalSeconds))
                }
            }
        }
    }

    private fun remainingRestSeconds(): Int {
        val millisLeft = restDeadlineMillis - System.currentTimeMillis()
        // Round up so the timer shows 1 rather than 0 for the final part-second.
        return ((millisLeft + 999) / 1000).coerceAtLeast(0).toInt()
    }

    fun skipRest() {
        restJob?.cancel()
        restJob = null
        _state.update { it.copy(rest = RestState.Idle) }
    }

    /** ±seconds while resting; grows the total so the progress bar stays honest. */
    fun adjustRest(deltaSeconds: Int) {
        if (_state.value.rest !is RestState.Running) return
        val newRemaining = (remainingRestSeconds() + deltaSeconds).coerceIn(0, MAX_REST_SECONDS)
        restDeadlineMillis = System.currentTimeMillis() + newRemaining * 1000L
        restTotalSeconds = maxOf(restTotalSeconds, newRemaining)
        _state.update { it.copy(rest = RestState.Running(newRemaining, restTotalSeconds)) }
    }

    /** Moves to the next lift in the running order. No-op on a freestyle session. */
    fun goToNextExercise(onNavigate: (Long, String) -> Unit) {
        val next = nextSlot() ?: return
        skipRest()
        onNavigate(sessionId, next.exerciseId)
    }

    /** Stamps the finish time so the session stops counting as in progress. */
    fun finishSession(onFinished: (Long) -> Unit) {
        skipRest()
        viewModelScope.launch {
            repo.endSession(sessionId, System.currentTimeMillis())
            onFinished(sessionId)
        }
    }

    /** Guidance auto-opens once per exercise, then only on request (COACHING.md §4). */
    fun markGuidanceSeen() {
        val ex = exercise ?: return
        if (ex.hasBeenIntroduced) return
        exercise = ex.copy(hasBeenIntroduced = true)
        viewModelScope.launch { repo.markExerciseIntroduced(ex.id) }
    }

    override fun onCleared() {
        super.onCleared()
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

    /** Resting, so the subtitle reports what is behind you, not what is next. */
    private fun buildRestSubtitle(logged: Int, remaining: Int): String = when {
        remaining > 0 -> "SET $logged LOGGED · $remaining TO GO"
        else -> "SET $logged LOGGED · LAST ONE"
    }

    private fun buildReasoningLine(logged: List<LoggedSetLine>, previous: List<PreviousSetLine>): String {
        val lastWorking = logged.lastOrNull { !it.isWarmup }
        return when {
            lastWorking != null -> "Same weight as your last set —"
            previous.isNotEmpty() -> "Based on your last session —"
            else -> ""
        }
    }

    private fun buildRestCoachText(remaining: Int, loadKg: Double, nextExercise: String?): String {
        val restSeconds = slot?.restSeconds ?: DEFAULT_REST_SECONDS
        val restMinutes = restSeconds / 60
        val restLabel = if (restMinutes >= 1) {
            "$restMinutes minute${if (restMinutes > 1) "s" else ""}"
        } else {
            "$restSeconds seconds"
        }
        return when {
            remaining > 0 -> "$restLabel is the prescription. Next set: same ${formatKg(loadKg)} kg."
            nextExercise != null -> "That's this lift done. $nextExercise is up next."
            else -> "Last set of the session. Nice work."
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

        /** Sub-second so the readout never sits a whole second behind the deadline. */
        private const val TICK_MILLIS = 250L

        /** Enough to find a free station without turning the sheet into a catalogue. */
        private const val MAX_SUBSTITUTES = 5

        private const val PRIMARY_WEIGHT = 2.0
        private const val SECONDARY_WEIGHT = 1.0
    }
}
