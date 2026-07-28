package dev.redplate.data

/**
 * Whether the kit actually on hand can perform a movement.
 *
 * There were two answers to this question. The generator required **all** of an exercise's
 * equipment; the repository that feeds the exercise browser and the mid-workout substitute
 * list required only **any** of it. So an exercise needing a barbell *and* a bench read as
 * available in a gym with only the bench, and Redplate could offer a swap the user could
 * not physically perform — the exact failure COACHING.md §2 says the app exists to prevent.
 *
 * One definition, one call site each. `all` is the honest one: every listed piece of
 * equipment is required, or it would not be listed.
 */
object EquipmentAvailability {

    /** An exercise needing nothing is always performable. */
    fun canPerform(exercise: ExerciseEntity, availableEquipmentIds: Set<String>): Boolean =
        exercise.requiredEquipmentIds.all { it in availableEquipmentIds }

    fun availableIds(equipment: List<EquipmentEntity>): Set<String> =
        equipment.filter { it.isAvailable }.mapTo(mutableSetOf()) { it.id }
}
