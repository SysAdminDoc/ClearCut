package com.novacut.editor.ui.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.AiUsageLedger
import com.novacut.editor.engine.EncoderCapabilityProbe
import com.novacut.editor.engine.ExportColorConfidenceEngine
import com.novacut.editor.engine.ExportHistoryEntry
import com.novacut.editor.engine.ExportHistoryStatus
import com.novacut.editor.engine.ExportState
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.novacut.editor.engine.ProjectColorPolicy
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
import com.novacut.editor.ui.ClearCutTestTags
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.Motion
import com.novacut.editor.ui.theme.ClearCutChromeIconButton
import com.novacut.editor.ui.theme.ClearCutDialogIcon
import com.novacut.editor.ui.theme.ClearCutPrimaryButton
import com.novacut.editor.ui.theme.ClearCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

enum class ExportSheetPresentation {
    BOTTOM_SHEET,
    EMBEDDED_PANE
}

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
    sourceHdrSummary: ExportColorConfidenceEngine.SourceHdrSummary = ExportColorConfidenceEngine.SourceHdrSummary(),
    projectColorPolicy: ProjectColorPolicy = ProjectColorPolicy.DEFAULT,
    aiUsageLedger: List<AiUsageLedger.Entry> = emptyList(),
    exportHistory: List<ExportHistoryEntry> = emptyList(),
    encoderName: String? = null,
    stallWarning: Boolean = false,
    lastExportedFilePath: String? = null,
    suggestedResolution: Resolution? = null,
    suggestedFps: Int? = null,
    presentation: ExportSheetPresentation = ExportSheetPresentation.BOTTOM_SHEET,
    onConfigChanged: (ExportConfig) -> Unit,
    onStartExport: () -> Unit,
    onShare: () -> Unit = {},
    onSaveToGallery: () -> Unit = {},
    onCancel: () -> Unit = {},
    onExportOtio: () -> Unit = {},
    onExportFcpxml: () -> Unit = {},
    onExportSubtitles: (SubtitleFormat) -> Unit = {},
    onCaptureFrame: () -> Unit = {},
    onClearAiUsageLedger: () -> Unit = {},
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
    val aiUsageEntries = remember(aiUsageLedger) {
        AiUsageLedger.mergeOverlaps(aiUsageLedger)
    }
    val hasAiUsage = aiUsageEntries.isNotEmpty()
    val hasDisclosureBearingAiUsage = remember(aiUsageEntries) {
        AiUsageLedger.aggregateSeverity(aiUsageEntries) != AiUsageLedger.Severity.INTERNAL_ONLY
    }
    val aiDisclosureSummary = remember(aiUsageEntries) {
        AiUsageLedger.summaryLine(aiUsageEntries)
    }
    var showClearAiLedgerConfirm by remember { mutableStateOf(false) }
    val codecCanCarryHdr = config.codec != VideoCodec.H264
    val hdrProfileSupport = remember(effectiveConfig.codec) {
        EncoderCapabilityProbe.queryHdrProfiles(effectiveConfig.codec)
    }
    val deviceTierHint = remember {
        EncoderCapabilityProbe.deviceTierHint()
    }
    val hdrEncodeSupport = remember(hdrProfileSupport) {
        ExportColorConfidenceEngine.HdrEncodeSupport(
            supportedFormats = hdrProfileSupport.supportedFormats.map { it.displayName }.toSet(),
            maxWidth = hdrProfileSupport.maxWidth,
            maxHeight = hdrProfileSupport.maxHeight,
            maxBitrate = hdrProfileSupport.maxBitrate
        )
    }
    val colorConfidenceReport = remember(
        effectiveConfig,
        width,
        height,
        hdrEncodeSupport,
        sourceHdrSummary,
        projectColorPolicy
    ) {
        ExportColorConfidenceEngine.analyze(
            config = effectiveConfig,
            width = width,
            height = height,
            hdrSupport = hdrEncodeSupport,
            sourceSummary = sourceHdrSummary,
            projectColorPolicy = projectColorPolicy
        )
    }

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
    val containerShape = when (presentation) {
        ExportSheetPresentation.BOTTOM_SHEET -> RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl)
        ExportSheetPresentation.EMBEDDED_PANE -> RoundedCornerShape(topStart = Radius.xxl, bottomStart = Radius.xxl)
    }

    if (showClearAiLedgerConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAiLedgerConfirm = false },
            icon = {
                ClearCutDialogIcon(
                    icon = Icons.Default.AutoAwesome,
                    accent = Mocha.Mauve
                )
            },
            title = { Text(stringResource(R.string.export_ai_ledger_clear_title)) },
            text = {
                Text(stringResource(R.string.export_ai_ledger_clear_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAiUsageLedger()
                        showClearAiLedgerConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.export_ai_ledger_clear_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAiLedgerConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ClearCutTestTags.EXPORT_SHEET)
            .background(Mocha.Panel, containerShape)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = 14.dp)
    ) {
        if (presentation == ExportSheetPresentation.BOTTOM_SHEET) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(3.dp)
                    .background(Mocha.Surface2.copy(alpha = 0.55f), RoundedCornerShape(Radius.sm))
            )

            Spacer(modifier = Modifier.height(14.dp))
        }

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
                ClearCutChromeIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = onClose,
                    modifier = Modifier.testTag(ClearCutTestTags.EXPORT_CLOSE),
                    size = 40.dp
                )
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

            val encoderLine = encoderName?.let { stringResource(R.string.export_encoder_format, it) }
            val stallLine = if (stallWarning) stringResource(R.string.export_stall_warning) else null
            val bodyParts = listOfNotNull(
                stringResource(R.string.export_elapsed, elapsedLabel),
                encoderLine,
                stallLine
            ).joinToString("\n")

            ExportStateCard(
                icon = if (stallWarning) Icons.Default.Warning else Icons.Default.FileUpload,
                tint = if (stallWarning) Mocha.Yellow else Mocha.Mauve,
                title = stringResource(R.string.export_exporting),
                body = bodyParts,
                progress = exportProgress,
                progressLabel = "$percent%",
                secondaryBody = etaLabel,
                primaryLabel = stringResource(R.string.export_cancel),
                onPrimary = onCancel,
                primaryStyle = PrimaryStyle.Destructive
            )
            return
        }

        if (exportState == ExportState.COMPLETE) {
            if (lastExportedFilePath != null) {
                ExportPreviewPlayer(filePath = lastExportedFilePath)
                Spacer(Modifier.height(Spacing.md))
            }
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
            val latestFailureDiagnostic = exportHistory.firstOrNull {
                it.status == ExportHistoryStatus.FAILED || it.status == ExportHistoryStatus.BLOCKED
            }?.diagnosticSummary
            ExportStateCard(
                icon = Icons.Default.Error,
                tint = Mocha.Red,
                title = stringResource(R.string.export_failed),
                body = errorMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.export_error_unknown),
                secondaryBody = latestFailureDiagnostic,
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
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = Spacing.sm),
            ) {
                items(PlatformPreset.entries, key = { it.name }) { preset ->
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

            if (videoModeEnabled) {
                HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.6f))

                ExportToggleRow(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.export_ai_use_disclose_title),
                    description = if (hasAiUsage) {
                        aiDisclosureSummary
                    } else {
                        stringResource(R.string.export_ai_use_empty)
                    },
                    checked = config.discloseAiUse && hasAiUsage,
                    enabled = hasAiUsage,
                    onCheckedChange = { checked ->
                        onConfigChanged(
                            config.copy(
                                discloseAiUse = checked,
                                writeAiUseSidecar = if (checked) true else config.writeAiUseSidecar
                            )
                        )
                    },
                    accent = if (hasDisclosureBearingAiUsage) Mocha.Mauve else Mocha.Teal
                )

                if (config.discloseAiUse && hasAiUsage) {
                    ExportToggleRow(
                        icon = Icons.AutoMirrored.Filled.Notes,
                        title = stringResource(R.string.export_ai_use_sidecar_title),
                        description = stringResource(R.string.export_ai_use_sidecar_description),
                        checked = config.writeAiUseSidecar,
                        onCheckedChange = {
                            onConfigChanged(config.copy(writeAiUseSidecar = it))
                        },
                        accent = Mocha.Blue
                    )
                }

                if (hasAiUsage) {
                    TextButton(
                        onClick = { showClearAiLedgerConfirm = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = stringResource(R.string.export_ai_ledger_clear_button),
                            color = Mocha.Red
                        )
                    }
                }
            }

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
            if (videoModeEnabled && suggestedResolution != null &&
                (suggestedResolution != config.resolution || (suggestedFps != null && suggestedFps != config.frameRate))
            ) {
                val label = buildString {
                    append("Suggested: ${suggestedResolution.label}")
                    if (suggestedFps != null) append(" ${suggestedFps}fps")
                }
                val upscaleWarning = config.resolution.height > suggestedResolution.height
                Surface(
                    shape = RoundedCornerShape(Radius.md),
                    color = if (upscaleWarning) Mocha.Yellow.copy(alpha = 0.12f) else Mocha.Green.copy(alpha = 0.12f),
                    border = BorderStroke(
                        1.dp,
                        if (upscaleWarning) Mocha.Yellow.copy(alpha = 0.3f) else Mocha.Green.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            var updated = config.copy(resolution = suggestedResolution)
                            if (suggestedFps != null) updated = updated.copy(frameRate = suggestedFps)
                            onConfigChanged(updated)
                        }
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (upscaleWarning) Icons.Default.Warning else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (upscaleWarning) Mocha.Yellow else Mocha.Green,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (upscaleWarning) Mocha.Yellow else Mocha.Green
                            )
                            if (upscaleWarning) {
                                Text(
                                    "Export exceeds source resolution",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Mocha.Subtext0
                                )
                            }
                        }
                        Text(
                            "Apply",
                            style = MaterialTheme.typography.labelMedium,
                            color = Mocha.Mauve
                        )
                    }
                }
            }

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

                ExportToggleRow(
                    icon = Icons.Default.GraphicEq,
                    title = stringResource(R.string.export_hdr_preserve),
                    description = stringResource(
                        if (codecCanCarryHdr) R.string.export_hdr_preserve_description
                        else R.string.export_hdr_preserve_disabled
                    ),
                    checked = config.hdr10PlusMetadata && codecCanCarryHdr,
                    enabled = codecCanCarryHdr,
                    onCheckedChange = { enabled ->
                        onConfigChanged(config.copy(hdr10PlusMetadata = enabled && codecCanCarryHdr))
                    },
                    accent = Mocha.Yellow
                )

                HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.6f))

                ExportToggleRow(
                    icon = Icons.Default.Speed,
                    title = stringResource(R.string.export_fast_trim),
                    description = stringResource(R.string.export_fast_trim_description),
                    checked = config.allowStreamCopy,
                    onCheckedChange = { onConfigChanged(config.copy(allowStreamCopy = it)) },
                    accent = Mocha.Green
                )

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
            if (videoModeEnabled) {
                ColorConfidenceOutlook(report = colorConfidenceReport)
                DeviceTierOutlook(hint = deviceTierHint)
                // Highest-Value #2 — pre-export AI provenance preview. Renders
                // the AiUsageLedger summary as severity-coloured chips
                // alongside the existing color/HDR confidence row. Empty
                // ledger renders a single "No AI assistance recorded" line.
                AiUseConfidenceRow(
                    chips = remember(aiUsageEntries) {
                        AiUsageLedger.summarizeForChips(aiUsageEntries)
                    },
                )
            }
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
                    // AV1 is efficient when hardware-backed, but expensive
                    // when the device only exposes software encode. The tier
                    // probe lets premium devices keep the UI calm.
                    if (effectiveConfig.codec == VideoCodec.AV1 && !deviceTierHint.hasHardwareAv1) {
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
                        EncoderCapabilityProbe.check(
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

        if (exportHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            ExportHistorySection(exportHistory.take(3))
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
            ClearCutPrimaryButton(
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
                ClearCutSecondaryButton(
                    text = stringResource(R.string.export_otio),
                    onClick = onExportOtio,
                    modifier = Modifier.weight(1f),
                    contentColor = Mocha.Sapphire
                )
                ClearCutSecondaryButton(
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
    val colors = LocalClearCutColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.panelHighest),
        border = BorderStroke(
            1.dp,
            if (colors.highContrast) colors.cardStrokeStrong else colors.cardStrokeStrong.copy(alpha = 0.92f)
        ),
        shape = RoundedCornerShape(Radius.xl)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.12f),
                        colors.panelHighest,
                        colors.panelRaised.copy(alpha = 0.96f)
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
                        color = colors.text,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = description,
                        color = colors.subtext,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun ExportHistorySection(entries: List<ExportHistoryEntry>) {
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }
    ExportSectionCard(
        title = stringResource(R.string.export_history_title),
        description = stringResource(R.string.export_history_description),
        accent = Mocha.Teal
    ) {
        entries.forEachIndexed { index, entry ->
            ExportHistoryRow(entry = entry, dateFormat = dateFormat)
            if (index < entries.lastIndex) {
                HorizontalDivider(color = Mocha.CardStroke.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ExportHistoryRow(
    entry: ExportHistoryEntry,
    dateFormat: DateFormat
) {
    val statusColor = when (entry.status) {
        ExportHistoryStatus.COMPLETE -> Mocha.Green
        ExportHistoryStatus.FAILED -> Mocha.Red
        ExportHistoryStatus.CANCELLED -> Mocha.Peach
        ExportHistoryStatus.BLOCKED -> Mocha.Yellow
    }
    val statusLabel = when (entry.status) {
        ExportHistoryStatus.COMPLETE -> stringResource(R.string.export_history_status_complete)
        ExportHistoryStatus.FAILED -> stringResource(R.string.export_history_status_failed)
        ExportHistoryStatus.CANCELLED -> stringResource(R.string.export_history_status_cancelled)
        ExportHistoryStatus.BLOCKED -> stringResource(R.string.export_history_status_blocked)
    }
    val detail = stringResource(
        R.string.export_history_detail_format,
        dateFormat.format(Date(entry.finishedAtEpochMs)),
        entry.outputBytes?.let(::formatHistoryBytes) ?: entry.resolutionLabel,
        formatEtaSeconds((entry.elapsedMs / 1000L).coerceAtLeast(0L))
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = statusColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(Radius.md),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.32f))
            ) {
                Text(
                    text = statusLabel,
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.outputName ?: entry.projectName,
                    color = Mocha.Text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = detail,
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall
                )
                entry.diagnosticSummary?.let { diagnostic ->
                    Text(
                        text = diagnostic,
                        color = if (entry.status == ExportHistoryStatus.COMPLETE) Mocha.Subtext0 else Mocha.Yellow,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (entry.mediaBlockingCount > 0 || entry.mediaWarningCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.export_history_media_issues_format,
                            entry.mediaBlockingCount,
                            entry.mediaWarningCount
                        ),
                        color = Mocha.Peach,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
private fun ColorConfidenceOutlook(report: ExportColorConfidenceEngine.Report) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (report.hasWarnings) Mocha.Yellow.copy(alpha = 0.08f) else Mocha.Green.copy(alpha = 0.08f),
        border = BorderStroke(
            1.dp,
            if (report.hasWarnings) Mocha.Yellow.copy(alpha = 0.24f) else Mocha.Green.copy(alpha = 0.22f)
        ),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.export_color_confidence_title),
                color = Mocha.Text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.export_color_confidence_description),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                report.chips.forEach { chip ->
                    ColorConfidencePill(chip = chip)
                }
            }
            report.warnings.forEach { warning ->
                Text(
                    text = warning,
                    color = Mocha.Yellow,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DeviceTierOutlook(hint: EncoderCapabilityProbe.DeviceEncodingTierHint) {
    val accent = when (hint.tier) {
        EncoderCapabilityProbe.DeviceEncodingTier.PREMIUM -> Mocha.Mauve
        EncoderCapabilityProbe.DeviceEncodingTier.ADVANCED -> Mocha.Blue
        EncoderCapabilityProbe.DeviceEncodingTier.STANDARD -> Mocha.Subtext0
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
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
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.export_device_tier_title, hint.tier.displayName),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = hint.detail,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (hint.hasHardwareHevc) {
                    DeviceCapabilityPill(stringResource(R.string.export_hardware_hevc), Mocha.Blue)
                }
                if (hint.hasHardwareAv1) {
                    DeviceCapabilityPill(stringResource(R.string.export_hardware_av1), Mocha.Green)
                }
                if (hint.hasHardwareVp9) {
                    DeviceCapabilityPill(stringResource(R.string.export_hardware_vp9), Mocha.Teal)
                }
                hint.hdrFormats
                    .sortedBy { it.displayName }
                    .forEach { format ->
                        DeviceCapabilityPill(format.displayName, Mocha.Yellow)
                    }
            }
        }
    }
}

@Composable
private fun DeviceCapabilityPill(
    text: String,
    accent: Color
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(Radius.sm),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ColorConfidencePill(
    chip: ExportColorConfidenceEngine.Chip,
    modifier: Modifier = Modifier
) {
    val accent = when (chip.tone) {
        ExportColorConfidenceEngine.Tone.GOOD -> Mocha.Green
        ExportColorConfidenceEngine.Tone.INFO -> Mocha.Blue
        ExportColorConfidenceEngine.Tone.WARNING -> Mocha.Yellow
    }
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = chip.label,
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = chip.detail,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall
            )
        }
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
                    shape = RoundedCornerShape(Radius.sm)
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
    val context = LocalContext.current
    val pickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not grant persistable access; the current
                // in-memory URI still works for the active export session.
            }
            onWatermarkChanged(
                (watermark ?: Watermark(sourceUri = uri)).copy(sourceUri = uri)
            )
        }
    }

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
        }
    }
}

