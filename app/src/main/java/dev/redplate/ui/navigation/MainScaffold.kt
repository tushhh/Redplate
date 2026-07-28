package dev.redplate.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.redplate.history.HistoryRoute
import dev.redplate.onboarding.IntakeFlow
import dev.redplate.plan.ProgramBuilderRoute
import dev.redplate.plan.WeekPlanRoute
import dev.redplate.settings.BackupRoute
import dev.redplate.settings.EquipmentRoute
import dev.redplate.settings.PlanSettingsRoute
import dev.redplate.settings.SettingsRoute
import dev.redplate.today.TodayRoute
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.workout.ExercisePickerRoute
import dev.redplate.workout.SessionSummaryRoute
import dev.redplate.workout.SetLoggingRoute

/**
 * Main scaffold with 4-tab bottom navigation.
 * Tab bar hides when the user is in a full-bleed screen (set logging).
 * Shows intake flow when no profile exists yet.
 */
@Composable
fun MainScaffold() {
    val scaffoldViewModel: MainScaffoldViewModel = hiltViewModel()
    val hasProfile by scaffoldViewModel.hasProfile.collectAsStateWithLifecycle()

    if (hasProfile == null) return // Still loading

    if (hasProfile == false) {
        IntakeFlow(
            onIntakeComplete = { scaffoldViewModel.onProfileCreated() },
        )
        return
    }

    MainContent()
}

@Composable
private fun MainContent() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Tab bar hides during full-bleed screens
    val showTabs = currentRoute?.startsWith("setLogging") != true
        && currentRoute?.startsWith("sessionSummary") != true
        && currentRoute?.startsWith("programBuilder") != true
        && currentRoute != "backup"
        && currentRoute != "equipment"
        && currentRoute != "plan-settings"

    // Derived from the back stack rather than assigned inside each composable{} block.
    // Writing state during composition is what that did before, and it left the
    // highlight stale whenever navigation happened any other way (back gesture included).
    val selectedTab = when {
        currentRoute == null -> RedplateTab.Today

        // Checked before the Plan prefix: "plan-settings" belongs to You, and
        // startsWith("plan") would otherwise claim it.
        currentRoute.startsWith("you") ||
            currentRoute == "backup" ||
            currentRoute == "equipment" ||
            currentRoute == "plan-settings" -> RedplateTab.You

        currentRoute.startsWith("plan") || currentRoute.startsWith("programBuilder") ->
            RedplateTab.Plan

        currentRoute.startsWith("history") -> RedplateTab.History

        else -> RedplateTab.Today
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RedplateTheme.colors.ground),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = "today",
            ) {
                // ── Today tab ──
                composable("today") {
                    TodayRoute(
                        onStartWorkout = { sessionId, exerciseId ->
                            navController.navigate("setLogging/$sessionId/$exerciseId")
                        },
                        onPickExercise = {
                            navController.navigate("exercises")
                        },
                        // "See the full week" lands on the Plan tab, where the balance
                        // chart lives below the week list (design 10a).
                        onSeeFullWeek = {
                            navController.navigate("plan") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onEditSession = { templateId ->
                            navController.navigate("programBuilder/$templateId")
                        },
                        // Reopens the summary for a session already finished today.
                        onSeeSummary = { sessionId ->
                            navController.navigate("sessionSummary/$sessionId")
                        },
                    )
                }

                // ── Exercise picker (reachable from Today) ──
                composable("exercises") {
                    ExercisePickerRoute(
                        onExerciseSelected = { sessionId, exerciseId ->
                            navController.navigate("setLogging/$sessionId/$exerciseId") {
                                popUpTo("exercises") { inclusive = true }
                            }
                        },
                    )
                }

                // ── Set logging (full-bleed, hides tabs) ──
                composable(
                    "setLogging/{sessionId}/{exerciseId}",
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.LongType },
                        navArgument("exerciseId") { type = NavType.StringType },
                    ),
                ) {
                    SetLoggingRoute(
                        onBack = { navController.popBackStack() },
                        // Moving through the session replaces the current exercise rather
                        // than stacking on it, so Back leaves the workout instead of
                        // walking backwards through every lift already finished.
                        onNextExercise = { sessionId, exerciseId ->
                            navController.navigate("setLogging/$sessionId/$exerciseId") {
                                popUpTo("setLogging/{sessionId}/{exerciseId}") {
                                    inclusive = true
                                }
                            }
                        },
                        onSwapExercise = { sessionId, exerciseId ->
                            navController.navigate("setLogging/$sessionId/$exerciseId") {
                                popUpTo("setLogging/{sessionId}/{exerciseId}") {
                                    inclusive = true
                                }
                            }
                        },
                        onSessionFinished = { sessionId ->
                            navController.navigate("sessionSummary/$sessionId") {
                                popUpTo("today")
                            }
                        },
                    )
                }

                // ── Session summary (full-bleed) ──
                composable(
                    "sessionSummary/{sessionId}",
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.LongType },
                    ),
                ) {
                    SessionSummaryRoute(
                        onSeeLog = {
                            navController.navigate("history") {
                                popUpTo("today")
                                launchSingleTop = true
                            }
                        },
                        onDone = {
                            navController.navigate("today") {
                                popUpTo("today") { inclusive = true }
                            }
                        },
                    )
                }

                // ── Plan tab ──
                composable("plan") {
                    WeekPlanRoute(
                        onEditTemplate = { templateId ->
                            navController.navigate("programBuilder/$templateId")
                        },
                    )
                }

                // ── Program builder ──
                composable(
                    "programBuilder/{templateId}",
                    arguments = listOf(
                        navArgument("templateId") { type = NavType.LongType },
                    ),
                ) {
                    ProgramBuilderRoute(
                        onBack = { navController.popBackStack() },
                    )
                }

                // ── History tab ──
                composable("history") {
                    HistoryRoute()
                }

                // ── You tab ──
                composable("you") {
                    SettingsRoute(
                        onNavigateToBackup = { navController.navigate("backup") },
                        onNavigateToEquipment = { navController.navigate("equipment") },
                        // Plate pairs are part of the equipment inventory, so the
                        // "plates in your gym" row opens the screen that owns them
                        // rather than a plates screen that does not exist.
                        onNavigateToPlates = { navController.navigate("equipment") },
                        onNavigateToPlan = { navController.navigate("plan-settings") },
                    )
                }

                // ── Your plan (goal, days, session length, weekdays) ──
                composable("plan-settings") {
                    PlanSettingsRoute(onDone = { navController.popBackStack() })
                }

                // ── Backup screen ──
                composable("backup") {
                    BackupRoute(
                        onBack = { navController.popBackStack() },
                    )
                }

                // ── Equipment screen ──
                composable("equipment") {
                    EquipmentRoute(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        if (showTabs) {
            RedplateTabBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    val route = when (tab) {
                        RedplateTab.Today -> "today"
                        RedplateTab.Plan -> "plan"
                        RedplateTab.History -> "history"
                        RedplateTab.You -> "you"
                    }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
