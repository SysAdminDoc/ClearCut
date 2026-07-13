package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.engine.ExportState
import com.novacut.editor.ui.theme.ClearCutChromeIconButton
import com.novacut.editor.ui.theme.Elevation
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.Motion
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Floating export progress overlay that shows during background export.
 * Appears as a compact notification-style card in the top corner.
 */
@Composable
fun ExportProgressOverlay(
    exportState: ExportState,
    exportProgress: Float,
    exportStartTime: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalClearCutColors.current
    val isExporting = exportState == ExportState.EXPORTING
    val progressValue = exportProgress.coerceIn(0f, 1f)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isExporting) {
        now = System.currentTimeMillis()
        while (isExporting) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }
    val elapsed = if (exportStartTime > 0L) (now - exportStartTime).coerceAtLeast(0L) else 0L
    val remaining = if (progressValue > 0.05f && elapsed > 2000L) {
        val estimatedTotal = (elapsed / progressValue).toLong()
        (estimatedTotal - elapsed).coerceAtLeast(0L)
    } else {
        0L
    }
    val percent = (progressValue * 100).toInt().coerceIn(0, 100)
    val title = stringResource(R.string.panel_export_progress_exporting)
    val remainingLabel = if (remaining > 0L) {
        stringResource(R.string.export_eta_remaining, formatEta(remaining))
    } else {
        null
    }
    val elapsedLabel = if (elapsed > 0L) {
        stringResource(R.string.export_elapsed, formatEta(elapsed))
    } else {
        null
    }
    val statusDescription = listOfNotNull(
        title,
        "$percent%",
        remainingLabel,
        elapsedLabel
    ).joinToString(separator = ". ")

    AnimatedVisibility(
        visible = isExporting,
        enter = slideInVertically(
            animationSpec = tween(Motion.DurationMedium, easing = Motion.DecelerateEasing),
            initialOffsetY = { -it / 2 }
        ) + fadeIn(tween(Motion.DurationMedium, easing = Motion.DecelerateEasing)),
        exit = slideOutVertically(
            animationSpec = tween(Motion.DurationFast, easing = Motion.AccelerateEasing),
            targetOffsetY = { -it / 2 }
        ) + fadeOut(tween(Motion.DurationFast, easing = Motion.AccelerateEasing)),
        modifier = modifier
    ) {
        Surface(
            color = colors.panelHighest.copy(alpha = 0.98f),
            shape = RoundedCornerShape(Radius.xl),
            border = BorderStroke(
                1.dp,
                if (colors.highContrast) colors.cardStrokeStrong else colors.cardStrokeStrong.copy(alpha = 0.92f)
            ),
            shadowElevation = Elevation.toast,
            modifier = Modifier.semantics {
                contentDescription = statusDescription
                liveRegion = LiveRegionMode.Polite
                progressBarRangeInfo = ProgressBarRangeInfo(progressValue, 0f..1f)
            }
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                ClearCutAccents.Mauve.copy(alpha = 0.12f),
                                colors.panelHighest,
                                colors.panelHighest
                            )
                        )
                    )
                    .padding(horizontal = Spacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ClearCutAccents.Mauve.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier.size(34.dp),
                        color = ClearCutAccents.Mauve,
                        strokeWidth = 3.dp,
                        trackColor = ClearCutAccents.Mauve.copy(alpha = 0.12f)
                    )
                    Text(
                        text = "$percent%",
                        color = ClearCutAccents.Mauve,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(
                    modifier = Modifier.widthIn(min = 132.dp, max = 220.dp)
                ) {
                    Text(
                        text = title,
                        color = colors.text,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (remaining > 0L) {
                        Text(
                            text = stringResource(R.string.export_eta_remaining, formatEta(remaining)),
                            color = colors.subtext,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.export_elapsed, formatEta(elapsed)),
                            color = colors.subtext,
                            style = MaterialTheme.typography.labelMedium
                        )
                    } else if (elapsed > 0L) {
                        Text(
                            text = stringResource(R.string.export_elapsed, formatEta(elapsed)),
                            color = colors.subtext,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                ClearCutChromeIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_export_cancel),
                    onClick = onCancel,
                    tint = ClearCutAccents.Red,
                    containerColor = ClearCutAccents.Red.copy(alpha = 0.12f),
                    borderColor = ClearCutAccents.Red.copy(alpha = 0.24f),
                    shape = RoundedCornerShape(Radius.lg),
                )
            }
        }
    }
}

private fun formatEta(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
