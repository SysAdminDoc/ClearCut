package com.novacut.editor.ui.editor

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
import com.novacut.editor.ui.theme.Mocha
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
        subtitle = "Find the groove, mark the pulse, and snap cuts to the rhythm without scrubbing by hand.",
        icon = Icons.Default.MusicNote,
        accent = Mocha.Peach,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.beat_sync_close_cd),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Peach) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rhythm overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasBeats) {
                            "NovaCut has a beat map ready. You can refine the pulse manually before you commit it to the timeline."
                        } else {
                            "Start with beat detection or tap along live while preview playback is running."
                        },
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
                        text = if (hasBeats) {
                            stringResource(R.string.beat_sync_markers_count, beatMarkers.size)
                        } else {
                            "No beat map"
                        },
                        accent = Mocha.Peach
                    )
                    PremiumPanelPill(
                        text = if (avgBpm > 0.0) "${avgBpm.roundToInt()} BPM" else "BPM pending",
                        accent = Mocha.Blue
                    )
                    PremiumPanelPill(
                        text = if (isPlaying) "Tap ready" else "Play to tap",
                        accent = if (isPlaying) Mocha.Green else Mocha.Overlay1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            Text(
                text = "Capture the beat",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = "Use automatic analysis for a quick first pass, then tap beats live if you want to tighten sync against the exact feel of the music.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            if (!isPlaying && !isAnalyzing) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Mocha.Surface0,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "Start playback to tap beats live after the first analysis pass.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mocha.Subtext0,
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
                            containerColor = Mocha.Peach,
                            contentColor = Mocha.Crust,
                            disabledContainerColor = Mocha.Peach.copy(alpha = 0.45f),
                            disabledContentColor = Mocha.Crust.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Mocha.Crust,
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
                            contentColor = if (isPlaying) Mocha.Text else Mocha.Subtext0
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
                            containerColor = Mocha.Peach,
                            contentColor = Mocha.Crust,
                            disabledContainerColor = Mocha.Peach.copy(alpha = 0.45f),
                            disabledContentColor = Mocha.Crust.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Mocha.Crust,
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
                            contentColor = if (isPlaying) Mocha.Text else Mocha.Subtext0
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

        PremiumPanelCard(accent = Mocha.Mauve) {
            Text(
                text = "Beat timeline",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = if (hasBeats) {
                    "Review the detected pulse before applying it to your edit decisions."
                } else {
                    "Detected markers will appear here as soon as NovaCut maps the rhythm."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            Surface(
                color = Mocha.Base,
                shape = RoundedCornerShape(20.dp)
            ) {
                if (hasBeats) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Mocha.Base)
                    ) {
                        if (totalDurationMs > 0) {
                            beatMarkers.forEach { beatMs ->
                                val x = (beatMs.toFloat() / totalDurationMs) * size.width
                                drawLine(
                                    color = Mocha.Peach,
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
                            text = "No markers yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mocha.Overlay1
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
                        accent = Mocha.Peach
                    )
                    BeatSyncMetric(
                        label = stringResource(R.string.beat_sync_label_bpm),
                        value = if (avgBpm > 0.0) avgBpm.roundToInt().toString() else "—",
                        accent = Mocha.Blue
                    )
                    BeatSyncMetric(
                        label = stringResource(R.string.beat_sync_label_scan),
                        value = "${(totalDurationMs / 1000f).roundToInt()}s",
                        accent = Mocha.Mauve
                    )
                }

                OutlinedButton(
                    onClick = onClearBeats,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Red)
                ) {
                    Text(text = stringResource(R.string.panel_beat_sync_clear))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Green) {
            Text(
                text = "Apply beat sync",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = "Use the current beat map to drive timing decisions across the edit.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            Button(
                onClick = onApplyBeatSync,
                enabled = hasBeats,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Mauve,
                    contentColor = Mocha.Crust,
                    disabledContainerColor = Mocha.Surface1,
                    disabledContentColor = Mocha.Subtext0
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
                color = Mocha.Subtext0
            )
        }
    }
}
