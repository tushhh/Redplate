package dev.redplate.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.coach.CoachCopy
import dev.redplate.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class ExerciseRow(
    val orderIndex: Int,
    val name: String,
    val prescription: String,
    val loadNote: String?,
)

data class SessionCard(
    val label: String,
    /** "20 SETS · 58 MIN", or "14 SETS · ~45 MIN" before there is any history. */
    val summaryLine: String,
    val totalSets: Int,
    val estimatedMinutes: Int,
    val exercises: List<ExerciseRow>,
    val remainingCount: Int,
    val templateId: Long,
)

/** One bar of the six-week estimated-1RM chart on the stall screen (design 9c). */
data class E1rmWeek(
    val label: String,
    val e1rm: Double,
    /** Flat weeks carry the colour — they are the evidence the headline claims. */
    val isFlat: Boolean,
)

/** A line of "A DELOAD WEEK MEANS". [isOutcome] is the one that reads as the payoff. */
data class DeloadEffect(
    val label: String,
    val value: String,
    val isOutcome: Boolean = false,
)

data class VolumeRow(
    val label: String,
    val current: Int,
    val target: Int,
)

sealed interface TodayState {
    data object Loading : TodayState

    data object NoProgramYet : TodayState

    data class TrainingDay(
        val eyebrow: String,
        val headline: String,
        val coachBody: String,
        val sessionCard: SessionCard,
        val volumeRows: List<VolumeRow>,
        val volumeCoachLine: String,
        val primaryLabel: String,
        val isFirstSession: Boolean,
        /**
         * Set when a session against today's template was started and never finished. An
         * abandoned session used to be invisible and unreachable — the only way back in
         * was to open a second one beside it.
         */
        val resumeSessionId: Long? = null,
    ) : TodayState

    data class RestDay(
        val eyebrow: String,
        val headline: String,
        val coachBody: String,
        val nextSessionLabel: String?,
    ) : TodayState

    /**
     * The block is built but its start date has not arrived.
     *
     * A plan used to be assumed to begin the instant it was generated, so building one on
     * a Tuesday meant week 1 was already two days old. Choosing a start date is the point;
     * honouring it before that date is what makes the choice real.
     */
    data class NotStartedYet(
        val eyebrow: String,
        val headline: String,
        val coachBody: String,
        val firstSessionLabel: String?,
    ) : TodayState

    /**
     * Today's session is done.
     *
     * Finishing a workout and reopening Today used to show the same "Let's go" card as
     * before, with nothing to say it had happened and nothing stopping a second session
     * being logged against the same template by accident.
     */
    data class Completed(
        val eyebrow: String,
        /** "Upper A. Done." */
        val headline: String,
        /** "18 sets · 47 min · 2 PRs" */
        val summaryLine: String,
        val volumeRows: List<VolumeRow>,
        val volumeCoachLine: String,
        /** "Next: Lower A, Thursday" */
        val nextSessionLabel: String?,
        /** Reopens the summary for the session that was just finished. */
        val sessionId: Long,
        /** Training again today is possible, but deliberate: a secondary action. */
        val templateId: Long,
    ) : TodayState

