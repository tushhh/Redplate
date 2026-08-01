package dev.redplate.workout

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redplate.MainActivity
import dev.redplate.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts the rest timer in the status bar and guarantees the buzz at zero.
 *
 * The in-app countdown is a coroutine in [SetLoggingViewModel], which is the right thing
 * while the screen is up and useless the moment it isn't: put the phone down and the
 * composable stops collecting, and if Android reclaims the process the timer stops
 * existing. Between sets is exactly when the phone goes in a pocket, so the timer has to
 * survive leaving the app.
 *
 * Two independent pieces, neither of which needs the app to be running:
 *
 * 1. **A countdown notification.** `setChronometerCountDown` lets the system tick it, so
 *    the number stays right with no work from us and no foreground service.
 * 2. **An exact alarm at the deadline**, delivered to [RestAlarmReceiver], which vibrates.
 *    `setExactAndAllowWhileIdle` fires through doze — a phone lying still on a bench with
 *    the screen off is precisely the case a normal alarm would defer.
 *
 * The alarm is the *only* thing that vibrates, foreground or not. Vibrating from the
 * screen as well would double the buzz whenever the user happened to be looking.
 */
@Singleton
class RestTimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /**
     * Shows the countdown and arms the buzz.
     *
     * Safe to call repeatedly — adjusting the rest re-arms both against the new deadline,
     * because the alarm's [PendingIntent] matches by action and is simply replaced.
     */
    fun start(deadlineMillis: Long, exerciseName: String, setLabel: String) {
        ensureChannels()
        notifications.cancel(COMPLETE_ID)
        notifications.notify(COUNTDOWN_ID, countdownNotification(deadlineMillis, exerciseName, setLabel))
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineMillis, alarmIntent(exerciseName))
    }

    /** Rest is over early — skipped, moved on, or session finished. Leaves nothing behind. */
    fun cancel() {
        alarms.cancel(alarmIntent(exerciseName = ""))
        notifications.cancel(COUNTDOWN_ID)
        notifications.cancel(COMPLETE_ID)
    }

    /**
     * Clears the countdown when it runs out with the user watching.
     *
     * The alarm still fires and still vibrates; this only takes down the status-bar entry
     * that has just become false. [RestAlarmReceiver] decides whether to replace it with a
     * "rest complete" notification, which it does not do while the app is on screen.
     */
    fun clearCountdown() {
        notifications.cancel(COUNTDOWN_ID)
    }

    private fun countdownNotification(
        deadlineMillis: Long,
        exerciseName: String,
        setLabel: String,
    ): Notification =
        Notification.Builder(context, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle(exerciseName.ifEmpty { "Resting" })
            .setContentText(setLabel)
            // The system owns the ticking. A notification we updated once a second would
            // burn wakeups to redraw a number the platform already knows how to count.
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(deadlineMillis)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Mutable on purpose: [AlarmManager] needs to *send* this one, and an immutable
     * broadcast intent cannot carry the exercise name through to the receiver's own
     * notification. Explicit component, so nothing else can receive it.
     */
    private fun alarmIntent(exerciseName: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RestAlarmReceiver::class.java)
            .setAction(ACTION_REST_COMPLETE)
            .putExtra(EXTRA_EXERCISE_NAME, exerciseName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun ensureChannels() {
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COUNTDOWN,
                "Rest timer",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "The running rest countdown, so you can see it without opening the app."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COMPLETE,
                "Rest finished",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Tells you the rest is up. Vibration only — never a sound in a gym."
                setShowBadge(false)
                // The three long buzzes come from WorkoutHaptics, which can make a pattern
                // the user will recognise through a pocket. A channel vibration would add a
                // second, generic one on top of it.
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    companion object {
        const val CHANNEL_COUNTDOWN = "rest_timer"
        const val CHANNEL_COMPLETE = "rest_complete"
        const val COUNTDOWN_ID = 1001
        const val COMPLETE_ID = 1002
        const val ACTION_REST_COMPLETE = "dev.redplate.action.REST_COMPLETE"
        const val EXTRA_EXERCISE_NAME = "exerciseName"
    }
}
