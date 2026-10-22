package ec.donbolon.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.data.Meal
import ec.donbolon.app.data.Nutrition
import ec.donbolon.app.data.Science
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-EC"))
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TodayScreen(model: AppModel, onProfile: () -> Unit, onAsk: (String) -> Unit) {
    val state by model.store.state.collectAsState()
    val targets = Nutrition.targets(state.profile)
    val today = LocalDate.now()
    val totals = state.totalsOn(today)
    val meals = state.mealsOn(today)
    val (mood, line) = Science.bolonLine(state, targets)
    val streak = Nutrition.streak(state.loggedDays)
    var open by remember { mutableStateOf<Meal?>(null) }
    val water = state.water[today] ?: 0

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(dayFmt).replaceFirstChar { it.uppercase() }, color = C.muted, fontSize = 13.sp)
                    Text(if (state.profile.name.isBlank()) "Hoy" else "Hola, ${state.profile.name}", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onProfile) { Icon(Icons.Outlined.Settings, "Perfil", tint = C.muted) }
            }
            Gap(14.dp)
            Row(verticalAlignment = Alignment.Top) {
                Bolon(mood, 92.dp, onPoke = {})
                HGap(10.dp)
                SpeechBubble(line, Modifier.weight(1f).padding(top = 10.dp).clickable { onAsk("") })
            }
            Gap(18.dp)
            Panel {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CalorieRing(totals.kcal, targets.kcal) }
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MacroBar("Proteína", totals.protein, targets.protein, C.protein, Modifier.weight(1f))
                    MacroBar("Carbos", totals.carbs, targets.carbs, C.carbs, Modifier.weight(1f))
                    MacroBar("Grasa", totals.fat, targets.fat, C.fat, Modifier.weight(1f))
                    MacroBar("Fibra", totals.fiber, targets.fiber, C.fiber, Modifier.weight(1f))
                }
            }
            Gap(12.dp)
            val habits = Nutrition.habits(state, targets, today)
            Panel(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Constancia de hoy", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("${habits.count { it.second }}/4", color = C.honey, fontWeight = FontWeight.SemiBold)
                }
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    habits.forEach { (name, done) ->
                        val tap = if (name == "Entrené") Modifier.clickable {
                            model.store.edit { s -> s.copy(workouts = if (today in s.workouts) s.workouts - today else s.workouts + today) }
                        } else Modifier
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (done) C.honey.copy(alpha = 0.16f) else C.raised).then(tap).padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(if (done) "✓" else when (name) { "Registré" -> "📝"; "Proteína" -> "🥩"; "Entrené" -> "🏋️"; else -> "💧" }, fontSize = 16.sp, color = C.honey)
                            Text(name, fontSize = 11.sp, color = if (done) C.text else C.muted)
                        }
                    }
                }
                Gap(8.dp)
                Text(
                    "Entrenos esta semana: ${Nutrition.workoutsThisWeek(state)} de ${state.profile.trainDays} · toca 🏋️ cuando entrenes",
                    color = C.faint, fontSize = 12.sp,
                )
            }
            Gap(12.dp)
            Panel(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Agua", fontWeight = FontWeight.Medium)
                        Text("$water de ${targets.waterGlasses} vasos", color = C.muted, fontSize = 12.sp)
                    }
                    Stepper({ model.store.addWater(today, -1) }, { model.store.addWater(today, 1) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                            repeat(minOf(targets.waterGlasses, 10)) { i -> Dot(if (i < water) C.carbs else C.raised, 7.dp) }
                        }
                    }
                }
            }
            Gap(22.dp)
            MenuSection(model, state)
            Gap(22.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Registrado hoy", Modifier.weight(1f))
                if (streak > 0) Text("🔥 $streak ${if (streak == 1) "día" else "días"} registrando · meta 66", color = C.muted, fontSize = 12.sp)
            }
            Gap(10.dp)
            if (meals.isEmpty()) {
                Panel {
                    Text("Nada todavía.", fontWeight = FontWeight.Medium)
                    Text("Toca el botón + y tómale una foto a tu plato.", color = C.muted, fontSize = 13.sp)
                }
            }
        }
        items(meals, key = { it.id }) { m ->
            MealRow(m, model.store.photos) { open = m }
            Gap(8.dp)
        }
        item {
            Gap(18.dp)
            val tip = Science.tipOfDay()
            Panel {
                Label("Ciencia del día")
                Gap(6.dp)
                Text(tip.title, fontWeight = FontWeight.SemiBold)
                Gap(4.dp)
                Text(tip.tryThis, color = C.muted, fontSize = 14.sp)
            }
            Gap(24.dp)
        }
    }

    open?.let { m ->
        AlertDialog(
            onDismissRequest = { open = null },
            containerColor = C.card,
            title = { Text("${m.slot.label} · ${m.totals.kcal.fmt()} kcal") },
            text = {
                Column {
                    m.items.forEach {
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(it.name, Modifier.weight(1f), fontSize = 14.sp)
                            Text("${it.grams.fmt()} g · ${it.kcal.fmt()} kcal", color = C.muted, fontSize = 13.sp)
                        }
                    }
                    Gap(8.dp)
                    Text("P ${m.totals.protein.fmt()} g · C ${m.totals.carbs.fmt()} g · G ${m.totals.fat.fmt()} g · fibra ${m.totals.fiber.fmt()} g", color = C.muted, fontSize = 12.sp)
                    m.reason?.let { Text("Motivo: ${it.label}${m.hunger?.let { h -> " · hambre $h/10" } ?: ""}", color = C.muted, fontSize = 12.sp) }
                }
            },
            confirmButton = { QuietButton("Cerrar") { open = null } },
            dismissButton = { QuietButton("Eliminar", color = C.over) { model.store.deleteMeal(m.id); open = null } },
        )
    }
}

@Composable
private fun MealRow(m: Meal, photos: File, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = remember(m.photo) { m.photo?.let { BitmapFactory.decodeFile(File(photos, it).path)?.asImageBitmap() } }
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(C.raised), contentAlignment = Alignment.Center) {
            if (thumb != null) Image(thumb, null, Modifier.size(52.dp), contentScale = ContentScale.Crop)
            else Text(m.slot.emoji, fontSize = 22.sp)
        }
        HGap(12.dp)
        Column(Modifier.weight(1f)) {
            Text("${m.slot.label} · ${java.time.Instant.ofEpochMilli(m.time).atZone(java.time.ZoneId.systemDefault()).format(timeFmt)}", color = C.muted, fontSize = 12.sp)
            Text(m.items.joinToString { it.name }, maxLines = 2, fontSize = 14.sp)
        }
        HGap(8.dp)
        Column(horizontalAlignment = Alignment.End) {
            Text(m.totals.kcal.fmt(), fontWeight = FontWeight.SemiBold)
            Text("kcal", color = C.faint, fontSize = 11.sp)
        }
    }
}
