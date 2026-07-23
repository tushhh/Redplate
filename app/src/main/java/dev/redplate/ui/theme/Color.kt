package dev.redplate.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Palette per CLAUDE.md §3. Instrument-grade, not consumer-app.
 * True black ground: AMOLED power saving, minimal glare under gym lighting,
 * and maximum contrast for numerals read at arm's length.
 */
@Immutable
data class RedplateColors(
    val ground: Color = Color(0xFF000000),
    val surface: Color = Color(0xFF121417),
    val line: Color = Color(0xFF2A2F36),
    val ink: Color = Color(0xFFF5F5F0),
    val inkMuted: Color = Color(0xFF8B939E),
    /** The single warm accent. One use per screen: the live set, or the running timer. */
    val live: Color = Color(0xFFFF5C1A)
)

/**
 * IPF/IWF calibrated plate colours. These are functional, not decorative —
 * a red disc on screen means the same thing as a red disc on the bar.
 * Used ONLY inside the plate stack.
 */
object PlateColor {
    val KG_25 = Color(0xFFC8102E)
    val KG_20 = Color(0xFF0057B8)
    val KG_15 = Color(0xFFFFD100)
    val KG_10 = Color(0xFF00843D)
    val KG_5 = Color(0xFFF2F2F2)
    val KG_2_5 = Color(0xFF1A1A1A)
    val KG_1_25 = Color(0xFFC0C0C0)

    fun forPlate(kg: Double): Color = when (kg) {
        25.0 -> KG_25
        20.0 -> KG_20
        15.0 -> KG_15
        10.0 -> KG_10
        5.0 -> KG_5
        2.5 -> KG_2_5
        else -> KG_1_25
    }

    /** 2.5 kg and 5 kg discs need an outline to separate from the ground / surface. */
    fun needsOutline(kg: Double): Boolean = kg == 2.5 || kg == 5.0
}

/**
 * Semantic state colours. Never encode meaning in hue alone —
 * every use of these must be paired with an icon or a text label.
 */
object StateColor {
    val pr = Color(0xFFFFD100)          // matches the 15 kg plate; PRs read as "yellow"
    val regression = Color(0xFF8B939E)  // muted, not alarming — a bad set is information
    val deload = Color(0xFF0057B8)
}