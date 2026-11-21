package it.pingpro.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pingpro.app.ping.PingSample
import it.pingpro.app.ui.Format
import it.pingpro.app.ui.theme.Palette
import it.pingpro.app.ui.theme.latencyColor
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Calcola un fondo scala "rotondo" (1, 2, 5, 10, 20, 50…) per l'asse delle latenze. */
internal fun niceMax(v: Double): Double {
    if (v <= 1.0) return 1.0
    val exp = 10.0.pow(kotlin.math.floor(log10(v)))
    val f = v / exp
    val nice = when {
        f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 2.5 -> 2.5; f <= 5 -> 5.0; else -> 10.0
    }
    return nice * exp
}

data class ChartSeries(val samples: List<PingSample>, val color: Color, val label: String? = null)

/**
 * Grafico della latenza: area sfumata + linea, barre rosse per i pacchetti persi,
 * zone evidenziate per le interruzioni e linea tratteggiata della media.
 */
@Composable
fun LatencyChart(
    samples: List<PingSample>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    lineColor: Color = Palette.Accent,
    avgMs: Double? = null,
    extraSeries: List<ChartSeries> = emptyList(),
    alignBySeq: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(color = Palette.TextFaint, fontSize = 10.sp) }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val all = listOf(ChartSeries(samples, lineColor)) + extraSeries
        val maxPoints = all.maxOf { it.samples.size }.coerceAtLeast(2)
        val maxRtt = all.flatMap { s -> s.samples.mapNotNull { it.rttMs } }.maxOrNull() ?: 1.0
        val top = niceMax(max(maxRtt * 1.15, 1.0))

        val leftPad = 36.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val w = size.width - leftPad
        val h = size.height - bottomPad
        fun x(i: Int) = leftPad + if (maxPoints <= 1) w / 2 else i * w / (maxPoints - 1)
        fun y(v: Double) = (h - (v / top) * h).toFloat().coerceIn(0f, h)

        // Griglia orizzontale e etichette.
        for (k in 0..4) {
            val v = top * k / 4
            val yy = y(v)
            drawLine(Palette.Outline, Offset(leftPad, yy), Offset(size.width, yy), strokeWidth = 1f)
            drawLabel(measurer, Format.ms(v), labelStyle, Offset(0f, yy - 7.sp.toPx()))
        }

        all.forEachIndexed { si, series ->
            val pts = series.samples
            if (pts.isEmpty()) return@forEachIndexed
            val primary = si == 0
            // Pacchetti persi e interruzioni (solo serie principale).
            if (primary) {
                val barW = max(2.dp.toPx(), w / maxPoints * 0.6f)
                var runStart = -1
                pts.forEachIndexed { i, p ->
                    if (p.status.isLost) {
                        if (runStart < 0) runStart = i
                        drawRect(Palette.Red.copy(alpha = 0.85f), Offset(x(i) - barW / 2, 0f), Size(barW, h))
                    }
                    val endRun = runStart >= 0 && (!p.status.isLost || i == pts.lastIndex)
                    if (endRun) {
                        val last = if (p.status.isLost) i else i - 1
                        if (last - runStart >= 1) {
                            val x0 = x(runStart) - barW; val x1 = x(last) + barW
                            drawRect(Palette.Red.copy(alpha = 0.16f), Offset(x0, 0f), Size(x1 - x0, h))
                        }
                        runStart = -1
                    }
                }
            }

            // Linea + area per segmenti continui (interrotta dai pacchetti persi).
            val line = Path()
            val area = Path()
            var started = false
            var segStartX = 0f
            var lastX = 0f
            fun closeArea() {
                if (started) {
                    area.lineTo(lastX, h); area.lineTo(segStartX, h); area.close()
                }
            }
            pts.forEachIndexed { i, p ->
                val rtt = p.rttMs
                if (rtt == null) {
                    closeArea(); started = false
                } else {
                    val px = x(i); val py = y(rtt)
                    if (!started) {
                        line.moveTo(px, py); area.moveTo(px, py); segStartX = px; started = true
                    } else {
                        line.lineTo(px, py); area.lineTo(px, py)
                    }
                    lastX = px
                }
            }
            closeArea()
            if (primary) {
                drawPath(area, Brush.verticalGradient(listOf(series.color.copy(alpha = 0.32f), series.color.copy(alpha = 0f)), 0f, h))
            }
            drawPath(line, series.color, style = Stroke(width = if (primary) 2.5.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Punti singoli quando i campioni sono pochi.
            if (primary && pts.size <= 30) {
                pts.forEachIndexed { i, p ->
                    p.rttMs?.let { drawCircle(latencyColor(it), 3.5.dp.toPx(), Offset(x(i), y(it))) }
                }
            } else if (primary) {
                val lastIdx = pts.indexOfLast { it.rttMs != null }
                if (lastIdx >= 0) {
                    val c = Offset(x(lastIdx), y(pts[lastIdx].rttMs!!))
                    drawCircle(series.color.copy(alpha = 0.22f), 8.dp.toPx(), c)
                    drawCircle(series.color, 4.dp.toPx(), c)
                }
            }
        }

        avgMs?.let { a ->
            val yy = y(a)
            drawLine(Palette.Teal.copy(alpha = 0.8f), Offset(leftPad, yy), Offset(size.width, yy), strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(m: TextMeasurer, text: String, style: TextStyle, at: Offset) {
    drawText(m, text, at.copy(y = at.y.coerceAtLeast(0f)), style)
}

/** Mappa colorata degli ultimi pacchetti: un quadratino per ogni richiesta. */
@Composable
fun PacketStrip(samples: List<PingSample>, modifier: Modifier = Modifier, slots: Int = 40, highLatency: Int = 100, height: Dp = 26.dp) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val visible = samples.takeLast(slots)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Canvas(Modifier.fillMaxWidth().height(height)) {
                val gap = (if (slots > 30) 2 else 3).dp.toPx()
                val cell = (size.width - gap * (slots - 1)) / slots
                for (i in 0 until slots) {
                    val idx = i - (slots - visible.size)
                    val color = if (idx < 0) Palette.Surface3 else {
                        val p = visible[idx]
                        if (p.status.isLost) Palette.Red else latencyColor(p.rttMs, highLatency)
                    }
                    drawRect(color, Offset(i * (cell + gap), 0f), Size(cell, size.height))
                }
            }
        }
    }
}

/** Mini grafico per lo storico e gli esempi della guida. */
@Composable
fun Sparkline(values: List<Double?>, color: Color, modifier: Modifier = Modifier, height: Dp = 36.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val maxV = ceil((values.filterNotNull().maxOrNull() ?: 1.0) * 1.1).coerceAtLeast(1.0)
        val n = values.size.coerceAtLeast(2)
        fun x(i: Int) = i * size.width / (n - 1)
        fun y(v: Double) = (size.height - (v / maxV) * size.height).toFloat()
        val path = Path()
        var started = false
        values.forEachIndexed { i, v ->
            if (v == null) {
                drawRect(Palette.Red.copy(alpha = 0.8f), Offset(x(i) - 1.5f, 0f), Size(3f, size.height)); started = false
            } else if (!started) { path.moveTo(x(i), y(v)); started = true } else path.lineTo(x(i), y(v))
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
