package dev.redplate.data

/**
 * Equipment inventory for the user's home gym, transcribed from the facility floor plan.
 * See GYM.md for the full breakdown and every assumption flagged below.
 *
 * Items marked ASSUMPTION have a plausible commercial-gym default but are not confirmed
 * against the actual hardware. Items marked isAvailable = false are deliberately withheld
 * from the exercise filter until their contents are confirmed — fail closed, never guess
 * open, per COACHING.md §2.
 *
 * [EquipmentEntity.selectionPriority] controls which exercises the program generator
 * schedules first. Lower = higher priority. Scale:
 *   10 — dedicated single-purpose machines (pec fly, chest press, shoulder press, etc.)
 *   20 — barbell / compound platforms (barbell, smith machine)
 *   30 — dumbbells
 *   40 — cable / multi-station / assisted
 *   50 — bodyweight fixtures (bench, back extension)
 *   60 — functional / ROX zone
 *   70 — cardio machines (filtered from strength slots; finisher use only)
 */
object GymEquipmentSeed {

    /** Shared plate pool assumption for barbell + plate-loaded machines. Low-stakes ASSUMPTION —
     *  mainly affects the ceiling of loadable weight, not correctness of small increments. */
    private val commercialPlatePool: Map<Double, Int> = mapOf(
        25.0 to 4, 20.0 to 4, 15.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2
    )

    /**
     * The low row and lat pulldown selector stacks, read off the plates themselves.
     *
     * Not an assumption and not a generated ladder: these are the fifteen numbers printed
     * on the machine, in order, so the app's readout matches the label the user is looking
     * at when they set the pin. Note the spacing is not uniform — 7.5 kg per pin up to
     * 50 kg, then 10 kg — which is exactly why it has to be transcribed rather than
     * generated. [EquipmentEntity.minIncrement] takes the smallest real gap, so nothing
     * downstream ever prescribes a jump the pin cannot make.
     */
    val MULTIGYM_STACK_KG: List<Double> = listOf(
        5.0, 12.5, 20.0, 27.5, 35.0, 42.5, 50.0,
        60.0, 70.0, 80.0, 90.0, 100.0, 110.0, 120.0, 130.0,
    )

