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
import androidx.compose.ui.draw.clip

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio.toFloat())
                .clip(RoundedCornerShape(8.dp))
                .background(Mocha.Mantle),
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
                        contentDescription = null,
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

        // Playback Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timestamp
            Text(
                text = formatTimestamp(playheadMs),
                color = Mocha.Subtext0,
                fontSize = 12.sp,
                modifier = Modifier.width(if (totalDurationMs >= 3_600_000) 85.dp else 70.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Skip backward
            IconButton(
                onClick = { onSeek((playheadMs - 5000).coerceAtLeast(0)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Replay5,
                    contentDescription = stringResource(R.string.preview_back_5s),
                    tint = Mocha.Text,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Previous frame
            IconButton(
                onClick = { onSeek((playheadMs - frameStepMs).coerceAtLeast(0)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.preview_previous_frame),
                    tint = Mocha.Text,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Play/Pause
            FilledIconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Mocha.Mauve,
                    contentColor = Mocha.Crust
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.preview_pause) else stringResource(R.string.preview_play),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Next frame
            IconButton(
                onClick = { onSeek((playheadMs + frameStepMs).coerceAtMost(totalDurationMs)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.preview_next_frame),
                    tint = Mocha.Text,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Skip forward
            IconButton(
                onClick = { onSeek((playheadMs + 5000).coerceAtMost(totalDurationMs)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Forward5,
                    contentDescription = stringResource(R.string.preview_forward_5s),
                    tint = Mocha.Text,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Loop toggle
            IconButton(
                onClick = onToggleLoop,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Loop,
                    contentDescription = if (isLooping) stringResource(R.string.preview_disable_loop) else stringResource(R.string.preview_enable_loop),
                    tint = if (isLooping) Mocha.Mauve else Mocha.Overlay0,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Duration
            Text(
                text = formatTimestamp(totalDurationMs),
                color = Mocha.Subtext0,
                fontSize = 12.sp,
                modifier = Modifier.width(if (totalDurationMs >= 3_600_000) 85.dp else 70.dp)
            )
        }
    }
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
