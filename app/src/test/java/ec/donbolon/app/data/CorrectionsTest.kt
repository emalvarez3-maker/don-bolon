package ec.donbolon.app.data

import ec.donbolon.app.ui.CaptureRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionsTest {
    private fun f(name: String, g: Double) = FoodItem(name, g, g * 2, g * 0.2, 0.0, 0.0)

    @Test fun duplicateFoodsSavedUnchangedAreNotCorrections() {
        val ai = listOf(f("Presa de pollo", 120.0), f("presa de pollo", 80.0), f("Arroz", 150.0))
        assertTrue(Memory.corrections(ai, ai).isEmpty())
    }

    @Test fun duplicateFoodsWithOneRemovedAreNotCorrections() {
        val ai = listOf(f("presa de pollo", 120.0), f("presa de pollo", 80.0))
        // The 120 g piece was removed; the 80 g one stays as it was.
        assertTrue(Memory.corrections(ai, listOf(f("presa de pollo", 80.0))).isEmpty())
    }

    @Test fun alignedListsCompareByPosition() {
        val ai = listOf(f("presa de pollo", 120.0), f("presa de pollo", 80.0))
        val saved = listOf(f("presa de pollo", 120.0), f("presa de pollo", 40.0))
        val fixes = Memory.corrections(ai, saved)
        assertEquals(1, fixes.size)
        assertEquals(80.0, fixes[0].aiGrams, 0.0)
        assertEquals(40.0, fixes[0].userGrams, 0.0)
    }

    @Test fun aRealFixIsKept() {
        val fixes = Memory.corrections(listOf(f("Arroz", 200.0), f("Menestra", 100.0)), listOf(f("arroz", 150.0), f("Menestra", 100.0)))
        assertEquals(1, fixes.size)
        assertEquals("Arroz", fixes[0].food)
        assertEquals(200.0, fixes[0].aiGrams, 0.0)
        assertEquals(150.0, fixes[0].userGrams, 0.0)
    }

    @Test fun smallChangesRemovedAndAddedItemsAreIgnored() {
        val ai = listOf(f("Arroz", 200.0), f("Huevo", 50.0))
        assertTrue(Memory.corrections(ai, listOf(f("Arroz", 210.0))).isEmpty())
        assertTrue(Memory.corrections(ai, listOf(f("Cola", 350.0), f("Huevo", 50.0), f("Arroz", 200.0))).isEmpty())
        assertTrue(Memory.corrections(ai, emptyList()).isEmpty())
    }

    @Test fun misalignedListsUseEachAiItemOnce() {
        val ai = listOf(f("pollo", 120.0), f("pollo", 80.0))
        // An item was added, so positions no longer line up; each AI piece pairs with its nearest saved one.
        val saved = listOf(f("cola", 300.0), f("pollo", 80.0), f("pollo", 60.0))
        val fixes = Memory.corrections(ai, saved)
        assertEquals(1, fixes.size)
        assertEquals(120.0, fixes[0].aiGrams, 0.0)
        assertEquals(60.0, fixes[0].userGrams, 0.0)
    }
}

class CaptureRulesTest {
    private fun f(name: String, g: Double) = FoodItem(name, g, g, 0.0, 0.0, 0.0)

    @Test fun clarificationsAccumulateWithoutBlanksOrRepeats() {
        var list = CaptureRules.clarify(emptyList(), "¿Frito o al horno?", "frito")
        list = CaptureRules.clarify(list, "¿Con qué aceite?", "  ")
        list = CaptureRules.clarify(list, "¿Frito o al horno?", "frito")
        list = CaptureRules.clarify(list, "¿Cuántas presas?", " dos ")
        assertEquals(listOf("¿Frito o al horno?" to "frito", "¿Cuántas presas?" to "dos"), list)
    }

    @Test fun addedItemsAreKeptAndAiItemsAreNot() {
        val items = listOf(f("arroz", 150.0), f("cola", 350.0), f("pollo", 120.0), f("ají", 15.0))
        val added = listOf(false, true, false, true)
        assertEquals(listOf(f("cola", 350.0), f("ají", 15.0)), CaptureRules.kept(items, added))
        assertEquals(listOf(f("arroz", 150.0), f("pollo", 120.0)), CaptureRules.fromAi(items, added))
    }

    @Test fun adjustedOnlyWhenTheAiPartChanged() {
        val ai = listOf(f("arroz", 150.0), f("pollo", 120.0))
        assertFalse(CaptureRules.adjusted(ai, ai + f("cola", 350.0), listOf(false, false, true)))
        assertTrue(CaptureRules.adjusted(ai, listOf(f("arroz", 100.0), f("pollo", 120.0)), listOf(false, false)))
        assertTrue(CaptureRules.adjusted(ai, listOf(f("pollo", 120.0)), listOf(false)))
    }

    @Test fun rulerLabelSaysWhatWasUsed() {
        assertEquals("medido con tu mano", CaptureRules.rulerLabel("mano"))
        assertEquals("medido con tu plato", CaptureRules.rulerLabel("plato"))
        assertEquals("tamaño estimado", CaptureRules.rulerLabel("estimado"))
        assertEquals("tamaño estimado", CaptureRules.rulerLabel(""))
    }
}
