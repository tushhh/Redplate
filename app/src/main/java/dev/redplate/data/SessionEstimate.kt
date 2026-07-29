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

    fun minutes(slots: List<TemplateSlotEntity>): Int =
        minutesOf(slots.map { it.targetSets to it.restSeconds })

    /** [setsAndRest] is one `sets to restSeconds` pair per exercise, in running order. */
    fun minutesOf(setsAndRest: List<Pair<Int, Int>>): Int {
        val working = setsAndRest.filter { (sets, _) -> sets > 0 }
        if (working.isEmpty()) return 0

        val seconds = working.sumOf { (sets, rest) -> sets * (WORK_SECONDS_PER_SET + rest) }
        val trailingRest = working.last().second
        return ((seconds - trailingRest) / 60.0).roundToInt().coerceAtLeast(1)
    }

    /**
     * The estimate as the UI says it out loud. Nearest five minutes, never below fifteen —
     * integer-dividing by 15 rounded a short session down to "About 0 minutes".
     */
    fun spokenMinutes(minutes: Int): Int =
        ((minutes / 5.0).roundToInt() * 5).coerceAtLeast(MINIMUM_SPOKEN_MINUTES)

    private const val MINIMUM_SPOKEN_MINUTES = 15
}