    /**
     * Three flat weeks on one lift. Shown here rather than as a notification, and as
     * the evidence rather than a verdict (design 9c).
     */
    data class Stalled(
        val eyebrow: String,
        val headline: String,
        val coachBody: String,
        val e1rmWeeks: List<E1rmWeek>,
        val deloadEffects: List<DeloadEffect>,
        val templateId: Long,
    ) : TodayState
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val programDao: ProgramDao,
    private val sessionDao: SessionDao,
    private val volumeDao: VolumeDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val mesocycleAdvancer: MesocycleAdvancer,
    private val trainingClock: TrainingClock,
    private val outcomeReader: SessionOutcomeReader,
) : ViewModel() {

    private val _state = MutableStateFlow<TodayState>(TodayState.Loading)
    val state: StateFlow<TodayState> = _state.asStateFlow()

    // Holds the template ID + meso ID when there's a training day
    private var pendingTemplateId: Long? = null
    private var pendingMesoId: Long? = null
    private var pendingMesoWeek: Int? = null

    /** An unfinished session against today's template, so the primary action can resume it. */
    private var resumableSessionId: Long? = null

    /** "Push on" holds for the life of this ViewModel — never a recurring nag. */
    private var stallDismissed = false

    init {
        refresh()
    }

    /**
     * Recomputes the day. Called on every resume, because finishing a session or editing
     * a program happens on another screen and Today is a summary of both.
     */
    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val profile = profileDao.get()
        if (profile == null) {
            _state.value = TodayState.NoProgramYet
            return
        }

        val active = programDao.getActiveMesocycle()
        if (active == null) {
            _state.value = TodayState.NoProgramYet
            return
        }

        // A block moves forward here, on the screen that asks "what do I do today?".
        // Nothing else was ever going to notice that a week had been completed.
        mesocycleAdvancer.advanceIfDue(active, profile)
        val meso = programDao.getActiveMesocycle() ?: active

        val templates = programDao.observeTemplates(meso.id).first()
        if (templates.isEmpty()) {
            _state.value = TodayState.NoProgramYet
            return
        }

        // The training day, not the calendar day: a session logged at 02:00 belongs to
        // the day before, so that is the day Today answers for.
        val dayStartHour = profile.dayStartHour
        val today = trainingClock.trainingDate(System.currentTimeMillis(), dayStartHour)
        val dayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()
        val eyebrow = "$dayName · WEEK ${meso.currentWeek} OF ${meso.lengthWeeks}"

        // A block with a start date in the future has not begun. Saying so beats counting
        // weeks that have not happened.
        val beginsOn = meso.beginsAt.takeIf { it > 0L }
            ?.let { trainingClock.trainingDate(it, dayStartHour) }
        if (beginsOn != null && today < beginsOn) {
            _state.value = notStartedState(meso, templates, beginsOn, today, eyebrow)
            return
        }

        val todaysSessions = sessionsOn(today, dayStartHour)

        // Determine which template is today based on rotation
        val todayTemplate = findTodayTemplate(meso, templates, today)

        if (todayTemplate == null) {
            _state.value = restDayState(meso, templates, today, eyebrow, todaysSessions)
            return
        }

        pendingTemplateId = todayTemplate.id
        pendingMesoId = meso.id
        pendingMesoWeek = meso.currentWeek

        val forTemplate = todaysSessions.filter { it.templateId == todayTemplate.id }
        val finished = forTemplate.lastOrNull { it.endedAt != null }
        resumableSessionId = forTemplate.firstOrNull { it.endedAt == null }?.id

        if (finished != null) {
            _state.value = completedState(meso, templates, today, eyebrow, todayTemplate, finished)
            return
        }

        // A stall takes over the whole screen — the session is still there behind it,
        // but the decision in front of the user is what to do about the flat lift.
        if (!stallDismissed) {
            detectStall(todayTemplate.id)?.let {
                _state.value = it
                return
            }
        }

        val slots = programDao.getSlots(todayTemplate.id)
        val exerciseRows = slots.take(VISIBLE_SLOTS).map { slot ->
            val exercise = exerciseDao.getById(slot.exerciseId)
            val loadText = if (slot.workingLoadKg != null) {
                "${formatKg(slot.workingLoadKg)} kg"
            } else {
                "you choose"
            }
            ExerciseRow(
                orderIndex = slot.orderIndex + 1,
                name = exercise?.name ?: slot.exerciseId,
                prescription = "${slot.targetSets} × ${slot.repRangeLow}–${slot.repRangeHigh} · $loadText",
                loadNote = loadDeltaFor(slot),
            )
        }

        val totalSets = slots.sumOf { it.targetSets }
        // The real number, rest included, and no longer clamped to the ceiling — reporting
        // the ceiling when the session runs past it is just a lie with extra steps.
        val estimatedMinutes = SessionEstimate.minutes(slots)
        val remaining = (slots.size - 3).coerceAtLeast(0)

        val isFirst = sessionDao.getLatestSession() == null

        // Volume data
        val volumeRows = buildVolumeRows(meso.id, meso.currentWeek)

        val volumeCoachLine = if (isFirst) {
            CoachCopy.Today.FIRST_SESSION_VOLUME
        } else {
            buildVolumeCoachLine(volumeRows)
        }

        _state.value = TodayState.TrainingDay(
            eyebrow = eyebrow,
            headline = if (isFirst) {
                CoachCopy.Today.FIRST_SESSION_HEADLINE
            } else {
                "${todayTemplate.label}. About ${SessionEstimate.spokenMinutes(estimatedMinutes)} minutes."
            },
            coachBody = if (isFirst) {
                CoachCopy.Today.FIRST_SESSION_BODY
            } else {
                // What the block concluded from the week just trained outranks a bare
                // statement of today's load: it is the reason today's load is what it is.
                meso.assessmentNote ?: buildCoachBody(slots)
            },
            sessionCard = SessionCard(
                label = todayTemplate.label,
                summaryLine = if (isFirst) {
                    "$totalSets SETS · ~$estimatedMinutes MIN"
                } else {
                    "$totalSets SETS · $estimatedMinutes MIN"
                },
                totalSets = totalSets,
                estimatedMinutes = estimatedMinutes,
                exercises = exerciseRows,
                remainingCount = remaining,
                templateId = todayTemplate.id,
            ),
            volumeRows = volumeRows,
            volumeCoachLine = volumeCoachLine,
            primaryLabel = when {
                resumableSessionId != null -> "Resume session"
                isFirst -> "Start ${todayTemplate.label}"
                else -> "Let's go"
            },
            isFirstSession = isFirst,
            resumeSessionId = resumableSessionId,
        )
    }

    private suspend fun notStartedState(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        beginsOn: LocalDate,
        today: LocalDate,
        eyebrow: String,
    ): TodayState.NotStartedYet {
        val first = scheduled(templates).minByOrNull {
            Math.floorMod(it.dayIndex - trainingClock.weekdayIndex(beginsOn), 7)
        }
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, beginsOn).toInt()
        val dayName = beginsOn.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

        return TodayState.NotStartedYet(
            eyebrow = eyebrow,
            headline = when (days) {
                1 -> "${meso.name} starts tomorrow."
                else -> "${meso.name} starts $dayName."
            },
            coachBody = when {
                first != null && days == 1 ->
                    "First session is ${first.label}. Rest up — you start tomorrow."

                first != null ->
                    "First session is ${first.label}, in $days days. Nothing to do until then."

                else -> "Your plan is built and waiting."
            },
            firstSessionLabel = first?.label,
        )
    }

    // ── The states that acknowledge what already happened (2.2) ─────

    /**
     * Rest day. If something was logged anyway — a freestyle or body-map session — say so
     * rather than telling the user to rest on a day they have already trained.
     */
    private suspend fun restDayState(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        today: LocalDate,
        eyebrow: String,
        todaysSessions: List<SessionEntity>,
    ): TodayState {
        val nextTemplate = findNextTemplate(meso, templates, today)
        val logged = todaysSessions.lastOrNull { it.endedAt != null }

        if (logged != null) {
            val outcome = outcomeReader.read(logged)
            return TodayState.Completed(
                eyebrow = eyebrow,
                headline = CoachCopy.Today.UNSCHEDULED_SESSION_HEADLINE,
                summaryLine = summaryLine(outcome),
                volumeRows = buildVolumeRows(meso.id, meso.currentWeek),
                volumeCoachLine = CoachCopy.Today.UNSCHEDULED_SESSION_VOLUME,
                nextSessionLabel = nextSessionLabel(nextTemplate, today),
                sessionId = logged.id,
                templateId = logged.templateId ?: 0L,
            )
        }

        return TodayState.RestDay(
            eyebrow = eyebrow,
            headline = CoachCopy.Today.REST_DAY_HEADLINE,
            coachBody = if (nextTemplate != null) {
                CoachCopy.Today.nextSession(nextTemplate.label)
            } else {
                CoachCopy.Today.REST_DAY_NOTHING_LEFT
            },
            nextSessionLabel = nextTemplate?.label,
        )
    }

    private suspend fun completedState(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        today: LocalDate,
        eyebrow: String,
        template: SessionTemplateEntity,
        session: SessionEntity,
    ): TodayState.Completed {
        val outcome = outcomeReader.read(session)
        val volumeRows = buildVolumeRows(meso.id, meso.currentWeek)
        return TodayState.Completed(
            eyebrow = eyebrow,
            headline = "${template.label}. Done.",
            summaryLine = summaryLine(outcome),
            volumeRows = volumeRows,
            volumeCoachLine = buildVolumeCoachLine(volumeRows),
            nextSessionLabel = nextSessionLabel(findNextTemplate(meso, templates, today), today),
            sessionId = session.id,
            templateId = template.id,
        )
    }

    /** "18 sets · 47 min · 2 PRs" — what was achieved, not a congratulation. */
    private fun summaryLine(outcome: SessionOutcome): String = listOfNotNull(
        "${outcome.workingSets} set${plural(outcome.workingSets)}",
        outcome.durationMinutes.takeIf { it > 0 }?.let { "$it min" },
        outcome.prCount.takeIf { it > 0 }?.let { "$it PR${plural(it)}" },
    ).joinToString(" · ")

    private fun nextSessionLabel(next: SessionTemplateEntity?, today: LocalDate): String? {
        if (next == null) return null
        val day = DayOfWeek.of(next.dayIndex + 1)
            .getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "Next: ${next.label}, $day"
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    /** Sessions that started inside the given training day. */
    private suspend fun sessionsOn(date: LocalDate, dayStartHour: Int): List<SessionEntity> {
        val bounds = trainingClock.dayBounds(date, dayStartHour)
        return sessionDao.getSessionsStartedBetween(bounds.first, bounds.last + 1)
    }

    fun startSession(onNavigate: (Long, String) -> Unit) {
        viewModelScope.launch {
            val templateId = pendingTemplateId ?: return@launch
            val slots = programDao.getSlots(templateId)
            if (slots.isEmpty()) return@launch

            // Walk back into the session already open rather than opening a second one
            // beside it, landing on the first lift that still has sets owing.
            resumableSessionId?.let { sessionId ->
                val logged = sessionDao.getSetsForSession(sessionId)
                val next = firstUnfinishedSlot(slots, logged) ?: slots.first()
                onNavigate(sessionId, next.exerciseId)
                return@launch
            }

            onNavigate(openSession(templateId), slots.first().exerciseId)
        }
    }

    /**
     * Training the same day again. Possible, but never the primary action — the completed
     * card offers it as a secondary so a second session is always a decision.
     */
    fun startAnotherSession(templateId: Long, onNavigate: (Long, String) -> Unit) {
        viewModelScope.launch {
            val slots = programDao.getSlots(templateId)
            if (slots.isEmpty()) return@launch
            onNavigate(openSession(templateId), slots.first().exerciseId)
        }
    }

    private suspend fun openSession(templateId: Long): Long {
        val mesoId = pendingMesoId
        val week = pendingMesoWeek
        val meso = mesoId?.let { programDao.getMesocycleById(it) }
        return sessionDao.insertSession(
            SessionEntity(
                templateId = templateId,
                mesocycleId = mesoId,
                weekNumber = week,
                // Was hardcoded to ACCUMULATION, so a deload week logged as a hard one.
                phase = if (meso != null && week != null) {
                    meso.phaseForWeek(week)
                } else {
                    BlockPhase.ACCUMULATION
                },
                startedAt = System.currentTimeMillis(),
            )
        )
    }

    /** The first slot with fewer working sets logged than prescribed. */
    private fun firstUnfinishedSlot(
        slots: List<TemplateSlotEntity>,
        logged: List<SetLogEntity>,
    ): TemplateSlotEntity? {
        val done = logged.filter { !it.isWarmup }.groupingBy { it.exerciseId }.eachCount()
        return slots.firstOrNull { (done[it.exerciseId] ?: 0) < it.targetSets }
    }

    // ── Stall detection (COACHING.md §4, design 9c) ─────────────────

    /**
     * Three consecutive weeks without an estimated-1RM improvement on a primary lift.
     * Deliberately conservative: it needs [STALL_WEEKS_REQUIRED] flat weeks on top of
     * enough history to be sure, so a single bad session never triggers it.
     */
    private suspend fun detectStall(templateId: Long): TodayState.Stalled? {
        val slots = programDao.getSlots(templateId)
        val primary = slots.firstOrNull { it.progression != ProgressionRule.NONE } ?: return null
        val exercise = exerciseDao.getById(primary.exerciseId) ?: return null

        val weeks = weeklyBests(primary.exerciseId)
        if (weeks.size < E1RM_WEEKS) return null

        val recent = weeks.takeLast(E1RM_WEEKS)
        val best = recent.dropLast(STALL_WEEKS_REQUIRED).maxOfOrNull { it } ?: return null
        val flatTail = recent.takeLast(STALL_WEEKS_REQUIRED)
        if (flatTail.any { it > best + STALL_TOLERANCE_KG }) return null

        val current = primary.workingLoadKg ?: return null
        val equipment = equipmentFor(exercise)
        val deloaded = equipment
            ?.let { PlateMath.deload(current, DELOAD_FRACTION, it) }
            ?: (current * (1 - DELOAD_FRACTION))
        val restartAt = equipment
            ?.let { PlateMath.nextLoadUp(current, it) }
            ?: (current + 2.5)

        return TodayState.Stalled(
            eyebrow = "${exercise.name.uppercase()} · ${STALL_WEEKS_REQUIRED} FLAT WEEKS",
            headline = "${exercise.name} hasn't moved in $STALL_WEEKS_REQUIRED weeks.",
            coachBody = "Same ${formatKg(current)} kg, and the last rep got harder each " +
                "time. That's a stall, not a bad day.",
            e1rmWeeks = recent.mapIndexed { index, value ->
                E1rmWeek(
                    label = "W${index + 1}",
                    e1rm = value,
                    isFlat = index >= recent.size - STALL_WEEKS_REQUIRED,
                )
            },
            deloadEffects = listOf(
                DeloadEffect(exercise.name, "${formatKg(current)} → ${formatKg(deloaded)} kg"),
                DeloadEffect("Sets per lift", "${primary.targetSets} → ${(primary.targetSets / 2).coerceAtLeast(1)}"),
                DeloadEffect("Then week 1 restarts at", "${formatKg(restartAt)} kg", isOutcome = true),
            ),
            templateId = templateId,
        )
    }

    /** Best estimated 1RM per training week for one lift, oldest first. */
    private suspend fun weeklyBests(exerciseId: String): List<Double> {
        val sets = sessionDao.getWorkingSetsForExercise(exerciseId)
            .filter { it.reps in 1..12 }
        if (sets.isEmpty()) return emptyList()

        val dayStartHour = trainingClock.dayStartHour()
        return sets
            .groupBy { trainingClock.weekStart(it.completedAt, dayStartHour) }
            .toSortedMap()
            .map { (_, weekSets) -> weekSets.maxOf { it.estimated1Rm() } }
    }

    private suspend fun equipmentFor(exercise: ExerciseEntity): EquipmentEntity? =
        exercise.requiredEquipmentIds
            .firstNotNullOfOrNull { equipmentDao.getById(it) }
            ?.takeIf { it.isAvailable }

    /**
     * Accepts the deload: halves the sets, drops the loads, and moves the block into its
     * deload week. This used to relabel the week and change nothing at all, so the screen
     * promised a lighter week and then prescribed the same one.
     */
    fun takeDeload() {
        viewModelScope.launch {
            val meso = programDao.getActiveMesocycle() ?: return@launch
            mesocycleAdvancer.forceDeload(meso)
            stallDismissed = true
            load()
        }
    }

    /** "Push on" — the user's call, honoured without argument for this session. */
    fun pushOnThroughStall() {
        stallDismissed = true
        refresh()
    }

    private fun findTodayTemplate(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        today: LocalDate,
    ): SessionTemplateEntity? {
        // dayIndex maps to weekday: 0=MON, 1=TUE, ... 6=SUN
        val todayIndex = today.dayOfWeek.value - 1 // DayOfWeek.MONDAY = 1
        return scheduled(templates).find { it.dayIndex == todayIndex }
    }

    private fun findNextTemplate(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        today: LocalDate,
    ): SessionTemplateEntity? {
        val todayIndex = today.dayOfWeek.value - 1
        val scheduled = scheduled(templates)
        // Look for the next template after today
        return scheduled
            .filter { it.dayIndex > todayIndex }
            .minByOrNull { it.dayIndex }
            ?: scheduled.minByOrNull { it.dayIndex } // wrap to next week
    }

    /**
     * Ad-hoc body-map sessions are parked at a negative day index. Without this filter
     * `minByOrNull` picked one up and the rest-day card announced a one-off as the next
     * scheduled session.
     */
    private fun scheduled(templates: List<SessionTemplateEntity>): List<SessionTemplateEntity> =
        templates.filter { it.dayIndex >= 0 }

    /**
     * The muscles most in need of work this week, worst first.
     *
     * This used to take the first three rows the database returned, from a query with no
     * `ORDER BY` — so "arbitrary" was literal, and because nothing ever wrote a snapshot
     * they all read zero. Ranking by shortfall against MEV makes the footer answer the
     * only question it can usefully answer: what is this week still missing?
     */
    private suspend fun buildVolumeRows(mesoId: Long, week: Int): List<VolumeRow> {
        val landmarks = volumeDao.getAllLandmarks().ifEmpty { VolumeLandmarks.DEFAULTS }
        val credited = volumeDao.getSnapshots(mesoId, week).associateBy { it.muscle }

        return landmarks
            .map { landmark ->
                val done = credited[landmark.muscle]?.hardSets ?: 0.0
                val shortfall = landmark.mev - done
                VolumeRow(
                    label = landmark.muscle.displayName(),
                    current = done.roundToInt(),
                    target = credited[landmark.muscle]?.mav ?: landmark.mavHigh,
                ) to shortfall
            }
            .sortedByDescending { (_, shortfall) -> shortfall }
            .take(VOLUME_ROWS)
            .map { (row, _) -> row }
    }

    private fun buildVolumeCoachLine(rows: List<VolumeRow>): String {
        val lowest = rows.minByOrNull { it.current.toFloat() / it.target.coerceAtLeast(1) }
        return if (lowest != null && lowest.current < lowest.target) {
            CoachCopy.Today.volumeShort(lowest.label)
        } else {
            CoachCopy.Today.VOLUME_ON_TRACK
        }
    }

    private suspend fun buildCoachBody(slots: List<TemplateSlotEntity>): String {
        // Find a slot with a load change to highlight
        val slot = slots.firstOrNull { it.workingLoadKg != null }
        if (slot != null) {
            val load = slot.workingLoadKg ?: return CoachCopy.Today.SAME_PLAN
            val exercise = exerciseDao.getById(slot.exerciseId)
            val name = exercise?.name ?: "First exercise"
            return "${name} is at ${formatKg(load)} kg."
        }
        return CoachCopy.Today.SAME_PLAN
    }

    private fun formatKg(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) {
            kg.toLong().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", kg)
        }
    }

    /** "+2.5" beside a lift whose prescription moved since it was last trained. */
    private suspend fun loadDeltaFor(slot: TemplateSlotEntity): String? {
        val prescribed = slot.workingLoadKg ?: return null
        val lastLogged = sessionDao.getLatestWorkingSet(slot.exerciseId)?.loadKg ?: return null

        val delta = prescribed - lastLogged
        if (kotlin.math.abs(delta) < 0.01) return null
        return if (delta > 0) "+${formatKg(delta)}" else "−${formatKg(-delta)}"
    }

    private companion object {
        const val VISIBLE_SLOTS = 3
        const val VOLUME_ROWS = 3
        const val E1RM_WEEKS = 6
        const val STALL_WEEKS_REQUIRED = 3
        const val STALL_TOLERANCE_KG = 0.5
        const val DELOAD_FRACTION = 0.2
    }
}

private fun MuscleGroup.displayName(): String = when (this) {
    MuscleGroup.CHEST -> "Chest"
    MuscleGroup.UPPER_BACK -> "Upper Back"
    MuscleGroup.LATS -> "Lats"
    MuscleGroup.LOWER_BACK -> "Lower Back"
    MuscleGroup.FRONT_DELTS -> "Front Delts"
    MuscleGroup.SIDE_DELTS -> "Side Delts"
    MuscleGroup.REAR_DELTS -> "Rear Delts"
    MuscleGroup.BICEPS -> "Biceps"
    MuscleGroup.TRICEPS -> "Triceps"
    MuscleGroup.FOREARMS -> "Forearms"
    MuscleGroup.QUADS -> "Quads"
    MuscleGroup.HAMSTRINGS -> "Hamstrings"
    MuscleGroup.GLUTES -> "Glutes"
    MuscleGroup.ADDUCTORS -> "Adductors"
    MuscleGroup.CALVES -> "Calves"
    MuscleGroup.ABS -> "Abs"
    MuscleGroup.OBLIQUES -> "Obliques"
    MuscleGroup.TRAPS -> "Traps"
    MuscleGroup.NECK -> "Neck"
}
