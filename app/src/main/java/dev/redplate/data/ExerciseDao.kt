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

    /**
     * Pass user input through [escapeLike] before calling this. Unescaped, a query
     * containing `%` matches everything and `_` matches any character, so typing a
     * underscore in a search box quietly returns the wrong list.
     */
    @Query("""
        SELECT * FROM exercises
        WHERE name LIKE '%' || :query || '%' ESCAPE '\' AND isExcluded = 0
        ORDER BY name ASC
    """)
    fun search(query: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("UPDATE exercises SET isExcluded = :excluded WHERE id = :id")
    suspend fun setExcluded(id: String, excluded: Boolean)

    @Query("UPDATE exercises SET hasBeenIntroduced = 1 WHERE id = :id")
    suspend fun markIntroduced(id: String)

    /** Wipe (import only — must run inside the import transaction). */
    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}

/**
 * Neutralises the LIKE wildcards in a user's search string, matching the `ESCAPE '\'`
 * clause in [ExerciseDao.search]. The backslash has to be escaped first or it would
 * escape the escapes.
 */
fun escapeLike(query: String): String = query
    .replace("\\", "\\\\")
    .replace("%", "\\%")
    .replace("_", "\\_")
