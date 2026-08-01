package dev.redplate.data

import kotlin.math.abs

/**
 * What a week of logged sets says about where a lift actually is.
 *
 * The per-session engine ([ProgressionEngine]) moves one notch at a time from the last
 * session alone. That is the right instrument for one session and the wrong one for a
 * whole week: a lift the user has been clearing at three reps in reserve every set, every
 * session, is not one notch too light — it is a load chosen before there was any evidence,
 * and stepping it by 2.5 kg a week means several wasted weeks before it catches up.
 *
 * This looks at the week as a body of evidence, estimates the strength behind it, and says
 * whether the prescription was right. It runs at the week boundary, so the first thing it
 * ever assesses is week 1 — the week where the load was least likely to be right, because
 * nothing was known when it was set.
 */
enum class LiftResponse {
    /** Cleared the top of the range with more left in reserve than was asked for. */
    UNDERLOADED,

    /** The prescription did what it was meant to. Leave it to the per-session engine. */
    ON_TARGET,

    /** Missed the range, or worked well past the prescribed effort to hold it. */
    OVERREACHED,

    /** Not enough was logged, or no effort was reported, to say anything at all. */
    UNKNOWN,
}

/**
 * One lift's standing after a week.
 *
 * [estimated1Rm] is Epley extended by reported reps in reserve: a set of 10 at 3 in
 * reserve is evidence of a 13-rep max, not a 10-rep one. Ignoring the RIR would
 * systematically *under*-estimate exactly the lifts that are too light — the ones this
 * exists to find.
 */
data class LiftAssessment(
    val exerciseId: String,
    val response: LiftResponse,
    /** Heaviest working load logged in the week. The calibration clamp hangs off this. */
    val bestLoadKg: Double,
    /** Null when nothing could be estimated — no sets, or a bodyweight movement at zero. */
    val estimated1Rm: Double?,
    val averageRir: Double?,
    /** One sentence, no exercise name — the caller owns how the lift is addressed. */
    val reason: String,
)

/** A load the week's evidence says should change, and why. Never a no-op. */
data class LoadCalibration(
    val exerciseId: String,
    val fromKg: Double,
    val toKg: Double,
    val reason: String,
) {
    val isIncrease: Boolean get() = toKg > fromKg
}

object StrengthAssessment {

    /**
     * The most a calibration may move a load in one week.
     *
     * Measured against the load actually lifted, not against the current prescription, so
     * a per-session step that has already been applied cannot be compounded by this. The
     * arithmetic will happily suggest +25% from one very easy week; that is a number that
     * belongs in a spreadsheet, not under someone's back.
     */
    const val MAX_WEEKLY_CHANGE = 0.10

    /** Below this there is not enough of a week to draw a conclusion from. */
    const val MIN_SETS_FOR_ASSESSMENT = 2

    /** How many notches a level-marked machine moves when the week says it was too light. */
    const val UNDERLOADED_NOTCHES = 2

    private const val EPSILON = 1e-6

    // ── Assessing ───────────────────────────────────────────────────────

