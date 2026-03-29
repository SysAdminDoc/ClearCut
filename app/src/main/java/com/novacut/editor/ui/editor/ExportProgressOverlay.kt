package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.engine.ExportState
import com.novacut.editor.ui.theme.Mocha

/**
 * Floating export progress overlay that shows during background export.
 * Appears as a compact notification-style card in the top corner.
 */
@Composable
fun ExportProgressOverlay(
    exportState: ExportState,
    exportProgress: Float,
    exportStartTime: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExporting = exportState == ExportState.EXPORTING

    AnimatedVisibility(
        visible = isExporting,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Mocha.Surface0)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Progress circle
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { exportProgress },
                    modifier = Modifier.size(36.dp),
                    color = Mocha.Mauve,
                    strokeWidth = 3.dp,
                    trackColor = Mocha.Mauve.copy(alpha = 0.1f)
                )
                Text(
                    "${(exportProgress * 100).toInt()}%",
                    color = Mocha.Mauve,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.panel_export_progress_exporting), color = Mocha.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)

                // Estimated time remaining
                val currentTime by rememberUpdatedState(System.currentTimeMillis())
                val elapsed = currentTime - exportStartTime
                val estimatedTotal = if (exportProgress > 0.05f) {
                    (elapsed / exportProgress).toLong()
                } else 0L
                val remaining = (estimatedTotal - elapsed).coerceAtLeast(0L)

                if (remaining > 0 && exportProgress > 0.05f) {
                    Text(
                        stringResource(R.string.export_eta_remaining, formatEta(remaining)),
                        color = Mocha.Subtext0,
                        fontSize = 10.sp
                    )
                }
            }

            // Cancel button
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Close, stringResource(R.string.cd_export_cancel), tint = Mocha.Red, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatEta(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
