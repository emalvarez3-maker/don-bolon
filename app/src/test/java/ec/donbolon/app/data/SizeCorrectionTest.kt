package ec.donbolon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SizeCorrectionTest {
    private fun item(name: String, grams: Double, kcal: Double) = FoodItem(name, grams, kcal, kcal * 0.05, kcal * 0.1, kcal * 0.03)

    @Test fun lightPlatesAreRaisedUpToTheCeiling() {
        assertEquals(158.0, SizeCorrection.correctedKcal(100.0), 0.001)
        assertEquals(300.0, SizeCorrection.correctedKcal(250.0), 0.001) // 395 would pass the ceiling
    }

    @Test fun platesAtOrAboveTheCeilingAreNeverLowered() {
        assertEquals(300.0, SizeCorrection.correctedKcal(300.0), 0.001)
        assertEquals(850.0, SizeCorrection.correctedKcal(850.0), 0.001)
    }

    @Test fun emptyOrBrokenTotalsAreLeftAlone() {
        assertEquals(0.0, SizeCorrection.correctedKcal(0.0), 0.0)
        assertEquals(Double.NaN, SizeCorrection.correctedKcal(Double.NaN), 0.0)
    }

    @Test fun everyPortionScalesTogether() {
        val items = listOf(item("Ensalada", 100.0, 60.0), item("Pollo", 60.0, 90.0))
        val out = SizeCorrection.apply(items)
        assertEquals(237.0, out.sumOf { it.kcal }, 0.01) // 150 × 1.58
        assertEquals(158.0, out[0].grams, 0.01)
        assertEquals(94.8, out[1].grams, 0.01)
        assertEquals(out[1].kcal * 0.05, out[1].protein, 0.01)
    }

    @Test fun aBigPlateComesBackUntouched() {
        val items = listOf(item("Arroz", 250.0, 325.0), item("Menestra", 185.0, 220.0))
        assertSame(items, SizeCorrection.apply(items))
    }
}