    fun assess(
        exerciseId: String,
        sets: List<SetLogEntity>,
        slot: TemplateSlotEntity,
    ): LiftAssessment {
        val working = sets.filter { !it.isWarmup }
        if (working.size < MIN_SETS_FOR_ASSESSMENT) {
            return unknown(exerciseId, working, "only ${working.size} working sets were logged")
        }

        val reported = working.mapNotNull { it.rir }
        if (reported.isEmpty()) {
            // The same invariant the per-session engine holds: a skipped difficulty prompt
            // is missing information, not a maximal set.
            return unknown(exerciseId, working, "no effort was reported, so there is nothing to judge against")
        }

        val bestLoad = working.maxOf { it.loadKg }
        val averageRir = reported.average()
        val minRir = reported.min()
        val e1rm = estimated1Rm(working)

        val missedMost = working.count { it.reps < slot.repRangeLow } * 2 > working.size
        val heldOnHard = averageRir <= slot.targetRir - 1.0

        val response = when {
            missedMost || heldOnHard -> LiftResponse.OVERREACHED
            working.all { it.reps >= slot.repRangeHigh } && minRir >= slot.targetRir + 1 ->
                LiftResponse.UNDERLOADED

            else -> LiftResponse.ON_TARGET
        }

        val reason = when (response) {
            LiftResponse.UNDERLOADED ->
                "every set reached ${slot.repRangeHigh} reps with at least $minRir in reserve, " +
                    "against a target of ${slot.targetRir}"

            LiftResponse.OVERREACHED -> if (missedMost) {
                "most sets fell short of ${slot.repRangeLow} reps"
            } else {
                "you averaged ${trim(averageRir)} in reserve against a target of ${slot.targetRir} — " +
                    "harder every set than it was meant to be"
            }

            LiftResponse.ON_TARGET ->
                "the week landed inside ${slot.repRangeLow}–${slot.repRangeHigh} reps at " +
                    "${trim(averageRir)} in reserve"

            LiftResponse.UNKNOWN -> ""
        }

        return LiftAssessment(
            exerciseId = exerciseId,
            response = response,
            bestLoadKg = bestLoad,
            estimated1Rm = e1rm,
            averageRir = averageRir,
            reason = reason,
        )
    }

    /**
     * Epley, extended by reported reps in reserve, taken across the week's best set.
     *
     * Every set is a data point about the same strength, so the estimate is the largest
     * any single set supports rather than an average — an average would let a light
     * back-off set drag the number down and hide a lift that is genuinely too easy.
     */
    fun estimated1Rm(sets: List<SetLogEntity>): Double? {
        val usable = sets.filter { !it.isWarmup && it.loadKg > EPSILON }
        if (usable.isEmpty()) return null
        return usable.maxOf { set ->
            val effectiveReps = set.reps + (set.rir ?: 0)
            set.loadKg * (1 + effectiveReps / 30.0)
        }
    }

    // ── Turning the assessment into next week's load ────────────────────

    /**
     * The load the week's evidence says the next session should use, or null to leave the
     * prescription alone.
     *
     * Only [LiftResponse.UNDERLOADED] and [LiftResponse.OVERREACHED] produce anything. A
     * lift that is on target is already in the per-session engine's hands, and a second
     * opinion on it would be two rules fighting over the same number.
     *
     * The target is the load that should produce the *bottom* of the rep range at the
     * prescribed effort — which is what double progression wants, since the reps then
     * climb back up through the range from there.
     */
    fun calibrate(
        assessment: LiftAssessment,
        slot: TemplateSlotEntity,
        equipment: EquipmentEntity?,
    ): LoadCalibration? {
        val current = slot.workingLoadKg ?: assessment.bestLoadKg
        return when (assessment.response) {
            LiftResponse.UNDERLOADED -> harder(assessment, slot, equipment, current)
            LiftResponse.OVERREACHED -> easier(assessment, slot, equipment, current)
            LiftResponse.ON_TARGET, LiftResponse.UNKNOWN -> null
        }
    }

    private fun harder(
        assessment: LiftAssessment,
        slot: TemplateSlotEntity,
        equipment: EquipmentEntity?,
        current: Double,
    ): LoadCalibration? {
        // A number that is not a mass cannot be run through Epley: level 8 is harder than
        // level 6 but it is not eight kilograms, and dividing it by a rep factor produces
        // a number that means nothing. Those machines move in notches instead.
        if (!isMassBased(equipment)) {
            val next = stepNotches(current, equipment, UNDERLOADED_NOTCHES, harder = true) ?: return null
            return LoadCalibration(
                assessment.exerciseId, current, next,
                "${assessment.reason}, so it moves up $UNDERLOADED_NOTCHES notches instead of one",
            )
        }

        val target = loadForPrescription(assessment, slot) ?: return null
        val ceiling = assessment.bestLoadKg * (1 + MAX_WEEKLY_CHANGE)
        // Snapped downward on purpose. Rounding to the *nearest* loadable weight can land
        // above the cap, and a bound that the snapping step is allowed to breach is not a
        // bound. Under-shooting by one notch costs a week; over-shooting costs a session.
        val snapped = equipment?.largestLoadableAtOrBelow(target.coerceAtMost(ceiling))
            ?: target.coerceAtMost(ceiling)
        if (snapped <= current + EPSILON) return null

        return LoadCalibration(
            assessment.exerciseId, current, snapped,
            "${assessment.reason} — ${trim(snapped)} kg is what your week says you can do " +
                "${slot.repRangeLow} reps with",
        )
    }

