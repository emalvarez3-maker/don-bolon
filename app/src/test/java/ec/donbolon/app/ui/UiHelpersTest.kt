package ec.donbolon.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiHelpersTest {
    @Test fun contrastMatchesWcagExtremes() {
        assertEquals(21.0, contrast(Color.White, Color.Black), 0.01)
        assertEquals(1.0, contrast(C.card, C.card), 0.001)
        assertEquals(contrast(C.text, C.bg), contrast(C.bg, C.text), 0.0001)
    }

    @Test fun secondaryTextReachesAaOnBgAndCard() {
        for (fg in listOf(C.faint, C.muted, C.text)) for (bg in listOf(C.bg, C.card)) {
            assertTrue("${fg} on ${bg}: ${contrast(fg, bg)}", contrast(fg, bg) >= 4.5)
        }
    }

    @Test fun macroColorsStayReadableAndOffTheRewardAndWarningColors() {
        for (c in listOf(C.protein, C.fat, C.carbs)) assertTrue(contrast(c, C.card) >= 4.5)
        assertTrue(C.protein != C.gold)
        assertTrue(C.fat != C.over)
    }

    @Test fun pluralPicksTheRightForm() {
        assertEquals("1 día registrado", plural(1, "día registrado", "días registrados"))
        assertEquals("3 alimentos", plural(3, "alimento", "alimentos"))
        assertEquals("0 alimentos", plural(0, "alimento", "alimentos"))
    }

    @Test fun pentagonGrabsOnlyNearADot() {
        val v = listOf(1.0, 0.5, 0.5, 0.5, 0.5)
        // 260×260 box: the first dot sits straight up at radius 0.68 × 130 = 88.4 px from the center.
        assertEquals(0, pentagonHandle(v, 130f, 130f - 88.4f, 260f, 260f, 28f))
        assertEquals(0, pentagonHandle(v, 140f, 130f - 80f, 260f, 260f, 28f))
        assertEquals(-1, pentagonHandle(v, 10f, 250f, 260f, 260f, 28f))
        assertEquals(-1, pentagonHandle(v, 130f, 130f, 260f, 260f, 28f))
    }

    @Test fun handMeasureParsesCommaAndRange() {
        assertEquals(11.5, parseHandCm("11,5")!!, 0.0001)
        assertEquals(19.3, parseHandCm(" 19.26 ")!!, 0.0001)
        assertEquals(4.0, parseHandCm("4")!!, 0.0001)
        assertNull(parseHandCm("3.9"))
        assertNull(parseHandCm("25.1"))
        assertNull(parseHandCm("abc"))
    }

    @Test fun onlyTheGripMovesTheRulerLine() {
        assertTrue(onGrip(1000f, 505f, 500f, 1080f, 70f, 250f))
        assertFalse(onGrip(300f, 505f, 500f, 1080f, 70f, 250f))
        assertFalse(onGrip(1000f, 700f, 500f, 1080f, 70f, 250f))
    }
}
