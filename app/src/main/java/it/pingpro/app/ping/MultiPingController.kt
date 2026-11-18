package it.pingpro.app.ping

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import it.pingpro.app.data.DeviceRepository
import it.pingpro.app.data.HistoryRepository
import it.pingpro.app.data.MultiTarget
import it.pingpro.app.data.SettingsRepository
import it.pingpro.app.data.TestSummary
import it.pingpro.app.service.Alerts
import it.pingpro.app.service.PingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.util.UUID

/** Stato di una singola scheda all'interno del test di più schede. */
data class TargetState(
    val target: MultiTarget,
    val name: String?,
    val ip: String? = null,
    val status: LinkStatus = LinkStatus.STARTING,
    val stats: PingStats = PingStats(),
    /** Ultimi pacchetti (per mini-grafici e dettagli). */
    val recent: List<PingSample> = emptyList(),
    val consecutiveLost: Int = 0,
    val outages: Int = 0,
    val events: List<PingEvent> = emptyList(),
    val network: NetworkChoice? = null,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: target.address

    /** Esito sintetico usato per colori e conteggi. */
    val health: Health
        get() = when {
            error != null -> Health.DOWN
            stats.sent == 0 -> Health.WAITING
            finished -> when {
                stats.received == 0 -> Health.DOWN
                stats.lost > 0 -> Health.WARN
                else -> Health.OK
            }
            status == LinkStatus.DOWN -> Health.DOWN
            status == LinkStatus.UNSTABLE || status == LinkStatus.SLOW -> Health.WARN
            else -> Health.OK
        }

    enum class Health { OK, WARN, DOWN, WAITING }
}

data class MultiState(
    val running: Boolean = false,
    val mode: PingMode = PingMode.SINGLE,
    val count: Int = PingConfig.WINDOWS_COUNT,
    val groupName: String? = null,
    val targets: List<TargetState> = emptyList(),
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val batchId: String? = null,
) {
    val okCount get() = targets.count { it.health == TargetState.Health.OK }
    val warnCount get() = targets.count { it.health == TargetState.Health.WARN }
    val downCount get() = targets.count { it.health == TargetState.Health.DOWN }
    val waitingCount get() = targets.count { it.health == TargetState.Health.WAITING }
    val isIdle get() = targets.isEmpty()
}

/**
 * Esegue il PING di più schede contemporaneamente: ogni scheda ha il proprio socket e la propria
 * cadenza (circa 1 richiesta al secondo, come Windows), come se fossero aperte più finestre «ping».
 */
