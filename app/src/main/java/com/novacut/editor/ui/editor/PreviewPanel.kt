package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.SubcomposeAsyncImage
import com.novacut.editor.R
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.TextAlignment
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.ui.theme.ClearCutChromeIconButton
import com.novacut.editor.ui.theme.Radius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PreviewPanel(
    engine: VideoEngine,
    playheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    isPlaybackRequested: Boolean,
    modifier: Modifier = Modifier,
    isLooping: Boolean = false,
    aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    frameRate: Int = 30,
    onTogglePlayback: () -> Unit,
    onToggleLoop: () -> Unit = {},
    onSeek: (Long) -> Unit,
    onPlayerViewReady: (PlayerView) -> Unit = {},
    selectedClipId: String? = null,
    currentTimelineClip: Clip? = null,
    nextTimelineClip: Clip? = null,
    imageOverlays: List<ImageOverlay> = emptyList(),
    textOverlays: List<TextOverlay> = emptyList(),
    onOpenMediaManager: () -> Unit = {},
    jumpToContentMs: Long? = null,
    onJumpToContent: (Long) -> Unit = {},
    onPreviewTransformStarted: () -> Unit = {},
    onPreviewTransformEnded: () -> Unit = {},
    onPreviewTransformChanged: (dx: Float, dy: Float, scaleChange: Float, rotationChange: Float) -> Unit = { _, _, _, _ -> },
    showScopesButton: Boolean = false,
    onToggleScopes: () -> Unit = {},
    showCompositionGuides: Boolean = false,
    onToggleCompositionGuides: () -> Unit = {},
    isSplitPreviewEnabled: Boolean = false,
    onToggleSplitPreview: () -> Unit = {},
    hasActiveEffects: Boolean = false
) {
    val semanticColors = LocalClearCutColors.current
    val currentTimelineUri = currentTimelineClip?.let { it.proxyUri ?: it.sourceUri }
    val currentClipIsStillImage = remember(currentTimelineUri) {
        currentTimelineUri?.let(engine::isStillImage) == true
    }
    val canTransformPreview = selectedClipId != null && currentTimelineClip?.id == selectedClipId
    val showGapState = totalDurationMs > 0L && currentTimelineClip == null && !isPlaying
    val showGapPlaybackFrame = totalDurationMs > 0L && currentTimelineClip == null && isPlaying
    val activeImageOverlays = remember(imageOverlays, playheadMs) {
        imageOverlays.filter { overlay ->
            playheadMs >= overlay.startTimeMs && playheadMs <= overlay.endTimeMs
        }
    }
    val activeTextOverlays = remember(textOverlays, playheadMs) {
        activePreviewTextOverlays(textOverlays, playheadMs)
    }

    val frameDurationMs = remember(frameRate) {
        (1_000L / frameRate.coerceAtLeast(1)).coerceAtLeast(1L)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(semanticColors.background)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = semanticColors.onAccent),
            border = androidx.compose.foundation.BorderStroke(1.dp, semanticColors.cardStroke.copy(alpha = 0.72f)),
            shape = RoundedCornerShape(Radius.sm)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(semanticColors.onAccent)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val previewRatio = aspectRatio.toFloat().coerceAtLeast(0.1f)
                    val frameWidth = if (maxHeight * previewRatio <= maxWidth) {
                        maxHeight * previewRatio
                    } else {
                        maxWidth
                    }
                    val frameHeight = (frameWidth / previewRatio).coerceAtLeast(1.dp)

                    Box(
                        modifier = Modifier
                            .size(frameWidth, frameHeight)
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(semanticColors.onAccent)
                            .then(
                                // `awaitEachGesture` lets us bracket each gesture so we can
                                // fire `onPreviewTransformEnded` when the user lifts their
                                // fingers. `detectTransformGestures` has no end hook, so
                                // previously the VM had no way to know the drag was over
                                // and had to call saveProject on every tick instead.
                                if (canTransformPreview) Modifier.pointerInput(selectedClipId) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        // Track start with a gesture-LOCAL flag so the
                                        // begin/end bracket always pairs. A shared,
                                        // selection-keyed flag could be reset mid-gesture
                                        // (selection change during the drag), making this
                                        // `finally` skip onPreviewTransformEnded() — leaving
                                        // an orphaned undo state and an unsaved edit.
                                        var startedHere = false
                                        try {
                                            do {
                                                val event = awaitPointerEvent()
                                                val canceled = event.changes.any { it.isConsumed }
                                                if (canceled) break
                                                val zoomChange = event.calculateZoom()
                                                val rotationChange = event.calculateRotation()
                                                val panChange = event.calculatePan()
                                                if (zoomChange != 1f || rotationChange != 0f ||
                                                    panChange != Offset.Zero) {
                                                    if (!startedHere) {
                                                        startedHere = true
                                                        onPreviewTransformStarted()
                                                    }
                                                    onPreviewTransformChanged(
                                                        panChange.x, panChange.y,
                                                        zoomChange, rotationChange
                                                    )
                                                    event.changes.forEach { it.consume() }
                                                }
                                            } while (event.changes.any { it.pressed })
                                        } finally {
                                            if (startedHere) onPreviewTransformEnded()
                                        }
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        var isBuffering by remember { mutableStateOf(false) }
                        var hasPlaybackError by remember { mutableStateOf(false) }
                        DisposableEffect(engine) {
                            // Capture the player reference once; reuse on dispose to avoid
                            // attaching/removing on different player instances if engine state changes.
                            val capturedPlayer = engine.getPlayer()
                            val listener = object : Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) {
                                    isBuffering = state == Player.STATE_BUFFERING
                                    if (state == Player.STATE_READY) hasPlaybackError = false
                                }

                                override fun onPlayerError(error: PlaybackException) {
                                    isBuffering = false
                                    hasPlaybackError = !isRecoverablePreviewRuntimeFailure(error)
                                }
                            }
                            capturedPlayer.addListener(listener)
                            onDispose {
                                try { capturedPlayer.removeListener(listener) } catch (_: Exception) { /* player released */ }
                            }
                        }

                        // Keep one PlayerView mounted for the lifetime of the preview. Removing
                        // the AndroidView for a timeline gap, still image, or transient error
                        // destroys its SurfaceView while the hardware codec is active. On some
                        // Samsung/Qualcomm devices that detach blocks until Media3 raises
                        // TIMEOUT_OPERATION_DETACH_SURFACE and the preview becomes unrecoverable.
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                    onPlayerViewReady(this)
                                }
                            },
                            update = { playerView ->
                                val player = engine.getPlayer()
                                if (playerView.player !== player) playerView.player = player
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        when {
                            hasPlaybackError -> {
                                PreviewPlaybackErrorState(onOpenMediaManager = onOpenMediaManager)
                            }

                            showGapState -> {
                                PreviewGapState(
                                    nextClipStartMs = nextTimelineClip?.timelineStartMs,
                                    jumpToContentMs = jumpToContentMs,
                                    onJumpToContent = onJumpToContent
                                )
                            }

                            showGapPlaybackFrame -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(semanticColors.onAccent)
                                )
                            }

                            currentClipIsStillImage && currentTimelineUri != null -> {
                                SubcomposeAsyncImage(
                                    model = currentTimelineUri,
                                    contentDescription = stringResource(R.string.cd_preview_still_image),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                    loading = {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(36.dp),
                                            color = ClearCutAccents.Mauve,
                                            strokeWidth = 3.dp
                                        )
                                    },
                                    error = {
                                        PreviewUnavailableState()
                                    }
                                )
                            }

                            else -> Unit
                        }

                        activeImageOverlays.forEach { overlay ->
                            PreviewImageOverlay(
                                overlay = overlay,
                                frameWidth = frameWidth,
                                frameHeight = frameHeight,
                            )
                        }
                        activeTextOverlays.forEach { overlay ->
                            PreviewTextOverlay(
                                overlay = overlay,
                                frameWidth = frameWidth,
                                frameHeight = frameHeight,
                            )
                        }

                        if (showCompositionGuides && totalDurationMs > 0 && !showGapState) {
                            CompositionGuidesOverlay()
                        }

                        if (isSplitPreviewEnabled && hasActiveEffects && currentTimelineClip != null && !showGapState) {
                            SplitPreviewOverlay(
                                engine = engine,
                                clip = currentTimelineClip,
                                playheadMs = playheadMs,
                                frameWidthDp = frameWidth,
                                frameHeightDp = frameHeight,
                            )
                        }

                        if (totalDurationMs > 0 && !showGapState) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (showScopesButton) {
                                    ClearCutChromeIconButton(
                                        icon = Icons.Default.Insights,
                                        contentDescription = stringResource(R.string.preview_scopes),
                                        onClick = onToggleScopes,
                                        tint = semanticColors.subtext.copy(alpha = 0.9f),
                                        containerColor = semanticColors.background.copy(alpha = 0.72f),
                                        borderColor = semanticColors.cardStroke,
                                        shape = RoundedCornerShape(Radius.md)
                                    )
                                }
                                ClearCutChromeIconButton(
                                    icon = Icons.Default.GridOn,
                                    contentDescription = stringResource(R.string.preview_composition_guides),
                                    onClick = onToggleCompositionGuides,
                                    tint = if (showCompositionGuides) ClearCutAccents.Sky else semanticColors.subtext.copy(alpha = 0.9f),
                                    containerColor = if (showCompositionGuides) ClearCutAccents.Sky.copy(alpha = 0.22f) else semanticColors.background.copy(alpha = 0.72f),
                                    borderColor = if (showCompositionGuides) ClearCutAccents.Sky.copy(alpha = 0.6f) else semanticColors.cardStroke,
                                    shape = RoundedCornerShape(Radius.md),
                                )
                                if (hasActiveEffects) {
                                    ClearCutChromeIconButton(
                                        icon = Icons.Default.Compare,
                                        contentDescription = stringResource(R.string.preview_compare),
                                        onClick = onToggleSplitPreview,
                                        tint = if (isSplitPreviewEnabled) ClearCutAccents.Teal else semanticColors.subtext.copy(alpha = 0.9f),
                                        containerColor = if (isSplitPreviewEnabled) ClearCutAccents.Teal.copy(alpha = 0.3f) else semanticColors.background.copy(alpha = 0.72f),
                                        borderColor = if (isSplitPreviewEnabled) ClearCutAccents.Teal.copy(alpha = 0.6f) else semanticColors.cardStroke,
                                        shape = RoundedCornerShape(Radius.md),
                                    )
                                }
                            }
                        }

                        if (isBuffering && isPlaybackRequested && totalDurationMs > 0 && !showGapState) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = ClearCutAccents.Sky,
                                strokeWidth = 3.dp
                            )
                        }

                        if (totalDurationMs > 0L && !showGapState) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp),
                                color = semanticColors.background.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(Radius.sm),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    semanticColors.cardStroke.copy(alpha = 0.82f),
                                ),
                            ) {
                                Text(
                                    text = "${formatTimecode(playheadMs)} / ${formatTimecode(totalDurationMs)}",
                                    color = semanticColors.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                )
                            }
                        }

                        if (!isPlaying && totalDurationMs == 0L) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = semanticColors.panel.copy(alpha = 0.86f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, semanticColors.cardStroke.copy(alpha = 0.9f)),
                                shape = RoundedCornerShape(Radius.xl)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        color = ClearCutAccents.Sky.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(Radius.md),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ClearCutAccents.Sky.copy(alpha = 0.22f))
                                    ) {
                                        Icon(
                                            Icons.Default.VideoLibrary,
                                            contentDescription = stringResource(R.string.cd_preview_empty),
                                            tint = ClearCutAccents.Sky,
                                            modifier = Modifier
                                                .padding(16.dp)
                                                .size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        stringResource(R.string.preview_add_media),
                                        color = semanticColors.text,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            color = semanticColors.background,
            shape = RoundedCornerShape(Radius.sm),
            border = androidx.compose.foundation.BorderStroke(1.dp, semanticColors.cardStroke.copy(alpha = 0.64f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClearCutChromeIconButton(
                    icon = Icons.Default.Repeat,
                    contentDescription = stringResource(
                        if (isLooping) R.string.preview_disable_loop else R.string.preview_enable_loop
                    ),
                    onClick = onToggleLoop,
                    tint = if (isLooping) ClearCutAccents.Sky else semanticColors.subtext,
                    containerColor = if (isLooping) ClearCutAccents.Sky.copy(alpha = 0.14f) else Color.Transparent,
                    borderColor = Color.Transparent,
                    shape = RoundedCornerShape(Radius.sm),
                    size = 40.dp,
                    iconSize = 19.dp,
                )
                Spacer(modifier = Modifier.weight(1f))
                ClearCutChromeIconButton(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.preview_previous_frame),
                    onClick = { onSeek((playheadMs - frameDurationMs).coerceAtLeast(0L)) },
                    tint = semanticColors.text,
                    containerColor = Color.Transparent,
                    borderColor = Color.Transparent,
                    shape = RoundedCornerShape(Radius.sm),
                    size = 40.dp,
                    iconSize = 20.dp,
                )
                ClearCutChromeIconButton(
                    icon = if (isPlaybackRequested) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaybackRequested) {
                        stringResource(R.string.preview_pause)
                    } else {
                        stringResource(R.string.preview_play)
                    },
                    onClick = onTogglePlayback,
                    tint = semanticColors.background,
                    containerColor = ClearCutAccents.Sky,
                    borderColor = ClearCutAccents.Sky.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(Radius.sm),
                    size = 44.dp,
                    iconSize = 22.dp,
                )
                ClearCutChromeIconButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.preview_next_frame),
                    onClick = { onSeek((playheadMs + frameDurationMs).coerceAtMost(totalDurationMs)) },
                    tint = semanticColors.text,
                    containerColor = Color.Transparent,
                    borderColor = Color.Transparent,
                    shape = RoundedCornerShape(Radius.sm),
                    size = 40.dp,
                    iconSize = 20.dp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${aspectRatio.label}  •  $frameRate fps",
                    color = semanticColors.subtext,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PreviewImageOverlay(
    overlay: ImageOverlay,
    frameWidth: androidx.compose.ui.unit.Dp,
    frameHeight: androidx.compose.ui.unit.Dp,
) {
    val semanticColors = LocalClearCutColors.current
    val safeScale = overlay.scale.takeIf { it.isFinite() }?.coerceIn(0.01f, 2f) ?: 0.3f
    val width = frameWidth * safeScale
    val density = LocalDensity.current
    val frameWidthPx = with(density) { frameWidth.toPx() }
    val frameHeightPx = with(density) { frameHeight.toPx() }
    SubcomposeAsyncImage(
        model = overlay.sourceUri,
        contentDescription = stringResource(R.string.cd_preview_image_overlay),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .align(Alignment.Center)
            .width(width)
            .heightIn(max = frameHeight)
            .graphicsLayer {
                translationX = overlay.positionX.takeIf { it.isFinite() }?.coerceIn(-5f, 5f)
                    ?.let { it * frameWidthPx / 2f } ?: 0f
                translationY = overlay.positionY.takeIf { it.isFinite() }?.coerceIn(-5f, 5f)
                    ?.let { it * frameHeightPx / 2f } ?: 0f
                rotationZ = overlay.rotation.takeIf { it.isFinite() } ?: 0f
                alpha = overlay.opacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
            },
        loading = {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = ClearCutAccents.Mauve,
                    strokeWidth = 2.dp,
                )
            }
        },
        error = {
            Surface(
                color = semanticColors.background.copy(alpha = 0.72f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, semanticColors.cardStroke),
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = stringResource(R.string.cd_preview_image_overlay_missing),
                    tint = ClearCutAccents.Rosewater,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                )
            }
        },
    )
}

