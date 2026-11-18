package it.pingpro.app.ping

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.system.ErrnoException
import android.util.Log
import it.pingpro.app.data.DeviceRepository
import it.pingpro.app.data.HistoryRepository
import it.pingpro.app.data.SettingsRepository
import it.pingpro.app.data.TestSummary
import it.pingpro.app.service.Alerts
import it.pingpro.app.service.PingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.UUID

data class SessionState(
    val running: Boolean = false,
    val config: PingConfig? = null,
    val deviceName: String? = null,
    val resolvedIp: String? = null,
    val network: NetworkChoice? = null,
    val engine: String? = null,
    val samples: List<PingSample> = emptyList(),
    val events: List<PingEvent> = emptyList(),
    val stats: PingStats = PingStats(),
    val status: LinkStatus = LinkStatus.IDLE,
    val consecutiveLost: Int = 0,
    val outages: Int = 0,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val error: String? = null,
    val lastRecordId: String? = null,
)

/**
 * Esegue i test PING sul telefono. Vive per tutta la durata dell'app (non della schermata),
 * così un test continuativo prosegue anche se si cambia schermata o si blocca il telefono.
 */
class PingController(
    private val context: Context,
    private val history: HistoryRepository,
    private val devices: DeviceRepository,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var cancelled = false
    private val alerts = Alerts(context)

    fun start(config: PingConfig) {
        if (_state.value.running) return
        cancelled = false
        val target = config.target.trim()
        val deviceName = devices.findByAddress(target)?.name
        devices.markUsed(target)
        settings.setLastTarget(target)
        _state.value = SessionState(running = true, config = config.copy(target = target), deviceName = deviceName,
            status = LinkStatus.STARTING, startedAt = System.currentTimeMillis())
        PingService.start(context)
        job = scope.launch { runSession(config.copy(target = target), deviceName) }
    }

    fun stop() {
        cancelled = true
    }

    fun clear() {
        if (!_state.value.running) _state.value = SessionState()
    }

    private suspend fun runSession(config: PingConfig, deviceName: String?) {
        val tracker = SessionTracker(settings.settings.value.highLatencyMs)
        val startedAt = System.currentTimeMillis()
        var pinger: Pinger? = null
        var wifiLock: WifiManager.WifiLock? = null
        var resolved: InetAddress? = null
        var choice: NetworkChoice? = null
        try {
            // 1) Scelta della rete e risoluzione dell'indirizzo.
            val literal = NetworkResolver.isIpv4Literal(config.target) || config.target.contains(':')
            val preliminary = if (literal) runCatching { InetAddress.getByName(config.target) }.getOrNull() else null
            choice = NetworkResolver.choose(context, preliminary)
            resolved = try {
                preliminary ?: NetworkResolver.resolve(config.target, choice.network)
            } catch (e: UnknownHostException) {
                fail(tracker, "Impossibile trovare l'host ${config.target}. Verificare il nome e riprovare.")
                return
            }
            if (preliminary == null) choice = NetworkResolver.choose(context, resolved)
            val ip = resolved.hostAddress ?: config.target

            // 2) Apertura del motore ICMP (socket nativo, con ripiego sul comando di sistema).
            pinger = try {
                IcmpSocketPinger(resolved, choice.network, config.ttl, config.payloadSize)
            } catch (e: Throwable) {
                Log.w(TAG, "Socket ICMP non disponibile, uso ping di sistema", e)
                if (e is ErrnoException || e is SecurityException || e is java.io.IOException)
                    SystemPingPinger(resolved, config.ttl, config.payloadSize)
                else throw e
            }
            wifiLock = acquireWifiLock()

            tracker.addEvent(PingEvent(System.currentTimeMillis(), EventKind.START,
                "Test avviato verso $ip",
                "Rete: ${choice.label} · ${config.payloadSize} byte di dati · ${pinger.engineName}"))
            if (!choice.sameSubnet && preliminary != null && (preliminary.isSiteLocalAddress || preliminary.isLinkLocalAddress)) {
                tracker.addEvent(PingEvent(System.currentTimeMillis(), EventKind.INFO,
                    "Indirizzo fuori dalla rete del telefono",
                    "Il telefono non è collegato alla sottorete di $ip: verificare di essere connessi al Wi-Fi corretto."))
            }
            publish(tracker, config, deviceName, ip, choice, pinger.engineName, startedAt, running = true)

            // 3) Ciclo delle richieste, con la stessa cadenza di Windows.
            var seq = 0
            while (scope.isActive && !cancelled) {
                seq++
                val t0 = System.currentTimeMillis()
                val r = pinger.ping(seq, config.timeoutMs) { cancelled } ?: break
                val sample = PingSample(seq, t0, r.status, r.rttMs, r.ttl, r.bytes, r.message)
                when (tracker.add(sample)) {
                    SessionTracker.Alert.DOWN -> alerts.down(settings.settings.value)
                    SessionTracker.Alert.RECOVERED -> alerts.recovered(settings.settings.value)
                    SessionTracker.Alert.NONE -> Unit
                }
                publish(tracker, config, deviceName, ip, choice, pinger.engineName, startedAt, running = true)
                if (config.mode == PingMode.SINGLE && seq >= config.count) break
                // Windows invia la richiesta successiva circa 1 secondo dopo la precedente;
                // se la risposta non arriva (timeout), la successiva parte subito.
                val next = t0 + config.intervalMs
                while (!cancelled) {
                    val left = next - System.currentTimeMillis()
                    if (left <= 0) break
                    delay(minOf(50L, left))
                }
            }

            // 4) Chiusura e salvataggio nello storico.
            val end = System.currentTimeMillis()
            tracker.addEvent(PingEvent(end, EventKind.STOP,
                if (cancelled) "Test interrotto dall'utente" else "Test completato",
                "${tracker.stats.sent} inviati, ${tracker.stats.received} ricevuti, ${tracker.stats.lost} persi"))
            var recordId: String? = null
            if (tracker.stats.sent > 0) {
                recordId = UUID.randomUUID().toString()
                val st = tracker.stats
                history.save(
                    TestSummary(
                        id = recordId, target = config.target, resolvedIp = ip, deviceName = deviceName,
                        mode = config.mode, count = config.count, payloadSize = config.payloadSize,
                        timeoutMs = config.timeoutMs, ttl = config.ttl, startedAt = startedAt, endedAt = end,
                        sent = st.sent, received = st.received, minMs = st.minMs, avgMs = st.avgMs, maxMs = st.maxMs,
                        jitterMs = st.jitterMs, outages = tracker.outages, network = choice.label, engine = pinger.engineName,
                    ),
                    tracker.samples.toList(), tracker.events.toList(),
                )
            }
            publish(tracker, config, deviceName, ip, choice, pinger.engineName, startedAt, running = false,
                endedAt = end, recordId = recordId)
        } catch (t: Throwable) {
            Log.e(TAG, "Errore durante il test", t)
            fail(tracker, "Impossibile eseguire il test: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            runCatching { pinger?.close() }
            runCatching { if (wifiLock?.isHeld == true) wifiLock.release() }
        }
    }

    private fun fail(tracker: SessionTracker, message: String) {
        tracker.addEvent(PingEvent(System.currentTimeMillis(), EventKind.ERROR, "Errore", message))
        _state.value = _state.value.copy(running = false, status = LinkStatus.ERROR, error = message,
            events = tracker.events.toList(), endedAt = System.currentTimeMillis())
    }

    private fun publish(
        t: SessionTracker, config: PingConfig, deviceName: String?, ip: String, choice: NetworkChoice,
        engine: String, startedAt: Long, running: Boolean, endedAt: Long? = null, recordId: String? = null,
    ) {
        _state.value = SessionState(
            running = running, config = config, deviceName = deviceName, resolvedIp = ip, network = choice,
            engine = engine, samples = t.samples.toList(), events = t.events.toList(), stats = t.stats,
            status = if (running) (if (t.samples.isEmpty()) LinkStatus.STARTING else t.status) else LinkStatus.FINISHED,
            consecutiveLost = t.consecutiveLost, outages = t.outages, startedAt = startedAt, endedAt = endedAt,
            lastRecordId = recordId,
        )
    }

    /** Disattiva il risparmio energetico del Wi-Fi durante il test per misure di latenza più stabili. */
    @Suppress("DEPRECATION")
    private fun acquireWifiLock(): WifiManager.WifiLock? = runCatching {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wm.createWifiLock(mode, "PingPro:test").apply { setReferenceCounted(false); acquire() }
    }.getOrNull()

    companion object { private const val TAG = "PingController" }
}
