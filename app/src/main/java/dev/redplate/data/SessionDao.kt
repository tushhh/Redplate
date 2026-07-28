package dev.redplate.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    // ── Session ─────────────────────────────────────────────────────

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestSession(): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY id ASC")
    suspend fun getAllSessions(): List<SessionEntity>

    /** Every session logged against one week of one block — the unit a volume snapshot covers. */
    @Query("SELECT * FROM sessions WHERE mesocycleId = :mesocycleId AND weekNumber = :week")
    suspend fun getSessionsForBlockWeek(mesocycleId: Long, week: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE mesocycleId = :mesocycleId ORDER BY startedAt ASC")
    suspend fun getSessionsForMesocycle(mesocycleId: Long): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<SessionEntity>)

    // ── Set logs ────────────────────────────────────────────────────

    @Insert
    suspend fun insertSetLog(setLog: SetLogEntity): Long

    @Insert
    suspend fun insertSetLogs(setLogs: List<SetLogEntity>)

    @Update
    suspend fun updateSetLog(setLog: SetLogEntity)

    @Delete
    suspend fun deleteSetLog(setLog: SetLogEntity)

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY setIndex ASC")
    fun observeSetsForSession(sessionId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY setIndex ASC")
    suspend fun getSetsForSession(sessionId: Long): List<SetLogEntity>

    /**
     * The whole table. Only the JSON and CSV exports have any business calling this —
     * everything else wants a bounded query, because this grows without limit.
     */
    @Query("SELECT * FROM set_logs ORDER BY id ASC")
    suspend fun getAllSetLogs(): List<SetLogEntity>

    @Query("SELECT * FROM set_logs WHERE sessionId IN (:sessionIds)")
    suspend fun getSetsForSessions(sessionIds: List<Long>): List<SetLogEntity>

    @Query("""
        SELECT * FROM set_logs
        WHERE exerciseId = :exerciseId AND isWarmup = 0
        ORDER BY completedAt DESC
    """)
    fun observeHistoryForExercise(exerciseId: String): Flow<List<SetLogEntity>>

    @Query("""
        SELECT MAX(loadKg * (1 + reps / 30.0)) FROM set_logs
        WHERE exerciseId = :exerciseId AND isWarmup = 0 AND reps <= 12
    """)
    suspend fun getEstimated1Rm(exerciseId: String): Double?

    /**
     * The best set ever logged for a lift, ranked by estimated 1RM — the same Epley
     * definition [getEstimated1Rm] uses. It used to rank by raw load, so 100 kg x 1 beat
     * 95 kg x 8 while the PR banner beside it disagreed. One definition of "best".
     */
    @Query("""
        SELECT * FROM set_logs
        WHERE exerciseId = :exerciseId AND isWarmup = 0 AND reps <= 12
        ORDER BY (loadKg * (1 + reps / 30.0)) DESC, completedAt DESC
        LIMIT 1
    """)
    suspend fun getPrSet(exerciseId: String): SetLogEntity?

    // ── Wipe (import only — must run inside the import transaction) ──

    @Query("DELETE FROM set_logs")
    suspend fun deleteAllSetLogs()

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}