@Composable
private fun PreviewGapState(
    nextClipStartMs: Long?,
    jumpToContentMs: Long?,
    onJumpToContent: (Long) -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = semanticColors.panel.copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, semanticColors.cardStroke.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(Radius.xl)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = ClearCutAccents.Mauve.copy(alpha = 0.14f),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, ClearCutAccents.Mauve.copy(alpha = 0.22f))
            ) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = stringResource(R.string.preview_gap_title),
                    tint = ClearCutAccents.Rosewater,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.preview_gap_title),
                color = semanticColors.text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (nextClipStartMs != null) {
                    stringResource(R.string.preview_resume_at, formatTimecode(nextClipStartMs))
                } else {
                    stringResource(R.string.preview_gap_body)
                },
                color = semanticColors.subtext,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (jumpToContentMs != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { onJumpToContent(jumpToContentMs) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClearCutAccents.Rosewater,
                        contentColor = semanticColors.background
                    ),
                    shape = RoundedCornerShape(Radius.xl)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.preview_jump_to_content))
                }
            }
        }
    }
}

@Composable
private fun PreviewUnavailableState() {
    val semanticColors = LocalClearCutColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = semanticColors.panel.copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, semanticColors.cardStroke.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(Radius.xl)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = ClearCutAccents.Mauve.copy(alpha = 0.14f),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, ClearCutAccents.Mauve.copy(alpha = 0.22f))
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = stringResource(R.string.preview_unavailable_title),
                    tint = ClearCutAccents.Rosewater,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.preview_unavailable_title),
                color = semanticColors.text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.preview_unavailable_body),
                color = semanticColors.subtext,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PreviewPlaybackErrorState(onOpenMediaManager: () -> Unit) {
    val semanticColors = LocalClearCutColors.current
    Card(
        modifier = Modifier.padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = semanticColors.panel.copy(alpha = 0.96f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClearCutAccents.Red.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(Radius.xl),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                tint = ClearCutAccents.Red,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = stringResource(R.string.preview_playback_error_title),
                color = semanticColors.text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.preview_playback_error_body),
                color = semanticColors.subtext,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onOpenMediaManager,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClearCutAccents.Sky,
                    contentColor = semanticColors.background,
                ),
                shape = RoundedCornerShape(Radius.xl),
            ) {
                Text(stringResource(R.string.preview_open_media_manager))
            }
        }
    }
}

