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
 * The progression rules, checked one at a time.
 *
 * [ProgressionRule] was stored and rendered but never branched on, so all three rules
 * behaved as double progression. These tests exist to keep them distinct: each one
 * asserts a case where the rule under test disagrees with the others.
 */
class ProgressionEngineTest {

    private val barbell = EquipmentEntity(
        id = "barbell",
        displayName = "Barbell",
        category = EquipmentCategory.BARBELL,
        loadingScheme = LoadingScheme.PLATE_LOADED,
        barWeightKg = 20.0,
        platePairs = mapOf(25.0 to 2, 20.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2),
    )

    private val dumbbells = EquipmentEntity(
        id = "dumbbells",
        displayName = "Dumbbells",
        category = EquipmentCategory.DUMBBELL,
        loadingScheme = LoadingScheme.FIXED_INCREMENT,
        availableLoads = listOf(5.0, 10.0, 15.0, 20.0, 25.0, 30.0),
    )

    private fun slot(
        repLow: Int = 8,
        repHigh: Int = 12,
        targetRir: Int = 2,
        rule: ProgressionRule = ProgressionRule.DOUBLE_PROGRESSION,
        workingLoadKg: Double? = null,
    ) = TemplateSlotEntity(
        id = 1,
        templateId = 1,
        exerciseId = "bench",
        orderIndex = 0,
        targetSets = 3,
        repRangeLow = repLow,
        repRangeHigh = repHigh,
        targetRir = targetRir,
        restSeconds = 150,
        progression = rule,
        workingLoadKg = workingLoadKg,
    )

    private fun sets(vararg repsToRir: Pair<Int, Int?>, loadKg: Double = 60.0) =
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

    // ── Double progression ──────────────────────────────────────────────

    @Test
    fun `clearing the top of the range on every hard set steps the load up`() {
        val outcome = ProgressionEngine.decide(
            ProgressionRule.DOUBLE_PROGRESSION,
            sets(12 to 1, 12 to 1, 12 to 2),
            slot(),
            barbell,
        )
        assertTrue("Expected Up, got $outcome", outcome is ProgressionOutcome.Up)
        assertEquals(62.5, outcome.nextLoadKg, 1e-6)
    }

    /**
     * The bug this guards: `(it.rir ?: 0) <= 2` read a skipped difficulty prompt as a
     * maximal-effort set and promoted the load off no evidence at all.
     */
    @Test
    fun `an unreported set never earns an increase`() {
        val outcome = ProgressionEngine.decide(
            ProgressionRule.DOUBLE_PROGRESSION,
            sets(12 to 1, 12 to null, 12 to 1),
            slot(),
            barbell,
        )
        assertTrue("Unreported effort must not promote, got $outcome", outcome is ProgressionOutcome.Hold)
        assertEquals(60.0, outcome.nextLoadKg, 1e-6)
    }

    @Test
    fun `a single fatigued set under the range does not drop the load`() {
        val outcome = ProgressionEngine.decide(
            ProgressionRule.DOUBLE_PROGRESSION,
            sets(11 to 1, 9 to 0, 7 to 0),
            slot(),
            barbell,
        )
        assertTrue("One miss out of three is a hold, got $outcome", outcome is ProgressionOutcome.Hold)
        assertEquals(60.0, outcome.nextLoadKg, 1e-6)
    }

    @Test
    fun `most of the work missing the bottom of the range drops the load`() {
        val outcome = ProgressionEngine.decide(
            ProgressionRule.DOUBLE_PROGRESSION,
            sets(9 to 1, 7 to 0, 6 to 0),
            slot(),
            barbell,
        )
        assertTrue("Expected Down, got $outcome", outcome is ProgressionOutcome.Down)
        assertEquals(57.5, outcome.nextLoadKg, 1e-6)
    }

