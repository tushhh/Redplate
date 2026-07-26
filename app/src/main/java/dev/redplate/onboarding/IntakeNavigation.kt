package dev.redplate.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun IntakeFlow(
    onIntakeComplete: () -> Unit,
) {
    val navController = rememberNavController()
    val viewModel: IntakeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    NavHost(navController = navController, startDestination = "goal") {
        composable("goal") {
            GoalScreen(
                selectedGoal = state.goal,
                onSelectGoal = viewModel::setGoal,
                onNext = { navController.navigate("schedule") },
            )
        }

        composable("schedule") {
            ScheduleScreen(
                daysPerWeek = state.daysPerWeek,
                sessionMinutes = state.sessionMinutes,
                splitDescription = state.splitDescription,
                onSelectDays = viewModel::setDaysPerWeek,
                onSelectMinutes = viewModel::setSessionMinutes,
                onNext = { navController.navigate("equipment") },
            )
        }

        composable("equipment") {
            EquipmentScreen(
                equipment = state.filteredEquipment,
                selectedIds = state.selectedEquipmentIds,
                selectedCount = state.selectedEquipmentCount,
                equipmentFilter = state.equipmentFilter,
                dumbbellStep = state.dumbbellStep,
                onToggleEquipment = viewModel::toggleEquipment,
                onSetFilter = viewModel::setEquipmentFilter,
                onSetDumbbellStep = viewModel::setDumbbellStep,
                onNext = { navController.navigate("planFork") },
            )
        }

        composable("planFork") {
            PlanForkScreen(
                selectedChoice = state.planChoice,
                onSelectChoice = viewModel::setPlanChoice,
                onFinish = {
                    if (state.planChoice == PlanChoice.GIVE_ME_A_PLAN) {
                        navController.navigate("presetLibrary")
                    } else {
                        viewModel.finishIntake(onIntakeComplete)
                    }
                },
            )
        }

        composable("presetLibrary") {
            PresetLibraryScreen(
                daysPerWeek = state.daysPerWeek,
                sessionMinutes = state.sessionMinutes,
                presets = buildPresetList(state.daysPerWeek),
                selectedPresetId = "upper_lower",
                onSelectPreset = { presetId ->
                    viewModel.finishIntake(onIntakeComplete)
                },
            )
        }
    }
}

private fun buildPresetList(daysPerWeek: Int): List<PresetPlan> = listOf(
    PresetPlan(
        id = "upper_lower",
        name = "Upper / Lower",
        daysRequired = 4,
        durationRange = "55\u201365 MIN",
        volumeDescription = "4 DAYS \u00B7 55\u201365 MIN \u00B7 10\u201314 SETS PER MUSCLE / WEEK",
        description = "Everything trained twice a week with two clear rest days. The most reliable structure at four days.",
        isBestFit = daysPerWeek == 4,
    ),
    PresetPlan(
        id = "strength",
        name = "Strength \u2014 heavy triples",
        daysRequired = 4,
        durationRange = "60\u201375 MIN",
        volumeDescription = "3\u20134 DAYS \u00B7 60\u201375 MIN \u00B7 2\u20133 SETS PER LIFT @ ~80% 1RM",
        description = "Squat, bench, deadlift, press first while you\u2019re fresh. Long rests, low reps, small weekly jumps.",
    ),
    PresetPlan(
        id = "ppl",
        name = "Push / Pull / Legs",
        daysRequired = 6,
        durationRange = "50\u201360 MIN",
        volumeDescription = "6 DAYS \u00B7 50\u201360 MIN \u00B7 14\u201320 SETS PER MUSCLE / WEEK",
        description = "More volume than you need right now, and only if six days is genuinely realistic.",
        mismatchWarning = if (daysPerWeek < 6) "NEEDS 6 DAYS" else null,
    ),
)
