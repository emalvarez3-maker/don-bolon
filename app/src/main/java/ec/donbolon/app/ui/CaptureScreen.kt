package ec.donbolon.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ec.donbolon.app.AppModel
import ec.donbolon.app.ai.Ai
import ec.donbolon.app.ai.AiException
import ec.donbolon.app.ai.Analysis
import ec.donbolon.app.data.Fast
import ec.donbolon.app.data.Measured
import ec.donbolon.app.data.Portion
import ec.donbolon.app.data.FoodDb
import ec.donbolon.app.data.FoodItem
import ec.donbolon.app.data.Meal
import ec.donbolon.app.data.Memory
import ec.donbolon.app.data.Mood
import ec.donbolon.app.data.Reason
import ec.donbolon.app.data.Science
import ec.donbolon.app.data.Slot
import ec.donbolon.app.data.totals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

private val cookings = listOf("A la plancha", "Frito", "Airfryer", "Al horno", "Hervido / sopa", "Crudo")
private val thinkingLines = listOf(
    "Mirando el plato…", "Separando ingredientes…", "Estimando porciones…",
    "Buscando el aceite escondido…", "Comparando con tus porciones de siempre…", "Calculando proteína y calorías…",
)

private enum class Step { COMPOSE, THINKING, REVIEW }

/** The rules behind re-running an estimate, free of Compose so they can be tested. */
internal object CaptureRules {
    /** Adds an answered question to what the AI must keep in mind; a blank answer or a repeat adds nothing. */
    fun clarify(list: List<Pair<String, String>>, question: String, answer: String): List<Pair<String, String>> {
        val q = question.trim()
        val a = answer.trim()
        if (a.isBlank() || list.any { it.first == q && it.second == a }) return list
        return list + (q to a)
    }

    /** What the person added themselves ("¿Falta algo?", the libreta), as it is now: it survives a new estimate. */
    fun kept(items: List<FoodItem>, added: List<Boolean>): List<FoodItem> = items.filterIndexed { i, _ -> added.getOrElse(i) { false } }

    /** The AI's items as they are now, without what the person added. */
    fun fromAi(items: List<FoodItem>, added: List<Boolean>): List<FoodItem> = items.filterIndexed { i, _ -> !added.getOrElse(i) { false } }

    /** True when the person changed, renamed or removed anything the AI estimated. */
    fun adjusted(ai: List<FoodItem>, items: List<FoodItem>, added: List<Boolean>): Boolean = fromAi(items, added) != ai

    /** What the AI really used to size the food. */
    fun rulerLabel(ruler: String): String = when (ruler) {
        "mano" -> "medido con tu mano"
        "plato" -> "medido con tu plato"
        else -> "tamaño estimado"
    }
}

private const val FAILED = "Algo falló. Intenta otra vez."
private const val ANALYSIS_TIMEOUT_MS = 90_000L

