package dev.redplate.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.FinisherProgression
import dev.redplate.data.ProgressionEngine
import dev.redplate.data.isConditioning
import dev.redplate.data.LoadUnit
import dev.redplate.data.LoadingScheme
import dev.redplate.data.loadUnit
import dev.redplate.data.MediaResolver
import dev.redplate.data.PlateMath
import dev.redplate.data.ProfileDao
import dev.redplate.data.ProgramGenerator
import dev.redplate.data.SetLogEntity
import dev.redplate.data.TemplateSlotEntity
import dev.redplate.data.WorkoutRepository
import dev.redplate.data.WorkoutDraft
import dev.redplate.data.WorkoutDraftStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SetLoggingViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val profileDao: ProfileDao,
    private val programGenerator: ProgramGenerator,
    savedState: SavedStateHandle,
    val mediaResolver: MediaResolver,
    private val restNotifier: RestTimerNotifier,
    private val draftStore: WorkoutDraftStore,
) : ViewModel() {

    val sessionId: Long = savedState.get<Long>(ARG_SESSION_ID) ?: 0L
    private val exerciseId: String = savedState.get<String>(ARG_EXERCISE_ID) ?: ""
    private val slotId: Long? = savedState.get<Long>(ARG_SLOT_ID)?.takeIf { it > 0L }

    private val _state = MutableStateFlow(SetLoggingUiState())
    val state: StateFlow<SetLoggingUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WorkoutEvent> = _events.asSharedFlow()

    /**
     * The best estimated max for this lift before this session, read once. PR stars are
     * worked out from the database against it, so they survive leaving the screen — they
     * used to live in memory and vanished the moment you pressed Back.
     */
    private var priorSessionBest: Double? = null

    /** Conditioning and assistance work have no meaningful estimated max. */
    private var prEligible = true

    /** The chip tapped for each set logged here, so an undo restores "Failed" as "Failed". */
    private val loggedDifficulty = mutableMapOf<Long, Difficulty?>()

    private var exercise: ExerciseEntity? = null
    private var equipment: EquipmentEntity? = null
    private var slot: TemplateSlotEntity? = null

    /** Conditioning: minutes, not reps; no load, no PR, no muscle volume. */
    private var isCardio = false

    /**
     * Set while a set is being written. Two quick taps on Done used to launch two writes
     * that both read the same set index, logging the set twice.
     */
    private var completing = false

    /** Running order of the whole session, so the screen can move on when this lift is done. */
    private var sessionSlots: List<TemplateSlotEntity> = emptyList()
    private var slotIndex: Int = -1

    private var restJob: Job? = null
    private var restDeadlineMillis: Long = 0L
    private var restTotalSeconds: Int = 0

    /** When the previous set of this lift was logged, so the next one can record real rest. */
    private var lastSetCompletedAt: Long? = null

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

            // Conditioning is prescribed in minutes, whether the plan put it there as a
            // finisher or the user picked it freestyle.
            val cardio = sl?.isCardioFinisher == true || ex?.isConditioning == true
            isCardio = cardio
            val repHigh = sl?.repRangeHigh ?: if (cardio) FinisherProgression.START_MINUTES else DEFAULT_REP_HIGH
            val startLoad = resolveStartLoad(sl, eq)

            val targetSets = sl?.targetSets ?: if (cardio) 1 else DEFAULT_TARGET_SETS
            val repLow = sl?.repRangeLow ?: if (cardio) FinisherProgression.START_MINUTES else DEFAULT_REP_LOW

            _state.update {
                it.copy(
                    isLoading = false,
                    isCardioFinisher = cardio,
                    isFreestyle = slotIndex < 0,
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
                    // A lift opens at the top of its range so the stepper only ever comes
                    // down; a finisher opens at its target minutes.
                    reps = if (cardio) repLow else repHigh,
                    // A finisher opens with no effort picked, so an untouched chip row is
                    // logged as unreported and does not earn a minute. Lifts keep the
                    // prescription pre-filled, as they always have.
                    rir = if (cardio) null else sl?.targetRir,
                    loadKg = startLoad,
                    isPlateLoaded = eq?.loadingScheme == LoadingScheme.PLATE_LOADED,
                    // The readout says what the machine says. Bodyweight and banded work
                    // have no dial to read, so they fall back to kilograms of added load.
                    loadUnitLabel = (eq?.loadUnit ?: LoadUnit.KILOGRAMS).label,
                    loadIsWholeNumber = eq?.loadingScheme == LoadingScheme.RESISTANCE_LEVEL,
                    loadIsPerLimb = eq?.perLimb == true,
                    loadIsAssistance = eq?.isAssistance == true,
                    stationLabel = ex?.let { repo.describeStation(it) },
                )
            }
            val nextName = nextSlot()?.let { repo.getExercise(it.exerciseId)?.name }
            _state.update {
                it.copy(
                    nextExerciseName = nextName,
                    substitutes = ex?.let { e ->
                        if (cardio) loadConditioningSwaps(e) else loadSubstitutes(e)
                    }.orEmpty(),
                    guidanceMuscleTags = ex?.let { e ->
                        listOf(e.primaryMuscle) + e.secondaryMuscles
                    }?.map { m -> m.displayName.uppercase() }.orEmpty(),
                    instructionSteps = ex?.instructions
                        ?.split('\n')
                        ?.map(String::trim)
                        ?.filter(String::isNotEmpty)
                        .orEmpty(),
                )
            }

            priorSessionBest = repo.priorBestE1rmExcludingSession(exerciseId, sessionId)
            prEligible = !cardio && eq?.isAssistance != true
            restoreDraft(cardio)

            recomputePlates()
            observeSets()
            persistDrafts()
        }
    }

    /**
     * Puts back what was on screen when the user left: the draft if there is one, otherwise
     * the weight of the last set they actually logged on this lift today.
     */
    private suspend fun restoreDraft(cardio: Boolean) {
        val logged = repo.getSetsForSession(sessionId).filter { it.exerciseId == exerciseId }
        lastSetCompletedAt = logged.maxOfOrNull { it.completedAt }
        val draft = draftStore.load(sessionId, exerciseId)
        val lastWorking = logged.filter { !it.isWarmup }.maxByOrNull { it.completedAt }

        if (draft == null) {
            if (!cardio && lastWorking != null) _state.update { it.copy(loadKg = lastWorking.loadKg) }
            return
        }

        val sameSet = draft.loggedCount == logged.size
        _state.update {
            it.copy(
                loadKg = if (cardio) it.loadKg else draft.loadKg,
                reps = if (sameSet) draft.reps else it.reps,
                difficulty = if (sameSet) draft.difficulty?.let { d -> Difficulty.entries.firstOrNull { e -> e.name == d } } else it.difficulty,
                rir = if (sameSet) draft.rir else it.rir,
                adjustmentNote = draft.adjustmentNote.takeIf { sameSet },
            )
        }
        if (sameSet && draft.restDeadlineMillis > System.currentTimeMillis()) {
            restDeadlineMillis = draft.restDeadlineMillis
            restTotalSeconds = draft.restTotalSeconds.coerceAtLeast(1)
            _state.update { it.copy(rest = RestState.Running(remainingRestSeconds(), restTotalSeconds)) }
            runRestCountdown()
        }
    }

    private var draftJob: Job? = null

    /** Writes the draft whenever anything the user could lose changes. */
    private fun persistDrafts() {
        draftJob = viewModelScope.launch {
            _state
                .map { s ->
                    WorkoutDraft(
                        loadKg = s.loadKg,
                        reps = s.reps,
                        difficulty = s.difficulty?.name,
                        rir = s.rir,
                        loggedCount = s.loggedSets.size,
                        adjustmentNote = s.adjustmentNote,
                        restDeadlineMillis = if (s.rest is RestState.Running) restDeadlineMillis else 0L,
                        restTotalSeconds = if (s.rest is RestState.Running) restTotalSeconds else 0,
                    )
                }
                .distinctUntilChanged()
                .collect { draftStore.save(sessionId, exerciseId, it) }
        }
    }

    /**
     * Alternatives the user could actually perform right now: same primary muscle, kit
     * they own, ranked by how much of the secondary work they also cover. This is what
     * makes an occupied rack a one-tap problem instead of a session-ending one.
     *
     * Lifts already in the running order are excluded. Swapping into one would put the
     * same exercise in two slots of the same session — you would meet it again later and
     * be asked to train it twice, which is not a substitution.
     */
    private suspend fun loadSubstitutes(current: ExerciseEntity): List<SubstituteOption> {
        val alreadyInSession = sessionSlots.mapTo(mutableSetOf()) { it.exerciseId }
        return repo.availableExercisesForMuscle(current.primaryMuscle)
            .filter { it.id != current.id && it.id !in alreadyInSession }
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
    }

    /**
     * A finisher swaps for another finisher — the treadmill is taken, so the bike. Ranked
     * by overlap like any swap, but drawn only from conditioning, so a squat can never be
     * offered in place of ten minutes on the rower.
     */
    private suspend fun loadConditioningSwaps(current: ExerciseEntity): List<SubstituteOption> {
        val alreadyInSession = sessionSlots.mapTo(mutableSetOf()) { it.exerciseId }
        return repo.availableConditioning()
            .filter { it.id != current.id && it.id !in alreadyInSession }
            .sortedByDescending { overlapPercent(current, it) }
            .take(MAX_SUBSTITUTES)
            .map { candidate ->
                SubstituteOption(
                    exerciseId = candidate.id,
                    name = candidate.name,
                    equipmentLabel = repo.getPrimaryEquipment(candidate)?.displayName ?: "No equipment",
                    overlapPercent = overlapPercent(current, candidate),
                    startImageUri = mediaResolver.startImage(candidate.id),
                    endImageUri = mediaResolver.endImage(candidate.id),
                    primaryMuscle = candidate.primaryMuscle,
                )
            }
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
            ) { sessionSets, history ->
                val mine = sessionSets.filter { it.exerciseId == exerciseId }
                val prIds = prSetIdsOf(mine)
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
                        coachReasoningLine = it.adjustmentNote
                            ?.takeIf { workingCount > 0 }
                            ?.substringBefore(" — ")
                            ?.let { note -> "$note —" }
                            ?: buildReasoningLine(logged, previous),
                        prBadgeText = if (hasPr && lastLogged != null)
                            "Best set you've done at ${formatKg(lastLogged.loadKg)} kg."
                        else null,
                        restCoachText = buildRestCoachText(
                            remaining, it.loadKg, it.nextExerciseName, it.adjustmentNote,
                        ),
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

    /**
     * Sets that beat everything before them: prior sessions, then earlier sets today — the
     * rule the haptic fires on when the set is logged, derived here rather than remembered
     * so it survives leaving the screen.
     */
    private fun prSetIdsOf(sets: List<SetLogEntity>): Set<Long> {
        if (!prEligible) return emptySet()
        var best = priorSessionBest
        val ids = mutableSetOf<Long>()
        for (set in sets.filter { !it.isWarmup }.sortedBy { it.completedAt }) {
            if (set.loadKg <= 0.0 || set.reps !in 1..12) continue
            val e1rm = set.estimated1Rm()
            if (best == null || e1rm > best + 1e-6) {
                ids += set.id
                best = e1rm
            }
        }
        return ids
    }

    // ── Load stepper (delegates to PlateMath so we never offer an unloadable weight) ──

    fun loadUp() = setLoad(
        equipment?.let { PlateMath.nextLoadUp(_state.value.loadKg, it) } ?: (_state.value.loadKg + 2.5)
    )

    fun loadDown() = setLoad(
        equipment?.let { PlateMath.nextLoadDown(_state.value.loadKg, it) }
            ?: (_state.value.loadKg - 2.5).coerceAtLeast(0.0)
    )

    /**
     * The user moved the weight themselves, so the coach's own adjustment no longer
     * describes what is on screen. The reasoning line is recomputed against the new load
     * on the spot — it used to wait for the next logged set and keep saying "same weight"
     * over a weight that had just changed.
     */
    private fun setLoad(kg: Double) {
        _state.update {
            it.copy(
                loadKg = kg,
                adjustmentNote = null,
                coachReasoningLine = buildReasoningLine(it.loggedSets, it.previousSets, kg),
            )
        }
        recomputePlates()
    }

    // ── Typing the load in directly ──

    /**
     * Opens the keypad on an empty field.
     *
     * The steppers walk the increments the equipment is *believed* to have; this records
     * what was actually on the machine. Those are different jobs, and only the first one
     * existed — so a stack marked in levels, or any weight the seeded ladder did not
     * happen to contain, simply could not be logged.
     */
    fun startLoadEntry() = _state.update { it.copy(loadEntry = "") }

    fun cancelLoadEntry() = _state.update { it.copy(loadEntry = null) }

    /** [key] is a digit or a decimal point. Anything else is ignored. */
    fun appendLoadDigit(key: Char) = _state.update { state ->
        val current = state.loadEntry ?: return@update state
        val next = when {
            key.isDigit() -> current + key
            // One point, and none at all on equipment that only reads whole numbers.
            key == '.' && !state.loadIsWholeNumber && !current.contains('.') ->
                if (current.isEmpty()) "0." else "$current."

            else -> current
        }
        if (next.length > MAX_LOAD_DIGITS) state else state.copy(loadEntry = next)
    }

    fun backspaceLoadEntry() = _state.update { state ->
        val current = state.loadEntry ?: return@update state
        state.copy(loadEntry = current.dropLast(1))
    }

    /**
     * Commits what was typed, exactly as typed.
     *
     * Deliberately not snapped to [EquipmentEntity.nearestAchievable]: the user is
     * reporting a fact about a set they have already done, and an inventory of plates the
     * app only half knows is not entitled to overrule it.
     */
    fun commitLoadEntry() {
        val state = _state.value
        val typed = state.loadEntry?.toDoubleOrNull() ?: return
        _state.update { it.copy(loadEntry = null) }
        setLoad(typed.coerceAtLeast(0.0))
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

    // ── Rep stepper ──

    fun repsUp() = _state.update { it.copy(reps = it.reps + 1) }
    fun repsDown() = _state.update { it.copy(reps = (it.reps - 1).coerceAtLeast(0)) }

    /**
     * How hard that was, in the words the design asks the question in (8a).
     *
     * This is the only way RIR is entered. A numeric stepper existed alongside it and had
     * no control on any screen — two ways to write one field, one of them unreachable, is
     * how the two drift apart.
     */
    fun setDifficulty(difficulty: Difficulty?) {
        _state.update {
            it.copy(
                difficulty = difficulty,
                rir = difficulty?.rir?.coerceAtLeast(0),
            )
        }
    }

    // ── Completing a set ──

    fun completeSet() {
        val s = _state.value
        if (exercise == null || !s.canCompleteSet || completing) return
        completing = true
        viewModelScope.launch {
            try {
                logCurrentSet(s)
            } finally {
                completing = false
            }
        }
    }

    private suspend fun logCurrentSet(s: SetLoggingUiState) {
        val now = System.currentTimeMillis()
        val cardio = isCardio

        // Conditioning earns no PR: its "reps" are minutes and it carries no load, so an
        // estimated max would be a number about nothing. It used to compare the readout's
        // phantom 20 kg against a logged 0 and fire a PR every single finisher.
        val priorBest = if (!s.isWarmup && !cardio) repo.priorBestE1rm(exerciseId) else null
        val e1rm = s.loadKg * (1 + s.reps / 30.0)
        val isPr = prEligible && !cardio && !s.isWarmup &&
            s.loadKg > 0.0 &&
            s.reps in 1..12 &&
            (priorBest == null || e1rm > priorBest + 1e-6)

        // Decided before the write, so the rest screen that the write triggers already
        // shows the adjusted weight rather than flickering from the old one.
        val workingLogged = s.loggedSets.count { !it.isWarmup } + if (s.isWarmup) 0 else 1
        val setsLeft = (s.targetSets - workingLogged).coerceAtLeast(0)
        val advice = if (!cardio && !s.isWarmup && setsLeft > 0) adviseNextSet(s) else null
        _state.update {
            it.copy(
                adjustmentNote = advice?.second,
                loadKg = advice?.first ?: it.loadKg,
            )
        }
        if (advice != null) recomputePlates()

        val id = repo.logSet(
            SetLogEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setIndex = s.loggedSets.size,
                loadKg = if (cardio) 0.0 else s.loadKg,
                reps = s.reps,
                rir = s.rir,
                isWarmup = s.isWarmup,
                completedAt = now,
                // How long the user actually rested before this set, not the
                // prescription. This column was always written null, which made it a
                // column that recorded nothing.
                restTakenSeconds = restTakenBefore(now),
                countsTowardVolume = !cardio && !s.isWarmup && (s.rir == null || s.rir <= 3),
            )
        )
        lastSetCompletedAt = now

        _events.tryEmit(if (isPr) WorkoutEvent.PrHit else WorkoutEvent.SetLogged)

        // After a warmup, default the next set back to working.
        if (s.isWarmup) _state.update { it.copy(isWarmup = false) }

        loggedDifficulty[id] = s.difficulty

        when {
            // Freestyle: there is no running order to know this was the last thing, so it
            // goes to the rest screen like any set, where "Finish session" is a choice and
            // Back picks something else — a cardio warm-up must not end the session.
            cardio && slotIndex < 0 -> startRest()

            // A programmed finisher is one block of time with no rest after it: move
            // straight on, or close the session when it is the last thing in it.
            cardio -> _events.tryEmit(
                if (nextSlot() != null) WorkoutEvent.AdvanceToNext else WorkoutEvent.SessionFinished,
            )

            else -> startRest()
        }
    }

    /**
     * COACHING.md §4: deviation is first-class. When a set lands well outside the range,
     * the next one is adjusted now rather than at the end of the session — and the screen
     * says why in one line.
     *
     * Two or more reps under the floor, or a failed rep, steps the load down one notch. Two
     * or more over the ceiling with three or more in reserve steps it up one. Anything
     * else leaves the weight where it is: inside the range, a hard last rep is the plan
     * working.
     */
    private fun adviseNextSet(s: SetLoggingUiState): Pair<Double, String>? {
        val sl = slot ?: return null
        val eq = equipment
        val load = s.loadKg
        val unit = s.loadUnitLabel.lowercase()
        val short = sl.repRangeLow - s.reps
        val failed = s.difficulty == Difficulty.FAILED

        if (short >= 2 || (failed && s.reps < sl.repRangeHigh)) {
            val lighter = when {
                eq == null -> (load - ProgressionEngine.MANUAL_STEP_KG).coerceAtLeast(0.0)
                eq.isAssistance -> PlateMath.nextLoadUp(load, eq)
                else -> PlateMath.nextLoadDown(load, eq)
            }
            if (kotlin.math.abs(lighter - load) < LOAD_EPSILON) return null
            val why = if (short >= 2) {
                "that set fell $short reps short of ${sl.repRangeLow}"
            } else {
                "that rep failed"
            }
            val verb = if (eq?.isAssistance == true) "More assistance" else "Down to ${formatKg(lighter)} $unit"
            return lighter to "$verb — $why."
        }

        val over = s.reps - sl.repRangeHigh
        if (over >= 2 && (s.rir ?: 0) >= 3) {
            val heavier = when {
                eq == null -> load + ProgressionEngine.MANUAL_STEP_KG
                eq.isAssistance -> PlateMath.nextLoadDown(load, eq)
                else -> PlateMath.nextLoadUp(load, eq)
            }
            if (kotlin.math.abs(heavier - load) < LOAD_EPSILON) return null
            val verb = if (eq?.isAssistance == true) "Less assistance" else "Up to ${formatKg(heavier)} $unit"
            return heavier to "$verb — ${s.reps} reps with ${s.rir} left is past the range."
        }
        return null
    }

    /**
     * Takes back the last set of this lift. A mis-tap on Done used to be permanent: the
     * repository could delete a set, but nothing on any screen could ask it to.
     *
     * The rest is cancelled and the input comes back pre-filled with what was removed,
     * so correcting a wrong rep count is one tap and a stepper, not re-entering the set.
     */
    fun undoLastSet() {
        if (completing) return
        viewModelScope.launch {
            val removed = repo.deleteLastSet(sessionId, exerciseId) ?: return@launch
            skipRest()
            // The next set's rest is measured from whatever came before the one removed.
            lastSetCompletedAt = repo.getSetsForSession(sessionId)
                .filter { it.exerciseId == exerciseId }
                .maxOfOrNull { it.completedAt }
            // "Failed the rep" and "All I had" both store 0 in reserve, so the chip comes
            // back from what was tapped, not from the number.
            val chip = loggedDifficulty.remove(removed.id)
                ?: Difficulty.entries.firstOrNull { d -> d.rir.coerceAtLeast(0) == removed.rir }
            _state.update {
                it.copy(
                    reps = removed.reps,
                    loadKg = if (isCardio) it.loadKg else removed.loadKg,
                    rir = removed.rir,
                    difficulty = chip,
                    adjustmentNote = null,
                )
            }
            recomputePlates()
        }
    }

    /**
     * Seconds between the previous set finishing and this one, or null for the first set
     * of a lift — there is nothing to have rested from.
     *
     * Measured from when the last set was logged rather than from the timer, so it stays
     * honest whether the user waited out the countdown, skipped it, or left the app and
     * came back.
     */
    private fun restTakenBefore(now: Long): Int? {
        val previous = lastSetCompletedAt ?: return null
        val seconds = ((now - previous) / 1000L).toInt()
        return seconds.takeIf { it in 0..MAX_REST_SECONDS }
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
        restDeadlineMillis = System.currentTimeMillis() + seconds * 1000L
        restTotalSeconds = seconds
        _state.update { it.copy(rest = RestState.Running(seconds, seconds)) }
        publishRest()
        runRestCountdown()
    }

    /** Ticks the on-screen countdown against [restDeadlineMillis] until it runs out. */
    private fun runRestCountdown() {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                if (_state.value.rest !is RestState.Running) break

                val remaining = remainingRestSeconds()
                if (remaining <= 0) {
                    _state.update { it.copy(rest = RestState.Idle) }
                    // The vibration is the alarm's job, not this loop's — see
                    // RestTimerNotifier. This only takes down the countdown that has
                    // just stopped being true.
                    restNotifier.clearCountdown()
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
        restNotifier.cancel()
    }

    /** ±seconds while resting; grows the total so the progress bar stays honest. */
    fun adjustRest(deltaSeconds: Int) {
        if (_state.value.rest !is RestState.Running) return
        val newRemaining = (remainingRestSeconds() + deltaSeconds).coerceIn(0, MAX_REST_SECONDS)
        restDeadlineMillis = System.currentTimeMillis() + newRemaining * 1000L
        restTotalSeconds = maxOf(restTotalSeconds, newRemaining)
        _state.update { it.copy(rest = RestState.Running(newRemaining, restTotalSeconds)) }
        // Re-armed against the new deadline, or the notification would keep counting to a
        // time the app no longer believes in.
        publishRest()
    }

    /**
     * Mirrors the running rest into the status bar and arms the alarm that ends it.
     *
     * Called on every change to the deadline rather than only at the start, because a
     * timer that is right on screen and wrong in the notification is worse than no
     * notification: the user is trusting whichever one they can see.
     */
    private fun publishRest() {
        restNotifier.start(
            deadlineMillis = restDeadlineMillis,
            exerciseName = exercise?.name.orEmpty(),
            setLabel = _state.value.restSubtitle.ifEmpty { "Rest" },
        )
    }

    /** "Add another exercise" on the last rest screen: rest ends, the picker opens. */
    fun addAnotherExercise(onNavigate: (Long) -> Unit) {
        skipRest()
        onNavigate(sessionId)
    }

    /** Moves to the next lift in the running order. No-op on a freestyle session. */
    fun goToNextExercise(onNavigate: (Long, String) -> Unit) {
        val next = nextSlot() ?: return
        skipRest()
        onNavigate(sessionId, next.exerciseId)
    }

    /**
     * Puts a different lift in this slot and opens it.
     *
     * The slot is rewritten *before* navigating, and that ordering is the whole point.
     * This used to navigate straight to the new exercise and leave the template alone, so
     * the incoming screen looked for an exercise the running order did not contain,
     * [slotIndex] came back -1, and everything derived from it collapsed: no prescription
     * (the generic 3 × 8-12 at 120 s took over), no working load, and — because
     * [nextSlot] returns null on a negative index — no next exercise. The rest screen's
     * one button could then only offer "Finish session", so swapping a lift silently
     * ended the workout at that lift, however many were left to do.
     *
     * [ProgramGenerator.replaceSlotExercise] re-prescribes for the new movement rather
     * than inheriting the old one's rep range, and deliberately drops the working load:
     * it belonged to the lift that was there. The swap is written to the template, so it
     * persists into the rest of the block — the same thing the pre-session swap in the
     * picker does.
     *
     * A freestyle session has no slot to rewrite, so it just navigates, exactly as before.
     */
    fun swapExercise(newExerciseId: String, onNavigate: (Long, String) -> Unit) {
        val current = slot
        skipRest()
        viewModelScope.launch {
            val profile = profileDao.get()
            if (current != null && profile != null) {
                programGenerator.replaceSlotExercise(current.id, newExerciseId, profile)
            }
            onNavigate(sessionId, newExerciseId)
        }
    }

    /** Stamps the finish time so the session stops counting as in progress. */
    fun finishSession(onFinished: (Long) -> Unit) {
        skipRest()
        // Nothing about this session is in progress any more; a late write would leave a
        // draft behind the clear in endSession.
        draftJob?.cancel()
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

    /**
     * Stops the on-screen tick and nothing else.
     *
     * The notification and the alarm are deliberately left running. This fires when the
     * user navigates away or swipes the app out of recents — which is *leaving the app
     * mid-rest*, the case the status-bar timer exists for. Cancelling here would mean the
     * buzz never came for the one user who most needed it.
     */
    override fun onCleared() {
        super.onCleared()
        restJob?.cancel()
    }

    // ── Helpers ──

    /**
     * Where the readout opens.
     *
     * A stored working load is used as it stands. It came either from the progression
     * engine, which already only produces loads the equipment can make, or from a weight
     * the user typed in — and re-snapping either of those to an inventory the app is only
     * guessing at would quietly change a number that was already right.
     */
    private fun resolveStartLoad(slot: TemplateSlotEntity?, eq: EquipmentEntity?): Double {
        slot?.workingLoadKg?.let { return it }
        val fallback = eq?.barWeightKg
            ?: eq?.availableLoads?.firstOrNull()
            ?: if (eq?.loadingScheme == LoadingScheme.RESISTANCE_LEVEL) 1.0 else 20.0
        return eq?.nearestAchievable(fallback) ?: fallback
    }

    private fun supersetLabel(group: Int?): String? =
        group?.takeIf { it >= 1 }?.let { "SUPERSET " + ('A' + (it - 1)) }

    private fun buildHeaderSubtitle(setNum: Int, total: Int, repLow: Int, repHigh: Int, remaining: Int): String {
        if (isCardio) return (if (slotIndex > 0) "FINISHER" else "CARDIO") + " · $repLow MIN TARGET"
        if (setNum > total) return "SET $setNum · EXTRA · $repLow–$repHigh REPS"
        return "SET $setNum OF $total · $repLow–$repHigh REPS · $remaining LEFT"
    }

    /** Resting, so the subtitle reports what is behind you, not what is next. */
    private fun buildRestSubtitle(logged: Int, remaining: Int): String = when {
        remaining > 0 -> "SET $logged LOGGED · $remaining TO GO"
        else -> "SET $logged LOGGED · LAST ONE"
    }

    /**
     * Why the weight on screen is the weight on screen.
     *
     * This used to say "Same weight as your last set" the moment anything was logged,
     * including right after the user had changed the load — the one moment it is certainly
     * untrue. It now reads the load actually showing against the load actually logged.
     */
    private fun buildReasoningLine(
        logged: List<LoggedSetLine>,
        previous: List<PreviousSetLine>,
        currentLoad: Double = _state.value.loadKg,
    ): String {
        if (isCardio) return ""
        val lastWorking = logged.lastOrNull { !it.isWarmup }
        return when {
            lastWorking == null ->
                if (previous.isNotEmpty()) "Based on your last session —" else ""

            kotlin.math.abs(lastWorking.loadKg - currentLoad) < LOAD_EPSILON ->
                "Same weight as your last set —"

            currentLoad > lastWorking.loadKg ->
                "Up from ${formatKg(lastWorking.loadKg)} kg —"

            else -> "Down from ${formatKg(lastWorking.loadKg)} kg —"
        }
    }

    private fun buildRestCoachText(
        remaining: Int,
        loadKg: Double,
        nextExercise: String?,
        adjustment: String?,
    ): String {
        val restLabel = ProgramGenerator.formatRest(slot?.restSeconds ?: DEFAULT_REST_SECONDS)
        val unit = _state.value.loadUnitLabel.lowercase()
        return when {
            remaining > 0 && adjustment != null -> "$adjustment $restLabel rest, then go again."
            remaining > 0 -> "$restLabel is the prescription. Next set: same ${formatKg(loadKg)} $unit."
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

        /** Sub-second so the readout never sits a whole second behind the deadline. */
        private const val TICK_MILLIS = 250L

        /** Enough to find a free station without turning the sheet into a catalogue. */
        private const val MAX_SUBSTITUTES = 5

        private const val PRIMARY_WEIGHT = 2.0
        private const val SECONDARY_WEIGHT = 1.0

        /** Loads are stored as doubles; this is "the same weight" in kg. */
        private const val LOAD_EPSILON = 0.001

        /** Enough for "1234.75". Past that the user has mistyped, not lifted. */
        private const val MAX_LOAD_DIGITS = 7
    }
}
