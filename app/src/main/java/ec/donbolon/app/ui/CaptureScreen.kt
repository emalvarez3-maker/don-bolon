package ec.donbolon.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ec.donbolon.app.AppModel
import ec.donbolon.app.ai.AiException
import ec.donbolon.app.ai.Analysis
import ec.donbolon.app.data.Mood
import ec.donbolon.app.data.FoodDb
import ec.donbolon.app.data.FoodItem
import ec.donbolon.app.data.Meal
import ec.donbolon.app.data.Reason
import ec.donbolon.app.data.Science
import ec.donbolon.app.data.Slot
import ec.donbolon.app.data.totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private val cookings = listOf("A la plancha", "Frito", "Airfryer", "Al horno", "Hervido / sopa", "Crudo")

private enum class Step { COMPOSE, THINKING, REVIEW }

/**
 * Photo → (optional words) → AI estimate → the user corrects anything → save.
 * The pre-meal hunger check is here on purpose: noticing *why* you eat is the habit.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun CaptureScreen(model: AppModel, onDone: () -> Unit, onSos: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by model.store.state.collectAsState()
    val ai = remember { model.ai() }

    var step by remember { mutableStateOf(Step.COMPOSE) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var cooking by rememberSaveable { mutableStateOf<String?>(null) }
    var slot by rememberSaveable { mutableStateOf(Slot.forHour(LocalTime.now().hour)) }
    var at by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var shots by remember { mutableStateOf(0) }
    var hunger by rememberSaveable { mutableStateOf(6f) }
    var reason by rememberSaveable { mutableStateOf<Reason?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    var answer by remember { mutableStateOf("") }
    var nudged by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var mindful by remember { mutableStateOf(false) }
    val items = remember { mutableStateListOf<FoodItem>() }

    val camFile = remember { File(context.cacheDir, "camera").apply { mkdirs() }.resolve("plato.jpg") }
    val camUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", camFile) }
    fun accept(uri: Uri?) {
        External.open = false
        if (uri != null) scope.launch {
            photo = withContext(Dispatchers.IO) { Images.load(context, uri) }
            if (photo != null) shots++
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> accept(if (ok) camUri else null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { accept(it) }
    fun shoot() { External.open = true; camera.launch(camUri) }
    fun pick() { External.open = true; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // Open the camera right away: one tap from home to a photo.
    LaunchedEffect(Unit) { if (ai.available) shoot() }

    fun runAnalysis(previous: Analysis? = null) {
        error = null
        step = Step.THINKING
        scope.launch {
            try {
                val context = Science.context(state, model.targets())
                val result = withContext(Dispatchers.IO) { ai.analyze(photo, description, cooking, context, previous, answer) }
                analysis = result
                items.clear(); items.addAll(result.items)
                answer = ""
                step = Step.REVIEW
            } catch (e: AiException) {
                error = e.message; step = Step.COMPOSE
            } catch (e: Exception) {
                error = "Algo falló: ${e.message ?: e.javaClass.simpleName}"; step = Step.COMPOSE
            }
        }
    }

    // Photo first: as soon as a picture arrives, the analysis starts. The AI asks for words only if it needs them.
    LaunchedEffect(shots) { if (shots > 0 && ai.available && step == Step.COMPOSE) runAnalysis() }

    fun offline() {
        items.clear(); items.addAll(FoodDb.guess(description))
        analysis = null
        step = Step.REVIEW
        if (items.isEmpty()) searching = true
    }

    fun save() {
        val id = UUID.randomUUID().toString()
        val photoName = photo?.let { b -> "$id.jpg".also { Images.saveThumb(b, File(model.store.photos, it)) } }
        model.store.addMeal(
            Meal(
                id = id, time = LocalDate.now().atTime(at).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), slot = slot, items = items.toList(), photo = photoName,
                note = description, hunger = hunger.toInt(), reason = reason, mindful = mindful,
                source = if (analysis == null) "libreta" else if (photo != null) "foto" else "texto",
            )
        )
        onDone()
    }

    val emotional = reason?.physical == false && hunger <= 4f

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onDone) { Icon(Icons.Filled.Close, "Cerrar", tint = C.muted) }
            Text(if (step == Step.REVIEW) "Revisa y guarda" else "Registrar comida", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }

        when (step) {
            Step.THINKING -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Bolon(Mood.THINKING, 170.dp, talking = true)
                Gap(18.dp)
                Text("Hmm… déjame ver ese plato.", style = MaterialTheme.typography.titleMedium)
                Gap(4.dp)
                Text("Separando ingredientes y estimando porciones", color = C.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Step.COMPOSE -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                PhotoBox(photo, onCamera = ::shoot, onGallery = ::pick)
                Gap(14.dp)
                Field(description, { description = it }, "¿Qué es? (opcional, mejora la precisión)", singleLine = false, minLines = 2)
                Text(
                    "Ej: \"bolón de queso frito, 2 huevos revueltos, café con leche sin azúcar\"",
                    color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
                Gap(16.dp)
                Label("Cocción")
                Gap(8.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cookings.forEach { c -> Chip(c, cooking == c) { cooking = if (cooking == c) null else c } }
                }
                error?.let {
                    Gap(12.dp)
                    Text(it, color = C.over, fontSize = 13.sp)
                }
                Gap(20.dp)
            }

            Step.REVIEW -> LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                item {
                    analysis?.let { a ->
                        Row(verticalAlignment = Alignment.Top) {
                            Bolon(if (a.items.isEmpty()) Mood.ANGRY else Mood.EATING, 64.dp)
                            HGap(10.dp)
                            Column(Modifier.weight(1f)) {
                                SpeechBubble(a.verdict.ifBlank { "Hmpf. Esto es lo que veo." })
                                Gap(4.dp)
                                Text("Confianza ${a.confidence}", color = C.faint, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        if (a.question.isNotBlank()) {
                            Gap(12.dp)
                            Panel(padding = 14.dp) {
                                Text(a.question, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Gap(8.dp)
                                Field(answer, { answer = it }, "Tu respuesta")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    QuietButton("Corregir estimación") { if (answer.isNotBlank()) runAnalysis(a) }
                                }
                            }
                        }
                        Gap(14.dp)
                    } ?: run {
                        Text("Sin IA: estimé con la libreta. Ajusta las porciones o añade lo que falte.", color = C.muted, fontSize = 13.sp)
                        Gap(12.dp)
                    }
                    Label("Lo que comiste")
                    Gap(8.dp)
                }
                items(items.indices.toList(), key = { "$it-${items.getOrNull(it)?.name}" }) { i ->
                    items.getOrNull(i)?.let { f -> ItemRow(f, onChange = { items[i] = it }, onRemove = { items.removeAt(i) }) }
                    Gap(8.dp)
                }
                item {
                    QuietButton("+ Añadir alimento") { searching = true }
                    Gap(8.dp)
                    val t = items.totals()
                    val left = model.targets().kcal - state.totalsOn(LocalDate.now()).kcal - t.kcal
                    Panel {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(t.kcal.fmt(), style = MaterialTheme.typography.headlineMedium)
                            Text("  kcal", color = C.muted, modifier = Modifier.padding(bottom = 4.dp))
                            Box(Modifier.weight(1f))
                            Text(
                                if (left >= 0) "te quedarán ${left.fmt()}" else "${(-left).fmt()} sobre la meta",
                                color = if (left >= 0) C.muted else C.over, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Gap(8.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Macro("Proteína", t.protein, C.protein); Macro("Carbos", t.carbs, C.carbs); Macro("Grasa", t.fat, C.fat); Macro("Fibra", t.fiber, C.fiber)
                        }
                    }
                    analysis?.better?.takeIf { it.isNotBlank() }?.let {
                        Gap(10.dp)
                        Panel(padding = 14.dp) {
                            Label("Para la próxima")
                            Gap(4.dp)
                            Text(it, fontSize = 14.sp)
                        }
                    }
                    Gap(18.dp)
                    Label("Momento")
                    Gap(8.dp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Slot.entries.forEach { s -> Chip("${s.emoji} ${s.label}", slot == s) { slot = s } }
                    }
                    Gap(10.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hora", color = C.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        // Steps land on :00 and :30 so earlier meals are quick to place.
                        Stepper(
                            onMinus = { at = if (at.minute % 30 != 0) at.withMinute(at.minute / 30 * 30) else at.minusMinutes(30); slot = Slot.forHour(at.hour) },
                            onPlus = { at = if (at.minute % 30 != 0) at.withMinute(at.minute / 30 * 30).plusMinutes(30) else at.plusMinutes(30); slot = Slot.forHour(at.hour) },
                        ) {
                            Text("%02d:%02d".format(at.hour, at.minute), Modifier.padding(horizontal = 10.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Gap(14.dp)
                    Label("¿Cuánta hambre tenías?")
                    Gap(8.dp)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3f to "Poca", 6f to "Normal", 8f to "Mucha").forEach { (v, l) -> Chip(l, hunger == v) { hunger = v; if (v > 3f) reason = null } }
                        Chip("📵 Sin pantallas", mindful) { mindful = !mindful }
                    }
                    // Only when hunger was low is it worth asking why: that is where emotional eating hides.
                    if (hunger <= 3f) {
                        Gap(12.dp)
                        Text("Entonces, ¿por qué comes?", color = C.muted, fontSize = 13.sp)
                        Gap(8.dp)
                        FlowChips(Reason.entries.filterNot { it.physical }.map { it.label }, reason?.label) { l -> reason = Reason.entries.first { it.label == l }.takeIf { it != reason } }
                    }
                    if (emotional && !nudged) {
                        Gap(12.dp)
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Bolon(Mood.GRUMPY, 54.dp)
                                HGap(10.dp)
                                Text("Hmpf. Eso suena a hambre de cabeza, no de panza. No te lo prohíbo: ¿probamos 3 minutos antes?", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                QuietButton("Igual voy a comer", color = C.muted) { nudged = true }
                                QuietButton("3 minutos") { onSos() }
                            }
                        }
                    }
                    Gap(16.dp)
                }
            }
        }

        if (step != Step.THINKING) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                if (step == Step.COMPOSE) {
                    val canGo = photo != null || description.isNotBlank()
                    if (ai.available) {
                        PrimaryButton(if (photo != null) "Analizar foto" else "Analizar descripción", enabled = canGo) { runAnalysis() }
                        if (description.isNotBlank()) QuietButton("Usar solo la libreta (sin IA)", Modifier.align(Alignment.CenterHorizontally), color = C.muted) { offline() }
                    } else {
                        PrimaryButton("Buscar en la libreta", enabled = true) { offline() }
                        Text("Configura una llave de IA en Perfil para analizar fotos.", color = C.faint, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                    }
                } else {
                    PrimaryButton("Guardar", enabled = items.isNotEmpty()) { save() }
                    QuietButton("Volver", Modifier.align(Alignment.CenterHorizontally), color = C.muted) { step = Step.COMPOSE }
                }
            }
        }
    }

    if (searching) FoodSearch(onPick = { items.add(it); searching = false }, onDismiss = { searching = false })
}

private fun hungerHint(h: Int) = when (h) {
    in 1..2 -> "Lleno o casi. Probablemente no es hambre física."
    in 3..4 -> "Poco. ¿Es hambre o es otra cosa?"
    in 5..6 -> "Hambre tranquila. Buen momento para comer."
    in 7..8 -> "Hambre clara. Come ya, sin esperar a más."
    else -> "Hambre fuerte: cuidado, aquí se come rápido y de más. Empieza con proteína y verduras."
}

@Composable
private fun PhotoBox(photo: Bitmap?, onCamera: () -> Unit, onGallery: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(22.dp)).background(C.card)
            .border(1.dp, C.line, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo.asImageBitmap(), "Tu plato", Modifier.fillMaxSize().clickable(onClick = onCamera), contentScale = ContentScale.Crop)
            Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Otra foto", false, onClick = onCamera)
                Chip("Galería", false, onClick = onGallery)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📷", fontSize = 40.sp)
                Gap(8.dp)
                Text("Foto de arriba, con todo el plato", color = C.muted, fontSize = 13.sp)
                Gap(14.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("Tomar foto", true, onClick = onCamera)
                    Chip("Galería", false, onClick = onGallery)
                }
            }
        }
    }
}

@Composable
private fun ItemRow(f: FoodItem, onChange: (FoodItem) -> Unit, onRemove: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.name, Modifier.weight(1f).clickable { editing = true }, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text("${f.kcal.fmt()} kcal", fontWeight = FontWeight.SemiBold)
            HGap(4.dp)
            IconButton(onRemove, Modifier.size(28.dp)) { Icon(Icons.Filled.Close, "Quitar", tint = C.faint, modifier = Modifier.size(16.dp)) }
        }
        Gap(6.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("P ${f.protein.fmt()} · C ${f.carbs.fmt()} · G ${f.fat.fmt()}", color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Stepper(
                onMinus = { onChange(f.withGrams((f.grams * 0.85).coerceAtLeast(5.0))) },
                onPlus = { onChange(f.withGrams(f.grams * 1.15)) },
            ) {
                Text("${f.grams.fmt()} g", Modifier.clickable { editing = true }.padding(horizontal = 6.dp), fontSize = 13.sp)
            }
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
                Text("Cambiar los gramos ajusta todo en proporción.", color = C.faint, fontSize = 12.sp)
            }
        },
        confirmButton = {
            QuietButton("Listo") {
                val g = grams.replace(',', '.').toDoubleOrNull() ?: f.grams
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
private fun androidx.compose.foundation.layout.RowScope.Macro(name: String, v: Double, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Dot(color, 6.dp); HGap(5.dp); Text("${v.fmt()} g", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        Text(name, color = C.faint, fontSize = 11.sp)
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun FlowChips(options: List<String>, selected: String?, onClick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> Chip(o, o == selected) { onClick(o) } }
    }
}
