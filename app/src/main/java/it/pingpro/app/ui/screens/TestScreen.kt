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
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
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
import it.pingpro.app.ping.NetInterfaceInfo
import it.pingpro.app.ping.PingEvent
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ping.PingSample
import it.pingpro.app.ping.SessionState
import it.pingpro.app.ui.Format
import it.pingpro.app.ui.TestUi
import it.pingpro.app.ui.components.AppCard
import androidx.compose.material3.ExperimentalMaterial3Api
import it.pingpro.app.ui.theme.Sans
import it.pingpro.app.ui.components.HelpSheet
import it.pingpro.app.ui.components.PillShape
import it.pingpro.app.ui.components.HelpButton
import it.pingpro.app.ui.components.HelpTopic
import it.pingpro.app.ui.components.LatencyChart
import it.pingpro.app.ui.components.PacketStrip
import it.pingpro.app.ui.components.Pill
import it.pingpro.app.ui.components.SectionHeader
import it.pingpro.app.ui.components.SegmentedToggle
import it.pingpro.app.ui.components.StatTile
import it.pingpro.app.ui.components.tourTarget
import it.pingpro.app.ui.theme.Mono
import it.pingpro.app.ui.components.screenHeader
import it.pingpro.app.ui.components.PanelTitle
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
    lan: NetInterfaceInfo? = null,
    onOpenNetwork: () -> Unit = {},
    onSwitchMulti: (() -> Unit)? = null,
    multiRunning: Boolean = false,
    onOpenScan: () -> Unit = {},
) {
    val context = LocalContext.current
    val hasSession = session.status != LinkStatus.IDLE

    val button: @Composable () -> Unit = {
        StartStopButton(
            running = session.running,
            enabled = (vm.targetValid && !multiRunning) || session.running,
            progress = if (session.running && session.config?.mode == PingMode.SINGLE)
                session.stats.sent.toFloat() / (session.config.count.coerceAtLeast(1)) else null,
            onStart = { onStartRequested(); vm.start() },
            onStop = { vm.stop() },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().screenHeader(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Test", style = MaterialTheme.typography.headlineLarge)
                    val cfg = session.config
                    Text(
                        if (hasSession && cfg != null) (session.deviceName?.let { "$it · " } ?: "") + (session.resolvedIp ?: cfg.target)
                        else "Controlla se una scheda risponde",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Impostazioni", tint = Palette.TextDim) }
            }
        }

        if (onSwitchMulti != null) item {
            SingleMultiSwitch(false, !session.running && !multiRunning) { if (it) onSwitchMulti() }
        }

        if (session.running) {
            item { StatusHero(session, settings) }
            item { StatsGrid(session, settings) }
            item { ChartCard(session) }
            item { button() }
            item { PacketsCard(session, settings) }
            item { EventsCard(session.events) }
        } else {
            item { TargetCard(vm, session.running, devices, history, settings.count, onSaveDevice, lan, onOpenNetwork, onOpenScan) }
            item { button() }
            if (hasSession) {
                item { StatusHero(session, settings) }
                item { StatsGrid(session, settings) }
                item { ChartCard(session) }
                if (session.lastRecordId != null) {
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
                item { PacketsCard(session, settings) }
                item { EventsCard(session.events) }
            } else {
                item { IdleHint() }
            }
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
    lan: NetInterfaceInfo?,
    onOpenNetwork: () -> Unit,
    onOpenScan: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val saved = vm.deviceFor(vm.target)
    val invalid = vm.target.isNotBlank() && !vm.targetValid
    AppCard {
        SectionHeader("Indirizzo della scheda", help = HelpTopic.ADDRESS)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.AccentBg).clickable(onClick = onOpenScan).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = Palette.AccentSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cerca dispositivi", style = MaterialTheme.typography.labelMedium, color = Palette.AccentSoft)
            }
            Row(
                Modifier.tourTarget("ipconfig").clip(RoundedCornerShape(4.dp)).background(Palette.Surface2).clickable(onClick = onOpenNetwork).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lan, null, tint = Palette.AccentSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("ipconfig", style = MaterialTheme.typography.labelMedium, color = Palette.AccentSoft)
            }
        }
        Spacer(Modifier.height(10.dp))
        val borderColor by animateColorAsState(if (invalid) Palette.Red.copy(alpha = 0.6f) else Palette.Outline, label = "b")
        Row(
            Modifier
                .fillMaxWidth()
                .tourTarget("address")
                .clip(RoundedCornerShape(4.dp))
                .background(Palette.Surface2)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
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
            IconButton(onClick = { if (vm.targetValid) onSaveDevice(vm.target) }, enabled = vm.targetValid, modifier = Modifier.tourTarget("save")) {
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
        val gateway = lan?.gateway
        if (chips.isNotEmpty() || gateway != null) {
            Spacer(Modifier.height(12.dp))
            LazyRow(Modifier.tourTarget("chips"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (gateway != null) item(key = "gw") {
                    val sel = gateway == vm.target
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (sel) Palette.Teal.copy(alpha = 0.22f) else Palette.Teal.copy(alpha = 0.10f))
                            .border(1.dp, Palette.Teal.copy(alpha = if (sel) 0.7f else 0.35f), RoundedCornerShape(4.dp))
                            .clickable(enabled = !running) { vm.target = gateway }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Router, null, tint = Palette.Teal, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gateway $gateway", style = MaterialTheme.typography.labelMedium, color = Palette.Text)
                    }
                }
                items(chips, key = { it.second + it.first }) { (label, addr) ->
                    val sel = addr.equals(vm.target, true)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (sel) Palette.Accent.copy(alpha = 0.18f) else Palette.Surface2)
                            .border(1.dp, if (sel) Palette.Accent.copy(alpha = 0.5f) else Palette.Outline, RoundedCornerShape(4.dp))
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
            modifier = Modifier.tourTarget("mode"),
            options = listOf("Singolo · $count", "Continuo · -t"),
            selected = if (vm.mode == PingMode.SINGLE) 0 else 1,
            onSelect = { vm.mode = if (it == 0) PingMode.SINGLE else PingMode.CONTINUOUS },
            enabled = !running,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "C:\\> " + vm.buildConfig().windowsCommand(),
            fontFamily = Mono, fontSize = 13.sp, color = Palette.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp).tourTarget("command"),
        )
    }
}

@Composable
fun StartStopButton(running: Boolean, enabled: Boolean, progress: Float?, onStart: () -> Unit, onStop: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.97f else 1f, label = "s")
    val bg by animateColorAsState(when { running -> Palette.Danger; enabled -> Palette.Accent; else -> Palette.Surface3 }, label = "btn")
    Box(
        Modifier
            .fillMaxWidth()
            .tourTarget("start")
            .height(60.dp)
            .scale(scale)
            .clip(PillShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled) {
                if (running) onStop() else onStart()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(60.dp).background(Palette.OnAccent.copy(alpha = 0.16f)))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null,
                tint = if (enabled) Palette.OnAccent else Palette.TextFaint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (running) "INTERROMPI TEST" else "AVVIA TEST", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Sans, letterSpacing = 1.8.sp,
                color = if (enabled) Palette.OnAccent else Palette.TextFaint)
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

/** Pillola di stato colorata: ONLINE, NON RISPONDE, … */
@Composable
fun StatusPill(text: String, color: Color) {
    val bg = when (color) {
        Palette.Green -> Palette.GreenBg
        Palette.Amber -> Palette.AmberBg
        Palette.Red -> Palette.DangerBg
        else -> Palette.Surface2
    }
    Row(Modifier.clip(RoundedCornerShape(3.dp)).background(bg).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}

@Composable
fun StatusHero(s: SessionState, settings: AppSettings) {
    val color = finishedColor(s)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.running) {
        while (s.running) { now = System.currentTimeMillis(); delay(250) }
        now = System.currentTimeMillis()
    }
    val elapsed = ((s.endedAt ?: now) - (s.startedAt ?: now)).coerceAtLeast(0)
    val down = s.status == LinkStatus.DOWN
    val infinite = rememberInfiniteTransition(label = "pulse")
    val alarm by infinite.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alarm")
    val cfg = s.config

    val heroBg by animateColorAsState(if (down) Palette.DangerBg else Palette.Surface, tween(450), label = "heroBg")
    AppCard(
        color = heroBg,
        borderColor = if (down) Palette.Red.copy(alpha = alarm) else Palette.Outline,
        padding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(statusTitle(s), color)
            HelpButton(HelpTopic.STATUS, size = 16.dp)
            Spacer(Modifier.weight(1f))
            Text(
                Format.clock(elapsed) + " · " + if (cfg?.mode == PingMode.SINGLE) "${s.stats.sent} di ${cfg.count}" else "continuo",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = Palette.TextDim,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!down) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (s.running) "Ultima risposta" else "Latenza media", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
            HelpButton(HelpTopic.LATENCY, size = 14.dp)
        }
        val shown = if (s.running) s.stats.lastMs else s.stats.avgMs
        val lastSample = s.samples.lastOrNull()
        Row(verticalAlignment = Alignment.Bottom) {
            if (shown != null) {
                Text(Format.ms(shown), style = MaterialTheme.typography.displayLarge, color = Palette.Text)
                Spacer(Modifier.width(6.dp))
                Text("ms", style = MaterialTheme.typography.headlineSmall, color = Palette.TextDim, modifier = Modifier.padding(bottom = 10.dp))
            } else if (!down) {
                Text(if (lastSample?.status?.isLost == true) "—" else "···", style = MaterialTheme.typography.displayLarge,
                    color = if (lastSample?.status?.isLost == true) Palette.Red else Palette.TextFaint)
            }
            if (down) {
                Spacer(Modifier.height(80.dp))
                // Il «timbro» rosso: si riconosce da lontano anche con il telefono appoggiato sul quadro.
                Text(
                    "NON RISPONDE", color = Palette.Red, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp,
                    letterSpacing = 2.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 12.dp).rotate(-5f)
                        .border(3.dp, Palette.Red, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val sentence = buildString {
            append(statusSubtitle(s))
            if (s.running && lastSample?.status?.isLost == true) append(": ").append((lastSample.message ?: "Richiesta scaduta.").trimEnd('.').lowercase())
            else if (s.running && s.stats.avgMs != null) append(". Media ").append(Format.ms(s.stats.avgMs!!)).append(" ms")
            append('.')
        }
        Text(sentence, style = MaterialTheme.typography.bodyLarge, color = if (down) Palette.OnDangerBg else Palette.TextDim)
        Spacer(Modifier.height(14.dp))
        PacketStrip(s.samples, slots = 20, highLatency = settings.highLatencyMs, height = 10.dp)
        s.network?.let { n ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(n.label, if (n.sameSubnet) Palette.Teal else Palette.Amber, icon = if (n.type == "Ethernet") Icons.Outlined.Lan else Icons.Outlined.Wifi)
                HelpButton(HelpTopic.NETWORK, size = 14.dp)
            }
        }
        AnimatedVisibility(down, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val since = s.samples.takeLast(s.consecutiveLost).firstOrNull()?.timestamp
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Palette.Surface).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.WarningAmber, null, tint = Palette.Red)
                Spacer(Modifier.width(10.dp))
                Text(
                    "La scheda non risponde" + (since?.let { " da ${Format.duration(now - it)} (dalle ${Format.time(it)})" } ?: ""),
                    style = MaterialTheme.typography.titleSmall, color = Palette.OnDangerBg,
                )
            }
        }
    }
}

