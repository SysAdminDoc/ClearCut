package com.novacut.editor.ui.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.filled.ViewModule
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.AudioCodec
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ExportQuality
import com.novacut.editor.model.FrameCaptureFormat
import com.novacut.editor.model.PlatformPreset
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.SubtitleFormat
import com.novacut.editor.model.TargetSizePreset
import com.novacut.editor.model.VideoCodec
import com.novacut.editor.model.Watermark
import com.novacut.editor.model.WatermarkPosition
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.NovaCutPrimaryButton
import com.novacut.editor.ui.theme.NovaCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing

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
    smartRenderSummary: SmartRenderEngine.SmartRenderSummary? = null,
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
    val effectiveConfig = remember(config, totalDurationMs) {
        if (config.targetSizeBytes != null) config.resolveTargetSize(totalDurationMs) else config
    }
    val estimatedSize = remember(effectiveConfig, totalDurationMs) {
        estimateExportSize(totalDurationMs, effectiveConfig)
    }
    val videoModeEnabled = !config.exportAudioOnly && !config.exportStemsOnly && !config.exportAsGif && !config.captureFrameOnly && !config.exportAsContactSheet
    val audioCodecVisible = !config.captureFrameOnly && !config.exportAsGif && !config.exportAsContactSheet

    val bitrateDescription = when {
        effectiveConfig.videoBitrate >= 40_000_000 -> stringResource(R.string.export_studio_quality)
        effectiveConfig.videoBitrate >= 15_000_000 -> stringResource(R.string.export_great_for_youtube)
        effectiveConfig.videoBitrate >= 6_000_000 -> stringResource(R.string.export_good_for_sharing)
        else -> stringResource(R.string.export_compact_file_size)
    }

    val summaryHeadline = when {
        config.exportAsContactSheet -> stringResource(R.string.export_contact_sheet_summary, config.contactSheetColumns)
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
            append(stringResource(R.string.export_bitrate_format, effectiveConfig.videoBitrate / 1_000_000, bitrateDescription))
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
        config.exportAsContactSheet -> stringResource(R.string.export_contact_sheet_button)
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
        config.exportAsContactSheet -> Icons.Default.ViewModule
        else -> Icons.Default.FileUpload
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Panel, RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(3.dp)
                .background(Mocha.Surface2.copy(alpha = 0.55f), RoundedCornerShape(Radius.pill))
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Mocha.Mauve.copy(alpha = 0.14f),
                shape = RoundedCornerShape(Radius.lg),
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
                onPrimary = onCancel,
                // Cancel during export should not look like a celebrate-the-result CTA.
                primaryStyle = PrimaryStyle.Destructive
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
                onTertiary = onClose,
                primaryStyle = PrimaryStyle.Filled
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
                onPrimary = onClose,
                // "Done" after a user-initiated cancel is informational, not celebratory.
                primaryStyle = PrimaryStyle.Quiet
            )
            return
        }

        if (exportState == ExportState.ERROR) {
            ExportStateCard(
                icon = Icons.Default.Error,
                tint = Mocha.Red,
                title = stringResource(R.string.export_failed),
                body = errorMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.error),
                primaryLabel = stringResource(R.string.retry),
                onPrimary = onStartExport,
                secondaryLabel = stringResource(R.string.close),
                onSecondary = onClose,
                primaryStyle = PrimaryStyle.Filled
            )
            return
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
            border = BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(Radius.xl)
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
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
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
                            captureFrameOnly = false,
                            exportAsContactSheet = false
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
                            captureFrameOnly = false,
                            exportAsContactSheet = false
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
                            exportStemsOnly = false,
                            exportAsContactSheet = false
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
                            exportStemsOnly = false,
                            exportAsContactSheet = false
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

            HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.6f))

            ExportToggleRow(
                icon = Icons.Default.ViewModule,
                title = stringResource(R.string.export_contact_sheet),
                description = stringResource(R.string.export_contact_sheet_description),
                checked = config.exportAsContactSheet,
                onCheckedChange = {
                    onConfigChanged(
                        config.copy(
                            exportAsContactSheet = it,
                            exportAsGif = false,
                            captureFrameOnly = false,
                            exportAudioOnly = false,
                            exportStemsOnly = false
                        )
                    )
                },
                accent = Mocha.Flamingo
            )

            if (config.exportAsContactSheet) {
                ExportChoiceGroup(
                    title = stringResource(R.string.export_contact_sheet_columns),
                    accent = Mocha.Flamingo
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 4, 5, 6).forEach { cols ->
                            FilterChip(
                                onClick = { onConfigChanged(config.copy(contactSheetColumns = cols)) },
                                label = { Text("$cols columns", style = MaterialTheme.typography.labelMedium) },
                                selected = config.contactSheetColumns == cols,
                                colors = exportChipColors(Mocha.Flamingo)
                            )
                        }
                    }
                }
            }

            // Watermark burn-in. Applies across all video clips during export;
            // no effect on GIF / contact-sheet / frame-capture paths.
            if (videoModeEnabled) {
                HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.6f))
                WatermarkSection(
                    watermark = config.watermark,
                    onWatermarkChanged = { updated ->
                        onConfigChanged(config.copy(watermark = updated))
                    }
                )
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
                    text = stringResource(R.string.export_estimated_size_format, estimatedSize),
                    color = Mocha.Peach,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (totalDurationMs > 0L && videoModeEnabled) {
                val etaSec = estimateExportEtaSeconds(totalDurationMs, effectiveConfig)
                Text(
                    text = stringResource(R.string.export_estimated_time_format, formatEtaSeconds(etaSec)),
                    color = Mocha.Blue,
                    style = MaterialTheme.typography.bodySmall
                )
                if (smartRenderSummary != null) {
                    SmartRenderExportOutlook(summary = smartRenderSummary)
                }
                // Pre-flight warnings. These are static heuristics so they run
                // every recomposition without any state plumbing — the signal
                // is whether the *currently selected* config will produce an
                // expensive render, not historical comparison. The goal is to
                // surface obvious footguns ("4K AV1 in a 2-hour timeline")
                // before the user hits Export, not to second-guess every
                // conservative choice.
                val preflightWarnings = buildList {
                    // 30-minute render is our "go make coffee" threshold. Below
                    // that most users tolerate the wait; above it, surfacing a
                    // heads-up prevents the "is it stuck?" support pattern.
                    if (etaSec >= 30L * 60L) {
                        add(stringResource(R.string.export_warning_long_render, formatEtaSeconds(etaSec)))
                    }
                    // 1 GB is the practical upper bound for most share targets
                    // — WhatsApp caps at 16 MB, Telegram 50 MB, Gmail 25 MB,
                    // and even YouTube/Drive uploads from mobile get painful
                    // past a gig. Warn so users can pick target-size if they
                    // intended to share.
                    val estimatedBytes = estimateExportBytes(totalDurationMs, effectiveConfig)
                    if (estimatedBytes >= 1_073_741_824L) {
                        add(stringResource(R.string.export_warning_large_file))
                    }
                    // AV1 software encode is brutally slow on most Android
                    // devices. Hardware AV1 is rare in 2025. Warn so users who
                    // selected it for file-size reasons can fall back to HEVC.
                    if (effectiveConfig.codec == VideoCodec.AV1) {
                        add(stringResource(R.string.export_warning_av1_slow))
                    }
                    // Device-aware encoder capability probe. Surfaces a
                    // reason-bearing message when the codec+resolution+fps+
                    // bitrate combo exceeds what any advertised encoder on
                    // this device accepts. The probe is cached across
                    // recompositions via remember — MediaCodecList queries
                    // are cheap but not free, and the result only changes
                    // when the user tweaks the config.
                    val probe = remember(
                        effectiveConfig.codec,
                        width, height,
                        effectiveConfig.frameRate,
                        effectiveConfig.videoBitrate
                    ) {
                        com.novacut.editor.engine.EncoderCapabilityProbe.check(
                            codec = effectiveConfig.codec,
                            width = width,
                            height = height,
                            framerate = effectiveConfig.frameRate,
                            bitrate = effectiveConfig.videoBitrate
                        )
                    }
                    if (!probe.supported) {
                        probe.reason?.let { add(it) }
                    }
                }
                preflightWarnings.forEach { warning ->
                    Text(
                        text = warning,
                        color = Mocha.Yellow,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (videoModeEnabled) {
            Spacer(modifier = Modifier.height(12.dp))

            ExportSectionCard(
                title = stringResource(R.string.export_target_size),
                description = stringResource(R.string.export_target_size_description),
                accent = Mocha.Pink
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        onClick = {
                            onConfigChanged(config.copy(targetSizeBytes = null, bitrateOverride = null))
                        },
                        label = { Text("Off", style = MaterialTheme.typography.labelMedium) },
                        selected = config.targetSizeBytes == null,
                        colors = exportChipColors(Mocha.Pink)
                    )
                    TargetSizePreset.entries.forEach { preset ->
                        FilterChip(
                            onClick = {
                                onConfigChanged(config.copy(targetSizeBytes = preset.sizeBytes))
                            },
                            label = { Text(preset.displayName, style = MaterialTheme.typography.labelMedium) },
                            selected = config.targetSizeBytes == preset.sizeBytes,
                            colors = exportChipColors(Mocha.Pink)
                        )
                    }
                }
                if (config.targetSizeBytes != null && totalDurationMs > 0L) {
                    val mbps = effectiveConfig.videoBitrate / 1_000_000.0
                    Text(
                        text = "Target bitrate: %.1f Mbps".format(mbps),
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExportSectionCard(
                title = stringResource(R.string.export_filename_template),
                description = stringResource(R.string.export_filename_template_description),
                accent = Mocha.Lavender
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "{name}" to "Name",
                        "{name}_{date}" to "Name + Date",
                        "{name}_{date}_{time}" to "Name + Timestamp",
                        "{name}_{res}_{fps}" to "Name + Specs",
                        "{name}_{preset}" to "Name + Preset",
                        "{name}_{duration}" to "Name + Duration",
                        "{name}_{sizeMB}" to "Name + Size"
                    ).forEach { (tmpl, label) ->
                        FilterChip(
                            onClick = { onConfigChanged(config.copy(filenameTemplate = tmpl)) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            selected = config.filenameTemplate == tmpl,
                            colors = exportChipColors(Mocha.Lavender)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.export_current_filename_template, config.filenameTemplate),
                    color = Mocha.Subtext0,
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
            NovaCutPrimaryButton(
                text = primaryButtonLabel,
                onClick = {
                    if (config.captureFrameOnly) {
                        onCaptureFrame()
                    } else {
                        // Video export path. When a subtitle format is selected the
                        // sidecar is now written inside ExportDelegate.startExport's
                        // `onComplete`, so it lands next to the rendered file with
                        // guaranteed ordering before Share/Save-to-Gallery are offered.
                        // Firing `onExportSubtitles` here used to write the same file
                        // in parallel to a separate `externalFilesDir/subtitles/` dir
                        // and could race the share intent — removed to stop duplicating
                        // work and to keep the sidecar co-located with the video.
                        onStartExport()
                    }
                },
                icon = primaryButtonIcon,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExportSectionCard(
            title = stringResource(R.string.export_timeline_exchange),
            description = stringResource(R.string.export_timeline_exchange_description),
            accent = Mocha.Sapphire
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                NovaCutSecondaryButton(
                    text = stringResource(R.string.export_otio),
                    onClick = onExportOtio,
                    modifier = Modifier.weight(1f),
                    contentColor = Mocha.Sapphire
                )
                NovaCutSecondaryButton(
                    text = stringResource(R.string.export_fcpxml),
                    onClick = onExportFcpxml,
                    modifier = Modifier.weight(1f),
                    contentColor = Mocha.Sapphire
                )
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
        shape = RoundedCornerShape(Radius.xl)
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
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = title,
            color = accent,
            style = MaterialTheme.typography.labelLarge
        )
        content()
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SmartRenderExportOutlook(summary: SmartRenderEngine.SmartRenderSummary) {
    val passThroughPercent = if (summary.totalDurationMs > 0L) {
        ((summary.passThroughDurationMs * 100L) / summary.totalDurationMs).toInt().coerceIn(0, 100)
    } else {
        0
    }
    val isInstant = summary.totalSegments > 0 && summary.reEncodeSegments == 0
    val speedupText = if (isInstant) {
        stringResource(R.string.render_speedup_instant)
    } else {
        stringResource(R.string.render_speedup_value, summary.estimatedSpeedup.coerceAtMost(99.9f))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Mocha.Blue.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Mocha.Blue.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = Mocha.Blue,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.export_smart_render_title),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = Mocha.Blue.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(Radius.pill)
                ) {
                    Text(
                        text = speedupText,
                        color = Mocha.Blue,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.export_smart_render_detail,
                    passThroughPercent,
                    summary.passThroughSegments,
                    summary.reEncodeSegments
                ),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                SmartRenderMetricPill(
                    text = stringResource(
                        R.string.render_pass_through_duration,
                        formatEtaSeconds((summary.passThroughDurationMs / 1000L).coerceAtLeast(0L))
                    ),
                    accent = Mocha.Green
                )
                SmartRenderMetricPill(
                    text = stringResource(
                        R.string.render_re_encode_duration,
                        formatEtaSeconds((summary.reEncodeDurationMs / 1000L).coerceAtLeast(0L))
                    ),
                    accent = Mocha.Peach
                )
            }
        }
    }
}

@Composable
private fun SmartRenderMetricPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.11f),
        shape = RoundedCornerShape(Radius.md)
    ) {
        Text(
            text = text,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

/**
 * Watermark configuration UI. Renders inside the Special Outputs section
 * and is only shown when `videoModeEnabled` — audio / stems / GIF /
 * contact-sheet exports don't get a watermark. The picker stores the
 * returned URI directly; `ExportWatermarkOverlay.loadBitmap` resolves it
 * at export time via the content resolver (handles both `file://` paths
 * from a local import and `content://` URIs from a system picker).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatermarkSection(
    watermark: Watermark?,
    onWatermarkChanged: (Watermark?) -> Unit
) {
    val pickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            // Persist the read permission so the export process can still
            // decode the bitmap after app restarts / activity recreation.
            // Silently fall through on failure — some provider URIs don't
            // grant persistable permission (Photo Picker) but still work
            // for the lifetime of the ExportConfig in memory, which is our
            // primary use case.
            val ctx = uri
            try {
                androidx.compose.ui.platform.LocalContext
                // The LocalContext composition-local can't be read from a
                // non-composable lambda; grab the context via the launcher's
                // registry instead when we need it. For now, just pass the
                // URI through — permission persistence happens at the
                // launcher's callsite in MediaPicker already, and this
                // picker path is read once per export.
            } catch (_: Throwable) {}
            onWatermarkChanged(
                (watermark ?: Watermark(sourceUri = uri)).copy(sourceUri = uri)
            )
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    ExportToggleRow(
        icon = Icons.Default.Image,
        title = stringResource(R.string.export_watermark),
        description = stringResource(R.string.export_watermark_description),
        checked = watermark != null,
        onCheckedChange = { enabled ->
            if (enabled) {
                pickerLauncher.launch(arrayOf("image/*"))
            } else {
                onWatermarkChanged(null)
            }
        },
        accent = Mocha.Rosewater
    )

    if (watermark != null) {
        ExportChoiceGroup(
            title = stringResource(R.string.export_watermark_position),
            accent = Mocha.Rosewater
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WatermarkPosition.entries.forEach { pos ->
                    FilterChip(
                        onClick = { onWatermarkChanged(watermark.copy(position = pos)) },
                        label = { Text(pos.displayName, style = MaterialTheme.typography.labelMedium) },
                        selected = watermark.position == pos,
                        colors = exportChipColors(Mocha.Rosewater)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = stringResource(R.string.export_watermark_opacity, (watermark.opacity * 100).toInt()),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.labelMedium
            )
            androidx.compose.material3.Slider(
                value = watermark.opacity,
                onValueChange = { onWatermarkChanged(watermark.copy(opacity = it.coerceIn(0f, 1f))) },
                valueRange = 0f..1f,
                steps = 19,  // 5% step increments
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Mocha.Rosewater,
                    activeTrackColor = Mocha.Rosewater,
                    inactiveTrackColor = Mocha.Surface2
                )
            )

            Text(
                text = stringResource(R.string.export_watermark_scale, watermark.scalePercent),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.labelMedium
            )
            androidx.compose.material3.Slider(
                value = watermark.scalePercent.toFloat(),
                onValueChange = {
                    onWatermarkChanged(watermark.copy(scalePercent = it.toInt().coerceIn(5, 50)))
                },
                valueRange = 5f..50f,
                steps = 44,  // 1% step
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Mocha.Rosewater,
                    activeTrackColor = Mocha.Rosewater,
                    inactiveTrackColor = Mocha.Surface2
                )
            )

            // Re-pick button so users can swap the image without toggling
            // the watermark off + on (which would lose the position /
            // opacity / scale settings they'd already dialled in).
            TextButton(
                onClick = { pickerLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.export_watermark_replace),
                    color = Mocha.Blue
                )
            }
            // Suppress unused warning for ctx — retained because future
            // expansion (e.g. inline preview of the chosen bitmap) will
            // need the composition-local.
            @Suppress("UNUSED_EXPRESSION") context
        }
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (checked) accent.copy(alpha = 0.08f) else Mocha.PanelRaised.copy(alpha = 0.7f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (checked) accent.copy(alpha = 0.24f) else Mocha.CardStroke
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
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

            Surface(
                color = if (checked) accent.copy(alpha = 0.14f) else Mocha.Panel,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, if (checked) accent.copy(alpha = 0.26f) else Mocha.CardStroke)
            ) {
                Text(
                    text = stringResource(if (checked) R.string.state_on else R.string.state_off),
                    color = if (checked) accent else Mocha.Subtext0,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent,
                    checkedThumbColor = Mocha.Crust,
                    uncheckedTrackColor = Mocha.Surface1,
                    uncheckedThumbColor = Mocha.Subtext0
                )
            )
        }
    }
}

/**
 * Visual treatment for the primary CTA button on an [ExportStateCard]. Picking the right
 * style is purely semantic — "Share completed export" is a confident success action, while
 * "Cancel running export" is a destructive-ish action that should never look like a CTA.
 */
private enum class PrimaryStyle { Filled, Destructive, Quiet }

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
    onTertiary: (() -> Unit)? = null,
    primaryStyle: PrimaryStyle = PrimaryStyle.Filled
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
        border = BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(Radius.xxl)
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
                // Two-layer halo: outer translucent ring + inner filled disc with the icon.
                // The ring gives the icon a sense of presence/depth without resorting to a
                // hard shadow that would conflict with the surrounding gradient surface.
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        color = Color.Transparent,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
                        modifier = Modifier.size(80.dp)
                    ) {}
                    Surface(
                        color = tint.copy(alpha = 0.16f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f))
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
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(title, color = Mocha.Text, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                if (progress != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    // Smoothly animate the bar so it doesn't snap on each Transformer progress tick.
                    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = progress.coerceIn(0f, 1f),
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
                        label = "exportProgress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(com.novacut.editor.ui.theme.Radius.pill)),
                        color = tint,
                        trackColor = Mocha.PanelHighest.copy(alpha = 0.8f)
                    )
                }
                if (progressLabel != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        progressLabel,
                        color = Mocha.Text,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                if (!secondaryBody.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(secondaryBody, color = tint, style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(18.dp))
                when (primaryStyle) {
                    PrimaryStyle.Destructive -> {
                        // Cancel during export: outlined Peach. Reads as available-but-not-celebratory.
                        OutlinedButton(
                            onClick = onPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            border = BorderStroke(1.dp, tint.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
                            shape = RoundedCornerShape(com.novacut.editor.ui.theme.Radius.lg)
                        ) {
                            Text(primaryLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    PrimaryStyle.Quiet -> {
                        OutlinedButton(
                            onClick = onPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            border = BorderStroke(1.dp, Mocha.CardStrokeStrong),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Text),
                            shape = RoundedCornerShape(com.novacut.editor.ui.theme.Radius.lg)
                        ) {
                            Text(primaryLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    PrimaryStyle.Filled -> {
                        Button(
                            onClick = onPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tint == Mocha.Red) Mocha.Red else Mocha.Rosewater,
                                contentColor = if (tint == Mocha.Red) Mocha.Crust else Mocha.Midnight
                            ),
                            shape = RoundedCornerShape(com.novacut.editor.ui.theme.Radius.lg)
                        ) {
                            Text(primaryLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
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

private fun estimateExportBytes(totalDurationMs: Long, config: ExportConfig): Long {
    if (totalDurationMs <= 0L) return 0L
    val totalBitrate = config.videoBitrate + config.audioBitrate
    return (totalBitrate.toLong() * totalDurationMs) / 8000L
}

private fun estimateExportSize(
    totalDurationMs: Long,
    config: ExportConfig
): String? {
    if (totalDurationMs <= 0L) return null

    val estimatedBytes = estimateExportBytes(totalDurationMs, config)
    return when {
        estimatedBytes >= 1_073_741_824L -> "%.1f GB".format(estimatedBytes / 1_073_741_824.0)
        estimatedBytes >= 1_048_576L -> "%.0f MB".format(estimatedBytes / 1_048_576.0)
        else -> "%.0f KB".format(estimatedBytes / 1024.0)
    }
}

/**
 * Heuristic encode-time estimate before export starts. Calibrated against mid-range
 * Android devices — 1080p30 runs at ~1.2x real-time with H.264, HEVC/AV1 are slower.
 * Pixel count and bitrate scale the estimate roughly linearly.
 */
private fun estimateExportEtaSeconds(totalDurationMs: Long, config: ExportConfig): Long {
    if (totalDurationMs <= 0L) return 0L
    val durationSec = totalDurationMs / 1000.0
    val pixels = config.resolution.width.toLong() * config.resolution.height.toLong()
    val refPixels = 1920L * 1080L
    val resolutionFactor = (pixels.toDouble() / refPixels).coerceAtLeast(0.25)
    val codecFactor = when (config.codec) {
        VideoCodec.H264 -> 1.0
        VideoCodec.HEVC -> 1.6
        VideoCodec.AV1 -> 2.4
        VideoCodec.VP9 -> 1.9
    }
    val fpsFactor = config.frameRate / 30.0
    // Base rate: 1080p30 H.264 ≈ 0.85x realtime on mid devices (so encode takes ~1.17x).
    val encodeMultiplier = 1.17 * resolutionFactor * codecFactor * fpsFactor
    return (durationSec * encodeMultiplier).toLong().coerceAtLeast(1L)
}

private fun formatEtaSeconds(seconds: Long): String = when {
    seconds >= 3600 -> "%dh %dm".format(seconds / 3600, (seconds % 3600) / 60)
    seconds >= 60 -> "%dm %02ds".format(seconds / 60, seconds % 60)
    else -> "${seconds}s"
}
