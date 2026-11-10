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
        // Without a known capacity the glass is a cylinder times its fill: π/4 · 8 · 8 · 10 · 0.8 = 402.124
        assertEquals(402.124, Portion.volumeMl(m(Shape.LIQUID, 8.0, 8.0, 10.0, fill = 0.8)), 0.01)
        // No fill said means full.
        assertEquals(350.0, Portion.volumeMl(m(Shape.LIQUID, 8.0, 8.0, 10.0), dishCapacityMl = 350.0), 0.001)
    }

    @Test fun aHalfFullGlassWithoutCapacityIsHalfItsCylinder() {
        // π/4 · 7 · 7 · 12 = 461.81 ml for the glass, half of it is 230.9.
        assertEquals(230.907, Portion.volumeMl(m(Shape.LIQUID, 7.0, 7.0, 12.0, fill = 0.5)), 0.01)
        assertEquals(461.814, Portion.volumeMl(m(Shape.LIQUID, 7.0, 7.0, 12.0, fill = 3.0)), 0.01)
    }

    @Test fun theBowlCapacityIsOnlyForTheOneLiquidInIt() {
        val bowl = Dish("b", "Bowl", Dish.Kind.BOWL, 500.0)
        // Soup measured as π/4 · 12 · 12 · 5 = 565.5 ml, within 40 % of the 500 ml bowl.
        val soup = m(Shape.LIQUID, 12.0, 12.0, 5.0, key = "sopa", fill = 0.8)
        val alone = Portion.items(listOf(soup, m(Shape.MOUND, 10.0, 10.0, 4.67, key = "arroz-cocido")), bowl)
        assertEquals(500.0 * 0.8 * Portion.density(soup), alone[0].grams, 0.01)

        // Soup and a juice glass: neither becomes the bowl, each is its own cylinder × fill.
        val juice = m(Shape.LIQUID, 7.0, 7.0, 12.0, key = "jugo", fill = 0.5)
        val both = Portion.items(listOf(soup, juice), bowl)
        assertEquals(Portion.grams(soup), both[0].grams, 0.001)
        assertEquals(230.907 * Portion.density(juice), both[1].grams, 0.01)
        assertTrue(abs(both[0].grams - both[1].grams) > 100)

        // A lone liquid far from the capacity (a 226 ml glass beside a 500 ml bowl) keeps its own cylinder.
        val small = m(Shape.LIQUID, 6.0, 6.0, 8.0, key = "jugo", fill = 0.9)
        val far = Portion.items(listOf(small), bowl)
        assertEquals(Portion.grams(small), far[0].grams, 0.001)
        assertTrue(far[0].grams < 250)

        // A plate has no capacity at all.
        val plate = Dish("p", "Plato", Dish.Kind.PLATO, 26.0)
        assertEquals(Portion.grams(soup), Portion.items(listOf(soup), plate)[0].grams, 0.001)
        assertEquals(Portion.grams(soup), Portion.items(listOf(soup), null)[0].grams, 0.001)
    }

    @Test fun aChickenPieceIsSolidButAHeapOfShreddedChickenIsNot() {
        // π/6 · 14 · 8 · 3 = 175.9 ml of breast, at 17 g per cubic inch ≈ 182 g.
        val piece = m(Shape.PIECE, 14.0, 8.0, 3.0, key = "pollo-cocido")
        val g = Portion.grams(piece)
        assertTrue("$g g", g in 170.0..195.0)
        val heap = m(Shape.MOUND, 10.0, 10.0, 4.67, key = "pollo-cocido")
        assertEquals(140.0 / 236.6, Portion.density(heap), 1e-9)
        // A food with no verified solid value falls back on the shape default for a piece.
        assertEquals(Portion.defaultDensity(Shape.PIECE), Portion.density(m(Shape.PIECE, 5.0, 3.0, 3.0, key = "yuca-cocida")), 0.0)
        assertEquals(1.08, Portion.density(m(Shape.PIECE, 6.0, 5.0, 4.0, key = "papa-cocida")), 0.0)
        // Keys without a solid value use the same density whatever the shape.
        assertEquals(17.0 / 16.387, Portion.density(m(Shape.PIECE, 6.0, 5.0, 2.0, key = "carne-res-cocida")), 1e-9)
    }

    @Test fun densityKeysAreNormalized() {
        assertEquals(158.0 / 236.6, Portion.density(m(Shape.MOUND, 1.0, 1.0, 1.0, key = " Arroz cocido ")), 1e-9)
        assertEquals(200.0 / 236.6, Portion.density(m(Shape.MOUND, 1.0, 1.0, 1.0, key = "bolón")), 1e-9)
        assertEquals(60.0 / 236.6, Portion.density(m(Shape.MOUND, 1.0, 1.0, 1.0, key = "papas_fritas")), 1e-9)
        assertEquals("patacon", Portion.normalizeKey("PATACÓN"))
        Portion.densities.keys.forEach { assertEquals(it, Portion.normalizeKey(it)) }
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

    @Test fun kcalFarBelowTheMacrosIsReplaced() {
        // Macros say 4·10 + 4·20 + 9·5 = 165 kcal; 100 is more than 20 % below, so the macros win.
        val low = Per100(kcal = 100.0, protein = 10.0, carbs = 20.0, fat = 5.0)
        val it = Portion.item(m(Shape.FLAT, 10.0, 8.0, 1.0, per100 = low))
        assertEquals(165.0 * it.grams / 100, it.kcal, 0.001)
        // A missing kcal is the macros too.
        val none = Portion.item(m(Shape.FLAT, 10.0, 8.0, 1.0, per100 = low.copy(kcal = 0.0)))
        assertEquals(165.0 * none.grams / 100, none.kcal, 0.001)
        // 140 is within 20 % below, and 300 is above the macros: both stay.
        listOf(140.0, 300.0).forEach { kcal ->
            val kept = Portion.item(m(Shape.FLAT, 10.0, 8.0, 1.0, per100 = low.copy(kcal = kcal)))
            assertEquals(kcal * kept.grams / 100, kept.kcal, 0.001)
        }
    }

    @Test fun alcoholKcalAreKept() {
        // Beer per 100 g: 43 kcal on only 16.4 kcal of macros; 330 g of it is about 142 kcal.
        val beer = Per100(kcal = 43.0, protein = 0.5, carbs = 3.6, fat = 0.0)
        val glass = m(Shape.LIQUID, 7.0, 7.0, 12.0, fill = 1.0, per100 = beer)
        val it = Portion.item(glass, dishCapacityMl = 330.0 / Portion.density(glass))
        assertEquals(330.0, it.grams, 0.001)
        assertEquals(141.9, it.kcal, 0.5)
    }

    @Test fun kcalPer100IsCappedAtPureFat() {
        val absurd = Per100(kcal = 2500.0, protein = 0.0, carbs = 0.0, fat = 100.0)
        val it = Portion.item(m(Shape.FLAT, 10.0, 8.0, 1.0, per100 = absurd))
        assertEquals(900.0 * it.grams / 100, it.kcal, 0.001)
    }

    @Test fun aSizeAboveSixtyCentimetersIsAMisread() {
        Shape.values().forEach { s ->
            assertEquals("$s", 0.0, Portion.volumeMl(m(s, 120.0, 8.0, 4.0)), 0.0)
            assertEquals("$s", 0.0, Portion.grams(m(s, 10.0, 8.0, 61.0, fill = 0.5)), 0.0)
        }
        assertTrue(Portion.volumeMl(m(Shape.FLAT, 60.0, 40.0, 1.0)) > 0)
    }

    @Test fun everyDensityCitesItsTable() {
        assertTrue(Portion.densities.size >= 25)
        Portion.densities.forEach { (key, d) ->
            assertTrue("$key has no source", d.source.isNotBlank() && "USDA" in d.source)
            assertTrue("$key: ${d.gPerMl}", d.gPerMl in 0.2..1.2)
            d.solidGPerMl?.let {
                assertTrue("$key: solid $it", it in 0.9..1.2)
                assertTrue("$key solid has no source", !d.solidSource.isNullOrBlank())
            }
        }
        listOf("pollo-cocido", "huevo-frito", "papa-cocida", "yuca-cocida", "verde-cocido", "maduro-frito", "camaron", "choclo", "patacon")
            .forEach { assertTrue("$it is not marked as bulk", Portion.densities.getValue(it).solidSource != null) }
    }

    @Test fun gramsAreNeverNegative() {
        Shape.values().forEach { s ->
            assertEquals(0.0, Portion.grams(m(s, -10.0, 8.0, 4.0, fill = -0.5), dishCapacityMl = -300.0), 0.0)
            assertEquals(0.0, Portion.grams(m(s, Double.NaN, 8.0, 4.0)), 0.0)
            assertTrue(Portion.item(m(s, 0.0, 0.0, 0.0)).grams >= 0.0)
        }
    }
}
