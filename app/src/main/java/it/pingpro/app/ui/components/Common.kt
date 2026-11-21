package it.pingpro.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pingpro.app.ui.theme.Palette

val CardShape = RoundedCornerShape(4.dp)

/** Forma "a pillola" dei pulsanti principali. */
val PillShape = RoundedCornerShape(4.dp)

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    color: Color = Palette.Surface,
    borderColor: Color = Palette.Outline,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(color)
            .border(1.dp, borderColor, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, help: HelpTopic? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp, color = Palette.TextDim)
        if (help != null) {
            Spacer(Modifier.width(4.dp))
            HelpButton(help, size = 18.dp)
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpButton(topic: HelpTopic, size: Dp = 18.dp, tint: Color = Palette.TextFaint) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(size + 14.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Aiuto: ${topic.title}", tint = tint, modifier = Modifier.size(size))
    }
    if (open) HelpSheet(topic) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(topic: HelpTopic, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(40.dp, 4.dp).clip(CircleShape).background(Palette.OutlineStrong))
        },
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = Palette.AccentSoft, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(topic.title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            Text(topic.body, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    valueColor: Color = Palette.Text,
    help: HelpTopic? = null,
) {
    val color by animateColorAsState(valueColor, label = "statColor")
    Column(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Outline, RoundedCornerShape(4.dp))
            .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = Palette.TextDim, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (help != null) HelpButton(help, size = 15.dp) else Spacer(Modifier.height(29.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Palette.OutlineStrong, RoundedCornerShape(4.dp))
            .background(Palette.Surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) Palette.Accent else Color.Transparent, label = "seg")
            val fg by animateColorAsState(if (sel) Palette.OnAccent else Palette.TextDim, label = "segFg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(bg)
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) fg else Palette.TextFaint)
            }
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.10f))
        .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun PressScale(pressed: Boolean): Float {
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return s
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, Palette.AccentSoft, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Suppress("unused")
fun Modifier.pressScale(scale: Float) = this.scale(scale)
