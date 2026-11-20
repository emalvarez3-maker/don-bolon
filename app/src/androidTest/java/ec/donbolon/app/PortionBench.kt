package ec.donbolon.app

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ec.donbolon.app.ai.Ai
import ec.donbolon.app.ai.AiException
import ec.donbolon.app.data.Portion
import ec.donbolon.app.data.Provider
import ec.donbolon.app.data.Secrets
import ec.donbolon.app.data.Similar
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Fixed evaluator for portion estimation (node E1): Nutrition5k dishes weighed in a lab, analyzed by
 * several methods against the real weight and calories:
 * - `base`: the app's method, [Ai.analyze] once with the scale sentence as `ruler` (the AI guesses grams);
 * - `ens3`: the same with `samples = 3`, keeping the answer with the median kcal;
 * - `strong`: the same as `base` with `model = "gemini-4-hydrogen"` forced first;
 * - `anchors`: the same as `base` with `promptVariant = "anchors"` (USDA portion weights to compare against);
 * - `steps`: the same as `base` with `promptVariant = "steps"` (plate fraction and height, then cups, then grams);
 * - `new`: the AI measures centimeters and geometry plus density give the grams (lost on 2026-11-10; kept
 *   for history, not run by default);
 * - `side`: `base` plus the side frame (`bench/side/<id>.jpg`, listed in `bench/side/index.json`) as `side`;
 * - `sidebase`: plain `base`, but only on the dishes that have a side frame, the fair twin of `side`;
 * - `depth`: `base` with the depth-sensor measurement (`bench/depth.json`) appended to the scale sentence;
 * - `similar`: `base` with the hint from `bench/similar.json` appended to the scale sentence;
 * - `refs`: the real two-step flow, [Ai.analyzeWithReferences] with the same scale sentence as `base`: the AI
 *   analyzes, the app finds 3 similar lab-weighed dishes in the shipped database ([Similar], which already
 *   excludes the 40 bench dishes) by the foods it recognized, and the AI re-estimates with that hint;
 * - `refs5`: `refs` with 5 similar dishes instead of 3;
 * - `refs38` / `base38`: `refs` / `base` with `model = "gemini-3.8-flash"` forced first.
 * `refs5` and `refs38` are also paired against `refs` when it runs.
 * A dish without the data a mode needs is skipped for that mode (logged as skipped, never an error), and the
 * optional asset files may be missing altogether. SUMMARY also pairs `base` with every other mode (and
 * `sidebase` with `side`) on the dishes where both answered, so two modes are compared on the same dishes.
 *
 * It runs on the phone so the AI key never leaves it. The dishes are test assets (not in git):
 * `src/androidTest/assets/bench/bench.json` plus one `<dish id>.png` per dish. Run with
 * `adb shell am instrument -w -e mode base,ens3,strong -e limit 40 ec.donbolon.app.test/androidx.test.runner.AndroidJUnitRunner`
 * (`mode` is a comma list; `both` still means `base,new`) and read `files/bench/` with `run-as`. Each row
 * records its method, and SUMMARY reports each mode.
 */
@RunWith(AndroidJUnit4::class)
class PortionBench {
    // Nutrition5k overhead camera: 35.9 cm above the plate, 5.957e-3 cm² per pixel (Thames et al., 2021),
    // so each 640×480 photo covers 49.4 × 37.0 cm at plate level. This plays the hand's role here.
    private val scale = "Foto tomada desde arriba, a 35.9 cm del plato. La imagen completa cubre 49.4 cm de ancho y 37.0 cm de alto " +
        "al nivel del plato (1 píxel = 0.77 mm). No hay mano en la foto: usa esa escala."

