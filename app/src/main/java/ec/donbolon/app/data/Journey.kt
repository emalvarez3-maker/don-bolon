package ec.donbolon.app.data

import kotlin.math.abs
import kotlin.math.ceil

/** A stop on the way to the goal weight, and whether the user already passed it. */
data class Milestone(val kg: Double, val reached: Boolean)

/**
 * "Tu camino": how far along the road from the start weight to the goal the user is.
 * [fraction] is 0 at the start and 1 at the goal, clamped (going the wrong way stays at 0,
 * overshooting stays at 1). [lostKg] is the progress made toward the goal in kg (weight lost
 * when losing, gained when gaining), never negative; [leftKg] is what is still missing, never
 * negative. [milestones] run from the start toward the goal; the last one is always the goal.
 */
data class Journey(
    val startKg: Double,
    val currentKg: Double,
    val goalKg: Double,
    val fraction: Double,
    val milestones: List<Milestone>,
    val lostKg: Double,
    val leftKg: Double,
) {
    /** True when the goal is above the start (building weight instead of losing it). */
    val gaining: Boolean get() = goalKg > startKg

    /** Where a milestone sits along the road, 0..1. */
    fun at(kg: Double): Double = ((kg - startKg) / (goalKg - startKg)).coerceIn(0.0, 1.0)
}

/** Never more milestones than this; the step widens instead. */
const val MAX_MILESTONES = 12

/**
 * Builds the journey from [startKg] to [goalKg] with the user now at [currentKg]. Works both for
 * losing and for gaining weight. A milestone every [stepKg] (widened to a multiple of 0.5 kg so
 * there are at most [MAX_MILESTONES], goal included). Null when start and goal are the same.
 *
 * Integrator's choice of start: the first logged weight (`s.weights.firstOrNull()?.kg`, the list
 * is kept sorted by date) or, before any weigh-in, the profile weight. See [journeyFor].
 */
fun journey(startKg: Double, currentKg: Double, goalKg: Double, stepKg: Double = 1.0): Journey? {
    val total = goalKg - startKg
    if (abs(total) < 1e-9) return null
    val dir = if (total > 0) 1.0 else -1.0
    val distance = abs(total)

    var step = if (stepKg > 0) stepKg else 1.0
    if (distance / step > MAX_MILESTONES) step = ceil(distance / MAX_MILESTONES * 2) / 2
    val eps = 1e-6
    fun passed(kg: Double) = (currentKg - kg) * dir >= -eps

    val stops = mutableListOf<Double>()
    var k = 1
    while (k * step < distance - eps) { stops += startKg + dir * k * step; k++ }
    stops += goalKg

    val progress = (currentKg - startKg) * dir
    return Journey(
        startKg = startKg,
        currentKg = currentKg,
        goalKg = goalKg,
        fraction = (progress / distance).coerceIn(0.0, 1.0),
        milestones = stops.map { Milestone(it, passed(it)) },
        lostKg = progress.coerceAtLeast(0.0),
        leftKg = ((goalKg - currentKg) * dir).coerceAtLeast(0.0),
    )
}

/** The journey for the app state: first weigh-in (or profile weight) → smoothed current → goal. */
fun journeyFor(s: AppState): Journey? {
    val goal = s.profile.goalWeightKg ?: return null
    val start = s.weights.firstOrNull()?.kg ?: s.profile.weightKg
    return journey(start, Progress.currentWeight(s), goal)
}
