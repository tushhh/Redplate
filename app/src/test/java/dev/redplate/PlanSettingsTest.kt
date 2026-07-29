package dev.redplate

import dev.redplate.data.Goal
import dev.redplate.data.MovementPattern
import dev.redplate.data.MuscleGroup
import dev.redplate.data.PlanSettings
import dev.redplate.data.Split
import dev.redplate.data.TrainingClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plan settings are the one place that decides what a valid plan is, for both the
 * intake and the "Your plan" screen. Two copies of these rules is how the generator and
 * the repository ended up disagreeing about available equipment.
 */
class PlanSettingsTest {

    private val base = PlanSettings(
        goal = Goal.HYPERTROPHY,
        daysPerWeek = 4,
        sessionCeilingMinutes = 60,
    )

    // ── Normalising ─────────────────────────────────────────────────────

    @Test
    fun `days per week clamps into the supported range`() {
        assertEquals(2, base.copy(daysPerWeek = 0).normalised().daysPerWeek)
        assertEquals(6, base.copy(daysPerWeek = 9).normalised().daysPerWeek)
    }

    @Test
    fun `session length snaps to an offered value`() {
        assertEquals(45, base.copy(sessionCeilingMinutes = 47).normalised().sessionCeilingMinutes)
        assertEquals(90, base.copy(sessionCeilingMinutes = 300).normalised().sessionCeilingMinutes)
    }

    @Test
    fun `no more than two priority muscles survive`() {
        val normalised = base.copy(
            priorityMuscles = listOf(
                MuscleGroup.CHEST, MuscleGroup.LATS, MuscleGroup.QUADS, MuscleGroup.CHEST,
            ),
        ).normalised()
        assertEquals(listOf(MuscleGroup.CHEST, MuscleGroup.LATS), normalised.priorityMuscles)
    }

    /**
     * A weekday selection that does not match the day count is stale, not half-valid.
     * Scheduling four sessions across three chosen days is not a plan anyone asked for.
     */
    @Test
    fun `a weekday selection that does not match the day count is dropped`() {
        assertNull(base.copy(daysPerWeek = 4, trainingDays = listOf(0, 2, 4)).normalised().trainingDays)
        assertEquals(
            listOf(0, 2, 4, 6),
            base.copy(daysPerWeek = 4, trainingDays = listOf(6, 0, 4, 2)).normalised().trainingDays,
        )
    }

    @Test
    fun `weekdays outside the week are discarded before counting`() {
        assertNull(base.copy(daysPerWeek = 4, trainingDays = listOf(0, 2, 4, 9)).normalised().trainingDays)
    }

    @Test
    fun `the day start hour stays inside the offered range`() {
        assertEquals(0, base.copy(dayStartHour = -3).normalised().dayStartHour)
        assertEquals(8, base.copy(dayStartHour = 23).normalised().dayStartHour)
        assertEquals(
            TrainingClock.DEFAULT_DAY_START_HOUR,
            base.normalised().dayStartHour,
        )
    }

    // ── What a change costs ─────────────────────────────────────────────

    @Test
    fun `goal and days rebuild the block`() {
        assertTrue(base.copy(goal = Goal.STRENGTH).needsRebuild(base))
        assertTrue(base.copy(daysPerWeek = 5).needsRebuild(base))
        assertTrue(base.copy(priorityMuscles = listOf(MuscleGroup.CHEST)).needsRebuild(base))
        assertTrue(base.copy(excludedPatterns = listOf(MovementPattern.LUNGE)).needsRebuild(base))
    }

    /** Session length only affects trimming, so it re-fits rather than regenerating. */
    @Test
    fun `session length re-fits instead of rebuilding`() {
        val longer = base.copy(sessionCeilingMinutes = 90)
        assertFalse(longer.needsRebuild(base))
        assertTrue(longer.needsRefit(base))
    }

    @Test
    fun `the day start hour touches nothing about the block`() {
        val later = base.copy(dayStartHour = 8)
        assertFalse(later.needsRebuild(base))
        assertFalse(later.needsRefit(base))
        assertFalse(later.needsReschedule(base))
    }

    /**
     * The bug this guards: a weekday-only change fell through to "nothing to do", so the
     * profile recorded the new days and every session stayed scheduled where it was.
     */
    @Test
    fun `choosing different weekdays reschedules without rebuilding`() {
        val moved = base.copy(trainingDays = listOf(1, 2, 4, 5))
        assertTrue(moved.needsReschedule(base))
        assertFalse("The exercises do not change, so nothing needs regenerating", moved.needsRebuild(base))
        assertFalse(moved.needsRefit(base))
    }

    /** Clearing a custom selection moves the sessions back to the split's layout. */
    @Test
    fun `resetting to the split's days is also a reschedule`() {
        val custom = base.copy(trainingDays = listOf(1, 2, 4, 5))
        assertTrue(base.needsReschedule(custom))
    }

    @Test
    fun `picking the same days the split already uses is not a reschedule`() {
        val explicit = base.copy(trainingDays = Split.forDays(base.daysPerWeek).weekdayIndices)
        assertFalse(explicit.needsReschedule(base))
    }

    /** A weekday move and a longer session can arrive in the same save. */
    @Test
    fun `weekdays and session length can both change at once`() {
        val both = base.copy(trainingDays = listOf(1, 2, 4, 5), sessionCeilingMinutes = 90)
        assertTrue(both.needsReschedule(base))
        assertTrue(both.needsRefit(base))
        assertFalse(both.needsRebuild(base))
    }

    /** A rebuild regenerates the days from scratch, so it must not also be a reschedule. */
    @Test
    fun `a rebuild is not also reported as a reschedule`() {
        val moreDays = base.copy(daysPerWeek = 5)
        assertTrue(moreDays.needsRebuild(base))
        assertFalse(moreDays.needsReschedule(base))
    }

    @Test
    fun `a rebuild is not also reported as a re-fit`() {
        val both = base.copy(goal = Goal.STRENGTH, sessionCeilingMinutes = 90)
        assertTrue(both.needsRebuild(base))
        assertFalse("A rebuild already re-fits", both.needsRefit(base))
    }

    // ── Weekdays ────────────────────────────────────────────────────────

    @Test
    fun `chosen weekdays override the split's layout`() {
        val chosen = listOf(1, 3, 5, 6)
        assertEquals(chosen, base.copy(trainingDays = chosen).weekdayIndices())
    }

    @Test
    fun `no choice falls back to the split's own days`() {
        for (days in PlanSettings.DAYS_RANGE) {
            assertEquals(
                Split.forDays(days).weekdayIndices,
                base.copy(daysPerWeek = days, trainingDays = null).weekdayIndices(),
            )
        }
    }

    /**
     * The KDoc promised sessions were spread out; PPL_6 prescribed Mon–Sat, six in a row,
     * and put the only rest day at the end of the week.
     */
    @Test
    fun `the six-day split does not stack every session consecutively`() {
        val days = Split.forDays(6).weekdayIndices
        val longestRun = days.sorted().fold(1 to 1) { (best, run), day ->
            val next = if (day - 1 in days) run + 1 else 1
            maxOf(best, next) to next
        }.first
        assertTrue("Six consecutive sessions is not a spread week", longestRun < 6)
    }
}
