package ec.donbolon.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProgressTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val sample = Profile(sex = Sex.HOMBRE, age = 25, heightCm = 172.0, weightKg = 80.0, priorities = Priorities(0.9, 0.8, 0.4, 0.6, 0.5))

    private fun meal(day: LocalDate, kcal: Double, hour: Int = 13) = Meal(
        "m$day$kcal$hour", day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), Slot.ALMUERZO,
        listOf(FoodItem("Comida", 300.0, kcal, 30.0, 50.0, 10.0)),
    )

    /** A complete-looking day: lunch and dinner adding up to [kcal]. */
    private fun day(day: LocalDate, kcal: Double) = listOf(meal(day, kcal / 2, 13), meal(day, kcal / 2, 19))

    @Test fun projectionReachesTheGoalAndSlowsDown() {
        val p = Progress.projection(AppState(profile = sample.copy(goalWeightKg = 75.0)), today)
        assertNotNull(p.reachesGoal)
        assertEquals(75.0, p.points.last().value, 0.001)
        // A smaller body burns less, so later weeks lose less than early ones.
        val pts = p.points
        val early = pts[0].value - pts[1].value
        val late = pts[pts.size - 3].value - pts[pts.size - 2].value
        assertTrue("early $early > late $late", early > late)
    }

    @Test fun projectionWithoutGoalShowsTwelveWeeks() {
        val p = Progress.projection(AppState(profile = sample), today)
        assertNull(p.reachesGoal)
        assertEquals(13, p.points.size)
        assertTrue(p.points.last().value < 80.0)
    }

    @Test fun goalAboveCurrentWeightIsNotReachedWhileCutting() {
        val p = Progress.projection(AppState(profile = sample.copy(goalWeightKg = 85.0)), today, maxWeeks = 20)
        assertNull(p.reachesGoal)
    }

    @Test fun fatCurveAddsTheDeficitOfLoggedDaysOnly() {
        val tdee = Nutrition.targets(sample).tdee
        val s = AppState(profile = sample, meals = day(today.minusDays(3), tdee - 770.0) + day(today.minusDays(1), tdee - 770.0))
        val curve = Progress.fatCurve(s, 10, today)
        assertEquals(2, curve.size)
        assertEquals(0.2, curve.last().value, 0.001) // 2 × 770 kcal ≈ 0.2 kg
    }

    @Test fun todayIsNotCountedUntilItEnds() {
        val s = AppState(profile = sample, meals = listOf(meal(today, 500.0)))
        assertTrue(Progress.fatCurve(s, 10, today).isEmpty())
    }

    @Test fun calendarMarksEachDay() {
        val t = Nutrition.targets(sample)
        val s = AppState(profile = sample, meals = listOf(meal(today.minusDays(1), t.kcal.toDouble()), meal(today.minusDays(2), t.kcal * 1.5), meal(today.minusDays(3), t.kcal * 0.5)))
        assertEquals(DayMark.IN_RANGE, Progress.mark(s, today.minusDays(1), today))
        assertEquals(DayMark.OVER, Progress.mark(s, today.minusDays(2), today))
        assertEquals(DayMark.UNDER, Progress.mark(s, today.minusDays(3), today))
        assertEquals(DayMark.EMPTY, Progress.mark(s, today.minusDays(4), today))
        assertEquals(DayMark.FUTURE, Progress.mark(s, today.plusDays(1), today))
    }

    @Test fun waistSurvivesARoundTrip() {
        val s = AppState(waist = mapOf(today to 86.5))
        assertEquals(86.5, AppState.from(JSONObject(s.toJson().toString())).waist[today]!!, 0.001)
    }

    @Test fun dishesSurviveARoundTripAndDescribeTheirScale() {
        val mine = Dish.defaults + Dish("azul", "Plato azul", Dish.Kind.PLATO, 27.0)
        val back = AppState.from(JSONObject(AppState(dishes = mine).toJson().toString()))
        assertEquals(mine, back.dishes)
        assertEquals("Plato azul, plato de 27 cm de diámetro", mine.last().describe())
        assertEquals("Bowl, bowl de 500 ml de capacidad", Dish.defaults.first { it.kind == Dish.Kind.BOWL }.describe())
        // An old diary without dishes gets the starter set, and the last dish is remembered.
        assertEquals(Dish.defaults, AppState.from(JSONObject()).dishes)
        assertEquals("azul", Settings.from(Settings(lastDish = "azul").toJson()).lastDish)
        assertNull(Settings.from(Settings(lastDish = null).toJson()).lastDish)
    }

    @Test fun everyLibraryLineCitesItsSourceAndNoneOverclaims() {
        Science.principles.forEach { p ->
            assertTrue(p.title, p.source.contains(Regex("\\(\\d{4}\\)")))
            assertTrue(p.title, p.line.length in 60..320)
        }
        val tips = Science.tips(AppState(profile = sample), Nutrition.targets(sample))
        assertTrue(tips.none { (it.source ?: "").contains("duplican") || it.text.contains("duplican") })
        // Citations checked against the abstracts (PubMed): years, numbers and what was really found.
        val all = Science.principles.joinToString(" ¶ ") { it.line + " | " + it.source } + " ¶ " + Science.COACH_PERSONA
        val by = { title: String -> Science.principles.first { it.title == title } }
        assertTrue(by("Dormir").source.contains("Al Khatib et al. (2017)"))
        assertTrue("2018" !in all.substringAfter("Al Khatib").substringBefore(")"))
        by("La báscula").line.let {
            assertTrue(it, "42,6%" in it && "6,8%" in it)
            assertTrue(it, "correos" in it) // the trial was scale + graph + weekly e-mails, not weighing alone
            assertTrue(it, "sin báscula" !in it)
        }
        by("Surfear el antojo").line.let {
            assertTrue(it, "redujo los antojos" !in it)
            assertTrue(it, "fumaron menos" in it) // Bowen & Marlatt: urges did not drop, smoking did
        }
        by("Comer tarde").line.let { assertTrue(it, "al día siguiente" !in it && "16" in it) }
        assertTrue("aunque anotaran mal" !in all)
        assertTrue("ganando músculo" !in by("La cintura cuenta").line)
        assertTrue("nunca fallar dos veces" !in all)
        val sources = tips.mapNotNull { it.source }.joinToString()
        assertTrue(sources, "se queda corta" !in sources && "2018" !in sources.substringAfter("Al Khatib", "").take(20))
    }

    @Test fun partialDaysDoNotInventFatLoss() {
        // Ten days with only a 600 kcal lunch logged: forgotten dinners, not 2.47 kg of fat.
        val s = AppState(profile = sample, meals = (1..10).map { meal(today.minusDays(it.toLong()), 600.0) })
        assertTrue(Progress.fatCurve(s, 14, today).isEmpty())
        // Two meals but under half the resting metabolism still looks partial.
        val low = AppState(profile = sample, meals = day(today.minusDays(1), 700.0))
        assertTrue(Progress.fatCurve(low, 14, today).isEmpty())
        assertTrue(Progress.looksComplete(AppState(profile = sample, meals = day(today.minusDays(1), 1800.0)), today.minusDays(1)))
    }

    @Test fun aGoalAlreadyPassedIsReachedNotMissed() {
        // Started at 80, goal 75, the trend is already 74 and the plan still cuts.
        val weights = listOf(WeightEntry(today.minusDays(120), 80.0), WeightEntry(today.minusDays(1), 74.0))
        val s = AppState(profile = sample.copy(weightKg = 74.0, goalWeightKg = 75.0), weights = weights)
        val p = Progress.projection(s, today)
        assertTrue(p.reached)
        assertEquals(today, p.reachesGoal)
        // Exactly on the goal: reached today.
        val at = Progress.projection(AppState(profile = sample.copy(goalWeightKg = 80.0)), today)
        assertTrue(at.reached)
        assertEquals(today, at.reachesGoal)
        // Still on the way: not reached yet.
        assertFalse(Progress.projection(AppState(profile = sample.copy(goalWeightKg = 75.0)), today).reached)
    }

    @Test fun goalDateReadsNaturally() {
        assertEquals("en 6 semanas", Progress.humanWeeks(today, today.plusWeeks(6)))
        assertEquals("en 3 meses", Progress.humanWeeks(today, today.plusWeeks(13)))
    }
}
