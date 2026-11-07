package ec.donbolon.app.ai

import android.graphics.Bitmap
import android.util.Base64
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaMessageParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import ec.donbolon.app.data.ChatMessage
import ec.donbolon.app.data.FoodItem
import ec.donbolon.app.data.Measured
import ec.donbolon.app.data.Per100
import ec.donbolon.app.data.PlannedMeal
import ec.donbolon.app.data.Shape
import ec.donbolon.app.data.Provider
import ec.donbolon.app.data.Slot
import ec.donbolon.app.data.objects
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** [badKey]: the provider refused the key itself, so the fix is pasting another one, not retrying. */
class AiException(message: String, val badKey: Boolean = false) : Exception(message)

data class Analysis(
    val items: List<FoodItem>,
    val confidence: String,
    val verdict: String,
    val better: String,
    val question: String,
)

/** What [Ai.measure] saw: each food in centimeters, and what it used as a ruler. */
data class Measurement(
    val items: List<Measured>,
    val reference: String,
    val confidence: String,
    val verdict: String,
    val better: String,
    val question: String,
)

/** Both providers behind one door. Calls block, so run them off the main thread. */
class Ai(private val provider: Provider, private val key: String) {

    val available get() = provider != Provider.NINGUNO && key.isNotBlank()

    /**
     * [answer] is the user's reply to the question a previous pass asked, so a second pass can
     * correct the first one instead of starting over.
     */
    fun analyze(photo: Bitmap?, description: String, cooking: String?, context: String, previous: Analysis? = null, answer: String = "", dish: String? = null): Analysis {
        val ask = buildString {
            append("Analiza esta comida y estima sus nutrientes.\n")
            if (previous != null) {
                append("Tu estimación anterior fue: ${previous.items.joinToString { "${it.name} ${it.grams.toInt()}g" }}.\n")
                append("Preguntaste: \"${previous.question}\" y la persona respondió: \"$answer\". Corrige la estimación con eso.\n")
            }
            if (description.isNotBlank()) append("Lo que la persona dice que comió (confía en esto por sobre la foto si hay conflicto): \"$description\"\n")
            if (cooking != null) append("Método de cocción indicado: $cooking (si es frito, suma el aceite absorbido).\n")
            if (dish != null) append(
                "Está servido en: $dish. Es una medida REAL tomada por la persona: úsala como escala para calcular el tamaño " +
                    "y los gramos de cada alimento (por ejemplo, qué fracción del diámetro ocupa el arroz, o hasta dónde llega el líquido).\n",
            )
            if (photo == null) append("No hay foto: estima solo con la descripción y porciones típicas de Ecuador.\n")
            append("\n$context")
        }
        val raw = when (provider) {
            Provider.GEMINI -> gemini(ANALYZE_SYSTEM, listOf(Turn(true, ask, photo)), json = true)
            Provider.CLAUDE -> claude(ANALYZE_SYSTEM, listOf(Turn(true, ask, photo)), effort = BetaOutputConfig.Effort.HIGH)
            Provider.NINGUNO -> throw AiException("Sin IA configurada")
        }
        return parseAnalysis(raw)
    }

    /**
     * Measures each food in centimeters instead of guessing grams: [scale] says what the ruler in the
     * photo is (the person's hand, the dish, or a known camera distance), [side] adds the height the top
     * view can't show, and [densityKeys] lists the density table the grams will come from. The grams
     * themselves are computed by [ec.donbolon.app.data.Portion], not by the model.
     */
    fun measure(top: Bitmap?, side: Bitmap?, description: String, cooking: String?, context: String, scale: String, densityKeys: Collection<String>): Measurement {
        val ask = buildString {
            append("Mide cada alimento de esta comida.\n")
            append("REGLA DE ESCALA: $scale\n")
            if (top != null) append("La primera foto es DE ARRIBA: úsala para el largo y el ancho de cada alimento.\n")
            if (side != null) append("La ${if (top != null) "segunda" else "única"} foto es DE LADO: úsala para el alto de cada alimento.\n")
            if (side == null) append("No hay foto de lado: estima el alto con la sombra, el borde del plato y lo típico de ese alimento.\n")
            if (description.isNotBlank()) append("Lo que la persona dice que comió (manda sobre la foto si hay conflicto): \"$description\"\n")
            if (cooking != null) append("Método de cocción indicado: $cooking (si es frito, cuenta el aceite en los valores por 100 g).\n")
            append("Claves de densidad disponibles: ${densityKeys.joinToString()}\n")
            append("\n$context")
        }
        val photos = listOfNotNull(top, side)
        val raw = when (provider) {
            Provider.GEMINI -> gemini(MEASURE_SYSTEM, listOf(Turn(true, ask, photos)), json = true)
            Provider.CLAUDE -> claude(MEASURE_SYSTEM, listOf(Turn(true, ask, photos)), effort = BetaOutputConfig.Effort.HIGH)
            Provider.NINGUNO -> throw AiException("Sin IA configurada")
        }
        return parseMeasurement(raw)
    }

