package it.pingpro.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import it.pingpro.app.ui.theme.Palette

/** Un passo del tour: [key] identifica l'elemento reale dello schermo da evidenziare. */
data class TourStep(val key: String?, val title: String, val text: String)

/** Tiene traccia della posizione sullo schermo degli elementi evidenziabili. */
class TourController {
    val bounds = mutableStateMapOf<String, Rect>()
    var steps by mutableStateOf<List<TourStep>>(emptyList())
    var index by mutableIntStateOf(0)
    var active by mutableStateOf(false)

    fun start(all: List<TourStep>) {
        // Si saltano i passi il cui elemento non è presente in questo momento.
        steps = all.filter { it.key == null || bounds.containsKey(it.key) }
        index = 0
        active = steps.isNotEmpty()
    }

    fun next() { if (index < steps.lastIndex) index++ else active = false }
    fun back() { if (index > 0) index-- }
    fun close() { active = false }
}

val LocalTour = staticCompositionLocalOf<TourController?> { null }

/** Registra la posizione di un elemento, così il tour può evidenziarlo. */
fun Modifier.tourTarget(key: String): Modifier = composed {
    val tour = LocalTour.current
    if (tour == null) this else {
        DisposableEffect(key) { onDispose { tour.bounds.remove(key) } }
        this.onGloballyPositioned { tour.bounds[key] = it.boundsInRoot() }
    }
}

/**
 * Sovrapposizione del tour: oscura lo schermo lasciando "illuminato" l'elemento spiegato,
 * con un fumetto che si posiziona sopra o sotto in base allo spazio disponibile.
 */
@Composable
fun TourOverlay(tour: TourController, onFinished: () -> Unit = {}) {
    if (!tour.active) return
    val step = tour.steps.getOrNull(tour.index) ?: return
    val rect = step.key?.let { tour.bounds[it] }
    val pulse by rememberInfiniteTransition(label = "tour").animateFloat(0f, 1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tp")

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = 0.78f))
            if (rect != null) {
                val pad = 8.dp.toPx()
                val r = Rect(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
                val corner = CornerRadius(20.dp.toPx())
                drawRoundRect(Color.Transparent, Offset(r.left, r.top), Size(r.width, r.height), corner, blendMode = BlendMode.Clear)
                val grow = 4.dp.toPx() * pulse
                drawRoundRect(Palette.AccentSoft.copy(alpha = 0.9f - 0.5f * pulse), Offset(r.left - grow, r.top - grow),
                    Size(r.width + grow * 2, r.height + grow * 2), corner, style = Stroke(2.dp.toPx()))
            }
        }
        Layout(content = { TourBubble(tour, step, onFinished) }) { measurables, constraints ->
            val margin = 16.dp.roundToPx()
            val p = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth - margin * 2))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val y = when {
                    rect == null -> (constraints.maxHeight - p.height) / 2
                    rect.center.y < constraints.maxHeight / 2f -> (rect.bottom + 20.dp.toPx()).toInt()
                    else -> (rect.top - 20.dp.toPx() - p.height).toInt()
                }.coerceIn(margin, (constraints.maxHeight - p.height - margin).coerceAtLeast(margin))
                p.place(margin, y)
            }
        }
    }
}

@Composable
private fun TourBubble(tour: TourController, step: TourStep, onFinished: () -> Unit) {
    val last = tour.index == tour.steps.lastIndex
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Surface2)
            .border(1.dp, Palette.Accent.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(20.dp)
    ) {
        Row {
            repeat(tour.steps.size) { i ->
                Box(Modifier.padding(end = 4.dp).size(if (i == tour.index) 18.dp else 6.dp, 6.dp).clip(CircleShape)
                    .background(if (i <= tour.index) Palette.Accent else Palette.Surface3))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(step.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(step.text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { tour.close(); onFinished() }) { Text("Chiudi", color = Palette.TextDim) }
            Spacer(Modifier.weight(1f))
            if (tour.index > 0) TextButton(onClick = { tour.back() }) { Text("Indietro") }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { if (last) { tour.close(); onFinished() } else tour.next() }) {
                Text(if (last) "Fine" else "Avanti", fontWeight = FontWeight.Bold)
            }
        }
    }
}
