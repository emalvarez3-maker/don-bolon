package ec.donbolon.app.ui

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.data.DayMark
import ec.donbolon.app.data.Nutrition
import ec.donbolon.app.data.Point
import ec.donbolon.app.data.Progress
import ec.donbolon.app.data.journeyFor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val es = Locale.forLanguageTag("es-EC")
private val shortDate = DateTimeFormatter.ofPattern("d MMM", es)

private enum class Metric(val label: String, val unit: String) { PESO("Peso", "kg"), CINTURA("Cintura", "cm"), GRASA("Grasa perdida", "kg") }
private enum class Range(val label: String, val days: Long?) { MES("1 mes", 30), TRIMESTRE("3 meses", 90), TODO("Todo", null) }

/**
 * How far Don Bolón has already walked on "Tu camino" in this process. The tab leaves composition
 * when the user switches tabs, so a rememberSaveable alone would forget it; this keeps the walk
 * from replaying on every visit, and only the change since last time is animated.
 */
private var lastWalked = 0f

/** "1 día" / "3 días". */
internal fun days(n: Int): String = if (n == 1) "1 día" else "$n días"

/** The streak line of the calendar: the days in a row, out of the 66 it takes to make a habit. */
internal fun streakLine(n: Int): String = "🔥 Racha: ${days(n)} · de 66 para volverlo hábito"

