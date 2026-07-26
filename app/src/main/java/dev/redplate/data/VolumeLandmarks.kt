package dev.redplate.data

/**
 * Default weekly hard-set landmarks per muscle, straight from COACHING.md §3.
 *
 * MV   — maintenance: enough to hold what you have
 * MEV  — minimum effective: where a block starts
 * MAV  — adaptive range: where most of a block is spent
 * MRV  — recoverable maximum: the ceiling a block approaches, then deloads from
 *
 * These are starting values, not truth. [VolumeLandmarkEntity.userAdjusted] marks a row
 * the user has overridden so regeneration never stamps on their own numbers.
 */
object VolumeLandmarks {

    val DEFAULTS: List<VolumeLandmarkEntity> = listOf(
        landmark(MuscleGroup.CHEST, mv = 6, mev = 8, mavLow = 12, mavHigh = 18, mrv = 20),
        landmark(MuscleGroup.UPPER_BACK, mv = 8, mev = 10, mavLow = 14, mavHigh = 20, mrv = 24),
        landmark(MuscleGroup.LATS, mv = 8, mev = 10, mavLow = 14, mavHigh = 20, mrv = 24),
        landmark(MuscleGroup.LOWER_BACK, mv = 4, mev = 6, mavLow = 8, mavHigh = 12, mrv = 14),
        landmark(MuscleGroup.FRONT_DELTS, mv = 4, mev = 6, mavLow = 8, mavHigh = 12, mrv = 16),
        landmark(MuscleGroup.SIDE_DELTS, mv = 6, mev = 8, mavLow = 12, mavHigh = 20, mrv = 24),
        landmark(MuscleGroup.REAR_DELTS, mv = 6, mev = 8, mavLow = 12, mavHigh = 20, mrv = 24),
        landmark(MuscleGroup.BICEPS, mv = 4, mev = 6, mavLow = 10, mavHigh = 16, mrv = 20),
        landmark(MuscleGroup.TRICEPS, mv = 4, mev = 6, mavLow = 10, mavHigh = 16, mrv = 20),
        landmark(MuscleGroup.FOREARMS, mv = 2, mev = 4, mavLow = 6, mavHigh = 10, mrv = 12),
        landmark(MuscleGroup.QUADS, mv = 6, mev = 8, mavLow = 12, mavHigh = 18, mrv = 20),
        landmark(MuscleGroup.HAMSTRINGS, mv = 4, mev = 6, mavLow = 10, mavHigh = 16, mrv = 18),
        landmark(MuscleGroup.GLUTES, mv = 4, mev = 6, mavLow = 10, mavHigh = 16, mrv = 18),
        landmark(MuscleGroup.ADDUCTORS, mv = 2, mev = 4, mavLow = 6, mavHigh = 10, mrv = 12),
        landmark(MuscleGroup.CALVES, mv = 6, mev = 8, mavLow = 12, mavHigh = 16, mrv = 20),
        landmark(MuscleGroup.ABS, mv = 0, mev = 4, mavLow = 8, mavHigh = 16, mrv = 20),
        landmark(MuscleGroup.OBLIQUES, mv = 0, mev = 4, mavLow = 6, mavHigh = 12, mrv = 16),
        landmark(MuscleGroup.TRAPS, mv = 4, mev = 6, mavLow = 10, mavHigh = 16, mrv = 20),
        landmark(MuscleGroup.NECK, mv = 0, mev = 2, mavLow = 4, mavHigh = 8, mrv = 10),
    )

    private val byMuscle = DEFAULTS.associateBy { it.muscle }

    fun forMuscle(muscle: MuscleGroup): VolumeLandmarkEntity =
        byMuscle.getValue(muscle)

    private fun landmark(
        muscle: MuscleGroup,
        mv: Int,
        mev: Int,
        mavLow: Int,
        mavHigh: Int,
        mrv: Int,
    ) = VolumeLandmarkEntity(
        muscle = muscle,
        mv = mv,
        mev = mev,
        mavLow = mavLow,
        mavHigh = mavHigh,
        mrv = mrv,
    )
}
