package dev.redplate.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VolumeDao {

    // ── Snapshots ───────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: VolumeSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshots(snapshots: List<VolumeSnapshotEntity>)

    @Query("""
        SELECT * FROM volume_snapshots
        WHERE mesocycleId = :mesocycleId AND weekNumber = :week
    """)
    suspend fun getSnapshots(mesocycleId: Long, week: Int): List<VolumeSnapshotEntity>

    @Query("""
        SELECT * FROM volume_snapshots
        WHERE mesocycleId = :mesocycleId
        ORDER BY weekNumber ASC, muscle ASC
    """)
    fun observeSnapshotsForMesocycle(mesocycleId: Long): Flow<List<VolumeSnapshotEntity>>

    // ── Landmarks ───────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLandmark(landmark: VolumeLandmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLandmarks(landmarks: List<VolumeLandmarkEntity>)

    @Query("SELECT * FROM volume_landmarks")
    fun observeAllLandmarks(): Flow<List<VolumeLandmarkEntity>>

    @Query("SELECT * FROM volume_landmarks WHERE muscle = :muscle")
    suspend fun getLandmark(muscle: MuscleGroup): VolumeLandmarkEntity?
}
