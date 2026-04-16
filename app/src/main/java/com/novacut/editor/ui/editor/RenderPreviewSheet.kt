package com.novacut.editor.ui.editor

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.ui.theme.Mocha

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
    val reEncodeRatio = if (summary.totalDurationMs > 0L) {
        summary.reEncodeDurationMs.toFloat() / summary.totalDurationMs.toFloat()
    } else {
        0f
    }
    val isCompactActions = LocalConfiguration.current.screenWidthDp < 430
    val speedupLabel = when {
        summary.reEncodeSegments == 0 -> "Instant"
        summary.estimatedSpeedup >= 100f -> "100x+"
        else -> "%.1fx".format(summary.estimatedSpeedup)
    }

    PremiumEditorPanel(
        title = stringResource(R.string.render_preview_title),
        subtitle = "See which sections can stream through untouched and which shots still need a full render pass.",
        icon = Icons.Default.Preview,
        accent = Mocha.Peach,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.render_preview_close_cd),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = if (summary.reEncodeSegments > 0) Mocha.Peach else Mocha.Green) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart render outlook",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "NovaCut can skip untouched sections and focus encode time only where the timeline changed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = formatDuration(summary.totalDurationMs),
                        accent = Mocha.Blue
                    )
                    PremiumPanelPill(
                        text = speedupLabel,
                        accent = if (summary.reEncodeSegments > 0) Mocha.Peach else Mocha.Green
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
                    accent = Mocha.Green,
                    modifier = Modifier.widthIn(min = 110.dp)
                )
                RenderMetric(
                    label = stringResource(R.string.panel_render_re_encode),
                    value = summary.reEncodeSegments.toString(),
                    accent = if (summary.reEncodeSegments > 0) Mocha.Yellow else Mocha.Green,
                    modifier = Modifier.widthIn(min = 110.dp)
                )
                RenderMetric(
                    label = stringResource(R.string.panel_render_speedup),
                    value = speedupLabel,
                    accent = Mocha.Peach,
                    modifier = Modifier.widthIn(min = 110.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(Mocha.Green.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(reEncodeRatio.coerceIn(0f, 1f))
                        .height(10.dp)
                        .background(Mocha.Yellow.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
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
                    color = Mocha.Green
                )
                Text(
                    text = stringResource(
                        R.string.render_re_encode_duration,
                        formatDuration(summary.reEncodeDurationMs)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (summary.reEncodeSegments > 0) Mocha.Yellow else Mocha.Subtext0
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            Text(
                text = stringResource(R.string.panel_render_segments),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = "Each block shows whether NovaCut can copy that range directly or has to render it again.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            if (segments.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Mocha.PanelRaised,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Mocha.CardStroke)
                ) {
                    Text(
                        text = "No clips are on the timeline yet, so there is nothing to analyze.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0,
                        modifier = Modifier.padding(16.dp)
                    )
                }
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

        PremiumPanelCard(accent = Mocha.Mauve) {
            Text(
                text = "Render choices",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = "Generate a lightweight check render first, or jump straight into the full export flow with the current settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            if (isCompactActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRenderPreview,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Mocha.Peach.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Peach)
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve)
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Mocha.Peach.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Peach)
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve)
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
        }
    }
}

@Composable
private fun RenderMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
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
                color = Mocha.Subtext0
            )
        }
    }
}

@Composable
private fun RenderSegmentRow(segment: SmartRenderEngine.RenderSegment) {
    val accent = if (segment.needsReEncode) Mocha.Yellow else Mocha.Green
    val statusLabel = if (segment.needsReEncode) "Re-encode" else "Pass-through"
    val detail = if (segment.needsReEncode) {
        segment.reason.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    } else {
        "Stream copied directly with no image-processing work."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (segment.needsReEncode) accent.copy(alpha = 0.12f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (segment.needsReEncode) accent.copy(alpha = 0.2f) else Mocha.CardStroke
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${formatDuration(segment.startMs)} - ${formatDuration(segment.endMs)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Mocha.Text,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Mocha.Subtext0
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            PremiumPanelPill(
                text = statusLabel,
                accent = accent
            )
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