@Composable
fun StatsGrid(s: SessionState, settings: AppSettings) {
    val st = s.stats
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Ricevuti", st.received.toString(), "/${st.sent}", Modifier.weight(1f), help = HelpTopic.RECEIVED)
            StatTile("Persi", st.lost.toString(), null, Modifier.weight(1f), if (st.lost > 0) Palette.Red else Palette.Text, HelpTopic.LOST)
            StatTile("Perdita", Format.percent(st.lossPercent), null, Modifier.weight(1f),
                when { st.sent == 0 -> Palette.Text; st.lossPercent == 0.0 -> Palette.Green; st.lossPercent < 5 -> Palette.Amber; else -> Palette.Red },
                HelpTopic.LOSS_PERCENT)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Media", st.avgMs, HelpTopic.AVG, Modifier.weight(1f), latencyColor(st.avgMs, settings.highLatencyMs).takeIf { st.avgMs != null })
            MiniStat("Minima", st.minMs, HelpTopic.MIN, Modifier.weight(1f))
            MiniStat("Massima", st.maxMs, HelpTopic.MAX, Modifier.weight(1f))
            MiniStat("Jitter", st.jitterMs, HelpTopic.JITTER, Modifier.weight(1f))
        }
    }
}

/** Riquadro compatto in millisecondi; toccandolo si apre la spiegazione. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniStat(label: String, value: Double?, help: HelpTopic, modifier: Modifier, color: Color? = null) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier.clip(RoundedCornerShape(4.dp)).background(Palette.Surface).border(1.dp, Palette.Outline, RoundedCornerShape(4.dp))
            .clickable { open = true }.padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Format.msOrDash(value), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Sans, color = color ?: Palette.Text, maxLines = 1)
            if (value != null) Text(" ms", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
    if (open) HelpSheet(help) { open = false }
}

@Composable
fun ChartCard(s: SessionState) {
    var all by remember { mutableStateOf(false) }
    AppCard {
        SectionHeader("Andamento", help = HelpTopic.CHART) {
            Row(Modifier.clip(RoundedCornerShape(3.dp)).border(1.dp, Palette.OutlineStrong.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(2.dp)) {
                listOf("Ultimi 60" to false, "Tutto" to true).forEach { (label, v) ->
                    val sel = all == v
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        color = if (sel) Palette.OnAccent else Palette.TextDim,
                        modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(if (sel) Palette.Accent else Color.Transparent)
                            .clickable(onClickLabel = "Mostra $label") { all = v }.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val data = if (all) downsample(s.samples, 600) else s.samples.takeLast(60)
        LatencyChart(data, avgMs = s.stats.avgMs)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(Palette.Text, "Latenza (ms)", line = true)
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
private fun Legend(color: Color, label: String, dashed: Boolean = false, line: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            dashed -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(3) { Box(Modifier.size(4.dp, 2.dp).background(color)) } }
            line -> Box(Modifier.size(14.dp, 2.dp).background(color))
            else -> Box(Modifier.size(8.dp, 5.dp).background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
    }
}

@Composable
fun StripCard(s: SessionState, settings: AppSettings) {
    AppCard {
        SectionHeader("Mappa pacchetti", help = HelpTopic.STRIP) {
            Text("ultimi 40", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        Spacer(Modifier.height(12.dp))
        PacketStrip(s.samples, highLatency = settings.highLatencyMs)
    }
}

@Composable
fun PacketsCard(s: SessionState, settings: AppSettings) {
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
    Row(
        Modifier.fillMaxWidth()
            .drawBehind { drawLine(Palette.Outline, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1f) }
            .background(if (p.status.isLost) Palette.Red.copy(alpha = 0.05f) else Color.Transparent)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (p.status.isLost) Palette.Red else latencyColor(p.rttMs, high)
        Box(Modifier.size(4.dp, 22.dp).background(c))
        Spacer(Modifier.width(10.dp))
        Text("#${p.seq}", style = MaterialTheme.typography.bodySmall, fontFamily = Mono, color = Palette.TextFaint, modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            if (p.status.isLost) {
                Text(p.message ?: "Richiesta scaduta.", style = MaterialTheme.typography.bodyMedium, color = Palette.Red)
            } else {
                Text("Risposta da $ip", style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("byte=${p.bytes}" + (p.ttl?.let { " TTL=$it" } ?: "") + "  " + Format.time(p.timestamp),
                    style = MaterialTheme.typography.bodySmall, fontFamily = Mono, fontSize = 11.sp, color = Palette.TextFaint)
            }
        }
        if (!p.status.isLost) {
            Text(Format.ms(p.rttMs ?: 0.0), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = c)
            Text(" ms", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        } else {
            Text(Format.time(p.timestamp), style = MaterialTheme.typography.bodySmall, fontFamily = Mono, fontSize = 11.sp, color = Palette.TextFaint)
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
        if (shown.isEmpty()) {
            Text("Nessun evento: non ci sono state perdite, interruzioni o picchi.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
        }
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
            Box(Modifier.size(10.dp).background(color))
            if (!last) Box(Modifier.padding(top = 3.dp).width(1.dp).weight(1f).background(Palette.OutlineStrong.copy(alpha = 0.35f)))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
            Row {
                Text(e.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
                Text(Format.time(e.timestamp), style = MaterialTheme.typography.bodySmall, fontFamily = Mono, fontSize = 11.sp, color = Palette.TextFaint)
            }
            e.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim) }
        }
    }
}

@Composable
fun FinishedActions(onCopy: () -> Unit, onShare: () -> Unit, onRepeat: () -> Unit, onDetail: () -> Unit) {
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
        modifier.clip(RoundedCornerShape(4.dp)).background(Palette.Surface).border(1.dp, Palette.OutlineStrong.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Palette.Text)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Text)
    }
}

@Composable
private fun IdleHint() {
    AppCard {
        PanelTitle("Come funziona")
        Spacer(Modifier.height(10.dp))
        listOf(
            "1" to "Collega il telefono al Wi-Fi della scheda.",
            "2" to "Tocca \"Gateway\" per usare il gateway predefinito, oppure scrivi l'IP della scheda (es. 192.168.1.100).",
            "3" to "Scegli Singolo (4 richieste) o Continuo (fino a quando premi Interrompi).",
            "4" to "Premi «Avvia test» e guarda i risultati in tempo reale. Hai dubbi? Apri la Guida: c'è anche la Diagnosi guidata.",
        ).forEach { (n, t) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Accent), contentAlignment = Alignment.Center) {
                    Text(n, style = MaterialTheme.typography.labelMedium, fontFamily = Mono, color = Palette.OnAccent)
                }
                Spacer(Modifier.width(12.dp))
                Text(t, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
        }
    }
}
