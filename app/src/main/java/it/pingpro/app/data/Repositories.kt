package it.pingpro.app.data

import android.content.Context
import android.content.SharedPreferences
import it.pingpro.app.ping.PingEvent
import it.pingpro.app.ping.PingSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

class DeviceRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("devices", Context.MODE_PRIVATE)
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<SavedDevice>> = _devices.asStateFlow()

    private fun load(): List<SavedDevice> = runCatching {
        val a = JSONArray(prefs.getString("list", "[]"))
        (0 until a.length()).map { SavedDevice.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun persist(list: List<SavedDevice>) {
        _devices.value = list
        val a = JSONArray(); list.forEach { a.put(it.toJson()) }
        prefs.edit().putString("list", a.toString()).apply()
    }

    fun upsert(id: String?, name: String, address: String, note: String): SavedDevice {
        val current = _devices.value
        val existing = current.firstOrNull { it.id == id }
        val dev = existing?.copy(name = name.trim(), address = address.trim(), note = note.trim())
            ?: SavedDevice(UUID.randomUUID().toString(), name.trim(), address.trim(), note.trim())
        persist(if (existing != null) current.map { if (it.id == dev.id) dev else it } else current + dev)
        return dev
    }

    fun delete(id: String) = persist(_devices.value.filterNot { it.id == id })

    fun markUsed(address: String) {
        val now = System.currentTimeMillis()
        if (_devices.value.none { it.address.equals(address, true) }) return
        persist(_devices.value.map { if (it.address.equals(address, true)) it.copy(lastUsedAt = now) else it })
    }

    fun findByAddress(address: String): SavedDevice? =
        _devices.value.firstOrNull { it.address.equals(address.trim(), ignoreCase = true) }
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load() = AppSettings(
        count = prefs.getInt("count", 4),
        payloadSize = prefs.getInt("payloadSize", 32),
        timeoutMs = prefs.getInt("timeoutMs", 4000),
        ttl = prefs.getInt("ttl", 128),
        highLatencyMs = prefs.getInt("highLatencyMs", 100),
        vibrateOnAlert = prefs.getBoolean("vibrate", true),
        soundOnAlert = prefs.getBoolean("sound", false),
        keepScreenOn = prefs.getBoolean("keepScreenOn", true),
        onboardingDone = prefs.getBoolean("onboardingDone", false),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        val s = _settings.value
        prefs.edit()
            .putInt("count", s.count).putInt("payloadSize", s.payloadSize).putInt("timeoutMs", s.timeoutMs)
            .putInt("ttl", s.ttl).putInt("highLatencyMs", s.highLatencyMs)
            .putBoolean("vibrate", s.vibrateOnAlert).putBoolean("sound", s.soundOnAlert)
            .putBoolean("keepScreenOn", s.keepScreenOn).putBoolean("onboardingDone", s.onboardingDone)
            .apply()
    }

    fun lastTarget(): String = prefs.getString("lastTarget", "") ?: ""
    fun setLastTarget(t: String) = prefs.edit().putString("lastTarget", t).apply()
}

/** Storico dei test: un indice con i riepiloghi e un file separato per i dettagli di ogni test. */
class HistoryRepository(context: Context) {
    private val dir = File(context.filesDir, "history").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<TestSummary>>(emptyList())
    val items: StateFlow<List<TestSummary>> = _items.asStateFlow()

    init {
        scope.launch {
            _items.value = runCatching {
                val a = JSONArray(indexFile.readText())
                (0 until a.length()).map { TestSummary.fromJson(a.getJSONObject(it)) }
            }.getOrDefault(emptyList()).sortedByDescending { it.startedAt }
        }
    }

    @Synchronized
    private fun writeIndex(list: List<TestSummary>) {
        val a = JSONArray(); list.forEach { a.put(it.toJson()) }
        val tmp = File(dir, "index.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(indexFile)
    }

    fun save(summary: TestSummary, samples: List<PingSample>, events: List<PingEvent>) {
        scope.launch {
            File(dir, "${summary.id}.json").writeText(DetailCodec.encode(samples, events))
            _items.update { (listOf(summary) + it.filterNot { s -> s.id == summary.id }).take(MAX_ITEMS) }
            val kept = _items.value
            writeIndex(kept)
            // Pulizia dei dettagli dei test eliminati per limite di spazio.
            val ids = kept.map { it.id }.toSet()
            dir.listFiles()?.forEach { f ->
                val id = f.name.removeSuffix(".json")
                if (f.name.endsWith(".json") && f != indexFile && id !in ids) f.delete()
            }
        }
    }

    suspend fun detail(id: String): TestDetail? = withContext(Dispatchers.IO) {
        val summary = _items.value.firstOrNull { it.id == id } ?: return@withContext null
        val (samples, events) = runCatching { DetailCodec.decode(File(dir, "$id.json").readText()) }
            .getOrDefault(emptyList<PingSample>() to emptyList())
        TestDetail(summary, samples, events)
    }

    fun delete(id: String) {
        _items.update { it.filterNot { s -> s.id == id } }
        scope.launch { File(dir, "$id.json").delete(); writeIndex(_items.value) }
    }

    fun clear() {
        _items.value = emptyList()
        scope.launch { dir.listFiles()?.forEach { it.delete() }; writeIndex(emptyList()) }
    }

    companion object { const val MAX_ITEMS = 200 }
}
