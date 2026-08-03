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

    /**
     * 4 → 5 puts the schedule under the user's control.
     *
     * - `profile.weekStartsOn` — first day of the training week, 0 = Monday. The week was
     *   hardcoded Monday-to-Sunday, so a block begun on a Thursday spent its first week as
     *   four days and the plan grid could never match how the user actually trains.
     * - `mesocycles.beginsAt` — the training day the block is scheduled to start, as epoch
     *   millis. Distinct from `startedAt`, which is when the block was generated. 0 means
     *   "already begun", which is right for every block that exists today.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `weekStartsOn` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `mesocycles` ADD COLUMN `beginsAt` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * 5 → 6 adds `equipment.perLimb` and repairs the exercise-to-equipment mapping.
     *
     * Every barbell lift in the seed named only the station it happens at — a squat listed
     * `power_rack`, a deadlift listed `deadlift_platform` — and both of those are
     * `BODYWEIGHT` fixtures with no bar weight and no plates. So the app resolved the load
     * source to a thing that weighs nothing: no plate stack on squat or bench, and
     * progression stepping in the fixture's 1.25 kg rather than the barbell's 2.5.
     *
     * Each of those lifts now requires the barbell as well as its station. Existing rows
     * are rewritten here; logged sets are untouched, because the loads recorded against
     * them were always real weights.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `equipment` ADD COLUMN `perLimb` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `equipment` SET `perLimb` = 1 WHERE `id` = 'dumbbells'")

            // requiredEquipmentIds is a JSON array of ids, written by Converters.
            fun remap(ids: List<String>, exercises: List<String>) {
                val json = ids.joinToString(",", "[", "]") { "\"$it\"" }
                val list = exercises.joinToString(",") { "'$it'" }
                db.execSQL(
                    "UPDATE `exercises` SET `requiredEquipmentIds` = '$json' WHERE `id` IN ($list)"
                )
            }

            // Barbell lifts in the half rack. Pull-ups, chin-ups and hanging leg raises
            // stay rack-only — those genuinely need nothing but the rack.
            remap(
                listOf("barbell", "power_rack"),
                listOf(
                    "barbell_back_squat", "barbell_front_squat", "barbell_ohp",
                    "barbell_reverse_lunge", "bulgarian_split_squat_bb", "barbell_calf_raise",
                ),
            )
            // Barbell pressing: the bench is the station, not the rack.
            remap(
                listOf("barbell", "flat_incline_bench"),
                listOf("barbell_flat_bench", "barbell_close_grip_bench"),
            )
            remap(
                listOf("barbell", "deadlift_platform"),
                listOf(
                    "conventional_deadlift", "sumo_deadlift", "romanian_deadlift_bb",
                    "barbell_bent_over_row", "power_clean",
                ),
            )
            remap(listOf("barbell", "decline_bench"), listOf("decline_bench_press"))
            remap(
                listOf("dumbbells", "flat_incline_bench"),
                listOf(
                    "db_flat_bench", "db_incline_bench", "db_flat_fly", "db_pullover",
                    "db_single_arm_row", "db_bulgarian_split_squat",
                ),
            )
        }
    }

    /**
     * 6 → 7 splits the 4-station multi-gym into the four stations it actually is, and adds
     * `equipment.isAssistance` so one of them can be read backwards.
     *
     * The frame was modelled as a single piece of equipment, which told the user nothing:
     * "4-Station Multi-Gym" is not somewhere you can walk to. It is a cable station, a low
     * row, a lat pulldown and an assisted dip/chin — four things you queue for separately,
     * each with its own level printed on it.
     *
     * The assisted dip/chin is the reason this needs a column rather than four inserts.
     * Its counterweight *removes* effort: a higher number is easier. Left unmarked, every
     * rule in [ProgressionEngine] would have raised the number on a good session — walking
     * the user toward the machine doing all of the work — and its "tonnage" would have been
     * counted as work performed rather than work avoided.
     *
     * The old frame's `isAvailable` carries over to all four, so a user who had switched it
     * off does not find four new machines switched on. Set logs are untouched: a level
     * recorded against `lat_pulldown_wide` is still that lift's history, and the exercise
     * rows are repointed so the history stays continuous.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `equipment` ADD COLUMN `isAssistance` INTEGER NOT NULL DEFAULT 0")

            // Inherit availability from the frame the four stations came out of. If the row
            // is already gone — a database seeded after the split — default to available.
            fun station(id: String, name: String, assistance: Boolean) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `equipment`
                        (`id`, `displayName`, `category`, `loadingScheme`, `availableLoads`,
                         `barWeightKg`, `platePairs`, `isAvailable`, `perLimb`, `isAssistance`)
                    VALUES ('$id', '$name', 'MACHINE', 'RESISTANCE_LEVEL', '[]',
                            NULL, '{}',
                            COALESCE(
                                (SELECT `isAvailable` FROM `equipment` WHERE `id` = 'four_station_multigym'),
                                1
                            ),
                            0, ${if (assistance) 1 else 0})
                    """.trimIndent()
                )
            }
            station("multigym_cable", "Multi-Gym · Cable", assistance = false)
            station("multigym_low_row", "Multi-Gym · Low Row", assistance = false)
            station("multigym_lat_pulldown", "Multi-Gym · Lat Pulldown", assistance = false)
            station("multigym_assist_dip_chin", "Multi-Gym · Assisted Dip/Chin", assistance = true)

            // Repoint the exercises that named the frame. requiredEquipmentIds is a JSON
            // array written by Converters, so a textual substitution is exact here: the ids
            // are quoted whole and no other id contains this one as a substring.
            db.execSQL(
                """
                UPDATE `exercises`
                SET `requiredEquipmentIds` =
                    REPLACE(`requiredEquipmentIds`, '"four_station_multigym"', '"multigym_lat_pulldown"')
                WHERE `id` IN ('lat_pulldown_wide', 'lat_pulldown_close')
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE `exercises`
                SET `requiredEquipmentIds` =
                    REPLACE(`requiredEquipmentIds`, '"four_station_multigym"', '"multigym_low_row"')
                WHERE `id` = 'seated_cable_row'
                """.trimIndent()
            )
            // Anything else still pointing at the frame goes to the cable station, which is
            // the general-purpose one. Better than leaving an exercise naming equipment that
            // no longer exists, which reads as unavailable and silently disappears.
            db.execSQL(
                """
                UPDATE `exercises`
                SET `requiredEquipmentIds` =
                    REPLACE(`requiredEquipmentIds`, '"four_station_multigym"', '"multigym_cable"')
                WHERE `requiredEquipmentIds` LIKE '%four_station_multigym%'
                """.trimIndent()
            )

            db.execSQL("DELETE FROM `equipment` WHERE `id` = 'four_station_multigym'")
        }
    }

    /**
     * 7 → 8 gives a block somewhere to record what it concluded from the week just
     * trained.
     *
     * [MesocycleAdvancer] now assesses each finished week and rewrites loads from it. A
     * recommendation the user cannot interrogate is the black box this app exists to avoid
     * (CLAUDE.md §5), so the reasoning is stored alongside the change it caused rather than
     * being recomputed later from a plan that has already moved on.
     *
     * Nullable with no default: an existing block has no assessed week behind it, and
     * inventing one would be worse than saying nothing.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `mesocycles` ADD COLUMN `assessmentNote` TEXT")
        }
    }

    /**
     * 8 → 9 corrects exercises whose name, machine or picture described three different
     * movements.
     *
     * The seed was assembled by pattern-matching names against a floor plan, and nothing
     * ever checked that the three agreed. "Standing Cable Row" was illustrated with a
     * *seated* one-arm row on a low-row machine; "Wide-Stance Leg Press" with a
     * narrow-stance one; "Incline Barbell Bench Press" was assigned to a plate-loaded
     * chest press machine you cannot put a barbell on. A user copies the picture, so a
     * picture that contradicts the name is worse than no picture at all.
     *
     * Names and equipment are rewritten here. Set logs are untouched: the ids do not
     * change, so every rep already logged still belongs to the lift it was logged under.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO `equipment`
                    (`id`, `displayName`, `category`, `loadingScheme`, `availableLoads`,
                     `barWeightKg`, `platePairs`, `isAvailable`, `perLimb`, `isAssistance`)
                VALUES ('landmine_attachment', 'Landmine Attachment', 'BARBELL', 'BODYWEIGHT',
                        '[]', NULL, '{}', 0, 0, 0)
                """.trimIndent()
            )

            fun rename(id: String, name: String) =
                db.execSQL("UPDATE `exercises` SET `name` = '${name.replace("'", "''")}' WHERE `id` = '$id'")

            fun requires(id: String, equipmentIds: List<String>) {
                val json = equipmentIds.joinToString(",", "[", "]") { "\"$it\"" }
                db.execSQL("UPDATE `exercises` SET `requiredEquipmentIds` = '$json' WHERE `id` = '$id'")
            }

            // Name did not match the movement in the bundled still.
            rename("cable_lateral_raise", "Seated Cable Lateral Raise")
            rename("smith_hack_squat", "Smith Machine Squat")
            rename("barbell_reverse_lunge", "Barbell Lunge")
            rename("decline_sit_up", "Decline Crunch")
            // Neither still shows a rear foot elevated, so neither is a Bulgarian.
            rename("bulgarian_split_squat_bb", "Barbell Split Squat")
            rename("db_bulgarian_split_squat", "Dumbbell Split Squat")
            // #15 is a plate-loaded machine. A barbell does not go on it.
            rename("incline_barbell_bench", "Incline Chest Press (Machine)")
            rename("bench_step_up", "Dumbbell Step-Up")
            rename("db_rear_delt_fly", "Seated Bent-Over Rear Delt Raise")

            // Equipment the lift actually needs and never named.
            requires("bench_step_up", listOf("dumbbells", "flat_incline_bench"))
            requires("barbell_skullcrusher", listOf("barbell", "flat_incline_bench"))
            requires("db_rear_delt_fly", listOf("dumbbells", "flat_incline_bench"))
            // A split squat does not elevate the rear foot, so it needs no bench.
            requires("db_bulgarian_split_squat", listOf("dumbbells"))
            // Landmine work needs a landmine, which this gym has not confirmed owning.
            requires("landmine_press", listOf("barbell", "landmine_attachment"))
            requires("landmine_row", listOf("barbell", "landmine_attachment"))

            // Hip abduction is glute medius. The quads do nothing.
            db.execSQL("UPDATE `exercises` SET `secondaryMuscles` = '[]' WHERE `id` = 'machine_hip_abduction'")
        }
    }

    /**
     * 9 → 10 drops three lifts that were doing another lift's job.
     *
     * - `cable_row_standing` — the gym has a dedicated Low Row station, which is what
     *   `seated_cable_row` is on. A standing row at the dual pulley is the same movement
     *   done worse, and its picture was of the low row anyway.
     * - `assisted_pull_up` — same station, same pattern, same muscles as
     *   `assisted_chin_up`. The machine is labelled "dip/chin"; one entry is enough.
     * - `bulgarian_split_squat_bb` — once its still turned out to be a *side* split squat,
     *   the exercise became "Barbell Split Squat" on the same rack and barbell as
     *   "Barbell Lunge", with no picture of its own to tell them apart.
     *
     * Removal is conditional, and that is the point. A lift with sets logged against it, or
     * one sitting in somebody's plan, is not deleted — it is marked excluded, so it leaves
     * the pool but every rep already recorded still resolves to a name. Deleting it would
     * turn training history into orphaned rows, which is the one unrecoverable failure in
     * this project.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val removed = listOf("cable_row_standing", "assisted_pull_up", "bulgarian_split_squat_bb")
            val list = removed.joinToString(",") { "'$it'" }

            // Out of the generator's pool and out of the picker either way.
            db.execSQL("UPDATE `exercises` SET `isExcluded` = 1 WHERE `id` IN ($list)")

            // Gone entirely only where nothing points at it.
            db.execSQL(
                """
                DELETE FROM `exercises`
                WHERE `id` IN ($list)
                  AND `id` NOT IN (SELECT DISTINCT `exerciseId` FROM `set_logs`)
                  AND `id` NOT IN (SELECT DISTINCT `exerciseId` FROM `template_slots`)
                """.trimIndent()
            )
        }
    }

    /**
     * 10 → 11 puts kilograms back on the low row and lat pulldown, and drops everything
     * that hung from the half rack's bar.
     *
     * **The stacks.** Those two stations turned out to be printed in kilograms after all —
     * 5, 12.5, 20 … 130, photographed off the selector plates and transcribed into
     * [GymEquipmentSeed.MULTIGYM_STACK_KG]. Migration 3 → 4 stripped the multi-gym of a
     * *made-up* 5–100 kg ladder, which was right at the time: nothing had read the machine.
     * This is the opposite situation — the numbers are the ones on the plates, so the
     * station goes back to `PIN_STACK` and the readout says `50 KG` because that is what
     * the label under the pin says. The cable and assisted dip/chin stations are untouched;
     * they really are numbered levels with no mass on them.
     *
     * **The history.** This one *does* rewrite logged loads, and only because it is a unit
     * conversion rather than a correction. A "level" recorded against these stations was
     * always a pin position — the user counted plates down the stack, because there was
     * nothing else to count — and pin position 7 has now been measured at 50 kg. Leaving it
     * would not preserve history, it would reinterpret "the 7th plate" as "7 kilograms",
     * and the progression engine would open the next pulldown session at 12.5 kg.
     *
     * Only whole numbers within the stack's 15 positions convert. Anything else — a
     * fractional value, or a figure above 15 — cannot be a pin position on this machine and
     * is left exactly as logged, which also protects any load that predates 3 → 4 and is
     * already a genuine kilogram figure.
     *
     * **The bar that is not there.** The half rack has no pull-up bar on it. `chin_up`,
     * `pull_up` and `hanging_leg_raise` all named the rack and all needed that one bar, so
     * all three go: a lift the gym cannot perform is worse than a missing one, because the
     * generator will keep prescribing it. `assisted_chin_up` and `assisted_dip` are
     * unaffected — the multi-gym's dip/chin station exists. Removal follows 9 → 10:
     * excluded always, deleted only where no set log or template slot points at it, so no
     * rep already recorded is orphaned.
     *
     * No table, column or index changes here.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val stations = listOf("multigym_low_row", "multigym_lat_pulldown")
            val stack = GymEquipmentSeed.MULTIGYM_STACK_KG
            val loadsJson = stack.joinToString(",", "[", "]") { it.toString() }

            for (id in stations) {
                db.execSQL(
                    """
                    UPDATE `equipment`
                    SET `loadingScheme` = 'PIN_STACK', `availableLoads` = '$loadsJson'
                    WHERE `id` = '$id'
                    """.trimIndent()
                )
            }

            // Pin position n (1-based) is the nth number printed on the stack.
            val positionToKg = stack.withIndex().joinToString(" ") { (index, kg) ->
                "WHEN ${index + 1} THEN $kg"
            }
            val onTheseStations = listOf("lat_pulldown_wide", "lat_pulldown_close", "seated_cable_row")
            val exerciseList = onTheseStations.joinToString(",") { "'$it'" }

            fun convert(table: String, column: String, keyColumn: String) {
                db.execSQL(
                    """
                    UPDATE `$table`
                    SET `$column` = CASE CAST(`$column` AS INTEGER) $positionToKg ELSE `$column` END
                    WHERE `$keyColumn` IN ($exerciseList)
                      AND `$column` = CAST(`$column` AS INTEGER)
                      AND `$column` BETWEEN 1 AND ${stack.size}
                    """.trimIndent()
                )
            }
            convert("set_logs", "loadKg", "exerciseId")
            convert("template_slots", "workingLoadKg", "exerciseId")

            // Everything that needed the pull-up bar the half rack does not have.
            val offTheBar = listOf("chin_up", "pull_up", "hanging_leg_raise")
            val barList = offTheBar.joinToString(",") { "'$it'" }

            db.execSQL("UPDATE `exercises` SET `isExcluded` = 1 WHERE `id` IN ($barList)")
            db.execSQL(
                """
                DELETE FROM `exercises`
                WHERE `id` IN ($barList)
                  AND `id` NOT IN (SELECT DISTINCT `exerciseId` FROM `set_logs`)
                  AND `id` NOT IN (SELECT DISTINCT `exerciseId` FROM `template_slots`)
                """.trimIndent()
            )
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
    )
}
