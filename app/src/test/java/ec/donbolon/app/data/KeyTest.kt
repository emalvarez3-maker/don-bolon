package ec.donbolon.app.data

import ec.donbolon.app.ui.cleanKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** Made-up keys only: these tests check the shape, never real credentials. */
class KeyTest {
    private val classic = "AIza" + "X".repeat(35)
    private val newer = "AQ." + "Ab8" + "Y".repeat(40)

    @Test fun classicGeminiKeySurvivesClipboardNoise() {
        assertEquals(classic, cleanKey(Provider.GEMINI, "  \"$classic\"\n"))
        assertEquals(classic, cleanKey(Provider.GEMINI, "key=$classic"))
    }

    @Test fun newerGeminiKeyFormatIsAccepted() {
        assertEquals(newer, cleanKey(Provider.GEMINI, newer))
        // Wrapped onto two lines by the keyboard's clipboard.
        assertEquals(newer, cleanKey(Provider.GEMINI, newer.substring(0, 20) + "\n" + newer.substring(20)))
    }

    @Test fun anthropicKeyIsExtracted() {
        val k = "sk-ant-" + "z".repeat(30)
        assertEquals(k, cleanKey(Provider.CLAUDE, "Bearer $k"))
    }
}
