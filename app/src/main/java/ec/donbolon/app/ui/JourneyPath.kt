package ec.donbolon.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.data.Journey
import ec.donbolon.app.data.Mood
import ec.donbolon.app.data.Progress
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val PathHeight = 176.dp
private val MascotSize = 46.dp

/** The plain-Spanish summary above the path. */
internal fun journeyLine(j: Journey): String {
    val more = if (j.gaining) "más" else "menos"
    return when {
        j.leftKg < 0.05 -> "¡Llegaste a tu meta de ${j.goalKg.fmt(1)} kg!"
        j.lostKg < 0.05 -> "Estás en el inicio · te faltan ${j.leftKg.fmt(1)} kg"
        else -> "Llevas ${j.lostKg.fmt(1)} kg $more · te faltan ${j.leftKg.fmt(1)} kg"
    }
}

/**
 * "Tu camino": a winding road from "Inicio" to a little flag at "Meta". The walked part is
 * solid sage, the rest dashed; milestone dots glow gold once passed (they pop as Don Bolón
 * walks past them on first composition) and Don Bolón stands where the user is now.
 */
@Composable
fun JourneyPath(j: Journey, reachesGoal: LocalDate?, modifier: Modifier = Modifier) {
    val walk = remember { Animatable(0f) }
    LaunchedEffect(j.fraction) {
        walk.animateTo(j.fraction.toFloat(), tween(1400, delayMillis = 150, easing = FastOutSlowInEasing))
    }
    val today = LocalDate.now()

    Column(modifier) {
        Text(journeyLine(j), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.text)
        if (reachesGoal != null && j.leftKg >= 0.05) {
            Text("a este ritmo llegas ${Progress.humanWeeks(today, reachesGoal)}", fontSize = 13.sp, color = C.muted)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(PathHeight)) {
            val density = LocalDensity.current
            val w = with(density) { maxWidth.toPx() }
            val h = with(density) { PathHeight.toPx() }
            val road = remember(w, h) { Road(w, h, with(density) { 26.dp.toPx() }, with(density) { 54.dp.toPx() }, with(density) { 30.dp.toPx() }) }
            val milestones = j.milestones.dropLast(1).map { j.at(it.kg).toFloat() to it }

            Canvas(Modifier.fillMaxWidth().height(PathHeight)) {
                val now = walk.value
                val width = 7.dp.toPx()

                // The road bed: a soft wide band under the whole way.
                drawPath(road.path(0f, 1f), C.raised.copy(alpha = 0.7f), style = Stroke(width * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Still to walk: dashed.
                drawPath(
                    road.path(now, 1f), C.faint.copy(alpha = 0.9f),
                    style = Stroke(width * 0.45f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 7.dp.toPx()))),
                )
                // Already walked: solid accent with a faint glow.
                if (now > 0.002f) {
                    val walked = road.path(0f, now)
                    drawPath(walked, C.accent.copy(alpha = 0.18f), style = Stroke(width * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(walked, C.accent, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                // Milestones: gold and glowing once passed, with a little pop as Bolón goes by.
                milestones.forEach { (t, m) ->
                    val c = road.at(t)
                    val lit = m.reached && now >= t - 0.001f
                    if (lit) {
                        val s = ((now - t) / 0.06f).coerceIn(0f, 1f)
                        val pop = 1f + 0.45f * sin(PI.toFloat() * s)
                        val r = 5.dp.toPx() * pop
                        drawCircle(Brush.radialGradient(listOf(C.gold.copy(alpha = 0.55f), Color.Transparent), c, r * 3.2f), r * 3.2f, c)
                        drawCircle(C.gold, r, c)
                        drawCircle(Color.White.copy(alpha = 0.6f), r * 0.35f, c + Offset(-r * 0.3f, -r * 0.3f))
                    } else {
                        drawCircle(C.card, 4.5.dp.toPx(), c)
                        drawCircle(C.faint, 4.5.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
                    }
                }

                // Start: a small post; goal: a flag (gold once reached).
                val start = road.at(0f)
                drawCircle(C.muted, 5.dp.toPx(), start)
                drawCircle(C.card, 2.dp.toPx(), start)
                drawFlag(road.at(1f), if (j.leftKg < 0.05 && now >= 0.999f) C.gold else C.gold.copy(alpha = 0.75f))

                val label = Paint().apply { isAntiAlias = true; textSize = 11.dp.toPx(); color = C.muted.toArgb(); typeface = Typeface.DEFAULT_BOLD }
                val sub = Paint().apply { isAntiAlias = true; textSize = 10.dp.toPx(); color = C.faint.toArgb() }
                val nc = drawContext.canvas.nativeCanvas
                nc.drawText("Inicio", start.x - 8.dp.toPx(), start.y + 20.dp.toPx(), label)
                nc.drawText("${j.startKg.fmt(1)} kg", start.x - 8.dp.toPx(), start.y + 33.dp.toPx(), sub)
                val goal = road.at(1f)
                label.textAlign = Paint.Align.RIGHT; sub.textAlign = Paint.Align.RIGHT
                label.color = C.gold.toArgb()
                nc.drawText("Meta", goal.x + 10.dp.toPx(), goal.y + 20.dp.toPx(), label)
                nc.drawText("${j.goalKg.fmt(1)} kg", goal.x + 10.dp.toPx(), goal.y + 33.dp.toPx(), sub)
            }

            // Don Bolón standing on the road where the user is.
            val here = road.at(walk.value)
            val sizePx = with(density) { MascotSize.toPx() }
            Box(Modifier.offset { IntOffset((here.x - sizePx / 2).roundToInt(), (here.y - sizePx * 0.9f).roundToInt()) }) {
                Bolon(if (j.fraction >= 0.5) Mood.HAPPY else Mood.CALM, MascotSize)
            }
        }
    }
}

/**
 * A gentle S from the bottom-left to the top-right, sampled as a polyline so any stretch of it
 * (walked / still to walk) can be cut by fraction. [side] is the horizontal margin, [top] the
 * room kept above for the mascot and flag, [bottom] the room below for the labels.
 */
private class Road(w: Float, h: Float, side: Float, top: Float, bottom: Float) {
    private val n = 120
    private val pts: List<Offset>
    private val cum: FloatArray

    init {
        val x0 = side; val x1 = w - side
        val yLow = h - bottom; val yHigh = top
        pts = (0..n).map { i ->
            val t = i / n.toFloat()
            val rise = t * t * (3 - 2 * t) // smoothstep, so it starts and ends almost flat
            val wave = 0.22f * (yLow - yHigh) * sin(2 * PI.toFloat() * t)
            Offset(x0 + (x1 - x0) * t, yLow - (yLow - yHigh) * rise + wave)
        }
        cum = FloatArray(n + 1)
        for (i in 1..n) cum[i] = cum[i - 1] + (pts[i] - pts[i - 1]).getDistance()
    }

    /** The point at [f] of the road's length (0..1), so the mascot walks at an even pace. */
    fun at(f: Float): Offset {
        val target = f.coerceIn(0f, 1f) * cum[n]
        var i = 1
        while (i < n && cum[i] < target) i++
        val seg = cum[i] - cum[i - 1]
        val k = if (seg <= 0f) 0f else (target - cum[i - 1]) / seg
        return pts[i - 1] + (pts[i] - pts[i - 1]) * k
    }

    fun path(from: Float, to: Float): Path {
        val p = Path()
        val a = from.coerceIn(0f, 1f); val b = to.coerceIn(0f, 1f)
        if (b <= a) return p
        val start = at(a)
        p.moveTo(start.x, start.y)
        val total = cum[n]
        for (i in 1 until n) {
            val d = cum[i] / total
            if (d > a && d < b) p.lineTo(pts[i].x, pts[i].y)
        }
        val end = at(b)
        p.lineTo(end.x, end.y)
        return p
    }
}

private fun DrawScope.drawFlag(base: Offset, color: Color) {
    val pole = 30.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.3f), 4.dp.toPx(), base + Offset(0f, 2.dp.toPx()))
    drawLine(C.text.copy(alpha = 0.85f), base, base - Offset(0f, pole), 2.dp.toPx(), StrokeCap.Round)
    val top = base - Offset(0f, pole)
    val cloth = Path().apply {
        moveTo(top.x, top.y)
        quadraticTo(top.x + 9.dp.toPx(), top.y + 1.dp.toPx(), top.x + 17.dp.toPx(), top.y + 5.dp.toPx())
        lineTo(top.x, top.y + 12.dp.toPx())
        close()
    }
    drawPath(cloth, color)
    drawCircle(C.gold, 2.dp.toPx(), top)
}
