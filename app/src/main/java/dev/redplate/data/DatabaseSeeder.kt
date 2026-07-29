package dev.redplate.data

import androidx.room.withTransaction
import dev.redplate.coach.CoachCopy
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the first-run seed has got to.
 *
 * The seed used to run on a bare coroutine scope with no completion signal and no error
 * handling, so onboarding could ask an exercise-dependent question before any exercise
 * existed, and a failed seed produced an app with an empty library and no explanation.
 */
sealed interface SeedState {
    data object Seeding : SeedState
    data object Ready : SeedState

    /** [message] is shown to the user as written: what happened, and what to do about it. */
    data class Failed(val message: String, val cause: Throwable) : SeedState
}

@Singleton
class DatabaseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: RedplateDatabase,
) {
    private val _state = MutableStateFlow<SeedState>(SeedState.Seeding)

    /** Gate anything that reads exercises on this reaching [SeedState.Ready]. */
    val state: StateFlow<SeedState> = _state.asStateFlow()

    suspend fun seedIfNeeded() {
        val result = runCatching {
            if (db.exerciseDao().count() == 0) {
                val exercises = CuratedExerciseSeed.seed()
                val equipment = GymEquipmentSeed.seed()

                db.withTransaction {
                    db.equipmentDao().insertAll(equipment)
                    db.exerciseDao().insertAll(exercises)
                }
            }
        }

        _state.value = result.fold(
            onSuccess = { SeedState.Ready },
            onFailure = { cause ->
                SeedState.Failed(
                    message = CoachCopy.Setup.SEED_FAILED_BODY,
                    cause = cause,
                )
            },
        )
    }

    /** Lets a failed first run be retried without reinstalling. */
    suspend fun retry() {
        _state.value = SeedState.Seeding
        seedIfNeeded()
    }
}
