package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import com.novacut.editor.R
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Clip
import com.novacut.editor.ui.theme.Mocha

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
    jumpToContentMs: Long? = null,
    onJumpToContent: (Long) -> Unit = {},
    onPreviewTransformStarted: () -> Unit = {},
    onPreviewTransformChanged: (dx: Float, dy: Float, scaleChange: Float, rotationChange: Float) -> Unit = { _, _, _, _ -> },
    showScopesButton: Boolean = false,
    onToggleScopes: () -> Unit = {}
) {
    val currentTimelineUri = currentTimelineClip?.let { it.proxyUri ?: it.sourceUri }
    val currentClipIsStillImage = remember(currentTimelineUri) {
        currentTimelineUri?.let(engine::isStillImage) == true
    }
    val canTransformPreview = selectedClipId != null && currentTimelineClip?.id == selectedClipId
    val showGapState = totalDurationMs > 0L && currentTimelineClip == null && !isPlaying
    val showGapPlaybackFrame = totalDurationMs > 0L && currentTimelineClip == null && isPlaying

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Mocha.Midnight,
                        Mocha.Base,
                        Mocha.Midnight
                    )
                )
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var transformStarted by remember { mutableStateOf(false) }
        LaunchedEffect(selectedClipId, currentTimelineClip?.id) { transformStarted = false }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(26.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Mocha.PanelHighest.copy(alpha = 0.9f),
                                Mocha.Panel
                            )
                        )
                    )
                    .padding(10.dp)
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
                            .clip(RoundedCornerShape(22.dp))
                            .background(Mocha.Crust)
                            .then(
                                if (canTransformPreview) Modifier.pointerInput(selectedClipId) {
                                    detectTransformGestures { _, pan, zoom, rotation ->
                                        if (!transformStarted) {
                                            transformStarted = true
                                            onPreviewTransformStarted()
                                        }
                                        onPreviewTransformChanged(pan.x, pan.y, zoom, rotation)
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        var isBuffering by remember { mutableStateOf(false) }
                        DisposableEffect(engine) {
                            val player = engine.getPlayer()
                            val listener = object : Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) {
                                    isBuffering = state == Player.STATE_BUFFERING
                                }
                            }
                            player.addListener(listener)
                            onDispose { player.removeListener(listener) }
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
                                    contentDescription = null,
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

                        if (!showGapState) {
                            Surface(
                                color = Mocha.Midnight.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Mocha.Rosewater, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.preview_live),
                                        color = Mocha.Text,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        if (showScopesButton && totalDurationMs > 0 && !showGapState) {
                            Surface(
                                color = Mocha.Midnight.copy(alpha = 0.72f),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                IconButton(
                                    onClick = onToggleScopes,
                                    modifier = Modifier.size(38.dp)
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

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = Mocha.Panel,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.preview_pause) else stringResource(R.string.preview_play),
                            tint = Mocha.Midnight,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
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
