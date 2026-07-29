package dev.redplate

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.redplate.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var db: RedplateDatabase
    private lateinit var repo: BackupRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, RedplateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BackupRepository(ctx, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ────────────────────────────────────────────────────

    private val profile = ProfileEntity(
        trainingAgeMonths = 24,
        daysPerWeek = 4,
        sessionCeilingMinutes = 75,
        goal = Goal.HYPERTROPHY,
        bodyweightKg = 82.5,
        priorityMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.LATS),
        excludedPatterns = listOf(MovementPattern.LUNGE),
        readinessFlagged = true,
        useMetric = true,
    )

    private val equipment = listOf(
        EquipmentEntity(
            id = "barbell_olympic",
            displayName = "Olympic Barbell",
            category = EquipmentCategory.BARBELL,
            loadingScheme = LoadingScheme.PLATE_LOADED,
            barWeightKg = 20.0,
            platePairs = mapOf(25.0 to 1, 20.0 to 1, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2),
        ),
        EquipmentEntity(
            id = "bodyweight",
            displayName = "Bodyweight",
            category = EquipmentCategory.BODYWEIGHT,
            loadingScheme = LoadingScheme.BODYWEIGHT,
        ),
        EquipmentEntity(
            id = "dumbbell_set",
            displayName = "Dumbbells",
            category = EquipmentCategory.DUMBBELL,
            loadingScheme = LoadingScheme.FIXED_INCREMENT,
            availableLoads = listOf(5.0, 7.5, 10.0, 12.5, 15.0, 20.0, 25.0, 30.0),
        ),
    )

    private val exercises = listOf(
        ExerciseEntity(
            id = "bench_press",
            name = "Bench Press",
            pattern = MovementPattern.HORIZONTAL_PUSH,
            primaryMuscle = MuscleGroup.CHEST,
            secondaryMuscles = listOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTS),
            requiredEquipmentIds = listOf("barbell_olympic"),
            complexity = Complexity.INTERMEDIATE,
            fatigueCost = 4,
            isCompound = true,
            instructions = "Lie on bench.\nUnrack the bar.\nLower to chest.\nPress up.",
            imageAssetPaths = listOf("bench_press/0.jpg", "bench_press/1.jpg"),
        ),
        ExerciseEntity(
            id = "pull_up",
            name = "Pull-Up",
            pattern = MovementPattern.VERTICAL_PULL,
            primaryMuscle = MuscleGroup.LATS,
            secondaryMuscles = listOf(MuscleGroup.BICEPS),
            requiredEquipmentIds = listOf("bodyweight"),
            complexity = Complexity.BEGINNER,
            fatigueCost = 3,
            isCompound = true,
            isCustom = true,
        ),
        ExerciseEntity(
            id = "excluded_exercise",
            name = "Behind-the-Neck Press",
            pattern = MovementPattern.VERTICAL_PUSH,
            primaryMuscle = MuscleGroup.FRONT_DELTS,
            complexity = Complexity.ADVANCED,
            fatigueCost = 3,
            isExcluded = true,
        ),
    )

    private fun mesocycle() = MesocycleEntity(
        id = 1,
        name = "Hypertrophy Block 1",
        goal = Goal.HYPERTROPHY,
        startedAt = 1_700_000_000_000L,
        lengthWeeks = 5,
        currentWeek = 3,
        isActive = true,
    )

    private fun templates() = listOf(
        SessionTemplateEntity(id = 1, mesocycleId = 1, label = "Upper A", dayIndex = 0),
        SessionTemplateEntity(id = 2, mesocycleId = 1, label = "Lower A", dayIndex = 1),
    )

    private fun slots() = listOf(
        TemplateSlotEntity(
            id = 1, templateId = 1, exerciseId = "bench_press",
            orderIndex = 0, targetSets = 4, repRangeLow = 6, repRangeHigh = 10,
            targetRir = 2, restSeconds = 180,
            progression = ProgressionRule.DOUBLE_PROGRESSION,
            workingLoadKg = 80.0, supersetGroup = null,
        ),
        TemplateSlotEntity(
            id = 2, templateId = 1, exerciseId = "pull_up",
            orderIndex = 1, targetSets = 3, repRangeLow = 8, repRangeHigh = 12,
            targetRir = 2, restSeconds = 120,
            progression = ProgressionRule.RIR_AUTOREGULATED,
            workingLoadKg = null, supersetGroup = 1,
        ),
    )

    private fun session() = SessionEntity(
        id = 1,
        templateId = 1,
        mesocycleId = 1,
        weekNumber = 3,
        phase = BlockPhase.ACCUMULATION,
        startedAt = 1_700_100_000_000L,
        endedAt = 1_700_104_500_000L,
        bodyweightKg = 82.3,
        notes = "Felt strong today",
    )

    private fun setLogs() = listOf(
        SetLogEntity(
            id = 1, sessionId = 1, exerciseId = "bench_press", setIndex = 0,
            loadKg = 80.0, reps = 8, rir = 2, completedAt = 1_700_100_300_000L,
            restTakenSeconds = 180,
        ),
        SetLogEntity(
            id = 2, sessionId = 1, exerciseId = "bench_press", setIndex = 1,
            loadKg = 80.0, reps = 7, rir = 1, completedAt = 1_700_100_600_000L,
            restTakenSeconds = 180,
        ),
        SetLogEntity(
            id = 3, sessionId = 1, exerciseId = "bench_press", setIndex = 2,
            loadKg = 60.0, reps = 5, rir = null, isWarmup = true,
            completedAt = 1_700_100_100_000L,
        ),
    )

    private val snapshots = listOf(
        VolumeSnapshotEntity(
            mesocycleId = 1, weekNumber = 1, muscle = MuscleGroup.CHEST,
            hardSets = 12.5, mev = 8, mav = 14, mrv = 20,
        ),
        VolumeSnapshotEntity(
            mesocycleId = 1, weekNumber = 2, muscle = MuscleGroup.CHEST,
            hardSets = 14.0, mev = 8, mav = 14, mrv = 20,
        ),
    )

    private val landmarks = listOf(
        VolumeLandmarkEntity(
            muscle = MuscleGroup.CHEST,
            mv = 6, mev = 8, mavLow = 12, mavHigh = 16, mrv = 20,
            userAdjusted = true,
        ),
        VolumeLandmarkEntity(
            muscle = MuscleGroup.LATS,
            mv = 6, mev = 10, mavLow = 14, mavHigh = 18, mrv = 22,
        ),
    )

    // ── Helpers ─────────────────────────────────────────────────────

    private suspend fun seedAll() {
        db.profileDao().upsert(profile)
        db.equipmentDao().insertAll(equipment)
        db.exerciseDao().insertAll(exercises)
        db.programDao().insertMesocycle(mesocycle())
        db.programDao().insertTemplates(templates())
        db.programDao().insertSlots(slots())
        db.sessionDao().insertSession(session())
        db.sessionDao().insertSetLogs(setLogs())
        db.volumeDao().upsertSnapshots(snapshots)
        db.volumeDao().upsertLandmarks(landmarks)
    }

    // ── Tests ───────────────────────────────────────────────────────

    @Test
    fun fullRoundTrip() = runBlocking {
        seedAll()

        val exported = repo.export()

        db.clearAllTables()
        assertEquals(0, db.exerciseDao().count())

        repo.import(exported)

        val p = db.profileDao().get()
        assertNotNull(p)
        assertEquals(profile, p)

        val eq = db.equipmentDao().getAll()
        assertEquals(equipment.sortedBy { it.displayName }, eq)

        val ex = db.exerciseDao().getAll()
        assertEquals(exercises.sortedBy { it.name }, ex)

        val meso = db.programDao().getAllMesocycles()
        assertEquals(listOf(mesocycle()), meso)

        val tmpl = db.programDao().getAllTemplates()
        assertEquals(templates(), tmpl)

        val sl = db.programDao().getAllSlots()
        assertEquals(slots(), sl)

        val sess = db.sessionDao().getAllSessions()
        assertEquals(listOf(session()), sess)

        val logs = db.sessionDao().getAllSetLogs()
        assertEquals(setLogs(), logs)

        val snaps = db.volumeDao().getAllSnapshots()
        assertEquals(snapshots, snaps)

        val marks = db.volumeDao().getAllLandmarks()
        assertEquals(landmarks.sortedBy { it.muscle.name }, marks)
    }

    @Test
    fun emptyDatabaseRoundTrip() = runBlocking {
        val exported = repo.export()
        repo.import(exported)

        assertEquals(0, db.exerciseDao().count())
        assertEquals(null, db.profileDao().get())
        assertEquals(emptyList<EquipmentEntity>(), db.equipmentDao().getAll())
    }

    @Test
    fun excludedExerciseSurvivesRoundTrip() = runBlocking {
        db.exerciseDao().insertAll(exercises)

        val exported = repo.export()
        db.clearAllTables()
        repo.import(exported)

        val restored = db.exerciseDao().getById("excluded_exercise")
        assertNotNull(restored)
        assertEquals(true, restored!!.isExcluded)
    }

    @Test
    fun nullableFieldsSurviveRoundTrip() = runBlocking {
        val freestyleSession = SessionEntity(
            id = 1,
            templateId = null,
            mesocycleId = null,
            weekNumber = null,
            startedAt = 1_700_000_000_000L,
            endedAt = null,
            bodyweightKg = null,
            notes = null,
        )
        db.sessionDao().insertSession(freestyleSession)

        val exported = repo.export()
        db.clearAllTables()
        repo.import(exported)

        val restored = db.sessionDao().getSessionById(1)
        assertEquals(freestyleSession, restored)
    }

    @Test
    fun warmupSetCountsTowardVolumePreserved() = runBlocking {
        db.equipmentDao().insertAll(equipment)
        db.exerciseDao().insertAll(exercises)
        val session = SessionEntity(
            id = 1, templateId = null, mesocycleId = null,
            weekNumber = null, startedAt = 1_700_000_000_000L,
        )
        db.sessionDao().insertSession(session)

        val warmupSet = SetLogEntity(
            id = 1, sessionId = 1, exerciseId = "bench_press", setIndex = 0,
            loadKg = 40.0, reps = 10, rir = null, isWarmup = true,
            completedAt = 1_700_000_100_000L,
        )
        val workSet = SetLogEntity(
            id = 2, sessionId = 1, exerciseId = "bench_press", setIndex = 1,
            loadKg = 80.0, reps = 8, rir = 2, isWarmup = false,
            completedAt = 1_700_000_300_000L,
        )
        db.sessionDao().insertSetLogs(listOf(warmupSet, workSet))

        val exported = repo.export()
        db.clearAllTables()
        repo.import(exported)

        val restored = db.sessionDao().getAllSetLogs()
        assertEquals(false, restored[0].countsTowardVolume)
        assertEquals(true, restored[1].countsTowardVolume)
    }

    @Test
    fun jsonIsDeserializableFromString() = runBlocking {
        seedAll()
        val exported = repo.export()
        val parsed = repo.json.decodeFromString<BackupData>(exported)

        assertEquals(1, parsed.schemaVersion)
        assertEquals(3, parsed.exercises.size)
        assertEquals(3, parsed.equipment.size)
        assertNotNull(parsed.profile)
        assertEquals(1, parsed.mesocycles.size)
        assertEquals(2, parsed.sessionTemplates.size)
        assertEquals(2, parsed.templateSlots.size)
        assertEquals(1, parsed.sessions.size)
        assertEquals(3, parsed.setLogs.size)
        assertEquals(2, parsed.volumeSnapshots.size)
        assertEquals(2, parsed.volumeLandmarks.size)
    }

    @Test
    fun doubleImportIsIdempotent() = runBlocking {
        seedAll()
        val exported = repo.export()

        repo.import(exported)
        repo.import(exported)

        assertEquals(3, db.exerciseDao().getAll().size)
        assertEquals(3, db.equipmentDao().getAll().size)
        assertEquals(1, db.programDao().getAllMesocycles().size)
    }

    /**
     * Data loss is the only unrecoverable failure in this project, and a bad restore is
     * the likeliest way to cause one. Import used to wipe the database before parsing
     * the file, so pointing it at the wrong document destroyed the training log and put
     * nothing back. Nothing may be touched unless the whole file is known good.
     */
    @Test
    fun failedImportLeavesExistingDataIntact() = runBlocking {
        seedAll()
        val before = db.sessionDao().getAllSetLogs()

        val garbage = "{ this is not a backup"
        try {
            repo.import(garbage)
            fail("Import of a non-backup file should not succeed")
        } catch (expected: IllegalArgumentException) {
            // Expected — the file never parses, so the database is never touched.
        }

        assertEquals(before, db.sessionDao().getAllSetLogs())
        assertEquals(profile, db.profileDao().get())
        assertEquals(3, db.exerciseDao().count())
    }

    @Test
    fun importOfAnUnsupportedSchemaVersionIsRejectedBeforeAnyWrite() = runBlocking {
        seedAll()
        // Well-formed JSON, readable shape, version this build does not know.
        val fromTheFuture = """{"schemaVersion":99,"exportedAt":1700000000000}"""

        try {
            repo.import(fromTheFuture)
            fail("Import of an unsupported schema version should not succeed")
        } catch (expected: IllegalArgumentException) {
            // Expected — the version check runs before the transaction opens.
        }

        assertEquals(3, db.exerciseDao().count())
        assertEquals(profile, db.profileDao().get())
    }

    /**
     * Overwriting a larger backup with a smaller one.
     *
     * `openOutputStream(uri)` defaults to mode "w", which on many document providers does
     * not truncate — so the old file's tail stayed behind the new content and the result
     * was JSON that would not parse. A backup that silently stops being restorable is the
     * worst failure this project has, so it is checked end to end through a real Uri.
     */
    @Test
    fun overwritingALargerBackupProducesAFileThatStillImports() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = File(ctx.cacheDir, "overwrite-test.json")
        val uri = Uri.fromFile(file)

        // A large export first: everything seeded, plus enough sets to make the file big.
        seedAll()
        db.sessionDao().insertSetLogs(
            (0 until 500).map { index ->
                SetLogEntity(
                    id = 1000L + index,
                    sessionId = 1,
                    exerciseId = "bench_press",
                    setIndex = index,
                    loadKg = 60.0 + index,
                    reps = 8,
                    rir = 2,
                    completedAt = 1_700_000_000_000L + index,
                )
            }
        )
        repo.exportToUri(uri)
        val largeLength = file.length()

        // Then a much smaller one over the top of it.
        db.clearAllTables()
        db.exerciseDao().insertAll(exercises)
        repo.exportToUri(uri)

        assertTrue(
            "The second export ($file, ${file.length()} bytes) should be smaller " +
                "than the first ($largeLength bytes) — otherwise this proves nothing",
            file.length() < largeLength,
        )

        // The real assertion: no trailing bytes, so it is still a parseable backup.
        val parsed = repo.json.decodeFromString<BackupData>(file.readText())
        assertEquals(3, parsed.exercises.size)
        assertEquals(0, parsed.setLogs.size)

        db.clearAllTables()
        repo.importFromUri(uri)

        assertEquals(3, db.exerciseDao().count())
        assertEquals(0, db.sessionDao().getAllSetLogs().size)

        file.delete()
    }
