package ec.donbolon.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.data.Hand
import ec.donbolon.app.data.Ruler
import kotlin.math.roundToInt

/** Longest hand measure that can be typed in, and the shortest. */
const val HAND_MAX_CM = 25.0
const val HAND_MIN_CM = 4.0

/** How far above or below the line a touch still grabs it, and how wide the grip zone is. */
private val GripReach = 28.dp
private val GripZoneWidth = 96.dp

/** A typed hand measure in cm ("11,5" or "11.5"), rounded to one decimal, or null outside 4–25 cm. */
fun parseHandCm(text: String): Double? {
    val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    val cm = (v * 10).roundToInt() / 10.0
    return cm.takeIf { it in HAND_MIN_CM..HAND_MAX_CM }
}

/** Whether a touch at (x, y) lands on the grip: near the line vertically and on the right edge. */
fun onGrip(x: Float, y: Float, lineY: Float, width: Float, reach: Float, zone: Float): Boolean =
    kotlin.math.abs(y - lineY) <= reach && x >= width - zone

private class HandStep(val title: String, val how: String, val start: (Hand) -> Double)

private val steps = listOf(
    HandStep(
        "Ancho de la palma",
        "Pon tu palma sobre la regla, de lado a lado: el borde del meñique en la línea 0 de arriba. " +
            "Arrastra la línea verde desde su agarradera hasta el borde del índice, justo en los nudillos.",
    ) { it.palmWidthCm },
    HandStep(
        "Largo del dedo medio",
        "Apoya el dedo medio a lo largo de la regla, con el pliegue donde nace el dedo en la línea 0. " +
            "Arrastra la línea hasta la punta del dedo.",
    ) { it.middleFingerCm },
    HandStep(
        "Ancho del pulgar",
        "Acuesta el pulgar de lado sobre la regla, con un borde en la línea 0. " +
            "Arrastra la línea hasta el otro borde, en la parte más ancha.",
    ) { it.thumbWidthCm },
)

/**
 * Measures the person's hand once against a ruler drawn at true size, so later photos can use
 * the hand as a scale. The ruler runs down the long side of the screen and uses the panel's
 * real vertical density ([android.util.DisplayMetrics.ydpi]), so a centimeter on screen is a
 * centimeter on the table.
 */
