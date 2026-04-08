package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.novacut.editor.R
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.ui.theme.Mocha

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PreviewPanel(
    engine: VideoEngine,
    playheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    isLooping: Boolean = false,
    aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    frameRate: Int = 30,
    onTogglePlayback: () -> Unit,
    onToggleLoop: () -> Unit = {},
    onSeek: (Long) -> Unit,
    onPlayerViewReady: (PlayerView) -> Unit = {},
    selectedClipId: String? = null,
    onPreviewTransformStarted: () -> Unit = {},
    onPreviewTransformChanged: (dx: Float, dy: Float, scaleChange: Float, rotationChange: Float) -> Unit = { _, _, _, _ -> },
    showScopesButton: Boolean = false,
    onToggleScopes: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val frameStepMs = (1000L / frameRate.coerceAtLeast(1))
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
        // Video Preview
        var transformStarted by remember { mutableStateOf(false) }
        LaunchedEffect(selectedClipId) { transformStarted = false }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Mocha.Crust)
                        .then(
                            if (selectedClipId != null) Modifier.pointerInput(selectedClipId) {
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
            // Observe player buffering state
            var isBuffering by remember { mutableStateOf(false) }
            DisposableEffect(engine) {
                val player = engine.getPlayer()
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                    }
                }
                player?.addListener(listener)
                onDispose { player?.removeListener(listener) }
            }

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

            // Buffering indicator
            if (isBuffering && totalDurationMs > 0) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Mocha.Mauve,
                    strokeWidth = 3.dp
                )
            }

            // Scopes toggle button (top-right corner of preview)
            if (showScopesButton && totalDurationMs > 0) {
                Surface(
                    color = Mocha.Midnight.copy(alpha = 0.72f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    androidx.compose.material3.IconButton(
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

            // Overlay play button when paused and no content
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
    val millis = (ms % 1000) / 10 // Show centiseconds (00-99)

    return if (hours > 0) {
        "%d:%02d:%02d.%02d".format(hours, minutes, seconds, millis)
    } else {
        "%02d:%02d.%02d".format(minutes, seconds, millis)
    }
}
