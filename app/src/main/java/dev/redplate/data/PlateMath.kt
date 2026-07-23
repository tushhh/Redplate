package dev.redplate.data

import kotlin.math.abs

/**
 * Plate loading maths. Backs both the progression engine (what loads are achievable)
 * and the plate stack UI (what to actually put on the bar).
 */
object PlateMath {

    /** IPF/IWF calibrated plate colours, keyed by plate mass in kg. */
    val PLATE_COLOURS: Map<Double, Long> = mapOf(
        25.0 to 0xFFC8102E,   // red
        20.0 to 0xFF0057B8,   // blue
        15.0 to 0xFFFFD100,   // yellow
        10.0 to 0xFF00843D,   // green
        5.0 to 0xFFF2F2F2,    // white
        2.5 to 0xFF1A1A1A,    // black
        1.25 to 0xFFC0C0C0    // chrome
    )

    data class PlateLoad(
        val totalKg: Double,
        /** Plates on ONE side, heaviest first. Render outward from the collar. */
        val perSide: List<Double>,
        val exact: Boolean
    )

    /**
     * Greedy plate selection constrained by pairs actually owned.
     * Greedy is optimal here because standard plate sets are canonical
     * (each denomination >= 2x the next smaller).
     */
    fun load(targetKg: Double, equipment: EquipmentEntity): PlateLoad {
        val bar = equipment.barWeightKg ?: 20.0
        if (targetKg <= bar) return PlateLoad(bar, emptyList(), targetKg == bar)

        var remainingPerSide = (targetKg - bar) / 2.0
        val chosen = mutableListOf<Double>()
        val stock = equipment.platePairs.toMutableMap()

        for (plate in stock.keys.sortedDescending()) {
            var pairsLeft = stock[plate] ?: 0
            while (pairsLeft > 0 && remainingPerSide >= plate - 1e-6) {
                chosen += plate
                remainingPerSide -= plate
                pairsLeft--
            }
            stock[plate] = pairsLeft
        }

        val achieved = bar + chosen.sum() * 2
        return PlateLoad(achieved, chosen, abs(achieved - targetKg) < 1e-6)
    }

    /** Nearest load that can actually be assembled from the plates on hand. */
    fun closestLoadable(targetKg: Double, equipment: EquipmentEntity): Double =
        load(targetKg, equipment).totalKg

    /**
     * Next achievable load strictly above [currentKg]. Used by every progression rule —
     * never suggest an increment the user cannot physically make.
     */
    fun nextLoadUp(currentKg: Double, equipment: EquipmentEntity): Double {
        return when (equipment.loadingScheme) {
            LoadingScheme.FIXED_INCREMENT, LoadingScheme.PIN_STACK ->
                equipment.availableLoads.firstOrNull { it > currentKg + 1e-6 } ?: currentKg
            LoadingScheme.PLATE_LOADED -> {
                var candidate = currentKg + equipment.minIncrement()
                repeat(20) {
                    val achievable = closestLoadable(candidate, equipment)
                    if (achievable > currentKg + 1e-6) return achievable
                    candidate += equipment.minIncrement()
                }
                currentKg
            }
            else -> currentKg + 1.25
        }
    }

    fun nextLoadDown(currentKg: Double, equipment: EquipmentEntity): Double {
        return when (equipment.loadingScheme) {
            LoadingScheme.FIXED_INCREMENT, LoadingScheme.PIN_STACK ->
                equipment.availableLoads.lastOrNull { it < currentKg - 1e-6 } ?: currentKg
            LoadingScheme.PLATE_LOADED -> closestLoadable(currentKg - equipment.minIncrement(), equipment)
            else -> (currentKg - 1.25).coerceAtLeast(0.0)
        }
    }

    /**
     * Percentage deload snapped to something loadable — a "10% drop" that
     * lands on an impossible number is worse than no deload at all.
     */
    fun deload(currentKg: Double, fraction: Double, equipment: EquipmentEntity): Double =
        equipment.nearestAchievable(currentKg * (1 - fraction))
}