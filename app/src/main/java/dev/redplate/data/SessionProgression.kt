package dev.redplate.data

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The piece that supplies a lift's load — the barbell, not the rack it sits in.
 *
 * Chosen by what it is rather than by list order. A lift names both the station and the
 * load source, and two call sites took the first entry: a barbell squat resolved to the
 * half rack, a `BODYWEIGHT` fixture, and progression fell back to stepping 1.25 kg on a
 * bar loaded in 2.5 kg pairs. One definition, used everywhere.
 */
fun ExerciseEntity.loadSource(equipmentById: Map<String, EquipmentEntity>): EquipmentEntity? {
    val declared = requiredEquipmentIds.mapNotNull { equipmentById[it] }
    return declared.firstOrNull { it.carriesLoad && it.isAvailable }
        ?: declared.firstOrNull { it.carriesLoad }
        ?: declared.firstOrNull { it.isAvailable }
        ?: declared.firstOrNull()
}

/** What one session did to the next conditioning finisher. Minutes, never kilograms. */
data class FinisherOutcome(
    /** The target this session was prescribed. */
    val targetMinutes: Int,
    /** What was actually done this session. */
    val loggedMinutes: Int,
    /** The target the slot carries into the next session. */
    val nextMinutes: Int,
    val reason: String,
) {
    val isIncrease: Boolean get() = nextMinutes > targetMinutes
}

/**
 * How a conditioning finisher progresses: by duration, one minute at a time, up to a cap.
 *
 * The finisher used to be fed through double progression as though minutes were reps, so
 * ten minutes on a treadmill "earned" 1.25 kg. Duration is what moves here, and only when
 * the effort was reported and was not everything you had — a finisher that wipes you out
 * is already long enough.
 *
 * Cutting a finisher short never lowers its target: a session that ran out of time is not
 * evidence that ten minutes is too long.
 */
object FinisherProgression {

    /** Short finisher: the default, and lower-body days. */
    const val START_MINUTES = 10
    const val NOVICE_START_MINUTES = 8
    const val MIN_MINUTES = 5
    /** The short finisher's cap. */
    const val MAX_MINUTES = 15

    /**
     * The incline walk after an upper-body day: starts at 20 and builds to 30, two minutes
     * a session. Once it is at 30 the time holds and the incline goes up instead.
     */
    const val LONG_START_MINUTES = 20
    const val NOVICE_LONG_START_MINUTES = 15
    const val LONG_CAP_MINUTES = 30

    /** The standalone rest-day walk: 30 to 45 minutes. */
    const val REST_DAY_START_MINUTES = 30
    const val REST_DAY_CAP_MINUTES = 45

    /** Nothing in the program builder goes past this. */
    const val ABSOLUTE_MAX_MINUTES = 60

    /** Long walks build two minutes at a time; short finishers one. */
    fun stepFor(capMinutes: Int): Int = if (capMinutes > MAX_MINUTES) 2 else 1

    /**
     * [capMinutes] is the slot's ceiling — [TemplateSlotEntity.repRangeHigh] on a finisher.
     */
    fun decide(sets: List<SetLogEntity>, targetMinutes: Int, capMinutes: Int = MAX_MINUTES): FinisherOutcome {
        val cap = capMinutes.coerceIn(MIN_MINUTES, ABSOLUTE_MAX_MINUTES)
        val target = targetMinutes.coerceIn(MIN_MINUTES, cap)
        val step = stepFor(cap)
        val logged = sets.filter { !it.isWarmup }
        if (logged.isEmpty()) {
            return FinisherOutcome(target, 0, target, "nothing was logged")
        }
        val minutes = logged.maxOf { it.reps }
        // Doing more than prescribed moves the target up to what was done.
        val held = minutes.coerceIn(target, cap)
        val effort = logged.mapNotNull { it.rir }.minOrNull()

        return when {
            minutes < target -> FinisherOutcome(
                target, minutes, target,
                "stopped at $minutes of $target min — same target next time",
            )

            effort == null -> FinisherOutcome(
                target, minutes, held,
                "$minutes min done, but no effort was picked — same time next session",
            )

            effort == 0 -> FinisherOutcome(
                target, minutes, held,
                "$minutes min took everything you had — repeat it before adding more",
            )

            minutes >= cap -> FinisherOutcome(
                target, minutes, cap,
                "at the $cap-minute cap — keep the time and raise the incline or pace instead",
            )

            else -> {
                val next = (minutes + step).coerceIn(MIN_MINUTES, cap)
                FinisherOutcome(target, minutes, next, "$minutes min with something left, so next time is $next")
            }
        }
    }

