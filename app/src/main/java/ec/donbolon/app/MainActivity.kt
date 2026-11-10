package ec.donbolon.app

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ec.donbolon.app.ai.Ai
import ec.donbolon.app.data.Nutrition
import ec.donbolon.app.data.Secrets
import ec.donbolon.app.data.Store
import ec.donbolon.app.ui.BolonApp
import ec.donbolon.app.ui.BolonTheme
import ec.donbolon.app.ui.C
import ec.donbolon.app.ui.External
import ec.donbolon.app.ui.Images

/** Everything the screens need, created once per process. */
class AppModel(activity: FragmentActivity) {
    val store = Store.get(activity)
    val secrets = Secrets(activity.applicationContext)

    fun ai(): Ai {
        val p = store.state.value.settings.provider
        return Ai(p, secrets.get(p))
    }

    fun targets() = Nutrition.targets(store.state.value.profile)
}

class MainActivity : FragmentActivity() {
    private companion object {
        /** Away longer than this re-locks even if the camera or gallery never came back. */
        const val RELOCK_AFTER_MS = 2 * 60 * 1000L
    }

    private lateinit var model: AppModel
    private val locked = mutableStateOf(false)
    private var backgroundAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        // Full-size camera originals from an earlier session are no longer needed.
        if (savedInstanceState == null && !External.open) Thread { Images.clearCamera(applicationContext) }.start()
        model = AppModel(this)
        locked.value = model.store.state.value.settings.lock
        setContent {
            val state by model.store.state.collectAsState()
            val failed by model.store.loadFailed.collectAsState()
            // With the lock on, keep the diary out of the recents thumbnail and screenshots.
            LaunchedEffect(state.settings.lock) {
                if (state.settings.lock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            BolonTheme {
                if (failed) LoadFailed(retry = { model.store.retry() }, startOver = { model.store.startOver() })
                else BolonApp(model, locked.value, ::unlock)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val away = backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt > RELOCK_AFTER_MS
        if (away && model.store.state.value.settings.lock) locked.value = true
        backgroundAt = 0L
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundAt = SystemClock.elapsedRealtime()
        // Re-lock whenever the diary leaves the screen, unless we are only opening the camera.
        if (model.store.state.value.settings.lock && !isChangingConfigurations && !External.open) locked.value = true
    }

    private fun unlock() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked.value = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Don Bolón")
                .setSubtitle("Tu diario es privado")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }
}

/** Shown instead of the app when the diary file exists but could not be opened; nothing is overwritten meanwhile. */
@Composable
private fun LoadFailed(retry: () -> Boolean, startOver: () -> Unit) {
    var tried by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("No pude abrir tu diario", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "Tus datos siguen guardados en una copia. Prueba otra vez; si sigue fallando, puedes empezar de cero y la copia se queda en el teléfono.",
                color = C.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            if (tried) Text("Todavía no se pudo abrir.", color = C.over, fontSize = 13.sp)
            Button(
                onClick = { tried = !retry() },
                Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.onAccent),
            ) { Text("Reintentar", fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = { if (confirm) startOver() else confirm = true }, Modifier.fillMaxWidth()) {
                Text(if (confirm) "Sí, empezar un diario nuevo" else "Empezar de cero", color = C.over)
            }
        }
    }
}
