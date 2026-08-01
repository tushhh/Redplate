package dev.redplate.workout

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.redplate.AppForeground
import dev.redplate.MainActivity
import dev.redplate.R

/**
 * Fires at the end of the rest period, whatever the app is doing.
 *
 * This is the whole point of arming an alarm rather than trusting a coroutine: Android
 * starts the process to deliver this even if it had killed the app outright, so the buzz
 * cannot be lost by putting the phone in a pocket.
 */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RestTimerNotifier.ACTION_REST_COMPLETE) return

        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.cancel(RestTimerNotifier.COUNTDOWN_ID)

        // The buzz always happens. It is the one thing the user is relying on.
        WorkoutHaptics(context).restComplete()

        // The notification does not, when the app is already on screen: the rest screen has
        // just hit zero in front of the user, and a heads-up saying so would cover the
        // number they are looking at to tell them what it says.
        if (AppForeground.isForeground) return

        val exerciseName = intent.getStringExtra(RestTimerNotifier.EXTRA_EXERCISE_NAME).orEmpty()
        notifications.notify(
            RestTimerNotifier.COMPLETE_ID,
            Notification.Builder(context, RestTimerNotifier.CHANNEL_COMPLETE)
                .setSmallIcon(R.drawable.ic_stat_rest)
                .setContentTitle("Rest is up")
                .setContentText(
                    if (exerciseName.isEmpty()) "Back to the bar." else "Back to $exerciseName.",
                )
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build(),
        )
    }
}
