package com.novacut.editor.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.graphics.Brush
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.*
import com.novacut.editor.model.ClipLabel
import androidx.compose.ui.graphics.Color
import com.novacut.editor.ui.export.BatchExportPanel
import com.novacut.editor.ui.export.ExportSheet
import com.novacut.editor.ui.mediapicker.MediaPickerSheet
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.NovaCutDialogIcon
import com.novacut.editor.ui.theme.NovaCutPrimaryButton
import com.novacut.editor.ui.theme.NovaCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.novacut.editor.R
import java.io.File

@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val oneHandedMode by viewModel.oneHandedMode.collectAsStateWithLifecycle()
    val desktopOverride by viewModel.desktopOverride.collectAsStateWithLifecycle()
    val layoutMode = rememberLayoutMode(oneHandedMode, desktopOverride)
    val whisperState by viewModel.whisperModelState.collectAsStateWithLifecycle()
    val whisperProgress by viewModel.whisperDownloadProgress.collectAsStateWithLifecycle()
    val segmentationState by viewModel.segmentationModelState.collectAsStateWithLifecycle()
    val segmentationProgress by viewModel.segmentationDownloadProgress.collectAsStateWithLifecycle()
    val scopeFrame by viewModel.scopeFrame.collectAsStateWithLifecycle()
    val showLutPicker by viewModel.showLutPicker.collectAsStateWithLifecycle()
    val autoSaveTopPadding by animateDpAsState(
        targetValue = if (state.exportState == ExportState.EXPORTING) 120.dp else 48.dp,
        label = "autoSaveOverlayOffset"
    )
    val context = LocalContext.current
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceover()
        } else {
            viewModel.showToast(context.getString(R.string.audio_mic_permission_required))
        }
    }

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

    // Reset picker visibility when the user changes clip selection so a previously
    // open label picker doesn't reappear over a newly selected (or deselected) clip.
    var showClipLabelPicker by remember(state.selectedClipId) { mutableStateOf(false) }

    // Radial menu state
    var showRadialMenu by remember { mutableStateOf(false) }
    var radialMenuPosition by remember { mutableStateOf(Offset.Zero) }
    var isToolPanelExpanded by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val hasOpenPanel = state.panels.hasOpenPanel || state.selectedEffectId != null || state.editingTextOverlayId != null
    val isTutorialOpen = state.panels.isOpen(PanelId.TUTORIAL)
    val hasClipSelection = state.selectedClipIds.isNotEmpty()
    val isClipMode = state.selectedClipId != null
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val isCompactEditorHeight = screenHeightDp < 820
    val selectedPreviewHeight = when {
        !isClipMode -> 0.dp
        isToolPanelExpanded -> 154.dp
        isCompactEditorHeight -> 224.dp
        else -> 252.dp
    }
    val timelineMinHeight = when {
        isClipMode && isToolPanelExpanded -> 184.dp
        isClipMode && isCompactEditorHeight -> 204.dp
        isClipMode -> 224.dp
        else -> 240.dp
    }
    val timelineMaxHeight = when {
        isClipMode && isToolPanelExpanded -> 208.dp
        isClipMode && isCompactEditorHeight -> 248.dp
        isClipMode -> 284.dp
        else -> 330.dp
    }

    val allClips by remember(state.tracks) {
        derivedStateOf { state.tracks.flatMap { it.clips } }
    }
    val selectedClip by remember(allClips, state.selectedClipId) {
        derivedStateOf {
            state.selectedClipId?.let { id -> allClips.find { it.id == id } }
        }
    }
    val allCaptions by remember(allClips) {
        derivedStateOf {
            allClips.flatMap { clip ->
                clip.captions.map { caption ->
                    caption.copy(
                        startTimeMs = caption.startTimeMs + clip.timelineStartMs,
                        endTimeMs = caption.endTimeMs + clip.timelineStartMs
                    )
                }
            }
        }
    }
    val previewTrack by remember(state.tracks) {
        derivedStateOf {
            state.tracks
                .sortedBy { it.index }
                .firstOrNull {
                    (it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY) &&
                        it.isVisible &&
                        it.clips.isNotEmpty()
                }
        }
    }
    // previewTrackClips is keyed on previewTrack only — the sortedBy call above
    // was running on every playhead tick via the downstream derive chain below,
    // costing an O(n log n) sort 30x/sec during playback for a static clip list.
    val previewTrackClips by remember(previewTrack) {
        derivedStateOf { previewTrack?.clips?.sortedBy { it.timelineStartMs } ?: emptyList() }
    }
    // These two derives intentionally read `playheadMs` (the fast-path flow) so
    // they recompute every playhead tick, but because the sorted list is cached
    // above, each recompute is just a cheap linear scan over the sorted list.
    val previewClipAtPlayhead by remember(previewTrackClips) {
        derivedStateOf {
            previewTrackClips.firstOrNull { playheadMs in it.timelineStartMs until it.timelineEndMs }
        }
    }
    val nextPreviewClip by remember(previewTrackClips) {
        derivedStateOf { previewTrackClips.firstOrNull { it.timelineStartMs > playheadMs } }
    }
    val previewRecoveryTargetMs by remember(previewClipAtPlayhead, nextPreviewClip, previewTrackClips) {
        derivedStateOf {
            when {
                nextPreviewClip != null -> nextPreviewClip?.timelineStartMs
                previewClipAtPlayhead != null -> previewClipAtPlayhead?.timelineStartMs
                previewTrackClips.isNotEmpty() -> previewTrackClips.last().timelineStartMs
                else -> null
            }
        }
    }

    BackHandler(enabled = hasOpenPanel || state.currentTool != EditorTool.NONE || hasClipSelection || isClipMode) {
        when {
            hasOpenPanel -> viewModel.dismissAllPanels()
            state.currentTool != EditorTool.NONE -> viewModel.setTool(EditorTool.NONE)
            state.selectedClipIds.size > 1 -> viewModel.clearMultiSelect()
            state.selectedClipId != null -> viewModel.selectClip(null)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.selectedClipId) {
        if (state.selectedClipId == null) showClipLabelPicker = false
    }

    CompositionLocalProvider(LocalLayoutMode provides layoutMode) {
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
        // v3.69 DESKTOP layout — fixed 260 dp left sidebar (Media Bin + quick
        // actions + v3.69 hub entry). Absent on PHONE / ONE_HANDED so the
        // existing layout is untouched when no desktop surface is present.
        val desktopSidebarWidth = if (layoutMode == LayoutMode.DESKTOP) 260.dp else 0.dp
        if (layoutMode == LayoutMode.DESKTOP) {
            DesktopSidebar(
                viewModel = viewModel,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = desktopSidebarWidth)
                .then(if (isTutorialOpen) Modifier.clearAndSetSemantics { } else Modifier)
        ) {
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
                onDuplicateClip = viewModel::duplicateSelectedClip,
                onSplitClip = viewModel::splitClipAtPlayhead,
                onAddMedia = viewModel::showMediaPicker,
                onAddTrack = viewModel::addTrack,
                onExport = viewModel::showExportSheet,
                onSaveTemplate = viewModel::saveAsTemplate,
                editorMode = state.editorMode,
                onToggleEditorMode = viewModel::toggleEditorMode,
                onOpenScratchpad = viewModel::showScratchpad,
                onOpenV369Features = viewModel::showV369Features
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
                        colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(Radius.xxl)
                    ) {
                        Box(
                            modifier = Modifier.background(
                                Brush.verticalGradient(
                                    listOf(
                                        Mocha.PanelHighest.copy(alpha = 0.86f),
                                        Mocha.Panel
                                    )
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    color = Mocha.Mauve.copy(alpha = 0.14f),
                                    shape = CircleShape,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.22f))
                                ) {
                                    Icon(
                                        Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        tint = Mocha.Rosewater,
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .size(28.dp)
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    stringResource(R.string.editor_empty_title),
                                    color = Mocha.Text,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.editor_empty_body),
                                    color = Mocha.Subtext0,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(Spacing.lg))
                                NovaCutPrimaryButton(
                                    text = stringResource(R.string.editor_add_media),
                                    icon = Icons.Default.Add,
                                    onClick = viewModel::showMediaPicker,
                                    modifier = Modifier.widthIn(min = 180.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Preview panel with long-press radial menu
            if (hasClips || hasOpenPanel) Box(
                modifier = (if (isClipMode) {
                    Modifier.height(selectedPreviewHeight)
                } else {
                    Modifier.weight(1f)
                })
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
                    currentTimelineClip = previewClipAtPlayhead,
                    nextTimelineClip = nextPreviewClip,
                    jumpToContentMs = previewRecoveryTargetMs,
                    onJumpToContent = viewModel::seekTo,
                    onPreviewTransformStarted = { viewModel.beginTransformChange() },
                    onPreviewTransformEnded = { viewModel.endTransformChange() },
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
            if (state.selectedClipIds.size > 1) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.Transparent,
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Peach.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Mocha.Peach.copy(alpha = 0.18f),
                                        Mocha.PanelHighest.copy(alpha = 0.96f),
                                        Mocha.Panel.copy(alpha = 0.98f)
                                    )
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.editor_selection),
                                color = Mocha.Peach,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = stringResource(R.string.editor_selected_count, state.selectedClipIds.size),
                                color = Mocha.Text,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Mocha.Red.copy(alpha = 0.14f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Red.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable(onClick = viewModel::deleteMultiSelectedClips)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.editor_delete_selected),
                                    tint = Mocha.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.editor_delete),
                                    color = Mocha.Red,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Mocha.Surface0.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable(onClick = viewModel::clearMultiSelect)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_cancel),
                                    color = Mocha.Subtext0,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
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

            // Timeline
            if (!state.isTimelineCollapsed) {
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
                    onSlideEditStarted = viewModel::beginSlideEdit,
                    onSlideEditEnded = viewModel::endSlideEdit,
                    onSlipEditStarted = viewModel::beginSlipEdit,
                    onSlipEditEnded = viewModel::endSlipEdit,
                    onToggleTrackCollapsed = viewModel::toggleTrackCollapsed,
                    onToggleTrackWaveform = viewModel::toggleTrackWaveform,
                    onCollapseAllTracks = viewModel::collapseAllTracks,
                    onExpandAllTracks = viewModel::expandAllTracks,
                    onSetTrackHeight = viewModel::setTrackHeight,
                    onScrubStart = viewModel::beginScrub,
                    onScrubEnd = viewModel::endScrub,
                    onSplitAtPlayhead = viewModel::splitClipAtPlayhead,
                    onDeleteSelectedClip = viewModel::deleteSelectedClip,
                    engine = viewModel.engine,
                    modifier = Modifier.heightIn(min = timelineMinHeight, max = timelineMaxHeight)
                )
            }

            // Bottom tool area (PowerDirector-style tab bar + sub-menu grids)
            BottomToolArea(
                selectedClipId = state.selectedClipId,
                hasCopiedEffects = state.copiedEffects.isNotEmpty(),
                textOverlays = state.textOverlays,
                onEditTextOverlay = { id -> viewModel.editTextOverlay(id) },
                editorMode = state.editorMode,
                onExpandedChange = { expanded -> isToolPanelExpanded = expanded },
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
                        "back" -> {
                            viewModel.dismissAllPanels()
                            viewModel.selectClip(null)
                            viewModel.setTool(EditorTool.NONE)
                        }
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
                        "ai_hub" -> viewModel.showAiToolsPanel()
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
            Column {
                MiniPlayerBar(
                    isPlaying = state.isPlaying, playheadMs = playheadMs,
                    totalDurationMs = state.totalDurationMs,
                    onTogglePlayback = viewModel::togglePlayback, onSeek = viewModel::seekTo
                )
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
        }

        // Speed panel
        BottomSheetSlot(
            visible = state.currentTool == EditorTool.SPEED && state.selectedClipId != null,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column {
                MiniPlayerBar(
                    isPlaying = state.isPlaying, playheadMs = playheadMs,
                    totalDurationMs = state.totalDurationMs,
                    onTogglePlayback = viewModel::togglePlayback, onSeek = viewModel::seekTo
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

        // Transition picker
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.TRANSITION_PICKER),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column {
                MiniPlayerBar(
                    isPlaying = state.isPlaying, playheadMs = playheadMs,
                    totalDurationMs = state.totalDurationMs,
                    onTogglePlayback = viewModel::togglePlayback, onSeek = viewModel::seekTo
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

        // Voiceover recorder
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.VOICEOVER_RECORDER),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VoiceoverRecorder(
                isRecording = state.isRecordingVoiceover,
                recordingDurationMs = state.voiceoverDurationMs,
                onStartRecording = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.startVoiceover()
                    } else {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
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

        // Color Grading panel
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.COLOR_GRADING),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column {
                MiniPlayerBar(
                    isPlaying = state.isPlaying, playheadMs = playheadMs,
                    totalDurationMs = state.totalDurationMs,
                    onTogglePlayback = viewModel::togglePlayback, onSeek = viewModel::seekTo
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

        // Audio Mixer panel
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.AUDIO_MIXER),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column {
            MiniPlayerBar(
                isPlaying = state.isPlaying, playheadMs = playheadMs,
                totalDurationMs = state.totalDurationMs,
                onTogglePlayback = viewModel::togglePlayback, onSeek = viewModel::seekTo
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
            Column {
            MiniPlayerBar(
                isPlaying = state.isPlaying, playheadMs = playheadMs,
                totalDurationMs = state.totalDurationMs,
                onTogglePlayback = viewModel::togglePlayback, onSeek = viewModel::seekTo
            )
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
                    onClose = viewModel::hideSpeedCurveEditor,
                    onSpeedDragStarted = viewModel::beginSpeedChange,
                    onSpeedDragEnded = viewModel::endSpeedChange
                )
            }
            }
        }

        // Mask Editor panel
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.MASK_EDITOR),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column {
                MiniPlayerBar(
                    isPlaying = state.isPlaying, playheadMs = playheadMs,
                    totalDurationMs = state.totalDurationMs,
                    onTogglePlayback = viewModel::togglePlayback, onSeek = viewModel::seekTo
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
                    onTransformEnded = viewModel::endTransformChange,
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

        // Scratchpad
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

        // Recovery dialog — shown on editor open when auto-save data was restored.
        // Modal (no back-press, no outside-tap dismissal) so the user must make
        // an explicit choice. Previously `onDismissRequest` treated "tap outside"
        // as "Keep recovered", which silently overrode users who meant to discard.
        if (state.panels.isOpen(PanelId.RECOVERY_DIALOG)) {
            AlertDialog(
                onDismissRequest = { /* forced choice — handled by the two buttons */ },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                ),
                icon = {
                    NovaCutDialogIcon(
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
                    Text(
                        text = stringResource(R.string.recovery_message),
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    NovaCutPrimaryButton(
                        text = stringResource(R.string.recovery_keep),
                        onClick = { viewModel.dismissRecoveryDialog(recover = true) },
                        icon = Icons.Default.Check
                    )
                },
                dismissButton = {
                    NovaCutSecondaryButton(
                        text = stringResource(R.string.recovery_discard),
                        onClick = { viewModel.dismissRecoveryDialog(recover = false) },
                        contentColor = Mocha.Red,
                        icon = Icons.Default.Delete
                    )
                },
                containerColor = Mocha.PanelHighest,
                titleContentColor = Mocha.Text,
                textContentColor = Mocha.Subtext0,
                shape = RoundedCornerShape(Radius.xxl)
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
                onRelinkMedia = { _ ->
                    viewModel.showToast(context.getString(R.string.editor_media_relink_unavailable))
                },
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

        // v3.69 Features Hub
        BottomSheetSlot(
            visible = state.panels.isOpen(PanelId.V369_FEATURES),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            V369FeaturesPanel(
                viewModel = viewModel,
                onDismiss = viewModel::hideV369Features
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
            visible = isTutorialOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FirstRunTutorial(
                onComplete = viewModel::hideTutorial,
                modifier = Modifier.zIndex(10f)
            )
        }

        // Auto-save indicator (top-end overlay)
        AutoSaveIndicator(
            state = state.saveIndicator,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = autoSaveTopPadding, end = 8.dp)
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
                colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
                border = BorderStroke(1.dp, Mocha.CardStrokeStrong.copy(alpha = 0.86f)),
                shape = RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Mocha.PanelHighest.copy(alpha = 0.86f),
                                    Mocha.Panel
                                )
                            )
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.panel_editor_clip_label),
                                color = Mocha.Text,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.clip_label_picker_description),
                                color = Mocha.Subtext0,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { showClipLabelPicker = false }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.cd_close_color_grading), tint = Mocha.Subtext0, modifier = Modifier.size(20.dp))
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val labelClip = selectedClip
                        ClipLabel.entries.forEach { label ->
                            val isSelected = labelClip?.clipLabel == label
                            val labelName = if (label == ClipLabel.NONE) {
                                stringResource(R.string.clip_label_none)
                            } else {
                                label.displayName
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(TouchTarget.minimum)
                                    .clip(CircleShape)
                                    .background(
                                        if (label == ClipLabel.NONE) Mocha.Surface2
                                        else Color(label.argb)
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, Mocha.Text, CircleShape)
                                        else Modifier.border(1.dp, Mocha.CardStroke.copy(alpha = 0.7f), CircleShape)
                                    )
                                    .semantics { contentDescription = labelName }
                                    .clickable(role = Role.Button) {
                                        state.selectedClipId?.let { viewModel.setClipLabel(it, label) }
                                    }
                            ) {
                                if (label == ClipLabel.NONE) {
                                    Icon(Icons.Default.Close, labelName, tint = Mocha.Subtext0, modifier = Modifier.size(16.dp))
                                }
                                if (isSelected && label != ClipLabel.NONE) {
                                    Icon(Icons.Default.Check, labelName, tint = Mocha.Crust, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Toast messages — animated, severity-aware Mocha snackbar.
        PremiumSnackbarHost(
            message = state.toastMessage,
            severity = state.toastSeverity,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                .zIndex(10f)
        )

        // Bulk-undo prompt. Raised by ClipEditingDelegate when ≥3 deletes
        // happen in 10s — offers a one-shot Undo path without making the
        // user hunt for the overflow menu. Keyed on `id` so re-raising after
        // a fresh burst actually re-triggers the LaunchedEffect timer.
        val bulkPrompt = state.bulkUndoPrompt
        if (bulkPrompt != null) {
            LaunchedEffect(bulkPrompt.id) {
                kotlinx.coroutines.delay(8000)
                viewModel.dismissBulkUndoPrompt()
            }
            androidx.compose.material3.Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                    .zIndex(11f),
                containerColor = Mocha.PanelHighest,
                contentColor = Mocha.Text,
                actionContentColor = Mocha.Peach,
                action = {
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.undo()
                        viewModel.dismissBulkUndoPrompt()
                    }) {
                        Text(text = stringResource(R.string.bulk_undo_action))
                    }
                },
                dismissAction = {
                    androidx.compose.material3.IconButton(onClick = { viewModel.dismissBulkUndoPrompt() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.bulk_undo_dismiss_cd),
                            tint = Mocha.Subtext0
                        )
                    }
                },
                shape = RoundedCornerShape(Radius.xl)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = Mocha.Peach,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.bulk_undo_message, bulkPrompt.count),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
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
    modifier: Modifier = Modifier,
    confirmBeforeDelete: Boolean = true,
    onDuplicateClip: () -> Unit,
    onSplitClip: () -> Unit,
    onAddMedia: () -> Unit,
    onAddTrack: (TrackType) -> Unit,
    onExport: () -> Unit,
    onSaveTemplate: (String) -> Unit = {},
    editorMode: EditorMode = EditorMode.PRO,
    onToggleEditorMode: () -> Unit = {},
    onOpenScratchpad: () -> Unit = {},
    onOpenV369Features: () -> Unit = {}
) {
    var showOverflow by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var showAddTrackMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    // v3.69: honour layout mode. ONE_HANDED forces compact even on wider
    // screens (user opted in). DESKTOP leaves the bar at its generous size
    // — we would rather pad out on large screens than fake compact.
    val layoutMode = LocalLayoutMode.current
    val isCompactBar = when (layoutMode) {
        LayoutMode.ONE_HANDED -> true
        LayoutMode.DESKTOP -> false
        LayoutMode.PHONE -> LocalConfiguration.current.screenWidthDp < 430
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = {
                NovaCutDialogIcon(
                    icon = Icons.Default.Delete,
                    accent = Mocha.Red
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.editor_delete),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.editor_delete_clip_message),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                NovaCutSecondaryButton(
                    text = stringResource(R.string.editor_delete),
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    icon = Icons.Default.Delete,
                    contentColor = Mocha.Red
                )
            },
            dismissButton = {
                NovaCutSecondaryButton(
                    text = stringResource(R.string.editor_cancel),
                    onClick = { showDeleteConfirmation = false }
                )
            },
            containerColor = Mocha.PanelHighest,
            titleContentColor = Mocha.Text,
            textContentColor = Mocha.Subtext0,
            shape = RoundedCornerShape(Radius.xxl)
        )
    }

    if (showSaveTemplateDialog) {
        var templateName by remember(projectName) { mutableStateOf("$projectName Template") }
        val trimmedTemplateName = templateName.trim()
        val canSaveTemplate = trimmedTemplateName.isNotBlank()
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            icon = {
                NovaCutDialogIcon(
                    icon = Icons.Default.Save,
                    accent = Mocha.Mauve
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.editor_save_as_template),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    label = { Text(stringResource(R.string.editor_template_name)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Mocha.Text,
                        unfocusedTextColor = Mocha.Text,
                        cursorColor = Mocha.Mauve,
                        focusedBorderColor = Mocha.Mauve,
                        unfocusedBorderColor = Mocha.CardStroke,
                        focusedLabelColor = Mocha.Mauve,
                        unfocusedLabelColor = Mocha.Subtext0,
                        focusedContainerColor = Mocha.PanelRaised,
                        unfocusedContainerColor = Mocha.PanelRaised
                    )
                )
            },
            confirmButton = {
                NovaCutPrimaryButton(
                    text = stringResource(R.string.editor_save),
                    onClick = {
                        onSaveTemplate(trimmedTemplateName)
                        showSaveTemplateDialog = false
                    },
                    enabled = canSaveTemplate,
                    icon = Icons.Default.Check
                )
            },
            dismissButton = {
                NovaCutSecondaryButton(
                    text = stringResource(R.string.editor_cancel),
                    onClick = { showSaveTemplateDialog = false }
                )
            },
            containerColor = Mocha.PanelHighest,
            titleContentColor = Mocha.Text,
            textContentColor = Mocha.Subtext0,
            shape = RoundedCornerShape(Radius.xxl)
        )
    }

    if (showRenameDialog) {
        var nameText by remember(projectName) { mutableStateOf(projectName) }
        val trimmedNameText = nameText.trim()
        val canSubmitRename = trimmedNameText.isNotBlank() && trimmedNameText != projectName
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            icon = {
                NovaCutDialogIcon(
                    icon = Icons.Default.Edit,
                    accent = Mocha.Rosewater
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.editor_rename_project),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    label = { Text(stringResource(R.string.projects_rename_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Mocha.Text,
                        unfocusedTextColor = Mocha.Text,
                        cursorColor = Mocha.Mauve,
                        focusedBorderColor = Mocha.Mauve,
                        // Normalized to match the editor's other input borders so the rename
                        // dialog feels like part of the same surface system rather than a fork.
                        unfocusedBorderColor = Mocha.CardStroke,
                        focusedLabelColor = Mocha.Mauve,
                        unfocusedLabelColor = Mocha.Subtext0,
                        focusedContainerColor = Mocha.PanelRaised,
                        unfocusedContainerColor = Mocha.PanelRaised
                    )
                )
            },
            confirmButton = {
                NovaCutPrimaryButton(
                    text = stringResource(R.string.editor_save),
                    onClick = {
                        onRename(trimmedNameText)
                        showRenameDialog = false
                    },
                    enabled = canSubmitRename,
                    icon = Icons.Default.Check
                )
            },
            dismissButton = {
                NovaCutSecondaryButton(
                    text = stringResource(R.string.editor_cancel),
                    onClick = { showRenameDialog = false }
                )
            },
            containerColor = Mocha.PanelHighest,
            titleContentColor = Mocha.Text,
            textContentColor = Mocha.Subtext0,
            shape = RoundedCornerShape(Radius.xxl)
        )
    }

    Surface(
        color = Mocha.Panel,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isCompactBar) 58.dp else 62.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Mocha.PanelHighest.copy(alpha = 0.9f),
                            Mocha.Panel,
                            Mocha.Mantle
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isCompactBar) 8.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Mocha.PanelHighest,
                    shape = RoundedCornerShape(if (isCompactBar) 16.dp else 18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(if (isCompactBar) 36.dp else 38.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Mocha.Text,
                            modifier = Modifier.size(if (isCompactBar) 18.dp else 20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(if (isCompactBar) 8.dp else 10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = projectName,
                        color = Mocha.Text,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        onClick = onToggleEditorMode,
                        color = if (editorMode == EditorMode.PRO) Mocha.Mauve.copy(alpha = 0.14f) else Mocha.Sapphire.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (editorMode == EditorMode.PRO) {
                                Mocha.Mauve.copy(alpha = 0.2f)
                            } else {
                                Mocha.Sapphire.copy(alpha = 0.2f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = if (isCompactBar) 7.dp else 8.dp,
                                vertical = if (isCompactBar) 3.dp else 4.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (editorMode == EditorMode.PRO) Mocha.Rosewater else Mocha.Sapphire,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (editorMode == EditorMode.PRO) {
                                    stringResource(R.string.settings_mode_pro)
                                } else {
                                    stringResource(R.string.settings_mode_easy)
                                },
                                color = if (editorMode == EditorMode.PRO) Mocha.Rosewater else Mocha.Sapphire,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Surface(
                    color = Mocha.PanelHighest,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onUndo,
                            enabled = canUndo,
                            modifier = Modifier.size(if (isCompactBar) 32.dp else 34.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.editor_undo),
                                tint = if (canUndo) Mocha.Text else Mocha.Surface2,
                                modifier = Modifier.size(if (isCompactBar) 16.dp else 18.dp)
                            )
                        }
                        IconButton(
                            onClick = onRedo,
                            enabled = canRedo,
                            modifier = Modifier.size(if (isCompactBar) 32.dp else 34.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = stringResource(R.string.editor_redo),
                                tint = if (canRedo) Mocha.Text else Mocha.Surface2,
                                modifier = Modifier.size(if (isCompactBar) 16.dp else 18.dp)
                            )
                        }
                    }
                }

                if (selectedClipId != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Mocha.Red.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (confirmBeforeDelete) showDeleteConfirmation = true
                                else onDelete()
                            },
                            modifier = Modifier.size(if (isCompactBar) 32.dp else 34.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.editor_delete),
                                tint = Mocha.Red,
                                modifier = Modifier.size(if (isCompactBar) 16.dp else 18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box {
                    Surface(
                        color = Mocha.PanelHighest,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke)
                    ) {
                        IconButton(
                            onClick = { showOverflow = true },
                            modifier = Modifier.size(if (isCompactBar) 36.dp else 38.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.editor_more),
                                tint = Mocha.Text,
                                modifier = Modifier.size(if (isCompactBar) 18.dp else 20.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                        containerColor = Mocha.PanelHighest
                    ) {
                        if (selectedClipId != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tool_duplicate)) },
                                onClick = {
                                    showOverflow = false
                                    onDuplicateClip()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.tool_duplicate))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tool_split)) },
                                onClick = {
                                    showOverflow = false
                                    onSplitClip()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCut, contentDescription = stringResource(R.string.tool_split))
                                }
                            )
                        } else {
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scratchpad_menu_label)) },
                                onClick = {
                                    showOverflow = false
                                    onOpenScratchpad()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Notes,
                                        contentDescription = stringResource(R.string.scratchpad_menu_label)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.v369_features_label)) },
                                onClick = {
                                    showOverflow = false
                                    onOpenV369Features()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = stringResource(R.string.v369_features_label),
                                        tint = Mocha.Mauve
                                    )
                                }
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showAddTrackMenu,
                        onDismissRequest = { showAddTrackMenu = false },
                        containerColor = Mocha.PanelHighest
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

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mocha.Rosewater,
                        contentColor = Mocha.Midnight
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = if (isCompactBar) 12.dp else 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(if (isCompactBar) 36.dp else 38.dp)
                ) {
                    Icon(
                        Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(if (isCompactBar) 16.dp else 17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_export), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

