package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.ui.theme.Mocha

@Composable
fun MiniPlayerBar(
    isPlaying: Boolean,
    playheadMs: Long,
    totalDurationMs: Long,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play/Pause
        IconButton(
            onClick = onTogglePlayback,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Mocha.Text,
                modifier = Modifier.size(20.dp)
            )
        }

        // Timecode
        Text(
            text = formatTimecode(playheadMs),
            color = Mocha.Subtext0,
            fontSize = 11.sp,
            modifier = Modifier.width(40.dp)
        )

        // Scrub slider
        val progress = if (totalDurationMs > 0) playheadMs.toFloat() / totalDurationMs else 0f
        Slider(
            value = progress,
            onValueChange = { onSeek((it * totalDurationMs).toLong()) },
            modifier = Modifier.weight(1f).height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Mocha.Sky,
                activeTrackColor = Mocha.Sky,
                inactiveTrackColor = Mocha.Surface1
            )
        )

        // Duration
        Text(
            text = formatTimecode(totalDurationMs),
            color = Mocha.Subtext0,
            fontSize = 11.sp,
            modifier = Modifier.width(40.dp)
        )
    }
}
