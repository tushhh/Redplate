package dev.redplate

import dev.redplate.data.EquipmentCategory
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.LoadingScheme
import dev.redplate.data.ProgressionEngine
import dev.redplate.data.ProgressionOutcome
import dev.redplate.data.ProgressionRule
import dev.redplate.data.SetLogEntity
import dev.redplate.data.TemplateSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assisted dip/chin station, where the number on the machine runs backwards.
 *
 * The counterweight takes part of your bodyweight, so a higher level is an *easier* set.
 * Every rule in [ProgressionEngine] would otherwise raise that number after a good
 * session — congratulating the user for doing less work, and walking the assistance up
 * until the machine was doing all of it.
 */
class AssistanceProgressionTest {

    private val assist = EquipmentEntity(
        id = "multigym_assist_dip_chin",
        displayName = "Multi-Gym · Assisted Dip/Chin",
        category = EquipmentCategory.MACHINE,
        loadingScheme = LoadingScheme.RESISTANCE_LEVEL,
        isAssistance = true,
    )

    private val pulldown = assist.copy(
        id = "multigym_lat_pulldown",
        displayName = "Multi-Gym · Lat Pulldown",
        isAssistance = false,
    )

    private fun slot(rule: ProgressionRule = ProgressionRule.DOUBLE_PROGRESSION) =
        TemplateSlotEntity(
            id = 1,
            templateId = 1,
            exerciseId = "assisted_chin_up",
            orderIndex = 0,
            targetSets = 3,
            repRangeLow = 6,
            repRangeHigh = 10,
            targetRir = 2,
            restSeconds = 150,
            progression = rule,
            workingLoadKg = null,
        )

    private fun sets(vararg repsToRir: Pair<Int, Int?>, level: Double) =
        repsToRir.mapIndexed { index, (reps, rir) ->
            SetLogEntity(
                id = index + 1L,
                sessionId = 1,
                exerciseId = "assisted_chin_up",
                setIndex = index,
                loadKg = level,
                reps = reps,
                rir = rir,
                completedAt = index * 1000L,
            )
        }

    private fun decide(
        equipment: EquipmentEntity,
        rule: ProgressionRule = ProgressionRule.DOUBLE_PROGRESSION,
        vararg repsToRir: Pair<Int, Int?>,
        level: Double = 8.0,
    ) = ProgressionEngine.decide(rule, sets(*repsToRir, level = level), slot(rule), equipment)

    // ── Getting stronger means less help ────────────────────────────────

    @Test
    fun `clearing the range takes assistance off, not on`() {
        val outcome = decide(assist, repsToRir = arrayOf(10 to 1, 10 to 1, 10 to 0))
        assertTrue("A cleared range should progress", outcome is ProgressionOutcome.Up)
        assertEquals(7.0, outcome.nextLoadKg, 1e-9)
        assertTrue(
            "The reason should say the assistance came down, got: ${outcome.reason}",
            outcome.reason.contains("assistance comes down"),
        )
    }

    /** The same session on an unassisted station goes the other way. */
    @Test
    fun `an ordinary station steps the level up on the same session`() {
        val outcome = decide(pulldown, repsToRir = arrayOf(10 to 1, 10 to 1, 10 to 0))
        assertTrue(outcome is ProgressionOutcome.Up)
        assertEquals(9.0, outcome.nextLoadKg, 1e-9)
    }

    @Test
    fun `missing the range puts assistance back on`() {
        val outcome = decide(assist, repsToRir = arrayOf(4 to 0, 3 to 0, 3 to 0))
        assertTrue("A missed range should back off", outcome is ProgressionOutcome.Down)
        assertEquals(9.0, outcome.nextLoadKg, 1e-9)
        assertTrue(
            "The reason should say the assistance went up, got: ${outcome.reason}",
            outcome.reason.contains("assistance goes up"),
        )
    }

    /**
     * Level zero is the whole point of the machine: no counterweight left means the user is
     * doing the movement unassisted, and the app should say so rather than inventing a
     * negative level.
     */
    @Test
    fun `there is nothing below unassisted`() {
        val outcome = decide(assist, repsToRir = arrayOf(10 to 1, 10 to 1, 10 to 1), level = 0.0)
        assertTrue("Level zero cannot progress further", outcome is ProgressionOutcome.Hold)
        assertEquals(0.0, outcome.nextLoadKg, 1e-9)
        assertTrue(
            "The reason should name the milestone, got: ${outcome.reason}",
            outcome.reason.contains("unassisted"),
        )
    }

    // ── Every rule, not just double progression ─────────────────────────

    @Test
    fun `load progression also runs backwards on assistance`() {
        val outcome = decide(
            assist,
            rule = ProgressionRule.LOAD_PROGRESSION,
            repsToRir = arrayOf(8 to 1, 8 to 1, 7 to 2),
        )
        assertTrue(outcome is ProgressionOutcome.Up)
        assertEquals(7.0, outcome.nextLoadKg, 1e-9)
    }

    @Test
    fun `RIR autoregulation also runs backwards on assistance`() {
        // Well easier than the target: take help away.
        val easy = decide(
            assist,
            rule = ProgressionRule.RIR_AUTOREGULATED,
            repsToRir = arrayOf(8 to 4, 8 to 4, 8 to 3),
        )
        assertTrue(easy is ProgressionOutcome.Up)
        assertEquals(7.0, easy.nextLoadKg, 1e-9)

        // Harder than prescribed: give help back.
        val hard = decide(
            assist,
            rule = ProgressionRule.RIR_AUTOREGULATED,
            repsToRir = arrayOf(8 to 0, 8 to 0, 8 to 1),
        )
        assertTrue(hard is ProgressionOutcome.Down)
        assertEquals(9.0, hard.nextLoadKg, 1e-9)
    }
}