/**
 * Logging a meal: photo (or voice, or words) → the AI estimates every ingredient → the user
 * adjusts portions with a slider → save. Shortcuts for what they eat often keep logging under
 * ten seconds, because the easier it is, the more consistently people log (Burke et al., 2011).
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CaptureScreen(model: AppModel, onDone: () -> Unit, onSos: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by model.store.state.collectAsState()
    var ai by remember { mutableStateOf(model.ai()) }
    val targets = model.targets()

    var step by remember { mutableStateOf(Step.COMPOSE) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    // The view from the side gives the height the top view can't, so volumes stop being guesses.
    var side by remember { mutableStateOf<Bitmap?>(null) }
    var handSetup by remember { mutableStateOf(false) }
    // What the AI used as a ruler in the last measurement: "mano", "plato" or "estimado".
    var ruler by remember { mutableStateOf("estimado") }
    var description by rememberSaveable { mutableStateOf("") }
    var cooking by rememberSaveable { mutableStateOf<String?>(null) }
    var slot by rememberSaveable { mutableStateOf(Slot.forHour(LocalTime.now().hour)) }
    var at by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var hunger by rememberSaveable { mutableStateOf(6f) }
    var reason by rememberSaveable { mutableStateOf<Reason?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    // Every question the AI asked and the person answered; each later estimate gets all of them.
    var clarifications by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var answer by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf("") }
    var addingMissing by remember { mutableStateOf(false) }
    var nudged by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var mindful by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("foto") }
    var job by remember { mutableStateOf<Job?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // A dish chip tapped after the person adjusted the items: re-running waits for their yes.
    var pendingDish by remember { mutableStateOf<Pair<String?, Boolean>?>(null) }
    var dishId by rememberSaveable { mutableStateOf(state.settings.lastDish) }
    fun chooseDish(id: String?) {
        dishId = id
        model.store.edit { it.copy(settings = it.settings.copy(lastDish = id)) }
    }
    val items = remember { mutableStateListOf<FoodItem>() }
    // Per item, by position: the grams it had when it arrived (the slider scales from there),
    // whether the person added it, and what the AI measured for it.
    val bases = remember { mutableStateListOf<Double>() }
    val added = remember { mutableStateListOf<Boolean>() }
    val sizes = remember { mutableStateListOf<Measured?>() }

    fun setItems(list: List<FoodItem>, measures: List<Measured> = emptyList()) {
        items.clear(); items.addAll(list)
        bases.clear(); bases.addAll(list.map { it.grams })
        added.clear(); added.addAll(list.map { false })
        sizes.clear(); sizes.addAll(list.indices.map { measures.getOrNull(it) })
    }
    fun addItem(f: FoodItem) { items.add(f); bases.add(f.grams); added.add(true); sizes.add(null) }
    fun removeAt(i: Int) { items.removeAt(i); bases.removeAt(i); added.removeAt(i); sizes.removeAt(i) }

    val camFile = remember { File(context.cacheDir, "camera").apply { mkdirs() }.resolve("plato.jpg") }
    val camUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", camFile) }
    // A new top photo (camera or gallery) is a new plate: the old side view no longer belongs to it.
    fun accept(uri: Uri?) {
        External.back()
        scope.launch {
            val b = if (uri == null) null else withContext(Dispatchers.IO) { Images.load(context, uri) }
            // The full-size original is only needed until it is decoded.
            withContext(Dispatchers.IO) { Images.clearCamera(context) }
            if (b != null) { photo = b; side = null; error = null } else if (uri != null) error = "No pude abrir esa foto. Prueba otra."
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> accept(if (ok) camUri else null) }
    val sideFile = remember { File(context.cacheDir, "camera").apply { mkdirs() }.resolve("plato-lado.jpg") }
    val sideUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", sideFile) }
    val sideCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        External.back()
        scope.launch {
            val b = if (ok) withContext(Dispatchers.IO) { Images.load(context, sideUri) } else null
            withContext(Dispatchers.IO) { Images.clearCamera(context) }
            if (b != null) side = b else if (ok) error = "No pude abrir esa foto. Prueba otra."
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { accept(it) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        External.back()
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !said.isNullOrBlank()) description = listOf(description, said).filter { it.isNotBlank() }.joinToString(", ")
    }
    fun shoot() = runCatching { External.open = true; camera.launch(camUri) }.onFailure { External.open = false; error = "No encontré una app de cámara." }
    fun shootSide() = runCatching { External.open = true; sideCamera.launch(sideUri) }.onFailure { External.open = false; error = "No encontré una app de cámara." }
    fun pick() = runCatching { External.open = true; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        .onFailure { External.open = false; error = "No pude abrir la galería." }
    fun listen() = runCatching {
        External.open = true
        voice.launch(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-EC")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué comiste?"),
        )
    }.onFailure { External.open = false; error = "Este teléfono no tiene dictado por voz." }

    // With the AI connected, open the camera for the top view right away: one tap from home to a
    // photo. Nothing else opens on its own: the side view waits in its slot for the person.
    LaunchedEffect(Unit) { if (ai.available) shoot() }

    fun aiContext() = Science.context(state, targets) + "\n" + Memory.forAnalysis(state)

    fun runAnalysis() {
        if (job?.isActive == true) return
        error = null
        step = Step.THINKING
        job = scope.launch {
            try {
                val dish = state.dishes.firstOrNull { it.id == dishId }
                val top = photo
                val sideNow = side
                val asked = clarifications
                val said = description
                val (result, measures, used) = withTimeout(ANALYSIS_TIMEOUT_MS) {
                    // With a photo the AI measures in centimeters and geometry gives the grams; words alone
                    // have nothing to measure, so they keep the estimate in grams.
                    val m = if (top == null && sideNow == null) null else runInterruptible(Dispatchers.IO) {
                        ai.measure(top, sideNow, said, cooking, aiContext(), Portion.scale(state.profile.hand, dish), Portion.densities.keys, asked)
                    }.takeIf { it.items.isNotEmpty() }
                    if (m != null) Triple(Analysis(Portion.items(m.items, dish), m.confidence, m.verdict, m.better, m.question), m.items, m.ruler)
                    else Triple(
                        runInterruptible(Dispatchers.IO) { ai.analyze(top, said, cooking, aiContext(), dish = dish?.describe(), clarifications = asked) },
                        emptyList<Measured>(), "estimado",
                    )
                }
                // What the person added by hand is theirs: a new estimate replaces only the AI's part.
                val keep = CaptureRules.kept(items.toList(), added.toList())
                analysis = result
                ruler = used
                setItems(result.items, measures)
                keep.forEach { addItem(it) }
                source = if (photo != null) "foto" else "texto"
                answer = ""
                step = Step.REVIEW
            } catch (e: TimeoutCancellationException) {
                error = Ai.SLOW; step = Step.COMPOSE
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                error = e.message ?: FAILED; step = Step.COMPOSE
            } catch (e: Exception) {
                Log.w("DonBolon", Ai.scrub(e.toString()))
                error = FAILED; step = Step.COMPOSE
            }
        }
    }

    // Stops waiting for the AI and goes back to the meal as it was: nothing typed or shot is lost.
    fun cancelAnalysis() {
        job?.cancel()
        job = null
        step = Step.COMPOSE
    }

    fun quick(list: List<FoodItem>, from: String) {
        analysis = null
        setItems(list)
        source = from
        step = Step.REVIEW
    }

    fun openSearch(q: String) { searchQuery = q.trim(); searching = true }

    fun addMissing() {
        val text = missing.trim()
        if (text.isBlank() || addingMissing) return
        addingMissing = true
        scope.launch {
            try {
                val found = try {
                    withTimeout(ANALYSIS_TIMEOUT_MS) { runInterruptible(Dispatchers.IO) { ai.analyze(null, text, null, aiContext(), clarifications = clarifications) }.items }
                } catch (e: TimeoutCancellationException) {
                    FoodDb.guess(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e !is AiException) Log.w("DonBolon", Ai.scrub(e.toString()))
                    FoodDb.guess(text)
                }
                found.forEach { addItem(it) }
                // Nothing found: the words stay where they were and the libreta opens with them.
                if (found.isEmpty()) openSearch(text) else missing = ""
            } finally {
                addingMissing = false
            }
        }
    }

    fun save() {
        val id = UUID.randomUUID().toString()
        val photoName = photo?.let { b -> "$id.jpg".also { Images.saveThumb(b, File(model.store.photos, it)) } }
        model.store.addMeal(
            Meal(
                id = id, time = LocalDate.now().atTime(at).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                slot = slot, items = items.toList(), photo = photoName, note = description,
                hunger = hunger.toInt(), reason = reason, mindful = mindful, source = source,
            )
        )
        analysis?.let { a ->
            val fixes = Memory.corrections(a.items, CaptureRules.fromAi(items.toList(), added.toList()))
            if (fixes.isNotEmpty()) model.store.edit { it.copy(corrections = it.corrections + fixes) }
        }
        Images.clearCamera(context)
        onDone()
    }

    val hasWork = photo != null || side != null || description.isNotBlank() || items.isNotEmpty()
    fun close() { if (hasWork) confirmDiscard = true else onDone() }

    val emotional = reason?.physical == false && hunger <= 4f
    val total = items.totals()
    val leftAfter = targets.kcal - state.totalsOn(LocalDate.now()).kcal - total.kcal

    if (handSetup) {
        BackHandler { if (!External.justReturned()) handSetup = false }
        Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
            HandSetup(
                state.profile.hand,
                onSave = { h -> model.store.edit { it.copy(profile = it.profile.copy(hand = h)) }; handSetup = false },
                onCancel = { handSetup = false },
            )
        }
        return
    }

    // Back walks one step back instead of throwing the meal away.
    BackHandler(enabled = step != Step.COMPOSE || hasWork) {
        if (External.justReturned()) return@BackHandler
        when (step) {
            Step.REVIEW -> step = Step.COMPOSE
            Step.THINKING -> cancelAnalysis()
            Step.COMPOSE -> confirmDiscard = true
        }
    }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ close() }) { Icon(Icons.Filled.Close, "Cerrar", tint = C.muted) }
            Text(
                when (step) { Step.COMPOSE -> "Registrar comida"; Step.THINKING -> "Analizando"; Step.REVIEW -> "Revisa y guarda" },
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            StepDots(step.ordinal)
        }

        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "step") { s ->
            when (s) {
                Step.THINKING -> Thinking(photo, onCancel = { cancelAnalysis() })

                Step.COMPOSE -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    // A refused key is fixed right here, without leaving the meal half written.
                    if (!ai.available || Ai.keyRejected) {
                        ConnectAi(model) { ai = model.ai(); error = null; if (description.isNotBlank() || photo != null) runAnalysis() }
                        Gap(16.dp)
                    }
                    TwoViewCapture(
                        top = photo, side = side, handReady = state.profile.hand != null,
                        onShootTop = { shoot() }, onShootSide = { shootSide() }, onPickTop = { pick() }, onClearSide = { side = null },
                        onSetupHand = { handSetup = true },
                    )

                    val usuals = remember(state.meals.size) { Memory.usuals(state).take(6) }
                    val recent = remember(state.meals.size) {
                        state.meals.sortedByDescending { it.time }.distinctBy { m -> m.items.joinToString { it.name } }.take(3)
                    }
                    if (usuals.isNotEmpty() || recent.isNotEmpty()) {
                        Gap(16.dp)
                        Label("Rápido")
                        Gap(8.dp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recent.forEach { m ->
                                Chip("↻ ${m.items.joinToString { it.name }.take(28)}", false) {
                                    slot = Slot.forHour(LocalTime.now().hour)
                                    quick(m.items, "repetida")
                                }
                            }
                            usuals.forEach { u ->
                                Chip("${u.name} · ${u.grams.roundToInt()} g", false) {
                                    val ref = state.meals.flatMap { it.items }.last { it.name == u.name }
                                    quick(listOf(ref.withGrams(u.grams)), "rápida")
                                }
                            }
                        }
                    }
                    Gap(18.dp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HintField(description, { description = it }, "¿Qué comiste?", "Ej: bolón de queso, 2 huevos, café con leche", Modifier.weight(1f), singleLine = false)
                        HGap(8.dp)
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(C.raised).clickable(onClickLabel = "Dictar por voz") { listen() },
                            contentAlignment = Alignment.Center,
                        ) { Text("🎤", fontSize = 22.sp) }
                    }
                    Gap(12.dp)
                    Text("¿Cómo se cocinó? (opcional)", color = C.muted, fontSize = 13.sp)
                    Gap(8.dp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cookings.forEach { c -> Chip(c, cooking == c) { cooking = if (cooking == c) null else c } }
                    }
                    Gap(14.dp)
                    Text("¿En qué lo serviste? Con la medida real calculo mejor las porciones", color = C.muted, fontSize = 13.sp)
                    Gap(8.dp)
                    DishChips(state.dishes, dishId) { chooseDish(it) }

                    error?.let {
                        Gap(14.dp)
                        Text(
                            "⚠ $it", color = C.text, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.raised).padding(12.dp),
                        )
                    }
                    Gap(24.dp)
                }

                Step.REVIEW -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    item {
                        Summary(photo, total.kcal, total.protein, total.carbs, total.fat, analysis?.confidence)
                        analysis?.let { a ->
                            // Changing the dish here re-runs the estimate with the new scale.
                            Gap(10.dp)
                            Text("Servido en (toca para cambiar y recalcular):", color = C.faint, fontSize = 12.sp)
                            Gap(6.dp)
                            DishChips(state.dishes, dishId) { id ->
                                if (id == dishId) return@DishChips
                                if (CaptureRules.adjusted(a.items, items.toList(), added.toList())) pendingDish = id to true
                                else { chooseDish(id); runAnalysis() }
                            }
                        }
                        Gap(14.dp)
                        analysis?.let { a ->
                            val mood = when {
                                a.items.isEmpty() -> Mood.GRUMPY
                                leftAfter < -targets.kcal * targets.band -> Mood.GRUMPY
                                total.protein >= 25 -> Mood.HAPPY
                                else -> Mood.CALM
                            }
                            Row(verticalAlignment = Alignment.Top) {
                                Bolon(mood, 64.dp)
                                HGap(10.dp)
                                SpeechBubble(a.verdict.ifBlank { "Hmpf. Esto es lo que veo." }, Modifier.weight(1f))
                            }
                            if (a.question.isNotBlank()) {
                                Gap(12.dp)
                                Column(
                                    Modifier.fillMaxWidth().clip(Shape).border(1.dp, C.line, Shape).background(C.card).padding(14.dp),
                                ) {
                                    Text("Una pregunta para afinar", color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Gap(4.dp)
                                    Text(a.question, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.text)
                                    Gap(8.dp)
                                    Field(answer, { answer = it }, "Tu respuesta")
                                    Gap(8.dp)
                                    PrimaryButton("Corregir con IA", enabled = answer.isNotBlank()) {
                                        clarifications = CaptureRules.clarify(clarifications, a.question, answer)
                                        runAnalysis()
                                    }
                                }
                            }
                            Gap(16.dp)
                        } ?: run {
                            if (source == "libreta") Text("Estimé con la libreta (sin IA). Ajusta las porciones si hace falta.", color = C.muted, fontSize = 13.sp)
                            Gap(8.dp)
                        }
                        Label("Lo que comiste")
                        Gap(8.dp)
                    }
                    items(items.size) { i ->
                        items.getOrNull(i)?.let { f ->
                            val base = bases.getOrElse(i) { f.grams }
                            // The size line describes the AI's portion: once the grams change it no longer applies.
                            val size = sizes.getOrNull(i)?.takeIf { f.grams == base }?.let { m ->
                                "${CaptureRules.rulerLabel(ruler)}: ${cm(m.lengthCm)} × ${cm(m.widthCm)} × ${cm(m.heightCm)} cm"
                            }
                            ItemCard(f, base, size, onChange = { items[i] = it }, onRemove = { removeAt(i) })
                        }
                        Gap(8.dp)
                    }
                    item {
                        // Add what the photo missed: words go through the AI too.
                        if (ai.available) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HintField(missing, { missing = it }, "¿Falta algo?", "Ej: una cola, salsa de ají", Modifier.weight(1f))
                                HGap(8.dp)
                                QuietButton(if (addingMissing) "…" else "Añadir") { addMissing() }
                            }
                        }
                        QuietButton("+ Buscar en la libreta", color = C.muted) { openSearch(missing) }
                        Gap(10.dp)

                        val dayAfter = state.totalsOn(LocalDate.now()).kcal + total.kcal
                        Panel(padding = 14.dp) {
                            Text(
                                if (leftAfter >= 0) "Después de esto te quedan ${leftAfter.fmt()} kcal" else "Con esto pasas tu meta por ${(-leftAfter).fmt()} kcal",
                                fontWeight = FontWeight.Medium, color = if (leftAfter < -targets.kcal * targets.band) C.over else C.text,
                            )
                            Gap(8.dp)
                            ProgressLine((dayAfter / targets.kcal).toFloat(), if (leftAfter < -targets.kcal * targets.band) C.over else C.accent)
                            Gap(6.dp)
                            Text(
                                "Proteína del día: ${(state.totalsOn(LocalDate.now()).protein + total.protein).fmt()} de ${targets.protein} g",
                                color = C.muted, fontSize = 12.sp,
                            )
                        }

                        analysis?.better?.takeIf { it.isNotBlank() }?.let {
                            Gap(10.dp)
                            Panel(padding = 14.dp) {
                                Text("💡 Para la próxima", color = C.gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Gap(4.dp)
                                Text(it, fontSize = 14.sp)
                            }
                        }

                        val p = state.profile
                        if (!Fast.inWindow(p, at)) {
                            Gap(10.dp)
                            Text(
                                "🌙 Esto cae fuera de tu ventana de ayuno (${Fast.opens(p)}–${Fast.closes(p)}). No pasa nada; solo para que lo tengas en cuenta.",
                                color = C.water, fontSize = 13.sp,
                            )
                        }

                        Gap(14.dp)
                        // Details are folded: the defaults are right most of the time.
                        Column(Modifier.fillMaxWidth().clip(Shape).background(C.card).animateContentSize()) {
                            Row(
                                Modifier.fillMaxWidth().clickable { details = !details }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Detalles", fontWeight = FontWeight.Medium)
                                    Text(
                                        "${slot.emoji} ${slot.label} · %02d:%02d · hambre ${if (hunger <= 3f) "poca" else if (hunger >= 8f) "mucha" else "normal"}".format(at.hour, at.minute),
                                        color = C.muted, fontSize = 12.sp,
                                    )
                                }
                                Text(if (details) "▲" else "▼", color = C.muted)
                            }
                            if (details) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Slot.entries.forEach { sl -> Chip("${sl.emoji} ${sl.label}", slot == sl) { slot = sl } }
                                }
                                Gap(10.dp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Hora", color = C.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Stepper(
                                        onMinus = { at = if (at.minute % 30 != 0) at.withMinute(at.minute / 30 * 30) else at.minusMinutes(30); slot = Slot.forHour(at.hour) },
                                        onPlus = { at = if (at.minute % 30 != 0) at.withMinute(at.minute / 30 * 30).plusMinutes(30) else at.plusMinutes(30); slot = Slot.forHour(at.hour) },
                                    ) { Text("%02d:%02d".format(at.hour, at.minute), Modifier.padding(horizontal = 10.dp), fontWeight = FontWeight.SemiBold) }
                                }
                                Gap(12.dp)
                                Text("¿Cuánta hambre tenías?", color = C.muted, fontSize = 13.sp)
                                Gap(8.dp)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(3f to "Poca", 6f to "Normal", 8f to "Mucha").forEach { (v, l) -> Chip(l, hunger == v) { hunger = v; if (v > 3f) reason = null } }
                                    Chip("📵 Sin pantallas", mindful) { mindful = !mindful }
                                }
                                // Only when hunger was low is it worth asking why: that is where emotional eating hides.
                                if (hunger <= 3f) {
                                    Gap(12.dp)
                                    Text("Entonces, ¿por qué comes?", color = C.muted, fontSize = 13.sp)
                                    Gap(8.dp)
                                    FlowChips(Reason.entries.filterNot { it.physical }.map { it.label }, reason?.label) { l ->
                                        reason = Reason.entries.first { it.label == l }.takeIf { it != reason }
                                    }
                                }
                                if (emotional && !nudged) {
                                    Gap(12.dp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Bolon(Mood.CALM, 48.dp)
                                        HGap(10.dp)
                                        Text("Suena a hambre de cabeza, no de panza. No te lo prohíbo: ¿probamos 3 minutos antes?", fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        QuietButton("Igual voy a comer", color = C.muted) { nudged = true }
                                        QuietButton("3 minutos") { onSos() }
                                    }
                                }
                            }
                        }
                        Gap(20.dp)
                    }
                }
            }
        }

        if (step != Step.THINKING) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                if (step == Step.COMPOSE) {
                    val canGo = photo != null || side != null || description.isNotBlank()
                    if (ai.available) {
                        PrimaryButton(if (error != null) "Reintentar" else "Analizar", enabled = canGo) { runAnalysis() }
                        // The notebook is only a lifeboat for when the AI can't be reached.
                        if (error != null && description.isNotBlank()) {
                            QuietButton("Estimar con mi libreta mientras tanto", Modifier.align(Alignment.CenterHorizontally), color = C.muted) {
                                quick(FoodDb.guess(description), "libreta"); if (items.isEmpty()) openSearch(description)
                            }
                        }
                    } else if (description.isNotBlank()) {
                        QuietButton("Estimar con mi libreta (menos preciso)", Modifier.align(Alignment.CenterHorizontally), color = C.muted) {
                            quick(FoodDb.guess(description), "libreta"); if (items.isEmpty()) openSearch(description)
                        }
                    }
                } else {
                    PrimaryButton(if (items.isEmpty()) "Añade al menos un alimento" else "Guardar · ${total.kcal.fmt()} kcal", enabled = items.isNotEmpty()) { save() }
                    QuietButton("Volver", Modifier.align(Alignment.CenterHorizontally), color = C.muted) { step = Step.COMPOSE }
                }
            }
        }
    }

    if (searching) FoodSearch(initial = searchQuery, onPick = { addItem(it); searching = false }, onDismiss = { searching = false })

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        containerColor = C.card,
        title = { Text("¿Descartar esta comida?") },
        text = { Text("Se borran las fotos y lo que escribiste.", color = C.muted) },
        confirmButton = {
            QuietButton("Descartar", color = C.text) {
                confirmDiscard = false
                job?.cancel()
                Images.clearCamera(context)
                onDone()
            }
        },
        dismissButton = { QuietButton("Seguir aquí", onClick = { confirmDiscard = false }) },
    )

    pendingDish?.let { (id, _) ->
        AlertDialog(
            onDismissRequest = { pendingDish = null },
            containerColor = C.card,
            title = { Text("¿Recalcular?") },
            text = { Text("Se pierden tus ajustes. Lo que añadiste tú se queda.", color = C.muted) },
            confirmButton = { QuietButton("Recalcular") { pendingDish = null; chooseDish(id); runAnalysis() } },
            dismissButton = { QuietButton("Cancelar", color = C.muted, onClick = { pendingDish = null }) },
        )
    }
}

/** A text field with a short label and the example as a placeholder, so the label stays readable. */
@Composable
private fun HintField(value: String, onChange: (String) -> Unit, label: String, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text(placeholder, color = C.faint) },
        singleLine = singleLine, shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.accent, unfocusedBorderColor = C.line, focusedLabelColor = C.accent, cursorColor = C.accent,
            focusedContainerColor = C.card, unfocusedContainerColor = C.card,
        ),
    )
}

