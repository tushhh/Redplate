package dev.redplate

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.redplate.data.DatabaseSeeder
import dev.redplate.data.GymEquipmentSeed
import dev.redplate.data.RedplateDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val equipmentAfterSecond = db.equipmentDao().getAll()
        val exerciseCountAfterSecond = db.exerciseDao().count()

        assertEquals(equipmentAfterFirst.size, equipmentAfterSecond.size)
        assertEquals(exerciseCountAfterFirst, exerciseCountAfterSecond)
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
    fun unmappedEquipmentStringsExcludeTheExercise() = runBlocking {
        seeder.seedIfNeeded()

        val exercises = db.exerciseDao().getAll()
        // Every seeded exercise's required equipment ids must resolve to real,
        // currently-known equipment — fail closed, never a dangling reference.
        val knownIds = db.equipmentDao().getAll().map { it.id }.toSet()
        for (exercise in exercises) {
            for (requiredId in exercise.requiredEquipmentIds) {
                assertTrue(
                    "Exercise ${exercise.id} references unknown equipment $requiredId",
                    requiredId in knownIds,
                )
            }
        }

        // At least one exercise fell into a deliberately-unmapped bucket (e.g. "machine")
        // and was excluded rather than guessed.
        assertTrue(exercises.any { it.isExcluded })
    }

    @Test
    fun resolveEquipmentDistinguishesGenuineBodyweightFromUnmappedGear() {
        // "body only" (and a missing field, which free-exercise-db uses for the same
        // thing) genuinely needs no equipment — not excluded, empty id list.
        val bodyweight = DatabaseSeeder.resolveEquipment("body only")
        assertFalse(bodyweight.excluded)
        assertTrue(bodyweight.equipmentIds.isEmpty())

        val nullEquipment = DatabaseSeeder.resolveEquipment(null)
        assertFalse(nullEquipment.excluded)
        assertTrue(nullEquipment.equipmentIds.isEmpty())

        // A known type resolves to real local equipment ids.
        val barbell = DatabaseSeeder.resolveEquipment("Barbell")
        assertFalse(barbell.excluded)
        assertEquals(listOf("barbell"), barbell.equipmentIds)

        // "machine" is too coarse to trust — excluded, not guessed, per GYM.md.
        val machine = DatabaseSeeder.resolveEquipment("machine")
        assertTrue(machine.excluded)
        assertTrue(machine.equipmentIds.isEmpty())

        val other = DatabaseSeeder.resolveEquipment("other")
        assertTrue(other.excluded)
    }
}
