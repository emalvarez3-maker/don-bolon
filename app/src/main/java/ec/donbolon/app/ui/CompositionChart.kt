package ec.donbolon.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.data.Composition
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val es = Locale.forLanguageTag("es-EC")
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", es)

/** Most bars the chart shows; older measurements stay out so each bar keeps a readable width. */
const val COMPOSITION_MAX_BARS = 6

/** "12 oct": short Spanish date, without the trailing dot some locales add to the month. */
internal fun shortSpanishDate(d: LocalDate): String = d.format(dayMonth).replace(".", "")

/** "−1.8" / "+0.3" / "0.0": a signed kg change, rounded to one decimal before choosing the sign. */
internal fun signedKg(d: Double): String {
    val r = (d * 10).roundToInt() / 10.0
    return when {
        r < 0 -> "−${abs(r).fmt(1)}"
        r > 0 -> "+${r.fmt(1)}"
        else -> 0.0.fmt(1)
    }
}

/**
 * The plain sentence above the bars. With two or more points it compares the first and the last;
 * with one it states the split ("Hoy" when it was measured today, else its date). Null when empty.
 */
internal fun compositionLine(points: List<Composition>, today: LocalDate = LocalDate.now()): String? {
    if (points.isEmpty()) return null
    val last = points.last()
    if (points.size == 1) {
        val whenText = if (last.date == today) "Hoy" else "El ${shortSpanishDate(last.date)}"
        return "$whenText: ${last.fatKg.fmt(1)} kg de grasa (${last.fatPct.roundToInt()} %) y ${last.leanKg.fmt(1)} kg magra"
    }
    val first = points.first()
    return "Desde el ${shortSpanishDate(first.date)}: ${signedKg(last.fatKg - first.fatKg)} kg de grasa, " +
        "${signedKg(last.leanKg - first.leanKg)} kg de músculo"
}

/**
 * "Grasa vs. músculo": one stacked bar per waist measurement (at most the last
 * [COMPOSITION_MAX_BARS]), lean mass at the bottom in sage and fat on top in mustard gold, each
 * segment labelled in kg and the date under the bar. Terracotta stays reserved for going over.
 */
@Composable
fun CompositionChart(points: List<Composition>, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (points.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(C.raised.copy(alpha = 0.5f)).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mide tu cintura para ver cuánto es grasa y cuánto músculo", color = C.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Gap(6.dp)
                    Text("Anótala en «Cintura (cm)», en Tu curva.", color = C.faint, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            return@Column
        }

        val bars = points.takeLast(COMPOSITION_MAX_BARS)
        compositionLine(bars)?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.text) }
        Gap(12.dp)

        val grow = remember { Animatable(0f) }
        LaunchedEffect(bars) { grow.snapTo(0f); grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        val top = bars.maxOf { it.weightKg }.coerceAtLeast(1.0)

        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val g = grow.value
            val labelRoom = 18.dp.toPx()  // dates under the bars
            val headRoom = 18.dp.toPx()   // total weight over the bars
            val base = size.height - labelRoom
            val usable = base - headRoom
            val slot = size.width / bars.size
            val bw = (slot * 0.56f).coerceAtMost(54.dp.toPx())
            val r = 8.dp.toPx()
            val nc = drawContext.canvas.nativeCanvas
            val inside = Paint().apply { isAntiAlias = true; textSize = 11.dp.toPx(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = C.onAccent.toArgb() }
            val head = Paint().apply { isAntiAlias = true; textSize = 10.dp.toPx(); textAlign = Paint.Align.CENTER; color = C.muted.toArgb() }
            val date = Paint().apply { isAntiAlias = true; textSize = 10.dp.toPx(); textAlign = Paint.Align.CENTER; color = C.faint.toArgb() }
            val minInside = 16.dp.toPx()

            // Baseline.
            drawLine(C.line, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())

            bars.forEachIndexed { i, p ->
                val cx = i * slot + slot / 2
                val x0 = cx - bw / 2
                val leanH = (p.leanKg / top * usable).toFloat() * g
                val fatH = (p.fatKg / top * usable).toFloat() * g
                val fatTop = base - leanH - fatH

                // Lean: square top, rounded bottom only where it meets the baseline.
                drawRoundRect(C.accent, Offset(x0, base - leanH), Size(bw, leanH), CornerRadius(r / 3))
                // Fat: rounded cap on top of the lean segment.
                val cap = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = x0, top = fatTop, right = x0 + bw, bottom = base - leanH,
                            topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                        ),
                    )
                }
                drawPath(cap, C.gold)
                // A thin dark seam between the two so they read as separate parts.
                drawLine(C.card, Offset(x0, base - leanH), Offset(x0 + bw, base - leanH), 1.5.dp.toPx())

                if (g > 0.95f) {
                    val textOffset = inside.textSize * 0.35f
                    if (fatH >= minInside) nc.drawText(p.fatKg.fmt(1), cx, fatTop + fatH / 2 + textOffset, inside)
                    if (leanH >= minInside) nc.drawText(p.leanKg.fmt(1), cx, base - leanH / 2 + textOffset, inside)
                    val total = if (fatH >= minInside) "${p.weightKg.fmt(1)} kg" else "${p.fatKg.fmt(1)} grasa"
                    head.color = (if (fatH >= minInside) C.muted else C.gold).toArgb()
                    nc.drawText(total, cx, fatTop - 5.dp.toPx(), head)
                }
                date.color = (if (i == bars.lastIndex) C.text else C.faint).toArgb()
                nc.drawText(shortSpanishDate(p.date), cx, size.height - 3.dp.toPx(), date)
            }
        }
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Swatch(C.gold, "Grasa (kg)")
            Swatch(C.accent, "Masa magra (kg)")
        }
        Text(
            "Estimado con tu cintura y estatura (RFM, Woolcott y Bergman, 2018)",
            color = C.faint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Swatch(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 5.dp).height(9.dp).aspectRatio(1f).clip(CircleShape).background(color))
        Text(text, color = C.muted, fontSize = 11.sp)
    }
}
