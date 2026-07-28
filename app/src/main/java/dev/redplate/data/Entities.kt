package dev.redplate.data

import androidx.room.*
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

@Serializable
enum class MuscleGroup {
    CHEST, UPPER_BACK, LATS, LOWER_BACK, FRONT_DELTS, SIDE_DELTS, REAR_DELTS,
    BICEPS, TRICEPS, FOREARMS, QUADS, HAMSTRINGS, GLUTES, ADDUCTORS, CALVES,
    ABS, OBLIQUES, TRAPS, NECK
}

@Serializable
enum class MovementPattern {
    HORIZONTAL_PUSH, VERTICAL_PUSH, HORIZONTAL_PULL, VERTICAL_PULL,
    SQUAT, HINGE, LUNGE, CARRY, ISOLATION, CORE
}

@Serializable
enum class EquipmentCategory {
    BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, BAND, KETTLEBELL, CARDIO_MACHINE, OTHER
}

/** Determines how load is selected and therefore how progression may step. */
@Serializable
enum class LoadingScheme {
    PLATE_LOADED,     // barbell / plate machine — increment = 2x smallest plate pair
    FIXED_INCREMENT,  // dumbbells, kettlebells — only discrete sizes owned
    PIN_STACK,        // selectorised machine — fixed stack increments
    BODYWEIGHT,       // load = bodyweight (+ optional added)
    BANDED            // qualitative resistance
}

/**
 * What the user is training for. Drives rep ranges, rest and volume distribution.
 *
 * Stored as its name through [Converters], so adding a value is not a schema change.
 * LEAN exists because the intake offers it as a distinct answer (design 2c); it is the
 * hypertrophy prescription with shorter rests, never a diet setting — COACHING.md §1
 * rules out weight targets entirely.
 */
@Serializable
enum class Goal { STRENGTH, HYPERTROPHY, LEAN, GENERAL }

@Serializable
enum class ProgressionRule { DOUBLE_PROGRESSION, LOAD_PROGRESSION, RIR_AUTOREGULATED, NONE }

@Serializable
enum class Complexity { BEGINNER, INTERMEDIATE, ADVANCED }

/**
 * Where a session sits in its block. A Redplate block is [MesocycleEntity.lengthWeeks]
 * long: accumulation until the final week, then a deload. There is no intensification
 * phase — an `INTENSIFICATION` value existed here, was never written by anything, and was
 * removed rather than left as a value the app could not produce.
 */
@Serializable
enum class BlockPhase { ACCUMULATION, DELOAD }

// ---------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------

@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,                 // single-user app: always row 1
    val trainingAgeMonths: Int,
    val daysPerWeek: Int,                        // 2..6
    val sessionCeilingMinutes: Int,              // 30/45/60/75/90
    val goal: Goal,
    val bodyweightKg: Double,
    val priorityMuscles: List<MuscleGroup> = emptyList(),   // max 2
    val excludedPatterns: List<MovementPattern> = emptyList(),
    val readinessFlagged: Boolean = false,       // from the one-time screening
    val useMetric: Boolean = true,
    /**
     * Which weekdays the user actually trains on, 0 = Monday. Null falls back to the
     * split's own layout. Validated against [daysPerWeek] before it is written.
     */
    val trainingDays: List<Int>? = null,
    /**
     * Hour at which a training day begins. A session logged before it belongs to the
     * previous training day — see [TrainingClock].
     *
     * The literal default must match [TrainingClock.DEFAULT_DAY_START_HOUR]; Room needs a
     * compile-time constant here so the migration's `DEFAULT 4` and the entity agree.
     */
    @ColumnInfo(defaultValue = "4")
    val dayStartHour: Int = TrainingClock.DEFAULT_DAY_START_HOUR
)

// ---------------------------------------------------------------------------
// Equipment — see COACHING.md §2. This is the differentiator.
// ---------------------------------------------------------------------------

