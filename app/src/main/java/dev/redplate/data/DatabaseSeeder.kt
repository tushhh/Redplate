package dev.redplate.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: RedplateDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded() {
        if (db.exerciseDao().count() > 0) return

        val exercises = parseExercises()
        val equipment = GymEquipmentSeed.seed()

        db.withTransaction {
            db.equipmentDao().insertAll(equipment)
            db.exerciseDao().insertAll(exercises)
        }
    }

    private fun parseExercises(): List<ExerciseEntity> {
        val raw = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        return json.decodeFromString<List<RawExercise>>(raw).mapNotNull { it.toEntity() }
    }

    // ── JSON model matching yuhonas/free-exercise-db ────────────────

    @Serializable
    private data class RawExercise(
        val id: String,
        val name: String,
        val force: String? = null,
        val level: String? = null,
        val mechanic: String? = null,
        val equipment: String? = null,
        val primaryMuscles: List<String> = emptyList(),
        val secondaryMuscles: List<String> = emptyList(),
        val instructions: List<String> = emptyList(),
        val category: String? = null,
        val images: List<String> = emptyList(),
    ) {
        fun toEntity(): ExerciseEntity? {
            val primary = primaryMuscles.firstNotNullOfOrNull { mapMuscle(it) } ?: return null
            val secondary = secondaryMuscles.mapNotNull { mapMuscle(it) }
            val compound = mechanic != "isolation"
            val resolved = resolveEquipment(equipment)

            return ExerciseEntity(
                id = id,
                name = name,
                pattern = derivePattern(force, primary, compound),
                primaryMuscle = primary,
                secondaryMuscles = secondary,
                requiredEquipmentIds = resolved.equipmentIds,
                complexity = when (level) {
                    "beginner" -> Complexity.BEGINNER
                    "expert" -> Complexity.ADVANCED
                    else -> Complexity.INTERMEDIATE
                },
                fatigueCost = deriveFatigueCost(primary, compound, category),
                isCompound = compound,
                defaultProgression = ProgressionRule.DOUBLE_PROGRESSION,
                instructions = instructions.joinToString("\n").ifEmpty { null },
                imageAssetPaths = images,
                isExcluded = resolved.excluded,
            )
        }
    }

    companion object {

        internal fun mapMuscle(name: String): MuscleGroup? = when (name.lowercase()) {
            "abdominals" -> MuscleGroup.ABS
            "adductors" -> MuscleGroup.ADDUCTORS
            "abductors" -> MuscleGroup.GLUTES
            "biceps" -> MuscleGroup.BICEPS
            "calves" -> MuscleGroup.CALVES
            "chest" -> MuscleGroup.CHEST
            "forearms" -> MuscleGroup.FOREARMS
            "glutes" -> MuscleGroup.GLUTES
            "hamstrings" -> MuscleGroup.HAMSTRINGS
            "lats" -> MuscleGroup.LATS
            "lower back" -> MuscleGroup.LOWER_BACK
            "middle back" -> MuscleGroup.UPPER_BACK
            "neck" -> MuscleGroup.NECK
            "quadriceps" -> MuscleGroup.QUADS
            "shoulders" -> MuscleGroup.FRONT_DELTS
            "traps" -> MuscleGroup.TRAPS
            "triceps" -> MuscleGroup.TRICEPS
            else -> null
        }

        internal fun derivePattern(
            force: String?,
            primary: MuscleGroup,
            compound: Boolean,
        ): MovementPattern {
            if (!compound) return MovementPattern.ISOLATION
            return when (primary) {
                MuscleGroup.CHEST -> MovementPattern.HORIZONTAL_PUSH
                MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS -> MovementPattern.VERTICAL_PUSH
                MuscleGroup.UPPER_BACK, MuscleGroup.REAR_DELTS -> MovementPattern.HORIZONTAL_PULL
                MuscleGroup.LATS, MuscleGroup.TRAPS -> MovementPattern.VERTICAL_PULL
                MuscleGroup.LOWER_BACK -> MovementPattern.HINGE
                MuscleGroup.QUADS -> if (force == "push") MovementPattern.SQUAT else MovementPattern.LUNGE
                MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES -> MovementPattern.HINGE
                MuscleGroup.ABS, MuscleGroup.OBLIQUES -> MovementPattern.CORE
                else -> MovementPattern.ISOLATION
            }
        }

        internal data class EquipmentResolution(val equipmentIds: List<String>, val excluded: Boolean)

        /**
         * Resolves free-exercise-db's broad `equipment` string against the real gym
         * inventory in [GymEquipmentSeed.looseEquipmentMapping]. A string that maps to
         * an explicit (possibly empty) list is trusted as-is — "body only" genuinely
         * needs no equipment. A string absent from the map ("machine", "e-z curl bar",
         * "exercise ball", "other", or anything unrecognised) is too coarse to match to
         * real hardware, so the exercise is excluded rather than guessing — fail closed,
         * per COACHING.md §2 / GYM.md.
         */
        internal fun resolveEquipment(equipment: String?): EquipmentResolution {
            val key = equipment?.lowercase() ?: "body only"
            val ids = GymEquipmentSeed.looseEquipmentMapping[key]
            return if (ids != null) {
                EquipmentResolution(ids, excluded = false)
            } else {
                EquipmentResolution(emptyList(), excluded = true)
            }
        }

        internal fun deriveFatigueCost(
            primary: MuscleGroup,
            compound: Boolean,
            category: String?,
        ): Int {
            if (category == "stretching") return 1
            if (!compound) return 2
            return when (primary) {
                MuscleGroup.QUADS, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS -> 5
                MuscleGroup.CHEST, MuscleGroup.LATS,
                MuscleGroup.UPPER_BACK, MuscleGroup.LOWER_BACK -> 4
                else -> 3
            }
        }
    }
}
