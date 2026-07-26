package dev.redplate.data

import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: RedplateDatabase,
) {
    suspend fun seedIfNeeded() {
        if (db.exerciseDao().count() > 0) return

        val exercises = CuratedExerciseSeed.seed()
        val equipment = GymEquipmentSeed.seed()

        db.withTransaction {
            db.equipmentDao().insertAll(equipment)
            db.exerciseDao().insertAll(exercises)
        }
    }
}
