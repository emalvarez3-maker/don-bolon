package ec.donbolon.app.data

import java.text.Normalizer

/**
 * Offline food notebook: values per 100 g (USDA FoodData Central and the Ecuadorian
 * food composition table, rounded), plus a typical portion. It backs the app when there
 * is no AI key or no signal, and lets the user fix anything the AI gets wrong.
 */
data class FoodRef(
    val name: String,
    val portionG: Double,
    val portion: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val aliases: List<String> = emptyList(),
) {
    fun item(grams: Double = portionG) = FoodItem(
        name, grams, kcal * grams / 100, protein * grams / 100, carbs * grams / 100, fat * grams / 100, fiber * grams / 100,
    )
}

object FoodDb {
    val all = listOf(
        // Ecuador
        FoodRef("Bolón de verde (frito)", 180.0, "1 bolón", 260.0, 4.0, 34.0, 12.0, 2.3, listOf("bolon")),
        FoodRef("Bolón de verde (airfryer)", 170.0, "1 bolón", 190.0, 4.5, 34.0, 4.5, 2.4, listOf("bolon airfryer", "bolon horno")),
        FoodRef("Encebollado", 450.0, "1 plato", 75.0, 7.0, 7.0, 2.0, 0.8, listOf("encebollado")),
        FoodRef("Chifles", 30.0, "1 puñado", 520.0, 2.3, 58.0, 31.0, 3.5, listOf("chifle", "chifles")),
        FoodRef("Patacones", 100.0, "3 patacones", 310.0, 1.5, 48.0, 13.0, 3.0, listOf("patacon", "patacones", "tostones")),
        FoodRef("Maduro frito", 100.0, "1/2 maduro", 250.0, 1.5, 46.0, 8.0, 2.5, listOf("maduro")),
        FoodRef("Arroz blanco cocido", 180.0, "1 taza", 130.0, 2.7, 28.0, 0.3, 0.4, listOf("arroz")),
        FoodRef("Arroz con menestra", 350.0, "1 plato", 150.0, 5.5, 26.0, 2.5, 4.0, listOf("menestra", "arroz con menestra")),
        FoodRef("Seco de pollo", 400.0, "1 plato con arroz", 140.0, 9.0, 16.0, 4.5, 1.0, listOf("seco de pollo", "seco")),
        FoodRef("Ceviche de camarón", 300.0, "1 plato", 70.0, 9.0, 6.0, 1.0, 1.0, listOf("ceviche")),
        FoodRef("Hornado", 200.0, "1 porción de carne", 290.0, 25.0, 0.0, 21.0, 0.0, listOf("hornado", "cerdo horneado")),
        FoodRef("Fritada", 200.0, "1 porción", 330.0, 24.0, 0.0, 26.0, 0.0, listOf("fritada")),
        FoodRef("Llapingachos", 150.0, "2 llapingachos", 210.0, 5.0, 25.0, 10.0, 2.0, listOf("llapingacho")),
        FoodRef("Mote", 150.0, "1 taza", 120.0, 3.0, 25.0, 1.0, 3.0, listOf("mote")),
        FoodRef("Tortilla de verde", 120.0, "1 tortilla", 230.0, 4.0, 30.0, 10.0, 2.0, listOf("tortilla de verde")),
        FoodRef("Humita", 150.0, "1 humita", 190.0, 5.0, 26.0, 8.0, 2.5, listOf("humita")),
        FoodRef("Tamal", 180.0, "1 tamal", 210.0, 7.0, 22.0, 10.0, 2.0, listOf("tamal")),
        FoodRef("Empanada de viento", 90.0, "1 empanada", 390.0, 7.0, 45.0, 20.0, 1.5, listOf("empanada de viento", "empanada")),
        FoodRef("Pan de yuca", 40.0, "1 pan", 330.0, 7.0, 40.0, 15.0, 0.5, listOf("pan de yuca")),
        FoodRef("Locro de papa", 350.0, "1 plato", 85.0, 3.0, 11.0, 3.5, 1.2, listOf("locro")),
        FoodRef("Caldo de gallina", 400.0, "1 plato", 45.0, 4.5, 3.0, 1.5, 0.3, listOf("caldo")),
        FoodRef("Encocado de pescado", 350.0, "1 plato con arroz", 160.0, 9.0, 15.0, 7.0, 1.0, listOf("encocado")),
        FoodRef("Guatita", 350.0, "1 plato", 120.0, 8.0, 9.0, 6.0, 1.0, listOf("guatita")),
        FoodRef("Salchipapa", 300.0, "1 porción", 290.0, 8.0, 28.0, 16.0, 2.5, listOf("salchipapa")),
        FoodRef("Colada morada", 250.0, "1 vaso", 80.0, 0.6, 19.0, 0.2, 1.0, listOf("colada morada", "colada")),
        FoodRef("Morocho", 250.0, "1 vaso", 105.0, 3.0, 18.0, 2.5, 1.0, listOf("morocho")),
        FoodRef("Yuca cocida", 150.0, "1 trozo", 160.0, 1.4, 38.0, 0.3, 1.8, listOf("yuca")),
        FoodRef("Verde cocinado", 150.0, "1 verde", 120.0, 1.3, 31.0, 0.4, 2.3, listOf("verde cocinado", "platano verde")),
        // Proteins
        FoodRef("Pechuga de pollo a la plancha", 150.0, "1 filete", 165.0, 31.0, 0.0, 3.6, 0.0, listOf("pollo", "pechuga")),
        FoodRef("Pollo frito", 150.0, "1 presa", 260.0, 25.0, 9.0, 14.0, 0.4, listOf("pollo frito", "pollo broaster")),
        FoodRef("Carne de res magra", 150.0, "1 bistec", 200.0, 27.0, 0.0, 10.0, 0.0, listOf("carne", "res", "bistec", "lomo")),
        FoodRef("Cerdo (chuleta)", 150.0, "1 chuleta", 230.0, 26.0, 0.0, 14.0, 0.0, listOf("chuleta", "cerdo")),
        FoodRef("Pescado blanco", 150.0, "1 filete", 105.0, 23.0, 0.0, 1.2, 0.0, listOf("pescado", "tilapia", "corvina")),
        FoodRef("Atún en agua", 120.0, "1 lata escurrida", 116.0, 26.0, 0.0, 1.0, 0.0, listOf("atun")),
        FoodRef("Camarón cocido", 120.0, "1 porción", 99.0, 24.0, 0.2, 0.3, 0.0, listOf("camaron", "camarones")),
        FoodRef("Huevo", 50.0, "1 huevo", 143.0, 12.6, 0.7, 9.5, 0.0, listOf("huevo", "huevos")),
        FoodRef("Claras de huevo", 100.0, "3 claras", 52.0, 11.0, 0.7, 0.2, 0.0, listOf("clara", "claras")),
        FoodRef("Queso fresco", 40.0, "1 tajada", 260.0, 18.0, 3.0, 20.0, 0.0, listOf("queso")),
        FoodRef("Yogur griego natural", 170.0, "1 vaso", 73.0, 10.0, 3.9, 1.9, 0.0, listOf("yogur griego", "yogurt griego")),
        FoodRef("Yogur con sabor", 200.0, "1 vaso", 95.0, 3.5, 16.0, 2.0, 0.0, listOf("yogur", "yogurt")),
        FoodRef("Leche entera", 250.0, "1 vaso", 61.0, 3.2, 4.8, 3.3, 0.0, listOf("leche")),
        FoodRef("Proteína whey", 30.0, "1 scoop", 400.0, 80.0, 8.0, 6.0, 0.0, listOf("whey", "proteina en polvo", "batido de proteina")),
        FoodRef("Lenteja cocida", 200.0, "1 taza", 116.0, 9.0, 20.0, 0.4, 7.9, listOf("lenteja", "lentejas")),
        FoodRef("Fréjol cocido", 180.0, "1 taza", 127.0, 8.7, 22.8, 0.5, 6.4, listOf("frejol", "frijol", "porotos")),
        FoodRef("Chocho", 100.0, "1 taza", 119.0, 15.6, 9.9, 2.9, 4.5, listOf("chocho", "chochos")),
        // Starches
        FoodRef("Quinua cocida", 185.0, "1 taza", 120.0, 4.4, 21.3, 1.9, 2.8, listOf("quinua", "quinoa")),
        FoodRef("Avena en hojuelas (cruda)", 40.0, "1/2 taza", 389.0, 16.9, 66.0, 6.9, 10.6, listOf("avena")),
        FoodRef("Pan blanco", 30.0, "1 rebanada", 265.0, 9.0, 49.0, 3.2, 2.7, listOf("pan")),
        FoodRef("Pan integral", 35.0, "1 rebanada", 247.0, 13.0, 41.0, 3.4, 7.0, listOf("pan integral")),
        FoodRef("Papa cocida", 150.0, "1 papa mediana", 87.0, 1.9, 20.0, 0.1, 1.8, listOf("papa", "papas")),
        FoodRef("Papas fritas", 120.0, "1 porción", 312.0, 3.4, 41.0, 15.0, 3.8, listOf("papas fritas", "papitas")),
        FoodRef("Fideo / pasta cocida", 200.0, "1 plato", 158.0, 5.8, 31.0, 0.9, 1.8, listOf("fideo", "pasta", "tallarin", "espagueti")),
        FoodRef("Camote cocido", 150.0, "1 camote", 90.0, 2.0, 21.0, 0.2, 3.3, listOf("camote")),
        FoodRef("Choclo", 150.0, "1 choclo", 96.0, 3.4, 21.0, 1.5, 2.4, listOf("choclo", "maiz")),
        FoodRef("Tortilla de harina", 45.0, "1 tortilla", 310.0, 8.0, 50.0, 8.0, 3.0, listOf("tortilla", "wrap")),
        // Fruit & veg
        FoodRef("Banano", 120.0, "1 banano", 89.0, 1.1, 23.0, 0.3, 2.6, listOf("banano", "guineo", "platano")),
        FoodRef("Manzana", 180.0, "1 manzana", 52.0, 0.3, 14.0, 0.2, 2.4, listOf("manzana")),
        FoodRef("Papaya", 200.0, "1 taza", 43.0, 0.5, 11.0, 0.3, 1.7, listOf("papaya")),
        FoodRef("Piña", 165.0, "1 taza", 50.0, 0.5, 13.0, 0.1, 1.4, listOf("pina")),
        FoodRef("Mango", 165.0, "1 taza", 60.0, 0.8, 15.0, 0.4, 1.6, listOf("mango")),
        FoodRef("Naranja", 150.0, "1 naranja", 47.0, 0.9, 12.0, 0.1, 2.4, listOf("naranja", "mandarina")),
        FoodRef("Frutillas", 150.0, "1 taza", 32.0, 0.7, 7.7, 0.3, 2.0, listOf("frutilla", "fresa")),
        FoodRef("Aguacate", 70.0, "1/2 aguacate", 160.0, 2.0, 8.5, 14.7, 6.7, listOf("aguacate", "palta")),
        FoodRef("Ensalada verde", 150.0, "1 plato", 20.0, 1.3, 3.5, 0.2, 1.8, listOf("ensalada", "lechuga")),
        FoodRef("Brócoli", 150.0, "1 taza", 35.0, 2.4, 7.2, 0.4, 3.3, listOf("brocoli")),
        FoodRef("Tomate", 120.0, "1 tomate", 18.0, 0.9, 3.9, 0.2, 1.2, listOf("tomate")),
        FoodRef("Zanahoria", 80.0, "1 zanahoria", 41.0, 0.9, 10.0, 0.2, 2.8, listOf("zanahoria")),
        // Fats, snacks, drinks
        FoodRef("Aceite (cucharada)", 14.0, "1 cucharada", 884.0, 0.0, 0.0, 100.0, 0.0, listOf("aceite")),
        FoodRef("Mantequilla de maní", 32.0, "2 cucharadas", 588.0, 25.0, 20.0, 50.0, 6.0, listOf("mani", "mantequilla de mani")),
        FoodRef("Maní / nueces", 30.0, "1 puñado", 600.0, 22.0, 18.0, 50.0, 8.0, listOf("nueces", "almendras", "mani tostado")),
        FoodRef("Chocolate", 40.0, "1 barra pequeña", 540.0, 6.0, 58.0, 31.0, 4.0, listOf("chocolate")),
        FoodRef("Galletas", 30.0, "4 galletas", 480.0, 6.0, 68.0, 20.0, 2.0, listOf("galleta", "galletas")),
        FoodRef("Helado", 100.0, "1 bola grande", 207.0, 3.5, 24.0, 11.0, 0.7, listOf("helado")),
        FoodRef("Pizza", 110.0, "1 porción", 266.0, 11.0, 33.0, 10.0, 2.3, listOf("pizza")),
        FoodRef("Hamburguesa", 220.0, "1 hamburguesa", 250.0, 13.0, 24.0, 11.0, 1.5, listOf("hamburguesa")),
        FoodRef("Hot dog", 150.0, "1 hot dog", 270.0, 9.5, 25.0, 15.0, 1.0, listOf("hot dog", "salchicha")),
        FoodRef("Cola / gaseosa", 355.0, "1 lata", 42.0, 0.0, 10.6, 0.0, 0.0, listOf("cola", "gaseosa", "coca")),
        FoodRef("Jugo de fruta con azúcar", 300.0, "1 vaso", 55.0, 0.4, 13.0, 0.1, 0.3, listOf("jugo")),
        FoodRef("Café negro", 240.0, "1 taza", 2.0, 0.3, 0.0, 0.0, 0.0, listOf("cafe")),
        FoodRef("Café con leche y azúcar", 240.0, "1 taza", 45.0, 1.6, 6.5, 1.5, 0.0, listOf("cafe con leche")),
        FoodRef("Cerveza", 355.0, "1 lata", 43.0, 0.5, 3.6, 0.0, 0.0, listOf("cerveza", "pilsener")),
    )

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").trim()

