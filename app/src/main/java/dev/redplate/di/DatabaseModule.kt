package dev.redplate.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.redplate.data.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RedplateDatabase =
        Room.databaseBuilder(context, RedplateDatabase::class.java, "redplate.db")
            .build()

    @Provides fun provideExerciseDao(db: RedplateDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideEquipmentDao(db: RedplateDatabase): EquipmentDao = db.equipmentDao()
    @Provides fun provideProfileDao(db: RedplateDatabase): ProfileDao = db.profileDao()
    @Provides fun provideProgramDao(db: RedplateDatabase): ProgramDao = db.programDao()
    @Provides fun provideSessionDao(db: RedplateDatabase): SessionDao = db.sessionDao()
    @Provides fun provideVolumeDao(db: RedplateDatabase): VolumeDao = db.volumeDao()
}
