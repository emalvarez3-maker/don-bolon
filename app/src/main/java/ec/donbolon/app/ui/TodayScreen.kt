package ec.donbolon.app.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
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
import ec.donbolon.app.data.Tip
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-EC"))
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TodayScreen(model: AppModel, go: (Overlay) -> Unit, ask: (String) -> Unit) {
    val state by model.store.state.collectAsState()
    val targets = Nutrition.targets(state.profile)
    val today = LocalDate.now()
    val totals = state.totalsOn(today)
    val meals = state.mealsOn(today)
    var open by remember { mutableStateOf<Meal?>(null) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(dayFmt).replaceFirstChar { it.uppercase() }, color = C.muted, fontSize = 13.sp)
                    Text(if (state.profile.name.isBlank()) "Hoy" else "Hola, ${state.profile.name}", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton({ go(Overlay.PROFILE) }) { Icon(Icons.Outlined.Settings, "Perfil y metas", tint = C.muted) }
            }
            Gap(14.dp)
            BolonSays(
                Science.tips(state, targets),
                onBolon = { ask("") },
                onAction = { a ->
                    when (a) {
                        Tip.Action.CAPTURE -> go(Overlay.CAPTURE)
                        Tip.Action.SOS -> go(Overlay.SOS)
                        Tip.Action.CHAT -> ask("¿Qué como con lo que me queda hoy?")
                    }
                },
            )
            Gap(16.dp)
            Panel {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CalorieRing(totals.kcal, targets.kcal, 172.dp) }
                Gap(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MacroBar("Proteína", totals.protein, targets.protein, C.protein, Modifier.weight(1f))
                    MacroBar("Carbos", totals.carbs, targets.carbs, C.carbs, Modifier.weight(1f))
                    MacroBar("Grasa", totals.fat, targets.fat, C.fat, Modifier.weight(1f))
                }
            }
            Gap(12.dp)
            Habits(model, targets.waterGlasses)
            Gap(12.dp)
            NextMeal(model, state)
            Gap(22.dp)
            Label("Comidas de hoy")
            Gap(10.dp)
            if (meals.isEmpty()) {
                Text("Toca + y tómale una foto a tu plato.", color = C.muted, fontSize = 14.sp)
            }
        }
        items(meals, key = { it.id }) { m ->
            MealRow(m, model.store.photos) { open = m }
            Gap(8.dp)
        }
        item { Gap(24.dp) }
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
                    Text("Proteína ${m.totals.protein.fmt()} g · Carbos ${m.totals.carbs.fmt()} g · Grasa ${m.totals.fat.fmt()} g", color = C.muted, fontSize = 12.sp)
                }
            },
            confirmButton = { QuietButton("Cerrar") { open = null } },
            dismissButton = { QuietButton("Eliminar", color = C.over) { model.store.deleteMeal(m.id); open = null } },
        )
    }
}

/**
 * Don Bolón and his speech bubble. One thing at a time: tap the bubble to see the study behind
 * it, "Otro" for the next thing he has to say, and tap him to talk.
 */
@Composable
private fun BolonSays(tips: List<Tip>, onBolon: () -> Unit, onAction: (Tip.Action) -> Unit) {
    var i by remember(tips.size) { mutableIntStateOf(0) }
    var why by remember { mutableStateOf(false) }
    val tip = tips[i % tips.size]
    Row(verticalAlignment = Alignment.Top) {
        Bolon(tip.mood, 104.dp, onPoke = onBolon)
        HGap(8.dp)
        Column(Modifier.weight(1f).padding(top = 6.dp).animateContentSize()) {
            AnimatedContent(tip, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tip") { t ->
                Column(
                    Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                        .background(C.card).clickable { why = !why }.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(t.text, fontSize = 14.sp, lineHeight = 20.sp)
                    if (why && t.source != null) {
                        Gap(6.dp)
                        Text("📚 ${t.source}", color = C.muted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                tip.action?.let { a -> QuietButton(a.label) { onAction(a) } }
                if (tip.source != null && !why) QuietButton("¿Por qué?", color = C.muted) { why = true }
                Box(Modifier.weight(1f))
                if (tips.size > 1) QuietButton("Otro ›", color = C.muted) { i++; why = false }
            }
        }
    }
}

/** The four daily habits as round toggles: two fill themselves, two are one tap. */
@Composable
private fun Habits(model: AppModel, waterGoal: Int) {
    val state by model.store.state.collectAsState()
    val today = LocalDate.now()
    val habits = Nutrition.habits(state, Nutrition.targets(state.profile), today).toMap()
    val water = state.water[today] ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Habit("📝", "Registré", habits.getValue("Registré"), Modifier.weight(1f))
        Habit("🥩", "Proteína", habits.getValue("Proteína"), Modifier.weight(1f))
        Habit("🏋️", "Entrené", habits.getValue("Entrené"), Modifier.weight(1f)) {
            model.store.edit { s -> s.copy(workouts = if (today in s.workouts) s.workouts - today else s.workouts + today) }
        }
        Habit("💧", "$water/$waterGoal", habits.getValue("Agua"), Modifier.weight(1f)) { model.store.addWater(today, 1) }
    }
}

@Composable
private fun Habit(icon: String, label: String, done: Boolean, modifier: Modifier, onTap: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(if (done) C.honey.copy(alpha = 0.14f) else C.card)
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (done) C.honey else C.raised),
            contentAlignment = Alignment.Center,
        ) { Text(if (done) "✓" else icon, fontSize = 15.sp, color = C.onAccent, fontWeight = FontWeight.Bold) }
        Gap(6.dp)
        Text(label, fontSize = 11.sp, color = if (done) C.text else C.muted)
    }
}

@Composable
private fun MealRow(m: Meal, photos: File, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = remember(m.photo) { m.photo?.let { BitmapFactory.decodeFile(File(photos, it).path)?.asImageBitmap() } }
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(C.raised), contentAlignment = Alignment.Center) {
            if (thumb != null) Image(thumb, null, Modifier.size(52.dp), contentScale = ContentScale.Crop)
            else Text(m.slot.emoji, fontSize = 22.sp)
        }
        HGap(12.dp)
        Column(Modifier.weight(1f)) {
            Text("${m.slot.label} · ${Instant.ofEpochMilli(m.time).atZone(ZoneId.systemDefault()).format(timeFmt)}", color = C.muted, fontSize = 12.sp)
            Text(m.items.joinToString { it.name }, maxLines = 2, fontSize = 14.sp)
        }
        HGap(8.dp)
        Column(horizontalAlignment = Alignment.End) {
            Text(m.totals.kcal.fmt(), fontWeight = FontWeight.SemiBold)
            Text("kcal", color = C.faint, fontSize = 11.sp)
        }
    }
}
