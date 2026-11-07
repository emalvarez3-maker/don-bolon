package ec.donbolon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PortionTest {
    private val rice = Per100(kcal = 130.0, protein = 2.7, carbs = 28.2, fat = 0.3, fiber = 0.4)

    private fun m(shape: Shape, l: Double, w: Double, h: Double, key: String? = null, fill: Double? = null, per100: Per100 = rice) =
        Measured("Comida", shape, l, w, h, per100, key, fill)

    @Test fun moundIsASphericalCapOverAnEllipse() {
        // a = 5, b = 4, h = 4: π/6 · 4 · (3·20 + 16) = 159.174
        assertEquals(159.174, Portion.volumeMl(m(Shape.MOUND, 10.0, 8.0, 4.0)), 0.01)
    }

    @Test fun flatIsAnEllipticalSlab() {
        // π/4 · 10 · 8 · 1 = 62.832
        assertEquals(62.832, Portion.volumeMl(m(Shape.FLAT, 10.0, 8.0, 1.0)), 0.01)
    }

    @Test fun cylinderUsesItsFootprint() {
        // π/4 · 7 · 7 · 10 = 384.845
        assertEquals(384.845, Portion.volumeMl(m(Shape.CYLINDER, 7.0, 7.0, 10.0)), 0.01)
    }

    @Test fun pieceIsAnEllipsoid() {
        // π/6 · 12 · 6 · 4 = 150.796
        assertEquals(150.796, Portion.volumeMl(m(Shape.PIECE, 12.0, 6.0, 4.0)), 0.01)
    }

    @Test fun liquidIsCapacityTimesFill() {
        assertEquals(280.0, Portion.volumeMl(m(Shape.LIQUID, 8.0, 8.0, 10.0, fill = 0.8), dishCapacityMl = 350.0), 0.001)
        // Without a known capacity the glass is measured as a cylinder: π/4 · 8 · 8 · 10 = 502.655
        assertEquals(502.655, Portion.volumeMl(m(Shape.LIQUID, 8.0, 8.0, 10.0, fill = 0.8)), 0.01)
        assertEquals(502.655, Portion.volumeMl(m(Shape.LIQUID, 8.0, 8.0, 10.0), dishCapacityMl = 350.0), 0.01)
    }

    @Test fun aCupOfRiceAsAMoundWeighsAboutAUSDACup() {
        // 10 × 10 cm footprint, 4.67 cm tall ≈ 236.6 ml, one US cup.
        val cup = m(Shape.MOUND, 10.0, 10.0, 4.67, key = "arroz-cocido")
        assertEquals(236.6, Portion.volumeMl(cup), 236.6 * 0.02)
        val g = Portion.grams(cup)
        assertTrue("$g g", abs(g - 158.0) <= 158.0 * 0.10)
    }

    @Test fun unknownKeysFallBackToTheShapeDefault() {
        val known = m(Shape.FLAT, 10.0, 8.0, 1.0, key = "nada-que-ver")
        assertEquals(Portion.volumeMl(known) * Portion.defaultDensity(Shape.FLAT), Portion.grams(known), 0.001)
    }

    @Test fun itemScalesPer100ByTheGrams() {
        val it = Portion.item(m(Shape.MOUND, 10.0, 10.0, 4.67, key = "arroz-cocido"))
        val k = it.grams / 100
        assertEquals(130.0 * k, it.kcal, 0.001)
        assertEquals(2.7 * k, it.protein, 0.001)
        assertEquals(0.4 * k, it.fiber, 0.001)
    }

    @Test fun kcalThatDisagreesWithTheMacrosIsReplaced() {
        // Macros say 4·10 + 4·20 + 9·5 = 165 kcal; 300 is off by more than 20 %.
        val wrong = Per100(kcal = 300.0, protein = 10.0, carbs = 20.0, fat = 5.0)
        val it = Portion.item(m(Shape.FLAT, 10.0, 8.0, 1.0, per100 = wrong))
        assertEquals(165.0 * it.grams / 100, it.kcal, 0.001)
        // 180 is within 20 % of 165, so it stays.
        val close = Portion.item(m(Shape.FLAT, 10.0, 8.0, 1.0, per100 = wrong.copy(kcal = 180.0)))
        assertEquals(180.0 * close.grams / 100, close.kcal, 0.001)
    }

    @Test fun everyDensityCitesItsTable() {
        assertTrue(Portion.densities.size >= 25)
        Portion.densities.forEach { (key, d) ->
            assertTrue("$key has no source", d.source.isNotBlank() && "USDA" in d.source)
            assertTrue("$key: ${d.gPerMl}", d.gPerMl in 0.2..1.2)
        }
    }

    @Test fun gramsAreNeverNegative() {
        Shape.values().forEach { s ->
            assertEquals(0.0, Portion.grams(m(s, -10.0, 8.0, 4.0, fill = -0.5), dishCapacityMl = -300.0), 0.0)
            assertEquals(0.0, Portion.grams(m(s, Double.NaN, 8.0, 4.0)), 0.0)
            assertTrue(Portion.item(m(s, 0.0, 0.0, 0.0)).grams >= 0.0)
        }
    }
}
