package ec.donbolon.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.data.Nutrition
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

@Composable
fun ProgressScreen(model: AppModel) {
    val state by model.store.state.collectAsState()
    val targets = Nutrition.targets(state.profile)
    val today = LocalDate.now()
    var weightText by remember { mutableStateOf("") }
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val kcals = days.map { state.totalsOn(it).kcal }
    val logged = days.filter { state.mealsOn(it).isNotEmpty() }
    val trend = Nutrition.trend(state.weights)

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            Text("Progreso", style = MaterialTheme.typography.headlineMedium)
            Gap(18.dp)

            state.focus?.let { f ->
                Panel {
                    Label("Enfoque de la semana")
                    Gap(6.dp)
                    Text(f.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = C.gold)
                    Gap(4.dp)
                    Text(f.plan, fontSize = 14.sp)
                    Gap(6.dp)
                    Text(f.review, color = C.muted, fontSize = 12.sp)
                }
                Gap(12.dp)
            }

            Panel {
                Label("Últimos 7 días")
                Gap(14.dp)
                val top = max(kcals.maxOrNull() ?: 0.0, targets.kcal * 1.2)
                Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                    val slot = size.width / 7
                    val bw = slot * 0.5f
                    kcals.forEachIndexed { i, k ->
                        val h = (k / top * size.height).toFloat()
                        val color = when {
                            k == 0.0 -> C.raised
                            k > targets.kcal * 1.1 -> C.over
                            else -> C.accent
                        }
                        drawRoundRect(color, Offset(i * slot + (slot - bw) / 2, size.height - max(h, 4f)), Size(bw, max(h, 4f)), CornerRadius(6f, 6f))
                    }
                    val y = (size.height - targets.kcal / top * size.height).toFloat()
                    drawLine(C.muted, Offset(0f, y), Offset(size.width, y), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                }
                Gap(6.dp)
                Row {
                    days.forEach {
                        Text(
                            it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.forLanguageTag("es")).uppercase(),
                            Modifier.weight(1f), color = if (it == today) C.text else C.faint, fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                Gap(14.dp)
                Row {
                    val avg = logged.map { state.totalsOn(it).kcal }.average().takeIf { !it.isNaN() } ?: 0.0
                    Mini("${avg.fmt()}", "kcal promedio", Modifier.weight(1f))
                    Mini("${logged.count { Nutrition.onTarget(state.totalsOn(it).kcal, targets.kcal, targets.band) }}/7", "días en rango", Modifier.weight(1f))
                    Mini("${logged.count { state.totalsOn(it).protein >= targets.protein * 0.9 }}/7", "días con proteína", Modifier.weight(1f))
                }
            }
            Gap(12.dp)

            Panel {
                Label("Peso")
                Gap(10.dp)
                if (trend.size >= 2) {
                    val first = trend.first()
                    val last = trend.last()
                    val weeks = max(ChronoUnit.DAYS.between(first.first, last.first), 1) / 7.0
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(last.second.fmt(1), style = MaterialTheme.typography.headlineMedium)
                        Text("  kg tendencia", color = C.muted, modifier = Modifier.padding(bottom = 4.dp))
                        Box(Modifier.weight(1f))
                        val rate = (last.second - first.second) / weeks
                        Text("${if (rate >= 0) "+" else ""}${rate.fmt(2)} kg/sem", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Gap(10.dp)
                    WeightChart(state.weights.map { it.date to it.kg }, trend)
                    Gap(6.dp)
                    Text("Los puntos son el peso del día (agua incluida); la línea es tu peso real.", color = C.faint, fontSize = 12.sp)
                } else {
                    Text("Pésate en ayunas 2–3 veces por semana. Con dos registros dibujo tu tendencia.", color = C.muted, fontSize = 14.sp)
                }
                Gap(12.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(weightText, { weightText = it }, "Peso de hoy (kg)", Modifier.weight(1f), number = true)
                    HGap(8.dp)
                    QuietButton("Guardar") {
                        weightText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..300.0 }?.let { model.store.logWeight(it); weightText = "" }
                    }
                }
            }
            Gap(12.dp)

            Panel {
                Label("Hábito de 66 días")
                Gap(6.dp)
                val start = state.meals.minOfOrNull { it.date } ?: today
                val loggedDays = state.loggedDays
                Text("${loggedDays.count { it >= start && it < start.plusDays(66) }} de 66 días registrados", fontWeight = FontWeight.Medium)
                Text("Un día perdido no rompe nada. Dos seguidos sí empiezan a pesar.", color = C.muted, fontSize = 12.sp)
                Gap(12.dp)
                for (row in 0 until 6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        for (col in 0 until 11) {
                            val d = start.plusDays((row * 11 + col).toLong())
                            Dot(
                                when {
                                    d in loggedDays -> C.gold
                                    d.isAfter(today) -> C.raised.copy(alpha = 0.5f)
                                    else -> C.line
                                },
                                18.dp,
                            )
                        }
                    }
                }
            }
            Gap(24.dp)
        }
    }
}

@Composable
private fun Mini(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(label, color = C.muted, fontSize = 11.sp)
    }
}

@Composable
private fun WeightChart(raw: List<Pair<LocalDate, Double>>, trend: List<Pair<LocalDate, Double>>) {
    val recent = raw.takeLast(60)
    val from = recent.first().first
    val span = max(ChronoUnit.DAYS.between(from, recent.last().first), 1).toFloat()
    val all = recent.map { it.second } + trend.takeLast(60).map { it.second }
    val lo = all.min() - 0.5
    val hi = all.max() + 0.5
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        fun x(d: LocalDate) = ChronoUnit.DAYS.between(from, d) / span * size.width
        fun y(kg: Double) = ((hi - kg) / (hi - lo) * size.height).toFloat()
        recent.forEach { (d, kg) -> drawCircle(C.muted.copy(alpha = 0.6f), 3.5f, Offset(x(d), y(kg))) }
        val path = Path()
        trend.filter { it.first >= from }.forEachIndexed { i, (d, kg) -> if (i == 0) path.moveTo(x(d), y(kg)) else path.lineTo(x(d), y(kg)) }
        drawPath(path, C.accent, style = Stroke(4f, cap = StrokeCap.Round))
    }
}
