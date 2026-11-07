package ec.donbolon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulerChoiceTest {
    private val hand = Hand(8.4, 8.1, 2.2)
    private val plate = Dish("plato", "Plato llano", Dish.Kind.PLATO, 26.0)
    private val bowl = Dish("bowl", "Bowl", Dish.Kind.BOWL, 500.0)

    @Test fun handLeadsAndTheDishBacksItUp() {
        val s = Portion.scale(hand, plate)
        assertTrue(s.startsWith("La mano de la persona mide: palma 8.4 cm"))
        assertTrue(s.contains("regla principal"))
        assertTrue(s.contains("26 cm"))
    }

    @Test fun withoutHandOrDishItSaysSoInsteadOfPretending() {
        assertTrue(Portion.scale(null, null).contains("No hay mano"))
        assertTrue(Portion.scale(null, plate).startsWith("Está servido en"))
    }

    @Test fun onlyBowlsGlassesAndCupsHaveACapacity() {
        assertNull(Portion.capacityMl(plate))
        assertNull(Portion.capacityMl(null))
        assertEquals(500.0, Portion.capacityMl(bowl)!!, 0.0)
    }
}