@Serializable
@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val category: EquipmentCategory,
    val loadingScheme: LoadingScheme,
    /**
     * The loads that PHYSICALLY EXIST for this equipment, ascending, in kg.
     * FIXED_INCREMENT: every dumbbell owned, e.g. [2.5, 5.0, 7.5, 10.0, ...]
     * PIN_STACK:       every pin position, e.g. [5.0, 10.0, 15.0, ...]
     * PLATE_LOADED:    empty — derived from `plates` + `barWeightKg`
     */
    val availableLoads: List<Double> = emptyList(),
    val barWeightKg: Double? = null,
    /** Pairs available per plate size, e.g. {25.0: 2, 20.0: 4, 1.25: 1} */
    val platePairs: Map<Double, Int> = emptyMap(),
    val isAvailable: Boolean = true
) {
    /** Smallest load step this equipment can actually make. Never progress by less than this. */
    fun minIncrement(): Double = when (loadingScheme) {
        LoadingScheme.PLATE_LOADED -> (platePairs.keys.minOrNull() ?: 1.25) * 2
        LoadingScheme.FIXED_INCREMENT, LoadingScheme.PIN_STACK ->
            availableLoads.zipWithNext { a, b -> b - a }.minOrNull() ?: 2.5
        LoadingScheme.BODYWEIGHT -> 1.25
        LoadingScheme.BANDED -> 0.0
    }

    /**
     * Snap a desired load to the nearest assemblable one, in either direction.
     *
     * Use this when the goal is "the weight closest to what was asked for" — picking a
     * starting load, or snapping a stepper. Use [largestLoadableAtOrBelow] when overshooting
     * would be wrong. These two were one function under this name, which rounded to nearest
     * for stacks and downward for barbells: two contracts, one name.
     */
    fun nearestAchievable(desiredKg: Double): Double = when (loadingScheme) {
        LoadingScheme.FIXED_INCREMENT, LoadingScheme.PIN_STACK ->
            availableLoads.minByOrNull { kotlin.math.abs(it - desiredKg) } ?: desiredKg

        LoadingScheme.PLATE_LOADED -> {
            val below = PlateMath.largestLoadableAtOrBelow(desiredKg, this)
            val above = PlateMath.nextLoadUp(below, this)
            if (above <= below || desiredKg - below <= above - desiredKg) below else above
        }

        else -> desiredKg
    }

    /**
     * Heaviest assemblable load not above [desiredKg]. Never overshoots — this is what a
     * deload and any "back off to" prescription mean.
     */
    fun largestLoadableAtOrBelow(desiredKg: Double): Double = when (loadingScheme) {
        LoadingScheme.FIXED_INCREMENT, LoadingScheme.PIN_STACK ->
            availableLoads.lastOrNull { it <= desiredKg + 1e-6 }
                ?: availableLoads.firstOrNull()
                ?: desiredKg

        LoadingScheme.PLATE_LOADED -> PlateMath.largestLoadableAtOrBelow(desiredKg, this)

        else -> desiredKg
    }
}

// ---------------------------------------------------------------------------
// Exercises
// ---------------------------------------------------------------------------

@Serializable
@Entity(
    tableName = "exercises",
    indices = [Index("primaryMuscle"), Index("pattern")]
)
data class ExerciseEntity(
    @PrimaryKey val id: String,                  // slug from free-exercise-db, or uuid if custom
    val name: String,
    val pattern: MovementPattern,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),   // 0.5 set credit each
    val requiredEquipmentIds: List<String> = emptyList(),
    val complexity: Complexity = Complexity.INTERMEDIATE,
    /** Systemic cost 1..5. Drives session ordering and MRV accounting. */
    val fatigueCost: Int = 3,
    val isCompound: Boolean = true,
    val defaultProgression: ProgressionRule = ProgressionRule.DOUBLE_PROGRESSION,
    // --- Guidance (COACHING.md §4) ---
    /** Step-by-step text from free-exercise-db. Public domain, always bundled. */
    val instructions: String? = null,
    /** Asset paths for start/end position stills. Bundled only for active-program exercises. */
    val imageAssetPaths: List<String> = emptyList(),
    /** URI of a form video the user recorded themselves. App-private storage. */
    val userFormVideoUri: String? = null,
    /** Set true once the guidance sheet has auto-opened. Prevents re-prompting. */
    val hasBeenIntroduced: Boolean = false,

    val isCustom: Boolean = false,
    val isExcluded: Boolean = false              // injury / dislike
)

