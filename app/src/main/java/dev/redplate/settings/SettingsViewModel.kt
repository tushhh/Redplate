package dev.redplate.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.EquipmentDao
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.ProfileDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val name: String = "",
    val bodyweightKg: Double = 0.0,
    val trainingAgeMonths: Int = 0,
    val useMetric: Boolean = true,
    val daysPerWeek: Int = 0,
    val equipmentCount: Int = 0,
    val isLoading: Boolean = true,
)

data class EquipmentListState(
    val equipment: List<EquipmentEntity> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val equipmentDao: EquipmentDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    private val _equipmentState = MutableStateFlow(EquipmentListState())
    val equipmentState: StateFlow<EquipmentListState> = _equipmentState

    init {
        viewModelScope.launch {
            profileDao.observe().collect { profile ->
                if (profile != null) {
                    val equipment = equipmentDao.getAll()
                    val availableCount = equipment.count { it.isAvailable }
                    _state.value = SettingsState(
                        bodyweightKg = profile.bodyweightKg,
                        trainingAgeMonths = profile.trainingAgeMonths,
                        useMetric = profile.useMetric,
                        daysPerWeek = profile.daysPerWeek,
                        equipmentCount = availableCount,
                        isLoading = false,
                    )
                }
            }
        }
        loadEquipment()
    }

    private fun loadEquipment() {
        viewModelScope.launch {
            equipmentDao.observeAll().collect { equipment ->
                _equipmentState.value = EquipmentListState(
                    equipment = equipment,
                    isLoading = false,
                )
            }
        }
    }

    fun toggleUnits() {
        viewModelScope.launch {
            val profile = profileDao.get() ?: return@launch
            profileDao.upsert(profile.copy(useMetric = !profile.useMetric))
        }
    }

    fun toggleEquipment(equipmentId: String) {
        viewModelScope.launch {
            val eq = equipmentDao.getById(equipmentId) ?: return@launch
            equipmentDao.update(eq.copy(isAvailable = !eq.isAvailable))
            // Refresh settings count
            val all = equipmentDao.getAll()
            _state.value = _state.value.copy(equipmentCount = all.count { it.isAvailable })
        }
    }
}
