package it.pingpro.app.ping

import kotlin.math.abs

/**
 * Statistiche cumulative, calcolate come fa Windows (minimo/massimo/media sulle sole risposte
 * ricevute, perdita = persi / inviati) con in più la variazione di latenza (jitter).
 */
data class PingStats(
    val sent: Int = 0,
    val received: Int = 0,
    val minMs: Double? = null,
    val maxMs: Double? = null,
    val sumMs: Double = 0.0,
    val lastMs: Double? = null,
    /** Media delle differenze assolute tra risposte consecutive. */
    val jitterMs: Double? = null,
    private val jitterSum: Double = 0.0,
    private val jitterCount: Int = 0,
    private val prevRtt: Double? = null,
) {
    val lost: Int get() = sent - received
    val avgMs: Double? get() = if (received > 0) sumMs / received else null
    val lossPercent: Double get() = if (sent > 0) lost * 100.0 / sent else 0.0

    fun add(sample: PingSample): PingStats {
        val rtt = sample.rttMs
        if (sample.status != ReplyStatus.OK || rtt == null) {
            return copy(sent = sent + 1, lastMs = null)
        }
        val (jSum, jCount) = if (prevRtt != null) (jitterSum + abs(rtt - prevRtt)) to (jitterCount + 1) else jitterSum to jitterCount
        return copy(
            sent = sent + 1,
            received = received + 1,
            minMs = minOf(minMs ?: rtt, rtt),
            maxMs = maxOf(maxMs ?: rtt, rtt),
            sumMs = sumMs + rtt,
            lastMs = rtt,
            jitterSum = jSum,
            jitterCount = jCount,
            jitterMs = if (jCount > 0) jSum / jCount else null,
            prevRtt = rtt,
        )
    }

    companion object {
        fun of(samples: List<PingSample>): PingStats = samples.fold(PingStats()) { acc, s -> acc.add(s) }
    }
}
