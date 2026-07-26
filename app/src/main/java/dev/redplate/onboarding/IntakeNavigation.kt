package dev.redplate.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * The intake — designs 2c → 2d → 2e → 3a, and 3b when a plan is asked for.
 *
 * One question per screen, no keyboard, and every answer states its consequence. Nothing
 * is written until the last screen commits, so backing out of intake leaves no trace.
 */
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
                consequence = state.consequence,
                onSelectDays = viewModel::setDaysPerWeek,
                onSelectMinutes = viewModel::setSessionMinutes,
                onNext = { navController.navigate("equipment") },
            )
        }

        composable("equipment") {
            EquipmentScreen(
                equipment = state.filteredEquipment,
                totalCount = state.totalEquipmentCount,
                selectedIds = state.selectedEquipmentIds,
                selectedCount = state.selectedEquipmentCount,
                equipmentFilter = state.equipmentFilter,
                dumbbellStep = state.dumbbellStep,
                searchQuery = state.equipmentSearch,
                onToggleEquipment = viewModel::toggleEquipment,
                onSetFilter = viewModel::setEquipmentFilter,
                onSetDumbbellStep = viewModel::setDumbbellStep,
                onSearchChange = viewModel::setEquipmentSearch,
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
            val presets = remember(state.daysPerWeek) { buildPresetList(state.daysPerWeek) }

            // The screen opens on its best fit, as 3b draws it. Landing on a disabled
            // "pick one" bar after four answered questions reads as a dead end.
            LaunchedEffect(presets) {
                if (state.selectedPresetId == null) {
                    presets.firstOrNull { it.isBestFit }?.let { viewModel.selectPreset(it.id) }
                }
            }

            PresetLibraryScreen(
                daysPerWeek = state.daysPerWeek,
                sessionMinutes = state.sessionMinutes,
                presets = presets,
                selectedPresetId = state.selectedPresetId,
                // Tapping a card selects it; the primary bar commits.
                onSelectPreset = viewModel::selectPreset,
                onConfirm = { viewModel.finishIntake(onIntakeComplete) },
            )
        }
    }
}

/**
 * The presets 3b offers, marked against the week the user just described. A plan that
 * needs more days than they have is still listed, labelled and dimmed — picking it moves
 * the week to suit, which is the user's call to make.
 */
private fun buildPresetList(daysPerWeek: Int): List<PresetPlan> = listOf(
    PresetPlan(
        id = PRESET_UPPER_LOWER,
        name = "Upper / Lower",
        daysRequired = 4,
        durationRange = "55–65 MIN",
        volumeDescription = "4 DAYS · 55–65 MIN · 10–14 SETS PER MUSCLE / WEEK",
        description = "Everything trained twice a week with two clear rest days. The most " +
            "reliable structure at four days.",
        isBestFit = daysPerWeek in 3..5,
    ),
    PresetPlan(
        id = PRESET_STRENGTH,
        name = "Strength — heavy triples",
        daysRequired = 4,
        durationRange = "60–75 MIN",
        volumeDescription = "3–4 DAYS · 60–75 MIN · 2–3 SETS PER LIFT @ ~80% 1RM",
        description = "Squat, bench, deadlift, press first while you’re fresh. Long rests, " +
            "low reps, small weekly jumps.",
        isBestFit = daysPerWeek == 2,
    ),
    PresetPlan(
        id = PRESET_PPL,
        name = "Push / Pull / Legs",
        daysRequired = 6,
        durationRange = "50–60 MIN",
        volumeDescription = "6 DAYS · 50–60 MIN · 14–20 SETS PER MUSCLE / WEEK",
        description = "More volume than you need right now, and only if six days is " +
            "genuinely realistic.",
        isBestFit = daysPerWeek == 6,
        mismatchWarning = if (daysPerWeek < 6) "NEEDS 6 DAYS" else null,
    ),
)