/** Three dots that fill as the user moves from photo to analysis to saving. */
@Composable
private fun StepDots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            Box(Modifier.size(width = if (i == current) 18.dp else 6.dp, height = 6.dp).clip(CircleShape).background(if (i <= current) C.accent else C.line))
        }
    }
}

/** The photo being "scanned" while the AI works, with Don Bolón thinking and what he is doing. */
@Composable
private fun Thinking(photo: Bitmap?, onCancel: () -> Unit) {
    var line by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1600); line = (line + 1) % thinkingLines.size } }
    val scan by rememberInfiniteTransition(label = "scan").animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (photo != null) {
            Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(26.dp))) {
                Image(photo.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height * scan
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, C.accent.copy(alpha = 0.45f), Color.Transparent), y - 60f, y + 60f), Offset(0f, y - 60f), androidx.compose.ui.geometry.Size(size.width, 120f))
                    drawLine(C.accent, Offset(0f, y), Offset(size.width, y), 3f)
                }
                Bolon(Mood.THINKING, 70.dp, Modifier.align(Alignment.BottomEnd).padding(6.dp), talking = true)
            }
        } else {
            Bolon(Mood.THINKING, 160.dp, talking = true)
        }
        Gap(22.dp)
        AnimatedContent(line, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "line") { i ->
            Text(thinkingLines[i], style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Gap(6.dp)
        Text("Suele tardar unos segundos", color = C.muted, fontSize = 13.sp)
        Gap(18.dp)
        QuietButton("Cancelar", color = C.muted, onClick = onCancel)
    }
}

