package ec.donbolon.app.ui

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.data.DayMark
import ec.donbolon.app.data.Nutrition
import ec.donbolon.app.data.Point
import ec.donbolon.app.data.Progress
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val es = Locale.forLanguageTag("es-EC")
private val shortDate = DateTimeFormatter.ofPattern("d MMM", es)
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", es)

private enum class Metric(val label: String, val unit: String) { PESO("Peso", "kg"), CINTURA("Cintura", "cm"), GRASA("Grasa estimada", "kg") }
private enum class Range(val label: String, val days: Long?) { MES("1 mes", 30), TRIMESTRE("3 meses", 90), TODO("Todo", null) }

@Composable
fun ProgressScreen(model: AppModel) {
    val state by model.store.state.collectAsState()
    val targets = Nutrition.targets(state.profile)
    val today = LocalDate.now()
    var metric by remember { mutableStateOf(Metric.PESO) }
    var range by remember { mutableStateOf(Range.TRIMESTRE) }
    var weightText by remember { mutableStateOf("") }
    var waistText by remember { mutableStateOf("") }
    var goalText by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf<String?>(null) }

    val trend = Nutrition.trend(state.weights).map { Point(it.first, it.second) }
    val projection = remember(state.weights, state.profile) { Progress.projection(state) }
    val fat = remember(state.meals.size, state.weights) { Progress.fatCurve(state, 120) }
    val week = Progress.weekStats(state)

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            Text("Progreso", style = MaterialTheme.typography.headlineMedium)
            Gap(16.dp)

            state.focus?.let { f ->
                Panel(padding = 16.dp) {
                    Label("Enfoque de la semana")
                    Gap(4.dp)
                    Text(f.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = C.gold)
                    Gap(2.dp)
                    Text(f.plan, fontSize = 14.sp)
                }
                Gap(12.dp)
            }

            // --- The curve ---------------------------------------------------------------------
            Panel(padding = 16.dp) {
                Label("Tu curva")
                Gap(10.dp)
                val start = state.weights.firstOrNull()?.kg ?: state.profile.weightKg
                val now = Progress.currentWeight(state)
                val goal = state.profile.goalWeightKg
                Row {
                    BigStat("Inicio", "${start.fmt(1)}", "kg", C.muted)
                    BigStat("Ahora", "${now.fmt(1)}", "kg", C.text, delta = now - start)
                    BigStat("Meta", goal?.fmt(1) ?: "—", if (goal != null) "kg" else "", C.gold)
                }
                Gap(14.dp)
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
                            CurveChart(raw, cut(trend), proj, goal, Metric.PESO.unit)
                        }
                        Gap(10.dp)
                        val reach = projection.reachesGoal
                        Text(
                            when {
                                goal == null -> "Ponte un peso meta y te digo cuándo llegas a este ritmo."
                                reach != null -> "Si mantienes tus ${targets.kcal} kcal, llegas a ${goal.fmt(1)} kg ${Progress.humanWeeks(today, reach)} (${reach.format(monthYear)})."
                                else -> "Con tus calorías actuales no llegas a ${goal.fmt(1)} kg: revisa tus prioridades en Perfil."
                            },
                            fontSize = 14.sp,
                        )
                        Text("La línea punteada se va aplanando: al pesar menos gastas menos, así que la misma dieta baja más lento. Es normal.", color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        if (goal == null) {
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
                    }
                    Metric.CINTURA -> {
                        val pts = cut(state.waist.entries.sortedBy { it.key }.map { Point(it.key, it.value) })
                        if (pts.size < 2) EmptyCurve("Mide tu cintura a la altura del ombligo, relajado, una vez por semana. Si la cintura baja aunque el peso no se mueva, estás ganando músculo y perdiendo grasa.")
                        else {
                            CurveChart(pts, pts, emptyList(), null, Metric.CINTURA.unit)
                            Gap(8.dp)
                            val d = pts.last().value - pts.first().value
                            Text("${if (d <= 0) "−" else "+"}${abs(d).fmt(1)} cm desde el ${pts.first().date.format(shortDate)}", fontSize = 14.sp)
                        }
                    }
                    Metric.GRASA -> {
                        val pts = cut(fat)
                        if (pts.size < 2) EmptyCurve("Registra tus comidas algunos días y te muestro cuánta grasa vas perdiendo según tu déficit.")
                        else {
                            CurveChart(pts, pts, emptyList(), null, Metric.GRASA.unit, area = true)
                            Gap(8.dp)
                            val k = pts.last().value
                            Text(
                                if (k >= 0) "≈ ${k.fmt(2)} kg de grasa menos en ${pts.size} días registrados." else "≈ ${(-k).fmt(2)} kg ganados en ${pts.size} días registrados (superávit).",
                                fontSize = 14.sp,
                            )
                            Text("Estimado con tu gasto diario y lo que registraste (7 700 kcal ≈ 1 kg de grasa). Solo cuenta los días con registro.", color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Gap(14.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(weightText, { weightText = it; saved = null }, "Peso hoy (kg)", Modifier.weight(1f), number = true)
                    HGap(8.dp)
                    Field(waistText, { waistText = it; saved = null }, "Cintura (cm)", Modifier.weight(1f), number = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    saved?.let { Text(it, color = C.good, fontSize = 13.sp, modifier = Modifier.weight(1f)) } ?: Box(Modifier.weight(1f))
                    QuietButton("Guardar medidas") {
                        val kg = weightText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..300.0 }
                        val cm = waistText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 40.0..200.0 }
                        kg?.let { model.store.logWeight(it) }
                        cm?.let { model.store.logWaist(it) }
                        saved = if (kg == null && cm == null) null else "✓ Guardado"
                        if (kg != null) weightText = ""
                        if (cm != null) waistText = ""
                    }
                }
            }
            Gap(12.dp)

            // --- The week ----------------------------------------------------------------------
            Panel(padding = 16.dp) {
                Label("Tu semana")
                Gap(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeekTile("${week.avgKcal.fmt()}", "kcal promedio", (week.avgKcal / targets.kcal).toFloat(), if (week.avgKcal > targets.kcal * (1 + targets.band)) C.over else C.accent)
                    WeekTile("${week.inRange}/7", "días en rango", week.inRange / 7f, C.accent)
                }
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeekTile("${week.proteinDays}/7", "días con proteína", week.proteinDays / 7f, C.protein)
                    WeekTile("${week.trained}/${state.profile.trainDays}", "entrenos", week.trained / state.profile.trainDays.toFloat(), C.gold)
                }
                Gap(14.dp)
                WeekBars(state, targets.kcal, targets.band)
            }
            Gap(12.dp)

            // --- Consistency calendar ----------------------------------------------------------
            Panel(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Constancia", Modifier.weight(1f))
                    Text("🔥 ${Nutrition.streak(state.loggedDays)} días · meta 66", color = C.muted, fontSize = 12.sp)
                }
                Gap(10.dp)
                Calendar(state, today)
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Legend(C.accent, "En rango"); Legend(C.water, "Debajo"); Legend(C.over, "Arriba"); Legend(C.raised, "Sin registro")
                }
            }
            Gap(24.dp)
        }
    }
}

