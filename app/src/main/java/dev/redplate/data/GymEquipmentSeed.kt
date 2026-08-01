package dev.redplate.data

/**
 * Equipment inventory for the user's home gym, transcribed from the facility floor plan.
 * See GYM.md for the full breakdown and every assumption flagged below.
 *
 * Items marked ASSUMPTION have a plausible commercial-gym default but are not confirmed
 * against the actual hardware. Items marked isAvailable = false are deliberately withheld
 * from the exercise filter until their contents are confirmed — fail closed, never guess
 * open, per COACHING.md §2.
 */
object GymEquipmentSeed {

    /** Shared plate pool assumption for barbell + plate-loaded machines. Low-stakes ASSUMPTION —
     *  mainly affects the ceiling of loadable weight, not correctness of small increments. */
    private val commercialPlatePool: Map<Double, Int> = mapOf(
        25.0 to 4, 20.0 to 4, 15.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2
    )

    fun seed(): List<EquipmentEntity> = listOf(

        // --- Cardio machines (#1,2,3,4,28,29) — no discrete load, resistance set at console ---
        cardio("stairmill", "Stairmill"),
        cardio("treadmill", "Treadmill"),
        cardio("crosstrainer", "Crosstrainer"),
        cardio("concept2_rower", "Concept2 Rower"),
        cardio("airbike", "Airbike"),
        cardio("skierg", "SkiErg"),

        // --- Pin-loaded selectorised (#5-11) ---
        // ASSUMPTION: 2.5kg stack increments, 5-100kg range. Confirm against console if precision matters.
        pinStack("dual_adjustable_pulley", "Dual Adjustable Pulley"),
        pinStack("hip_adductor_abductor", "Hip Adductor/Abductor"),
        pinStack("pec_fly_rear_delt", "Pec Fly / Rear Delt"),
        pinStack("chest_press_machine", "Chest Press Machine"),
        pinStack("shoulder_press_machine", "Shoulder Press Machine"),
        pinStack("leg_curl_machine", "Leg Curl Machine"),
        // Marked in numbered resistance levels, not kilograms — so the app records the
        // number that is actually printed on the machine rather than inventing a mass.
        resistanceLevel("four_station_multigym", "4-Station Multi-Gym"),

        // --- Fixtures (#12,13,22,23,24) — no load of their own, gate specific variants ---
        fixture("deadlift_platform", "Deadlift Platform", EquipmentCategory.OTHER),
        fixture("power_rack", "Half Rack", EquipmentCategory.BARBELL),
        fixture("decline_bench", "Decline Bench", EquipmentCategory.OTHER),
        fixture("flat_incline_bench", "Flat/Incline Bench", EquipmentCategory.OTHER),
        fixture("back_extension_bench", "Back Extension Bench", EquipmentCategory.OTHER),

        // --- Plate-loaded machines (#15,16,17) ---
        // ASSUMPTION: carriage/starting weight. Printed on the machine or in its manual.
        EquipmentEntity(
            id = "incline_chest_press_machine", displayName = "Incline Chest Press Machine",
            category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 10.0 /* ASSUMPTION: carriage weight */, platePairs = commercialPlatePool
        ),
        EquipmentEntity(
            id = "leg_press_machine", displayName = "Leg Press",
            category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 20.0 /* ASSUMPTION: sled weight */, platePairs = commercialPlatePool
        ),
        EquipmentEntity(
            id = "glute_drive_machine", displayName = "Glute Drive",
            category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 10.0 /* ASSUMPTION: carriage weight */, platePairs = commercialPlatePool
        ),

        // --- Smith Machine (#18) — HIGH STAKES ASSUMPTION, confirm this one first ---
        EquipmentEntity(
            id = "smith_machine", displayName = "Smith Machine",
            category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 10.0 /* ASSUMPTION: counterbalanced bars commonly read 0-20kg
                                   effective. Unload it and check the console before trusting
                                   any Smith Machine progression numbers. */,
            platePairs = commercialPlatePool
        ),

        // --- Dumbbells (#19+20, treated as one continuous rack) ---
        EquipmentEntity(
            id = "dumbbells", displayName = "Dumbbells",
            category = EquipmentCategory.DUMBBELL, loadingScheme = LoadingScheme.FIXED_INCREMENT,
            // The rack is labelled per dumbbell, so that is what gets logged: "30" is a
            // 30 kg dumbbell in each hand, and the readout says EACH so it cannot be read
            // as a combined figure.
            perLimb = true,
            availableLoads = generateSequence(10.0) { it + 2.0 }.takeWhile { it <= 40.0 }.toList()
        ),

        // --- Barbell (#14 bumper plates + #21 barbells & rack) ---
        EquipmentEntity(
            id = "barbell", displayName = "Barbell",
            category = EquipmentCategory.BARBELL, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 20.0, platePairs = commercialPlatePool
        ),

        // --- Rox Zone (#25,26,27) ---
        EquipmentEntity(
            id = "power_sled", displayName = "Power Sled",
            category = EquipmentCategory.OTHER, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 15.0 /* ASSUMPTION: unloaded sled weight */, platePairs = commercialPlatePool
        ),

        // FAIL CLOSED — contents of the Rox rack are not itemised on the floor plan.
        // Flip isAvailable = true and set real weights once confirmed. See GYM.md item 4.
        EquipmentEntity(
            id = "rox_kettlebells", displayName = "Kettlebells (Rox Zone)",
            category = EquipmentCategory.KETTLEBELL, loadingScheme = LoadingScheme.FIXED_INCREMENT,
            availableLoads = emptyList(), isAvailable = false
        ),
        EquipmentEntity(
            id = "rox_bands", displayName = "Resistance Bands (Rox Zone)",
            category = EquipmentCategory.BAND, loadingScheme = LoadingScheme.BANDED,
            isAvailable = false
        ),

        // FAIL CLOSED — floor plan shows the target, not the ball. See GYM.md item 5.
        EquipmentEntity(
            id = "wall_ball", displayName = "Wall Ball",
            category = EquipmentCategory.OTHER, loadingScheme = LoadingScheme.FIXED_INCREMENT,
            availableLoads = emptyList(), isAvailable = false
        ),
    )

    private fun cardio(id: String, name: String) = EquipmentEntity(
        id = id, displayName = name,
        category = EquipmentCategory.CARDIO_MACHINE, loadingScheme = LoadingScheme.BODYWEIGHT
    )

    private fun pinStack(id: String, name: String) = EquipmentEntity(
        id = id, displayName = name,
        category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.PIN_STACK,
        /* ASSUMPTION: 2.5kg stack increments, 5-100kg. Adjust per-machine if you check the pins. */
        availableLoads = generateSequence(5.0) { it + 2.5 }.takeWhile { it <= 100.0 }.toList()
    )

    /**
     * A stack the user reads as a level, not a weight. No [EquipmentEntity.availableLoads]
     * on purpose: there is no ladder to snap to, and the previous invented 5–100 kg one was
     * worse than none — it put a kilogram figure on screen that appears nowhere on the
     * machine, and refused to record the level the user actually set.
     */
    private fun resistanceLevel(id: String, name: String) = EquipmentEntity(
        id = id, displayName = name,
        category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.RESISTANCE_LEVEL,
    )

    private fun fixture(id: String, name: String, category: EquipmentCategory) = EquipmentEntity(
        id = id, displayName = name,
        category = category, loadingScheme = LoadingScheme.BODYWEIGHT
    )

}
