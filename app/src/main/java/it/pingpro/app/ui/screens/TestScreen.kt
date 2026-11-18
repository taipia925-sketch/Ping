package it.pingpro.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pingpro.app.data.AppSettings
import it.pingpro.app.data.Export
import it.pingpro.app.data.SavedDevice
import it.pingpro.app.data.TestSummary
import it.pingpro.app.ping.EventKind
import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ping.PingEvent
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ping.PingSample
import it.pingpro.app.ping.SessionState
import it.pingpro.app.ui.Format
import it.pingpro.app.ui.TestUi
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.components.HelpButton
import it.pingpro.app.ui.components.HelpTopic
import it.pingpro.app.ui.components.LatencyChart
import it.pingpro.app.ui.components.PacketStrip
import it.pingpro.app.ui.components.Pill
import it.pingpro.app.ui.components.SectionHeader
import it.pingpro.app.ui.components.SegmentedToggle
import it.pingpro.app.ui.components.StatTile
import it.pingpro.app.ui.theme.Mono
import it.pingpro.app.ui.theme.Palette
import it.pingpro.app.ui.theme.latencyColor
import it.pingpro.app.ui.theme.statusColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TestScreen(
    vm: TestUi,
    session: SessionState,
    devices: List<SavedDevice>,
    history: List<TestSummary>,
    settings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onSaveDevice: (String) -> Unit,
    toast: (String) -> Unit,
    onStartRequested: () -> Unit = {},
) {
    val context = LocalContext.current
    val hasSession = session.status != LinkStatus.IDLE

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp, start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PingPro", style = MaterialTheme.typography.headlineLarge)
                    Text("Test di comunicazione con le schede", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Impostazioni", tint = Palette.TextDim) }
            }
        }

        item {
            TargetCard(vm, session.running, devices, history, settings.count, onSaveDevice)
        }

        item {
            StartStopButton(
                running = session.running,
                enabled = vm.targetValid || session.running,
                progress = if (session.running && session.config?.mode == PingMode.SINGLE)
                    session.stats.sent.toFloat() / (session.config.count.coerceAtLeast(1)) else null,
                onStart = { onStartRequested(); vm.start() },
                onStop = { vm.stop() },
            )
        }

        if (hasSession) {
            item { StatusHero(session, settings) }
            item { StatsGrid(session, settings) }
            item { ChartCard(session) }
            item { StripCard(session, settings) }
            item { PacketsCard(session, settings) }
            item { EventsCard(session.events) }
            if (!session.running && session.lastRecordId != null) {
                item {
                    FinishedActions(
                        onCopy = {
                            val summary = history.firstOrNull { it.id == session.lastRecordId }
                            if (summary != null) {
                                Export.copy(context, "Risultati PING", Export.windowsStyle(summary, session.samples))
                                toast("Risultati copiati negli appunti")
                            }
                        },
                        onShare = {
                            history.firstOrNull { it.id == session.lastRecordId }?.let { Export.share(context, it, session.samples, asCsv = false) }
                        },
                        onRepeat = { onStartRequested(); vm.start() },
                        onDetail = { session.lastRecordId.let(onOpenRecord) },
                    )
                }
            }
        } else {
            item { IdleHint() }
        }
    }
}

