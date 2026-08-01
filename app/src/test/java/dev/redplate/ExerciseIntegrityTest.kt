package dev.redplate

import dev.redplate.data.CuratedExerciseSeed
import dev.redplate.data.EquipmentCategory
import dev.redplate.data.ExerciseMediaMap
import dev.redplate.data.GymEquipmentSeed
import dev.redplate.data.MovementPattern
import dev.redplate.data.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exercise seed has to be internally honest: the name, the machine it is assigned to,
 * and the picture it shows all have to describe the same movement.
 *
 * Nothing checked that before. "Standing Cable Row" was illustrated with a *seated*
 * one-arm row on a low-row machine, "Wide-Stance Leg Press" with a narrow-stance one, and
 * "Incline Barbell Bench Press" was assigned to a plate-loaded chest press machine you
 * cannot put a barbell on. A user copies the picture, so a picture that disagrees with the
 * name teaches the wrong lift.
 *
 * A machine cannot be inspected from a unit test, so what is enforced here is everything
 * that *can* be: no stale mappings, no unillustrated name/still contradictions of the kinds
 * already found, and the structural requirements a movement implies.
 */
class ExerciseIntegrityTest {

    private val exercises = CuratedExerciseSeed.seed()
    private val equipment = GymEquipmentSeed.seed().associateBy { it.id }
    private val byId = exercises.associateBy { it.id }

    @Test
    fun `exercise ids are unique`() {
        assertEquals(exercises.size, exercises.map { it.id }.toSet().size)
    }

    @Test
    fun `exercise names are unique`() {
        val duplicates = exercises.groupBy { it.name }.filterValues { it.size > 1 }
        assertTrue("Two lifts cannot share a name: ${duplicates.keys}", duplicates.isEmpty())
    }

    // ── The media map ───────────────────────────────────────────────────

    @Test
    fun `every media mapping points at an exercise that exists`() {
        for (id in ExerciseMediaMap.ASSET_STEMS.keys) {
            assertTrue("Media mapped for unknown exercise '$id'", id in byId)
        }
    }

    /**
     * The removals are load-bearing. Re-adding one of these silently reinstates a picture
     * that shows a different movement, which is exactly the bug the user found.
     */
    @Test
    fun `exercises with a known-wrong still stay unmapped`() {
        for (id in ExerciseMediaMap.DELIBERATELY_UNMAPPED) {
            assertTrue("'$id' should still exist", id in byId)
            assertFalse(
                "'$id' has a still that shows a different movement — see the KDoc on " +
                    "DELIBERATELY_UNMAPPED before mapping it again",
                id in ExerciseMediaMap.ASSET_STEMS,
            )
        }
    }

    /**
     * A name that says "seated" against a still that is standing, or vice versa, is the
     * cheapest possible contradiction to introduce and the hardest to notice.
     */
    @Test
    fun `posture words in a name match the still it is mapped to`() {
        val postures = listOf("Seated", "Standing", "Lying", "Incline", "Decline", "Kneeling")
        for ((id, stem) in ExerciseMediaMap.ASSET_STEMS) {
            val name = byId.getValue(id).name
            val stemWords = stem.replace('_', ' ').replace('-', ' ')
            for (posture in postures) {
                if (!name.contains(posture, ignoreCase = true)) continue
                // Only flag when the still names a *different* posture. A still that is
                // silent about posture is not evidence of a contradiction.
                val conflicting = postures.filter {
                    it != posture && stemWords.contains(it, ignoreCase = true)
                }
                assertTrue(
                    "'$name' is $posture but its still is '$stem' ($conflicting)",
                    conflicting.isEmpty(),
                )
            }
        }
    }

    // ── What a movement structurally requires ───────────────────────────

    /** You cannot press lying down without something to lie on. */
    @Test
    fun `lifts done on a bench name a bench`() {
        val benches = setOf("flat_incline_bench", "decline_bench", "back_extension_bench")
        val needsOne = listOf(
            "barbell_flat_bench", "barbell_close_grip_bench", "barbell_skullcrusher",
            "db_flat_bench", "db_incline_bench", "db_flat_fly", "db_pullover",
            "db_rear_delt_fly", "decline_bench_press", "decline_sit_up", "bench_step_up",
        )
        for (id in needsOne) {
            assertTrue(
                "$id happens on a bench and does not name one",
                byId.getValue(id).requiredEquipmentIds.any { it in benches },
            )
        }
    }

