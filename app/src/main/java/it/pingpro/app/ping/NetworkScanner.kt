package it.pingpro.app.ping

import android.content.Context
import android.net.DnsResolver
import android.net.Network
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Un dispositivo che ha risposto durante la ricerca. */
data class FoundDevice(
    val address: String,
    val rttMs: Double,
    val ttl: Int?,
    val name: String? = null,
    val isGateway: Boolean = false,
    val isPhone: Boolean = false,
)

data class ScanState(
    val running: Boolean = false,
    val from: String = "",
    val to: String = "",
    val total: Int = 0,
    val done: Int = 0,
    val found: List<FoundDevice> = emptyList(),
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val cancelled: Boolean = false,
    val error: String? = null,
) {
    val progress: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/**
 * "Cerca dispositivi": controlla UNA volta ogni indirizzo di un intervallo e restituisce chi risponde.
 * A differenza del test continuo non resta attivo, non salva nello storico e limita le richieste
 * contemporanee, per non caricare la rete (le richieste verso indirizzi vuoti generano traffico
 * broadcast ARP).
 */
class NetworkScanner(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()
    @Volatile private var cancelled = false
    private var job: Job? = null
    private val dnsExecutor = Executors.newFixedThreadPool(4)

    fun start(from: String, to: String, gateway: String?, phoneIp: String?) {
        if (_state.value.running) return
        val list = TargetParser.expandRange(from, to, MAX_ADDRESSES) ?: return
        cancelled = false
        val start = System.currentTimeMillis()
        _state.value = ScanState(true, from, to, list.size, 0, emptyList(), start)
        job = scope.launch { run(list, gateway, phoneIp, start) }
    }

    fun stop() { cancelled = true }

    fun clear() { if (!_state.value.running) _state.value = ScanState() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun run(list: List<String>, gateway: String?, phoneIp: String?, start: Long) {
        val io = Dispatchers.IO.limitedParallelism(CONCURRENCY)
        val choice = runCatching { NetworkResolver.choose(context, InetAddress.getByName(list.first())) }.getOrNull()
        val network = choice?.network
        val queue = Channel<String>(Channel.UNLIMITED).apply { list.forEach { trySend(it) }; close() }
        val done = AtomicInteger(0)
        val found = java.util.concurrent.ConcurrentLinkedQueue<FoundDevice>()
        val lock = Any()
        fun publish() {
            val sorted = found.sortedBy { ipKey(it.address) }
            synchronized(lock) { _state.value = _state.value.copy(done = done.get(), found = sorted) }
        }
        val workers = (0 until CONCURRENCY).map {
            scope.async(io) {
                for (addr in queue) {
                    if (cancelled) break
                    val rtt = probe(addr, network)
                    if (rtt != null) {
                        found.add(FoundDevice(addr, rtt.first, rtt.second, isGateway = addr == gateway, isPhone = addr == phoneIp))
                    }
                    done.incrementAndGet()
                    publish()
                }
            }
        }
        workers.awaitAll()
        // Nomi dei dispositivi trovati (se la rete li fornisce), in parallelo e con un tempo massimo.
        val named = found.toList().map { d ->
            scope.async(io) { d.copy(name = withTimeoutOrNull(3000) { reverseName(d.address, network) }) }
        }.awaitAll().sortedBy { ipKey(it.address) }
        _state.value = _state.value.copy(running = false, done = done.get(), found = named,
            endedAt = System.currentTimeMillis(), cancelled = cancelled)
    }

    /** Due tentativi da 1 secondo: restituisce (tempo, TTL) oppure null se non risponde. */
    private fun probe(addr: String, network: Network?): Pair<Double, Int?>? {
        val ip = runCatching { InetAddress.getByName(addr) }.getOrNull() ?: return null
        val r = PingEngine.Resolved(ip, addr, NetworkChoice(network, "", null, null, null, true), true)
        val pinger = runCatching { PingEngine.openPinger(r, PingConfig.WINDOWS_TTL, PingConfig.WINDOWS_PAYLOAD) }.getOrNull() ?: return null
        try {
            for (seq in 1..ATTEMPTS) {
                if (cancelled) return null
                val res = pinger.ping(seq, TIMEOUT_MS) { cancelled } ?: return null
                if (res.status == ReplyStatus.OK && res.rttMs != null) return res.rttMs to res.ttl
            }
            return null
        } finally {
            runCatching { pinger.close() }
        }
    }

    /** Nome del dispositivo dalla rete (record PTR), interrogando il DNS della rete locale. */
    private suspend fun reverseName(addr: String, network: Network?): String? {
        val ptr = DnsPtr.queryName(addr) ?: return null
        if (Build.VERSION.SDK_INT >= 29 && network != null) {
            return suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    DnsResolver.getInstance().rawQuery(network, ptr, DnsResolver.CLASS_IN, DnsPtr.TYPE_PTR, DnsResolver.FLAG_EMPTY,
                        dnsExecutor, signal, object : DnsResolver.Callback<ByteArray> {
                            override fun onAnswer(answer: ByteArray, rcode: Int) { if (cont.isActive) cont.resume(DnsPtr.parsePtr(answer)) }
                            override fun onError(error: DnsResolver.DnsException) { if (cont.isActive) cont.resume(null) }
                        })
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return runCatching {
            val n = InetAddress.getByName(addr).canonicalHostName
            n.takeIf { it != addr }
        }.getOrNull()
    }

    companion object {
        const val MAX_ADDRESSES = 1024
        const val CONCURRENCY = 32
        const val ATTEMPTS = 2
        const val TIMEOUT_MS = 1000

        fun ipKey(a: String): Long = a.split('.').mapNotNull { it.toLongOrNull() }.fold(0L) { acc, p -> (acc shl 8) or p }

        /** Intervallo proposto: la rete del telefono, limitata alle 254 posizioni attorno al suo indirizzo. */
        fun defaultRange(lan: NetInterfaceInfo?): Pair<String, String>? {
            val ip = lan?.ipv4 ?: return null
            val prefix = lan.prefixLength ?: return null
            val r = IpConfig.hostRange(ip, prefix) ?: return null
            return if (prefix >= 22) r else {
                val base = ip.substringBeforeLast('.')
                "$base.1" to "$base.254"
            }
        }

        /** Stima della durata in secondi (caso peggiore: nessuno risponde). */
        fun estimateSeconds(count: Int): Int = ((count + CONCURRENCY - 1) / CONCURRENCY) * ATTEMPTS * TIMEOUT_MS / 1000
    }
}

/** Costruzione e lettura delle interrogazioni DNS inverse (PTR), es. 1.1.168.192.in-addr.arpa. */
object DnsPtr {
    const val TYPE_PTR = 12

    fun queryName(ipv4: String): String? {
        val p = ipv4.split('.')
        if (p.size != 4 || p.any { it.toIntOrNull() !in 0..255 }) return null
        return p.reversed().joinToString(".") + ".in-addr.arpa"
    }

    /** Estrae il primo nome PTR da una risposta DNS in formato binario. */
    fun parsePtr(msg: ByteArray): String? = runCatching {
        fun u16(i: Int) = ((msg[i].toInt() and 0xFF) shl 8) or (msg[i + 1].toInt() and 0xFF)
        val qd = u16(4); val an = u16(6)
        if (an == 0) return null
        var pos = 12
        repeat(qd) { pos = skipName(msg, pos) + 4 }
        repeat(an) {
            pos = skipName(msg, pos)
            val type = u16(pos); val rdlen = u16(pos + 8)
            val rdata = pos + 10
            if (type == TYPE_PTR) return readName(msg, rdata).trimEnd('.').takeIf { it.isNotEmpty() }
            pos = rdata + rdlen
        }
        null
    }.getOrNull()

    private fun skipName(m: ByteArray, start: Int): Int {
        var i = start
        while (true) {
            val len = m[i].toInt() and 0xFF
            if (len == 0) return i + 1
            if (len and 0xC0 == 0xC0) return i + 2
            i += len + 1
        }
    }

    private fun readName(m: ByteArray, start: Int): String {
        val sb = StringBuilder()
        var i = start
        var jumps = 0
        while (jumps < 16) {
            val len = m[i].toInt() and 0xFF
            when {
                len == 0 -> break
                len and 0xC0 == 0xC0 -> { i = ((len and 0x3F) shl 8) or (m[i + 1].toInt() and 0xFF); jumps++ }
                else -> {
                    if (sb.isNotEmpty()) sb.append('.')
                    sb.append(String(m, i + 1, len, Charsets.US_ASCII))
                    i += len + 1
                }
            }
        }
        return sb.toString()
    }
}
