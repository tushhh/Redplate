package dev.redplate.data

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val schemaVersion: Int = SCHEMA_VERSION,
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
) {
    companion object {
        /**
         * Bump only when the JSON shape changes in a way older builds cannot read.
         * Adding a field with a default is backwards compatible and does not need a bump.
         */
        const val SCHEMA_VERSION = 1
    }
}