    private fun easier(
        assessment: LiftAssessment,
        slot: TemplateSlotEntity,
        equipment: EquipmentEntity?,
        current: Double,
    ): LoadCalibration? {
        if (!isMassBased(equipment)) {
            val next = stepNotches(current, equipment, 1, harder = false) ?: return null
            return LoadCalibration(
                assessment.exerciseId, current, next,
                "${assessment.reason}, so it comes back a notch",
            )
        }

        val target = loadForPrescription(assessment, slot) ?: return null
        val floor = assessment.bestLoadKg * (1 - MAX_WEEKLY_CHANGE)
        // Snapped upward for the same reason, mirrored: the smallest loadable weight that
        // is still at or above the floor. Taking the nearest instead would let a coarse
        // plate set drop someone 12% in a week while the code claimed a 10% bound.
        val snapped = equipment?.let { eq ->
            val below = eq.largestLoadableAtOrBelow(target.coerceAtLeast(floor))
            if (below < floor - EPSILON) PlateMath.nextLoadUp(below, eq) else below
        } ?: target.coerceAtLeast(floor)
        if (snapped >= current - EPSILON || snapped <= EPSILON) return null

        return LoadCalibration(
            assessment.exerciseId, current, snapped,
            "${assessment.reason} — ${trim(snapped)} kg is where ${slot.repRangeLow} reps " +
                "at ${slot.targetRir} in reserve actually sits",
        )
    }

    /**
     * Epley run backwards: given the estimated max, the load that should yield
     * [TemplateSlotEntity.repRangeLow] reps with [TemplateSlotEntity.targetRir] left.
     */
    private fun loadForPrescription(assessment: LiftAssessment, slot: TemplateSlotEntity): Double? {
        val e1rm = assessment.estimated1Rm ?: return null
        val effectiveReps = slot.repRangeLow + slot.targetRir
        return e1rm / (1 + effectiveReps / 30.0)
    }

    /**
     * True when the number on this equipment is a weight that can be reasoned about.
     *
     * Assistance is excluded even though it reads in kilograms on some machines: the load
     * is what is being taken *off* you, so an estimated max built from it would describe
     * the machine's contribution rather than yours.
     */
    private fun isMassBased(equipment: EquipmentEntity?): Boolean {
        if (equipment == null) return true // a manual lift is entered in kilograms
        return equipment.loadUnit == LoadUnit.KILOGRAMS && !equipment.isAssistance
    }

    /** Walks [notches] steps in the direction that makes the exercise harder or easier. */
    private fun stepNotches(
        from: Double,
        equipment: EquipmentEntity?,
        notches: Int,
        harder: Boolean,
    ): Double? {
        if (equipment == null) return null
        // Assistance runs backwards: less counterweight is a harder set.
        val up = harder != equipment.isAssistance
        var load = from
        repeat(notches) {
            val next = if (up) PlateMath.nextLoadUp(load, equipment) else PlateMath.nextLoadDown(load, equipment)
            if (abs(next - load) < EPSILON) return@repeat
            load = next
        }
        return load.takeIf { abs(it - from) > EPSILON }
    }

    private fun unknown(exerciseId: String, working: List<SetLogEntity>, reason: String) =
        LiftAssessment(
            exerciseId = exerciseId,
            response = LiftResponse.UNKNOWN,
            bestLoadKg = working.maxOfOrNull { it.loadKg } ?: 0.0,
            estimated1Rm = estimated1Rm(working),
            averageRir = working.mapNotNull { it.rir }.takeIf { it.isNotEmpty() }?.average(),
            reason = reason,
        )

    private fun trim(value: Double): String =
        if (abs(value % 1.0) < EPSILON) {
            value.toInt().toString()
        } else {
            String.format(java.util.Locale.ROOT, "%.1f", value)
        }
}
