package dev.redplate.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import dev.redplate.workout.LoadEntrySheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.redplate.coach.CoachCopy
import dev.redplate.data.Goal
import dev.redplate.data.SeedState
import dev.redplate.ui.components.CoachHeadline
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * The intake — designs 2c → 2d → about you → 2e → 3a, and 3b when a plan is asked for.
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val seedState by viewModel.seedState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = "goal") {
        composable("goal") {
            GoalScreen(
                selectedGoal = state.goal,
                onSelectGoal = viewModel::setGoal,
                onNext = { navController.navigate("schedule") },
            )
        }

        composable("schedule") {
            ProvideIntakeBack({ navController.popBackStack() }) {
            ScheduleScreen(
                daysPerWeek = state.daysPerWeek,
                sessionMinutes = state.sessionMinutes,
                consequence = state.consequence,
                onSelectDays = viewModel::setDaysPerWeek,
                onSelectMinutes = viewModel::setSessionMinutes,
                onNext = { navController.navigate("aboutYou") },
            )
            }
        }

        composable("aboutYou") {
            ProvideIntakeBack({ navController.popBackStack() }) {
            AboutYouScreen(
                experience = state.experience,
                bodyweightKg = state.bodyweightKg,
                readinessFlagged = state.readinessFlagged,
                onSelectExperience = viewModel::setExperience,
                onBodyweightDown = viewModel::bodyweightDown,
                onBodyweightUp = viewModel::bodyweightUp,
                onEditBodyweight = viewModel::startBodyweightEntry,
                onSetReadiness = viewModel::setReadiness,
                onNext = { navController.navigate("equipment") },
            )
            state.bodyweightEntry?.let { entry ->
                LoadEntrySheet(
                    entry = entry,
                    unitLabel = "KG",
                    allowsDecimal = true,
                    canCommit = entry.toDoubleOrNull()?.let { it in 30.0..300.0 } == true,
                    onDigit = viewModel::appendBodyweightDigit,
                    onBackspace = viewModel::backspaceBodyweightEntry,
                    onCommit = viewModel::commitBodyweightEntry,
                    onDismiss = viewModel::cancelBodyweightEntry,
                    title = "WHAT DO YOU WEIGH?",
                )
            }
            }
        }

        // The first screen that reads seeded rows. Asking about an inventory that has not
        // been written yet shows an empty gym and no reason for it, so this waits.
        composable("equipment") {
            ProvideIntakeBack({ navController.popBackStack() }) {
            when (val seed = seedState) {
                SeedState.Seeding -> SeedWaitScreen()

                is SeedState.Failed -> SeedFailedScreen(
                    message = seed.message,
                    onRetry = viewModel::retrySeed,
                )

                SeedState.Ready -> EquipmentScreen(
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
            }
        }

        composable("planFork") {
            ProvideIntakeBack({ navController.popBackStack() }) {
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
        }

        composable("presetLibrary") {
            ProvideIntakeBack({ navController.popBackStack() }) {
            val base = state.presetBase ?: PresetBase(state.goal ?: Goal.HYPERTROPHY, state.daysPerWeek)
            val presets = remember(base, state.sessionMinutes) {
                buildPresetList(base, state.sessionMinutes)
            }

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

    state.saveError?.let { message ->
        IntakeErrorSheet(message = message, onDismiss = viewModel::consumeSaveError)
    }
}

/** Building the plan failed. Says what happened and what to try, then gets out of the way. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntakeErrorSheet(message: String, onDismiss: () -> Unit) {
    val colors = RedplateTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Column(Modifier.padding(horizontal = 22.dp)) {
                MonoLabel(text = "THAT DIDN'T WORK")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.height(14.dp))
            }
            PrimaryBar(
                label = "Back to setup",
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * The seed takes a moment on a cold first launch. Says what it is doing rather than
 * showing a spinner over an empty list.
 */
@Composable
private fun SeedWaitScreen() {
    val colors = RedplateTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        MonoLabel(text = "SETTING UP")
        Spacer(Modifier.height(10.dp))
        CoachHeadline(text = CoachCopy.Setup.SEEDING_HEADLINE)
        Spacer(Modifier.height(5.dp))
        Text(
            text = CoachCopy.Setup.SEEDING_BODY,
            style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
            color = colors.inkSecondary,
        )
    }
}

/** A failed seed used to be silent: an app with no exercises and nothing to explain it. */
@Composable
private fun SeedFailedScreen(message: String, onRetry: () -> Unit) {
    val colors = RedplateTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding()
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            MonoLabel(text = "SETUP FAILED")
            Spacer(Modifier.height(10.dp))
            CoachHeadline(text = CoachCopy.Setup.SEED_FAILED_HEADLINE)
            Spacer(Modifier.height(5.dp))
            Text(
                text = message,
                style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                color = colors.inkSecondary,
            )
        }
        PrimaryBar(label = CoachCopy.Setup.SEED_RETRY, onClick = onRetry)
    }
}

@Preview
@Composable
private fun SeedWaitPreview() {
    RedplateTheme { SeedWaitScreen() }
}

@Preview
@Composable
private fun SeedFailedPreview() {
    RedplateTheme {
        SeedFailedScreen(
message = CoachCopy.Setup.SEED_FAILED_BODY,
            onRetry = {},
        )
    }
}

/**
 * The presets 3b offers. The first is always "your answers" — the plan the five
 * questions describe, changing nothing — and it is the best fit by definition. The others
 * are alternatives, and each card says what picking it would change.
 */
private fun buildPresetList(base: PresetBase, sessionMinutes: Int): List<PresetPlan> = buildList {
    val split = when (base.daysPerWeek) {
        2 -> "Full body, twice a week"
        3 -> "Full body, three times a week"
        4 -> "Upper / Lower, twice each"
        5 -> "Upper / Lower / Push / Pull / Legs"
        else -> "Push / Pull / Legs, twice each"
    }
    add(
        PresetPlan(
            id = PRESET_ANSWERS,
            name = split,
            daysRequired = base.daysPerWeek,
            durationRange = "$sessionMinutes MIN",
            volumeDescription = "${base.daysPerWeek} DAYS · UP TO $sessionMinutes MIN · ${goalTag(base.goal)}",
            description = "Built from your answers. " + goalSummary(base.goal),
            isBestFit = true,
        ),
    )
    if (base.goal != Goal.STRENGTH) {
        add(
            PresetPlan(
                id = PRESET_STRENGTH,
                name = "Strength focus",
                daysRequired = base.daysPerWeek,
                durationRange = "$sessionMinutes MIN",
                volumeDescription = "${base.daysPerWeek} DAYS · HEAVY 3–6 REPS · LONG RESTS",
                description = "Switches your goal to Get stronger: heavier, fewer reps, longer " +
                    "rests." + if (base.goal == Goal.LEAN) " Drops the cardio finisher." else "",
                mismatchWarning = "CHANGES YOUR GOAL",
            ),
        )
    }
    if (base.daysPerWeek != 6) {
        add(
            PresetPlan(
                id = PRESET_PPL,
                name = "Push / Pull / Legs",
                daysRequired = 6,
                durationRange = "50–60 MIN",
                volumeDescription = "6 DAYS · 50–60 MIN · MORE WEEKLY VOLUME",
                description = "Only if six days a week is genuinely realistic — it moves your " +
                    "week from ${base.daysPerWeek} days to 6.",
                mismatchWarning = "NEEDS 6 DAYS",
            ),
        )
    }
}

private fun goalTag(goal: Goal): String = when (goal) {
    Goal.STRENGTH -> "STRENGTH"
    Goal.HYPERTROPHY -> "MUSCLE"
    Goal.LEAN -> "LEANER & STRONGER"
    Goal.GENERAL -> "GENERAL FITNESS"
}

private fun goalSummary(goal: Goal): String = when (goal) {
    Goal.STRENGTH -> "Heavy compounds, low reps, long rests."
    Goal.HYPERTROPHY -> "Moderate reps close to failure, volume climbing week to week."
    Goal.LEAN -> "Heavy 5–8 rep compounds, dense accessories and a short cardio finisher."
    Goal.GENERAL -> "A middle rep range, nothing brutal."
}

/**
 * The back affordance for intake screens after the first. They had none: system Back was
 * the only way, and nothing on screen said it existed.
 */
val LocalIntakeBack = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
private fun ProvideIntakeBack(onBack: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalIntakeBack provides onBack, content = content)
}
