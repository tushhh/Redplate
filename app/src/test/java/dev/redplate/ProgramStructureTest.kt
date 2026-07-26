package dev.redplate

import dev.redplate.data.MuscleGroup
import dev.redplate.data.Split
import dev.redplate.data.VolumeLandmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural guarantees the generated program has to hold whatever the user's inputs
 * are. These are the claims COACHING.md §3 makes, checked against the split definitions
 * rather than against a database, so they run on the JVM and stay fast.
 */
class ProgramStructureTest {

    @Test
    fun `every supported schedule maps to a split`() {
        for (days in 2..6) {
            assertEquals(
                "A $days-day week must produce $days sessions",
                days,
                Split.forDays(days).days.size,
            )
        }
    }

    @Test
    fun `a split has one weekday slot per session`() {
        for (days in 2..6) {
            val split = Split.forDays(days)
            assertEquals(split.days.size, split.weekdayIndices.size)
        }
    }

    @Test
    fun `sessions never collide on the same weekday`() {
        for (days in 2..6) {
            val indices = Split.forDays(days).weekdayIndices
            assertEquals("Two sessions on one day at $days days", indices.size, indices.toSet().size)
            assertTrue("Weekday index out of range at $days days", indices.all { it in 0..6 })
        }
    }

    @Test
    fun `out of range schedules clamp rather than crash`() {
        assertEquals(Split.forDays(2), Split.forDays(0))
        assertEquals(Split.forDays(2), Split.forDays(1))
        assertEquals(Split.forDays(6), Split.forDays(7))
        assertEquals(Split.forDays(6), Split.forDays(99))
    }

    /**
     * "Each muscle trained 2x per week minimum" is the frequency rule the whole block
     * structure rests on. Checked from four days up, where the spec commits to it.
     */
    @Test
    fun `major muscles are trained at least twice a week from four days up`() {
        val major = listOf(
            MuscleGroup.CHEST,
            MuscleGroup.LATS,
            MuscleGroup.UPPER_BACK,
            MuscleGroup.QUADS,
            MuscleGroup.HAMSTRINGS,
        )

        for (days in 4..6) {
            val split = Split.forDays(days)
            for (muscle in major) {
                val sessionsHitting = split.days.count { day ->
                    day.slots.any { it.muscle == muscle }
                }
                assertTrue(
                    "$muscle is trained $sessionsHitting time(s) a week on ${split.displayName} " +
                        "at $days days; the spec requires at least 2",
                    sessionsHitting >= 2,
                )
            }
        }
    }

    @Test
    fun `every session opens with a compound`() {
        for (days in 2..6) {
            for (day in Split.forDays(days).days) {
                assertTrue(
                    "${day.label} does not start with a compound",
                    day.slots.first().compound,
                )
            }
        }
    }

    @Test
    fun `compounds are ordered before isolation work within a session`() {
        for (days in 2..6) {
            for (day in Split.forDays(days).days) {
                val firstIsolation = day.slots.indexOfFirst { !it.compound }
                if (firstIsolation == -1) continue
                assertTrue(
                    "${day.label} puts a compound after isolation work",
                    day.slots.drop(firstIsolation).none { it.compound },
                )
            }
        }
    }

    @Test
    fun `no session repeats the same movement intention twice`() {
        for (days in 2..6) {
            for (day in Split.forDays(days).days) {
                assertEquals(
                    "${day.label} lists the same intention twice",
                    day.slots.size,
                    day.slots.toSet().size,
                )
            }
        }
    }

    // ── Volume landmarks ────────────────────────────────────────────

    @Test
    fun `landmarks exist for every muscle the app can train`() {
        val covered = VolumeLandmarks.DEFAULTS.map { it.muscle }.toSet()
        assertEquals(MuscleGroup.entries.toSet(), covered)
    }

    @Test
    fun `landmarks ascend from maintenance through to the recoverable maximum`() {
        for (landmark in VolumeLandmarks.DEFAULTS) {
            val m = landmark.muscle
            assertTrue("$m: MV above MEV", landmark.mv <= landmark.mev)
            assertTrue("$m: MEV above the adaptive range", landmark.mev <= landmark.mavLow)
            assertTrue("$m: adaptive range inverted", landmark.mavLow <= landmark.mavHigh)
            assertTrue("$m: adaptive range above MRV", landmark.mavHigh <= landmark.mrv)
        }
    }
}
