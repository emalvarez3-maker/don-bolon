package ec.donbolon.app.data

import org.json.JSONObject
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** Something Don Bolón learned about the user in conversation ("no come cerdo", "entrena a las 6 am"). */
data class MemoryNote(val id: String, val text: String, val time: Long) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("text", text).put("time", time)

    companion object {
        fun from(o: JSONObject) = MemoryNote(o.optString("id", UUID.randomUUID().toString()), o.optString("text"), o.optLong("time"))
    }
}

/** The user fixed an AI estimate: future photos of this food are calibrated with it. */
data class Correction(val food: String, val aiGrams: Double, val userGrams: Double, val time: Long) {
    fun toJson(): JSONObject = JSONObject().put("food", food).put("ai", aiGrams).put("user", userGrams).put("time", time)

    companion object {
        fun from(o: JSONObject) = Correction(o.optString("food"), o.optDouble("ai"), o.optDouble("user"), o.optLong("time"))
    }
}

/**
 * One thing to work on this week, chosen from last week's weakest habit. A single, specific
 * target beats many vague ones (Locke & Latham, 2002), and it comes with an if-then plan.
 */
data class Focus(val weekStart: LocalDate, val title: String, val plan: String, val review: String) {
    fun toJson(): JSONObject = JSONObject().put("week", weekStart.toString()).put("title", title).put("plan", plan).put("review", review)

    companion object {
        fun from(o: JSONObject) = Focus(LocalDate.parse(o.getString("week")), o.optString("title"), o.optString("plan"), o.optString("review"))
    }
}

/** A food the user eats often and how much of it, learned from the diary. */
data class Usual(val name: String, val times: Int, val grams: Double)

object Memory {
    private const val MAX_NOTES = 60

    private fun norm(s: String) = Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    /** Adds what the coach decided to remember, skipping anything already known. */
    fun remember(s: AppState, facts: List<String>): AppState {
        val known = s.memories.map { norm(it.text) }.toMutableSet()
        val fresh = facts.map { it.trim().trimEnd('.') }.filter { it.length in 4..160 && known.add(norm(it)) }
            .map { MemoryNote(UUID.randomUUID().toString(), it, System.currentTimeMillis()) }
        return if (fresh.isEmpty()) s else s.copy(memories = (s.memories + fresh).takeLast(MAX_NOTES))
    }

    /** Compares what the AI estimated with what was saved, and keeps every real portion fix. */
    fun corrections(ai: List<FoodItem>, saved: List<FoodItem>): List<Correction> = ai.mapNotNull { a ->
        val s = saved.firstOrNull { norm(it.name) == norm(a.name) } ?: return@mapNotNull null
        if (a.grams <= 0 || abs(s.grams - a.grams) / a.grams < 0.12) null
        else Correction(a.name, a.grams, s.grams, System.currentTimeMillis())
    }

    /** The foods eaten most in the last 60 days with the usual portion. */
    fun usuals(s: AppState, today: LocalDate = LocalDate.now()): List<Usual> =
        s.meals.filter { it.date >= today.minusDays(60) }.flatMap { it.items }
            .groupBy { norm(it.name) }
            .filter { it.value.size >= 2 }
            .map { (_, items) -> Usual(items.first().name, items.size, items.map { it.grams }.average()) }
            .sortedByDescending { it.times }.take(10)

    /** Per food, how the user tends to correct the AI (e.g. arroz ×0.8). */
    fun calibration(s: AppState): List<Pair<String, Double>> =
        s.corrections.groupBy { norm(it.food) }.map { (_, cs) ->
            cs.last().food to cs.takeLast(5).map { it.userGrams / it.aiGrams }.average()
        }.sortedByDescending { abs(it.second - 1) }.take(10)

    /** What the AI should know about this person before estimating a plate. */
    fun forAnalysis(s: AppState): String {
        val usual = usuals(s)
        val cal = calibration(s)
        if (usual.isEmpty() && cal.isEmpty()) return ""
        return buildString {
            append("LO QUE YA SABES DE SUS PORCIONES (úsalo para calibrar):\n")
            usual.forEach { append("- Suele comer ${it.name}: ~${it.grams.roundToInt()} g (${it.times} veces)\n") }
            cal.forEach { (food, k) -> append("- Cuando estimas $food, la persona corrige a ×${"%.2f".format(k)} de tu estimación\n") }
        }
    }

