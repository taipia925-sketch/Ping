package it.pingpro.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
) {
    var sortProblems by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<String?>(null) }
    val hasRun = !state.isIdle

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp, start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PingPro", style = MaterialTheme.typography.headlineLarge)
                    Text("Più schede insieme", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Impostazioni", tint = Palette.TextDim) }
            }
        }
        item { SingleMultiSwitch(true, !state.running && !singleRunning) { if (!it) onSwitchSingle() } }

        item {
            AppCard {
                SectionHeader("Schede da controllare", help = HelpTopic.MULTI) {
                    if (!state.running) Row(
                        Modifier.clip(CircleShape).background(Palette.Surface2).clickable(onClick = onEdit).padding(horizontal = 12.dp, vertical = 6.dp),
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
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
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
                                Modifier.clip(CircleShape)
                                    .background(if (sel) Palette.Accent.copy(alpha = 0.18f) else Palette.Surface2)
                                    .border(1.dp, if (sel) Palette.Accent.copy(alpha = 0.5f) else Palette.Outline, CircleShape)
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
            item { MultiSummary(state) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("Schede", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (sortProblems) "Problemi prima" else "Ordine elenco", style = MaterialTheme.typography.labelMedium, color = Palette.AccentSoft,
                        modifier = Modifier.clip(CircleShape).background(Palette.Surface2).clickable { sortProblems = !sortProblems }.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            val list = if (sortProblems) state.targets.sortedBy {
                when (it.health) { TargetState.Health.DOWN -> 0; TargetState.Health.WARN -> 1; TargetState.Health.WAITING -> 2; TargetState.Health.OK -> 3 }
            } else state.targets
            items(list.chunked(2), key = { row -> row.joinToString { it.target.address } }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t -> TargetTile(t, settings, Modifier.weight(1f)) { detail = t.target.address } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
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
                        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Accent)
                            .clickable { detail = null; onPingSingle(open.target.address) },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.NetworkPing, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Test solo su questa scheda", color = Color.White, fontWeight = FontWeight.Bold)
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

@Composable
private fun MultiSummary(m: MultiState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(m.running) {
        while (m.running) { now = System.currentTimeMillis(); delay(500) }
        now = System.currentTimeMillis()
    }
    val elapsed = ((m.endedAt ?: now) - (m.startedAt ?: now)).coerceAtLeast(0)
    val down = m.targets.filter { it.health == TargetState.Health.DOWN }
    val pulse by rememberInfiniteTransition(label = "md").animateFloat(0.10f, 0.26f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "mdp")
    AppCard(
        color = if (down.isNotEmpty() && m.running) Color(0xFF2A0F17) else Palette.Surface,
        borderColor = if (down.isNotEmpty()) Palette.Red.copy(alpha = 0.3f + if (m.running) pulse else 0f) else Palette.Outline,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    m.running -> "Test in corso"
                    down.isEmpty() && m.warnCount == 0 -> "Tutte le schede rispondono"
                    else -> "Test completato"
                },
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                color = if (!m.running && down.isEmpty() && m.warnCount == 0) Palette.Green else Palette.Text,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.clock(elapsed), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                Text(if (m.mode == PingMode.CONTINUOUS) "continuo" else "singolo · ${m.count} per scheda", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Counter("Rispondono", m.okCount, Palette.Green, Modifier.weight(1f))
            Counter("Instabili", m.warnCount, Palette.Amber, Modifier.weight(1f))
            Counter("Non rispondono", m.downCount, Palette.Red, Modifier.weight(1f))
        }
        // Barra proporzionale.
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Palette.Surface3)) {
            listOf(m.okCount to Palette.Green, m.warnCount to Palette.Amber, m.downCount to Palette.Red, m.waitingCount to Palette.Surface3).forEach { (n, c) ->
                if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxSize().background(c))
            }
        }
        AnimatedVisibility(down.isNotEmpty()) {
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Red.copy(alpha = 0.16f)).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.WarningAmber, null, tint = Palette.Red)
                Spacer(Modifier.width(10.dp))
                Text("Non rispondono: " + down.joinToString(", ") { it.label }, style = MaterialTheme.typography.titleSmall, color = Color.White)
            }
        }
    }
}

@Composable
private fun Counter(label: String, n: Int, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.10f)).padding(vertical = 12.dp, horizontal = 12.dp)) {
        Text("$n", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = if (n > 0) color else Palette.TextFaint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim, maxLines = 2, minLines = 2)
    }
}

@Composable
private fun TargetTile(t: TargetState, settings: AppSettings, modifier: Modifier, onClick: () -> Unit) {
    val c = healthColor(t.health)
    val border by animateColorAsState(c.copy(alpha = if (t.health == TargetState.Health.WAITING) 0.15f else 0.45f), label = "tb")
    val down = t.health == TargetState.Health.DOWN
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (down) Color(0xFF2A0F17) else Palette.Surface)
            .border(1.dp, border, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(8.dp))
            Text(t.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (t.name != null) Text(t.ip ?: t.target.address, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = Palette.TextFaint, maxLines = 1)
        else Text(" ", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        val last = t.recent.lastOrNull()
        Row(verticalAlignment = Alignment.Bottom) {
            when {
                t.error != null -> Text("Errore", style = MaterialTheme.typography.titleLarge, color = Palette.Red)
                t.stats.sent == 0 -> Text("···", style = MaterialTheme.typography.titleLarge, color = Palette.TextFaint)
                t.finished && t.stats.avgMs != null -> {
                    Text(Format.ms(t.stats.avgMs!!), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = latencyColor(t.stats.avgMs, settings.highLatencyMs))
                    Text(" ms media", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(bottom = 3.dp))
                }
                last?.rttMs != null && !t.finished -> {
                    Text(Format.ms(last.rttMs), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = latencyColor(last.rttMs, settings.highLatencyMs))
                    Text(" ms", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(bottom = 3.dp))
                }
                else -> Text(if (down) "Non risponde" else "Perso", style = MaterialTheme.typography.titleMedium, color = Palette.Red)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            t.error ?: "${t.stats.received}/${t.stats.sent} · ${Format.percent(t.stats.lossPercent)} persi",
            style = MaterialTheme.typography.bodySmall, color = if (t.stats.lost > 0 || t.error != null) Palette.Amber else Palette.TextDim,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        PacketStrip(t.recent, slots = 20, highLatency = settings.highLatencyMs, height = 10.dp)
    }
}

