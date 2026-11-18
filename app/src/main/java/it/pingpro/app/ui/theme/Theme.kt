package it.pingpro.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.pingpro.app.ping.LinkStatus

object Palette {
    val Bg = Color(0xFF07090F)
    val Surface = Color(0xFF10141D)
    val Surface2 = Color(0xFF171C28)
    val Surface3 = Color(0xFF1F2533)
    val Outline = Color(0x14FFFFFF)
    val OutlineStrong = Color(0x26FFFFFF)
    val Text = Color(0xFFEEF2F8)
    val TextDim = Color(0xFF98A2B5)
    val TextFaint = Color(0xFF5E6778)
    val Accent = Color(0xFF3D8BFF)
    val AccentSoft = Color(0xFF7FB2FF)
    val Teal = Color(0xFF4FE3C1)
    val Green = Color(0xFF34D399)
    val Amber = Color(0xFFFFB547)
    val Orange = Color(0xFFFF8A4C)
    val Red = Color(0xFFFF5470)
    val Violet = Color(0xFFA78BFA)
}

fun statusColor(s: LinkStatus): Color = when (s) {
    LinkStatus.ONLINE -> Palette.Green
    LinkStatus.SLOW -> Palette.Amber
    LinkStatus.UNSTABLE -> Palette.Amber
    LinkStatus.DOWN, LinkStatus.ERROR -> Palette.Red
    LinkStatus.STARTING -> Palette.Accent
    LinkStatus.IDLE, LinkStatus.FINISHED -> Palette.TextDim
}

/** Colore in base al tempo di risposta (verde → giallo → arancio → rosso). */
fun latencyColor(ms: Double?, high: Int = 100): Color = when {
    ms == null -> Palette.Red
    ms < high * 0.2 -> Palette.Green
    ms < high * 0.5 -> Palette.Teal
    ms < high -> Palette.Amber
    else -> Palette.Orange
}

private val Num = FontFamily.Default

val Mono = FontFamily.Monospace

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Num, fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-2).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontFamily = Num, fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

@Composable
fun PingProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF14284A),
            onPrimaryContainer = Palette.AccentSoft,
            secondary = Palette.Teal,
            background = Palette.Bg,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.Surface2,
            onSurfaceVariant = Palette.TextDim,
            surfaceContainer = Palette.Surface,
            surfaceContainerHigh = Palette.Surface2,
            surfaceContainerHighest = Palette.Surface3,
            surfaceContainerLow = Palette.Surface,
            outline = Palette.OutlineStrong,
            outlineVariant = Palette.Outline,
            error = Palette.Red,
        ),
        typography = AppTypography,
    ) {
        // Colore del testo predefinito chiaro ovunque, anche fuori da Scaffold/Surface.
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
