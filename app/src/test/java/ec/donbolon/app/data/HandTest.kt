package ec.donbolon.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandTest {
    private val mine = Hand(palmWidthCm = 8.4, middleFingerCm = 8.1, thumbWidthCm = 2.2)

    @Test fun oneInchOfPixelsIsTwoPointFiveFourCm() {
        assertEquals(2.54, Ruler.pxToCm(416f, 416f), 1e-9)
        assertEquals(1.0, Ruler.pxToCm(416f / 2.54f, 416f), 1e-5)
        assertEquals(416f, Ruler.cmToPx(2.54, 416f), 1e-3f)
        assertEquals(163.78f, Ruler.cmToPx(1.0, 416f), 0.01f)
    }

    @Test fun pxAndCmRoundTrip() {
        for (cm in listOf(0.1, 2.2, 8.4, 13.0)) {
            assertEquals(cm, Ruler.pxToCm(Ruler.cmToPx(cm, 420.5f), 420.5f), 1e-4)
        }
        assertEquals(0.0, Ruler.pxToCm(100f, 0f), 0.0)
    }

    @Test fun handJsonRoundTrip() {
        assertEquals(mine, Hand.from(JSONObject(mine.toJson().toString())))
    }

    @Test fun brokenHandValuesFallBackToTypical() {
        val h = Hand.from(JSONObject().put("palmWidthCm", -3.0).put("middleFingerCm", 7.5))
        assertEquals(Hand.typical.palmWidthCm, h.palmWidthCm, 0.0)
        assertEquals(7.5, h.middleFingerCm, 0.0)
        assertEquals(Hand.typical.thumbWidthCm, h.thumbWidthCm, 0.0)
    }

    @Test fun describeIsSpanishWithOneDecimal() {
        assertEquals(
            "La mano de la persona mide: palma 8.4 cm de ancho, dedo medio 8.1 cm de largo, pulgar 2.2 cm de ancho.",
            mine.describe(),
        )
    }

    @Test fun typicalHandIsAdultSized() {
        with(Hand.typical) {
            assertTrue(palmWidthCm in 7.0..10.0)
            assertTrue(middleFingerCm in 6.5..10.0)
            assertTrue(thumbWidthCm in 1.5..3.0)
        }
    }

    @Test fun profileRoundTripWithHand() {
        val p = Profile(name = "x", heightCm = 172.0, hand = mine)
        val back = Profile.from(JSONObject(p.toJson().toString()))
        assertEquals(p, back)
        assertEquals(mine, back.hand)
    }

    @Test fun profileRoundTripWithoutHand() {
        val p = Profile(name = "x")
        val back = Profile.from(JSONObject(p.toJson().toString()))
        assertEquals(p, back)
        assertNull(back.hand)
    }

    @Test fun oldProfileJsonWithoutHandKeyHasNoHand() {
        val old = Profile(name = "x").toJson().apply { remove("hand") }
        assertTrue(!old.has("hand"))
        assertNull(Profile.from(old).hand)
    }
}
