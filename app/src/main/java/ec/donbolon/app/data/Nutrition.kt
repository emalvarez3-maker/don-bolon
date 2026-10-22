package ec.donbolon.app.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The daily prescription. Every number here comes from a published method:
 * - Resting energy: Mifflin–St Jeor (1990), the most accurate predictive equation in
 *   the ADA evidence review (Frankenfield et al., 2005).
 * - Activity multipliers: FAO/WHO/UNU physical-activity levels.
 * - 1 kg of body fat ≈ 7700 kcal; deficits are capped at 25% of TDEE and ~1% of body
 *   weight per week to protect lean mass (Helms et al., 2014; Hall et al., 2011 show the
 *   real curve flattens, so the weekly estimate is a guide, not a promise).
 * - Protein 1.6–2.2 g/kg in a deficit (Morton et al., 2018; Helms 2014); fiber 14 g per
 *   1000 kcal (Dietary Guidelines); fat never below ~0.6 g/kg for hormones.
 */
data class Targets(
    val bmr: Int,
    val tdee: Int,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val fiber: Int,
    val waterGlasses: Int,
    val weeklyChangeKg: Double,
    val floored: Boolean,
    val capped: Boolean,
)

object Nutrition {
    fun bmr(p: Profile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return if (p.sex == Sex.HOMBRE) base + 5 else base - 161
    }

    fun bmi(p: Profile) = p.weightKg / ((p.heightCm / 100) * (p.heightCm / 100))

    /** With obesity, protein is set from a reference weight (BMI 25) so the target stays realistic. */
    private fun proteinWeight(p: Profile): Double {
        val ref = 25 * (p.heightCm / 100) * (p.heightCm / 100)
        return if (bmi(p) > 30) ref else p.weightKg
    }

    fun targets(p: Profile): Targets {
        val bmr = bmr(p)
        val tdee = bmr * p.activity.factor
        var capped = false
        var floored = false
        val kcal = when (p.goal) {
            Goal.MANTENER -> tdee
            Goal.BAJAR -> {
                val wanted = p.rateKgWeek * 7700 / 7
                val safe = min(tdee * 0.25, p.weightKg * 0.01 * 7700 / 7)
                if (wanted > safe) capped = true
                val raw = tdee - min(wanted, safe)
                val floor = if (p.sex == Sex.HOMBRE) 1500.0 else 1200.0
                if (raw < floor) floored = true
                max(raw, floor)
            }
            Goal.SUBIR -> {
                val wanted = p.rateKgWeek * 7700 / 7
                if (wanted > 500) capped = true
                tdee + min(wanted, 500.0)
            }
            // Recomposition: a small deficit (~15%, at most ~0.5% of body weight a week) plus
            // strength training and high protein lets fat drop while muscle still grows
            // (Barakat et al., 2020, Strength & Conditioning Journal).
            Goal.DEFINIR -> {
                val raw = tdee - min(tdee * 0.15, p.weightKg * 0.005 * 7700 / 7)
                val floor = if (p.sex == Sex.HOMBRE) 1500.0 else 1200.0
                if (raw < floor) floored = true
                max(raw, floor)
            }
        }
        val pw = proteinWeight(p)
        val proteinPerKg = when (p.goal) {
            Goal.BAJAR -> if (p.activity.ordinal >= Activity.MODERADO.ordinal) 2.2 else 1.8
            Goal.DEFINIR -> 2.2
            Goal.SUBIR -> 2.0
            Goal.MANTENER -> 1.6
        }
        val protein = pw * proteinPerKg
        val fat = max(kcal * 0.28 / 9, p.weightKg * 0.6)
        val carbs = max((kcal - protein * 4 - fat * 9) / 4, 50.0)
        val change = (kcal - tdee) * 7 / 7700
        return Targets(
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            kcal = (kcal / 10).roundToInt() * 10,
            protein = protein.roundToInt(),
            carbs = carbs.roundToInt(),
            fat = fat.roundToInt(),
            fiber = (kcal / 1000 * 14).roundToInt(),
            waterGlasses = (p.weightKg * 35 / 250).roundToInt().coerceIn(6, 16),
            weeklyChangeKg = change,
            floored = floored,
            capped = capped,
        )
    }

    /**
     * Exponentially smoothed weight (α = 0.1, as in Hacker's Diet): daily water swings of
     * ±1–2 kg hide the real trend, and seeing only the trend reduces the urge to react to noise.
     */
    fun trend(weights: List<WeightEntry>): List<Pair<LocalDate, Double>> {
        var t: Double? = null
        return weights.sortedBy { it.date }.map { w ->
            t = t?.let { it + 0.1 * (w.kg - it) } ?: w.kg
            w.date to t!!
        }
    }

    /** Consecutive days with at least one meal logged, ending today (or yesterday if today is still empty). */
    fun streak(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    /**
     * The four daily habits that build visible abs: log, hit protein, train (on training days
     * the user plans), drink water. Small, checkable wins keep motivation going (Amabile &
     * Kramer's progress principle; self-monitoring, Burke 2011).
     */
    fun habits(s: AppState, t: Targets, day: LocalDate): List<Pair<String, Boolean>> {
        val totals = s.totalsOn(day)
        return listOf(
            "Registré" to s.mealsOn(day).isNotEmpty(),
            "Proteína" to (totals.protein >= t.protein * 0.9),
            "Entrené" to (day in s.workouts),
            "Agua" to ((s.water[day] ?: 0) >= t.waterGlasses),
        )
    }

    /** Workouts in the Monday-to-Sunday week that contains [day]. */
    fun workoutsThisWeek(s: AppState, day: LocalDate = LocalDate.now()): Int {
        val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
        return s.workouts.count { it >= monday && it <= monday.plusDays(6) }
    }

    /** A day is "on target" within ±10% of the calorie goal: flexible, not all-or-nothing. */
    fun onTarget(kcal: Double, target: Int) = target > 0 && abs(kcal - target) <= target * 0.10
}
