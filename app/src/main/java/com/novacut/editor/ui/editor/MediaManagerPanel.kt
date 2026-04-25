package com.novacut.editor.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.ui.theme.Mocha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class MediaAsset(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val durationMs: Long,
    val usedInClipIds: List<String>,
    val isAccessible: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaManagerPanel(
    tracks: List<Track>,
    onJumpToClip: (String) -> Unit,
    onRelinkMedia: (Uri) -> Unit,
    onRemoveUnused: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var assets by remember(tracks) { mutableStateOf(emptyList<MediaAsset>()) }
    var isAnalyzing by remember(tracks) { mutableStateOf(true) }

    LaunchedEffect(context, tracks) {
        isAnalyzing = true
        assets = withContext(Dispatchers.IO) {
            analyzeMediaAssets(context, tracks)
        }
        isAnalyzing = false
    }

    val totalSize = assets.sumOf { it.fileSize }
    val missingCount = assets.count { !it.isAccessible }
    val emptyTrackCount = remember(tracks) {
        tracks.count { it.index >= 2 && it.clips.isEmpty() }
    }
    val statusLabel = when {
        isAnalyzing -> stringResource(R.string.media_manager_status_scanning)
        missingCount > 0 -> pluralStringResource(
            R.plurals.media_manager_status_missing_count,
            missingCount,
            missingCount
        )
        emptyTrackCount > 0 -> pluralStringResource(
            R.plurals.media_manager_status_empty_count,
            emptyTrackCount,
            emptyTrackCount
        )
        else -> stringResource(R.string.media_manager_status_healthy)
    }
    val statusAccent = when {
        isAnalyzing -> Mocha.Blue
        missingCount > 0 -> Mocha.Red
        emptyTrackCount > 0 -> Mocha.Yellow
        else -> Mocha.Green
    }
    val assetCountLabel = pluralStringResource(
        R.plurals.media_manager_asset_count,
        assets.size,
        assets.size
    )
    val emptyTrackLabel = pluralStringResource(
        R.plurals.media_manager_empty_tracks_count,
        emptyTrackCount,
        emptyTrackCount
    )

    PremiumEditorPanel(
        title = stringResource(R.string.media_manager_title),
        subtitle = stringResource(R.string.media_manager_subtitle),
        icon = Icons.Default.PermMedia,
        accent = if (missingCount > 0) Mocha.Red else Mocha.Blue,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.media_manager_close_cd),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = if (missingCount > 0) Mocha.Red else Mocha.Blue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.media_manager_health_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.media_manager_health_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = if (isAnalyzing) "Analyzing..." else formatFileSize(totalSize),
                        accent = Mocha.Peach
                    )
                    PremiumPanelPill(
                        text = statusLabel,
                        accent = statusAccent
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediaHealthMetric(
                    title = stringResource(R.string.media_stat_assets),
                    value = if (isAnalyzing) "..." else assets.size.toString(),
                    accent = Mocha.Blue,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
                MediaHealthMetric(
                    title = stringResource(R.string.media_stat_size),
                    value = if (isAnalyzing) "..." else formatFileSize(totalSize),
                    accent = Mocha.Peach,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
                MediaHealthMetric(
                    title = stringResource(R.string.media_stat_missing),
                    value = if (isAnalyzing) "..." else missingCount.toString(),
                    accent = if (missingCount > 0) Mocha.Red else Mocha.Green,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
                MediaHealthMetric(
                    title = stringResource(R.string.media_stat_empty_tracks),
                    value = emptyTrackCount.toString(),
                    accent = if (emptyTrackCount > 0) Mocha.Yellow else Mocha.Green,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
            }

            if (isAnalyzing) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Mocha.PanelRaised,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Mocha.CardStroke)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                        ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(18.dp)
                                .width(18.dp),
                            color = Mocha.Blue,
                            strokeWidth = 2.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.media_manager_scanning_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = Mocha.Text,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.media_manager_scanning_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = Mocha.Subtext0
                            )
                        }
                    }
                }
            } else {
                MediaManagerMessageCard(
                    title = when {
                        missingCount > 0 -> pluralStringResource(
                            R.plurals.media_manager_missing_title,
                            missingCount,
                            missingCount
                        )
                        assets.isEmpty() -> stringResource(R.string.media_manager_empty_title)
                        else -> stringResource(R.string.media_manager_ready_title)
                    },
                    body = when {
                        missingCount > 0 -> stringResource(R.string.media_manager_missing_body)
                        assets.isEmpty() -> stringResource(R.string.media_manager_empty_body)
                        else -> stringResource(R.string.media_manager_ready_body)
                    },
                    accent = when {
                        missingCount > 0 -> Mocha.Red
                        assets.isEmpty() -> Mocha.Blue
                        else -> Mocha.Green
                    },
                    icon = when {
                        missingCount > 0 -> Icons.Default.BrokenImage
                        assets.isEmpty() -> Icons.Default.PermMedia
                        else -> Icons.Default.Link
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.media_manager_assets_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.media_manager_assets_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                PremiumPanelPill(
                    text = assetCountLabel,
                    accent = when {
                        missingCount > 0 -> Mocha.Peach
                        assets.isEmpty() -> Mocha.Overlay0
                        else -> Mocha.Blue
                    }
                )
            }

            when {
                isAnalyzing -> Unit
                assets.isEmpty() -> {
                    MediaManagerMessageCard(
                        title = stringResource(R.string.media_manager_empty_title),
                        body = stringResource(R.string.media_manager_empty_body),
                        accent = Mocha.Blue,
                        icon = Icons.Default.PermMedia
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        assets.forEach { asset ->
                            MediaAssetCard(
                                asset = asset,
                                onJumpToClip = onJumpToClip,
                                onRelinkMedia = onRelinkMedia
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = if (emptyTrackCount > 0) Mocha.Yellow else Mocha.Green) {
            Text(
                text = stringResource(R.string.media_manager_cleanup_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = if (emptyTrackCount > 0) {
                    stringResource(R.string.media_manager_cleanup_needs_trim)
                } else {
                    stringResource(R.string.media_manager_cleanup_ready)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            PremiumPanelPill(
                text = emptyTrackLabel,
                accent = if (emptyTrackCount > 0) Mocha.Yellow else Mocha.Green
            )

            Button(
                onClick = onRemoveUnused,
                enabled = emptyTrackCount > 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Yellow,
                    contentColor = Mocha.Base,
                    disabledContainerColor = Mocha.Surface1,
                    disabledContentColor = Mocha.Subtext0
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = stringResource(R.string.cd_cleaning_services)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.panel_media_manager_remove_unused))
            }
        }
    }
}

@Composable
private fun MediaHealthMetric(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Mocha.Subtext0
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MediaManagerMessageCard(
    title: String,
    body: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaAssetCard(
    asset: MediaAsset,
    onJumpToClip: (String) -> Unit,
    onRelinkMedia: (Uri) -> Unit
) {
    val accent = if (asset.isAccessible) Mocha.Blue else Mocha.Red
    val statusLabel = stringResource(if (asset.isAccessible) R.string.media_status_online else R.string.media_status_missing)
    val usageLabel = pluralStringResource(
        R.plurals.media_used_in_clip_count,
        asset.usedInClipIds.size,
        asset.usedInClipIds.size
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (asset.isAccessible) Mocha.PanelRaised else Mocha.Red.copy(alpha = 0.08f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (asset.isAccessible) Mocha.CardStroke else Mocha.Red.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = if (asset.isAccessible) Icons.Default.VideoFile else Icons.Default.BrokenImage,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = asset.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (asset.isAccessible) Mocha.Text else Mocha.Red,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.media_file_meta,
                                formatFileSize(asset.fileSize),
                                formatDuration(asset.durationMs)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Mocha.Subtext0
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                PremiumPanelPill(
                    text = statusLabel,
                    accent = accent
                )
            }

            if (!asset.isAccessible) {
                MediaManagerMessageCard(
                    title = stringResource(R.string.media_missing_asset_title),
                    body = stringResource(R.string.media_source_unavailable),
                    accent = Mocha.Red,
                    icon = Icons.Default.BrokenImage
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PremiumPanelPill(
                    text = usageLabel,
                    accent = if (asset.isAccessible) Mocha.Green else Mocha.Peach
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!asset.isAccessible) {
                        OutlinedButton(
                            onClick = { onRelinkMedia(asset.uri) },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = stringResource(R.string.media_manager_relink_cd)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.media_manager_relink_action))
                        }
                    }

                    if (asset.usedInClipIds.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onJumpToClip(asset.usedInClipIds.first()) },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Mocha.Blue.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Blue)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = stringResource(R.string.cd_media_goto)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.media_goto_first_use))
                        }
                    }
                }
            }
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

    return clipsByUri.map { (_, clips) ->
        val uri = clips.first().sourceUri
        var fileName = "Unknown"
        var fileSize = 0L
        var accessible = false

        try {
            if (uri.scheme == "file") {
                val localFile = uri.path?.let(::File)
                if (localFile != null) {
                    if (localFile.name.isNotBlank()) {
                        fileName = localFile.name
                    }
                    accessible = localFile.exists()
                    if (accessible) {
                        fileSize = localFile.length()
                    }
                }
            } else {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                        accessible = true
                    }
                }

                if (!accessible) {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                        accessible = true
                        if (fileSize <= 0L && descriptor.length > 0L) {
                            fileSize = descriptor.length
                        }
                    }
                }
            }
        } catch (e: Exception) {
            fileName = uri.lastPathSegment ?: "Unknown"
        }

        if (fileName == "Unknown") {
            fileName = uri.lastPathSegment ?: fileName
        }

        MediaAsset(
            uri = uri,
            fileName = fileName,
            fileSize = fileSize,
            durationMs = clips.first().sourceDurationMs,
            usedInClipIds = clips.map { it.id },
            isAccessible = accessible
        )
    }.sortedWith(compareBy<MediaAsset> { it.isAccessible }.thenByDescending { it.usedInClipIds.size })
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1fKB", bytes / 1024f)
    bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1fMB", bytes / (1024f * 1024f))
    else -> String.format(Locale.getDefault(), "%.2fGB", bytes / (1024f * 1024f * 1024f))
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    return if (m > 0) {
        String.format(Locale.getDefault(), "%d:%02d", m, s % 60)
    } else {
        String.format(Locale.getDefault(), "%ds", s)
    }
}