    /** The long-term picture for the coach: notes, habits and the week's focus. */
    fun forCoach(s: AppState, t: Targets, today: LocalDate = LocalDate.now()): String {
        val last14 = (1..14).map { today.minusDays(it.toLong()) }
        val logged = last14.count { s.mealsOn(it).isNotEmpty() }
        val inRange = last14.count { s.mealsOn(it).isNotEmpty() && Nutrition.onTarget(s.totalsOn(it).kcal, t.kcal, t.band) }
        val protein = last14.count { s.totalsOn(it).protein >= t.protein * 0.9 }
        val trained = s.workouts.count { it >= today.minusDays(14) }
        val triggers = s.meals.filter { it.date >= today.minusDays(30) && it.reason?.physical == false }
            .groupingBy { it.reason!!.label }.eachCount().entries.sortedByDescending { it.value }.take(3)
        val trend = Nutrition.trend(s.weights)
        return buildString {
            append("MEMORIA DE LARGO PLAZO:\n")
            if (s.memories.isEmpty()) append("- (todavía no sabes nada personal; aprende de lo que te cuente)\n")
            s.memories.forEach { append("- ${it.text}\n") }
            append("Últimos 14 días: registró $logged días, $inRange en rango de calorías, $protein con proteína cumplida, entrenó $trained veces (plan: ${s.profile.trainDays}/semana).\n")
            if (triggers.isNotEmpty()) append("Disparadores de comer sin hambre (30 días): ${triggers.joinToString { "${it.key} ×${it.value}" }}.\n")
            if (trend.size >= 2) append("Peso tendencia: ${"%.1f".format(trend.first().second)} → ${"%.1f".format(trend.last().second)} kg.\n")
            usuals(s).take(6).takeIf { it.isNotEmpty() }?.let { u -> append("Come seguido: ${u.joinToString { "${it.name} (~${it.grams.roundToInt()} g)" }}.\n") }
            s.focus?.let { append("ENFOQUE DE ESTA SEMANA: ${it.title}. Plan: ${it.plan}\n") }
        }
    }

    fun monday(day: LocalDate = LocalDate.now()): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Reviews last week and picks this week's focus: the habit furthest from its target.
     * Returns null until there is at least one logged day to learn from.
     */
    fun weeklyFocus(s: AppState, t: Targets, today: LocalDate = LocalDate.now()): Focus? {
        val start = monday(today)
        if (s.focus?.weekStart == start) return s.focus
        val days = (1..7).map { start.minusDays(it.toLong()) }.filter { it >= (s.meals.minOfOrNull { m -> m.date } ?: today) }
        if (s.meals.isEmpty()) return null
        val n = days.size.coerceAtLeast(1)
        val logged = days.count { s.mealsOn(it).isNotEmpty() }
        val protein = days.count { s.totalsOn(it).protein >= t.protein * 0.9 }
        val inRange = days.count { s.mealsOn(it).isNotEmpty() && Nutrition.onTarget(s.totalsOn(it).kcal, t.kcal, t.band) }
        val trained = s.workouts.count { it in days }
        val emotional = s.meals.count { it.date in days && it.reason?.physical == false }
        val review = if (days.isEmpty()) "Primera semana: empezamos." else
            "La semana pasada: registraste $logged/$n días, $inRange en rango, proteína $protein/$n, entrenaste $trained de ${s.profile.trainDays}."

        // Score each habit 0..1 and work on the weakest one.
        val wantTrain = s.profile.priorities.muscle >= 0.4
        val options = listOfNotNull(
            Triple(logged.toDouble() / n, "Registrar todos los días", "Si termino de comer, entonces le tomo foto antes de levantarme de la mesa."),
            Triple(protein.toDouble() / n, "Proteína en cada comida", "Si armo un plato, entonces lo primero que pongo es la proteína: huevo, pollo, atún, chochos o yogur griego."),
            Triple(inRange.toDouble() / n, "Quedarme en mi rango de calorías", "Si me da hambre después de la cena, entonces tomo agua o una infusión y espero 10 minutos."),
            if (wantTrain) Triple(trained.toDouble() / s.profile.trainDays, "Entrenar ${s.profile.trainDays} veces", "Si es lunes, miércoles o viernes a mi hora de siempre, entonces me pongo la ropa de entrenar, aunque sea para 20 minutos.") else null,
            if (emotional >= 2) Triple(1.0 - emotional / 7.0, "Comer solo con hambre de verdad", "Si quiero comer sin hambre, entonces abro el SOS de 3 minutos primero.") else null,
        )
        val (_, title, plan) = options.minBy { it.first }
        return Focus(start, title, plan, review)
    }
}
