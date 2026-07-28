package dev.redplate.data

import androidx.room.withTransaction
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What a call to [MesocycleAdvancer.advanceIfDue] actually did, for the caller to report. */
sealed interface BlockAdvance {
    data object Unchanged : BlockAdvance

    /** Moved into an accumulation week and added a set wherever there was headroom. */
    data class Accumulated(val week: Int, val setsAdded: Int) : BlockAdvance

    /** Reached the end of the block: sets halved, loads dropped. */
    data class Deloaded(val week: Int) : BlockAdvance

    /** The deload week is behind us. The old block is closed and [nextMesocycleId] is live. */
    data class BlockComplete(val nextMesocycleId: Long) : BlockAdvance
}

/**
 * Moves a block through its weeks.
 *
 * `MesocycleEntity.currentWeek` was only ever written by the "take a deload" button, so a
 * block sat at "WEEK 1 OF 5" forever and `ProgramGenerator.setsFor`'s promise that week 1
 * "starts at MEV and adds sets weekly" described something that never happened.
 *
 * The week is derived from training done, not from the wall clock. Counting calendar weeks
 * would burn a block week every time life got in the way, which is exactly backwards: a
 * week you did not train is a week you did not accumulate. So a week advances when either
 *
 * - every template scheduled for that week has been completed, or
 * - the week was started but has been open [STALE_WEEK_DAYS] days without finishing,
 *
 * whichever lands first. A week with nothing logged against it at all never expires: an
 * untrained week has not begun, and letting it lapse is precisely the silent burn the
 * derivation exists to prevent. The second condition is only there so a week that loses a
 * single session permanently still moves on rather than waiting for a day that will never
 * be trained.
 */
