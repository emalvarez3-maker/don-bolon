package ec.donbolon.app.data

import ec.donbolon.app.ai.Ai
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The portion bench's prompt variants: each adds its own section and keeps the JSON contract; null changes nothing. */
class PromptVariantTest {
    @Test fun noVariantKeepsTodaysPrompt() {
        assertEquals(Ai.ANALYZE_SYSTEM, Ai.analyzeSystem(null))
        assertEquals(Ai.ANALYZE_SYSTEM, Ai.analyzeSystem("desconocida"))
    }

    @Test fun anchorsAddsTheTableAndTheNoRoundingDownRule() {
        val p = Ai.analyzeSystem("anchors")
        assertTrue(p.startsWith(Ai.ANALYZE_SYSTEM))
        assertTrue(p.contains("ANCLAS DE PORCIÓN"))
        assertTrue(p.contains("Arroz blanco cocido: 1 taza = 158 g"))
        assertTrue(p.contains("CÓMO SE VE 1 TAZA"))
        assertTrue(p.contains("no redondees hacia abajo"))
        assertFalse(p.contains("RAZONAMIENTO POR ALIMENTO"))
    }

    @Test fun stepsAddsTheOrderAndTheSameTable() {
        val p = Ai.analyzeSystem("steps")
        assertTrue(p.startsWith(Ai.ANALYZE_SYSTEM))
        assertTrue(p.contains("RAZONAMIENTO POR ALIMENTO"))
        assertTrue(p.contains("a) Con la regla de escala"))
        assertTrue(p.contains("c) Sus gramos"))
        assertTrue(p.contains("ANCLAS DE PORCIÓN"))
        assertTrue(p.contains("SOLO el JSON"))
    }

    @Test fun bothVariantsShareOneAnchorTable() {
        val table = { p: String -> p.substringAfter("ANCLAS DE PORCIÓN").substringBefore("Cómo usar").substringBefore("RAZONAMIENTO") }
        assertEquals(table(Ai.analyzeSystem("anchors")), table(Ai.analyzeSystem("steps")))
    }

    @Test fun aVariantAnswerStillParses() {
        val raw = """{"items":[{"name":"arroz","grams":180,"kcal":234,"protein":4.3,"carbs":51,"fat":0.5,"fiber":0.7}],
            "confidence":"media","question":"","verdict":"Bien.","better":""}"""
        val a = Ai.parseAnalysis(raw)
        assertEquals(1, a.items.size)
        assertEquals(180.0, a.items[0].grams, 0.01)
    }
}
