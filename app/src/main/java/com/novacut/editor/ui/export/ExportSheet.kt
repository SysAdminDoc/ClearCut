package com.novacut.editor.ui.export

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.*
import androidx.compose.ui.text.font.FontWeight
import com.novacut.editor.ui.theme.Mocha

@Composable
fun ExportSheet(
    config: ExportConfig,
    exportState: ExportState,
    exportProgress: Float,
    aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    errorMessage: String? = null,
    exportStartTime: Long = 0L,
    totalDurationMs: Long = 0L,
    onConfigChanged: (ExportConfig) -> Unit,
    onStartExport: () -> Unit,
    onShare: () -> Unit = {},
    onSaveToGallery: () -> Unit = {},
    onCancel: () -> Unit = {},
    onExportOtio: () -> Unit = {},
    onExportFcpxml: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.export_title), color = Mocha.Text, fontSize = 18.sp)
            if (exportState != ExportState.EXPORTING) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (exportState == ExportState.EXPORTING) {
            // Export progress with elapsed time and ETA
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.export_exporting),
                    color = Mocha.Text,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { exportProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Mocha.Mauve,
                    trackColor = Mocha.Surface0,
                )
                Spacer(modifier = Modifier.height(8.dp))

                val pct = (exportProgress * 100).toInt().coerceIn(0, 100)
                val elapsedMs = if (exportStartTime > 0L) System.currentTimeMillis() - exportStartTime else 0L
                val elapsedSec = (elapsedMs / 1000).toInt()
                val elapsedStr = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)
                val etaStr = if (exportProgress > 0.05f && elapsedMs > 2000L) {
                    val totalEstMs = (elapsedMs / exportProgress).toLong()
                    val remainMs = (totalEstMs - elapsedMs).coerceAtLeast(0L)
                    val remainSec = (remainMs / 1000).toInt()
                    "%d:%02d remaining".format(remainSec / 60, remainSec % 60)
                } else ""

                Text(
                    "$pct%",
                    color = Mocha.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Elapsed: $elapsedStr", color = Mocha.Subtext0, fontSize = 12.sp)
                    if (etaStr.isNotEmpty()) {
                        Text(etaStr, color = Mocha.Blue, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.export_cancel), color = Mocha.Red)
                }
            }
            return
        }

        if (exportState == ExportState.COMPLETE) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.complete),
                    tint = Mocha.Green,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.export_complete), color = Mocha.Green, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.share), color = Mocha.Crust)
                    }
                    Button(
                        onClick = onSaveToGallery,
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Green)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = stringResource(R.string.export_save_to_gallery_cd), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.export_save_to_gallery), color = Mocha.Crust)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.done), color = Mocha.Subtext0)
                }
            }
            return
        }

        if (exportState == ExportState.CANCELLED) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = stringResource(R.string.cancelled),
                    tint = Mocha.Peach,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.export_cancelled), color = Mocha.Peach, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.done), color = Mocha.Subtext0)
                }
            }
            return
        }

        if (exportState == ExportState.ERROR) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = stringResource(R.string.error),
                    tint = Mocha.Red,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.export_failed), color = Mocha.Red, fontSize = 16.sp)
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        errorMessage,
                        color = Mocha.Subtext0,
                        fontSize = 12.sp,
                        maxLines = 3
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStartExport,
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.retry), color = Mocha.Crust)
                    }
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Surface1)
                    ) {
                        Text(stringResource(R.string.close), color = Mocha.Text)
                    }
                }
            }
            return
        }

        // Platform Presets
        Text(stringResource(R.string.export_quick_presets), color = Mocha.Subtext1, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PlatformPreset.entries.forEach { preset ->
                val isSelected = config.platformPreset == preset
                FilterChip(
                    onClick = {
                        onConfigChanged(config.copy(
                            resolution = preset.resolution,
                            frameRate = preset.frameRate,
                            codec = preset.codec,
                            platformPreset = preset
                        ))
                    },
                    label = { Text(preset.displayName, fontSize = 11.sp) },
                    selected = isSelected,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Green.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Green
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Audio Only toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.export_audio_only), color = Mocha.Text, fontSize = 13.sp)
            Switch(
                checked = config.exportAudioOnly,
                onCheckedChange = { onConfigChanged(config.copy(exportAudioOnly = it)) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Mocha.Mauve,
                    checkedThumbColor = Mocha.Crust,
                    uncheckedTrackColor = Mocha.Surface1,
                    uncheckedThumbColor = Mocha.Subtext0
                )
            )
        }

        // Chapter Markers toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Include Chapter Markers", color = Mocha.Text, fontSize = 13.sp)
            Switch(
                checked = config.includeChapterMarkers,
                onCheckedChange = { onConfigChanged(config.copy(includeChapterMarkers = it)) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Mocha.Mauve,
                    checkedThumbColor = Mocha.Crust,
                    uncheckedTrackColor = Mocha.Surface1,
                    uncheckedThumbColor = Mocha.Subtext0
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Resolution
        Text(stringResource(R.string.export_resolution), color = Mocha.Subtext1, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Resolution.entries.forEach { res ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(resolution = res)) },
                    label = { Text(res.label, fontSize = 12.sp) },
                    selected = config.resolution == res,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Frame rate
        Text(stringResource(R.string.export_frame_rate), color = Mocha.Subtext1, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 30, 60).forEach { fps ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(frameRate = fps)) },
                    label = { Text("${fps}fps", fontSize = 12.sp) },
                    selected = config.frameRate == fps,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Codec (filtered by device hardware support)
        val availableCodecs = remember { ExportConfig.getAvailableCodecs() }
        Text(stringResource(R.string.export_codec), color = Mocha.Subtext1, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoCodec.entries.forEach { codec ->
                val isAvailable = codec in availableCodecs
                FilterChip(
                    onClick = { if (isAvailable) onConfigChanged(config.copy(codec = codec)) },
                    label = { Text(codec.label, fontSize = 12.sp) },
                    selected = config.codec == codec,
                    enabled = isAvailable,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve,
                        disabledContainerColor = Mocha.Surface0.copy(alpha = 0.3f),
                        disabledLabelColor = Mocha.Subtext0.copy(alpha = 0.4f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Audio Codec
        Text("Audio Codec", color = Mocha.Subtext1, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioCodec.entries.forEach { codec ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(audioCodec = codec)) },
                    label = { Text(codec.label, fontSize = 12.sp) },
                    selected = config.audioCodec == codec,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Teal.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Teal
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quality
        Text(stringResource(R.string.export_quality), color = Mocha.Subtext1, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportQuality.entries.forEach { quality ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(quality = quality)) },
                    label = { Text(quality.label, fontSize = 12.sp) },
                    selected = config.quality == quality,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Estimated file info
        val (w, h) = config.resolution.forAspect(aspectRatio)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Mocha.Surface0)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.export_output_details), color = Mocha.Subtext1, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${w}x${h} @ ${config.frameRate}fps", color = Mocha.Text, fontSize = 13.sp)
                Text("${config.codec.label} / ${config.quality.label}", color = Mocha.Text, fontSize = 13.sp)
                val bitrateDesc = when {
                    config.videoBitrate >= 40_000_000 -> stringResource(R.string.export_studio_quality)
                    config.videoBitrate >= 15_000_000 -> stringResource(R.string.export_great_for_youtube)
                    config.videoBitrate >= 6_000_000 -> stringResource(R.string.export_good_for_sharing)
                    else -> stringResource(R.string.export_compact_file_size)
                }
                Text(
                    "${config.videoBitrate / 1_000_000}Mbps — $bitrateDesc",
                    color = Mocha.Subtext0,
                    fontSize = 12.sp
                )
                // Estimated file size
                if (totalDurationMs > 0L) {
                    val totalBitrate = config.videoBitrate + config.audioBitrate
                    val estBytes = (totalBitrate.toLong() * totalDurationMs) / 8000L
                    val estStr = when {
                        estBytes >= 1_073_741_824L -> "%.1f GB".format(estBytes / 1_073_741_824.0)
                        estBytes >= 1_048_576L -> "%.0f MB".format(estBytes / 1_048_576.0)
                        else -> "%.0f KB".format(estBytes / 1024.0)
                    }
                    Text(
                        "~$estStr estimated",
                        color = Mocha.Peach,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Export button
        Button(
            onClick = onStartExport,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Mocha.Mauve,
                contentColor = Mocha.Crust
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.export_video_cd), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.export_video_button), fontSize = 15.sp)
        }

        // Timeline Exchange section
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.export_timeline_exchange), color = Mocha.Subtext0, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onExportOtio,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Blue),
                border = BorderStroke(1.dp, Mocha.Blue.copy(alpha = 0.4f))
            ) {
                Text("OTIO", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = onExportFcpxml,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Blue),
                border = BorderStroke(1.dp, Mocha.Blue.copy(alpha = 0.4f))
            ) {
                Text("FCPXML", fontSize = 11.sp)
            }
        }
    }
}
