package ec.donbolon.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.data.Mood
import ec.donbolon.app.data.IfThenPlan
import ec.donbolon.app.data.Science
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.UUID

private val planIdeas = listOf(
    "llego a casa con hambre" to "como un yogur o fruta antes de cocinar",
    "me da antojo de dulce en la noche" to "me lavo los dientes y tomo una infusión",
    "estoy aburrido y voy a la cocina" to "tomo un vaso de agua y salgo a caminar 5 minutos",
    "como fuera de casa" to "pido proteína a la plancha y mitad del arroz",
    "me sirvo" to "lleno medio plato de verduras primero",
    "veo series" to "como en plato, nunca de la bolsa",
)

@Composable
fun MindScreen(model: AppModel, onSos: () -> Unit) {
    val state by model.store.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var openTip by remember { mutableStateOf<Int?>(null) }
    val week = state.meals.filter { it.date >= LocalDate.now().minusDays(7) }
    val withReason = week.filter { it.reason != null }
    val physical = withReason.count { it.reason!!.physical }
    val topTrigger = withReason.filter { !it.reason!!.physical }.groupingBy { it.reason!! }.eachCount().maxByOrNull { it.value }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            Text("Mente", style = MaterialTheme.typography.headlineMedium)
            Text("Comer distinto empieza por darte cuenta.", color = C.muted, fontSize = 14.sp)
            Gap(18.dp)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.honey).clickable(onClick = onSos).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SOS antojo", color = C.night, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("3 minutos para dejar pasar la ola", color = C.night.copy(alpha = 0.75f), fontSize = 13.sp)
                }
                Text("→", color = C.night, fontSize = 24.sp)
            }
            Gap(12.dp)
            Panel {
                Label("Tu semana por dentro")
                Gap(10.dp)
                if (withReason.isEmpty()) {
                    Text("Cuando registres, marca por qué comes. Aquí verás tus patrones.", color = C.muted, fontSize = 14.sp)
                } else {
                    Row {
                        Stat("${physical * 100 / withReason.size}%", "por hambre real", Modifier.weight(1f))
                        Stat("${week.count { it.mindful }}", "comidas sin pantallas", Modifier.weight(1f))
                        Stat("${state.urgesSurfed}", "antojos surfeados", Modifier.weight(1f))
                    }
                    topTrigger?.let {
                        Gap(12.dp)
                        Text("Tu disparador más común: ${it.key.label.lowercase()} (${it.value}×). Hazle un plan Si-Entonces.", fontSize = 13.sp, color = C.muted)
                    }
                }
            }
            Gap(22.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Planes Si-Entonces", Modifier.weight(1f))
                QuietButton("+ Nuevo") { adding = true }
            }
            Text("Decidir antes duplica la probabilidad de cumplir (Gollwitzer, 2006).", color = C.faint, fontSize = 12.sp)
            Gap(8.dp)
            if (state.plans.isEmpty()) Panel(padding = 14.dp) {
                Text("Aún no tienes planes. Empieza por tu momento más difícil del día.", color = C.muted, fontSize = 14.sp)
            }
        }
        items(state.plans, key = { it.id }) { p ->
            Panel(padding = 14.dp) {
                Text(buildPlan(p), fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (p.hits == 0) "Aún sin cumplir" else "Cumplido ${p.hits}×", color = C.faint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    QuietButton("Borrar", color = C.faint) { model.store.edit { s -> s.copy(plans = s.plans.filterNot { it.id == p.id }) } }
                    QuietButton("Lo cumplí") { model.store.edit { s -> s.copy(plans = s.plans.map { if (it.id == p.id) it.copy(hits = it.hits + 1) else it }) } }
                }
            }
            Gap(8.dp)
        }
        item {
            Gap(18.dp)
            Label("La ciencia detrás")
            Gap(8.dp)
        }
        items(Science.principles.indices.toList()) { i ->
            val pr = Science.principles[i]
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card)
                    .clickable { openTip = if (openTip == i) null else i }.padding(14.dp).animateContentSize(),
            ) {
                Text(pr.title, fontWeight = FontWeight.Medium)
                if (openTip == i) {
                    Gap(6.dp)
                    Text(pr.body, fontSize = 14.sp, color = C.muted)
                    Gap(8.dp)
                    Text("Prueba: ${pr.tryThis}", fontSize = 14.sp)
                    Gap(6.dp)
                    Text(pr.source, fontSize = 11.sp, color = C.faint)
                }
            }
            Gap(8.dp)
        }
        item { Gap(20.dp) }
    }

    if (adding) {
        var cue by remember { mutableStateOf("") }
        var action by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = C.card,
            title = { Text("Nuevo plan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(cue, { cue = it }, "Si… (situación concreta)")
                    Field(action, { action = it }, "Entonces… (acción pequeña)")
                    Text("Ideas:", color = C.faint, fontSize = 12.sp)
                    planIdeas.forEach { (c, a) ->
                        Text("Si $c → $a", fontSize = 13.sp, color = C.muted, modifier = Modifier.clickable { cue = c; action = a }.padding(vertical = 2.dp))
                    }
                }
            },
            confirmButton = {
                QuietButton("Guardar") {
                    if (cue.isNotBlank() && action.isNotBlank()) {
                        model.store.edit { it.copy(plans = it.plans + IfThenPlan(UUID.randomUUID().toString(), cue.trim(), action.trim())) }
                        adding = false
                    }
                }
            },
            dismissButton = { QuietButton("Cancelar", color = C.muted) { adding = false } },
        )
    }
}

