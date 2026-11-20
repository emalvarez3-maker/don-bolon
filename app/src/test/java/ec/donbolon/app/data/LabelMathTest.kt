package ec.donbolon.app.data

import ec.donbolon.app.ai.Label
import ec.donbolon.app.ui.LabelMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelMathTest {
    private val col = Per100(kcal = 400.0, protein = 10.0, carbs = 60.0, fat = 12.0, fiber = 4.0)

    @Test fun per100ScalesByGrams() {
        val f = assertNotNull(LabelMath.item(Label("Galletas", col, 30.0, Per100(999.0, 0.0, 0.0, 0.0)), 50.0))
        assertEquals("Galletas", f.name)
        assertEquals(50.0, f.grams, 1e-9)
        assertEquals(200.0, f.kcal, 1e-9)
        assertEquals(5.0, f.protein, 1e-9)
        assertEquals(30.0, f.carbs, 1e-9)
        assertEquals(6.0, f.fat, 1e-9)
        assertEquals(2.0, f.fiber, 1e-9)
    }

    @Test fun perServingOnlyConvertsThroughTheServingGrams() {
        // 400 kcal per 40 g serving = 10 kcal per gram.
        val label = Label("Barra", null, 40.0, col)
        assertTrue(LabelMath.usable(label))
        val f = assertNotNull(LabelMath.item(label, 20.0))
        assertEquals(200.0, f.kcal, 1e-9)
        assertEquals(5.0, f.protein, 1e-9)
        assertEquals(30.0, f.carbs, 1e-9)
        assertEquals(6.0, f.fat, 1e-9)
    }

    @Test fun neitherColumnUsableGivesNull() {
        val noServing = Label("Barra", null, null, col)
        assertFalse(LabelMath.usable(noServing))
        assertNull(LabelMath.item(noServing, 50.0))
        assertNull(LabelMath.item(Label("Nada", null, 30.0, null), 50.0))
    }

    @Test fun nonPositiveGramsGiveNull() {
        val label = Label("Galletas", col, null, null)
        assertNull(LabelMath.item(label, 0.0))
        assertNull(LabelMath.item(label, -10.0))
        assertNull(LabelMath.item(label, Double.NaN))
    }

    @Test fun blankNameGetsAFallback() {
        assertEquals("Producto", LabelMath.item(Label("  ", col, null, null), 100.0)?.name)
    }

    private fun <T> assertNotNull(x: T?): T { assertTrue(x != null); return x!! }
}
