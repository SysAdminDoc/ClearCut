package com.novacut.editor.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.ui.theme.Mocha

data class MediaAsset(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val durationMs: Long,
    val usedInClipIds: List<String>,
    val isAccessible: Boolean
)

@Composable
fun MediaManagerPanel(
    tracks: List<Track>,
    onJumpToClip: (String) -> Unit,
    onRelinkMedia: (Uri, Uri) -> Unit,
    onRemoveUnused: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val assets by produceState(initialValue = emptyList<MediaAsset>(), key1 = tracks) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            analyzeMediaAssets(context, tracks)
        }
    }
    val totalSize = assets.sumOf { it.fileSize }
    val missingCount = assets.count { !it.isAccessible }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.media_manager_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.media_manager_close_cd), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // Summary stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mocha.Surface0, RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatChip(stringResource(R.string.media_stat_assets), "${assets.size}", Mocha.Mauve)
            StatChip(stringResource(R.string.media_stat_size), formatFileSize(totalSize), Mocha.Peach)
            StatChip(stringResource(R.string.media_stat_missing), "$missingCount", if (missingCount > 0) Mocha.Red else Mocha.Green)
        }

        Spacer(Modifier.height(8.dp))

        // Asset list
        if (assets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.media_manager_empty), color = Mocha.Subtext0, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(assets, key = { it.uri.toString() }) { asset ->
                    MediaAssetRow(
                        asset = asset,
                        onJumpToClip = { clipId -> onJumpToClip(clipId) }
                    )
                }
            }
        }

        // Actions
        if (assets.any { it.usedInClipIds.isEmpty() }) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRemoveUnused,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Mocha.Yellow.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.CleaningServices, stringResource(R.string.cd_cleaning_services), tint = Mocha.Yellow, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.panel_media_manager_remove_unused), color = Mocha.Yellow, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Mocha.Subtext0, fontSize = 10.sp)
    }
}

@Composable
private fun MediaAssetRow(
    asset: MediaAsset,
    onJumpToClip: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Mocha.Surface0)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon + info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                if (asset.isAccessible) Icons.Default.VideoFile else Icons.Default.BrokenImage,
                null,
                tint = if (asset.isAccessible) Mocha.Mauve else Mocha.Red,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    asset.fileName,
                    color = if (asset.isAccessible) Mocha.Text else Mocha.Red,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatFileSize(asset.fileSize), color = Mocha.Subtext0, fontSize = 10.sp)
                    Text(formatDuration(asset.durationMs), color = Mocha.Subtext0, fontSize = 10.sp)
                    Text(
                        stringResource(R.string.media_used_count, asset.usedInClipIds.size),
                        color = if (asset.usedInClipIds.isEmpty()) Mocha.Yellow else Mocha.Green,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Jump to first usage
        if (asset.usedInClipIds.isNotEmpty()) {
            IconButton(
                onClick = { onJumpToClip(asset.usedInClipIds.first()) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.MyLocation, stringResource(R.string.cd_media_goto), tint = Mocha.Subtext0, modifier = Modifier.size(14.dp))
            }
        }

        // Missing indicator
        if (!asset.isAccessible) {
            Icon(Icons.Default.Warning, stringResource(R.string.cd_media_missing), tint = Mocha.Red, modifier = Modifier.size(16.dp))
        }
    }
}

private fun analyzeMediaAssets(context: Context, tracks: List<Track>): List<MediaAsset> {
    val clipsByUri = mutableMapOf<String, MutableList<Clip>>()

    tracks.forEach { track ->
        track.clips.forEach { clip ->
            val key = clip.sourceUri.toString()
            clipsByUri.getOrPut(key) { mutableListOf() }.add(clip)
        }
    }

    return clipsByUri.map { (uriStr, clips) ->
        val uri = clips.first().sourceUri
        var fileName = "Unknown"
        var fileSize = 0L
        var accessible = false

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    accessible = true
                }
            }
        } catch (e: Exception) {
            fileName = uri.lastPathSegment ?: "Unknown"
        }

        MediaAsset(
            uri = uri,
            fileName = fileName,
            fileSize = fileSize,
            durationMs = clips.first().sourceDurationMs,
            usedInClipIds = clips.map { it.id },
            isAccessible = accessible
        )
    }.sortedByDescending { it.usedInClipIds.size }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "%.1fKB".format(bytes / 1024f)
    bytes < 1024 * 1024 * 1024 -> "%.1fMB".format(bytes / (1024f * 1024f))
    else -> "%.2fGB".format(bytes / (1024f * 1024f * 1024f))
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    return if (m > 0) "%d:%02d".format(m, s % 60) else "${s}s"
}
