package ec.donbolon.app.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Time-restricted eating. The evidence (Lowe et al., 2020, JAMA Intern Med; Liu et al., 2022,
 * NEJM) is that it works about as well as plain calorie restriction: its value is that it
 * makes the deficit easier for people who like it. So the app adapts the day to the window
 * and keeps protein the priority inside it; it never sells fasting as magic.
 *
 * The window is free (any opening hour, any length); these presets are shortcuts.
 */
enum class Fasting(val label: String, val fastHours: Int, val detail: String) {
    NINGUNO("Sin ayuno", 0, "Comes cuando te toca"),
    F12("12:12", 12, "12 h de ayuno, 12 h para comer: el punto de partida"),
    F14("14:10", 14, "Cómodo para la mayoría"),
    F16("16:8", 16, "El clásico: dos o tres comidas en 8 horas"),
    F17("17:7", 17, "Dos comidas fuertes y quizás un snack en 7 horas"),
    F18("18:6", 18, "Exigente: solo si ya dominas el 16:8"),
    ;

    val windowHours get() = 24 - fastHours
}

val Profile.fasting get() = windowHours in 1..23
val Profile.fastLabel get() = if (fasting) "${24 - windowHours}:$windowHours" else "Sin ayuno"

/** Where the user is right now: inside the eating window or fasting, and for how long. */
data class FastState(
    val eating: Boolean,
    /** Minutes until the window closes (when eating) or opens (when fasting). */
    val minutesToChange: Long,
    /** Hours since the last meal, or since the window closed if nothing logged after it. */
    val hoursFasted: Double,
    val opens: LocalTime,
    val closes: LocalTime,
)

/** A window suggested from the evidence and from when this person actually eats. */
data class WindowAdvice(val start: Int, val hours: Int, val why: List<Why>) {
    val end get() = (start + hours) % 24
}

object Fast {
    fun opens(p: Profile): LocalTime = LocalTime.of(p.windowStart, 0)
    fun closes(p: Profile): LocalTime = opens(p).plusHours(p.windowHours.toLong())

    fun inWindow(p: Profile, t: LocalTime): Boolean {
        if (!p.fasting) return true
        val o = opens(p)
        val c = closes(p)
        return if (o < c) t >= o && t < c else t >= o || t < c // a window that crosses midnight
    }

    fun state(s: AppState, now: LocalDateTime = LocalDateTime.now()): FastState? {
        val p = s.profile
        if (!p.fasting) return null
        val o = opens(p)
        val c = closes(p)
        val eating = inWindow(p, now.toLocalTime())
        val target = if (eating) c else o
        var next = now.toLocalDate().atTime(target)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val lastMeal = s.meals.maxOfOrNull { it.time }?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        val windowClosed = now.toLocalDate().atTime(c).let { if (it.isAfter(now)) it.minusDays(1) else it }
        // The fast starts at the last meal if it was in (or after) the last window; otherwise when the window closed.
        val lastWindowOpened = windowClosed.minusHours(p.windowHours.toLong())
        val fastStart = if (lastMeal != null && !lastMeal.isBefore(lastWindowOpened) && !lastMeal.isAfter(now)) lastMeal else windowClosed
        return FastState(
            eating = eating,
            minutesToChange = Duration.between(now, next).toMinutes(),
            hoursFasted = if (eating) 0.0 else Duration.between(fastStart, now).toMinutes() / 60.0,
            opens = o,
            closes = c,
        )
    }

    /** The meals that fit inside the window, by their usual hour. */
    fun slots(p: Profile): List<Slot> {
        val usual = mapOf(Slot.DESAYUNO to 8, Slot.ALMUERZO to 13, Slot.SNACK to 17, Slot.CENA to 20)
        if (!p.fasting) return listOf(Slot.DESAYUNO, Slot.ALMUERZO, Slot.SNACK, Slot.CENA)
        val inside = usual.filter { (_, h) -> inWindow(p, LocalTime.of(h, 0)) }.keys.toList()
        // A short window that misses every usual hour still gets a first meal and a last one.
        return inside.ifEmpty { listOf(Slot.ALMUERZO, Slot.CENA) }
    }

    /** The hour of the first meal on a typical day, from the last two weeks of the diary. */
    fun usualFirstMeal(s: AppState, today: LocalDate = LocalDate.now()): Int? {
        val firsts = (1..14).mapNotNull { d ->
            s.mealsOn(today.minusDays(d.toLong())).minOfOrNull { it.time }
                ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).hour }
        }.sorted()
        return if (firsts.size >= 2) firsts[firsts.size / 2] else null
    }

    /**
     * The evidence-based window: close at least 3 h before sleep, 8–10 h long, and as close as
     * possible to when this person already starts eating, so it fits the life they have.
     */
    fun advise(s: AppState): WindowAdvice {
        val p = s.profile
        val close = (p.sleepHour - 3 + 24) % 24
        val habit = usualFirstMeal(s) ?: p.windowStart.takeIf { p.fasting } ?: 12
        val hours = ((close - habit + 24) % 24).coerceIn(8, 10)
        val start = (close - hours + 24) % 24
        val why = listOf(
            Why(
                "Cerrar a las %02d:00, 3 horas antes de dormir (%02d:00): comer tarde da más hambre al día siguiente y quema menos.".format(close, p.sleepHour),
                "Vujović et al. (2022), Cell Metabolism",
            ),
            Why(
                "Una ventana de $hours h es suficiente para 2–3 comidas con buena proteína y se sostiene en el tiempo.",
                "Lowe et al. (2020) JAMA Intern Med; Liu et al. (2022) NEJM",
            ),
            Why(
                "Mientras más temprano, mejor responde tu cuerpo al azúcar y a la insulina.",
                "Sutton et al. (2018) Cell Metabolism; Jamshed et al. (2022) JAMA Intern Med",
            ),
            Why(
                if (usualFirstMeal(s) != null) "Empieza a la hora en que ya sueles comer por primera vez (%02d:00): pocos cambios, más constancia.".format(habit)
                else "Si recién empiezas, sube de a poco: 12:12 → 14:10 → 16:8.",
                "Los cambios pequeños se sostienen más (Lally et al., 2010)",
            ),
        )
        return WindowAdvice(start, hours, why)
    }

    fun format(minutes: Long): String = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
