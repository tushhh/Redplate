package dev.redplate.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.EquipmentDao
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.Goal
import dev.redplate.data.ProfileDao
import dev.redplate.data.ProfileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntakeViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val equipmentDao: EquipmentDao,
) : ViewModel() {

    private val _state = MutableStateFlow(IntakeState())
    val state: StateFlow<IntakeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val equipment = equipmentDao.getAll()
            _state.update { it.copy(allEquipment = equipment) }
        }
    }

    fun setGoal(goal: Goal) = _state.update { it.copy(goal = goal) }

    fun setDaysPerWeek(days: Int) = _state.update { it.copy(daysPerWeek = days) }

    fun setSessionMinutes(minutes: Int) = _state.update { it.copy(sessionMinutes = minutes) }

    fun toggleEquipment(equipmentId: String) {
        _state.update { state ->
            val current = state.selectedEquipmentIds
            val next = if (equipmentId in current) current - equipmentId else current + equipmentId
            state.copy(selectedEquipmentIds = next)
        }
    }

    fun setDumbbellStep(step: DumbbellStep) = _state.update { it.copy(dumbbellStep = step) }

    fun setPlanChoice(choice: PlanChoice) = _state.update { it.copy(planChoice = choice) }

    fun setEquipmentFilter(filter: EquipmentFilter) = _state.update { it.copy(equipmentFilter = filter) }

    fun finishIntake(onComplete: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            profileDao.upsert(
                ProfileEntity(
                    trainingAgeMonths = 0,
                    daysPerWeek = s.daysPerWeek,
                    sessionCeilingMinutes = s.sessionMinutes,
                    goal = s.goal ?: Goal.HYPERTROPHY,
                    bodyweightKg = 80.0,
                )
            )

            // Mark selected equipment as available, others as unavailable
            s.allEquipment.forEach { eq ->
                equipmentDao.update(eq.copy(isAvailable = eq.id in s.selectedEquipmentIds))
            }

            onComplete()
        }
    }
}

data class IntakeState(
    val goal: Goal? = null,
    val daysPerWeek: Int = 4,
    val sessionMinutes: Int = 60,
    val allEquipment: List<EquipmentEntity> = emptyList(),
    val selectedEquipmentIds: Set<String> = emptySet(),
    val dumbbellStep: DumbbellStep = DumbbellStep.TWO_POINT_FIVE,
    val planChoice: PlanChoice = PlanChoice.GIVE_ME_A_PLAN,
    val equipmentFilter: EquipmentFilter = EquipmentFilter.ALL,
) {
    val selectedEquipmentCount: Int get() = selectedEquipmentIds.size

    val filteredEquipment: List<EquipmentEntity> get() = when (equipmentFilter) {
        EquipmentFilter.ALL -> allEquipment
        EquipmentFilter.BAR -> allEquipment.filter {
            it.category == dev.redplate.data.EquipmentCategory.BARBELL
        }
        EquipmentFilter.MACHINE -> allEquipment.filter {
            it.category == dev.redplate.data.EquipmentCategory.MACHINE
        }
        EquipmentFilter.CABLE -> allEquipment.filter {
            it.category == dev.redplate.data.EquipmentCategory.CABLE
        }
    }

    val splitDescription: String get() {
        val split = when (daysPerWeek) {
            2 -> "Full body, both days"
            3 -> "Full body, three sessions"
            4 -> "Upper / Lower, twice each"
            5 -> "Upper / Lower / Push / Pull / Legs"
            6 -> "Push / Pull / Legs, twice each"
            else -> "Full body"
        }
        val setsPerSession = when {
            sessionMinutes <= 30 -> "10–12"
            sessionMinutes <= 45 -> "14–16"
            sessionMinutes <= 60 -> "18–22"
            sessionMinutes <= 75 -> "22–26"
            else -> "26–30"
        }
        return "$split. Around $setsPerSession sets a session, every muscle hit ${if (daysPerWeek >= 4) "twice" else "1–2×"} a week — which is the point where progress actually shows up."
    }
}

enum class DumbbellStep(val label: String, val kgStep: Double) {
    TWO_POINT_FIVE("2.5 kg", 2.5),
    FIVE("5 kg", 5.0),
    CUSTOM("Pick", 0.0),
}

enum class PlanChoice {
    GIVE_ME_A_PLAN,
    I_CHOOSE,
}

enum class EquipmentFilter(val label: String) {
    ALL("ALL"),
    BAR("BAR"),
    MACHINE("MACHINE"),
    CABLE("CABLE"),
}
