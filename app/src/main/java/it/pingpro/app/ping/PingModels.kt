package it.pingpro.app.ping

/** Modalità di test, equivalenti ai comandi di Windows. */
enum class PingMode {
    /** `ping <ip>` – numero definito di richieste (default Windows: 4). */
    SINGLE,

    /** `ping -t <ip>` – richieste continue fino all'interruzione manuale. */
    CONTINUOUS
}

/**
 * Parametri del test. I valori di default sono identici a quelli di ping.exe di Windows:
 * -n 4, -l 32 byte, -w 4000 ms, -i 128 (TTL), ~1 secondo tra una richiesta e la successiva.
 */
data class PingConfig(
    val target: String,
    val mode: PingMode = PingMode.SINGLE,
    val count: Int = WINDOWS_COUNT,
    val payloadSize: Int = WINDOWS_PAYLOAD,
    val timeoutMs: Int = WINDOWS_TIMEOUT_MS,
    val ttl: Int = WINDOWS_TTL,
    val intervalMs: Int = WINDOWS_INTERVAL_MS,
) {
    /** Comando Windows equivalente, mostrato all'utente come riferimento. */
    fun windowsCommand(): String = buildString {
        append("ping")
        if (mode == PingMode.CONTINUOUS) append(" -t")
        else if (count != WINDOWS_COUNT) append(" -n ").append(count)
        if (payloadSize != WINDOWS_PAYLOAD) append(" -l ").append(payloadSize)
        if (timeoutMs != WINDOWS_TIMEOUT_MS) append(" -w ").append(timeoutMs)
        if (ttl != WINDOWS_TTL) append(" -i ").append(ttl)
        append(' ').append(target.ifBlank { "<indirizzo>" })
    }

    companion object {
        const val WINDOWS_COUNT = 4
        const val WINDOWS_PAYLOAD = 32
        const val WINDOWS_TIMEOUT_MS = 4000
        const val WINDOWS_TTL = 128
        const val WINDOWS_INTERVAL_MS = 1000
    }
}

enum class ReplyStatus {
    /** Risposta ICMP Echo Reply ricevuta. */
    OK,

    /** Nessuna risposta entro il timeout ("Richiesta scaduta."). */
    TIMEOUT,

    /** Rete o host non raggiungibile (errore restituito dal sistema). */
    UNREACHABLE,

    /** Errore generico di invio. */
    ERROR;

    val isLost: Boolean get() = this != OK
}

/** Risultato di una singola richiesta ICMP Echo. */
data class PingSample(
    val seq: Int,
    val timestamp: Long,
    val status: ReplyStatus,
    val rttMs: Double? = null,
    val ttl: Int? = null,
    val bytes: Int = 0,
    val message: String? = null,
)

/** Stato della connessione mostrato in modo evidente nella dashboard. */
enum class LinkStatus {
    IDLE, STARTING, ONLINE, SLOW, UNSTABLE, DOWN, FINISHED, ERROR
}

enum class EventKind { START, INFO, LOSS, OUTAGE_START, OUTAGE_END, SPIKE, STOP, ERROR }

data class PingEvent(
    val timestamp: Long,
    val kind: EventKind,
    val title: String,
    val detail: String? = null,
    val seq: Int? = null,
)
