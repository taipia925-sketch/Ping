package it.pingpro.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.pingpro.app.data.SavedDevice
import it.pingpro.app.data.TestSummary
import it.pingpro.app.ping.NetworkResolver
import it.pingpro.app.ui.Format
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.components.EmptyState
import it.pingpro.app.ui.components.IconBadge
import it.pingpro.app.ui.theme.Palette

/** Stato dell'editor delle schede: null = chiuso. */
data class DeviceDraft(val id: String?, val name: String, val address: String, val note: String)

@Composable
fun DevicesScreen(
    devices: List<SavedDevice>,
    history: List<TestSummary>,
    running: Boolean,
    onPing: (SavedDevice) -> Unit,
    onEdit: (DeviceDraft) -> Unit,
    onDelete: (SavedDevice) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp, start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Schede", style = MaterialTheme.typography.headlineLarge)
                    Text("Indirizzi salvati con un nome", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
                FilledIconButton(onClick = { onEdit(DeviceDraft(null, "", "", "")) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Accent)) {
                    Icon(Icons.Outlined.Add, "Aggiungi scheda")
                }
            }
        }
        if (devices.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.DeveloperBoard, "Nessuna scheda salvata",
                    "Aggiungi le schede che controlli spesso, con un nome facile da ricordare (es. \"Quadro linea 3\"). Potrai avviare il test con un tocco.")
            }
        }
        items(devices.sortedBy { it.name.lowercase() }, key = { it.id }) { d ->
            val last = history.firstOrNull { it.target.equals(d.address, true) }
            AppCard(padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.DeveloperBoard, Palette.AccentSoft, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.address, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Palette.TextDim)
                        if (d.note.isNotBlank()) Text(d.note, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    FilledIconButton(
                        onClick = { onPing(d) }, enabled = !running,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Accent.copy(alpha = 0.18f), contentColor = Palette.AccentSoft),
                        modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.Outlined.PlayArrow, "Avvia test") }
                }
                if (last != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface2).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val ok = last.received > 0 && last.lost == 0
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) Palette.Green else if (last.received > 0) Palette.Amber else Palette.Red))
                        Spacer(Modifier.width(8.dp))
                        Text("Ultimo test ${Format.dateTime(last.startedAt)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.weight(1f))
                        Text(
                            (last.avgMs?.let { "${Format.ms(it)} ms · " } ?: "") + "${Format.percent(last.lossPercent)} persi",
                            style = MaterialTheme.typography.bodySmall, color = Palette.Text,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onEdit(DeviceDraft(d.id, d.name, d.address, d.note)) }) {
                        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Modifica")
                    }
                    TextButton(onClick = { onDelete(d) }) {
                        Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(16.dp), tint = Palette.Red); Spacer(Modifier.width(6.dp))
                        Text("Elimina", color = Palette.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceEditorDialog(draft: DeviceDraft, onDismiss: () -> Unit, onSave: (DeviceDraft) -> Unit) {
    var name by remember { mutableStateOf(draft.name) }
    var address by remember { mutableStateOf(draft.address) }
    var note by remember { mutableStateOf(draft.note) }
    val valid = name.isNotBlank() && NetworkResolver.isValidTarget(address)
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.OutlineStrong)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(if (draft.id == null) "Nuova scheda" else "Modifica scheda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome (es. Quadro linea 3)") }, singleLine = true, colors = colors, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(
                    address, { address = it.trim() }, label = { Text("Indirizzo IP") }, singleLine = true, colors = colors, shape = RoundedCornerShape(16.dp),
                    isError = address.isNotEmpty() && !NetworkResolver.isValidTarget(address),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(note, { note = it }, label = { Text("Note (facoltative)") }, colors = colors, shape = RoundedCornerShape(16.dp), maxLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(DeviceDraft(draft.id, name, address, note)) }, enabled = valid) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

