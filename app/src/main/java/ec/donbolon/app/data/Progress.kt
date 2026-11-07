package ec.donbolon.app.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** One point of a curve: a date and a value (kg, cm or kcal). */
data class Point(val date: LocalDate, val value: Double)

/** Where the projection says the user lands, and when. */
data class Projection(val points: List<Point>, val reachesGoal: LocalDate?)

/** The week in numbers, compared with the targets. */
data class WeekStats(val loggedDays: Int, val avgKcal: Double, val inRange: Int, val proteinDays: Int, val trained: Int, val avgWater: Double)

/** How a day went, for the calendar. */
enum class DayMark { EMPTY, IN_RANGE, UNDER, OVER, FUTURE }

object Progress {
    /** kcal in a kilogram of body fat (Wishnofsky, 1958); a rule of thumb, not an exact law. */
    const val KCAL_PER_KG = 7700.0

    /** The latest smoothed weight, or the profile weight before any weigh-in. */
    fun currentWeight(s: AppState): Double = Nutrition.trend(s.weights).lastOrNull()?.second ?: s.profile.weightKg

    /**
     * Where weight goes if the daily target is kept. Each week the expenditure is recomputed for
     * the new weight, so the curve flattens as it should (Hall et al., 2011, The Lancet): the
     * same intake makes a smaller deficit in a lighter body.
     */
    fun projection(s: AppState, from: LocalDate = LocalDate.now(), maxWeeks: Int = 52): Projection {
        val intake = Nutrition.targets(s.profile).kcal.toDouble()
        val goal = s.profile.goalWeightKg
        var w = currentWeight(s)
        val pts = mutableListOf(Point(from, w))
        var reached: LocalDate? = null
        val weeks = if (goal == null) 12 else maxWeeks
        for (i in 1..weeks) {
            val tdee = Nutrition.targets(s.profile.copy(weightKg = w)).tdee
            val next = w + (intake - tdee) * 7 / KCAL_PER_KG
            val date = from.plusWeeks(i.toLong())
            if (goal != null && reached == null && (w - goal) * (next - goal) <= 0) {
                // Crossed the goal this week: stop on it.
                pts += Point(date, goal); reached = date; break
            }
            w = next
            pts += Point(date, w)
            if (abs(intake - tdee) < 20) break // maintenance: the curve is flat from here
        }
        return Projection(pts, reached)
    }

    /**
     * Estimated fat lost from what was eaten: the energy gap of each logged day, summed and turned
     * into kg. Days without logs are skipped (unknown), so this only counts what the diary shows.
     */
    fun fatCurve(s: AppState, days: Int = 60, today: LocalDate = LocalDate.now()): List<Point> {
        var kg = 0.0
        val out = mutableListOf<Point>()
        for (d in days - 1 downTo 0) {
            val day = today.minusDays(d.toLong())
            if (s.mealsOn(day).isEmpty() || day == today) continue // today isn't finished yet
            val w = Nutrition.trend(s.weights.filter { !it.date.isAfter(day) }).lastOrNull()?.second ?: s.profile.weightKg
            val tdee = Nutrition.targets(s.profile.copy(weightKg = w)).tdee
            kg += (tdee - s.totalsOn(day).kcal) / KCAL_PER_KG
            out += Point(day, kg)
        }
        return out
    }

    fun weekStats(s: AppState, today: LocalDate = LocalDate.now()): WeekStats {
        val t = Nutrition.targets(s.profile)
        val days = (0..6).map { today.minusDays(it.toLong()) }
        val logged = days.filter { s.mealsOn(it).isNotEmpty() }
        return WeekStats(
            loggedDays = logged.size,
            avgKcal = logged.map { s.totalsOn(it).kcal }.average().takeIf { !it.isNaN() } ?: 0.0,
            inRange = logged.count { Nutrition.onTarget(s.totalsOn(it).kcal, t.kcal, t.band) },
            proteinDays = logged.count { s.totalsOn(it).protein >= t.protein * 0.9 },
            trained = s.workouts.count { it in days },
            avgWater = days.map { s.water[it] ?: 0 }.average(),
        )
    }

