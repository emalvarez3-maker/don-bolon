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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Fixed evaluator for portion estimation (node E1): Nutrition5k dishes weighed in a lab, analyzed by
 * the current method (the AI guesses grams) and the new one (the AI measures centimeters, geometry and
 * density give the grams), against the real weight and calories.
 *
 * It runs on the phone so the AI key never leaves it. The dishes are test assets (not in git):
 * `src/androidTest/assets/bench/bench.json` plus one `<dish id>.png` per dish. Run with
 * `adb shell am instrument -w -e mode both -e limit 40 ec.donbolon.app.test/androidx.test.runner.AndroidJUnitRunner`
 * and read `files/bench/` with `run-as`.
 */
@RunWith(AndroidJUnit4::class)
class PortionBench {
    // Nutrition5k overhead camera: 35.9 cm above the plate, 5.957e-3 cm² per pixel (Thames et al., 2021),
    // so each 640×480 photo covers 49.4 × 37.0 cm at plate level. This plays the hand's role here.
    private val scale = "Foto tomada desde arriba, a 35.9 cm del plato. La imagen completa cubre 49.4 cm de ancho y 37.0 cm de alto " +
        "al nivel del plato (1 píxel = 0.77 mm). No hay mano en la foto: usa esa escala."

    @Test fun bench() {
        val args = InstrumentationRegistry.getArguments()
        val mode = args.getString("mode", "both")
        val limit = args.getString("limit", "40").toInt()
        val pause = args.getString("pauseMs", "4000").toLong()
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val key = Secrets(app).get(Provider.GEMINI)
        assumeTrue("No hay llave de Gemini guardada en el teléfono", key.isNotBlank())
        val ai = Ai(Provider.GEMINI, key)

        val dishes = JSONArray(assets.open("bench/bench.json").bufferedReader().readText())
        val out = File(app.filesDir, "bench").apply { mkdirs() }
        val rows = File(out, "rows-${System.currentTimeMillis()}.jsonl")
        val errors = mutableMapOf("base" to mutableListOf<Pair<Double, Double>>(), "new" to mutableListOf())

        for (i in 0 until minOf(limit, dishes.length())) {
            val d = dishes.getJSONObject(i)
            val id = d.getString("id")
            val photo = assets.open("bench/$id.png").use { BitmapFactory.decodeStream(it) }
            val realG = d.getDouble("grams")
            val realKcal = d.getDouble("kcal")
            for (method in listOf("base", "new").filter { mode == "both" || mode == it }) {
                val (g, kcal) = runCatching {
                    retrying {
                        if (method == "base") {
                            val a = ai.analyze(photo, "", null, "", dish = scale)
                            a.items.sumOf { it.grams } to a.items.sumOf { it.kcal }
                        } else {
                            val items = ai.measure(photo, null, "", null, "", scale, Portion.densities.keys).items.map { Portion.item(it) }
                            items.sumOf { it.grams } to items.sumOf { it.kcal }
                        }
                    }
                }.getOrElse { e ->
                    Log.w("DonBolonBench", "$method $id failed: ${e.message}")
                    rows.appendText(JSONObject().put("id", id).put("method", method).put("error", e.message).toString() + "\n")
                    Thread.sleep(pause)
                    continue
                }
                errors.getValue(method) += abs(g - realG) to abs(kcal - realKcal) / realKcal
                rows.appendText(
                    JSONObject().put("id", id).put("method", method).put("grams", g).put("kcal", kcal)
                        .put("realGrams", realG).put("realKcal", realKcal).toString() + "\n",
                )
                Log.i("DonBolonBench", "$method $id: ${g.toInt()} g vs ${realG.toInt()} g, ${kcal.toInt()} kcal vs ${realKcal.toInt()} kcal")
                Thread.sleep(pause)
            }
        }

        val summary = JSONObject()
        errors.filterValues { it.isNotEmpty() }.forEach { (method, e) ->
            summary.put(method, JSONObject()
                .put("dishes", e.size)
                .put("gramsMae", e.map { it.first }.average())
                .put("kcalMape", e.map { it.second }.average() * 100))
        }
        File(out, "summary-${rows.nameWithoutExtension.removePrefix("rows-")}.json").writeText(summary.toString(1))
        Log.i("DonBolonBench", "SUMMARY $summary")
    }

    /** One retry after a minute when Gemini is saturated, so a quota blip doesn't count as an error. */
    private fun <T> retrying(block: () -> T): T = try {
        block()
    } catch (e: AiException) {
        if (!(e.message ?: "").contains("saturado")) throw e
        Thread.sleep(60_000)
        block()
    }
}
