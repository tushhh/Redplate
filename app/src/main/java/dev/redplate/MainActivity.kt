package dev.redplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.workout.ExercisePickerRoute
import dev.redplate.workout.SetLoggingRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            RedplateTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "exercises") {
                    composable("exercises") {
                        ExercisePickerRoute(
                            onExerciseSelected = { sessionId, exerciseId ->
                                navController.navigate("setLogging/$sessionId/$exerciseId")
                            },
                        )
                    }
                    composable(
                        "setLogging/{sessionId}/{exerciseId}",
                        arguments = listOf(
                            navArgument("sessionId") { type = NavType.LongType },
                            navArgument("exerciseId") { type = NavType.StringType },
                        ),
                    ) {
                        SetLoggingRoute(
                            onBack = { navController.popBackStack() },
                            onOpenGuidance = {},
                        )
                    }
                }
            }
        }
    }
}
