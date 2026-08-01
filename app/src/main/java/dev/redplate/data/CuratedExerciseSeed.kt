package dev.redplate.data

/**
 * Curated exercise list derived from the gym's actual equipment and workouts.md.
 * Each exercise is unique — similar variations collapsed into the most useful one.
 * Grouped by equipment piece for equipment-aware filtering.
 */
object CuratedExerciseSeed {

    fun seed(): List<ExerciseEntity> = buildList {
        // ── Stairmill ──
        add(exercise("stairmill_climbing", "Stair Climbing", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES, MuscleGroup.CALVES), "stairmill", MovementPattern.SQUAT, compound = true, fatigue = 3))

        // ── Treadmill ──
        add(exercise("treadmill_incline_walk", "Incline Walking", MuscleGroup.GLUTES, listOf(MuscleGroup.CALVES, MuscleGroup.HAMSTRINGS), "treadmill", MovementPattern.CARRY, compound = true, fatigue = 2))

        // ── Concept2 Rower ──
        add(exercise("rower_full_body", "Rowing (Full Body)", MuscleGroup.UPPER_BACK, listOf(MuscleGroup.LATS, MuscleGroup.QUADS, MuscleGroup.BICEPS), "concept2_rower", MovementPattern.HORIZONTAL_PULL, compound = true, fatigue = 3))

