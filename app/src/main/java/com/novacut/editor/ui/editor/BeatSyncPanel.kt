package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeatSyncPanel(
    beatMarkers: List<Long>,
    totalDurationMs: Long,
    isAnalyzing: Boolean,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onAnalyze: () -> Unit,
    onTapBeat: () -> Unit = {},
    onClearBeats: () -> Unit = {},
    onApplyBeatSync: () -> Unit,
    onClose: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    val hasBeats = beatMarkers.isNotEmpty()
    val isCompactActions = LocalConfiguration.current.screenWidthDp < 430
    val avgBpm = remember(beatMarkers) {
        if (beatMarkers.size < 2) {
            0.0
        } else {
            val intervals = beatMarkers.zipWithNext { a, b -> b - a }
            val avgIntervalMs = intervals.average()
            if (avgIntervalMs > 0) 60_000.0 / avgIntervalMs else 0.0
        }
    }

    PremiumEditorPanel(
        title = stringResource(R.string.beat_sync_title),
        subtitle = stringResource(R.string.beat_sync_subtitle),
        icon = Icons.Default.MusicNote,
        accent = ClearCutAccents.Peach,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.beat_sync_close_cd),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Peach) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.beat_sync_rhythm_overview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasBeats) {
                            stringResource(R.string.beat_sync_overview_ready)
                        } else {
                            stringResource(R.string.beat_sync_overview_empty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = if (hasBeats) {
                            stringResource(R.string.beat_sync_markers_count, beatMarkers.size)
                        } else {
                            stringResource(R.string.beat_sync_no_beat_map)
                        },
                        accent = ClearCutAccents.Peach
                    )
                    PremiumPanelPill(
                        text = if (avgBpm > 0.0) stringResource(R.string.beat_sync_bpm_value, avgBpm.roundToInt()) else stringResource(R.string.beat_sync_bpm_pending),
                        accent = ClearCutAccents.Blue
                    )
                    PremiumPanelPill(
                        text = if (isPlaying) stringResource(R.string.beat_sync_tap_ready) else stringResource(R.string.beat_sync_play_to_tap),
                        accent = if (isPlaying) ClearCutAccents.Green else semanticColors.overlayStrong
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Blue) {
            Text(
                text = stringResource(R.string.beat_sync_capture_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.beat_sync_capture_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            if (!isPlaying && !isAnalyzing) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = semanticColors.surfaceLow,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = stringResource(R.string.beat_sync_playback_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.subtext,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }

            if (isCompactActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAnalyze,
                        enabled = !isAnalyzing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClearCutAccents.Peach,
                            contentColor = semanticColors.onAccent,
                            disabledContainerColor = ClearCutAccents.Peach.copy(alpha = 0.45f),
                            disabledContentColor = semanticColors.onAccent.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = semanticColors.onAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.beat_sync_detecting))
                        } else {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = stringResource(R.string.cd_detect_beats)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.beat_sync_detect))
                        }
                    }

                    OutlinedButton(
                        onClick = onTapBeat,
                        enabled = isPlaying,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPlaying) semanticColors.text else semanticColors.subtext
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = stringResource(R.string.cd_tap_beats)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.panel_beat_sync_tap))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAnalyze,
                        enabled = !isAnalyzing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClearCutAccents.Peach,
                            contentColor = semanticColors.onAccent,
                            disabledContainerColor = ClearCutAccents.Peach.copy(alpha = 0.45f),
                            disabledContentColor = semanticColors.onAccent.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = semanticColors.onAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.beat_sync_detecting))
                        } else {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = stringResource(R.string.cd_detect_beats)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.beat_sync_detect))
                        }
                    }

                    OutlinedButton(
                        onClick = onTapBeat,
                        enabled = isPlaying,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPlaying) semanticColors.text else semanticColors.subtext
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = stringResource(R.string.cd_tap_beats)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.panel_beat_sync_tap))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Mauve) {
            Text(
                text = stringResource(R.string.beat_sync_timeline_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = if (hasBeats) {
                    stringResource(R.string.beat_sync_timeline_ready)
                } else {
                    stringResource(R.string.beat_sync_timeline_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            Surface(
                color = semanticColors.surfaceBase,
                shape = RoundedCornerShape(20.dp)
            ) {
                if (hasBeats) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(semanticColors.surfaceBase)
                    ) {
                        if (totalDurationMs > 0) {
                            beatMarkers.forEach { beatMs ->
                                val x = (beatMs.toFloat() / totalDurationMs) * size.width
                                drawLine(
                                    color = ClearCutAccents.Peach,
                                    start = Offset(x, 8f),
                                    end = Offset(x, size.height - 8f),
                                    strokeWidth = 4f
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.beat_sync_no_markers),
                            style = MaterialTheme.typography.bodyMedium,
                            color = semanticColors.overlayStrong
                        )
                    }
                }
            }

            if (hasBeats) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BeatSyncMetric(
                        label = stringResource(R.string.beat_sync_label_beats),
                        value = beatMarkers.size.toString(),
                        accent = ClearCutAccents.Peach
                    )
                    BeatSyncMetric(
                        label = stringResource(R.string.beat_sync_label_bpm),
                        value = if (avgBpm > 0.0) avgBpm.roundToInt().toString() else stringResource(R.string.beat_sync_bpm_placeholder),
                        accent = ClearCutAccents.Blue
                    )
                    BeatSyncMetric(
                        label = stringResource(R.string.beat_sync_label_scan),
                        value = stringResource(R.string.panel_text_template_duration_format, (totalDurationMs / 1000f).roundToInt()),
                        accent = ClearCutAccents.Mauve
                    )
                }

                OutlinedButton(
                    onClick = onClearBeats,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Red)
                ) {
                    Text(text = stringResource(R.string.panel_beat_sync_clear))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Green) {
            Text(
                text = stringResource(R.string.beat_sync_apply_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.beat_sync_apply_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            Button(
                onClick = onApplyBeatSync,
                enabled = hasBeats,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClearCutAccents.Mauve,
                    contentColor = semanticColors.onAccent,
                    disabledContainerColor = semanticColors.surface,
                    disabledContentColor = semanticColors.subtext
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = stringResource(R.string.beat_sync_apply))
            }
        }
    }
}

@Composable
private fun BeatSyncMetric(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = semanticColors.subtext
            )
        }
    }
}
