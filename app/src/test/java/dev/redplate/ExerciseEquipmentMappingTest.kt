package dev.redplate

import dev.redplate.data.CuratedExerciseSeed
import dev.redplate.data.EquipmentAvailability
import dev.redplate.data.GymEquipmentSeed
import dev.redplate.data.LoadUnit
import dev.redplate.data.LoadingScheme
import dev.redplate.data.PlateMath
import dev.redplate.data.carriesLoad
import dev.redplate.data.limbMultiplier
import dev.redplate.data.loadUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every exercise has to name the station it happens at *and* whatever supplies the load.
 *
 * The seed used to name only one. A barbell squat listed `power_rack` — a BODYWEIGHT
 * fixture with no bar weight and no plates — so the app resolved the load source to
 * something that weighs nothing: no plate stack on squat or bench, and progression
 * stepping in the fixture's 1.25 kg rather than the barbell's 2.5.
 */
class ExerciseEquipmentMappingTest {

    private val equipment = GymEquipmentSeed.seed().associateBy { it.id }
    private val exercises = CuratedExerciseSeed.seed()

    private fun loadSourceFor(exerciseId: String) =
        exercises.first { it.id == exerciseId }
            .requiredEquipmentIds
            .mapNotNull { equipment[it] }
            .firstOrNull { it.carriesLoad }

    @Test
    fun `every exercise names equipment the seed actually defines`() {
        for (exercise in exercises) {
            for (id in exercise.requiredEquipmentIds) {
                assertNotNull("${exercise.id} needs unknown equipment '$id'", equipment[id])
            }
        }
    }

    /**
     * The regression that matters: a loaded lift must resolve to something that weighs
     * something. Bodyweight movements are exempt — a pull-up in a rack is correct.
     */
    @Test
    fun `every loaded lift resolves to equipment that carries load`() {
        val bodyweight = setOf(
            "bench_tricep_dip",
            "decline_sit_up", "hyperextension", "glute_focused_extension", "push_up",
            "plank", "bodyweight_squat", "stairmill_climbing", "treadmill_incline_walk",
            "rower_full_body",
        )
        for (exercise in exercises) {
            if (exercise.id in bodyweight || exercise.requiredEquipmentIds.isEmpty()) continue
            val source = exercise.requiredEquipmentIds
                .mapNotNull { equipment[it] }
                .firstOrNull { it.carriesLoad }
            assertNotNull(
                "${exercise.id} resolves to no load-bearing equipment — " +
                    "it names only ${exercise.requiredEquipmentIds}",
                source,
            )
        }
    }

    @Test
    fun `barbell lifts require the barbell, not just the rack they sit in`() {
        for (id in listOf(
            "barbell_back_squat", "barbell_flat_bench", "barbell_ohp",
            "conventional_deadlift", "barbell_bent_over_row",
        )) {
            val source = loadSourceFor(id)
            assertEquals("$id should be loaded by the barbell", "barbell", source?.id)
            assertEquals(20.0, source?.barWeightKg ?: 0.0, 1e-9)
            assertEquals(LoadingScheme.PLATE_LOADED, source?.loadingScheme)
        }
    }

    /** Turning the barbell off has to remove the squat, not leave it behind the rack. */
    @Test
    fun `a rack without a barbell cannot perform a barbell squat`() {
        val withoutBarbell = EquipmentAvailability.availableIds(
            GymEquipmentSeed.seed().map { if (it.id == "barbell") it.copy(isAvailable = false) else it },
        )
        val squat = exercises.first { it.id == "barbell_back_squat" }
        assertFalse(EquipmentAvailability.canPerform(squat, withoutBarbell))

        val assistedChin = exercises.first { it.id == "assisted_chin_up" }
        assertTrue(
            "An assisted chin needs no barbell",
            EquipmentAvailability.canPerform(assistedChin, withoutBarbell),
        )
    }

    @Test
    fun `dumbbell bench work requires a bench`() {
        for (id in listOf("db_flat_bench", "db_incline_bench", "db_flat_fly")) {
            assertTrue(
                "$id should need a bench",
                "flat_incline_bench" in exercises.first { it.id == id }.requiredEquipmentIds,
            )
        }
    }

    // ── Per-limb loads ──────────────────────────────────────────────────

    @Test
    fun `dumbbells are logged per implement and count twice toward tonnage`() {
        val dumbbells = equipment.getValue("dumbbells")
        assertTrue(dumbbells.perLimb)
        assertEquals(2, dumbbells.limbMultiplier)
    }

    @Test
    fun `a barbell is one implement`() {
        val barbell = equipment.getValue("barbell")
        assertFalse(barbell.perLimb)
        assertEquals(1, barbell.limbMultiplier)
    }

    @Test
    fun `flat dumbbell bench press is a per-limb lift`() {
        assertEquals(2, loadSourceFor("db_flat_bench")?.limbMultiplier)
    }

    // ── Stations and units ──────────────────────────────────────────────

    @Test
    fun `fixtures carry no load and machines do`() {
        for (id in listOf("power_rack", "flat_incline_bench", "deadlift_platform")) {
            assertFalse("$id is a fixture", equipment.getValue(id).carriesLoad)
        }
        for (id in listOf("barbell", "dumbbells", "leg_press_machine", "multigym_lat_pulldown")) {
            assertTrue("$id supplies load", equipment.getValue(id).carriesLoad)
        }
    }

