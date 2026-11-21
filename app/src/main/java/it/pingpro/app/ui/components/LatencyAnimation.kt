package it.pingpro.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pingpro.app.ui.Format
import it.pingpro.app.ui.theme.Mono
import it.pingpro.app.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Animazione didattica (rallentata) del viaggio di un ping: telefono → access point Wi-Fi → scheda e ritorno.
 * Con «Wi-Fi disturbato» alcuni pacchetti si perdono e appare «Richiesta scaduta».
 */
@Composable
fun LatencyAnimation(modifier: Modifier = Modifier) {
    var disturbed by remember { mutableStateOf(false) }
    val disturbedNow by rememberUpdatedState(disturbed)
    val progress = remember { Animatable(0f) }
    var lostAt by remember { mutableStateOf<Float?>(null) }
    var lines by remember { mutableStateOf(listOf<String>()) }
    var seq by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val rnd = Random(7)
        while (true) {
            seq++
            val lose = disturbedNow && rnd.nextInt(3) == 0
            val rtt = if (disturbedNow) 8.0 + rnd.nextDouble() * 60 else 2.0 + rnd.nextDouble() * 3
            progress.snapTo(0f); lostAt = null
            if (lose) {
                progress.animateTo(0.3f, tween(700, easing = LinearEasing))
                lostAt = 0.3f
                delay(1600)
                lines = (lines + "#$seq  Richiesta scaduta.").takeLast(3)
            } else {
                progress.animateTo(1f, tween(2200, easing = LinearEasing))
                lines = (lines + "#$seq  Risposta: ${Format.ms(rtt)} ms").takeLast(3)
            }
            delay(600)
        }
    }

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Palette.Surface2).padding(16.dp)) {
        Box(Modifier.fillMaxWidth().height(70.dp)) {
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                val y = size.height / 2
                val x0 = 24.dp.toPx(); val x2 = size.width - 24.dp.toPx()
                drawLine(Palette.OutlineStrong, Offset(x0, y), Offset(x2, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                val p = progress.value
                val f = if (p <= 0.5f) p * 2 else (1 - p) * 2
                val x = x0 + (x2 - x0) * f
                val color = if (lostAt != null) Palette.Red else if (p <= 0.5f) Palette.AccentSoft else Palette.Teal
                drawCircle(color.copy(alpha = 0.25f), 11.dp.toPx(), Offset(x, y))
                drawCircle(color, 6.dp.toPx(), Offset(x, y))
            }
            Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Node(Icons.Outlined.Smartphone, "Telefono")
                Node(Icons.Outlined.Router, "Access point")
                Node(Icons.Outlined.DeveloperBoard, "Scheda")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                lostAt != null -> "Il pacchetto si è perso: nessuna risposta. Dopo 4 secondi verrà contato come perso."
                progress.value <= 0.5f -> "Andata: la richiesta (ICMP Echo Request) viaggia verso la scheda…"
                else -> "Ritorno: la scheda risponde (Echo Reply). Il tempo totale è la latenza."
            },
            style = MaterialTheme.typography.bodySmall, color = if (lostAt != null) Palette.Red else Palette.TextDim,
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color(0xFF0C0C0C)).padding(10.dp)) {
            lines.forEach { Text(it, fontFamily = Mono, fontSize = 12.sp, color = if (it.contains("scaduta")) Palette.Red else androidx.compose.ui.graphics.Color(0xFFCCCCCC)) }
            if (lines.isEmpty()) Text(" ", fontFamily = Mono, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Animazione molto rallentata: nella realtà il viaggio dura pochi millesimi di secondo.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(if (disturbed) "Wi-Fi normale" else "Disturba il Wi-Fi", style = MaterialTheme.typography.labelMedium,
                color = if (disturbed) Palette.Green else Palette.Amber, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background((if (disturbed) Palette.Green else Palette.Amber).copy(alpha = 0.14f))
                    .clickable { disturbed = !disturbed }.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun Node(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Surface3), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Palette.Text, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 10.sp, color = Palette.TextDim)
    }
}
