package ec.donbolon.app.data

import ec.donbolon.app.ai.Ai
import ec.donbolon.app.ai.AiException
import ec.donbolon.app.ai.Analysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RefFlowTest {
    private fun analysis(vararg items: Pair<String, Double>, en: List<String> = emptyList()) = Analysis(
        items = items.map { (n, g) -> FoodItem(n, g, g * 1.3, 1.0, 1.0, 1.0, 0.0) },
        confidence = "media", verdict = "", better = "", question = "", en = en,
    )

    @Test fun parseReadsEnglishNamesAlignedWithItems() {
        val raw = """{"items":[
            {"name":"arroz","en":"white rice","grams":180,"kcal":234},
            {"name":"nada","en":"nothing","grams":0,"kcal":0},
            {"name":"huevo frito","grams":50,"kcal":90}]}"""
        val a = Ai.parseAnalysis(raw)
        assertEquals(listOf("arroz", "huevo frito"), a.items.map { it.name })
        assertEquals(listOf("white rice", ""), a.en)
    }

    @Test fun parseWithoutEnglishStillWorks() {
        val a = Ai.parseAnalysis("""{"items":[{"name":"arroz","grams":180,"kcal":234,"en":null}],"confidence":"alta"}""")
        assertEquals(1, a.items.size)
        assertEquals(listOf(""), a.en)
        assertEquals("alta", a.confidence)
    }

    @Test fun englishNameIsOneShortLine() {
        val a = Ai.parseAnalysis("""{"items":[{"name":"arroz","en":"white\nrice ${"x".repeat(100)}","grams":180,"kcal":234}]}""")
        assertTrue(a.en[0].startsWith("white rice"))
        assertTrue(a.en[0].length <= 60)
    }

    @Test fun promptAsksForEnglishNames() {
        assertTrue(Ai.ANALYZE_SYSTEM.contains("\"en\":\"short English name"))
    }

    @Test fun foodsUseEnglishOrFallBackToSpanishAndSkipZeroGrams() {
        val a = analysis("arroz" to 180.0, "menestra" to 120.0, "salsa" to 0.0, en = listOf("white rice", ""))
        assertEquals(listOf("white rice" to 180.0, "menestra" to 120.0), Ai.referenceFoods(a))
    }

    @Test fun rulerAppendsHintAndRule() {
        assertEquals("Regla. Pista. " + Ai.REFERENCE_RULE, Ai.referenceRuler("Regla.", " Pista. "))
        assertEquals("Pista. " + Ai.REFERENCE_RULE, Ai.referenceRuler(null, "Pista."))
        assertTrue(Ai.REFERENCE_RULE == "Úsalos para calibrar los gramos, sin copiarlos: tu plato puede ser más grande o más chico.")
    }

    @Test fun withHintTheSecondPassWinsAndGetsTheHint() {
        val one = analysis("arroz" to 180.0, en = listOf("white rice"))
        val two = analysis("arroz" to 220.0)
        var seenFoods: List<Pair<String, Double>>? = null
        var seenHint: String? = null
        val r = Ai.withReferences({ one }, { seenFoods = it; "Platos parecidos: A 300 g." }, { seenHint = it; two })
        assertSame(two, r)
        assertEquals(listOf("white rice" to 180.0), seenFoods)
        assertEquals("Platos parecidos: A 300 g.", seenHint)
    }

    @Test fun noHintReturnsFirstWithoutSecondPass() {
        val one = analysis("arroz" to 180.0)
        var calls = 0
        assertSame(one, Ai.withReferences({ one }, { null }, { calls++; analysis() }))
        assertSame(one, Ai.withReferences({ one }, { "  " }, { calls++; analysis() }))
        assertSame(one, Ai.withReferences({ one }, { throw IllegalStateException("db") }, { calls++; analysis() }))
        assertEquals(0, calls)
    }

    @Test fun noFoodsSkipsTheLookup() {
        val one = analysis()
        var looked = false
        assertSame(one, Ai.withReferences({ one }, { looked = true; "x" }, { analysis() }))
        assertTrue(!looked)
    }

    @Test fun secondPassFailureFallsBackToFirst() {
        val one = analysis("arroz" to 180.0)
        assertSame(one, Ai.withReferences({ one }, { "pista" }, { throw AiException("Gemini no respondió.") }))
    }

    @Test fun firstPassFailureThrows() {
        val e = runCatching { Ai.withReferences({ throw AiException("Sin IA configurada") }, { "x" }, { analysis() }) }.exceptionOrNull()
        assertEquals("Sin IA configurada", e?.message)
    }
}
