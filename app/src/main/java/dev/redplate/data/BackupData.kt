package dev.redplate.data

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val profile: ProfileEntity? = null,
    val equipment: List<EquipmentEntity> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val mesocycles: List<MesocycleEntity> = emptyList(),
    val sessionTemplates: List<SessionTemplateEntity> = emptyList(),
    val templateSlots: List<TemplateSlotEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val setLogs: List<SetLogEntity> = emptyList(),
    val volumeSnapshots: List<VolumeSnapshotEntity> = emptyList(),
    val volumeLandmarks: List<VolumeLandmarkEntity> = emptyList(),
)
