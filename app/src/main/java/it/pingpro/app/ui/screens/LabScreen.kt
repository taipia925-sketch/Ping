package it.pingpro.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.pingpro.app.data.AppSettings
import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ping.PingConfig
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ping.PingSample
import it.pingpro.app.ping.ReplyStatus
import it.pingpro.app.ping.SessionState
import it.pingpro.app.ping.SessionTracker
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

enum class LabScenario(val label: String, val description: String) {
    NORMAL("Scheda sana", "Tempi bassi e regolari, nessuna perdita. È ciò che ti aspetti da una scheda che funziona."),
    WEAK_WIFI("Wi-Fi debole", "Tempi irregolari, picchi e qualche pacchetto perso: tipico del telefono lontano dall'access point o di un Wi-Fi disturbato."),
    DROPOUTS("Interruzioni", "La scheda smette di rispondere per alcuni secondi e poi riprende, come con un cavo che fa falso contatto."),
    OFF("Scheda spenta", "Nessuna risposta: ogni richiesta attende 4 secondi (il timeout) e poi viene contata come persa."),
    SPIKES("Picchi isolati", "Risposte veloci con rallentamenti occasionali, senza perdite."),
}

/** Genera la risposta simulata (null = nessuna risposta) per il pacchetto [seq]. */
internal fun simulateRtt(s: LabScenario, seq: Int, rnd: Random): Double? {
    fun noise(scale: Double) = abs(rnd.nextDouble() + rnd.nextDouble() - 1.0) * scale
    return when (s) {
        LabScenario.NORMAL -> 2.4 + noise(1.6) + if (rnd.nextInt(25) == 0) 4.0 + rnd.nextDouble() * 6 else 0.0
        LabScenario.WEAK_WIFI -> when {
            rnd.nextInt(14) == 0 -> null
            rnd.nextInt(7) == 0 -> 80.0 + rnd.nextDouble() * 170
            else -> 6.0 + noise(40.0)
        }
        LabScenario.DROPOUTS -> if (seq % 22 in 12..17) null else 2.5 + noise(2.0)
        LabScenario.OFF -> null
        LabScenario.SPIKES -> if (seq % 7 == 0) 35.0 + rnd.nextDouble() * 40 else 2.0 + noise(1.5)
    }
}

/**
 * Laboratorio: una scheda simulata per imparare a leggere i risultati senza bisogno della rete.
 * Usa la stessa logica di stato ([SessionTracker]) e gli stessi riquadri del test reale.
 */
@Composable
fun LabScreen(onBack: () -> Unit) {
    var scenario by remember { mutableStateOf(LabScenario.NORMAL) }
    var unplugged by remember { mutableStateOf(false) }
    var fast by remember { mutableStateOf(false) }
    var runToken by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(SessionState()) }
    val unpluggedNow by rememberUpdatedState(unplugged)
    val fastNow by rememberUpdatedState(fast)
    val settings = remember { AppSettings() }

    LaunchedEffect(scenario, runToken) {
        val tracker = SessionTracker(settings.highLatencyMs)
        val rnd = Random(scenario.ordinal * 31 + runToken)
        val start = System.currentTimeMillis()
        val cfg = PingConfig("192.168.1.100", PingMode.CONTINUOUS)
        fun publish() {
            state = SessionState(
                running = true, config = cfg, deviceName = "Scheda simulata", resolvedIp = "192.168.1.100",
                samples = tracker.samples.toList(), events = tracker.events.toList(), stats = tracker.stats,
                status = if (tracker.samples.isEmpty()) LinkStatus.STARTING else tracker.status,
                consecutiveLost = tracker.consecutiveLost, outages = tracker.outages, startedAt = start,
            )
        }
        publish()
        var seq = 0
        while (true) {
            seq++
            val rtt = if (unpluggedNow) null else simulateRtt(scenario, seq, rnd)
            val speed = if (fastNow) 4 else 1
            // Come nel test reale: la risposta arriva dopo [rtt]; se non arriva si attende il timeout di 4 s.
            delay(((rtt ?: 4000.0) / speed).toLong())
            val now = System.currentTimeMillis()
            tracker.add(
                if (rtt == null) PingSample(seq, now, ReplyStatus.TIMEOUT, message = "Richiesta scaduta.")
                else PingSample(seq, now, ReplyStatus.OK, rtt, 64, 32)
            )
            publish()
            if (rtt != null) delay(((1000.0 - rtt).coerceAtLeast(0.0) / speed).toLong())
        }
    }

    LabContent(state, scenario, unplugged, fast, settings, onBack,
        onScenario = { scenario = it; unplugged = false },
        onToggleCable = { unplugged = !unplugged },
        onToggleSpeed = { fast = !fast },
        onRestart = { unplugged = false; runToken++ })
}

