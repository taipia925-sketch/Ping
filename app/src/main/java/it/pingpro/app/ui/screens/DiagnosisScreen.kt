package it.pingpro.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.pingpro.app.ping.IpConfig
import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ping.NetInterfaceInfo
import it.pingpro.app.ping.NetworkResolver
import it.pingpro.app.ping.SessionState
import it.pingpro.app.ui.Format
import it.pingpro.app.ui.theme.Palette

enum class Verdict { TODO, RUNNING, OK, WARN, FAIL, SKIPPED }

data class CheckResult(val verdict: Verdict, val sent: Int = 0, val received: Int = 0, val avgMs: Double? = null, val error: String? = null)

/** Valutazione dell'indirizzo della scheda rispetto alla rete del telefono (logica pura, testabile). */
data class AddressCheck(val verdict: Verdict, val title: String, val text: String, val sameNetwork: Boolean? = null)

fun checkAddress(address: String, lan: NetInterfaceInfo?): AddressCheck {
    val a = address.trim()
    if (!NetworkResolver.isValidTarget(a)) return AddressCheck(Verdict.FAIL, "Indirizzo non valido",
        "Scrivi l'indirizzo nella forma 192.168.1.100: quattro numeri da 0 a 255 separati da punti.")
    if (!NetworkResolver.isIpv4Literal(a)) return AddressCheck(Verdict.WARN, "Nome di rete",
        "Hai scritto un nome invece di un indirizzo IP: verrà tradotto in indirizzo al momento del test, se la rete lo permette.")
    val ip = lan?.ipv4; val prefix = lan?.prefixLength
    if (ip == null || prefix == null) return AddressCheck(Verdict.WARN, "Rete del telefono sconosciuta",
        "Non è possibile confrontare l'indirizzo con la rete del telefono perché il telefono non è collegato a un Wi-Fi o a una rete Ethernet.")
    if (a == ip) return AddressCheck(Verdict.FAIL, "È l'indirizzo del telefono",
        "$a è l'indirizzo del telefono stesso, non quello della scheda.")
    val range = IpConfig.hostRange(ip, prefix)
    if (IpConfig.sameSubnet(a, ip, prefix)) {
        if (range != null && (a == IpConfig.networkAddress(ip, prefix) || !inRange(a, range))) {
            return AddressCheck(Verdict.FAIL, "Indirizzo riservato",
                "$a è l'indirizzo della rete o di broadcast, non può appartenere a una scheda. Gli indirizzi validi vanno da ${range.first} a ${range.second}.")
        }
        return AddressCheck(Verdict.OK, "Stessa rete del telefono",
            "La scheda è nella stessa rete del telefono (${range?.first} – ${range?.second}): il telefono la raggiunge direttamente, senza passare dal gateway.",
            sameNetwork = true)
    }
    return if (lan.gateway != null) AddressCheck(Verdict.WARN, "Rete diversa da quella del telefono",
        "Il telefono è nella rete ${range?.first} – ${range?.second}, la scheda no. Il telefono può raggiungerla solo passando dal gateway ${lan.gateway}, " +
            "e solo se la rete è configurata per farlo. Spesso significa che l'indirizzo è scritto male o che il telefono è collegato al Wi-Fi sbagliato.",
        sameNetwork = false)
    else AddressCheck(Verdict.FAIL, "Rete diversa e nessun gateway",
        "La scheda non è nella rete del telefono (${range?.first} – ${range?.second}) e questa rete non ha un gateway: il telefono non ha modo di raggiungerla. " +
            "Verifica l'indirizzo o collega il telefono alla rete della scheda.")
}

private fun inRange(ip: String, r: Pair<String, String>): Boolean {
    fun n(s: String) = s.split('.').fold(0L) { acc, p -> (acc shl 8) or p.toLong() }
    return n(ip) in n(r.first)..n(r.second)
}

