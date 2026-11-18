package it.pingpro.app.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ping.PingSample
import it.pingpro.app.ping.ReplyStatus
import it.pingpro.app.ui.Format
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

object Export {

    /** Durata nello stile di Windows: "durata<1ms" oppure "durata=3ms". */
    private fun winTime(rtt: Double): String = if (rtt < 1) "durata<1ms" else "durata=${rtt.roundToInt()}ms"

    /** Report testuale identico nella forma all'output di ping.exe (Windows in italiano), con dati reali. */
    fun windowsStyle(s: TestSummary, samples: List<PingSample>): String = buildString {
        appendLine("PingPro – ${s.displayName}   ${Format.dateTimeFull(s.startedAt)}")
        appendLine("Comando equivalente: ping${if (s.mode == PingMode.CONTINUOUS) " -t" else if (s.count != 4) " -n ${s.count}" else ""} ${s.target}")
        s.network?.let { appendLine("Rete del telefono: $it") }
        appendLine()
        val name = if (s.target != s.resolvedIp) "${s.target} [${s.resolvedIp}]" else s.resolvedIp
        appendLine("Esecuzione di Ping $name con ${s.payloadSize} byte di dati:")
        samples.forEach { p ->
            when (p.status) {
                ReplyStatus.OK -> appendLine("Risposta da ${s.resolvedIp}: byte=${p.bytes} ${winTime(p.rttMs ?: 0.0)}" +
                    (p.ttl?.let { " TTL=$it" } ?: "") + "   (${Format.ms(p.rttMs ?: 0.0)} ms)")
                else -> appendLine(p.message ?: "Richiesta scaduta.")
            }
        }
        appendLine()
        appendLine("Statistiche Ping per ${s.resolvedIp}:")
        appendLine("    Pacchetti: Trasmessi = ${s.sent}, Ricevuti = ${s.received},")
        appendLine("    Persi = ${s.lost} (${s.lossPercent.roundToInt()}% persi),")
        if (s.received > 0) {
            appendLine("Tempo approssimativo percorsi andata/ritorno in millisecondi:")
            appendLine("    Minimo = ${s.minMs?.roundToInt()}ms, Massimo = ${s.maxMs?.roundToInt()}ms, Medio = ${s.avgMs?.roundToInt()}ms")
            appendLine("Valori precisi: minimo ${Format.msOrDash(s.minMs)} ms, massimo ${Format.msOrDash(s.maxMs)} ms, " +
                "medio ${Format.msOrDash(s.avgMs)} ms, jitter ${Format.msOrDash(s.jitterMs)} ms")
        }
        appendLine("Durata del test: ${Format.duration(s.durationMs)}" + if (s.outages > 0) " · Interruzioni: ${s.outages}" else "")
    }

    fun csv(s: TestSummary, samples: List<PingSample>): String = buildString {
        appendLine("seq;data_ora;esito;tempo_ms;ttl;byte;messaggio")
        samples.forEach { p ->
            val esito = when (p.status) {
                ReplyStatus.OK -> "risposta"; ReplyStatus.TIMEOUT -> "scaduta"
                ReplyStatus.UNREACHABLE -> "non_raggiungibile"; ReplyStatus.ERROR -> "errore"
            }
            append(p.seq).append(';').append(Format.dateTimeFull(p.timestamp)).append(';').append(esito).append(';')
            append(p.rttMs?.let { String.format(Locale.ITALY, "%.3f", it) } ?: "").append(';')
            append(p.ttl ?: "").append(';').append(if (p.status == ReplyStatus.OK) p.bytes else "").append(';')
            appendLine((p.message ?: "").replace(';', ','))
        }
    }

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun share(context: Context, s: TestSummary, samples: List<PingSample>, asCsv: Boolean) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = s.displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "ping_${safe}_${Format.fileStamp(s.startedAt)}.${if (asCsv) "csv" else "txt"}")
        file.writeText(if (asCsv) csv(s, samples) else windowsStyle(s, samples))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (asCsv) "text/csv" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Risultati PING ${s.displayName} – ${Format.dateTime(s.startedAt)}")
            if (!asCsv) putExtra(Intent.EXTRA_TEXT, windowsStyle(s, samples))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Esporta risultati").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun verdict(t: it.pingpro.app.ping.TargetState): String = when {
        t.error != null -> "ERRORE"
        t.stats.sent == 0 -> "in attesa"
        t.stats.received == 0 -> "NON RISPONDE"
        t.stats.lost > 0 -> "con perdite"
        else -> "OK"
    }

    /** Riepilogo del test di più schede: una riga per scheda. */
    fun multiReport(m: it.pingpro.app.ping.MultiState): String = buildString {
        val start = m.startedAt ?: System.currentTimeMillis()
        appendLine("PingPro – ${m.groupName ?: "Test di più schede"}   ${Format.dateTimeFull(start)}")
        appendLine("${m.targets.size} schede · ${if (m.mode == PingMode.CONTINUOUS) "continuo (ping -t)" else "singolo (${m.count} richieste per scheda)"}" +
            (m.endedAt?.let { " · durata ${Format.duration(it - start)}" } ?: ""))
        appendLine("Rispondono: ${m.okCount} · Con perdite: ${m.warnCount} · Non rispondono: ${m.downCount}")
        appendLine()
        m.targets.forEach { t ->
            val st = t.stats
            append(verdict(t).padEnd(13)).append(t.label)
            if (t.name != null) append(" (").append(t.ip ?: t.target.address).append(')')
            appendLine()
            if (t.error != null) appendLine("             ${t.error}")
            else appendLine("             Trasmessi ${st.sent}, Ricevuti ${st.received}, Persi ${st.lost} (${st.lossPercent.roundToInt()}%)" +
                (if (st.received > 0) " · Min ${Format.msOrDash(st.minMs)} / Medio ${Format.msOrDash(st.avgMs)} / Max ${Format.msOrDash(st.maxMs)} ms" else ""))
        }
    }

    fun multiCsv(m: it.pingpro.app.ping.MultiState): String = buildString {
        appendLine("scheda;indirizzo;esito;inviati;ricevuti;persi;perdita_pct;min_ms;medio_ms;max_ms;jitter_ms;interruzioni")
        fun d(v: Double?) = v?.let { String.format(Locale.ITALY, "%.3f", it) } ?: ""
        m.targets.forEach { t ->
            val st = t.stats
            appendLine(listOf((t.name ?: "").replace(';', ','), t.ip ?: t.target.address, verdict(t), st.sent, st.received, st.lost,
                String.format(Locale.ITALY, "%.1f", st.lossPercent), d(st.minMs), d(st.avgMs), d(st.maxMs), d(st.jitterMs), t.outages).joinToString(";"))
        }
    }

    fun shareMulti(context: Context, m: it.pingpro.app.ping.MultiState, asCsv: Boolean) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = (m.groupName ?: "piu_schede").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "ping_${safe}_${Format.fileStamp(m.startedAt ?: System.currentTimeMillis())}.${if (asCsv) "csv" else "txt"}")
        val text = multiReport(m)
        file.writeText(if (asCsv) multiCsv(m) else text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (asCsv) "text/csv" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Risultati PING – ${m.groupName ?: "${m.targets.size} schede"}")
            if (!asCsv) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Esporta risultati").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