@Composable
private fun TargetCard(
    vm: TestUi,
    running: Boolean,
    devices: List<SavedDevice>,
    history: List<TestSummary>,
    count: Int,
    onSaveDevice: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    val saved = vm.deviceFor(vm.target)
    val invalid = vm.target.isNotBlank() && !vm.targetValid
    AppCard {
        SectionHeader("Indirizzo della scheda", help = HelpTopic.ADDRESS)
        Spacer(Modifier.height(10.dp))
        val borderColor by animateColorAsState(if (invalid) Palette.Red.copy(alpha = 0.6f) else Palette.Outline, label = "b")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Surface2)
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Router, null, tint = Palette.AccentSoft)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (vm.target.isEmpty()) {
                    Text("192.168.1.100", style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Palette.TextFaint))
                }
                BasicTextField(
                    value = vm.target,
                    onValueChange = { v -> vm.target = v.filterNot { it == ' ' || it == '\n' } },
                    enabled = !running,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, color = if (running) Palette.TextDim else Palette.Text, fontFeatureSettings = "tnum"),
                    cursorBrush = SolidColor(Palette.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); vm.start() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (vm.target.isNotEmpty() && !running) {
                IconButton(onClick = { vm.target = "" }) { Icon(Icons.Outlined.Close, "Cancella", tint = Palette.TextDim) }
            }
            IconButton(onClick = { if (vm.targetValid) onSaveDevice(vm.target) }, enabled = vm.targetValid) {
                Icon(if (saved != null) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkAdd,
                    if (saved != null) "Scheda salvata" else "Salva scheda",
                    tint = if (saved != null) Palette.Teal else if (vm.targetValid) Palette.TextDim else Palette.TextFaint)
            }
        }
        AnimatedVisibility(invalid || saved != null) {
            Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (invalid) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = Palette.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Indirizzo non valido (esempio corretto: 192.168.1.100)", style = MaterialTheme.typography.bodySmall, color = Palette.Red)
                } else if (saved != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Teal))
                    Spacer(Modifier.width(8.dp))
                    Text(saved.name, style = MaterialTheme.typography.titleSmall, color = Palette.Teal)
                    if (saved.note.isNotBlank()) Text("  ·  ${saved.note}", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Scelte rapide: schede salvate e indirizzi usati di recente.
        val recent = remember(history, devices) {
            val savedAddr = devices.map { it.address.lowercase() }.toSet()
            history.map { it.target }.distinctBy { it.lowercase() }.filter { it.lowercase() !in savedAddr }.take(6)
        }
        val chips = devices.sortedByDescending { it.lastUsedAt ?: it.createdAt }.map { it.name to it.address } + recent.map { it to it }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips, key = { it.second + it.first }) { (label, addr) ->
                    val sel = addr.equals(vm.target, true)
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(if (sel) Palette.Accent.copy(alpha = 0.18f) else Palette.Surface2)
                            .border(1.dp, if (sel) Palette.Accent.copy(alpha = 0.5f) else Palette.Outline, CircleShape)
                            .clickable(enabled = !running) { vm.target = addr }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (label != addr) Icons.Outlined.Bookmark else Icons.Outlined.History, null,
                            tint = if (sel) Palette.AccentSoft else Palette.TextDim, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) Palette.Text else Palette.TextDim)
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
        SegmentedToggle(
            options = listOf("Singolo · $count", "Continuo · -t"),
            selected = if (vm.mode == PingMode.SINGLE) 0 else 1,
            onSelect = { vm.mode = if (it == 0) PingMode.SINGLE else PingMode.CONTINUOUS },
            enabled = !running,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "C:\\> " + vm.buildConfig().windowsCommand(),
            fontFamily = Mono, fontSize = 13.sp, color = Palette.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun StartStopButton(running: Boolean, enabled: Boolean, progress: Float?, onStart: () -> Unit, onStop: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.97f else 1f, label = "s")
    val brush = when {
        running -> Brush.horizontalGradient(listOf(Color(0xFFFF4D6A), Color(0xFFE0365A)))
        enabled -> Brush.horizontalGradient(listOf(Color(0xFF3D8BFF), Color(0xFF2F6BFF)))
        else -> SolidColor(Palette.Surface3)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled) {
                if (running) onStop() else onStart()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(68.dp).background(Color.White.copy(alpha = 0.12f)))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null,
                tint = if (enabled) Color.White else Palette.TextFaint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (running) "INTERROMPI" else "AVVIA TEST", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                color = if (enabled) Color.White else Palette.TextFaint)
        }
    }
}

private fun statusTitle(s: SessionState): String = when (s.status) {
    LinkStatus.STARTING -> "AVVIO…"
    LinkStatus.ONLINE -> "ONLINE"
    LinkStatus.SLOW -> "LENTA"
    LinkStatus.UNSTABLE -> if (s.consecutiveLost > 0) "PACCHETTO PERSO" else "INSTABILE"
    LinkStatus.DOWN -> "NON RISPONDE"
    LinkStatus.ERROR -> "ERRORE"
    LinkStatus.FINISHED -> when {
        s.stats.sent == 0 -> "TEST INTERROTTO"
        s.stats.received == 0 -> "NESSUNA RISPOSTA"
        s.stats.lost > 0 -> "COMPLETATO CON PERDITE"
        else -> "COMPLETATO · OK"
    }
    LinkStatus.IDLE -> ""
}

