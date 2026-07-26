package dev.redplate.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class ExerciseRow(
    val orderIndex: Int,
    val name: String,
    val prescription: String,
    val loadNote: String?,
)

data class SessionCard(
    val label: String,
    val totalSets: Int,
    val estimatedMinutes: Int,
    val exercises: List<ExerciseRow>,
    val remainingCount: Int,
    val templateId: Long,
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
    ) : TodayState

    data class RestDay(
        val eyebrow: String,
        val headline: String,
        val coachBody: String,
        val nextSessionLabel: String?,
    ) : TodayState
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val programDao: ProgramDao,
    private val sessionDao: SessionDao,
    private val volumeDao: VolumeDao,
    private val exerciseDao: ExerciseDao,
) : ViewModel() {

    private val _state = MutableStateFlow<TodayState>(TodayState.Loading)
    val state: StateFlow<TodayState> = _state.asStateFlow()

    // Holds the template ID + meso ID when there's a training day
    private var pendingTemplateId: Long? = null
    private var pendingMesoId: Long? = null
    private var pendingMesoWeek: Int? = null

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

        val meso = programDao.getActiveMesocycle()
        if (meso == null) {
            _state.value = TodayState.NoProgramYet
            return
        }

        val templates = programDao.observeTemplates(meso.id).first()
        if (templates.isEmpty()) {
            _state.value = TodayState.NoProgramYet
            return
        }

        val today = LocalDate.now()
        val todayDayOfWeek = today.dayOfWeek
        val dayName = todayDayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()
        val timeOfDay = timeOfDayLabel()

        // Determine which template is today based on rotation
        val todayTemplate = findTodayTemplate(meso, templates, today)

        if (todayTemplate == null) {
            // Rest day
            val nextTemplate = findNextTemplate(meso, templates, today)
            _state.value = TodayState.RestDay(
                eyebrow = "$dayName · WEEK ${meso.currentWeek} OF ${meso.lengthWeeks}",
                headline = "Rest day. You've earned it.",
                coachBody = if (nextTemplate != null) {
                    "Next session is ${nextTemplate.label}."
                } else {
                    "No more sessions scheduled this week."
                },
                nextSessionLabel = nextTemplate?.label,
            )
            return
        }

        pendingTemplateId = todayTemplate.id
        pendingMesoId = meso.id
        pendingMesoWeek = meso.currentWeek

        val slots = programDao.getSlots(todayTemplate.id)
        val exerciseRows = slots.take(3).map { slot ->
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
                loadNote = null,
            )
        }

        val totalSets = slots.sumOf { it.targetSets }
        val estimatedMinutes = estimateSessionMinutes(slots, profile.sessionCeilingMinutes)
        val remaining = (slots.size - 3).coerceAtLeast(0)

        val isFirst = sessionDao.getLatestSession() == null

        // Volume data
        val volumeRows = buildVolumeRows(meso.id, meso.currentWeek)

        val volumeCoachLine = if (isFirst) {
            "Fills in as you log. Trends need three sessions."
        } else {
            buildVolumeCoachLine(volumeRows)
        }

        val eyebrow = "$dayName $timeOfDay · WEEK ${meso.currentWeek} OF ${meso.lengthWeeks}"

        _state.value = TodayState.TrainingDay(
            eyebrow = eyebrow,
            headline = if (isFirst) {
                "First one. Go light on purpose."
            } else {
                "${todayTemplate.label}. About ${estimatedMinutes / 15 * 15} minutes."
            },
            coachBody = if (isFirst) {
                "Pick a weight you could manage two more reps with. Today sets the baseline — every number after this is built off it."
            } else {
                buildCoachBody(slots)
            },
            sessionCard = SessionCard(
                label = todayTemplate.label,
                totalSets = totalSets,
                estimatedMinutes = estimatedMinutes,
                exercises = exerciseRows,
                remainingCount = remaining,
                templateId = todayTemplate.id,
            ),
            volumeRows = volumeRows,
            volumeCoachLine = volumeCoachLine,
            primaryLabel = if (isFirst) "Start ${todayTemplate.label}" else "Let's go",
            isFirstSession = isFirst,
        )
    }

    fun startSession(onNavigate: (Long, String) -> Unit) {
        viewModelScope.launch {
            val templateId = pendingTemplateId ?: return@launch
            val mesoId = pendingMesoId
            val week = pendingMesoWeek

            val slots = programDao.getSlots(templateId)
            if (slots.isEmpty()) return@launch

            val session = SessionEntity(
                templateId = templateId,
                mesocycleId = mesoId,
                weekNumber = week,
                startedAt = System.currentTimeMillis(),
            )
            val sessionId = sessionDao.insertSession(session)
            val firstExerciseId = slots.first().exerciseId
            onNavigate(sessionId, firstExerciseId)
        }
    }

    private fun findTodayTemplate(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        today: LocalDate,
    ): SessionTemplateEntity? {
        // dayIndex maps to weekday: 0=MON, 1=TUE, ... 6=SUN
        val todayIndex = today.dayOfWeek.value - 1 // DayOfWeek.MONDAY = 1
        return templates.find { it.dayIndex == todayIndex }
    }

    private fun findNextTemplate(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
        today: LocalDate,
    ): SessionTemplateEntity? {
        val todayIndex = today.dayOfWeek.value - 1
        // Look for the next template after today
        return templates
            .filter { it.dayIndex > todayIndex }
            .minByOrNull { it.dayIndex }
            ?: templates.minByOrNull { it.dayIndex } // wrap to next week
    }

    private suspend fun buildVolumeRows(mesoId: Long, week: Int): List<VolumeRow> {
        val snapshots = volumeDao.getSnapshots(mesoId, week)
        if (snapshots.isEmpty()) {
            // Return landmarks with zero progress
            val landmarks = volumeDao.observeAllLandmarks().first()
            return landmarks.take(3).map { lm ->
                VolumeRow(
                    label = lm.muscle.displayName(),
                    current = 0,
                    target = lm.mavHigh,
                )
            }
        }
        return snapshots.take(3).map { snap ->
            VolumeRow(
                label = snap.muscle.displayName(),
                current = snap.hardSets.toInt(),
                target = snap.mav,
            )
        }
    }

    private fun buildVolumeCoachLine(rows: List<VolumeRow>): String {
        val lowest = rows.minByOrNull { it.current.toFloat() / it.target.coerceAtLeast(1) }
        return if (lowest != null && lowest.current < lowest.target) {
            "${lowest.label} is light this week — later sessions cover it."
        } else {
            "Volume is on track this week."
        }
    }

    private suspend fun buildCoachBody(slots: List<TemplateSlotEntity>): String {
        // Find a slot with a load change to highlight
        val slot = slots.firstOrNull { it.workingLoadKg != null }
        if (slot != null) {
            val load = slot.workingLoadKg ?: return "Same plan as last time — stay focused on form."
            val exercise = exerciseDao.getById(slot.exerciseId)
            val name = exercise?.name ?: "First exercise"
            return "${name} is at ${formatKg(load)} kg."
        }
        return "Same plan as last time — stay focused on form."
    }

    private fun estimateSessionMinutes(slots: List<TemplateSlotEntity>, ceiling: Int): Int {
        // Rough estimate: ~3 min per set (including rest)
        val estimated = slots.sumOf { it.targetSets } * 3
        return estimated.coerceAtMost(ceiling)
    }

    private fun timeOfDayLabel(): String {
        val hour = java.time.LocalTime.now().hour
        return when {
            hour < 12 -> "MORNING"
            hour < 17 -> "AFTERNOON"
            else -> "EVENING"
        }
    }

    private fun formatKg(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) {
            kg.toLong().toString()
        } else {
            kg.toString()
        }
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