@Composable
fun HandSetup(current: Hand?, onSave: (Hand) -> Unit, onCancel: () -> Unit) {
    val ydpi = LocalContext.current.resources.displayMetrics.ydpi
    val start = current ?: Hand.typical
    val values = remember { mutableStateListOf(*steps.map { it.start(start) }.toTypedArray()) }
    var step by remember { mutableIntStateOf(0) }
    var areaPx by remember { mutableFloatStateOf(0f) }
    // Read live (not captured), so the gesture loop always knows the ruler's real length.
    fun visibleMax() = if (areaPx > 0f) Ruler.pxToCm(areaPx, ydpi) else 15.0

    fun set(cm: Double) {
        // A hand typed in beyond the ruler can still be nudged with ±, up to the typed range.
        val cap = if (values[step] > visibleMax() + 0.05) HAND_MAX_CM else visibleMax()
        values[step] = (cm * 10).roundToInt().coerceIn(5, (cap * 10).toInt().coerceAtLeast(5)) / 10.0
    }
    val atEdge = values[step] >= visibleMax() - 0.05
    var typed by remember(step) { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(C.bg)) {
        Box(
            Modifier.fillMaxWidth().weight(1f).statusBarsPadding()
                .onSizeChanged { areaPx = it.height.toFloat() }
                .pointerInput(step, ydpi) {
                    // Only a finger that lands on the grip moves the line; the palm lying on the
                    // ruler (or any other finger) is ignored, however many pointers are down.
                    awaitPointerEventScope {
                        var dragId: PointerId? = null
                        var grab = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                if (dragId == null && c.changedToDown()) {
                                    val lineY = Ruler.cmToPx(values[step], ydpi)
                                    if (onGrip(c.position.x, c.position.y, lineY, size.width.toFloat(), GripReach.toPx(), GripZoneWidth.toPx())) {
                                        dragId = c.id
                                        grab = c.position.y - lineY
                                        c.consume()
                                    }
                                } else if (c.id == dragId) {
                                    if (c.pressed) set(Ruler.pxToCm(c.position.y - grab, ydpi)) else dragId = null
                                    c.consume()
                                }
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRuler(ydpi)
                drawHandle(Ruler.cmToPx(values[step], ydpi))
            }
        }

        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
            Panel(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Paso ${step + 1} de ${steps.size}")
                    Box(Modifier.weight(1f))
                    Text("${"%.1f".format(values[step])} cm", color = C.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Gap(4.dp)
                Text(steps[step].title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Gap(6.dp)
                Text(steps[step].how, color = C.muted, fontSize = 13.sp, lineHeight = 18.sp)
                if (atEdge || typed.isNotEmpty()) {
                    Gap(10.dp)
                    Text("Tu mano es más larga que la regla: escribe la medida", color = C.text, fontSize = 13.sp)
                    Gap(6.dp)
                    Field(
                        typed,
                        { t -> typed = t; parseHandCm(t)?.let { values[step] = it } },
                        "Medida en cm", number = true, placeholder = "Entre 4 y 25, ej. 11,5",
                    )
                }
                Gap(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stepper(onMinus = { set(values[step] - 0.1) }, onPlus = { set(values[step] + 0.1) }) {
                        Text("1 mm", color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    Box(Modifier.weight(1f))
                    QuietButton(if (step == 0) "Cancelar" else "Atrás", color = C.muted) {
                        if (step == 0) onCancel() else step--
                    }
                }
                Gap(8.dp)
                if (step < steps.lastIndex) {
                    PrimaryButton("Siguiente") { step++ }
                } else {
                    PrimaryButton("Guardar mi mano") { onSave(Hand(values[0], values[1], values[2])) }
                }
            }
        }
    }
}

/** True centimeter ticks down the left edge: long at each cm, medium at 5 mm, short at each mm. */
private fun DrawScope.drawRuler(dpi: Float) {
    val mmPx = Ruler.cmToPx(0.1, dpi)
    if (mmPx <= 0f) return
    val stripW = 64.dp.toPx()
    drawRect(C.card, size = Size(stripW, size.height))
    drawLine(C.line, Offset(stripW, 0f), Offset(stripW, size.height), 1.dp.toPx())
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = C.text.toArgb()
        textSize = 12.sp.toPx()
    }
    var mm = 0
    while (true) {
        val y = mm * mmPx
        if (y > size.height) break
        val (len, color, w) = when {
            mm % 10 == 0 -> Triple(28.dp.toPx(), C.text, 2.dp.toPx())
            mm % 5 == 0 -> Triple(18.dp.toPx(), C.muted, 1.5.dp.toPx())
            else -> Triple(10.dp.toPx(), C.faint, 1.dp.toPx())
        }
        drawLine(color, Offset(0f, y), Offset(len, y), w)
        if (mm % 10 == 0 && mm > 0) {
            drawContext.canvas.nativeCanvas.drawText("${mm / 10}", 34.dp.toPx(), y + 4.sp.toPx(), paint)
        }
        mm++
    }
    // The zero line runs across the screen so the edge of the hand has something to sit on.
    drawLine(C.accent, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx())
}

/** The line the person drags to the far edge, with a grip on the right. */
private fun DrawScope.drawHandle(y: Float) {
    drawRect(C.accent.copy(alpha = 0.10f), Offset(0f, 0f), Size(size.width, y))
    drawLine(C.accent, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), cap = StrokeCap.Round)
    val gripW = 56.dp.toPx()
    val gripH = 22.dp.toPx()
    drawRoundRect(
        C.accent, Offset(size.width - gripW - 12.dp.toPx(), y - gripH / 2), Size(gripW, gripH),
        CornerRadius(gripH / 2),
    )
    for (i in -1..1) {
        val x = size.width - 12.dp.toPx() - gripW / 2 + i * 8.dp.toPx()
        drawLine(C.onAccent, Offset(x, y - 5.dp.toPx()), Offset(x, y + 5.dp.toPx()), 1.5.dp.toPx())
    }
}
