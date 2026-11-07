package ec.donbolon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CompositionTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val me = Profile(sex = Sex.HOMBRE, age = 25, heightCm = 172.0, weightKg = 80.0, priorities = Priorities(0.9, 0.8, 0.4, 0.6, 0.5))

    @Test fun rfmForMenMatchesTheHandCalculation() {
        assertEquals(24.0, Progress.rfm(Sex.HOMBRE, 172.0, 86.0)!!, 1e-9) // 64 − 20 × 2
        assertEquals(64.0 - 20.0 * 180.0 / 90.0, Progress.rfm(Sex.HOMBRE, 180.0, 90.0)!!, 1e-9)
    }

    @Test fun rfmForWomenAddsTwelvePoints() {
        assertEquals(32.0, Progress.rfm(Sex.MUJER, 165.0, 75.0)!!, 1e-9) // 76 − 20 × 2.2
        assertEquals(12.0, Progress.rfm(Sex.MUJER, 172.0, 86.0)!! - Progress.rfm(Sex.HOMBRE, 172.0, 86.0)!!, 1e-9)
    }

    @Test fun rfmIsClampedToAPlausibleRange() {
        assertEquals(Progress.RFM_MIN_PCT, Progress.rfm(Sex.HOMBRE, 180.0, 50.0)!!, 1e-9) // 64 − 72 = −8
        assertEquals(Progress.RFM_MAX_PCT, Progress.rfm(Sex.MUJER, 150.0, 200.0)!!, 1e-9) // 76 − 15 = 61
    }

    @Test fun invalidInputsGiveNull() {
        assertNull(Progress.rfm(Sex.HOMBRE, 0.0, 86.0))
        assertNull(Progress.rfm(Sex.HOMBRE, 172.0, 0.0))
        assertNull(Progress.rfm(Sex.HOMBRE, -172.0, 86.0))
        assertNull(Progress.rfm(Sex.MUJER, 172.0, Double.NaN))
        assertNull(Progress.rfm(Sex.MUJER, Double.POSITIVE_INFINITY, 86.0))
        assertNull(Progress.rfm(Sex.HOMBRE, 172.0, 500.0))
    }

    @Test fun compositionPairsEachWaistWithTheClosestSmoothedWeight() {
        val s = AppState(
            profile = me,
            weights = listOf(WeightEntry(today.minusDays(30), 90.0), WeightEntry(today.minusDays(3), 80.0)),
            waist = mapOf(today.minusDays(28) to 90.0, today.minusDays(5) to 86.0, today.minusDays(250) to 100.0),
        )
        val c = Progress.composition(s, 6, today)
        assertEquals(listOf(today.minusDays(28), today.minusDays(5)), c.map { it.date }) // the old one is out of the window
        assertEquals(90.0, c[0].weightKg, 1e-9) // trend on day −30
        assertEquals(89.0, c[1].weightKg, 1e-9) // trend on day −3: 90 + 0.1 × (80 − 90)
        assertEquals(24.0, c[1].fatPct, 1e-9)
        assertEquals(89.0 * 0.24, c[1].fatKg, 1e-9)
        c.forEach { assertEquals(it.weightKg, it.fatKg + it.leanKg, 1e-9) }
    }

    @Test fun compositionUsesTheProfileWeightBeforeAnyWeighIn() {
        val c = Progress.composition(AppState(profile = me, waist = mapOf(today to 86.0)), 6, today)
        assertEquals(1, c.size)
        assertEquals(80.0, c[0].weightKg, 1e-9)
        assertEquals(19.2, c[0].fatKg, 1e-9)
    }

    @Test fun compositionIsEmptyWithoutWaist() {
        val s = AppState(profile = me, weights = listOf(WeightEntry(today.minusDays(3), 80.0)))
        assertTrue(Progress.composition(s, 6, today).isEmpty())
    }
}