@Composable
fun LabContent(
    state: SessionState, scenario: LabScenario, unplugged: Boolean, fast: Boolean, settings: AppSettings, onBack: () -> Unit,
    onScenario: (LabScenario) -> Unit, onToggleCable: () -> Unit, onToggleSpeed: () -> Unit, onRestart: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BackHeader("Laboratorio", "Impara a leggere i risultati", onBack) }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Palette.Violet.copy(alpha = 0.14f))
                    .border(1.dp, Palette.Violet.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.School, null, tint = Palette.Violet)
                Spacer(Modifier.width(10.dp))
                Text("SIMULAZIONE · nessun pacchetto viene inviato. I valori sono di esempio e servono solo per imparare: i test veri si fanno dalla schermata Test.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Text)
            }
        }
        item {
            Text("1. Scegli una situazione", style = MaterialTheme.typography.titleSmall, color = Palette.TextDim, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LabScenario.entries) { sc ->
                    val sel = sc == scenario
                    Text(sc.label, style = MaterialTheme.typography.labelLarge, color = if (sel) Palette.OnAccent else Palette.TextDim,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (sel) Palette.Accent else Palette.Surface)
                            .clickable { onScenario(sc) }.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(scenario.description, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, modifier = Modifier.padding(horizontal = 4.dp))
        }
        item {
            Text("2. Interagisci", style = MaterialTheme.typography.titleSmall, color = Palette.TextDim, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabButton(if (unplugged) Icons.Outlined.Cable else Icons.Outlined.LinkOff, if (unplugged) "Ricollega cavo" else "Stacca cavo",
                    if (unplugged) Palette.Green else Palette.Red, Modifier.weight(1.2f), onToggleCable)
                LabButton(Icons.Outlined.Speed, if (fast) "Velocità ×4" else "Velocità ×1", Palette.AccentSoft, Modifier.weight(1f), onToggleSpeed)
                LabButton(Icons.Outlined.RestartAlt, "Ricomincia", Palette.TextDim, Modifier.weight(1f), onRestart)
            }
        }
        item { ExplainCard(state, unplugged) }
        item { StatusHero(state, settings) }
        item { StatsGrid(state, settings) }
        item { ChartCard(state) }
        item { EventsCard(state.events) }
        item { PacketsCard(state, settings) }
    }
}

@Composable
private fun LabButton(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
    }
}

internal fun explanation(s: SessionState, unplugged: Boolean): String = when (s.status) {
    LinkStatus.STARTING -> "Il telefono ha inviato la prima richiesta e aspetta la risposta."
    LinkStatus.ONLINE -> "La scheda risponde a ogni richiesta. Il numero grande è il tempo dell'ultima risposta, in millisecondi; " +
        "sotto trovi media, minima e massima. Nella mappa pacchetti tutti i quadratini sono verdi o azzurri."
    LinkStatus.SLOW -> "L'ultima risposta ha superato 100 ms (soglia modificabile nelle Impostazioni). Se capita solo ogni tanto è normale per il Wi-Fi " +
        "di un telefono; se i tempi restano alti a lungo va verificato."
    LinkStatus.UNSTABLE -> if (s.consecutiveLost > 0)
        "L'ultima richiesta non ha avuto risposta entro 4 secondi: in Windows vedresti «Richiesta scaduta.». Un solo pacchetto perso " +
            "non è ancora un'interruzione: lo stato diventa rosso dal secondo pacchetto perso di fila."
    else "La scheda risponde, ma negli ultimi 10 pacchetti ce n'è almeno uno perso: lo stato resta giallo finché non arrivano 10 risposte di fila."
    LinkStatus.DOWN -> "Almeno 2 richieste consecutive senza risposta: lo stato è rosso e, in un test reale, il telefono vibra. " +
        "Nota che ogni richiesta persa viene conteggiata solo dopo 4 secondi di attesa (il timeout), per questo i contatori avanzano più lentamente." +
        if (unplugged) " Premi «Ricollega cavo»: vedrai l'evento «Connessione ripristinata» con la durata dell'interruzione." else ""
    else -> ""
}

@Composable
private fun ExplainCard(s: SessionState, unplugged: Boolean) {
    AppCard(color = Palette.Surface2) {
        Text("Cosa stai vedendo", style = MaterialTheme.typography.titleSmall, color = Palette.AccentSoft, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        AnimatedContent(explanation(s, unplugged), label = "exp") { t ->
            Text(t, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        }
    }
}

