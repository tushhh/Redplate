package dev.redplate.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.BlockPhase
import dev.redplate.data.MesocycleEntity
import dev.redplate.data.ProgramDao
import dev.redplate.data.SessionDao
import dev.redplate.data.SessionTemplateEntity
import dev.redplate.data.TemplateSlotEntity
import dev.redplate.data.VolumeDao
import dev.redplate.data.VolumeSnapshotEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DayCard(
    val dayLabel: String,       // "MON", "TUE", etc.
    val sessionName: String?,   // null = rest day
    val templateId: Long?,
    val setCount: Int,
    val status: DayStatus,
)

enum class DayStatus { DONE, TODAY, PLANNED, REST }

data class VolumeTarget(
    val muscleName: String,
    val current: Int,
    val target: Int,
)

data class WeekPlanState(
    val weekNumber: Int = 1,
    val totalWeeks: Int = 5,
    val splitName: String = "",
    val splitDescription: String = "",
    val isOnTrack: Boolean = true,
    val phase: BlockPhase = BlockPhase.ACCUMULATION,
    val days: List<DayCard> = emptyList(),
    val volumeTargets: List<VolumeTarget> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class WeekPlanViewModel @Inject constructor(
    private val programDao: ProgramDao,
    private val sessionDao: SessionDao,
    private val volumeDao: VolumeDao,
) : ViewModel() {

    private val _state = MutableStateFlow(WeekPlanState())
    val state: StateFlow<WeekPlanState> = _state

    init {
        viewModelScope.launch {
            val meso = programDao.getActiveMesocycle()
            if (meso == null) {
                _state.value = WeekPlanState(isLoading = false)
                return@launch
            }

            programDao.observeTemplates(meso.id).collect { templates ->
                val days = buildDayCards(meso, templates)
                val volumes = loadVolumeTargets(meso)

                _state.value = WeekPlanState(
                    weekNumber = meso.currentWeek,
                    totalWeeks = meso.lengthWeeks,
                    splitName = meso.name,
                    splitDescription = describeSplit(templates),
                    isOnTrack = true,
                    phase = if (meso.currentWeek == meso.lengthWeeks) BlockPhase.DELOAD else BlockPhase.ACCUMULATION,
                    days = days,
                    volumeTargets = volumes,
                    isLoading = false,
                )
            }
        }
    }

    private suspend fun buildDayCards(
        meso: MesocycleEntity,
        templates: List<SessionTemplateEntity>,
    ): List<DayCard> {
        val today = LocalDate.now()
        val dayOfWeek = today.dayOfWeek
        val dayLabels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

        // Get completed sessions this week
        val weekStart = today.with(DayOfWeek.MONDAY)
        val weekStartMillis = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val allSessions = sessionDao.getAllSessions()
        val completedTemplateIds = allSessions
            .filter { it.mesocycleId == meso.id && it.weekNumber == meso.currentWeek && it.endedAt != null }
            .mapNotNull { it.templateId }
            .toSet()

        return dayLabels.mapIndexed { index, label ->
            val javaDow = DayOfWeek.of(index + 1)
            val template = templates.find { it.dayIndex == index }

            if (template == null) {
                DayCard(label, null, null, 0, DayStatus.REST)
            } else {
                val slots = programDao.getSlots(template.id)
                val status = when {
                    completedTemplateIds.contains(template.id) -> DayStatus.DONE
                    javaDow == dayOfWeek -> DayStatus.TODAY
                    javaDow < dayOfWeek -> DayStatus.DONE // past day without completion = still DONE visually
                    else -> DayStatus.PLANNED
                }
                DayCard(
                    dayLabel = label,
                    sessionName = template.label,
                    templateId = template.id,
                    setCount = slots.sumOf { it.targetSets },
                    status = status,
                )
            }
        }
    }

    private suspend fun loadVolumeTargets(meso: MesocycleEntity): List<VolumeTarget> {
        val snapshots = volumeDao.getSnapshots(meso.id, meso.currentWeek)
        val landmarks = volumeDao.getAllLandmarks()

        return snapshots.map { snap ->
            val landmark = landmarks.find { it.muscle == snap.muscle }
            VolumeTarget(
                muscleName = snap.muscle.name.lowercase().replace('_', ' ')
                    .replaceFirstChar { it.uppercaseChar() },
                current = snap.hardSets.toInt(),
                target = landmark?.mavHigh ?: snap.mav,
            )
        }.sortedByDescending { it.current }
    }

    private fun describeSplit(templates: List<SessionTemplateEntity>): String {
        val count = templates.size
        val labels = templates.map { it.label }
        return "$count days · ${labels.joinToString(", ")}"
    }
}
