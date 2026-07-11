package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.model.TrackType
import com.novacut.editor.ui.theme.ClearCutChromeIconButton
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.TouchTarget
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun TimelineToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    compact: Boolean = false,
    highlight: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalClearCutColors.current
    val actionAccent = if (destructive) colors.danger else colors.accent
    val backgroundColor = when {
        !enabled -> colors.panelHighest.copy(alpha = 0.35f)
        highlight -> actionAccent.copy(alpha = 0.16f)
        else -> colors.panelHighest
    }
    val borderColor = when {
        !enabled -> colors.cardStroke.copy(alpha = 0.35f)
        highlight -> actionAccent.copy(alpha = 0.58f)
        else -> if (colors.highContrast) colors.cardStrokeStrong else colors.cardStroke
    }
    val iconTint = when {
        !enabled -> colors.text.copy(alpha = 0.4f)
        highlight -> actionAccent
        else -> colors.text
    }
    ClearCutChromeIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        tint = iconTint,
        containerColor = backgroundColor,
        borderColor = borderColor,
        shape = RoundedCornerShape(Radius.md),
        size = TouchTarget.minimum,
        iconSize = if (compact) 16.dp else 18.dp,
        enabled = enabled
    )
}

@Composable
internal fun TimelineInfoChip(
    text: String,
    accent: Color,
    compact: Boolean = false
) {
    val colors = LocalClearCutColors.current
    Surface(
        color = accent.copy(alpha = 0.13f),
        shape = RoundedCornerShape(Radius.sm),
        border = BorderStroke(1.dp, accent.copy(alpha = if (colors.highContrast) 0.46f else 0.2f))
    ) {
        Text(
            text = text,
            color = accent,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 5.dp else 6.dp
            )
        )
    }
}

@Composable
internal fun TimelineTextActionChip(
    text: String,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalClearCutColors.current
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = TouchTarget.minimum)
            .clickable(role = Role.Button, onClick = onClick),
        color = colors.panelHighest,
        shape = RoundedCornerShape(Radius.sm),
        border = BorderStroke(1.dp, if (colors.highContrast) colors.cardStrokeStrong else colors.cardStroke)
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 5.dp else 6.dp
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = colors.text,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun TimelineMiniIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    accent: Color,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalClearCutColors.current
    ClearCutChromeIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        tint = if (active) accent else colors.subtext,
        containerColor = if (active) accent.copy(alpha = 0.14f) else colors.panelHighest.copy(alpha = 0.8f),
        borderColor = if (active) accent.copy(alpha = 0.26f) else colors.cardStroke.copy(alpha = 0.5f),
        shape = RoundedCornerShape(Radius.sm),
        size = if (compact) 28.dp else 32.dp,
        iconSize = if (compact) 12.dp else 14.dp
    )
}

@Composable
internal fun TimelineClipBadge(
    text: String,
    accent: Color,
    compact: Boolean = false
) {
    Surface(
        color = accent.copy(alpha = 0.18f),
        shape = RoundedCornerShape(Radius.sm),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Text(
            text = text,
            color = Mocha.Text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp
            )
        )
    }
}

internal fun trackIcon(trackType: TrackType): ImageVector = when (trackType) {
    TrackType.VIDEO -> Icons.Default.Videocam
    TrackType.AUDIO -> Icons.Default.MusicNote
    TrackType.OVERLAY -> Icons.Default.Layers
    TrackType.TEXT -> Icons.Default.Title
    TrackType.ADJUSTMENT -> Icons.Default.Tune
}

internal fun formatTimelineClipName(
    rawName: String?,
    fallback: String
): String {
    val cleaned = rawName
        ?.substringAfterLast('/')
        ?.substringBeforeLast('.')
        ?.replace("%20", " ")
        ?.replace(Regex("[_-]+"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return fallback

    val looksGenerated = !cleaned.any { it.isLetter() } ||
        Regex("^(img|vid|pxl|mvimg|screenshot|image|video)\\s*\\d[\\w\\s-]*$", RegexOption.IGNORE_CASE)
            .matches(cleaned)

    return if (looksGenerated) fallback else cleaned
}

internal fun formatTimelineTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

internal fun formatTimelineDurationLabel(ms: Long): String {
    return if (ms in 1L until 1_000L) {
        "%.1fs".format(Locale.US, (ms / 1000f).coerceAtLeast(0.1f))
    } else {
        formatTimelineTime(ms)
    }
}

internal fun formatSpeedLabel(speed: Float): String {
    val rounded = if (abs(speed - speed.roundToInt()) < 0.05f) {
        speed.roundToInt().toString()
    } else {
        "%.1f".format(speed)
    }
    return "${rounded}x"
}
