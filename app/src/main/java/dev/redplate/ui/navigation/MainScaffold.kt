package dev.redplate.ui.navigation

import androidx.compose.runtime.Composable
import dev.redplate.workout.SetLoggingRoute

/**
 * Top-level scaffold hosting the single active screen. No navigation graph —
 * the app is one screen (set logging) per CLAUDE.md / COACHING.md spec.
 *
 * SavedStateHandle in the ViewModel reads these keys to bootstrap; here we
 * hardcode sample values so the app launches into a valid state.
 */
@Composable
fun MainScaffold() {
    SetLoggingRoute(
        onBack = { /* single screen — no-op */ },
        onOpenGuidance = { /* no-op until guidance sheet is built */ },
    )
}
