package dev.redplate.data

/**
 * What one session did to the next prescription for one lift.
 *
 * Every outcome carries both the load to carry forward and the reason in plain language,
 * because a prescription the user cannot interrogate is the black box this app exists to
 * avoid (COACHING.md §3).
 *
 * [Hold] is a class rather than an object on purpose: holding still means the slot learns
 * the load that was actually used, which is how a slot that has never been written picks
 * up a working load at all.
 */
sealed interface ProgressionOutcome {

    /** The load the slot should carry into the next session. */
    val nextLoadKg: Double

    /** One sentence, no exercise name — the caller owns how the lift is addressed. */
    val reason: String

    data class Up(
        val fromKg: Double,
        override val nextLoadKg: Double,
        override val reason: String,
    ) : ProgressionOutcome

    data class Down(
        val fromKg: Double,
        override val nextLoadKg: Double,
        override val reason: String,
    ) : ProgressionOutcome

    data class Hold(
        override val nextLoadKg: Double,
        override val reason: String,
    ) : ProgressionOutcome
}

/**
 * The progression rules, as pure functions of what was logged.
 *
 * [ProgressionRule] used to be stored, rendered and then ignored — every slot behaved as
 * double progression whatever it claimed to be. This is the one place that decides, so
 * the rule on the slot and the behaviour of the app cannot drift apart again.
 *
 * Two invariants hold across every rule:
 *
 * 1. **Unreported effort never earns an increase.** A skipped difficulty prompt is missing
 *    information, not a maximal set. Rules that need RIR hold when it is absent rather
 *    than assuming the best case.
 * 2. **The load only steps to something the equipment can actually make**, via [PlateMath].
 *    Suggesting 62.3 kg to someone with 2.5 kg plates is worse than suggesting nothing.
 */
object ProgressionEngine {

    /** Fallback step when the lift has no equipment row to read increments from. */
    const val MANUAL_STEP_KG = 2.5

    /** "Close enough to failure to count" — the ceiling double progression promotes at. */
    const val HARD_SET_RIR = 2

    /**
     * [previousSessionMissed] is whether the last time this lift was trained, most of its
     * sets also fell short of the bottom of the range. Load progression uses it for the
     * "two misses running → back off 10%" rule (COACHING.md §4); the others ignore it.
     */
    fun decide(
        rule: ProgressionRule,
        sets: List<SetLogEntity>,
        slot: TemplateSlotEntity,
        equipment: EquipmentEntity?,
        previousSessionMissed: Boolean = false,
    ): ProgressionOutcome {
        val working = sets.filter { !it.isWarmup }
        if (working.isEmpty()) {
            return ProgressionOutcome.Hold(
                nextLoadKg = slot.workingLoadKg ?: 0.0,
                reason = "nothing was logged, so the prescription stays where it was",
            )
        }

        val load = working.maxOf { it.loadKg }
        return when (rule) {
            ProgressionRule.DOUBLE_PROGRESSION -> doubleProgression(working, slot, equipment, load)
            ProgressionRule.LOAD_PROGRESSION ->
                loadProgression(working, slot, equipment, load, previousSessionMissed)
            ProgressionRule.RIR_AUTOREGULATED -> rirAutoregulated(working, slot, equipment, load)
            ProgressionRule.NONE -> ProgressionOutcome.Hold(
                nextLoadKg = load,
                reason = "this lift isn't on a progression rule — the load stays yours to pick",
            )
        }
    }

    // ── The rules ───────────────────────────────────────────────────────

    /**
     * Reps climb inside the range first, then the load. Clear the top of the range on
     * every set with the effort reported and the weight goes up; miss the bottom on most
     * of the sets and it comes down; anything between holds.
     *
     * Clearing the top used to also require [HARD_SET_RIR] or harder, so a set that hit
     * fifteen reps and was reported as easy *held* — the lift that most obviously needed
     * more weight was the one guaranteed never to get it. Reported effort is still
     * required: an unreported set is missing information, not a maximal one.
     */
    private fun doubleProgression(
        sets: List<SetLogEntity>,
        slot: TemplateSlotEntity,
        equipment: EquipmentEntity?,
        load: Double,
    ): ProgressionOutcome {
        val clearedTop = sets.all { it.reps >= slot.repRangeHigh && it.rir != null }
        if (clearedTop) {
            val minRir = sets.minOf { it.rir ?: HARD_SET_RIR }
            return stepUp(
                load, equipment,
                "every set cleared ${slot.repRangeHigh} reps with $minRir " +
                    "${repWord(minRir)} in reserve",
            )
        }
        if (majorityMissed(sets, slot.repRangeLow)) {
            val short = slot.repRangeLow - sets.minOf { it.reps }
            return stepDown(load, equipment, "most sets fell $short ${repWord(short)} short of ${slot.repRangeLow}")
        }
        return ProgressionOutcome.Hold(
            nextLoadKg = load,
            reason = "${sets.maxOf { it.reps }} reps at ${trim(load)} kg — stay there until the range is clean",
        )
    }

