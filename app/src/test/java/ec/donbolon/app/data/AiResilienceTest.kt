package ec.donbolon.app.data

import ec.donbolon.app.ai.Ai
import ec.donbolon.app.ai.AiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Which message the person sees when every Gemini model failed, the ensemble's median, and label parsing. */
class AiResilienceTest {

    @Test fun everyModelOutOfQuotaSaysItIsNotTheKey() {
        assertEquals(Ai.QUOTA, Ai.outcomeMessage(listOf(429, 429, 429, 429)))
    }

    @Test fun everyModelOverloadedSaysItIsNotTheKey() {
        assertEquals(Ai.OVERLOADED, Ai.outcomeMessage(listOf(503, 503, 500, 504)))
    }

    @Test fun aModelNotOfferedDoesNotHideTheOverload() {
        assertEquals(Ai.OVERLOADED, Ai.outcomeMessage(listOf(503, 503, 503, 404)))
        assertEquals(Ai.QUOTA, Ai.outcomeMessage(listOf(404, 429)))
    }

    @Test fun keyProblemsKeepTheirOwnMessage() {
        assertNull(Ai.outcomeMessage(listOf(401, 401, 401, 401)))
        assertNull(Ai.outcomeMessage(listOf(503, 401, 503, 503)))
        assertNull(Ai.outcomeMessage(listOf(429, 403)))
    }

    @Test fun mixedOrEmptyOutcomesKeepTheLastMessage() {
        assertNull(Ai.outcomeMessage(emptyList()))
        assertNull(Ai.outcomeMessage(listOf(404, 404)))
        assertNull(Ai.outcomeMessage(listOf(429, 503)))
        assertNull(Ai.outcomeMessage(listOf(503, -1)))
    }

    @Test fun medianPicksTheMiddleSample() {
        assertEquals(500.0, Ai.medianOf(listOf(900.0, 500.0, 300.0)) { it }, 0.0)
        assertEquals("b", Ai.medianOf(listOf("c" to 3.0, "a" to 1.0, "b" to 2.0)) { it.second }.first)
    }

    @Test fun medianOfTwoIsTheLowerAndOfOneIsItself() {
        assertEquals(400.0, Ai.medianOf(listOf(700.0, 400.0)) { it }, 0.0)
        assertEquals(650.0, Ai.medianOf(listOf(650.0)) { it }, 0.0)
    }

    @Test fun labelWithBothColumns() {
        val l = Ai.parseLabel(
            """{"name":"Galletas de avena","per100":{"kcal":450,"protein":7,"carbs":68,"fat":16,"fiber":4},
               "servingG":"30 g","perServing":{"kcal":135,"protein":2.1,"carbs":20.4,"fat":4.8,"fiber":1.2}}""",
        )
        assertEquals("Galletas de avena", l.name)
        assertEquals(Per100(450.0, 7.0, 68.0, 16.0, 4.0), l.per100)
        assertEquals(30.0, l.servingG!!, 0.0)
        assertEquals(135.0, l.perServing!!.kcal, 0.0)
        assertEquals(1.2, l.perServing!!.fiber, 1e-9)
    }

    @Test fun labelWithOnlyPerServing() {
        val l = Ai.parseLabel("""```json
            {"name":"Yogur","per100":null,"servingG":200,"perServing":{"kcal":160,"protein":6,"carbs":24,"fat":4.5,"fiber":null}}
            ```""")
        assertNull(l.per100)
        assertEquals(200.0, l.servingG!!, 0.0)
        assertEquals(Per100(160.0, 6.0, 24.0, 4.5, 0.0), l.perServing)
    }

    @Test fun labelWithoutServingSizeKeepsItNull() {
        val l = Ai.parseLabel("""{"name":null,"per100":{"kcal":"380 kcal","protein":"8,5"},"servingG":null,"perServing":null}""")
        assertEquals("", l.name)
        assertEquals(380.0, l.per100!!.kcal, 0.0)
        assertEquals(8.5, l.per100!!.protein, 1e-9)
        assertNull(l.servingG)
        assertNull(l.perServing)
    }

    @Test fun garbageLabelIsAnError() {
        assertThrows(AiException::class.java) { Ai.parseLabel("no veo ninguna etiqueta") }
        assertThrows(AiException::class.java) { Ai.parseLabel("{not json at all") }
        assertThrows(AiException::class.java) { Ai.parseLabel("{ esto { no } es json }") }
        assertThrows(AiException::class.java) { Ai.parseLabel("""{"name":"x","per100":null,"perServing":null}""") }
    }
}
