package ec.donbolon.app.data

/**
 * How a food sits on the plate, which decides how its size turns into volume.
 * The AI only measures; the grams come from geometry and density, never from a guess.
 */
enum class Shape {
    /** A heap, like rice or menestra: a spherical cap over its footprint. */
    MOUND,
    /** A flat layer, like a steak, a fried egg or a tortilla: footprint × thickness. */
    FLAT,
    /** A round piece standing up, like a glass of juice or a bolón seen from the side. */
    CYLINDER,
    /** A solid piece, like a chicken leg, a potato or a maduro: an ellipsoid. */
    PIECE,
    /** Soup or drink in a bowl or glass: its cylinder (or the dish's capacity) times how full it is. */
    LIQUID,
}

/** Nutrients per 100 g, as the AI reads them from composition tables. */
data class Per100(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fiber: Double = 0.0)

/**
 * One food as the AI measured it, in centimeters, using the hand or the dish as a ruler.
 * [fill] is only for LIQUID: the fraction of the glass or bowl it takes up (0..1, 1 when unknown).
 * [densityKey] names the density table entry (for example "arroz-cocido"); null means use [Shape]'s default.
 */
data class Measured(
    val name: String,
    val shape: Shape,
    val lengthCm: Double,
    val widthCm: Double,
    val heightCm: Double,
    val per100: Per100,
    val densityKey: String? = null,
    val fill: Double? = null,
)

/**
 * How heavy one milliliter of a food is, and the table it was read from.
 * [gPerMl] is what a heap (MOUND) of it weighs. When [solidSource] is set, [gPerMl] is a cup (bulk)
 * weight with air between the pieces, so a single PIECE, FLAT or CYLINDER of it uses [solidGPerMl]
 * instead, or the shape's default when no solid value could be verified ([solidGPerMl] null).
 */
data class Density(
    val gPerMl: Double,
    val source: String,
    val solidGPerMl: Double? = null,
    val solidSource: String? = null,
)

/** Measured centimeters to grams and nutrients, with no AI in the loop. */
object Portion {
    private const val CUP_ML = 236.6
    private const val TBSP_ML = 14.79
    private const val CUBIC_INCH_ML = 16.387

    /**
     * Nothing served on a plate is bigger than 60 cm on any side (a large platter is about 40 cm),
     * so a larger number is the AI misreading the ruler or the units: the food counts as nothing.
     */
    private const val MAX_CM = 60.0

    /** Pure fat is 884–900 kcal per 100 g; nothing eaten has more. */
    private const val MAX_KCAL_100 = 900.0

    /** No solid weight could be verified for this food: a single piece falls back on the shape's default. */
    private const val UNVERIFIED = "Sin densidad sólida verificada: una pieza usa la densidad por defecto de su forma"

    // Cooked beef steak (FDC 2705824), pork (2705862) and tilapia (2706320) all weigh 17 g per cubic inch,
    // and cooked chicken (FDC 2705931, "1 cup, cooked, diced" = 135 g) packs a cup exactly like them,
    // so cooked chicken meat is taken as the same solid density.
    private const val COOKED_MEAT_SOLID = 17.0 / CUBIC_INCH_ML

    private fun cup(fdc: String, measure: String, grams: Double) =
        Density(grams / CUP_ML, "USDA FDC $fdc: $measure = ${fmt(grams)} g")

    private fun Density.solid(gPerMl: Double?, source: String) = copy(solidGPerMl = gPerMl, solidSource = source)

    private fun fmt(g: Double) = if (g % 1.0 == 0.0) g.toInt().toString() else g.toString()

