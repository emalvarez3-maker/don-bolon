package ec.donbolon.app.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.data.Meal
import ec.donbolon.app.data.Memory
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
    // A new week (or the first logged meal) picks a fresh focus from last week's weakest habit.
    androidx.compose.runtime.LaunchedEffect(state.meals.size, Memory.monday(today)) {
        val f = Memory.weeklyFocus(state, targets, today)
        if (f != null && f.weekStart != state.focus?.weekStart) model.store.edit { it.copy(focus = f) }
    }

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
            if (state.profile.windowHours in 1..23) {
                FastingCard(state)
                Gap(12.dp)
            }
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
        // Deleting a meal takes two taps: the first one only asks.
        var sure by remember(m.id) { mutableStateOf(false) }
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
            dismissButton = {
                if (!sure) QuietButton("Eliminar", color = C.muted) { sure = true }
                else QuietButton("Sí, eliminar", color = C.text) { model.store.deleteMeal(m.id); open = null }
            },
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
    // What he blurts out when touched; it replaces the tip for a moment, then the tip comes back.
    var said by remember { mutableStateOf<String?>(null) }
    var touched by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(said) { if (said != null) { delay(3500); said = null } }
    // The first thing he says is how to play with him; the label below is always the way to talk.
    LaunchedEffect(Unit) { if (touched == 0) said = "Tócame o acaríciame. Para conversar, toca «Hablar»." }
    Row(verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Bolon(
                tip.mood, 104.dp,
                Modifier.semantics { contentDescription = "Don Bolón, toca para hablar"; onClick(label = "Hablar") { onBolon(); true } },
                onReact = { said = it.line; touched++ }, onLongPress = onBolon,
            )
            Box(
                Modifier.clip(CircleShape).clickable(onClick = onBolon).sizeIn(minWidth = 48.dp, minHeight = 48.dp).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("💬 Hablar", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
        }
        HGap(8.dp)
        Column(Modifier.weight(1f).padding(top = 6.dp).animateContentSize()) {
            AnimatedContent(said?.let { Tip(tip.mood, it) } ?: tip, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tip") { t ->
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
    // The first glass teaches the long press once; a small preference remembers it was shown.
    val prefs = LocalContext.current.getSharedPreferences("ui_hints", android.content.Context.MODE_PRIVATE)
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) { if (hint) { delay(4000); hint = false } }
    Column {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Habit("📝", "Registré", habits.getValue("Registré"), Modifier.weight(1f))
            Habit("🥩", "Proteína", habits.getValue("Proteína"), Modifier.weight(1f))
            Habit("🏋️", "Entrené", habits.getValue("Entrené"), Modifier.weight(1f), onTap = {
                model.store.edit { s -> s.copy(workouts = if (today in s.workouts) s.workouts - today else s.workouts + today) }
            })
            Habit(
                "💧", "$water/$waterGoal", habits.getValue("Agua"), Modifier.weight(1f),
                onTap = {
                    model.store.addWater(today, 1)
                    if (!prefs.getBoolean("water_hint", false)) { hint = true; prefs.edit().putBoolean("water_hint", true).apply() }
                },
                onLongPress = { if (water > 0) model.store.addWater(today, -1); hint = false },
            )
        }
        if (hint) Text(
            "Mantén presionado el vaso para quitar uno",
            color = C.muted, fontSize = 12.sp, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

/**
 * One habit tile. Tiles you tap are raised cards; the ones that fill themselves ([onTap] null)
 * are flat outlines with an "auto" note, so they don't promise a tap that does nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Habit(icon: String, label: String, done: Boolean, modifier: Modifier, onTap: (() -> Unit)? = null, onLongPress: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(18.dp)
    val auto = onTap == null
    Column(
        modifier.fillMaxHeight().clip(shape)
            .then(
                if (auto) Modifier.background(if (done) C.gold.copy(alpha = 0.08f) else C.bg).border(1.dp, C.line, shape)
                else Modifier.background(if (done) C.gold.copy(alpha = 0.14f) else C.card)
                    .combinedClickable(onLongClick = onLongPress, onClick = onTap!!),
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (done) C.gold else C.raised),
            contentAlignment = Alignment.Center,
        ) { Text(if (done) "✓" else icon, fontSize = 15.sp, color = C.onAccent, fontWeight = FontWeight.Bold) }
        Gap(6.dp)
        Text(label, fontSize = 11.sp, color = if (done) C.text else C.muted)
        if (auto) Text("auto", fontSize = 9.sp, color = C.faint, lineHeight = 10.sp)
    }
}

@Composable
private fun MealRow(m: Meal, photos: File, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnails are sealed: open them off the main thread.
        val thumb = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, m.photo) {
            value = m.photo?.let { name ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Images.loadThumb(File(photos, name))?.asImageBitmap() }
            }
        }.value
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
