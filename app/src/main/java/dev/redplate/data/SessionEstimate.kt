package dev.redplate.data

import kotlin.math.roundToInt

/**
 * How long a session will actually take.
 *
 * Both the generator's time-budget trimming and Today's headline used to assume a flat
 * three minutes per set. A `STRENGTH` compound is prescribed 240 s of rest, so a single
 * set of it blew the estimate before the bar left the rack — which meant
 * `profile.sessionCeilingMinutes`, one of the few things the intake actually asks, was
 * quietly not honoured.
 *
 * The estimate charges every set the work it takes plus the rest that follows it, and
 * forgives exactly one rest: the last set of the session, which is followed by leaving.
 * Rest between exercises is not forgiven — walking to the next station and loading it
 * costs about as much as the prescribed rest would have.
 */
object SessionEstimate {

    /** Time under the bar for one set, setup included. Rest is counted separately. */
    const val WORK_SECONDS_PER_SET = 45

    /**
     * A conditioning finisher is charged its prescribed minutes, not a set of work plus
     * rest — it used to be estimated as one 45-second set, so ten minutes on the bike
     * vanished from the budget and sessions ran past the ceiling the user chose.
     */
    fun minutes(slots: List<TemplateSlotEntity>): Int =
        minutesOf(
            slots.filterNot { it.isCardioFinisher }.map { it.targetSets to it.restSeconds },
            extraMinutes = slots.filter { it.isCardioFinisher }
                .sumOf { it.repRangeLow * it.targetSets.coerceAtLeast(1) },
        )

    /**
     * What a session is allowed to grow to: a finisher counted at its cap rather than
     * today's target. The plan is fitted to this, so a walk building from 20 to 30 minutes
     * does not push the session past its ceiling as it grows.
     */
    fun budgetMinutes(slots: List<TemplateSlotEntity>): Int =
        minutesOf(
            slots.filterNot { it.isCardioFinisher }.map { it.targetSets to it.restSeconds },
            extraMinutes = slots.filter { it.isCardioFinisher }
                .sumOf { maxOf(it.repRangeHigh, it.repRangeLow) * it.targetSets.coerceAtLeast(1) },
        )

    /**
     * [setsAndRest] is one `sets to restSeconds` pair per lift, in running order.
     * [extraMinutes] is fixed-duration work — a finisher — added on top.
     */
    fun minutesOf(setsAndRest: List<Pair<Int, Int>>, extraMinutes: Int = 0): Int {
        val working = setsAndRest.filter { (sets, _) -> sets > 0 }
        if (working.isEmpty()) return extraMinutes.coerceAtLeast(0)

        val seconds = working.sumOf { (sets, rest) -> sets * (WORK_SECONDS_PER_SET + rest) }
        val trailingRest = working.last().second
        val lifting = ((seconds - trailingRest) / 60.0).roundToInt().coerceAtLeast(1)
        return lifting + extraMinutes.coerceAtLeast(0)
    }

    /**
     * The estimate as the UI says it out loud. Nearest five minutes, never below fifteen —
     * integer-dividing by 15 rounded a short session down to "About 0 minutes".
     */
    fun spokenMinutes(minutes: Int): Int =
        ((minutes / 5.0).roundToInt() * 5).coerceAtLeast(MINIMUM_SPOKEN_MINUTES)

    private const val MINIMUM_SPOKEN_MINUTES = 15
}