    /**
     * Cup and tablespoon weights are bulk densities (the air between grains or pieces included),
     * which is what a heap on the plate also holds. Solid cuts that are measured as one block
     * (steak, pork, fish, cheese) use FNDDS "1 cubic inch" weights instead, since a cup of diced
     * meat would undercount a whole steak by about 40 %. Foods that come both heaped and as one
     * piece (chicken, potato, plantain...) carry a solid value for the single-piece shapes.
     */
    val densities: Map<String, Density> = run {
        val rice = cup("168878", "Rice, white, long-grain, regular, enriched, cooked, 1 cup", 158.0)
        val beans = cup("173740", "Beans, kidney, all types, mature seeds, cooked, boiled, without salt, 1 cup", 177.0)
        val lentils = cup("172421", "Lentils, mature seeds, cooked, boiled, without salt, 1 cup", 198.0)
        val mashedPlantain = cup("2709558", "Plantain, cooked, no added fat, 1 cup, mashed", 200.0)
        val soup = cup("2707134", "Soup, chicken, 1 cup", 245.0)
        val juice = cup("169098", "Orange juice, raw, 1 cup", 248.0)
        val oil = Density(13.6 / TBSP_ML, "USDA FDC 171411: Oil, soybean, salad or cooking, 1 tbsp = 13.6 g")
        val salsa = cup("174524", "Sauce, salsa, ready-to-serve, 1 cup", 259.0)
        mapOf(
            "arroz-cocido" to rice,
            // Menestra is beans or lentils stewed thick; canario beans cooked from dried are the closest FNDDS entry.
            "menestra" to cup("2707387", "Peruvian beans, from dried, 1 cup", 185.0),
            "frejol-cocido" to beans,
            "lenteja-cocida" to lentils,
            "maduro-frito" to cup("168200", "Plantains, yellow, fried, Latino restaurant, 1 cup", 169.0).solid(null, UNVERIFIED),
            "verde-cocido" to cup("168216", "Plantains, green, boiled, 1 cup", 137.0).solid(null, UNVERIFIED),
            "bolon" to mashedPlantain,
            "majado-de-verde" to mashedPlantain,
            "patacon" to cup("168199", "Plantains, green, fried, 1 cup", 118.0).solid(null, UNVERIFIED),
            // A whole tuber has no air in it: its specific gravity, the potato industry's quality measure,
            // runs 1.06 to 1.10 for most cultivars. Boiled peeled flesh is assumed to stay about the same.
            "papa-cocida" to Density(78.0 * 2 / CUP_ML, "USDA FDC 170440: Potatoes, boiled, cooked without skin, flesh, without salt, 0.5 cup = 78 g")
                .solid(1.08, "USDA Circular 872 (Kirkpatrick et al.): tuber specific gravity 1.061 to 1.10; 1.08 taken for boiled flesh"),
            "papas-fritas" to cup("2709458", "Potato, french fries, from fresh, fried, 1 cup", 60.0),
            "yuca-cocida" to cup("2709564", "Cassava, cooked, 1 cup", 160.0).solid(null, UNVERIFIED),
            "mote" to cup("2709933", "Hominy, cooked, 1 cup", 170.0),
            "choclo" to cup("169999", "Corn, sweet, yellow, cooked, boiled, drained, without salt, 1 cup cut", 149.0).solid(null, UNVERIFIED),
            "carne-res-cocida" to Density(17.0 / CUBIC_INCH_ML, "USDA FDC 2705824: Beef, steak, NFS, 1 cubic inch = 17 g"),
            "pollo-cocido" to cup("172403", "Chicken, stewing, meat only, cooked, stewed, 1 cup, chopped or diced", 140.0)
                .solid(COOKED_MEAT_SOLID, "USDA FDC 2705824/2705862/2706320: cooked beef, pork and fish, 1 cubic inch = 17 g; FDC 2705931 chicken shares their 1 cup diced = 135 g"),
            "cerdo-cocido" to Density(17.0 / CUBIC_INCH_ML, "USDA FDC 2705862: Pork, NFS, 1 cubic inch = 17 g"),
            "pescado-cocido" to Density(17.0 / CUBIC_INCH_ML, "USDA FDC 2706320: Fish, tilapia, grilled, 1 cubic inch = 17 g"),
            "camaron" to cup("2706360", "Shrimp, NFS, 1 cup", 135.0).solid(null, UNVERIFIED),
            "huevo-frito" to cup("2707158", "Egg, whole, fried with oil, 1 cup", 135.0).solid(null, UNVERIFIED),
            "huevo-revuelto" to cup("172187", "Egg, whole, cooked, scrambled, 1 cup", 220.0),
            "queso-fresco" to Density(18.0 / CUBIC_INCH_ML, "USDA FDC 2705745: Queso Fresco, 1 cubic inch = 18 g"),
            // Pureed has no air in it, so it is the flesh's own density, as when half an avocado is measured whole.
            "aguacate" to cup("171705", "Avocados, raw, all commercial varieties, 1 cup, pureed", 230.0),
            // Curtido is chopped tomato and onion in about equal parts: the mean of both cups.
            "ensalada-cruda" to Density(
                (180.0 + 160.0) / 2 / CUP_ML,
                "USDA FDC 170457: Tomatoes, red, ripe, raw, 1 cup, chopped or sliced = 180 g; USDA FDC 170000: Onions, raw, 1 cup, chopped = 160 g",
            ),
            "lechuga" to cup("169248", "Lettuce, iceberg, raw, 1 cup shredded", 72.0),
            "sopa" to soup,
            "caldo" to soup,
            "jugo" to juice,
            "bebida" to juice,
            "salsa" to salsa,
            "aceite" to oil,
        )
    }

    /**
     * Fallbacks when the AI names no known food. A heap is mostly cooked grains or legumes
     * (0.58–0.84 g/ml above), flat layers are solid meat, egg or cheese (≈1.0), an upright
     * cylinder is a drink or a packed bolón (0.85–1.05), a piece is solid but not always
     * packed, and liquids are water-like broths and juices (1.03–1.05).
     */
    fun defaultDensity(shape: Shape): Double = when (shape) {
        Shape.MOUND -> 0.75
        Shape.FLAT -> 1.0
        Shape.CYLINDER -> 0.9
        Shape.PIECE -> 0.95
        Shape.LIQUID -> 1.03
    }

