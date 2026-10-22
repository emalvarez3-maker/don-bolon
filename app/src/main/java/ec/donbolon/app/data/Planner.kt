package ec.donbolon.app.data

import kotlin.random.Random

/**
 * Builds a day of meals that lands on the calorie target, the way Fitia does: fixed shares
 * per meal, each meal a realistic combo, portions scaled until the kcal fit. This is the
 * offline planner; with AI the same shape comes back from the model instead.
 */
object Planner {
    /** Calorie share per slot. Protein is spread across the day for better muscle synthesis (Areta et al., 2013). */
    val share = mapOf(Slot.DESAYUNO to 0.25, Slot.ALMUERZO to 0.35, Slot.CENA to 0.28, Slot.SNACK to 0.12)

    private val combos = mapOf(
        Slot.DESAYUNO to listOf(
            "Bolón airfryer con huevos" to listOf("Bolón de verde (airfryer)", "Huevo", "Café negro"),
            "Avena con banano y whey" to listOf("Avena en hojuelas (cruda)", "Banano", "Proteína whey"),
            "Tostadas con huevo y aguacate" to listOf("Pan integral", "Huevo", "Aguacate"),
            "Yogur griego con fruta y avena" to listOf("Yogur griego natural", "Frutillas", "Avena en hojuelas (cruda)"),
            "Tortilla de verde con queso fresco" to listOf("Tortilla de verde", "Queso fresco", "Papaya"),
        ),
        Slot.ALMUERZO to listOf(
            "Pollo a la plancha, arroz y menestra" to listOf("Pechuga de pollo a la plancha", "Arroz con menestra", "Ensalada verde"),
            "Pescado con quinua y ensalada" to listOf("Pescado blanco", "Quinua cocida", "Ensalada verde", "Aguacate"),
            "Seco de pollo con ensalada" to listOf("Seco de pollo", "Ensalada verde"),
            "Carne magra con papa y brócoli" to listOf("Carne de res magra", "Papa cocida", "Brócoli"),
            "Lentejas con arroz y huevo" to listOf("Lenteja cocida", "Arroz blanco cocido", "Huevo", "Ensalada verde"),
        ),
        Slot.CENA to listOf(
            "Ceviche de camarón con chifles medidos" to listOf("Ceviche de camarón", "Chifles"),
            "Atún con camote y ensalada" to listOf("Atún en agua", "Camote cocido", "Ensalada verde"),
            "Caldo de gallina y pan integral" to listOf("Caldo de gallina", "Pechuga de pollo a la plancha", "Pan integral"),
            "Encebollado" to listOf("Encebollado", "Chifles"),
            "Omelette de claras con verduras" to listOf("Claras de huevo", "Huevo", "Tomate", "Pan integral"),
        ),
        Slot.SNACK to listOf(
            "Yogur griego" to listOf("Yogur griego natural"),
            "Chochos con tomate" to listOf("Chocho", "Tomate"),
            "Manzana con maní" to listOf("Manzana", "Maní / nueces"),
            "Batido de whey con banano" to listOf("Proteína whey", "Banano"),
        ),
    )

    private fun ref(name: String) = FoodDb.all.first { it.name == name }

    fun meal(slot: Slot, kcal: Double, avoid: String? = null, rnd: Random = Random.Default): PlannedMeal {
        val options = combos.getValue(slot).filter { it.first != avoid }
        val (title, names) = options[rnd.nextInt(options.size)]
        val base = names.map { ref(it).item() }
        val k = (kcal / base.totals().kcal).coerceIn(0.5, 2.0)
        // Scale everything except drinks and condiments, which don't grow with appetite.
        val items = base.map { if (it.name == "Café negro") it else it.withGrams((it.grams * k / 5).let { g -> Math.round(g) * 5.0 }) }
        return PlannedMeal(slot, title, items)
    }

    fun day(targets: Targets, rnd: Random = Random.Default) =
        Slot.entries.map { meal(it, targets.kcal * share.getValue(it), rnd = rnd) }
}
