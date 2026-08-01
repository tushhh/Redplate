package dev.redplate.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProfileEntity::class,
        EquipmentEntity::class,
        ExerciseEntity::class,
        MesocycleEntity::class,
        SessionTemplateEntity::class,
        TemplateSlotEntity::class,
        SessionEntity::class,
        SetLogEntity::class,
        VolumeSnapshotEntity::class,
        VolumeLandmarkEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RedplateDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun profileDao(): ProfileDao
    abstract fun programDao(): ProgramDao
    abstract fun sessionDao(): SessionDao
    abstract fun volumeDao(): VolumeDao
}