// ---------------------------------------------------------------------------
// Programming: Mesocycle -> Week -> Session template -> prescribed slots
// ---------------------------------------------------------------------------

@Serializable
@Entity(tableName = "mesocycles")
data class MesocycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val goal: Goal,
    val startedAt: Long,
    val lengthWeeks: Int = 5,                    // 4 accumulation + 1 deload
    val currentWeek: Int = 1,
    val isActive: Boolean = true,
    val completedAt: Long? = null
) {
    /**
     * A block accumulates until its final week, which is the deload. Sessions used to be
     * stamped [BlockPhase.ACCUMULATION] unconditionally, so a deload week's history was
     * indistinguishable from a hard one.
     */
    fun phaseForWeek(week: Int): BlockPhase =
        if (week >= lengthWeeks) BlockPhase.DELOAD else BlockPhase.ACCUMULATION
}

@Serializable
@Entity(
    tableName = "session_templates",
    foreignKeys = [ForeignKey(
        entity = MesocycleEntity::class,
        parentColumns = ["id"], childColumns = ["mesocycleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("mesocycleId")]
)
data class SessionTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mesocycleId: Long,
    val label: String,                           // "Upper A", "Push", "Full Body B"
    val dayIndex: Int                            // 0-based position in the weekly rotation
)

@Serializable
@Entity(
    tableName = "template_slots",
    foreignKeys = [ForeignKey(
        entity = SessionTemplateEntity::class,
        parentColumns = ["id"], childColumns = ["templateId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("templateId"), Index("exerciseId")]
)
data class TemplateSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int,
    val repRangeLow: Int,
    val repRangeHigh: Int,
    val targetRir: Int,
    val restSeconds: Int,
    val progression: ProgressionRule,
    /** Null until the first session; thereafter carried forward by the engine. */
    val workingLoadKg: Double? = null,
    val supersetGroup: Int? = null
)

// ---------------------------------------------------------------------------
// Logging
// ---------------------------------------------------------------------------

@Serializable
@Entity(
    tableName = "sessions",
    indices = [Index("startedAt"), Index("templateId")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,                       // null = freestyle session
    val mesocycleId: Long?,
    val weekNumber: Int?,
    val phase: BlockPhase = BlockPhase.ACCUMULATION,
    val startedAt: Long,
    val endedAt: Long? = null,
    val bodyweightKg: Double? = null,
    val notes: String? = null
)

@Serializable
@Entity(
    tableName = "set_logs",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"], childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("exerciseId"), Index("completedAt")]
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val setIndex: Int,
    val loadKg: Double,
    val reps: Int,
    val rir: Int?,                               // null = not reported
    val isWarmup: Boolean = false,
    val completedAt: Long,
    val restTakenSeconds: Int? = null,
    /** Denormalised for fast volume queries. Set at insert time. */
    val countsTowardVolume: Boolean = !isWarmup && (rir == null || rir <= 3)
) {
    /** Epley. Only meaningful for reps <= 12. */
    fun estimated1Rm(): Double = loadKg * (1 + reps / 30.0)
}

@Serializable
@Entity(
    tableName = "volume_snapshots",
    primaryKeys = ["mesocycleId", "weekNumber", "muscle"],
    foreignKeys = [ForeignKey(
        entity = MesocycleEntity::class,
        parentColumns = ["id"], childColumns = ["mesocycleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("mesocycleId")],
)
data class VolumeSnapshotEntity(
    val mesocycleId: Long,
    val weekNumber: Int,
    val muscle: MuscleGroup,
    val hardSets: Double,                        // fractional: secondaries count 0.5
    val mev: Int,
    val mav: Int,
    val mrv: Int
)

@Serializable
@Entity(tableName = "volume_landmarks")
data class VolumeLandmarkEntity(
    @PrimaryKey val muscle: MuscleGroup,
    val mv: Int,
    val mev: Int,
    val mavLow: Int,
    val mavHigh: Int,
    val mrv: Int,
    val userAdjusted: Boolean = false
)