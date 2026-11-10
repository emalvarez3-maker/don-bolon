package ec.donbolon.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StoreParseTest {
    private val day = LocalDate.of(2026, 10, 23)

    private fun diary(): JSONObject {
        val meal = Meal("a", 1_000L, Slot.ALMUERZO, listOf(FoodItem("Arroz", 150.0, 195.0, 4.0, 42.0, 0.5)))
        return AppState(meals = listOf(meal, meal.copy(id = "b")), weights = listOf(WeightEntry(day, 80.0)), water = mapOf(day to 4)).toJson()
    }

    @Test fun aGoodDiaryReadsWhole() {
        val p = Store.parse(diary().toString())
        assertEquals(0, p.dropped)
        assertEquals(2, p.state.meals.size)
        assertEquals(4, p.state.water[day])
    }

    @Test fun oneBadMealDoesNotSinkTheRest() {
        val o = diary()
        o.getJSONArray("meals").put(JSONObject().put("time", 5L)) // no id: Meal.from throws
        val p = Store.parse(o.toString())
        assertEquals(1, p.dropped)
        assertEquals(listOf("a", "b"), p.state.meals.map { it.id })
        assertEquals(1, p.state.weights.size)
        assertEquals(4, p.state.water[day])
    }

    @Test fun aBadDayKeyDropsOnlyThatDay() {
        val o = diary()
        o.getJSONObject("water").put("ayer", 3)
        o.put("weights", JSONArray().put(JSONObject().put("date", "no-es-fecha").put("kg", 1.0)).put(WeightEntry(day, 79.0).toJson()))
        val p = Store.parse(o.toString())
        assertEquals(2, p.dropped)
        assertEquals(mapOf(day to 4), p.state.water)
        assertEquals(listOf(79.0), p.state.weights.map { it.kg })
        assertEquals(2, p.state.meals.size)
    }

    @Test fun notJsonAtAllFails() {
        assertTrue(runCatching { Store.parse("\u0000garbage") }.isFailure)
    }
}
