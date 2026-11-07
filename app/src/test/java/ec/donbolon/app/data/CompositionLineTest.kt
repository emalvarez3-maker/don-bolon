package ec.donbolon.app.data

import ec.donbolon.app.ui.compositionLine
import ec.donbolon.app.ui.fmt
import ec.donbolon.app.ui.shortSpanishDate
import ec.donbolon.app.ui.signedKg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CompositionLineTest {
    private val today = LocalDate.of(2026, 11, 7)
    private fun c(date: LocalDate, fat: Double, lean: Double) = Composition(date, fat + lean, fat / (fat + lean) * 100, fat, lean)

    @Test fun emptyGivesNoSentence() {
        assertNull(compositionLine(emptyList(), today))
    }

    @Test fun shortDateIsSpanishWithoutDot() {
        val d = shortSpanishDate(LocalDate.of(2026, 10, 12))
        assertEquals("12 oct", d)
        assertFalse(d.contains('.'))
    }

    @Test fun signedKgRoundsBeforeChoosingTheSign() {
        assertEquals("−${1.8.fmt(1)}", signedKg(-1.8))
        assertEquals("+${0.3.fmt(1)}", signedKg(0.3))
        assertEquals(0.0.fmt(1), signedKg(-0.02))
        assertEquals(0.0.fmt(1), signedKg(0.04))
    }

    @Test fun twoPointsCompareFirstAndLast() {
        val pts = listOf(c(LocalDate.of(2026, 10, 12), 20.4, 59.1), c(LocalDate.of(2026, 10, 26), 19.5, 59.2), c(today, 18.6, 59.4))
        assertEquals(
            "Desde el 12 oct: −${1.8.fmt(1)} kg de grasa, +${0.3.fmt(1)} kg de músculo",
            compositionLine(pts, today),
        )
    }

    @Test fun onePointStatesTheSplit() {
        val p = Composition(today, 78.0, 23.846, 18.6, 59.4)
        assertEquals("Hoy: ${18.6.fmt(1)} kg de grasa (24 %) y ${59.4.fmt(1)} kg magra", compositionLine(listOf(p), today))
        val older = p.copy(date = LocalDate.of(2026, 10, 12))
        assertEquals("El 12 oct: ${18.6.fmt(1)} kg de grasa (24 %) y ${59.4.fmt(1)} kg magra", compositionLine(listOf(older), today))
    }
}
