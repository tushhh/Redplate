package dev.redplate.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that decides which day a workout belongs to.
 *
 * A training day is not a calendar day. Sessions run past midnight, and a set logged at
 * 02:00 belongs to the evening it started in, not to the date the clock rolled over to.
 * Every screen that groups, filters or schedules by day goes through here, so Today, the
 * week plan and History cannot disagree about when "yesterday" was.
 *
 * The boundary hour is [ProfileEntity.dayStartHour], user-configurable, defaulting to
 * [DEFAULT_DAY_START_HOUR]. Everything before it rolls back one day.
 *
 * Export timestamps deliberately do **not** come through here: a CSV column called
 * `completed_at` must be literal wall-clock time or it stops meaning anything to a
 * spreadsheet.
 */
@Singleton
class TrainingClock internal constructor(private val zone: ZoneId) {

    /** Production always reads the device zone; the zone-taking constructor is for tests. */
    @Inject
    constructor() : this(ZoneId.systemDefault())

    /** The training date [epochMillis] belongs to. */
    fun trainingDate(epochMillis: Long, dayStartHour: Int): LocalDate =
        Instant.ofEpochMilli(epochMillis)
            .atZone(zone)
            .minusHours(dayStartHour.toLong())
            .toLocalDate()

    fun today(dayStartHour: Int, now: Long = System.currentTimeMillis()): LocalDate =
        trainingDate(now, dayStartHour)

    /** Monday of the training week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    /** The training week a moment belongs to, as its Monday. */
    fun weekStart(epochMillis: Long, dayStartHour: Int): LocalDate =
        weekStart(trainingDate(epochMillis, dayStartHour))

    /**
     * Inclusive-exclusive epoch bounds of a training day, for range queries.
     * `until` is the first millisecond of the next training day.
     */
    fun dayBounds(date: LocalDate, dayStartHour: Int): LongRange {
        val start = date.atStartOfDay(zone).plusHours(dayStartHour.toLong()).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).plusHours(dayStartHour.toLong())
            .toInstant().toEpochMilli()
        return start until end
    }

    /** Bounds of a whole training week, Monday through Sunday inclusive. */
    fun weekBounds(weekStart: LocalDate, dayStartHour: Int): LongRange {
        val start = dayBounds(weekStart, dayStartHour).first
        val end = dayBounds(weekStart.plusDays(6), dayStartHour).last + 1
        return start until end
    }

    /** 0 = Monday, matching [SessionTemplateEntity.dayIndex]. */
    fun weekdayIndex(date: LocalDate): Int = date.dayOfWeek.value - 1

    companion object {
        /**
         * 04:00. A session at 02:00 is the tail of the previous evening and rolls back;
         * a session at 07:00 is an early morning and stays where it is. Midnight would
         * split late sessions in two, and 08:00 would post an early lift to yesterday.
         */
        const val DEFAULT_DAY_START_HOUR = 4

        /** The range the settings picker offers. Above 8 the label stops being honest. */
        val DAY_START_HOURS = 0..8
    }
}
