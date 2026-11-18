package it.pingpro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import it.pingpro.app.data.UpdateInfo
import it.pingpro.app.data.UpdateState
import it.pingpro.app.ui.Format
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.theme.Palette
import java.util.Locale

private fun size(b: Long) = if (b <= 0) "" else String.format(Locale.ITALY, " · %.1f MB", b / 1_048_576.0)

/** true quando la finestra di aggiornamento deve essere visibile. */
fun updateDialogVisible(s: UpdateState, dismissedVersion: Int): Boolean = when (s) {
    is UpdateState.Available -> s.info.versionCode != dismissedVersion
    is UpdateState.Downloading, is UpdateState.Ready -> true
    is UpdateState.Failed -> s.info != null
    else -> false
}

@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    canInstall: () -> Boolean,
    onDownload: (UpdateInfo) -> Unit,
    onLater: (UpdateInfo?) -> Unit,
    onAllowInstall: () -> Unit,
    onInstall: (java.io.File) -> Unit,
) {
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Failed -> state.info
        else -> null
    } ?: return
    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) onLater(info) },
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(when (state) {
                is UpdateState.Ready -> "Pronto da installare"
                is UpdateState.Failed -> "Aggiornamento non riuscito"
                is UpdateState.Downloading -> "Download in corso…"
                else -> "Nuova versione disponibile"
            })
        },
        text = {
            Column {
                Text("PingPro ${info.versionName}${size(info.sizeBytes)} · ora hai la $currentVersion",
                    style = MaterialTheme.typography.titleSmall, color = Palette.AccentSoft)
                if (info.notes.isNotBlank() && state !is UpdateState.Failed) {
                    Spacer(Modifier.height(10.dp))
                    Text("Novità", style = MaterialTheme.typography.labelLarge)
                    Text(info.notes, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
                when (state) {
                    is UpdateState.Downloading -> {
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Palette.Surface3)) {
                            Box(Modifier.fillMaxWidth(state.progress).height(8.dp).clip(CircleShape).background(Palette.Accent))
                        }
                        Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    is UpdateState.Ready -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (canInstall()) "File scaricato e verificato. Premi «Installa» e conferma nella schermata di Android: schede salvate, gruppi e storico restano."
                            else "Solo la prima volta: Android chiede di autorizzare PingPro a installare aggiornamenti. Premi «Consenti», attiva l'interruttore, torna indietro e premi «Installa».",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is UpdateState.Failed -> {
                        Spacer(Modifier.height(10.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = Palette.Amber)
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { onDownload(info) }) { Text("Aggiorna ora") }
                is UpdateState.Failed -> TextButton(onClick = { onDownload(info) }) { Text("Riprova") }
                is UpdateState.Ready -> if (canInstall()) TextButton(onClick = { onInstall(state.file) }) { Text("Installa") }
                else TextButton(onClick = onAllowInstall) { Text("Consenti") }
                is UpdateState.Downloading -> CircularProgressIndicator(Modifier.padding(8.dp).width(24.dp).height(24.dp), strokeWidth = 2.dp)
                else -> {}
            }
        },
        dismissButton = {
            if (state !is UpdateState.Downloading) TextButton(onClick = { onLater(info) }) { Text("Più tardi", color = Palette.TextDim) }
        },
    )
}

/** Sezione "Aggiornamenti" delle Impostazioni. */
@Composable
fun UpdateSettingsCard(state: UpdateState, currentVersion: String, lastCheck: Long, onCheck: () -> Unit, onDownload: (UpdateInfo) -> Unit) {
    AppCard {
        Text("Aggiornamenti", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Versione installata: $currentVersion", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        Spacer(Modifier.height(8.dp))
        val (text, color) = when (state) {
            is UpdateState.Checking -> "Controllo in corso…" to Palette.TextDim
            is UpdateState.UpToDate -> "Hai già l'ultima versione." to Palette.Green
            is UpdateState.Available -> "Disponibile la versione ${state.info.versionName}." to Palette.AccentSoft
            is UpdateState.Downloading -> "Download ${(state.progress * 100).toInt()}%…" to Palette.TextDim
            is UpdateState.Ready -> "Versione ${state.info.versionName} pronta da installare." to Palette.AccentSoft
            is UpdateState.Failed -> state.message to Palette.Amber
            UpdateState.Idle -> (if (lastCheck > 0) "Ultimo controllo: ${Format.dateTime(lastCheck)}" else "L'app controlla da sola all'avvio, quando c'è Internet.") to Palette.TextDim
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state is UpdateState.Available) TextButton(onClick = { onDownload(state.info) }) { Text("Aggiorna a ${state.info.versionName}") }
            TextButton(onClick = onCheck, enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading) { Text("Controlla ora") }
        }
    }
}
