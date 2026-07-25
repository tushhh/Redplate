package dev.redplate.workout

import dev.redplate.data.PlateMath

/**
 * Everything the set logging screen renders. Hoisted out of the ViewModel so the
 * screen composable stays stateless and every state below is directly @Preview-able.
 */
data class SetLoggingUiState(
    val isLoading: Boolean = true,

    // ── Read-only prescription (top zone) ──
    val exerciseName: String = "",
    val supersetLabel: String? = null,        // e.g. "SUPERSET A"; null when not supersetted
    val hasGuidance: Boolean = false,
    val setNumber: Int = 1,                   // 1-based working-set counter
    val targetSets: Int = 0,
    val repRangeLow: Int = 0,
    val repRangeHigh: Int = 0,
    val targetRir: Int? = null,
    val previousSets: List<PreviousSetLine> = emptyList(),
    val loggedSets: List<LoggedSetLine> = emptyList(),

    /** Subtitle under exercise name: "SET 3 OF 4 · 6–10 REPS · 2 LEFT" */
    val headerSubtitle: String = "",

    /** Coach reasoning line above the load: "Same weight as your last set —" */
    val coachReasoningLine: String = "",

    // ── Readout (signature element) ──
    val loadKg: Double = 20.0,
    val isPlateLoaded: Boolean = false,
    val plateLoad: PlateMath.PlateLoad? = null,
    val isExactLoad: Boolean = true,          // false when plates can't hit the target exactly

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
) {
    val canCompleteSet: Boolean get() = !isLoading && reps >= 1
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
    FAILED("Failed the rep", -1),
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
