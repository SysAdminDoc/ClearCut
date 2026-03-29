package com.novacut.editor.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

@Composable
fun BeatSyncPanel(
    beatMarkers: List<Long>,
    totalDurationMs: Long,
    isAnalyzing: Boolean,
    isPlaying: Boolean = false,
    onAnalyze: () -> Unit,
    onTapBeat: () -> Unit = {},
    onClearBeats: () -> Unit = {},
    onApplyBeatSync: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasBeats = beatMarkers.isNotEmpty()
    val avgBpm = remember(beatMarkers) {
        if (beatMarkers.size < 2) 0.0
        else {
            val intervals = beatMarkers.zipWithNext { a, b -> b - a }
            val avgIntervalMs = intervals.average()
            if (avgIntervalMs > 0) 60_000.0 / avgIntervalMs else 0.0
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Mocha.Mantle)
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = stringResource(R.string.cd_beat_sync),
                tint = Mocha.Peach,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.beat_sync_title),
                color = Mocha.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.beat_sync_close_cd),
                    tint = Mocha.Subtext0,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Detect Beats + Tap Beats row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onAnalyze,
                enabled = !isAnalyzing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Surface0,
                    contentColor = Mocha.Text,
                    disabledContainerColor = Mocha.Surface0.copy(alpha = 0.5f),
                    disabledContentColor = Mocha.Subtext0
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Mocha.Peach
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.beat_sync_detecting), fontSize = 13.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = stringResource(R.string.cd_detect_beats),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.beat_sync_detect), fontSize = 13.sp)
                }
            }

            Button(
                onClick = onTapBeat,
                enabled = isPlaying,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Peach,
                    contentColor = Mocha.Crust,
                    disabledContainerColor = Mocha.Peach.copy(alpha = 0.3f),
                    disabledContentColor = Mocha.Subtext0
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = stringResource(R.string.cd_tap_beats),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.panel_beat_sync_tap), fontSize = 13.sp)
            }
        }

        // Marker count + clear row
        if (hasBeats) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.beat_sync_markers_count, beatMarkers.size),
                    color = Mocha.Subtext0,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearBeats) {
                    Text(stringResource(R.string.panel_beat_sync_clear), color = Mocha.Red, fontSize = 12.sp)
                }
            }
        }

        // Beat info & visualization
        if (hasBeats) {
            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${beatMarkers.size}",
                        color = Mocha.Peach,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.beat_sync_label_beats),
                        color = Mocha.Subtext0,
                        fontSize = 11.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.0f".format(avgBpm),
                        color = Mocha.Peach,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.beat_sync_label_bpm),
                        color = Mocha.Subtext0,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Beat timeline visualization
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Mocha.Base)
            ) {
                if (totalDurationMs > 0) {
                    beatMarkers.forEach { beatMs ->
                        val x = (beatMs.toFloat() / totalDurationMs) * size.width
                        drawLine(
                            color = Mocha.Peach,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Apply button
            Button(
                onClick = onApplyBeatSync,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Mauve,
                    contentColor = Mocha.Crust
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.beat_sync_apply),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
