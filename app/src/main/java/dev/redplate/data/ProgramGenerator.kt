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
 * intentions ([SlotSpec]), and each intention is filled by the first equipment-valid match
 * from an id-sorted pool — there is no quality ranking, and the KDoc used to claim there
 * was. An intention with nothing to fill it is dropped rather than substituted with
 * something that trains a different muscle.
 */
@Singleton
class ProgramGenerator @Inject constructor(
    private val db: RedplateDatabase,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val programDao: ProgramDao,
    private val sessionDao: SessionDao,
    private val volumeDao: VolumeDao,
) {

    /**
     * Replaces any active mesocycle with a freshly generated one and returns its id.
     * Runs in a single transaction: a half-written program is worse than none.
     */
    suspend fun generate(profile: ProfileEntity, now: Long = System.currentTimeMillis()): Long {
        val pool = performablePool(profile, EquipmentAvailability.availableIds(equipmentDao.getAll()))

        val split = Split.forDays(profile.daysPerWeek)
        val plan = buildPlan(split, pool, profile)
        // The user's chosen weekdays win over the split's own layout when they have set
        // any; the split default is only a starting suggestion.
        val weekdays = profile.planSettings().weekdayIndices()

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
                        dayIndex = weekdays.getOrElse(index) { split.weekdayIndices[index] },
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
        val available = EquipmentAvailability.availableIds(equipmentDao.getAll())
        // Same pool the programmed days use, so a body-map session cannot prescribe
        // something the generator would refuse to.
        val pool = performablePool(profile, available)

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
            // Find-or-create the parking block. This used to attach to the *active*
            // mesocycle whenever one existed, so a one-off body-map session became part of
            // the current program and the rest-day card announced it as the next session.
            val mesocycleId = freestyleMesocycleId(profile, now)

            val templateId = programDao.insertTemplate(
                SessionTemplateEntity(
                    mesocycleId = mesocycleId,
                    label = muscles.joinToString(" + ") { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    dayIndex = AD_HOC_DAY_INDEX,
                ),
            )

            val slots = mutableListOf<TemplateSlotEntity>()
            val planned = mutableListOf<Pair<Int, Int>>()
            for ((exercise, compound) in chosen) {
                val rx = Prescription.of(profile.goal, compound)
                val sets = if (compound) 3 else 2

                // Rest is most of a session's length, so the budget has to count it.
                val withThis = planned + (sets to rx.restSeconds)
                if (slots.isNotEmpty() &&
                    SessionEstimate.minutesOf(withThis) > profile.sessionCeilingMinutes
                ) {
                    break
                }
                planned += sets to rx.restSeconds

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

    /**
     * The inactive block ad-hoc templates are parked on, created once and reused.
     *
     * It must never be the active mesocycle: templates on the active block are the
     * program, and a one-off session is not part of anyone's program. It stays inactive so
     * `getActiveMesocycle()` can never return it.
     */
    private suspend fun freestyleMesocycleId(profile: ProfileEntity, now: Long): Long =
        programDao.getAllMesocycles()
            .firstOrNull { it.name == FREESTYLE_BLOCK && !it.isActive }
            ?.id
            ?: programDao.insertMesocycle(
                MesocycleEntity(
                    name = FREESTYLE_BLOCK,
                    goal = profile.goal,
                    startedAt = now,
                    lengthWeeks = 1,
                    isActive = false,
                ),
            )

    // ── Revising the plan without rebuilding it (2.1b) ──────────────────

    /**
     * Re-fits an existing block to the profile's session ceiling.
     *
     * Changing only how long a session may run affects nothing but [trimToTimeBudget], so
     * regenerating the whole block for it would throw away a plan the user may have edited
     * for no reason. This works on the templates in place: over budget, trailing accessories
     * come off, isolation first; under budget, the intentions the generator would have
     * programmed for that day go back on. Slots that survive keep their ids and their
     * working loads.
     *
     * Returns how many templates changed.
     */
    suspend fun refitToCeiling(profile: ProfileEntity, mesocycleId: Long): Int {
        val available = EquipmentAvailability.availableIds(equipmentDao.getAll())
        val pool = performablePool(profile, available)
        val exercises = exerciseDao.getAll().associateBy { it.id }
        val split = Split.forDays(profile.daysPerWeek)

        // Untrimmed, so the day's dropped intentions are visible and can be restored.
        val fullPlan = buildPlan(split, pool, profile, ceilingMinutes = null)
        val templates = programDao.getAllTemplates()
            .filter { it.mesocycleId == mesocycleId && it.dayIndex >= 0 }
            .sortedBy { it.id }

        var changed = 0
        db.withTransaction {
            templates.forEachIndexed { index, template ->
                val slots = programDao.getSlots(template.id).toMutableList()
                var touched = false

                while (slots.size > MIN_EXERCISES &&
                    SessionEstimate.minutes(slots) > profile.sessionCeilingMinutes
                ) {
                    val isolation = slots.indexOfLast {
                        exercises[it.exerciseId]?.isCompound == false
                    }
                    programDao.deleteSlot(slots.removeAt(if (isolation >= 0) isolation else slots.lastIndex))
                    touched = true
                }

                val present = slots.mapTo(mutableSetOf()) { it.exerciseId }
                for (candidate in fullPlan.getOrNull(index)?.slots.orEmpty()) {
                    if (candidate.exercise.id in present) continue
                    val addition = candidate.toSlot(template.id, slots.size, profile.goal)
                    if (SessionEstimate.minutes(slots + addition) > profile.sessionCeilingMinutes) break
                    slots += addition.copy(id = programDao.insertSlot(addition))
                    present += candidate.exercise.id
                    touched = true
                }

                // Order indices have to stay dense or the running order develops gaps.
                slots.forEachIndexed { order, slot ->
                    if (slot.orderIndex != order) programDao.updateSlot(slot.copy(orderIndex = order))
                }
                if (touched) changed++
            }
        }
        return changed
    }

    /**
     * Moves an existing block's sessions onto the weekdays the profile now names.
     *
     * Not a rebuild: the exercises, the sets, the loads and the logged history are all
     * untouched — the same session simply happens on a Tuesday instead of a Monday.
     * Templates are matched to weekdays in their current day order, so an Upper/Lower
     * A-B-A-B rotation keeps its alternation rather than being reshuffled.
     *
     * Returns how many sessions actually moved.
     */
    suspend fun rescheduleWeekdays(profile: ProfileEntity, mesocycleId: Long): Int {
        val weekdays = profile.planSettings().weekdayIndices()
        val templates = programDao.getAllTemplates()
            .filter { it.mesocycleId == mesocycleId && it.dayIndex >= 0 }
            .sortedBy { it.dayIndex }

        var moved = 0
        db.withTransaction {
            templates.forEachIndexed { index, template ->
                // Fewer weekdays than sessions should be impossible — the count is
                // validated against daysPerWeek before it is stored — but a session with
                // nowhere to go keeps the day it has rather than vanishing from the week.
                val day = weekdays.getOrNull(index) ?: return@forEachIndexed
                if (template.dayIndex != day) {
                    programDao.updateTemplate(template.copy(dayIndex = day))
                    moved++
                }
            }
        }
        return moved
    }

    /**
     * Fills in working loads from what the user has actually lifted.
     *
     * Rebuilding a plan must not put every lift back to an empty bar, so a fresh block
     * starts each slot at the most recent working set logged for that movement — whatever
     * block it was logged in. Only empty slots are written unless [overwrite] is set.
     */
    suspend fun seedLoadsFromHistory(mesocycleId: Long, overwrite: Boolean = false): Int {
        val templates = programDao.getAllTemplates().filter { it.mesocycleId == mesocycleId }
        var seeded = 0
        db.withTransaction {
            for (template in templates) {
                for (slot in programDao.getSlots(template.id)) {
                    if (slot.workingLoadKg != null && !overwrite) continue
                    val load = sessionDao.getLatestWorkingSet(slot.exerciseId)?.loadKg ?: continue
                    programDao.updateSlot(slot.copy(workingLoadKg = load))
                    seeded++
                }
            }
        }
        return seeded
    }

    private suspend fun performablePool(
        profile: ProfileEntity,
        available: Set<String>,
    ): List<ExerciseEntity> {
        val cardio = equipmentDao.getAll()
            .filter { it.category == EquipmentCategory.CARDIO_MACHINE }
            .mapTo(mutableSetOf()) { it.id }

        return exerciseDao.getAll()
            .filter { !it.isExcluded }
            .filter { it.pattern !in profile.excludedPatterns }
            .filter { EquipmentAvailability.canPerform(it, available) }
            // Cardio is not a strength slot. Rowing is a horizontal pull and the rower is a
            // horizontal-pull machine, so nothing stopped the generator filling Upper A's
            // back compound with "Rowing (Full Body), 3 × 6–10 at 2 in reserve" — a
            // prescription that cannot be followed on a Concept2.
            .filterNot { exercise -> exercise.requiredEquipmentIds.any { it in cardio } }
            .sortedBy { it.id } // deterministic tie-break before any preference ordering
    }

    // ── Editing a template in place (8c: swap a row, add a row) ─────────

    /**
     * Appends an exercise to the end of a template and returns the new slot.
     *
     * The prescription comes from the same [Prescription] table the generator uses, so a
     * lift the user added by hand is programmed exactly as one the engine chose — there
     * is no second, weaker code path for "manual" slots.
     */
    suspend fun appendSlot(
        templateId: Long,
        exerciseId: String,
        profile: ProfileEntity,
    ): TemplateSlotEntity? {
        val exercise = exerciseDao.getById(exerciseId) ?: return null
        val existing = programDao.getSlots(templateId)
        val slot = prescribe(
            templateId = templateId,
            exercise = exercise,
            orderIndex = existing.size,
            goal = profile.goal,
        )
        return slot.copy(id = programDao.insertSlot(slot))
    }

    /**
     * Swaps the exercise in an existing slot, re-prescribing for the new movement.
     *
     * A compound cannot inherit an isolation's rep range and still make sense, so the
     * whole prescription is rebuilt. The working load is dropped deliberately: it belongs
     * to the lift that was there, and carrying it over would put someone under a bar at a
     * weight they have never lifted on that movement.
     */
    suspend fun replaceSlotExercise(
        slotId: Long,
        exerciseId: String,
        profile: ProfileEntity,
    ): TemplateSlotEntity? {
        val slot = programDao.getSlotById(slotId) ?: return null
        val exercise = exerciseDao.getById(exerciseId) ?: return null
        val replacement = prescribe(
            templateId = slot.templateId,
            exercise = exercise,
            orderIndex = slot.orderIndex,
            goal = profile.goal,
        ).copy(id = slot.id, supersetGroup = slot.supersetGroup)
        programDao.updateSlot(replacement)
        return replacement
    }

    private fun prescribe(
        templateId: Long,
        exercise: ExerciseEntity,
        orderIndex: Int,
        goal: Goal,
    ): TemplateSlotEntity {
        val rx = Prescription.of(goal, exercise.isCompound)
        return TemplateSlotEntity(
            templateId = templateId,
            exerciseId = exercise.id,
            orderIndex = orderIndex,
            targetSets = if (exercise.isCompound) 3 else 2,
            repRangeLow = rx.repLow,
            repRangeHigh = rx.repHigh,
            targetRir = rx.targetRir,
            restSeconds = rx.restSeconds,
            progression = exercise.defaultProgression,
        )
    }

    // ── Plan assembly ───────────────────────────────────────────────────

    /**
     * [ceilingMinutes] of null builds the untrimmed plan — every intention the split calls
     * for, before the time budget takes any of them away. That is what a raised session
     * ceiling needs in order to know what it can put back.
     */
    private fun buildPlan(
        split: Split,
        pool: List<ExerciseEntity>,
        profile: ProfileEntity,
        ceilingMinutes: Int? = profile.sessionCeilingMinutes,
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
                FilledSlot(spec, exercise, setsFor(spec, profile), profile.goal)
            }
            PlannedDay(day.label, ceilingMinutes?.let { trimToTimeBudget(filled, it) } ?: filled)
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
        SessionEstimate.minutesOf(
            slots.map { it.sets to Prescription.of(it.goal, it.spec.compound).restSeconds }
        )

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

                // Same stimulus as hypertrophy, shorter rests. Nothing here is a calorie
                // target: the only lever the engine has is how the training is arranged.
                Goal.LEAN -> if (compound) {
                    Prescription(6, 12, 2, 105)
                } else {
                    Prescription(12, 18, 1, 60)
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
        /** Carried so the time estimate can read the rest this slot will be prescribed. */
        val goal: Goal,
    )

    private data class PlannedDay(val label: String, val slots: List<FilledSlot>)

    companion object {
        /** 4 accumulation weeks plus a deload. */
        const val BLOCK_WEEKS = 5

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
            val rest = formatRest(slot.restSeconds)
            val why = when (goal) {
                Goal.STRENGTH -> "heavy, low reps and long rests are what move a max"
                Goal.HYPERTROPHY -> "moderate reps close to failure is what drives growth"
                Goal.LEAN -> "the same reps with shorter rests keeps the work density up"
                Goal.GENERAL -> "a middle rep range keeps strength and size both moving"
            }
            return "$exerciseName: ${slot.targetSets} × ${slot.repRangeLow}–${slot.repRangeHigh} " +
                "at ${slot.targetRir} in reserve, $rest rest — $why."
        }

        /**
         * Integer division rendered both 105 s and 90 s as "1 min", so two different
         * prescriptions read identically. Seconds are kept whenever they are not zero.
         */
        fun formatRest(seconds: Int): String {
            if (seconds < 60) return "$seconds s"
            val minutes = seconds / 60
            val remainder = seconds % 60
            return if (remainder == 0) "$minutes min" else "${minutes}m ${remainder}s"
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
 * [weekdayIndices] is the default layout (0 = Monday), chosen to break up runs of hard days
 * where the schedule leaves room. At six days a week there is exactly one rest day, so the
 * best available is two blocks of three rather than six consecutive sessions — which is
 * what [PPL_6] used to prescribe while the docs claimed otherwise.
 *
 * These are defaults, not rules: `ProfileEntity.trainingDays` overrides them whenever the
 * user has picked their own weekdays.
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

    /** Mon–Wed, rest Thursday, Fri–Sun. One rest day is all six sessions leave room for. */
    PPL_6(
        "Push / Pull / Legs",
        listOf(0, 1, 2, 4, 5, 6),
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
