package dev.redplate.workout

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Distinct vibration patterns per CLAUDE.md §4 — the user often isn't looking at
 * the screen, so set-logged, PR, and rest-complete must feel different in the hand.
 * minSdk 36 → VibratorManager is always present; no legacy branch.
 */
class WorkoutHaptics(context: Context) {

    private val vibrator: Vibrator =
        (context.applicationContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator

    /** A single crisp tick — "logged, move on." */
    fun setLogged() {
        vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** An escalating triple — unmistakably a celebration, not a routine log. */
    fun prHit() {
        val timings = longArrayOf(0, 55, 70, 55, 70, 150)
        val amplitudes = intArrayOf(0, 170, 0, 210, 0, 255)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Two firm buzzes — "rest is over, load the bar." */
    fun restComplete() {
        val timings = longArrayOf(0, 130, 90, 130)
        val amplitudes = intArrayOf(0, 180, 0, 230)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }
}
