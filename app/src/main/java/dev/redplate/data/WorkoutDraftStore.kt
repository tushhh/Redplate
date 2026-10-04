package dev.redplate.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What is on the set screen but not yet logged: the weight dialled in, the reps, the chip,
 * the coach's adjustment, and a running rest.
 *
 * Logged sets were always safe in Room. Everything *between* sets lived only in a
 * ViewModel, so pressing Back — or Android reclaiming the app while the phone sat in a
 * pocket — put the weight back to the plan's number, the reps back to the top of the
 * range, and the rest timer back to nothing. That is the "starts again from square one"
 * a user meets mid-workout.
 */
@Serializable
data class WorkoutDraft(
    val loadKg: Double,
    val reps: Int,
    val difficulty: String? = null,
    val rir: Int? = null,
    /** How many sets of this lift were logged when the draft was written. */
    val loggedCount: Int,
    val adjustmentNote: String? = null,
    /** Wall-clock end of a running rest, or 0 when none is running. */
    val restDeadlineMillis: Long = 0L,
    val restTotalSeconds: Int = 0,
)

/**
 * One draft per (session, exercise), in plain SharedPreferences.
 *
 * Not Room on purpose: this is scratch state for a screen, written on every stepper tap,
 * and it is thrown away when the session ends. It never belongs in a backup.
 */
@Singleton
class WorkoutDraftStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(sessionId: Long, exerciseId: String): WorkoutDraft? =
        prefs.getString(key(sessionId, exerciseId), null)
            ?.let { runCatching { json.decodeFromString<WorkoutDraft>(it) }.getOrNull() }

    fun save(sessionId: Long, exerciseId: String, draft: WorkoutDraft) {
        prefs.edit().putString(key(sessionId, exerciseId), json.encodeToString(WorkoutDraft.serializer(), draft)).apply()
    }

    /** Called when a session ends: nothing about it is in progress any more. */
    fun clearSession(sessionId: Long) {
        val prefix = "$KEY_PREFIX$sessionId$SEPARATOR"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    private fun key(sessionId: Long, exerciseId: String) = "$KEY_PREFIX$sessionId$SEPARATOR$exerciseId"

    private companion object {
        const val PREFS = "workout_drafts"
        const val KEY_PREFIX = "draft_"
        const val SEPARATOR = "__"
    }
}
