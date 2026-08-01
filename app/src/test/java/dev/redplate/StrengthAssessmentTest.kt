package dev.redplate

import dev.redplate.data.EquipmentCategory
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.LiftResponse
import dev.redplate.data.LoadingScheme
import dev.redplate.data.ProgressionRule
import dev.redplate.data.SetLogEntity
import dev.redplate.data.StrengthAssessment
import dev.redplate.data.TemplateSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weekly strength assessment: reading a whole week and rewriting the load from it.
 *
 * The per-session engine moves one notch from one session. That is too slow to correct a
 * load that was picked before there was any evidence — the case week 1 always is. These
 * tests pin the two things that make the difference: that a whole easy week produces a
 * jump bigger than a notch, and that it is still bounded by something sane.
 */
class StrengthAssessmentTest {

    private val barbell = EquipmentEntity(
        id = "barbell",
        displayName = "Barbell",
        category = EquipmentCategory.BARBELL,
        loadingScheme = LoadingScheme.PLATE_LOADED,
        barWeightKg = 20.0,
        platePairs = mapOf(25.0 to 2, 20.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2),
    )

    private val pulldown = EquipmentEntity(
        id = "multigym_lat_pulldown",
        displayName = "Multi-Gym · Lat Pulldown",
        category = EquipmentCategory.MACHINE,
        loadingScheme = LoadingScheme.RESISTANCE_LEVEL,
    )

    private val assist = pulldown.copy(
        id = "multigym_assist_dip_chin",
        displayName = "Multi-Gym · Assisted Dip/Chin",
        isAssistance = true,
    )

    private fun slot(
        repLow: Int = 6,
        repHigh: Int = 10,
        targetRir: Int = 2,
        sets: Int = 3,
        workingLoadKg: Double? = 60.0,
    ) = TemplateSlotEntity(
        id = 1,
        templateId = 1,
        exerciseId = "bench",
        orderIndex = 0,
        targetSets = sets,
        repRangeLow = repLow,
        repRangeHigh = repHigh,
        targetRir = targetRir,
        restSeconds = 150,
        progression = ProgressionRule.DOUBLE_PROGRESSION,
        workingLoadKg = workingLoadKg,
    )

    private fun week(vararg repsToRir: Pair<Int, Int?>, loadKg: Double = 60.0) =
        repsToRir.mapIndexed { index, (reps, rir) ->
            SetLogEntity(
                id = index + 1L,
                sessionId = 1,
                exerciseId = "bench",
                setIndex = index,
                loadKg = loadKg,
                reps = reps,
                rir = rir,
                completedAt = index * 1000L,
            )
        }

    // ── Classifying the week ────────────────────────────────────────────

    @Test
    fun `clearing the top of the range with reps to spare reads as under-loaded`() {
        val assessment = StrengthAssessment.assess(
            "bench",
            week(10 to 3, 10 to 3, 10 to 4),
            slot(),
        )
        assertEquals(LiftResponse.UNDERLOADED, assessment.response)
    }

    /** One rep in reserve above target is inside the noise, not evidence. */
    @Test
    fun `hitting the range at the prescribed effort reads as on target`() {
        val assessment = StrengthAssessment.assess(
            "bench",
            week(8 to 2, 8 to 2, 7 to 1),
            slot(),
        )
        assertEquals(LiftResponse.ON_TARGET, assessment.response)
    }

    @Test
    fun `missing the bottom of the range reads as overreached`() {
        val assessment = StrengthAssessment.assess(
            "bench",
            week(5 to 0, 4 to 0, 4 to 0),
            slot(),
        )
        assertEquals(LiftResponse.OVERREACHED, assessment.response)
    }

    /** Holding the reps by working far harder than prescribed is still overreaching. */
    @Test
    fun `grinding every set to failure reads as overreached even at the right reps`() {
        val assessment = StrengthAssessment.assess(
            "bench",
            week(8 to 0, 8 to 0, 8 to 1),
            slot(),
        )
        assertEquals(LiftResponse.OVERREACHED, assessment.response)
    }

    /**
     * The same invariant the per-session engine holds: a skipped difficulty prompt is
     * missing information, not a maximal set.
     */
    @Test
    fun `an unreported week concludes nothing`() {
        val assessment = StrengthAssessment.assess(
            "bench",
            week(12 to null, 12 to null, 12 to null),
            slot(),
        )
        assertEquals(LiftResponse.UNKNOWN, assessment.response)
        assertNull(StrengthAssessment.calibrate(assessment, slot(), barbell))
    }

    @Test
    fun `one set is not a week`() {
        val assessment = StrengthAssessment.assess("bench", week(10 to 3), slot())
        assertEquals(LiftResponse.UNKNOWN, assessment.response)
    }

    // ── Estimating the strength behind it ───────────────────────────────

    /**
     * Reps in reserve are part of the evidence. Ignoring them under-estimates precisely
     * the lifts that are too light, which are the ones this exists to find.
     */
    @Test
    fun `reps in reserve count toward the estimated max`() {
        val withReserve = StrengthAssessment.estimated1Rm(week(10 to 3, 10 to 3))!!
        val toFailure = StrengthAssessment.estimated1Rm(week(10 to 0, 10 to 0))!!
        assertTrue("3 in reserve should read stronger", withReserve > toFailure)
        // 60 kg × (1 + 13/30)
        assertEquals(86.0, withReserve, 0.01)
    }

    @Test
    fun `the estimate takes the best set, not the average`() {
        val sets = week(10 to 2, 10 to 2, 10 to 2) +
            week(3 to 2, 3 to 2, loadKg = 30.0)
        assertEquals(
            StrengthAssessment.estimated1Rm(week(10 to 2, 10 to 2))!!,
            StrengthAssessment.estimated1Rm(sets)!!,
            0.01,
        )
    }