@Composable
private fun RowScope.BigStat(label: String, value: String, unit: String, color: Color, delta: Double? = null) {
    Column(Modifier.weight(1f)) {
        Text(label, color = C.muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color)
            if (unit.isNotEmpty()) Text(" $unit", color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        delta?.takeIf { abs(it) >= 0.05 }?.let {
            Text("${if (it < 0) "−" else "+"}${abs(it).fmt(1)} kg", color = if (it < 0) C.good else C.over, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
 * and the goal as a horizontal line. Touch and slide to read any day.
 */
@Composable
private fun CurveChart(raw: List<Point>, line: List<Point>, projection: List<Point>, goal: Double?, unit: String, area: Boolean = false) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    val all = raw + line + projection
    if (all.isEmpty()) return
    val first = all.minOf { it.date }
    val last = all.maxOf { it.date }.let { if (it == first) it.plusDays(1) else it }
    val values = all.map { it.value } + listOfNotNull(goal)
    val pad = max((values.max() - values.min()) * 0.15, 0.4)
    val lo = values.min() - pad
    val hi = values.max() + pad
    val span = ChronoUnit.DAYS.between(first, last).toFloat()
    val today = LocalDate.now()
    val grow by animateFloatAsState(1f, tween(800), label = "grow")

    Column {
        Box(
            Modifier.fillMaxWidth().height(190.dp)
                .pointerInput(all) { detectTapGestures { touchX = it.x } }
                .pointerInput(all) { detectDragGestures(onDragEnd = { touchX = null }, onDragCancel = { touchX = null }) { c, _ -> touchX = c.position.x } },
        ) {
            Canvas(Modifier.fillMaxWidth().height(190.dp)) {
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

                if (area && line.size >= 2) {
                    val fill = Path().apply {
                        moveTo(x(line.first().date), y(0.0.coerceIn(lo, hi)))
                        line.forEach { lineTo(x(it.date), y(it.value)) }
                        lineTo(x(line.last().date), y(0.0.coerceIn(lo, hi)))
                        close()
                    }
                    drawPath(fill, C.accent.copy(alpha = 0.18f * grow))
                }
                raw.forEach { drawCircle(C.muted.copy(alpha = 0.55f), 3.5.dp.toPx(), Offset(x(it.date), y(it.value))) }
                if (line.size >= 2) {
                    val p = Path()
                    line.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(pt.date), y(pt.value)) else p.lineTo(x(pt.date), y(pt.value)) }
                    drawPath(p, C.accent, style = Stroke(3.dp.toPx() * grow, cap = StrokeCap.Round))
                }
                if (projection.size >= 2) {
                    val p = Path()
                    projection.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(pt.date), y(pt.value)) else p.lineTo(x(pt.date), y(pt.value)) }
                    drawPath(p, C.gold, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
                    drawCircle(C.gold, 4.dp.toPx(), Offset(x(projection.last().date), y(projection.last().value)))
                }

                // Scrubbing: the nearest point to the finger, with its value.
                touchX?.let { tx ->
                    val near = all.minBy { abs(x(it.date) - tx) }
                    val px = x(near.date)
                    drawLine(C.text.copy(alpha = 0.5f), Offset(px, top), Offset(px, bottom), 1.5f)
                    drawCircle(C.text, 5.dp.toPx(), Offset(px, y(near.value)))
                    val tip = "${near.value.fmt(1)} $unit · ${near.date.format(shortDate)}${if (near.date.isAfter(today)) " (proyección)" else ""}"
                    val tp = Paint().apply { isAntiAlias = true; textSize = 12.dp.toPx(); color = C.text.toArgb(); isFakeBoldText = true }
                    val w = tp.measureText(tip) + 16.dp.toPx()
                    val bx = (px - w / 2).coerceIn(left, right - w)
                    drawRoundRect(C.raised, Offset(bx, top), Size(w, 22.dp.toPx()), CornerRadius(8.dp.toPx()))
                    drawContext.canvas.nativeCanvas.drawText(tip, bx + 8.dp.toPx(), top + 15.dp.toPx(), tp)
                }
            }
        }
        Gap(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (raw.isNotEmpty() && raw != line) Legend(C.muted, "Medido")
            if (line.size >= 2) Legend(C.accent, if (raw != line) "Tendencia" else "Curva")
            if (projection.size >= 2) Legend(C.gold, "Hacia dónde vas")
        }
        Text("Toca o desliza el dedo sobre la curva para ver cada día.", color = C.faint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
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

/** Seven bars, the target as a dashed line, and the tallest day named. */
@Composable
private fun WeekBars(state: ec.donbolon.app.data.AppState, target: Int, band: Double) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val kcals = days.map { state.totalsOn(it).kcal }
    val top = max(kcals.maxOrNull() ?: 0.0, target * 1.25)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val slot = size.width / 7
        val bw = slot * 0.46f
        kcals.forEachIndexed { i, k ->
            val h = max((k / top * size.height).toFloat(), 4f)
            val color = when {
                k == 0.0 -> C.raised
                k > target * (1 + band) -> C.over
                k < target * (1 - band) -> C.water
                else -> C.accent
            }
            drawRoundRect(color, Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(6f, 6f))
        }
        val y = (size.height - target / top * size.height).toFloat()
        drawLine(C.gold.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    }
    Gap(4.dp)
    Row {
        days.forEach {
            Text(
                it.dayOfWeek.getDisplayName(TextStyle.NARROW, es).uppercase(), Modifier.weight(1f),
                color = if (it == today) C.text else C.faint, fontSize = 11.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

/** The last ten weeks as a grid, Monday on top, each day colored by how it went. */
@Composable
private fun Calendar(state: ec.donbolon.app.data.AppState, today: LocalDate) {
    val weeks = 10
    val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) { Text(it, color = C.faint, fontSize = 10.sp) } }
        }
        for (w in 0 until weeks) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (d in 0 until 7) {
                    val day = firstMonday.plusDays((w * 7 + d).toLong())
                    val color = when (Progress.mark(state, day, today)) {
                        DayMark.IN_RANGE -> C.accent
                        DayMark.UNDER -> C.water
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
