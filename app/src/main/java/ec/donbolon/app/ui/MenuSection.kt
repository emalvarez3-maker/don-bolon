package ec.donbolon.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import ec.donbolon.app.data.Fast
import ec.donbolon.app.data.Meal
import ec.donbolon.app.data.PlannedMeal
import ec.donbolon.app.data.Planner
import ec.donbolon.app.data.Science
import ec.donbolon.app.data.Slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Meals in the order they happen, with the hour each one is "over". */
private val dayOrder = listOf(Slot.DESAYUNO to 11, Slot.ALMUERZO to 16, Slot.SNACK to 19, Slot.CENA to 23)

/** The next meal of the day that hasn't been eaten and whose time hasn't passed. */
fun nextSlot(state: AppState, now: LocalTime = LocalTime.now()): Slot? {
    val eaten = state.mealsOn(LocalDate.now()).map { it.slot }.toSet()
    val allowed = Fast.slots(state.profile)
    return dayOrder.firstOrNull { (slot, until) -> slot in allowed && slot !in eaten && now.hour < until }?.first
}

/**
 * One idea for the next meal, sized to what is left of the day (Fitia-style planning, one
 * meal at a time so it never feels like homework). "Lo comí" logs it with one tap.
 */
@Composable
fun NextMeal(model: AppModel, state: AppState) {
    val slot = nextSlot(state) ?: return
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val plan = state.menu[today]?.firstOrNull { it.slot == slot && !it.eaten }
    var busy by remember { mutableStateOf(false) }

    fun budget(): Int {
        val targets = model.targets()
        val left = targets.kcal - state.totalsOn(today).kcal
        val allowed = Fast.slots(state.profile)
        val remaining = dayOrder.map { it.first }.dropWhile { it != slot }.filter { s -> s in allowed && state.mealsOn(today).none { it.slot == s } }
        val share = Planner.share.getValue(slot) / remaining.sumOf { Planner.share.getValue(it) }
        return max(150.0, left * share).roundToInt()
    }

    fun idea() {
        busy = true
        scope.launch {
            val kcal = budget()
            val avoid = listOfNotNull(plan?.title)
            val ai = model.ai()
            val fresh = if (!ai.available) Planner.meal(slot, kcal.toDouble(), avoid.firstOrNull()) else runCatching {
                withContext(Dispatchers.IO) { ai.menu(Science.context(model.store.state.value, model.targets()), mapOf(slot to kcal), avoid) }.firstOrNull()
            }.getOrNull() ?: Planner.meal(slot, kcal.toDouble(), avoid.firstOrNull())
            model.store.edit { s -> s.copy(menu = s.menu + (today to (s.menu[today].orEmpty().filterNot { it.slot == slot } + fresh))) }
            busy = false
        }
    }

    fun eat(p: PlannedMeal) {
        model.store.addMeal(Meal(UUID.randomUUID().toString(), System.currentTimeMillis(), p.slot, p.items, note = p.title, source = "menú"))
        model.store.edit { s -> s.copy(menu = s.menu + (today to s.menu[today].orEmpty().map { if (it.slot == p.slot) it.copy(eaten = true) else it })) }
    }

    Panel(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slot.emoji, fontSize = 20.sp)
            HGap(10.dp)
            Column(Modifier.weight(1f)) {
                val moment = when (slot) { Slot.DESAYUNO -> "en el desayuno"; Slot.ALMUERZO -> "en el almuerzo"; Slot.CENA -> "en la cena"; Slot.SNACK -> "de snack" }
                Text("¿Qué como $moment?", color = C.muted, fontSize = 12.sp)
                Text(plan?.title ?: "Te doy una idea que cuadra con tu día", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = C.accent, strokeWidth = 2.dp)
            else plan?.let { Text("${it.totals.kcal.fmt()} kcal", color = C.muted, fontSize = 13.sp) }
        }
        plan?.let { p ->
            Gap(6.dp)
            Text(p.items.joinToString(" · ") { "${it.name} ${it.grams.fmt()} g" }, color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 30.dp))
        }
        Gap(10.dp)
        Row(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (plan == null) SmallChip(if (busy) "Pensando…" else "Dame una idea", true) { if (!busy) idea() }
            else if (!busy) {
                SmallChip("Lo comí", true) { eat(plan) }
                SmallChip("Otra idea", false) { idea() }
            }
        }
    }
}

@Composable
private fun SmallChip(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (primary) C.accent else C.raised)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = if (primary) C.onAccent else C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}
