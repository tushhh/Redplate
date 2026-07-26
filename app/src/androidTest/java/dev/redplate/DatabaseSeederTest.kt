package dev.redplate

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.redplate.data.DatabaseSeeder
import dev.redplate.data.ExerciseMediaMap
import dev.redplate.data.GymEquipmentSeed
import dev.redplate.data.RedplateDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSeederTest {

    private lateinit var db: RedplateDatabase
    private lateinit var seeder: DatabaseSeeder

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, RedplateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seeder = DatabaseSeeder(ctx, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedIfNeededPopulatesGymEquipmentSeed() = runBlocking {
        seeder.seedIfNeeded()

        val equipment = db.equipmentDao().getAll()
        assertEquals(GymEquipmentSeed.seed().size, equipment.size)
        assertTrue(equipment.any { it.id == "barbell" })

        // Fail closed: kit whose contents are unconfirmed is seeded unavailable so the
        // exercise filter never suggests something the user cannot actually load.
        assertTrue(equipment.any { it.id == "rox_kettlebells" && !it.isAvailable })

        assertTrue(db.exerciseDao().count() > 0)
    }

    @Test
    fun seedIfNeededIsIdempotent() = runBlocking {
        seeder.seedIfNeeded()

        val equipmentAfterFirst = db.equipmentDao().getAll()
        val exerciseCountAfterFirst = db.exerciseDao().count()

        // Second call must be a no-op: exerciseDao().count() > 0 short-circuits it.
        seeder.seedIfNeeded()

        assertEquals(equipmentAfterFirst.size, db.equipmentDao().getAll().size)
        assertEquals(exerciseCountAfterFirst, db.exerciseDao().count())
    }

    @Test
    fun equipmentInsertAllIsUpsertSafeEvenOutsideTheGuard() = runBlocking {
        // Defence in depth: even if something calls insertAll directly a second time
        // (bypassing seedIfNeeded's count() > 0 guard), REPLACE-on-conflict means the
        // same ids overwrite rather than duplicate.
        val equipment = GymEquipmentSeed.seed()
        db.equipmentDao().insertAll(equipment)
        db.equipmentDao().insertAll(equipment)

        assertEquals(equipment.size, db.equipmentDao().getAll().size)
    }

    @Test
    fun everySeededExerciseReferencesRealEquipment() = runBlocking {
        seeder.seedIfNeeded()

        val knownIds = db.equipmentDao().getAll().map { it.id }.toSet()
        for (exercise in db.exerciseDao().getAll()) {
            for (requiredId in exercise.requiredEquipmentIds) {
                assertTrue(
                    "Exercise ${exercise.id} references unknown equipment $requiredId",
                    requiredId in knownIds,
                )
            }
        }
    }

    /**
     * The media map is keyed by exercise id, and a typo there is invisible at runtime —
     * the image simply never appears, which is exactly the failure that left 1746
     * unusable images in the APK. This catches a stale key the moment it appears.
     */
    @Test
    fun everyMediaMapKeyMatchesASeededExercise() = runBlocking {
        seeder.seedIfNeeded()

        val exerciseIds = db.exerciseDao().getAll().map { it.id }.toSet()
        val orphans = ExerciseMediaMap.ASSET_STEMS.keys - exerciseIds
        assertTrue("Media map keys with no matching exercise: $orphans", orphans.isEmpty())
    }

    /** Every stem the map points at must exist as a file, or the lookup silently fails. */
    @Test
    fun everyMappedAssetExistsOnDisk() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val files = ctx.assets.list("exercises")?.toSet().orEmpty()

        val missing = ExerciseMediaMap.ASSET_STEMS.values
            .flatMap { listOf("${it}_start.jpg", "${it}_end.jpg") }
            .filterNot { it in files }

        assertTrue("Mapped assets missing from assets/exercises: $missing", missing.isEmpty())
    }
}
