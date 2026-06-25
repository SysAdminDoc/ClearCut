package com.novacut.editor.ui.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacut.editor.R
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlayType
import com.novacut.editor.model.TrackType
import com.novacut.editor.ui.export.BatchExportPanel
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.ClearCutDialogIcon
import com.novacut.editor.ui.theme.ClearCutPrimaryButton
import com.novacut.editor.ui.theme.ClearCutSecondaryButton
import com.novacut.editor.ui.theme.Radius

@Composable
fun BoxScope.EditorUtilityPanelHost(
    state: EditorState,
    viewModel: EditorViewModel,
    selectedClip: Clip?,
    playheadMs: Long,
    context: Context,
    onRelinkMedia: (Uri) -> Unit,
    onBulkRelinkMissing: () -> Unit,
    onImportStickerFromGallery: () -> Unit,
    onAction: (String) -> Unit = {}
) {
    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.SCRATCHPAD),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        ScratchpadSheet(
            initialNotes = state.project.notes,
            projectName = state.project.name,
            onNotesChanged = viewModel::updateProjectNotes,
            onClose = viewModel::hideScratchpad
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.CHAPTER_MARKERS),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        ChapterMarkerPanel(
            chapters = state.chapterMarkers,
            playheadMs = playheadMs,
            onAddChapter = viewModel::addChapterMarker,
            onUpdateChapter = viewModel::updateChapterMarker,
            onDeleteChapter = viewModel::deleteChapterMarker,
            onJumpTo = viewModel::seekTo,
            onClose = viewModel::hideChapterMarkers
        )
    }

    if (state.panels.isOpen(PanelId.RECOVERY_DIALOG)) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRecoveryDialog(recover = true) },
            icon = {
                ClearCutDialogIcon(
                    icon = Icons.Default.Restore,
                    accent = Mocha.Green
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.recovery_title),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.recovery_message),
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    recoveryMediaStatusFor(state.media.healthReport)?.let { status ->
                        RecoveryMediaStatusSummary(status = status)
                    }
                }
            },
            confirmButton = {
                ClearCutPrimaryButton(
                    text = stringResource(R.string.recovery_keep),
                    onClick = { viewModel.dismissRecoveryDialog(recover = true) },
                    icon = Icons.Default.Check
                )
            },
            containerColor = Mocha.PanelHighest,
            titleContentColor = Mocha.Text,
            textContentColor = Mocha.Subtext0,
            shape = RoundedCornerShape(Radius.xxl)
        )
    }

    state.aiModelRequirement?.let { req ->
        AiModelRequirementSheet(
            requirement = req,
            onDismiss = viewModel::dismissAiModelRequirement,
            onDownload = {
                viewModel.dismissAiModelRequirement()
                viewModel.showAiToolsPanel()
            },
            onRun = { requirement ->
                viewModel.dismissAiModelRequirement()
                viewModel.runAiToolAfterRequirement(requirement.tool.toolId)
            },
            onReviewModels = {
                viewModel.dismissAiModelRequirement()
                viewModel.showAiToolsPanel()
            },
        )
    }

    state.aiRequirementPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAiRequirementPrompt,
            icon = {
                ClearCutDialogIcon(
                    icon = Icons.Default.Download,
                    accent = Mocha.Mauve
                )
            },
            title = {
                Text(
                    text = prompt.title,
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = prompt.body,
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AiRequirementInfoChip(
                            label = stringResource(R.string.ai_requirement_model_label),
                            value = prompt.modelName,
                            accent = Mocha.Mauve,
                            modifier = Modifier.weight(1f)
                        )
                        AiRequirementInfoChip(
                            label = stringResource(R.string.ai_requirement_size_label),
                            value = prompt.estimatedSize,
                            accent = Mocha.Blue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                ClearCutPrimaryButton(
                    text = prompt.actionLabel,
                    onClick = {
                        viewModel.dismissAiRequirementPrompt()
                        viewModel.showAiToolsPanel()
                    },
                    icon = Icons.Default.AutoAwesome
                )
            },
            dismissButton = {
                ClearCutSecondaryButton(
                    text = stringResource(R.string.ai_requirement_not_now),
                    onClick = viewModel::dismissAiRequirementPrompt,
                    icon = Icons.Default.Close
                )
            },
            containerColor = Mocha.PanelHighest,
            titleContentColor = Mocha.Text,
            textContentColor = Mocha.Subtext0,
            shape = RoundedCornerShape(Radius.xxl)
        )
    }

    state.backupImportFeedback?.let { feedback ->
        BackupImportReportDialog(
            feedback = feedback,
            onDismiss = viewModel::dismissBackupImportFeedback
        )
    }

    state.timelineExchangeFeedback?.let { feedback ->
        TimelineExchangeReportDialog(
            feedback = feedback,
            onDismiss = viewModel::dismissTimelineExchangeFeedback
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.SNAPSHOT_HISTORY),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        SnapshotHistoryPanel(
            snapshots = state.projectSnapshots,
            onCreateSnapshot = viewModel::createSnapshot,
            onRestoreSnapshot = viewModel::restoreSnapshot,
            onDeleteSnapshot = viewModel::deleteSnapshot,
            onClose = viewModel::hideSnapshotHistory
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.MEDIA_MANAGER),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        MediaManagerPanel(
            tracks = state.tracks,
            relinkReports = state.mediaRelinkReports,
            mediaHealthReport = state.media.healthReport,
            onJumpToClip = viewModel::jumpToClip,
            onRelinkMedia = onRelinkMedia,
            onBulkRelinkMissing = onBulkRelinkMissing,
            onRemoveUnused = viewModel::removeUnusedMedia,
            onClose = viewModel::hideMediaManager
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.AUDIO_NORM),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        AudioNormPanel(
            currentVolume = selectedClip?.volume ?: 1f,
            onNormalize = viewModel::normalizeAudio,
            onNormalizeAll = viewModel::normalizeAllClips,
            hasSelectedClip = selectedClip != null,
            onClose = viewModel::hideAudioNorm
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.RENDER_PREVIEW),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        state.renderSummary?.let { summary ->
            RenderPreviewSheet(
                segments = state.renderSegments,
                summary = summary,
                onRenderPreview = viewModel::renderQuickPreview,
                onRenderFull = {
                    viewModel.hideRenderPreview()
                    viewModel.showExportSheet()
                },
                onClose = viewModel::hideRenderPreview
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.BATCH_EXPORT),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        BatchExportPanel(
            queue = state.batchExportQueue,
            onAddItem = viewModel::addBatchExportItem,
            onRemoveItem = viewModel::removeBatchExportItem,
            onStartBatch = viewModel::startBatchExport,
            onClose = viewModel::hideBatchExport
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.BEAT_SYNC),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        BeatSyncPanel(
            beatMarkers = state.beatMarkers,
            totalDurationMs = state.totalDurationMs,
            isAnalyzing = state.isAnalyzingBeats,
            isPlaying = state.isPlaying,
            onAnalyze = viewModel::analyzeBeats,
            onTapBeat = viewModel::tapBeatMarker,
            onClearBeats = viewModel::clearBeatMarkers,
            onApplyBeatSync = viewModel::applyBeatSync,
            onClose = viewModel::hideBeatSync
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.CAPTION_STYLE_GALLERY),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        CaptionStyleGallery(
            onStyleSelected = viewModel::applyCaptionStyle,
            onClose = viewModel::hideCaptionStyleGallery,
            installedStyles = remember { viewModel.installedCaptionStyles() },
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.SPEED_PRESETS),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        SpeedPresetsPanel(
            onPresetSelected = viewModel::applySpeedPreset,
            onClose = viewModel::hideSpeedPresets
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.SMART_REFRAME),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        SmartReframePanel(
            currentAspect = state.project.aspectRatio,
            isProcessing = state.isReframing,
            onReframe = viewModel::applySmartReframe,
            onClose = viewModel::hideSmartReframe
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.UNDO_HISTORY),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        UndoHistoryPanel(
            currentIndex = state.undoStack.size,
            entries = state.undoHistoryEntries,
            onJumpTo = viewModel::jumpToUndoState,
            onClose = viewModel::hideUndoHistory
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.MARKER_LIST),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        MarkerListPanel(
            markers = state.timelineMarkers,
            onJumpTo = { viewModel.seekTo(it) },
            onDelete = viewModel::deleteTimelineMarker,
            onUpdateLabel = viewModel::updateMarkerLabel,
            onClose = viewModel::hideMarkerList
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.COMMAND_PALETTE),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        CommandPaletteSheet(
            hasSelectedClip = state.selectedClipId != null,
            onAction = onAction,
            onDismiss = viewModel::hideCommandPalette
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.TTS),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        TtsPanel(
            isAvailable = state.isTtsAvailable,
            isSynthesizing = state.isSynthesizingTts,
            onSynthesize = viewModel::synthesizeTts,
            onPreview = viewModel::previewTts,
            onStopPreview = viewModel::stopTtsPreview,
            onClose = viewModel::hideTts
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.AUTO_EDIT),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        AutoEditPanel(
            clipCount = state.tracks.filter { it.type == TrackType.VIDEO }.flatMap { it.clips }.size,
            hasAudio = state.tracks.any { it.type == TrackType.AUDIO && it.clips.isNotEmpty() },
            isProcessing = state.isAutoEditing,
            onGenerate = { script ->
                val storyboardScript = if (state.storyboardCards.isNotEmpty() && script.isNullOrBlank()) {
                    state.storyboardCards.sortedBy { it.ordinal }
                        .joinToString("\n") { "${it.ordinal + 1}. ${it.shotText} (${it.targetDurationMs / 1000}s)" }
                } else script
                viewModel.runAutoEdit(storyboardScript)
            },
            onClose = viewModel::hideAutoEdit
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.STORYBOARD),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        StoryboardPanel(
            cards = state.storyboardCards,
            onAddCard = viewModel::addStoryboardCard,
            onUpdateCard = viewModel::updateStoryboardCard,
            onRemoveCard = viewModel::removeStoryboardCard,
            onClose = viewModel::hideStoryboard
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.NOISE_REDUCTION),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        NoiseReductionPanel(
            isAnalyzing = state.isAnalyzingNoise,
            analysisResult = state.noiseAnalysisResult,
            onAnalyze = viewModel::analyzeAndReduceNoise,
            onClose = viewModel::hideNoiseReduction
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.EFFECT_LIBRARY),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        EffectLibraryPanel(
            hasClipSelected = state.selectedClipId != null,
            hasCopiedEffects = state.copiedEffects.isNotEmpty(),
            onExportEffects = { viewModel.exportClipEffects("exported_effects") },
            onImportEffects = { viewModel.showToast(context.getString(R.string.editor_use_file_picker_import)) },
            onCopyEffects = viewModel::copyEffects,
            onPasteEffects = viewModel::pasteEffects,
            onClose = viewModel::hideEffectLibrary
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.STICKER_PICKER),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        StickerPickerPanel(
            onStickerSelected = { uri ->
                viewModel.addImageOverlay(uri, ImageOverlayType.STICKER)
                viewModel.hideStickerPicker()
            },
            onImportFromGallery = {
                viewModel.hideStickerPicker()
                onImportStickerFromGallery()
            },
            onClose = viewModel::hideStickerPicker
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.DRAWING),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        DrawingOverlayPanel(
            drawingColor = state.drawingColor,
            drawingStrokeWidth = state.drawingStrokeWidth,
            onColorChanged = viewModel::setDrawingColor,
            onStrokeWidthChanged = viewModel::setDrawingStrokeWidth,
            onUndo = viewModel::undoLastPath,
            onClear = viewModel::clearDrawing,
            onDone = viewModel::hideDrawingMode
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.MULTI_CAM),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        MultiCamPanel(
            tracks = state.tracks,
            selectedClipId = state.selectedClipId,
            onAngleSelected = viewModel::switchMultiCamAngle,
            onSyncClips = viewModel::syncMultiCamClips,
            onClose = viewModel::hideMultiCam
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.V369_FEATURES),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        V369FeaturesPanel(
            viewModel = viewModel,
            onDismiss = viewModel::hideV369Features
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.CLOUD_BACKUP),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val lastBackupTime by viewModel.lastBackupTime.collectAsStateWithLifecycle()
        val backupSize by viewModel.backupEstimatedSize.collectAsStateWithLifecycle()
        val isExportingBackup by viewModel.isExportingBackup.collectAsStateWithLifecycle()
        val isImportingBackup by viewModel.isImportingBackup.collectAsStateWithLifecycle()
        var pendingBackupImportUri by remember { mutableStateOf<Uri?>(null) }

        LaunchedEffect(Unit) { viewModel.estimateBackupSize() }

        val backupImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) pendingBackupImportUri = uri
        }

        CloudBackupPanel(
            lastBackupTime = lastBackupTime,
            estimatedSizeBytes = backupSize,
            isExporting = isExportingBackup,
            isImporting = isImportingBackup,
            onExportBackup = viewModel::exportProjectBackup,
            onImportBackup = { backupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            onClose = viewModel::hideCloudBackup
        )

        pendingBackupImportUri?.let { importUri ->
            AlertDialog(
                onDismissRequest = { pendingBackupImportUri = null },
                icon = {
                    ClearCutDialogIcon(
                        icon = Icons.Default.Restore,
                        accent = Mocha.Blue
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.panel_cloud_backup_import_confirm_title),
                        color = Mocha.Text,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.panel_cloud_backup_import_confirm_body),
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    ClearCutPrimaryButton(
                        text = stringResource(R.string.panel_cloud_backup_import_confirm_action),
                        onClick = {
                            pendingBackupImportUri = null
                            viewModel.importProjectBackup(importUri)
                        },
                        icon = Icons.Default.Restore
                    )
                },
                dismissButton = {
                    ClearCutSecondaryButton(
                        text = stringResource(R.string.editor_cancel),
                        onClick = { pendingBackupImportUri = null },
                        icon = Icons.Default.Close
                    )
                },
                containerColor = Mocha.PanelHighest,
                titleContentColor = Mocha.Text,
                textContentColor = Mocha.Subtext0,
                shape = RoundedCornerShape(Radius.xxl)
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.TEXT_TEMPLATES),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        TextTemplateGallery(
            playheadMs = playheadMs,
            onTemplateSelected = { template -> viewModel.applyTextTemplate(template) },
            onClose = viewModel::hideTextTemplates
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.PROJECT_INSPECTOR),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        ProjectInspectorPanel(
            data = viewModel.collectProjectInspectorData(),
            onClose = viewModel::hideProjectInspector
        )
    }
}

