package dev.redplate.workout

import dev.redplate.data.MuscleGroup

// ── Volume state ─────────────────────────────────────────────────────────────

enum class VolumeLevel { NONE, BELOW_MEV, MEV_TO_MAV, APPROACHING_MRV, AT_MRV, PICKED }

// ── Shape / zone types ───────────────────────────────────────────────────────

/**
 * A rounded-rectangle muscle shape from the SVG.
 * All coordinates are in the SVG viewBox space (0 0 280 560).
 */
data class VisualShape(
    val muscle: MuscleGroup,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cornerRadius: Float,
) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
}

/** What happens when the user taps a hit zone. */
sealed interface HitBehavior {
    /** Routes directly to a single muscle's exercise sheet. */
    data class Direct(val muscle: MuscleGroup) : HitBehavior

    /**
     * Opens an anchored chip-popover with [muscles] choices.
     * Used for two-stage clusters and fuzzy trigger bands.
     */
    data class Cluster(val muscles: List<MuscleGroup>) : HitBehavior
}

/**
 * An interactable padded rectangle on the body map.
 * All coordinates in SVG viewBox space (280 × 560).
 * Zones are stored in priority order; the first zone whose rect contains
 * the touch point wins.
 */
data class HitZone(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val behavior: HitBehavior,
) {
    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
}

// ── Private builder helpers ───────────────────────────────────────────────────

private fun vs(m: MuscleGroup, l: Float, t: Float, r: Float, b: Float, cr: Float) =
    VisualShape(m, l, t, r, b, cr)

private fun direct(l: Float, t: Float, r: Float, b: Float, m: MuscleGroup) =
    HitZone(l, t, r, b, HitBehavior.Direct(m))

private fun cluster(l: Float, t: Float, r: Float, b: Float, vararg ms: MuscleGroup) =
    HitZone(l, t, r, b, HitBehavior.Cluster(ms.toList()))

// ── FRONT VIEW ───────────────────────────────────────────────────────────────
//
// SVG paths decoded from Body_Map_Front.svg (viewBox 280 × 560).
// Bilateral muscles produce two shapes (left and right side).

/**
 * Visual shapes for the front-body map, drawn in SVG layer order.
 * Bilateral muscles (BICEPS, FOREARMS, etc.) appear twice.
 */
val frontVisuals: List<VisualShape> = listOf(
    vs(MuscleGroup.NECK,        124f,  72f, 156f,  94f,  6f),
    // Front delts — bilateral
    vs(MuscleGroup.FRONT_DELTS,  44f,  98f,  88f, 138f, 16f),
    vs(MuscleGroup.FRONT_DELTS, 192f,  98f, 236f, 138f, 16f),
    // Side delts — bilateral (front view)
    vs(MuscleGroup.SIDE_DELTS,   20f, 110f,  56f, 156f, 14f),
    vs(MuscleGroup.SIDE_DELTS,  224f, 110f, 260f, 156f, 14f),
    vs(MuscleGroup.CHEST,        96f, 100f, 184f, 164f, 12f),
    // Biceps — bilateral
    vs(MuscleGroup.BICEPS,       30f, 150f,  74f, 220f, 14f),
    vs(MuscleGroup.BICEPS,      206f, 150f, 250f, 220f, 14f),
    // Forearms — bilateral
    vs(MuscleGroup.FOREARMS,     28f, 224f,  68f, 308f, 12f),
    vs(MuscleGroup.FOREARMS,    212f, 224f, 252f, 308f, 12f),
    vs(MuscleGroup.ABS,         108f, 168f, 172f, 256f, 10f),
    // Obliques — bilateral (visual only — no direct hit zone; TalkBack action only)
    vs(MuscleGroup.OBLIQUES,     90f, 172f, 106f, 254f,  6f),
    vs(MuscleGroup.OBLIQUES,    174f, 172f, 190f, 254f,  6f),
    // Quads — bilateral
    vs(MuscleGroup.QUADS,        70f, 314f, 122f, 432f, 18f),
    vs(MuscleGroup.QUADS,       158f, 314f, 210f, 432f, 18f),
    vs(MuscleGroup.ADDUCTORS,   122f, 320f, 158f, 428f, 14f),
    // Calves — bilateral
    vs(MuscleGroup.CALVES,       84f, 440f, 136f, 546f, 18f),
    vs(MuscleGroup.CALVES,      144f, 440f, 196f, 546f, 18f),
)

