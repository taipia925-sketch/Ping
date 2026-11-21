package it.pingpro.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.pingpro.app.ping.FoundDevice
import it.pingpro.app.ping.NetInterfaceInfo
import it.pingpro.app.ping.NetworkScanner
import it.pingpro.app.ping.ScanState
import it.pingpro.app.ping.TargetParser
import it.pingpro.app.ui.Format
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.components.HelpButton
import it.pingpro.app.ui.components.HelpTopic
import it.pingpro.app.ui.components.IconBadge
import it.pingpro.app.ui.components.PillShape
import it.pingpro.app.ui.components.PanelTitle
import it.pingpro.app.ui.theme.Palette

/** Testo del dispositivo trovato: nome dalla rete, oppure "router"/"questo telefono". */
fun FoundDevice.title(savedName: String?): String = savedName ?: name ?: when {
    isGateway -> "Router / gateway"
    isPhone -> "Questo telefono"
    else -> address
}

/** Dispositivi da pingare: i selezionati (o tutti), senza il telefono stesso. */
fun pingTargets(found: List<FoundDevice>, selected: Set<String>): List<FoundDevice> =
    found.filter { (selected.isEmpty() || it.address in selected) && !it.isPhone }

/** Solo gli indirizzi IP, uno per riga e in ordine: pronti da incollare (anche in «Più schede»). */
fun ipList(devices: List<FoundDevice>): String =
    devices.sortedBy { NetworkScanner.ipKey(it.address) }.joinToString("\n") { it.address }

/** Elenco descrittivo dei dispositivi trovati. */
fun scanReport(s: ScanState, names: (String) -> String?): String = buildString {
    appendLine("PingPro – Ricerca dispositivi ${s.from} – ${s.to}")
    s.startedAt?.let { appendLine(Format.dateTimeFull(it)) }
    appendLine("${s.found.size} dispositivi hanno risposto su ${s.total} indirizzi" + if (s.cancelled) " (ricerca interrotta)" else "")
    appendLine()
    s.found.forEach { d ->
        append(d.address.padEnd(16)).append(Format.ms(d.rttMs).padStart(6)).append(" ms  ")
        appendLine(listOfNotNull(names(d.address), d.name, if (d.isGateway) "gateway" else null, if (d.isPhone) "questo telefono" else null)
            .distinct().joinToString(" · "))
    }
}