@Composable
private fun ExportToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color
) {
    val colors = LocalClearCutColors.current
    val contentAlpha = if (enabled) 1f else 0.52f
    val semanticState = stringResource(if (checked && enabled) R.string.state_on else R.string.state_off)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (checked && enabled) accent.copy(alpha = 0.08f) else colors.panelRaised.copy(alpha = 0.7f),
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(
            1.dp,
            if (checked && enabled) accent.copy(alpha = 0.24f) else colors.cardStroke
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = semanticState }
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accent.copy(alpha = if (enabled) 0.14f else 0.07f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.22f else 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent.copy(alpha = contentAlpha),
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
                    color = colors.text.copy(alpha = contentAlpha),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    color = colors.subtext.copy(alpha = contentAlpha),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Surface(
                color = if (checked && enabled) accent.copy(alpha = 0.14f) else colors.panel,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (checked && enabled) accent.copy(alpha = 0.26f) else colors.cardStroke)
            ) {
                Text(
                    text = semanticState,
                    color = if (checked && enabled) accent else colors.subtext.copy(alpha = contentAlpha),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }

            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent,
                    checkedThumbColor = Mocha.Crust,
                    uncheckedTrackColor = Mocha.Surface1,
                    uncheckedThumbColor = colors.subtext
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
    val colors = LocalClearCutColors.current
    val statusDescription = listOfNotNull(title, progressLabel, secondaryBody).joinToString(". ")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = statusDescription
            },
        colors = CardDefaults.cardColors(containerColor = colors.panel),
        border = BorderStroke(
            1.dp,
            if (colors.highContrast) colors.cardStrokeStrong else colors.cardStroke.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(Radius.xxl)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = 0.12f),
                        colors.panelHighest.copy(alpha = 0.82f),
                        colors.panel
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
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(title, color = colors.text, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    color = colors.subtext,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                if (progress != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    // Smoothly animate the bar so it doesn't snap on each Transformer progress tick.
                    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = progress.coerceIn(0f, 1f),
                        animationSpec = Motion.standard(),
                        label = "exportProgress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .semantics {
                                progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress, 0f..1f)
                            },
                        color = tint,
                        trackColor = colors.panelHighest.copy(alpha = 0.8f)
                    )
                }
                if (progressLabel != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        progressLabel,
                        color = colors.text,
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
                        ClearCutSecondaryButton(
                            text = primaryLabel,
                            onClick = onPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentColor = tint
                        )
                    }
                    PrimaryStyle.Quiet -> {
                        ClearCutSecondaryButton(
                            text = primaryLabel,
                            onClick = onPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentColor = colors.text
                        )
                    }
                    PrimaryStyle.Filled -> {
                        ClearCutPrimaryButton(
                            text = primaryLabel,
                            onClick = onPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            icon = if (tint == Mocha.Red) Icons.Default.Error else null
                        )
                    }
                }

                if (secondaryLabel != null && onSecondary != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ClearCutSecondaryButton(
                        text = secondaryLabel,
                        onClick = onSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = colors.text
                    )
                }

                if (tertiaryLabel != null && onTertiary != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = onTertiary) {
                        Text(tertiaryLabel, color = colors.subtext, style = MaterialTheme.typography.labelLarge)
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
        shape = RoundedCornerShape(10.dp),
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

private fun formatHistoryBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ExportPreviewPlayer(filePath: String) {
    val context = LocalContext.current
    val file = remember(filePath) { java.io.File(filePath) }
    if (!file.exists()) return

    val player = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    DisposableEffect(filePath) {
        onDispose { player.release() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Mocha.Surface0),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    controllerAutoShow = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )
    }
}
