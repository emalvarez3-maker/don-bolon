package ec.donbolon.app.ai

import android.graphics.Bitmap
import android.util.Base64
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicInvalidDataException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
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
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.URL
import java.net.UnknownHostException
import java.text.Normalizer
import java.time.Duration

/** [badKey]: the provider refused the key itself, so the fix is pasting another one, not retrying. */
class AiException(message: String, val badKey: Boolean = false) : Exception(message)

/**
 * Model text made safe to store and show on one line: control characters and line breaks become
 * spaces, runs of spaces collapse, and the result is cut to [max] characters.
 */
fun String.oneLine(max: Int): String =
    replace(Regex("[\\p{Cntrl}\\u2028\\u2029\\u0085]"), " ").replace(Regex("\\s+"), " ").trim().take(max).trim()

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
    /** What the model used as its ruler: "mano", "plato" or "estimado". */
    val ruler: String = "estimado",
)

/** Both providers behind one door. Calls block, so run them off the main thread. */
class Ai(private val provider: Provider, private val key: String) {

    val available get() = provider != Provider.NINGUNO && key.isNotBlank()

    /**
     * [answer] is the user's reply to the question a previous pass asked, so a second pass can
     * correct the first one instead of starting over. [clarifications] are earlier (question, answer)
     * pairs the person already answered.
     */
    fun analyze(
        photo: Bitmap?, description: String, cooking: String?, context: String, previous: Analysis? = null, answer: String = "",
        dish: String? = null, clarifications: List<Pair<String, String>> = emptyList(),
    ): Analysis {
        val ask = buildString {
            append("Analiza esta comida y estima sus nutrientes.\n")
            if (previous != null) {
                append("Tu estimación anterior fue: ${previous.items.joinToString { "${it.name} ${it.grams.toInt()}g" }}.\n")
                append("Preguntaste: \"${previous.question}\" y la persona respondió: \"$answer\". Corrige la estimación con eso.\n")
            }
            append(clarify(clarifications))
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
    fun measure(
        top: Bitmap?, side: Bitmap?, description: String, cooking: String?, context: String, scale: String, densityKeys: Collection<String>,
        clarifications: List<Pair<String, String>> = emptyList(),
    ): Measurement {
        val ask = buildString {
            append("Mide cada alimento de esta comida.\n")
            append("REGLA DE ESCALA: $scale\n")
            append("Si la mano aparece en la foto, úsala como regla principal; si no aparece, usa el plato (su medida real si te la dan) y dilo en reference.\n")
            append(clarify(clarifications))
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
        return Reply(o.optString("reply").trim(), remembered(o))
    }

    data class Reply(val text: String, val remember: List<String>)

    /** The cheapest request that proves the key works, with short timeouts so connecting never hangs. */
    fun check() {
        when (provider) {
            Provider.GEMINI -> gemini("Responde solo: ok", listOf(Turn(true, "ok", null)), json = false, readTimeout = 20_000)
            Provider.CLAUDE -> claude("Responde solo: ok", listOf(Turn(true, "ok", null)), effort = BetaOutputConfig.Effort.LOW, timeout = Duration.ofSeconds(20))
            Provider.NINGUNO -> throw AiException("Sin IA configurada")
        }
    }

    /** One message; a meal can carry two photos (from above and from the side). */
    private data class Turn(val user: Boolean, val text: String, val photos: List<Bitmap> = emptyList()) {
        constructor(user: Boolean, text: String, photo: Bitmap?) : this(user, text, listOfNotNull(photo))
    }

    private fun jpeg(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun gemini(system: String, turns: List<Turn>, json: Boolean, readTimeout: Int = 60_000): String {
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
                val parts = try {
                    JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                } catch (e: JSONException) { throw AiException(ODD) }
                val out = parts.objects().joinToString("") { it.optString("text") }
                if (out.isBlank()) { last = AiException("Gemini no devolvió respuesta."); continue }
                workingModel = model
                keyRejected = false
                return out
            } catch (e: IOException) {
                val err = networkError(e)
                // A timeout or no network will not get better with another model: answer now instead of hanging for minutes.
                if (err.message == SLOW || err.message == OFFLINE) throw err
                last = err
            }
        }
        if ((last as? AiException)?.badKey == true) keyRejected = true
        throw last ?: AiException("Gemini no respondió.")
    }

    private fun claude(system: String, turns: List<Turn>, effort: BetaOutputConfig.Effort, timeout: Duration = Duration.ofSeconds(75)): String {
        // No silent retries: one bounded request, so "Analizando" never outlives the timeout.
        val client = AnthropicOkHttpClient.builder().apiKey(key).timeout(timeout).maxRetries(0).build()
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
        } catch (e: AnthropicServiceException) {
            throw claudeError(e.statusCode(), e.message.orEmpty())
        } catch (e: AnthropicInvalidDataException) {
            throw AiException(ODD)
        } catch (e: AnthropicIoException) {
            throw networkError(e)
        } catch (e: IOException) {
            throw networkError(e)
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

        const val SLOW = "La IA tardó demasiado. Intenta otra vez."
        const val OFFLINE = "Sin conexión. Revisa tu internet."
        const val ODD = "La IA respondió algo raro. Intenta otra vez."

        /** A key, or the start of one cut by a length limit: the prefix plus 4 or more key characters. */
        private val KEY = Regex("AIza[0-9A-Za-z_-]{4,}|AQ\\.[0-9A-Za-z_.-]{4,}|sk-ant-[0-9A-Za-z_-]{4,}")

        /** Removes anything that looks like an API key, so provider or exception text can be shown and logged. */
        fun scrub(s: String): String = s.replace(KEY, "[llave]")

        /** Google explains what went wrong in error.message; show that instead of guessing. Scrubbed before cutting, so a cut key can't leak. */
        internal fun googleMessage(body: String, code: Int): String =
            runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
                ?.let { scrub(it).oneLine(220) }?.ifBlank { null } ?: "error $code"

        /** No network, too slow, or something else on the wire: each gets its own message. */
        internal fun networkError(e: Throwable): AiException {
            val chain = generateSequence(e) { it.cause.takeIf { c -> c !== it } }.take(8).toList()
            return when {
                chain.any { it is InterruptedIOException } -> AiException(SLOW)
                chain.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException } -> AiException(OFFLINE)
                else -> AiException("Falló la conexión con la IA. Intenta otra vez.")
            }
        }

        /** Anthropic errors other than a bad key or rate limit, in short Spanish with the provider's text scrubbed. */
        internal fun claudeError(code: Int, message: String): AiException {
            val detail = scrub(message).oneLine(140).let { if (it.isBlank()) "" else ": $it" }
            return when {
                code == 403 -> AiException("Anthropic no dio acceso con esta llave (403)$detail")
                code >= 500 -> AiException("Claude no respondió ($code). Intenta en un rato.")
                code == 400 -> AiException("Claude no aceptó la solicitud (400)$detail")
                else -> AiException("Claude dio un error ($code)$detail")
            }
        }

        /** The answers the person already gave, so a retry applies them instead of asking again. */
        internal fun clarify(clarifications: List<Pair<String, String>>): String = buildString {
            clarifications.filter { it.second.isNotBlank() }.forEach { (q, a) ->
                append("Antes preguntaste: «${q.oneLine(200)}» y la persona respondió: «${a.oneLine(300)}». Aplícalo.\n")
            }
        }

        /** At most two short, single-line memories per turn: model text is not trusted to stay small. */
        internal fun remembered(o: JSONObject): List<String> =
            o.optJSONArray("remember")?.let { a -> (0 until a.length()).map { a.optString(it).oneLine(140) }.filter { it.isNotBlank() } }
                ?.take(2) ?: emptyList()

        private fun plain(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

        private val SHAPES = mapOf(
            "mound" to Shape.MOUND, "monton" to Shape.MOUND, "monticulo" to Shape.MOUND,
            "flat" to Shape.FLAT, "plano" to Shape.FLAT, "plana" to Shape.FLAT, "capa" to Shape.FLAT,
            "cylinder" to Shape.CYLINDER, "cilindro" to Shape.CYLINDER,
            "piece" to Shape.PIECE, "pieza" to Shape.PIECE,
            "liquid" to Shape.LIQUID, "liquido" to Shape.LIQUID,
        )

        internal fun shapeOf(s: String): Shape? = SHAPES[plain(s)]

        /** A size as a number, or as text like "12 cm" or "3,5"; NaN when there is no number. */
        internal fun number(j: JSONObject, key: String): Double = when (val v = j.opt(key)) {
            is Number -> v.toDouble()
            is String -> Regex("-?\\d+(?:[.,]\\d+)?").find(v)?.value?.replace(',', '.')?.toDoubleOrNull() ?: Double.NaN
            else -> Double.NaN
        }

        private fun rulerOf(s: String): String = plain(s).takeIf { it in setOf("mano", "plato", "estimado") } ?: "estimado"

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
1. Lee la REGLA DE ESCALA. Si la mano aparece en la foto, úsala como regla principal con sus medidas reales (ancho de palma, largo del dedo medio, ancho del pulgar). Si no aparece, usa el plato (su diámetro real si te lo dan) y dilo en reference. Si no hay mano ni plato claros, estima con lo que se vea (cubiertos, distancia de la cámara) y pon ruler "estimado". No inventes una mano que no está en la foto.
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
 "ruler":"mano|plato|estimado",
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
                val shape = shapeOf(j.optString("shape")) ?: return@mapNotNull null
                val p = j.optJSONObject("per100") ?: return@mapNotNull null
                val m = Measured(
                    name = j.optString("name").oneLine(60).ifBlank { return@mapNotNull null },
                    shape = shape,
                    lengthCm = number(j, "length"),
                    widthCm = number(j, "width"),
                    heightCm = number(j, "height"),
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
                ruler = rulerOf(o.optString("ruler")),
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