@Singleton
class MesocycleAdvancer @Inject constructor(
    private val db: RedplateDatabase,
    private val programGenerator: ProgramGenerator,
    private val programDao: ProgramDao,
    private val sessionDao: SessionDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val volumeDao: VolumeDao,
    private val trainingClock: TrainingClock,
) {

    suspend fun advanceIfDue(
        mesocycle: MesocycleEntity,
        profile: ProfileEntity,
        now: Long = System.currentTimeMillis(),
    ): BlockAdvance {
        if (!mesocycle.isActive) return BlockAdvance.Unchanged

        val templates = scheduledTemplates(mesocycle.id)
        if (templates.isEmpty()) return BlockAdvance.Unchanged
        if (!isWeekDone(mesocycle, templates, profile, now)) return BlockAdvance.Unchanged

        val next = mesocycle.currentWeek + 1
        return when {
            // The deload week is over — close the block and open the next one.
            mesocycle.currentWeek >= mesocycle.lengthWeeks -> completeBlock(mesocycle, profile, now)

            // Stepping into the final week means stepping into the deload.
            next >= mesocycle.lengthWeeks -> {
                applyDeload(mesocycle, templates, next)
                BlockAdvance.Deloaded(next)
            }

            else -> BlockAdvance.Accumulated(next, accumulate(mesocycle, templates, next))
        }
    }

    /**
     * Drops the block straight into its deload week, whatever week it was on. This is what
     * the stall screen's "take the deload" button means — it used to relabel the week and
     * change nothing at all.
     */
    suspend fun forceDeload(mesocycle: MesocycleEntity): BlockAdvance {
        val templates = scheduledTemplates(mesocycle.id)
        if (templates.isEmpty()) return BlockAdvance.Unchanged
        applyDeload(mesocycle, templates, mesocycle.lengthWeeks)
        return BlockAdvance.Deloaded(mesocycle.lengthWeeks)
    }

    /** Ad-hoc templates sit at a negative day index and are not part of the weekly schedule. */
    private suspend fun scheduledTemplates(mesocycleId: Long): List<SessionTemplateEntity> =
        programDao.getAllTemplates().filter { it.mesocycleId == mesocycleId && it.dayIndex >= 0 }

    // ── Is the week finished? ───────────────────────────────────────────

    private suspend fun isWeekDone(
        mesocycle: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        profile: ProfileEntity,
        now: Long,
    ): Boolean {
        val logged = sessionDao.getSessionsForBlockWeek(mesocycle.id, mesocycle.currentWeek)
        if (logged.isEmpty()) return false

        val finished = logged.filter { it.endedAt != null }.mapNotNull { it.templateId }.toSet()
        if (templates.all { it.id in finished }) return true

        val openedAt = logged.minOf { it.startedAt }
        val daysOpen = ChronoUnit.DAYS.between(
            trainingClock.trainingDate(openedAt, profile.dayStartHour),
            trainingClock.trainingDate(now, profile.dayStartHour),
        )
        return daysOpen >= STALE_WEEK_DAYS
    }

    // ── What advancing does to the plan ─────────────────────────────────

    /**
     * One more set per slot, capped at the top of the muscle's adaptive range. Week 1 opens
     * at MEV by design, so the block climbs into that range rather than starting inside it;
     * the cap is what stops it climbing past what can be recovered from.
     *
     * Returns how many slots actually gained a set.
     */
    private suspend fun accumulate(
        mesocycle: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        week: Int,
    ): Int {
        val exercises = exerciseDao.getAll().associateBy { it.id }
        val landmarks = volumeDao.getAllLandmarks().associateBy { it.muscle }
        val slots = templates.flatMap { programDao.getSlots(it.id) }

        // Prescribed sets per muscle across the whole block week, credited the same way
        // logged volume is, so the ceiling is measured in the units it was written in.
        val planned = mutableMapOf<MuscleGroup, Double>()
        for (slot in slots) {
            val exercise = exercises[slot.exerciseId] ?: continue
            planned.merge(exercise.primaryMuscle, slot.targetSets * VolumeCredit.PRIMARY_CREDIT, Double::plus)
            exercise.secondaryMuscles.forEach {
                planned.merge(it, slot.targetSets * VolumeCredit.SECONDARY_CREDIT, Double::plus)
            }
        }

        var added = 0
        db.withTransaction {
            for (slot in slots) {
                val exercise = exercises[slot.exerciseId] ?: continue
                if (slot.targetSets >= MAX_SETS_PER_SLOT) continue

                val muscle = exercise.primaryMuscle
                val ceiling = (landmarks[muscle] ?: VolumeLandmarks.forMuscle(muscle)).mavHigh
                val current = planned[muscle] ?: 0.0
                if (current + VolumeCredit.PRIMARY_CREDIT > ceiling) continue

                programDao.updateSlot(slot.copy(targetSets = slot.targetSets + 1))
                planned[muscle] = current + VolumeCredit.PRIMARY_CREDIT
                added++
            }
            programDao.updateMesocycle(mesocycle.copy(currentWeek = week))
        }
        return added
    }

    /**
     * A deload is a real change to the plan, not a label: sets halve and every working load
     * comes down by [DELOAD_FRACTION], snapped to something the equipment can assemble.
     */
    private suspend fun applyDeload(
        mesocycle: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        week: Int,
    ) {
        val exercises = exerciseDao.getAll().associateBy { it.id }
        db.withTransaction {
            for (template in templates) {
                for (slot in programDao.getSlots(template.id)) {
                    val equipment = exercises[slot.exerciseId]?.let { equipmentFor(it) }
                    val deloadedLoad = slot.workingLoadKg?.let { load ->
                        equipment?.let { PlateMath.deload(load, DELOAD_FRACTION, it) }
                            ?: (load * (1 - DELOAD_FRACTION))
                    }
                    programDao.updateSlot(
                        slot.copy(
                            targetSets = (slot.targetSets / 2).coerceAtLeast(1),
                            workingLoadKg = deloadedLoad,
                        )
                    )
                }
            }
            programDao.updateMesocycle(mesocycle.copy(currentWeek = week))
        }
    }

    /**
     * Closes the finished block and opens the next one, seeded from what was achieved.
     *
     * The old mesocycle is deactivated and stamped, never deleted: `sessions.mesocycleId`
     * and `sessions.templateId` point into it and the history has to keep resolving. The
     * new block starts from the loads the user actually lifted — undoing the deload drop —
     * so a new block is not a fresh start from an empty bar.
     */
    private suspend fun completeBlock(
        mesocycle: MesocycleEntity,
        profile: ProfileEntity,
        now: Long,
    ): BlockAdvance {
        val achieved = achievedLoads(mesocycle)

        // generate() deactivates whatever block is active, but stamping completedAt here
        // is what marks this one as finished rather than merely replaced.
        programDao.updateMesocycle(
            mesocycle.copy(isActive = false, completedAt = now, currentWeek = mesocycle.lengthWeeks)
        )

        val nextId = programGenerator.generate(profile, now)
        seedLoads(nextId, achieved)
        return BlockAdvance.BlockComplete(nextId)
    }

    /** The heaviest working set logged per exercise in a block — what the next one starts from. */
    private suspend fun achievedLoads(mesocycle: MesocycleEntity): Map<String, Double> {
        val sessions = sessionDao.getSessionsForMesocycle(mesocycle.id)
        if (sessions.isEmpty()) return emptyMap()
        return sessionDao.getSetsForSessions(sessions.map { it.id })
            .filter { !it.isWarmup }
            .groupBy { it.exerciseId }
            .mapValues { (_, sets) -> sets.maxOf { it.loadKg } }
    }

    private suspend fun seedLoads(mesocycleId: Long, achieved: Map<String, Double>) {
        if (achieved.isEmpty()) return
        db.withTransaction {
            for (template in programDao.getAllTemplates().filter { it.mesocycleId == mesocycleId }) {
                for (slot in programDao.getSlots(template.id)) {
                    val load = achieved[slot.exerciseId] ?: continue
                    programDao.updateSlot(slot.copy(workingLoadKg = load))
                }
            }
        }
    }

    private suspend fun equipmentFor(exercise: ExerciseEntity): EquipmentEntity? =
        exercise.requiredEquipmentIds
            .firstNotNullOfOrNull { equipmentDao.getById(it) }
            ?.takeIf { it.isAvailable }

    companion object {
        /** A started week that has not finished in this long has run out; move the block on. */
        const val STALE_WEEK_DAYS = 7L

        /** No slot climbs past this however much headroom the muscle has. */
        const val MAX_SETS_PER_SLOT = 6

        const val DELOAD_FRACTION = 0.2
    }
}
