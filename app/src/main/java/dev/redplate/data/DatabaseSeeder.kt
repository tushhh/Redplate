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

    /**
     * Brings the bundled seed into the database on every launch, not just the first.
     *
     * This used to insert only into an empty table, so an installed copy never saw a seed
     * change again: new exercises never appeared, machine priorities stayed at their
     * migration default, and lifts removed from the seed lived on. Now the seed is merged:
     *
     * - New rows are inserted as seeded.
     * - Existing rows take the seed's *description* — names, muscles, patterns, which
     *   equipment a lift needs, selection priority — but keep everything the user owns:
     *   availability, the loads and plates they confirmed, exclusions, whether guidance
     *   has been shown, their own form video.
     * - Seeded exercises that have left the seed are deleted only when nothing points at
     *   them. One with logged sets or a place in a plan stays, so history keeps its names.
     *
     * Only rows that actually differ are written, so a launch with nothing new is a read.
     */
    suspend fun seedIfNeeded() {
        val result = runCatching {
            db.withTransaction {
                syncEquipment(GymEquipmentSeed.seed())
                syncExercises(CuratedExerciseSeed.seed())
                // Plans written before conditioning was its own pattern.
                db.programDao().convertConditioningSlots(FinisherProgression.START_MINUTES)
                db.programDao().normaliseFinisherSlots()
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

    private suspend fun syncEquipment(seed: List<EquipmentEntity>) {
        val existing = db.equipmentDao().getAll().associateBy { it.id }
        val firstRun = existing.isEmpty()
        val changed = seed.mapNotNull { seeded ->
            // A machine new to the seed arrives switched off on an existing install: the
            // user has not said they have it. Fail closed, never guess open (COACHING §2).
            val current = existing[seeded.id]
                ?: return@mapNotNull if (firstRun) seeded else seeded.copy(isAvailable = false)
            current.copy(
                displayName = seeded.displayName,
                category = seeded.category,
                selectionPriority = seeded.selectionPriority,
                perLimb = seeded.perLimb,
                isAssistance = seeded.isAssistance,
            ).takeIf { it != current }
        }
        if (changed.isNotEmpty()) db.equipmentDao().insertAll(changed)
    }

    private suspend fun syncExercises(seed: List<ExerciseEntity>) {
        val dao = db.exerciseDao()
        val existing = dao.getAll().associateBy { it.id }
        val changed = seed.mapNotNull { seeded ->
            val current = existing[seeded.id] ?: return@mapNotNull seeded
            seeded.copy(
                instructions = seeded.instructions ?: current.instructions,
                imageAssetPaths = current.imageAssetPaths.ifEmpty { seeded.imageAssetPaths },
                userFormVideoUri = current.userFormVideoUri,
                hasBeenIntroduced = current.hasBeenIntroduced,
                isExcluded = current.isExcluded,
            ).takeIf { it != current }
        }
        if (changed.isNotEmpty()) dao.insertAll(changed)

        val seedIds = seed.mapTo(mutableSetOf()) { it.id }
        val retired = existing.values.filter { !it.isCustom && it.id !in seedIds }.map { it.id }
        if (retired.isNotEmpty()) dao.deleteUnreferenced(retired)
    }
}
