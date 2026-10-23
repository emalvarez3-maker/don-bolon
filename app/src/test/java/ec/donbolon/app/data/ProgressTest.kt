package ec.donbolon.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProgressTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val me = Profile(sex = Sex.HOMBRE, age = 25, heightCm = 172.0, weightKg = 80.0, priorities = Priorities(0.9, 0.8, 0.4, 0.6, 0.5))

    private fun meal(day: LocalDate, kcal: Double) = Meal(
        "m$day$kcal", day.atTime(13, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), Slot.ALMUERZO,
        listOf(FoodItem("Comida", 300.0, kcal, 30.0, 50.0, 10.0)),
    )

    @Test fun projectionReachesTheGoalAndSlowsDown() {
        val p = Progress.projection(AppState(profile = me.copy(goalWeightKg = 75.0)), today)
        assertNotNull(p.reachesGoal)
        assertEquals(75.0, p.points.last().value, 0.001)
        // A smaller body burns less, so later weeks lose less than early ones.
        val pts = p.points
        val early = pts[0].value - pts[1].value
        val late = pts[pts.size - 3].value - pts[pts.size - 2].value
        assertTrue("early $early > late $late", early > late)
    }

    @Test fun projectionWithoutGoalShowsTwelveWeeks() {
        val p = Progress.projection(AppState(profile = me), today)
        assertNull(p.reachesGoal)
        assertEquals(13, p.points.size)
        assertTrue(p.points.last().value < 80.0)
    }

    @Test fun goalAboveCurrentWeightIsNotReachedWhileCutting() {
        val p = Progress.projection(AppState(profile = me.copy(goalWeightKg = 85.0)), today, maxWeeks = 20)
        assertNull(p.reachesGoal)
    }

    @Test fun fatCurveAddsTheDeficitOfLoggedDaysOnly() {
        val tdee = Nutrition.targets(me).tdee
        val s = AppState(profile = me, meals = listOf(meal(today.minusDays(3), tdee - 770.0), meal(today.minusDays(1), tdee - 770.0)))
        val curve = Progress.fatCurve(s, 10, today)
        assertEquals(2, curve.size)
        assertEquals(0.2, curve.last().value, 0.001) // 2 × 770 kcal ≈ 0.2 kg
    }

    @Test fun todayIsNotCountedUntilItEnds() {
        val s = AppState(profile = me, meals = listOf(meal(today, 500.0)))
        assertTrue(Progress.fatCurve(s, 10, today).isEmpty())
    }

    @Test fun calendarMarksEachDay() {
        val t = Nutrition.targets(me)
        val s = AppState(profile = me, meals = listOf(meal(today.minusDays(1), t.kcal.toDouble()), meal(today.minusDays(2), t.kcal * 1.5), meal(today.minusDays(3), t.kcal * 0.5)))
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
        val tips = Science.tips(AppState(profile = me), Nutrition.targets(me))
        assertTrue(tips.none { (it.source ?: "").contains("duplican") || it.text.contains("duplican") })
    }

    @Test fun goalDateReadsNaturally() {
        assertEquals("en 6 semanas", Progress.humanWeeks(today, today.plusWeeks(6)))
        assertEquals("en 3 meses", Progress.humanWeeks(today, today.plusWeeks(13)))
    }
}
