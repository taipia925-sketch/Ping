package it.pingpro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pingpro.app.ping.IpConfig
import it.pingpro.app.ping.NetInterfaceInfo
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.components.EmptyState
import it.pingpro.app.ui.components.HelpButton
import it.pingpro.app.ui.components.HelpTopic
import it.pingpro.app.ui.components.IconBadge
import it.pingpro.app.ui.components.Pill
import it.pingpro.app.ui.components.PillShape
import it.pingpro.app.ui.components.SegmentedToggle
import it.pingpro.app.ui.theme.Mono
import it.pingpro.app.ui.theme.Palette

private fun iconFor(type: String): ImageVector = when (type) {
    "Wi-Fi" -> Icons.Outlined.Wifi
    "Ethernet" -> Icons.Outlined.Lan
    "Dati mobili" -> Icons.Outlined.CellTower
    "VPN" -> Icons.Outlined.VpnLock
    else -> Icons.Outlined.Router
}

/**
 * Schermata "Rete": l'equivalente di `ipconfig` di Windows. Mostra il gateway predefinito in
 * evidenza, con i pulsanti per pingarlo subito o usarlo come indirizzo da testare.
 */
@Composable
fun NetworkScreen(
    interfaces: List<NetInterfaceInfo>,
    running: Boolean,
    onPing: (String) -> Unit,
    onUse: (String) -> Unit,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
) {
    var promptView by remember { mutableStateOf(false) }
    val lan = interfaces.firstOrNull { it.isLan && it.ipv4 != null }
    val others = interfaces.filter { it !== lan }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 22.dp, start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rete", style = MaterialTheme.typography.headlineLarge)
                    Text("Come ipconfig, sul telefono", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Aggiorna", tint = Palette.TextDim) }
            }
        }
        item {
            SegmentedToggle(listOf("Vista grafica", "Vista Prompt"), if (promptView) 1 else 0, { promptView = it == 1 })
        }

        if (promptView) {
            item { PromptCard(interfaces, onCopy) }
            if (lan?.gateway != null) item { GatewayQuickRow(lan.gateway, running, onPing, onUse) }
            return@LazyColumn
        }

        if (lan == null) {
            item {
                AppCard {
                    EmptyState(Icons.Outlined.WifiOff, "Nessuna rete locale",
                        "Il telefono non è collegato a un Wi-Fi o a una rete Ethernet. Collegati al Wi-Fi delle schede per vedere gateway e indirizzi.")
                }
            }
        } else {
            item { GatewayHero(lan, running, onPing, onUse) }
            item { DetailsCard(lan, running, onPing, onUse) }
            if (lan.wifiRssi != null) item { SignalCard(lan) }
        }
        items(others) { i -> OtherInterfaceCard(i) }
        item {
            Text(
                "Suggerimento: per controllare una scheda, prova prima il ping del gateway. Se il gateway risponde ma la scheda no, il problema è nella scheda o nel suo collegamento.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun GatewayHero(lan: NetInterfaceInfo, running: Boolean, onPing: (String) -> Unit, onUse: (String) -> Unit) {
    AppCard(padding = PaddingValues(horizontal = 20.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val band = lan.frequencyMHz?.let { if (it >= 5900) " · 6 GHz" else if (it >= 4900) " · 5 GHz" else " · 2,4 GHz" } ?: ""
            Pill(lan.type + band, Palette.AccentSoft, icon = iconFor(lan.type))
            Pill(if (lan.validatedInternet) "Internet OK" else "Senza Internet", if (lan.validatedInternet) Palette.Green else Palette.Amber)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gateway predefinito", style = MaterialTheme.typography.titleSmall, color = Palette.TextDim)
            HelpButton(HelpTopic.GATEWAY, size = 15.dp)
        }
        Text(lan.gateway ?: "Non presente", style = MaterialTheme.typography.displayMedium, color = if (lan.gateway != null) Palette.Text else Palette.TextFaint)
        if (lan.gateway == null) {
            Text("Questa rete non ha un gateway (rete isolata). Puoi comunque pingare le schede della sottorete.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        Text("Telefono: ${lan.ipv4}/${lan.prefixLength}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        lan.gateway?.let { gw ->
            Spacer(Modifier.height(16.dp))
            GatewayQuickRow(gw, running, onPing, onUse)
        }
    }
}

@Composable
private fun GatewayQuickRow(gw: String, running: Boolean, onPing: (String) -> Unit, onUse: (String) -> Unit, onDark: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.weight(1.3f).height(54.dp).clip(PillShape)
                .background(if (running) Palette.Surface3 else Palette.Accent)
                .clickable(enabled = !running) { onPing(gw) },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.NetworkPing, null, tint = if (running) Palette.TextFaint else Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ping gateway", color = if (running) Palette.TextFaint else Color.White, style = MaterialTheme.typography.labelLarge)
        }
        Row(
            Modifier.weight(1f).height(54.dp).clip(PillShape).background(Palette.AccentBg).clickable { onUse(gw) },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Usa", color = Palette.AccentSoft, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Potenza del segnale Wi-Fi con le tacche, come nella barra di stato del telefono. */
@Composable
private fun SignalCard(lan: NetInterfaceInfo) {
    val rssi = lan.wifiRssi ?: return
    val bars = when { rssi >= -55 -> 4; rssi >= -67 -> 3; rssi >= -75 -> 2; rssi >= -85 -> 1; else -> 0 }
    val color = when { bars >= 3 -> Palette.Green; bars == 2 -> Palette.Amber; else -> Palette.Red }
    AppCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(28.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(8, 14, 20, 28).forEachIndexed { i, h ->
                    Box(Modifier.width(6.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(if (i < bars) color else Palette.Surface3))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Segnale ${IpConfig.signalLabel(rssi).lowercase()} · $rssi dBm", style = MaterialTheme.typography.titleSmall)
                lan.linkSpeedMbps?.let { Text("Collegamento a $it Mbps", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim) }
            }
            HelpButton(HelpTopic.SIGNAL, size = 16.dp)
        }
    }
}

@Composable
private fun DetailsCard(i: NetInterfaceInfo, running: Boolean, onPing: (String) -> Unit, onUse: (String) -> Unit) {
    AppCard(padding = PaddingValues(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 10.dp)) {
        Text("Dettagli", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        InfoRow("Indirizzo IPv4 telefono", i.ipv4, HelpTopic.PHONE_IP)
        InfoRow("Subnet mask", i.subnetMask?.let { "$it  (/${i.prefixLength})" }, HelpTopic.SUBNET)
        InfoRow("Gateway predefinito", i.gateway, HelpTopic.GATEWAY, pingable = true, running = running, onPing = onPing, onUse = onUse)
        i.hostRange?.let { (a, b) -> InfoRow("Indirizzi della rete", "$a – $b", HelpTopic.SUBNET) }
        i.dns.forEachIndexed { k, d -> InfoRow(if (k == 0) "Server DNS" else "", d, if (k == 0) HelpTopic.DNS else null, pingable = true, running = running, onPing = onPing, onUse = onUse) }
        i.dhcpServer?.let { InfoRow("Server DHCP", it, HelpTopic.DHCP, pingable = true, running = running, onPing = onPing, onUse = onUse) }
        i.frequencyMHz?.let { InfoRow("Banda", if (it >= 5900) "6 GHz" else if (it >= 4900) "5 GHz" else "2,4 GHz") }
        i.ipv6.firstOrNull { !IpConfig.isLinkLocalV6(it) }?.let { InfoRow("Indirizzo IPv6", it) }
        i.ipv6.firstOrNull { IpConfig.isLinkLocalV6(it) }?.let { InfoRow("IPv6 locale (collegamento)", it) }
        i.mtu?.let { InfoRow("MTU", it.toString()) }
        i.domain?.takeIf { it.isNotBlank() }?.let { InfoRow("Suffisso DNS", it) }
    }
}

@Composable
private fun InfoRow(
    label: String, value: String?, help: HelpTopic? = null, pingable: Boolean = false, running: Boolean = false,
    onPing: (String) -> Unit = {}, onUse: (String) -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            if (label.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                if (help != null) HelpButton(help, size = 13.dp)
            }
            Text(value ?: "—", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = if (value == null) Palette.TextFaint else Palette.Text)
        }
        if (pingable && value != null) {
            Text("Usa", style = MaterialTheme.typography.labelMedium, color = Palette.AccentSoft,
                modifier = Modifier.clip(CircleShape).clickable { onUse(value) }.padding(horizontal = 10.dp, vertical = 8.dp))
            IconButton(onClick = { onPing(value) }, enabled = !running) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Palette.Accent.copy(alpha = if (running) 0.06f else 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.NetworkPing, "Ping $value", tint = if (running) Palette.TextFaint else Palette.AccentSoft, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun OtherInterfaceCard(i: NetInterfaceInfo) {
    AppCard(padding = PaddingValues(16.dp), color = Palette.Surface.copy(alpha = 0.7f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(iconFor(i.type), Palette.TextDim, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(i.type + (i.interfaceName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(i.ipv4?.let { "IP $it" }, i.gateway?.let { "gateway $it" }).joinToString(" · ").ifEmpty { "Nessun indirizzo IPv4" },
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
            }
            if (i.isDefault) Pill("Predefinita", Palette.TextDim)
        }
        if (i.type == "Dati mobili") {
            Spacer(Modifier.height(8.dp))
            Text("I test verso le schede della rete locale non passano da qui: l'app usa automaticamente il Wi-Fi/Ethernet.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
    }
}

@Composable
private fun PromptCard(interfaces: List<NetInterfaceInfo>, onCopy: (String) -> Unit) {
    var all by remember { mutableStateOf(false) }
    val text = IpConfig.windowsText(interfaces, all)
    AppCard(color = Color(0xFF0C0C0C), padding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().background(Palette.Surface2).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Terminal, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (all) "ipconfig /all" else "ipconfig", fontFamily = Mono, fontSize = 13.sp, color = Palette.Text, modifier = Modifier.weight(1f))
            Text(if (all) "Ridotto" else "/all", style = MaterialTheme.typography.labelMedium, color = Palette.AccentSoft,
                modifier = Modifier.clip(CircleShape).clickable { all = !all }.padding(horizontal = 10.dp, vertical = 8.dp))
            IconButton(onClick = { onCopy(text) }) { Icon(Icons.Outlined.ContentCopy, "Copia", tint = Palette.TextDim) }
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp)) {
            Text("C:\\> ${if (all) "ipconfig /all" else "ipconfig"}\n\n$text", fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFFCCCCCC))
        }
    }
}
