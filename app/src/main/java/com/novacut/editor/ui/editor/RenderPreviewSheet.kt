package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.SmartRenderEngine

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderPreviewSheet(
    segments: List<SmartRenderEngine.RenderSegment>,
    summary: SmartRenderEngine.SmartRenderSummary,
    onRenderPreview: () -> Unit,
    onRenderFull: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    val hasSegments = segments.isNotEmpty()
    val reEncodeRatio = if (summary.totalDurationMs > 0L) {
        summary.reEncodeDurationMs.toFloat() / summary.totalDurationMs.toFloat()
    } else {
        0f
    }
    val isCompactActions = LocalConfiguration.current.screenWidthDp < 430
    val speedupLabel = when {
        !hasSegments || summary.reEncodeSegments == 0 -> stringResource(R.string.render_speedup_instant)
        summary.estimatedSpeedup >= 100f -> "100x+"
        else -> stringResource(R.string.render_speedup_value, summary.estimatedSpeedup)
    }
    val outlook = when {
        !hasSegments -> RenderOutlookState(
            title = stringResource(R.string.render_preview_outlook_empty_title),
            body = stringResource(R.string.render_preview_outlook_empty_body),
            accent = ClearCutAccents.Blue,
            icon = Icons.Default.Preview
        )
        summary.reEncodeSegments == 0 -> RenderOutlookState(
            title = stringResource(R.string.render_preview_outlook_instant_title),
            body = stringResource(R.string.render_preview_outlook_instant_body),
            accent = ClearCutAccents.Green,
            icon = Icons.Default.Preview
        )
        summary.passThroughSegments == 0 -> RenderOutlookState(
            title = stringResource(R.string.render_preview_outlook_full_title),
            body = stringResource(R.string.render_preview_outlook_full_body),
            accent = ClearCutAccents.Yellow,
            icon = Icons.Default.RocketLaunch
        )
        else -> RenderOutlookState(
            title = stringResource(R.string.render_preview_outlook_mixed_title),
            body = stringResource(R.string.render_preview_outlook_mixed_body),
            accent = ClearCutAccents.Peach,
            icon = Icons.Default.Preview
        )
    }

    PremiumEditorPanel(
        title = stringResource(R.string.render_preview_title),
        subtitle = stringResource(R.string.render_preview_subtitle),
        icon = Icons.Default.Preview,
        accent = ClearCutAccents.Peach,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.render_preview_close_cd),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = outlook.accent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        color = outlook.accent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, outlook.accent.copy(alpha = 0.18f))
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = outlook.icon,
                            contentDescription = null,
                            tint = outlook.accent,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = outlook.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = semanticColors.text
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = outlook.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = semanticColors.subtext
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = formatDuration(summary.totalDurationMs),
                        accent = ClearCutAccents.Blue
                    )
                    PremiumPanelPill(
                        text = speedupLabel,
                        accent = outlook.accent
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RenderMetric(
                    label = stringResource(R.string.panel_render_pass_through),
                    value = summary.passThroughSegments.toString(),
                    accent = ClearCutAccents.Green,
                    modifier = Modifier.widthIn(min = 110.dp)
                )
                RenderMetric(
                    label = stringResource(R.string.panel_render_re_encode),
                    value = summary.reEncodeSegments.toString(),
                    accent = if (summary.reEncodeSegments > 0) ClearCutAccents.Yellow else ClearCutAccents.Green,
                    modifier = Modifier.widthIn(min = 110.dp)
                )
                RenderMetric(
                    label = stringResource(R.string.panel_render_speedup),
                    value = speedupLabel,
                    accent = outlook.accent,
                    modifier = Modifier.widthIn(min = 110.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(ClearCutAccents.Green.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(reEncodeRatio.coerceIn(0f, 1f))
                        .height(10.dp)
                        .background(ClearCutAccents.Yellow.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.render_pass_through_duration,
                        formatDuration(summary.passThroughDurationMs)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = ClearCutAccents.Green
                )
                Text(
                    text = stringResource(
                        R.string.render_re_encode_duration,
                        formatDuration(summary.reEncodeDurationMs)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (summary.reEncodeSegments > 0) ClearCutAccents.Yellow else semanticColors.subtext
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Blue) {
            Text(
                text = stringResource(R.string.panel_render_segments),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.render_preview_segments_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            if (segments.isEmpty()) {
                RenderMessageCard(
                    title = stringResource(R.string.render_preview_empty_title),
                    body = stringResource(R.string.render_preview_empty_body),
                    accent = ClearCutAccents.Blue,
                    icon = Icons.Default.Preview
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    segments.forEach { segment ->
                        RenderSegmentRow(segment = segment)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Mauve) {
            Text(
                text = stringResource(R.string.render_preview_actions_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.render_preview_actions_body),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            if (isCompactActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRenderPreview,
                        enabled = hasSegments,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, ClearCutAccents.Peach.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Peach)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Preview,
                            contentDescription = stringResource(R.string.render_preview_play_cd)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.panel_render_preview))
                    }

                    Button(
                        onClick = onRenderFull,
                        enabled = hasSegments,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClearCutAccents.Mauve)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = stringResource(R.string.panel_render_export)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.panel_render_export))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRenderPreview,
                        enabled = hasSegments,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, ClearCutAccents.Peach.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Peach)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Preview,
                            contentDescription = stringResource(R.string.render_preview_play_cd)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.panel_render_preview))
                    }

                    Button(
                        onClick = onRenderFull,
                        enabled = hasSegments,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClearCutAccents.Mauve)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = stringResource(R.string.panel_render_export)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.panel_render_export))
                    }
                }
            }

            if (!hasSegments) {
                Text(
                    text = stringResource(R.string.render_actions_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext
                )
            }
        }
    }
}

private data class RenderOutlookState(
    val title: String,
    val body: String,
    val accent: Color,
    val icon: ImageVector
)

@Composable
private fun RenderMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = semanticColors.subtext
            )
        }
    }
}

@Composable
private fun RenderMessageCard(
    title: String,
    body: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
                )
            }
        }
    }
}

@Composable
private fun RenderSegmentRow(segment: SmartRenderEngine.RenderSegment) {
    val semanticColors = LocalClearCutColors.current
    val accent = if (segment.needsReEncode) ClearCutAccents.Yellow else ClearCutAccents.Green
    val statusLabel = stringResource(
        if (segment.needsReEncode) R.string.panel_render_re_encode else R.string.panel_render_pass_through
    )
    val detail = if (segment.needsReEncode) {
        segment.reason.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    } else {
        stringResource(R.string.render_segment_pass_through_detail)
    }
    val durationLabel = stringResource(
        R.string.render_segment_duration,
        formatDuration(segment.endMs - segment.startMs)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (segment.needsReEncode) accent.copy(alpha = 0.12f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (segment.needsReEncode) accent.copy(alpha = 0.2f) else semanticColors.cardStroke
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = if (segment.needsReEncode) Icons.Default.RocketLaunch else Icons.Default.Preview,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.render_segment_time_range,
                            formatDuration(segment.startMs),
                            formatDuration(segment.endMs)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = semanticColors.text,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.subtext
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = statusLabel,
                    accent = accent
                )
                PremiumPanelPill(
                    text = durationLabel,
                    accent = if (segment.needsReEncode) semanticColors.overlayStrong else ClearCutAccents.Blue
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "%d:%02d:%02d".format(hours, minutes, seconds)
        minutes > 0L -> "%d:%02d".format(minutes, seconds)
        else -> "${seconds}s"
    }
}