    fun seed(): List<EquipmentEntity> = listOf(

        // --- Cardio machines (#1,2,3,4,28,29) — priority 70 ---
        // Filtered out of strength slots by ProgramGenerator; only surface as finisher options.
        cardio("stairmill", "Stairmill"),
        cardio("treadmill", "Treadmill"),
        cardio("crosstrainer", "Crosstrainer"),
        cardio("concept2_rower", "Concept2 Rower"),
        cardio("airbike", "Airbike"),
        cardio("skierg", "SkiErg"),

        // --- Dedicated single-purpose machines (#6,7,8,9,10) — priority 10 ---
        // These machines were skipped when cables sorted first alphabetically.
        // Priority 10 means they win every slot competition before cables (40) or
        // dumbbells (30) get a look in. Cables and dumbbells remain as swap options.
        pinStack("hip_adductor_abductor", "Hip Adductor/Abductor", priority = 10),
        pinStack("pec_fly_rear_delt", "Pec Fly / Rear Delt", priority = 10),
        pinStack("chest_press_machine", "Chest Press Machine", priority = 10),
        pinStack("shoulder_press_machine", "Shoulder Press Machine", priority = 10),
        pinStack("leg_curl_machine", "Leg Curl Machine", priority = 10),

        // --- 4-Station Multi-Gym (#11) — priority 40 (cable / multi-station) ---
        // Modelled as four pieces because they are four things you queue for: naming the
        // frame told you nothing about which station to walk to.
        //
        // The low row and lat pulldown stacks are printed in kilograms — confirmed from a
        // photo of the selector plates, transcribed into [MULTIGYM_STACK_KG]. The
        // remaining two are still numbered levels with no mass printed anywhere, so they
        // record the number on the machine rather than inventing one.
        pinStack("multigym_low_row", "Multi-Gym · Low Row", MULTIGYM_STACK_KG, priority = 40),
        pinStack("multigym_lat_pulldown", "Multi-Gym · Lat Pulldown", MULTIGYM_STACK_KG, priority = 40),
        resistanceLevel("multigym_cable", "Multi-Gym · Cable", priority = 40),
        // Counterweighted: a higher number takes MORE of your bodyweight, so it is easier.
        // Everything that reads this has to run backwards — see EquipmentEntity.isAssistance.
        resistanceLevel("multigym_assist_dip_chin", "Multi-Gym · Assisted Dip/Chin", assistance = true, priority = 40),

        // --- Dual Adjustable Pulley (#5) — priority 40 ---
        // Cable machine — an excellent swap option and irreplaceable for many isolation
        // patterns, but not the first choice when a dedicated machine exists for that slot.
        // ASSUMPTION: 2.5kg stack increments, 5-100kg range.
        pinStack("dual_adjustable_pulley", "Dual Adjustable Pulley", priority = 40),

        // --- Fixtures (#12,13,22,23,24) — priority 50 ---
        // No load of their own; gate specific barbell/dumbbell variants.
        // Priority 50 keeps fixture-dependent exercises behind cables in the pool, so
        // they are not preferentially scheduled either.
        fixture("deadlift_platform", "Deadlift Platform", EquipmentCategory.OTHER, priority = 50),
        fixture("power_rack", "Half Rack", EquipmentCategory.BARBELL, priority = 50),
        fixture("decline_bench", "Decline Bench", EquipmentCategory.OTHER, priority = 50),
        fixture("flat_incline_bench", "Flat/Incline Bench", EquipmentCategory.OTHER, priority = 50),
        fixture("back_extension_bench", "Back Extension Bench", EquipmentCategory.OTHER, priority = 50),

        // --- Plate-loaded dedicated machines (#15,16,17) — priority 10 ---
        // ASSUMPTION: carriage/starting weight. Printed on the machine or in its manual.
        EquipmentEntity(
            id = "incline_chest_press_machine", displayName = "Incline Chest Press Machine",
            category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 10.0 /* ASSUMPTION: carriage weight */, platePairs = commercialPlatePool,
            selectionPriority = 10,
        ),
        EquipmentEntity(
            id = "leg_press_machine", displayName = "Leg Press",
            category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 20.0 /* ASSUMPTION: sled weight */, platePairs = commercialPlatePool,
            selectionPriority = 10,
        ),
        EquipmentEntity(
            id = "glute_drive_machine", displayName = "Glute Drive",
            category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 10.0 /* ASSUMPTION: carriage weight */, platePairs = commercialPlatePool,
            selectionPriority = 10,
        ),

        // --- Smith Machine (#18) — priority 20 (plate-loaded compound) ---
        // HIGH STAKES ASSUMPTION on bar weight — confirm this one first.
        EquipmentEntity(
            id = "smith_machine", displayName = "Smith Machine",
            category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 10.0 /* ASSUMPTION: counterbalanced bars commonly read 0-20kg
                                   effective. Unload it and check the console before trusting
                                   any Smith Machine progression numbers. */,
            platePairs = commercialPlatePool,
            selectionPriority = 20,
        ),

        // --- Dumbbells (#19+20, treated as one continuous rack) — priority 30 ---
        EquipmentEntity(
            id = "dumbbells", displayName = "Dumbbells",
            category = EquipmentCategory.DUMBBELL, loadingScheme = LoadingScheme.FIXED_INCREMENT,
            // The rack is labelled per dumbbell, so that is what gets logged: "30" is a
            // 30 kg dumbbell in each hand, and the readout says EACH so it cannot be read
            // as a combined figure.
            perLimb = true,
            availableLoads = generateSequence(10.0) { it + 2.0 }.takeWhile { it <= 40.0 }.toList(),
            selectionPriority = 30,
        ),

        // --- Barbell (#14 bumper plates + #21 barbells & rack) — priority 20 ---
        EquipmentEntity(
            id = "barbell", displayName = "Barbell",
            category = EquipmentCategory.BARBELL, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 20.0, platePairs = commercialPlatePool,
            selectionPriority = 20,
        ),

        // --- Rox Zone (#25,26,27) — priority 60 (functional) ---
        EquipmentEntity(
            id = "power_sled", displayName = "Power Sled",
            category = EquipmentCategory.OTHER, loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 15.0 /* ASSUMPTION: unloaded sled weight */, platePairs = commercialPlatePool,
            selectionPriority = 60,
        ),

        // FAIL CLOSED — contents of the Rox rack are not itemised on the floor plan.
        // Flip isAvailable = true and set real weights once confirmed.
        EquipmentEntity(
            id = "rox_kettlebells", displayName = "Kettlebells (Rox Zone)",
            category = EquipmentCategory.KETTLEBELL, loadingScheme = LoadingScheme.FIXED_INCREMENT,
            availableLoads = emptyList(), isAvailable = false,
            selectionPriority = 60,
        ),
        EquipmentEntity(
            id = "rox_bands", displayName = "Resistance Bands (Rox Zone)",
            category = EquipmentCategory.BAND, loadingScheme = LoadingScheme.BANDED,
            isAvailable = false,
            selectionPriority = 60,
        ),

        // FAIL CLOSED — landmine work needs a landmine sleeve or a corner to jam a bar in,
        // and the floor plan shows neither. Landmine press and row named only the barbell,
        // so the app was free to prescribe them on hardware that may not exist.
        EquipmentEntity(
            id = "landmine_attachment", displayName = "Landmine Attachment",
            category = EquipmentCategory.BARBELL, loadingScheme = LoadingScheme.BODYWEIGHT,
            isAvailable = false,
            selectionPriority = 60,
        ),

        // FAIL CLOSED — floor plan shows the target, not the ball.
        EquipmentEntity(
            id = "wall_ball", displayName = "Wall Ball",
            category = EquipmentCategory.OTHER, loadingScheme = LoadingScheme.FIXED_INCREMENT,
            availableLoads = emptyList(), isAvailable = false,
            selectionPriority = 60,
        ),
    )