    /**
     * Reps are fixed at the bottom of the range and the load is what moves. Complete the
     * prescribed reps with something left — no more than one rep harder than the target
     * RIR — and it steps, whether or not the top of the range was reached.
     *
     * The check used to be the other way round (`rir <= targetRir`), which stepped a set
     * ground out at zero in reserve and *held* one done with three to spare. A grinder is
     * the set that should repeat the weight; the easy one is the set that should move it.
     *
     * Two sessions running where most sets missed the prescribed reps is a stall rather
     * than a bad day, and the load backs off [CONSECUTIVE_MISS_DELOAD] to build again.
     */
    private fun loadProgression(
        sets: List<SetLogEntity>,
        slot: TemplateSlotEntity,
        equipment: EquipmentEntity?,
        load: Double,
        previousSessionMissed: Boolean,
    ): ProgressionOutcome {
        val floorRir = (slot.targetRir - 1).coerceAtLeast(0)
        val hitPrescription = sets.all {
            it.reps >= slot.repRangeLow && it.rir != null && it.rir >= floorRir
        }
        if (hitPrescription) {
            val minRir = sets.minOf { it.rir ?: floorRir }
            return stepUp(
                load, equipment,
                "every set made ${slot.repRangeLow} reps with $minRir ${repWord(minRir)} in reserve",
            )
        }
        if (majorityMissed(sets, slot.repRangeLow)) {
            val short = slot.repRangeLow - sets.minOf { it.reps }
            if (previousSessionMissed) {
                return backOff(
                    load, equipment,
                    "two sessions running short of ${slot.repRangeLow} reps",
                )
            }
            return stepDown(load, equipment, "most sets fell $short ${repWord(short)} short of ${slot.repRangeLow}")
        }
        return ProgressionOutcome.Hold(
            nextLoadKg = load,
            reason = "the prescription wasn't clean at ${trim(load)} kg — same weight next time",
        )
    }

    /**
     * The load is driven by reported effort against [TemplateSlotEntity.targetRir]: a full
     * point easier than the target and it climbs, harder than the target and it drops,
     * within a point and it holds. With no RIR reported there is nothing to autoregulate
     * against, so it holds.
     */
    private fun rirAutoregulated(
        sets: List<SetLogEntity>,
        slot: TemplateSlotEntity,
        equipment: EquipmentEntity?,
        load: Double,
    ): ProgressionOutcome {
        val reported = sets.mapNotNull { it.rir }
        if (reported.isEmpty()) {
            return ProgressionOutcome.Hold(
                nextLoadKg = load,
                reason = "no effort was reported, so there's nothing to autoregulate against",
            )
        }
        val average = reported.average()
        val target = slot.targetRir
        return when {
            average >= target + 1.0 -> stepUp(
                load, equipment,
                "you averaged ${trim(average)} in reserve against a target of $target — too easy",
            )

            average < target -> stepDown(
                load, equipment,
                "you averaged ${trim(average)} in reserve against a target of $target — harder than prescribed",
            )

            else -> ProgressionOutcome.Hold(
                nextLoadKg = load,
                reason = "${trim(average)} in reserve is on target — the load is right",
            )
        }
    }

    // ── Shared judgements ───────────────────────────────────────────────

    /**
     * The load only comes down when *most* of the sets missed the bottom of the range.
     *
     * `any` was too eager: one fatigued last set under the floor is the normal shape of a
     * hard session, and dropping the weight for it walks the prescription backwards every
     * time the user pushes. A strict majority still drops a single-set slot on its first
     * miss, which is the case where one set is all the evidence there is.
     */
    private fun majorityMissed(sets: List<SetLogEntity>, repLow: Int): Boolean =
        sets.count { it.reps < repLow } * 2 > sets.size

