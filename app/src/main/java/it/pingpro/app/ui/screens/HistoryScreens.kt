package it.pingpro.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.pingpro.app.data.Export
import it.pingpro.app.data.TestDetail
import it.pingpro.app.data.TestSummary
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ui.Format
import it.pingpro.app.ui.MainViewModel
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.components.ChartSeries
import it.pingpro.app.ui.components.EmptyState
import it.pingpro.app.ui.components.HelpTopic
import it.pingpro.app.ui.components.LatencyChart
import it.pingpro.app.ui.components.Pill
import it.pingpro.app.ui.components.SectionHeader
import it.pingpro.app.ui.components.StatTile
import it.pingpro.app.ui.theme.Palette
import it.pingpro.app.ui.theme.latencyColor

private fun resultColor(s: TestSummary): Color = when {
    s.received == 0 -> Palette.Red
    s.lost > 0 -> Palette.Amber
    else -> Palette.Green
}

private fun resultLabel(s: TestSummary): String = when {
    s.received == 0 -> "Nessuna risposta"
    s.lost > 0 -> "Con perdite"
    else -> "OK"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    items: List<TestSummary>,
    onOpen: (String) -> Unit,
    onCompare: (String, String) -> Unit,
    onClear: () -> Unit,
) {
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp, start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Storico", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        if (selecting) "Seleziona 2 test da confrontare (${selected.size}/2)" else "${items.size} test salvati",
                        style = MaterialTheme.typography.bodyMedium, color = if (selecting) Palette.AccentSoft else Palette.TextDim,
                    )
                }
                if (items.size >= 2) {
                    IconButton(onClick = { selecting = !selecting; selected = emptyList() }) {
                        Icon(Icons.AutoMirrored.Outlined.CompareArrows, "Confronta", tint = if (selecting) Palette.Accent else Palette.TextDim)
                    }
                }
                if (items.isNotEmpty() && !selecting) {
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, "Svuota storico", tint = Palette.TextDim) }
                }
            }
        }
        if (selecting && selected.size == 2) {
            item {
                TextButton(
                    onClick = { onCompare(selected[0], selected[1]); selecting = false; selected = emptyList() },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Accent),
                ) { Text("Confronta i 2 test selezionati", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(6.dp)) }
            }
        }
        if (items.isEmpty()) {
            item { EmptyState(Icons.Outlined.History, "Nessun test ancora", "Ogni test che esegui viene salvato qui automaticamente, con grafici e statistiche, per poterlo rivedere, esportare o confrontare.") }
        }
        // I test di più schede eseguiti insieme vengono raggruppati in un unico blocco.
        val entries = ArrayList<Pair<String?, List<TestSummary>>>()
        val batchIndex = HashMap<String, Int>()
        items.forEach { s ->
            val b = s.batchId
            if (b == null) entries.add(null to listOf(s))
            else {
                val i = batchIndex[b]
                if (i == null) { batchIndex[b] = entries.size; entries.add(b to listOf(s)) }
                else entries[i] = b to (entries[i].second + s)
            }
        }
        var lastDay = ""
        entries.forEach { (batch, group) ->
            val first = group.first()
            val day = Format.dateTime(first.startedAt).substring(0, 10)
            if (day != lastDay) {
                lastDay = day
                item(key = "h$day") {
                    Text(day, style = MaterialTheme.typography.labelLarge, color = Palette.TextFaint, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
                }
            }
            fun rowFor(s: TestSummary, indent: Boolean) {
                item(key = s.id) {
                    val isSel = s.id in selected
                    HistoryRow(s, selecting, isSel, indent,
                        onClick = { if (selecting) selected = if (isSel) selected - s.id else (selected + s.id).takeLast(2) else onOpen(s.id) },
                        onLongClick = { selecting = true; selected = listOf(s.id) })
                }
            }
            if (batch == null) rowFor(first, false)
            else {
                val open = batch in expanded
                item(key = "b$batch") {
                    BatchCard(group, open) { expanded = if (open) expanded - batch else expanded + batch }
                }
                if (open) group.sortedBy { it.displayName.lowercase() }.forEach { rowFor(it, true) }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Palette.Surface2,
            title = { Text("Svuotare lo storico?") },
            text = { Text("Tutti i test salvati verranno eliminati definitivamente.") },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Elimina tutto", color = Palette.Red) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(s: TestSummary, selecting: Boolean, isSel: Boolean, indent: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val border by animateColorAsState(if (isSel) Palette.Accent else Palette.Outline, label = "sel")
    Row(
        Modifier
            .padding(start = if (indent) 18.dp else 0.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Surface)
            .border(1.dp, border, RoundedCornerShape(22.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(if (isSel) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                tint = if (isSel) Palette.Accent else Palette.TextFaint)
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(resultColor(s)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Format.time(s.startedAt)} · ${if (s.mode == PingMode.CONTINUOUS) "continuo" else "singolo"} · ${Format.duration(s.durationMs)}" +
                    if (s.deviceName != null) " · ${s.target}" else "",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(s.avgMs?.let { "${Format.ms(it)} ms" } ?: "—", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = s.avgMs?.let { latencyColor(it) } ?: Palette.Red)
            Text("${s.received}/${s.sent} · ${Format.percent(s.lossPercent)} persi", style = MaterialTheme.typography.bodySmall,
                color = if (s.lost > 0) Palette.Amber else Palette.TextDim)
        }
    }
}

/** Blocco riassuntivo di un test di più schede nello storico. */
@Composable
private fun BatchCard(group: List<TestSummary>, open: Boolean, onToggle: () -> Unit) {
    val first = group.first()
    val ok = group.count { it.received > 0 && it.lost == 0 }
    val warn = group.count { it.received > 0 && it.lost > 0 }
    val none = group.count { it.received == 0 }
    val color = when { none > 0 -> Palette.Red; warn > 0 -> Palette.Amber; else -> Palette.Green }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Surface)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(22.dp)).clickable(onClick = onToggle).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(first.batchName ?: "Più schede", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Format.time(first.startedAt)} · ${group.size} schede · ${if (first.mode == PingMode.CONTINUOUS) "continuo" else "singolo"} · ${Format.duration(first.durationMs)}",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ExpandMore, if (open) "Chiudi" else "Apri", tint = Palette.TextDim, modifier = Modifier.rotate(if (open) 180f else 0f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ok > 0) Pill("$ok OK", Palette.Green)
            if (warn > 0) Pill("$warn con perdite", Palette.Amber)
            if (none > 0) Pill("$none senza risposta", Palette.Red)
        }
    }
}

