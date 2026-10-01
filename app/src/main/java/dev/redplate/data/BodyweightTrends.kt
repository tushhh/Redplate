package dev.redplate.data

import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** One weigh-in with the seven-day average that ends at it. */
data class BodyweightPoint(
    val measuredAt: Long,
    val weightKg: Double,
    val averageKg: Double,
)

/**
 * The bodyweight log read as a trend.
 *
 * A single weigh-in swings a kilo or more with water, salt and what was eaten the night
 * before, so nothing here reads one entry on its own: every number is built from seven-day
 * averages. The rate is stated against bodyweight because half a kilo a week means
 * something different at 60 kg than at 100.
 */
data class BodyweightTrend(
    val points: List<BodyweightPoint>,
    val latestKg: Double?,
    val averageKg: Double?,
    /** Average change per week across up to the last four weeks; null until there is two weeks of averaged data. */
    val weeklyChangeKg: Double?,
    val weeklyChangePercent: Double?,
    val latestWaistCm: Double?,
    /** Change in waist over up to the last four weeks; null without two measurements. */
    val waistChangeCm: Double?,
) {
    val hasEnoughHistory: Boolean get() = weeklyChangeKg != null
}

object BodyweightTrends {

    private val DAY_MS = TimeUnit.DAYS.toMillis(1)
    private val WEEK_MS = 7 * DAY_MS

    /** Two weeks before a rate is worth saying out loud. */
    private val MIN_SPAN_MS = 14 * DAY_MS

    /** Look back at most this far for the rate, so an old cut does not blur a new one. */
    private val WINDOW_MS = 28 * DAY_MS

    private const val MIN_BASELINE_ENTRIES = 3

    fun compute(entries: List<BodyweightEntryEntity>): BodyweightTrend {
        val sorted = entries.sortedBy { it.measuredAt }
        if (sorted.isEmpty()) {
            return BodyweightTrend(emptyList(), null, null, null, null, null, null)
        }

        val points = sorted.map { entry ->
            val window = sorted.filter {
                it.measuredAt in (entry.measuredAt - WEEK_MS + 1)..entry.measuredAt
            }
            BodyweightPoint(entry.measuredAt, entry.weightKg, window.map { it.weightKg }.average())
        }

        val latest = points.last()
        val windowStart = latest.measuredAt - WINDOW_MS
        // The earliest average inside the window that rests on a few weigh-ins — an
        // average of one entry is just an entry. Weighing in daily, that is day three, so
        // the rate appears about two weeks after that.
        val baseline = points.firstOrNull { point ->
            point.measuredAt >= windowStart &&
                sorted.count { it.measuredAt in (point.measuredAt - WEEK_MS + 1)..point.measuredAt } >= MIN_BASELINE_ENTRIES
        } ?: points.firstOrNull { it.measuredAt >= windowStart }

        val span = baseline?.let { latest.measuredAt - it.measuredAt } ?: 0L
        val weekly = if (baseline != null && span >= MIN_SPAN_MS) {
            (latest.averageKg - baseline.averageKg) / (span.toDouble() / WEEK_MS)
        } else {
            null
        }

        val waists = sorted.filter { it.waistCm != null }
        val lastWaist = waists.lastOrNull()
        val firstWaistInWindow = waists.firstOrNull { it.measuredAt >= (lastWaist?.measuredAt ?: 0L) - WINDOW_MS }
        val waistChange = if (lastWaist != null && firstWaistInWindow != null && firstWaistInWindow != lastWaist) {
            lastWaist.waistCm!! - firstWaistInWindow.waistCm!!
        } else {
            null
        }

        return BodyweightTrend(
            points = points,
            latestKg = latest.weightKg,
            averageKg = latest.averageKg,
            weeklyChangeKg = weekly,
            weeklyChangePercent = weekly?.let { it / latest.averageKg * 100 },
            latestWaistCm = lastWaist?.waistCm,
            waistChangeCm = waistChange,
        )
    }

    /**
     * One plain sentence about the rate. Facts and the one rule of thumb that matters for
     * keeping strength while getting leaner — never a target, never a calorie.
     */
    fun describe(trend: BodyweightTrend): String {
        val percent = trend.weeklyChangePercent
        val kg = trend.weeklyChangeKg
        if (percent == null || kg == null) {
            return if (trend.points.isEmpty()) {
                "Weigh in first thing in the morning, a few days a week. The trend is what " +
                    "counts — any single day can be a kilo off."
            } else {
                "Keep weighing in a few mornings a week. The rate appears after two to three weeks of weigh-ins."
            }
        }
        val perWeek = String.format(java.util.Locale.ROOT, "%.1f", abs(kg))
        return when {
            percent <= -1.0 ->
                "Down $perWeek kg a week — more than 1% of your bodyweight. That pace usually " +
                    "costs strength; if the lifts start stalling, this is why."

            percent <= -0.25 ->
                "Down $perWeek kg a week. A pace that lets you get leaner and keep the bar moving."

            percent < 0.25 ->
                "Holding steady. You can still get leaner here — watch the waist and the lifts, " +
                    "not just the scale."

            else -> "Up $perWeek kg a week."
        }
    }
}