    /** A split squat is not rear-foot-elevated, so it must not demand a bench either. */
    @Test
    fun `a split squat needs no bench`() {
        assertFalse(
            "flat_incline_bench" in byId.getValue("db_bulgarian_split_squat").requiredEquipmentIds,
        )
    }

    /**
     * Landmine work needs a landmine. The floor plan shows none, so the attachment is
     * seeded unavailable and the lifts disappear behind it — fail closed, never guess open.
     */
    @Test
    fun `landmine work is gated behind hardware the gym has not confirmed`() {
        for (id in listOf("landmine_press", "landmine_row")) {
            assertTrue(
                "$id must name the landmine attachment",
                "landmine_attachment" in byId.getValue(id).requiredEquipmentIds,
            )
        }
        assertFalse(
            "The landmine attachment is unconfirmed and must stay unavailable",
            equipment.getValue("landmine_attachment").isAvailable,
        )
    }

    // ── Cardio is not a strength slot ───────────────────────────────────

    /**
     * Rowing is a horizontal pull and the rower is a horizontal-pull machine, so nothing
     * stopped the generator filling Upper A's back compound with "Rowing (Full Body),
     * 3 × 6–10 at 2 in reserve" — a prescription you cannot follow on a Concept2.
     * [ProgramGenerator] now filters these out; this pins which ones they are.
     */
    @Test
    fun `cardio machines are identifiable so the generator can exclude them`() {
        val cardio = equipment.values
            .filter { it.category == EquipmentCategory.CARDIO_MACHINE }
            .map { it.id }
            .toSet()
        val onCardio = exercises
            .filter { ex -> ex.requiredEquipmentIds.any { it in cardio } }
            .map { it.id }
            .toSet()
        assertEquals(
            setOf("stairmill_climbing", "treadmill_incline_walk", "rower_full_body"),
            onCardio,
        )
    }

    // ── Muscles ─────────────────────────────────────────────────────────

    @Test
    fun `no exercise lists its primary muscle as a secondary`() {
        for (exercise in exercises) {
            assertFalse(
                "${exercise.id} credits ${exercise.primaryMuscle} twice",
                exercise.primaryMuscle in exercise.secondaryMuscles,
            )
        }
    }

    @Test
    fun `secondary muscles are not repeated`() {
        for (exercise in exercises) {
            assertEquals(
                "${exercise.id} repeats a secondary muscle",
                exercise.secondaryMuscles.size,
                exercise.secondaryMuscles.toSet().size,
            )
        }
    }

    /** Hip abduction is glute medius. The quads were credited for doing nothing. */
    @Test
    fun `hip abduction credits no quads`() {
        assertFalse(MuscleGroup.QUADS in byId.getValue("machine_hip_abduction").secondaryMuscles)
    }

    /**
     * Every muscle the splits program for needs something in the pool that can fill it,
     * or the intention is silently dropped and the day comes up short.
     */
    @Test
    fun `every muscle a split asks for has at least one exercise`() {
        val programmed = listOf(
            MuscleGroup.CHEST, MuscleGroup.LATS, MuscleGroup.UPPER_BACK,
            MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS, MuscleGroup.REAR_DELTS,
            MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.TRAPS,
            MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES,
            MuscleGroup.CALVES, MuscleGroup.ABS, MuscleGroup.ADDUCTORS,
        )
        for (muscle in programmed) {
            assertTrue(
                "Nothing in the seed trains $muscle",
                exercises.any { it.primaryMuscle == muscle },
            )
        }
    }

    /** A core slot asks for the CORE pattern specifically. */
    @Test
    fun `the abs slots the splits ask for can be filled`() {
        assertTrue(
            exercises.any {
                it.primaryMuscle == MuscleGroup.ABS &&
                    it.pattern == MovementPattern.CORE &&
                    !it.isCompound
            },
        )
    }
}
