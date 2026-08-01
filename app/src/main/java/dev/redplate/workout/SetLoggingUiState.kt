package dev.redplate.workout

import dev.redplate.data.LoadUnit
import dev.redplate.data.MuscleGroup
import dev.redplate.data.PlateMath

/**
 * Everything the set logging screen renders. Hoisted out of the ViewModel so the
 * screen composable stays stateless and every state below is directly @Preview-able.
 */
data class SetLoggingUiState(
    val isLoading: Boolean = true,

    // ── Read-only prescription (top zone) ──
    val exerciseId: String = "",
    val exerciseName: String = "",
    val primaryMuscle: MuscleGroup = MuscleGroup.CHEST,
    /** Start position still. Half of the movement window. */
    val imageUri: String? = null,
    /** End position still. The window cross-fades to this; null holds the start frame. */
    val endImageUri: String? = null,
    val supersetLabel: String? = null,        // e.g. "SUPERSET A"; null when not supersetted
    val hasGuidance: Boolean = false,
    /** "EXERCISE 2 OF 6"; empty on a freestyle session with no running order. */
    val exercisePositionLabel: String = "",
    /** Name of the next lift in the session, or null when this is the last one. */
    val nextExerciseName: String? = null,
    /** Equipment-valid alternatives, offered in the guidance sheet. */
    val substitutes: List<SubstituteOption> = emptyList(),
    /** Primary muscle first, then secondaries — the guidance sheet's tag row. */
    val guidanceMuscleTags: List<String> = emptyList(),
    /**
     * Step-by-step text, when the exercise carries any. The curated seed has none, so
     * this is usually empty and the sheet leans on the stills, the muscles worked and
     * the substitutes instead. Better an honest gap than invented lifting cues.
     */
    val instructionSteps: List<String> = emptyList(),
    val setNumber: Int = 1,                   // 1-based working-set counter
    val targetSets: Int = 0,
    val repRangeLow: Int = 0,
    val repRangeHigh: Int = 0,
    val targetRir: Int? = null,
    val previousSets: List<PreviousSetLine> = emptyList(),
    val loggedSets: List<LoggedSetLine> = emptyList(),

    /** Subtitle under exercise name while logging: "SET 3 OF 4 · 6–10 REPS · 2 LEFT" */
    val headerSubtitle: String = "",

    /** Subtitle while resting: "SET 3 LOGGED · 1 TO GO" */
    val restSubtitle: String = "",

    /** Coach reasoning line above the load: "Same weight as your last set —" */
    val coachReasoningLine: String = "",

    // ── Readout (signature element) ──
    val loadKg: Double = 20.0,
    val isPlateLoaded: Boolean = false,
    val plateLoad: PlateMath.PlateLoad? = null,
    val isExactLoad: Boolean = true,          // false when plates can't hit the target exactly
    /**
     * What the equipment's numbers are read in — "KG", or "LEVEL" for a machine marked in
     * resistance rather than mass. The readout used to say KG unconditionally, which is a
     * lie on any machine that prints no weight.
     */
    val loadUnitLabel: String = LoadUnit.KILOGRAMS.label,
    /** Whole numbers only, so the keypad hides the decimal point on a level-marked stack. */
    val loadIsWholeNumber: Boolean = false,
    /**
     * True when the load describes one implement — a dumbbell in each hand. The readout
     * says EACH so the number cannot be read as a combined figure.
     */
    val loadIsPerLimb: Boolean = false,
    /**
     * True on a counterweighted machine, where the number is help taken off you rather than
     * load added. Everything about this reads backwards — a bigger number is an easier set —
     * so the readout has to say so out loud, or the user will chase the wrong direction.
     */
    val loadIsAssistance: Boolean = false,
    /** "Half Rack · Barbell" — where the lift is done. */
    val stationLabel: String? = null,

    // ── Direct load entry ──
    /**
     * Digits typed into the keypad, or null when it is closed. Held as text rather than a
     * number so a half-typed "10." is representable and nothing is rounded until Done.
     */
    val loadEntry: String? = null,

    // ── Editable inputs (control zone) ──
    val reps: Int = 0,
    val rir: Int? = null,
    val isWarmup: Boolean = false,
    val difficulty: Difficulty? = null,

    // ── Rest timer ──
    val rest: RestState = RestState.Idle,

    // ── Rest screen extras ──
    val prBadgeText: String? = null,          // "Best set you've done at 102.5 kg."
    val restCoachText: String = "",           // Coach advice during rest
    val restPrimaryLabel: String = "",        // "I'm ready — set 4"
    val restPrimaryAction: RestAction = RestAction.NEXT_SET,
) {
    val canCompleteSet: Boolean get() = !isLoading && reps >= 1

    val isEnteringLoad: Boolean get() = loadEntry != null

    /** What the big readout shows: what is being typed, or the load as it stands. */
    val loadDisplay: String
        get() = loadEntry ?: formatLoad(loadKg)

    /** Blocks Done on an empty or malformed entry rather than committing a zero. */
    val canCommitLoadEntry: Boolean
        get() = loadEntry?.toDoubleOrNull()?.let { it >= 0.0 } == true
}

/** Trailing ".0" is noise on a readout that has to be legible from two metres. */
fun formatLoad(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(java.util.Locale.getDefault(), "%.2f", value).trimEnd('0').trimEnd('.', ',')

/**
 * What the rest screen's one primary button does. The label and the behaviour are
 * derived from the same value, so they cannot drift apart.
 */
enum class RestAction {
    /** More sets left on this exercise: end the rest and log the next one. */
    NEXT_SET,

    /** This lift is done: move to the next one in the session's running order. */
    NEXT_EXERCISE,

    /** Last lift of the session: stamp the finish time and show the summary. */
    FINISH_SESSION,
}

/**
 * Plain-language difficulty chips that map to RIR values.
 * Design: 2 rows of 3 chips.
 */
enum class Difficulty(val label: String, val rir: Int) {
    EASY("Easy", 4),
    THREE_LEFT("3 more in me", 3),
    TWO_LEFT("2 more in me", 2),
    ONE_LEFT("1 more, maybe", 1),
    ALL_OUT("All I had", 0),
    FAILED("Failed the rep", -1);

    /**
     * The chip face, broken where the design breaks it so all six read as one grid
     * rather than three wide chips and three narrow ones (design 8a).
     */
    val chipLabel: String
        get() = when (this) {
            EASY -> "Easy"
            THREE_LEFT -> "3 more\nin me"
            TWO_LEFT -> "2 more\nin me"
            ONE_LEFT -> "1 more,\nmaybe"
            ALL_OUT -> "All I\nhad"
            FAILED -> "Failed\nthe rep"
        }
}

sealed interface RestState {
    data object Idle : RestState
    data class Running(val remainingSeconds: Int, val totalSeconds: Int) : RestState
}

data class PreviousSetLine(val loadKg: Double, val reps: Int, val rir: Int?)

data class LoggedSetLine(
    val setIndex: Int,
    val loadKg: Double,
    val reps: Int,
    val rir: Int?,
    val isWarmup: Boolean,
    val isPr: Boolean,
)

/** One-shot side effects the screen turns into haptics. */
sealed interface WorkoutEvent {
    data object SetLogged : WorkoutEvent
    data object PrHit : WorkoutEvent
    data object RestComplete : WorkoutEvent
}
