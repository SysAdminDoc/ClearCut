package com.novacut.editor.ui.editor

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.novacut.editor.R
import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ColorGrade
import com.novacut.editor.model.EffectType

@Composable
fun BoxScope.EditorClipAdjustmentPanelHost(
    state: EditorState,
    viewModel: EditorViewModel,
    selectedClip: Clip?,
    playheadMs: Long,
    context: Context
) {
    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.TRANSFORM),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val clip = selectedClip
        if (clip != null) {
            TransformPanel(
                clip = clip,
                onTransformDragStarted = viewModel::beginTransformChange,
                onTransformDragEnded = viewModel::endTransformChange,
                onTransformChanged = { px, py, sx, sy, rot ->
                    viewModel.setClipTransform(clip.id, px, py, sx, sy, rot)
                },
                onOpacityDragStarted = viewModel::beginOpacityChange,
                onOpacityDragEnded = viewModel::endOpacityChange,
                onOpacityChanged = { viewModel.setClipOpacity(clip.id, it) },
                onReset = { viewModel.resetClipTransform(clip.id) },
                onClose = viewModel::hideTransformPanel
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.CROP),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        CropPanel(
            currentAspect = state.project.aspectRatio,
            onCropSelected = { ratio -> viewModel.updateProjectAspect(ratio) },
            onClose = viewModel::hideCropPanel
        )
    }

    BottomSheetSlot(
        visible = state.selectedEffectId != null,
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val clip = selectedClip
        val effect = clip?.effects?.firstOrNull { it.id == state.selectedEffectId }
        if (effect != null) {
            EffectAdjustmentPanel(
                effect = effect,
                onUpdateParams = { params ->
                    val clipId = state.selectedClipId ?: return@EffectAdjustmentPanel
                    viewModel.updateEffect(clipId, effect.id, params)
                },
                onEffectDragStarted = viewModel::beginEffectAdjust,
                onEffectDragEnded = viewModel::endEffectAdjust,
                onToggleEnabled = {
                    val clipId = state.selectedClipId ?: return@EffectAdjustmentPanel
                    viewModel.toggleEffectEnabled(clipId, effect.id)
                },
                onRemove = {
                    val clipId = state.selectedClipId ?: return@EffectAdjustmentPanel
                    viewModel.removeEffect(clipId, effect.id)
                    viewModel.clearSelectedEffect()
                },
                onClose = viewModel::clearSelectedEffect
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.COLOR_GRADING),
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
            ColorGradingPanel(
                colorGrade = clip?.colorGrade ?: ColorGrade(),
                onColorGradeChanged = viewModel::updateClipColorGrade,
                onDragStarted = viewModel::beginColorGradeAdjust,
                onLutImport = viewModel::importLut,
                onClose = viewModel::hideColorGrading
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.AUDIO_MIXER),
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
            AudioMixerPanel(
                tracks = state.tracks,
                onTrackVolumeChanged = viewModel::setTrackVolume,
                onVolumeDragStarted = viewModel::beginVolumeAdjust,
                onVolumeDragEnded = viewModel::endVolumeAdjust,
                onTrackPanChanged = viewModel::setTrackPan,
                onPanDragStarted = viewModel::beginPanAdjust,
                onPanDragEnded = viewModel::endPanAdjust,
                onTrackMuteToggled = { viewModel.toggleTrackMute(it) },
                onTrackSoloToggled = viewModel::toggleTrackSolo,
                onTrackAudioEffectAdded = viewModel::addTrackAudioEffect,
                onTrackAudioEffectRemoved = viewModel::removeTrackAudioEffect,
                onTrackAudioEffectParamChanged = viewModel::updateTrackAudioEffectParam,
                vuLevels = state.vuLevels,
                onClose = viewModel::hideAudioMixer
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.KEYFRAME_EDITOR),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val clip = selectedClip
        if (clip != null) {
            KeyframeCurveEditor(
                keyframes = clip.keyframes,
                clipDurationMs = clip.durationMs,
                playheadMs = (playheadMs - clip.timelineStartMs).coerceAtLeast(0L),
                activeProperties = state.activeKeyframeProperties,
                onKeyframesChanged = viewModel::updateClipKeyframes,
                onPropertyToggled = viewModel::toggleKeyframeProperty,
                onAddKeyframe = viewModel::addKeyframe,
                onDeleteKeyframe = viewModel::deleteKeyframe,
                onClose = viewModel::hideKeyframeEditor
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.SPEED_CURVE),
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
                SpeedCurveEditor(
                    speedCurve = clip.speedCurve,
                    constantSpeed = clip.speed,
                    clipDurationMs = clip.durationMs,
                    onSpeedCurveChanged = viewModel::setClipSpeedCurve,
                    onConstantSpeedChanged = { speed ->
                        state.selectedClipId?.let { viewModel.setClipSpeed(it, speed) }
                    },
                    isReversed = clip.isReversed,
                    onReversedChanged = { reversed ->
                        state.selectedClipId?.let { viewModel.setClipReversed(it, reversed) }
                    },
                    onClose = viewModel::hideSpeedCurveEditor,
                    onSpeedDragStarted = viewModel::beginSpeedChange,
                    onSpeedDragEnded = viewModel::endSpeedChange
                )
            }
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.MASK_EDITOR),
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
            MaskEditorPanel(
                masks = clip?.masks ?: emptyList(),
                selectedMaskId = state.selectedMaskId,
                onMaskSelected = viewModel::selectMask,
                onMaskAdded = viewModel::addMask,
                onMaskUpdated = viewModel::updateMask,
                onMaskDeleted = viewModel::deleteMask,
                onClose = viewModel::hideMaskEditor
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.BLEND_MODE),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        BlendModeSelector(
            currentMode = selectedClip?.blendMode ?: BlendMode.NORMAL,
            onModeSelected = viewModel::setClipBlendMode,
            onClose = viewModel::hideBlendModeSelector
        )
    }

    if (state.panels.isOpen(PanelId.MASK_EDITOR)) {
        val clip = selectedClip
        if (clip != null) {
            MaskPreviewOverlay(
                masks = clip.masks,
                selectedMaskId = state.selectedMaskId,
                previewWidth = 1f,
                previewHeight = 1f,
                onMaskPointMoved = viewModel::updateMaskPoint,
                onFreehandDraw = viewModel::setFreehandMaskPoints,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.PIP_PRESETS),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        PipPresetsPanel(
            onPresetSelected = { preset ->
                viewModel.applyPipPreset(preset)
                viewModel.hidePipPresets()
            },
            onClose = viewModel::hidePipPresets
        )
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.CHROMA_KEY),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val clip = selectedClip
        val chromaEffect = clip?.effects?.find { it.type == EffectType.CHROMA_KEY }
        ChromaKeyPanel(
            similarity = chromaEffect?.params?.get("similarity") ?: 0.4f,
            smoothness = chromaEffect?.params?.get("smoothness") ?: 0.1f,
            spillSuppression = chromaEffect?.params?.get("spill") ?: 0.1f,
            keyColorR = chromaEffect?.params?.get("keyR") ?: 0f,
            keyColorG = chromaEffect?.params?.get("keyG") ?: 1f,
            keyColorB = chromaEffect?.params?.get("keyB") ?: 0f,
            onSimilarityChanged = { value ->
                state.selectedClipId?.let { clipId ->
                    chromaEffect?.let {
                        viewModel.updateEffect(clipId, it.id, it.params + ("similarity" to value))
                    }
                }
            },
            onSmoothnessChanged = { value ->
                state.selectedClipId?.let { clipId ->
                    chromaEffect?.let {
                        viewModel.updateEffect(clipId, it.id, it.params + ("smoothness" to value))
                    }
                }
            },
            onSpillChanged = { value ->
                state.selectedClipId?.let { clipId ->
                    chromaEffect?.let {
                        viewModel.updateEffect(clipId, it.id, it.params + ("spill" to value))
                    }
                }
            },
            onKeyColorChanged = { red, green, blue ->
                state.selectedClipId?.let { clipId ->
                    chromaEffect?.let {
                        viewModel.updateEffect(
                            clipId,
                            it.id,
                            it.params + ("keyR" to red) + ("keyG" to green) + ("keyB" to blue)
                        )
                    }
                }
            },
            onShowAlphaMatte = { viewModel.showToast(context.getString(R.string.editor_alpha_matte_preview)) },
            onClose = viewModel::hideChromaKey
        )
    }

    if (state.selectedClipId != null && state.panels.isOpen(PanelId.TRANSFORM)) {
        val clip = selectedClip
        if (clip != null) {
            TransformOverlay(
                positionX = clip.positionX,
                positionY = clip.positionY,
                scaleX = clip.scaleX,
                scaleY = clip.scaleY,
                rotation = clip.rotation,
                anchorX = clip.anchorX,
                anchorY = clip.anchorY,
                opacity = clip.opacity,
                previewWidth = 400f,
                previewHeight = 225f,
                onPositionChanged = { x, y ->
                    state.selectedClipId?.let { viewModel.setClipTransform(it, positionX = x, positionY = y) }
                },
                onScaleChanged = { sx, sy ->
                    state.selectedClipId?.let { viewModel.setClipTransform(it, scaleX = sx, scaleY = sy) }
                },
                onRotationChanged = { rotation ->
                    state.selectedClipId?.let { viewModel.setClipTransform(it, rotation = rotation) }
                },
                onAnchorChanged = viewModel::setClipAnchor,
                onTransformStarted = viewModel::beginTransformChange,
                onTransformEnded = viewModel::endTransformChange,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.CAPTION_EDITOR),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val clip = selectedClip
        CaptionEditorPanel(
            captions = clip?.captions ?: emptyList(),
            playheadMs = (playheadMs - (clip?.timelineStartMs ?: 0L)).coerceAtLeast(0L),
            clipDurationMs = clip?.durationMs ?: 0L,
            onAddCaption = viewModel::addCaption,
            onUpdateCaption = viewModel::updateCaption,
            onDeleteCaption = viewModel::removeCaption,
            onGenerateAutoCaption = viewModel::generateAutoCaption,
            translationRows = state.captionTranslationRows,
            translationSourceLang = state.captionTranslationSourceLang,
            translationTargetLang = state.captionTranslationTargetLang,
            translationQuality = state.captionTranslationQuality,
            translationTargets = viewModel.captionTranslationTargets(),
            onTranslationTargetSelected = viewModel::runCaptionTranslation,
            onTranslationUserEdit = viewModel::applyCaptionTranslationEdit,
            onTranslationRegenerate = viewModel::regenerateCaptionTranslation,
            onClose = viewModel::hideCaptionEditor
        )
    }
}
