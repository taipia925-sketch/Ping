package it.pingpro.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pingpro.app.R
import it.pingpro.app.ping.LinkStatus

/**
 * Colori dell'app, stile «Scheda tecnica»: carta chiara, inchiostro nero, filetti sottili e
 * il rosso solo per gli allarmi. La variante scura («Notte») resta disponibile dalle Impostazioni.
 * I valori sono letti da uno stato osservabile: cambiando tema tutta l'interfaccia si ridisegna.
 */
object Palette {
    var dark by mutableStateOf(false)

    private fun pick(light: Long, dark: Long) = Color(if (this.dark) dark else light)

    /** Sfondo dell'app (carta). */
    val Bg get() = pick(0xFFF3EFE6, 0xFF14130F)
    /** Riquadri principali. */
    val Surface get() = pick(0xFFFBF9F3, 0xFF1C1B16)
    /** Riquadri dentro i riquadri, campi di testo. */
    val Surface2 get() = pick(0xFFECE7DB, 0xFF25241E)
    /** Elementi selezionati, tracce dei grafici. */
    val Surface3 get() = pick(0xFFDFD9CB, 0xFF302E27)
    /** Filetto sottile. */
    val Outline get() = pick(0xFFD3CCBC, 0xFF35332B)
    /** Filetto marcato (inchiostro). */
    val OutlineStrong get() = pick(0xFF1B1B1B, 0xFFE6E0D2)
    val Text get() = pick(0xFF1B1B1B, 0xFFEDE8DC)
    val TextDim get() = pick(0xFF5A554B, 0xFFADA696)
    val TextFaint get() = pick(0xFF7A7467, 0xFF847E70)
    /** Pulsanti principali: inchiostro. */
    val Accent get() = pick(0xFF1B1B1B, 0xFFEDE8DC)
    /** Testo e icone sopra [Accent], [Danger], [Green]. */
    val OnAccent get() = pick(0xFFFBF9F3, 0xFF14130F)
    /** Collegamenti ed evidenziazioni (blu inchiostro). */
    val AccentSoft get() = pick(0xFF22408C, 0xFF9DB4F0)
    /** Sfondo tenue di un elemento selezionato. */
    val AccentBg get() = pick(0xFFE3DDCE, 0xFF2E2C25)
    val Teal get() = pick(0xFF1D6464, 0xFF6CC9BE)
    val Green get() = pick(0xFF2D6A38, 0xFF7ACB86)
    val Amber get() = pick(0xFF8A5A00, 0xFFE5B44F)
    val Orange get() = pick(0xFFA6461A, 0xFFF08A55)
    val Red get() = pick(0xFFB3261E, 0xFFFF6B5E)
    val Violet get() = pick(0xFF5A4688, 0xFFB7A4EA)
    /** Pulsante "Interrompi". */
    val Danger get() = pick(0xFFB3261E, 0xFFD9483C)
    /** Sfondo di un riquadro in allarme (scheda che non risponde). */
    val DangerBg get() = pick(0xFFF5E0D9, 0xFF35140F)
    val OnDangerBg get() = pick(0xFF7A1712, 0xFFFFD9D3)
    val GreenBg get() = pick(0xFFE0E8D6, 0xFF17281A)
    val AmberBg get() = pick(0xFFF2E5C2, 0xFF2E2410)
    /** Barra di navigazione in basso. */
    val NavBar get() = pick(0xFFF3EFE6, 0xFF14130F)
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

/** IBM Plex Sans Condensed (licenza SIL OFL): stretto, tecnico, leggibilissimo nei numeri. */
val Sans = FontFamily(
    Font(R.font.plex_cond_regular, FontWeight.Normal),
    Font(R.font.plex_cond_medium, FontWeight.Medium),
    Font(R.font.plex_cond_semibold, FontWeight.SemiBold),
    Font(R.font.plex_cond_bold, FontWeight.Bold),
    Font(R.font.plex_cond_bold, FontWeight.ExtraBold),
)

/** IBM Plex Mono: indirizzi, comandi, valori misurati. */
val Mono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.plex_mono_semibold, FontWeight.Bold),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 80.sp, lineHeight = 80.sp, letterSpacing = (-2).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.4.sp),
)

/** Angoli quasi vivi: aspetto da documento tecnico, non da «app». */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(3.dp), medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp), extraLarge = RoundedCornerShape(6.dp),
)

@Composable
fun PingProTheme(content: @Composable () -> Unit) {
    val scheme = if (Palette.dark) darkColorScheme(
        primary = Palette.Accent, onPrimary = Palette.OnAccent, primaryContainer = Palette.AccentBg, onPrimaryContainer = Palette.AccentSoft,
        secondary = Palette.AccentSoft, secondaryContainer = Palette.AccentBg, onSecondaryContainer = Palette.AccentSoft, background = Palette.Bg, onBackground = Palette.Text, surface = Palette.Surface, onSurface = Palette.Text,
        surfaceVariant = Palette.Surface2, onSurfaceVariant = Palette.TextDim, surfaceContainer = Palette.Surface,
        surfaceContainerHigh = Palette.Surface2, surfaceContainerHighest = Palette.Surface3, surfaceContainerLow = Palette.Surface,
        outline = Palette.OutlineStrong, outlineVariant = Palette.Outline, error = Palette.Red,
    ) else lightColorScheme(
        primary = Palette.Accent, onPrimary = Palette.OnAccent, primaryContainer = Palette.AccentBg, onPrimaryContainer = Palette.AccentSoft,
        secondary = Palette.AccentSoft, secondaryContainer = Palette.AccentBg, onSecondaryContainer = Palette.AccentSoft, background = Palette.Bg, onBackground = Palette.Text, surface = Palette.Surface, onSurface = Palette.Text,
        surfaceVariant = Palette.Surface2, onSurfaceVariant = Palette.TextDim, surfaceContainer = Palette.Surface,
        surfaceContainerHigh = Palette.Surface, surfaceContainerHighest = Palette.Surface3, surfaceContainerLow = Palette.Surface,
        outline = Palette.OutlineStrong, outlineVariant = Palette.Outline, error = Palette.Red,
    )
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
        // Colore del testo predefinito ovunque, anche fuori da Scaffold/Surface.
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
