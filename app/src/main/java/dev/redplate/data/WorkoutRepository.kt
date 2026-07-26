package dev.redplate.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data access for the active-session set logging flow. Thin — the DAOs already
 * hold the queries; this exists so the ViewModel depends on one collaborator
 * per the Compose → ViewModel → Repository → DAO contract.
 */
@Singleton
class WorkoutRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val programDao: ProgramDao,
) {
    fun observeSetsForSession(sessionId: Long): Flow<List<SetLogEntity>> =
        sessionDao.observeSetsForSession(sessionId)

    fun observeHistory(exerciseId: String): Flow<List<SetLogEntity>> =
        sessionDao.observeHistoryForExercise(exerciseId)

    suspend fun getExercise(id: String): ExerciseEntity? = exerciseDao.getById(id)

    suspend fun getSlot(id: Long): TemplateSlotEntity? = programDao.getSlotById(id)

    /** The ordered slots of a session template — the running order for the whole workout. */
    suspend fun getSlotsForTemplate(templateId: Long): List<TemplateSlotEntity> =
        programDao.getSlots(templateId)

    suspend fun getSession(id: Long): SessionEntity? = sessionDao.getSessionById(id)

    /** Stamps the finish time. A session without one is still in progress. */
    suspend fun endSession(sessionId: Long, endedAt: Long) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        if (session.endedAt == null) {
            sessionDao.updateSession(session.copy(endedAt = endedAt))
        }
    }

    suspend fun markExerciseIntroduced(exerciseId: String) =
        exerciseDao.markIntroduced(exerciseId)

    /** First required equipment that is on hand; falls back to any declared equipment. */
    suspend fun getPrimaryEquipment(exercise: ExerciseEntity): EquipmentEntity? {
        for (id in exercise.requiredEquipmentIds) {
            val eq = equipmentDao.getById(id)
            if (eq != null && eq.isAvailable) return eq
        }
        return exercise.requiredEquipmentIds.firstNotNullOfOrNull { equipmentDao.getById(it) }
    }

    /** Best estimated 1RM across prior non-warmup sets — the bar a new set must clear to be a PR. */
    suspend fun priorBestE1rm(exerciseId: String): Double? = sessionDao.getEstimated1Rm(exerciseId)

    suspend fun logSet(set: SetLogEntity): Long = sessionDao.insertSetLog(set)

    suspend fun deleteSet(set: SetLogEntity) = sessionDao.deleteSetLog(set)

    /**
     * Opens a session against a template — either a programmed day or the one the body
     * map just generated. Set logging reads its prescription and running order from here.
     */
    suspend fun startTemplatedSession(templateId: Long, now: Long): Long {
        val template = programDao.getTemplateById(templateId)
        val mesocycle = programDao.getActiveMesocycle()
        return sessionDao.insertSession(
            SessionEntity(
                templateId = templateId,
                mesocycleId = template?.mesocycleId,
                weekNumber = mesocycle?.currentWeek?.takeIf { template?.mesocycleId == mesocycle.id },
                startedAt = now,
            )
        )
    }

    suspend fun startFreestyleSession(now: Long): Long =
        sessionDao.insertSession(
            SessionEntity(
                templateId = null,
                mesocycleId = null,
                weekNumber = null,
                startedAt = now,
            )
        )

    /**
     * Hard sets per muscle over the last seven days, secondaries at half credit and only
     * counting sets logged at 0–3 RIR, per COACHING.md §3. This is what turns the body map
     * from navigation into a status display: you see what is undertrained while choosing
     * what to train.
     */
    suspend fun weeklyHardSetsPerMuscle(now: Long = System.currentTimeMillis()): Map<MuscleGroup, Double> {
        val since = now - SEVEN_DAYS_MILLIS
        val recent = sessionDao.getAllSetLogs()
            .filter { it.completedAt >= since && it.countsTowardVolume }
        if (recent.isEmpty()) return emptyMap()

        val exercises = exerciseDao.getAll().associateBy { it.id }
        val perMuscle = mutableMapOf<MuscleGroup, Double>()
        for (set in recent) {
            val exercise = exercises[set.exerciseId] ?: continue
            perMuscle.merge(exercise.primaryMuscle, 1.0, Double::plus)
            exercise.secondaryMuscles.forEach { perMuscle.merge(it, 0.5, Double::plus) }
        }
        return perMuscle
    }

    /** One-shot list of exercises for a muscle that the available equipment can support. */
    suspend fun availableExercisesForMuscle(muscle: MuscleGroup): List<ExerciseEntity> =
        exerciseDao.getAll()
            .filter { it.primaryMuscle == muscle && !it.isExcluded }
            .filter { isExerciseAvailable(it) }

    /** Stream of all exercises that can be performed with available equipment, for a given muscle. */
    fun observeExercisesByMuscleWithAvailableEquipment(muscle: MuscleGroup): Flow<List<ExerciseEntity>> =
        exerciseDao.observeByMuscle(muscle).map { exercises ->
            exercises.filter { isExerciseAvailable(it) }
        }

    /** Stream of all exercises that can be performed with available equipment. */
    fun observeExercisesWithAvailableEquipment(): Flow<List<ExerciseEntity>> =
        exerciseDao.observeAll().map { exercises ->
            exercises.filter { isExerciseAvailable(it) }
        }

    private companion object {
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }

    /** Check if an exercise can be performed with the available equipment in the gym. */
    private suspend fun isExerciseAvailable(exercise: ExerciseEntity): Boolean {
        // If exercise requires no equipment, it's always available
        if (exercise.requiredEquipmentIds.isEmpty()) return true
        // Check if any required equipment is available
        return exercise.requiredEquipmentIds.any { eqId ->
            val eq = equipmentDao.getById(eqId)
            eq != null && eq.isAvailable
        }
    }
}
