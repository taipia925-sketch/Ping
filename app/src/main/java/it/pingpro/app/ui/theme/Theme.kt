package it.pingpro.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.pingpro.app.R
import it.pingpro.app.ping.LinkStatus

/**
 * Colori dell'app. Il tema principale è chiaro (stile "Giorno"); la variante scura resta disponibile
 * dalle Impostazioni. I valori sono letti da uno stato osservabile: cambiando tema tutta l'interfaccia
 * si ridisegna da sola.
 */
object Palette {
    var dark by mutableStateOf(false)

    private fun pick(light: Long, dark: Long) = Color(if (this.dark) dark else light)

    /** Sfondo dell'app (grigio chiaro tipo One UI). */
    val Bg get() = pick(0xFFF2F3F7, 0xFF07090F)
    /** Card principali (bianche). */
    val Surface get() = pick(0xFFFFFFFF, 0xFF10141D)
    /** Riquadri dentro le card, campi di testo, chip. */
    val Surface2 get() = pick(0xFFF2F3F7, 0xFF171C28)
    /** Elementi selezionati di un selettore, tracce dei grafici. */
    val Surface3 get() = pick(0xFFE3E5EB, 0xFF1F2533)
    val Outline get() = pick(0x0F000000, 0x14FFFFFF)
    val OutlineStrong get() = pick(0x24000000, 0x26FFFFFF)
    val Text get() = pick(0xFF111318, 0xFFEEF2F8)
    val TextDim get() = pick(0xFF5B6270, 0xFF98A2B5)
    val TextFaint get() = pick(0xFF767C88, 0xFF6E7789)
    val Accent get() = pick(0xFF1F5FD6, 0xFF3D8BFF)
    /** Accento per testi e icone su sfondo neutro. */
    val AccentSoft get() = pick(0xFF1F5FD6, 0xFF7FB2FF)
    /** Sfondo tenue dell'accento (indicatore della barra in basso, chip). */
    val AccentBg get() = pick(0xFFDCE6FD, 0xFF14284A)
    val Teal get() = pick(0xFF0A7F72, 0xFF4FE3C1)
    val Green get() = pick(0xFF0E8A43, 0xFF34D399)
    val Amber get() = pick(0xFF9A5B00, 0xFFFFB547)
    val Orange get() = pick(0xFFC2410C, 0xFFFF8A4C)
    val Red get() = pick(0xFFC9302C, 0xFFFF5470)
    val Violet get() = pick(0xFF6D4BD8, 0xFFA78BFA)
    /** Pulsante "Interrompi". */
    val Danger get() = pick(0xFFD93A3A, 0xFFE0365A)
    /** Sfondo di una card in allarme (scheda che non risponde). */
    val DangerBg get() = pick(0xFFFDECEC, 0xFF2A0F17)
    val OnDangerBg get() = pick(0xFF8C1D18, 0xFFFFFFFF)
    val GreenBg get() = pick(0xFFE3F6EA, 0xFF0F2A20)
    val AmberBg get() = pick(0xFFFFF1D6, 0xFF2E230F)
    /** Barra di navigazione in basso. */
    val NavBar get() = pick(0xFFFFFFFF, 0xFF0E121B)
}

fun statusColor(s: LinkStatus): Color = when (s) {
    LinkStatus.ONLINE -> Palette.Green
    LinkStatus.SLOW -> Palette.Amber
    LinkStatus.UNSTABLE -> Palette.Amber
    LinkStatus.DOWN, LinkStatus.ERROR -> Palette.Red
    LinkStatus.STARTING -> Palette.Accent
    LinkStatus.IDLE, LinkStatus.FINISHED -> Palette.TextDim
}

/** Colore in base al tempo di risposta (verde → azzurro → giallo → arancio). */
fun latencyColor(ms: Double?, high: Int = 100): Color = when {
    ms == null -> Palette.Red
    ms < high * 0.2 -> Palette.Green
    ms < high * 0.5 -> Palette.Teal
    ms < high -> Palette.Amber
    else -> Palette.Orange
}

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

val Mono = FontFamily.Monospace

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 76.sp, lineHeight = 78.sp, letterSpacing = (-3.5).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.3.sp),
)

@Composable
fun PingProTheme(content: @Composable () -> Unit) {
    val scheme = if (Palette.dark) darkColorScheme(
        primary = Palette.Accent, onPrimary = Color.White, primaryContainer = Palette.AccentBg, onPrimaryContainer = Palette.AccentSoft,
        secondary = Palette.Teal, secondaryContainer = Palette.AccentBg, onSecondaryContainer = Palette.AccentSoft, background = Palette.Bg, onBackground = Palette.Text, surface = Palette.Surface, onSurface = Palette.Text,
        surfaceVariant = Palette.Surface2, onSurfaceVariant = Palette.TextDim, surfaceContainer = Palette.Surface,
        surfaceContainerHigh = Palette.Surface2, surfaceContainerHighest = Palette.Surface3, surfaceContainerLow = Palette.Surface,
        outline = Palette.OutlineStrong, outlineVariant = Palette.Outline, error = Palette.Red,
    ) else lightColorScheme(
        primary = Palette.Accent, onPrimary = Color.White, primaryContainer = Palette.AccentBg, onPrimaryContainer = Palette.AccentSoft,
        secondary = Palette.Teal, secondaryContainer = Palette.AccentBg, onSecondaryContainer = Palette.AccentSoft, background = Palette.Bg, onBackground = Palette.Text, surface = Palette.Surface, onSurface = Palette.Text,
        surfaceVariant = Palette.Surface2, onSurfaceVariant = Palette.TextDim, surfaceContainer = Palette.Surface,
        surfaceContainerHigh = Palette.Surface, surfaceContainerHighest = Palette.Surface3, surfaceContainerLow = Palette.Surface,
        outline = Palette.OutlineStrong, outlineVariant = Palette.Outline, error = Palette.Red,
    )
    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        // Colore del testo predefinito ovunque, anche fuori da Scaffold/Surface.
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
