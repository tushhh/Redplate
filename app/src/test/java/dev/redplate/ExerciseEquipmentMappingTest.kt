package dev.redplate

import dev.redplate.data.CuratedExerciseSeed
import dev.redplate.data.EquipmentAvailability
import dev.redplate.data.GymEquipmentSeed
import dev.redplate.data.LoadUnit
import dev.redplate.data.LoadingScheme
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
            "pull_up", "chin_up", "hanging_leg_raise", "bench_tricep_dip", "bench_step_up",
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

        val pullUp = exercises.first { it.id == "pull_up" }
        assertTrue("A pull-up needs no barbell", EquipmentAvailability.canPerform(pullUp, withoutBarbell))
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
        for (id in listOf("barbell", "dumbbells", "leg_press_machine", "four_station_multigym")) {
            assertTrue("$id supplies load", equipment.getValue(id).carriesLoad)
        }
    }

    @Test
    fun `the multi-gym reads in levels and everything else in kilograms`() {
        assertEquals(LoadUnit.LEVEL, equipment.getValue("four_station_multigym").loadUnit)
        assertEquals(LoadUnit.KILOGRAMS, equipment.getValue("barbell").loadUnit)
        assertEquals(LoadUnit.KILOGRAMS, equipment.getValue("dumbbells").loadUnit)
    }
}
