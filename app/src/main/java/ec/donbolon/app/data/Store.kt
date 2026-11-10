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
import org.json.JSONArray
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

        class Parsed(val state: AppState, val dropped: Int)

        /**
         * Reads the diary JSON so one malformed record doesn't sink the whole file: if the full parse
         * fails, every element of every list or map is tried on its own and the bad ones are dropped.
         * Throws only when the text is not a JSON object at all.
         */
        fun parse(json: String): Parsed {
            val o = JSONObject(json)
            runCatching { AppState.from(o) }.getOrNull()?.let { return Parsed(it, 0) }
            var dropped = 0
            val clean = JSONObject()
            fun fits(key: String, value: Any) = runCatching { AppState.from(JSONObject().put(key, value)) }.isSuccess
            for (key in o.keys().asSequence().toList()) {
                when (val v = o.opt(key)) {
                    is JSONArray -> {
                        val kept = JSONArray()
                        for (i in 0 until v.length()) {
                            val el = v.opt(i)
                            if (el != null && fits(key, JSONArray().put(el))) kept.put(el) else dropped++
                        }
                        clean.put(key, kept)
                    }
                    is JSONObject -> if (fits(key, v)) clean.put(key, v) else {
                        // A map by day (water, waist, menu): keep the entries that read, drop the rest.
                        val part = JSONObject()
                        for (k in v.keys().asSequence().toList()) {
                            if (fits(key, JSONObject().put(k, v.opt(k)))) part.put(k, v.opt(k)) else dropped++
                        }
                        if (part.length() > 0 && fits(key, part)) clean.put(key, part)
                    }
                    else -> if (v != null && fits(key, v)) clean.put(key, v) else dropped++
                }
            }
            return Parsed(AppState.from(clean), dropped)
        }
    }

    private val file = File(context.filesDir, "diario.bin")
    private val backup = File(context.filesDir, "diario.bad")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _loadFailed = MutableStateFlow(false)

    /** True when diario.bin exists but could not be read: nothing is written until [retry] works or [startOver]. */
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()
    val photos = File(context.filesDir, "fotos").apply { mkdirs() }

    private fun load(): AppState {
        if (!file.exists()) return AppState().also { _loadFailed.value = false }
        return runCatching { parse(String(Vault.open(file.readBytes()))) }.fold(
            onSuccess = { p ->
                // Some records were unreadable: keep the original before the next save drops them.
                if (p.dropped > 0) keepBackup()
                _loadFailed.value = false
                p.state
            },
            onFailure = {
                keepBackup()
                _loadFailed.value = true
                AppState()
            },
        )
    }

    /** Copies the diary aside once; an existing diario.bad is never overwritten. */
    private fun keepBackup() {
        if (!backup.exists() && file.exists()) runCatching { file.copyTo(backup) }
    }

    /** Tries to read the diary again (after a Keystore hiccup, say). True when it opened. */
    fun retry(): Boolean {
        val s = load()
        if (!_loadFailed.value) _state.value = s
        return !_loadFailed.value
    }

    /** The user chose to start a new diary; the unreadable one stays in diario.bad. */
    fun startOver() {
        keepBackup()
        _loadFailed.value = false
        edit { AppState() }
    }

    fun edit(change: (AppState) -> AppState) {
        // Never overwrite a diary we couldn't read with an empty one.
        if (_loadFailed.value) return
        _state.update(change)
        scope.launch {
            writeLock.withLock {
                // Always write the newest state: saves can start out of order, the file must not go back in time.
                if (!_loadFailed.value) write()
            }
        }
    }

    /** Writes the current state on this thread; for broadcast receivers that may be killed right after. */
    fun saveNow() = kotlinx.coroutines.runBlocking {
        writeLock.withLock { if (!_loadFailed.value) write() }
    }

    private fun write() {
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(Vault.seal(_state.value.toJson().toString().toByteArray()))
        tmp.renameTo(file)
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

    fun logWaist(cm: Double, day: LocalDate = LocalDate.now()) = edit { s -> s.copy(waist = s.waist + (day to cm)) }

    fun wipe() {
        photos.listFiles()?.forEach { it.delete() }
        ec.donbolon.app.ui.Images.clearCamera(context)
        edit { AppState(settings = it.settings) }
    }
}