    /**
     * Two of the four multi-gym stations have kilograms printed on the selector plates and
     * two do not. The readout has to say whichever the user is looking at.
     */
    @Test
    fun `only the unmarked multi-gym stations read in levels`() {
        for (id in levelStations) {
            assertEquals("$id is marked in levels", LoadUnit.LEVEL, equipment.getValue(id).loadUnit)
        }
        for (id in kilogramStations) {
            assertEquals(
                "$id has kilograms printed on the stack",
                LoadUnit.KILOGRAMS,
                equipment.getValue(id).loadUnit,
            )
        }
        assertEquals(LoadUnit.KILOGRAMS, equipment.getValue("barbell").loadUnit)
        assertEquals(LoadUnit.KILOGRAMS, equipment.getValue("dumbbells").loadUnit)
    }

    // ── The 4-station multi-gym ─────────────────────────────────────────

    /** Printed in kilograms — read off the plates, not generated. */
    private val kilogramStations = listOf("multigym_low_row", "multigym_lat_pulldown")

    /** Numbered selectors with no mass on them anywhere. */
    private val levelStations = listOf("multigym_cable", "multigym_assist_dip_chin")

    private val multiGymStations = kilogramStations + levelStations

    /**
     * The frame was one row called "4-Station Multi-Gym", which is not somewhere you can
     * walk to. Naming it told the user nothing about which station the lift happens at.
     */
    @Test
    fun `the multi-gym is four stations, not one frame`() {
        assertFalse(
            "the merged frame should be gone",
            equipment.containsKey("four_station_multigym"),
        )
        for (id in multiGymStations) {
            assertNotNull("$id should exist", equipment[id])
        }
        for (id in levelStations) {
            assertEquals(LoadingScheme.RESISTANCE_LEVEL, equipment.getValue(id).loadingScheme)
        }
    }

    /**
     * The low row and lat pulldown stacks are labelled in kilograms, so they are a pin
     * stack with the numbers that are actually on the plates — not a generated ladder and
     * not levels. The spacing is deliberately uneven: 7.5 kg per pin to 50, then 10 kg.
     */
    @Test
    fun `the low row and pulldown carry the stack printed on the machine`() {
        val printed = listOf(
            5.0, 12.5, 20.0, 27.5, 35.0, 42.5, 50.0,
            60.0, 70.0, 80.0, 90.0, 100.0, 110.0, 120.0, 130.0,
        )
        for (id in kilogramStations) {
            val station = equipment.getValue(id)
            assertEquals(LoadingScheme.PIN_STACK, station.loadingScheme)
            assertEquals("$id should carry the printed stack", printed, station.availableLoads)
            assertEquals("the smallest real pin gap", 7.5, station.minIncrement(), 1e-9)
        }
    }

    /** Nothing may prescribe a load that is not a pin position on the stack. */
    @Test
    fun `pulldown loads snap to printed pin positions`() {
        val pulldown = equipment.getValue("multigym_lat_pulldown")
        assertEquals(50.0, pulldown.nearestAchievable(48.0), 1e-9)
        assertEquals(60.0, PlateMath.nextLoadUp(50.0, pulldown), 1e-9)
        assertEquals(42.5, PlateMath.nextLoadDown(50.0, pulldown), 1e-9)
        // Above the top plate there is nowhere further to go.
        assertEquals(130.0, PlateMath.nextLoadUp(130.0, pulldown), 1e-9)
    }

    /**
     * The half rack has no pull-up bar on it, so nothing may hang from it. All three lifts
     * that did are gone; the multi-gym's dip/chin station covers the vertical pull instead.
     *
     * Guarding the whole pool rather than the three ids is the point: any *new* lift that
     * hangs from the rack fails here, which is the mistake that put these three in the seed
     * to begin with.
     */
    @Test
    fun `nothing hangs from the half rack`() {
        for (id in listOf("chin_up", "pull_up", "hanging_leg_raise")) {
            assertFalse(
                "'$id' needs a bar this rack does not have",
                exercises.any { it.id == id },
            )
        }
        assertTrue(exercises.any { it.id == "assisted_chin_up" })

        // What is left on the rack is squatting and pressing, and every one of those needs
        // the barbell. A rack-only lift is a lift that hangs from the bar.
        val rackOnly = exercises.filter { it.requiredEquipmentIds == listOf("power_rack") }
        assertTrue("These would need the bar: ${rackOnly.map { it.id }}", rackOnly.isEmpty())
    }

    /** Only the counterweighted station runs backwards. */
    @Test
    fun `assistance is marked on the dip-chin station and nowhere else`() {
        assertTrue(equipment.getValue("multigym_assist_dip_chin").isAssistance)
        for (other in equipment.values.filter { it.id != "multigym_assist_dip_chin" }) {
            assertFalse("${other.id} should not be assistance", other.isAssistance)
        }
    }

    @Test
    fun `pulldowns and rows point at their own station`() {
        assertEquals("multigym_lat_pulldown", loadSourceFor("lat_pulldown_wide")?.id)
        assertEquals("multigym_lat_pulldown", loadSourceFor("lat_pulldown_close")?.id)
        assertEquals("multigym_low_row", loadSourceFor("seated_cable_row")?.id)
    }

    @Test
    fun `the assisted variants live on the assistance station`() {
        for (id in listOf("assisted_dip", "assisted_chin_up")) {
            val source = loadSourceFor(id)
            assertEquals("$id belongs on the assist station", "multigym_assist_dip_chin", source?.id)
            assertTrue("$id should be marked assisted", source?.isAssistance == true)
        }
    }
}