    fun search(q: String): List<FoodRef> {
        val n = norm(q)
        if (n.isBlank()) return all.take(20)
        return all.filter { f -> norm(f.name).contains(n) || f.aliases.any { norm(it).contains(n) || n.contains(norm(it)) } }
            .sortedBy { if (norm(it.name).startsWith(n)) 0 else 1 }
    }

    private val words = mapOf("un" to 1.0, "una" to 1.0, "dos" to 2.0, "tres" to 3.0, "cuatro" to 4.0, "medio" to 0.5, "media" to 0.5)

    /**
     * Best-effort reading of "2 huevos, un bolón y café" without AI: split on commas and "y/con",
     * find a known food in each piece, and use a leading number as the count of portions.
     */
    fun guess(description: String): List<FoodItem> {
        val pieces = norm(description).split(Regex(",|\\by\\b|\\bcon\\b|\\+|\\n|;")).map { it.trim() }.filter { it.isNotEmpty() }
        return pieces.mapNotNull { piece ->
            val match = all.flatMap { f -> (f.aliases + norm(f.name)).map { a -> f to norm(a) } }
                .filter { (_, a) -> piece.contains(a) }
                .maxByOrNull { (_, a) -> a.length }?.first ?: return@mapNotNull null
            val first = piece.split(" ").firstOrNull().orEmpty()
            val count = first.replace(",", ".").toDoubleOrNull() ?: words[first] ?: 1.0
            val grams = Regex("(\\d+)\\s*(g|gr|gramos)\\b").find(piece)?.groupValues?.get(1)?.toDoubleOrNull()
            match.item(grams ?: match.portionG * count)
        }
    }
}