    // ── Rewriting the load ──────────────────────────────────────────────

    /** The point of the whole exercise: a week of easy sets is worth more than one notch. */
    @Test
    fun `an under-loaded week jumps further than the per-session engine would`() {
        val assessment = StrengthAssessment.assess("bench", week(10 to 3, 10 to 3, 10 to 3), slot())
        val calibration = StrengthAssessment.calibrate(assessment, slot(), barbell)

        assertNotNull(calibration)
        assertTrue(
            "Should beat the 2.5 kg the per-session engine steps, got ${calibration!!.toKg}",
            calibration.toKg >= 65.0,
        )
        assertTrue(calibration.isIncrease)
    }

    /** Bounded, because the arithmetic will cheerfully suggest a number nobody should lift. */
    @Test
    fun `no calibration moves more than ten percent in a week`() {
        val ridiculous = week(12 to 5, 12 to 5, 12 to 5)
        val assessment = StrengthAssessment.assess("bench", ridiculous, slot())
        val calibration = StrengthAssessment.calibrate(assessment, slot(), barbell)!!
        assertTrue(
            "60 kg + 10% is 66, snapped to 65; got ${calibration.toKg}",
            calibration.toKg <= 66.0,
        )
    }

    /**
     * The bound has to survive the snapping step, in both directions and at every load.
     *
     * Rounding to the *nearest* loadable weight broke this: from 60 kg an overreached week
     * snapped to 52.5, a 12.5% drop, while the code claimed a 10% one. A bound the last
     * step is allowed to breach is not a bound.
     */
    @Test
    fun `the ten percent bound survives snapping at every load`() {
        var load = 25.0
        while (load <= 200.0) {
            for (evidence in listOf(
                week(12 to 5, 12 to 5, 12 to 5, loadKg = load),
                week(3 to 0, 2 to 0, 2 to 0, loadKg = load),
            )) {
                val at = slot(workingLoadKg = load)
                val assessment = StrengthAssessment.assess("bench", evidence, at)
                val calibration = StrengthAssessment.calibrate(assessment, at, barbell) ?: continue
                val change = (calibration.toKg - load) / load
                assertTrue(
                    "From $load kg went to ${calibration.toKg} kg — ${(change * 100).toInt()}%",
                    kotlin.math.abs(change) <= StrengthAssessment.MAX_WEEKLY_CHANGE + 1e-9,
                )
            }
            load += 2.5
        }
    }

    @Test
    fun `an overreached week brings the load down`() {
        val assessment = StrengthAssessment.assess("bench", week(4 to 0, 4 to 0, 3 to 0), slot())
        val calibration = StrengthAssessment.calibrate(assessment, slot(), barbell)!!
        assertTrue("Should come down from 60, got ${calibration.toKg}", calibration.toKg < 60.0)
        assertTrue("And not fall off a cliff", calibration.toKg >= 54.0)
    }

    /** On target is the per-session engine's business. Two rules on one number is one too many. */
    @Test
    fun `an on-target week is left alone`() {
        val assessment = StrengthAssessment.assess("bench", week(8 to 2, 8 to 2, 8 to 2), slot())
        assertNull(StrengthAssessment.calibrate(assessment, slot(), barbell))
    }

    @Test
    fun `the calibrated load is one the equipment can actually make`() {
        val assessment = StrengthAssessment.assess("bench", week(10 to 3, 10 to 3, 10 to 3), slot())
        val calibration = StrengthAssessment.calibrate(assessment, slot(), barbell)!!
        assertEquals(
            "Should sit on a real plate combination",
            calibration.toKg,
            barbell.nearestAchievable(calibration.toKg),
            1e-9,
        )
    }

    // ── Machines that are not marked in kilograms ───────────────────────

    /**
     * A level is ordinal. Running level 8 through Epley produces a number that means
     * nothing, so those machines move in notches instead.
     */
    @Test
    fun `a level-marked machine moves two notches rather than a computed weight`() {
        val levelSlot = slot(workingLoadKg = 8.0)
        val assessment = StrengthAssessment.assess("bench", week(10 to 3, 10 to 3, 10 to 3, loadKg = 8.0), levelSlot)
        val calibration = StrengthAssessment.calibrate(assessment, levelSlot, pulldown)!!
        assertEquals(10.0, calibration.toKg, 1e-9)
    }

    @Test
    fun `an easy week on the assist station takes assistance off`() {
        val assistSlot = slot(workingLoadKg = 8.0)
        val assessment = StrengthAssessment.assess("bench", week(10 to 3, 10 to 3, 10 to 3, loadKg = 8.0), assistSlot)
        val calibration = StrengthAssessment.calibrate(assessment, assistSlot, assist)!!
        assertEquals("Less counterweight is a harder set", 6.0, calibration.toKg, 1e-9)
    }

    @Test
    fun `a hard week on the assist station puts assistance back on`() {
        val assistSlot = slot(workingLoadKg = 8.0)
        val assessment = StrengthAssessment.assess("bench", week(4 to 0, 4 to 0, 3 to 0, loadKg = 8.0), assistSlot)
        val calibration = StrengthAssessment.calibrate(assessment, assistSlot, assist)!!
        assertEquals(9.0, calibration.toKg, 1e-9)
    }

    // ── Explaining itself ───────────────────────────────────────────────

    @Test
    fun `every calibration carries its reasoning`() {
        val assessment = StrengthAssessment.assess("bench", week(10 to 3, 10 to 3, 10 to 3), slot())
        val calibration = StrengthAssessment.calibrate(assessment, slot(), barbell)!!
        assertTrue(
            "The reason should name the evidence, got: ${calibration.reason}",
            calibration.reason.contains("10 reps") && calibration.reason.contains("in reserve"),
        )
    }
}