    /**
     * "Arroz cocido", "bolón" and "papas_fritas" are the keys "arroz-cocido", "bolon" and
     * "papas-fritas": trimmed, lowercase, without accents, spaces and underscores as hyphens.
     */
    fun normalizeKey(key: String): String =
        java.text.Normalizer.normalize(key.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[\\s_]+"), "-")

    fun density(m: Measured): Double {
        val d = m.densityKey?.let { densities[normalizeKey(it)] } ?: return defaultDensity(m.shape)
        val onePiece = m.shape == Shape.PIECE || m.shape == Shape.FLAT || m.shape == Shape.CYLINDER
        return if (onePiece && d.solidSource != null) d.solidGPerMl ?: defaultDensity(m.shape) else d.gPerMl
    }

    private fun misread(m: Measured): Boolean {
        val l = m.lengthCm
        val w = m.widthCm
        val h = m.heightCm
        // A zero or negative size is a misread, not a hole, and so is one beyond MAX_CM.
        return !(l > 0 && w > 0 && h > 0) || l > MAX_CM || w > MAX_CM || h > MAX_CM
    }

    /** A LIQUID's own glass or bowl as a cylinder, before the fill: π/4 · l · w · h, 0 if misread. */
    fun cylinderMl(m: Measured): Double =
        if (misread(m)) 0.0 else Math.PI / 4 * m.lengthCm * m.widthCm * m.heightCm

    /**
     * [dishCapacityMl] counts only for a LIQUID, and only when the caller knows this is the one liquid
     * in that bowl or glass ([items] decides that); otherwise a liquid is its own measured cylinder.
     * Either way it is multiplied by how full it is (1 when the AI did not say).
     */
    fun volumeMl(m: Measured, dishCapacityMl: Double? = null): Double {
        val l = m.lengthCm
        val w = m.widthCm
        val h = m.heightCm
        val fill = (m.fill ?: 1.0).coerceIn(0.0, 1.0)
        val v = when {
            m.shape == Shape.LIQUID && dishCapacityMl != null && dishCapacityMl > 0 -> dishCapacityMl * fill
            m.shape == Shape.LIQUID -> cylinderMl(m) * fill
            misread(m) -> 0.0
            m.shape == Shape.MOUND -> Math.PI / 6 * h * (3 * (l / 2) * (w / 2) + h * h)
            m.shape == Shape.PIECE -> Math.PI / 6 * l * w * h
            else -> Math.PI / 4 * l * w * h // FLAT and CYLINDER
        }
        return if (v.isNaN() || v <= 0) 0.0 else v
    }

    /**
     * The ruler sentence for the AI: the hand is the best one because it is always in the photo,
     * the dish's real size backs it up, and with neither the AI falls back on a standard plate.
     */
    fun scale(hand: Hand?, dish: Dish?): String = listOfNotNull(
        hand?.let { it.describe() + " Está abierta y plana junto al plato: úsala como regla principal." },
        dish?.let { "Está servido en: ${it.describe()} (medida real)." },
    ).joinToString(" ").ifBlank { "No hay mano ni medida del plato: usa como regla un plato llano estándar de 26 cm y los cubiertos." }

    /** How much a bowl, glass or cup holds, for soups and drinks measured as a fill fraction. */
    fun capacityMl(dish: Dish?): Double? = dish?.takeIf { it.kind != Dish.Kind.PLATO }?.size

    fun grams(m: Measured, dishCapacityMl: Double? = null): Double {
        val g = volumeMl(m, dishCapacityMl) * density(m)
        return if (g.isNaN() || g <= 0) 0.0 else g
    }

    /**
     * The AI sometimes reads kcal from one table and macros from another. Kcal above the macros
     * is kept, since alcohol (7 kcal/g) and polyols count without being protein, carbs or fat:
     * beer is 43 kcal per 100 g on 16 kcal of macros. Only a missing kcal, or one more than 20 %
     * below what the macros alone give, is replaced by the macros.
     */
    fun item(m: Measured, dishCapacityMl: Double? = null): FoodItem {
        val g = grams(m, dishCapacityMl)
        val p = m.per100
        val fromMacros = 4 * p.protein + 4 * p.carbs + 9 * p.fat
        val read = if (!(p.kcal > 0) || p.kcal < 0.8 * fromMacros) fromMacros else p.kcal
        val kcal100 = if (read.isNaN() || read <= 0) 0.0 else read.coerceAtMost(MAX_KCAL_100)
        val k = g / 100
        return FoodItem(m.name, g, kcal100 * k, p.protein * k, p.carbs * k, p.fat * k, p.fiber * k)
    }

    /**
     * The whole photo at once. A bowl's or glass's capacity is used only for the one liquid in it:
     * when exactly one LIQUID was measured and its own cylinder is within 40 % of the capacity,
     * it is the dish's content. Soup plus a glass of juice, or a liquid that does not match the
     * dish, are each measured by their own cylinder × fill.
     */
    fun items(measured: List<Measured>, dish: Dish?): List<FoodItem> {
        val capacity = capacityMl(dish)
        val inDish = measured.singleOrNull { it.shape == Shape.LIQUID }?.takeIf { liquid ->
            capacity != null && capacity > 0 && kotlin.math.abs(cylinderMl(liquid) - capacity) <= 0.4 * capacity
        }
        return measured.map { item(it, if (inDish != null && it === inDish) capacity else null) }
    }
}
