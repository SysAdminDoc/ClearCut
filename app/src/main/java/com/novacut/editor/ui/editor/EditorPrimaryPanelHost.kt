package com.novacut.editor.ui.editor

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.novacut.editor.R
import com.novacut.editor.engine.ExportColorConfidenceEngine
import com.novacut.editor.engine.ProjectColorPolicy
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.Transition
import com.novacut.editor.ui.NovaCutTestTags
import com.novacut.editor.ui.export.ExportSheet
import com.novacut.editor.ui.export.ExportSheetPresentation
import com.novacut.editor.ui.mediapicker.MediaPickerSheet
import java.io.File

@Composable
fun BoxScope.EditorPrimaryPanelHost(
    state: EditorState,
    viewModel: EditorViewModel,
    selectedClip: Clip?,
    playheadMs: Long,
    useEmbeddedExportPane: Boolean,
    embeddedExportPaneWidth: Dp,
    context: Context,
    onStartExportRequested: (File) -> Unit = { outputDir -> viewModel.startExport(outputDir) },
    onStartVoiceoverRecording: () -> Unit
) {
    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.MEDIA_PICKER),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        MediaPickerSheet(
            onMediaSelected = { uri, mediaType ->
                val trackType = when (mediaType) {
                    "audio" -> TrackType.AUDIO
                    else -> TrackType.VIDEO
                }
                viewModel.addClipToTrack(uri, trackType)
            },
            onClose = viewModel::hideMediaPicker,
            modifier = Modifier.testTag(NovaCutTestTags.MEDIA_PICKER_SHEET)
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.EFFECTS),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column {
            MiniPlayerBar(
                isPlaying = state.isPlaying,
                playheadMs = playheadMs,
                totalDurationMs = state.totalDurationMs,
                onTogglePlayback = viewModel::togglePlayback,
                onSeek = viewModel::seekTo
            )
            EffectsPanel(
                selectedClip = selectedClip,
                trackedObjects = state.trackedObjects,
                onAddEffect = { effectType ->
                    val clipId = state.selectedClipId ?: return@EffectsPanel
                    val effect = Effect(type = effectType, params = EffectType.defaultParams(effectType))
                    viewModel.addEffect(clipId, effect)
                    viewModel.selectEffect(effect.id)
                    viewModel.hideEffectsPanel()
                },
                onAddTrackedMosaic = { trackedObject ->
                    viewModel.applyTrackedMosaicToObject(trackedObject.id)
                    viewModel.hideEffectsPanel()
                },
                onClose = viewModel::hideEffectsPanel
            )
        }
    }

    BottomSheetSlot(
        visible = state.currentTool == EditorTool.SPEED && state.selectedClipId != null,
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column {
            MiniPlayerBar(
                isPlaying = state.isPlaying,
                playheadMs = playheadMs,
                totalDurationMs = state.totalDurationMs,
                onTogglePlayback = viewModel::togglePlayback,
                onSeek = viewModel::seekTo
            )
            val clip = selectedClip
            if (clip != null) {
                SpeedPanel(
                    currentSpeed = clip.speed,
                    isReversed = clip.isReversed,
                    onSpeedDragStarted = viewModel::beginSpeedChange,
                    onSpeedDragEnded = viewModel::endSpeedChange,
                    onSpeedChanged = { viewModel.setClipSpeed(clip.id, it) },
                    onReversedChanged = { viewModel.setClipReversed(clip.id, it) },
                    onClose = { viewModel.setTool(EditorTool.NONE) }
                )
            }
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.TRANSITION_PICKER),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column {
            MiniPlayerBar(
                isPlaying = state.isPlaying,
                playheadMs = playheadMs,
                totalDurationMs = state.totalDurationMs,
                onTogglePlayback = viewModel::togglePlayback,
                onSeek = viewModel::seekTo
            )
            val clip = selectedClip
            TransitionPicker(
                onTransitionSelected = { type ->
                    val clipId = state.selectedClipId ?: return@TransitionPicker
                    viewModel.setTransition(clipId, Transition(type = type))
                },
                onRemoveTransition = {
                    val clipId = state.selectedClipId ?: return@TransitionPicker
                    viewModel.setTransition(clipId, null)
                },
                onDurationChanged = { durationMs ->
                    val clipId = state.selectedClipId ?: return@TransitionPicker
                    viewModel.setTransitionDuration(clipId, durationMs)
                },
                onDurationDragStarted = viewModel::beginTransitionDurationChange,
                onClose = viewModel::hideTransitionPicker,
                currentTransition = clip?.transition
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.TEXT_EDITOR),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val editingOverlay = state.editingTextOverlayId?.let { id ->
            state.textOverlays.firstOrNull { it.id == id }
        }
        TextEditorSheet(
            existingOverlay = editingOverlay,
            playheadMs = playheadMs,
            onSave = { overlay ->
                if (editingOverlay != null) {
                    viewModel.updateTextOverlay(overlay)
                } else {
                    viewModel.addTextOverlay(overlay)
                }
                viewModel.hideTextEditor()
            },
            onClose = viewModel::hideTextEditor
        )
    }

    val exportPanelContent: @Composable () -> Unit = {
        val exportSmartRenderSummary = remember(state.tracks, state.exportConfig, state.textOverlays) {
            SmartRenderEngine.getSummary(
                SmartRenderEngine.analyzeTimeline(
                    tracks = state.tracks,
                    config = state.exportConfig,
                    textOverlays = state.textOverlays
                )
            ).takeIf { it.totalSegments > 0 }
        }
        val sourceHdrSummary = remember(state.tracks) {
            ExportColorConfidenceEngine.summarizeSources(state.tracks)
        }
        ExportSheet(
            config = state.exportConfig,
            exportState = state.exportState,
            exportProgress = state.exportProgress,
            aspectRatio = state.project.aspectRatio,
            errorMessage = state.exportErrorMessage,
            exportStartTime = state.exportStartTime,
            totalDurationMs = state.totalDurationMs,
            smartRenderSummary = exportSmartRenderSummary,
            sourceHdrSummary = sourceHdrSummary,
            projectColorPolicy = ProjectColorPolicy.DEFAULT,
            aiUsageLedger = state.aiUsageLedger,
            presentation = if (useEmbeddedExportPane) {
                ExportSheetPresentation.EMBEDDED_PANE
            } else {
                ExportSheetPresentation.BOTTOM_SHEET
            },
            onConfigChanged = viewModel::updateExportConfig,
            onStartExport = {
                val moviesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                val outputDir = File(moviesDir ?: context.filesDir, "NovaCut").apply { mkdirs() }
                onStartExportRequested(outputDir)
            },
            onShare = {
                viewModel.getShareIntent()?.let { intent ->
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.editor_share_video)))
                }
            },
            onSaveToGallery = viewModel::saveToGallery,
            onCancel = { viewModel.engine.cancelExport() },
            onExportOtio = viewModel::exportToOtio,
            onExportFcpxml = viewModel::exportToFcpxml,
            onCaptureFrame = viewModel::captureFrame,
            onExportSubtitles = { format -> viewModel.exportSubtitles(format) },
            onClearAiUsageLedger = viewModel::clearAiUsageLedger,
            onClose = viewModel::hideExportSheet
        )
    }
    if (useEmbeddedExportPane) {
        SidePanelSlot(
            visible = state.panels.isOpen(PanelId.EXPORT_SHEET),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(embeddedExportPaneWidth)
                .zIndex(8f)
        ) {
            exportPanelContent()
        }
    } else {
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.EXPORT_SHEET),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            exportPanelContent()
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.AUDIO),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column {
            MiniPlayerBar(
                isPlaying = state.isPlaying,
                playheadMs = playheadMs,
                totalDurationMs = state.totalDurationMs,
                onTogglePlayback = viewModel::togglePlayback,
                onSeek = viewModel::seekTo
            )
            val clip = selectedClip
            AudioPanel(
                clip = clip,
                waveform = clip?.let { state.waveforms[it.id] },
                onVolumeChanged = { volume ->
                    val clipId = state.selectedClipId ?: return@AudioPanel
                    viewModel.setClipVolume(clipId, volume)
                },
                onVolumeDragStarted = viewModel::beginVolumeChange,
                onVolumeDragEnded = viewModel::endVolumeChange,
                onFadeInChanged = { fadeMs ->
                    val clipId = state.selectedClipId ?: return@AudioPanel
                    viewModel.setClipFadeIn(clipId, fadeMs)
                },
                onFadeOutChanged = { fadeMs ->
                    val clipId = state.selectedClipId ?: return@AudioPanel
                    viewModel.setClipFadeOut(clipId, fadeMs)
                },
                onFadeDragEnded = viewModel::endFadeAdjust,
                onFadeDragStarted = viewModel::beginFadeAdjust,
                onStartVoiceover = viewModel::showVoiceoverPanel,
                onClose = viewModel::hideAudioPanel
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.VOICEOVER_RECORDER),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        VoiceoverRecorder(
            isRecording = state.isRecordingVoiceover,
            recordingDurationMs = state.voiceoverDurationMs,
            onStartRecording = onStartVoiceoverRecording,
            onStopRecording = viewModel::stopVoiceover,
            onClose = viewModel::hideVoiceoverPanel
        )
    }
}
