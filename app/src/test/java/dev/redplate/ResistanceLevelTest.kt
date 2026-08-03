package dev.redplate

import dev.redplate.data.EquipmentCategory
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.LoadUnit
import dev.redplate.data.LoadingScheme
import dev.redplate.data.PlateMath
import dev.redplate.data.loadUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Machines marked in numbered resistance rather than mass.
 *
 * The multi-gym stations were seeded as a pin stack with an invented 5–100 kg ladder, so
 * the app displayed a kilogram figure printed nowhere on the machine and refused to record
 * the level the user had actually set.
 *
 * The low row and lat pulldown have since been read off the plates and are back on real
 * kilograms; the cable and assisted dip/chin stations genuinely have no mass printed on
 * them, and the cable stands in for that case here.
 */
class ResistanceLevelTest {

    private val multigym = EquipmentEntity(
        id = "multigym_cable",
        displayName = "Multi-Gym · Cable",
        category = EquipmentCategory.MACHINE,
        loadingScheme = LoadingScheme.RESISTANCE_LEVEL,
    )

    private val cable = EquipmentEntity(
        id = "cable",
        displayName = "Cable",
        category = EquipmentCategory.MACHINE,
        loadingScheme = LoadingScheme.PIN_STACK,
        availableLoads = listOf(5.0, 10.0, 15.0, 20.0),
    )

    @Test
    fun `a level-marked machine reads in levels, not kilograms`() {
        assertEquals(LoadUnit.LEVEL, multigym.loadUnit)
        assertEquals("LEVEL", multigym.loadUnit.label)
        assertEquals(LoadUnit.KILOGRAMS, cable.loadUnit)
    }

    @Test
    fun `levels step one notch at a time`() {
        assertEquals(8.0, PlateMath.nextLoadUp(7.0, multigym), 1e-9)
        assertEquals(6.0, PlateMath.nextLoadDown(7.0, multigym), 1e-9)
        assertEquals(1.0, multigym.minIncrement(), 1e-9)
    }

    /** There is no highest level the app can know about, so stepping never stalls. */
    @Test
    fun `stepping up a level never stalls`() {
        var level = 1.0
        repeat(30) {
            val next = PlateMath.nextLoadUp(level, multigym)
            assertTrue("Stalled at level $level", next > level)
            level = next
        }
    }

    @Test
    fun `levels never go below zero`() {
        assertEquals(0.0, PlateMath.nextLoadDown(0.0, multigym), 1e-9)
        assertEquals(0.0, multigym.largestLoadableAtOrBelow(0.4), 1e-9)
    }

    /** A half-level does not exist, so anything fractional resolves to a whole notch. */
    @Test
    fun `levels resolve to whole numbers`() {
        assertEquals(7.0, multigym.nearestAchievable(6.6), 1e-9)
        assertEquals(6.0, multigym.nearestAchievable(6.4), 1e-9)
        assertEquals(6.0, multigym.largestLoadableAtOrBelow(6.9), 1e-9)
    }

    /** Stepping from a typed, off-notch value lands on a real notch rather than drifting. */
    @Test
    fun `stepping from a fractional level snaps onto the notches`() {
        assertEquals(8.0, PlateMath.nextLoadUp(7.5, multigym), 1e-9)
        assertEquals(7.0, PlateMath.nextLoadDown(7.5, multigym), 1e-9)
    }

    @Test
    fun `a level machine carries no invented weight ladder`() {
        assertTrue(
            "A level-marked machine has no kilogram ladder to snap to",
            multigym.availableLoads.isEmpty(),
        )
    }
}
