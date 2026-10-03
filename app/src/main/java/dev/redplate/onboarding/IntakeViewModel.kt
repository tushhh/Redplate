package dev.redplate.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.BodyweightDao
import dev.redplate.data.BodyweightEntryEntity
import dev.redplate.data.DatabaseSeeder
import dev.redplate.data.RedplateDatabase
import androidx.room.withTransaction
import dev.redplate.data.EquipmentCategory
import dev.redplate.data.EquipmentDao
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.Goal
import dev.redplate.data.LoadingScheme
import dev.redplate.data.PlanSettings
import dev.redplate.data.ProfileDao
import dev.redplate.data.ProfileEntity
import dev.redplate.data.ProgramGenerator
import dev.redplate.data.SeedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntakeViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val equipmentDao: EquipmentDao,
    private val programGenerator: ProgramGenerator,
    private val seeder: DatabaseSeeder,
    private val bodyweightDao: BodyweightDao,
    private val db: RedplateDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(IntakeState())
    val state: StateFlow<IntakeState> = _state.asStateFlow()

    /** The intake's equipment step reads seeded rows, so it has to wait for the seed. */
    val seedState: StateFlow<SeedState> = seeder.state

    fun retrySeed() {
        viewModelScope.launch { seeder.retry() }
    }

    init {
        viewModelScope.launch {
            // Reading the inventory the instant this ViewModel is built raced the first-run
            // seed and could come back empty, leaving the user an inventory with nothing
            // in it and no way to tell why.
            seeder.state.first { it !is SeedState.Seeding }
            val equipment = equipmentDao.getAll()
            _state.update { state ->
                state.copy(
                    allEquipment = equipment,
                    // The seed already itemises the user's gym, so the inventory opens
                    // pre-ticked. Unticking three rows is cheap; ticking fourteen from
                    // scratch is the form everyone abandons.
                    selectedEquipmentIds = equipment
                        .filter { it.isAvailable }
                        .map { it.id }
                        .toSet(),
                )
            }
        }
    }

    // Changing an answer again forgets any preset tried on top of the old one.
    fun setGoal(goal: Goal) = _state.update {
        it.copy(goal = goal, selectedPresetId = null, presetBase = null)
    }

    fun setDaysPerWeek(days: Int) = _state.update {
        it.copy(daysPerWeek = days, selectedPresetId = null, presetBase = null)
    }

    fun setSessionMinutes(minutes: Int) = _state.update { it.copy(sessionMinutes = minutes) }

    // ── About you ──

    fun setExperience(experience: TrainingExperience) = _state.update { it.copy(experience = experience) }

    fun setReadiness(flagged: Boolean) = _state.update { it.copy(readinessFlagged = flagged) }

    fun bodyweightUp() = stepBodyweight(+BODYWEIGHT_STEP)

    fun bodyweightDown() = stepBodyweight(-BODYWEIGHT_STEP)

    /** The steppers start from a typical weight the first time, then walk half a kilo. */
    private fun stepBodyweight(delta: Double) = _state.update {
        val from = it.bodyweightKg ?: BODYWEIGHT_STEPPER_START
        it.copy(bodyweightKg = (from + delta).coerceIn(BODYWEIGHT_RANGE))
    }

    fun startBodyweightEntry() = _state.update { it.copy(bodyweightEntry = "") }

    fun cancelBodyweightEntry() = _state.update { it.copy(bodyweightEntry = null) }

    fun appendBodyweightDigit(key: Char) = _state.update { state ->
        val current = state.bodyweightEntry ?: return@update state
        val next = when {
            key.isDigit() -> current + key
            key == '.' && !current.contains('.') -> if (current.isEmpty()) "0." else "$current."
            else -> current
        }
        if (next.length > MAX_BODYWEIGHT_DIGITS) state else state.copy(bodyweightEntry = next)
    }

    fun backspaceBodyweightEntry() = _state.update { state ->
        state.copy(bodyweightEntry = state.bodyweightEntry?.dropLast(1))
    }

    fun commitBodyweightEntry() = _state.update { state ->
        val typed = state.bodyweightEntry?.toDoubleOrNull()?.takeIf { it in BODYWEIGHT_RANGE }
            ?: return@update state
        state.copy(bodyweightKg = typed, bodyweightEntry = null)
    }

    fun toggleEquipment(equipmentId: String) {
        _state.update { state ->
            val current = state.selectedEquipmentIds
            val next = if (equipmentId in current) current - equipmentId else current + equipmentId
            state.copy(selectedEquipmentIds = next)
        }
    }

    fun setDumbbellStep(step: DumbbellStep) = _state.update { it.copy(dumbbellStep = step) }

    /**
     * Presets only belong to the "give me a plan" path. Switching to picking each day
     * restores the answers a preset tried on had changed, rather than saving them.
     */
    fun setPlanChoice(choice: PlanChoice) = _state.update { state ->
        val base = state.presetBase
        if (choice == PlanChoice.I_CHOOSE && base != null) {
            state.copy(
                planChoice = choice,
                goal = base.goal,
                daysPerWeek = base.daysPerWeek,
                selectedPresetId = null,
                presetBase = null,
            )
        } else {
            state.copy(planChoice = choice)
        }
    }

    fun setEquipmentFilter(filter: EquipmentFilter) = _state.update { it.copy(equipmentFilter = filter) }

    fun setEquipmentSearch(query: String) = _state.update { it.copy(equipmentSearch = query) }

    /**
     * A preset is a structure, so choosing one sets the inputs that structure implies.
     *
     * The first card is always the user's own answers and changes nothing. This used to
     * auto-select an "Upper / Lower" card that forced four days — so a three-day user was
     * moved to four just by opening the screen — and a strength card that replaced the
     * goal without saying so. Now every other card says on its face what it changes, and
     * going back to "your answers" restores them exactly.
     */
    fun selectPreset(presetId: String) = _state.update { state ->
        val base = state.presetBase ?: PresetBase(state.goal ?: Goal.HYPERTROPHY, state.daysPerWeek)
        when (presetId) {
            PRESET_STRENGTH -> state.copy(
                selectedPresetId = presetId,
                presetBase = base,
                daysPerWeek = base.daysPerWeek,
                goal = Goal.STRENGTH,
            )

            PRESET_PPL -> state.copy(
                selectedPresetId = presetId,
                presetBase = base,
                daysPerWeek = 6,
                goal = base.goal,
            )

            else -> state.copy(
                selectedPresetId = presetId,
                presetBase = base,
                daysPerWeek = base.daysPerWeek,
                goal = base.goal,
            )
        }
    }

    /**
     * Writes the profile, applies the equipment inventory, and — when the user asked for
     * a plan — generates one. Ordering matters: [ProgramGenerator] selects exercises from
     * whatever equipment is marked available, so the inventory has to land first.
     */
    fun finishIntake(onComplete: () -> Unit) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            // A failure here used to leave the button disabled for good and the profile
            // unwritten — stuck in onboarding with no way forward. Now it says so and the
            // button comes back.
            val result = runCatching { saveIntake() }
            _state.update {
                it.copy(
                    isSaving = false,
                    saveError = result.exceptionOrNull()?.let {
                        "The plan couldn't be built. Nothing was saved — try again, and if " +
                            "it keeps failing, untick a piece of equipment and retry."
                    },
                )
            }
            if (result.isSuccess) onComplete()
        }
    }

    fun consumeSaveError() = _state.update { it.copy(saveError = null) }

    /**
     * One transaction, so the error sheet's "nothing was saved" is true: a failure leaves
     * no half-applied inventory, no orphan plan, and no first weigh-in to duplicate on retry.
     */
    private suspend fun saveIntake() {
        db.withTransaction {
            val s = _state.value
            // The same [PlanSettings] the "Your plan" screen edits, normalised by the same
            // rules. Intake and Settings write one shape of answer, not two.
            val plan = PlanSettings(
                goal = s.goal ?: Goal.HYPERTROPHY,
                daysPerWeek = s.daysPerWeek,
                sessionCeilingMinutes = s.sessionMinutes,
                trainingAgeMonths = s.experience?.months ?: 0,
            ).normalised()
            val profile = plan.applyTo(
                ProfileEntity(
                    trainingAgeMonths = s.experience?.months ?: 0,
                    daysPerWeek = plan.daysPerWeek,
                    sessionCeilingMinutes = plan.sessionCeilingMinutes,
                    goal = plan.goal,
                    bodyweightKg = s.bodyweightKg ?: 0.0,
                    readinessFlagged = s.readinessFlagged == true,
                )
            )

            // Equipment first — the generator only picks what the user can actually load.
            // The dumbbell answer is written here too. It is the entire reason 2e asks the
            // follow-up, and it was previously collected and discarded, which left the
            // engine free to prescribe loads that do not exist on the rack.
            s.allEquipment.forEach { eq ->
                val available = eq.id in s.selectedEquipmentIds
                val loads = if (
                    eq.category == EquipmentCategory.DUMBBELL &&
                    eq.loadingScheme == LoadingScheme.FIXED_INCREMENT
                ) {
                    s.dumbbellStep.ladder(eq.availableLoads)
                } else {
                    eq.availableLoads
                }
                equipmentDao.update(eq.copy(isAvailable = available, availableLoads = loads))
            }

            if (s.planChoice == PlanChoice.GIVE_ME_A_PLAN) {
                programGenerator.generate(profile)
            }

            // The answer to "what do you weigh?" is the first point on the trend.
            s.bodyweightKg?.let {
                bodyweightDao.insert(
                    BodyweightEntryEntity(measuredAt = System.currentTimeMillis(), weightKg = it),
                )
            }

            // Written last: the profile row is what MainScaffold watches to leave intake,
            // so anything the first screen needs must already exist when it appears.
            profileDao.upsert(profile)
        }
    }

    private companion object {
        const val BODYWEIGHT_STEP = 0.5
        const val BODYWEIGHT_STEPPER_START = 80.0
        val BODYWEIGHT_RANGE = 30.0..300.0
        const val MAX_BODYWEIGHT_DIGITS = 5
    }
}

