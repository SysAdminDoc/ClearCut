@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.novacut.editor.ui.editor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.*
import com.novacut.editor.ui.NovaCutTestTags
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.NovaCutDialogIcon
import com.novacut.editor.ui.theme.NovaCutPrimaryButton
import com.novacut.editor.ui.theme.NovaCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.novacut.editor.R
import java.io.File

private const val EXPORT_NOTIFICATION_PERMISSION_PREFS = "export_notification_permission"
private const val EXPORT_NOTIFICATION_PERMISSION_HANDLED = "handled"

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
    val isTabletopPosture = LocalTabletopPosture.current
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
    var pendingRelinkUri by remember { mutableStateOf<Uri?>(null) }
    val mediaRelinkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val oldUri = pendingRelinkUri
        pendingRelinkUri = null
        if (uri != null && oldUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.w("EditorScreen", "Could not persist relink media permission", e)
            }
            viewModel.relinkMedia(oldUri, uri)
        }
    }

    // Sticker image import — direct Photo Picker (ImageOnly) so users don't have
    // to navigate the full MediaPicker just for a single overlay image. Selected
    // Photos compatibility is automatic with PickVisualMedia on API 33+.
    val stickerImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addImageOverlay(uri, com.novacut.editor.model.ImageOverlayType.STICKER)
        }
    }
    val exportNotificationPrefs = remember(context) {
        context.getSharedPreferences(EXPORT_NOTIFICATION_PERMISSION_PREFS, Context.MODE_PRIVATE)
    }
    var pendingNotificationExportDir by remember { mutableStateOf<File?>(null) }
    var showExportNotificationPermissionDialog by remember { mutableStateOf(false) }

    fun markExportNotificationPromptHandled() {
        exportNotificationPrefs.edit()
            .putBoolean(EXPORT_NOTIFICATION_PERMISSION_HANDLED, true)
            .apply()
    }

    fun startPendingNotificationExport(showFallbackMessage: Boolean) {
        val outputDir = pendingNotificationExportDir ?: return
        pendingNotificationExportDir = null
        if (showFallbackMessage) {
            viewModel.showToast(context.getString(R.string.export_notification_permission_fallback))
        }
        viewModel.startExport(outputDir)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        markExportNotificationPromptHandled()
        startPendingNotificationExport(showFallbackMessage = !granted)
    }

    fun startExportWithNotificationPermission(outputDir: File) {
        val notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val decision = decideExportNotificationPermission(
            sdkInt = Build.VERSION.SDK_INT,
            notificationPermissionGranted = notificationPermissionGranted,
            promptAlreadyHandled = exportNotificationPrefs.getBoolean(
                EXPORT_NOTIFICATION_PERMISSION_HANDLED,
                false
            )
        )

        if (decision.shouldPrompt) {
            pendingNotificationExportDir = outputDir
            showExportNotificationPermissionDialog = true
        } else {
            viewModel.startExport(outputDir)
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
    var isTimelineTrimGestureActive by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val hasOpenPanel = state.panels.hasOpenPanel || state.selectedEffectId != null || state.editingTextOverlayId != null || state.ai.cutAssistantReview != null
    val isTutorialOpen = state.panels.isOpen(PanelId.TUTORIAL)
    val hasClipSelection = state.selectedClipIds.isNotEmpty()
    val isClipMode = state.selectedClipId != null
    val configuration = LocalConfiguration.current
    val adaptiveLayoutDecision = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        layoutMode,
        isTabletopPosture
    ) {
        AdaptiveEditorLayoutPolicy.decide(
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp,
            isTabletop = isTabletopPosture,
            desktopLike = layoutMode == LayoutMode.DESKTOP
        )
    }
    val screenHeightDp = configuration.screenHeightDp
    val isCompactEditorHeight = adaptiveLayoutDecision.compactTimeline || screenHeightDp < 820
    // Pane sizing reacts only to the deliberate TRIM tool, never to the live
    // edge-drag gesture — resizing panes mid-gesture moves the trim handle
    // under the user's finger and opens dead space around the tool rail.
    val isTrimToolActive = state.currentTool == EditorTool.TRIM
    val isTrimInteractionActive = isTrimToolActive || isTimelineTrimGestureActive
    val isBottomToolPanelExpanded = isToolPanelExpanded && !isTrimToolActive
    val previewMinHeight = when {
        adaptiveLayoutDecision.paneMode == AdaptiveEditorLayoutPolicy.PaneMode.TABLETOP_SPLIT -> 240.dp
        adaptiveLayoutDecision.paneMode == AdaptiveEditorLayoutPolicy.PaneMode.THREE_PANE -> 220.dp
        adaptiveLayoutDecision.paneMode == AdaptiveEditorLayoutPolicy.PaneMode.TWO_PANE -> 200.dp
        isClipMode && isTrimToolActive -> 220.dp
        isClipMode && isCompactEditorHeight -> 210.dp
        isClipMode -> 230.dp
        else -> 180.dp
    }
    // Height available to the timeline after reserving the top bar (~64dp), a
    // usable preview, and the tool rail (96dp compact / 244dp with a
    // sub-menu open) — keeps a tall track stack from starving the preview and
    // keeps the fixed minimums below from overflowing short screens.
    val timelineHeightBudget = (
        screenHeightDp.dp - 64.dp - previewMinHeight -
            (if (isBottomToolPanelExpanded) 244.dp else 96.dp)
        ).coerceAtLeast(160.dp)
    val timelineMinHeight = when {
        adaptiveLayoutDecision.paneMode == AdaptiveEditorLayoutPolicy.PaneMode.TABLETOP_SPLIT -> 280.dp
        adaptiveLayoutDecision.paneMode == AdaptiveEditorLayoutPolicy.PaneMode.THREE_PANE -> 280.dp
        adaptiveLayoutDecision.paneMode == AdaptiveEditorLayoutPolicy.PaneMode.TWO_PANE -> 252.dp
        isClipMode && isTrimToolActive && isCompactEditorHeight -> 240.dp
        isClipMode && isTrimToolActive -> 280.dp
        isClipMode && isBottomToolPanelExpanded -> 220.dp
        isClipMode && isCompactEditorHeight -> 220.dp
        isClipMode -> 280.dp
        else -> 240.dp
    }.coerceAtMost(timelineHeightBudget)
    val timelineMaxHeight = when {
        adaptiveLayoutDecision.paneMode == AdaptiveEditorLayoutPolicy.PaneMode.TABLETOP_SPLIT -> 380.dp
        adaptiveLayoutDecision.paneMode == AdaptiveEditorLayoutPolicy.PaneMode.THREE_PANE -> 420.dp
        adaptiveLayoutDecision.paneMode == AdaptiveEditorLayoutPolicy.PaneMode.TWO_PANE -> 360.dp
        isClipMode && isTrimToolActive && isCompactEditorHeight -> 300.dp
        isClipMode && isTrimToolActive -> 360.dp
        isClipMode && isBottomToolPanelExpanded -> 260.dp
        isClipMode && isCompactEditorHeight -> 280.dp
        isClipMode -> 390.dp
        else -> 330.dp
    }.coerceIn(timelineMinHeight, timelineHeightBudget)
    val useEmbeddedExportPane = state.panels.isOpen(PanelId.EXPORT_SHEET) &&
        adaptiveLayoutDecision.preferEmbeddedExportPane
    val embeddedExportPaneWidth = when {
        configuration.screenWidthDp >= 1280 -> 500.dp
        configuration.screenWidthDp >= 1120 -> 460.dp
        else -> 420.dp
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

    BackHandler(
        enabled = hasOpenPanel ||
            state.currentTool != EditorTool.NONE ||
            hasClipSelection ||
            isClipMode ||
            state.compoundNavDepth > 0,
    ) {
        when {
            hasOpenPanel -> viewModel.dismissAllPanels()
            state.currentTool != EditorTool.NONE -> viewModel.setTool(EditorTool.NONE)
            state.selectedClipIds.size > 1 -> viewModel.clearMultiSelect()
            state.selectedClipId != null -> viewModel.selectClip(null)
            // Tier C.13 — predictive back pops one compound nesting level
            // when no other in-context action consumes the gesture. Root
            // (depth 0) falls through to the system back-to-home animation
            // because the BackHandler's `enabled` predicate stops gating it.
            state.compoundNavDepth > 0 -> viewModel.exitCompoundLevel()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.selectedClipId) {
        if (state.selectedClipId == null) showClipLabelPicker = false
    }

    if (showExportNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportNotificationPermissionDialog = false
                markExportNotificationPromptHandled()
                startPendingNotificationExport(showFallbackMessage = true)
            },
            title = { Text(stringResource(R.string.export_notification_permission_title)) },
            text = { Text(stringResource(R.string.export_notification_permission_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportNotificationPermissionDialog = false
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                ) {
                    Text(stringResource(R.string.export_notification_permission_allow))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportNotificationPermissionDialog = false
                        markExportNotificationPromptHandled()
                        startPendingNotificationExport(showFallbackMessage = true)
                    }
                ) {
                    Text(stringResource(R.string.export_notification_permission_not_now))
                }
            }
        )
    }

    fun nudgeSelectedClip(deltaMs: Long): Boolean {
        val selectedClipId = state.selectedClipId ?: return false
        viewModel.beginSlideEdit()
        viewModel.slideClip(selectedClipId, deltaMs)
        viewModel.endSlideEdit()
        return true
    }

    CompositionLocalProvider(LocalLayoutMode provides layoutMode) {
    Box(modifier = Modifier
        .fillMaxSize()
        .testTag(NovaCutTestTags.EDITOR_SCREEN)
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
                        if (state.selectedClipId != null) {
                            viewModel.deleteSelectedClip()
                            true
                        } else false
                    }
                    // M = add marker
                    event.key == Key.M && !event.isCtrlPressed -> { viewModel.addTimelineMarker(); true }
                    // Z = undo (Ctrl+Z)
                    event.key == Key.Z && event.isCtrlPressed && !event.isShiftPressed -> { viewModel.undo(); true }
                    // Shift+Z or Ctrl+Y = redo
                    (event.key == Key.Z && event.isCtrlPressed && event.isShiftPressed) ||
                    (event.key == Key.Y && event.isCtrlPressed) -> { viewModel.redo(); true }
                    // Shift+Arrow = nudge selected clip by 100 ms; Ctrl+Shift = 1 second.
                    event.key == Key.DirectionLeft && event.isShiftPressed && state.selectedClipId != null -> {
                        nudgeSelectedClip(if (event.isCtrlPressed) -1000L else -100L)
                    }
                    event.key == Key.DirectionRight && event.isShiftPressed && state.selectedClipId != null -> {
                        nudgeSelectedClip(if (event.isCtrlPressed) 1000L else 100L)
                    }
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
                .padding(
                    start = desktopSidebarWidth,
                    end = if (useEmbeddedExportPane) embeddedExportPaneWidth else 0.dp
                )
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
                                    modifier = Modifier
                                        .widthIn(min = 180.dp)
                                        .testTag(NovaCutTestTags.EDITOR_EMPTY_ADD_MEDIA)
                                )
                            }
                        }
                    }
                }
            }

            // Preview panel with long-press radial menu. The preview is the
            // ONLY flexible element in this column: it absorbs whatever the
            // wrap-content timeline and tool rail leave over, so the rail
            // always hugs the bottom edge with no dead panel space.
            if (hasClips || hasOpenPanel) Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = previewMinHeight)
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
                    imageOverlays = state.imageOverlays,
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
                        hasOpenableCompoundClipSelected = selectedClip?.isCompound == true,
                        onAction = { actionId ->
                            showRadialMenu = false
                            when (actionId) {
                                "open_compound" -> selectedClip?.id?.let { viewModel.openCompoundClip(it) }
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
                            shape = RoundedCornerShape(10.dp),
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
                            shape = RoundedCornerShape(10.dp),
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

            if (state.compoundNavDepth > 0) {
                CompoundNavBreadcrumb(
                    breadcrumbText = state.compoundBreadcrumbText,
                    onExit = viewModel::exitCompoundLevel,
                )
            }

            val shouldShowTimeline = !state.isTimelineCollapsed ||
                isClipMode ||
                isTrimInteractionActive

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Timeline — wraps its track stack between min/max bounds so
                // the tool rail below stays snug against the timeline content.
                if (shouldShowTimeline) {
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
                        onTrimDragStarted = {
                            isTimelineTrimGestureActive = true
                            viewModel.beginTrim()
                        },
                        onTrimDragEnded = {
                            viewModel.endTrim()
                            isTimelineTrimGestureActive = false
                        },
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
                        onOpenCompoundClip = viewModel::openCompoundClip,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = timelineMinHeight, max = timelineMaxHeight)
                    )
                }

                // Bottom tool area (PowerDirector-style tab bar + sub-menu grids)
                BottomToolArea(
                    selectedClipId = state.selectedClipId,
                    hasCopiedEffects = state.copiedEffects.isNotEmpty(),
                    textOverlays = state.textOverlays,
                    onEditTextOverlay = { id -> viewModel.editTextOverlay(id) },
                    editorMode = state.editorMode,
                    compactLocked = isTrimToolActive,
                    onExpandedChange = { expanded ->
                        isToolPanelExpanded = expanded
                    },
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
                        // Filler cleanup now routes through the unified Cut
                        // Assistant Review panel and its silence / filler
                        // filter chips.
                        "filler_removal" -> viewModel.proposeCutsForReview()
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
                        "cut_assistant" -> viewModel.proposeCutsForReview()
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
        }

        // Bottom sheets / overlays
        EditorPrimaryPanelHost(
            state = state,
            viewModel = viewModel,
            selectedClip = selectedClip,
            playheadMs = playheadMs,
            useEmbeddedExportPane = useEmbeddedExportPane,
            embeddedExportPaneWidth = embeddedExportPaneWidth,
            context = context,
            onStartExportRequested = ::startExportWithNotificationPermission,
            onStartVoiceoverRecording = {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.startVoiceover()
                } else {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )

        EditorAiPanelHost(
            state = state,
            viewModel = viewModel,
            whisperModelState = whisperState,
            whisperDownloadProgress = whisperProgress,
            segmentationModelState = segmentationState,
            segmentationDownloadProgress = segmentationProgress
        )

        EditorClipAdjustmentPanelHost(
            state = state,
            viewModel = viewModel,
            selectedClip = selectedClip,
            playheadMs = playheadMs,
            context = context
        )

        EditorUtilityPanelHost(
            state = state,
            viewModel = viewModel,
            selectedClip = selectedClip,
            playheadMs = playheadMs,
            context = context,
            onRelinkMedia = { uri ->
                pendingRelinkUri = uri
                mediaRelinkLauncher.launch(arrayOf("video/*", "audio/*", "image/*"))
            },
            onImportStickerFromGallery = {
                stickerImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        EditorOverlayHost(
            state = state,
            viewModel = viewModel,
            selectedClip = selectedClip,
            allCaptions = allCaptions,
            playheadMs = playheadMs,
            scopeFrame = scopeFrame,
            showClipLabelPicker = showClipLabelPicker,
            onClipLabelPickerDismiss = { showClipLabelPicker = false },
            useEmbeddedExportPane = useEmbeddedExportPane,
            embeddedExportPaneWidth = embeddedExportPaneWidth,
            autoSaveTopPadding = autoSaveTopPadding,
            isTutorialOpen = isTutorialOpen
        )
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
                        modifier = Modifier
                            .size(if (isCompactBar) 36.dp else 38.dp)
                            .testTag(NovaCutTestTags.EDITOR_BACK)
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
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = projectName,
                        color = Mocha.Text,
                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.offset(y = 7.dp)
                    )
                    Surface(
                        onClick = onToggleEditorMode,
                        color = if (editorMode == EditorMode.PRO) Mocha.Mauve.copy(alpha = 0.14f) else Mocha.Sapphire.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
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
                    modifier = Modifier
                        .height(if (isCompactBar) 36.dp else 38.dp)
                        .testTag(NovaCutTestTags.EDITOR_EXPORT)
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
