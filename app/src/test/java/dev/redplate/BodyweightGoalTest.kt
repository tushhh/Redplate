package dev.redplate

import dev.redplate.data.BodyweightEntryEntity
import dev.redplate.data.BodyweightTrends
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class BodyweightGoalTest {

    private val day = TimeUnit.DAYS.toMillis(1)

    /** Daily weigh-ins for [days] days, starting at [startKg] and moving [perDayKg] a day. */
    private fun log(days: Int, startKg: Double, perDayKg: Double) = (0 until days).map { i ->
        BodyweightEntryEntity(measuredAt = i * day, weightKg = startKg + i * perDayKg)
    }

    @Test
    fun noGoalMeansNoProgress() {
        val trend = BodyweightTrends.compute(log(28, 90.0, -0.1))
        assertNull(BodyweightTrends.goalProgress(trend, null, 0L))
    }

    @Test
    fun noWeighInsMeansNoProgress() {
        val trend = BodyweightTrends.compute(emptyList())
        assertNull(BodyweightTrends.goalProgress(trend, 80.0, 0L))
    }

    @Test
    fun losingTowardGoalGivesADate() {
        // Losing 0.1 kg a day. The weekly rate is measured between trailing 7-day
        // averages, so it is read from the trend rather than assumed to be exactly 0.7.
        val trend = BodyweightTrends.compute(log(28, 90.0, -0.1))
        val perWeek = trend.weeklyChangeKg!!
        assertTrue(perWeek < -0.5)
        val now = 28 * day
        val progress = BodyweightTrends.goalProgress(trend, 80.0, now)
        assertNotNull(progress)
        progress!!

        assertEquals(trend.averageKg!! - 80.0, progress.remainingKg, 1e-9)
        assertFalse(progress.reached)
        val weeks = progress.weeksToGo!!
        assertEquals(progress.remainingKg / -perWeek, weeks, 1e-9)
        assertEquals(now + (weeks * 7 * day).toLong(), progress.etaMillis)
    }

    @Test
    fun gainingAwayFromGoalGivesNoDate() {
        val trend = BodyweightTrends.compute(log(28, 90.0, +0.1))
        val progress = BodyweightTrends.goalProgress(trend, 80.0, 0L)!!
        assertNull(progress.weeksToGo)
        assertNull(progress.etaMillis)
    }

    @Test
    fun tooLittleHistoryGivesDistanceButNoDate() {
        val trend = BodyweightTrends.compute(log(5, 90.0, -0.1))
        val progress = BodyweightTrends.goalProgress(trend, 80.0, 0L)!!
        assertTrue(progress.remainingKg > 0)
        assertNull(progress.weeksToGo)
    }

    @Test
    fun withinBandCountsAsReached() {
        val trend = BodyweightTrends.compute(log(28, 80.1, 0.0))
        val progress = BodyweightTrends.goalProgress(trend, 80.0, 0L)!!
        assertTrue(progress.reached)
        assertNull(progress.etaMillis)
    }

    @Test
    fun fastLossWithFallingLiftsIsAMuscleRisk() {
        // 0.2 kg a day ≈ 1.4 kg a week at ~86 kg: well over 1% a week.
        val trend = BodyweightTrends.compute(log(28, 90.0, -0.2))
        assertTrue(BodyweightTrends.losingMuscleRisk(trend, liftsUp = 1, liftsDown = 3))
    }

    @Test
    fun fastLossWithLiftsHoldingIsNotARisk() {
        val trend = BodyweightTrends.compute(log(28, 90.0, -0.2))
        assertFalse(BodyweightTrends.losingMuscleRisk(trend, liftsUp = 4, liftsDown = 1))
    }

    @Test
    fun moderateLossWithFallingLiftsIsNotFlaggedAsMuscleRisk() {
        // 0.05 kg a day ≈ 0.35 kg a week: a sustainable pace; a dip in lifts is noise.
        val trend = BodyweightTrends.compute(log(28, 90.0, -0.05))
        assertFalse(BodyweightTrends.losingMuscleRisk(trend, liftsUp = 0, liftsDown = 3))
    }
}
