package com.novacut.editor.ui.editor

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.SubcomposeAsyncImage
import com.novacut.editor.R
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.TouchTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PreviewPanel(
    engine: VideoEngine,
    playheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
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

    // Both gradients are static — colors don't change with state. Hoist them into `remember`
    // so we don't allocate new Brush + List instances on every recomposition (PreviewPanel
    // recomposes on every playhead tick during playback, ~30 fps).
    val outerGradient = remember {
        Brush.verticalGradient(listOf(Mocha.Midnight, Mocha.Base, Mocha.Midnight))
    }
    val previewGradient = remember {
        Brush.verticalGradient(listOf(Mocha.PanelHighest.copy(alpha = 0.9f), Mocha.Panel))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(outerGradient)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(previewGradient)
                    .padding(8.dp)
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
                            .clip(RoundedCornerShape(20.dp))
                            .background(Mocha.Crust)
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
                        DisposableEffect(engine) {
                            // Capture the player reference once; reuse on dispose to avoid
                            // attaching/removing on different player instances if engine state changes.
                            val capturedPlayer = engine.getPlayer()
                            val listener = object : Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) {
                                    isBuffering = state == Player.STATE_BUFFERING
                                }
                            }
                            capturedPlayer.addListener(listener)
                            onDispose {
                                try { capturedPlayer.removeListener(listener) } catch (_: Exception) { /* player released */ }
                            }
                        }

                        when {
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
                                        .background(Mocha.Crust)
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
                                            color = Mocha.Mauve,
                                            strokeWidth = 3.dp
                                        )
                                    },
                                    error = {
                                        PreviewUnavailableState()
                                    }
                                )
                            }

                            else -> {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            useController = false
                                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                            onPlayerViewReady(this)
                                        }
                                    },
                                    update = { playerView ->
                                        playerView.player = engine.getPlayer()
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        if (!showGapState && !showGapPlaybackFrame) {
                            activeImageOverlays.forEach { overlay ->
                                PreviewImageOverlay(
                                    overlay = overlay,
                                    frameWidth = frameWidth,
                                    frameHeight = frameHeight,
                                )
                            }
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
                                    Surface(
                                        color = Mocha.Midnight.copy(alpha = 0.72f),
                                        shape = CircleShape,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke),
                                    ) {
                                        IconButton(
                                            onClick = onToggleScopes,
                                            modifier = Modifier.size(TouchTarget.minimum)
                                        ) {
                                            Icon(
                                                Icons.Default.Insights,
                                                stringResource(R.string.preview_scopes),
                                                tint = Mocha.Subtext0.copy(alpha = 0.9f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    color = if (showCompositionGuides) Mocha.Mauve.copy(alpha = 0.3f) else Mocha.Midnight.copy(alpha = 0.72f),
                                    shape = CircleShape,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (showCompositionGuides) Mocha.Mauve.copy(alpha = 0.6f) else Mocha.CardStroke
                                    ),
                                ) {
                                    IconButton(
                                        onClick = onToggleCompositionGuides,
                                        modifier = Modifier.size(TouchTarget.minimum)
                                    ) {
                                        Icon(
                                            Icons.Default.GridOn,
                                            contentDescription = stringResource(R.string.preview_composition_guides),
                                            tint = if (showCompositionGuides) Mocha.Mauve else Mocha.Subtext0.copy(alpha = 0.9f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (hasActiveEffects) {
                                    Surface(
                                        color = if (isSplitPreviewEnabled) Mocha.Teal.copy(alpha = 0.3f) else Mocha.Midnight.copy(alpha = 0.72f),
                                        shape = CircleShape,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSplitPreviewEnabled) Mocha.Teal.copy(alpha = 0.6f) else Mocha.CardStroke
                                        ),
                                    ) {
                                        IconButton(
                                            onClick = onToggleSplitPreview,
                                            modifier = Modifier.size(TouchTarget.minimum)
                                        ) {
                                            Icon(
                                                Icons.Default.Compare,
                                                contentDescription = stringResource(R.string.preview_compare),
                                                tint = if (isSplitPreviewEnabled) Mocha.Teal else Mocha.Subtext0.copy(alpha = 0.9f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isBuffering && totalDurationMs > 0 && !showGapState) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = Mocha.Mauve,
                                strokeWidth = 3.dp
                            )
                        }

                        if (!isPlaying && totalDurationMs == 0L) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Mocha.Panel.copy(alpha = 0.86f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        color = Mocha.Mauve.copy(alpha = 0.14f),
                                        shape = CircleShape,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.22f))
                                    ) {
                                        Icon(
                                            Icons.Default.VideoLibrary,
                                            contentDescription = stringResource(R.string.cd_preview_empty),
                                            tint = Mocha.Rosewater,
                                            modifier = Modifier
                                                .padding(16.dp)
                                                .size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        stringResource(R.string.preview_add_media),
                                        color = Mocha.Text,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = Mocha.Panel,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatTimecode(playheadMs),
                        color = Mocha.Text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatTimecode(totalDurationMs),
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    color = Mocha.Rosewater,
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = onTogglePlayback,
                        modifier = Modifier.size(TouchTarget.minimum)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) {
                                stringResource(R.string.preview_pause)
                            } else {
                                stringResource(R.string.preview_play)
                            },
                            tint = Mocha.Midnight,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
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
                    color = Mocha.Mauve,
                    strokeWidth = 2.dp,
                )
            }
        },
        error = {
            Surface(
                color = Mocha.Midnight.copy(alpha = 0.72f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke),
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = stringResource(R.string.cd_preview_image_overlay_missing),
                    tint = Mocha.Rosewater,
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Mocha.Panel.copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = Mocha.Mauve.copy(alpha = 0.14f),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.22f))
            ) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = stringResource(R.string.preview_gap_title),
                    tint = Mocha.Rosewater,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.preview_gap_title),
                color = Mocha.Text,
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
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (jumpToContentMs != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { onJumpToContent(jumpToContentMs) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mocha.Rosewater,
                        contentColor = Mocha.Midnight
                    ),
                    shape = RoundedCornerShape(16.dp)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Mocha.Panel.copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = Mocha.Mauve.copy(alpha = 0.14f),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.22f))
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = stringResource(R.string.preview_unavailable_title),
                    tint = Mocha.Rosewater,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.preview_unavailable_title),
                color = Mocha.Text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.preview_unavailable_body),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
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
    val safeZoneColor = Mocha.Yellow.copy(alpha = 0.25f)
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
    val density = LocalDensity.current
    val frameWidthPx = with(density) { frameWidthDp.toPx() }
    val frameHeightPx = with(density) { frameHeightDp.toPx() }

    var wipePosition by remember { mutableFloatStateOf(0.5f) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val sourceUri = clip.sourceUri
    val sourceTimeUs = ((playheadMs - clip.timelineStartMs + clip.trimStartMs)
        .coerceIn(clip.trimStartMs, clip.trimEndMs)) * 1000L

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
                color = Mocha.Text,
                start = Offset(wipeX, 0f),
                end = Offset(wipeX, size.height),
                strokeWidth = 3f
            )

            val handleRadius = 10f
            drawCircle(
                color = Mocha.Text,
                radius = handleRadius,
                center = Offset(wipeX, size.height / 2f)
            )
            drawCircle(
                color = Mocha.Crust,
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
                color = Mocha.Midnight.copy(alpha = 0.72f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    stringResource(R.string.preview_compare_original),
                    color = Mocha.Teal,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Surface(
                color = Mocha.Midnight.copy(alpha = 0.72f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    stringResource(R.string.preview_compare_edited),
                    color = Mocha.Rosewater,
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
                thumbColor = Mocha.Text,
                activeTrackColor = Mocha.Teal.copy(alpha = 0.5f),
                inactiveTrackColor = Mocha.Rosewater.copy(alpha = 0.5f)
            )
        )
    }
}
