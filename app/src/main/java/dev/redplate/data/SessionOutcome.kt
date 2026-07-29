package dev.redplate.data

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a session actually amounted to. Derived, never stored, so it cannot drift from the
 * set logs it describes.
 */
data class SessionOutcome(
    val workingSets: Int,
    val durationMinutes: Int,
    val tonnageKg: Double,
    /** Sets logged in resistance levels, which are not kilograms and cannot be summed. */
    val excludedFromTonnage: Int = 0,
    val prCount: Int,
) {
    val isEmpty: Boolean get() = workingSets == 0
}

/**
 * Reads a session's outcome once, for both the summary screen and Today's completed card.
 *
 * Today needs the same three numbers the summary already computed. Recomputing them
 * beside it is how two screens end up disagreeing about how many sets you did.
 */
@Singleton
class SessionOutcomeReader @Inject constructor(
    private val sessionDao: SessionDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
) {

    suspend fun read(session: SessionEntity): SessionOutcome {
        val working = sessionDao.getSetsForSession(session.id).filter { !it.isWarmup }
        val inKilograms = massBasedSets(working)
        return SessionOutcome(
            workingSets = working.size,
            durationMinutes = session.endedAt
                ?.let { TimeUnit.MILLISECONDS.toMinutes(it - session.startedAt) }
                ?.toInt()
                ?: 0,
            tonnageKg = inKilograms.sumOf { it.loadKg * it.reps },
            excludedFromTonnage = working.size - inKilograms.size,
            prCount = countPrs(session.id, working),
        )
    }

    /**
     * Only sets whose equipment is marked in kilograms count toward tonnage.
     *
     * A resistance level is ordinal: level 8 is harder than level 6, but it is not eight
     * kilograms, and adding it to a barbell total produces a number that means nothing.
     * PRs are unaffected — those compare a lift against its own history, where the unit is
     * consistent by construction.
     */
    private suspend fun massBasedSets(working: List<SetLogEntity>): List<SetLogEntity> {
        if (working.isEmpty()) return working
        val exercises = exerciseDao.getAll().associateBy { it.id }
        val equipment = equipmentDao.getAll().associateBy { it.id }
        return working.filter { set ->
            val exercise = exercises[set.exerciseId] ?: return@filter true
            exercise.requiredEquipmentIds
                .firstNotNullOfOrNull { equipment[it] }
                ?.let { it.loadUnit == LoadUnit.KILOGRAMS }
                ?: true
        }
    }

    /**
     * A set is a PR if its estimated 1RM beats everything logged for that lift *before*
     * this session. Compared against prior history only, so two climbing sets in one
     * session are not counted as beating each other.
     */
    private suspend fun countPrs(sessionId: Long, working: List<SetLogEntity>): Int {
        var count = 0
        for ((exerciseId, sets) in working.groupBy { it.exerciseId }) {
            val priorBest = sessionDao.getEstimated1RmExcludingSession(exerciseId, sessionId) ?: 0.0
            count += sets.count { it.reps in PR_REP_RANGE && it.estimated1Rm() > priorBest + EPSILON }
        }
        return count
    }

    private companion object {
        /** Epley stops being meaningful past a dozen reps. */
        val PR_REP_RANGE = 1..12
        const val EPSILON = 1e-6
    }
}
