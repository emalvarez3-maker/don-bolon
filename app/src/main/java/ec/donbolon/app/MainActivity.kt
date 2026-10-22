package ec.donbolon.app

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ec.donbolon.app.ai.Ai
import ec.donbolon.app.data.Nutrition
import ec.donbolon.app.data.Secrets
import ec.donbolon.app.data.Store
import ec.donbolon.app.ui.BolonApp
import ec.donbolon.app.ui.BolonTheme
import ec.donbolon.app.ui.External

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
    private lateinit var model: AppModel
    private val locked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        model = AppModel(this)
        locked.value = model.store.state.value.settings.lock
        setContent {
            BolonTheme { BolonApp(model, locked.value, ::unlock) }
        }
    }

    override fun onStop() {
        super.onStop()
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
