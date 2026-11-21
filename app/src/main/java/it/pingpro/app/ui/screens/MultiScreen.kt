package it.pingpro.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pingpro.app.data.AppSettings
import it.pingpro.app.data.MultiTarget
import it.pingpro.app.data.TargetGroup
import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ping.MultiState
import it.pingpro.app.ping.PingConfig
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ping.SessionState
import it.pingpro.app.ping.TargetState
import it.pingpro.app.ui.Format
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.components.HelpButton
import it.pingpro.app.ui.components.HelpTopic
import it.pingpro.app.ui.components.PacketStrip
import it.pingpro.app.ui.components.SectionHeader
import it.pingpro.app.ui.components.SegmentedToggle
import it.pingpro.app.ui.components.tourTarget
import it.pingpro.app.ui.theme.Mono
import it.pingpro.app.ui.theme.Palette
import it.pingpro.app.ui.theme.latencyColor
import kotlinx.coroutines.delay

fun healthColor(h: TargetState.Health): Color = when (h) {
    TargetState.Health.OK -> Palette.Green
    TargetState.Health.WARN -> Palette.Amber
    TargetState.Health.DOWN -> Palette.Red
    TargetState.Health.WAITING -> Palette.TextFaint
}

/** Selettore in cima alla schermata Test: una scheda o più schede insieme. */
@Composable
fun SingleMultiSwitch(multi: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SegmentedToggle(
            listOf("Una scheda", "Più schede"), if (multi) 1 else 0, { onChange(it == 1) },
            modifier = Modifier.weight(1f).tourTarget("multi"), enabled = enabled,
        )
        HelpButton(HelpTopic.MULTI, size = 18.dp)
    }
}

