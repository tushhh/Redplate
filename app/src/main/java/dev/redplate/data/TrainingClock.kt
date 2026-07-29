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
class TrainingClock private constructor(
    private val profileDao: ProfileDao?,
    private val zone: ZoneId,
) {

    @Inject
    constructor(profileDao: ProfileDao) : this(profileDao, ZoneId.systemDefault())

    /**
     * For testing the date maths, which reads no profile and must not depend on the
     * machine's own zone. A null [profileDao] means [dayStartHour] answers the default.
     */
    internal constructor(zone: ZoneId) : this(null, zone)

    /** The user's configured boundary, or the default when there is no profile yet. */
    suspend fun dayStartHour(): Int =
        profileDao?.get()?.dayStartHour ?: DEFAULT_DAY_START_HOUR

    /** Today's training date, reading the boundary from the profile. */
    suspend fun todayDate(now: Long = System.currentTimeMillis()): LocalDate =
        trainingDate(now, dayStartHour())

    /** The training date [epochMillis] belongs to. */
    fun trainingDate(epochMillis: Long, dayStartHour: Int): LocalDate =
        Instant.ofEpochMilli(epochMillis)
            .atZone(zone)
            .minusHours(dayStartHour.toLong())
            .toLocalDate()

    fun today(dayStartHour: Int, now: Long = System.currentTimeMillis()): LocalDate =
        trainingDate(now, dayStartHour)

    /**
     * The first day of the training week containing [date].
     *
     * [weekStartsOn] is 0 = Monday. It is the user's, not the calendar's: someone who
     * starts a block on a Thursday has a week that runs Thursday to Wednesday, and forcing
     * it onto Monday would split their first week in two and burn one for nothing.
     */
    fun weekStart(date: LocalDate, weekStartsOn: Int = DEFAULT_WEEK_STARTS_ON): LocalDate {
        val offset = Math.floorMod(weekdayIndex(date) - weekStartsOn, DAYS_IN_WEEK)
        return date.minusDays(offset.toLong())
    }

    /** The training week a moment belongs to, as its first day. */
    fun weekStart(
        epochMillis: Long,
        dayStartHour: Int,
        weekStartsOn: Int = DEFAULT_WEEK_STARTS_ON,
    ): LocalDate = weekStart(trainingDate(epochMillis, dayStartHour), weekStartsOn)

    /** The user's configured first day of the week, or Monday when there is no profile. */
    suspend fun weekStartsOn(): Int = profileDao?.get()?.weekStartsOn ?: DEFAULT_WEEK_STARTS_ON

    /** The seven weekday indices of a week, in the order this user's week runs. */
    fun weekdayOrder(weekStartsOn: Int = DEFAULT_WEEK_STARTS_ON): List<Int> =
        (0 until DAYS_IN_WEEK).map { Math.floorMod(weekStartsOn + it, DAYS_IN_WEEK) }

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

        /** 0 = Monday, the convention `SessionTemplateEntity.dayIndex` already uses. */
        const val DEFAULT_WEEK_STARTS_ON = 0

        const val DAYS_IN_WEEK = 7
    }
}