data class IntakeState(
    val goal: Goal? = null,
    val daysPerWeek: Int = 4,
    val sessionMinutes: Int = 60,
    /** Null until answered: a default here is how every user became an 80 kg beginner. */
    val experience: TrainingExperience? = null,
    val bodyweightKg: Double? = null,
    /** Digits on the bodyweight keypad, or null when it is closed. */
    val bodyweightEntry: String? = null,
    /** The one-time readiness screen (COACHING.md §1). Null until answered. */
    val readinessFlagged: Boolean? = null,
    val allEquipment: List<EquipmentEntity> = emptyList(),
    val selectedEquipmentIds: Set<String> = emptySet(),
    /**
     * Opens on the rack as itemised. The seed transcribes the real gym — 10 to 40 kg in
     * 2 kg steps — and defaulting to "2.5 kg steps" silently rewrote it into dumbbells
     * that are not on the rack.
     */
    val dumbbellStep: DumbbellStep = DumbbellStep.AS_RACKED,
    val planChoice: PlanChoice = PlanChoice.GIVE_ME_A_PLAN,
    val equipmentFilter: EquipmentFilter = EquipmentFilter.ALL,
    val equipmentSearch: String = "",
    val selectedPresetId: String? = null,
    /** The answers as given, before any preset was tried on top of them. */
    val presetBase: PresetBase? = null,
    /** Guards the finish button: generating a plan writes a lot of rows. */
    val isSaving: Boolean = false,
    /** Said plainly when building the plan failed; the button is usable again. */
    val saveError: String? = null,
) {
    val selectedEquipmentCount: Int get() = selectedEquipmentIds.size

    /** What "ALL n" counts — the whole inventory, not whatever survived the filter. */
    val totalEquipmentCount: Int get() = allEquipment.size

    val filteredEquipment: List<EquipmentEntity> get() {
        val byCategory = when (equipmentFilter) {
            EquipmentFilter.ALL -> allEquipment
            EquipmentFilter.BAR -> allEquipment.filter { it.category == EquipmentCategory.BARBELL }
            EquipmentFilter.MACHINE -> allEquipment.filter { it.category == EquipmentCategory.MACHINE }
            EquipmentFilter.CABLE -> allEquipment.filter { it.category == EquipmentCategory.CABLE }
        }
        val query = equipmentSearch.trim()
        return if (query.isEmpty()) {
            byCategory
        } else {
            byCategory.filter { it.displayName.contains(query, ignoreCase = true) }
        }
    }

    /**
     * The live "THAT MEANS" line on 2d, in parts so the screen can bring the sets phrase
     * forward in full-strength ink without pattern-matching its own sentence.
     */
    val consequence: ScheduleConsequence get() {
        val split = when (daysPerWeek) {
            2 -> "Full body, both days"
            3 -> "Full body, three sessions"
            4 -> "Upper / Lower, twice each"
            5 -> "Upper / Lower / Push / Pull / Legs"
            6 -> "Push / Pull / Legs, twice each"
            else -> "Full body"
        }
        val setsPerSession = when {
            sessionMinutes <= 30 -> "10–12"
            sessionMinutes <= 45 -> "14–16"
            sessionMinutes <= 60 -> "18–22"
            sessionMinutes <= 75 -> "22–26"
            else -> "26–30"
        }
        val tail = if (daysPerWeek >= 4) {
            "every muscle hit twice a week — which is the point where progress actually shows up."
        } else {
            "every muscle hit once or twice a week — enough to grow, with room to add a day later."
        }
        return ScheduleConsequence(
            split = split,
            setsPhrase = "$setsPerSession sets a session",
            tail = tail,
        )
    }
}

