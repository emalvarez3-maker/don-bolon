package ec.donbolon.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** One lab-weighed dish from Nutrition5k: total grams and its ingredients (English name, grams). */
data class RefDish(val mass: Double, val items: List<Pair<String, Double>>)

/**
 * The shipped reference portions (assets/refs): real dishes weighed in a lab by Nutrition5k
 * (Thames et al., 2021, Google Research, CC BY 4.0), minus the dishes of the portion bench,
 * and the English→Spanish names of their ingredients.
 */
class RefDb(val dishes: List<RefDish>, val es: Map<String, String>) {
    /** Each dish as ingredient → share of the dish's grams (shares add up to 1). */
    internal val shares: List<Map<String, Double>> by lazy {
        dishes.map { d ->
            val agg = LinkedHashMap<String, Double>()
            for ((n, g) in d.items) agg[n] = (agg[n] ?: 0.0) + g
            val tot = agg.values.sum().takeIf { it > 0 } ?: 1.0
            agg.mapValues { it.value / tot }
        }
    }

    /** Vocabulary: ingredient name, its normalized tokens, and in how many dishes it appears. */
    internal val vocab: List<Similar.Term> by lazy {
        val freq = HashMap<String, Int>()
        for (d in dishes) for (n in d.items.map { it.first }.toSet()) freq[n] = (freq[n] ?: 0) + 1
        freq.keys.sorted().map { Similar.Term(it, Similar.key(it), Similar.tokens(it), freq.getValue(it)) }
    }
}

/**
 * Finds weighed dishes that look like what the AI recognized, to give it a size reference.
 *
 * Matching a recognized food to a Nutrition5k ingredient (see [match]): both names are
 * lowercased, stripped of accents and punctuation, and each word singularized
 * ("tomatoes" → "tomato", "berries" → "berry"). An identical name wins. Otherwise the
 * ingredient whose words all appear in the food wins ("white rice" → "white rice",
 * "grilled chicken breast" → "chicken breast", "red apple" → "apple"), preferring more
 * shared words, then the one containing the food's last word (its head noun in English),
 * then the most common ingredient. If none is fully contained, an ingredient sharing at
 * least half its words, including the food's head noun, is accepted ("rice" → "white rice").
 *
 * Similarity is the weighted Jaccard index over ingredient shares of each plate's grams:
 * sim(A, B) = Σ min(aᵢ, bᵢ) / Σ max(aᵢ, bᵢ). Foods that match no ingredient keep their
 * own share, so a plate that is mostly unknown food scores low against every dish.
 *
 * [THRESHOLD] = 0.25: with shares adding up to 1, J = m / (2 − m) where m = Σ min, so
 * J ≥ 0.25 means at least 40 % of both plates' grams are the same ingredients. Below that a
 * reference dish is mostly a different food and its weight would mislead, so no hint.
 */
object Similar {
    const val THRESHOLD = 0.25
    const val MAX_HINT = 420
    private const val PREFIX = "Platos parecidos pesados en laboratorio (referencia de tamaño): "
    private val STOP = setOf("and", "with", "of", "the", "a", "an", "in", "on", "de", "con", "y")

    internal data class Term(val name: String, val key: String, val tokens: Set<String>, val freq: Int)

