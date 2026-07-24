package dev.redplate.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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

    // ── Import ──────────────────────────────────────────────────────

    suspend fun import(jsonString: String) {
        val data = json.decodeFromString<BackupData>(jsonString)
        require(data.schemaVersion == 1) {
            "Unsupported backup version ${data.schemaVersion}"
        }

        db.clearAllTables()

        db.withTransaction {
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
}
