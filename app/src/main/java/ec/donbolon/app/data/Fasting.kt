package ec.donbolon.app.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Time-restricted eating. The evidence (Lowe et al., 2020, JAMA Intern Med; Liu et al., 2022,
 * NEJM) is that it works about as well as plain calorie restriction: its value is that it
 * makes the deficit easier for people who like it. So the app adapts the day to the window
 * and keeps protein the priority inside it; it never sells fasting as magic.
 */
enum class Fasting(val label: String, val fastHours: Int, val detail: String) {
    NINGUNO("Sin ayuno", 0, "Comes cuando te toca"),
    F12("12:12", 12, "12 h de ayuno, 12 h para comer: el punto de partida"),
    F14("14:10", 14, "Cómodo para la mayoría"),
    F16("16:8", 16, "El clásico: dos o tres comidas en 8 horas"),
    F18("18:6", 18, "Exigente: solo si ya dominas el 16:8"),
    ;

    val windowHours get() = 24 - fastHours
}

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

object Fast {
    fun opens(p: Profile): LocalTime = LocalTime.of(p.windowStart, 0)
    fun closes(p: Profile): LocalTime = opens(p).plusHours(p.fasting.windowHours.toLong())

    fun inWindow(p: Profile, t: LocalTime): Boolean {
        if (p.fasting == Fasting.NINGUNO) return true
        val o = opens(p)
        val c = closes(p)
        return if (o < c) t >= o && t < c else t >= o || t < c // a window that crosses midnight
    }

    fun state(s: AppState, now: LocalDateTime = LocalDateTime.now()): FastState? {
        val p = s.profile
        if (p.fasting == Fasting.NINGUNO) return null
        val o = opens(p)
        val c = closes(p)
        val eating = inWindow(p, now.toLocalTime())
        val target = if (eating) c else o
        var next = now.toLocalDate().atTime(target)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val lastMeal = s.meals.maxOfOrNull { it.time }?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        val windowClosed = now.toLocalDate().atTime(c).let { if (it.isAfter(now)) it.minusDays(1) else it }
        // The fast starts at the last meal if it was in (or after) the last window; otherwise when the window closed.
        val lastWindowOpened = windowClosed.minusHours(p.fasting.windowHours.toLong())
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
        if (p.fasting == Fasting.NINGUNO) return listOf(Slot.DESAYUNO, Slot.ALMUERZO, Slot.SNACK, Slot.CENA)
        val inside = usual.filter { (_, h) -> inWindow(p, LocalTime.of(h, 0)) }.keys.toList()
        // A short window that misses every usual hour still gets a first meal and a last one.
        return inside.ifEmpty { listOf(Slot.ALMUERZO, Slot.CENA) }
    }

    fun format(minutes: Long): String = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