    private fun cardio(id: String, name: String) = EquipmentEntity(
        id = id, displayName = name,
        category = EquipmentCategory.CARDIO_MACHINE, loadingScheme = LoadingScheme.BODYWEIGHT,
        selectionPriority = 70,
    )

    /**
     * A selectorised stack marked in kilograms. [loads] defaults to the unconfirmed
     * commercial-gym ladder; pass the real one wherever the plates have been read.
     * [priority] drives which exercises get scheduled first by [ProgramGenerator].
     */
    private fun pinStack(
        id: String,
        name: String,
        /* ASSUMPTION: 2.5kg stack increments, 5-100kg. Adjust per-machine if you check the pins. */
        loads: List<Double> = generateSequence(5.0) { it + 2.5 }.takeWhile { it <= 100.0 }.toList(),
        priority: Int = 40,
    ) = EquipmentEntity(
        id = id, displayName = name,
        category = EquipmentCategory.MACHINE, loadingScheme = LoadingScheme.PIN_STACK,
        availableLoads = loads,
        selectionPriority = priority,
    )

    /**
     * A stack the user reads as a level, not a weight. No [EquipmentEntity.availableLoads]
     * on purpose: there is no ladder to snap to, and the previous invented 5–100 kg one was
     * worse than none — it put a kilogram figure on screen that appears nowhere on the
     * machine, and refused to record the level the user actually set.
     */
    private fun resistanceLevel(
        id: String,
        name: String,
        assistance: Boolean = false,
        priority: Int = 40,
    ) = EquipmentEntity(
        id = id, displayName = name,
        category = EquipmentCategory.MACHINE,
        loadingScheme = LoadingScheme.RESISTANCE_LEVEL,
        isAssistance = assistance,
        selectionPriority = priority,
    )

    private fun fixture(
        id: String,
        name: String,
        category: EquipmentCategory,
        priority: Int = 50,
    ) = EquipmentEntity(
        id = id, displayName = name,
        category = category, loadingScheme = LoadingScheme.BODYWEIGHT,
        selectionPriority = priority,
    )

}
