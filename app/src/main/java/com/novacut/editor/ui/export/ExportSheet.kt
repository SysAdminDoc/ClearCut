package com.novacut.editor.ui.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.AudioCodec
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ExportQuality
import com.novacut.editor.model.FrameCaptureFormat
import com.novacut.editor.model.PlatformPreset
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.SubtitleFormat
import com.novacut.editor.model.VideoCodec
import com.novacut.editor.ui.theme.Mocha

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(
    config: ExportConfig,
    exportState: ExportState,
    exportProgress: Float,
    modifier: Modifier = Modifier,
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
    onClose: () -> Unit
) {
    val availableCodecs = remember { ExportConfig.getAvailableCodecs() }
    val (width, height) = config.resolution.forAspect(aspectRatio)
    val estimatedSize = remember(config, totalDurationMs) { estimateExportSize(totalDurationMs, config) }
    val videoModeEnabled = !config.exportAudioOnly && !config.exportStemsOnly && !config.exportAsGif && !config.captureFrameOnly
    val audioCodecVisible = !config.captureFrameOnly && !config.exportAsGif

    val bitrateDescription = when {
        config.videoBitrate >= 40_000_000 -> stringResource(R.string.export_studio_quality)
        config.videoBitrate >= 15_000_000 -> stringResource(R.string.export_great_for_youtube)
        config.videoBitrate >= 6_000_000 -> stringResource(R.string.export_good_for_sharing)
        else -> stringResource(R.string.export_compact_file_size)
    }

    val summaryHeadline = when {
        config.captureFrameOnly -> stringResource(R.string.export_capture_summary_format, width, height)
        config.exportAsGif -> stringResource(R.string.export_gif_summary_format, config.gifMaxWidth)
        config.exportStemsOnly -> stringResource(R.string.export_stems_summary)
        config.exportAudioOnly -> stringResource(R.string.export_audio_summary)
        else -> stringResource(R.string.export_resolution_format, width, height, config.frameRate)
    }

    val summaryDetail = when {
        config.captureFrameOnly -> stringResource(R.string.export_capture_details_format, config.captureFormat.displayName)
        config.exportAsGif -> stringResource(R.string.export_gif_details_format, config.gifMaxWidth, config.gifFrameRate)
        config.exportStemsOnly -> stringResource(R.string.export_stems_details_format, config.audioCodec.label, config.audioBitrate / 1000)
        config.exportAudioOnly -> stringResource(R.string.export_audio_details_format, config.audioCodec.label, config.audioBitrate / 1000)
        else -> buildString {
            append(stringResource(R.string.export_bitrate_format, config.videoBitrate / 1_000_000, bitrateDescription))
            estimatedSize?.let {
                append("  •  ~")
                append(it)
            }
        }
    }

    val outputDetailsPrimary = when {
        config.captureFrameOnly -> stringResource(R.string.export_capture_details_format, config.captureFormat.displayName)
        config.exportAsGif -> stringResource(R.string.export_gif_details_format, config.gifMaxWidth, config.gifFrameRate)
        config.exportStemsOnly -> stringResource(R.string.export_stems_details_format, config.audioCodec.label, config.audioBitrate / 1000)
        config.exportAudioOnly -> stringResource(R.string.export_audio_details_format, config.audioCodec.label, config.audioBitrate / 1000)
        else -> stringResource(R.string.export_codec_quality_format, config.codec.label, config.quality.label)
    }

    val outputDetailsSecondary = when {
        config.captureFrameOnly -> stringResource(R.string.export_capture_summary_format, width, height)
        config.exportAsGif -> stringResource(R.string.export_gif_summary_format, config.gifMaxWidth)
        config.exportStemsOnly -> stringResource(R.string.export_audio_codec)
        config.exportAudioOnly -> stringResource(R.string.export_audio_codec)
        else -> stringResource(R.string.export_resolution_format, width, height, config.frameRate)
    }

    val primaryButtonLabel = when {
        config.exportAsGif -> stringResource(R.string.export_gif_button)
        config.captureFrameOnly -> stringResource(R.string.export_capture_button)
        config.exportStemsOnly -> stringResource(R.string.export_stems_button)
        config.exportAudioOnly -> stringResource(R.string.export_audio_button)
        else -> stringResource(R.string.export_video_button)
    }

    val primaryButtonIcon = when {
        config.exportAudioOnly -> Icons.Default.GraphicEq
        config.exportStemsOnly -> Icons.Default.Layers
        config.exportAsGif -> Icons.Default.GifBox
        config.captureFrameOnly -> Icons.Default.Image
        else -> Icons.Default.FileUpload
    }

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
                        Icon(
                            Icons.Default.Close,
                            stringResource(R.string.close),
                            tint = Mocha.Subtext0,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (exportState == ExportState.EXPORTING) {
            val percent = (exportProgress * 100).toInt().coerceIn(0, 100)
            val elapsedMs = if (exportStartTime > 0L) System.currentTimeMillis() - exportStartTime else 0L
            val elapsedSeconds = (elapsedMs / 1000).toInt()
            val elapsedLabel = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
            val etaLabel = if (exportProgress > 0.05f && elapsedMs > 2000L) {
                val totalEstimateMs = (elapsedMs / exportProgress).toLong()
                val remainingMs = (totalEstimateMs - elapsedMs).coerceAtLeast(0L)
                val remainingSeconds = (remainingMs / 1000).toInt()
                stringResource(R.string.export_eta_remaining, "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60))
            } else {
                null
            }

            ExportStateCard(
                icon = Icons.Default.FileUpload,
                tint = Mocha.Mauve,
                title = stringResource(R.string.export_exporting),
                body = stringResource(R.string.export_elapsed, elapsedLabel),
                progress = exportProgress,
                progressLabel = "$percent%",
                secondaryBody = etaLabel,
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
                            Mocha.PanelHighest.copy(alpha = 0.95f),
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
                        text = config.platformPreset?.displayName ?: stringResource(R.string.export_delivery_summary),
                        color = Mocha.Rosewater,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = summaryHeadline,
                        color = Mocha.Text,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when {
                            config.captureFrameOnly -> {
                                ExportPill(config.captureFormat.displayName, Mocha.Mauve)
                                ExportPill(aspectRatio.label, Mocha.Sapphire)
                            }
                            config.exportAsGif -> {
                                ExportPill("${config.gifFrameRate}fps", Mocha.Mauve)
                                ExportPill("${config.gifMaxWidth}px", Mocha.Sapphire)
                                ExportPill(aspectRatio.label, Mocha.Teal)
                            }
                            config.exportStemsOnly -> {
                                ExportPill(stringResource(R.string.export_stems), Mocha.Mauve)
                                ExportPill(config.audioCodec.label, Mocha.Sapphire)
                            }
                            config.exportAudioOnly -> {
                                ExportPill(stringResource(R.string.export_audio_only), Mocha.Mauve)
                                ExportPill(config.audioCodec.label, Mocha.Sapphire)
                            }
                            else -> {
                                ExportPill("${config.frameRate}fps", Mocha.Mauve)
                                ExportPill(config.codec.label, Mocha.Sapphire)
                                ExportPill(config.quality.label, Mocha.Teal)
                            }
                        }
                    }
                    Text(
                        text = summaryDetail,
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        ExportSectionCard(
            title = stringResource(R.string.export_quick_presets),
            description = stringResource(R.string.export_presets_description),
            accent = Mocha.Green
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlatformPreset.entries.forEach { preset ->
                    val isSelected = config.platformPreset == preset
                    FilterChip(
                        onClick = {
                            onConfigChanged(
                                config.copy(
                                    resolution = preset.resolution,
                                    frameRate = preset.frameRate,
                                    codec = preset.codec,
                                    platformPreset = preset
                                )
                            )
                        },
                        label = { Text(preset.displayName, style = MaterialTheme.typography.labelMedium) },
                        selected = isSelected,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Mocha.PanelRaised,
                            labelColor = Mocha.Subtext0,
                            selectedContainerColor = Mocha.Green.copy(alpha = 0.16f),
                            selectedLabelColor = Mocha.Green
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExportSectionCard(
            title = stringResource(R.string.export_special_outputs),
            description = stringResource(R.string.export_special_outputs_description),
            accent = Mocha.Mauve
        ) {
            ExportToggleRow(
                icon = Icons.Default.GraphicEq,
                title = stringResource(R.string.export_audio_only),
                description = stringResource(R.string.export_audio_only_description),
                checked = config.exportAudioOnly,
                onCheckedChange = {
                    onConfigChanged(
                        config.copy(
                            exportAudioOnly = it,
                            exportStemsOnly = false,
                            exportAsGif = false,
                            captureFrameOnly = false
                        )
                    )
                },
                accent = Mocha.Peach
            )

            HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.6f))

            ExportToggleRow(
                icon = Icons.Default.ClosedCaption,
                title = stringResource(R.string.export_subtitles),
                description = stringResource(R.string.export_subtitles_description),
                checked = config.subtitleFormat != null,
                onCheckedChange = {
                    onConfigChanged(config.copy(subtitleFormat = if (it) SubtitleFormat.SRT else null))
                },
                accent = Mocha.Blue
            )

            if (config.subtitleFormat != null) {
                ExportChoiceGroup(
                    title = stringResource(R.string.export_subtitles),
                    accent = Mocha.Blue
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SubtitleFormat.entries.forEach { format ->
                            FilterChip(
                                onClick = { onConfigChanged(config.copy(subtitleFormat = format)) },
                                label = { Text(format.displayName, style = MaterialTheme.typography.labelMedium) },
                                selected = config.subtitleFormat == format,
                                colors = exportChipColors(Mocha.Blue)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.6f))

            ExportToggleRow(
                icon = Icons.Default.Layers,
                title = stringResource(R.string.export_stems),
                description = stringResource(R.string.export_stems_description),
                checked = config.exportStemsOnly,
                onCheckedChange = {
                    onConfigChanged(
                        config.copy(
                            exportStemsOnly = it,
                            exportAudioOnly = false,
                            exportAsGif = false,
                            captureFrameOnly = false
                        )
                    )
                },
                accent = Mocha.Yellow
            )

            HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.6f))

            ExportToggleRow(
                icon = Icons.AutoMirrored.Filled.Notes,
                title = stringResource(R.string.export_chapter_markers),
                description = stringResource(R.string.export_chapter_markers_description),
                checked = config.includeChapterMarkers,
                onCheckedChange = { onConfigChanged(config.copy(includeChapterMarkers = it)) },
                accent = Mocha.Sapphire
            )

            HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.6f))

            ExportToggleRow(
                icon = Icons.Default.LayersClear,
                title = stringResource(R.string.export_transparent_bg),
                description = stringResource(R.string.export_transparent_bg_description),
                checked = config.transparentBackground,
                onCheckedChange = { onConfigChanged(config.copy(transparentBackground = it)) },
                accent = Mocha.Teal
            )

            HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.6f))

            ExportToggleRow(
                icon = Icons.Default.GifBox,
                title = stringResource(R.string.export_gif),
                description = stringResource(R.string.export_gif_description),
                checked = config.exportAsGif,
                onCheckedChange = {
                    onConfigChanged(
                        config.copy(
                            exportAsGif = it,
                            captureFrameOnly = false,
                            exportAudioOnly = false,
                            exportStemsOnly = false
                        )
                    )
                },
                accent = Mocha.Mauve
            )

            if (config.exportAsGif) {
                ExportChoiceGroup(
                    title = stringResource(R.string.export_gif_frame_rate),
                    accent = Mocha.Mauve
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 20).forEach { frameRate ->
                            FilterChip(
                                onClick = { onConfigChanged(config.copy(gifFrameRate = frameRate)) },
                                label = { Text("${frameRate}fps", style = MaterialTheme.typography.labelMedium) },
                                selected = config.gifFrameRate == frameRate,
                                colors = exportChipColors(Mocha.Mauve)
                            )
                        }
                    }
                }

                ExportChoiceGroup(
                    title = stringResource(R.string.export_gif_max_width),
                    accent = Mocha.Mauve
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(320, 480, 640).forEach { maxWidth ->
                            FilterChip(
                                onClick = { onConfigChanged(config.copy(gifMaxWidth = maxWidth)) },
                                label = { Text("${maxWidth}px", style = MaterialTheme.typography.labelMedium) },
                                selected = config.gifMaxWidth == maxWidth,
                                colors = exportChipColors(Mocha.Mauve)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.6f))

            ExportToggleRow(
                icon = Icons.Default.Image,
                title = stringResource(R.string.export_capture_frame),
                description = stringResource(R.string.export_capture_frame_description),
                checked = config.captureFrameOnly,
                onCheckedChange = {
                    onConfigChanged(
                        config.copy(
                            captureFrameOnly = it,
                            exportAsGif = false,
                            exportAudioOnly = false,
                            exportStemsOnly = false
                        )
                    )
                },
                accent = Mocha.Green
            )

            if (config.captureFrameOnly) {
                ExportChoiceGroup(
                    title = stringResource(R.string.export_capture_format),
                    accent = Mocha.Green
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FrameCaptureFormat.entries.forEach { format ->
                            FilterChip(
                                onClick = { onConfigChanged(config.copy(captureFormat = format)) },
                                label = { Text(format.displayName, style = MaterialTheme.typography.labelMedium) },
                                selected = config.captureFormat == format,
                                colors = exportChipColors(Mocha.Green)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExportSectionCard(
            title = stringResource(R.string.export_delivery_options),
            description = stringResource(R.string.export_delivery_options_description),
            accent = Mocha.Sapphire
        ) {
            if (videoModeEnabled) {
                ExportChoiceGroup(
                    title = stringResource(R.string.export_resolution),
                    accent = Mocha.Rosewater
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Resolution.entries.forEach { resolution ->
                            FilterChip(
                                onClick = { onConfigChanged(config.copy(resolution = resolution)) },
                                label = { Text(resolution.label, style = MaterialTheme.typography.labelMedium) },
                                selected = config.resolution == resolution,
                                colors = exportChipColors(Mocha.Rosewater)
                            )
                        }
                    }
                }

                ExportChoiceGroup(
                    title = stringResource(R.string.export_frame_rate),
                    accent = Mocha.Mauve
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(24, 30, 60).forEach { frameRate ->
                            FilterChip(
                                onClick = { onConfigChanged(config.copy(frameRate = frameRate)) },
                                label = { Text("${frameRate}fps", style = MaterialTheme.typography.labelMedium) },
                                selected = config.frameRate == frameRate,
                                colors = exportChipColors(Mocha.Mauve)
                            )
                        }
                    }
                }

                ExportChoiceGroup(
                    title = stringResource(R.string.export_codec),
                    accent = Mocha.Blue
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoCodec.entries.forEach { codec ->
                            val isAvailable = codec in availableCodecs
                            FilterChip(
                                onClick = { if (isAvailable) onConfigChanged(config.copy(codec = codec)) },
                                label = { Text(codec.label, style = MaterialTheme.typography.labelMedium) },
                                selected = config.codec == codec,
                                enabled = isAvailable,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Mocha.PanelRaised,
                                    labelColor = Mocha.Subtext0,
                                    selectedContainerColor = Mocha.Blue.copy(alpha = 0.16f),
                                    selectedLabelColor = Mocha.Blue,
                                    disabledContainerColor = Mocha.PanelRaised.copy(alpha = 0.45f),
                                    disabledLabelColor = Mocha.Subtext0.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }
                }

                ExportChoiceGroup(
                    title = stringResource(R.string.export_quality),
                    accent = Mocha.Teal
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExportQuality.entries.forEach { quality ->
                            FilterChip(
                                onClick = { onConfigChanged(config.copy(quality = quality)) },
                                label = { Text(quality.label, style = MaterialTheme.typography.labelMedium) },
                                selected = config.quality == quality,
                                colors = exportChipColors(Mocha.Teal)
                            )
                        }
                    }
                }
            }

            if (audioCodecVisible) {
                ExportChoiceGroup(
                    title = stringResource(R.string.export_audio_codec),
                    accent = Mocha.Peach
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioCodec.entries.forEach { audioCodec ->
                            FilterChip(
                                onClick = { onConfigChanged(config.copy(audioCodec = audioCodec)) },
                                label = { Text(audioCodec.label, style = MaterialTheme.typography.labelMedium) },
                                selected = config.audioCodec == audioCodec,
                                colors = exportChipColors(Mocha.Peach)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExportSectionCard(
            title = stringResource(R.string.export_output_details),
            description = summaryDetail,
            accent = Mocha.Rosewater
        ) {
            Text(
                text = outputDetailsPrimary,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = outputDetailsSecondary,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
            if (estimatedSize != null && videoModeEnabled) {
                Text(
                    text = "~$estimatedSize estimated",
                    color = Mocha.Peach,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExportSectionCard(
            title = stringResource(R.string.export_ready_to_export),
            description = stringResource(R.string.export_ready_to_export_description),
            accent = Mocha.Rosewater
        ) {
            Button(
                onClick = {
                    if (config.captureFrameOnly) {
                        onCaptureFrame()
                    } else {
                        onStartExport()
                        config.subtitleFormat?.let { onExportSubtitles(it) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Rosewater,
                    contentColor = Mocha.Midnight
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = primaryButtonIcon,
                    contentDescription = stringResource(R.string.export_video_cd),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = primaryButtonLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExportSectionCard(
            title = stringResource(R.string.export_timeline_exchange),
            description = stringResource(R.string.export_timeline_exchange_description),
            accent = Mocha.Sapphire
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
}

@Composable
private fun ExportSectionCard(
    title: String,
    description: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Mocha.PanelHighest),
        border = BorderStroke(1.dp, Mocha.CardStrokeStrong.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.12f),
                        Mocha.PanelHighest,
                        Mocha.PanelRaised.copy(alpha = 0.96f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        color = Mocha.Text,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = description,
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun ExportChoiceGroup(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = accent,
            style = MaterialTheme.typography.labelLarge
        )
        content()
    }
}

@Composable
private fun ExportToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = accent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedThumbColor = Mocha.Crust,
                uncheckedTrackColor = Mocha.Surface1,
                uncheckedThumbColor = Mocha.Subtext0
            )
        )
    }
}

@Composable
private fun ExportStateCard(
    icon: ImageVector,
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

@Composable
private fun exportChipColors(accent: Color) = FilterChipDefaults.filterChipColors(
    containerColor = Mocha.PanelRaised,
    labelColor = Mocha.Subtext0,
    selectedContainerColor = accent.copy(alpha = 0.16f),
    selectedLabelColor = accent
)

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
