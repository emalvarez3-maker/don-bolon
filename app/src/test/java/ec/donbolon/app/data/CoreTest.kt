package ec.donbolon.app.data

import ec.donbolon.app.ai.Ai
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class NutritionTest {
    private val esteban = Profile(sex = Sex.HOMBRE, age = 25, heightCm = 172.0, weightKg = 80.0, activity = Activity.LIGERO, goal = Goal.DEFINIR)

    @Test fun mifflinStJeorMatchesTheFormula() {
        // 10·80 + 6.25·172 − 5·25 + 5 = 1755
        assertEquals(1755.0, Nutrition.bmr(esteban), 0.01)
    }

    @Test fun absPresetIsASmallDeficitWithHighProtein() {
        val t = Nutrition.targets(esteban)
        assertTrue("deficit", t.kcal < t.tdee)
        val pct = (t.tdee - t.kcal).toDouble() / t.tdee
        assertTrue("recomposition deficit is gentle: $pct", pct in 0.08..0.15)
        assertTrue("protein ≥ 2 g/kg: ${t.proteinPerKg}", t.proteinPerKg in 2.0..2.4)
        assertTrue("macros add up", abs(t.protein * 4 + t.carbs * 4 + t.fat * 9 - t.kcal) < 60)
    }

    @Test fun fatLossNeverGoesBelowTheSafetyFloor() {
        val small = Profile(sex = Sex.MUJER, age = 40, heightCm = 150.0, weightKg = 48.0, activity = Activity.SEDENTARIO, priorities = Priorities(1.0, 0.0, 0.0, 0.0, 0.0))
        val t = Nutrition.targets(small)
        assertTrue(t.kcal >= 1200)
    }

    @Test fun deficitIsCappedAtOnePercentOfBodyWeightPerWeek() {
        val t = Nutrition.targets(esteban.copy(priorities = Priorities(1.0, 0.0, 0.0, 0.0, 0.0)))
        assertTrue(-t.weeklyChangeKg <= 80 * 0.01 + 0.01)
    }

    @Test fun surplusIsCappedForMuscleGain() {
        val t = Nutrition.targets(esteban.copy(priorities = Priorities(0.0, 1.0, 0.0, 0.0, 0.0)))
        assertTrue(t.kcal > t.tdee)
        assertTrue("surplus ≤ 1.5%/month", t.kcal - t.tdee <= 80 * 0.015 * 7700 / 30 + 10)
    }

    @Test fun trendSmoothsDailyNoise() {
        val d = LocalDate.of(2026, 10, 1)
        val w = listOf(WeightEntry(d, 80.0), WeightEntry(d.plusDays(1), 82.0), WeightEntry(d.plusDays(2), 80.0))
        val tr = Nutrition.trend(w)
        assertEquals(80.2, tr[1].second, 0.001)
        assertTrue(tr.last().second < 80.4)
    }

    @Test fun streakCountsBackFromToday() {
        val today = LocalDate.of(2026, 10, 22)
        assertEquals(3, Nutrition.streak(setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4)), today))
        assertEquals(1, Nutrition.streak(setOf(today.minusDays(1)), today))
    }
}

class FoodDbTest {
    @Test fun readsTodaysBreakfastDescription() {
        val items = FoodDb.guess("120 g verde majado, 1 huevo frito, cafe con leche, 100 g lomo en salsa, 120 g arroz, 100 g ensalada")
        assertEquals(listOf("Verde majado", "Huevo frito", "Café con leche y azúcar", "Lomo en salsa", "Arroz blanco cocido", "Ensalada verde"), items.map { it.name })
        assertEquals(120.0, items[0].grams, 0.01)
        assertEquals(100.0, items[3].grams, 0.01)
    }

    @Test fun conIsNotASeparatorButYIs() {
        val items = FoodDb.guess("arroz con menestra y 200 g de carne")
        assertEquals(listOf("Arroz con menestra", "Carne de res magra"), items.map { it.name })
        assertEquals(200.0, items[1].grams, 0.01)
    }

    @Test fun countsMultiplyThePortion() {
        val items = FoodDb.guess("2 huevos y un bolon")
        assertEquals(100.0, items.first { it.name == "Huevo" }.grams, 0.01)
        assertEquals(1, items.count { it.name.startsWith("Bolón") })
    }

    @Test fun portionsScaleEveryNutrient() {
        val egg = FoodDb.all.first { it.name == "Huevo" }.item(50.0).withGrams(100.0)
        assertEquals(143.0, egg.kcal, 0.01)
        assertEquals(12.6, egg.protein, 0.01)
    }
}

