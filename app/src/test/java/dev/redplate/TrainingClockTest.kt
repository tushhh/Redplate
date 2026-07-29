package dev.redplate

import dev.redplate.data.TrainingClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A training day is not a calendar day. Everything that groups, filters or schedules by
 * day goes through [TrainingClock], so these are the rules Today, the week plan and
 * History all inherit.
 *
 * The zone is pinned rather than taken from the system: a test that passes only on the
 * machine that wrote it is not evidence of anything.
 */
class TrainingClockTest {

    private val zone = ZoneId.of("Asia/Singapore")
    private val clock = TrainingClock(zone)

    private fun at(date: String, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `a session after midnight belongs to the day before`() {
        // Thursday 30 July 2026, 02:00 — the tail of Wednesday's session.
        val date = clock.trainingDate(at("2026-07-30", 2), dayStartHour = 8)
        assertEquals(LocalDate.of(2026, 7, 29), date)
        assertEquals(DayOfWeek.WEDNESDAY, date.dayOfWeek)
    }

    @Test
    fun `the boundary hour is where the day flips`() {
        assertEquals(
            LocalDate.of(2026, 7, 29),
            clock.trainingDate(at("2026-07-30", 7, 59), dayStartHour = 8),
        )
        assertEquals(
            LocalDate.of(2026, 7, 30),
            clock.trainingDate(at("2026-07-30", 8, 0), dayStartHour = 8),
        )
    }

    @Test
    fun `late evening stays on its own calendar day`() {
        assertEquals(
            LocalDate.of(2026, 7, 30),
            clock.trainingDate(at("2026-07-30", 23, 59), dayStartHour = 8),
        )
    }

    @Test
    fun `midnight rolls back at every configured boundary`() {
        val midnight = at("2026-07-30", 0, 0)
        assertEquals(LocalDate.of(2026, 7, 30), clock.trainingDate(midnight, dayStartHour = 0))
        assertEquals(LocalDate.of(2026, 7, 29), clock.trainingDate(midnight, dayStartHour = 4))
        assertEquals(LocalDate.of(2026, 7, 29), clock.trainingDate(midnight, dayStartHour = 8))
    }

    @Test
    fun `a session at one in the morning on monday belongs to the previous training week`() {
        // Monday 3 August 2026, 01:00 — still Sunday's training day, so still last week.
        val week = clock.weekStart(at("2026-08-03", 1), dayStartHour = 4)
        assertEquals(LocalDate.of(2026, 7, 27), week)
        assertEquals(DayOfWeek.MONDAY, week.dayOfWeek)
    }

    @Test
    fun `a monday morning session belongs to its own week`() {
        assertEquals(
            LocalDate.of(2026, 8, 3),
            clock.weekStart(at("2026-08-03", 9), dayStartHour = 4),
        )
    }

    @Test
    fun `day bounds cover exactly the instants that map to that day`() {
        val date = LocalDate.of(2026, 7, 30)
        val bounds = clock.dayBounds(date, dayStartHour = 4)

        assertEquals(at("2026-07-30", 4), bounds.first)
        assertEquals(at("2026-07-31", 4) - 1, bounds.last)

        assertTrue(at("2026-07-30", 4) in bounds)
        assertTrue(at("2026-07-31", 3, 59) in bounds)
        assertTrue("03:59 belongs to the day before", at("2026-07-30", 3, 59) !in bounds)
        assertTrue("04:00 the next day has rolled over", at("2026-07-31", 4) !in bounds)
    }

    /** The property that matters: bounds and [TrainingClock.trainingDate] cannot disagree. */
    @Test
    fun `every hour of a day falls inside that day's bounds`() {
        val date = LocalDate.of(2026, 7, 30)
        for (hour in 0..47) {
            val millis = at("2026-07-30", 0) + hour * 3_600_000L
            val bounds = clock.dayBounds(clock.trainingDate(millis, dayStartHour = 4), 4)
            assertTrue("hour $hour fell outside its own day", millis in bounds)
        }
    }

    @Test
    fun `week bounds run monday to sunday inclusive`() {
        val bounds = clock.weekBounds(LocalDate.of(2026, 7, 27), dayStartHour = 4)
        assertEquals(at("2026-07-27", 4), bounds.first)
        assertEquals(at("2026-08-03", 4) - 1, bounds.last)
    }

    @Test
    fun `weekday index is zero based from monday`() {
        assertEquals(0, clock.weekdayIndex(LocalDate.of(2026, 7, 27)))
        assertEquals(3, clock.weekdayIndex(LocalDate.of(2026, 7, 30)))
        assertEquals(6, clock.weekdayIndex(LocalDate.of(2026, 8, 2)))
    }
}