    /** A menu for the given slots, each sized to its calorie budget. [avoid] lists titles to not repeat. */
    fun menu(context: String, budgets: Map<Slot, Int>, avoid: List<String>): List<PlannedMeal> {
        val ask = buildString {
            append("Arma comidas para estos momentos, cada una cerca de su presupuesto de kcal (±8%):\n")
            budgets.forEach { (s, k) -> append("- ${s.name}: $k kcal\n") }
            if (avoid.isNotEmpty()) append("No repitas: ${avoid.joinToString()}.\n")
            append("\n$context")
        }
        val raw = when (provider) {
            Provider.GEMINI -> gemini(MENU_SYSTEM, listOf(Turn(true, ask, null)), json = true)
            Provider.CLAUDE -> claude(MENU_SYSTEM, listOf(Turn(true, ask, null)), effort = BetaOutputConfig.Effort.LOW)
            Provider.NINGUNO -> throw AiException("Sin IA configurada")
        }
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("No entendí el menú. Intenta otra vez.")
        val o = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrElse { throw AiException("El menú vino mal. Intenta otra vez.") }
        return o.optJSONArray("meals").objects().map(PlannedMeal::from).filter { it.items.isNotEmpty() && it.slot in budgets }
    }

    fun chat(system: String, history: List<ChatMessage>): String {
        val turns = history.takeLast(20).map { Turn(it.fromUser, it.text, null) }.dropWhile { !it.user }
        return when (provider) {
            Provider.GEMINI -> gemini(system, turns, json = false)
            Provider.CLAUDE -> claude(system, turns, effort = BetaOutputConfig.Effort.LOW)
            Provider.NINGUNO -> throw AiException("Sin IA configurada")
        }.trim()
    }

    /**
     * A coach turn that also says what is worth remembering about the user long term, so
     * Don Bolón builds a memory across conversations without a second request.
     */
    fun coach(system: String, history: List<ChatMessage>): Reply {
        val turns = history.takeLast(20).map { Turn(it.fromUser, it.text, null) }.dropWhile { !it.user }
        val full = system + "\n\n" + MEMORY_RULES
        val raw = when (provider) {
            Provider.GEMINI -> gemini(full, turns, json = true)
            Provider.CLAUDE -> claude(full, turns, effort = BetaOutputConfig.Effort.LOW)
            Provider.NINGUNO -> throw AiException("Sin IA configurada")
        }
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        val o = if (start >= 0 && end > start) runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() else null
        if (o == null || o.optString("reply").isBlank()) return Reply(raw.trim(), emptyList())
        val remember = o.optJSONArray("remember")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        return Reply(o.optString("reply").trim(), remember)
    }

    data class Reply(val text: String, val remember: List<String>)

    /** The cheapest request that proves the key works, with short timeouts so connecting never hangs. */
    fun check() {
        when (provider) {
            Provider.GEMINI -> gemini("Responde solo: ok", listOf(Turn(true, "ok", null)), json = false, readTimeout = 20_000)
            Provider.CLAUDE -> claude("Responde solo: ok", listOf(Turn(true, "ok", null)), effort = BetaOutputConfig.Effort.LOW)
            Provider.NINGUNO -> throw AiException("Sin IA configurada")
        }
    }

    /** Google explains what went wrong in error.message; show that instead of guessing. */
    private fun googleMessage(body: String, code: Int): String =
        (runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()?.take(220) ?: "error $code")
            // Some messages quote the key itself; never show or log it.
            .replace(Regex("AIza[0-9A-Za-z_-]{10,}|AQ\\.[0-9A-Za-z_.-]{10,}|sk-ant-[0-9A-Za-z_-]{10,}"), "[llave]")

    /** One message; a meal can carry two photos (from above and from the side). */
    private data class Turn(val user: Boolean, val text: String, val photos: List<Bitmap> = emptyList()) {
        constructor(user: Boolean, text: String, photo: Bitmap?) : this(user, text, listOfNotNull(photo))
    }

