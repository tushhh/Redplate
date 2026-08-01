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
        val weighted = massBasedSets(working)
        return SessionOutcome(
            workingSets = working.size,
            durationMinutes = session.endedAt
                ?.let { TimeUnit.MILLISECONDS.toMinutes(it - session.startedAt) }
                ?.toInt()
                ?: 0,
            // Per-limb loads count both implements: a set of ten with 30 kg dumbbells is
            // 600 kg moved, not 300.
            tonnageKg = weighted.sumOf { (set, limbs) -> set.loadKg * set.reps * limbs },
            excludedFromTonnage = working.size - weighted.size,
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
    private suspend fun massBasedSets(working: List<SetLogEntity>): List<Pair<SetLogEntity, Int>> {
        if (working.isEmpty()) return emptyList()
        val exercises = exerciseDao.getAll().associateBy { it.id }
        val equipment = equipmentDao.getAll().associateBy { it.id }

        return working.mapNotNull { set ->
            val exercise = exercises[set.exerciseId] ?: return@mapNotNull set to 1
            // The load source, not the station: a rack weighs nothing and reads in nothing.
            val source = exercise.requiredEquipmentIds
                .mapNotNull { equipment[it] }
                .firstOrNull { it.carriesLoad }
                ?: return@mapNotNull set to 1

            if (source.loadUnit != LoadUnit.KILOGRAMS) null else set to source.limbMultiplier
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