private fun statusSubtitle(s: SessionState): String = when (s.status) {
    LinkStatus.STARTING -> "Preparazione del test…"
    LinkStatus.ONLINE -> "La scheda risponde regolarmente"
    LinkStatus.SLOW -> "Risponde, ma con tempi elevati"
    LinkStatus.UNSTABLE -> if (s.consecutiveLost > 0) "Nessuna risposta all'ultima richiesta" else "Perdite di pacchetti negli ultimi secondi"
    LinkStatus.DOWN -> "${s.consecutiveLost} richieste consecutive senza risposta"
    LinkStatus.ERROR -> s.error ?: "Errore"
    LinkStatus.FINISHED -> "${s.stats.received} risposte su ${s.stats.sent} richieste"
    LinkStatus.IDLE -> ""
}

private fun finishedColor(s: SessionState): Color = when {
    s.status != LinkStatus.FINISHED -> statusColor(s.status)
    s.stats.sent == 0 -> Palette.TextDim
    s.stats.received == 0 -> Palette.Red
    s.stats.lost > 0 -> Palette.Amber
    else -> Palette.Green
}

@Composable
private fun StatusHero(s: SessionState, settings: AppSettings) {
    val color by animateColorAsState(finishedColor(s), tween(400), label = "hero")
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.running) {
        while (s.running) { now = System.currentTimeMillis(); delay(250) }
        now = System.currentTimeMillis()
    }
    val elapsed = ((s.endedAt ?: now) - (s.startedAt ?: now)).coerceAtLeast(0)
    val down = s.status == LinkStatus.DOWN
    val infinite = rememberInfiniteTransition(label = "pulse")
    val alarm by infinite.animateFloat(0.10f, 0.28f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alarm")

    AppCard(
        color = if (down) Color(0xFF2A0F17) else Palette.Surface,
        borderColor = if (down) Palette.Red.copy(alpha = 0.3f + alarm) else color.copy(alpha = 0.25f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusOrb(color = color, active = s.running, icon = when {
                s.status == LinkStatus.DOWN || s.status == LinkStatus.ERROR -> Icons.Outlined.WifiOff
                s.status == LinkStatus.UNSTABLE || s.status == LinkStatus.SLOW -> Icons.Outlined.WarningAmber
                s.status == LinkStatus.FINISHED -> if (s.stats.lost == 0 && s.stats.sent > 0) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber
                else -> Icons.Outlined.Wifi
            })
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(statusTitle(s), style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    HelpButton(HelpTopic.STATUS, size = 16.dp)
                }
                Text(statusSubtitle(s), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.running) "LATENZA ATTUALE" else "LATENZA MEDIA", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                    HelpButton(HelpTopic.LATENCY, size = 14.dp)
                }
                val shown = if (s.running) s.stats.lastMs else s.stats.avgMs
                val lastSample = s.samples.lastOrNull()
                Row(verticalAlignment = Alignment.Bottom) {
                    if (shown != null) {
                        Text(Format.ms(shown), style = MaterialTheme.typography.displayLarge,
                            color = latencyColor(shown, settings.highLatencyMs))
                        Spacer(Modifier.width(6.dp))
                        Text("ms", style = MaterialTheme.typography.titleLarge, color = Palette.TextDim, modifier = Modifier.padding(bottom = 12.dp))
                    } else {
                        Text(if (lastSample?.status?.isLost == true) "—" else "···", style = MaterialTheme.typography.displayLarge, color = Palette.TextFaint)
                        if (lastSample?.status?.isLost == true) {
                            Spacer(Modifier.width(10.dp))
                            Text(lastSample.message ?: "Richiesta scaduta", style = MaterialTheme.typography.bodyMedium, color = Palette.Red,
                                modifier = Modifier.padding(bottom = 14.dp))
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
                Text(Format.clock(elapsed), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = Palette.Text)
                val cfg = s.config
                Text(
                    if (cfg?.mode == PingMode.SINGLE) "${s.stats.sent} di ${cfg.count}" else "continuo",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
            }
        }
        // Destinazione e rete.
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill((s.deviceName?.let { "$it · " } ?: "") + (s.resolvedIp ?: s.config?.target ?: ""), Palette.AccentSoft, icon = Icons.Outlined.Router)
        }
        s.network?.let { n ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(n.label, if (n.sameSubnet) Palette.Teal else Palette.Amber, icon = if (n.type == "Ethernet") Icons.Outlined.Lan else Icons.Outlined.Wifi)
                HelpButton(HelpTopic.NETWORK, size = 14.dp)
            }
        }
        AnimatedVisibility(down, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val since = s.samples.takeLast(s.consecutiveLost).firstOrNull()?.timestamp
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Red.copy(alpha = alarm + 0.05f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.WarningAmber, null, tint = Palette.Red)
                Spacer(Modifier.width(10.dp))
                Text(
                    "La scheda non risponde" + (since?.let { " da ${Format.duration(now - it)} (dalle ${Format.time(it)})" } ?: ""),
                    style = MaterialTheme.typography.titleSmall, color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun StatusOrb(color: Color, active: Boolean, icon: ImageVector) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val wave by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "wave")
    val breathe by infinite.animateFloat(0.9f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            if (active) {
                drawCircle(color.copy(alpha = (1f - wave) * 0.45f), radius = r * (0.55f + 0.45f * wave), style = Stroke(2.dp.toPx()))
            }
            drawCircle(color.copy(alpha = 0.16f), radius = r * 0.62f * (if (active) breathe else 1f))
            drawCircle(color.copy(alpha = 0.9f), radius = r * 0.62f * (if (active) breathe else 1f), style = Stroke(1.5.dp.toPx()), center = Offset(r, r))
        }
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun StatsGrid(s: SessionState, settings: AppSettings) {
    val st = s.stats
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Media", Format.msOrDash(st.avgMs), "ms", Modifier.weight(1f), latencyColor(st.avgMs, settings.highLatencyMs).takeIf { st.avgMs != null } ?: Palette.Text, HelpTopic.AVG)
            StatTile("Minima", Format.msOrDash(st.minMs), "ms", Modifier.weight(1f), help = HelpTopic.MIN)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Massima", Format.msOrDash(st.maxMs), "ms", Modifier.weight(1f), help = HelpTopic.MAX)
            StatTile("Jitter", Format.msOrDash(st.jitterMs), "ms", Modifier.weight(1f), help = HelpTopic.JITTER)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Inviati", st.sent.toString(), null, Modifier.weight(1f), help = HelpTopic.SENT)
            StatTile("Ricevuti", st.received.toString(), null, Modifier.weight(1f), if (st.received > 0) Palette.Green else Palette.Text, HelpTopic.RECEIVED)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Persi", st.lost.toString(), null, Modifier.weight(1f), if (st.lost > 0) Palette.Red else Palette.Text, HelpTopic.LOST)
            StatTile("Perdita", Format.percent(st.lossPercent), null, Modifier.weight(1f),
                when { st.sent == 0 -> Palette.Text; st.lossPercent == 0.0 -> Palette.Green; st.lossPercent < 5 -> Palette.Amber; else -> Palette.Red },
                HelpTopic.LOSS_PERCENT)
        }
    }
}

@Composable
private fun ChartCard(s: SessionState) {
    var all by remember { mutableStateOf(false) }
    AppCard {
        SectionHeader("Andamento latenza", help = HelpTopic.CHART) {
            Row(Modifier.clip(CircleShape).background(Palette.Surface2).padding(3.dp)) {
                listOf("Ultimi 60" to false, "Tutto" to true).forEach { (label, v) ->
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        color = if (all == v) Palette.Text else Palette.TextDim,
                        modifier = Modifier.clip(CircleShape).background(if (all == v) Palette.Surface3 else Color.Transparent)
                            .clickable { all = v }.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val data = if (all) downsample(s.samples, 600) else s.samples.takeLast(60)
        LatencyChart(data, avgMs = s.stats.avgMs)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(Palette.Accent, "Latenza")
            Legend(Palette.Teal, "Media", dashed = true)
            Legend(Palette.Red, "Perso")
        }
    }
}

/** Riduce i punti per grafici molto lunghi conservando sempre i pacchetti persi e i picchi. */
fun downsample(samples: List<PingSample>, maxPoints: Int): List<PingSample> {
    if (samples.size <= maxPoints) return samples
    val bucket = (samples.size + maxPoints - 1) / maxPoints
    return samples.chunked(bucket).map { chunk ->
        chunk.firstOrNull { it.status.isLost } ?: chunk.maxBy { it.rttMs ?: 0.0 }
    }
}

@Composable
private fun Legend(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (dashed) 12.dp else 8.dp, if (dashed) 2.dp else 8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
    }
}

@Composable
private fun StripCard(s: SessionState, settings: AppSettings) {
    AppCard {
        SectionHeader("Mappa pacchetti", help = HelpTopic.STRIP) {
            Text("ultimi 40", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        Spacer(Modifier.height(12.dp))
        PacketStrip(s.samples, highLatency = settings.highLatencyMs)
    }
}

@Composable
private fun PacketsCard(s: SessionState, settings: AppSettings) {
    var expanded by remember { mutableStateOf(false) }
    val list = s.samples.asReversed()
    val shown = if (expanded) list.take(500) else list.take(8)
    AppCard(padding = PaddingValues(vertical = 18.dp, horizontal = 20.dp)) {
        SectionHeader("Pacchetti", help = HelpTopic.TTL) {
            Text("${s.stats.received} ricevuti · ${s.stats.lost} persi", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            Text("In attesa della prima risposta…", style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint, modifier = Modifier.padding(vertical = 8.dp))
        }
        shown.forEach { PacketRow(it, s.resolvedIp ?: "", settings.highLatencyMs) }
        if (list.size > 8) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (expanded) "Mostra meno" else "Mostra tutti (${list.size})")
            }
        }
    }
}

@Composable
fun PacketRow(p: PingSample, ip: String, high: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        val c = if (p.status.isLost) Palette.Red else latencyColor(p.rttMs, high)
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(12.dp))
        Text("#${p.seq}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Palette.TextFaint, modifier = Modifier.width(52.dp))
        Column(Modifier.weight(1f)) {
            if (p.status.isLost) {
                Text(p.message ?: "Richiesta scaduta.", style = MaterialTheme.typography.bodyMedium, color = Palette.Red)
            } else {
                Text("Risposta da $ip", style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("byte=${p.bytes}" + (p.ttl?.let { " · TTL=$it" } ?: "") + " · " + Format.time(p.timestamp),
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            }
        }
        if (!p.status.isLost) {
            Text(Format.ms(p.rttMs ?: 0.0), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = c)
            Text(" ms", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        } else {
            Text(Format.time(p.timestamp), style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
    }
}

@Composable
fun EventsCard(events: List<PingEvent>, title: String = "Cronologia eventi") {
    var expanded by remember { mutableStateOf(false) }
    val list = events.asReversed()
    val shown = if (expanded) list else list.take(6)
    AppCard {
        SectionHeader(title, help = HelpTopic.EVENTS)
        Spacer(Modifier.height(10.dp))
        shown.forEachIndexed { i, e -> EventRow(e, last = i == shown.lastIndex) }
        if (list.size > 6) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (expanded) "Mostra meno" else "Mostra tutti (${list.size})")
            }
        }
    }
}

@Composable
private fun EventRow(e: PingEvent, last: Boolean) {
    val color = when (e.kind) {
        EventKind.START, EventKind.STOP -> Palette.AccentSoft
        EventKind.INFO -> Palette.Amber
        EventKind.LOSS -> Palette.Amber
        EventKind.OUTAGE_START, EventKind.ERROR -> Palette.Red
        EventKind.OUTAGE_END -> Palette.Green
        EventKind.SPIKE -> Palette.Violet
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp).fillMaxHeight()) {
            Spacer(Modifier.height(5.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            if (!last) Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(Palette.OutlineStrong))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
            Row {
                Text(e.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
                Text(Format.time(e.timestamp), style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            }
            e.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim) }
        }
    }
}

@Composable
private fun FinishedActions(onCopy: () -> Unit, onShare: () -> Unit, onRepeat: () -> Unit, onDetail: () -> Unit) {
    AppCard {
        SectionHeader("Risultati")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(Icons.Outlined.ContentCopy, "Copia", Modifier.weight(1f), onCopy)
            ActionTile(Icons.Outlined.IosShare, "Esporta", Modifier.weight(1f), onShare)
            ActionTile(Icons.Outlined.Refresh, "Ripeti", Modifier.weight(1f), onRepeat)
            ActionTile(Icons.Outlined.History, "Dettagli", Modifier.weight(1f), onDetail)
        }
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Palette.Surface2).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Palette.AccentSoft)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
    }
}

@Composable
private fun IdleHint() {
    AppCard(color = Palette.Surface.copy(alpha = 0.6f)) {
        Text("Come funziona", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        listOf(
            "1" to "Collega il telefono al Wi-Fi della scheda.",
            "2" to "Scrivi l'indirizzo IP della scheda (es. 192.168.1.100).",
            "3" to "Scegli Singolo (4 richieste) o Continuo (fino a quando premi Interrompi).",
            "4" to "Premi AVVIA TEST e guarda i risultati in tempo reale.",
        ).forEach { (n, t) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Palette.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text(n, style = MaterialTheme.typography.labelMedium, color = Palette.AccentSoft)
                }
                Spacer(Modifier.width(12.dp))
                Text(t, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
        }
    }
}