/** A small "¿Por qué?" that opens the science behind a number, closed by default. */
@Composable
internal fun WhyNote(text: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable(text) { mutableStateOf(false) }
    Column(modifier) {
        QuietButton(if (open) "Ocultar" else "¿Por qué?", color = C.muted) { open = !open }
        AnimatedVisibility(open) {
            Text(text, color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
}

@Composable
fun ProgressScreen(model: AppModel) {
    val state by model.store.state.collectAsState()
    val targets = Nutrition.targets(state.profile)
    val today = LocalDate.now()
    var metric by rememberSaveable { mutableStateOf(Metric.PESO) }
    var range by rememberSaveable { mutableStateOf(Range.TRIMESTRE) }
    var weightText by remember { mutableStateOf("") }
    var waistText by remember { mutableStateOf("") }
    var goalText by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf<String?>(null) }
    var walked by rememberSaveable { mutableFloatStateOf(lastWalked) }

    val trend = Nutrition.trend(state.weights).map { Point(it.first, it.second) }
    val projection = remember(state.weights, state.profile) { Progress.projection(state) }
    val fat = remember(state.meals.size, state.weights) { Progress.fatCurve(state, 120) }
    val week = Progress.weekStats(state)
    val journey = remember(state.weights, state.profile) { journeyFor(state) }
    val composition = remember(state.waist, state.weights, state.profile) { Progress.composition(state) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            Text("Progreso", style = MaterialTheme.typography.headlineMedium)
            Gap(12.dp)

            // --- Today's measurements: the one thing to do here ---------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(weightText, { weightText = it; saved = null }, "Peso hoy (kg)", Modifier.weight(1f), number = true)
                HGap(8.dp)
                Field(waistText, { waistText = it; saved = null }, "Cintura (cm)", Modifier.weight(1f), number = true)
                HGap(4.dp)
                QuietButton("Guardar") {
                    val kg = weightText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..300.0 }
                    val cm = waistText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 40.0..200.0 }
                    kg?.let { model.store.logWeight(it) }
                    cm?.let { model.store.logWaist(it) }
                    saved = if (kg == null && cm == null) null else "✓ Guardado"
                    if (kg != null) weightText = ""
                    if (cm != null) waistText = ""
                }
            }
            saved?.let { Text(it, color = C.good, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            if (composition.isEmpty() && saved == null) {
                Text("Con tu cintura te digo cuánto es grasa y cuánto músculo.", color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Gap(16.dp)

            // --- The road to the goal ----------------------------------------------------------
            if (journey != null) {
                Panel(padding = 16.dp) {
                    Label("Tu camino")
                    Gap(8.dp)
                    JourneyPath(journey, projection.reachesGoal, initialFraction = walked, onWalked = { walked = it; lastWalked = it })
                }
                Gap(12.dp)
            } else if (state.profile.goalWeightKg == null) {
                Panel(padding = 16.dp) {
                    Label("Tu camino")
                    Gap(6.dp)
                    Text("Ponte un peso meta y te digo cuándo llegas a este ritmo.", fontSize = 14.sp)
                    Gap(8.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(goalText, { goalText = it }, "Peso meta (kg)", Modifier.weight(1f), number = true)
                        HGap(8.dp)
                        QuietButton("Guardar") {
                            goalText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 35.0..250.0 }?.let { g ->
                                model.store.edit { it.copy(profile = it.profile.copy(goalWeightKg = g)) }; goalText = ""
                            }
                        }
                    }
                }
                Gap(12.dp)
            }

            state.focus?.let { f ->
                Panel(padding = 16.dp) {
                    Label("Enfoque de la semana")
                    Gap(4.dp)
                    Text(f.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = C.text)
                    Gap(2.dp)
                    Text(f.plan, fontSize = 14.sp, color = C.muted)
                }
                Gap(12.dp)
            }

            // --- The curve ---------------------------------------------------------------------
            Panel(padding = 16.dp) {
                val start = state.weights.firstOrNull()?.kg ?: state.profile.weightKg
                val now = Progress.currentWeight(state)
                val goal = state.profile.goalWeightKg
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Tu curva", Modifier.weight(1f))
                    NowStat(now, now - start, goal?.let { it - start })
                }
                Gap(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Metric.entries.forEach { m -> SmallToggle(m.label, metric == m) { metric = m } }
                }
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Range.entries.forEach { r -> SmallToggle(r.label, range == r) { range = r } }
                }
                Gap(12.dp)

                val from = range.days?.let { today.minusDays(it) }
                fun cut(ps: List<Point>) = if (from == null) ps else ps.filter { !it.date.isBefore(from) }
                when (metric) {
                    Metric.PESO -> {
                        val raw = cut(state.weights.map { Point(it.date, it.kg) })
                        val proj = if (range == Range.MES) projection.points.filter { it.date <= today.plusDays(30) } else projection.points
                        if (raw.size + trend.size < 2) {
                            EmptyCurve("Pésate en ayunas 2–3 veces por semana. Con dos registros dibujo tu curva y hacia dónde vas.")
                        } else {
                            CurveChart(raw, cut(trend), proj, goal, Metric.PESO, "peso")
                            WhyNote("La línea punteada se va aplanando: al pesar menos gastas menos, así que la misma dieta baja más lento. Es normal (Hall y otros, 2011).")
                        }
                    }
                    Metric.CINTURA -> {
                        val pts = cut(state.waist.entries.sortedBy { it.key }.map { Point(it.key, it.value) })
                        if (pts.size < 2) EmptyCurve("Mide tu cintura a la altura del ombligo, relajado, una vez por semana. Si baja aunque el peso no se mueva, estás ganando músculo y perdiendo grasa.")
                        else {
                            CurveChart(pts, pts, emptyList(), null, Metric.CINTURA, "cintura")
                            Gap(8.dp)
                            val d = pts.last().value - pts.first().value
                            Text("${if (d <= 0) "−" else "+"}${abs(d).fmt(1)} cm desde el ${pts.first().date.format(shortDate)}", fontSize = 14.sp)
                        }
                    }
                    Metric.GRASA -> {
                        val pts = cut(fat)
                        if (pts.size < 2) EmptyCurve("Registra tus comidas algunos días y te muestro cuánta grasa vas perdiendo según tu déficit.")
                        else {
                            CurveChart(pts, pts, emptyList(), null, Metric.GRASA, "grasa perdida", area = true)
                            Gap(8.dp)
                            val k = pts.last().value
                            Text(
                                if (k >= 0) "≈ ${k.fmt(1)} kg de grasa perdida en ${days(pts.size)} con registro."
                                else "≈ ${(-k).fmt(1)} kg ganados en ${days(pts.size)} con registro (comiste más de lo que gastas).",
                                fontSize = 14.sp,
                            )
                            WhyNote("Lo calculo con tu gasto diario y lo que registraste: 7 700 kcal de déficit ≈ 1 kg de grasa. Solo cuenta los días con registro.")
                        }
                    }
                }
            }
            Gap(12.dp)

            // --- Fat vs. muscle, from the waist (only once there is a waist) -------------------
            if (composition.isNotEmpty()) {
                Panel(padding = 16.dp) {
                    Label("Grasa vs. músculo")
                    Gap(10.dp)
                    CompositionChart(composition)
                }
                Gap(12.dp)
            }

            // --- The week (only once something was logged) -------------------------------------
            if (week.loggedDays > 0 || week.trained > 0) {
                Panel(padding = 16.dp) {
                    Label("Tu semana")
                    Gap(12.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WeekTile(week.avgKcal.fmt(), "kcal promedio", (week.avgKcal / targets.kcal).toFloat(), if (week.avgKcal > targets.kcal * (1 + targets.band)) C.over else C.accent)
                        WeekTile("${week.inRange}/7", "días en rango", week.inRange / 7f, C.accent)
                    }
                    Gap(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WeekTile("${week.proteinDays}/7", "días con proteína", week.proteinDays / 7f, C.protein)
                        WeekTile("${week.trained}/${state.profile.trainDays}", "entrenos", week.trained / max(state.profile.trainDays, 1).toFloat(), C.accent)
                    }
                }
                Gap(12.dp)
            }

            // --- Consistency calendar ----------------------------------------------------------
            Panel(padding = 16.dp) {
                Label("Constancia")
                Gap(4.dp)
                Text(streakLine(Nutrition.streak(state.loggedDays)), color = C.muted, fontSize = 13.sp)
                Gap(10.dp)
                Calendar(state, today)
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Legend(C.accent, "En rango"); Legend(C.muted, "Debajo"); Legend(C.over, "Arriba"); Legend(C.raised, "Sin registro")
                }
            }
            Gap(24.dp)
        }
    }
}

