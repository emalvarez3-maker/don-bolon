package ec.donbolon.app.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.ai.Ai
import ec.donbolon.app.data.Mood
import ec.donbolon.app.data.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import ec.donbolon.app.ai.AiException
import kotlinx.coroutines.launch

/**
 * Connects the AI that recognizes the food. The key is tried with a tiny request before it is
 * saved (encrypted), so a typo shows up here and not in the middle of logging a meal.
 */
@Composable
fun ConnectAi(model: AppModel, onConnected: () -> Unit) {
    val uri = LocalUriHandler.current
    var provider by remember { mutableStateOf(Provider.GEMINI) }
    var key by remember { mutableStateOf("") }
    var busy by KeyCheck.busy
    var error by KeyCheck.error

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Bolon(if (busy) Mood.THINKING else Mood.GRUMPY, 56.dp, talking = busy)
            HGap(10.dp)
            Column(Modifier.weight(1f)) {
                Text("Conecta mi cerebro", fontWeight = FontWeight.SemiBold)
                Text("Para reconocer todo lo que comes en la foto necesito IA. Es gratis con Google.", color = C.muted, fontSize = 13.sp)
            }
        }
        Gap(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Gemini (gratis)", provider == Provider.GEMINI) { provider = Provider.GEMINI; error = null }
            Chip("Claude", provider == Provider.CLAUDE) { provider = Provider.CLAUDE; error = null }
        }
        Gap(10.dp)
        Text(
            if (provider == Provider.GEMINI) "1. Abre Google AI Studio  2. Toca \"Create API key\"  3. Cópiala y pégala aquí."
            else "1. Abre la consola de Anthropic  2. Crea una API key  3. Cópiala y pégala aquí.",
            color = C.muted, fontSize = 12.sp,
        )
        QuietButton(if (provider == Provider.GEMINI) "Abrir Google AI Studio ↗" else "Abrir consola de Anthropic ↗") {
            uri.openUri(if (provider == Provider.GEMINI) "https://aistudio.google.com/apikey" else "https://console.anthropic.com/settings/keys")
        }
        fun connect() {
            if (busy || key.length < 20) return
            // Copying from a web page often brings spaces, quotes or a label along: keep only the key itself.
            val clean = cleanKey(provider, key)
            val chosen = provider
            busy = true
            error = null
            // A fingerprint of the key (first 3 characters, length, any odd characters), never the key itself:
            // enough to tell a truncated or wrong paste from a key Google really rejects.
            val odd = clean.filterNot { c -> c.isLetterOrDigit() || c in "_-." }.toSet().joinToString("")
            val shape = "empieza con \"${clean.take(3)}…\", ${clean.length} caracteres" + if (odd.isNotEmpty()) ", caracteres raros: $odd" else ""
            Log.i("DonBolon", "AI connect: testing ${chosen.name} key ($shape)")
            // The test outlives this card: leaving for AI Studio or the keyboard redrawing the screen must not
            // cancel it halfway and leave "Probando…" with nothing saved.
            KeyCheck.scope.launch {
                val started = System.currentTimeMillis()
                val test = async(Dispatchers.IO) { runCatching { Ai(chosen, clean).check() } }
                val ok = withTimeoutOrNull(40_000) { test.await() }
                    ?: Result.failure(AiException("Google tardó más de 40 segundos en contestar. Revisa tu internet e intenta otra vez."))
                busy = false
                ok.onSuccess {
                    model.secrets.set(chosen, clean)
                    Ai.keyRejected = false
                    model.store.edit { it.copy(settings = it.settings.copy(provider = chosen)) }
                    Log.i("DonBolon", "AI connect: ${chosen.name} key works (${System.currentTimeMillis() - started} ms)")
                    onConnected()
                }.onFailure {
                    Log.w("DonBolon", "AI connect failed (${chosen.name}, $shape): ${it.javaClass.simpleName}: ${it.message}")
                    error = (it.message ?: "No pude conectar.") + "\n\nLo que pegaste: $shape."
                }
            }
        }
        OutlinedTextField(
            key,
            { pasted ->
                val before = key
                key = pasted.trim(); error = null
                // A whole key arriving at once is a paste: connect right away, no second tap needed.
                if (key.length - before.length >= 20 && cleanKey(provider, key).let { looksComplete(provider, it) }) connect()
            },
            Modifier.fillMaxWidth(),
            label = { Text("Pega tu llave aquí") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { connect() }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.accent, unfocusedBorderColor = C.line, focusedLabelColor = C.accent, cursorColor = C.accent),
        )
        error?.let {
            Gap(8.dp)
            Text(
                "⚠ $it", color = C.over, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.over.copy(alpha = 0.12f)).padding(10.dp),
            )
        }
        Gap(10.dp)
        PrimaryButton(if (busy) "Probando…" else "Conectar", enabled = key.length >= 20 && !busy) { connect() }
        Text("Se guarda cifrada en tu teléfono y nunca sale de él, salvo hacia el proveedor cuando analizas una comida.", color = C.faint, fontSize = 11.sp)
    }
}

/** The key test in flight, kept outside the card so it survives the card being redrawn or reopened. */
private object KeyCheck {
    val scope = MainScope()
    val busy = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
}

/**
 * Pulls the actual key out of whatever was pasted. Gemini keys come in two formats (the classic
 * "AIza…" and the newer "AQ.…"), Anthropic's start with "sk-ant-". Clipboards sometimes add
 * spaces, line breaks or quotes, so those go first.
 */
fun cleanKey(provider: Provider, pasted: String): String {
    val compact = pasted.replace(Regex("\\s+"), "").trim('"', '\'')
    val pattern = if (provider == Provider.GEMINI) Regex("AIza[0-9A-Za-z_-]{30,}|AQ\\.[0-9A-Za-z_.-]{20,}") else Regex("sk-ant-[0-9A-Za-z_-]{20,}")
    return pattern.find(compact)?.value ?: compact
}

/** True when [clean] has the full shape of a key, so a paste can connect without a tap. */
fun looksComplete(provider: Provider, clean: String): Boolean =
    if (provider == Provider.GEMINI) Regex("AIza[0-9A-Za-z_-]{35}|AQ\\.[0-9A-Za-z_.-]{30,}").matches(clean)
    else Regex("sk-ant-[0-9A-Za-z_-]{40,}").matches(clean)
