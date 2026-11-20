package ec.donbolon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun f(name: String, g: Double) = FoodItem(name, g, g * 1.3, g * 0.03, g * 0.28, g * 0.01, g * 0.004)
    private fun c(food: String, ai: Double, user: Double, daysAgo: Long = 1) = Correction(food, ai, user, now - daysAgo * day)
    private fun state(vararg cs: Correction) = AppState(corrections = cs.toList())

    private val plate = listOf(f("Arroz", 200.0), f("Menestra", 120.0))

    @Test fun noCorrectionsLeavesThePlateAlone() {
        val r = Memory.calibrate(plate, AppState(), now)
        assertEquals(plate, r.items)
        assertTrue(r.adjusted.isEmpty())
    }

    @Test fun oneCorrectionIsNotEnough() {
        val r = Memory.calibrate(plate, state(c("arroz", 200.0, 150.0)), now)
        assertEquals(plate, r.items)
        assertTrue(r.adjusted.isEmpty())
    }

    @Test fun threeCorrectionsScaleByTheMedianRatio() {
        // Ratios 0.80, 0.75, 0.833: the median is 0.80.
        val s = state(c("arroz", 200.0, 160.0, 3), c("arroz", 200.0, 150.0, 2), c("arroz", 180.0, 150.0, 1))
        val r = Memory.calibrate(plate, s, now)
        assertEquals(160.0, r.items[0].grams, 1e-9)
        assertEquals(plate[1], r.items[1])
        assertEquals(listOf("Arroz"), r.adjusted)
    }

    @Test fun nutrientsScaleWithTheGrams() {
        val s = state(c("arroz", 200.0, 160.0), c("arroz", 100.0, 80.0))
        val before = plate[0]
        val after = Memory.calibrate(plate, s, now).items[0]
        assertEquals(before.kcal * 0.8, after.kcal, 1e-9)
        assertEquals(before.protein * 0.8, after.protein, 1e-9)
        assertEquals(before.carbs * 0.8, after.carbs, 1e-9)
        assertEquals(before.fat * 0.8, after.fat, 1e-9)
        assertEquals(before.fiber * 0.8, after.fiber, 1e-9)
    }

    @Test fun extremeRatiosAreClamped() {
        val low = Memory.calibrate(plate, state(c("arroz", 100.0, 30.0), c("arroz", 200.0, 60.0)), now)
        assertEquals(200.0 * 0.6, low.items[0].grams, 1e-9)
        val high = Memory.calibrate(plate, state(c("arroz", 100.0, 300.0), c("arroz", 50.0, 150.0)), now)
        assertEquals(200.0 * 1.6, high.items[0].grams, 1e-9)
    }

    @Test fun accentsAndCaseStillMatch() {
        val s = state(c("  PLÁTANO maduro", 100.0, 150.0), c("platano Maduro", 100.0, 150.0))
        val r = Memory.calibrate(listOf(f("Plátano maduro", 100.0)), s, now)
        assertEquals(150.0, r.items[0].grams, 1e-9)
        assertEquals(listOf("Plátano maduro"), r.adjusted)
    }

    @Test fun onlyTheLastFiveCorrectionsCount() {
        val old = (10L downTo 6L).map { c("arroz", 100.0, 150.0, it) }
        val recent = (5L downTo 1L).map { c("arroz", 100.0, 80.0, it) }
        val r = Memory.calibrate(plate, state(*(old + recent).toTypedArray()), now)
        assertEquals(160.0, r.items[0].grams, 1e-9)
    }

    @Test fun correctionsOlderThan120DaysAreIgnored() {
        val s = state(c("arroz", 200.0, 150.0, 200), c("arroz", 200.0, 150.0, 130), c("arroz", 200.0, 160.0, 10))
        assertEquals(plate, Memory.calibrate(plate, s, now).items)
    }

    @Test fun forAnalysisNoLongerRepeatsTheCorrections() {
        val today = java.time.LocalDate.now()
        val t = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 60 * 60 * 1000
        val meals = (0..1).map { Meal("m$it", t - it * day, Slot.ALMUERZO, listOf(f("Arroz", 150.0))) }
        val real = System.currentTimeMillis()
        val s = AppState(meals = meals, corrections = listOf(Correction("arroz", 200.0, 150.0, real), Correction("arroz", 200.0, 160.0, real)))
        val text = Memory.forAnalysis(s)
        assertFalse(text.contains("corrige a ×"))
        assertTrue(text.contains("Suele comer Arroz"))
        assertEquals("", Memory.forAnalysis(state(c("arroz", 200.0, 150.0), c("arroz", 200.0, 160.0))))
    }
}
