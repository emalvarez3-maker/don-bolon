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
        return if (w < 9) "en $w semanas" else "en ${(w / 4.345).roundToInt()} meses"
    }
}
