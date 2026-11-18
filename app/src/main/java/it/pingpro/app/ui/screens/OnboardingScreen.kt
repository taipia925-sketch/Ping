package it.pingpro.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pingpro.app.ui.components.Pill
import it.pingpro.app.ui.components.SegmentedToggle
import it.pingpro.app.ui.components.Sparkline
import it.pingpro.app.ui.theme.Mono
import it.pingpro.app.ui.theme.Palette
import kotlinx.coroutines.launch

private data class Page(val title: String, val text: String, val art: @Composable () -> Unit)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        Page("Il PING di Windows,\nora sul telefono",
            "Verifica in un attimo se una scheda elettronica comunica in rete. Stesse richieste ICMP del Prompt dei comandi, risultati reali misurati dal tuo telefono.") { PulseArt() },
        Page("1. Scegli l'indirizzo",
            "Collega il telefono al Wi-Fi della scheda. Il gateway predefinito (come in ipconfig) è già pronto con un tocco; oppure scrivi l'IP della scheda e salvalo con un nome.") { AddressArt() },
        Page("2. Singolo o continuo",
            "Singolo invia 4 richieste come \"ping\". Continuo va avanti finché non premi Interrompi, come \"ping -t\".") { ModeArt() },
        Page("3. Capisci tutto a colpo d'occhio",
            "Verde: la scheda risponde. Giallo: rallentamenti o perdite. Rosso: non risponde, e il telefono vibra per avvisarti.") { StatusArt() },
        Page("Aiuto sempre a portata di mano",
            "Tocca le icone ? per una spiegazione semplice di ogni valore. Nella sezione Guida trovi esempi pratici e la soluzione ai problemi più comuni.") { HelpArt() },
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0D1830), Palette.Bg, Palette.Bg))).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Salta", color = Palette.TextDim) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { p.art() }
                Spacer(Modifier.height(36.dp))
                Text(p.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text(p.text, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 24.dp else 8.dp, label = "dot")
                val c by animateColorAsState(if (pager.currentPage == i) Palette.Accent else Palette.Surface3, label = "dotc")
                Box(Modifier.padding(horizontal = 4.dp).size(w, 8.dp).clip(CircleShape).background(c))
            }
        }
        val last = pager.currentPage == pages.lastIndex
        Box(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp).fillMaxWidth().height(60.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF3D8BFF), Color(0xFF2F6BFF))))
                .clickable { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (last) "INIZIA" else "AVANTI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.2.sp)
        }
    }
}

@Composable
private fun PulseArt() {
    val t = rememberInfiniteTransition(label = "p")
    val w by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "w")
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(240.dp)) {
            val r = size.minDimension / 2
            for (k in 0..2) {
                val f = (w + k / 3f) % 1f
                drawCircle(Palette.Accent.copy(alpha = (1 - f) * 0.5f), r * (0.3f + 0.7f * f), style = Stroke(2.dp.toPx()))
            }
            drawCircle(Palette.Accent.copy(alpha = 0.18f), r * 0.32f)
        }
        Icon(Icons.Outlined.Router, null, tint = Palette.AccentSoft, modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun AddressArt() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Surface2).border(1.dp, Palette.Accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Router, null, tint = Palette.AccentSoft)
            Spacer(Modifier.width(12.dp))
            Text("192.168.1.100", fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Gateway 192.168.1.1", Palette.Teal)
            Pill("Quadro linea 3", Palette.AccentSoft)
        }
        Spacer(Modifier.height(18.dp))
        Text("C:\\> ping 192.168.1.100", fontFamily = Mono, color = Palette.TextFaint, fontSize = 14.sp)
    }
}

@Composable
private fun ModeArt() {
    Column(Modifier.fillMaxWidth()) {
        SegmentedToggle(listOf("Singolo · 4", "Continuo · -t"), 1, {})
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("ping 192.168.1.100", fontFamily = Mono, color = Palette.TextFaint, fontSize = 12.sp)
            Text("ping -t 192.168.1.100", fontFamily = Mono, color = Palette.AccentSoft, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusArt() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            Triple("ONLINE", Palette.Green, listOf<Double?>(2.0, 3.0, 2.5, 2.8, 3.1, 2.2, 2.6, 2.9, 2.4, 2.7, 2.5, 2.8)),
            Triple("INSTABILE", Palette.Amber, listOf<Double?>(3.0, 30.0, 5.0, null, 60.0, 4.0, 25.0, 6.0, 80.0, 5.0, null, 9.0)),
            Triple("NON RISPONDE", Palette.Red, listOf<Double?>(2.0, 2.4, 2.1, null, null, null, null, null, null, null, null, null)),
        ).forEach { (label, color, values) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface2).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Wifi, null, tint = color)
                Spacer(Modifier.width(10.dp))
                Text(label, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(118.dp), fontSize = 14.sp)
                Box(Modifier.weight(1f)) { Sparkline(values, color, height = 28.dp) }
            }
        }
    }
}

@Composable
private fun HelpArt() {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Icons.AutoMirrored.Outlined.HelpOutline to Palette.AccentSoft, Icons.Outlined.History to Palette.Teal).forEach { (icon, c) ->
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c, modifier = Modifier.size(46.dp))
            }
        }
    }
}