    @Test
    fun `a lone set that misses the range still drops the load`() {
        val outcome = ProgressionEngine.decide(
            ProgressionRule.DOUBLE_PROGRESSION,
            sets(5 to 0),
            slot(),
            barbell,
        )
        assertTrue("Expected Down, got $outcome", outcome is ProgressionOutcome.Down)
    }

    @Test
    fun `mid-range reps hold at the same load`() {
        val outcome = ProgressionEngine.decide(
            ProgressionRule.DOUBLE_PROGRESSION,
            sets(10 to 1, 10 to 1, 9 to 0),
            slot(),
            barbell,
        )
        assertTrue(outcome is ProgressionOutcome.Hold)
        assertEquals(60.0, outcome.nextLoadKg, 1e-6)
    }

    // ── Load progression ────────────────────────────────────────────────

    /**
     * The rules disagree here, which is the whole point: 3 reps of a 3–6 range is
     * mid-range for double progression and a completed prescription for load progression.
     */
    @Test
    fun `load progression steps on the prescribed reps without reaching the range ceiling`() {
        val fixed = slot(repLow = 3, repHigh = 6, targetRir = 2, rule = ProgressionRule.LOAD_PROGRESSION)
        val logged = sets(3 to 2, 3 to 2, 3 to 1, loadKg = 100.0)

        val loadRule = ProgressionEngine.decide(ProgressionRule.LOAD_PROGRESSION, logged, fixed, barbell)
        assertTrue("Expected Up, got $loadRule", loadRule is ProgressionOutcome.Up)
        assertEquals(102.5, loadRule.nextLoadKg, 1e-6)

        val doubleRule = ProgressionEngine.decide(ProgressionRule.DOUBLE_PROGRESSION, logged, fixed, barbell)
        assertTrue("Double progression must hold here", doubleRule is ProgressionOutcome.Hold)
    }

    @Test
    fun `load progression holds when effort was not reported`() {
        val fixed = slot(repLow = 3, repHigh = 6, rule = ProgressionRule.LOAD_PROGRESSION)
        val outcome = ProgressionEngine.decide(
            ProgressionRule.LOAD_PROGRESSION,
            sets(3 to null, 3 to null, 3 to null, loadKg = 100.0),
            fixed,
            barbell,
        )
        assertTrue("Expected Hold, got $outcome", outcome is ProgressionOutcome.Hold)
    }

    @Test
    fun `load progression drops when most sets miss the prescribed reps`() {
        val fixed = slot(repLow = 5, repHigh = 5, rule = ProgressionRule.LOAD_PROGRESSION)
        val outcome = ProgressionEngine.decide(
            ProgressionRule.LOAD_PROGRESSION,
            sets(5 to 1, 3 to 0, 2 to 0, loadKg = 100.0),
            fixed,
            barbell,
        )
        assertTrue("Expected Down, got $outcome", outcome is ProgressionOutcome.Down)
        assertEquals(97.5, outcome.nextLoadKg, 1e-6)
    }

    // ── RIR autoregulation ──────────────────────────────────────────────

    @Test
    fun `rir autoregulation steps up when the sets averaged a full point easier than target`() {
        val auto = slot(targetRir = 2, rule = ProgressionRule.RIR_AUTOREGULATED)
        val outcome = ProgressionEngine.decide(
            ProgressionRule.RIR_AUTOREGULATED,
            sets(9 to 3, 9 to 3, 9 to 3),
            auto,
            barbell,
        )
        assertTrue("Expected Up, got $outcome", outcome is ProgressionOutcome.Up)
        assertEquals(62.5, outcome.nextLoadKg, 1e-6)
    }

    @Test
    fun `rir autoregulation steps down when the sets came in harder than target`() {
        val auto = slot(targetRir = 2, rule = ProgressionRule.RIR_AUTOREGULATED)
        val outcome = ProgressionEngine.decide(
            ProgressionRule.RIR_AUTOREGULATED,
            sets(9 to 1, 9 to 1, 9 to 0),
            auto,
            barbell,
        )
        assertTrue("Expected Down, got $outcome", outcome is ProgressionOutcome.Down)
        assertEquals(57.5, outcome.nextLoadKg, 1e-6)
    }

