package ec.donbolon.app.data

import kotlin.math.max
import kotlin.math.min

/**
 * The photo AI falls short on plates it judges to be light: on the 40-dish Nutrition5k bench
 * (2026-11-20, 110 estimates) a plate it put under 250 kcal really had a median 1.58× that,
 * and it fell short in 28 of 29 cases, while larger plates showed no steady bias. So light
 * estimates are raised by that factor, up to [CEILING] kcal, and nothing is ever lowered.
 * Left-one-dish-out, this cut the kcal error from 29.7 % to 25.1 % (small plates 34.6 → 18.3 %),
 * with ceilings from 280 to 330 kcal giving nearly the same result.
 */
object SizeCorrection {
    const val FACTOR = 1.58
    const val CEILING = 300.0

    /** The plate total after the correction; equal to [kcal] for anything at or above the ceiling. */
    fun correctedKcal(kcal: Double): Double =
        if (kcal.isNaN() || kcal <= 0) kcal else max(kcal, min(kcal * FACTOR, CEILING))

    /** Every portion scaled by the same factor, so the meal's total lands on [correctedKcal]. */
    fun apply(items: List<FoodItem>): List<FoodItem> {
        val total = items.sumOf { it.kcal }
        val target = correctedKcal(total)
        if (total <= 0 || target <= total) return items
        val k = target / total
        return items.map { it.withGrams(it.grams * k) }
    }
}