/**
 * The current weight and how much it moved since the start, in one compact stat. The change is
 * sage when it goes toward the goal and muted otherwise (never terracotta: that means "over").
 */
@Composable
private fun NowStat(now: Double, delta: Double, towardGoal: Double?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(now.fmt(1), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = C.text)
        Text(" kg", color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        if (abs(delta) >= 0.05) {
            val good = towardGoal != null && delta * towardGoal > 0
            Text(
                "  ${signedKg(delta)} kg", color = if (good) C.accent else C.muted, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun SmallToggle(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (on) C.accent.copy(alpha = 0.18f) else C.raised)
            .then(Modifier.pointerInput(text) { detectTapGestures { onClick() } }).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = if (on) C.accent else C.muted, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun EmptyCurve(text: String) {
    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(C.raised.copy(alpha = 0.5f)).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = C.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/**
 * Dots for what was measured, a solid line for the trend, a dashed one for where it is heading,
 * and the goal as a horizontal line. Tap or slide sideways to read any day; vertical swipes still
 * scroll the screen.
 */
@Composable
private fun CurveChart(raw: List<Point>, line: List<Point>, projection: List<Point>, goal: Double?, metric: Metric, name: String, area: Boolean = false) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    val all = raw + line + projection
    if (all.isEmpty()) return
    val unit = metric.unit
    val first = all.minOf { it.date }
    val last = all.maxOf { it.date }.let { if (it == first) it.plusDays(1) else it }
    val values = all.map { it.value } + listOfNotNull(goal)
    val pad = max((values.max() - values.min()) * 0.15, 0.4)
    val lo = values.min() - pad
    val hi = values.max() + pad
    val span = ChronoUnit.DAYS.between(first, last).toFloat()
    val today = LocalDate.now()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(all) { grow.snapTo(0f); grow.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }

    val series = line.ifEmpty { raw }
    val summary = buildString {
        append("Curva de $name: ")
        append("${series.first().value.fmt(1)} $unit el ${series.first().date.format(shortDate)}, ")
        append("${series.last().value.fmt(1)} $unit el ${series.last().date.format(shortDate)}")
        goal?.let { append(", meta ${it.fmt(1)} $unit") }
    }

    Column {
        Box(
            Modifier.fillMaxWidth().height(190.dp)
                .pointerInput(all) { detectTapGestures { touchX = it.x } }
                .pointerInput(all) {
                    detectHorizontalDragGestures(
                        onDragStart = { touchX = it.x },
                        onDragEnd = { touchX = null },
                        onDragCancel = { touchX = null },
                    ) { c, _ -> touchX = c.position.x }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = summary }) {
                val g = grow.value
                val left = 4.dp.toPx(); val right = size.width - 4.dp.toPx()
                val top = 10.dp.toPx(); val bottom = size.height - 18.dp.toPx()
                fun x(d: LocalDate) = left + (ChronoUnit.DAYS.between(first, d) / span) * (right - left)
                fun y(v: Double) = (top + (hi - v) / (hi - lo) * (bottom - top)).toFloat()
                val label = Paint().apply { isAntiAlias = true; textSize = 10.dp.toPx(); color = C.faint.toArgb() }

                // Soft grid: three horizontal lines with their values.
                for (i in 0..2) {
                    val v = lo + (hi - lo) * (0.2 + 0.3 * i)
                    drawLine(C.line, Offset(left, y(v)), Offset(right, y(v)), 1f)
                    drawContext.canvas.nativeCanvas.drawText(v.fmt(1), left, y(v) - 4, label)
                }
                // Date labels at the ends.
                drawContext.canvas.nativeCanvas.drawText(first.format(shortDate), left, size.height - 2, label)
                label.textAlign = Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(last.format(shortDate), right, size.height - 2, label)

                goal?.let {
                    drawLine(C.gold.copy(alpha = 0.7f), Offset(left, y(it)), Offset(right, y(it)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                    label.color = C.gold.toArgb()
                    drawContext.canvas.nativeCanvas.drawText("meta ${it.fmt(1)}", right, y(it) - 6, label)
                }
                if (today in first..last) drawLine(C.muted.copy(alpha = 0.35f), Offset(x(today), top), Offset(x(today), bottom), 1.5f)

                // The data draws itself from left to right.
                clipRect(right = left + (right - left) * g + 6.dp.toPx()) {
                    if (area && line.size >= 2) {
                        val fill = Path().apply {
                            moveTo(x(line.first().date), y(0.0.coerceIn(lo, hi)))
                            line.forEach { lineTo(x(it.date), y(it.value)) }
                            lineTo(x(line.last().date), y(0.0.coerceIn(lo, hi)))
                            close()
                        }
                        drawPath(fill, C.accent.copy(alpha = 0.18f))
                    }
                    raw.forEach { drawCircle(C.muted.copy(alpha = 0.55f), 3.5.dp.toPx(), Offset(x(it.date), y(it.value))) }
                    if (line.size >= 2) {
                        val p = Path()
                        line.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(pt.date), y(pt.value)) else p.lineTo(x(pt.date), y(pt.value)) }
                        drawPath(p, C.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                    if (projection.size >= 2) {
                        val p = Path()
                        projection.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(pt.date), y(pt.value)) else p.lineTo(x(pt.date), y(pt.value)) }
                        drawPath(p, C.gold, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
                        drawCircle(C.gold, 4.dp.toPx(), Offset(x(projection.last().date), y(projection.last().value)))
                    }
                }

                // Scrubbing: the nearest point to the finger, with its value.
                touchX?.let { tx ->
                    val near = all.minBy { abs(x(it.date) - tx) }
                    val px = x(near.date)
                    drawLine(C.text.copy(alpha = 0.5f), Offset(px, top), Offset(px, bottom), 1.5f)
                    drawCircle(C.text, 5.dp.toPx(), Offset(px, y(near.value)))
                    val what = if (metric == Metric.GRASA) "${metric.label} " else ""
                    val tip = "$what${near.value.fmt(1)} $unit · ${near.date.format(shortDate)}${if (near.date.isAfter(today)) " (proyección)" else ""}"
                    val tp = Paint().apply { isAntiAlias = true; textSize = 12.dp.toPx(); color = C.text.toArgb(); isFakeBoldText = true }
                    val w = tp.measureText(tip) + 16.dp.toPx()
                    val bx = (px - w / 2).coerceIn(left, (right - w).coerceAtLeast(left))
                    drawRoundRect(C.raised, Offset(bx, top), Size(w, 22.dp.toPx()), CornerRadius(8.dp.toPx()))
                    drawContext.canvas.nativeCanvas.drawText(tip, bx + 8.dp.toPx(), top + 15.dp.toPx(), tp)
                }
            }
        }
        Gap(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (raw.isNotEmpty() && raw != line) Legend(C.muted, "Medido")
            if (line.size >= 2) Legend(C.accent, if (raw != line) "Tendencia" else metric.label)
            if (projection.size >= 2) Legend(C.gold, "Hacia dónde vas")
        }
    }
}

@Composable
private fun RowScope.WeekTile(value: String, label: String, frac: Float, color: Color) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(700), label = label)
    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(C.raised.copy(alpha = 0.6f)).padding(12.dp)) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = C.muted, fontSize = 11.sp)
        Gap(8.dp)
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(C.line)) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

