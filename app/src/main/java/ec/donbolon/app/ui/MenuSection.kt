package ec.donbolon.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.data.AppState
import ec.donbolon.app.data.Meal
import ec.donbolon.app.data.Mood
import ec.donbolon.app.data.PlannedMeal
import ec.donbolon.app.data.Planner
import ec.donbolon.app.data.Science
import ec.donbolon.app.data.Slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

/**
 * "Tu menú de hoy": a ready-made day that fits the targets. Eating a planned meal is one tap,
 * which makes logging nearly free — the single biggest lever on adherence.
 */
@Composable
fun MenuSection(model: AppModel, state: AppState) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val menu = state.menu[today]
    var busy by remember { mutableStateOf<Set<Slot>>(emptySet()) }
    var note by remember { mutableStateOf<String?>(null) }

    fun build(slots: List<Slot>, avoid: List<String>) {
        busy = busy + slots
        note = null
        scope.launch {
            val targets = model.targets()
            val budgets = slots.associateWith { (targets.kcal * Planner.share.getValue(it)).roundToInt() }
            val ai = model.ai()
            val fresh: List<PlannedMeal> = if (!ai.available) budgets.map { (s, k) -> Planner.meal(s, k.toDouble(), avoid.firstOrNull()) } else try {
                val got = withContext(Dispatchers.IO) { ai.menu(Science.context(model.store.state.value, targets), budgets, avoid) }
                // Anything the model skipped is filled from the offline planner.
                slots.map { s -> got.firstOrNull { it.slot == s } ?: Planner.meal(s, budgets.getValue(s).toDouble()) }
            } catch (e: Exception) {
                note = "No pude preguntarle a la IA (${e.message}). Te armé uno con mi libreta."
                budgets.map { (s, k) -> Planner.meal(s, k.toDouble(), avoid.firstOrNull()) }
            }
            model.store.edit { s ->
                val current = s.menu[today].orEmpty().filterNot { it.slot in slots }
                s.copy(menu = s.menu + (today to (current + fresh).sortedBy { it.slot.ordinal }))
            }
            busy = busy - slots.toSet()
        }
    }

    fun eat(p: PlannedMeal) {
        model.store.addMeal(Meal(UUID.randomUUID().toString(), System.currentTimeMillis(), p.slot, p.items, note = p.title, source = "menú"))
        model.store.edit { s -> s.copy(menu = s.menu + (today to s.menu[today].orEmpty().map { if (it.slot == p.slot) it.copy(eaten = true) else it })) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("Tu menú de hoy", Modifier.weight(1f))
        if (menu != null && busy.isEmpty()) QuietButton("Rehacer", color = C.muted) { build(Slot.entries.filter { s -> menu.none { it.slot == s && it.eaten } }, menu.map { it.title }) }
    }
    Gap(8.dp)
    if (menu == null) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Bolon(if (busy.isEmpty()) Mood.GRUMPY else Mood.THINKING, 56.dp, talking = busy.isNotEmpty())
                HGap(12.dp)
                Column(Modifier.weight(1f)) {
                    Text("¿No sabes qué comer?", fontWeight = FontWeight.Medium)
                    Text("Te armo el día completo, ya cuadrado con tus calorías y proteína.", color = C.muted, fontSize = 13.sp)
                }
            }
            Gap(12.dp)
            PrimaryButton(if (busy.isEmpty()) "Armar mi menú" else "Cocinando ideas…", enabled = busy.isEmpty()) { build(Slot.entries, emptyList()) }
        }
    } else {
        Column(Modifier.fillMaxWidth().clip(Shape).background(C.card)) {
            menu.forEachIndexed { i, p ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = C.line)
                PlannedRow(p, loading = p.slot in busy, onEat = { eat(p) }, onSwap = { build(listOf(p.slot), listOf(p.title)) })
            }
            val left = menu.filterNot { it.eaten }.sumOf { it.totals.kcal }
            Text(
                "Menú: ${menu.sumOf { it.totals.kcal }.fmt()} kcal · ${menu.sumOf { it.totals.protein }.fmt()} g proteína" + if (left > 0) " · faltan ${left.fmt()}" else "",
                color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp, top = 2.dp),
            )
        }
    }
    note?.let { Text(it, color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
}

@Composable
private fun PlannedRow(p: PlannedMeal, loading: Boolean, onEat: () -> Unit, onSwap: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.slot.emoji, fontSize = 18.sp)
            HGap(10.dp)
            Column(Modifier.weight(1f)) {
                Text(p.slot.label, color = C.faint, fontSize = 11.sp)
                Text(p.title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = if (p.eaten) C.muted else C.text)
            }
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = C.honey, strokeWidth = 2.dp)
            else Text("${p.totals.kcal.fmt()} kcal", fontSize = 13.sp, color = C.muted)
        }
        if (open) {
            Gap(6.dp)
            p.items.forEach { Text("· ${it.name}  ${it.grams.fmt()} g", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 30.dp)) }
            Text("P ${p.totals.protein.fmt()} · C ${p.totals.carbs.fmt()} · G ${p.totals.fat.fmt()}", color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(start = 30.dp, top = 2.dp))
        }
        Gap(8.dp)
        Row(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p.eaten) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(C.good), contentAlignment = Alignment.Center) { Text("✓", color = C.night, fontSize = 11.sp) }
                Text("Registrado", color = C.good, fontSize = 13.sp)
            } else if (!loading) {
                SmallChip("Lo comí", true, onEat)
                SmallChip("Cambiar", false, onSwap)
            }
        }
    }
}

@Composable
private fun SmallChip(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (primary) C.honey.copy(alpha = 0.16f) else C.raised)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = if (primary) C.honey else C.text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}
