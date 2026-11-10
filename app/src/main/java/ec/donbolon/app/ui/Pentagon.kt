package ec.donbolon.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A radar of the five priorities. Grab a corner's dot and drag it along its axis to set that
 * value. Touches that don't start on a dot pass through, so scrolling over the radar scrolls.
 */
/** How far from a corner's dot a touch still grabs it. */
private val HandleReach = 28.dp

/**
 * The corner whose dot is closest to (x, y) and within [reach] pixels, or -1. Same geometry as
 * the drawing: center of a w×h box, radius 68% of half the short side, first axis pointing up.
 */
fun pentagonHandle(values: List<Double>, x: Float, y: Float, w: Float, h: Float, reach: Float): Int {
    val n = values.size
    if (n == 0) return -1
    val r = minOf(w, h) / 2 * 0.68
    var best = -1
    var bestD = reach.toDouble()
    values.forEachIndexed { i, v ->
        val a = -PI / 2 + i * 2 * PI / n
        val k = v.coerceIn(0.04, 1.0)
        val dx = x - (w / 2 + r * k * cos(a))
        val dy = y - (h / 2 + r * k * sin(a))
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        if (d <= bestD) { bestD = d; best = i }
    }
    return best
}

@Composable
fun Pentagon(values: List<Double>, labels: List<String>, size: Dp = 260.dp, onChange: (Int, Double) -> Unit) {
    val current by rememberUpdatedState(values)
    var active by remember { mutableIntStateOf(-1) }
    val n = values.size
    fun angle(i: Int) = -PI / 2 + i * 2 * PI / n

    fun move(i: Int, pos: Offset, w: Float, h: Float) {
        val r = minOf(w, h) / 2 * 0.68f
        val d = pos - Offset(w / 2, h / 2)
        // Project the finger onto the axis so sideways wobble doesn't matter.
        val along = d.x * cos(angle(i)) + d.y * sin(angle(i))
        onChange(i, (along / r).coerceIn(0.0, 1.0))
    }

    Canvas(
        Modifier.size(size)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = this.size.width.toFloat()
                    val h = this.size.height.toFloat()
                    val i = pentagonHandle(current, down.position.x, down.position.y, w, h, HandleReach.toPx())
                    if (i < 0) return@awaitEachGesture // not on a dot: leave it to the scroll
                    down.consume()
                    active = i
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        move(i, change.position, w, h)
                        change.consume()
                    }
                    active = -1
                }
            },
    ) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2 * 0.68f
        fun pt(i: Int, k: Double) = Offset(c.x + (r * k * cos(angle(i))).toFloat(), c.y + (r * k * sin(angle(i))).toFloat())

        // Grid rings at 25/50/75/100% and the spokes.
        for (ring in 1..4) {
            val path = Path()
            for (i in 0 until n) { val q = pt(i, ring / 4.0); if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y) }
            path.close()
            drawPath(path, C.line, style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) drawLine(C.line, c, pt(i, 1.0), 1.dp.toPx())

        // The user's shape.
        val shape = Path()
        current.forEachIndexed { i, v -> val q = pt(i, v.coerceAtLeast(0.04)); if (i == 0) shape.moveTo(q.x, q.y) else shape.lineTo(q.x, q.y) }
        shape.close()
        drawPath(shape, C.accent.copy(alpha = 0.22f))
        drawPath(shape, C.accent, style = Stroke(2.5.dp.toPx()))
        current.forEachIndexed { i, v ->
            drawCircle(if (i == active) C.text else C.accent, (if (i == active) 8 else 6).dp.toPx(), pt(i, v.coerceAtLeast(0.04)))
        }

        // Labels just outside each corner, with the value.
        val paint = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 12.dp.toPx() }
        labels.forEachIndexed { i, label ->
            val q = pt(i, 1.28)
            paint.color = (if (i == active) C.accent else C.muted).toArgb()
            drawContext.canvas.nativeCanvas.drawText(label, q.x, q.y, paint)
            paint.color = C.text.toArgb()
            drawContext.canvas.nativeCanvas.drawText("${(current[i] * 100).toInt()}", q.x, q.y + 15.dp.toPx(), paint)
        }
    }
}