@Composable
private fun RecoveryMediaStatusSummary(status: RecoveryMediaStatus) {
    val title = when (status.kind) {
        RecoveryMediaStatusKind.NO_MEDIA -> stringResource(R.string.recovery_media_no_media_title)
        RecoveryMediaStatusKind.READY -> stringResource(R.string.recovery_media_ready_title)
        RecoveryMediaStatusKind.NEEDS_REPAIR -> stringResource(R.string.recovery_media_repair_title)
        RecoveryMediaStatusKind.PROXY_FALLBACK -> stringResource(R.string.recovery_media_proxy_title)
        RecoveryMediaStatusKind.WARNINGS -> stringResource(R.string.recovery_media_warning_title)
    }
    val body = when (status.kind) {
        RecoveryMediaStatusKind.NO_MEDIA -> stringResource(R.string.recovery_media_no_media_body)
        RecoveryMediaStatusKind.READY -> pluralStringResource(
            R.plurals.recovery_media_ready_body,
            status.totalReferences,
            status.totalReferences
        )
        RecoveryMediaStatusKind.NEEDS_REPAIR -> pluralStringResource(
            R.plurals.recovery_media_repair_body,
            status.blockingCount,
            status.blockingCount
        )
        RecoveryMediaStatusKind.PROXY_FALLBACK -> pluralStringResource(
            R.plurals.recovery_media_proxy_body,
            status.warningCount,
            status.warningCount
        )
        RecoveryMediaStatusKind.WARNINGS -> pluralStringResource(
            R.plurals.recovery_media_warning_body,
            status.warningCount,
            status.warningCount
        )
    }
    val accent = when (status.kind) {
        RecoveryMediaStatusKind.NEEDS_REPAIR -> Mocha.Red
        RecoveryMediaStatusKind.PROXY_FALLBACK,
        RecoveryMediaStatusKind.WARNINGS -> Mocha.Yellow
        RecoveryMediaStatusKind.NO_MEDIA,
        RecoveryMediaStatusKind.READY -> Mocha.Green
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = accent,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = body,
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