    @Test fun bench() {
        val args = InstrumentationRegistry.getArguments()
        val modes = args.getString("mode", "base,ens3,strong").let { if (it == "both") "base,new" else it }
            .split(",").map { it.trim() }.filter { it in METHODS }.distinct()
        assumeTrue("Ningún modo válido (${METHODS.joinToString()})", modes.isNotEmpty())
        val limit = args.getString("limit", "40").toInt()
        val pause = args.getString("pauseMs", "4000").toLong()
        // Skip the first N dishes, so a run that died midway can be resumed.
        val offset = args.getString("offset", "0").toInt()
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val key = Secrets(app).get(Provider.GEMINI)
        assumeTrue("No hay llave de Gemini guardada en el teléfono", key.isNotBlank())
        val ai = Ai(Provider.GEMINI, key)

        val dishes = JSONArray(assets.open("bench/bench.json").bufferedReader().readText())
        val out = File(app.filesDir, "bench").apply { mkdirs() }
        val rows = File(out, "rows-${System.currentTimeMillis()}.jsonl")
        // Per mode, dish id -> (grams absolute error, kcal relative error), so SUMMARY can pair modes by dish.
        val errors = modes.associateWith { linkedMapOf<String, Pair<Double, Double>>() }
        val skipped = modes.associateWith { 0 }.toMutableMap()
        // Optional assets other nodes prepare; a missing file just means that mode skips every dish.
        val sideIds = optionalJson(assets, "bench/side/index.json")?.let { JSONArray(it) }
            ?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val depth = optionalJson(assets, "bench/depth.json")?.let { JSONObject(it) } ?: JSONObject()
        val similar = optionalJson(assets, "bench/similar.json")?.let { JSONObject(it) } ?: JSONObject()
        Log.i("DonBolonBench", "side frames: ${sideIds.size}, depth: ${depth.length()}, similar: ${similar.length()}")
        // The app's own reference database, loaded once and only when the refs mode runs.
        val refDb by lazy { Similar.load(app) }

        for (i in offset until minOf(limit, dishes.length())) {
            val d = dishes.getJSONObject(i)
            val id = d.getString("id")
            val photo = assets.open("bench/$id.png").use { BitmapFactory.decodeStream(it) }
            val realG = d.getDouble("grams")
            val realKcal = d.getDouble("kcal")
            val side by lazy {
                if (id !in sideIds) null
                else runCatching { assets.open("bench/side/$id.jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull()
            }
            for (method in modes) {
                // The extra input this mode needs; null means the dish lacks it and is skipped for this mode.
                val ruler: String? = when (method) {
                    "depth" -> depth.optJSONObject(id)?.let { runCatching { scale + " " + depthSentence(it) }.getOrNull() }
                    "similar" -> similar.optString(id, "").takeIf { it.isNotBlank() }?.let { "$scale $it" }
                    "side", "sidebase" -> scale.takeIf { side != null }
                    else -> scale
                }
                if (ruler == null) {
                    skipped[method] = skipped.getValue(method) + 1
                    Log.i("DonBolonBench", "$method $id skipped: no ${if (method == "sidebase") "side" else method} data")
                    continue
                }
                val started = System.currentTimeMillis()
                val (g, kcal) = runCatching {
                    retrying {
                        if (method == "new") {
                            val items = ai.measure(photo, null, "", null, "", scale, Portion.densities.keys).items.map { Portion.item(it) }
                            items.sumOf { it.grams } to items.sumOf { it.kcal }
                        } else {
                            // The same scale sentence the app passes as its ruler, so the comparison matches the app.
                            val a = when (method) {
                                "ens3" -> ai.analyze(photo, "", null, "", ruler = scale, samples = 3)
                                "strong" -> ai.analyze(photo, "", null, "", ruler = scale, model = STRONG, readTimeoutMs = STRONG_TIMEOUT_MS)
                                "anchors", "steps" -> ai.analyze(photo, "", null, "", ruler = scale, promptVariant = method)
                                "side" -> ai.analyze(photo, "", null, "", side = side, ruler = scale)
                                "refs", "refs5", "refs38" -> ai.analyzeWithReferences(
                                    photo, "", null, "", ruler = scale, model = if (method == "refs38") FLASH38 else null,
                                ) { foods ->
                                    Similar.hint(refDb, foods, k = if (method == "refs5") 5 else 3)
                                        .also { Log.i("DonBolonBench", "$method $id foods=$foods hint=${it != null}") }
                                }
                                "base38" -> ai.analyze(photo, "", null, "", ruler = scale, model = FLASH38)
                                // base, sidebase, depth and similar: only the ruler sentence differs.
                                else -> ai.analyze(photo, "", null, "", ruler = ruler)
                            }
                            a.items.sumOf { it.grams } to a.items.sumOf { it.kcal }
                        }
                    }
                }.getOrElse { e ->
                    val ms = System.currentTimeMillis() - started
                    Log.w("DonBolonBench", "$method $id failed after $ms ms: ${e.message}")
                    rows.appendText(JSONObject().put("id", id).put("method", method).put("error", e.message).put("ms", ms).toString() + "\n")
                    Thread.sleep(pause)
                    continue
                }
                // ms includes the one-minute wait of a retry; model shows whether a fallback answered instead.
                val ms = System.currentTimeMillis() - started
                val model = if (method == "new") null else Ai.lastModel
                errors.getValue(method)[id] = abs(g - realG) to abs(kcal - realKcal) / realKcal
                rows.appendText(
                    JSONObject().put("id", id).put("method", method).put("grams", g).put("kcal", kcal)
                        .put("realGrams", realG).put("realKcal", realKcal).put("ms", ms).put("model", model).toString() + "\n",
                )
                Log.i("DonBolonBench", "$method $id: ${g.toInt()} g vs ${realG.toInt()} g, ${kcal.toInt()} kcal vs ${realKcal.toInt()} kcal, $ms ms, $model")
                Thread.sleep(pause)
            }
        }

        val summary = JSONObject()
        modes.forEach { method ->
            val e = errors.getValue(method).values
            val s = JSONObject().put("dishes", e.size).put("skipped", skipped.getValue(method))
            if (e.isNotEmpty()) s.put("gramsMae", e.map { it.first }.average()).put("kcalMape", e.map { it.second }.average() * 100)
            summary.put(method, s)
        }
        // Paired comparisons: each pair only on the dishes where both modes answered.
        val pairs = modes.filter { it != "base" }.map { "base" to it }.toMutableList()
        if ("side" in modes && "sidebase" in modes) pairs += "sidebase" to "side"
        // The reference variants also against plain refs, so k and the model are measured with references held fixed.
        if ("refs" in modes) pairs += listOf("refs5", "refs38").filter { it in modes }.map { "refs" to it }
        val paired = JSONObject()
        for ((a, b) in pairs) {
            val ea = errors[a] ?: continue
            val eb = errors[b] ?: continue
            val common = ea.keys.filter { it in eb }
            val p = JSONObject().put("dishes", common.size)
            if (common.isNotEmpty()) {
                p.put("${a}GramsMae", common.map { ea.getValue(it).first }.average())
                    .put("${b}GramsMae", common.map { eb.getValue(it).first }.average())
                    .put("${a}KcalMape", common.map { ea.getValue(it).second }.average() * 100)
                    .put("${b}KcalMape", common.map { eb.getValue(it).second }.average() * 100)
                    // How many dishes b got closer in kcal than a.
                    .put("${b}BetterKcal", common.count { eb.getValue(it).second < ea.getValue(it).second })
            }
            paired.put("$a vs $b", p)
        }
        if (paired.length() > 0) summary.put("paired", paired)
        File(out, "summary-${rows.nameWithoutExtension.removePrefix("rows-")}.json").writeText(summary.toString(1))
        Log.i("DonBolonBench", "SUMMARY $summary")
    }

    private companion object {
        val METHODS = listOf("base", "ens3", "strong", "new", "anchors", "steps", "side", "sidebase", "depth", "similar", "refs", "refs5", "refs38", "base38")
        const val STRONG = "gemini-4-hydrogen"
        // Forced first by base38 and refs38, to measure the model with and without references.
        const val FLASH38 = "gemini-3.8-flash"
        // hydrogen is slow: the app's 60 s analysis timeout cut off every answer on 2026-11-20.
        const val STRONG_TIMEOUT_MS = 300_000
    }

    /** The asset's text, or null when that optional file isn't packaged. */
    private fun optionalJson(assets: android.content.res.AssetManager, path: String): String? =
        runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    private fun depthSentence(d: JSONObject): String = String.format(
        Locale.US,
        "Un sensor de profundidad midió la comida: volumen total ≈ %.0f ml, altura máxima ≈ %.1f cm, área ≈ %.0f cm². " +
            "Úsalo para calcular los gramos.",
        d.getDouble("volumeMl"), d.getDouble("maxHeightCm"), d.getDouble("areaCm2"),
    )

    /** One retry after a minute when Gemini is saturated or out of quota, so a blip doesn't count as an error. */
    private fun <T> retrying(block: () -> T): T = try {
        block()
    } catch (e: AiException) {
        val m = e.message ?: ""
        if (!(m.contains("saturado") || m == Ai.QUOTA)) throw e
        Thread.sleep(60_000)
        block()
    }
}
