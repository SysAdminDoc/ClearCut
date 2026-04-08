package com.novacut.editor.ui.export

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onExportSubtitles: (SubtitleFormat) -> Unit = {},
    onCaptureFrame: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableCodecs = remember { ExportConfig.getAvailableCodecs() }
    val (w, h) = config.resolution.forAspect(aspectRatio)
    val estimatedSize = remember(config, totalDurationMs) { estimateExportSize(totalDurationMs, config) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Panel, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(4.dp)
                .background(Mocha.Surface2.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Mocha.Mauve.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.22f))
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FileUpload,
                        contentDescription = stringResource(R.string.export_title),
                        tint = Mocha.Rosewater,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.export_title),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    stringResource(R.string.export_subtitle),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (exportState != ExportState.EXPORTING) {
                Surface(
                    color = Mocha.PanelHighest,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Mocha.CardStroke)
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (exportState == ExportState.EXPORTING) {
            val pct = (exportProgress * 100).toInt().coerceIn(0, 100)
            val elapsedMs = if (exportStartTime > 0L) System.currentTimeMillis() - exportStartTime else 0L
            val elapsedSec = (elapsedMs / 1000).toInt()
            val elapsedStr = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)
            val etaStr = if (exportProgress > 0.05f && elapsedMs > 2000L) {
                val totalEstMs = (elapsedMs / exportProgress).toLong()
                val remainMs = (totalEstMs - elapsedMs).coerceAtLeast(0L)
                val remainSec = (remainMs / 1000).toInt()
                stringResource(R.string.export_eta_remaining, "%d:%02d".format(remainSec / 60, remainSec % 60))
            } else null

            ExportStateCard(
                icon = Icons.Default.FileUpload,
                tint = Mocha.Mauve,
                title = stringResource(R.string.export_exporting),
                body = stringResource(R.string.export_elapsed, elapsedStr),
                progress = exportProgress,
                progressLabel = "$pct%",
                secondaryBody = etaStr,
                primaryLabel = stringResource(R.string.export_cancel),
                onPrimary = onCancel
            )
            return
        }

        if (exportState == ExportState.COMPLETE) {
            ExportStateCard(
                icon = Icons.Default.CheckCircle,
                tint = Mocha.Green,
                title = stringResource(R.string.export_complete),
                body = stringResource(R.string.export_subtitle),
                primaryLabel = stringResource(R.string.share),
                onPrimary = onShare,
                secondaryLabel = stringResource(R.string.export_save_to_gallery),
                onSecondary = onSaveToGallery,
                tertiaryLabel = stringResource(R.string.done),
                onTertiary = onClose
            )
            return
        }

        if (exportState == ExportState.CANCELLED) {
            ExportStateCard(
                icon = Icons.Default.Cancel,
                tint = Mocha.Peach,
                title = stringResource(R.string.export_cancelled),
                body = stringResource(R.string.export_subtitle),
                primaryLabel = stringResource(R.string.done),
                onPrimary = onClose
            )
            return
        }

        if (exportState == ExportState.ERROR) {
            ExportStateCard(
                icon = Icons.Default.Error,
                tint = Mocha.Red,
                title = stringResource(R.string.export_failed),
                body = errorMessage ?: stringResource(R.string.error),
                primaryLabel = stringResource(R.string.retry),
                onPrimary = onStartExport,
                secondaryLabel = stringResource(R.string.close),
                onSecondary = onClose
            )
            return
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
            border = BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            Mocha.PanelHighest.copy(alpha = 0.9f),
                            Mocha.Panel
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.export_delivery_summary),
                        color = Mocha.Rosewater,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        "${w}x${h} - ${aspectRatio.label}",
                        color = Mocha.Text,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportPill("${config.frameRate}fps", Mocha.Mauve)
                        ExportPill(config.codec.label, Mocha.Sapphire)
                        ExportPill(config.quality.label, Mocha.Teal)
                    }
                    val bitrateDesc = when {
                        config.videoBitrate >= 40_000_000 -> stringResource(R.string.export_studio_quality)
                        config.videoBitrate >= 15_000_000 -> stringResource(R.string.export_great_for_youtube)
                        config.videoBitrate >= 6_000_000 -> stringResource(R.string.export_good_for_sharing)
                        else -> stringResource(R.string.export_compact_file_size)
                    }
                    Text(
                        buildString {
                            append("${config.videoBitrate / 1_000_000}Mbps - $bitrateDesc")
                            if (estimatedSize != null) append(" - ~$estimatedSize")
                        },
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Platform Presets
        Text(stringResource(R.string.export_quick_presets), color = Mocha.Rosewater, style = MaterialTheme.typography.labelLarge)
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
                    label = { Text(preset.displayName, style = MaterialTheme.typography.labelMedium) },
                    selected = isSelected,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.PanelHighest,
                        labelColor = Mocha.Subtext0,
                        selectedContainerColor = Mocha.Green.copy(alpha = 0.16f),
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
            Text(stringResource(R.string.export_audio_only), color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = config.exportAudioOnly,
                onCheckedChange = { onConfigChanged(config.copy(exportAudioOnly = it, exportAsGif = false, captureFrameOnly = false)) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Mocha.Mauve,
                    checkedThumbColor = Mocha.Crust,
                    uncheckedTrackColor = Mocha.Surface1,
                    uncheckedThumbColor = Mocha.Subtext0
                )
            )
        }

        // Subtitle Export
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.export_subtitles), color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = config.subtitleFormat != null,
                onCheckedChange = {
                    onConfigChanged(config.copy(subtitleFormat = if (it) SubtitleFormat.SRT else null))
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Mocha.Mauve, checkedThumbColor = Mocha.Crust, uncheckedTrackColor = Mocha.Surface1, uncheckedThumbColor = Mocha.Subtext0)
            )
        }

        // Show format picker when enabled
        if (config.subtitleFormat != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleFormat.entries.forEach { fmt ->
                    FilterChip(
                        onClick = { onConfigChanged(config.copy(subtitleFormat = fmt)) },
                        label = { Text(fmt.displayName, style = MaterialTheme.typography.labelMedium) },
                        selected = config.subtitleFormat == fmt,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Mocha.PanelHighest,
                            labelColor = Mocha.Subtext0,
                            selectedContainerColor = Mocha.Mauve.copy(alpha = 0.16f),
                            selectedLabelColor = Mocha.Mauve
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Stems Export (per-track audio)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.export_stems), color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = config.exportStemsOnly,
                onCheckedChange = { onConfigChanged(config.copy(exportStemsOnly = it, exportAudioOnly = false)) },
                colors = SwitchDefaults.colors(checkedTrackColor = Mocha.Mauve, checkedThumbColor = Mocha.Crust, uncheckedTrackColor = Mocha.Surface1, uncheckedThumbColor = Mocha.Subtext0)
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
            Text(stringResource(R.string.export_chapter_markers), color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
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

        // Transparent Background toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.export_transparent_bg), color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = config.transparentBackground,
                onCheckedChange = { onConfigChanged(config.copy(transparentBackground = it)) },
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
        Text(stringResource(R.string.export_resolution), color = Mocha.Rosewater, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Resolution.entries.forEach { res ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(resolution = res)) },
                    label = { Text(res.label, style = MaterialTheme.typography.labelMedium) },
                    selected = config.resolution == res,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.PanelHighest,
                        labelColor = Mocha.Subtext0,
                        selectedContainerColor = Mocha.Rosewater.copy(alpha = 0.16f),
                        selectedLabelColor = Mocha.Rosewater
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Frame rate
        Text(stringResource(R.string.export_frame_rate), color = Mocha.Subtext0, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 30, 60).forEach { fps ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(frameRate = fps)) },
                    label = { Text("${fps}fps", style = MaterialTheme.typography.labelMedium) },
                    selected = config.frameRate == fps,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.PanelHighest,
                        labelColor = Mocha.Subtext0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.16f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Codec (filtered by device hardware support)
        Text(stringResource(R.string.export_codec), color = Mocha.Subtext0, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoCodec.entries.forEach { codec ->
                val isAvailable = codec in availableCodecs
                FilterChip(
                    onClick = { if (isAvailable) onConfigChanged(config.copy(codec = codec)) },
                    label = { Text(codec.label, style = MaterialTheme.typography.labelMedium) },
                    selected = config.codec == codec,
                    enabled = isAvailable,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.PanelHighest,
                        labelColor = Mocha.Subtext0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.16f),
                        selectedLabelColor = Mocha.Mauve,
                        disabledContainerColor = Mocha.PanelHighest.copy(alpha = 0.45f),
                        disabledLabelColor = Mocha.Subtext0.copy(alpha = 0.4f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Audio Codec
        Text(stringResource(R.string.export_audio_codec), color = Mocha.Subtext0, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioCodec.entries.forEach { codec ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(audioCodec = codec)) },
                    label = { Text(codec.label, style = MaterialTheme.typography.labelMedium) },
                    selected = config.audioCodec == codec,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.PanelHighest,
                        labelColor = Mocha.Subtext0,
                        selectedContainerColor = Mocha.Teal.copy(alpha = 0.16f),
                        selectedLabelColor = Mocha.Teal
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quality
        Text(stringResource(R.string.export_quality), color = Mocha.Subtext0, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportQuality.entries.forEach { quality ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(quality = quality)) },
                    label = { Text(quality.label, style = MaterialTheme.typography.labelMedium) },
                    selected = config.quality == quality,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.PanelHighest,
                        labelColor = Mocha.Subtext0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.16f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Estimated file info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Mocha.PanelHighest),
            border = BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.export_output_details), color = Mocha.Rosewater, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${w}x${h} @ ${config.frameRate}fps", color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
                Text("${config.codec.label} / ${config.quality.label}", color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
                val bitrateDesc = when {
                    config.videoBitrate >= 40_000_000 -> stringResource(R.string.export_studio_quality)
                    config.videoBitrate >= 15_000_000 -> stringResource(R.string.export_great_for_youtube)
                    config.videoBitrate >= 6_000_000 -> stringResource(R.string.export_good_for_sharing)
                    else -> stringResource(R.string.export_compact_file_size)
                }
                Text(
                    "${config.videoBitrate / 1_000_000}Mbps - $bitrateDesc",
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall
                )
                // Estimated file size
                if (estimatedSize != null) {
                    Text(
                        "~$estimatedSize estimated",
                        color = Mocha.Peach,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // GIF Export toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.export_gif), color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = config.exportAsGif,
                onCheckedChange = { onConfigChanged(config.copy(exportAsGif = it, captureFrameOnly = false, exportAudioOnly = false)) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Mocha.Mauve,
                    checkedThumbColor = Mocha.Crust,
                    uncheckedTrackColor = Mocha.Surface1,
                    uncheckedThumbColor = Mocha.Subtext0
                )
            )
        }
        if (config.exportAsGif) {
            Text(stringResource(R.string.export_gif_frame_rate), color = Mocha.Subtext0, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 20).forEach { fps ->
                    FilterChip(
                        onClick = { onConfigChanged(config.copy(gifFrameRate = fps)) },
                        label = { Text("${fps}fps", style = MaterialTheme.typography.labelMedium) },
                        selected = config.gifFrameRate == fps,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Mocha.PanelHighest,
                            labelColor = Mocha.Subtext0,
                            selectedContainerColor = Mocha.Mauve.copy(alpha = 0.16f),
                            selectedLabelColor = Mocha.Mauve
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.export_gif_max_width), color = Mocha.Subtext0, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(320, 480, 640).forEach { w ->
                    FilterChip(
                        onClick = { onConfigChanged(config.copy(gifMaxWidth = w)) },
                        label = { Text("${w}px", style = MaterialTheme.typography.labelMedium) },
                        selected = config.gifMaxWidth == w,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Mocha.PanelHighest,
                            labelColor = Mocha.Subtext0,
                            selectedContainerColor = Mocha.Mauve.copy(alpha = 0.16f),
                            selectedLabelColor = Mocha.Mauve
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Frame Capture (single frame)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.export_capture_frame), color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = config.captureFrameOnly,
                onCheckedChange = { onConfigChanged(config.copy(captureFrameOnly = it, exportAsGif = false, exportAudioOnly = false)) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Mocha.Mauve,
                    checkedThumbColor = Mocha.Crust,
                    uncheckedTrackColor = Mocha.Surface1,
                    uncheckedThumbColor = Mocha.Subtext0
                )
            )
        }
        if (config.captureFrameOnly) {
            Text(stringResource(R.string.export_capture_format), color = Mocha.Subtext0, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrameCaptureFormat.entries.forEach { fmt ->
                    FilterChip(
                        onClick = { onConfigChanged(config.copy(captureFormat = fmt)) },
                        label = { Text(fmt.displayName, style = MaterialTheme.typography.labelMedium) },
                        selected = config.captureFormat == fmt,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Mocha.PanelHighest,
                            labelColor = Mocha.Subtext0,
                            selectedContainerColor = Mocha.Mauve.copy(alpha = 0.16f),
                            selectedLabelColor = Mocha.Mauve
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Export button
        Button(
            onClick = {
                if (config.captureFrameOnly) onCaptureFrame() else {
                    onStartExport()
                    config.subtitleFormat?.let { onExportSubtitles(it) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Mocha.Rosewater,
                contentColor = Mocha.Midnight
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.export_video_cd), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                when {
                    config.exportAsGif -> stringResource(R.string.export_gif_button)
                    config.captureFrameOnly -> stringResource(R.string.export_capture_button)
                    else -> stringResource(R.string.export_video_button)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Timeline Exchange section
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.export_timeline_exchange), color = Mocha.Rosewater, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onExportOtio,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Sapphire),
                border = BorderStroke(1.dp, Mocha.CardStrokeStrong),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.export_otio), style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onExportFcpxml,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Sapphire),
                border = BorderStroke(1.dp, Mocha.CardStrokeStrong),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.export_fcpxml), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ExportStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    progress: Float? = null,
    progressLabel: String? = null,
    secondaryBody: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
        border = BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(26.dp)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = 0.12f),
                        Mocha.PanelHighest.copy(alpha = 0.82f),
                        Mocha.Panel
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = tint.copy(alpha = 0.14f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, tint.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier.padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = title,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(title, color = Mocha.Text, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(body, color = Mocha.Subtext0, style = MaterialTheme.typography.bodyMedium)

                if (progress != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = tint,
                        trackColor = Mocha.PanelHighest
                    )
                }
                if (progressLabel != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(progressLabel, color = Mocha.Text, style = MaterialTheme.typography.titleMedium)
                }
                if (!secondaryBody.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(secondaryBody, color = tint, style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tint == Mocha.Red) Mocha.Red else Mocha.Rosewater,
                        contentColor = if (tint == Mocha.Red) Mocha.Crust else Mocha.Midnight
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(primaryLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }

                if (secondaryLabel != null && onSecondary != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Mocha.CardStrokeStrong),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(secondaryLabel, color = Mocha.Text, style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (tertiaryLabel != null && onTertiary != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = onTertiary) {
                        Text(tertiaryLabel, color = Mocha.Subtext0, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportPill(
    text: String,
    accent: Color
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun estimateExportSize(
    totalDurationMs: Long,
    config: ExportConfig
): String? {
    if (totalDurationMs <= 0L) return null

    val totalBitrate = config.videoBitrate + config.audioBitrate
    val estimatedBytes = (totalBitrate.toLong() * totalDurationMs) / 8000L
    return when {
        estimatedBytes >= 1_073_741_824L -> "%.1f GB".format(estimatedBytes / 1_073_741_824.0)
        estimatedBytes >= 1_048_576L -> "%.0f MB".format(estimatedBytes / 1_048_576.0)
        else -> "%.0f KB".format(estimatedBytes / 1024.0)
    }
}
