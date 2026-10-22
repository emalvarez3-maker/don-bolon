package ec.donbolon.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A radar of the five priorities. Drag (or tap) along an axis to set that value; the
 * corner nearest to the finger's angle is the one that moves.
 */
@Composable
fun Pentagon(values: List<Double>, labels: List<String>, size: Dp = 260.dp, onChange: (Int, Double) -> Unit) {
    val current by rememberUpdatedState(values)
    var active by remember { mutableIntStateOf(-1) }
    val n = values.size
    fun angle(i: Int) = -PI / 2 + i * 2 * PI / n

    fun pick(pos: Offset, w: Float, h: Float, lock: Boolean) {
        val c = Offset(w / 2, h / 2)
        val r = minOf(w, h) / 2 * 0.68f
        val d = pos - c
        val a = atan2(d.y.toDouble(), d.x.toDouble())
        if (!lock || active < 0) {
            active = (0 until n).minBy { i -> abs(((a - angle(i) + 3 * PI) % (2 * PI)) - PI) }
        }
        // Project the finger onto the active axis so sideways wobble doesn't matter.
        val along = d.x * cos(angle(active)) + d.y * sin(angle(active))
        onChange(active, (along / r).coerceIn(0.0, 1.0))
    }

    Canvas(
        Modifier.size(size)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pick(it, this.size.width.toFloat(), this.size.height.toFloat(), lock = false) },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                ) { change, _ -> pick(change.position, this.size.width.toFloat(), this.size.height.toFloat(), lock = true) }
            }
            .pointerInput(Unit) {
                detectTapGestures { pick(it, this.size.width.toFloat(), this.size.height.toFloat(), lock = false); active = -1 }
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
        drawPath(shape, C.honey.copy(alpha = 0.22f))
        drawPath(shape, C.honey, style = Stroke(2.5.dp.toPx()))
        current.forEachIndexed { i, v ->
            drawCircle(if (i == active) C.text else C.honey, (if (i == active) 8 else 6).dp.toPx(), pt(i, v.coerceAtLeast(0.04)))
        }

        // Labels just outside each corner, with the value.
        val paint = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 12.dp.toPx() }
        labels.forEachIndexed { i, label ->
            val q = pt(i, 1.28)
            paint.color = (if (i == active) C.honey else C.muted).toArgb()
            drawContext.canvas.nativeCanvas.drawText(label, q.x, q.y, paint)
            paint.color = C.text.toArgb()
            drawContext.canvas.nativeCanvas.drawText("${(current[i] * 100).toInt()}", q.x, q.y + 15.dp.toPx(), paint)
        }
    }
}
