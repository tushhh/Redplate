package dev.redplate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import dev.redplate.R

/**
 * Fonts are BUNDLED in res/font. Do not use the Google Fonts Compose provider —
 * that is a network call, and this app must work identically in airplane mode.
 *
 * Download from https://github.com/IBM/plex (OFL 1.1) and place in res/font:
 *   ibm_plex_sans_condensed_medium.ttf
 *   ibm_plex_sans_condensed_semibold.ttf
 *   ibm_plex_sans_regular.ttf
 *   ibm_plex_sans_medium.ttf
 *   ibm_plex_mono_regular.ttf
 *   ibm_plex_sans_semibold.ttf
 *   ibm_plex_mono_medium.ttf
 */
val PlexCondensed = FontFamily(
    Font(R.font.ibm_plex_sans_condensed_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_condensed_semibold, FontWeight.SemiBold)
)

val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold)
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium)
)

object RedplateType {

    /** Rest countdown. Read from the floor, at arm's length, out of breath. */
    val timer = TextStyle(
        fontFamily = PlexCondensed,
        fontWeight = FontWeight.Medium,
        fontSize = 112.sp,
        lineHeight = 112.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = "tnum",
        textAlign = TextAlign.Center
    )

    /** Working load. The single largest thing on the set screen after the timer. */
    val load = TextStyle(
        fontFamily = PlexCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        letterSpacing = (-0.015).em,
        fontFeatureSettings = "tnum"
    )

    /** Rep counts, set counters, RIR. */
    val figure = TextStyle(
        fontFamily = PlexCondensed,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 34.sp,
        fontFeatureSettings = "tnum"
    )

    val exerciseName = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp
    )

    val body = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )

    /** Eyebrow labels: LOAD, REPS, RIR, REST. Always uppercase, always inkMuted. */
    val label = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.12.em
    )

    /** Coach headlines: "Push day. About an hour." */
    val headline = TextStyle(
        fontFamily = PlexCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.015).em
    )

    /** Section titles: session names, screen headers. */
    val title = TextStyle(
        fontFamily = PlexCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    )

    /** Larger body text for coach descriptions. */
    val bodyLarge = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp
    )

    /** Button labels and card titles that need emphasis. */
    val action = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    )

    /** Mono eyebrow: "FRIDAY MORNING · WEEK 3 OF 5". Always uppercase. */
    val mono = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.em,
        fontFeatureSettings = "tnum"
    )

    /** Set history rows and export previews — alignment matters more than warmth. */
    val data = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    )
}

val RedplateTypography = Typography(
    displayLarge = RedplateType.timer,
    displayMedium = RedplateType.load,
    headlineLarge = RedplateType.headline,
    headlineMedium = RedplateType.figure,
    titleLarge = RedplateType.title,
    titleMedium = RedplateType.exerciseName,
    bodyLarge = RedplateType.bodyLarge,
    bodyMedium = RedplateType.body,
    labelMedium = RedplateType.action,
    labelSmall = RedplateType.label,
)