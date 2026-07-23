package dev.redplate.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {

    // ── Mesocycle ────────────────────────────────────────────────────

    @Insert
    suspend fun insertMesocycle(mesocycle: MesocycleEntity): Long

    @Update
    suspend fun updateMesocycle(mesocycle: MesocycleEntity)

    @Query("SELECT * FROM mesocycles WHERE isActive = 1 LIMIT 1")
    fun observeActiveMesocycle(): Flow<MesocycleEntity?>

    @Query("SELECT * FROM mesocycles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveMesocycle(): MesocycleEntity?

    @Query("SELECT * FROM mesocycles ORDER BY startedAt DESC")
    fun observeAllMesocycles(): Flow<List<MesocycleEntity>>

    // ── Session templates ───────────────────────────────────────────

    @Insert
    suspend fun insertTemplate(template: SessionTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: SessionTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: SessionTemplateEntity)

    @Query("SELECT * FROM session_templates WHERE mesocycleId = :mesocycleId ORDER BY dayIndex ASC")
    fun observeTemplates(mesocycleId: Long): Flow<List<SessionTemplateEntity>>

    @Query("SELECT * FROM session_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): SessionTemplateEntity?

    // ── Template slots ──────────────────────────────────────────────

    @Insert
    suspend fun insertSlot(slot: TemplateSlotEntity): Long

    @Insert
    suspend fun insertSlots(slots: List<TemplateSlotEntity>)

    @Update
    suspend fun updateSlot(slot: TemplateSlotEntity)

    @Delete
    suspend fun deleteSlot(slot: TemplateSlotEntity)

    @Query("SELECT * FROM template_slots WHERE templateId = :templateId ORDER BY orderIndex ASC")
    fun observeSlots(templateId: Long): Flow<List<TemplateSlotEntity>>

    @Query("SELECT * FROM template_slots WHERE templateId = :templateId ORDER BY orderIndex ASC")
    suspend fun getSlots(templateId: Long): List<TemplateSlotEntity>
}
