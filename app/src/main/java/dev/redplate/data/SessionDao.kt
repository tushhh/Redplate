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

    @Query("SELECT * FROM set_logs ORDER BY id ASC")
    suspend fun getAllSetLogs(): List<SetLogEntity>

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

    @Query("""
        SELECT * FROM set_logs
        WHERE exerciseId = :exerciseId AND isWarmup = 0
        ORDER BY loadKg DESC, reps DESC
        LIMIT 1
    """)
    suspend fun getPrSet(exerciseId: String): SetLogEntity?
}