    @Test
    fun `rir autoregulation holds within a point of target`() {
        val auto = slot(targetRir = 2, rule = ProgressionRule.RIR_AUTOREGULATED)
        val outcome = ProgressionEngine.decide(
            ProgressionRule.RIR_AUTOREGULATED,
            sets(9 to 2, 9 to 2, 9 to 3),
            auto,
            barbell,
        )
        assertTrue("Expected Hold, got $outcome", outcome is ProgressionOutcome.Hold)
        assertEquals(60.0, outcome.nextLoadKg, 1e-6)
    }

    /** Reps alone say nothing about effort, so with no RIR there is nothing to regulate. */
    @Test
    fun `rir autoregulation holds when nothing was reported`() {
        val auto = slot(targetRir = 2, rule = ProgressionRule.RIR_AUTOREGULATED)
        val outcome = ProgressionEngine.decide(
            ProgressionRule.RIR_AUTOREGULATED,
            sets(15 to null, 15 to null, 15 to null),
            auto,
            barbell,
        )
        assertTrue("Expected Hold, got $outcome", outcome is ProgressionOutcome.Hold)
    }

    // ── NONE, and the edges ─────────────────────────────────────────────

    @Test
    fun `the none rule always holds, at the load that was actually used`() {
        val outcome = ProgressionEngine.decide(
            ProgressionRule.NONE,
            sets(20 to 0, 20 to 0, 20 to 0, loadKg = 42.5),
            slot(rule = ProgressionRule.NONE),
            barbell,
        )
        assertTrue(outcome is ProgressionOutcome.Hold)
        assertEquals(42.5, outcome.nextLoadKg, 1e-6)
    }

    @Test
    fun `warmups never decide a progression`() {
        val logged = sets(12 to 1, 12 to 1).map { it.copy(isWarmup = true) } +
            sets(6 to 3, loadKg = 60.0)
        val outcome = ProgressionEngine.decide(
            ProgressionRule.DOUBLE_PROGRESSION,
            logged,
            slot(),
            barbell,
        )
        assertTrue("The working set missed the range, got $outcome", outcome is ProgressionOutcome.Down)
    }

    @Test
    fun `an empty session changes nothing`() {
        val outcome = ProgressionEngine.decide(
            ProgressionRule.DOUBLE_PROGRESSION,
            emptyList(),
            slot(workingLoadKg = 80.0),
            barbell,
        )
        assertTrue(outcome is ProgressionOutcome.Hold)
        assertEquals(80.0, outcome.nextLoadKg, 1e-6)
    }

    /** The user cleared the range on the heaviest dumbbell in the rack. Nothing to add. */
    @Test
    fun `an earned increase the equipment cannot make becomes a hold`() {
        val outcome = ProgressionEngine.decide(
            ProgressionRule.DOUBLE_PROGRESSION,
            sets(12 to 0, 12 to 0, 12 to 1, loadKg = 30.0),
            slot(),
            dumbbells,
        )
        assertTrue("Expected Hold, got $outcome", outcome is ProgressionOutcome.Hold)
        assertEquals(30.0, outcome.nextLoadKg, 1e-6)
        assertTrue(outcome.reason.contains("nothing heavier"))
    }

    @Test
    fun `a lift with no equipment row still steps by a sane default`() {
        val outcome = ProgressionEngine.decide(
            ProgressionRule.DOUBLE_PROGRESSION,
            sets(12 to 1, 12 to 1, 12 to 1),
            slot(),
            equipment = null,
        )
        assertTrue(outcome is ProgressionOutcome.Up)
        assertEquals(62.5, outcome.nextLoadKg, 1e-6)
    }
}
