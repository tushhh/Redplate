package dev.redplate

import dev.redplate.data.Complexity
import dev.redplate.data.EquipmentAvailability
import dev.redplate.data.EquipmentCategory
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.LoadingScheme
import dev.redplate.data.MovementPattern
import dev.redplate.data.MuscleGroup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COACHING.md §2: prescribing something the user cannot physically perform is the failure
 * this app exists to avoid. The repository used to answer this with `any`, so an exercise
 * needing a barbell *and* a bench read as available in a gym with only the bench.
 */
class EquipmentAvailabilityTest {

    private fun exercise(vararg equipmentIds: String) = ExerciseEntity(
        id = "bench-press",
        name = "Bench Press",
        pattern = MovementPattern.HORIZONTAL_PUSH,
        primaryMuscle = MuscleGroup.CHEST,
        requiredEquipmentIds = equipmentIds.toList(),
        complexity = Complexity.INTERMEDIATE,
    )

    private fun equipment(id: String, available: Boolean) = EquipmentEntity(
        id = id,
        displayName = id,
        category = EquipmentCategory.BARBELL,
        loadingScheme = LoadingScheme.PLATE_LOADED,
        isAvailable = available,
    )

    @Test
    fun `an exercise needing two things is unavailable when only one is present`() {
        val available = setOf("bench")
        assertFalse(EquipmentAvailability.canPerform(exercise("barbell", "bench"), available))
    }

    @Test
    fun `an exercise needing two things is available when both are present`() {
        val available = setOf("bench", "barbell")
        assertTrue(EquipmentAvailability.canPerform(exercise("barbell", "bench"), available))
    }

    @Test
    fun `an exercise needing nothing is always available`() {
        assertTrue(EquipmentAvailability.canPerform(exercise(), emptySet()))
    }

    @Test
    fun `equipment switched off in settings is not available`() {
        val ids = EquipmentAvailability.availableIds(
            listOf(equipment("barbell", true), equipment("bench", false)),
        )
        assertTrue("barbell" in ids)
        assertFalse("bench" in ids)
        assertFalse(EquipmentAvailability.canPerform(exercise("barbell", "bench"), ids))
    }
}
