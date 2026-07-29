package dev.redplate.data

import androidx.room.withTransaction
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What a schedule edit did, so the screen can say it plainly. */
sealed interface ScheduleEdit {
    data object NoActiveBlock : ScheduleEdit

    /** The day is already taken by another session, and swapping was not asked for. */
    data class DayTaken(val occupiedBy: String) : ScheduleEdit

    data class Moved(val sessionLabel: String, val toWeekdayIndex: Int) : ScheduleEdit

    /** Two sessions traded days, which is what a move onto an occupied day means. */
    data class Swapped(val first: String, val second: String) : ScheduleEdit

    data class StartDateSet(val date: LocalDate) : ScheduleEdit
}

/**
 * Direct control over when the program runs.
 *
 * The schedule used to be entirely derived: a split picked the weekdays, the week was
 * hardcoded Monday-to-Sunday, and a block was assumed to have begun the moment it was
 * generated. None of that survives contact with a real week — sessions move, weeks start
 * on whatever day you started training, and a plan built on Tuesday might not begin until
 * Sunday. This is the part the user drives.
 *
 * Every operation works on the existing templates in place. Nothing here regenerates a
 * plan or touches a logged session: moving Thursday's session to Friday is a change to a
 * calendar, not to the training.
 */
@Singleton
class ScheduleEditor @Inject constructor(
    private val db: RedplateDatabase,
    private val profileDao: ProfileDao,
    private val programDao: ProgramDao,
    private val trainingClock: TrainingClock,
) {

    /**
     * Moves one session onto [weekdayIndex] (0 = Monday).
     *
     * If another session already sits there the two trade days, because that is what the
     * user means by dragging one onto the other — and silently stacking two sessions on
     * one day would produce a week the app cannot represent.
     */
    suspend fun moveSession(templateId: Long, weekdayIndex: Int, swap: Boolean = true): ScheduleEdit {
        val template = programDao.getTemplateById(templateId) ?: return ScheduleEdit.NoActiveBlock
        val day = weekdayIndex.coerceIn(0, TrainingClock.DAYS_IN_WEEK - 1)
        if (template.dayIndex == day) return ScheduleEdit.Moved(template.label, day)

        val occupant = programDao.getAllTemplates()
            .firstOrNull { it.mesocycleId == template.mesocycleId && it.dayIndex == day }

        if (occupant != null && !swap) return ScheduleEdit.DayTaken(occupant.label)

        db.withTransaction {
            occupant?.let { programDao.updateTemplate(it.copy(dayIndex = template.dayIndex)) }
            programDao.updateTemplate(template.copy(dayIndex = day))
            syncTrainingDays(template.mesocycleId)
        }

        return occupant
            ?.let { ScheduleEdit.Swapped(template.label, it.label) }
            ?: ScheduleEdit.Moved(template.label, day)
    }

    /**
     * Records which weekdays the block now occupies on the profile.
     *
     * Without this a hand-moved session would be undone the next time anything read
     * `trainingDays` — the plan and the profile have to describe the same week.
     */
    private suspend fun syncTrainingDays(mesocycleId: Long) {
        val profile = profileDao.get() ?: return
        val days = programDao.getAllTemplates()
            .filter { it.mesocycleId == mesocycleId && it.dayIndex >= 0 }
            .map { it.dayIndex }
            .distinct()
            .sorted()
        if (days.size == profile.daysPerWeek) {
            profileDao.upsert(profile.copy(trainingDays = days))
        }
    }

    /** First day of the training week, 0 = Monday. */
    suspend fun setWeekStartsOn(weekdayIndex: Int) {
        val profile = profileDao.get() ?: return
        profileDao.upsert(
            profile.copy(weekStartsOn = weekdayIndex.coerceIn(0, TrainingClock.DAYS_IN_WEEK - 1))
        )
    }

    /**
     * Sets the training date the active block begins on.
     *
     * Until that date Today reports the block as not yet started rather than counting
     * weeks that have not happened. Choosing a date in the past simply means it has begun.
     */
    suspend fun setStartDate(date: LocalDate): ScheduleEdit {
        val meso = programDao.getActiveMesocycle() ?: return ScheduleEdit.NoActiveBlock
        val dayStartHour = trainingClock.dayStartHour()
        programDao.updateMesocycle(
            meso.copy(beginsAt = trainingClock.dayBounds(date, dayStartHour).first)
        )
        return ScheduleEdit.StartDateSet(date)
    }

    /** Starts the block today, for the "I'll begin now" path. */
    suspend fun startNow(now: Long = System.currentTimeMillis()): ScheduleEdit =
        setStartDate(trainingClock.trainingDate(now, trainingClock.dayStartHour()))

    /**
     * The block's start date, or null when it has no explicit one and simply runs from the
     * day it was created.
     */
    suspend fun startDate(): LocalDate? {
        val meso = programDao.getActiveMesocycle() ?: return null
        if (meso.beginsAt <= 0L) return null
        return trainingClock.trainingDate(meso.beginsAt, trainingClock.dayStartHour())
    }
}
