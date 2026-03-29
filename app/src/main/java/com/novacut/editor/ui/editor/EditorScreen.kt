package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.*
import com.novacut.editor.model.ClipLabel
import androidx.compose.ui.graphics.Color
import com.novacut.editor.ui.export.BatchExportPanel
import com.novacut.editor.ui.export.ExportSheet
import com.novacut.editor.ui.mediapicker.MediaPickerSheet
import com.novacut.editor.ui.theme.Mocha
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import java.io.File

@Composable
fun EditorScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val whisperState by viewModel.whisperModelState.collectAsStateWithLifecycle()
    val whisperProgress by viewModel.whisperDownloadProgress.collectAsStateWithLifecycle()
    val segmentationState by viewModel.segmentationModelState.collectAsStateWithLifecycle()
    val segmentationProgress by viewModel.segmentationDownloadProgress.collectAsStateWithLifecycle()
    val scopeFrame by viewModel.scopeFrame.collectAsStateWithLifecycle()
    val showLutPicker by viewModel.showLutPicker.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // LUT file picker
    val lutPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onLutFileSelected(uri)
        } else {
            viewModel.onLutPickerDismissed()
        }
    }
    LaunchedEffect(showLutPicker) {
        if (showLutPicker) {
            lutPickerLauncher.launch(arrayOf("*/*"))
        }
    }

    var showClipLabelPicker by remember { mutableStateOf(false) }

    // Radial menu state
    var showRadialMenu by remember { mutableStateOf(false) }
    var radialMenuPosition by remember { mutableStateOf(Offset.Zero) }

    val focusRequester = remember { FocusRequester() }

    val hasOpenPanel = state.panels.hasOpenPanel || state.selectedEffectId != null || state.editingTextOverlayId != null
    val isClipMode = state.selectedClipId != null

    val selectedClip = state.selectedClipId?.let { id ->
        state.tracks.flatMap { it.clips }.find { it.id == id }
    }

    val allCaptions = state.tracks.flatMap { it.clips }.flatMap { clip ->
        clip.captions.map { caption ->
            caption.copy(
                startTimeMs = caption.startTimeMs + clip.timelineStartMs,
                endTimeMs = caption.endTimeMs + clip.timelineStartMs
            )
        }
    }

    BackHandler(enabled = hasOpenPanel || state.currentTool != EditorTool.NONE || isClipMode) {
        when {
            hasOpenPanel -> viewModel.dismissAllPanels()
            state.currentTool != EditorTool.NONE -> viewModel.setTool(EditorTool.NONE)
            state.selectedClipId != null -> viewModel.selectClip(null)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.selectedClipId) {
        if (state.selectedClipId == null) showClipLabelPicker = false
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Mocha.Base)
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when {
                    // Space = play/pause
                    event.key == Key.Spacebar -> { viewModel.togglePlayPause(); true }
                    // Delete/Backspace = delete clip
                    event.key == Key.Delete || event.key == Key.Backspace -> {
                        if (state.selectedClipId != null) viewModel.deleteSelectedClip()
                        true
                    }
                    // M = add marker
                    event.key == Key.M && !event.isCtrlPressed -> { viewModel.addTimelineMarker(); true }
                    // Z = undo (Ctrl+Z)
                    event.key == Key.Z && event.isCtrlPressed && !event.isShiftPressed -> { viewModel.undo(); true }
                    // Shift+Z or Ctrl+Y = redo
                    (event.key == Key.Z && event.isCtrlPressed && event.isShiftPressed) ||
                    (event.key == Key.Y && event.isCtrlPressed) -> { viewModel.redo(); true }
                    // Left arrow = seek back 1s
                    event.key == Key.DirectionLeft && !event.isCtrlPressed -> {
                        viewModel.seekTo((playheadMs - 1000).coerceAtLeast(0))
                        true
                    }
                    // Right arrow = seek forward 1s
                    event.key == Key.DirectionRight && !event.isCtrlPressed -> {
                        viewModel.seekTo(playheadMs + 1000)
                        true
                    }
                    // Ctrl+Left = seek back 5s
                    event.key == Key.DirectionLeft && event.isCtrlPressed -> {
                        viewModel.seekTo((playheadMs - 5000).coerceAtLeast(0))
                        true
                    }
                    // Ctrl+Right = seek forward 5s
                    event.key == Key.DirectionRight && event.isCtrlPressed -> {
                        viewModel.seekTo(playheadMs + 5000)
                        true
                    }
                    // + or = key = zoom in
                    event.key == Key.Equals || event.key == Key.NumPadAdd -> {
                        viewModel.setZoomLevel((state.zoomLevel * 1.33f).coerceAtMost(10f))
                        true
                    }
                    // - key = zoom out
                    event.key == Key.Minus || event.key == Key.NumPadSubtract -> {
                        viewModel.setZoomLevel((state.zoomLevel * 0.75f).coerceAtLeast(0.1f))
                        true
                    }
                    // S = split at playhead
                    event.key == Key.S && !event.isCtrlPressed -> {
                        viewModel.splitAtPlayhead()
                        true
                    }
                    // Ctrl+S = save project
                    event.key == Key.S && event.isCtrlPressed -> {
                        viewModel.saveProject()
                        true
                    }
                    // C = copy effects
                    event.key == Key.C && event.isCtrlPressed -> {
                        viewModel.copyClipEffects()
                        true
                    }
                    // V = paste effects
                    event.key == Key.V && event.isCtrlPressed -> {
                        viewModel.pasteClipEffects()
                        true
                    }
                    else -> false
                }
            } else false
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar (Home / Undo / Redo / Delete / More / Export)
            EditorTopBar(
                projectName = state.project.name,
                onRename = viewModel::renameProject,
                onBack = onBack,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                canUndo = state.undoStack.isNotEmpty(),
                canRedo = state.redoStack.isNotEmpty(),
                selectedClipId = state.selectedClipId,
                onDelete = viewModel::deleteSelectedClip,
                confirmBeforeDelete = viewModel.confirmBeforeDelete,
                onAddMedia = viewModel::showMediaPicker,
                onAddTrack = viewModel::addTrack,
                onExport = viewModel::showExportSheet,
                onSaveTemplate = viewModel::saveAsTemplate,
                editorMode = state.editorMode,
                onToggleEditorMode = viewModel::toggleEditorMode
            )

            // Empty project onboarding hint
            val hasClips = state.tracks.any { it.clips.isNotEmpty() }
            if (!hasClips && !hasOpenPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Mocha.Surface0.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = stringResource(R.string.editor_no_clips_yet),
                                tint = Mocha.Mauve,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.editor_no_clips_yet), color = Mocha.Text, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.editor_add_media_hint),
                                color = Mocha.Subtext0,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Preview panel with long-press radial menu
            if (hasClips || hasOpenPanel) Box(
                modifier = Modifier
                    .weight(0.45f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                radialMenuPosition = offset
                                showRadialMenu = true
                            }
                        )
                    }
            ) {
                PreviewPanel(
                    engine = viewModel.engine,
                    playheadMs = playheadMs,
                    totalDurationMs = state.totalDurationMs,
                    isPlaying = state.isPlaying,
                    isLooping = state.isLooping,
                    aspectRatio = state.project.aspectRatio,
                    frameRate = state.project.frameRate,
                    onTogglePlayback = viewModel::togglePlayback,
                    onToggleLoop = viewModel::toggleLoop,
                    onSeek = viewModel::seekTo,
                    selectedClipId = state.selectedClipId,
                    onPreviewTransformStarted = { viewModel.beginTransformChange() },
                    onPreviewTransformChanged = { dx, dy, scaleChange, rotationChange ->
                        val clip = selectedClip ?: return@PreviewPanel
                        viewModel.setClipTransform(
                            clipId = clip.id,
                            positionX = clip.positionX + dx / 500f,
                            positionY = clip.positionY + dy / 500f,
                            scaleX = (clip.scaleX * scaleChange),
                            scaleY = (clip.scaleY * scaleChange),
                            rotation = clip.rotation + rotationChange
                        )
                    },
                    showScopesButton = true,
                    onToggleScopes = viewModel::toggleScopes,
                    modifier = Modifier.fillMaxSize()
                )

                if (showRadialMenu) {
                    RadialActionMenu(
                        position = radialMenuPosition,
                        hasClipSelected = isClipMode,
                        onAction = { actionId ->
                            showRadialMenu = false
                            when (actionId) {
                                "add_media" -> viewModel.showMediaPicker()
                                "add_text" -> viewModel.showTextEditor()
                                "add_audio" -> viewModel.showMediaPicker()
                                "record" -> viewModel.showVoiceoverPanel()
                                "snapshot" -> viewModel.createSnapshot()
                                "split" -> viewModel.splitClipAtPlayhead()
                                "duplicate" -> viewModel.duplicateSelectedClip()
                                "effects" -> viewModel.showEffectsPanel()
                                "speed" -> viewModel.showSpeedCurveEditor()
                                "transform" -> viewModel.showTransformPanel()
                                "delete" -> viewModel.deleteSelectedClip()
                            }
                        },
                        onDismiss = { showRadialMenu = false }
                    )
                }
            }

            // Multi-select action bar
            if (state.selectedClipIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Mocha.Peach.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.editor_selected_count, state.selectedClipIds.size),
                        color = Mocha.Peach,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::deleteMultiSelectedClips) {
                        Icon(Icons.Default.Delete, stringResource(R.string.editor_delete_selected), tint = Mocha.Red, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_delete), color = Mocha.Red, fontSize = 12.sp)
                    }
                    TextButton(onClick = viewModel::clearMultiSelect) {
                        Text(stringResource(R.string.editor_cancel), color = Mocha.Subtext0, fontSize = 12.sp)
                    }
                }
            }

            // AI Suggestion Banner
            AiSuggestionBanner(
                suggestion = state.aiSuggestion,
                onApply = { actionId ->
                    viewModel.dismissAiSuggestion()
                    when (actionId) {
                        "auto_color" -> viewModel.runAiTool("auto_color")
                        "denoise" -> viewModel.runAiTool("denoise")
                        "transition" -> viewModel.showTransitionPicker()
                        else -> Log.w("EditorScreen", "Unknown AI suggestion action: $actionId")
                    }
                },
                onDismiss = viewModel::dismissAiSuggestion
            )

            // Timeline collapse toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleTimelineCollapse() }
                    .background(Mocha.Mantle)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.editor_timeline), color = Mocha.Subtext0, fontSize = 11.sp)
                Icon(
                    if (state.isTimelineCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = stringResource(R.string.editor_toggle_timeline),
                    tint = Mocha.Subtext0,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Timeline
            AnimatedVisibility(
                visible = !state.isTimelineCollapsed,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Timeline(
                    tracks = state.tracks,
                    playheadMs = playheadMs,
                    totalDurationMs = state.totalDurationMs,
                    zoomLevel = state.zoomLevel,
                    scrollOffsetMs = state.scrollOffsetMs,
                    selectedClipId = state.selectedClipId,
                    isTrimMode = state.currentTool == EditorTool.TRIM,
                    waveforms = if (viewModel.showWaveforms) state.waveforms else emptyMap(),
                    onClipSelected = viewModel::selectClip,
                    onPlayheadMoved = viewModel::seekTo,
                    onZoomChanged = viewModel::setZoomLevel,
                    onScrollChanged = viewModel::setScrollOffset,
                    onTrimChanged = viewModel::trimClip,
                    onTrimDragStarted = viewModel::beginTrim,
                    onTrimDragEnded = viewModel::endTrim,
                    onTimelineWidthChanged = viewModel::setTimelineWidth,
                    onToggleTrackMute = viewModel::toggleTrackMute,
                    onToggleTrackVisible = viewModel::toggleTrackVisibility,
                    onToggleTrackLock = viewModel::toggleTrackLock,
                    beatMarkers = state.beatMarkers,
                    selectedClipIds = state.selectedClipIds,
                    snapToBeat = viewModel.snapToBeat,
                    snapToMarker = viewModel.snapToMarker,
                    markers = state.timelineMarkers,
                    onAddMarker = { viewModel.addTimelineMarker() },
                    onMarkerTapped = { marker -> viewModel.seekTo(marker.timeMs) },
                    onClipLongPress = viewModel::toggleClipMultiSelect,
                    onSlideClip = viewModel::slideClip,
                    onSlipClip = viewModel::slipClip,
                    onToggleTrackCollapsed = viewModel::toggleTrackCollapsed,
                    onToggleTrackWaveform = viewModel::toggleTrackWaveform,
                    onCollapseAllTracks = viewModel::collapseAllTracks,
                    onExpandAllTracks = viewModel::expandAllTracks,
                    onSetTrackHeight = viewModel::setTrackHeight,
                    onScrubStart = viewModel::beginScrub,
                    onScrubEnd = viewModel::endScrub,
                    engine = viewModel.engine,
                    modifier = Modifier.weight(0.55f)
                )
            }

            // Bottom tool area (PowerDirector-style tab bar + sub-menu grids)
            BottomToolArea(
                selectedClipId = state.selectedClipId,
                hasCopiedEffects = state.copiedEffects.isNotEmpty(),
                textOverlays = state.textOverlays,
                onEditTextOverlay = { id -> viewModel.editTextOverlay(id) },
                editorMode = state.editorMode,
                onDeleteTextOverlay = { id ->
                    viewModel.removeTextOverlay(id)
                },
                onAction = { actionId ->
                    when (actionId) {
                        "edit" -> viewModel.showMediaPicker()
                        "audio_add" -> viewModel.showMediaPicker()
                        "audio_tool" -> viewModel.showAudioPanel()
                        "speed" -> viewModel.showSpeedCurveEditor()
                        "transform" -> viewModel.showTransformPanel()
                        "effects" -> viewModel.showEffectsPanel()
                        "effects_disabled" -> viewModel.showToast(context.getString(R.string.editor_select_clip_effects))
                        "transition" -> viewModel.showTransitionPicker()
                        "aspect" -> viewModel.showCropPanel()
                        "back" -> { viewModel.dismissAllPanels(); viewModel.selectClip(null) }
                        "add_text" -> viewModel.showTextEditor()
                        "split" -> { viewModel.splitClipAtPlayhead(); viewModel.setTool(EditorTool.NONE) }
                        "trim" -> { viewModel.setTool(EditorTool.TRIM); viewModel.dismissAllPanels() }
                        "merge" -> viewModel.mergeWithNextClip()
                        "duplicate" -> viewModel.duplicateSelectedClip()
                        "freeze" -> { viewModel.insertFreezeFrame(); viewModel.setTool(EditorTool.NONE) }
                        "copy_fx" -> viewModel.copyEffects()
                        "paste_fx" -> viewModel.pasteEffects()
                        // New features
                        "color_grade" -> viewModel.showColorGrading()
                        "color_grade_disabled" -> viewModel.showToast(context.getString(R.string.editor_select_clip_color_grade))
                        "keyframes" -> viewModel.showKeyframeEditor()
                        "keyframes_disabled" -> viewModel.showToast(context.getString(R.string.editor_select_clip_keyframes))
                        "masks" -> viewModel.showMaskEditor()
                        "masks_disabled" -> viewModel.showToast(context.getString(R.string.editor_select_clip_masks))
                        "blend_mode" -> viewModel.showBlendModeSelector()
                        "blend_mode_disabled" -> viewModel.showToast(context.getString(R.string.editor_select_clip_blend_mode))
                        "pip" -> viewModel.showPipPresets()
                        "pip_disabled" -> viewModel.showToast(context.getString(R.string.editor_select_clip_pip))
                        "chroma_key" -> viewModel.showChromaKey()
                        "chroma_key_disabled" -> viewModel.showToast(context.getString(R.string.editor_select_clip_chroma_key))
                        "auto_duck" -> viewModel.autoDuck()
                        "scopes" -> viewModel.toggleScopes()
                        "audio_mixer" -> viewModel.showAudioMixer()
                        "beat_detect" -> viewModel.detectBeats()
                        "adjustment_layer" -> viewModel.addAdjustmentLayer()
                        "snapshot" -> viewModel.createSnapshot()
                        "captions" -> {
                            if (state.selectedClipId != null) viewModel.showCaptionEditor()
                            else viewModel.showToast(context.getString(R.string.editor_select_clip_captions))
                        }
                        "captions_disabled" -> viewModel.showToast(context.getString(R.string.editor_select_clip_captions))
                        "chapters" -> viewModel.showChapterMarkers()
                        "history" -> viewModel.showSnapshotHistory()
                        "export_srt" -> viewModel.exportSubtitles(SubtitleFormat.SRT)
                        "export_vtt" -> viewModel.exportSubtitles(SubtitleFormat.VTT)
                        "text_templates" -> viewModel.showTextTemplates()
                        "media_manager" -> viewModel.showMediaManager()
                        "audio_norm" -> viewModel.showAudioNorm()
                        "audio_norm_disabled" -> viewModel.showToast(context.getString(R.string.editor_select_clip_normalize))
                        "compound" -> viewModel.createCompoundClip()
                        "render_preview" -> viewModel.showRenderPreview()
                        "cloud_backup" -> viewModel.showCloudBackup()
                        "archive" -> viewModel.exportProjectArchive()
                        "group" -> viewModel.groupSelectedClips()
                        "ungroup" -> viewModel.ungroupSelectedClips()
                        "unlink_av" -> viewModel.unlinkAudioVideo()
                        "multi_delete" -> viewModel.deleteMultiSelectedClips()
                        "batch_export" -> viewModel.showBatchExport()
                        "proxy_toggle" -> viewModel.setProxyEnabled(!state.proxySettings.enabled)
                        "beat_sync" -> viewModel.showBeatSync()
                        "auto_edit" -> viewModel.showAutoEdit()
                        "smart_reframe" -> viewModel.showSmartReframe()
                        "caption_styles" -> viewModel.showCaptionStyleGallery()
                        "speed_presets" -> viewModel.showSpeedPresets()
                        "filler_removal" -> viewModel.showFillerRemoval()
                        "tts" -> viewModel.showTts()
                        "stickers" -> viewModel.showStickerPicker()
                        "noise_reduction" -> viewModel.showNoiseReduction()
                        "effect_library" -> viewModel.showEffectLibrary()
                        "undo_history" -> viewModel.showUndoHistory()
                        "draw" -> viewModel.showDrawingMode()
                        "label" -> showClipLabelPicker = true
                        "multi_cam" -> viewModel.showMultiCam()
                        "marker_list" -> viewModel.showMarkerList()
                        // AI tools
                        "auto_captions" -> viewModel.runAiTool("auto_captions")
                        "scene_detect" -> viewModel.runAiTool("scene_detect")
                        "smart_crop" -> viewModel.runAiTool("smart_crop")
                        "auto_color" -> viewModel.runAiTool("auto_color")
                        "stabilize" -> viewModel.runAiTool("stabilize")
                        "denoise" -> viewModel.runAiTool("denoise")
                        "remove_bg" -> viewModel.runAiTool("remove_bg")
                        "track_motion" -> viewModel.runAiTool("track_motion")
                        "style_transfer" -> viewModel.runAiTool("style_transfer")
                        "face_track" -> viewModel.runAiTool("face_track")
                        "upscale" -> viewModel.runAiTool("upscale")
                        "frame_interp" -> viewModel.runAiTool("frame_interp")
                        "object_remove" -> viewModel.runAiTool("object_remove")
                        "video_upscale" -> viewModel.runAiTool("video_upscale")
                        "ai_background" -> viewModel.runAiTool("ai_background")
                        "ai_stabilize" -> viewModel.runAiTool("ai_stabilize")
                        "ai_style_transfer" -> viewModel.runAiTool("ai_style_transfer")
                        "bg_replace" -> viewModel.runAiTool("bg_replace")
                        // smart_reframe handled above via showSmartReframe()
                        else -> Log.w("EditorScreen", "Unknown action: $actionId")
                    }
                }
            )
        }

        // Bottom sheets / overlays
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
                onClose = viewModel::hideMediaPicker
            )
        }

        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.EFFECTS),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            EffectsPanel(
                selectedClip = selectedClip,
                onAddEffect = { effectType ->
                    val clipId = state.selectedClipId ?: return@EffectsPanel
                    val effect = Effect(type = effectType, params = EffectType.defaultParams(effectType))
                    viewModel.addEffect(clipId, effect)
                    viewModel.selectEffect(effect.id)
                    viewModel.hideEffectsPanel()
                },
                onClose = viewModel::hideEffectsPanel
            )
        }

        // Speed panel
        BottomSheetSlot(
            visible = state.currentTool == EditorTool.SPEED && state.selectedClipId != null,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
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

        // Transition picker
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.TRANSITION_PICKER),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
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

        // Text editor
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

        // Export sheet
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.EXPORT_SHEET),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ExportSheet(
                config = state.exportConfig,
                exportState = state.exportState,
                exportProgress = state.exportProgress,
                aspectRatio = state.project.aspectRatio,
                errorMessage = state.exportErrorMessage,
                exportStartTime = state.exportStartTime,
                totalDurationMs = state.totalDurationMs,
                onConfigChanged = viewModel::updateExportConfig,
                onStartExport = {
                    // Use app-private external dir — works on all Android versions including 11+
                    val moviesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                    val outputDir = File(moviesDir ?: context.filesDir, "NovaCut").apply { mkdirs() }
                    viewModel.startExport(outputDir)
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
                onClose = viewModel::hideExportSheet
            )
        }

        // Audio panel
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.AUDIO),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = selectedClip
            AudioPanel(
                clip = clip,
                waveform = clip?.let { state.waveforms[it.id] },
                onVolumeChanged = { volume ->
                    val clipId = state.selectedClipId ?: return@AudioPanel
                    viewModel.setClipVolume(clipId, volume)
                },
                onVolumeDragStarted = viewModel::beginVolumeChange,
                onFadeInChanged = { fadeMs ->
                    val clipId = state.selectedClipId ?: return@AudioPanel
                    viewModel.setClipFadeIn(clipId, fadeMs)
                },
                onFadeOutChanged = { fadeMs ->
                    val clipId = state.selectedClipId ?: return@AudioPanel
                    viewModel.setClipFadeOut(clipId, fadeMs)
                },
                onFadeDragStarted = viewModel::beginFadeAdjust,
                onStartVoiceover = viewModel::showVoiceoverPanel,
                onClose = viewModel::hideAudioPanel
            )
        }

        // Voiceover recorder
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.VOICEOVER_RECORDER),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VoiceoverRecorder(
                isRecording = state.isRecordingVoiceover,
                recordingDurationMs = state.voiceoverDurationMs,
                onStartRecording = viewModel::startVoiceover,
                onStopRecording = viewModel::stopVoiceover,
                onClose = viewModel::hideVoiceoverPanel
            )
        }

        // Transform panel
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.TRANSFORM),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = selectedClip
            if (clip != null) {
                TransformPanel(
                    clip = clip,
                    onTransformDragStarted = viewModel::beginTransformChange,
                    onTransformChanged = { px, py, sx, sy, rot ->
                        viewModel.setClipTransform(clip.id, px, py, sx, sy, rot)
                    },
                    onOpacityChanged = { viewModel.setClipOpacity(clip.id, it) },
                    onReset = { viewModel.resetClipTransform(clip.id) },
                    onClose = viewModel::hideTransformPanel
                )
            }
        }

        // Crop panel
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.CROP),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CropPanel(
                currentAspect = state.project.aspectRatio,
                onCropSelected = { ratio ->
                    viewModel.updateProjectAspect(ratio)
                },
                onClose = viewModel::hideCropPanel
            )
        }

        // AI tools panel
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.AI_TOOLS),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            AiToolsPanel(
                hasSelectedClip = state.selectedClipId != null,
                onToolSelected = { toolId -> viewModel.runAiTool(toolId) },
                onDisabledToolTapped = { toolName -> viewModel.showToast("Select a clip to use $toolName") },
                onCancelProcessing = viewModel::cancelAiTool,
                onClose = viewModel::hideAiToolsPanel,
                processingTool = state.aiProcessingTool,
                whisperModelState = whisperState,
                whisperDownloadProgress = whisperProgress,
                onDownloadWhisper = viewModel::downloadWhisperModel,
                onDeleteWhisper = viewModel::deleteWhisperModel,
                segmentationModelState = segmentationState,
                segmentationDownloadProgress = segmentationProgress,
                onDownloadSegmentation = viewModel::downloadSegmentationModel,
                onDeleteSegmentation = viewModel::deleteSegmentationModel
            )
        }

        // Effect adjustment panel
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

        // Color Grading panel
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.COLOR_GRADING),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = selectedClip
            ColorGradingPanel(
                colorGrade = clip?.colorGrade ?: ColorGrade(),
                onColorGradeChanged = viewModel::updateClipColorGrade,
                onDragStarted = viewModel::beginColorGradeAdjust,
                onLutImport = viewModel::importLut,
                onClose = viewModel::hideColorGrading
            )
        }

        // Audio Mixer panel
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.AUDIO_MIXER),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            AudioMixerPanel(
                tracks = state.tracks,
                onTrackVolumeChanged = viewModel::setTrackVolume,
                onTrackPanChanged = viewModel::setTrackPan,
                onTrackMuteToggled = { viewModel.toggleTrackMute(it) },
                onTrackSoloToggled = viewModel::toggleTrackSolo,
                onTrackAudioEffectAdded = viewModel::addTrackAudioEffect,
                onTrackAudioEffectRemoved = viewModel::removeTrackAudioEffect,
                onTrackAudioEffectParamChanged = viewModel::updateTrackAudioEffectParam,
                vuLevels = state.vuLevels,
                onClose = viewModel::hideAudioMixer
            )
        }

        // Keyframe Curve Editor
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

        // Speed Curve Editor
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.SPEED_CURVE),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = selectedClip
            if (clip != null) {
                SpeedCurveEditor(
                    speedCurve = clip.speedCurve,
                    constantSpeed = clip.speed,
                    clipDurationMs = clip.durationMs,
                    onSpeedCurveChanged = viewModel::setClipSpeedCurve,
                    onConstantSpeedChanged = { speed -> state.selectedClipId?.let { viewModel.setClipSpeed(it, speed) } },
                    isReversed = clip.isReversed,
                    onReversedChanged = { rev -> state.selectedClipId?.let { viewModel.setClipReversed(it, rev) } },
                    onClose = viewModel::hideSpeedCurveEditor
                )
            }
        }

        // Mask Editor panel
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.MASK_EDITOR),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
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

        // Blend Mode selector
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

        // Mask preview overlay on the video preview (when mask editor is open)
        if (state.panels.isOpen(PanelId.MASK_EDITOR)) {
            val clip = selectedClip
            if (clip != null) {
                MaskPreviewOverlay(
                    masks = clip.masks,
                    selectedMaskId = state.selectedMaskId,
                    previewWidth = 1f, // Normalized coordinates (0..1)
                    previewHeight = 1f,
                    onMaskPointMoved = viewModel::updateMaskPoint,
                    onFreehandDraw = viewModel::setFreehandMaskPoints,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // PiP Presets
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

        // Chroma Key Refinement
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
                onSimilarityChanged = { v ->
                    state.selectedClipId?.let { cid ->
                        chromaEffect?.let { viewModel.updateEffect(cid, it.id, it.params + ("similarity" to v)) }
                    }
                },
                onSmoothnessChanged = { v ->
                    state.selectedClipId?.let { cid ->
                        chromaEffect?.let { viewModel.updateEffect(cid, it.id, it.params + ("smoothness" to v)) }
                    }
                },
                onSpillChanged = { v ->
                    state.selectedClipId?.let { cid ->
                        chromaEffect?.let { viewModel.updateEffect(cid, it.id, it.params + ("spill" to v)) }
                    }
                },
                onKeyColorChanged = { r, g, b ->
                    state.selectedClipId?.let { cid ->
                        chromaEffect?.let { viewModel.updateEffect(cid, it.id, it.params + ("keyR" to r) + ("keyG" to g) + ("keyB" to b)) }
                    }
                },
                onShowAlphaMatte = { viewModel.showToast(context.getString(R.string.editor_alpha_matte_preview)) },
                onClose = viewModel::hideChromaKey
            )
        }

        // Transform overlay on preview (when clip selected and transform visible)
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
                    onPositionChanged = { x, y -> state.selectedClipId?.let { viewModel.setClipTransform(it, positionX = x, positionY = y) } },
                    onScaleChanged = { sx, sy -> state.selectedClipId?.let { viewModel.setClipTransform(it, scaleX = sx, scaleY = sy) } },
                    onRotationChanged = { r -> state.selectedClipId?.let { viewModel.setClipTransform(it, rotation = r) } },
                    onAnchorChanged = viewModel::setClipAnchor,
                    onTransformStarted = viewModel::beginTransformChange,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Caption Editor
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
                onClose = viewModel::hideCaptionEditor
            )
        }

        // Chapter Markers
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

        // Snapshot History
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

        // Media Manager
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.MEDIA_MANAGER),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MediaManagerPanel(
                tracks = state.tracks,
                onJumpToClip = viewModel::jumpToClip,
                onRelinkMedia = { _, _ -> viewModel.showToast(context.getString(R.string.editor_media_relink_unavailable)) },
                onRemoveUnused = { viewModel.removeUnusedMedia() },
                onClose = viewModel::hideMediaManager
            )
        }

        // Audio Normalization
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.AUDIO_NORM),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = selectedClip
            AudioNormPanel(
                currentVolume = clip?.volume ?: 1f,
                onNormalize = viewModel::normalizeAudio,
                onClose = viewModel::hideAudioNorm
            )
        }

        // Render Preview / Smart Render
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

        // Batch Export
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

        // Beat Sync
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

        // Caption Style Gallery
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.CAPTION_STYLE_GALLERY),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CaptionStyleGallery(
                onStyleSelected = viewModel::applyCaptionStyle,
                onClose = viewModel::hideCaptionStyleGallery
            )
        }

        // Speed Presets
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.SPEED_PRESETS),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SpeedPresetsPanel(
                onPresetSelected = viewModel::applySpeedPreset,
                onClose = viewModel::hideSpeedPresets
            )
        }

        // Smart Reframe
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

        // Undo History
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

        // Marker List
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

        // TTS Panel
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

        // Filler Removal
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.FILLER_REMOVAL),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FillerRemovalPanel(
                regionCount = state.fillerRegions.size,
                isAnalyzing = state.isAnalyzingFillers,
                onAnalyze = viewModel::analyzeFillers,
                onApply = viewModel::applyFillerRemoval,
                onClose = viewModel::hideFillerRemoval
            )
        }

        // Auto Edit
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.AUTO_EDIT),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            AutoEditPanel(
                clipCount = state.tracks.filter { it.type == TrackType.VIDEO }.flatMap { it.clips }.size,
                hasAudio = state.tracks.any { it.type == TrackType.AUDIO && it.clips.isNotEmpty() },
                isProcessing = state.isAutoEditing,
                onGenerate = { script -> viewModel.runAutoEdit(script) },
                onClose = viewModel::hideAutoEdit
            )
        }

        // Noise Reduction
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

        // Effect Library
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

        // Sticker Picker
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.STICKER_PICKER),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            StickerPickerPanel(
                onStickerSelected = { uri ->
                    viewModel.addImageOverlay(uri, com.novacut.editor.model.ImageOverlayType.STICKER)
                    viewModel.hideStickerPicker()
                },
                onImportFromGallery = {
                    viewModel.hideStickerPicker()
                    viewModel.showMediaPicker()
                },
                onClose = viewModel::hideStickerPicker
            )
        }

        // Drawing Overlay
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

        // Drawing Canvas on preview
        if (state.isDrawingMode || state.drawingPaths.isNotEmpty()) {
            DrawingCanvas(
                paths = state.drawingPaths,
                isDrawingMode = state.isDrawingMode,
                drawingColor = state.drawingColor,
                drawingStrokeWidth = state.drawingStrokeWidth,
                onPathAdded = viewModel::addDrawingPath,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Multi-Cam Panel
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

        // Project Backup
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.CLOUD_BACKUP),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val lastBackupTime by viewModel.lastBackupTime.collectAsStateWithLifecycle()
            val backupSize by viewModel.backupEstimatedSize.collectAsStateWithLifecycle()
            val isExportingBackup by viewModel.isExportingBackup.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { viewModel.estimateBackupSize() }

            val backupImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) viewModel.importProjectBackup(uri)
            }

            CloudBackupPanel(
                lastBackupTime = lastBackupTime,
                estimatedSizeBytes = backupSize,
                isExporting = isExportingBackup,
                onExportBackup = viewModel::exportProjectBackup,
                onImportBackup = { backupImportLauncher.launch(arrayOf("*/*")) },
                onClose = viewModel::hideCloudBackup
            )
        }

        // Export Progress Overlay (floating card during export)
        ExportProgressOverlay(
            exportState = state.exportState,
            exportProgress = state.exportProgress,
            exportStartTime = state.exportStartTime,
            onCancel = viewModel::cancelExport,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 8.dp)
        )

        // First Run Tutorial
        AnimatedVisibility(
            visible = state.panels.isOpen(PanelId.TUTORIAL),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FirstRunTutorial(
                onComplete = viewModel::hideTutorial
            )
        }

        // Auto-save indicator (top-end overlay)
        AutoSaveIndicator(
            state = state.saveIndicator,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 8.dp)
        )

        // Text Template Gallery
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

        // Caption preview on video (always show when captions exist)
        if (allCaptions.isNotEmpty()) {
            CaptionPreviewOverlay(
                captions = allCaptions,
                currentTimeMs = playheadMs,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Motion path overlay on preview (when keyframe editor is open and position keyframes exist)
        if (state.panels.isOpen(PanelId.KEYFRAME_EDITOR) && state.selectedClipId != null) {
            val clip = selectedClip
            if (clip != null && clip.keyframes.any { it.property == KeyframeProperty.POSITION_X || it.property == KeyframeProperty.POSITION_Y }) {
                MotionPathOverlay(
                    keyframes = clip.keyframes,
                    clipDurationMs = clip.durationMs,
                    currentTimeMs = (playheadMs - clip.timelineStartMs).coerceAtLeast(0L),
                    previewWidth = 400f,
                    previewHeight = 225f,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Video scopes overlay
        AnimatedVisibility(
            visible = state.panels.isOpen(PanelId.SCOPES),
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            VideoScopesOverlay(
                frameBitmap = scopeFrame,
                activeScope = state.activeScopeType,
                onScopeChanged = viewModel::setScopeType,
                onClose = viewModel::toggleScopes,
                modifier = Modifier.padding(8.dp)
            )
        }

        // Clip Label Picker
        AnimatedVisibility(
            visible = showClipLabelPicker && state.selectedClipId != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(20f)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Mocha.Surface0),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.panel_editor_clip_label), color = Mocha.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showClipLabelPicker = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ClipLabel.entries.forEach { label ->
                            val selectedClip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
                            val isSelected = selectedClip?.clipLabel == label
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (label == ClipLabel.NONE) Mocha.Surface2
                                        else Color(label.argb)
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, Mocha.Text, CircleShape) else Modifier
                                    )
                                    .clickable {
                                        state.selectedClipId?.let { viewModel.setClipLabel(it, label) }
                                    }
                            ) {
                                if (label == ClipLabel.NONE) {
                                    Icon(Icons.Default.Close, null, tint = Mocha.Subtext0, modifier = Modifier.size(16.dp))
                                }
                                if (isSelected && label != ClipLabel.NONE) {
                                    Icon(Icons.Default.Check, null, tint = Mocha.Crust, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Toast messages
        state.toastMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                    .zIndex(10f),
                containerColor = Mocha.Surface0,
                contentColor = Mocha.Text,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(message, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    projectName: String,
    onRename: (String) -> Unit,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    selectedClipId: String?,
    onDelete: () -> Unit,
    confirmBeforeDelete: Boolean = true,
    onAddMedia: () -> Unit,
    onAddTrack: (TrackType) -> Unit,
    onExport: () -> Unit,
    onSaveTemplate: (String) -> Unit = {},
    editorMode: EditorMode = EditorMode.PRO,
    onToggleEditorMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showOverflow by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var showAddTrackMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.editor_delete), color = Mocha.Text) },
            text = { Text(stringResource(R.string.editor_delete_clip_message), color = Mocha.Subtext1) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) { Text(stringResource(R.string.editor_delete), color = Mocha.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.editor_cancel), color = Mocha.Subtext0)
                }
            },
            containerColor = Mocha.Mantle
        )
    }

    if (showSaveTemplateDialog) {
        var templateName by remember { mutableStateOf("$projectName Template") }
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            title = { Text(stringResource(R.string.editor_save_as_template), color = Mocha.Text) },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.editor_template_name)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Mocha.Text,
                        unfocusedTextColor = Mocha.Text,
                        cursorColor = Mocha.Mauve,
                        focusedBorderColor = Mocha.Mauve,
                        unfocusedBorderColor = Mocha.Surface1,
                        focusedLabelColor = Mocha.Mauve,
                        unfocusedLabelColor = Mocha.Subtext0
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (templateName.isNotBlank()) onSaveTemplate(templateName.trim())
                    showSaveTemplateDialog = false
                }) { Text(stringResource(R.string.editor_save), color = Mocha.Mauve) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplateDialog = false }) {
                    Text(stringResource(R.string.editor_cancel), color = Mocha.Subtext0)
                }
            },
            containerColor = Mocha.Mantle
        )
    }

    if (showRenameDialog) {
        var nameText by remember { mutableStateOf(projectName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.editor_rename_project), color = Mocha.Text) },
            text = {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Mocha.Text,
                        unfocusedTextColor = Mocha.Text,
                        cursorColor = Mocha.Mauve,
                        focusedBorderColor = Mocha.Mauve,
                        unfocusedBorderColor = Mocha.Surface1
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nameText.isNotBlank()) onRename(nameText.trim())
                    showRenameDialog = false
                }) { Text(stringResource(R.string.editor_save), color = Mocha.Mauve) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.editor_cancel), color = Mocha.Subtext0)
                }
            },
            containerColor = Mocha.Mantle
        )
    }

    Surface(
        color = Mocha.Crust,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = stringResource(R.string.editor_home),
                    tint = Mocha.Text,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.editor_undo),
                    tint = if (canUndo) Mocha.Text else Mocha.Surface2,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.editor_redo),
                    tint = if (canRedo) Mocha.Text else Mocha.Surface2,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Project name (tap to rename)
            Text(
                text = projectName,
                color = Mocha.Subtext1,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clickable { showRenameDialog = true }
            )

            // Mode toggle
            Text(
                text = editorMode.label,
                color = if (editorMode == EditorMode.PRO) Mocha.Mauve else Mocha.Green,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Mocha.Surface0)
                    .clickable { onToggleEditorMode() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            if (selectedClipId != null) {
                IconButton(
                    onClick = {
                        if (confirmBeforeDelete) showDeleteConfirmation = true
                        else onDelete()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.editor_delete),
                        tint = Mocha.Red,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showOverflow = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.editor_more),
                        tint = Mocha.Text,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_add_media)) },
                        onClick = {
                            showOverflow = false
                            onAddMedia()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.editor_add_media_cd))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_add_track)) },
                        onClick = {
                            showOverflow = false
                            showAddTrackMenu = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.VideoLibrary, contentDescription = stringResource(R.string.editor_add_track_cd))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_rename_project)) },
                        onClick = {
                            showOverflow = false
                            showRenameDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.editor_rename_project_cd))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_save_as_template)) },
                        onClick = {
                            showOverflow = false
                            showSaveTemplateDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.SaveAs, contentDescription = stringResource(R.string.editor_save_as_template_cd))
                        }
                    )
                }
                DropdownMenu(
                    expanded = showAddTrackMenu,
                    onDismissRequest = { showAddTrackMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_video_track)) },
                        onClick = { showAddTrackMenu = false; onAddTrack(TrackType.VIDEO) },
                        leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = stringResource(R.string.editor_video_track_cd)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_audio_track)) },
                        onClick = { showAddTrackMenu = false; onAddTrack(TrackType.AUDIO) },
                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = stringResource(R.string.editor_audio_track_cd)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_overlay_track)) },
                        onClick = { showAddTrackMenu = false; onAddTrack(TrackType.OVERLAY) },
                        leadingIcon = { Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.editor_overlay_track_cd)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_text_track)) },
                        onClick = { showAddTrackMenu = false; onAddTrack(TrackType.TEXT) },
                        leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = stringResource(R.string.editor_text_track_cd)) }
                    )
                }
            }

            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Mauve,
                    contentColor = Mocha.Crust
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(stringResource(R.string.editor_export), fontSize = 13.sp)
            }
        }
    }
}

