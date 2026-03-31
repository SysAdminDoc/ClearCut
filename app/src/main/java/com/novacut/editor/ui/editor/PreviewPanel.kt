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
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.res.stringResource
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
            .background(Mocha.Crust),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Video Preview
        var transformStarted by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Mocha.Mantle)
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
                androidx.compose.material3.IconButton(
                    onClick = onToggleScopes,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Insights,
                        stringResource(R.string.preview_scopes),
                        tint = Mocha.Subtext0.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Overlay play button when paused and no content
            if (!isPlaying && totalDurationMs == 0L) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = stringResource(R.string.cd_preview_empty),
                        tint = Mocha.Overlay0,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.preview_add_media),
                        color = Mocha.Overlay0,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Playback Controls — compact PowerDirector-style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timecode (current / total)
            Text(
                text = formatTimecode(playheadMs) + "/" + formatTimecode(totalDurationMs),
                color = Mocha.Subtext0,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Play/Pause — centered
            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.preview_pause) else stringResource(R.string.preview_play),
                    tint = Mocha.Text,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
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
