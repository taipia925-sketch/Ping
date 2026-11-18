package it.pingpro.app.ping

import kotlin.math.roundToLong

/**
 * Logica pura (senza Android) che, pacchetto dopo pacchetto, aggiorna statistiche,
 * stato della connessione ed eventi. È separata dal motore di rete per poter essere testata.
 */
class SessionTracker(private val highLatencyMs: Int = 100) {

    val samples = ArrayList<PingSample>()
    val events = ArrayList<PingEvent>()
    var stats = PingStats(); private set
    var status = LinkStatus.STARTING; private set
    var consecutiveLost = 0; private set
    var outages = 0; private set

    private var outageStart: Long? = null
    private var outageStartSeq: Int? = null

    /** Risultato dell'aggiunta di un pacchetto: indica se va emesso un allarme. */
    enum class Alert { NONE, DOWN, RECOVERED }

    fun addEvent(e: PingEvent) { events.add(e) }

    fun add(sample: PingSample): Alert {
        samples.add(sample)
        stats = stats.add(sample)
        var alert = Alert.NONE
        if (sample.status.isLost) {
            consecutiveLost++
            if (consecutiveLost == 1) {
                outageStart = sample.timestamp
                outageStartSeq = sample.seq
                events.add(PingEvent(sample.timestamp, EventKind.LOSS, "Pacchetto #${sample.seq} perso",
                    sample.message ?: "Nessuna risposta entro il timeout", sample.seq))
            }
            if (consecutiveLost == OUTAGE_THRESHOLD) {
                outages++
                alert = Alert.DOWN
                events.add(PingEvent(sample.timestamp, EventKind.OUTAGE_START, "Interruzione: la scheda non risponde",
                    "Nessuna risposta da $OUTAGE_THRESHOLD richieste consecutive (dal pacchetto #$outageStartSeq)", sample.seq))
            }
        } else {
            if (consecutiveLost >= OUTAGE_THRESHOLD) {
                val start = outageStart ?: sample.timestamp
                val secs = ((sample.timestamp - start) / 1000.0).roundToLong()
                events.add(PingEvent(sample.timestamp, EventKind.OUTAGE_END, "Connessione ripristinata",
                    "Interruzione di circa $secs s, $consecutiveLost pacchetti persi", sample.seq))
                alert = Alert.RECOVERED
            }
            consecutiveLost = 0
            outageStart = null
            detectSpike(sample)
        }
        status = computeStatus()
        return alert
    }

    private fun detectSpike(sample: PingSample) {
        val rtt = sample.rttMs ?: return
        val recent = samples.asReversed().asSequence().drop(1).mapNotNull { it.rttMs }.take(20).toList()
        if (recent.size < 5) return
        val median = recent.sorted()[recent.size / 2]
        if (rtt > median * 4 && rtt - median > 20) {
            events.add(PingEvent(sample.timestamp, EventKind.SPIKE, "Picco di latenza: ${fmt(rtt)} ms",
                "Valore tipico in quel momento: ${fmt(median)} ms", sample.seq))
        }
    }

    private fun computeStatus(): LinkStatus {
        if (consecutiveLost >= OUTAGE_THRESHOLD) return LinkStatus.DOWN
        if (consecutiveLost > 0) return LinkStatus.UNSTABLE
        val recent = samples.takeLast(RECENT_WINDOW)
        if (recent.any { it.status.isLost }) return LinkStatus.UNSTABLE
        val last = samples.lastOrNull()?.rttMs
        if (last != null && last > highLatencyMs) return LinkStatus.SLOW
        return LinkStatus.ONLINE
    }

    companion object {
        /** Numero di richieste consecutive senza risposta oltre il quale la scheda è considerata "non raggiungibile". */
        const val OUTAGE_THRESHOLD = 2
        const val RECENT_WINDOW = 10

        fun fmt(v: Double): String = if (v < 10) String.format(java.util.Locale.ITALY, "%.1f", v)
        else String.format(java.util.Locale.ITALY, "%.0f", v)
    }
}