    /**
     * The target a finished session was prescribed, recovered from the slot as it stands
     * now — which, after [ProgressionApplier.applyOnFinish], already holds the *next*
     * target. The summary needs the original to say what changed.
     *
     * The write only ever produces three shapes: the logged minutes plus a step (an earned
     * increase), the logged minutes (a hold at or above target), or an unchanged target
     * above what was logged (cut short). Anything above `logged + step` can only be the
     * last of those, so it is the original; otherwise the original was at most what was
     * logged.
     */
    fun prescribedTarget(loggedMinutes: Int, slotMinutesNow: Int, step: Int = 1): Int =
        if (slotMinutesNow > loggedMinutes + step) slotMinutesNow
        else minOf(slotMinutesNow, loggedMinutes)
}

/** One lift's verdict after a session, in whichever unit that lift progresses in. */
sealed interface LiftDecision {
    val exerciseId: String
    val exerciseName: String

    data class Strength(
        override val exerciseId: String,
        override val exerciseName: String,
        val outcome: ProgressionOutcome,
    ) : LiftDecision

    data class Conditioning(
        override val exerciseId: String,
        override val exerciseName: String,
        val outcome: FinisherOutcome,
    ) : LiftDecision
}

/**
 * Decides — and, once, writes — what a finished session changes about the next one.
 *
 * This used to live in the summary screen's ViewModel, which meant progression was only
 * saved if the summary was opened: kill the app between "Finish" and the summary and the
 * session's progress was lost. Reopening an old summary also re-wrote its slots, which
 * could overwrite a newer load the weekly assessment had already set.
 *
 * Now [applyOnFinish] runs from [WorkoutRepository.endSession], the one moment a session
 * becomes history, and [preview] lets the summary render the same decisions without
 * writing anything. The decision is a pure function of the logged sets and the slot's
 * prescription, so the two always agree.
 */