data class PresetBase(val goal: Goal, val daysPerWeek: Int)

/** The three parts of 2d's consequence sentence: "$split. Around $setsPhrase, $tail" */
data class ScheduleConsequence(
    val split: String,
    val setsPhrase: String,
    val tail: String,
)

/**
 * The 2e follow-up: which dumbbells actually exist on the rack.
 *
 * [AS_RACKED] keeps whatever the seed itemised from the real gym, rather than pretending
 * a per-dumbbell editor exists. It is the honest third answer and it loses nothing.
 */
enum class DumbbellStep(val label: String, val caption: String, val kgStep: Double?) {
    TWO_POINT_FIVE("2.5 kg", "steps", 2.5),
    FIVE("5 kg", "steps", 5.0),
    AS_RACKED("Pick", "each", null);

    /** Rebuilds the ladder between the rack's own floor and ceiling. */
    fun ladder(existing: List<Double>): List<Double> {
        val step = kgStep ?: return existing
        val floor = existing.minOrNull() ?: step
        val ceiling = existing.maxOrNull() ?: DEFAULT_CEILING_KG
        return generateSequence(floor) { it + step }.takeWhile { it <= ceiling }.toList()
    }

    private companion object {
        const val DEFAULT_CEILING_KG = 40.0
    }
}

enum class PlanChoice {
    GIVE_ME_A_PLAN,
    I_CHOOSE,
}

const val PRESET_UPPER_LOWER = "upper_lower"
const val PRESET_ANSWERS = "answers"
const val PRESET_STRENGTH = "strength"
const val PRESET_PPL = "ppl"

enum class EquipmentFilter(val label: String) {
    ALL("ALL"),
    BAR("BAR"),
    MACHINE("MACHINE"),
    CABLE("CABLE"),
}