    fun mark(s: AppState, day: LocalDate, today: LocalDate = LocalDate.now()): DayMark {
        if (day.isAfter(today)) return DayMark.FUTURE
        if (s.mealsOn(day).isEmpty()) return DayMark.EMPTY
        val t = Nutrition.targets(s.profile)
        val k = s.totalsOn(day).kcal
        return when {
            Nutrition.onTarget(k, t.kcal, t.band) -> DayMark.IN_RANGE
            k > t.kcal -> DayMark.OVER
            else -> DayMark.UNDER
        }
    }

    /** "en 9 semanas" / "en 3 meses", for the goal date. */
    fun humanWeeks(from: LocalDate, to: LocalDate): String {
        val w = ((to.toEpochDay() - from.toEpochDay()) / 7.0).roundToInt()
        return when {
            w <= 1 -> "en 1 semana"
            w < 9 -> "en $w semanas"
            else -> "en ${(w / 4.345).roundToInt()} meses"
        }
    }

    /** Lowest and highest body fat % [rfm] returns; see its KDoc for why. */
    const val RFM_MIN_PCT = 4.0
    const val RFM_MAX_PCT = 60.0

    /**
     * Relative Fat Mass, an estimate of whole-body fat % from height and waist alone:
     * men 64 − 20 × (height / waist), women 76 − 20 × (height / waist), both in the same unit.
     *
     * Source: Woolcott OO, Bergman RN (2018). Relative fat mass (RFM) as a new estimator of
     * whole-body fat percentage — a cross-sectional study in American adult individuals.
     * Scientific Reports 8:10980, doi:10.1038/s41598-018-29362-1. Developed against DXA in
     * NHANES 1999–2004 (n = 12,581) and validated in NHANES 2005–2006 (n = 3,456). In validation
     * RFM explained R² = 0.75 (men) and 0.69 (women) of DXA fat %, with bias of +0.5 and +0.9
     * points; 88.9 % (men) and 91.5 % (women) of estimates fell within 20 % of DXA. It misclassified
     * DXA obesity in 9.4 % of men and 12.7 % of women (AUC 0.94 and 0.93). An estimate, not a scan:
     * a single value can be several points off, so trends matter more than one reading.
     *
     * Returns null for non-finite or implausible inputs (height outside 100–250 cm, waist outside
     * 40–200 cm). The result is clamped to [RFM_MIN_PCT, RFM_MAX_PCT]: 4 % is about the essential
     * fat of a lean man (below it the linear formula just extrapolates past physiology), and DXA fat
     * % in the NHANES samples the formula was fit on rarely exceeds 60 %.
     */
    fun rfm(sex: Sex, heightCm: Double, waistCm: Double): Double? {
        if (!heightCm.isFinite() || !waistCm.isFinite()) return null
        if (heightCm !in 100.0..250.0 || waistCm !in 40.0..200.0) return null
        val base = if (sex == Sex.MUJER) 76.0 else 64.0
        return (base - 20.0 * heightCm / waistCm).coerceIn(RFM_MIN_PCT, RFM_MAX_PCT)
    }

    /**
     * Fat and lean mass over the last [months] months: one point per waist measurement (waist is
     * measured rarely, so each reading is a real data point; bucketing by month would either drop
     * readings or repeat one). Each waist is paired with the smoothed weight ([Nutrition.trend])
     * whose date is closest to it, earlier winning ties; before any weigh-in the profile weight is
     * used, as in [currentWeight]. No waist data gives an empty list: a waist is never invented.
     */
    fun composition(s: AppState, months: Int = 6, today: LocalDate = LocalDate.now()): List<Composition> {
        val from = today.minusMonths(months.toLong())
        val trend = Nutrition.trend(s.weights)
        return s.waist.entries
            .filter { !it.key.isAfter(today) && it.key.isAfter(from) }
            .sortedBy { it.key }
            .mapNotNull { (date, waistCm) ->
                val pct = rfm(s.profile.sex, s.profile.heightCm, waistCm) ?: return@mapNotNull null
                val weight = trend.minWithOrNull(
                    compareBy<Pair<LocalDate, Double>> { abs(it.first.toEpochDay() - date.toEpochDay()) }.thenBy { it.first },
                )?.second ?: s.profile.weightKg
                val fatKg = weight * pct / 100.0
                Composition(date, weight, pct, fatKg, weight - fatKg)
            }
    }
}

/** Body composition on one day: fat % from [Progress.rfm] and the weight split into fat and lean kg. */
data class Composition(val date: LocalDate, val weightKg: Double, val fatPct: Double, val fatKg: Double, val leanKg: Double)
