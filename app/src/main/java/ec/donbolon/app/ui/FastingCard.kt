package ec.donbolon.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.data.AppState
import ec.donbolon.app.data.Fast
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val hm = DateTimeFormatter.ofPattern("HH:mm")

/** Fasting or eating, with a ring that fills as the fast (or the window) goes by. Ticks every 30 s. */
@Composable
fun FastingCard(state: AppState) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    val f = Fast.state(state, now) ?: return
    val p = state.profile
    val total = if (f.eating) p.fasting.windowHours * 60.0 else p.fasting.fastHours * 60.0
    val done = if (f.eating) total - f.minutesToChange else (f.hoursFasted * 60).coerceAtMost(total)
    val frac by animateFloatAsState((done / total).toFloat().coerceIn(0f, 1f), tween(700), label = "fast")
    val color = if (f.eating) C.accent else C.water

    Panel(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(58.dp)) {
                    val w = 6.dp.toPx()
                    drawCircle(C.raised, size.minDimension / 2 - w / 2, style = Stroke(w))
                    drawArc(color, -90f, 360f * frac, false, style = Stroke(w, cap = StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w))
                }
                Text(if (f.eating) "🍽️" else "🌙", fontSize = 20.sp)
            }
            HGap(14.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    if (f.eating) "Ventana abierta" else "Ayunando · ${Fast.format((f.hoursFasted * 60).toLong())}",
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = color,
                )
                Text(
                    if (f.eating) "Cierra a las ${f.closes.format(hm)} · quedan ${Fast.format(f.minutesToChange)}"
                    else "Tu ventana abre a las ${f.opens.format(hm)} · en ${Fast.format(f.minutesToChange)}",
                    color = C.muted, fontSize = 13.sp,
                )
                Text("${p.fasting.label} · agua, café negro y té no lo rompen", color = C.faint, fontSize = 11.sp)
            }
        }
    }
}
