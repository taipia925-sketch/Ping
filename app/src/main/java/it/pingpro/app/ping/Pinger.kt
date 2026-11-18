package it.pingpro.app.ping

import java.io.Closeable

/** Risultato grezzo di una richiesta, prima di essere trasformato in [PingSample]. */
data class EchoResult(
    val status: ReplyStatus,
    val rttMs: Double? = null,
    val ttl: Int? = null,
    val bytes: Int = 0,
    val message: String? = null,
)

interface Pinger : Closeable {
    /** Nome del motore usato, mostrato nei dettagli tecnici. */
    val engineName: String

    /**
     * Invia una richiesta ICMP Echo e attende la risposta fino a [timeoutMs].
     * Restituisce null se il test è stato interrotto durante l'attesa.
     */
    fun ping(seq: Int, timeoutMs: Int, isCancelled: () -> Boolean): EchoResult?
}
