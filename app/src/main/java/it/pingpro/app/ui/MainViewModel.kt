package it.pingpro.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import it.pingpro.app.PingApp
import it.pingpro.app.data.AppSettings
import it.pingpro.app.data.SavedDevice
import it.pingpro.app.data.TestDetail
import it.pingpro.app.data.TestSummary
import it.pingpro.app.ping.NetworkResolver
import it.pingpro.app.ping.PingConfig
import it.pingpro.app.ping.PingMode

/** Stato e azioni della schermata Test (interfaccia separata per poterla visualizzare in anteprima). */
interface TestUi {
    var target: String
    var mode: PingMode
    val targetValid: Boolean
    fun buildConfig(): PingConfig
    fun start()
    fun stop()
    fun deviceFor(address: String): SavedDevice?
}

class MainViewModel(app: Application) : AndroidViewModel(app), TestUi {
    private val pa = app as PingApp
    val session = pa.controller.state
    val devices = pa.devices.devices
    val history = pa.history.items
    val settings = pa.settings.settings
    val network = pa.network.interfaces

    fun refreshNetwork() = pa.network.refresh()

    /** Avvia subito un test verso un indirizzo (es. il gateway) senza doverlo scrivere. */
    fun pingNow(address: String) {
        if (session.value.running) return
        target = address
        start()
    }

    override var target by mutableStateOf(pa.settings.lastTarget().ifBlank { pa.network.primaryLan?.gateway ?: "" })
    override var mode by mutableStateOf(PingMode.SINGLE)

    override val targetValid: Boolean get() = NetworkResolver.isValidTarget(target)

    override fun buildConfig(): PingConfig = buildConfig(target, mode)

    fun buildConfig(t: String, m: PingMode): PingConfig {
        val s = settings.value
        return PingConfig(target = t.trim(), mode = m, count = s.count, payloadSize = s.payloadSize, timeoutMs = s.timeoutMs, ttl = s.ttl)
    }

    override fun start() {
        if (!targetValid) return
        pa.controller.start(buildConfig())
    }

    /** Controllo rapido per la diagnosi: test singolo di 4 richieste con i parametri di Windows. */
    fun runCheck(address: String): Boolean {
        if (session.value.running) return false
        pa.controller.start(PingConfig(address.trim(), PingMode.SINGLE))
        return true
    }

    override fun stop() = pa.controller.stop()

    fun repeat(s: TestSummary) {
        target = s.target
        mode = s.mode
        pa.controller.start(PingConfig(s.target, s.mode, s.count, s.payloadSize, s.timeoutMs, s.ttl))
    }

    fun saveDevice(id: String?, name: String, address: String, note: String) = pa.devices.upsert(id, name, address, note)
    fun deleteDevice(id: String) = pa.devices.delete(id)
    override fun deviceFor(address: String): SavedDevice? = pa.devices.findByAddress(address)

    suspend fun detail(id: String): TestDetail? = pa.history.detail(id)
    fun deleteRecord(id: String) = pa.history.delete(id)
    fun clearHistory() = pa.history.clear()

    fun updateSettings(t: (AppSettings) -> AppSettings) = pa.settings.update(t)
}
