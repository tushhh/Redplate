package dev.redplate.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * How a logged set turns into volume credit, in one place.
 *
 * COACHING.md §3: the muscle a movement is for gets a whole set, the muscles that helped
 * get half, and only sets taken to 0–3 reps in reserve count at all. Three callers used to
 * carry their own copy of that rule; drift between two copies of the equipment check is
 * what made an unavailable exercise look available, so this one is shared.
 */
object VolumeCredit {

    const val PRIMARY_CREDIT = 1.0
    const val SECONDARY_CREDIT = 0.5

    fun perMuscle(
        sets: List<SetLogEntity>,
        exercisesById: Map<String, ExerciseEntity>,
    ): Map<MuscleGroup, Double> {
        val perMuscle = mutableMapOf<MuscleGroup, Double>()
        for (set in sets) {
            if (!set.countsTowardVolume) continue
            val exercise = exercisesById[set.exerciseId] ?: continue
            perMuscle.merge(exercise.primaryMuscle, PRIMARY_CREDIT, Double::plus)
            exercise.secondaryMuscles.forEach { perMuscle.merge(it, SECONDARY_CREDIT, Double::plus) }
        }
        return perMuscle
    }
}

/**
 * Writes the `volume_snapshots` rows the whole volume readout is built from.
 *
 * Nothing wrote this table outside backup import, so Today always fell through to the
 * empty branch and reported zero sets against three arbitrary muscles. A snapshot is
 * recomputed from scratch for the whole block week each time a session ends, rather than
 * incremented — that way an edited or deleted set corrects the number instead of leaving
 * a running total that can only ever drift upward.
 */
@Singleton
class VolumeRecorder @Inject constructor(
    private val sessionDao: SessionDao,
    private val exerciseDao: ExerciseDao,
    private val volumeDao: VolumeDao,
) {

    /** No-op for a freestyle session: it belongs to no block week, so there is nothing to key on. */
    suspend fun recordForSession(session: SessionEntity) {
        val mesocycleId = session.mesocycleId ?: return
        val week = session.weekNumber ?: return
        recordBlockWeek(mesocycleId, week)
    }

    suspend fun recordBlockWeek(mesocycleId: Long, week: Int) {
        val sessions = sessionDao.getSessionsForBlockWeek(mesocycleId, week)
        if (sessions.isEmpty()) return

        val sets = sessionDao.getSetsForSessions(sessions.map { it.id })
        val exercises = exerciseDao.getAll().associateBy { it.id }
        val credited = VolumeCredit.perMuscle(sets, exercises)
        if (credited.isEmpty()) return

        val landmarks = volumeDao.getAllLandmarks().associateBy { it.muscle }
        volumeDao.upsertSnapshots(
            credited.map { (muscle, hardSets) ->
                val landmark = landmarks[muscle] ?: VolumeLandmarks.forMuscle(muscle)
                VolumeSnapshotEntity(
                    mesocycleId = mesocycleId,
                    weekNumber = week,
                    muscle = muscle,
                    hardSets = hardSets,
                    mev = landmark.mev,
                    // The readout compares against the top of the adaptive range, which is
                    // what a week in an accumulation block is aiming at.
                    mav = landmark.mavHigh,
                    mrv = landmark.mrv,
                )
            }
        )
    }
}
