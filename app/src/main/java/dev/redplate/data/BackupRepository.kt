package dev.redplate.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: RedplateDatabase,
) {
    internal val json = Json {
        prettyPrint = true
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Export ───────────────────────────────────────────────────────

    suspend fun export(): String {
        val data = BackupData(
            exportedAt = System.currentTimeMillis(),
            profile = db.profileDao().get(),
            equipment = db.equipmentDao().getAll(),
            exercises = db.exerciseDao().getAll(),
            mesocycles = db.programDao().getAllMesocycles(),
            sessionTemplates = db.programDao().getAllTemplates(),
            templateSlots = db.programDao().getAllSlots(),
            sessions = db.sessionDao().getAllSessions(),
            setLogs = db.sessionDao().getAllSetLogs(),
            volumeSnapshots = db.volumeDao().getAllSnapshots(),
            volumeLandmarks = db.volumeDao().getAllLandmarks(),
        )
        return json.encodeToString(data)
    }

    suspend fun exportToStream(stream: OutputStream) {
        stream.bufferedWriter().use { it.write(export()) }
    }

    suspend fun exportToUri(uri: Uri) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Cannot open output stream")
        exportToStream(stream)
    }

    // ── CSV (lossy, for spreadsheets) ────────────────────────────────

    /**
     * One row per logged set, joined to exercise and session. Lossy on purpose: it drops
     * the program, equipment and volume tables, so it can never be a restore path. Use
     * JSON for that.
     */
    suspend fun exportCsv(): String {
        val sets = db.sessionDao().getAllSetLogs()
        val exerciseNames = db.exerciseDao().getAll().associate { it.id to it.name }
        val sessionStarts = db.sessionDao().getAllSessions().associate { it.id to it.startedAt }

        return buildString {
            appendLine(CSV_HEADER)
            for (set in sets.sortedBy { it.completedAt }) {
                appendLine(
                    listOf(
                        isoDate(set.completedAt),
                        set.sessionId.toString(),
                        sessionStarts[set.sessionId]?.let(::isoDate).orEmpty(),
                        csvEscape(exerciseNames[set.exerciseId] ?: set.exerciseId),
                        (set.setIndex + 1).toString(),
                        set.loadKg.toString(),
                        set.reps.toString(),
                        set.rir?.toString().orEmpty(),
                        if (set.isWarmup) "1" else "0",
                        if (set.countsTowardVolume) "1" else "0",
                        "%.2f".format(set.estimated1Rm()),
                    ).joinToString(",")
                )
            }
        }
    }

    suspend fun exportCsvToUri(uri: Uri) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Cannot open output stream")
        stream.bufferedWriter().use { it.write(exportCsv()) }
    }

    private fun isoDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    /** Exercise names can contain commas; quote and double any embedded quotes. */
    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    // ── Import ──────────────────────────────────────────────────────

    /**
     * Replaces the database with the contents of [jsonString].
     *
     * Atomic by construction: the file is fully parsed *before* anything is touched,
     * and the wipe plus every insert run inside one transaction, so a malformed or
     * truncated backup leaves the existing data exactly as it was. The previous
     * version wiped first and inserted afterwards — a failure halfway through took
     * the training history with it.
     */
    suspend fun import(jsonString: String) {
        val data = try {
            json.decodeFromString<BackupData>(jsonString)
        } catch (e: SerializationException) {
            throw IllegalArgumentException(
                "That file isn't a Redplate backup. Pick the .json file written by Export.",
                e,
            )
        }
        require(data.schemaVersion == BackupData.SCHEMA_VERSION) {
            "This backup is version ${data.schemaVersion}; this build reads version " +
                "${BackupData.SCHEMA_VERSION}. Install the matching build to restore it."
        }

        db.withTransaction {
            // Children before parents, so nothing is orphaned mid-wipe.
            db.sessionDao().deleteAllSetLogs()
            db.sessionDao().deleteAllSessions()
            db.programDao().deleteAllSlots()
            db.programDao().deleteAllTemplates()
            db.programDao().deleteAllMesocycles()
            db.volumeDao().deleteAllSnapshots()
            db.volumeDao().deleteAllLandmarks()
            db.exerciseDao().deleteAll()
            db.equipmentDao().deleteAll()
            db.profileDao().deleteAll()

            db.equipmentDao().insertAll(data.equipment)
            db.exerciseDao().insertAll(data.exercises)
            data.profile?.let { db.profileDao().upsert(it) }
            db.programDao().insertMesocycles(data.mesocycles)
            db.programDao().insertTemplates(data.sessionTemplates)
            db.programDao().insertSlots(data.templateSlots)
            db.sessionDao().insertSessions(data.sessions)
            db.sessionDao().insertSetLogs(data.setLogs)
            db.volumeDao().upsertSnapshots(data.volumeSnapshots)
            db.volumeDao().upsertLandmarks(data.volumeLandmarks)
        }
    }

    suspend fun importFromStream(stream: InputStream) {
        import(stream.bufferedReader().use { it.readText() })
    }

    suspend fun importFromUri(uri: Uri) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open input stream")
        importFromStream(stream)
    }

    // ── Status, for the backup screen ───────────────────────────────

    /**
     * Roughly how large a JSON export would be, without building one.
     *
     * Serialising the whole database just to show a size on a settings screen would be
     * wasteful, so this estimates from row counts against measured per-row sizes. It is
     * only ever rendered as "about 1.4 MB".
     */
    suspend fun approximateJsonBytes(): Long {
        val sets = db.sessionDao().getAllSetLogs().size.toLong()
        val sessions = db.sessionDao().getAllSessions().size.toLong()
        val exercises = db.exerciseDao().count().toLong()
        val equipment = db.equipmentDao().getAll().size.toLong()
        return sets * BYTES_PER_SET +
            sessions * BYTES_PER_SESSION +
            exercises * BYTES_PER_EXERCISE +
            equipment * BYTES_PER_EQUIPMENT
    }

    suspend fun status(): BackupStatus {
        val sessions = db.sessionDao().getAllSessions()
        return BackupStatus(
            sessionCount = sessions.size,
            setCount = db.sessionDao().getAllSetLogs().count { !it.isWarmup },
            lastSessionAt = sessions.maxOfOrNull { it.startedAt },
        )
    }

    private companion object {
        // Measured against pretty-printed output; only used for a rounded display size.
        const val BYTES_PER_SET = 260L
        const val BYTES_PER_SESSION = 220L
        const val BYTES_PER_EXERCISE = 420L
        const val BYTES_PER_EQUIPMENT = 300L

        const val CSV_HEADER =
            "completed_at,session_id,session_started_at,exercise,set_number," +
                "load_kg,reps,rir,is_warmup,counts_toward_volume,estimated_1rm"
    }
}

/** What is actually in the database right now — never a placeholder. */
data class BackupStatus(
    val sessionCount: Int,
    val setCount: Int,
    val lastSessionAt: Long?,
)
