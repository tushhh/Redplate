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
        val equipment = defaultEquipment()

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

            return ExerciseEntity(
                id = id,
                name = name,
                pattern = derivePattern(force, primary, compound),
                primaryMuscle = primary,
                secondaryMuscles = secondary,
                requiredEquipmentIds = listOf(mapEquipmentId(equipment)),
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

        internal fun mapEquipmentId(equipment: String?): String = when (equipment?.lowercase()) {
            "barbell" -> "barbell_olympic"
            "dumbbell" -> "dumbbell_set"
            "machine" -> "machine_generic"
            "cable" -> "cable_machine"
            "body only", null -> "bodyweight"
            "kettlebells" -> "kettlebell_set"
            "bands" -> "band_set"
            "e-z curl bar" -> "ez_curl_bar"
            "medicine ball" -> "medicine_ball"
            "exercise ball" -> "exercise_ball"
            "foam roll" -> "foam_roller"
            else -> "other"
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

        internal fun defaultEquipment(): List<EquipmentEntity> = listOf(
            EquipmentEntity(
                id = "barbell_olympic",
                displayName = "Olympic Barbell",
                category = EquipmentCategory.BARBELL,
                loadingScheme = LoadingScheme.PLATE_LOADED,
                barWeightKg = 20.0,
                platePairs = mapOf(
                    25.0 to 1, 20.0 to 1, 15.0 to 1,
                    10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2,
                ),
            ),
            EquipmentEntity(
                id = "dumbbell_set",
                displayName = "Dumbbells",
                category = EquipmentCategory.DUMBBELL,
                loadingScheme = LoadingScheme.FIXED_INCREMENT,
                availableLoads = (1..20).map { it * 2.0 },
            ),
            EquipmentEntity(
                id = "machine_generic",
                displayName = "Machine",
                category = EquipmentCategory.MACHINE,
                loadingScheme = LoadingScheme.PIN_STACK,
                availableLoads = (1..20).map { it * 5.0 },
            ),
            EquipmentEntity(
                id = "cable_machine",
                displayName = "Cable Machine",
                category = EquipmentCategory.CABLE,
                loadingScheme = LoadingScheme.PIN_STACK,
                availableLoads = (1..20).map { it * 5.0 },
            ),
            EquipmentEntity(
                id = "bodyweight",
                displayName = "Bodyweight",
                category = EquipmentCategory.BODYWEIGHT,
                loadingScheme = LoadingScheme.BODYWEIGHT,
            ),
            EquipmentEntity(
                id = "kettlebell_set",
                displayName = "Kettlebells",
                category = EquipmentCategory.KETTLEBELL,
                loadingScheme = LoadingScheme.FIXED_INCREMENT,
                availableLoads = listOf(8.0, 12.0, 16.0, 20.0, 24.0, 28.0, 32.0),
            ),
            EquipmentEntity(
                id = "band_set",
                displayName = "Resistance Bands",
                category = EquipmentCategory.BAND,
                loadingScheme = LoadingScheme.BANDED,
            ),
            EquipmentEntity(
                id = "ez_curl_bar",
                displayName = "EZ Curl Bar",
                category = EquipmentCategory.BARBELL,
                loadingScheme = LoadingScheme.PLATE_LOADED,
                barWeightKg = 10.0,
                platePairs = mapOf(10.0 to 1, 5.0 to 2, 2.5 to 2, 1.25 to 2),
            ),
            EquipmentEntity(
                id = "medicine_ball",
                displayName = "Medicine Ball",
                category = EquipmentCategory.OTHER,
                loadingScheme = LoadingScheme.FIXED_INCREMENT,
                availableLoads = listOf(3.0, 5.0, 8.0, 10.0),
            ),
            EquipmentEntity(
                id = "exercise_ball",
                displayName = "Exercise Ball",
                category = EquipmentCategory.OTHER,
                loadingScheme = LoadingScheme.BODYWEIGHT,
            ),
            EquipmentEntity(
                id = "foam_roller",
                displayName = "Foam Roller",
                category = EquipmentCategory.OTHER,
                loadingScheme = LoadingScheme.BODYWEIGHT,
            ),
            EquipmentEntity(
                id = "other",
                displayName = "Other",
                category = EquipmentCategory.OTHER,
                loadingScheme = LoadingScheme.BODYWEIGHT,
            ),
        )
    }
}