fun formatTimecode(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

internal fun activePreviewTextOverlays(overlays: List<TextOverlay>, playheadMs: Long): List<TextOverlay> {
    return overlays.filter { overlay ->
        playheadMs >= overlay.startTimeMs && playheadMs < overlay.endTimeMs
    }
}

@Composable
private fun BoxScope.PreviewTextOverlay(
    overlay: TextOverlay,
    frameWidth: androidx.compose.ui.unit.Dp,
    frameHeight: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    val frameWidthPx = with(density) { frameWidth.toPx() }
    val frameHeightPx = with(density) { frameHeight.toPx() }
    val previewFontSize = (overlay.fontSize * frameHeight.value / 1_080f)
        .coerceIn(10f, 72f)
    val horizontal = (overlay.positionX.coerceIn(0f, 1f) - 0.5f) * frameWidthPx
    val vertical = (overlay.positionY.coerceIn(0f, 1f) - 0.5f) * frameHeightPx
    val fontFamily = when (overlay.fontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
    val textAlign = when (overlay.alignment) {
        TextAlignment.LEFT -> TextAlign.Left
        TextAlignment.RIGHT -> TextAlign.Right
        TextAlignment.CENTER -> TextAlign.Center
    }

    Text(
        text = overlay.text,
        color = Color(overlay.color),
        textAlign = textAlign,
        fontFamily = fontFamily,
        fontWeight = if (overlay.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (overlay.italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = previewFontSize.sp,
        letterSpacing = (overlay.letterSpacing * previewFontSize / 48f).sp,
        lineHeight = (previewFontSize * overlay.lineHeight.coerceIn(0.8f, 3f)).sp,
        style = TextStyle(
            shadow = Shadow(
                color = Color(overlay.shadowColor),
                offset = Offset(overlay.shadowOffsetX, overlay.shadowOffsetY),
                blurRadius = overlay.shadowBlur.coerceAtLeast(0f),
            ),
            textDecoration = TextDecoration.None,
        ),
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                translationX = horizontal
                translationY = vertical
                rotationZ = overlay.rotation
                scaleX = overlay.scaleX.coerceAtLeast(0.01f)
                scaleY = overlay.scaleY.coerceAtLeast(0.01f)
            }
            .background(Color(overlay.backgroundColor), RoundedCornerShape(Radius.xs))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000) / 10

    return if (hours > 0) {
        "%d:%02d:%02d.%02d".format(hours, minutes, seconds, millis)
    } else {
        "%02d:%02d.%02d".format(minutes, seconds, millis)
    }
}

@Composable
private fun CompositionGuidesOverlay() {
    val guideColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)
    val safeZoneColor = ClearCutAccents.Yellow.copy(alpha = 0.25f)
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Rule of thirds
        val thirdX1 = w / 3f
        val thirdX2 = w * 2f / 3f
        val thirdY1 = h / 3f
        val thirdY2 = h * 2f / 3f
        drawLine(guideColor, Offset(thirdX1, 0f), Offset(thirdX1, h), strokeWidth = 1f)
        drawLine(guideColor, Offset(thirdX2, 0f), Offset(thirdX2, h), strokeWidth = 1f)
        drawLine(guideColor, Offset(0f, thirdY1), Offset(w, thirdY1), strokeWidth = 1f)
        drawLine(guideColor, Offset(0f, thirdY2), Offset(w, thirdY2), strokeWidth = 1f)

        // Title safe zone (80% inner area)
        val titleInset = 0.1f
        drawRect(
            safeZoneColor,
            topLeft = Offset(w * titleInset, h * titleInset),
            size = androidx.compose.ui.geometry.Size(w * (1 - 2 * titleInset), h * (1 - 2 * titleInset)),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
        )

        // Action safe zone (90% inner area)
        val actionInset = 0.05f
        drawRect(
            guideColor.copy(alpha = 0.15f),
            topLeft = Offset(w * actionInset, h * actionInset),
            size = androidx.compose.ui.geometry.Size(w * (1 - 2 * actionInset), h * (1 - 2 * actionInset)),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
        )

        // Center crosshair
        val cx = w / 2f
        val cy = h / 2f
        val crossSize = minOf(w, h) * 0.02f
        drawLine(guideColor, Offset(cx - crossSize, cy), Offset(cx + crossSize, cy), strokeWidth = 1f)
        drawLine(guideColor, Offset(cx, cy - crossSize), Offset(cx, cy + crossSize), strokeWidth = 1f)
    }
}

@Composable
private fun BoxScope.SplitPreviewOverlay(
    engine: VideoEngine,
    clip: Clip,
    playheadMs: Long,
    frameWidthDp: androidx.compose.ui.unit.Dp,
    frameHeightDp: androidx.compose.ui.unit.Dp,
) {
    val semanticColors = LocalClearCutColors.current
    val density = LocalDensity.current
    val frameWidthPx = with(density) { frameWidthDp.toPx() }
    val frameHeightPx = with(density) { frameHeightDp.toPx() }

    var wipePosition by remember { mutableFloatStateOf(0.5f) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val sourceUri = clip.sourceUri
    val timelineOffsetMs = (playheadMs - clip.timelineStartMs)
        .coerceIn(0L, clip.durationMs.coerceAtLeast(0L))
    val sourceTimeUs = clip.timelineOffsetToSourceMs(timelineOffsetMs) * 1000L

    LaunchedEffect(sourceUri, sourceTimeUs / 100_000) {
        originalBitmap = withContext(Dispatchers.IO) {
            engine.extractThumbnail(
                sourceUri,
                sourceTimeUs,
                frameWidthPx.toInt().coerceAtLeast(64),
                frameHeightPx.toInt().coerceAtLeast(36)
            )
        }
    }

    val bmp = originalBitmap
    if (bmp != null && !bmp.isRecycled) {
        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        wipePosition = (change.position.x / size.width).coerceIn(0.05f, 0.95f)
                    }
                }
        ) {
            val wipeX = size.width * wipePosition
            clipRect(right = wipeX) {
                drawImage(
                    imageBitmap,
                    srcSize = IntSize(bmp.width, bmp.height),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }

            drawLine(
                color = semanticColors.text,
                start = Offset(wipeX, 0f),
                end = Offset(wipeX, size.height),
                strokeWidth = 3f
            )

            val handleRadius = 10f
            drawCircle(
                color = semanticColors.text,
                radius = handleRadius,
                center = Offset(wipeX, size.height / 2f)
            )
            drawCircle(
                color = semanticColors.onAccent,
                radius = handleRadius - 3f,
                center = Offset(wipeX, size.height / 2f)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                color = semanticColors.background.copy(alpha = 0.72f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    stringResource(R.string.preview_compare_original),
                    color = ClearCutAccents.Teal,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Surface(
                color = semanticColors.background.copy(alpha = 0.72f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    stringResource(R.string.preview_compare_edited),
                    color = ClearCutAccents.Rosewater,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Slider(
            value = wipePosition,
            onValueChange = { wipePosition = it.coerceIn(0.05f, 0.95f) },
            valueRange = 0.05f..0.95f,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 0.dp)
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = semanticColors.text,
                activeTrackColor = ClearCutAccents.Teal.copy(alpha = 0.5f),
                inactiveTrackColor = ClearCutAccents.Rosewater.copy(alpha = 0.5f)
            )
        )
    }
}
