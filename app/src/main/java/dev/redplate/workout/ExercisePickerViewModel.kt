package dev.redplate.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.EquipmentAvailability
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.carriesLoad
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.MediaResolver
import dev.redplate.data.MuscleGroup
import dev.redplate.data.ProfileDao
import dev.redplate.data.ProgramGenerator
import dev.redplate.data.VolumeLandmarkEntity
import dev.redplate.data.VolumeLandmarks
import dev.redplate.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where the picker is. The design's flow is map → generated session → train, so the
 * exercise browser is a detour off the session rather than the main path (3d, 8c).
 */
enum class PickerPhase { MUSCLES, GENERATED, BROWSING }

/**
 * Why the browser is open. This is what makes 8c a real screen rather than a list: the
 * same grid either replaces a row, appends one, or opens a freestyle session, and the
 * primary bar says which.
 */
sealed interface BrowseIntent {
    /** Replace one slot of the generated session. */
    data class Swap(val slotId: Long, val currentExerciseId: String) : BrowseIntent

    /** Append a slot to the generated session. */
    data object Add : BrowseIntent

    /** No session built yet — reached by searching from the map. */
    data object Freestyle : BrowseIntent
}

@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val profileDao: ProfileDao,
    private val programGenerator: ProgramGenerator,
    private val savedState: SavedStateHandle,
    private val mediaResolver: MediaResolver,
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
        if (_phase.value == PickerPhase.BROWSING) refreshBrowser()
    }

    /**
     * Weekly trained volume per muscle, shading the body map against each muscle's own
     * landmarks — seeing what is undertrained *while* choosing what to train is the whole
     * reason the map exists.
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

    // ── Session management ────────────────────────────────────────────────────

    private var activeSessionId: Long
        get() = savedState.get<Long>(KEY_SESSION_ID) ?: 0L
        set(value) { savedState[KEY_SESSION_ID] = value }

    // ── Generated session (design 3d) ─────────────────────────────────────────

    private val _generated = MutableStateFlow(GeneratedSessionState())
    val generated: StateFlow<GeneratedSessionState> = _generated.asStateFlow()

    private var generatedTemplateId: Long
        get() = savedState.get<Long>(KEY_TEMPLATE_ID) ?: 0L
        set(value) { savedState[KEY_TEMPLATE_ID] = value }

    /**
     * Builds a session from the picked muscles and shows it for inspection before the
     * first set. The plan is stored as a real template, so set logging reads the same
     * prescription and walks the same running order as a programmed day.
     */
    fun buildSession() {
        val muscles = _pickedMuscles.value
        if (muscles.isEmpty()) return

        viewModelScope.launch {
            val profile = profileDao.get() ?: return@launch
            _phase.value = PickerPhase.GENERATED
            _generated.value = GeneratedSessionState(isLoading = true)

            // Rebuilding replaces the last plan rather than stacking beside it. Anything
            // already trained against is left alone by the repository.
            if (activeSessionId == 0L) repo.discardUnusedTemplate(generatedTemplateId)

            val templateId = runCatching {
                programGenerator.generateAdHocTemplate(muscles, profile)
            }.getOrNull()

            if (templateId == null) {
                generatedTemplateId = 0L
                _phase.value = PickerPhase.MUSCLES
                return@launch
            }
            generatedTemplateId = templateId
            _generated.value = describeGenerated(templateId, muscles, profile.sessionCeilingMinutes)
        }
    }

    private suspend fun describeGenerated(
        templateId: Long,
        muscles: Set<MuscleGroup>,
        ceilingMinutes: Int,
    ): GeneratedSessionState {
        val slots = repo.getSlotsForTemplate(templateId)
        val weekly = repo.weeklyHardSetsPerMuscle()
        val totalSets = slots.sumOf { it.targetSets }
        val minutes = totalSets * MINUTES_PER_SET

        val rows = slots.map { slot ->
            val exercise = repo.getExercise(slot.exerciseId)
            val equipment = exercise?.let { repo.getPrimaryEquipment(it) }
            val load = slot.workingLoadKg ?: equipment?.barWeightKg
            GeneratedSlotRow(
                slotId = slot.id,
                orderIndex = slot.orderIndex + 1,
                exerciseId = slot.exerciseId,
                name = exercise?.name ?: slot.exerciseId,
                prescription = buildString {
                    append("${slot.targetSets} × ${slot.repRangeLow}–${slot.repRangeHigh}")
                    if (load != null) append(" · ${formatKg(load)} KG")
                    append(" · ${slot.targetRir} RIR")
                },
                reason = exercise?.let { reasonFor(it, weekly) },
            )
        }

        return GeneratedSessionState(
            eyebrow = muscles.joinToString(" + ") { it.displayName.uppercase() } + " · YOUR PICK",
            headline = "$totalSets sets, $minutes minutes.",
            coachBody = "Compounds first while you're fresh, isolation after. " +
                "Tap any row to swap it.",
            rows = rows,
            minutesLeft = (ceilingMinutes - minutes).coerceAtLeast(0),
            isLoading = false,
        )
    }

    /** Recomputes 3d after the browser changed a row. */
    private fun refreshGenerated() {
        viewModelScope.launch {
            val profile = profileDao.get() ?: return@launch
            _generated.value = describeGenerated(
                generatedTemplateId,
                _pickedMuscles.value,
                profile.sessionCeilingMinutes,
            )
        }
    }

    /** Why this lift is in the session, in one line, from the week's actual volume. */
    private fun reasonFor(
        exercise: ExerciseEntity,
        weekly: Map<MuscleGroup, Double>,
    ): String {
        val muscle = exercise.primaryMuscle
        val done = (weekly[muscle] ?: 0.0).toInt()
        val target = VolumeLandmarks.forMuscle(muscle).mavHigh
        val gap = target - done
        return when {
            gap > 0 && exercise.isCompound ->
                "${muscle.displayName} is $gap set${if (gap == 1) "" else "s"} under target this week."

            gap > 0 -> "Cheap sets — low fatigue with ${muscle.displayName.lowercase()} already loaded."
            else -> "${muscle.displayName} is at its cap — this one is kept short."
        }
    }

    /** Opens a session against the generated template and hands back its id. */
    suspend fun startGeneratedSession(): Pair<Long, String>? {
        val slots = repo.getSlotsForTemplate(generatedTemplateId)
        val first = slots.firstOrNull() ?: return null
        val sessionId = repo.startTemplatedSession(generatedTemplateId, System.currentTimeMillis())
        activeSessionId = sessionId
        return sessionId to first.exerciseId
    }

    fun backToMuscles() {
        _phase.value = PickerPhase.MUSCLES
    }

    // ── Exercise browser (design 8c) ──────────────────────────────────────────

    private val _browser = MutableStateFlow(BrowserState())
    val browser: StateFlow<BrowserState> = _browser.asStateFlow()

    private var browseIntent: BrowseIntent = BrowseIntent.Freestyle

    /**
     * Where leaving the browser goes. Tracked rather than inferred: a user who built a
     * session, went back to the map and then searched must land back on the map, not on
     * a session they had already stepped away from.
     */
    private var browseReturnPhase: PickerPhase = PickerPhase.MUSCLES

    /** Tapping a row on 3d: the browser opens scoped to that slot's muscle. */
    fun browseToSwap(slotId: Long, exerciseId: String) {
        browseIntent = BrowseIntent.Swap(slotId, exerciseId)
        openBrowser(defaultFilters = setOf(BrowseFilter.MY_KIT, BrowseFilter.MUSCLE))
    }

    /** "Add an exercise" on 3d. */
    fun browseToAdd() {
        browseIntent = BrowseIntent.Add
        openBrowser(defaultFilters = setOf(BrowseFilter.MY_KIT))
    }

    /** Typing in the map's search field — there is no session yet to add to. */
    fun browseFreestyle() {
        if (_phase.value == PickerPhase.BROWSING) return
        browseIntent = BrowseIntent.Freestyle
        openBrowser(defaultFilters = setOf(BrowseFilter.MY_KIT))
    }

    private fun openBrowser(defaultFilters: Set<BrowseFilter>) {
        browseReturnPhase = _phase.value
        _phase.value = PickerPhase.BROWSING
        _browser.value = BrowserState(activeFilters = defaultFilters, isLoading = true)
        refreshBrowser()
    }

    fun toggleBrowseFilter(filter: BrowseFilter) {
        val current = _browser.value.activeFilters
        _browser.value = _browser.value.copy(
            activeFilters = if (filter in current) current - filter else current + filter,
        )
        refreshBrowser()
    }

    fun selectBrowsedExercise(id: String) {
        val state = _browser.value
        val next = if (state.selectedId == id) null else id
        _browser.value = state.copy(
            selectedId = next,
            selectedName = next?.let { picked ->
                state.sections.firstNotNullOfOrNull { section ->
                    section.items.firstOrNull { it.id == picked }?.name
                }
            },
        )
    }

    fun leaveBrowser() {
        _searchQuery.value = ""
        _phase.value = browseReturnPhase
    }

    /**
     * Commits the browser's pick.
     *
     * Swapping and adding edit the stored template and drop the user back on 3d, so the
     * session they inspected is the session they start. Only the freestyle path — reached
     * by searching before anything is built — opens a session directly, and it returns the
     * ids for navigation.
     */
    suspend fun commitBrowse(): Pair<Long, String>? {
        val exerciseId = _browser.value.selectedId ?: return null
        val profile = profileDao.get()

        when (val intent = browseIntent) {
            is BrowseIntent.Swap -> {
                if (profile != null) {
                    programGenerator.replaceSlotExercise(intent.slotId, exerciseId, profile)
                }
                _searchQuery.value = ""
                _phase.value = PickerPhase.GENERATED
                refreshGenerated()
                return null
            }

            BrowseIntent.Add -> {
                if (profile != null && generatedTemplateId > 0L) {
                    programGenerator.appendSlot(generatedTemplateId, exerciseId, profile)
                }
                _searchQuery.value = ""
                _phase.value = PickerPhase.GENERATED
                refreshGenerated()
                return null
            }

            BrowseIntent.Freestyle -> {
                val sessionId = getOrCreateSession()
                return sessionId to exerciseId
            }
        }
    }

    /**
     * Rebuilds the browser's tiers. Everything is derived here rather than stored, so a
     * filter tap and a keystroke go through exactly the same path.
     */
    private fun refreshBrowser() {
        viewModelScope.launch {
            val all = repo.allExercises().filter { !it.isExcluded }
            val equipment = repo.equipmentById()
            val setCounts = repo.workingSetCountsByExercise()
            val slots = if (generatedTemplateId > 0L) {
                repo.getSlotsForTemplate(generatedTemplateId)
            } else {
                emptyList()
            }
            val inSession = slots.map { it.exerciseId }.toSet()

            val intent = browseIntent
            val scopeMuscle = when (intent) {
                is BrowseIntent.Swap -> all.firstOrNull { it.id == intent.currentExerciseId }
                    ?.primaryMuscle

                else -> _pickedMuscles.value.firstOrNull()
            }

            val filters = _browser.value.activeFilters
            val query = _searchQuery.value.trim()
            val visible = all.filter { exercise ->
                val matchesQuery = query.isEmpty() ||
                    exercise.name.contains(query, ignoreCase = true)
                val matchesKit = BrowseFilter.MY_KIT !in filters || isAvailable(exercise, equipment)
                val matchesMuscle = BrowseFilter.MUSCLE !in filters || scopeMuscle == null ||
                    exercise.primaryMuscle == scopeMuscle
                val matchesCompound = BrowseFilter.COMPOUND !in filters || exercise.isCompound
                matchesQuery && matchesKit && matchesMuscle && matchesCompound
            }

            val sections = buildSections(visible, inSession, setCounts, equipment)
            val selectedId = _browser.value.selectedId?.takeIf { picked ->
                sections.any { section -> section.items.any { it.id == picked } }
            }

            _browser.value = BrowserState(
                title = browserTitle(intent, all),
                subtitle = browserSubtitle(setCounts.size, all.size),
                sections = sections,
                muscleFilterLabel = scopeMuscle?.displayName?.uppercase() ?: "ONE MUSCLE",
                activeFilters = filters,
                archiveSize = all.size,
                selectedId = selectedId,
                selectedName = selectedId?.let { picked -> all.firstOrNull { it.id == picked }?.name },
                confirmVerb = when (intent) {
                    is BrowseIntent.Swap -> "Swap in"
                    BrowseIntent.Add -> "Add"
                    BrowseIntent.Freestyle -> "Start with"
                },
                isLoading = false,
            )
        }
    }

    private fun buildSections(
        visible: List<ExerciseEntity>,
        inSession: Set<String>,
        setCounts: Map<String, Int>,
        equipment: Map<String, EquipmentEntity>,
    ): List<BrowserSection> {
        fun card(exercise: ExerciseEntity) = BrowserExercise(
            id = exercise.id,
            name = exercise.name,
            tag = tagFor(exercise, equipment, setCounts[exercise.id] ?: 0),
            primaryMuscle = exercise.primaryMuscle,
            startImageUri = mediaResolver.startImage(exercise.id),
            endImageUri = mediaResolver.endImage(exercise.id),
            isAvailable = isAvailable(exercise, equipment),
        )

        val session = visible.filter { it.id in inSession }
        val rest = visible.filterNot { it.id in inSession }
        val trained = rest
            .filter { (setCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { setCounts[it.id] ?: 0 }
        val trainedIds = trained.map { it.id }.toSet()
        val everythingElse = rest
            .filterNot { it.id in trainedIds }
            .sortedBy { it.name }

        return listOfNotNull(
            BrowserSection("IN THIS SESSION", session.map(::card)).takeIf { session.isNotEmpty() },
            BrowserSection("YOU TRAIN THESE MOST", trained.map(::card))
                .takeIf { trained.isNotEmpty() },
            BrowserSection("EVERYTHING ELSE", everythingElse.map(::card))
                .takeIf { everythingElse.isNotEmpty() },
        )
    }

    /** "DUMBBELL · 34 SETS" — the kit first, then how much of it you have actually done. */
    /**
     * "HALF RACK · BARBELL · 34 SETS" — the machine to walk to, then the thing that
     * supplies the load, then how much you have actually done it.
     */
    private fun tagFor(
        exercise: ExerciseEntity,
        equipment: Map<String, EquipmentEntity>,
        sets: Int,
    ): String {
        val declared = exercise.requiredEquipmentIds.mapNotNull { equipment[it] }
        val kit = if (declared.isEmpty()) {
            "BODYWEIGHT"
        } else {
            listOfNotNull(
                declared.firstOrNull { !it.carriesLoad }?.displayName,
                declared.firstOrNull { it.carriesLoad }?.displayName,
            ).distinct().joinToString(" · ").uppercase()
        }
        return if (sets > 0) "$kit · $sets SETS" else kit
    }

    /**
     * Shared with the generator and the repository. This used to be its own `any` check,
     * which read a barbell squat as available in a gym with a rack and no barbell — and
     * became actively wrong once lifts started naming both the station and the load.
     */
    private fun isAvailable(
        exercise: ExerciseEntity,
        equipment: Map<String, EquipmentEntity>,
    ): Boolean = EquipmentAvailability.canPerform(
        exercise,
        EquipmentAvailability.availableIds(equipment.values.toList()),
    )

    private suspend fun browserTitle(intent: BrowseIntent, all: List<ExerciseEntity>): String =
        when (intent) {
            is BrowseIntent.Swap -> {
                val name = all.firstOrNull { it.id == intent.currentExerciseId }?.name
                if (name != null) "Instead of $name" else "Swap this exercise"
            }

            BrowseIntent.Add -> {
                val label = repo.getTemplateLabel(generatedTemplateId)
                if (label != null) "Add to $label" else "Add an exercise"
            }

            BrowseIntent.Freestyle -> "Pick an exercise"
        }

    private fun browserSubtitle(trainedCount: Int, archiveSize: Int): String =
        if (trainedCount > 0) {
            "Yours first · $archiveSize in the archive"
        } else {
            "$archiveSize in the archive · your own move to the top once you log them"
        }

    private fun formatKg(kg: Double): String =
        if (kg % 1.0 == 0.0) kg.toInt().toString() else "%.1f".format(kg)

    suspend fun getOrCreateSession(): Long {
        val existing = activeSessionId
        if (existing > 0L) return existing
        val id = repo.startFreestyleSession(System.currentTimeMillis())
        activeSessionId = id
        return id
    }

    private companion object {
        const val MINUTES_PER_SET = 3
        const val KEY_SESSION_ID = "activeSessionId"
        const val KEY_TEMPLATE_ID = "generatedTemplateId"
    }
}