    private fun jpeg(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun gemini(system: String, turns: List<Turn>, json: Boolean, readTimeout: Int = 90_000): String {
        val contents = JSONArray(turns.map { t ->
            val parts = JSONArray()
            t.photos.forEach { parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", jpeg(it)))) }
            parts.put(JSONObject().put("text", t.text))
            JSONObject().put("role", if (t.user) "user" else "model").put("parts", parts)
        })
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().apply {
                put("temperature", if (json) 0.2 else 0.8)
                if (json) put("responseMimeType", "application/json")
            })
            .toString()
        var last: Exception? = null
        // The model that answered last time goes first, so a working setup costs one request.
        for (model in listOfNotNull(workingModel) + GEMINI_MODELS.filter { it != workingModel }) {
            try {
                val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    this.readTimeout = readTimeout
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-goog-api-key", key)
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
                when {
                    code == 400 && text.contains("API_KEY_INVALID") -> { keyRejected = true; throw AiException("Google dice que esta llave no es válida. Pega la que usas ahora (la de AI Studio).", badKey = true) }
                    // New "AQ." keys get 401 on some models and work on others, so a 401 is not final until every model said it.
                    code == 401 -> { last = AiException("Google rechazó la llave guardada (" + googleMessage(text, code) + "). Pega la que usas ahora (la de AI Studio).", badKey = true); continue }
                    // 403 can be about one model (not enabled for this key or tier), so try the next before giving up.
                    code == 403 -> { last = AiException("Google no dio acceso: " + googleMessage(text, code)); continue }
                    code == 429 -> { last = AiException("Gemini está saturado o se acabó tu cuota. Intenta en un minuto."); continue }
                    code == 404 || code >= 500 -> { last = AiException("Gemini no respondió ($code)."); continue }
                    code !in 200..299 -> throw AiException("Gemini: " + googleMessage(text, code))
                }
                val parts = JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val out = parts.objects().joinToString("") { it.optString("text") }
                if (out.isBlank()) { last = AiException("Gemini no devolvió respuesta."); continue }
                workingModel = model
                keyRejected = false
                return out
            } catch (e: IOException) {
                last = AiException("Sin conexión. Revisa tu internet.")
            }
        }
        if ((last as? AiException)?.badKey == true) keyRejected = true
        throw last ?: AiException("Gemini no respondió.")
    }

    private fun claude(system: String, turns: List<Turn>, effort: BetaOutputConfig.Effort): String {
        val client = AnthropicOkHttpClient.builder().apiKey(key).build()
        val messages = turns.map { t ->
            val blocks = buildList {
                t.photos.forEach {
                    add(BetaContentBlockParam.ofImage(
                        BetaImageBlockParam.builder().source(
                            BetaBase64ImageSource.builder().data(jpeg(it)).mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG).build()
                        ).build()
                    ))
                }
                add(BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(t.text).build()))
            }
            BetaMessageParam.builder()
                .role(if (t.user) BetaMessageParam.Role.USER else BetaMessageParam.Role.ASSISTANT)
                .contentOfBetaContentBlockParams(blocks)
                .build()
        }
        try {
            val params = MessageCreateParams.builder()
                .model(CLAUDE_MODEL)
                .maxTokens(8000L)
                .system(system)
                .outputConfig(BetaOutputConfig.builder().effort(effort).build())
                .messages(messages)
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()
            val response = client.beta().messages().create(params)
            if (response.stopReason().orElse(null) == BetaStopReason.REFUSAL) throw AiException("Claude no quiso responder a eso.")
            return response.content().flatMap { b -> b.text().map { listOf(it.text()) }.orElse(emptyList()) }.joinToString("\n")
        } catch (e: UnauthorizedException) {
            keyRejected = true
            throw AiException("Anthropic dice que esta llave no es válida. Pega la que usas ahora.", badKey = true)
        } catch (e: RateLimitException) {
            throw AiException("Claude está saturado. Intenta en un minuto.")
        } catch (e: AnthropicIoException) {
            throw AiException("Sin conexión. Revisa tu internet.")
        } finally {
            client.close()
        }
    }

    companion object {
        private val GEMINI_MODELS = listOf("gemini-3.6-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite")

        /** The Gemini model that last answered, remembered for the rest of the session. */
        @Volatile private var workingModel: String? = null

        /** The saved key was refused by the provider, so Perfil stops calling it "Conectado". */
        @Volatile var keyRejected = false
        private const val CLAUDE_MODEL = "claude-opus-5-5"

        private const val ANALYZE_SYSTEM = """Eres un nutricionista experto en estimar porciones a partir de fotos y descripciones, con dominio de la comida ecuatoriana y latinoamericana. Tu trabajo es estimar con honestidad, no adivinar con falsa precisión.

Método:
1. Identifica cada componente del plato por separado (incluye salsas, aceite, bebidas, acompañantes como chifles o arroz).
2. Estima los gramos de cada uno. Si te dan la medida real del plato, bowl o vaso, úsala como escala principal; si no, usa referencias visuales (plato estándar ~26 cm, cubiertos, manos) y porciones típicas.
3. Calcula kcal, proteína, carbohidratos, grasa y fibra con valores de tablas de composición (USDA / tabla ecuatoriana). Comprueba que kcal ≈ 4·P + 4·C + 9·G.
4. Si la descripción contradice la foto, la descripción manda. Si algo no se ve (aceite, azúcar en bebidas), asume lo típico y dilo.

Responde SOLO con JSON válido, sin texto extra ni markdown, con esta forma exacta:
{"items":[{"name":"nombre corto en español","grams":0,"kcal":0,"protein":0,"carbs":0,"fat":0,"fiber":0}],
 "confidence":"alta|media|baja",
 "question":"UNA pregunta corta solo si algo que la foto no puede mostrar cambia las calorías en más de ~15% (¿frito o a la plancha?, ¿la bebida tiene azúcar?, ¿qué tiene adentro?, ¿cuánto queda en el plato?); si la foto basta, vacío",
 "verdict":"1-2 frases de Don Bolón, un bolón de queso gruñón pero cariñoso: comentario honesto del plato en relación con lo que le queda del día. Sin culpa ni vergüenza.",
 "better":"una mejora concreta y realista para la próxima vez (cambio, porción o acompañante), o vacío si el plato está muy bien"}
Si la imagen no contiene comida, devuelve items vacío y explícalo en verdict."""

        private const val MEMORY_RULES = """FORMATO DE RESPUESTA: responde SOLO con JSON válido, sin markdown:
{"reply":"tu respuesta para la persona","remember":["dato nuevo y duradero sobre la persona"]}
En "remember" pon SOLO datos que la persona dijo explícitamente sobre sí misma en su ÚLTIMO mensaje y que valga la pena recordar por semanas (gustos, alimentos que no come, horarios, deporte, lesiones, metas, lo que le cuesta, lo que le funciona), en tercera persona y en una frase corta.
Nunca guardes lo que tú sugeriste o recomendaste, ni lo que supones o deduces, ni lo que ya está en la MEMORIA DE LARGO PLAZO, ni lo que comió hoy. Si su último mensaje es solo una pregunta, deja la lista vacía. Ante la duda, lista vacía."""

        private const val MENU_SYSTEM ="""Eres nutricionista y armas menús diarios para una persona en Ecuador, como lo hace la app Fitia: comida real, casera y fácil de conseguir en Ecuador (mercado, supermercado o un almuerzo típico), con porciones en gramos que cuadren con el presupuesto de calorías.

Reglas:
- Prioriza proteína en cada comida (reparte la meta diaria), verduras o fruta, y almidones medidos (arroz, verde, papa, yuca, quinua, mote).
- Incluye platos ecuatorianos cuando encajen (bolón airfryer, seco, menestra, encebollado, ceviche, locro, tigrillo ligero), sin abusar de fritos.
- Variedad: no repitas la misma proteína en todas las comidas.
- Valores de tablas de composición (USDA / tabla ecuatoriana). Comprueba kcal ≈ 4·P + 4·C + 9·G.
- Si el contexto muestra lo que ya comió hoy, no lo repitas.

Responde SOLO con JSON válido, sin markdown:
{"meals":[{"slot":"DESAYUNO|ALMUERZO|CENA|SNACK","title":"nombre corto del plato","items":[{"name":"","grams":0,"kcal":0,"protein":0,"carbs":0,"fat":0,"fiber":0}]}]}"""

        private const val MEASURE_SYSTEM = """Eres un experto en medir porciones de comida en fotos, con dominio de la comida ecuatoriana (arroz, menestra, verde, maduro, bolón, patacón, seco, encebollado, ceviche, locro, mote, yuca, tigrillo). NO estimas gramos: mides centímetros. Los gramos los calcula después un programa con la geometría y una tabla de densidades, así que tu trabajo es medir bien.

Método:
1. Lee la REGLA DE ESCALA. Si es la mano de la persona, localízala en la foto y usa sus medidas reales (ancho de palma, largo del dedo medio, ancho del pulgar) como regla. Si es el plato, usa su diámetro real. Si es la distancia de la cámara, usa el ancho real que cubre la imagen.
2. Separa cada componente del plato (incluye salsas, aceite visible, curtido, bebidas, acompañantes).
3. Para cada uno elige la forma:
   - MOUND: montón (arroz, menestra, puré, majado). length y width = la huella sobre el plato; height = lo más alto del montón.
   - FLAT: capa plana (carne, filete, huevo frito, tortilla, patacón). height = grosor.
   - CYLINDER: pieza redonda de pie (vaso, taza, bolón visto de lado).
   - PIECE: pieza sólida (presa de pollo, papa, maduro, yuca, choclo): length, width y height de la pieza; si hay varias iguales, una entrada por pieza o describe una y repítela.
   - LIQUID: sopa o bebida en un recipiente: length/width/height del recipiente por dentro y fill = fracción llena (0 a 1).
4. Mide length, width y height en centímetros con la regla, no con porciones típicas. Si una medida no se ve, estímala con lo que sí se ve y baja la confianza.
5. Elige densityKey de la lista de claves disponibles (la más parecida al alimento); si ninguna encaja, null.
6. Da los valores por 100 g del alimento TAL COMO ESTÁ servido (cocido, frito, con su aceite), de tablas de composición (tabla ecuatoriana o USDA). Comprueba kcal ≈ 4·P + 4·C + 9·G.
7. Si la descripción contradice la foto, la descripción manda.

Responde SOLO con JSON válido, sin texto extra ni markdown, con esta forma exacta:
{"items":[{"name":"nombre corto en español","shape":"MOUND|FLAT|CYLINDER|PIECE|LIQUID","length":0,"width":0,"height":0,"fill":null,"densityKey":"clave o null","per100":{"kcal":0,"protein":0,"carbs":0,"fat":0,"fiber":0}}],
 "reference":"qué usaste como regla y cuánto mide en la foto, en una frase",
 "confidence":"alta|media|baja",
 "question":"UNA pregunta corta solo si algo que la foto no puede mostrar cambia las calorías en más de ~15% (¿frito o a la plancha?, ¿la bebida tiene azúcar?); si la foto basta, vacío",
 "verdict":"1-2 frases de Don Bolón, un bolón de queso gruñón pero cariñoso, sobre el plato en relación con lo que le queda del día. Sin culpa ni vergüenza.",
 "better":"una mejora concreta y realista para la próxima vez, o vacío"}
Si la imagen no contiene comida, devuelve items vacío y explícalo en verdict."""

        /** Reads [measure]'s JSON; items with no size or an unknown shape are dropped instead of guessed. */
        fun parseMeasurement(raw: String): Measurement {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) throw AiException("No entendí la respuesta de la IA. Intenta otra vez.")
            val o = try { JSONObject(raw.substring(start, end + 1)) } catch (e: Exception) { throw AiException("La IA respondió algo raro. Intenta otra vez.") }
            val items = o.optJSONArray("items").objects().mapNotNull { j ->
                val shape = runCatching { Shape.valueOf(j.optString("shape").uppercase()) }.getOrNull() ?: return@mapNotNull null
                val p = j.optJSONObject("per100") ?: return@mapNotNull null
                val m = Measured(
                    name = j.optString("name").ifBlank { return@mapNotNull null },
                    shape = shape,
                    lengthCm = j.optDouble("length", 0.0),
                    widthCm = j.optDouble("width", 0.0),
                    heightCm = j.optDouble("height", 0.0),
                    per100 = Per100(p.optDouble("kcal", 0.0), p.optDouble("protein", 0.0), p.optDouble("carbs", 0.0), p.optDouble("fat", 0.0), p.optDouble("fiber", 0.0)),
                    densityKey = j.optString("densityKey").takeIf { it.isNotBlank() && it != "null" },
                    fill = if (j.isNull("fill")) null else j.optDouble("fill").takeIf { !it.isNaN() },
                )
                // NaN fails every comparison, so a missing size drops the item too.
                m.takeIf { it.lengthCm > 0 && it.widthCm > 0 && it.heightCm > 0 }
            }
            return Measurement(
                items = items,
                reference = o.optString("reference"),
                confidence = o.optString("confidence", "media"),
                verdict = o.optString("verdict"),
                better = o.optString("better"),
                question = o.optString("question"),
            )
        }

        fun parseAnalysis(raw: String): Analysis {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) throw AiException("No entendí la respuesta de la IA. Intenta otra vez.")
            val o = try { JSONObject(raw.substring(start, end + 1)) } catch (e: Exception) { throw AiException("La IA respondió algo raro. Intenta otra vez.") }
            return Analysis(
                items = o.optJSONArray("items").objects().map(FoodItem::from).filter { it.grams > 0 || it.kcal > 0 },
                confidence = o.optString("confidence", "media"),
                verdict = o.optString("verdict"),
                better = o.optString("better"),
                question = o.optString("question"),
            )
        }
    }
}