/**
 * Hit zones for the front-body map, in priority order (first match wins).
 *
 * Strategy per SCREENS.md:
 *  [0]   NECK  — padded upward to canvas top, widened to 64 SVG units.
 *  [1–2] {FRONT_DELTS, SIDE_DELTS} delt clusters — bilateral; first tap → 2-chip popover.
 *  [3–4] QUADS/ADDUCTORS inner-edge clusters — inner 20 SVG of each QUADS visual toward
 *        the body midline; taps there → "Quads / Adductors" 2-chip popover.
 *  [5–]  Individual direct zones: CHEST, BICEPS, FOREARMS, ABS, QUADS outer, CALVES.
 *        OBLIQUES has no hit zone (16dp wide, blocked on all sides; TalkBack only).
 */
val frontHitZones: List<HitZone> = listOf(
    // ── [0] NECK ─────────────────────────────────────────────────────────────
    // Visual (124, 72, 156, 94) → padded: top to canvas edge, width to 64 SVG.
    direct(108f,   0f, 172f,  94f, MuscleGroup.NECK),

    // ── [1–2] Front / Side delt clusters ─────────────────────────────────────
    // Combined bounding box of FRONT_DELTS (44–88) + SIDE_DELTS (20–56) on each side.
    // Neither region independently reaches 64dp in both axes at the relevant scale;
    // the combined zone opens a 2-chip popover on first tap.
    cluster( 20f,  98f,  88f, 156f, MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
    cluster(192f,  98f, 260f, 156f, MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),

    // ── [3–4] Quads / Adductors midline clusters ──────────────────────────────
    // QUADS L inner edge = x 122; QUADS R inner edge = x 158; body midline = 140.
    // Inner 20 SVG (~27dp at 384dp canvas) from each visual inner edge → popover.
    // Outer zones below handle the remaining QUADS area as Direct.
    cluster(102f, 314f, 122f, 432f, MuscleGroup.QUADS, MuscleGroup.ADDUCTORS),
    cluster(158f, 314f, 178f, 432f, MuscleGroup.QUADS, MuscleGroup.ADDUCTORS),

    // ── [5–] Direct zones ─────────────────────────────────────────────────────
    // CHEST — passes natively (88×64 SVG); small buffer added.
    direct( 92f,  96f, 188f, 168f, MuscleGroup.CHEST),
    // BICEPS — padded laterally toward canvas edge (L→x=0, R→x=280).
    direct(  0f, 150f,  74f, 220f, MuscleGroup.BICEPS),
    direct(206f, 150f, 280f, 220f, MuscleGroup.BICEPS),
    // FOREARMS — padded laterally toward canvas edge.
    direct(  0f, 224f,  68f, 308f, MuscleGroup.FOREARMS),
    direct(212f, 224f, 280f, 308f, MuscleGroup.FOREARMS),
    // ABS — passes natively (64×88 SVG); small buffer added.
    direct(104f, 164f, 176f, 260f, MuscleGroup.ABS),
    // QUADS outer zones — padded away from midline; inner zones handled above.
    direct(  0f, 314f, 102f, 432f, MuscleGroup.QUADS),   // left outer  (x=0 → 122−20)
    direct(178f, 314f, 280f, 432f, MuscleGroup.QUADS),   // right outer (x=158+20 → 280)
    // CALVES — padded outward away from midline.
    direct(  0f, 440f, 136f, 546f, MuscleGroup.CALVES),
    direct(144f, 440f, 280f, 546f, MuscleGroup.CALVES),
)

// ── BACK VIEW ────────────────────────────────────────────────────────────────
//
// SVG paths decoded from Body_Map_Back.svg (viewBox 280 × 560).

val backVisuals: List<VisualShape> = listOf(
    vs(MuscleGroup.NECK,        124f,  72f, 156f,  94f,  6f),
    vs(MuscleGroup.TRAPS,       104f,  88f, 176f, 118f,  8f),
    // Rear delts — bilateral
    vs(MuscleGroup.REAR_DELTS,   40f, 100f,  84f, 140f, 16f),
    vs(MuscleGroup.REAR_DELTS,  196f, 100f, 240f, 140f, 16f),
    // Side delts — bilateral (back view)
    vs(MuscleGroup.SIDE_DELTS,   20f, 112f,  54f, 158f, 14f),
    vs(MuscleGroup.SIDE_DELTS,  226f, 112f, 260f, 158f, 14f),
    vs(MuscleGroup.UPPER_BACK,   92f, 120f, 188f, 156f, 10f),
    // Lats — bilateral
    vs(MuscleGroup.LATS,         76f, 158f, 128f, 224f, 12f),
    vs(MuscleGroup.LATS,        152f, 158f, 204f, 224f, 12f),
    vs(MuscleGroup.LOWER_BACK,   98f, 226f, 182f, 262f,  8f),
    // Triceps — bilateral
    vs(MuscleGroup.TRICEPS,      30f, 150f,  76f, 222f, 14f),
    vs(MuscleGroup.TRICEPS,     204f, 150f, 250f, 222f, 14f),
    // Forearms — bilateral
    vs(MuscleGroup.FOREARMS,     28f, 224f,  68f, 308f, 12f),
    vs(MuscleGroup.FOREARMS,    212f, 224f, 252f, 308f, 12f),
    vs(MuscleGroup.GLUTES,       88f, 264f, 192f, 324f, 18f),
    // Hamstrings — bilateral
    vs(MuscleGroup.HAMSTRINGS,   80f, 328f, 140f, 432f, 18f),
    vs(MuscleGroup.HAMSTRINGS,  142f, 328f, 202f, 432f, 18f),
    // Calves — bilateral
    vs(MuscleGroup.CALVES,       84f, 442f, 136f, 546f, 18f),
    vs(MuscleGroup.CALVES,      144f, 442f, 196f, 546f, 18f),
)

/**
 * Hit zones for the back-body map, in priority order (first match wins).
 *
 *  [0]   NECK — same padding strategy as front; highest precedence.
 *  [1]   TRAPS — padded upward to canvas top. NECK zone [0] is checked first,
 *        so taps in the overlapping area (x=108–172, y=0–94) go to NECK, not TRAPS.
 *        Priority-order resolution: NECK wins for its padded zone; TRAPS wins elsewhere.
 *  [2–3] {REAR_DELTS, SIDE_DELTS} delt clusters — bilateral.
 *  [4]   UPPER_BACK fuzzy trigger band — the entire 36px-tall visible strip sits at
 *        2dp from TRAPS below and 2dp from LATS above; any tap is genuinely ambiguous
 *        at real thumb width. Zone always shows a 3-chip popover {TRAPS, UPPER_BACK, LATS}.
 *  [5]   LOWER_BACK fuzzy band — 2dp from GLUTES below; 2-chip popover {LOWER_BACK, GLUTES}.
 *  [6]   GLUTES seam zone — top 20 SVG of GLUTES visual, same 2-chip popover.
 *  [7–]  Individual direct zones: LATS (padded inward to spine midline), TRICEPS,
 *        FOREARMS, GLUTES main, HAMSTRINGS (padded outward), CALVES.
 */
val backHitZones: List<HitZone> = listOf(
    // ── [0] NECK ─────────────────────────────────────────────────────────────
    direct(108f,   0f, 172f,  94f, MuscleGroup.NECK),

    // ── [1] TRAPS — padded upward to canvas top ───────────────────────────────
    // Overlaps NECK zone in (108–172, 0–94); NECK [0] wins there.
    // TRAPS-exclusive area: lateral strips x=104–108 and 172–176 plus y=94–118 row.
    direct(104f,   0f, 176f, 118f, MuscleGroup.TRAPS),

    // ── [2–3] Rear / Side delt clusters ──────────────────────────────────────
    // Combined bounding box of REAR_DELTS (40–84) + SIDE_DELTS (20–54) on each side.
    cluster( 20f, 100f,  84f, 158f, MuscleGroup.REAR_DELTS, MuscleGroup.SIDE_DELTS),
    cluster(196f, 100f, 260f, 158f, MuscleGroup.REAR_DELTS, MuscleGroup.SIDE_DELTS),

    // ── [4] UPPER_BACK fuzzy trigger band ────────────────────────────────────
    // Strip height 36 SVG (y=120–156); 2dp gap to TRAPS bottom (118) and LATS top (158).
    // Ambiguous at every real thumb press — popover is the correct outcome, not a fallback.
    cluster( 92f, 120f, 188f, 156f,
        MuscleGroup.TRAPS, MuscleGroup.UPPER_BACK, MuscleGroup.LATS),

    // ── [5] LOWER_BACK fuzzy band ─────────────────────────────────────────────
    // Strip height 36 SVG (y=226–262); 2dp gap to GLUTES top (264).
    cluster( 98f, 226f, 182f, 262f, MuscleGroup.LOWER_BACK, MuscleGroup.GLUTES),

    // ── [6] GLUTES seam zone (top 20 SVG of GLUTES visual) ───────────────────
    // y=262–284; same popover as LOWER_BACK band — thumb reaching the seam from
    // either side sees the same disambiguation choices.
    cluster( 88f, 262f, 192f, 284f, MuscleGroup.LOWER_BACK, MuscleGroup.GLUTES),

    // ── [7–] Direct zones ─────────────────────────────────────────────────────
    // LATS — padded inward toward the spine gap (x=128–152 → 24 SVG gap → mid x=140).
    // L: extend right to x=140; R: extend left to x=140. Width reaches 64 SVG each.
    direct( 76f, 158f, 140f, 224f, MuscleGroup.LATS),
    direct(140f, 158f, 204f, 224f, MuscleGroup.LATS),
    // TRICEPS — padded laterally toward canvas edge.
    direct(  0f, 150f,  76f, 222f, MuscleGroup.TRICEPS),
    direct(204f, 150f, 280f, 222f, MuscleGroup.TRICEPS),
    // FOREARMS — padded laterally toward canvas edge.
    direct(  0f, 224f,  68f, 308f, MuscleGroup.FOREARMS),
    direct(212f, 224f, 280f, 308f, MuscleGroup.FOREARMS),
    // GLUTES main body — below the seam zone (y > 284).
    direct( 88f, 284f, 192f, 324f, MuscleGroup.GLUTES),
    // HAMSTRINGS — padded outward (away from midline x=140).
    direct(  0f, 328f, 140f, 432f, MuscleGroup.HAMSTRINGS),
    direct(140f, 328f, 280f, 432f, MuscleGroup.HAMSTRINGS),
    // CALVES — padded outward.
    direct(  0f, 442f, 136f, 546f, MuscleGroup.CALVES),
    direct(144f, 442f, 280f, 546f, MuscleGroup.CALVES),
)

// ── Accessibility ─────────────────────────────────────────────────────────────

/**
 * Every muscle in the enum is exposed as a discrete TalkBack custom action on the map,
 * regardless of whether it has a visual hit zone (OBLIQUES) or requires a popover
 * (clusters). Screen-reader users are never routed through the two-stage flow.
 */
val allMusclesForAccessibility: List<MuscleGroup> = MuscleGroup.entries.toList()

/** Human-readable label for TalkBack announcements and popover chips. */
val MuscleGroup.displayName: String get() = when (this) {
    MuscleGroup.CHEST -> "Chest"
    MuscleGroup.UPPER_BACK -> "Upper Back"
    MuscleGroup.LATS -> "Lats"
    MuscleGroup.LOWER_BACK -> "Lower Back"
    MuscleGroup.FRONT_DELTS -> "Front Delts"
    MuscleGroup.SIDE_DELTS -> "Side Delts"
    MuscleGroup.REAR_DELTS -> "Rear Delts"
    MuscleGroup.BICEPS -> "Biceps"
    MuscleGroup.TRICEPS -> "Triceps"
    MuscleGroup.FOREARMS -> "Forearms"
    MuscleGroup.QUADS -> "Quads"
    MuscleGroup.HAMSTRINGS -> "Hamstrings"
    MuscleGroup.GLUTES -> "Glutes"
    MuscleGroup.ADDUCTORS -> "Adductors"
    MuscleGroup.CALVES -> "Calves"
    MuscleGroup.ABS -> "Abs"
    MuscleGroup.OBLIQUES -> "Obliques"
    MuscleGroup.TRAPS -> "Traps"
    MuscleGroup.NECK -> "Neck"
}

// ── Callout labels ────────────────────────────────────────────────────────────
// Anchored floating labels that appear beside muscles on the body map.
// anchorX/Y are in SVG viewBox coords (280×560); side = LEFT or RIGHT of body.

enum class CalloutSide { LEFT, RIGHT }

data class CalloutAnchor(
    val muscle: MuscleGroup,
    val anchorX: Float,
    val anchorY: Float,
    val side: CalloutSide,
)

/** Callout anchors for the front-body map. Only muscles with meaningful volume data get labels. */
val frontCallouts: List<CalloutAnchor> = listOf(
    CalloutAnchor(MuscleGroup.FRONT_DELTS,  66f, 118f, CalloutSide.LEFT),
    CalloutAnchor(MuscleGroup.CHEST,       184f, 146f, CalloutSide.RIGHT),
    CalloutAnchor(MuscleGroup.BICEPS,       52f, 176f, CalloutSide.LEFT),
    CalloutAnchor(MuscleGroup.ABS,         140f, 212f, CalloutSide.RIGHT),
    CalloutAnchor(MuscleGroup.FOREARMS,     48f, 266f, CalloutSide.LEFT),
    CalloutAnchor(MuscleGroup.QUADS,        96f, 373f, CalloutSide.LEFT),
    CalloutAnchor(MuscleGroup.CALVES,      170f, 493f, CalloutSide.RIGHT),
)

/** Callout anchors for the back-body map. */
val backCallouts: List<CalloutAnchor> = listOf(
    CalloutAnchor(MuscleGroup.TRAPS,       140f, 103f, CalloutSide.RIGHT),
    CalloutAnchor(MuscleGroup.REAR_DELTS,   62f, 120f, CalloutSide.LEFT),
    CalloutAnchor(MuscleGroup.UPPER_BACK,  140f, 138f, CalloutSide.RIGHT),
    CalloutAnchor(MuscleGroup.LATS,        102f, 191f, CalloutSide.LEFT),
    CalloutAnchor(MuscleGroup.TRICEPS,     227f, 186f, CalloutSide.RIGHT),
    CalloutAnchor(MuscleGroup.LOWER_BACK,  140f, 244f, CalloutSide.RIGHT),
    CalloutAnchor(MuscleGroup.GLUTES,      140f, 294f, CalloutSide.LEFT),
    CalloutAnchor(MuscleGroup.HAMSTRINGS,  170f, 380f, CalloutSide.RIGHT),
    CalloutAnchor(MuscleGroup.CALVES,       110f, 493f, CalloutSide.LEFT),
)

