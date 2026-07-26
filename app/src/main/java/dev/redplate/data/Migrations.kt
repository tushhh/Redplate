package dev.redplate.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real migrations, never a destructive fallback. CLAUDE.md §6:
 * "Never destructive-migrate Room in a build the user has installed."
 *
 * Losing training history is the one unrecoverable failure in this project —
 * a dropped table cannot be reconstructed from anything the app still holds.
 */
object Migrations {

    /**
     * 1 → 2 is structurally empty. Both exported schemas carry the identity hash
     * `8f96adf491a87bdfb059b461c1c49aae`: the version was bumped without any column,
     * table or index changing. Room still needs the migration to exist so it can
     * stamp the new version instead of refusing to open the database.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No schema change between these versions. Intentionally empty.
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
