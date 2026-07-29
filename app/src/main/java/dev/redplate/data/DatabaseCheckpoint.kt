package dev.redplate.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Folds the write-ahead log back into the database file.
 *
 * Room runs SQLite in WAL mode, so recent writes live in `redplate.db-wal` until SQLite
 * chooses to checkpoint. Android Auto Backup only takes `redplate.db` — the sidecars are
 * excluded, because backing up all three from a running app can restore a set SQLite
 * refuses to open. Checkpointing when a session ends means the file that gets backed up
 * contains the session that just happened, rather than everything up to some arbitrary
 * earlier moment.
 *
 * Best effort by design: a failed checkpoint is not worth failing a finished workout over,
 * and the JSON export in Settings is the backup path this project actually relies on.
 */
@Singleton
class DatabaseCheckpoint @Inject constructor(
    private val db: RedplateDatabase,
) {
    fun checkpoint() {
        runCatching {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }
    }
}
