package ec.donbolon.app.data

import ec.donbolon.app.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Each test is one concrete scenario where a number or a line used to be wrong. */
class EvidenceFixesTest {
    private val today = LocalDate.now()

    private fun meal(day: LocalDate, hour: Int, kcal: Double, protein: Double = 5.0, fiber: Double = 1.0) = Meal(
        "m$day$hour$kcal", day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), Slot.forHour(hour),
        listOf(FoodItem("Comida", 200.0, kcal, protein, 40.0, 8.0, fiber)),
    )

    @Test fun trendFollowsWeeklyWeighInsInsteadOfLagging() {
        val d = LocalDate.of(2026, 9, 1)
        val w = listOf(WeightEntry(d, 80.0)) + (1..5).map { WeightEntry(d.plusWeeks(it.toLong()), 78.0) }
        val last = Nutrition.trend(w).last().second
        assertEquals(78.0, last, 0.6)
        // Daily entries keep the classic α = 0.1.
        val daily = Nutrition.trend(listOf(WeightEntry(d, 80.0), WeightEntry(d.plusDays(1), 82.0)))
        assertEquals(80.2, daily.last().second, 0.001)
    }

    @Test fun theSafetyFloorNeverBecomesASurplus() {
        val p = Profile(sex = Sex.MUJER, age = 60, heightCm = 150.0, weightKg = 45.0, activity = Activity.SEDENTARIO)
        val t = Nutrition.targets(p)
        assertTrue("tdee ${t.tdee}", t.tdee < 1200)
        assertTrue("kcal ${t.kcal} ≤ tdee ${t.tdee}", t.kcal <= t.tdee + 5)
        assertTrue(t.why.none { "Superávit" in it.text })
        assertTrue(t.why.any { "Tu gasto es bajo: mantenimiento, y consulta a un profesional antes de recortar." in it.text })
        // The activity factors are credited to what they are, not to FAO/OMS.
        assertTrue(t.why.first().source, "Harris–Benedict" in t.why.first().source && "PAL" in t.why.first().source)
        // Above the floor nothing changes: a regular woman still gets the 1200 floor or more.
        val regular = Nutrition.targets(Profile(sex = Sex.MUJER, age = 30, heightCm = 160.0, weightKg = 60.0))
        assertTrue(regular.kcal >= 1200)
    }

    @Test fun countsAreOfUnitsNotOfPortions() {
        fun kcal(s: String) = FoodDb.guess(s).single().kcal
        assertEquals(310.0, kcal("3 patacones"), 1.0)
        assertEquals(144.0, kcal("4 galletas"), 1.0)
        assertEquals(315.0, kcal("2 llapingachos"), 1.0)
        assertEquals(52.0, kcal("3 claras"), 1.0)
        assertEquals(70.0, FoodDb.guess("medio aguacate").single().grams, 0.01) // half an avocado, not a quarter
        assertEquals(140.0, FoodDb.guess("1 aguacate").single().grams, 0.01)
        assertEquals(100.0, FoodDb.guess("patacones").single().grams, 0.01) // no number: one portion
        assertEquals(100.0, FoodDb.guess("2 huevos").single().grams, 0.01) // single units still multiply
    }

    @Test fun adviceSaysItKeptTheHabitOnlyWhenItDid() {
        fun firstMealsAt(hour: Int) = (1..5).map { meal(today.minusDays(it.toLong()), hour, 400.0) }
        val kept = Fast.advise(AppState(profile = Profile(sleepHour = 23), meals = firstMealsAt(12)))
        assertEquals(12, kept.start)
        assertTrue(kept.why.any { "Empieza a la hora en que ya sueles comer por primera vez (12:00)" in it.text })

        val moved = Fast.advise(AppState(profile = Profile(sleepHour = 23), meals = firstMealsAt(7)))
        assertEquals(10, moved.start)
        assertTrue(moved.why.none { "Empieza a la hora" in it.text })
        assertTrue(moved.why.joinToString { it.text }, moved.why.any { "07:00" in it.text && "10 h" in it.text && "10:00" in it.text })
        // Vujović is about hunger during the day of late eating, not "al día siguiente"; the 3 h is a practical rule.
        val close = moved.why.first()
        assertFalse("al día siguiente" in close.text)
        assertTrue(close.source, "Regla práctica" in close.source)
    }

    @Test fun aFastingAndAWaterReminderOnTheSameMinuteBothFire() {
        val s = AppState(profile = Profile(windowHours = 8, windowStart = 12), settings = Settings(waterReminders = true))
        val now = LocalDateTime.of(2026, 10, 22, 18, 30)
        val (at, kinds) = Reminders.nextAll(s, now)!!
        assertEquals(LocalDateTime.of(2026, 10, 22, 19, 0), at)
        assertEquals(listOf(Reminders.Kind.FAST_CLOSING, Reminders.Kind.WATER), kinds)
        assertEquals(at to Reminders.Kind.FAST_CLOSING, Reminders.next(s, now))
        assertEquals(kinds, Reminders.kindsOf("FAST_CLOSING,WATER", "FAST_CLOSING"))
        assertEquals(listOf(Reminders.Kind.WATER), Reminders.kindsOf(null, "WATER")) // alarms booked by the old version
    }

    private val faster = Profile(sex = Sex.HOMBRE, age = 30, heightCm = 175.0, weightKg = 85.0, windowHours = 7, windowStart = 12)

    @Test fun noEatingTipsOnceTheWindowCloses() {
        // Window 12–19, at 20:00, two small low-fiber, low-protein meals logged.
        val s = AppState(profile = faster, meals = listOf(meal(today, 13, 400.0), meal(today, 16, 300.0)))
        val tips = Science.tips(s, Nutrition.targets(faster), LocalTime.of(20, 0)).map { it.text }
        assertTrue(tips.joinToString("\n"), tips.none { "Te quedan" in it || "Quedan" in it || "¿Te armo algo" in it })
        assertTrue(tips.none { "fruta en la cena" in it || "chochos en la siguiente" in it })
        assertTrue(tips.any { "Poca fibra" in it && "Mañana" in it })
        assertTrue(tips.any { "proteína" in it && "mañana" in it })
        // Inside the window the same day still gets the "eat this next" lines.
        val open = Science.tips(s, Nutrition.targets(faster), LocalTime.of(17, 30)).map { it.text }
        assertTrue(open.any { "Poca fibra" in it && "en la cena" in it })
    }

    @Test fun nothingLoggedWaitsTwoHoursAfterTheWindowOpens() {
        fun nags(p: Profile, at: LocalTime) = Science.tips(AppState(profile = p), Nutrition.targets(p), at)
            .any { it.mood == Mood.GRUMPY && it.action == Tip.Action.CAPTURE }
        assertFalse(nags(faster, LocalTime.of(12, 0)))
        assertFalse(nags(faster, LocalTime.of(13, 30)))
        assertTrue(nags(faster, LocalTime.of(14, 0)))
        val free = faster.copy(windowHours = 0)
        assertFalse(nags(free, LocalTime.of(10, 30)))
        assertTrue(nags(free, LocalTime.of(11, 0)))
    }
}
