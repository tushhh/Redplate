package dev.redplate

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Whether any of this app's UI is currently on screen.
 *
 * The rest alarm needs to know, because it is delivered identically whether the user is
 * watching the timer or has the phone in a pocket, and those two cases want different
 * things on screen. Counting started activities is enough here: this is a single-activity
 * app, so the count is 0 or 1 and there is nothing subtle to get wrong.
 *
 * Read from [dev.redplate.workout.RestAlarmReceiver], which runs in this same process —
 * and defaults to false in a process the alarm itself has just started, which is exactly
 * the answer that case needs.
 */
object AppForeground : Application.ActivityLifecycleCallbacks {

    private var startedActivities = 0

    val isForeground: Boolean get() = startedActivities > 0

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