        // ── Dual Adjustable Pulley ──
        add(exercise("cable_crossover_high", "Cable Crossover (High-to-Low)", MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTS), "dual_adjustable_pulley", MovementPattern.HORIZONTAL_PUSH, compound = false, fatigue = 2))
        add(exercise("cable_crossover_low", "Cable Crossover (Low-to-High)", MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTS), "dual_adjustable_pulley", MovementPattern.HORIZONTAL_PUSH, compound = false, fatigue = 2))
        add(exercise("cable_face_pull", "Cable Face Pull", MuscleGroup.REAR_DELTS, listOf(MuscleGroup.TRAPS, MuscleGroup.UPPER_BACK), "dual_adjustable_pulley", MovementPattern.HORIZONTAL_PULL, compound = true, fatigue = 2))
        add(exercise("cable_row_standing", "Standing Cable Row", MuscleGroup.UPPER_BACK, listOf(MuscleGroup.LATS, MuscleGroup.BICEPS), "dual_adjustable_pulley", MovementPattern.HORIZONTAL_PULL, compound = true, fatigue = 3))
        add(exercise("cable_straight_arm_pulldown", "Straight-Arm Pulldown", MuscleGroup.LATS, listOf(MuscleGroup.TRICEPS), "dual_adjustable_pulley", MovementPattern.VERTICAL_PULL, compound = false, fatigue = 2))
        add(exercise("cable_tricep_pushdown", "Tricep Rope Pushdown", MuscleGroup.TRICEPS, listOf(MuscleGroup.FRONT_DELTS), "dual_adjustable_pulley", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("cable_overhead_ext", "Overhead Cable Tricep Extension", MuscleGroup.TRICEPS, listOf(MuscleGroup.FRONT_DELTS), "dual_adjustable_pulley", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("cable_bicep_curl", "Cable Bicep Curl", MuscleGroup.BICEPS, listOf(MuscleGroup.FOREARMS), "dual_adjustable_pulley", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("cable_lateral_raise", "Cable Lateral Raise", MuscleGroup.SIDE_DELTS, listOf(MuscleGroup.TRAPS), "dual_adjustable_pulley", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("cable_rear_delt_fly", "Cable Rear Delt Fly", MuscleGroup.REAR_DELTS, listOf(MuscleGroup.TRAPS), "dual_adjustable_pulley", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("cable_woodchop", "Cable Woodchop", MuscleGroup.ABS, listOf(MuscleGroup.OBLIQUES), "dual_adjustable_pulley", MovementPattern.CORE, compound = true, fatigue = 2))
        add(exercise("pallof_press", "Pallof Press", MuscleGroup.ABS, listOf(MuscleGroup.OBLIQUES), "dual_adjustable_pulley", MovementPattern.CORE, compound = false, fatigue = 2))
        add(exercise("cable_pull_through", "Cable Pull-Through", MuscleGroup.GLUTES, listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.LOWER_BACK), "dual_adjustable_pulley", MovementPattern.HINGE, compound = true, fatigue = 3))
        add(exercise("cable_glute_kickback", "Cable Glute Kickback", MuscleGroup.GLUTES, listOf(MuscleGroup.HAMSTRINGS), "dual_adjustable_pulley", MovementPattern.ISOLATION, compound = false, fatigue = 2))

        // ── Hip Adductor / Abductor ──
        add(exercise("machine_hip_adduction", "Machine Hip Adduction", MuscleGroup.ADDUCTORS, listOf(MuscleGroup.GLUTES), "hip_adductor_abductor", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("machine_hip_abduction", "Machine Hip Abduction", MuscleGroup.GLUTES, listOf(MuscleGroup.QUADS), "hip_adductor_abductor", MovementPattern.ISOLATION, compound = false, fatigue = 2))

        // ── Pec Fly / Rear Delt ──
        add(exercise("machine_pec_fly", "Machine Chest Fly", MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTS), "pec_fly_rear_delt", MovementPattern.HORIZONTAL_PUSH, compound = false, fatigue = 2))
        add(exercise("machine_rear_delt_fly", "Machine Rear Delt Fly", MuscleGroup.REAR_DELTS, listOf(MuscleGroup.TRAPS, MuscleGroup.UPPER_BACK), "pec_fly_rear_delt", MovementPattern.HORIZONTAL_PULL, compound = false, fatigue = 2))

        // ── Chest Press Machine ──
        add(exercise("machine_chest_press", "Machine Chest Press", MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.TRICEPS), "chest_press_machine", MovementPattern.HORIZONTAL_PUSH, compound = true, fatigue = 3))

        // ── Shoulder Press Machine ──
        add(exercise("machine_shoulder_press", "Machine Shoulder Press", MuscleGroup.FRONT_DELTS, listOf(MuscleGroup.TRICEPS, MuscleGroup.TRAPS), "shoulder_press_machine", MovementPattern.VERTICAL_PUSH, compound = true, fatigue = 3))

        // ── Leg Curl Machine ──
        add(exercise("machine_leg_curl", "Machine Hamstring Curl", MuscleGroup.HAMSTRINGS, listOf(MuscleGroup.CALVES), "leg_curl_machine", MovementPattern.ISOLATION, compound = false, fatigue = 2))

        // ── 4-Station Multi-Gym ──
        add(exercise("lat_pulldown_wide", "Wide-Grip Lat Pulldown", MuscleGroup.LATS, listOf(MuscleGroup.BICEPS, MuscleGroup.UPPER_BACK), "four_station_multigym", MovementPattern.VERTICAL_PULL, compound = true, fatigue = 3))
        add(exercise("lat_pulldown_close", "Close-Grip Lat Pulldown", MuscleGroup.LATS, listOf(MuscleGroup.BICEPS), "four_station_multigym", MovementPattern.VERTICAL_PULL, compound = true, fatigue = 3))
        add(exercise("seated_cable_row", "Seated Cable Row", MuscleGroup.UPPER_BACK, listOf(MuscleGroup.LATS, MuscleGroup.BICEPS), "four_station_multigym", MovementPattern.HORIZONTAL_PULL, compound = true, fatigue = 3))

        // ── Deadlift Platform ──
        add(exercise("conventional_deadlift", "Conventional Deadlift", MuscleGroup.LOWER_BACK, listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS, MuscleGroup.TRAPS), "deadlift_platform", MovementPattern.HINGE, compound = true, fatigue = 5, alsoNeeds = listOf("barbell")))
        add(exercise("sumo_deadlift", "Sumo Deadlift", MuscleGroup.GLUTES, listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.LOWER_BACK), "deadlift_platform", MovementPattern.HINGE, compound = true, fatigue = 5, alsoNeeds = listOf("barbell")))
        add(exercise("romanian_deadlift_bb", "Romanian Deadlift (Barbell)", MuscleGroup.HAMSTRINGS, listOf(MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK), "deadlift_platform", MovementPattern.HINGE, compound = true, fatigue = 4, alsoNeeds = listOf("barbell")))
        add(exercise("barbell_bent_over_row", "Barbell Bent-Over Row", MuscleGroup.UPPER_BACK, listOf(MuscleGroup.LATS, MuscleGroup.BICEPS, MuscleGroup.LOWER_BACK), "deadlift_platform", MovementPattern.HORIZONTAL_PULL, compound = true, fatigue = 4, alsoNeeds = listOf("barbell")))
        add(exercise("power_clean", "Power Clean", MuscleGroup.FRONT_DELTS, listOf(MuscleGroup.TRAPS, MuscleGroup.QUADS, MuscleGroup.GLUTES), "deadlift_platform", MovementPattern.VERTICAL_PUSH, compound = true, fatigue = 5, alsoNeeds = listOf("barbell")))

        // ── Half Racks ──
        add(exercise("barbell_back_squat", "Barbell Back Squat", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK, MuscleGroup.HAMSTRINGS), "power_rack", MovementPattern.SQUAT, compound = true, fatigue = 5, alsoNeeds = listOf("barbell")))
        add(exercise("barbell_front_squat", "Barbell Front Squat", MuscleGroup.QUADS, listOf(MuscleGroup.ABS, MuscleGroup.GLUTES, MuscleGroup.UPPER_BACK), "power_rack", MovementPattern.SQUAT, compound = true, fatigue = 5, alsoNeeds = listOf("barbell")))
        add(exercise("barbell_flat_bench", "Barbell Flat Bench Press", MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.TRICEPS), "flat_incline_bench", MovementPattern.HORIZONTAL_PUSH, compound = true, fatigue = 4, alsoNeeds = listOf("barbell")))
        add(exercise("barbell_close_grip_bench", "Close-Grip Bench Press", MuscleGroup.TRICEPS, listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTS), "flat_incline_bench", MovementPattern.HORIZONTAL_PUSH, compound = true, fatigue = 4, alsoNeeds = listOf("barbell")))
        add(exercise("barbell_ohp", "Standing Overhead Press", MuscleGroup.FRONT_DELTS, listOf(MuscleGroup.TRICEPS, MuscleGroup.TRAPS, MuscleGroup.ABS), "power_rack", MovementPattern.VERTICAL_PUSH, compound = true, fatigue = 4, alsoNeeds = listOf("barbell")))
        add(exercise("barbell_reverse_lunge", "Barbell Reverse Lunge", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS), "power_rack", MovementPattern.LUNGE, compound = true, fatigue = 4, alsoNeeds = listOf("barbell")))
        add(exercise("bulgarian_split_squat_bb", "Bulgarian Split Squat (Barbell)", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES), "power_rack", MovementPattern.LUNGE, compound = true, fatigue = 4, alsoNeeds = listOf("barbell")))
        add(exercise("pull_up", "Pull-Up", MuscleGroup.LATS, listOf(MuscleGroup.BICEPS, MuscleGroup.UPPER_BACK), "power_rack", MovementPattern.VERTICAL_PULL, compound = true, fatigue = 4))
        add(exercise("chin_up", "Chin-Up", MuscleGroup.LATS, listOf(MuscleGroup.BICEPS), "power_rack", MovementPattern.VERTICAL_PULL, compound = true, fatigue = 4))
        add(exercise("hanging_leg_raise", "Hanging Leg Raise", MuscleGroup.ABS, listOf(MuscleGroup.QUADS), "power_rack", MovementPattern.CORE, compound = false, fatigue = 2))
        add(exercise("barbell_calf_raise", "Standing Barbell Calf Raise", MuscleGroup.CALVES, listOf(MuscleGroup.QUADS), "power_rack", MovementPattern.ISOLATION, compound = false, fatigue = 2, alsoNeeds = listOf("barbell")))

        // ── Incline Chest Press (Dedicated Rack) ──
        add(exercise("incline_barbell_bench", "Incline Barbell Bench Press", MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.TRICEPS), "incline_chest_press_machine", MovementPattern.HORIZONTAL_PUSH, compound = true, fatigue = 4))

        // ── Leg Press ──
        add(exercise("leg_press", "Leg Press", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES), "leg_press_machine", MovementPattern.SQUAT, compound = true, fatigue = 4))
        add(exercise("leg_press_wide", "Wide-Stance Leg Press", MuscleGroup.GLUTES, listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS), "leg_press_machine", MovementPattern.SQUAT, compound = true, fatigue = 4))
        add(exercise("leg_press_calf_raise", "Leg Press Calf Raise", MuscleGroup.CALVES, listOf(MuscleGroup.QUADS), "leg_press_machine", MovementPattern.ISOLATION, compound = false, fatigue = 2))

        // ── Glute Drive ──
        add(exercise("machine_hip_thrust", "Machine Hip Thrust", MuscleGroup.GLUTES, listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS), "glute_drive_machine", MovementPattern.HINGE, compound = true, fatigue = 3))

        // ── Smith Machine ──
        add(exercise("smith_hack_squat", "Smith Machine Hack Squat", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES, MuscleGroup.CALVES), "smith_machine", MovementPattern.SQUAT, compound = true, fatigue = 4))
        add(exercise("smith_rdl", "Smith Machine Romanian Deadlift", MuscleGroup.HAMSTRINGS, listOf(MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK), "smith_machine", MovementPattern.HINGE, compound = true, fatigue = 4))
        add(exercise("smith_calf_raise", "Smith Machine Calf Raise", MuscleGroup.CALVES, listOf(MuscleGroup.QUADS), "smith_machine", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("smith_bent_over_row", "Smith Machine Bent-Over Row", MuscleGroup.UPPER_BACK, listOf(MuscleGroup.LATS, MuscleGroup.BICEPS), "smith_machine", MovementPattern.HORIZONTAL_PULL, compound = true, fatigue = 3))

        // ── Dumbbells (Standing / Vertical Rack) ──
        add(exercise("db_lateral_raise", "Dumbbell Lateral Raise", MuscleGroup.SIDE_DELTS, listOf(MuscleGroup.TRAPS), "dumbbells", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("db_front_raise", "Dumbbell Front Raise", MuscleGroup.FRONT_DELTS, listOf(MuscleGroup.CHEST), "dumbbells", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("db_rear_delt_fly", "Rear Delt Dumbbell Fly", MuscleGroup.REAR_DELTS, listOf(MuscleGroup.TRAPS, MuscleGroup.UPPER_BACK), "dumbbells", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("db_bicep_curl", "Dumbbell Bicep Curl", MuscleGroup.BICEPS, listOf(MuscleGroup.FOREARMS), "dumbbells", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("db_hammer_curl", "Hammer Curl", MuscleGroup.BICEPS, listOf(MuscleGroup.FOREARMS), "dumbbells", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("db_overhead_tricep_ext", "Overhead Dumbbell Tricep Extension", MuscleGroup.TRICEPS, listOf(MuscleGroup.FRONT_DELTS), "dumbbells", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("db_shoulder_press", "Standing Dumbbell Shoulder Press", MuscleGroup.FRONT_DELTS, listOf(MuscleGroup.TRICEPS, MuscleGroup.TRAPS), "dumbbells", MovementPattern.VERTICAL_PUSH, compound = true, fatigue = 3))
        add(exercise("db_shrug", "Dumbbell Shrug", MuscleGroup.TRAPS, listOf(MuscleGroup.FOREARMS), "dumbbells", MovementPattern.ISOLATION, compound = false, fatigue = 2))

        // ── Dumbbells (2-Tier / with Bench) ──
        add(exercise("db_flat_bench", "Flat Dumbbell Bench Press", MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.TRICEPS), "dumbbells", MovementPattern.HORIZONTAL_PUSH, compound = true, fatigue = 4, alsoNeeds = listOf("flat_incline_bench")))
        add(exercise("db_incline_bench", "Incline Dumbbell Bench Press", MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.TRICEPS), "dumbbells", MovementPattern.HORIZONTAL_PUSH, compound = true, fatigue = 4, alsoNeeds = listOf("flat_incline_bench")))
        add(exercise("db_flat_fly", "Flat Dumbbell Fly", MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTS), "dumbbells", MovementPattern.HORIZONTAL_PUSH, compound = false, fatigue = 2, alsoNeeds = listOf("flat_incline_bench")))
        add(exercise("db_pullover", "Dumbbell Pullover", MuscleGroup.LATS, listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS), "dumbbells", MovementPattern.VERTICAL_PULL, compound = true, fatigue = 3, alsoNeeds = listOf("flat_incline_bench")))
        add(exercise("db_single_arm_row", "Single-Arm Dumbbell Row", MuscleGroup.UPPER_BACK, listOf(MuscleGroup.LATS, MuscleGroup.BICEPS), "dumbbells", MovementPattern.HORIZONTAL_PULL, compound = true, fatigue = 3, alsoNeeds = listOf("flat_incline_bench")))
        add(exercise("db_rdl", "Dumbbell Romanian Deadlift", MuscleGroup.HAMSTRINGS, listOf(MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK), "dumbbells", MovementPattern.HINGE, compound = true, fatigue = 4))
        add(exercise("db_goblet_squat", "Goblet Squat", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES, MuscleGroup.ABS), "dumbbells", MovementPattern.SQUAT, compound = true, fatigue = 3))
        add(exercise("db_lunge", "Dumbbell Lunge", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS), "dumbbells", MovementPattern.LUNGE, compound = true, fatigue = 4))
        add(exercise("db_bulgarian_split_squat", "Bulgarian Split Squat (Dumbbell)", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES), "dumbbells", MovementPattern.LUNGE, compound = true, fatigue = 4, alsoNeeds = listOf("flat_incline_bench")))

        // ── Barbells & Rack ──
        add(exercise("barbell_curl", "Standing Barbell Curl", MuscleGroup.BICEPS, listOf(MuscleGroup.FOREARMS), "barbell", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("barbell_skullcrusher", "Barbell Skullcrusher", MuscleGroup.TRICEPS, listOf(MuscleGroup.FRONT_DELTS), "barbell", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("barbell_upright_row", "Barbell Upright Row", MuscleGroup.SIDE_DELTS, listOf(MuscleGroup.TRAPS, MuscleGroup.BICEPS), "barbell", MovementPattern.VERTICAL_PULL, compound = true, fatigue = 3))
        add(exercise("barbell_shrug", "Barbell Shrug", MuscleGroup.TRAPS, listOf(MuscleGroup.FOREARMS), "barbell", MovementPattern.ISOLATION, compound = false, fatigue = 2))
        add(exercise("landmine_press", "Landmine Press", MuscleGroup.FRONT_DELTS, listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS), "barbell", MovementPattern.VERTICAL_PUSH, compound = true, fatigue = 3))
        add(exercise("landmine_row", "Landmine Row", MuscleGroup.UPPER_BACK, listOf(MuscleGroup.LATS, MuscleGroup.BICEPS), "barbell", MovementPattern.HORIZONTAL_PULL, compound = true, fatigue = 3))

        // ── Decline Bench ──
        add(exercise("decline_bench_press", "Decline Barbell Bench Press", MuscleGroup.CHEST, listOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTS), "decline_bench", MovementPattern.HORIZONTAL_PUSH, compound = true, fatigue = 4, alsoNeeds = listOf("barbell")))
        add(exercise("decline_sit_up", "Decline Sit-Up", MuscleGroup.ABS, listOf(MuscleGroup.QUADS), "decline_bench", MovementPattern.CORE, compound = false, fatigue = 2))

        // ── Flat/Incline Bench ──
        add(exercise("bench_tricep_dip", "Bench Tricep Dip", MuscleGroup.TRICEPS, listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTS), "flat_incline_bench", MovementPattern.HORIZONTAL_PUSH, compound = true, fatigue = 3))
        add(exercise("bench_step_up", "Bench Step-Up", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES), "flat_incline_bench", MovementPattern.LUNGE, compound = true, fatigue = 3))

        // ── Back Extension Bench ──
        add(exercise("hyperextension", "45-Degree Hyperextension", MuscleGroup.LOWER_BACK, listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS), "back_extension_bench", MovementPattern.HINGE, compound = true, fatigue = 3))
        add(exercise("glute_focused_extension", "Glute-Focused Back Extension", MuscleGroup.GLUTES, listOf(MuscleGroup.HAMSTRINGS), "back_extension_bench", MovementPattern.HINGE, compound = true, fatigue = 3))

        // ── Power Sled ──
        add(exercise("sled_push", "Sled Push", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES, MuscleGroup.CALVES), "power_sled", MovementPattern.CARRY, compound = true, fatigue = 4))

        // ── Bodyweight (no equipment needed) ──
        add(exercise("push_up", "Push-Up", MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.TRICEPS), equipment = null, MovementPattern.HORIZONTAL_PUSH, compound = true, fatigue = 2))
        add(exercise("plank", "Plank", MuscleGroup.ABS, listOf(MuscleGroup.OBLIQUES), equipment = null, MovementPattern.CORE, compound = false, fatigue = 1))
        add(exercise("bodyweight_squat", "Bodyweight Squat", MuscleGroup.QUADS, listOf(MuscleGroup.GLUTES), equipment = null, MovementPattern.SQUAT, compound = true, fatigue = 2))
    }

    private fun exercise(
        id: String,
        name: String,
        primary: MuscleGroup,
        secondary: List<MuscleGroup>,
        equipment: String?,
        pattern: MovementPattern,
        compound: Boolean,
        fatigue: Int,
        /**
         * Everything else the lift needs. A barbell squat is not performable with a rack
         * alone, and listing only the rack meant the app resolved a BODYWEIGHT fixture as
         * the load source — no plate stack, and progression stepping in 1.25 kg instead of
         * the barbell's 2.5.
         */
        alsoNeeds: List<String> = emptyList(),
    ) = ExerciseEntity(
        id = id,
        name = name,
        pattern = pattern,
        primaryMuscle = primary,
        secondaryMuscles = secondary,
        requiredEquipmentIds = listOfNotNull(equipment) + alsoNeeds,
        complexity = Complexity.INTERMEDIATE,
        fatigueCost = fatigue,
        isCompound = compound,
        defaultProgression = ProgressionRule.DOUBLE_PROGRESSION,
    )
}
