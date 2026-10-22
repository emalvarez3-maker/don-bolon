package ec.donbolon.app.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One line of the "why these numbers" list: what was decided and the evidence behind it. */
data class Why(val text: String, val source: String)

/**
 * The daily prescription. Every number comes from a published method; [why] carries the
 * reasoning so the app can show it instead of asking to be trusted.
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
    val proteinPerKg: Double = 0.0,
    val flexKcal: Int = 0,
    val strengthDays: Int = 3,
    val cardioMinutes: Int = 150,
    val band: Double = 0.10,
    val why: List<Why> = emptyList(),
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

    /**
     * Turns the five priorities into numbers. F = lose fat, M = gain muscle, P = performance,
     * H = health, X = flexibility, each 0..1. The shapes are chosen so the corners land on
     * the published recommendations:
     * - Energy: pure fat loss → −25% of TDEE; pure muscle → +10%; both (recomposition) → ≈ −12%
     *   (Helms et al., 2014; Iraki et al., 2019; Barakat et al., 2020). Performance shrinks a
     *   deficit by up to 30% to keep energy availability up (Mountjoy et al., 2018, RED-S).
     * - Safety: deficit ≤ 1% of body weight a week, surplus ≤ 1.5% a month, never below
     *   1500/1200 kcal without supervision.
     * - Protein 1.2–2.4 g/kg, highest when cutting while trying to keep or build muscle
     *   (Morton et al., 2018; Helms et al., 2014; Jäger et al., 2017, ISSN).
     * - Fat 25–30% of energy and ≥ 0.7 g/kg; fiber ≥ 14 g/1000 kcal and ≥ 25–30 g
     *   (Reynolds et al., 2019, The Lancet). Carbs fill the rest.
     */
    fun targets(p: Profile): Targets {
        val (f, m, perf, h, x) = p.priorities.values
        val bmr = bmr(p)
        val tdee = bmr * p.activity.factor
        val why = mutableListOf<Why>()
        var capped = false
        var floored = false

        var delta = -0.25 * f * (1 - 0.5 * m) + 0.10 * m * (1 - f)
        if (delta < 0) delta *= (1 - 0.3 * perf)
        var kcal = tdee * (1 + delta)
        if (delta < 0) {
            val maxDeficit = p.weightKg * 0.01 * 7700 / 7
            if (tdee - kcal > maxDeficit) { kcal = tdee - maxDeficit; capped = true }
            val floor = if (p.sex == Sex.HOMBRE) 1500.0 else 1200.0
            if (kcal < floor) { kcal = floor; floored = true }
        } else {
            val maxSurplus = p.weightKg * 0.015 * 7700 / 30
            if (kcal - tdee > maxSurplus) { kcal = tdee + maxSurplus; capped = true }
        }
        val pct = ((kcal / tdee - 1) * 100).roundToInt()
        why += Why(
            "Gasto diario ${tdee.roundToInt()} kcal: metabolismo en reposo ${bmr.roundToInt()} × actividad ${p.activity.factor}.",
            "Mifflin–St Jeor (1990), la ecuación más precisa según Frankenfield et al. (2005); factores FAO/OMS",
        )
        why += Why(
            when {
                pct <= -3 -> "Déficit de ${-pct}%: lo bastante para bajar grasa, no tanto como para perder músculo."
                pct >= 3 -> "Superávit de $pct%: crecer sin acumular grasa de más."
                else -> "Mantenimiento: el peso queda estable mientras mejoras composición."
            } + if (capped) " Lo limité por seguridad." else "",
            "Helms et al. (2014) JISSN; Iraki et al. (2019) Sports; Barakat et al. (2020) SCJ",
        )

        val proteinPerKg = (1.2 + 0.6 * max(f, m) + 0.4 * f * m + 0.2 * perf).coerceIn(1.2, 2.4)
        val protein = proteinWeight(p) * proteinPerKg
        why += Why(
            "Proteína ${"%.1f".format(proteinPerKg)} g/kg: protege y construye músculo, y es lo que más sacia.",
            "Morton et al. (2018) BJSM, meta-análisis; Jäger et al. (2017) posición ISSN",
        )
        val fat = max(kcal * (0.25 + 0.05 * h) / 9, p.weightKg * 0.7)
        val carbs = max((kcal - protein * 4 - fat * 9) / 4, 50.0)
        if (perf >= 0.6 && carbs < p.weightKg * 3) {
            why += Why("Para rendir conviene llegar a 3–5 g/kg de carbos los días de entreno: súbelos esos días.", "Kerksick et al. (2017) posición ISSN sobre timing de nutrientes")
        }
        val fiber = max(kcal / 1000 * 14, 25 + 5 * h)
        why += Why("Fibra ${fiber.roundToInt()} g: menos mortalidad y mejor saciedad con 25–30 g o más.", "Reynolds et al. (2019) The Lancet")

        val flex = kcal * (0.05 + 0.10 * x)
        val band = 0.07 + 0.08 * x
        why += Why(
            "Margen libre de ${flex.roundToInt()} kcal para lo que quieras, y un día cuenta como cumplido a ±${(band * 100).roundToInt()}%.",
            "Restricción flexible vs. rígida: Westenhoefer et al. (1999); Smith et al. (1999)",
        )
        val strength = (2 + 2 * m + perf).roundToInt().coerceIn(2, 6)
        val cardio = if (h >= 0.4) 150 else 75
        why += Why(
            "Fuerza $strength días por semana (cada músculo ≥ 2 veces) y $cardio min de cardio.",
            "Schoenfeld et al. (2016) Sports Med; Guías de actividad física OMS (2020)",
        )

        return Targets(
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            kcal = (kcal / 10).roundToInt() * 10,
            protein = protein.roundToInt(),
            carbs = carbs.roundToInt(),
            fat = fat.roundToInt(),
            fiber = fiber.roundToInt(),
            waterGlasses = (p.weightKg * 35 / 250).roundToInt().coerceIn(6, 16),
            weeklyChangeKg = (kcal - tdee) * 7 / 7700,
            floored = floored,
            capped = capped,
            proteinPerKg = proteinPerKg,
            flexKcal = (flex / 10).roundToInt() * 10,
            strengthDays = strength,
            cardioMinutes = cardio,
            band = band,
            why = why,
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
    fun onTarget(kcal: Double, target: Int, band: Double = 0.10) = target > 0 && abs(kcal - target) <= target * band
}
