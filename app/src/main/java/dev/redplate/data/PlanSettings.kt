package dev.redplate.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The answers that define a training plan.
 *
 * These used to be askable exactly once, during intake, with no way to change your mind
 * short of wiping the app. This is the shared editing surface both the intake and Settings
 * work through, so there is one definition of what a valid plan is rather than two that
 * drift — which is exactly how the generator and the repository ended up disagreeing about
 * what "available equipment" meant.
 */
data class PlanSettings(
    val goal: Goal,
    val daysPerWeek: Int,
    val sessionCeilingMinutes: Int,
    val priorityMuscles: List<MuscleGroup> = emptyList(),
    val excludedPatterns: List<MovementPattern> = emptyList(),
    /** Chosen weekdays, 0 = Monday. Null means "use the split's own layout". */
    val trainingDays: List<Int>? = null,
    val dayStartHour: Int = TrainingClock.DEFAULT_DAY_START_HOUR,
) {

    /**
     * Clamps everything to what the app can actually build.
     *
     * A weekday selection whose count does not match [daysPerWeek] is dropped rather than
     * half-applied: a plan that schedules four sessions across three chosen days is not a
     * plan anyone asked for.
     */
    fun normalised(): PlanSettings {
        val days = daysPerWeek.coerceIn(DAYS_RANGE)
        val chosen = trainingDays
            ?.filter { it in WEEKDAY_RANGE }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.size == days }
        return copy(
            daysPerWeek = days,
            sessionCeilingMinutes = SESSION_MINUTES.minByOrNull {
                kotlin.math.abs(it - sessionCeilingMinutes)
            } ?: sessionCeilingMinutes,
            priorityMuscles = priorityMuscles.distinct().take(MAX_PRIORITY_MUSCLES),
            excludedPatterns = excludedPatterns.distinct(),
            trainingDays = chosen,
            dayStartHour = dayStartHour.coerceIn(TrainingClock.DAY_START_HOURS),
        )
    }

    fun applyTo(profile: ProfileEntity): ProfileEntity = profile.copy(
        goal = goal,
        daysPerWeek = daysPerWeek,
        sessionCeilingMinutes = sessionCeilingMinutes,
        priorityMuscles = priorityMuscles,
        excludedPatterns = excludedPatterns,
        trainingDays = trainingDays,
        dayStartHour = dayStartHour,
    )

    /**
     * Whether moving from [previous] to this needs the block rebuilt.
     *
     * Anything that changes which exercises are programmed or on how many days does; the
     * session ceiling does not (see [needsRefit]), and neither does the day-start hour,
     * which is a display and grouping concern only.
     */
    fun needsRebuild(previous: PlanSettings): Boolean =
        goal != previous.goal ||
            daysPerWeek != previous.daysPerWeek ||
            priorityMuscles != previous.priorityMuscles ||
            excludedPatterns != previous.excludedPatterns

    /** Only the session ceiling changed: re-fit the block in place instead. */
    fun needsRefit(previous: PlanSettings): Boolean =
        !needsRebuild(previous) && sessionCeilingMinutes != previous.sessionCeilingMinutes

    /** Which weekdays a session lands on, falling back to the split's own layout. */
    fun weekdayIndices(): List<Int> =
        trainingDays ?: Split.forDays(daysPerWeek).weekdayIndices

    companion object {
        val DAYS_RANGE = 2..6
        val WEEKDAY_RANGE = 0..6
        val SESSION_MINUTES = listOf(30, 45, 60, 75, 90)
        const val MAX_PRIORITY_MUSCLES = 2
    }
}

fun ProfileEntity.planSettings(): PlanSettings = PlanSettings(
    goal = goal,
    daysPerWeek = daysPerWeek,
    sessionCeilingMinutes = sessionCeilingMinutes,
    priorityMuscles = priorityMuscles,
    excludedPatterns = excludedPatterns,
    trainingDays = trainingDays,
    dayStartHour = dayStartHour,
)

/** What applying a plan change actually did, so the UI can say so plainly. */
sealed interface PlanRevisionResult {
    data object NoProfile : PlanRevisionResult

    /** Nothing about the block changed — only settings that do not touch it. */
    data object SettingsOnly : PlanRevisionResult

    /** The block kept its identity and its history; sessions were re-fitted to the ceiling. */
    data class Refitted(val templatesChanged: Int) : PlanRevisionResult

    /** A new block, seeded from the achieved loads. The old one is kept, deactivated. */
    data class Rebuilt(val mesocycleId: Long) : PlanRevisionResult
}

/**
 * Applies a plan change.
 *
 * Never destructive: [ProgramGenerator.generate] deactivates the outgoing mesocycle rather
 * than deleting it, which it has to — `sessions.templateId` has no foreign key, so deleting
 * templates would leave logged history pointing at nothing.
 */
@Singleton
class PlanRevision @Inject constructor(
    private val profileDao: ProfileDao,
    private val programDao: ProgramDao,
    private val programGenerator: ProgramGenerator,
) {

    suspend fun preview(): PlanSettings? = profileDao.get()?.planSettings()

    suspend fun apply(
        settings: PlanSettings,
        now: Long = System.currentTimeMillis(),
    ): PlanRevisionResult {
        val profile = profileDao.get() ?: return PlanRevisionResult.NoProfile
        val next = settings.normalised()
        val previous = profile.planSettings()

        profileDao.upsert(next.applyTo(profile))
        val updated = profileDao.get() ?: return PlanRevisionResult.NoProfile
        val active = programDao.getActiveMesocycle()

        return when {
            active == null || next.needsRebuild(previous) -> {
                val mesocycleId = programGenerator.generate(updated, now)
                // Achieved loads carry across: a rebuilt plan must not put every lift back
                // on an empty bar.
                programGenerator.seedLoadsFromHistory(mesocycleId)
                PlanRevisionResult.Rebuilt(mesocycleId)
            }

            next.needsRefit(previous) ->
                PlanRevisionResult.Refitted(programGenerator.refitToCeiling(updated, active.id))

            else -> PlanRevisionResult.SettingsOnly
        }
    }
}
