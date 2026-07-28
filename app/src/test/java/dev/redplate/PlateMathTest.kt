package dev.redplate

import dev.redplate.data.EquipmentCategory
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.LoadingScheme
import dev.redplate.data.PlateMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plate maths backs both the plate stack and every progression rule, so an error here
 * shows up as a weight the user cannot physically load. COACHING.md §2 calls prescribing
 * an impossible load the single most common failure in generated programs.
 */
class PlateMathTest {

    private val barbell = EquipmentEntity(
        id = "barbell",
        displayName = "Barbell",
        category = EquipmentCategory.BARBELL,
        loadingScheme = LoadingScheme.PLATE_LOADED,
        barWeightKg = 20.0,
        platePairs = mapOf(25.0 to 4, 20.0 to 4, 15.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2),
    )

    private val dumbbells = EquipmentEntity(
        id = "dumbbells",
        displayName = "Dumbbells",
        category = EquipmentCategory.DUMBBELL,
        loadingScheme = LoadingScheme.FIXED_INCREMENT,
        availableLoads = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0),
    )

    private val pinStack = EquipmentEntity(
        id = "cable",
        displayName = "Cable",
        category = EquipmentCategory.MACHINE,
        loadingScheme = LoadingScheme.PIN_STACK,
        availableLoads = listOf(5.0, 10.0, 15.0, 20.0, 25.0),
    )

    // ── Loading the bar ─────────────────────────────────────────────

    @Test
    fun `loads a round weight exactly`() {
        val load = PlateMath.load(100.0, barbell)

        assertEquals(100.0, load.totalKg, 1e-9)
        assertEquals(listOf(25.0, 15.0), load.perSide)
        assertTrue(load.exact)
    }

    @Test
    fun `an empty bar carries no plates and is exact`() {
        val load = PlateMath.load(20.0, barbell)

        assertEquals(20.0, load.totalKg, 1e-9)
        assertTrue(load.perSide.isEmpty())
        assertTrue(load.exact)
    }

    @Test
    fun `a target under the bar reports the bar and is not exact`() {
        val load = PlateMath.load(15.0, barbell)

        assertEquals(20.0, load.totalKg, 1e-9)
        assertFalse(load.exact)
    }

    @Test
    fun `plates are chosen heaviest first so the stack renders in rack order`() {
        val perSide = PlateMath.load(160.0, barbell).perSide

        assertEquals(perSide.sortedDescending(), perSide)
    }

    @Test
    fun `never uses more pairs than the gym owns`() {
        // Only two 15s exist, and one pair each of the small change.
        val used = PlateMath.load(300.0, barbell).perSide.groupingBy { it }.eachCount()

        used.forEach { (plate, count) ->
            val owned = barbell.platePairs.getValue(plate)
            assertTrue("Used $count x $plate kg but only $owned pairs exist", count <= owned)
        }
    }

    @Test
    fun `an unreachable target reports the closest loadable weight, flagged inexact`() {
        val load = PlateMath.load(101.0, barbell)

        assertFalse(load.exact)
        assertTrue("Never overshoots the target", load.totalKg <= 101.0)
        assertEquals(load.totalKg, PlateMath.largestLoadableAtOrBelow(101.0, barbell), 1e-9)
    }

    /**
     * The case greedy gets wrong. One pair of 10s and two pairs of 7.5s: greedy takes the
     * 10 and strands the remaining 5 kg, reaching 40. Two 7.5s make the 50 exactly.
     *
     * The old comment claimed greedy was optimal "because each denomination >= 2x the next
     * smaller" — untrue even of a standard 25/20/15/10/5/2.5/1.25 set, and this stock
     * breaks it outright.
     */
    @Test
    fun `lopsided plate stock still reaches a weight greedy would miss`() {
        val lopsided = barbell.copy(platePairs = mapOf(10.0 to 1, 7.5 to 2))
        val load = PlateMath.load(50.0, lopsided)

        assertEquals(50.0, load.totalKg, 1e-9)
        assertEquals(listOf(7.5, 7.5), load.perSide)
        assertTrue(load.exact)
    }

    @Test
    fun `the exact search never overshoots or spends plates it does not have`() {
        val lopsided = barbell.copy(platePairs = mapOf(10.0 to 1, 7.5 to 2))
        for (target in listOf(30.0, 35.0, 42.5, 47.5, 50.0, 60.0, 100.0)) {
            val load = PlateMath.load(target, lopsided)
            assertTrue("Overshot $target", load.totalKg <= target + 1e-9)
            for ((plate, count) in load.perSide.groupingBy { it }.eachCount()) {
                assertTrue(
                    "Used $count x $plate kg at target $target",
                    count <= lopsided.platePairs.getValue(plate),
                )
            }
        }
    }

    // ── Stepping ────────────────────────────────────────────────────

    @Test
    fun `stepping a barbell up lands on a loadable weight above the current one`() {
        val next = PlateMath.nextLoadUp(100.0, barbell)

        assertTrue(next > 100.0)
        assertEquals(next, PlateMath.largestLoadableAtOrBelow(next, barbell), 1e-9)
    }

    @Test
    fun `stepping never returns the weight it started from`() {
        var load = 60.0
        repeat(8) {
            val next = PlateMath.nextLoadUp(load, barbell)
            assertTrue("Stepping up stalled at $load", next > load)
            load = next
        }
    }

    @Test
    fun `dumbbells step to sizes that exist, never between them`() {
        assertEquals(12.0, PlateMath.nextLoadUp(10.0, dumbbells), 1e-9)
        assertEquals(10.0, PlateMath.nextLoadDown(12.0, dumbbells), 1e-9)
    }

    @Test
    fun `stepping past the end of the rack holds rather than inventing a weight`() {
        assertEquals(20.0, PlateMath.nextLoadUp(20.0, dumbbells), 1e-9)
        assertEquals(10.0, PlateMath.nextLoadDown(10.0, dumbbells), 1e-9)
    }

    @Test
    fun `a pin stack only stops where a pin exists`() {
        assertEquals(20.0, PlateMath.nextLoadUp(15.0, pinStack), 1e-9)
        assertEquals(15.0, pinStack.nearestAchievable(16.0), 1e-9)
    }

    // ── Increments and deloads ──────────────────────────────────────

    @Test
    fun `smallest barbell step is a pair of the smallest plates`() {
        assertEquals(2.5, barbell.minIncrement(), 1e-9)
    }

    @Test
    fun `smallest dumbbell step is the gap between sizes owned`() {
        assertEquals(2.0, dumbbells.minIncrement(), 1e-9)
    }

    @Test
    fun `a ten percent deload snaps to something that can be loaded`() {
        val deloaded = PlateMath.deload(100.0, 0.1, barbell)

        assertEquals(deloaded, PlateMath.largestLoadableAtOrBelow(deloaded, barbell), 1e-9)
        assertTrue(deloaded < 100.0)
    }

    @Test
    fun `a deload on dumbbells lands on a dumbbell that exists`() {
        assertTrue(PlateMath.deload(20.0, 0.1, dumbbells) in dumbbells.availableLoads)
    }
}
