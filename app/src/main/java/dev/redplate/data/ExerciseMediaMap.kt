package dev.redplate.data

/**
 * Maps Redplate's own exercise ids to the filename stem of the bundled stills.
 *
 * The images come from `free-exercise-db` (public domain) and keep that project's
 * naming — `Barbell_Squat_start.jpg`. Our exercise ids are our own — `barbell_back_squat`.
 * Without this table [MediaResolver] looks for `barbell_back_squat_start.jpg`, finds
 * nothing, and every exercise falls back to the placeholder while the images sit unused
 * in the APK. That is exactly what was happening before this map existed.
 *
 * A missing entry is not an error. Cardio and landmine work have no faithful still in
 * the dataset, so they are deliberately absent and render the muscle-group placeholder
 * rather than a misleading picture of a different movement.
 */
object ExerciseMediaMap {

    /** Redplate exercise id → asset filename stem, without the `_start` / `_end` suffix. */
    val ASSET_STEMS: Map<String, String> = mapOf(
        "cable_crossover_high"        to "Cable_Crossover",
        "cable_crossover_low"         to "Low_Cable_Crossover",
        "cable_face_pull"             to "Face_Pull",
        "cable_straight_arm_pulldown" to "Straight-Arm_Pulldown",
        "cable_tricep_pushdown"       to "Triceps_Pushdown",
        "cable_overhead_ext"          to "Cable_Rope_Overhead_Triceps_Extension",
        "cable_bicep_curl"            to "Standing_Biceps_Cable_Curl",
        "cable_lateral_raise"         to "Cable_Seated_Lateral_Raise",
        "cable_rear_delt_fly"         to "Cable_Rear_Delt_Fly",
        "cable_woodchop"              to "Standing_Cable_Wood_Chop",
        "pallof_press"                to "Pallof_Press",
        "cable_pull_through"          to "Pull_Through",
        "cable_glute_kickback"        to "One-Legged_Cable_Kickback",
        "machine_hip_adduction"       to "Thigh_Adductor",
        "machine_hip_abduction"       to "Thigh_Abductor",
        "machine_pec_fly"             to "Butterfly",
        "machine_rear_delt_fly"       to "Reverse_Flyes",
        "machine_chest_press"         to "Machine_Bench_Press",
        "machine_shoulder_press"      to "Machine_Shoulder_Military_Press",
        "machine_leg_curl"            to "Lying_Leg_Curls",
        "lat_pulldown_wide"           to "Wide-Grip_Lat_Pulldown",
        "lat_pulldown_close"          to "Close-Grip_Front_Lat_Pulldown",
        "seated_cable_row"            to "Seated_Cable_Rows",
        "conventional_deadlift"       to "Barbell_Deadlift",
        "sumo_deadlift"               to "Sumo_Deadlift",
        "romanian_deadlift_bb"        to "Romanian_Deadlift",
        "barbell_bent_over_row"       to "Bent_Over_Barbell_Row",
        "power_clean"                 to "Power_Clean",
        "barbell_back_squat"          to "Barbell_Squat",
        "barbell_front_squat"         to "Front_Barbell_Squat",
        "barbell_flat_bench"          to "Barbell_Bench_Press_-_Medium_Grip",
        "barbell_close_grip_bench"    to "Close-Grip_Barbell_Bench_Press",
        "barbell_ohp"                 to "Barbell_Shoulder_Press",
        "barbell_reverse_lunge"       to "Barbell_Lunge",
        "barbell_calf_raise"          to "Standing_Barbell_Calf_Raise",
        "leg_press"                   to "Leg_Press",
        "leg_press_calf_raise"        to "Calf_Press_On_The_Leg_Press_Machine",
        "smith_hack_squat"            to "Smith_Machine_Squat",
        "smith_rdl"                   to "Smith_Machine_Stiff-Legged_Deadlift",
        "smith_calf_raise"            to "Smith_Machine_Calf_Raise",
        "smith_bent_over_row"         to "Smith_Machine_Bent_Over_Row",
        "db_lateral_raise"            to "Side_Lateral_Raise",
        "db_front_raise"              to "Front_Dumbbell_Raise",
        "db_rear_delt_fly"            to "Seated_Bent-Over_Rear_Delt_Raise",
        "db_bicep_curl"               to "Dumbbell_Bicep_Curl",
        "db_hammer_curl"              to "Hammer_Curls",
        "db_overhead_tricep_ext"      to "Standing_Dumbbell_Triceps_Extension",
        "db_shoulder_press"           to "Standing_Dumbbell_Press",
        "db_shrug"                    to "Dumbbell_Shrug",
        "db_flat_bench"               to "Dumbbell_Bench_Press",
        "db_incline_bench"            to "Incline_Dumbbell_Press",
        "db_flat_fly"                 to "Dumbbell_Flyes",
        "db_pullover"                 to "Straight-Arm_Dumbbell_Pullover",
        "db_single_arm_row"           to "One-Arm_Dumbbell_Row",
        "db_rdl"                      to "Stiff-Legged_Dumbbell_Deadlift",
        "db_goblet_squat"             to "Goblet_Squat",
        "db_lunge"                    to "Dumbbell_Lunges",
        "db_bulgarian_split_squat"    to "Split_Squat_with_Dumbbells",
        "barbell_curl"                to "Barbell_Curl",
        "barbell_skullcrusher"        to "EZ-Bar_Skullcrusher",
        "barbell_shrug"               to "Barbell_Shrug",
        "decline_bench_press"         to "Decline_Barbell_Bench_Press",
        "decline_sit_up"              to "Decline_Crunch",
        "bench_tricep_dip"            to "Bench_Dips",
        "bench_step_up"               to "Dumbbell_Step_Ups",
        "hyperextension"              to "Hyperextensions_Back_Extensions",
        "sled_push"                   to "Sled_Push",
        "push_up"                     to "Pushups",
        "plank"                       to "Plank",
        "bodyweight_squat"            to "Bodyweight_Squat",
    )

    /**
     * Entries deliberately absent, and why — so nobody "helpfully" adds them back.
     *
     * Each of these had a still that showed a different movement, a different machine, or
     * the exact opposite of the exercise. A picture that contradicts the name is worse than
     * no picture: the user copies the picture. `adb push`-ing a correct still keyed by the
     * exercise id restores any of them without a rebuild (see [MediaResolver]).
     *
     * - `leg_press_wide` — the only leg-press-stance still is `Narrow_Stance_Leg_Press`,
     *   which is the opposite stance to the one the exercise is named for.
     * - `barbell_upright_row` — the still is `Upright_Row_-_With_Bands`. No barbell, and
     *   this gym's bands are marked unavailable.
     * - `glute_focused_extension` — the still is literally
     *   `Hyperextensions_With_No_Hyperextension_Bench`, and the exercise requires the bench.
     * - `incline_barbell_bench` — now a plate-loaded machine press; the still is a barbell
     *   on an incline bench, which is different hardware.
     * - `machine_hip_thrust` — the still is a barbell across the hips on a bench, not the
     *   Glute Drive machine the exercise is performed on.
     */
    val DELIBERATELY_UNMAPPED: Set<String> = setOf(
        "leg_press_wide",
        "barbell_upright_row",
        "glute_focused_extension",
        "incline_barbell_bench",
        "machine_hip_thrust",
    )

    fun stemFor(exerciseId: String): String? = ASSET_STEMS[exerciseId]
}