/** Conclusione finale della diagnosi in base ai risultati dei passi. */
fun diagnosisConclusion(addr: AddressCheck?, gateway: CheckResult, board: CheckResult): Pair<Verdict, String> {
    val gwOk = gateway.verdict == Verdict.OK || gateway.verdict == Verdict.WARN
    val gwFail = gateway.verdict == Verdict.FAIL
    return when {
        board.verdict == Verdict.OK -> Verdict.OK to
            "La scheda risponde regolarmente: la comunicazione di rete funziona. Se l'apparecchio comunque non lavora come dovrebbe, " +
            "la causa non è il collegamento di rete (es. software, servizio o porta dell'applicazione)."
        board.verdict == Verdict.WARN -> Verdict.WARN to
            "La scheda risponde ma perde alcuni pacchetti: il collegamento è instabile. Avvia un test continuo di qualche minuto. " +
            (if (gateway.verdict == Verdict.WARN) "Anche il gateway ha perso pacchetti: la causa più probabile è il Wi-Fi del telefono (distanza, disturbi)."
            else if (gwOk) "Il gateway invece non ha perso nulla: la causa è più probabilmente vicino alla scheda (cavo, connettore, porta dello switch, scheda sovraccarica)."
            else "")
        board.verdict == Verdict.FAIL && gwOk && addr?.sameNetwork != false -> Verdict.FAIL to
            "Il telefono è collegato bene alla rete (il gateway risponde), ma la scheda non risponde. Controlla, in quest'ordine: " +
            "1) che la scheda sia accesa e i LED della porta di rete siano attivi; 2) il cavo e la porta dello switch; 3) che l'indirizzo IP della scheda " +
            "sia davvero quello (etichetta, configurazione); 4) che la scheda sia configurata per rispondere al ping: alcuni apparecchi non rispondono per scelta."
        board.verdict == Verdict.FAIL && gwOk -> Verdict.FAIL to
            "Il gateway risponde ma la scheda no, e la scheda non è nella stessa rete del telefono. Verifica prima l'indirizzo e il Wi-Fi a cui sei collegato; " +
            "se l'indirizzo è giusto, la rete potrebbe non permettere il passaggio tra le due reti (chiedi a chi gestisce la rete)."
        board.verdict == Verdict.FAIL && gwFail -> Verdict.FAIL to
            "Non rispondono né il gateway né la scheda: il problema è più probabilmente nel collegamento del telefono alla rete. Prova a: " +
            "scollegarti e ricollegarti al Wi-Fi, avvicinarti all'access point, verificare di non essere su una rete «ospiti» (spesso isolata), " +
            "disattivare un'eventuale VPN. Nota: alcuni gateway non rispondono al ping per configurazione."
        board.verdict == Verdict.FAIL -> Verdict.FAIL to
            "La scheda non risponde e non c'è il risultato del gateway da usare come confronto: verifica alimentazione e cavo della scheda, l'indirizzo IP " +
            "e che telefono e scheda siano sulla stessa rete."
        else -> Verdict.TODO to ""
    }
}

private fun resultFrom(s: SessionState): CheckResult = when {
    s.status == LinkStatus.ERROR -> CheckResult(Verdict.FAIL, error = s.error)
    s.stats.sent == 0 -> CheckResult(Verdict.SKIPPED)
    s.stats.received == 0 -> CheckResult(Verdict.FAIL, s.stats.sent, 0)
    s.stats.lost > 0 -> CheckResult(Verdict.WARN, s.stats.sent, s.stats.received, s.stats.avgMs)
    else -> CheckResult(Verdict.OK, s.stats.sent, s.stats.received, s.stats.avgMs)
}

private data class Waiting(val step: Int, val target: String, val after: Long)

