package ec.donbolon.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
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
import androidx.compose.ui.graphics.StrokeCap
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
import ec.donbolon.app.ai.AiException
import ec.donbolon.app.ai.Analysis
import ec.donbolon.app.data.Fast
import ec.donbolon.app.data.FoodDb
import ec.donbolon.app.data.FoodItem
import ec.donbolon.app.data.Meal
import ec.donbolon.app.data.Memory
import ec.donbolon.app.data.Mood
import ec.donbolon.app.data.Reason
import ec.donbolon.app.data.Science
import ec.donbolon.app.data.Slot
import ec.donbolon.app.data.totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var description by rememberSaveable { mutableStateOf("") }
    var cooking by rememberSaveable { mutableStateOf<String?>(null) }
    var slot by rememberSaveable { mutableStateOf(Slot.forHour(LocalTime.now().hour)) }
    var at by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var shots by remember { mutableIntStateOf(0) }
    var hunger by rememberSaveable { mutableStateOf(6f) }
    var reason by rememberSaveable { mutableStateOf<Reason?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    var answer by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf("") }
    var addingMissing by remember { mutableStateOf(false) }
    var nudged by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var mindful by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("foto") }
    var dishId by rememberSaveable { mutableStateOf(state.settings.lastDish) }
    fun chooseDish(id: String?) {
        dishId = id
        model.store.edit { it.copy(settings = it.settings.copy(lastDish = id)) }
    }
    val items = remember { mutableStateListOf<FoodItem>() }
    // The grams each item had when it arrived, so the portion slider scales from there.
    val bases = remember { mutableStateListOf<Double>() }

    fun setItems(list: List<FoodItem>) { items.clear(); items.addAll(list); bases.clear(); bases.addAll(list.map { it.grams }) }
    fun addItem(f: FoodItem) { items.add(f); bases.add(f.grams) }
    fun removeAt(i: Int) { items.removeAt(i); bases.removeAt(i) }

    val camFile = remember { File(context.cacheDir, "camera").apply { mkdirs() }.resolve("plato.jpg") }
    val camUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", camFile) }
    fun accept(uri: Uri?) {
        External.back()
        if (uri != null) scope.launch {
            photo = withContext(Dispatchers.IO) { Images.load(context, uri) }
            if (photo != null) shots++ else error = "No pude abrir esa foto. Prueba otra."
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> accept(if (ok) camUri else null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { accept(it) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        External.back()
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !said.isNullOrBlank()) description = listOf(description, said).filter { it.isNotBlank() }.joinToString(", ")
    }
    fun shoot() = runCatching { External.open = true; camera.launch(camUri) }.onFailure { External.open = false; error = "No encontré una app de cámara." }
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

    // With the AI connected, open the camera right away: one tap from home to a photo.
    LaunchedEffect(Unit) { if (ai.available) shoot() }

    fun aiContext() = Science.context(state, targets) + "\n" + Memory.forAnalysis(state)

    fun runAnalysis(previous: Analysis? = null) {
        error = null
        step = Step.THINKING
        scope.launch {
            try {
                val served = state.dishes.firstOrNull { it.id == dishId }?.describe()
                val result = withContext(Dispatchers.IO) { ai.analyze(photo, description, cooking, aiContext(), previous, answer, served) }
                analysis = result
                setItems(result.items)
                source = if (photo != null) "foto" else "texto"
                answer = ""
                step = Step.REVIEW
            } catch (e: AiException) {
                error = e.message; step = Step.COMPOSE
            } catch (e: Exception) {
                error = "Algo falló: ${e.message ?: e.javaClass.simpleName}"; step = Step.COMPOSE
            }
        }
    }

    // Photo first: as soon as a picture arrives, the analysis starts.
    LaunchedEffect(shots) { if (shots > 0 && ai.available && step == Step.COMPOSE) runAnalysis() }

    fun quick(list: List<FoodItem>, from: String) {
        analysis = null
        setItems(list)
        source = from
        step = Step.REVIEW
    }

    fun addMissing() {
        if (missing.isBlank()) return
        addingMissing = true
        scope.launch {
            val found = runCatching { withContext(Dispatchers.IO) { ai.analyze(null, missing, null, aiContext()) }.items }
                .getOrElse { FoodDb.guess(missing) }
            found.forEach { addItem(it) }
            if (found.isEmpty()) searching = true
            missing = ""
            addingMissing = false
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
            val fixes = Memory.corrections(a.items, items.toList())
            if (fixes.isNotEmpty()) model.store.edit { it.copy(corrections = it.corrections + fixes) }
        }
        onDone()
    }

    val emotional = reason?.physical == false && hunger <= 4f
    val total = items.totals()
    val leftAfter = targets.kcal - state.totalsOn(LocalDate.now()).kcal - total.kcal

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onDone) { Icon(Icons.Filled.Close, "Cerrar", tint = C.muted) }
            Text(
                when (step) { Step.COMPOSE -> "Registrar comida"; Step.THINKING -> "Analizando"; Step.REVIEW -> "Revisa y guarda" },
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            StepDots(step.ordinal)
        }

        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "step") { s ->
            when (s) {
                Step.THINKING -> Thinking(photo)

                Step.COMPOSE -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    if (!ai.available) {
                        ConnectAi(model) { ai = model.ai() }
                        Gap(16.dp)
                    }
                    Viewfinder(photo, onCamera = { shoot() }, onGallery = { pick() })
                    Gap(18.dp)

                    Label("O cuéntamelo")
                    Gap(8.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(description, { description = it }, "Ej: bolón de queso, 2 huevos, café con leche", Modifier.weight(1f), singleLine = false)
                        HGap(8.dp)
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(C.raised).clickable { listen() },
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

                    val usuals = remember(state.meals.size) { Memory.usuals(state).take(6) }
                    val recent = remember(state.meals.size) {
                        state.meals.sortedByDescending { it.time }.distinctBy { m -> m.items.joinToString { it.name } }.take(3)
                    }
                    if (usuals.isNotEmpty() || recent.isNotEmpty()) {
                        Gap(22.dp)
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

                    error?.let {
                        Gap(14.dp)
                        Text(
                            "⚠ $it", color = C.over, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.over.copy(alpha = 0.12f)).padding(12.dp),
                        )
                    }
                    Gap(24.dp)
                }

                Step.REVIEW -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    item {
                        Summary(photo, total.kcal, total.protein, total.carbs, total.fat, analysis?.confidence)
                        if (analysis != null) {
                            // Changing the dish here re-runs the estimate with the new scale.
                            Gap(10.dp)
                            Text("Servido en (toca para cambiar y recalcular):", color = C.faint, fontSize = 12.sp)
                            Gap(6.dp)
                            DishChips(state.dishes, dishId) { id -> chooseDish(id); runAnalysis() }
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
                                    Modifier.fillMaxWidth().clip(Shape).border(1.dp, C.gold.copy(alpha = 0.6f), Shape).background(C.card).padding(14.dp),
                                ) {
                                    Text("Una pregunta para afinar", color = C.gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Gap(4.dp)
                                    Text(a.question, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Gap(8.dp)
                                    Field(answer, { answer = it }, "Tu respuesta")
                                    Gap(8.dp)
                                    PrimaryButton("Corregir con IA", enabled = answer.isNotBlank()) { runAnalysis(a) }
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
                    items(items.size, key = { "$it-${items.getOrNull(it)?.name}" }) { i ->
                        items.getOrNull(i)?.let { f ->
                            ItemCard(f, bases.getOrElse(i) { f.grams }, onChange = { items[i] = it }, onRemove = { removeAt(i) })
                        }
                        Gap(8.dp)
                    }
                    item {
                        // Add what the photo missed: words go through the AI too.
                        if (ai.available) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Field(missing, { missing = it }, "¿Falta algo? Ej: una cola, salsa de ají", Modifier.weight(1f))
                                HGap(8.dp)
                                QuietButton(if (addingMissing) "…" else "Añadir") { addMissing() }
                            }
                        }
                        QuietButton("+ Buscar en la libreta", color = C.muted) { searching = true }
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
                    val canGo = photo != null || description.isNotBlank()
                    if (ai.available) {
                        PrimaryButton(if (error != null) "Reintentar con IA" else if (photo != null) "Analizar foto con IA" else "Analizar con IA", enabled = canGo) { runAnalysis() }
                        // The notebook is only a lifeboat for when the AI can't be reached.
                        if (error != null && description.isNotBlank()) {
                            QuietButton("Estimar con mi libreta mientras tanto", Modifier.align(Alignment.CenterHorizontally), color = C.muted) {
                                quick(FoodDb.guess(description), "libreta"); if (items.isEmpty()) searching = true
                            }
                        }
                    } else if (description.isNotBlank()) {
                        QuietButton("Estimar con mi libreta (menos preciso)", Modifier.align(Alignment.CenterHorizontally), color = C.muted) {
                            quick(FoodDb.guess(description), "libreta"); if (items.isEmpty()) searching = true
                        }
                    }
                } else {
                    PrimaryButton(if (items.isEmpty()) "Añade al menos un alimento" else "Guardar · ${total.kcal.fmt()} kcal", enabled = items.isNotEmpty()) { save() }
                    QuietButton("Volver", Modifier.align(Alignment.CenterHorizontally), color = C.muted) { step = Step.COMPOSE }
                }
            }
        }
    }

    if (searching) FoodSearch(onPick = { addItem(it); searching = false }, onDismiss = { searching = false })
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

/** A camera-style frame: tap anywhere to shoot, or pick from the gallery. */
@Composable
private fun Viewfinder(photo: Bitmap?, onCamera: () -> Unit, onGallery: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(C.card).clickable(onClick = onCamera),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo.asImageBitmap(), "Tu plato", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Bolon(Mood.CALM, 96.dp)
                Gap(10.dp)
                Text("Tómale foto a tu plato", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("De arriba y con todo a la vista", color = C.muted, fontSize = 13.sp)
            }
        }
        // Viewfinder corners.
        Canvas(Modifier.fillMaxSize().padding(18.dp)) {
            val l = 28.dp.toPx()
            val w = 3.dp.toPx()
            val c = C.accent.copy(alpha = 0.9f)
            listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f),
                Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f)).forEach { (o, d) ->
                drawLine(c, o, Offset(o.x + d.x * l, o.y), w, StrokeCap.Round)
                drawLine(c, o, Offset(o.x, o.y + d.y * l), w, StrokeCap.Round)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(if (photo == null) "📷 Cámara" else "📷 Otra foto", true, onClick = onCamera)
            Chip("🖼️ Galería", false, onClick = onGallery)
        }
    }
}

/** The photo being "scanned" while the AI works, with Don Bolón thinking and what he is doing. */
@Composable
private fun Thinking(photo: Bitmap?) {
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
                    val c = when (it.lowercase()) { "alta" -> C.good; "baja" -> C.over; else -> C.gold }
                    Text(
                        "Confianza $it", color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 3.dp),
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
private fun ItemCard(f: FoodItem, base: Double, onChange: (FoodItem) -> Unit, onRemove: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val k = if (base > 0) (f.grams / base).toFloat() else 1f
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { editing = true }) {
                Text(f.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("P ${f.protein.fmt()} · C ${f.carbs.fmt()} · G ${f.fat.fmt()}  ·  editar", color = C.muted, fontSize = 12.sp)
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
fun FoodSearch(onPick: (FoodItem) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
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