/** The meal at a glance: photo, total, confidence and how the calories split between macros. */
@Composable
private fun Summary(photo: Bitmap?, kcal: Double, protein: Double, carbs: Double, fat: Double, confidence: String?) {
    Panel(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) {
                Image(photo.asImageBitmap(), null, Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                HGap(14.dp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(kcal.fmt(), style = MaterialTheme.typography.displaySmall)
                    Text(" kcal", color = C.muted, modifier = Modifier.padding(bottom = 6.dp))
                }
                confidence?.let {
                    val high = it.lowercase() == "alta"
                    val low = it.lowercase() == "baja"
                    Text(
                        "${if (low) "⚠ " else ""}Confianza $it", color = if (high) C.good else C.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(if (high) C.good.copy(alpha = 0.14f) else C.raised).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Gap(14.dp)
        // Share of calories from each macro, as one bar.
        val pk = protein * 4; val ck = carbs * 4; val fk = fat * 9
        val sum = (pk + ck + fk).coerceAtLeast(1.0)
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(C.raised)) {
            if (pk > 0) Box(Modifier.weight((pk / sum).toFloat()).fillMaxHeight().background(C.protein))
            if (ck > 0) Box(Modifier.weight((ck / sum).toFloat()).fillMaxHeight().background(C.carbs))
            if (fk > 0) Box(Modifier.weight((fk / sum).toFloat()).fillMaxHeight().background(C.fat))
        }
        Gap(10.dp)
        Row {
            MacroLegend("Proteína", protein, C.protein)
            MacroLegend("Carbos", carbs, C.carbs)
            MacroLegend("Grasa", fat, C.fat)
        }
    }
}

@Composable
private fun RowScope.MacroLegend(name: String, grams: Double, color: Color) {
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Dot(color, 8.dp); HGap(6.dp); Text("${grams.fmt()} g", fontWeight = FontWeight.SemiBold) }
        Text(name, color = C.muted, fontSize = 11.sp)
    }
}

