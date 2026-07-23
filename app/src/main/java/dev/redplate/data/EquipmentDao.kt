package dev.redplate.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(equipment: List<EquipmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(equipment: EquipmentEntity)

    @Update
    suspend fun update(equipment: EquipmentEntity)

    @Query("SELECT * FROM equipment WHERE id = :id")
    suspend fun getById(id: String): EquipmentEntity?

    @Query("SELECT * FROM equipment ORDER BY displayName ASC")
    fun observeAll(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE isAvailable = 1 ORDER BY displayName ASC")
    fun observeAvailable(): Flow<List<EquipmentEntity>>
}
