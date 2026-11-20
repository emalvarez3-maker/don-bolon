package ec.donbolon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarTest {
    private val refs = """
        [
          {"m":300,"i":[["white rice",150],["chicken",120],["broccoli",30]]},
          {"m":310,"i":[["white rice",160],["chicken",120],["broccoli",30]]},
          {"m":250,"i":[["white rice",125],["chicken breast",125]]},
          {"m":200,"i":[["white rice",120],["chicken",80]]},
          {"m":180,"i":[["brown rice",180]]},
          {"m":150,"i":[["tomatoes",100],["cucumbers",50]]},
          {"m":220,"i":[["berries",120],["greek yogurt",100]]}
        ]
    """.trimIndent()
    private val es = """{"white rice":"arroz blanco","chicken":"pollo","broccoli":"brócoli","chicken breast":"pechuga de pollo","tomatoes":"tomate","cucumbers":"pepino"}"""
    private val db = Similar.parse(refs, es)

    @Test fun parsesDishesAndNames() {
        assertEquals(7, db.dishes.size)
        assertEquals(300.0, db.dishes[0].mass, 0.0)
        assertEquals("white rice" to 150.0, db.dishes[0].items[0])
        assertEquals("arroz blanco", db.es["white rice"])
    }

    @Test fun tokenMatching() {
        assertEquals("white rice", Similar.match(db, "White Rice"))
        assertEquals("white rice", Similar.match(db, "rice")) // most common rice
        assertEquals("brown rice", Similar.match(db, "brown rice, steamed"))
        assertEquals("chicken breast", Similar.match(db, "grilled chicken breast"))
        assertEquals("chicken", Similar.match(db, "roasted chicken"))
        assertEquals("tomatoes", Similar.match(db, "tomato"))
        assertEquals("cucumbers", Similar.match(db, "sliced cucumber"))
        assertEquals("berries", Similar.match(db, "mixed berry"))
        assertEquals("broccoli", Similar.match(db, "Bróccoli!"))
        assertNull(Similar.match(db, "bolón de verde"))
        assertNull(Similar.match(db, "  "))
    }

    @Test fun deterministicOrderAndDistinctSets() {
        val foods = listOf("rice" to 150.0, "grilled chicken" to 120.0, "broccoli" to 30.0)
        val a = Similar.nearest(db, foods)
        val b = Similar.nearest(db, foods.reversed())
        assertEquals(a, b)
        assertEquals(3, a.size)
        // Dish 0 and 1 share an identical ingredient set: only the better one (0, identical shares) is kept.
        assertEquals(db.dishes[0], a[0])
        assertTrue(db.dishes[1] !in a)
        assertEquals(a.size, a.map { d -> d.items.map { it.first }.toSet() }.toSet().size)
    }

    @Test fun kLimitsResults() {
        val foods = listOf("white rice" to 150.0, "chicken" to 120.0)
        assertEquals(1, Similar.nearest(db, foods, k = 1).size)
        assertTrue(Similar.nearest(db, foods, k = 0).isEmpty())
    }

    @Test fun unusualFoodGivesNoHint() {
        assertNull(Similar.hint(db, listOf("bolón de verde" to 200.0, "fried cheese" to 50.0)))
        // Mostly unknown food: rice is only 10 % of the plate, below the threshold against every dish.
        assertNull(Similar.hint(db, listOf("encebollado" to 450.0, "white rice" to 50.0)))
        assertNull(Similar.hint(db, emptyList()))
    }

    @Test fun hintIsSpanishAndShort() {
        val h = Similar.hint(db, listOf("salad tomato" to 90.0, "cucumber" to 60.0))
        assertNotNull(h)
        h!!
        assertTrue(h, h.startsWith("Platos parecidos pesados en laboratorio (referencia de tamaño): 1) "))
        assertTrue(h, h.contains("tomate 100 g, pepino 50 g (total 150 g)"))
        assertTrue(h.length <= Similar.MAX_HINT)
        val h2 = Similar.hint(db, listOf("rice" to 150.0, "chicken" to 120.0, "broccoli" to 30.0))!!
        assertTrue(h2, h2.contains("1) arroz blanco 150 g, pollo 120 g, brócoli 30 g (total 300 g)"))
        assertTrue(h2.endsWith("."))
    }

    @Test fun longHintIsShrunk() {
        val many = (1..30).joinToString(",") { """["ingredient number $it with a long name",${100 - it}]""" }
        val big = Similar.parse("""[{"m":2000,"i":[$many]},{"m":1900,"i":[$many,["salt",5]]}]""", "{}")
        val foods = (1..30).map { "ingredient number $it with a long name" to (100.0 - it) }
        val h = Similar.hint(big, foods)!!
        assertTrue(h.length <= Similar.MAX_HINT)
    }
}
