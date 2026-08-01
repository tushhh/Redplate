package dev.redplate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.AndroidEntryPoint
import dev.redplate.ui.navigation.MainScaffold
import dev.redplate.ui.theme.RedplateTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Asked for once, and never acted on either way.
     *
     * Notifications carry the rest timer into the status bar; refusing them costs that and
     * nothing else, so there is no case where the app should nag or gate a feature behind
     * it. The end-of-rest vibration does not go through notifications and fires regardless.
     */
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            RedplateTheme {
                MainScaffold()
            }
        }
    }
}