@Composable
fun BackHeader(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim) }
        }
        actions()
    }
}

@Composable
fun HistoryDetailScreen(vm: MainViewModel, id: String, onBack: () -> Unit, onRepeat: (TestSummary) -> Unit, toast: (String) -> Unit) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<TestDetail?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { detail = vm.detail(id) }
    val d = detail
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BackHeader(d?.summary?.displayName ?: "Dettaglio test", d?.summary?.let { Format.dateTimeFull(it.startedAt) }, onBack) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Elimina", tint = Palette.TextDim) }
            }
        }
        if (d == null) return@LazyColumn
        val s = d.summary
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(resultLabel(s), resultColor(s))
                    Spacer(Modifier.width(8.dp))
                    Pill(if (s.mode == PingMode.CONTINUOUS) "Continuo (-t)" else "Singolo (${s.count})", Palette.AccentSoft)
                    if (s.outages > 0) { Spacer(Modifier.width(8.dp)); Pill("${s.outages} interruz.", Palette.Red) }
                }
                Spacer(Modifier.height(14.dp))
                InfoLine("Indirizzo", if (s.target != s.resolvedIp) "${s.target} [${s.resolvedIp}]" else s.resolvedIp)
                InfoLine("Durata", Format.duration(s.durationMs))
                s.network?.let { InfoLine("Rete telefono", it) }
                InfoLine("Parametri", "${s.payloadSize} byte · timeout ${s.timeoutMs} ms · TTL ${s.ttl}")
                s.engine?.let { InfoLine("Motore", it) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Media", Format.msOrDash(s.avgMs), "ms", Modifier.weight(1f), help = HelpTopic.AVG)
                    StatTile("Minima", Format.msOrDash(s.minMs), "ms", Modifier.weight(1f), help = HelpTopic.MIN)
                    StatTile("Massima", Format.msOrDash(s.maxMs), "ms", Modifier.weight(1f), help = HelpTopic.MAX)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Inviati", s.sent.toString(), null, Modifier.weight(1f), help = HelpTopic.SENT)
                    StatTile("Persi", s.lost.toString(), null, Modifier.weight(1f), if (s.lost > 0) Palette.Red else Palette.Text, HelpTopic.LOST)
                    StatTile("Perdita", Format.percent(s.lossPercent), null, Modifier.weight(1f), if (s.lost > 0) Palette.Amber else Palette.Green, HelpTopic.LOSS_PERCENT)
                }
            }
        }
        item {
            AppCard {
                SectionHeader("Andamento latenza", help = HelpTopic.CHART)
                Spacer(Modifier.height(14.dp))
                LatencyChart(downsample(d.samples, 600), avgMs = s.avgMs)
            }
        }
        item {
            AppCard {
                SectionHeader("Azioni")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(Icons.Outlined.ContentCopy, "Copia", Modifier.weight(1f)) {
                        Export.copy(context, "Risultati PING", Export.windowsStyle(s, d.samples)); toast("Risultati copiati negli appunti")
                    }
                    ActionTile(Icons.Outlined.IosShare, "Testo", Modifier.weight(1f)) { Export.share(context, s, d.samples, asCsv = false) }
                    ActionTile(Icons.Outlined.TableChart, "CSV", Modifier.weight(1f)) { Export.share(context, s, d.samples, asCsv = true) }
                    ActionTile(Icons.Outlined.Refresh, "Ripeti", Modifier.weight(1f)) { onRepeat(s) }
                }
            }
        }
        item { EventsCard(d.events) }
        item {
            AppCard(padding = PaddingValues(vertical = 18.dp, horizontal = 20.dp)) {
                var expanded by remember { mutableStateOf(false) }
                SectionHeader("Pacchetti (${d.samples.size})", help = HelpTopic.TTL)
                val list = if (expanded) d.samples.take(2000) else d.samples.take(10)
                list.forEach { PacketRow(it, s.resolvedIp, 100) }
                if (d.samples.size > 10) {
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (expanded) "Mostra meno" else "Mostra tutti")
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.Surface2,
            title = { Text("Eliminare questo test?") },
            confirmButton = { TextButton(onClick = { vm.deleteRecord(id); confirmDelete = false; onBack() }) { Text("Elimina", color = Palette.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
    }
}

@Composable
fun CompareScreen(vm: MainViewModel, idA: String, idB: String, onBack: () -> Unit) {
    var a by remember { mutableStateOf<TestDetail?>(null) }
    var b by remember { mutableStateOf<TestDetail?>(null) }
    LaunchedEffect(idA, idB) { a = vm.detail(idA); b = vm.detail(idB) }
    val colorA = Palette.Accent
    val colorB = Palette.Amber
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BackHeader("Confronto test", null, onBack) }
        val da = a; val db = b
        if (da == null || db == null) return@LazyColumn
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(da to colorA, db to colorB).forEach { (d, c) ->
                    AppCard(Modifier.weight(1f), padding = PaddingValues(14.dp), borderColor = c.copy(alpha = 0.4f)) {
                        Box(Modifier.size(width = 24.dp, height = 4.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.height(8.dp))
                        Text(d.summary.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Format.dateTime(d.summary.startedAt), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                    }
                }
            }
        }
        item {
            AppCard {
                SectionHeader("Latenza sovrapposta", help = HelpTopic.CHART)
                Spacer(Modifier.height(14.dp))
                LatencyChart(
                    downsample(da.samples, 300), lineColor = colorA,
                    extraSeries = listOf(ChartSeries(downsample(db.samples, 300), colorB)),
                )
                Text("I due test sono allineati per numero di pacchetto.", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            AppCard {
                SectionHeader("Valori a confronto")
                Spacer(Modifier.height(8.dp))
                val sa = da.summary; val sb = db.summary
                CompareRow("Media", sa.avgMs, sb.avgMs, colorA, colorB, lowerIsBetter = true, unit = "ms")
                CompareRow("Minima", sa.minMs, sb.minMs, colorA, colorB, true, "ms")
                CompareRow("Massima", sa.maxMs, sb.maxMs, colorA, colorB, true, "ms")
                CompareRow("Jitter", sa.jitterMs, sb.jitterMs, colorA, colorB, true, "ms")
                CompareRow("Perdita", sa.lossPercent, sb.lossPercent, colorA, colorB, true, "%")
                CompareRow("Inviati", sa.sent.toDouble(), sb.sent.toDouble(), colorA, colorB, null, "")
                CompareRow("Persi", sa.lost.toDouble(), sb.lost.toDouble(), colorA, colorB, true, "")
                CompareRow("Interruzioni", sa.outages.toDouble(), sb.outages.toDouble(), colorA, colorB, true, "")
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, a: Double?, b: Double?, ca: Color, cb: Color, lowerIsBetter: Boolean?, unit: String) {
    fun fmt(v: Double?) = when {
        v == null -> "—"
        unit == "ms" -> Format.ms(v) + " ms"
        unit == "%" -> Format.percent(v)
        else -> v.toLong().toString()
    }
    val better = if (lowerIsBetter == null || a == null || b == null || a == b) 0 else if ((a < b) == lowerIsBetter) 1 else 2
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, modifier = Modifier.weight(1f))
        Text(fmt(a), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = if (better == 1) ca else Palette.Text, fontWeight = if (better == 1) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(96.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text(fmt(b), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = if (better == 2) cb else Palette.Text, fontWeight = if (better == 2) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(96.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