@Composable
private fun ProgressLine(frac: Float, color: Color, height: Dp = 8.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(C.raised)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/** One food: tap the name to fix it, slide to change the portion (¼× to 2½× of the estimate). */
@Composable
private fun ItemCard(f: FoodItem, base: Double, size: String?, onChange: (FoodItem) -> Unit, onRemove: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val k = if (base > 0) (f.grams / base).toFloat() else 1f
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { editing = true }) {
                Text(f.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("P ${f.protein.fmt()} · C ${f.carbs.fmt()} · G ${f.fat.fmt()}  ·  editar", color = C.muted, fontSize = 12.sp)
                size?.let { Text("📐 $it", color = C.faint, fontSize = 11.sp) }
            }
            Text("${f.kcal.fmt()} kcal", fontWeight = FontWeight.SemiBold)
            IconButton(onRemove, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Quitar", tint = C.faint, modifier = Modifier.size(16.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                k.coerceIn(0.25f, 2.5f), { onChange(f.withGrams((base * it / 0.05).roundToInt() * 0.05).let { x -> x.withGrams((x.grams).roundToInt().toDouble().coerceAtLeast(1.0)) }) },
                Modifier.weight(1f), valueRange = 0.25f..2.5f,
                colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.raised),
            )
            HGap(10.dp)
            Text("${f.grams.fmt()} g", Modifier.width(58.dp), color = C.muted, fontSize = 13.sp, textAlign = TextAlign.End)
        }
    }
    if (editing) EditItem(f, onSave = { onChange(it); editing = false }, onDismiss = { editing = false })
}

