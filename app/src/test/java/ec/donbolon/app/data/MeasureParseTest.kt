package ec.donbolon.app.data

import ec.donbolon.app.ai.Ai
import ec.donbolon.app.ai.AiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureParseTest {
    private val good = """Aquí va: {"items":[
        {"name":"Arroz","shape":"mound","length":12,"width":9,"height":3.5,"fill":null,"densityKey":"arroz-cocido","per100":{"kcal":130,"protein":2.7,"carbs":28,"fat":0.3,"fiber":0.4}},
        {"name":"Jugo de naranjilla","shape":"LIQUID","length":7,"width":7,"height":12,"fill":0.8,"densityKey":null,"per100":{"kcal":45,"protein":0.3,"carbs":11,"fat":0.1}}],
        "reference":"La palma (8.4 cm) mide 210 px","confidence":"media","question":"","verdict":"Bien.","better":""}"""

    @Test fun readsShapesSizesAndPer100() {
        val m = Ai.parseMeasurement(good)
        assertEquals(2, m.items.size)
        val rice = m.items[0]
        assertEquals(Shape.MOUND, rice.shape)
        assertEquals(3.5, rice.heightCm, 0.0)
        assertEquals("arroz-cocido", rice.densityKey)
        assertEquals(130.0, rice.per100.kcal, 0.0)
        assertNull(rice.fill)
        assertEquals(0.8, m.items[1].fill!!, 0.0)
        assertNull("the string \"null\" or a JSON null is not a key", m.items[1].densityKey)
        assertEquals(0.0, m.items[1].per100.fiber, 0.0)
        assertTrue(m.reference.contains("palma"))
    }

    @Test fun dropsItemsItCannotMeasureInsteadOfGuessing() {
        val raw = """{"items":[
            {"name":"Sin forma","shape":"BLOB","length":5,"width":5,"height":5,"per100":{"kcal":100}},
            {"name":"Sin alto","shape":"FLAT","length":10,"width":8,"per100":{"kcal":200}},
            {"name":"Sin valores","shape":"PIECE","length":4,"width":4,"height":4},
            {"name":"","shape":"PIECE","length":4,"width":4,"height":4,"per100":{"kcal":100}},
            {"name":"Huevo frito","shape":"FLAT","length":10,"width":9,"height":1,"per100":{"kcal":196,"protein":13.6,"carbs":0.8,"fat":15}}],
            "confidence":"baja"}"""
        val m = Ai.parseMeasurement(raw)
        assertEquals(listOf("Huevo frito"), m.items.map { it.name })
        assertEquals("baja", m.confidence)
    }

    @Test(expected = AiException::class)
    fun junkIsAnError() {
        Ai.parseMeasurement("lo siento, no puedo")
    }
}
