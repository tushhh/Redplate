package dev.redplate.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE isExcluded = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE primaryMuscle = :muscle AND isExcluded = 0 ORDER BY name ASC")
    fun observeByMuscle(muscle: MuscleGroup): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE pattern = :pattern AND isExcluded = 0 ORDER BY name ASC")
    fun observeByPattern(pattern: MovementPattern): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' AND isExcluded = 0 ORDER BY name ASC")
    fun search(query: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("UPDATE exercises SET isExcluded = :excluded WHERE id = :id")
    suspend fun setExcluded(id: String, excluded: Boolean)
}
