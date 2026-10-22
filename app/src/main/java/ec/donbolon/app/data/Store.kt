package ec.donbolon.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that lives in the Android Keystore and never leaves it.
 * Everything the app writes to disk (diary, API keys) goes through here.
 */
object Vault {
    private const val ALIAS = "don-bolon-vault"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun seal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return byteArrayOf(c.iv.size.toByte()) + c.iv + c.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        val ivLen = sealed[0].toInt()
        val iv = sealed.copyOfRange(1, 1 + ivLen)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return c.doFinal(sealed, 1 + ivLen, sealed.size - 1 - ivLen)
    }
}

/** API keys, encrypted at rest and kept apart from the diary. */
class Secrets(context: Context) {
    private val dir = File(context.filesDir, "secrets").apply { mkdirs() }

    fun get(p: Provider): String = runCatching { String(Vault.open(File(dir, p.name).readBytes())) }.getOrDefault("")

    fun set(p: Provider, value: String) {
        val f = File(dir, p.name)
        if (value.isBlank()) f.delete() else f.writeBytes(Vault.seal(value.trim().toByteArray()))
    }
}

/**
 * The whole diary in one encrypted JSON file; small enough to rewrite on every change.
 * One instance per process ([get]) so the app and the notification actions share it.
 */
class Store private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }
    }

    private val file = File(context.filesDir, "diario.bin")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()
    val photos = File(context.filesDir, "fotos").apply { mkdirs() }

    private fun load(): AppState = runCatching {
        if (!file.exists()) AppState() else AppState.from(JSONObject(String(Vault.open(file.readBytes()))))
    }.getOrElse { AppState() }

    fun edit(change: (AppState) -> AppState) {
        _state.update(change)
        scope.launch {
            writeLock.withLock {
                // Always write the newest state: saves can start out of order, the file must not go back in time.
                val tmp = File(file.path + ".tmp")
                tmp.writeBytes(Vault.seal(_state.value.toJson().toString().toByteArray()))
                tmp.renameTo(file)
            }
        }
    }

    /** Writes the current state on this thread; for broadcast receivers that may be killed right after. */
    fun saveNow() = kotlinx.coroutines.runBlocking {
        writeLock.withLock {
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(Vault.seal(_state.value.toJson().toString().toByteArray()))
            tmp.renameTo(file)
        }
    }

    fun addMeal(m: Meal) = edit { it.copy(meals = it.meals + m) }

    fun deleteMeal(id: String) {
        val meal = state.value.meals.firstOrNull { it.id == id } ?: return
        meal.photo?.let { File(photos, it).delete() }
        edit { s ->
            // A meal logged from the daily menu goes back to "not eaten" there.
            val menu = s.menu[meal.date]?.map { if (it.eaten && it.slot == meal.slot && it.title == meal.note) it.copy(eaten = false) else it }
            s.copy(meals = s.meals.filterNot { it.id == id }, menu = if (menu == null) s.menu else s.menu + (meal.date to menu))
        }
    }

    fun addWater(day: LocalDate, delta: Int) = edit { s ->
        s.copy(water = s.water + (day to ((s.water[day] ?: 0) + delta).coerceIn(0, 20)))
    }

    fun logWeight(kg: Double, day: LocalDate = LocalDate.now()) = edit { s ->
        val weights = (s.weights.filterNot { it.date == day } + WeightEntry(day, kg)).sortedBy { it.date }
        s.copy(weights = weights, profile = s.profile.copy(weightKg = weights.last().kg))
    }

    fun wipe() {
        photos.listFiles()?.forEach { it.delete() }
        edit { AppState(settings = it.settings) }
    }
}
