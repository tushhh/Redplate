package dev.redplate.data

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Single-enum converters (stored as name, not ordinal) ─────────

    @TypeConverter fun fromGoal(v: Goal): String = v.name
    @TypeConverter fun toGoal(v: String): Goal = Goal.valueOf(v)

    @TypeConverter fun fromMuscleGroup(v: MuscleGroup): String = v.name
    @TypeConverter fun toMuscleGroup(v: String): MuscleGroup = MuscleGroup.valueOf(v)

    @TypeConverter fun fromMovementPattern(v: MovementPattern): String = v.name
    @TypeConverter fun toMovementPattern(v: String): MovementPattern = MovementPattern.valueOf(v)

    @TypeConverter fun fromEquipmentCategory(v: EquipmentCategory): String = v.name
    @TypeConverter fun toEquipmentCategory(v: String): EquipmentCategory = EquipmentCategory.valueOf(v)

    @TypeConverter fun fromLoadingScheme(v: LoadingScheme): String = v.name
    @TypeConverter fun toLoadingScheme(v: String): LoadingScheme = LoadingScheme.valueOf(v)

    @TypeConverter fun fromComplexity(v: Complexity): String = v.name
    @TypeConverter fun toComplexity(v: String): Complexity = Complexity.valueOf(v)

    @TypeConverter fun fromProgressionRule(v: ProgressionRule): String = v.name
    @TypeConverter fun toProgressionRule(v: String): ProgressionRule = ProgressionRule.valueOf(v)

    @TypeConverter fun fromBlockPhase(v: BlockPhase): String = v.name
    @TypeConverter fun toBlockPhase(v: String): BlockPhase = BlockPhase.valueOf(v)

    // ── Enum-list converters (JSON array of name strings) ────────────

    @TypeConverter fun fromMuscleGroupList(v: List<MuscleGroup>): String =
        json.encodeToString(v.map { it.name })

    @TypeConverter fun toMuscleGroupList(v: String): List<MuscleGroup> =
        json.decodeFromString<List<String>>(v).map { MuscleGroup.valueOf(it) }

    @TypeConverter fun fromMovementPatternList(v: List<MovementPattern>): String =
        json.encodeToString(v.map { it.name })

    @TypeConverter fun toMovementPatternList(v: String): List<MovementPattern> =
        json.decodeFromString<List<String>>(v).map { MovementPattern.valueOf(it) }

    // ── Primitive-list converters ────────────────────────────────────

    @TypeConverter fun fromDoubleList(v: List<Double>): String = json.encodeToString(v)
    @TypeConverter fun toDoubleList(v: String): List<Double> = json.decodeFromString(v)

    @TypeConverter fun fromStringList(v: List<String>): String = json.encodeToString(v)
    @TypeConverter fun toStringList(v: String): List<String> = json.decodeFromString(v)

    // ── Map converter (platePairs: kg → pair count) ─────────────────

    @TypeConverter fun fromDoubleIntMap(v: Map<Double, Int>): String =
        json.encodeToString(v.mapKeys { (k, _) -> k.toString() })

    @TypeConverter fun toDoubleIntMap(v: String): Map<Double, Int> =
        json.decodeFromString<Map<String, Int>>(v).mapKeys { (k, _) -> k.toDouble() }
}
