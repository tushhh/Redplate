package dev.redplate

import dev.redplate.data.SessionEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The estimate used to be a flat three minutes per set, which is less than the rest
 * prescribed for a single heavy compound. That made `sessionCeilingMinutes` — one of the
 * few things the intake actually asks for — a number the app did not honour.
 */
class SessionEstimateTest {

    @Test
    fun `rest dominates a strength session and the estimate says so`() {
        // Four sets at 240 s rest: 4 x 45 s of work plus three full rests plus one trailing
        // rest that is not charged.
        val minutes = SessionEstimate.minutesOf(listOf(4 to 240))
        assertEquals(((4 * (45 + 240) - 240) / 60.0).toInt(), minutes)
        assertTrue("A single heavy lift already costs more than 12 minutes", minutes > 12)
    }

    @Test
    fun `the old flat three minutes per set understated a strength day badly`() {
        val slots = listOf(4 to 240, 4 to 240, 3 to 240)
        val flatEstimate = (4 + 4 + 3) * 3   // 33 minutes, the old assumption
        val real = SessionEstimate.minutesOf(slots)

        assertEquals(48, real)
        assertTrue(
            "A day the old maths fitted inside a 45-minute ceiling actually runs $real",
            real > flatEstimate,
        )
    }

    @Test
    fun `short rests make for a shorter session at the same set count`() {
        val heavy = SessionEstimate.minutesOf(listOf(3 to 240, 3 to 240))
        val light = SessionEstimate.minutesOf(listOf(3 to 60, 3 to 60))
        assertTrue(heavy > light)
    }

    @Test
    fun `only the last rest of the session is forgiven`() {
        val one = SessionEstimate.minutesOf(listOf(1 to 120))
        assertEquals(1, one) // 45 s of work, no rest after it, floored at a minute

        // 45 s + 120 s + 45 s = 210 s; the trailing rest is dropped, the rest is not.
        val two = SessionEstimate.minutesOf(listOf(1 to 120, 1 to 120))
        assertEquals(4, two)
    }

    @Test
    fun `slots with no sets do not count`() {
        assertEquals(0, SessionEstimate.minutesOf(emptyList()))
        assertEquals(0, SessionEstimate.minutesOf(listOf(0 to 120)))
    }

    /** "About ${minutes / 15 * 15} minutes" rendered a short session as "About 0 minutes". */
    @Test
    fun `the spoken estimate rounds to five and never says zero`() {
        assertEquals(15, SessionEstimate.spokenMinutes(0))
        assertEquals(15, SessionEstimate.spokenMinutes(14))
        assertEquals(45, SessionEstimate.spokenMinutes(44))
        assertEquals(60, SessionEstimate.spokenMinutes(59))
        assertEquals(60, SessionEstimate.spokenMinutes(61))
    }
}