@Composable
private fun EditItem(f: FoodItem, onSave: (FoodItem) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(f.name) }
    var grams by remember { mutableStateOf(f.grams.fmt()) }
    var kcal by remember { mutableStateOf(f.kcal.fmt()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        title = { Text("Corregir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Nombre")
                Field(grams, { grams = it }, "Gramos", number = true)
                Field(kcal, { kcal = it }, "Kcal (si sabes el valor de la etiqueta)", number = true)
                Text("Cambiar los gramos ajusta todo en proporción. Don Bolón recuerda tus correcciones.", color = C.faint, fontSize = 12.sp)
            }
        },
        confirmButton = {
            QuietButton("Listo") {
                val g = grams.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: f.grams
                var out = f.withGrams(g).copy(name = name.ifBlank { f.name })
                val k = kcal.replace(',', '.').toDoubleOrNull()
                if (k != null && kotlin.math.abs(k - f.kcal) > 1 && out.kcal > 0) {
                    val r = k / out.kcal
                    out = out.copy(kcal = k, protein = out.protein * r, carbs = out.carbs * r, fat = out.fat * r)
                }
                onSave(out)
            }
        },
        dismissButton = { QuietButton("Cancelar", color = C.muted, onClick = onDismiss) },
    )
}

@Composable
fun FoodSearch(onPick: (FoodItem) -> Unit, onDismiss: () -> Unit, initial: String = "") {
    var q by remember { mutableStateOf(initial) }
    var customKcal by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        title = { Text("Añadir alimento") },
        text = {
            Column {
                Field(q, { q = it }, "Buscar (ej. bolón, pollo, arroz)")
                Gap(8.dp)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(vertical = 4.dp)) {
                    items(FoodDb.search(q)) { r ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(r.item()) }.padding(vertical = 9.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontSize = 14.sp)
                                Text(r.portion, color = C.faint, fontSize = 12.sp)
                            }
                            Text("${(r.kcal * r.portionG / 100).fmt()} kcal", color = C.muted, fontSize = 13.sp)
                        }
                    }
                }
                if (q.isNotBlank()) {
                    Gap(6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(customKcal, { customKcal = it }, "Kcal de \"$q\"", Modifier.weight(1f), number = true)
                        QuietButton("Añadir") {
                            customKcal.replace(',', '.').toDoubleOrNull()?.let { onPick(FoodItem(q, 100.0, it, 0.0, 0.0, 0.0)) }
                        }
                    }
                }
            }
        },
        confirmButton = { QuietButton("Cerrar", color = C.muted, onClick = onDismiss) },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun FlowChips(options: List<String>, selected: String?, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> Chip(o, o == selected) { onClick(o) } }
    }
}

/** The person's own dishes with their real size; "Otro" means no known scale. */
@Composable
private fun DishChips(dishes: List<ec.donbolon.app.data.Dish>, selected: String?, onPick: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dishes.forEach { d -> Chip("${d.name} · ${d.size.toInt()} ${d.kind.unit}", selected == d.id) { onPick(d.id) } }
        Chip("Otro", selected == null) { onPick(null) }
    }
}

/** "9", "3,5": half-centimeter precision, which is all a photo can honestly give, written like [fmt]. */
private fun cm(x: Double): String = ((x * 2).roundToInt() / 2.0).fmt(1).removeSuffix(",0").removeSuffix(".0")