/** Converte lo stato di una scheda nel formato usato dai riquadri del test singolo, per riutilizzarli. */
fun TargetState.asSession(m: MultiState): SessionState = SessionState(
    running = m.running && !finished, config = PingConfig(target.address, m.mode, m.count), deviceName = name, resolvedIp = ip,
    network = network, samples = recent, events = events, stats = stats,
    status = when {
        error != null -> LinkStatus.ERROR
        !m.running || finished -> LinkStatus.FINISHED
        stats.sent == 0 -> LinkStatus.STARTING
        else -> status
    },
    consecutiveLost = consecutiveLost, outages = outages, startedAt = m.startedAt, endedAt = m.endedAt, error = error,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiScreen(
    state: MultiState,
    targets: List<MultiTarget>,
    listName: String?,
    groups: List<TargetGroup>,
    runMode: PingMode,
    settings: AppSettings,
    singleRunning: Boolean,
    onRunMode: (PingMode) -> Unit,
    onSwitchSingle: () -> Unit,
    onEdit: () -> Unit,
    onLoadGroup: (TargetGroup) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onPingSingle: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: (Boolean) -> Unit,
    lan: it.pingpro.app.ping.NetInterfaceInfo? = null,
) {
    var sortProblems by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<String?>(null) }
    val hasRun = !state.isIdle

    // Orologio che fa aggiornare la durata nel titolo durante il test.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.running) { while (state.running) { tick = System.currentTimeMillis(); delay(500) } }
    // Schede fuori dalla rete a cui è collegato ora il telefono (es. elenco fatto su un'altra rete).
    val offNet = remember(targets, lan?.ipv4, lan?.prefixLength) {
        val ip = lan?.ipv4; val pfx = lan?.prefixLength
        if (ip == null || pfx == null) emptyList()
        else targets.filter { t -> it.pingpro.app.ping.IpConfig.toInt(t.address) != null && !it.pingpro.app.ping.IpConfig.sameSubnet(t.address, ip, pfx) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 22.dp, start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (hasRun) (state.groupName ?: "Più schede") else "Test", style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (hasRun) "${state.targets.size} schede · ${if (state.mode == PingMode.CONTINUOUS) "continuo" else "singolo"} · ${tick.let { Format.clock(elapsedOf(state)) }}"
                        else "Controlla più schede insieme",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                    )
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Impostazioni", tint = Palette.TextDim) }
            }
        }
        item { SingleMultiSwitch(true, !state.running && !singleRunning) { if (!it) onSwitchSingle() } }
        if (offNet.isNotEmpty() && lan?.ipv4 != null) item { OffNetworkWarning(offNet.size, targets.size, lan) }

        if (!state.running) {
            item {
            AppCard {
                SectionHeader("Schede da controllare", help = HelpTopic.MULTI) {
                    if (!state.running) Row(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.Surface2).clickable(onClick = onEdit).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Edit, null, tint = Palette.AccentSoft, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (targets.isEmpty()) "Scegli" else "Modifica", style = MaterialTheme.typography.labelMedium, color = Palette.AccentSoft)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (targets.isEmpty()) {
                    Text("Nessuna scheda nell'elenco. Tocca «Scegli» per aggiungere le schede salvate, un intervallo di indirizzi (es. da 192.168.1.100 a 192.168.1.120) o un elenco incollato.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Text("${targets.size}", style = MaterialTheme.typography.titleLarge, color = Palette.AccentSoft)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(listName ?: "${targets.size} schede", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(compactList(targets), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (groups.isNotEmpty() && !state.running) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups, key = { it.id }) { g ->
                            val sel = g.name == listName
                            Row(
                                Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(if (sel) Palette.Accent.copy(alpha = 0.18f) else Palette.Surface2)
                                    .border(1.dp, if (sel) Palette.Accent.copy(alpha = 0.5f) else Palette.Outline, RoundedCornerShape(4.dp))
                                    .clickable { onLoadGroup(g) }.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Folder, null, tint = if (sel) Palette.AccentSoft else Palette.TextDim, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${g.name} · ${g.targets.size}", style = MaterialTheme.typography.labelMedium, color = if (sel) Palette.Text else Palette.TextDim)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Modalità", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                    HelpButton(HelpTopic.MODE, size = 16.dp)
                }
                Spacer(Modifier.height(6.dp))
                SegmentedToggle(listOf("Singolo · ${settings.count}", "Continuo · -t"), if (runMode == PingMode.SINGLE) 0 else 1,
                    { onRunMode(if (it == 0) PingMode.SINGLE else PingMode.CONTINUOUS) }, enabled = !state.running)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Come aprire ${targets.size.coerceAtLeast(1)} finestre «ping${if (runMode == PingMode.CONTINUOUS) " -t" else ""}» insieme",
                    fontFamily = Mono, fontSize = 13.sp, color = Palette.TextFaint, modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        }
        if (state.running) {
            item { MultiSummary(state) }
        }
        item {
            StartStopButton(
                running = state.running,
                enabled = state.running || (targets.isNotEmpty() && !singleRunning),
                progress = if (state.running && state.mode == PingMode.SINGLE && state.targets.isNotEmpty())
                    state.targets.sumOf { it.stats.sent }.toFloat() / (state.targets.size * state.count) else null,
                onStart = onStart, onStop = onStop,
            )
        }
        if (hasRun) {
            if (!state.running) item { MultiSummary(state) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("Schede", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (sortProblems) "Problemi prima" else "Ordine elenco", style = MaterialTheme.typography.labelMedium, color = Palette.AccentSoft,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.Surface2).clickable { sortProblems = !sortProblems }.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            val list = if (sortProblems) state.targets.sortedBy {
                when (it.health) { TargetState.Health.DOWN -> 0; TargetState.Health.WARN -> 1; TargetState.Health.WAITING -> 2; TargetState.Health.OK -> 3 }
            } else state.targets
            item {
                AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    list.forEachIndexed { idx, t ->
                        TargetRow(t, settings, last = idx == list.lastIndex) { detail = t.target.address }
                    }
                }
            }
            if (!state.running) {
                item {
                    AppCard {
                        SectionHeader("Risultati")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionTile(Icons.Outlined.ContentCopy, "Copia", Modifier.weight(1f), onCopy)
                            ActionTile(Icons.Outlined.IosShare, "Testo", Modifier.weight(1f)) { onShare(false) }
                            ActionTile(Icons.Outlined.TableChart, "CSV", Modifier.weight(1f)) { onShare(true) }
                            ActionTile(Icons.Outlined.Refresh, "Ripeti", Modifier.weight(1f), onStart)
                        }
                        Text("Ogni scheda è salvata anche nello Storico, raggruppata con le altre di questo test.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }

    val open = detail?.let { a -> state.targets.firstOrNull { it.target.address == a } }
    if (open != null) {
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Palette.Bg,
        ) {
            val s = open.asSession(state)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(open.label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
                StatusHero(s, settings)
                StatsGrid(s, settings)
                ChartCard(s)
                EventsCard(s.events)
                if (!state.running) {
                    Row(
                        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Accent)
                            .clickable { detail = null; onPingSingle(open.target.address) },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.NetworkPing, null, tint = Palette.OnAccent)
                        Spacer(Modifier.width(8.dp))
                        Text("Test solo su questa scheda", color = Palette.OnAccent, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Descrizione breve dell'elenco: "192.168.1.100 – 192.168.1.111" se è un intervallo, altrimenti i primi nomi. */
fun compactList(targets: List<MultiTarget>): String {
    if (targets.isEmpty()) return ""
    val labels = targets.map { it.name ?: it.address }
    return if (labels.size <= 4) labels.joinToString(", ") else labels.take(3).joinToString(", ") + " e altre ${labels.size - 3}"
}

fun elapsedOf(m: MultiState): Long = ((m.endedAt ?: System.currentTimeMillis()) - (m.startedAt ?: System.currentTimeMillis())).coerceAtLeast(0)

@Composable
private fun MultiSummary(m: MultiState) {
    val down = m.targets.filter { it.health == TargetState.Health.DOWN }
    val total = m.targets.size.coerceAtLeast(1)
    AppCard(padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                val track = Palette.Surface2; val g = Palette.Green; val a = Palette.Amber; val r = Palette.Red
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 11.dp.toPx()
                    val inset = sw / 2
                    val arc = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
                    val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(sw))
                    var start = -90f
                    listOf(m.okCount to g, m.warnCount to a, m.downCount to r).forEach { (n, c) ->
                        if (n > 0) {
                            val sweep = 360f * n / total
                            drawArc(c, start + 1.5f, (sweep - 3f).coerceAtLeast(1f), false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                            start += sweep
                        }
                    }
                }
                Text("${m.okCount}/${m.targets.size}", style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendLine(Palette.Green, "${m.okCount} " + if (m.okCount == 1) "risponde" else "rispondono", m.okCount > 0)
                LegendLine(Palette.Amber, "${m.warnCount} " + if (m.warnCount == 1) "instabile" else "instabili", m.warnCount > 0)
                LegendLine(Palette.Red, "${m.downCount} " + if (m.downCount == 1) "non risponde" else "non rispondono", m.downCount > 0)
                if (m.waitingCount > 0) LegendLine(Palette.TextFaint, "${m.waitingCount} in attesa", true)
            }
        }
        AnimatedVisibility(down.isNotEmpty()) {
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Palette.DangerBg).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.WarningAmber, null, tint = Palette.Red, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text((if (down.size == 1) "Non risponde: " else "Non rispondono: ") + down.joinToString(", ") { it.label },
                    style = MaterialTheme.typography.titleSmall, color = Palette.OnDangerBg)
            }
        }
        if (!m.running && down.isEmpty() && m.warnCount == 0 && m.waitingCount == 0) {
            Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Palette.GreenBg).padding(14.dp)) {
                Text("Tutte le schede hanno risposto a ogni richiesta.", style = MaterialTheme.typography.titleSmall, color = Palette.Green)
            }
        }
    }
}

@Composable
private fun LegendLine(color: Color, text: String, strong: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (strong) (if (color == Palette.Red) Palette.Red else Palette.Text) else Palette.TextDim,
            fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.SemiBold)
    }
}

@Composable
private fun TargetRow(t: TargetState, settings: AppSettings, last: Boolean, onClick: () -> Unit) {
    val c = healthColor(t.health)
    val bg = when (t.health) {
        TargetState.Health.OK -> Palette.GreenBg
        TargetState.Health.WARN -> Palette.AmberBg
        TargetState.Health.DOWN -> Palette.DangerBg
        TargetState.Health.WAITING -> Palette.Surface2
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(4.dp)).background(bg), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    t.error ?: ((if (t.name != null) "${t.ip ?: t.target.address} · " else "") + (if (t.stats.sent == 0) (if (!t.finished) "in attesa della prima risposta…" else "nessuna richiesta") else "${t.stats.received}/${t.stats.sent} · ${Format.percent(t.stats.lossPercent)} persi")),
                    style = MaterialTheme.typography.bodySmall, color = if (t.stats.lost > 0 || t.error != null) Palette.Amber else Palette.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            val lastS = t.recent.lastOrNull()
            when {
                t.error != null -> Text("Errore", style = MaterialTheme.typography.titleSmall, color = Palette.Red)
                t.stats.sent == 0 -> Text("···", style = MaterialTheme.typography.titleMedium, color = Palette.TextFaint)
                t.health == TargetState.Health.DOWN && (t.finished || lastS?.status?.isLost == true) ->
                    Text("Non risponde", style = MaterialTheme.typography.titleSmall, color = Palette.Red)
                t.finished && t.stats.avgMs != null -> MsValue(t.stats.avgMs!!, settings, "media")
                lastS?.rttMs != null -> MsValue(lastS.rttMs, settings, null)
                else -> Text("Perso", style = MaterialTheme.typography.titleSmall, color = Palette.Red)
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Outline))
    }
}

@Composable
private fun MsValue(v: Double, settings: AppSettings, caption: String?) {
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Format.ms(v), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = it.pingpro.app.ui.theme.Sans,
                color = if (v >= settings.highLatencyMs) Palette.Amber else Palette.Text)
            Text(" ms", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, modifier = Modifier.padding(bottom = 2.dp))
        }
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
    }
}

/** Avviso: alcune schede dell'elenco non sono nella rete a cui è collegato ora il telefono. */
@Composable
private fun OffNetworkWarning(off: Int, total: Int, lan: it.pingpro.app.ping.NetInterfaceInfo) {
    val range = it.pingpro.app.ping.IpConfig.hostRange(lan.ipv4!!, lan.prefixLength ?: 24)
    AppCard(color = Palette.AmberBg, borderColor = Palette.Amber) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Palette.Amber, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (off == total) "Queste schede sono su un'altra rete" else "$off schede su $total sono su un'altra rete",
                    style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                Spacer(Modifier.height(4.dp))
                Text("Il telefono ora è collegato alla rete ${lan.type} ${lan.ipv4}/${lan.prefixLength ?: 24}${range?.let { " (${it.first} – ${it.second})" } ?: ""}. " +
                    "Gli indirizzi fuori da questo intervallo quasi certamente non risponderanno: collegati alla rete delle schede, " +
                    "oppure rifai «Cerca dispositivi» su questa rete.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
        }
    }
}
