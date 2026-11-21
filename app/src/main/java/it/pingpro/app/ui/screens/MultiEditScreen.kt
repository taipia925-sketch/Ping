package it.pingpro.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.pingpro.app.data.MultiTarget
import it.pingpro.app.data.SavedDevice
import it.pingpro.app.data.TargetGroup
import it.pingpro.app.ping.NetInterfaceInfo
import it.pingpro.app.ping.TargetParser
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.theme.Palette

/** Composizione dell'elenco di schede da controllare insieme. */
@Composable
fun MultiEditScreen(
    targets: List<MultiTarget>,
    listName: String?,
    devices: List<SavedDevice>,
    groups: List<TargetGroup>,
    lan: NetInterfaceInfo?,
    onChange: (List<MultiTarget>, String?) -> Unit,
    onSaveGroup: (String) -> Unit,
    onLoadGroup: (TargetGroup) -> Unit,
    onDeleteGroup: (TargetGroup) -> Unit,
    onBack: () -> Unit,
    toast: (String) -> Unit,
    onScan: () -> Unit = {},
) {
    val prefix = lan?.ipv4?.substringBeforeLast('.')?.plus(".") ?: ""
    var from by remember { mutableStateOf(prefix) }
    var to by remember { mutableStateOf(prefix) }
    var pasted by remember { mutableStateOf("") }
    var saveDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TargetGroup?>(null) }
    val room = TargetParser.MAX_TARGETS - targets.size
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.OutlineStrong)

    fun add(list: List<MultiTarget>) {
        val existing = targets.map { it.address.lowercase() }.toSet()
        val fresh = list.filter { it.address.lowercase() !in existing }
        val merged = (targets + fresh).take(TargetParser.MAX_TARGETS)
        onChange(merged, listName)
        val added = merged.size - targets.size
        toast(when {
            added == 0 -> "Nessun indirizzo nuovo da aggiungere"
            merged.size == TargetParser.MAX_TARGETS && added < fresh.size -> "Aggiunti $added: raggiunto il massimo di ${TargetParser.MAX_TARGETS} schede"
            else -> "Aggiunti $added indirizzi"
        })
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BackHeader("Schede da controllare", "Fino a ${TargetParser.MAX_TARGETS} schede insieme · ora ${targets.size}", onBack) }
        if (room == 0) item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Palette.AmberBg).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("L'elenco è pieno (${TargetParser.MAX_TARGETS} schede). Per aggiungerne altre togli qualche scheda o premi «Svuota» in fondo.",
                    style = MaterialTheme.typography.titleSmall, color = Palette.Amber, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(emptyList(), null) }) { Text("Svuota", color = Palette.Red) }
            }
        }

        if (groups.isNotEmpty()) item {
            AppCard {
                Text("Gruppi salvati", style = MaterialTheme.typography.titleMedium)
                Text("Un gruppo è un elenco di schede con un nome, ad esempio tutte le schede di un apparato.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                Spacer(Modifier.height(6.dp))
                groups.forEach { g ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { onLoadGroup(g); toast("Caricato «${g.name}»") }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, null, tint = if (g.name == listName) Palette.Accent else Palette.TextDim)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleSmall)
                            Text("${g.targets.size} schede · ${compactList(g.targets)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { deleting = g }) { Icon(Icons.Outlined.DeleteOutline, "Elimina gruppo", tint = Palette.TextFaint) }
                    }
                }
            }
        }

        item {
            AppCard(onClick = onScan, padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                Text("Non sai quali indirizzi ci sono?", style = MaterialTheme.typography.titleSmall)
                Text("Usa «Cerca dispositivi»: trova chi risponde sulla rete e lo aggiungi con un tocco.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.AccentSoft)
            }
        }
        item {
            AppCard {
                Text("Aggiungi un intervallo", style = MaterialTheme.typography.titleMedium)
                Text("Tutti gli indirizzi dal primo all'ultimo, estremi compresi.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(from, { from = it.trim() }, label = { Text("Da") }, singleLine = true, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp), colors = colors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(to, { to = it.trim() }, label = { Text("A") }, singleLine = true, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp), colors = colors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                val size = TargetParser.rangeSize(from, to)
                val incomplete = from.endsWith(".") || to.endsWith(".")
                val rangePreview = remember(from, to, targets) {
                    if (size == null || size > 1000) null
                    else TargetParser.previewAdd(TargetParser.expandRange(from, to, 1000) ?: emptyList(), targets.map { it.address })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            size == null && incomplete -> "Completa i due indirizzi, ad esempio da ${prefix.ifEmpty { "192.168.1." }}100 a ${prefix.ifEmpty { "192.168.1." }}120."
                            size == null -> "Scrivi due indirizzi validi, il secondo uguale o maggiore del primo."
                            size > 1000 -> "Intervallo troppo grande ($size indirizzi): al massimo ${TargetParser.MAX_TARGETS} schede insieme."
                            else -> rangePreview?.message ?: ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if ((size == null && incomplete) || rangePreview?.warning == false) Palette.TextDim else Palette.Amber,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        rangePreview?.let { p -> add(p.newAddresses.map { a -> MultiTarget(a, devices.firstOrNull { d -> d.address == a }?.name) }) }
                    }, enabled = rangePreview?.canAdd == true) { Text("Aggiungi") }
                }
            }
        }

        if (devices.isNotEmpty()) item {
            AppCard {
                Text("Schede salvate", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                devices.sortedBy { it.name.lowercase() }.forEach { d ->
                    val inList = targets.any { it.address.equals(d.address, true) }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable {
                        if (inList) onChange(targets.filterNot { it.address.equals(d.address, true) }, listName)
                        else if (room > 0) onChange(targets + MultiTarget(d.address, d.name), listName)
                        else toast("Raggiunto il massimo di ${TargetParser.MAX_TARGETS} schede")
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (inList) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, tint = if (inList) Palette.Accent else Palette.TextDim)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleSmall)
                            Text(d.address, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                        }
                    }
                }
            }
        }

        item {
            AppCard {
                Text("Scrivi o incolla indirizzi", style = MaterialTheme.typography.titleMedium)
                Text("Uno per riga oppure separati da virgola. Puoi scrivere anche intervalli brevi, ad esempio 192.168.1.10-20.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(pasted, { pasted = it }, placeholder = { Text("192.168.1.100\n192.168.1.105\n192.168.1.110-115") },
                    modifier = Modifier.fillMaxWidth().height(130.dp), shape = RoundedCornerShape(4.dp), colors = colors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                val parsed = remember(pasted) { TargetParser.parse(pasted, 1000) }
                val pastePreview = remember(parsed, targets) { TargetParser.previewAdd(parsed.addresses, targets.map { it.address }) }
                if (pasted.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append(pastePreview.message)
                                if (parsed.invalid.isNotEmpty()) append(" Non validi: ${parsed.invalid.take(3).joinToString(", ")}${if (parsed.invalid.size > 3) "…" else ""}.")
                                if (parsed.addresses.isEmpty() && parsed.invalid.isEmpty()) append("Scrivi almeno un indirizzo.")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!pastePreview.warning && parsed.invalid.isEmpty()) Palette.TextDim else Palette.Amber,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            add(pastePreview.newAddresses.map { a -> MultiTarget(a, devices.firstOrNull { it.address.equals(a, true) }?.name) })
                            pasted = ""
                        }, enabled = pastePreview.canAdd) { Text("Aggiungi") }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) {
                Text("Elenco (${targets.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (targets.isNotEmpty()) TextButton(onClick = { onChange(emptyList(), null) }) { Text("Svuota", color = Palette.Red) }
            }
        }
        if (targets.isEmpty()) item {
            Text("L'elenco è vuoto.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint, modifier = Modifier.padding(start = 4.dp))
        }
        items(targets, key = { it.address }) { t ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Palette.Surface).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.name ?: t.address, style = MaterialTheme.typography.titleSmall)
                    if (t.name != null) Text(t.address, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
                IconButton(onClick = { onChange(targets.filterNot { it.address == t.address }, listName) }) {
                    Icon(Icons.Outlined.Close, "Rimuovi", tint = Palette.TextDim)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Surface2)
                    .clickable(enabled = targets.isNotEmpty()) { saveDialog = true }, contentAlignment = Alignment.Center) {
                    Text("Salva come gruppo", color = if (targets.isNotEmpty()) Palette.Text else Palette.TextFaint, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Accent).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center) {
                    Text("Fatto", color = Palette.OnAccent, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (saveDialog) {
        var name by remember { mutableStateOf(listName ?: "") }
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            containerColor = Palette.Surface,
            title = { Text("Salva come gruppo") },
            text = {
                Column {
                    Text("Dai un nome all'elenco, ad esempio il nome dell'apparato. Se esiste già un gruppo con lo stesso nome viene aggiornato.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Nome del gruppo") }, singleLine = true, shape = RoundedCornerShape(4.dp), colors = colors)
                }
            },
            confirmButton = { TextButton(onClick = { onSaveGroup(name); saveDialog = false; toast("Gruppo «${name.trim()}» salvato") }, enabled = name.isNotBlank()) { Text("Salva") } },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("Annulla") } },
        )
    }
    deleting?.let { g ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Palette.Surface,
            title = { Text("Eliminare il gruppo «${g.name}»?") },
            text = { Text("Le schede salvate e lo storico non vengono toccati.") },
            confirmButton = { TextButton(onClick = { onDeleteGroup(g); deleting = null }) { Text("Elimina", color = Palette.Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } },
        )
    }
}

