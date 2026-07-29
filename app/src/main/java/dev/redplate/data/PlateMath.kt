package dev.redplate.data

import kotlin.math.abs
import kotlin.math.roundToInt

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
     * The heaviest load at or below [targetKg] that the pairs on hand can actually build.
     *
     * This used to be greedy, justified by a comment claiming each denomination is at least
     * twice the next smaller. That is false for a standard 25/20/15/10/5/2.5/1.25 set, and
     * greedy genuinely fails on lopsided stock: one pair of 10s and two pairs of 7.5s
     * cannot reach 50 kg greedily (it takes the 10 and strands 5 kg), though 7.5 + 7.5
     * makes it exactly.
     *
     * So an exact bounded subset-sum over the owned pairs runs alongside greedy on a
     * 0.25 kg grid, and whichever gets closer wins. Ties go to greedy, because on a
     * canonical set it is already optimal and its answer is the one worth loading: the
     * heaviest plates first, which is fewer discs to lift and the stack the user expects
     * to see. The search space is tiny — a handful of denominations against a per-side
     * capacity of a few hundred grid steps. Neither branch ever overshoots.
     */
    fun load(targetKg: Double, equipment: EquipmentEntity): PlateLoad {
        val bar = equipment.barWeightKg ?: DEFAULT_BAR_KG
        if (targetKg <= bar) return PlateLoad(bar, emptyList(), abs(targetKg - bar) < EPSILON)

        val perSide = (targetKg - bar) / 2.0
        val greedy = greedyPerSide(perSide, equipment.platePairs)
        val exact = bestPerSide(perSide, equipment.platePairs)
        val chosen = when {
            exact != null && exact.sum() > greedy.sum() + EPSILON -> exact.sortedDescending()
            else -> greedy
        }

        val achieved = bar + chosen.sum() * 2
        return PlateLoad(achieved, chosen, abs(achieved - targetKg) < EPSILON)
    }

    /**
     * Exact search. Returns null when the plates cannot be expressed on the grid or the
     * problem is far larger than any real rack, so the caller keeps greedy's answer rather
     * than doing something unbounded.
     */
    private fun bestPerSide(perSideKg: Double, pairs: Map<Double, Int>): List<Double>? {
        if (pairs.isEmpty()) return emptyList()

        val capacity = (perSideKg / GRID_KG + EPSILON).toInt()
        if (capacity <= 0) return emptyList()
        if (capacity > MAX_GRID_STEPS) return null

        // One entry per physical pair, heaviest first so the reconstruction reads naturally.
        val weights = mutableListOf<Int>()
        for ((plate, count) in pairs.entries.sortedByDescending { it.key }) {
            if (plate <= 0.0 || count <= 0) continue
            val steps = plate / GRID_KG
            if (abs(steps - steps.roundToInt()) > EPSILON) return null   // off-grid plate
            repeat(count) { weights += steps.roundToInt() }
            if (weights.size > MAX_PLATE_PAIRS) return null
        }
        if (weights.isEmpty()) return emptyList()

        // 0/1 knapsack. `via[c]` is the pair used the first time c became reachable, and
        // that pair's predecessors all have smaller indices, so walking back never reuses
        // a plate the rack does not have twice.
        val reachable = BooleanArray(capacity + 1)
        val via = IntArray(capacity + 1) { -1 }
        reachable[0] = true

        for ((index, weight) in weights.withIndex()) {
            for (c in capacity downTo weight) {
                if (!reachable[c] && reachable[c - weight]) {
                    reachable[c] = true
                    via[c] = index
                }
            }
        }

        var best = capacity
        while (best > 0 && !reachable[best]) best--

        val chosen = mutableListOf<Double>()
        var cursor = best
        while (cursor > 0) {
            val index = via[cursor]
            if (index < 0) break
            chosen += weights[index] * GRID_KG
            cursor -= weights[index]
        }
        return chosen
    }

    /** Heaviest plate that still fits, repeatedly. Optimal on a canonical set, not in general. */
    private fun greedyPerSide(perSideKg: Double, pairs: Map<Double, Int>): List<Double> {
        var remaining = perSideKg
        val chosen = mutableListOf<Double>()
        for (plate in pairs.keys.sortedDescending()) {
            var pairsLeft = pairs[plate] ?: 0
            while (pairsLeft > 0 && remaining >= plate - EPSILON) {
                chosen += plate
                remaining -= plate
                pairsLeft--
            }
        }
        return chosen
    }

    /**
     * Heaviest load at or below [targetKg] that can be assembled from the plates on hand.
     *
     * Named for what it does. It was called `closestLoadable`, which reads as "nearest in
     * either direction" and is not what it has ever returned.
     */
    fun largestLoadableAtOrBelow(targetKg: Double, equipment: EquipmentEntity): Double =
        load(targetKg, equipment).totalKg

    /**
     * Next achievable load strictly above [currentKg]. Used by every progression rule —
     * never suggest an increment the user cannot physically make.
     */
    fun nextLoadUp(currentKg: Double, equipment: EquipmentEntity): Double {
        return when (equipment.loadingScheme) {
            // Levels are whole numbers with no ceiling the app can know about.
            LoadingScheme.RESISTANCE_LEVEL -> kotlin.math.floor(currentKg) + 1.0

            LoadingScheme.FIXED_INCREMENT, LoadingScheme.PIN_STACK ->
                equipment.availableLoads.firstOrNull { it > currentKg + EPSILON } ?: currentKg
            LoadingScheme.PLATE_LOADED -> {
                var candidate = currentKg + equipment.minIncrement()
                repeat(STEP_SEARCH_LIMIT) {
                    val achievable = largestLoadableAtOrBelow(candidate, equipment)
                    if (achievable > currentKg + EPSILON) return achievable
                    candidate += equipment.minIncrement()
                }
                currentKg
            }
            else -> currentKg + BODYWEIGHT_STEP_KG
        }
    }

    fun nextLoadDown(currentKg: Double, equipment: EquipmentEntity): Double {
        return when (equipment.loadingScheme) {
            LoadingScheme.RESISTANCE_LEVEL ->
                (kotlin.math.ceil(currentKg) - 1.0).coerceAtLeast(0.0)

            LoadingScheme.FIXED_INCREMENT, LoadingScheme.PIN_STACK ->
                equipment.availableLoads.lastOrNull { it < currentKg - EPSILON } ?: currentKg
            LoadingScheme.PLATE_LOADED ->
                largestLoadableAtOrBelow(currentKg - equipment.minIncrement(), equipment)
            else -> (currentKg - BODYWEIGHT_STEP_KG).coerceAtLeast(0.0)
        }
    }

    /**
     * Percentage deload snapped to something loadable — a "10% drop" that
     * lands on an impossible number is worse than no deload at all.
     *
     * Deliberately rounds down: a deload that snapped upward could land back on, or above,
     * the load being backed off from.
     */
    fun deload(currentKg: Double, fraction: Double, equipment: EquipmentEntity): Double =
        equipment.largestLoadableAtOrBelow(currentKg * (1 - fraction))

    private const val EPSILON = 1e-6
    private const val DEFAULT_BAR_KG = 20.0
    private const val BODYWEIGHT_STEP_KG = 1.25

    /** Finest plate granularity the exact search supports. Micro-plates go to 0.25 kg. */
    private const val GRID_KG = 0.25

    /** 1000 kg a side. Past this, something is wrong with the inputs, not the maths. */
    private const val MAX_GRID_STEPS = 4000

    /** No real rack has this many pairs of one bar's plates. */
    private const val MAX_PLATE_PAIRS = 64

    /** How far above the current load to look for the next assemblable one. */
    private const val STEP_SEARCH_LIMIT = 20
}
