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

    // ── Readout (signature element) ──
    val loadKg: Double = 20.0,
    val isPlateLoaded: Boolean = false,
    val plateLoad: PlateMath.PlateLoad? = null,
    val isExactLoad: Boolean = true,          // false when plates can't hit the target exactly

    // ── Editable inputs (control zone) ──
    val reps: Int = 0,
    val rir: Int? = null,
    val isWarmup: Boolean = false,

    // ── Rest timer ──
    val rest: RestState = RestState.Idle,
) {
    val canCompleteSet: Boolean get() = !isLoading && reps >= 1
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
