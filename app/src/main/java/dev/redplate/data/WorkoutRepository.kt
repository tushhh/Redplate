package dev.redplate.data

import kotlinx.coroutines.flow.Flow
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

    suspend fun getSession(id: Long): SessionEntity? = sessionDao.getSessionById(id)

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

    suspend fun startFreestyleSession(now: Long): Long =
        sessionDao.insertSession(
            SessionEntity(
                templateId = null,
                mesocycleId = null,
                weekNumber = null,
                startedAt = now,
            )
        )
}
