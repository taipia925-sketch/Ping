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
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * Grafico della latenza, impostato come un tracciato tecnico: assi a inchiostro, griglia sottile,
 * valori in carattere monospaziato. I pacchetti persi sono una fascia rossa tenue con una tacca
 * sull'asse (così la linea resta leggibile); le interruzioni sono tratteggiate. La media è una
 * linea tratteggiata con il suo valore scritto accanto.
 */
@Composable
fun LatencyChart(
    samples: List<PingSample>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    lineColor: Color = Palette.Text,
    avgMs: Double? = null,
    extraSeries: List<ChartSeries> = emptyList(),
    alignBySeq: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val dark = Palette.dark
    val labelStyle = remember(dark) { TextStyle(color = Palette.TextFaint, fontSize = 10.sp, fontFamily = it.pingpro.app.ui.theme.Mono) }
    val avgStyle = remember(dark) { TextStyle(color = Palette.Teal, fontSize = 10.sp, fontFamily = it.pingpro.app.ui.theme.Mono, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
    val lineC = if (lineColor == Palette.Accent) Palette.Text else lineColor
    Canvas(modifier.fillMaxWidth().height(height)) {
        val all = listOf(ChartSeries(samples, lineC)) + extraSeries
        val maxPoints = all.maxOf { it.samples.size }.coerceAtLeast(2)
        val maxRtt = all.flatMap { s -> s.samples.mapNotNull { it.rttMs } }.maxOrNull() ?: 1.0
        val top = niceMax(max(maxRtt * 1.15, 1.0))

        val leftPad = 30.dp.toPx()
        val markH = 5.dp.toPx()
        val bottomPad = markH + 3.dp.toPx()
        val w = size.width - leftPad
        val h = size.height - bottomPad
        fun x(i: Int) = leftPad + if (maxPoints <= 1) w / 2 else i * w / (maxPoints - 1)
        fun y(v: Double) = (h - (v / top) * h).toFloat().coerceIn(0f, h)
        fun tick(v: Double) = if (top <= 2.5) Format.ms(v) else kotlin.math.round(v).toInt().toString()

        // Griglia orizzontale ed etichette dell'asse.
        for (k in 0..4) {
            val v = top * k / 4
            val yy = y(v)
            if (k > 0) drawLine(Palette.Outline, Offset(leftPad, yy), Offset(size.width, yy), strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
            val t = measurer.measure(tick(v), labelStyle)
            drawText(t, topLeft = Offset(leftPad - 6.dp.toPx() - t.size.width, (yy - t.size.height / 2f).coerceIn(0f, size.height - t.size.height)))
        }
        // Assi a inchiostro.
        drawLine(Palette.OutlineStrong.copy(alpha = 0.7f), Offset(leftPad, 0f), Offset(leftPad, h), strokeWidth = 1.dp.toPx())
        drawLine(Palette.OutlineStrong.copy(alpha = 0.7f), Offset(leftPad, h), Offset(size.width, h), strokeWidth = 1.dp.toPx())

        all.forEachIndexed { si, series ->
            val pts = series.samples
            if (pts.isEmpty()) return@forEachIndexed
            val primary = si == 0
            // Pacchetti persi e interruzioni (solo serie principale).
            if (primary) {
                val slot = w / maxPoints
                val barW = max(2.dp.toPx(), slot * 0.7f)
                var runStart = -1
                pts.forEachIndexed { i, p ->
                    if (p.status.isLost) {
                        if (runStart < 0) runStart = i
                        drawRect(Palette.Red.copy(alpha = 0.10f), Offset(x(i) - barW / 2, 0f), Size(barW, h))
                        drawRect(Palette.Red, Offset(x(i) - barW / 2, h + 3.dp.toPx()), Size(barW, markH))
                    }
                    val endRun = runStart >= 0 && (!p.status.isLost || i == pts.lastIndex)
                    if (endRun) {
                        val last = if (p.status.isLost) i else i - 1
                        if (last - runStart >= 1) {
                            // Interruzione: tratteggio diagonale, come una zona "fuori servizio" su un disegno tecnico.
                            val x0 = (x(runStart) - barW / 2).coerceAtLeast(leftPad); val x1 = (x(last) + barW / 2).coerceAtMost(size.width)
                            clipRect(x0, 0f, x1, h) {
                                val step = 7.dp.toPx()
                                var xx = x0 - h
                                while (xx < x1) {
                                    drawLine(Palette.Red.copy(alpha = 0.28f), Offset(xx, h), Offset(xx + h, 0f), strokeWidth = 1.dp.toPx())
                                    xx += step
                                }
                            }
                            drawLine(Palette.Red.copy(alpha = 0.6f), Offset(x0, 0f), Offset(x0, h), strokeWidth = 1.dp.toPx())
                            drawLine(Palette.Red.copy(alpha = 0.6f), Offset(x1, 0f), Offset(x1, h), strokeWidth = 1.dp.toPx())
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
                drawPath(area, Brush.verticalGradient(listOf(series.color.copy(alpha = 0.14f), series.color.copy(alpha = 0f)), 0f, h))
            }
            drawPath(line, series.color, style = Stroke(width = if (primary) 2.dp.toPx() else 1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Punti quando i campioni sono pochi; altrimenti solo l'ultimo, evidenziato.
            if (primary && pts.size <= 30) {
                pts.forEachIndexed { i, p ->
                    p.rttMs?.let {
                        val c = Offset(x(i), y(it))
                        drawCircle(Palette.Surface, 4.5.dp.toPx(), c)
                        drawCircle(latencyColor(it), 3.dp.toPx(), c)
                    }
                }
            } else if (primary) {
                val lastIdx = pts.indexOfLast { it.rttMs != null }
                if (lastIdx >= 0) {
                    val c = Offset(x(lastIdx), y(pts[lastIdx].rttMs!!))
                    drawCircle(series.color.copy(alpha = 0.16f), 9.dp.toPx(), c)
                    drawCircle(Palette.Surface, 5.dp.toPx(), c)
                    drawCircle(series.color, 3.5.dp.toPx(), c)
                }
            }
        }

        avgMs?.let { a ->
            val yy = y(a)
            drawLine(Palette.Teal.copy(alpha = 0.85f), Offset(leftPad, yy), Offset(size.width, yy), strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            // Valore della media scritto sopra la linea, all'inizio (lontano dall'ultimo punto).
            val t = measurer.measure("Ø " + Format.ms(a), avgStyle)
            val ty = if (yy - t.size.height - 2.dp.toPx() >= 0f) yy - t.size.height - 2.dp.toPx() else yy + 2.dp.toPx()
            drawRect(Palette.Surface.copy(alpha = 0.85f), Offset(leftPad + 4.dp.toPx(), ty), Size(t.size.width + 4.dp.toPx(), t.size.height.toFloat()))
            drawText(t, topLeft = Offset(leftPad + 6.dp.toPx(), ty))
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