private fun buildPlan(p: IfThenPlan) = "Si ${p.cue}, entonces ${p.action}."

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, color = C.muted, fontSize = 12.sp)
    }
}

private val surfPrompts = listOf(
    "Nota el antojo sin obedecerlo. ¿Dónde lo sientes en el cuerpo?",
    "Ponle un número del 1 al 10. Solo obsérvalo.",
    "Ponle nombre a lo que sientes: ¿estrés, cansancio, aburrimiento, soledad?",
    "Las olas suben, llegan arriba y bajan. Esta también.",
    "¿Sigue igual de fuerte? Casi siempre ya bajó un poco.",
    "Si comes después, que sea una decisión, no un impulso.",
)

/** Urge surfing (Bowen & Marlatt, 2009): breathe with the circle for three minutes. */
@Composable
fun SosScreen(model: AppModel, onClose: () -> Unit) {
    var seconds by remember { mutableIntStateOf(180) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (seconds > 0) { delay(1000); seconds-- }
        done = true
    }
    // 4 s in, 6 s out: a slow exhale calms the stress response.
    val phase = (180 - seconds) % 10
    val inhale = phase < 4
    val grow by animateFloatAsState(if (inhale) 1f else 0.45f, tween(if (inhale) 4000 else 6000), label = "breath")

    Column(
        Modifier.fillMaxSize().background(C.night).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f))
            IconButton(onClose) { Icon(Icons.Filled.Close, "Cerrar", tint = C.muted) }
        }
        if (!done) {
            Text("%d:%02d".format(seconds / 60, seconds % 60), color = C.muted, fontSize = 15.sp)
            Gap(24.dp)
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(240.dp)) {
                    drawCircle(C.honey.copy(alpha = 0.08f), size.minDimension / 2)
                    drawCircle(C.honey.copy(alpha = 0.22f), size.minDimension / 2 * grow)
                }
                Text(if (inhale) "inhala" else "exhala", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Gap(32.dp)
            Text(
                surfPrompts[((180 - seconds) / 30).coerceIn(0, surfPrompts.size - 1)],
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            )
            Box(Modifier.weight(1f))
            Text("Revisa también: ¿tienes hambre, enojo, soledad o cansancio?", color = C.faint, fontSize = 12.sp, textAlign = TextAlign.Center)
        } else {
            Box(Modifier.weight(1f))
            Bolon(Mood.PROUD, 150.dp)
            Gap(16.dp)
            Text("…Hmpf. Lo lograste.", style = MaterialTheme.typography.headlineMedium)
            Gap(8.dp)
            Text(
                "Si todavía quieres comer, hazlo con calma y en plato. Si la ola pasó, eso fue un músculo que entrenaste.",
                color = C.muted, textAlign = TextAlign.Center,
            )
            Box(Modifier.weight(1f))
            PrimaryButton("La ola pasó") { model.store.edit { it.copy(urgesSurfed = it.urgesSurfed + 1) }; onClose() }
            QuietButton("Igual voy a comer, y está bien", color = C.muted, onClick = onClose)
        }
    }
}
