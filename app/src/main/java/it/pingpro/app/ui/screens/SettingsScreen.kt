package it.pingpro.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.pingpro.app.data.AppSettings
import it.pingpro.app.BuildConfig
import it.pingpro.app.ping.PingConfig
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.components.SegmentedToggle
import it.pingpro.app.ui.theme.Mono
import it.pingpro.app.ui.theme.Palette

@Composable
fun SettingsScreen(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, onBack: () -> Unit, onTutorial: () -> Unit,
    updates: (@Composable () -> Unit)? = null) {
    val isDefault = settings.count == PingConfig.WINDOWS_COUNT && settings.payloadSize == PingConfig.WINDOWS_PAYLOAD &&
        settings.timeoutMs == PingConfig.WINDOWS_TIMEOUT_MS && settings.ttl == PingConfig.WINDOWS_TTL
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BackHeader("Impostazioni", null, onBack) }
        item {
            AppCard {
                Text("Parametri del PING", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isDefault) "Valori predefiniti di Windows: nessuna modifica necessaria." else "Hai modificato i valori predefiniti di Windows.",
                    style = MaterialTheme.typography.bodySmall, color = if (isDefault) Palette.Green else Palette.Amber,
                )
                Spacer(Modifier.height(10.dp))
                Stepper("Richieste nel test singolo", "-n", settings.count, 1, 1000, 1) { v -> update { it.copy(count = v) } }
                Stepper("Dimensione dati (byte)", "-l", settings.payloadSize, 0, 1472, 8) { v -> update { it.copy(payloadSize = v) } }
                Stepper("Timeout risposta (ms)", "-w", settings.timeoutMs, 500, 20000, 500) { v -> update { it.copy(timeoutMs = v) } }
                Stepper("TTL in uscita", "-i", settings.ttl, 1, 255, 1) { v -> update { it.copy(ttl = v) } }
                Text("L'intervallo tra le richieste resta quello standard di Windows (circa 1 secondo).",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(top = 6.dp))
                if (!isDefault) {
                    TextButton(onClick = {
                        update { it.copy(count = PingConfig.WINDOWS_COUNT, payloadSize = PingConfig.WINDOWS_PAYLOAD, timeoutMs = PingConfig.WINDOWS_TIMEOUT_MS, ttl = PingConfig.WINDOWS_TTL) }
                    }) {
                        Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text("Ripristina valori di Windows")
                    }
                }
            }
        }
        item {
            AppCard {
                Text("Avvisi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Toggle("Vibrazione", "Vibra quando la scheda smette di rispondere e quando torna raggiungibile", settings.vibrateOnAlert) { v -> update { it.copy(vibrateOnAlert = v) } }
                Toggle("Suono", "Emette un segnale acustico negli stessi momenti", settings.soundOnAlert) { v -> update { it.copy(soundOnAlert = v) } }
                Stepper("Soglia latenza elevata (ms)", null, settings.highLatencyMs, 5, 2000, 5) { v -> update { it.copy(highLatencyMs = v) } }
            }
        }
        item {
            AppCard {
                Text("Aspetto", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                SegmentedToggle(listOf("Chiaro", "Scuro", "Automatico"), when (settings.theme) { "dark" -> 1; "system" -> 2; else -> 0 },
                    { i -> update { it.copy(theme = listOf("light", "dark", "system")[i]) } })
                Text("«Automatico» segue il tema scelto nelle impostazioni del telefono.", style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            AppCard {
                Text("Schermo", style = MaterialTheme.typography.titleMedium)
                Toggle("Mantieni lo schermo acceso", "Durante il test lo schermo non si spegne da solo", settings.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
            }
        }
        if (updates != null) item { updates() }
        item {
            AppCard {
                Text("Aiuto", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onTutorial) { Text("Rivedi il tutorial iniziale") }
                Text("PingPro ${BuildConfig.VERSION_NAME} · Motore ICMP nativo Android", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, flag: String?, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (flag != null) Text("Windows: $flag", fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        FilledTonalIconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min) { Icon(Icons.Outlined.Remove, "Diminuisci") }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) { Icon(Icons.Outlined.Add, "Aumenta") }
    }
}

@Composable
private fun Toggle(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
    }
}
