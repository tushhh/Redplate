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
    private val volumeRecorder: VolumeRecorder,
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

    /** What the session is called, for screens that add to it by name ("Add to Upper A"). */
    suspend fun getTemplateLabel(templateId: Long): String? =
        programDao.getTemplateById(templateId)?.label

    /**
     * Throws away a generated template nothing was ever logged against. Slots cascade.
     *
     * Only ever called for an ad-hoc plan the user replaced before starting it — going
     * back to the map and building again should not leave a trail of dead templates.
     * A template with a session against it is never touched: `sessions.templateId` has no
     * foreign key, so deleting one would leave the history pointing at nothing.
     */
    suspend fun discardUnusedTemplate(templateId: Long) {
        if (templateId <= 0L) return
        if (sessionDao.getAllSessions().any { it.templateId == templateId }) return
        programDao.getTemplateById(templateId)?.let { programDao.deleteTemplate(it) }
    }

    suspend fun getSession(id: Long): SessionEntity? = sessionDao.getSessionById(id)

    /**
     * Stamps the finish time and refreshes the block week's volume snapshot. A session
     * without a finish time is still in progress.
     *
     * The snapshot write lives here rather than on the summary screen because a session
     * can be finished without the summary ever being opened, and `volume_snapshots` is
     * what every volume readout in the app reads from.
     */
    suspend fun endSession(sessionId: Long, endedAt: Long) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        val finished = if (session.endedAt == null) {
            session.copy(endedAt = endedAt).also { sessionDao.updateSession(it) }
        } else {
            session
        }
        volumeRecorder.recordForSession(finished)
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
        val inActiveBlock = mesocycle != null && template?.mesocycleId == mesocycle.id
        val week = mesocycle?.currentWeek?.takeIf { inActiveBlock }
        return sessionDao.insertSession(
            SessionEntity(
                templateId = templateId,
                mesocycleId = template?.mesocycleId,
                weekNumber = week,
                // Was hardcoded to ACCUMULATION, which made a deload week's history
                // indistinguishable from a hard one.
                phase = if (mesocycle != null && week != null) {
                    mesocycle.phaseForWeek(week)
                } else {
                    BlockPhase.ACCUMULATION
                },
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

    /** The whole archive, one query — the exercise browser tiers and filters it in memory. */
    suspend fun allExercises(): List<ExerciseEntity> = exerciseDao.getAll()

    /** Equipment keyed by id, so a list of 800 exercises can be labelled without 800 queries. */
    suspend fun equipmentById(): Map<String, EquipmentEntity> =
        equipmentDao.getAll().associateBy { it.id }

    /**
     * Working sets ever logged per exercise. This is what "you train these most" means —
     * the user's own history, not a popularity list shipped with the app.
     */
    suspend fun workingSetCountsByExercise(): Map<String, Int> =
        sessionDao.getAllSetLogs()
            .filter { !it.isWarmup }
            .groupingBy { it.exerciseId }
            .eachCount()

    /** One-shot list of exercises for a muscle that the available equipment can support. */
    suspend fun availableExercisesForMuscle(muscle: MuscleGroup): List<ExerciseEntity> {
        val available = availableEquipmentIds()
        return exerciseDao.getAll()
            .filter { it.primaryMuscle == muscle && !it.isExcluded }
            .filter { EquipmentAvailability.canPerform(it, available) }
    }

    /** Stream of all exercises that can be performed with available equipment, for a given muscle. */
    fun observeExercisesByMuscleWithAvailableEquipment(muscle: MuscleGroup): Flow<List<ExerciseEntity>> =
        exerciseDao.observeByMuscle(muscle).map { exercises -> filterPerformable(exercises) }

    /** Stream of all exercises that can be performed with available equipment. */
    fun observeExercisesWithAvailableEquipment(): Flow<List<ExerciseEntity>> =
        exerciseDao.observeAll().map { exercises -> filterPerformable(exercises) }

    /**
     * The equipment set is read once per emission rather than once per exercise. The
     * previous shape called a `suspend` lookup from inside a `filter` inside a `Flow.map`,
     * which is one database round-trip per exercise per emission — 800 of them for the
     * browser's full list.
     */
    private suspend fun filterPerformable(exercises: List<ExerciseEntity>): List<ExerciseEntity> {
        val available = availableEquipmentIds()
        return exercises.filter { EquipmentAvailability.canPerform(it, available) }
    }

    private suspend fun availableEquipmentIds(): Set<String> =
        EquipmentAvailability.availableIds(equipmentDao.getAll())

    private companion object {
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