@Composable
fun ScanScreen(
    state: ScanState,
    lan: NetInterfaceInfo?,
    busy: Boolean,
    savedName: (String) -> String?,
    onStart: (String, String) -> Unit,
    onStop: () -> Unit,
    onPing: (String) -> Unit,
    onAddToMulti: (List<FoundDevice>) -> Unit,
    onPingAll: (List<FoundDevice>) -> Unit = {},
    onSave: (List<FoundDevice>) -> Unit,
    onCopy: (String) -> Unit,
    onBack: () -> Unit,
) {
    val def = remember(lan) { NetworkScanner.defaultRange(lan) }
    var from by remember { mutableStateOf(state.from.ifEmpty { def?.first ?: "" }) }
    var to by remember { mutableStateOf(state.to.ifEmpty { def?.second ?: "" }) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val count = TargetParser.rangeSize(from, to)
    val valid = count != null && count in 1..NetworkScanner.MAX_ADDRESSES
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.OutlineStrong)
    val finished = !state.running && state.endedAt != null

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Cerca dispositivi", "Chi risponde sulla rete del telefono", onBack) }

        if (lan == null) item {
            AppCard(color = Palette.AmberBg) {
                Text("Il telefono non è collegato a un Wi-Fi o a una rete Ethernet: collegati alla rete da esplorare.",
                    style = MaterialTheme.typography.titleSmall, color = Palette.Amber)
            }
        }

        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PanelTitle("Indirizzi da controllare", Modifier.weight(1f))
                    HelpButton(HelpTopic.SCAN, size = 16.dp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(from, { from = it.trim() }, label = { Text("Da") }, singleLine = true, modifier = Modifier.weight(1f),
                        enabled = !state.running, shape = RoundedCornerShape(4.dp), colors = colors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(to, { to = it.trim() }, label = { Text("A") }, singleLine = true, modifier = Modifier.weight(1f),
                        enabled = !state.running, shape = RoundedCornerShape(4.dp), colors = colors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        count == null -> "Scrivi due indirizzi validi, il secondo uguale o maggiore del primo."
                        count > NetworkScanner.MAX_ADDRESSES -> "Troppi indirizzi ($count): al massimo ${NetworkScanner.MAX_ADDRESSES} per ricerca."
                        else -> "$count indirizzi · al massimo circa ${NetworkScanner.estimateSeconds(count.toInt())} s"
                    },
                    style = MaterialTheme.typography.bodySmall, color = if (valid) Palette.TextDim else Palette.Amber,
                )
                if (def != null && (from != def.first || to != def.second) && !state.running) {
                    TextButton(onClick = { from = def.first; to = def.second }) { Text("Usa la rete del telefono (${def.first} – ${def.second})") }
                }
            }
        }

        item {
            val enabled = state.running || (valid && !busy)
            Row(
                Modifier.fillMaxWidth().height(60.dp).clip(PillShape)
                    .background(when { state.running -> Palette.Danger; enabled -> Palette.Accent; else -> Palette.Surface3 })
                    .clickable(enabled = enabled) { if (state.running) onStop() else { selected = emptySet(); onStart(from, to) } },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (state.running) Icons.Outlined.Stop else Icons.Outlined.Search, null, tint = if (enabled) Palette.OnAccent else Palette.TextFaint)
                Spacer(Modifier.width(8.dp))
                Text((if (state.running) "Interrompi ricerca" else if (finished) "Cerca di nuovo" else "Avvia ricerca").uppercase(),
                    style = MaterialTheme.typography.labelLarge, letterSpacing = 1.6.sp, color = if (enabled) Palette.OnAccent else Palette.TextFaint)
            }
            if (busy && !state.running) Text("C'è un test in corso: interrompilo per avviare la ricerca.", style = MaterialTheme.typography.bodySmall,
                color = Palette.Amber, modifier = Modifier.padding(top = 6.dp, start = 8.dp))
        }

        if (state.running || finished) item {
            AppCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.found.size}", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.found.size == 1) "dispositivo trovato" else "dispositivi trovati", style = MaterialTheme.typography.titleSmall,
                        color = Palette.TextDim, modifier = Modifier.padding(bottom = 8.dp))
                }
                Spacer(Modifier.height(10.dp))
                val prog by androidx.compose.animation.core.animateFloatAsState(state.progress, label = "scan")
                Box(Modifier.fillMaxWidth().height(8.dp).border(1.dp, Palette.OutlineStrong.copy(alpha = 0.5f)).padding(1.dp)) {
                    Box(Modifier.fillMaxWidth(prog).fillMaxHeight().background(if (state.running) Palette.Accent else Palette.Green))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        state.running -> "${state.done} di ${state.total} indirizzi controllati…"
                        state.cancelled -> "Ricerca interrotta dopo ${state.done} indirizzi su ${state.total}."
                        else -> "Controllati ${state.total} indirizzi in ${Format.duration((state.endedAt ?: 0) - (state.startedAt ?: 0))}."
                    },
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
            }
        }

        if (state.found.isNotEmpty()) {
            if (!state.running) item {
                // Pulsante principale: manda i dispositivi trovati direttamente al ping (test di più schede).
                val chosen = pingTargets(state.found, selected)
                val enabled = chosen.isNotEmpty() && !busy
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clip(PillShape).background(if (enabled) Palette.Green else Palette.Surface3)
                        .clickable(enabled = enabled) { onPingAll(chosen) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.NetworkPing, null, tint = if (enabled) Palette.OnAccent else Palette.TextFaint)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (selected.isEmpty()) "Pinga tutti (${chosen.size})" else if (chosen.size == 1) "Pinga il selezionato" else "Pinga i ${chosen.size} selezionati").uppercase(),
                        style = MaterialTheme.typography.labelLarge, letterSpacing = 1.6.sp, color = if (enabled) Palette.OnAccent else Palette.TextFaint,
                    )
                }
                Text(
                    "Avvia subito il test continuo di più schede con " + (if (selected.isEmpty()) "tutti i dispositivi trovati" else "i dispositivi selezionati") +
                        (if (state.found.any { it.isPhone && (selected.isEmpty() || it.address in selected) }) " (escluso questo telefono)." else "."),
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                    PanelTitle("Dispositivi che rispondono", Modifier.weight(1f))
                    if (!state.running) {
                        val all = selected.size == state.found.size
                        TextButton(onClick = { selected = if (all) emptySet() else state.found.map { it.address }.toSet() }) {
                            Text(if (all) "Deseleziona" else "Seleziona tutti")
                        }
                    }
                }
            }
            itemsIndexed(state.found, key = { _, d -> d.address }) { i, d ->
                val sel = d.address in selected
                val shape = when {
                    state.found.size == 1 -> RoundedCornerShape(4.dp)
                    i == 0 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                    i == state.found.lastIndex -> RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Column(Modifier.animateItem().fillMaxWidth().clip(shape).background(Palette.Surface)) {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !state.running) { selected = if (sel) selected - d.address else selected + d.address }
                            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!state.running) {
                            Icon(if (sel) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null,
                                tint = if (sel) Palette.Accent else Palette.TextFaint)
                            Spacer(Modifier.width(10.dp))
                        }
                        IconBadge(
                            when { d.isGateway -> Icons.Outlined.Router; d.isPhone -> Icons.Outlined.Smartphone; else -> Icons.Outlined.DeveloperBoard },
                            when { d.isGateway -> Palette.AccentSoft; d.isPhone -> Palette.Violet; else -> Palette.Green }, size = 38.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.title(savedName(d.address)), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val title = d.title(savedName(d.address))
                            Text(
                                if (title == d.address) (if (state.running) "in attesa del nome…" else "nome non disponibile")
                                else listOfNotNull(d.address, if (d.isGateway && (d.name != null || savedName(d.address) != null)) "gateway" else null,
                                    if (d.isPhone && d.name != null) "questo telefono" else null).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = if (title == d.address) Palette.TextFaint else Palette.TextDim,
                            )
                        }
                        Text(Format.ms(d.rttMs), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = Palette.Text)
                        Text(" ms", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                        IconButton(onClick = { onPing(d.address) }, enabled = !state.running && !busy) {
                            Icon(Icons.Outlined.NetworkPing, "Ping ${d.address}", tint = if (!state.running && !busy) Palette.AccentSoft else Palette.TextFaint)
                        }
                    }
                    if (i != state.found.lastIndex) Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(1.dp).background(Palette.Outline))
                }
            }
            if (!state.running) item {
                val chosen = state.found.filter { it.address in selected }
                AnimatedVisibility(chosen.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Box(Modifier.weight(1f).height(54.dp).clip(PillShape).background(Palette.Accent).clickable { onAddToMulti(chosen) },
                            contentAlignment = Alignment.Center) {
                            Text("Aggiungi ${chosen.size} a Più schede", style = MaterialTheme.typography.labelLarge, color = Palette.OnAccent)
                        }
                        Box(Modifier.weight(0.7f).height(54.dp).clip(PillShape).background(Palette.AccentBg).clickable { onSave(chosen) },
                            contentAlignment = Alignment.Center) {
                            Text("Salva come schede", style = MaterialTheme.typography.labelLarge, color = Palette.AccentSoft)
                        }
                    }
                }
            }
            if (!state.running) item {
                val toCopy = state.found.filter { it.address in selected }.ifEmpty { state.found }
                TextButton(onClick = { onCopy(ipList(toCopy)) }) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (toCopy.size == 1) "Copia 1 indirizzo IP" else "Copia ${toCopy.size} indirizzi IP" +
                        if (selected.isNotEmpty() && toCopy.size < state.found.size) " (selezionati)" else "")
                }
            }
        }
        if (finished && state.found.isEmpty() && !state.cancelled) item {
            AppCard(color = Palette.AmberBg) {
                Text("Nessun dispositivo ha risposto. Verifica di essere sulla rete giusta e che l'intervallo sia corretto.",
                    style = MaterialTheme.typography.titleSmall, color = Palette.Amber)
            }
        }
        item {
            Text(
                "Nota: alcuni dispositivi (molti telefoni e PC con firewall) sono collegati ma non rispondono al ping, quindi non compaiono. " +
                    "La ricerca controlla ogni indirizzo una sola volta e non viene salvata nello storico.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
