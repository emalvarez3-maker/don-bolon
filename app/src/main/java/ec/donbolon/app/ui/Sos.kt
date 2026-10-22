package ec.donbolon.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.data.Mood
import kotlinx.coroutines.delay

private val surfPrompts = listOf(
    "Nota el antojo sin obedecerlo. ¿Dónde lo sientes en el cuerpo?",
    "Ponle un número del 1 al 10. Solo obsérvalo.",
    "Ponle nombre: ¿estrés, cansancio, aburrimiento, soledad?",
    "Las olas suben, llegan arriba y bajan. Esta también.",
    "¿Sigue igual de fuerte? Casi siempre ya bajó un poco.",
    "Si comes después, que sea una decisión, no un impulso.",
)

/** Urge surfing (Bowen & Marlatt, 2009): breathe with the circle for three minutes. */
@Composable
fun SosScreen(model: AppModel, onClose: () -> Unit) {
    var seconds by remember { mutableIntStateOf(180) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (seconds > 0) { delay(1000); seconds-- }
        done = true
    }
    // 4 s in, 6 s out: a slow exhale calms the stress response.
    val inhale = (180 - seconds) % 10 < 4
    val grow by animateFloatAsState(if (inhale) 1f else 0.45f, tween(if (inhale) 4000 else 6000), label = "breath")

    Column(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f))
            IconButton(onClose) { Icon(Icons.Filled.Close, "Cerrar", tint = C.muted) }
        }
        if (!done) {
            Text("%d:%02d".format(seconds / 60, seconds % 60), color = C.muted, fontSize = 15.sp)
            Gap(24.dp)
            Box(Modifier.size(240.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Canvas(Modifier.size(240.dp)) {
                    drawCircle(C.honey.copy(alpha = 0.10f), size.minDimension / 2)
                    drawCircle(C.honey.copy(alpha = 0.28f), size.minDimension / 2 * grow)
                }
                Text(if (inhale) "inhala" else "exhala", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Gap(32.dp)
            Text(
                surfPrompts[((180 - seconds) / 30).coerceIn(0, surfPrompts.size - 1)],
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            )
            Box(Modifier.weight(1f))
            Text("Revisa también: ¿tienes hambre, enojo, soledad o cansancio?", color = C.faint, fontSize = 12.sp, textAlign = TextAlign.Center)
        } else {
            Box(Modifier.weight(1f))
            Bolon(Mood.PROUD, 150.dp)
            Gap(16.dp)
            Text("…Hmpf. Lo lograste.", style = MaterialTheme.typography.headlineMedium)
            Gap(8.dp)
            Text(
                "Si todavía quieres comer, hazlo con calma y en plato. Si la ola pasó, eso fue un músculo que entrenaste.",
                color = C.muted, textAlign = TextAlign.Center,
            )
            Box(Modifier.weight(1f))
            PrimaryButton("La ola pasó") { model.store.edit { it.copy(urgesSurfed = it.urgesSurfed + 1) }; onClose() }
            QuietButton("Igual voy a comer, y está bien", color = C.muted, onClick = onClose)
        }
    }
}
