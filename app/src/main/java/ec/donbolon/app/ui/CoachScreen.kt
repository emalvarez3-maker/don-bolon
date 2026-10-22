package ec.donbolon.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.ai.Ai
import ec.donbolon.app.ai.AiException
import ec.donbolon.app.data.Memory
import ec.donbolon.app.data.AppState
import ec.donbolon.app.data.Mood
import ec.donbolon.app.data.ChatMessage
import ec.donbolon.app.data.FoodDb
import ec.donbolon.app.data.Science
import ec.donbolon.app.data.Targets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val quick = listOf(
    "¿Qué como ahora?",
    "Arma mi día de mañana",
    "Me pasé, ¿qué hago?",
    "¿Cómo voy esta semana?",
)

@Composable
fun CoachScreen(model: AppModel, seed: String?, onSeedUsed: () -> Unit, onClose: () -> Unit, onSos: () -> Unit) {
    val state by model.store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    fun send(text: String) {
        if (text.isBlank() || busy) return
        input = ""
        model.store.edit { it.copy(chat = it.chat + ChatMessage(true, text.trim())) }
        busy = true
        scope.launch {
            val s = model.store.state.value
            val targets = model.targets()
            val ai = model.ai()
            val reply = if (!ai.available) Ai.Reply(localReply(text, s, targets), emptyList()) else try {
                withContext(Dispatchers.IO) { ai.coach(Science.COACH_PERSONA + "\n\n" + Science.context(s, targets), s.chat) }
            } catch (e: AiException) {
                Ai.Reply("Grrr, no pude pensar: ${e.message}\n\n" + localReply(text, s, targets), emptyList())
            } catch (e: Exception) {
                Ai.Reply("Grrr, algo falló. " + localReply(text, s, targets), emptyList())
            }
            // What he learned goes to long-term memory; the person can review it in Perfil.
            // Canned buttons and short questions say nothing about the person: never learn from them.
            val learn = text !in quick && !(text.trim().endsWith("?") && text.length < 80)
            model.store.edit { (if (learn) Memory.remember(it, reply.remember) else it).copy(chat = it.chat + ChatMessage(false, reply.text)) }
            busy = false
        }
    }

    LaunchedEffect(seed) { if (!seed.isNullOrBlank()) send(seed); onSeedUsed() }
    LaunchedEffect(state.chat.size, busy) { if (state.chat.isNotEmpty()) list.animateScrollToItem(state.chat.size) }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Filled.Close, "Cerrar", tint = C.muted) }
            Bolon(if (busy) Mood.THINKING else Mood.CALM, 60.dp, talking = busy, onPoke = {})
            HGap(10.dp)
            Column {
                Text("Don Bolón", style = MaterialTheme.typography.titleLarge)
                Text(if (busy) "pensando…" else "gruñón, pero de tu lado", color = C.muted, fontSize = 13.sp)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            if (state.chat.isEmpty()) item {
                SpeechBubble("Hmpf. Pregúntame qué comer, qué hacer con un antojo o cómo vas. Sé lo que comiste hoy, así que no me mientas.")
            }
            items(state.chat) { m -> Bubble(m) }
            item { Gap(4.dp) }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("Tengo un antojo", false, onClick = onSos)
            quick.forEach { q -> Chip(q, false) { send(q) } }
        }
        Row(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(input, { input = it }, "Escríbele a Don Bolón", Modifier.weight(1f), singleLine = false)
            IconButton({ send(input) }, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = if (busy) C.faint else C.accent) }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            m.text,
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (m.fromUser) C.accent.copy(alpha = 0.16f) else C.card)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 14.sp, lineHeight = 20.sp,
        )
    }
}

/** Without AI Don Bolón still answers, from the numbers and the food notebook. */
fun localReply(text: String, s: AppState, t: Targets): String {
    val today = s.totalsOn(LocalDate.now())
    val left = t.kcal - today.kcal
    val proteinLeft = t.protein - today.protein
    val q = text.lowercase()
    return when {
        "antojo" in q || "dulce" in q || "ansiedad" in q ->
            "Grrr, los antojos suben y bajan como olas. Ve a Mente → SOS y aguanta 3 minutos conmigo. Si después sigue ahí, cómelo servido en plato, sentado y sin culpa: una porción, no la bolsa."
        "pasé" in q || "pase" in q || "culpa" in q ->
            "Una comida no arruina nada. Lo que arruina es el \"ya qué más da\". Hoy comes normal: nada de compensar con hambre ni castigo. Proteína, verduras y a seguir registrando."
        "semana" in q -> {
            val days = (1..7).map { s.totalsOn(LocalDate.now().minusDays(it.toLong())).kcal }.filter { it > 0 }
            if (days.isEmpty()) "No tengo datos de la semana. Registra y luego hablamos."
            else "Promedio ${days.average().fmt()} kcal en ${days.size} días registrados (meta ${t.kcal}). ${if (days.average() <= t.kcal * 1.1) "Hmpf, vas bien." else "Te estás pasando; mira qué comida se repite."}"
        }
        else -> {
            val ideas = FoodDb.all.filter { it.protein >= 9 && it.kcal * it.portionG / 100 <= left.coerceAtLeast(150.0) }
                .sortedByDescending { it.protein / it.kcal }.take(3)
            "Te quedan ${left.fmt()} kcal y ${proteinLeft.coerceAtLeast(0.0).fmt()} g de proteína. " +
                (if (ideas.isEmpty()) "Ya casi no queda margen: verduras, caldo o una fruta." else "Opciones: " + ideas.joinToString("; ") { "${it.name} (${it.portion}, ${(it.kcal * it.portionG / 100).fmt()} kcal)" } + ". Acompaña con ensalada.")
        }
    }
}