@Singleton
class ProgressionApplier @Inject constructor(
    private val db: RedplateDatabase,
    private val sessionDao: SessionDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val programDao: ProgramDao,
) {

    suspend fun preview(session: SessionEntity): List<LiftDecision> =
        decide(session, afterWrite = true).map { it.second }

    /**
     * Writes the decisions to the session's template. Call once, when the session ends,
     * inside the same transaction that stamps it finished.
     */
    suspend fun applyOnFinish(session: SessionEntity) {
        val decisions = decide(session, afterWrite = false)
        if (decisions.isEmpty()) return
        db.withTransaction {
            for ((slot, decision) in decisions) {
                if (slot == null) continue
                val updated = when (decision) {
                    is LiftDecision.Strength ->
                        slot.copy(workingLoadKg = decision.outcome.nextLoadKg)

                    // The target moves; the cap in repRangeHigh stays where the plan put it.
                    is LiftDecision.Conditioning -> slot.copy(
                        repRangeLow = decision.outcome.nextMinutes,
                        repRangeHigh = maxOf(slot.repRangeHigh, decision.outcome.nextMinutes),
                        workingLoadKg = null,
                    )
                }
                if (updated != slot) programDao.updateSlot(updated)
            }
        }
    }

    /**
     * [afterWrite] says whether the slots may already hold this session's result. Lifts
     * do not care — their decision never reads the stored load — but a finisher's does
     * read its target, so the summary recovers the original one first.
     */
    private suspend fun decide(
        session: SessionEntity,
        afterWrite: Boolean,
    ): List<Pair<TemplateSlotEntity?, LiftDecision>> {
        val working = sessionDao.getSetsForSession(session.id).filter { !it.isWarmup }
        if (working.isEmpty()) return emptyList()

        val slots = session.templateId?.let { programDao.getSlots(it) }.orEmpty()
        val exercises = exerciseDao.getAll().associateBy { it.id }
        val equipment = equipmentDao.getAll().associateBy { it.id }

        // Running order, so the summary reads in the order the session was trained.
        val order = slots.withIndex().associate { (i, s) -> s.exerciseId to i }
        val grouped = working.groupBy { it.exerciseId }
            .toList()
            .sortedBy { (id, _) -> order[id] ?: Int.MAX_VALUE }

        return grouped.mapNotNull { (exerciseId, sets) ->
            val exercise = exercises[exerciseId] ?: return@mapNotNull null
            val slot = slots.firstOrNull { it.exerciseId == exerciseId }

            if (slot?.isCardioFinisher == true || exercise.isConditioning) {
                val logged = sets.maxOf { it.reps }
                val stored = slot?.repRangeLow ?: logged
                // repRangeHigh is the cap; older slots wrote the target there too.
                val cap = slot?.repRangeHigh?.takeIf { it > stored } ?: maxOf(stored, FinisherProgression.MAX_MINUTES)
                val step = FinisherProgression.stepFor(cap)
                val target = if (afterWrite) FinisherProgression.prescribedTarget(logged, stored, step) else stored
                return@mapNotNull slot to LiftDecision.Conditioning(
                    exerciseId, exercise.name, FinisherProgression.decide(sets, target, cap),
                )
            }

            // A freestyle session has no slot to progress, but the user still gets told
            // what the numbers mean — judged against the default range.
            val basis = slot ?: freestyleBasis(exerciseId, sets.size)
            val outcome = ProgressionEngine.decide(
                rule = basis.progression,
                sets = sets,
                slot = basis,
                equipment = exercise.loadSource(equipment),
                // Exactly one miss before this one makes two in a row. A longer streak has
                // already had its 10% back-off and steps down normally from here, rather
                // than taking another 10% every session.
                previousSessionMissed = basis.progression == ProgressionRule.LOAD_PROGRESSION &&
                    priorMissStreak(exerciseId, session, basis.repRangeLow) == 1,
            )
            slot to LiftDecision.Strength(exerciseId, exercise.name, outcome)
        }
    }

    /**
     * How many sessions in a row, immediately before this one, mostly missed the bottom of
     * the range on this lift. Counted to two at most — that is all the rule needs to know.
     */
    private suspend fun priorMissStreak(
        exerciseId: String,
        session: SessionEntity,
        repLow: Int,
    ): Int {
        val earlier = sessionDao.getWorkingSetsForExercise(exerciseId)
            .filter { it.sessionId != session.id && it.completedAt < session.startedAt }
        val bySession = earlier.groupBy { it.sessionId }
            .values
            .sortedByDescending { sets -> sets.maxOf { it.completedAt } }
        var streak = 0
        for (sets in bySession.take(2)) {
            if (!ProgressionEngine.missedMostSets(sets, repLow)) break
            streak++
        }
        return streak
    }

    private fun freestyleBasis(exerciseId: String, setCount: Int) = TemplateSlotEntity(
        templateId = 0,
        exerciseId = exerciseId,
        orderIndex = 0,
        targetSets = setCount,
        repRangeLow = DEFAULT_REP_LOW,
        repRangeHigh = DEFAULT_REP_HIGH,
        targetRir = DEFAULT_TARGET_RIR,
        restSeconds = DEFAULT_REST_SECONDS,
        progression = ProgressionRule.DOUBLE_PROGRESSION,
    )

    private companion object {
        const val DEFAULT_REP_LOW = 8
        const val DEFAULT_REP_HIGH = 12
        const val DEFAULT_TARGET_RIR = 2
        const val DEFAULT_REST_SECONDS = 120
    }
}
