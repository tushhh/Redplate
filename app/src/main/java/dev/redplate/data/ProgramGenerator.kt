package dev.redplate.data

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a mesocycle from the profile and the equipment actually on hand.
 *
 * Deterministic and local, per COACHING.md §3 — same inputs, same plan, every time.
 * Nothing here calls out, and every choice it makes can be read back in plain language
 * by [explainSlot], because a prescription the user cannot interrogate is a black box.
 *
 * The shape is a constrained pick, not a search: each day is a fixed list of movement
 * intentions ([SlotSpec]), and each intention is filled by the best exercise the user's
 * equipment can satisfy. An intention with nothing to fill it is dropped rather than
 * substituted with something that trains a different muscle.
 */
@Singleton
class ProgramGenerator @Inject constructor(
    private val db: RedplateDatabase,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val programDao: ProgramDao,
    private val volumeDao: VolumeDao,
) {

    /**
     * Replaces any active mesocycle with a freshly generated one and returns its id.
     * Runs in a single transaction: a half-written program is worse than none.
     */
    suspend fun generate(profile: ProfileEntity, now: Long = System.currentTimeMillis()): Long {
        val available = equipmentDao.getAll().filter { it.isAvailable }.map { it.id }.toSet()
        val pool = exerciseDao.getAll()
            .filter { !it.isExcluded }
            .filter { it.pattern !in profile.excludedPatterns }
            .filter { ex -> ex.requiredEquipmentIds.all { it in available } }
            .sortedBy { it.id } // deterministic tie-break before any preference ordering

        val split = Split.forDays(profile.daysPerWeek)
        val plan = buildPlan(split, pool, profile)

        return db.withTransaction {
            programDao.getActiveMesocycle()?.let {
                programDao.updateMesocycle(it.copy(isActive = false, completedAt = now))
            }

            val mesocycleId = programDao.insertMesocycle(
                MesocycleEntity(
                    name = split.displayName,
                    goal = profile.goal,
                    startedAt = now,
                    lengthWeeks = BLOCK_WEEKS,
                    currentWeek = 1,
                    isActive = true,
                )
            )

            plan.forEachIndexed { index, day ->
                val templateId = programDao.insertTemplate(
                    SessionTemplateEntity(
                        mesocycleId = mesocycleId,
                        label = day.label,
                        dayIndex = split.weekdayIndices[index],
                    )
                )
                programDao.insertSlots(
                    day.slots.mapIndexed { order, filled ->
                        filled.toSlot(templateId, order, profile.goal)
                    }
                )
            }

            // Landmarks are what the volume readouts compare against; seed any that are
            // missing but never overwrite a row the user has tuned themselves.
            val existing = volumeDao.getAllLandmarks().associateBy { it.muscle }
            volumeDao.upsertLandmarks(
                VolumeLandmarks.DEFAULTS.filter { existing[it.muscle]?.userAdjusted != true }
            )

            mesocycleId
        }
    }

    /**
     * Builds a one-off session around the muscles the user tapped on the body map, and
     * returns its template id (design 3d).
     *
     * Compounds first while they're fresh, isolation after, trimmed to the time they have.
     * The template is stored so set logging can read a real prescription and walk the
     * running order — the same machinery a programmed day uses. It is parked on an
     * inactive "Freestyle" mesocycle and given a negative day index, so it never appears
     * as a day on the Plan tab.
     */
    suspend fun generateAdHocTemplate(
        muscles: Set<MuscleGroup>,
        profile: ProfileEntity,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val available = equipmentDao.getAll().filter { it.isAvailable }.map { it.id }.toSet()
        val pool = exerciseDao.getAll()
            .filter { !it.isExcluded }
            .filter { it.pattern !in profile.excludedPatterns }
            .filter { ex -> ex.requiredEquipmentIds.all { it in available } }
            .sortedBy { it.id }

        // Compounds first, then isolation, cycling the picked muscles so no single one
        // takes the whole session.
        val chosen = mutableListOf<Pair<ExerciseEntity, Boolean>>()
        val used = mutableSetOf<String>()
        for (compound in listOf(true, false)) {
            for (muscle in muscles) {
                pool.firstOrNull {
                    it.primaryMuscle == muscle && it.isCompound == compound && it.id !in used
                }?.let {
                    used += it.id
                    chosen += it to compound
                }
            }
        }
        if (chosen.isEmpty()) error("No exercises available for the selected muscles")

        return db.withTransaction {
            val mesocycleId = programDao.getActiveMesocycle()?.id
                ?: programDao.insertMesocycle(
                    MesocycleEntity(
                        name = FREESTYLE_BLOCK,
                        goal = profile.goal,
                        startedAt = now,
                        lengthWeeks = 1,
                        isActive = false,
                    ),
                )

            val templateId = programDao.insertTemplate(
                SessionTemplateEntity(
                    mesocycleId = mesocycleId,
                    label = muscles.joinToString(" + ") { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    dayIndex = AD_HOC_DAY_INDEX,
                ),
            )

            var budget = profile.sessionCeilingMinutes
            val slots = mutableListOf<TemplateSlotEntity>()
            for ((exercise, compound) in chosen) {
                val rx = Prescription.of(profile.goal, compound)
                val sets = if (compound) 3 else 2
                val cost = sets * MINUTES_PER_SET
                if (budget - cost < 0 && slots.isNotEmpty()) break
                budget -= cost

                slots += TemplateSlotEntity(
                    templateId = templateId,
                    exerciseId = exercise.id,
                    orderIndex = slots.size,
                    targetSets = sets,
                    repRangeLow = rx.repLow,
                    repRangeHigh = rx.repHigh,
                    targetRir = rx.targetRir,
                    restSeconds = rx.restSeconds,
                    progression = ProgressionRule.DOUBLE_PROGRESSION,
                )
            }
            programDao.insertSlots(slots)
            templateId
        }
    }

    // ── Plan assembly ───────────────────────────────────────────────────

    private fun buildPlan(
        split: Split,
        pool: List<ExerciseEntity>,
        profile: ProfileEntity,
    ): List<PlannedDay> {
        // Exercises already used this week, so the plan varies across days instead of
        // prescribing the same bench press four times.
        val usedThisWeek = mutableSetOf<String>()

        return split.days.map { day ->
            val usedToday = mutableSetOf<String>()
            val filled = day.slots.mapNotNull { spec ->
                val exercise = pick(spec, pool, usedToday, usedThisWeek) ?: return@mapNotNull null
                usedToday += exercise.id
                usedThisWeek += exercise.id
                FilledSlot(spec, exercise, setsFor(spec, profile))
            }
            PlannedDay(day.label, trimToTimeBudget(filled, profile.sessionCeilingMinutes))
        }
    }

    /**
     * Fills one movement intention, relaxing the criteria in order of how much each
     * relaxation costs the plan: variety first, then the exact pattern, then the
     * compound/isolation split. Muscle is never relaxed — a slot that exists to train
     * hamstrings is dropped rather than filled with something else.
     */
    private fun pick(
        spec: SlotSpec,
        pool: List<ExerciseEntity>,
        usedToday: Set<String>,
        usedThisWeek: Set<String>,
    ): ExerciseEntity? {
        val forMuscle = pool.filter { it.primaryMuscle == spec.muscle && it.id !in usedToday }
        if (forMuscle.isEmpty()) return null

        val exactMatch = { e: ExerciseEntity ->
            e.isCompound == spec.compound && (spec.pattern == null || e.pattern == spec.pattern)
        }
        val patternRelaxed = { e: ExerciseEntity -> e.isCompound == spec.compound }

        return forMuscle.firstOrNull { it.id !in usedThisWeek && exactMatch(it) }
            ?: forMuscle.firstOrNull(exactMatch)
            ?: forMuscle.firstOrNull { it.id !in usedThisWeek && patternRelaxed(it) }
            ?: forMuscle.firstOrNull(patternRelaxed)
            ?: forMuscle.firstOrNull { it.id !in usedThisWeek }
            ?: forMuscle.first()
    }

    /**
     * Week 1 starts at MEV and adds sets weekly, so the opening prescription is
     * deliberately conservative. Beginners get one fewer set on compounds — the
     * limit early on is recovery and technique, not stimulus.
     */
    private fun setsFor(spec: SlotSpec, profile: ProfileEntity): Int {
        val base = when {
            spec.compound && profile.goal == Goal.STRENGTH -> 4
            spec.compound -> 3
            else -> 3
        }
        val noviceAdjustment = if (profile.trainingAgeMonths < NOVICE_MONTHS && spec.compound) 1 else 0
        val priorityAdjustment = if (spec.muscle in profile.priorityMuscles) 1 else 0
        return (base - noviceAdjustment + priorityAdjustment).coerceIn(2, 5)
    }

    /**
     * Drops trailing accessories until the session fits the declared ceiling. Isolation
     * work goes first because the compounds are the reason the day exists. Never trims
     * below [MIN_EXERCISES] — a two-move session is not the plan the user agreed to.
     */
    private fun trimToTimeBudget(slots: List<FilledSlot>, ceilingMinutes: Int): List<FilledSlot> {
        val kept = slots.toMutableList()
        while (kept.size > MIN_EXERCISES && estimateMinutes(kept) > ceilingMinutes) {
            val lastIsolation = kept.indexOfLast { !it.spec.compound }
            kept.removeAt(if (lastIsolation >= 0) lastIsolation else kept.lastIndex)
        }
        return kept
    }

    private fun estimateMinutes(slots: List<FilledSlot>): Int =
        slots.sumOf { it.sets } * MINUTES_PER_SET

    // ── Prescription per goal (COACHING.md §3) ──────────────────────────

    private fun FilledSlot.toSlot(
        templateId: Long,
        orderIndex: Int,
        goal: Goal,
    ): TemplateSlotEntity {
        val rx = Prescription.of(goal, spec.compound)
        return TemplateSlotEntity(
            templateId = templateId,
            exerciseId = exercise.id,
            orderIndex = orderIndex,
            targetSets = sets,
            repRangeLow = rx.repLow,
            repRangeHigh = rx.repHigh,
            targetRir = rx.targetRir,
            restSeconds = rx.restSeconds,
            progression = if (goal == Goal.STRENGTH && spec.compound) {
                ProgressionRule.LOAD_PROGRESSION
            } else {
                ProgressionRule.DOUBLE_PROGRESSION
            },
            workingLoadKg = null, // set from the first session's actual performance
        )
    }

    private data class Prescription(
        val repLow: Int,
        val repHigh: Int,
        val targetRir: Int,
        val restSeconds: Int,
    ) {
        companion object {
            fun of(goal: Goal, compound: Boolean): Prescription = when (goal) {
                Goal.STRENGTH -> if (compound) {
                    Prescription(3, 6, 2, 240)
                } else {
                    Prescription(6, 10, 2, 105)
                }

                Goal.HYPERTROPHY -> if (compound) {
                    Prescription(6, 10, 2, 150)
                } else {
                    Prescription(10, 15, 1, 90)
                }

                Goal.GENERAL -> if (compound) {
                    Prescription(5, 10, 2, 150)
                } else {
                    Prescription(10, 15, 2, 75)
                }
            }
        }
    }

    private data class FilledSlot(
        val spec: SlotSpec,
        val exercise: ExerciseEntity,
        val sets: Int,
    )

    private data class PlannedDay(val label: String, val slots: List<FilledSlot>)

    companion object {
        /** 4 accumulation weeks plus a deload. */
        const val BLOCK_WEEKS = 5

        private const val MINUTES_PER_SET = 3
        private const val MIN_EXERCISES = 3
        private const val NOVICE_MONTHS = 12

        /** Parks ad-hoc templates outside the Mon–Sun grid so the Plan tab ignores them. */
        private const val AD_HOC_DAY_INDEX = -1
        private const val FREESTYLE_BLOCK = "Freestyle"

        /**
         * One line explaining why a slot is prescribed the way it is. The engine must
         * always be able to answer "why am I doing this?" without a network call.
         */
        fun explainSlot(slot: TemplateSlotEntity, exerciseName: String, goal: Goal): String {
            val rest = if (slot.restSeconds >= 60) {
                "${slot.restSeconds / 60} min"
            } else {
                "${slot.restSeconds} s"
            }
            val why = when (goal) {
                Goal.STRENGTH -> "heavy, low reps and long rests are what move a max"
                Goal.HYPERTROPHY -> "moderate reps close to failure is what drives growth"
                Goal.GENERAL -> "a middle rep range keeps strength and size both moving"
            }
            return "$exerciseName: ${slot.targetSets} × ${slot.repRangeLow}–${slot.repRangeHigh} " +
                "at ${slot.targetRir} in reserve, $rest rest — $why."
        }
    }
}

// ── Split definitions ───────────────────────────────────────────────────

internal data class SlotSpec(
    val muscle: MuscleGroup,
    val pattern: MovementPattern?,
    val compound: Boolean,
)

internal data class DaySpec(val label: String, val slots: List<SlotSpec>)

/**
 * Split structure by days available, per COACHING.md §3. Every muscle is trained at
 * least twice a week at 4+ days; at 2–3 days the full-body rotation does the same job.
 *
 * [weekdayIndices] spreads sessions across the week (0 = Monday) rather than stacking
 * them, so consecutive hard days are the exception, not the default.
 */
internal enum class Split(
    val displayName: String,
    val weekdayIndices: List<Int>,
    val days: List<DaySpec>,
) {
    FULL_BODY_2("Full Body", listOf(0, 3), listOf(Days.fullBodyA, Days.fullBodyB)),

    FULL_BODY_3(
        "Full Body",
        listOf(0, 2, 4),
        listOf(Days.fullBodyA, Days.fullBodyB, Days.fullBodyC),
    ),

    UPPER_LOWER_4(
        "Upper / Lower",
        listOf(0, 1, 3, 4),
        listOf(Days.upperA, Days.lowerA, Days.upperB, Days.lowerB),
    ),

    HYBRID_5(
        "Upper / Lower / PPL",
        listOf(0, 1, 3, 4, 5),
        listOf(Days.upperA, Days.lowerA, Days.push, Days.pull, Days.legs),
    ),

    PPL_6(
        "Push / Pull / Legs",
        listOf(0, 1, 2, 3, 4, 5),
        listOf(Days.push, Days.pull, Days.legs, Days.pushB, Days.pullB, Days.legsB),
    );

    companion object {
        fun forDays(daysPerWeek: Int): Split = when (daysPerWeek.coerceIn(2, 6)) {
            2 -> FULL_BODY_2
            3 -> FULL_BODY_3
            4 -> UPPER_LOWER_4
            5 -> HYBRID_5
            else -> PPL_6
        }
    }
}

private object Days {

    private fun compound(muscle: MuscleGroup, pattern: MovementPattern? = null) =
        SlotSpec(muscle, pattern, compound = true)

    private fun isolation(muscle: MuscleGroup, pattern: MovementPattern? = null) =
        SlotSpec(muscle, pattern, compound = false)

    val upperA = DaySpec(
        "Upper A",
        listOf(
            compound(MuscleGroup.CHEST, MovementPattern.HORIZONTAL_PUSH),
            compound(MuscleGroup.LATS, MovementPattern.VERTICAL_PULL),
            compound(MuscleGroup.FRONT_DELTS, MovementPattern.VERTICAL_PUSH),
            compound(MuscleGroup.UPPER_BACK, MovementPattern.HORIZONTAL_PULL),
            isolation(MuscleGroup.SIDE_DELTS),
            isolation(MuscleGroup.BICEPS),
            isolation(MuscleGroup.TRICEPS),
        ),
    )

    val upperB = DaySpec(
        "Upper B",
        listOf(
            compound(MuscleGroup.UPPER_BACK, MovementPattern.HORIZONTAL_PULL),
            compound(MuscleGroup.CHEST, MovementPattern.HORIZONTAL_PUSH),
            compound(MuscleGroup.LATS, MovementPattern.VERTICAL_PULL),
            isolation(MuscleGroup.REAR_DELTS),
            isolation(MuscleGroup.CHEST),
            isolation(MuscleGroup.TRICEPS),
            isolation(MuscleGroup.BICEPS),
        ),
    )

    val lowerA = DaySpec(
        "Lower A",
        listOf(
            compound(MuscleGroup.QUADS, MovementPattern.SQUAT),
            compound(MuscleGroup.HAMSTRINGS, MovementPattern.HINGE),
            compound(MuscleGroup.GLUTES),
            isolation(MuscleGroup.CALVES),
            isolation(MuscleGroup.ABS, MovementPattern.CORE),
        ),
    )

    val lowerB = DaySpec(
        "Lower B",
        listOf(
            compound(MuscleGroup.HAMSTRINGS, MovementPattern.HINGE),
            compound(MuscleGroup.QUADS, MovementPattern.LUNGE),
            compound(MuscleGroup.GLUTES),
            isolation(MuscleGroup.CALVES),
            isolation(MuscleGroup.ABS, MovementPattern.CORE),
        ),
    )

    val push = DaySpec(
        "Push",
        listOf(
            compound(MuscleGroup.CHEST, MovementPattern.HORIZONTAL_PUSH),
            compound(MuscleGroup.FRONT_DELTS, MovementPattern.VERTICAL_PUSH),
            isolation(MuscleGroup.CHEST),
            isolation(MuscleGroup.SIDE_DELTS),
            isolation(MuscleGroup.TRICEPS),
        ),
    )

    val pushB = DaySpec(
        "Push B",
        listOf(
            compound(MuscleGroup.FRONT_DELTS, MovementPattern.VERTICAL_PUSH),
            compound(MuscleGroup.CHEST, MovementPattern.HORIZONTAL_PUSH),
            isolation(MuscleGroup.SIDE_DELTS),
            isolation(MuscleGroup.CHEST),
            isolation(MuscleGroup.TRICEPS),
        ),
    )

    val pull = DaySpec(
        "Pull",
        listOf(
            compound(MuscleGroup.LATS, MovementPattern.VERTICAL_PULL),
            compound(MuscleGroup.UPPER_BACK, MovementPattern.HORIZONTAL_PULL),
            isolation(MuscleGroup.REAR_DELTS),
            isolation(MuscleGroup.TRAPS),
            isolation(MuscleGroup.BICEPS),
        ),
    )

    val pullB = DaySpec(
        "Pull B",
        listOf(
            compound(MuscleGroup.UPPER_BACK, MovementPattern.HORIZONTAL_PULL),
            compound(MuscleGroup.LATS, MovementPattern.VERTICAL_PULL),
            isolation(MuscleGroup.LATS),
            isolation(MuscleGroup.REAR_DELTS),
            isolation(MuscleGroup.BICEPS),
        ),
    )

    val legs = DaySpec(
        "Legs",
        listOf(
            compound(MuscleGroup.QUADS, MovementPattern.SQUAT),
            compound(MuscleGroup.HAMSTRINGS, MovementPattern.HINGE),
            compound(MuscleGroup.GLUTES),
            isolation(MuscleGroup.CALVES),
            isolation(MuscleGroup.ABS, MovementPattern.CORE),
        ),
    )

    val legsB = DaySpec(
        "Legs B",
        listOf(
            compound(MuscleGroup.HAMSTRINGS, MovementPattern.HINGE),
            compound(MuscleGroup.QUADS, MovementPattern.LUNGE),
            compound(MuscleGroup.GLUTES),
            isolation(MuscleGroup.ADDUCTORS),
            isolation(MuscleGroup.CALVES),
        ),
    )

    val fullBodyA = DaySpec(
        "Full Body A",
        listOf(
            compound(MuscleGroup.QUADS, MovementPattern.SQUAT),
            compound(MuscleGroup.CHEST, MovementPattern.HORIZONTAL_PUSH),
            compound(MuscleGroup.UPPER_BACK, MovementPattern.HORIZONTAL_PULL),
            isolation(MuscleGroup.SIDE_DELTS),
            isolation(MuscleGroup.ABS, MovementPattern.CORE),
        ),
    )

    val fullBodyB = DaySpec(
        "Full Body B",
        listOf(
            compound(MuscleGroup.HAMSTRINGS, MovementPattern.HINGE),
            compound(MuscleGroup.FRONT_DELTS, MovementPattern.VERTICAL_PUSH),
            compound(MuscleGroup.LATS, MovementPattern.VERTICAL_PULL),
            isolation(MuscleGroup.BICEPS),
            isolation(MuscleGroup.TRICEPS),
        ),
    )

    val fullBodyC = DaySpec(
        "Full Body C",
        listOf(
            compound(MuscleGroup.GLUTES),
            compound(MuscleGroup.CHEST, MovementPattern.HORIZONTAL_PUSH),
            compound(MuscleGroup.LATS, MovementPattern.VERTICAL_PULL),
            isolation(MuscleGroup.CALVES),
            isolation(MuscleGroup.ABS, MovementPattern.CORE),
        ),
    )
}