/** The last ten weeks as a grid, Monday on top, each day colored by how it went. */
@Composable
private fun Calendar(state: ec.donbolon.app.data.AppState, today: LocalDate) {
    val weeks = 10
    val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
    val marks = (0 until weeks * 7).map { Progress.mark(state, firstMonday.plusDays(it.toLong()), today) }
    val inRange = marks.count { it == DayMark.IN_RANGE }
    val logged = marks.count { it == DayMark.IN_RANGE || it == DayMark.UNDER || it == DayMark.OVER }
    Row(
        Modifier.semantics { contentDescription = "Calendario de 10 semanas: ${days(logged)} con registro, ${days(inRange)} en rango" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) { Text(it, color = C.faint, fontSize = 10.sp) } }
        }
        for (w in 0 until weeks) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (d in 0 until 7) {
                    val day = firstMonday.plusDays((w * 7 + d).toLong())
                    val color = when (marks[w * 7 + d]) {
                        DayMark.IN_RANGE -> C.accent
                        DayMark.UNDER -> C.muted
                        DayMark.OVER -> C.over
                        DayMark.EMPTY -> C.raised
                        DayMark.FUTURE -> Color.Transparent
                    }
                    Box(
                        Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(5.dp)).background(color)
                            .then(if (day == today) Modifier.background(C.text.copy(alpha = 0.15f)) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 5.dp).height(8.dp).aspectRatio(1f).clip(CircleShape).background(color))
        Text(text, color = C.muted, fontSize = 11.sp)
    }
}
