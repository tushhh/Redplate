package dev.redplate.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyweightDao {

    @Insert
    suspend fun insert(entry: BodyweightEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<BodyweightEntryEntity>)

    @Delete
    suspend fun delete(entry: BodyweightEntryEntity)

    @Update
    suspend fun update(entry: BodyweightEntryEntity)

    /** Newest first — the log reads top-down from today. */
    @Query("SELECT * FROM bodyweight_entries ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<BodyweightEntryEntity>>

    @Query("SELECT * FROM bodyweight_entries ORDER BY measuredAt ASC")
    suspend fun getAll(): List<BodyweightEntryEntity>

    @Query("SELECT * FROM bodyweight_entries ORDER BY measuredAt DESC LIMIT 1")
    suspend fun getLatest(): BodyweightEntryEntity?

    @Query("SELECT COUNT(*) FROM bodyweight_entries")
    suspend fun count(): Int

    /** Wipe (import only — must run inside the import transaction). */
    @Query("DELETE FROM bodyweight_entries")
    suspend fun deleteAll()
}
