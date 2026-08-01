package dev.redplate

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.redplate.data.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RedplateApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var seeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()
        // Registered here rather than in the activity: the rest alarm can start this
        // process with no activity at all, and that is a case it has to answer for.
        registerActivityLifecycleCallbacks(AppForeground)
        appScope.launch { seeder.seedIfNeeded() }
    }
}