@Composable
fun DiagnosisScreen(
    lan: NetInterfaceInfo?,
    session: SessionState,
    initialAddress: String,
    runCheck: (String) -> Boolean,
    onRefreshNetwork: () -> Unit,
    onContinuous: (String) -> Unit,
    onBack: () -> Unit,
    toast: (String) -> Unit,
) {
    val context = LocalContext.current
    var address by remember { mutableStateOf(initialAddress.takeIf { it != lan?.gateway } ?: "") }
    var addressConfirmed by remember { mutableStateOf<AddressCheck?>(null) }
    var gw by remember { mutableStateOf(CheckResult(Verdict.TODO)) }
    var board by remember { mutableStateOf(CheckResult(Verdict.TODO)) }
    var waiting by remember { mutableStateOf<Waiting?>(null) }

    // Aspetta la fine del test reale avviato da un passo e ne legge il risultato.
    LaunchedEffect(session.running, session.status, session.startedAt) {
        val w = waiting ?: return@LaunchedEffect
        val started = session.startedAt ?: return@LaunchedEffect
        if (!session.running && started >= w.after && session.config?.target == w.target) {
            val r = resultFrom(session)
            if (w.step == 3) gw = r else board = r
            waiting = null
        }
    }

    fun start(step: Int, target: String) {
        val after = System.currentTimeMillis()
        if (runCheck(target)) {
            waiting = Waiting(step, target, after)
            if (step == 3) gw = CheckResult(Verdict.RUNNING) else board = CheckResult(Verdict.RUNNING)
        } else toast("C'è già un test in corso: interrompilo dalla schermata Test")
    }

    val step1 = if (lan != null) Verdict.OK else Verdict.FAIL
    val gwStep = if (lan != null && lan.gateway == null) CheckResult(Verdict.SKIPPED) else gw

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { BackHeader("Diagnosi guidata", "La scheda non risponde? Troviamo insieme il motivo", onBack) }
        item {
            Text("Ogni passo esegue controlli reali dal telefono. Segui i passi in ordine: alla fine trovi dove cercare il problema.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp))
        }

        // PASSO 1 — collegamento del telefono
        item {
            StepCard(1, "Il telefono è collegato alla rete?", step1, last = false) {
                if (lan != null) {
                    Text("Sì: ${lan.type}${lan.interfaceName?.let { " ($it)" } ?: ""}, indirizzo del telefono ${lan.ipv4}, " +
                        "rete ${lan.hostRange?.let { "${it.first} – ${it.second}" } ?: ""}.", style = MaterialTheme.typography.bodyMedium)
                    lan.wifiRssi?.let {
                        Text("Segnale Wi-Fi: $it dBm (${IpConfig.signalLabel(it)})" + if (it < -75) " — debole: avvicinati all'access point per un test affidabile." else "",
                            style = MaterialTheme.typography.bodySmall, color = if (it < -75) Palette.Amber else Palette.TextDim, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text("Verifica che sia la rete a cui è collegata la scheda.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text("No: il telefono non è collegato a un Wi-Fi o a una rete Ethernet. Collegati alla rete delle schede, poi premi «Ricontrolla».",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("Apri impostazioni Wi-Fi", primary = true) {
                            runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        SmallAction("Ricontrolla") { onRefreshNetwork() }
                    }
                }
            }
        }

        // PASSO 2 — indirizzo della scheda
        item {
            val live = if (address.isNotBlank()) checkAddress(address, lan) else null
            StepCard(2, "Qual è l'indirizzo della scheda?", addressConfirmed?.verdict ?: Verdict.TODO, last = false, enabled = true) {
                OutlinedTextField(
                    address, { address = it.trim(); addressConfirmed = null; board = CheckResult(Verdict.TODO) },
                    label = { Text("Indirizzo IP della scheda") }, placeholder = { Text("es. 192.168.1.100") },
                    singleLine = true, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.OutlineStrong),
                )
                if (live != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(live.title, style = MaterialTheme.typography.titleSmall, color = verdictColor(live.verdict))
                    Text(live.text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                    if (addressConfirmed == null && live.verdict != Verdict.FAIL) {
                        Spacer(Modifier.height(10.dp))
                        SmallAction("Conferma indirizzo", primary = true) { addressConfirmed = live }
                    }
                }
            }
        }

        // PASSO 3 — gateway
        item {
            StepCard(3, "Il gateway risponde?", gwStep.verdict, last = false, enabled = lan != null) {
                Text("Primo controllo: se il gateway ${lan?.gateway ?: ""} risponde, il collegamento del telefono alla rete funziona.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                Spacer(Modifier.height(8.dp))
                when (gwStep.verdict) {
                    Verdict.SKIPPED -> Text(if (lan?.gateway == null) "Questa rete non ha un gateway: passo saltato." else "Passo saltato.", style = MaterialTheme.typography.bodyMedium)
                    Verdict.RUNNING -> RunningLine(session)
                    Verdict.TODO -> if (lan?.gateway != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("Esegui ping del gateway", primary = true) { start(3, lan.gateway) }
                        SmallAction("Salta") { gw = CheckResult(Verdict.SKIPPED) }
                    }
                    else -> {
                        ResultLine(gwStep, "Il gateway risponde", "Il gateway non risponde")
                        if (gwStep.verdict == Verdict.FAIL) Text("Può dipendere dal collegamento del telefono, oppure il gateway è configurato per non rispondere al ping. Prosegui comunque con la scheda.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(8.dp))
                        SmallAction("Ripeti") { lan?.gateway?.let { start(3, it) } }
                    }
                }
            }
        }

        // PASSO 4 — scheda
        item {
            val canRun = addressConfirmed != null && gwStep.verdict != Verdict.TODO && gwStep.verdict != Verdict.RUNNING
            StepCard(4, "La scheda risponde?", board.verdict, last = false, enabled = canRun) {
                if (!canRun) {
                    Text("Completa prima i passi 2 e 3.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
                } else when (board.verdict) {
                    Verdict.RUNNING -> RunningLine(session)
                    Verdict.TODO -> SmallAction("Esegui ping di $address (4 richieste)", primary = true) { start(4, address) }
                    else -> {
                        ResultLine(board, "La scheda risponde", "La scheda non risponde")
                        Spacer(Modifier.height(8.dp))
                        SmallAction("Ripeti") { start(4, address) }
                    }
                }
            }
        }

        // CONCLUSIONE
        item {
            val (v, text) = diagnosisConclusion(addressConfirmed, gwStep, board)
            AnimatedVisibility(v != Verdict.TODO) {
                Column(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(verdictColor(v).copy(alpha = 0.12f))
                        .border(1.dp, verdictColor(v).copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(20.dp)
                ) {
                    Text("Risultato della diagnosi", style = MaterialTheme.typography.titleLarge, color = verdictColor(v))
                    Spacer(Modifier.height(8.dp))
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("Test continuo sulla scheda", primary = true) { onContinuous(address) }
                        SmallAction("Ricomincia") {
                            gw = CheckResult(Verdict.TODO); board = CheckResult(Verdict.TODO); addressConfirmed = null
                        }
                    }
                    Text("I test eseguiti sono salvati nello Storico.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.OK -> Palette.Green
    Verdict.WARN -> Palette.Amber
    Verdict.FAIL -> Palette.Red
    Verdict.RUNNING -> Palette.Accent
    else -> Palette.TextFaint
}

@Composable
private fun StepCard(n: Int, title: String, verdict: Verdict, last: Boolean, enabled: Boolean = true, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            val c = verdictColor(verdict)
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(if (verdict == Verdict.TODO) Palette.Surface3 else c.copy(alpha = 0.2f))
                .border(1.5.dp, if (verdict == Verdict.TODO) Palette.OutlineStrong else c, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                when (verdict) {
                    Verdict.OK -> Icon(Icons.Outlined.Check, null, tint = c, modifier = Modifier.size(18.dp))
                    Verdict.FAIL -> Icon(Icons.Outlined.Close, null, tint = c, modifier = Modifier.size(18.dp))
                    Verdict.WARN -> Icon(Icons.Outlined.PriorityHigh, null, tint = c, modifier = Modifier.size(18.dp))
                    Verdict.SKIPPED -> Icon(Icons.Outlined.Remove, null, tint = c, modifier = Modifier.size(18.dp))
                    Verdict.RUNNING -> CircularProgressIndicator(Modifier.size(18.dp), color = c, strokeWidth = 2.dp)
                    Verdict.TODO -> Text("$n", fontWeight = FontWeight.Bold, color = Palette.TextDim)
                }
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(Palette.OutlineStrong))
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f).padding(bottom = 16.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Surface)
                .border(1.dp, Palette.Outline, RoundedCornerShape(4.dp)).padding(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Palette.Text else Palette.TextFaint)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SmallAction(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = if (primary) Palette.OnAccent else Palette.AccentSoft,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (primary) Palette.Accent else Palette.Surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp))
}

@Composable
private fun RunningLine(s: SessionState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("Test in corso… ${s.stats.sent}/4 inviati, ${s.stats.received} risposte" + (s.stats.lastMs?.let { " · ultima ${Format.ms(it)} ms" } ?: ""),
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultLine(r: CheckResult, okText: String, failText: String) {
    val text = when (r.verdict) {
        Verdict.OK -> "$okText: ${r.received}/${r.sent} risposte, media ${Format.msOrDash(r.avgMs)} ms."
        Verdict.WARN -> "Risponde in parte: ${r.received}/${r.sent} risposte, media ${Format.msOrDash(r.avgMs)} ms."
        Verdict.FAIL -> r.error ?: "$failText: 0/${r.sent} risposte."
        Verdict.SKIPPED -> "Test interrotto."
        else -> ""
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = verdictColor(r.verdict), fontWeight = FontWeight.Medium)
}