    fun parse(refsJson: String, esJson: String): RefDb {
        val arr = JSONArray(refsJson)
        val dishes = ArrayList<RefDish>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val its = o.getJSONArray("i")
            val items = (0 until its.length()).map {
                val p = its.getJSONArray(it)
                p.getString(0).lowercase(Locale.ROOT) to p.getDouble(1)
            }
            dishes += RefDish(o.getDouble("m"), items)
        }
        val e = JSONObject(esJson)
        val es = e.keys().asSequence().associate { it.lowercase(Locale.ROOT) to e.getString(it) }
        return RefDb(dishes, es)
    }

    @Volatile private var cached: RefDb? = null

    /** Reads the shipped assets once and caches them. Blocking: call it off the main thread. */
    fun load(context: Context): RefDb = cached ?: synchronized(this) {
        cached ?: run {
            val a = context.applicationContext.assets
            val refs = a.open("refs/n5k_refs.json").bufferedReader().use { it.readText() }
            val es = a.open("refs/es.json").bufferedReader().use { it.readText() }
            parse(refs, es).also { cached = it }
        }
    }

    /** Lowercase, no accents, punctuation as spaces. */
    internal fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()

    internal fun singular(w: String): String = when {
        w.length <= 3 -> w
        w.endsWith("ies") -> w.dropLast(3) + "y"
        w.endsWith("oes") -> w.dropLast(2)
        w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes") || w.endsWith("sses") -> w.dropLast(2)
        w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") -> w.dropLast(1)
        else -> w
    }

    private fun words(s: String) = norm(s).split(' ').filter { it.isNotEmpty() && it !in STOP }.map(::singular)
    internal fun key(s: String) = words(s).joinToString(" ")
    internal fun tokens(s: String) = words(s).toSet()

    /** The Nutrition5k ingredient a recognized food stands for, or null if none fits. */
    fun match(db: RefDb, food: String): String? {
        val w = words(food)
        if (w.isEmpty()) return null
        val k = w.joinToString(" ")
        db.vocab.firstOrNull { it.key == k }?.let { return it.name }
        val ft = w.toSet()
        val head = w.last()
        var best: Term? = null
        var bestRank: List<Int>? = null
        for (t in db.vocab) {
            if (t.tokens.isEmpty()) continue
            val inter = t.tokens.count { it in ft }
            if (inter == 0) continue
            val full = inter == t.tokens.size
            val hasHead = head in t.tokens
            if (!full && (!hasHead || inter * 2 < t.tokens.size)) continue
            // Higher is better, compared in order; name order breaks the last ties (vocab is sorted).
            val rank = listOf(if (full) 1 else 0, inter, if (hasHead) 1 else 0, inter * 1000 / t.tokens.size, t.freq)
            if (bestRank == null || compareRanks(rank, bestRank) > 0) { best = t; bestRank = rank }
        }
        return best?.name
    }

    private fun compareRanks(a: List<Int>, b: List<Int>): Int {
        for (i in a.indices) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return 0
    }

    /** The plate as ingredient → share of its grams; unmatched foods keep a key no dish has. */
    internal fun plate(db: RefDb, foods: List<Pair<String, Double>>): Map<String, Double> {
        val agg = LinkedHashMap<String, Double>()
        for ((name, grams) in foods) {
            val g = if (grams.isFinite() && grams > 0) grams else 1.0
            val key = match(db, name) ?: ("?" + norm(name))
            agg[key] = (agg[key] ?: 0.0) + g
        }
        val tot = agg.values.sum()
        return if (tot > 0) agg.mapValues { it.value / tot } else emptyMap()
    }

    /** Weighted Jaccard: Σ min / Σ max over the union of ingredients. */
    internal fun similarity(a: Map<String, Double>, b: Map<String, Double>): Double {
        var mn = 0.0
        var mx = 0.0
        for ((k, x) in a) { val y = b[k] ?: 0.0; mn += minOf(x, y); mx += maxOf(x, y) }
        for ((k, y) in b) if (k !in a) mx += y
        return if (mx > 0) mn / mx else 0.0
    }

    /**
     * Up to [k] dishes most similar to [foods] (English name, estimated grams), best first,
     * ties by position in the db. Only dishes at or above [THRESHOLD]; a dish with the same
     * ingredient set as one already chosen is skipped. Empty when nothing is close enough.
     */
    fun nearest(db: RefDb, foods: List<Pair<String, Double>>, k: Int = 3): List<RefDish> {
        if (k <= 0) return emptyList()
        val p = plate(db, foods)
        if (p.isEmpty() || p.keys.all { it.startsWith("?") }) return emptyList()
        val scored = ArrayList<Pair<Int, Double>>()
        db.shares.forEachIndexed { i, s ->
            val sim = similarity(p, s)
            if (sim >= THRESHOLD) scored += i to sim
        }
        scored.sortWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first })
        val out = ArrayList<RefDish>(k)
        val sets = ArrayList<Set<String>>(k)
        for ((i, _) in scored) {
            val set = db.shares[i].keys
            if (sets.any { it == set }) continue
            out += db.dishes[i]
            sets += set
            if (out.size == k) break
        }
        return out
    }

    /** A Spanish size hint with up to [k] weighed dishes, ≤ [MAX_HINT] chars; null when none is close. */
    fun hint(db: RefDb, foods: List<Pair<String, Double>>, k: Int = 3): String? {
        val near = nearest(db, foods, k)
        if (near.isEmpty()) return null
        for (n in near.size downTo 1) for (top in intArrayOf(5, 4, 3, 2, 1)) {
            val s = render(db, near.take(n), top)
            if (s.length <= MAX_HINT) return s
        }
        return render(db, near.take(1), 1).take(MAX_HINT)
    }

    private fun render(db: RefDb, dishes: List<RefDish>, top: Int): String =
        PREFIX + dishes.mapIndexed { i, d ->
            val agg = LinkedHashMap<String, Double>()
            for ((n, g) in d.items) agg[n] = (agg[n] ?: 0.0) + g
            val items = agg.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
                .take(top).joinToString(", ") { "${db.es[it.key] ?: it.key} ${Math.round(it.value)} g" }
            "${i + 1}) $items (total ${Math.round(d.mass)} g)"
        }.joinToString("; ") + "."
}
