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

    /**
     * 2 → 3 adds the schedule and day-boundary settings to the profile, and gives volume
     * snapshots the foreign key they always should have had.
     *
     * - `profile.trainingDays` — which weekdays the user actually trains, overriding the
     *   split's own layout. Nullable, so an existing row keeps the split default.
     * - `profile.dayStartHour` — when a training day begins, so a session logged at 02:00
     *   counts against the previous day. See [TrainingClock].
     * - `volume_snapshots.mesocycleId` gains `REFERENCES mesocycles(id) ON DELETE CASCADE`.
     *   SQLite cannot add a constraint in place, so the table is rebuilt and copied.
     *   Rows whose mesocycle is already gone are orphans by definition and would violate
     *   the new constraint, so they are not carried over. No history is lost: every
     *   snapshot is derived from set logs, and set logs are untouched here.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `trainingDays` TEXT")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `dayStartHour` INTEGER NOT NULL DEFAULT 4")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `volume_snapshots_new` (
                    `mesocycleId` INTEGER NOT NULL,
                    `weekNumber` INTEGER NOT NULL,
                    `muscle` TEXT NOT NULL,
                    `hardSets` REAL NOT NULL,
                    `mev` INTEGER NOT NULL,
                    `mav` INTEGER NOT NULL,
                    `mrv` INTEGER NOT NULL,
                    PRIMARY KEY(`mesocycleId`, `weekNumber`, `muscle`),
                    FOREIGN KEY(`mesocycleId`) REFERENCES `mesocycles`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `volume_snapshots_new`
                    (`mesocycleId`, `weekNumber`, `muscle`, `hardSets`, `mev`, `mav`, `mrv`)
                SELECT `mesocycleId`, `weekNumber`, `muscle`, `hardSets`, `mev`, `mav`, `mrv`
                FROM `volume_snapshots`
                WHERE `mesocycleId` IN (SELECT `id` FROM `mesocycles`)
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `volume_snapshots`")
            db.execSQL("ALTER TABLE `volume_snapshots_new` RENAME TO `volume_snapshots`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_volume_snapshots_mesocycleId` " +
                    "ON `volume_snapshots` (`mesocycleId`)"
            )
        }
    }

    /**
     * 3 → 4 changes data, not structure: the 4-station multi-gym becomes a
     * `RESISTANCE_LEVEL` machine and loses its invented weight ladder.
     *
     * The seed shipped it as a pin stack with `availableLoads` generated from 5 to 100 kg
     * in 2.5 kg steps, under a comment admitting the figures were an assumption. The
     * machine is marked in numbered levels and prints no mass at all, so the app was
     * showing a kilogram number that exists nowhere in the gym and snapping the user onto
     * it. Clearing the ladder is what lets the level actually be recorded.
     *
     * Logged sets are untouched. A set recorded as "37.5" under the old ladder stays 37.5;
     * it is history, and rewriting history to fit a corrected model is how a training log
     * stops being trustworthy. New sets record levels.
     *
     * No table, column or index changes, so the exported schema for 4 is identical to 3
     * apart from the version — the same situation as 1 → 2.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                UPDATE `equipment`
                SET `loadingScheme` = 'RESISTANCE_LEVEL', `availableLoads` = '[]'
                WHERE `id` = 'four_station_multigym'
                """.trimIndent()
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