class MemoryTest {
    private fun meal(day: LocalDate, vararg items: FoodItem, reason: Reason? = null) = Meal(
        day.toString() + items.first().name, day.atTime(13, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        Slot.ALMUERZO, items.toList(), reason = reason,
    )

    @Test fun remembersOnlyNewFacts() {
        var s = Memory.remember(AppState(), listOf("No come cerdo", "Entrena a las 6 am"))
        s = Memory.remember(s, listOf("no come cerdo.", "Le cuesta la cena"))
        assertEquals(listOf("No come cerdo", "Entrena a las 6 am", "Le cuesta la cena"), s.memories.map { it.text })
    }

    @Test fun detectsPortionCorrections() {
        val ai = listOf(FoodItem("Arroz", 200.0, 260.0, 5.0, 56.0, 0.5), FoodItem("Pollo", 150.0, 250.0, 46.0, 0.0, 5.0))
        val saved = listOf(ai[0].withGrams(150.0), ai[1].withGrams(155.0))
        val fixes = Memory.corrections(ai, saved)
        assertEquals(1, fixes.size)
        assertEquals("Arroz", fixes[0].food)
    }

    @Test fun analysisContextCarriesUsualPortionsAndCalibration() {
        val today = LocalDate.now()
        val bolon = FoodItem("Bolón de verde", 180.0, 470.0, 7.0, 61.0, 22.0)
        val s = AppState(
            meals = listOf(meal(today.minusDays(1), bolon), meal(today.minusDays(2), bolon)),
            corrections = listOf(Correction("Arroz", 200.0, 150.0, 0)),
        )
        val ctx = Memory.forAnalysis(s)
        assertTrue(ctx.contains("Bolón de verde: ~180 g"))
        assertTrue(ctx.contains("Arroz") && ctx.contains("×0.75"))
    }

    @Test fun weeklyFocusPicksTheWeakestHabit() {
        val today = LocalDate.of(2026, 10, 26) // a Monday
        val low = FoodItem("Pan", 100.0, 900.0, 5.0, 150.0, 20.0)
        // Logged every day last week, in calorie range never, almost no protein.
        val meals = (1..7).map { meal(today.minusDays(it.toLong()), low) }
        val p = Profile(weightKg = 80.0, heightCm = 172.0, age = 25, priorities = Priorities(0.9, 0.0, 0.4, 0.6, 0.5))
        val f = Memory.weeklyFocus(AppState(profile = p, meals = meals), Nutrition.targets(p), today)
        assertNotNull(f)
        assertEquals(today, f!!.weekStart)
        assertEquals("Proteína en cada comida", f.title)
    }
}

class PersistenceTest {
    @Test fun stateSurvivesARoundTrip() {
        val s = AppState(
            memories = listOf(MemoryNote("1", "No come cerdo", 5)),
            corrections = listOf(Correction("Arroz", 200.0, 150.0, 7)),
            focus = Focus(LocalDate.of(2026, 10, 19), "Entrenar 4 veces", "Si es lunes…", "La semana pasada…"),
            workouts = setOf(LocalDate.of(2026, 10, 20)),
            profile = Profile(priorities = Priorities(0.9, 0.8, 0.4, 0.6, 0.5), onboarded = true),
        )
        val back = AppState.from(JSONObject(s.toJson().toString()))
        assertEquals(s.memories, back.memories)
        assertEquals(s.corrections, back.corrections)
        assertEquals(s.focus, back.focus)
        assertEquals(s.workouts, back.workouts)
        assertEquals(s.profile.priorities, back.profile.priorities)
    }

    @Test fun parsesAiAnalysisEvenWrappedInMarkdown() {
        val raw = "```json\n{\"items\":[{\"name\":\"Bolón\",\"grams\":180,\"kcal\":470,\"protein\":7,\"carbs\":61,\"fat\":22,\"fiber\":4}],\"confidence\":\"alta\",\"question\":\"\",\"verdict\":\"Hmpf.\",\"better\":\"\"}\n```"
        val a = Ai.parseAnalysis(raw)
        assertEquals(1, a.items.size)
        assertEquals(470.0, a.items[0].kcal, 0.01)
        assertEquals("alta", a.confidence)
    }
}

class PlannerTest {
    @Test fun aPlannedDayLandsNearTheTarget() {
        val t = Nutrition.targets(Profile(weightKg = 80.0, heightCm = 172.0, age = 25))
        repeat(20) { seed ->
            val day = Planner.day(t, kotlin.random.Random(seed))
            val kcal = day.sumOf { it.totals.kcal }
            assertTrue("seed $seed: $kcal vs ${t.kcal}", abs(kcal - t.kcal) / t.kcal < 0.25)
        }
    }
}