class MultiPingController(
    private val context: Context,
    private val history: HistoryRepository,
    private val devices: DeviceRepository,
    private val settings: SettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(TargetParser.MAX_TARGETS + 8)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(MultiState())
    val state: StateFlow<MultiState> = _state.asStateFlow()

    @Volatile private var cancelled = false
    private var job: Job? = null
    private val alerts = Alerts(context)
    private var lastAlert = 0L

    fun start(targets: List<MultiTarget>, mode: PingMode, groupName: String?) {
        if (_state.value.running || targets.isEmpty()) return
        cancelled = false
        val s = settings.settings.value
        val cfgFor = { t: MultiTarget -> PingConfig(t.address, mode, s.count, s.payloadSize, s.timeoutMs, s.ttl) }
        val list = targets.take(TargetParser.MAX_TARGETS)
        val names = list.map { it.name?.takeIf { n -> n.isNotBlank() } ?: devices.findByAddress(it.address)?.name }
        val slots = Array(list.size) { TargetState(list[it], names[it]) }
        val trackers = arrayOfNulls<SessionTracker>(list.size)
        val start = System.currentTimeMillis()
        val batchId = UUID.randomUUID().toString()
        val lock = Any()
        val dirty = java.util.concurrent.atomic.AtomicBoolean(true)

        _state.value = MultiState(true, mode, s.count, groupName, slots.toList(), start, null, batchId)
        PingService.start(context)

        job = scope.launch {
            val wifiLock: WifiManager.WifiLock? = PingEngine.acquireWifiLock(context)
            val publisher = launch {
                while (isActive) {
                    if (dirty.getAndSet(false)) {
                        val snapshot = synchronized(lock) { slots.toList() }
                        _state.value = _state.value.copy(targets = snapshot)
                    }
                    delay(250)
                }
            }
            val n = list.size
            val workers = list.mapIndexed { i, t ->
                launch(io) {
                    // Partenze sfalsate nel secondo per non inviare tutte le richieste nello stesso istante.
                    delay(i * 1000L / n)
                    runTarget(i, cfgFor(t), names[i], s.highLatencyMs, slots, trackers, lock) { dirty.set(true) }
                }
            }
            workers.joinAll()
            publisher.cancel()
            runCatching { if (wifiLock?.isHeld == true) wifiLock.release() }

            val end = System.currentTimeMillis()
            val finalSlots = synchronized(lock) { slots.map { it.copy(finished = true) } }
            val label = groupName ?: "${n} schede"
            finalSlots.forEachIndexed { i, ts ->
                val tr = trackers[i] ?: return@forEachIndexed
                val st = tr.stats
                if (st.sent == 0) return@forEachIndexed
                val cfg = cfgFor(ts.target)
                history.save(
                    TestSummary(
                        id = UUID.randomUUID().toString(), target = ts.target.address, resolvedIp = ts.ip ?: ts.target.address,
                        deviceName = ts.name, mode = mode, count = cfg.count, payloadSize = cfg.payloadSize, timeoutMs = cfg.timeoutMs,
                        ttl = cfg.ttl, startedAt = start, endedAt = end, sent = st.sent, received = st.received, minMs = st.minMs,
                        avgMs = st.avgMs, maxMs = st.maxMs, jitterMs = st.jitterMs, outages = tr.outages, network = ts.network?.label,
                        engine = "Più schede", batchId = batchId, batchName = label,
                    ),
                    tr.samples.toList(), tr.events.toList(),
                )
            }
            _state.value = _state.value.copy(running = false, targets = finalSlots, endedAt = end)
        }
    }

    private suspend fun runTarget(
        i: Int, config: PingConfig, name: String?, highLatency: Int,
        slots: Array<TargetState>, trackers: Array<SessionTracker?>, lock: Any, markDirty: () -> Unit,
    ) {
        val tracker = SessionTracker(highLatency)
        trackers[i] = tracker
        var pinger: Pinger? = null
        try {
            val r = try {
                PingEngine.resolve(context, config.target)
            } catch (e: UnknownHostException) {
                synchronized(lock) { slots[i] = slots[i].copy(status = LinkStatus.ERROR, error = PingEngine.unknownHostMessage(config.target), finished = true) }
                markDirty(); return
            }
            pinger = PingEngine.openPinger(r, config.ttl, config.payloadSize)
            tracker.addEvent(PingEvent(System.currentTimeMillis(), EventKind.START, "Test avviato verso ${r.ip}", "Rete: ${r.network.label}"))
            if (r.outsidePhoneNetwork) tracker.addEvent(PingEvent(System.currentTimeMillis(), EventKind.INFO,
                "Indirizzo fuori dalla rete del telefono", "Verificare l'indirizzo e il Wi-Fi a cui è collegato il telefono."))
            synchronized(lock) { slots[i] = slots[i].copy(ip = r.ip, network = r.network, events = tracker.events.toList()) }
            markDirty()

            var seq = 0
            while (!cancelled) {
                seq++
                val t0 = System.currentTimeMillis()
                val res = pinger.ping(seq, config.timeoutMs) { cancelled } ?: break
                val alert = tracker.add(PingSample(seq, t0, res.status, res.rttMs, res.ttl, res.bytes, res.message))
                if (alert != SessionTracker.Alert.NONE) alert(alert)
                synchronized(lock) {
                    slots[i] = slots[i].copy(
                        status = tracker.status, stats = tracker.stats, recent = tracker.samples.takeLast(RECENT),
                        consecutiveLost = tracker.consecutiveLost, outages = tracker.outages, events = tracker.events.toList(),
                    )
                }
                markDirty()
                if (config.mode == PingMode.SINGLE && seq >= config.count) break
                val next = t0 + config.intervalMs
                while (!cancelled) {
                    val left = next - System.currentTimeMillis()
                    if (left <= 0) break
                    delay(minOf(50L, left))
                }
            }
            tracker.addEvent(PingEvent(System.currentTimeMillis(), EventKind.STOP,
                if (cancelled) "Test interrotto dall'utente" else "Test completato",
                "${tracker.stats.sent} inviati, ${tracker.stats.received} ricevuti, ${tracker.stats.lost} persi"))
            synchronized(lock) { slots[i] = slots[i].copy(events = tracker.events.toList(), finished = true) }
            markDirty()
        } catch (t: Throwable) {
            Log.e("MultiPing", "Errore su ${config.target}", t)
            synchronized(lock) { slots[i] = slots[i].copy(status = LinkStatus.ERROR, error = "Errore: ${t.message ?: t.javaClass.simpleName}", finished = true) }
            markDirty()
        } finally {
            runCatching { pinger?.close() }
        }
    }

    /** Un solo avviso ogni pochi secondi, anche se molte schede cambiano stato insieme (es. apparato spento). */
    @Synchronized
    private fun alert(a: SessionTracker.Alert) {
        val now = System.currentTimeMillis()
        if (now - lastAlert < 3000) return
        lastAlert = now
        if (a == SessionTracker.Alert.DOWN) alerts.down(settings.settings.value) else alerts.recovered(settings.settings.value)
    }

    fun stop() { cancelled = true }

    fun clear() { if (!_state.value.running) _state.value = MultiState() }

    companion object { const val RECENT = 60 }
}
