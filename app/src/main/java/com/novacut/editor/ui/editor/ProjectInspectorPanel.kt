package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class ProjectInspectorData(
    val projectName: String = "",
    val clipCount: Int = 0,
    val videoTrackCount: Int = 0,
    val audioTrackCount: Int = 0,
    val overlayTrackCount: Int = 0,
    val textOverlayCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val autoSaveSizeBytes: Long = 0L,
    val autoSaveLastModifiedMs: Long = 0L,
    val missingMediaCount: Int = 0,
    val exportResolution: String = "",
    val exportCodec: String = "",
    val exportFrameRate: Int = 30,
    val dbSchemaVersion: Int = 8,
    val backupFileCount: Int = 0,
    val effectCount: Int = 0,
    val keyframeCount: Int = 0
)

@Composable
fun ProjectInspectorPanel(
    data: ProjectInspectorData,
    onClose: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = semanticColors.surfaceBase,
        shape = RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ClearCutAccents.Blue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.inspector_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = semanticColors.subtext
                    )
                }
            }

            if (data.projectName.isNotBlank()) {
                Text(
                    text = data.projectName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = semanticColors.subtextStrong
                )
                Spacer(Modifier.height(12.dp))
            }

            InspectorSection(stringResource(R.string.inspector_section_media)) {
                InspectorRow(
                    stringResource(R.string.inspector_clips),
                    data.clipCount.toString()
                )
                InspectorRow(
                    stringResource(R.string.inspector_video_tracks),
                    data.videoTrackCount.toString()
                )
                InspectorRow(
                    stringResource(R.string.inspector_audio_tracks),
                    data.audioTrackCount.toString()
                )
                if (data.overlayTrackCount > 0) {
                    InspectorRow(
                        stringResource(R.string.inspector_overlay_tracks),
                        data.overlayTrackCount.toString()
                    )
                }
                if (data.textOverlayCount > 0) {
                    InspectorRow(
                        stringResource(R.string.inspector_text_overlays),
                        data.textOverlayCount.toString()
                    )
                }
                if (data.effectCount > 0) {
                    InspectorRow(
                        stringResource(R.string.inspector_effects),
                        data.effectCount.toString()
                    )
                }
                if (data.keyframeCount > 0) {
                    InspectorRow(
                        stringResource(R.string.inspector_keyframes),
                        data.keyframeCount.toString()
                    )
                }
                InspectorRow(
                    stringResource(R.string.inspector_duration),
                    formatDuration(data.totalDurationMs)
                )
            }

            if (data.missingMediaCount > 0) {
                Spacer(Modifier.height(8.dp))
                InspectorSection(
                    stringResource(R.string.inspector_section_issues),
                    accentColor = ClearCutAccents.Red
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ClearCutAccents.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.inspector_missing_media, data.missingMediaCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = ClearCutAccents.Red
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            InspectorSection(stringResource(R.string.inspector_section_export)) {
                InspectorRow(
                    stringResource(R.string.inspector_resolution),
                    data.exportResolution
                )
                InspectorRow(
                    stringResource(R.string.inspector_codec),
                    data.exportCodec
                )
                InspectorRow(
                    stringResource(R.string.inspector_frame_rate),
                    "${data.exportFrameRate} fps"
                )
            }

            Spacer(Modifier.height(8.dp))
            InspectorSection(stringResource(R.string.inspector_section_storage)) {
                InspectorRow(
                    stringResource(R.string.inspector_autosave_size),
                    formatFileSize(data.autoSaveSizeBytes)
                )
                if (data.autoSaveLastModifiedMs > 0L) {
                    InspectorRow(
                        stringResource(R.string.inspector_last_saved),
                        formatDateTimestamp(data.autoSaveLastModifiedMs)
                    )
                }
                InspectorRow(
                    stringResource(R.string.inspector_backup_files),
                    data.backupFileCount.toString()
                )
                InspectorRow(
                    stringResource(R.string.inspector_db_version),
                    data.dbSchemaVersion.toString()
                )
            }

            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun InspectorSection(
    title: String,
    accentColor: androidx.compose.ui.graphics.Color = ClearCutAccents.Blue,
    content: @Composable () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = semanticColors.surface,
            thickness = 0.5.dp
        )
        content()
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    val semanticColors = LocalClearCutColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = semanticColors.subtext
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = semanticColors.text,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0s"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (minutes > 0 || hours > 0) append("${minutes}m ")
        append("${seconds}s")
    }.trim()
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatDateTimestamp(ms: Long): String {
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    } catch (_: Exception) {
        "—"
    }
}