    /**
     * Makes the exercise harder — normally by stepping up to the next loadable weight.
     * When the equipment has nothing heavier — the last pair of plates is already on the
     * bar — this holds and says so, rather than prescribing a weight that cannot be
     * assembled.
     *
     * On an assistance machine "harder" means *less* counterweight, so the number goes down.
     * Stepping it up would congratulate the user for making the movement easier, and would
     * keep doing so until the machine was doing all of the work.
     */
    private fun stepUp(
        load: Double,
        equipment: EquipmentEntity?,
        reason: String,
    ): ProgressionOutcome {
        if (equipment?.isAssistance == true) {
            val next = PlateMath.nextLoadDown(load, equipment)
            if (next >= load - EPSILON) {
                return ProgressionOutcome.Hold(
                    nextLoadKg = load,
                    reason = "$reason, and there's no assistance left to take off — " +
                        "you're doing it unassisted",
                )
            }
            return ProgressionOutcome.Up(
                fromKg = load,
                nextLoadKg = next,
                reason = "$reason, so the assistance comes down",
            )
        }

        val next = equipment?.let { PlateMath.nextLoadUp(load, it) } ?: (load + MANUAL_STEP_KG)
        if (next <= load + EPSILON) {
            return ProgressionOutcome.Hold(
                nextLoadKg = load,
                reason = "$reason, but there's nothing heavier you can load — add reps instead",
            )
        }
        return ProgressionOutcome.Up(fromKg = load, nextLoadKg = next, reason = reason)
    }

    /** Makes the exercise easier — which on an assistance machine means more counterweight. */
    private fun stepDown(
        load: Double,
        equipment: EquipmentEntity?,
        reason: String,
    ): ProgressionOutcome {
        if (equipment?.isAssistance == true) {
            val next = PlateMath.nextLoadUp(load, equipment)
            if (next <= load + EPSILON) {
                return ProgressionOutcome.Hold(
                    nextLoadKg = load,
                    reason = "$reason, but the machine is already taking as much as it can",
                )
            }
            return ProgressionOutcome.Down(
                fromKg = load,
                nextLoadKg = next,
                reason = "$reason, so the assistance goes up",
            )
        }

        val next = equipment?.let { PlateMath.nextLoadDown(load, it) }
            ?: (load - MANUAL_STEP_KG).coerceAtLeast(0.0)
        if (next >= load - EPSILON) {
            return ProgressionOutcome.Hold(
                nextLoadKg = load,
                reason = "$reason, but this is already the lightest load you can make",
            )
        }
        return ProgressionOutcome.Down(fromKg = load, nextLoadKg = next, reason = reason)
    }

    /**
     * The bigger step back: [CONSECUTIVE_MISS_DELOAD] off, snapped to a load the equipment
     * can make. On an assistance machine there is no percentage to take off, so it falls
     * back to one notch more help.
     */
    private fun backOff(
        load: Double,
        equipment: EquipmentEntity?,
        reason: String,
    ): ProgressionOutcome {
        if (equipment?.isAssistance == true) return stepDown(load, equipment, reason)
        val next = equipment?.let { PlateMath.deload(load, CONSECUTIVE_MISS_DELOAD, it) }
            ?: (load * (1 - CONSECUTIVE_MISS_DELOAD))
        if (next >= load - EPSILON) return stepDown(load, equipment, reason)
        val percent = (CONSECUTIVE_MISS_DELOAD * 100).toInt()
        return ProgressionOutcome.Down(
            fromKg = load,
            nextLoadKg = next,
            reason = "$reason — back off $percent% and build again",
        )
    }

    /** Two consecutive load-progression misses take this much off (COACHING.md §4). */
    const val CONSECUTIVE_MISS_DELOAD = 0.10

    /** Whether most of a session's working sets fell short of [repLow]. */
    fun missedMostSets(sets: List<SetLogEntity>, repLow: Int): Boolean {
        val working = sets.filter { !it.isWarmup }
        return working.isNotEmpty() && majorityMissed(working, repLow)
    }

    private fun repWord(n: Int) = if (n == 1) "rep" else "reps"

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", value)

    private const val EPSILON = 1e-6
}
