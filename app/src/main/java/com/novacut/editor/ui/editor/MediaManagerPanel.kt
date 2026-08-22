package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.novacut.editor.ui.ClearCutTestTags
import com.novacut.editor.engine.MediaDiagnostic
import com.novacut.editor.engine.MediaHealthReport
import com.novacut.editor.engine.MediaRelinkProbe
import com.novacut.editor.engine.MediaColorConfidence
import com.novacut.editor.engine.SyncFrameDirection
import com.novacut.editor.engine.MetadataSidecarFormat
import com.novacut.editor.engine.MetadataSidecarKind
import com.novacut.editor.engine.MetadataSidecarTrack
import com.novacut.editor.engine.ProjectMediaAsset
import com.novacut.editor.engine.normalizeMediaAssetTags
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class MediaAsset(
    val assetId: String,
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val durationMs: Long,
    val usedInClipIds: List<String>,
    val isAccessible: Boolean,
    val relinkState: MediaRelinkProbe.RelinkState = if (isAccessible) {
        MediaRelinkProbe.RelinkState.OK
    } else {
        MediaRelinkProbe.RelinkState.MISSING
    },
    val relinkMessage: String? = null,
    val diagnostic: MediaDiagnostic? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaManagerPanel(
    tracks: List<Track>,
    persistedMediaAssets: List<ProjectMediaAsset>,
    relinkReports: Map<String, MediaRelinkProbe.ClipRelinkReport>,
    mediaHealthReport: MediaHealthReport?,
    metadataSidecarExport: MetadataSidecarExportUiState,
    onJumpToClip: (String) -> Unit,
    onJumpToSyncFrame: (String, SyncFrameDirection) -> Unit,
    onRelinkMedia: (Uri) -> Unit,
    onBulkRelinkMissing: () -> Unit,
    onRemoveUnused: () -> Unit,
    onUpdateAssetMetadata: (Uri, String, List<String>) -> Unit,
    onExportMetadataSidecar: (Uri, MetadataSidecarTrack, MetadataSidecarFormat) -> Unit,
    onShareMetadataSidecar: (MetadataSidecarExportFile) -> Unit,
    onDismissMetadataSidecarExport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    val context = LocalContext.current
    val diagnostics = mediaHealthReport?.diagnostics.orEmpty()
    val scanScope = androidx.compose.runtime.rememberCoroutineScope()
    var scanState by remember { mutableStateOf<MediaScanState>(MediaScanState.Idle) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var scanGeneration by remember { mutableLongStateOf(0L) }
    var query by remember { mutableStateOf(MediaBinQuery()) }

    fun startScan() {
        val generation = nextMediaScanGeneration(scanGeneration)
        val previousResult = scanState.result
        scanGeneration = generation
        scanJob?.cancel()
        scanState = MediaScanState.Scanning(previousResult)
        scanJob = scanScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    analyzeMediaAssets(
                        context = context,
                        tracks = tracks,
                        persistedMediaAssets = persistedMediaAssets,
                        relinkReports = relinkReports,
                        diagnosticsByUri = diagnostics.associateBy { it.uri },
                    )
                }
                if (scanGeneration == generation) {
                    scanState = MediaScanState.Ready(result)
                    scanJob = null
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (scanGeneration == generation) {
                    scanState = MediaScanState.Failed(previousResult)
                    scanJob = null
                }
            }
        }
    }

    fun cancelScan() {
        if (scanState.status() == MediaScanStatus.SCANNING) {
            scanGeneration = nextMediaScanGeneration(scanGeneration)
            scanJob?.cancel()
            scanJob = null
            scanState = MediaScanState.Cancelled(scanState.result)
        }
    }

    LaunchedEffect(context, tracks, persistedMediaAssets, relinkReports, diagnostics) {
        startScan()
    }

    val scanStatus = scanState.status()
    val scanResult = scanState.result
    val assets = scanResult.assets
    val scanIssues = scanResult.issues
    val isAnalyzing = scanStatus == MediaScanStatus.SCANNING
    val totalSize = assets.sumOf { it.fileSize }
    val missingCount = assets.count { !it.isAccessible }
    val healthBlockingCount = mediaHealthReport?.blockingCount ?: 0
    val healthWarningCount = mediaHealthReport?.warningCount ?: 0
    val emptyTrackCount = remember(tracks) {
        tracks.count { it.index >= 2 && it.clips.isEmpty() }
    }
    val statusLabel = when {
        scanStatus == MediaScanStatus.IDLE -> stringResource(R.string.media_manager_status_idle)
        isAnalyzing -> stringResource(R.string.media_manager_status_scanning)
        scanStatus == MediaScanStatus.FAILED -> stringResource(R.string.media_manager_status_scan_failed)
        scanStatus == MediaScanStatus.CANCELLED -> stringResource(R.string.media_manager_status_scan_cancelled)
        scanStatus == MediaScanStatus.READY_WITH_PARTIAL_RESULTS -> pluralStringResource(
            R.plurals.media_manager_status_scan_partial,
            scanIssues.size,
            scanIssues.size,
        )
        healthBlockingCount > 0 -> pluralStringResource(
            R.plurals.media_health_blocking_count,
            healthBlockingCount,
            healthBlockingCount
        )
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
        scanStatus == MediaScanStatus.FAILED -> ClearCutAccents.Red
        scanStatus == MediaScanStatus.CANCELLED -> ClearCutAccents.Peach
        scanStatus == MediaScanStatus.READY_WITH_PARTIAL_RESULTS -> ClearCutAccents.Peach
        isAnalyzing -> ClearCutAccents.Blue
        healthBlockingCount > 0 -> ClearCutAccents.Red
        missingCount > 0 -> ClearCutAccents.Red
        healthWarningCount > 0 -> ClearCutAccents.Peach
        emptyTrackCount > 0 -> ClearCutAccents.Yellow
        else -> ClearCutAccents.Green
    }
    val projectHealthLabel = when {
        mediaHealthReport == null -> stringResource(R.string.media_health_unavailable)
        healthBlockingCount > 0 -> pluralStringResource(
            R.plurals.media_health_blocking_count,
            healthBlockingCount,
            healthBlockingCount
        )
        healthWarningCount > 0 -> pluralStringResource(
            R.plurals.media_health_warning_count,
            healthWarningCount,
            healthWarningCount
        )
        else -> stringResource(R.string.media_health_ready)
    }
    val assetCountLabel = pluralStringResource(
        R.plurals.media_manager_asset_count,
        assets.size,
        assets.size
    )
    val visibleAssets = remember(assets, query) {
        filterAndSortMediaAssets(assets, query)
    }
    val emptyTrackLabel = pluralStringResource(
        R.plurals.media_manager_empty_tracks_count,
        emptyTrackCount,
        emptyTrackCount
    )

    PremiumEditorPanel(
        title = stringResource(R.string.media_manager_title),
        subtitle = stringResource(R.string.media_manager_subtitle),
        icon = Icons.Default.PermMedia,
        accent = if (missingCount > 0) ClearCutAccents.Red else ClearCutAccents.Blue,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.media_manager_close_cd),
        closeButtonTestTag = ClearCutTestTags.MEDIA_MANAGER_CLOSE,
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = if (missingCount > 0) ClearCutAccents.Red else ClearCutAccents.Blue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.media_manager_health_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.media_manager_health_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = if (isAnalyzing) "Analyzing..." else formatFileSize(totalSize),
                        accent = ClearCutAccents.Peach
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
                    accent = ClearCutAccents.Blue,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
                MediaHealthMetric(
                    title = stringResource(R.string.media_stat_size),
                    value = if (isAnalyzing) "..." else formatFileSize(totalSize),
                    accent = ClearCutAccents.Peach,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
                MediaHealthMetric(
                    title = stringResource(R.string.media_stat_missing),
                    value = if (isAnalyzing) "..." else missingCount.toString(),
                    accent = if (missingCount > 0) ClearCutAccents.Red else ClearCutAccents.Green,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
                MediaHealthMetric(
                    title = stringResource(R.string.media_stat_project_health),
                    value = projectHealthLabel,
                    accent = when {
                        healthBlockingCount > 0 -> ClearCutAccents.Red
                        healthWarningCount > 0 -> ClearCutAccents.Peach
                        mediaHealthReport == null -> semanticColors.overlayStrong
                        else -> ClearCutAccents.Green
                    },
                    modifier = Modifier.widthIn(min = 132.dp)
                )
                MediaHealthMetric(
                    title = stringResource(R.string.media_stat_empty_tracks),
                    value = emptyTrackCount.toString(),
                    accent = if (emptyTrackCount > 0) ClearCutAccents.Yellow else ClearCutAccents.Green,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
            }

            if (isAnalyzing) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = semanticColors.panelRaised,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, semanticColors.cardStroke)
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
                            color = ClearCutAccents.Blue,
                            strokeWidth = 2.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.media_manager_scanning_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = semanticColors.text,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.media_manager_scanning_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = semanticColors.subtext
                            )
                        }
                        OutlinedButton(
                            onClick = ::cancelScan,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(text = stringResource(R.string.media_manager_scan_cancel))
                        }
                    }
                }
            } else {
                MediaManagerMessageCard(
                    title = when {
                        scanStatus == MediaScanStatus.FAILED -> stringResource(
                            R.string.media_manager_scan_failed_title
                        )
                        scanStatus == MediaScanStatus.CANCELLED -> stringResource(
                            R.string.media_manager_scan_cancelled_title
                        )
                        scanStatus == MediaScanStatus.READY_WITH_PARTIAL_RESULTS -> stringResource(
                            R.string.media_manager_scan_partial_title
                        )
                        missingCount > 0 -> pluralStringResource(
                            R.plurals.media_manager_missing_title,
                            missingCount,
                            missingCount
                        )
                        assets.isEmpty() -> stringResource(R.string.media_manager_empty_title)
                        else -> stringResource(R.string.media_manager_ready_title)
                    },
                    body = when {
                        scanStatus == MediaScanStatus.FAILED -> stringResource(
                            R.string.media_manager_scan_failed_body
                        )
                        scanStatus == MediaScanStatus.CANCELLED -> stringResource(
                            R.string.media_manager_scan_cancelled_body
                        )
                        scanStatus == MediaScanStatus.READY_WITH_PARTIAL_RESULTS -> stringResource(
                            R.string.media_manager_scan_partial_body
                        )
                        missingCount > 0 -> stringResource(R.string.media_manager_missing_body)
                        assets.isEmpty() -> stringResource(R.string.media_manager_empty_body)
                        else -> stringResource(R.string.media_manager_ready_body)
                    },
                    accent = when {
                        scanStatus == MediaScanStatus.FAILED -> ClearCutAccents.Red
                        scanStatus == MediaScanStatus.CANCELLED -> ClearCutAccents.Peach
                        scanStatus == MediaScanStatus.READY_WITH_PARTIAL_RESULTS -> ClearCutAccents.Peach
                        missingCount > 0 -> ClearCutAccents.Red
                        assets.isEmpty() -> ClearCutAccents.Blue
                        else -> ClearCutAccents.Green
                    },
                    icon = when {
                        scanStatus == MediaScanStatus.FAILED -> Icons.Default.BrokenImage
                        scanStatus == MediaScanStatus.CANCELLED -> Icons.Default.PermMedia
                        scanStatus == MediaScanStatus.READY_WITH_PARTIAL_RESULTS -> Icons.Default.Link
                        missingCount > 0 -> Icons.Default.BrokenImage
                        assets.isEmpty() -> Icons.Default.PermMedia
                        else -> Icons.Default.Link
                    }
                )
                if (scanStatus == MediaScanStatus.FAILED || scanStatus == MediaScanStatus.CANCELLED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = ::startScan,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(text = stringResource(R.string.media_manager_scan_retry))
                    }
                }
            }
        }

        if (!isAnalyzing && scanIssues.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            MediaScanIssueCard(
                issues = scanIssues,
                onRetry = ::startScan,
            )
        }

        if (!isAnalyzing && missingCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onBulkRelinkMissing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClearCutAccents.Red,
                    contentColor = semanticColors.surfaceBase
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.media_manager_bulk_relink,
                        missingCount,
                        missingCount
                    )
                )
            }
        }

        if (metadataSidecarExport.isExporting ||
            metadataSidecarExport.file != null ||
            metadataSidecarExport.errorMessage != null
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            MetadataSidecarExportCard(
                state = metadataSidecarExport,
                onShare = onShareMetadataSidecar,
                onDismiss = onDismissMetadataSidecarExport,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Blue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.media_manager_assets_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.media_manager_assets_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                PremiumPanelPill(
                    text = assetCountLabel,
                    accent = when {
                        missingCount > 0 -> ClearCutAccents.Peach
                        assets.isEmpty() -> semanticColors.overlay
                        else -> ClearCutAccents.Blue
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query.search,
                onValueChange = { value -> query = query.copy(search = value) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                label = { Text(text = stringResource(R.string.media_bin_search_label)) },
                placeholder = { Text(text = stringResource(R.string.media_bin_search_placeholder)) },
            )
            Text(
                text = stringResource(
                    R.string.media_bin_visible_summary,
                    visibleAssets.size,
                    assets.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = semanticColors.subtext,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaBinFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = query.filter == filter,
                        onClick = { query = query.copy(filter = filter) },
                        label = { Text(text = filter.localizedLabel()) },
                        leadingIcon = if (query.filter == filter) {
                            {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ClearCutAccents.Blue.copy(alpha = 0.16f),
                            selectedLabelColor = ClearCutAccents.Blue,
                            selectedLeadingIconColor = ClearCutAccents.Blue,
                        ),
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaBinSort.entries.forEach { sort ->
                    FilterChip(
                        selected = query.sort == sort,
                        onClick = { query = query.copy(sort = sort) },
                        label = { Text(text = sort.localizedLabel()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ClearCutAccents.Teal.copy(alpha = 0.16f),
                            selectedLabelColor = ClearCutAccents.Teal,
                        ),
                    )
                }
            }

            when {
                isAnalyzing -> Unit
                assets.isEmpty() -> {
                    MediaManagerMessageCard(
                        title = stringResource(R.string.media_manager_empty_title),
                        body = stringResource(R.string.media_manager_empty_body),
                        accent = ClearCutAccents.Blue,
                        icon = Icons.Default.PermMedia
                    )
                }

                visibleAssets.isEmpty() -> {
                    MediaManagerMessageCard(
                        title = stringResource(R.string.media_bin_no_matches_title),
                        body = stringResource(R.string.media_bin_no_matches_body),
                        accent = ClearCutAccents.Peach,
                        icon = Icons.Default.Search,
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        visibleAssets.forEach { asset ->
                            MediaAssetCard(
                                asset = asset,
                                onJumpToClip = onJumpToClip,
                                onJumpToSyncFrame = onJumpToSyncFrame,
                                onRelinkMedia = onRelinkMedia,
                                onExportMetadataSidecar = onExportMetadataSidecar,
                                onUpdateAssetMetadata = onUpdateAssetMetadata,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = if (emptyTrackCount > 0) ClearCutAccents.Yellow else ClearCutAccents.Green) {
            Text(
                text = stringResource(R.string.media_manager_cleanup_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = if (emptyTrackCount > 0) {
                    stringResource(R.string.media_manager_cleanup_needs_trim)
                } else {
                    stringResource(R.string.media_manager_cleanup_ready)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            PremiumPanelPill(
                text = emptyTrackLabel,
                accent = if (emptyTrackCount > 0) ClearCutAccents.Yellow else ClearCutAccents.Green
            )

            Button(
                onClick = onRemoveUnused,
                enabled = emptyTrackCount > 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClearCutAccents.Yellow,
                    contentColor = semanticColors.surfaceBase,
                    disabledContainerColor = semanticColors.surface,
                    disabledContentColor = semanticColors.subtext
                ),
                shape = RoundedCornerShape(12.dp)
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
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = semanticColors.subtext
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
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
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
                shape = RoundedCornerShape(12.dp),
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
                    color = semanticColors.subtext
                )
            }
        }
    }
}

@Composable
private fun MediaScanIssueCard(
    issues: List<MediaScanIssue>,
    onRetry: () -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    val providerFailureCount = issues.count { it.kind == MediaScanIssueKind.PROVIDER_FAILURE }
    val skippedCount = issues.count { it.kind == MediaScanIssueKind.SKIPPED }
    val accent = ClearCutAccents.Peach
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.media_manager_scan_issue_title),
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            if (providerFailureCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.media_manager_scan_provider_failure_count,
                        providerFailureCount,
                        providerFailureCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext,
                )
            }
            if (skippedCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.media_manager_scan_skipped_count,
                        skippedCount,
                        skippedCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext,
                )
            }
            issues.take(3).forEach { issue ->
                Text(
                    text = stringResource(
                        R.string.media_manager_scan_issue_asset,
                        issue.fileName,
                        issue.kind.localizedLabel(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (issues.size > 3) {
                Text(
                    text = pluralStringResource(
                        R.plurals.media_manager_scan_issue_more,
                        issues.size - 3,
                        issues.size - 3,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext,
                )
            }
            Text(
                text = stringResource(R.string.media_manager_scan_issue_action),
                style = MaterialTheme.typography.bodySmall,
                color = semanticColors.subtext,
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(text = stringResource(R.string.media_manager_scan_retry))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataSidecarExportCard(
    state: MetadataSidecarExportUiState,
    onShare: (MetadataSidecarExportFile) -> Unit,
    onDismiss: () -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    val file = state.file
    val accent = when {
        state.errorMessage != null -> ClearCutAccents.Peach
        file != null -> ClearCutAccents.Green
        else -> ClearCutAccents.Blue
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.media_sidecar_export_title),
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            when {
                state.isExporting -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = accent,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.media_sidecar_exporting),
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.subtext,
                        )
                    }
                }
                state.errorMessage != null -> {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.subtext,
                    )
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                    ) {
                        Text(text = stringResource(R.string.media_sidecar_dismiss))
                    }
                }
                file != null -> {
                    Text(
                        text = state.message ?: stringResource(R.string.media_sidecar_export_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.subtext,
                    )
                    Text(
                        text = stringResource(
                            R.string.media_sidecar_file_format,
                            file.fileName,
                            formatFileSize(file.sizeBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.subtext,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onShare(file) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.media_sidecar_share))
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, semanticColors.cardStroke),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = semanticColors.subtext),
                        ) {
                            Text(text = stringResource(R.string.media_sidecar_dismiss))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaAssetCard(
    asset: MediaAsset,
    onJumpToClip: (String) -> Unit,
    onJumpToSyncFrame: (String, SyncFrameDirection) -> Unit,
    onRelinkMedia: (Uri) -> Unit,
    onExportMetadataSidecar: (Uri, MetadataSidecarTrack, MetadataSidecarFormat) -> Unit,
    onUpdateAssetMetadata: (Uri, String, List<String>) -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    var isEditingMetadata by remember(asset.assetId, asset.notes, asset.tags) { mutableStateOf(false) }
    var notesDraft by remember(asset.assetId, asset.notes) { mutableStateOf(asset.notes) }
    var tagsDraft by remember(asset.assetId, asset.tags) {
        mutableStateOf(asset.tags.joinToString(", "))
    }
    val accent = when (asset.relinkState) {
        MediaRelinkProbe.RelinkState.OK -> ClearCutAccents.Blue
        MediaRelinkProbe.RelinkState.MISSING -> ClearCutAccents.Red
        MediaRelinkProbe.RelinkState.UNKNOWN -> ClearCutAccents.Peach
    }
    val statusLabel = stringResource(
        when (asset.relinkState) {
            MediaRelinkProbe.RelinkState.OK -> R.string.media_status_online
            MediaRelinkProbe.RelinkState.MISSING -> R.string.media_status_missing
            MediaRelinkProbe.RelinkState.UNKNOWN -> R.string.media_status_unverified
        }
    )
    val usageLabel = pluralStringResource(
        R.plurals.media_used_in_clip_count,
        asset.usedInClipIds.size,
        asset.usedInClipIds.size
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (asset.isAccessible) semanticColors.panelRaised else ClearCutAccents.Red.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (asset.isAccessible) semanticColors.cardStroke else ClearCutAccents.Red.copy(alpha = 0.2f)
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
                        shape = RoundedCornerShape(12.dp),
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
                            color = if (asset.isAccessible) semanticColors.text else ClearCutAccents.Red,
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
                            color = semanticColors.subtext
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
                    title = stringResource(
                        if (asset.relinkState == MediaRelinkProbe.RelinkState.UNKNOWN) {
                            R.string.media_unverified_asset_title
                        } else {
                            R.string.media_missing_asset_title
                        }
                    ),
                    body = asset.relinkMessage ?: stringResource(
                        if (asset.relinkState == MediaRelinkProbe.RelinkState.UNKNOWN) {
                            R.string.media_source_unverified
                        } else {
                            R.string.media_source_unavailable
                        }
                    ),
                    accent = accent,
                    icon = Icons.Default.BrokenImage
                )
            }

            if (isEditingMetadata) {
                OutlinedTextField(
                    value = notesDraft,
                    onValueChange = { notesDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text(text = stringResource(R.string.media_bin_notes_label)) },
                )
                OutlinedTextField(
                    value = tagsDraft,
                    onValueChange = { tagsDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.media_bin_tags_label)) },
                    supportingText = {
                        Text(text = stringResource(R.string.media_bin_tags_supporting))
                    },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onUpdateAssetMetadata(
                                asset.uri,
                                notesDraft,
                                normalizeMediaAssetTags(listOf(tagsDraft)),
                            )
                            isEditingMetadata = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ClearCutAccents.Teal.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Teal),
                    ) {
                        Text(text = stringResource(R.string.media_bin_save))
                    }
                    OutlinedButton(
                        onClick = {
                            notesDraft = asset.notes
                            tagsDraft = asset.tags.joinToString(", ")
                            isEditingMetadata = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, semanticColors.cardStroke),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = semanticColors.subtext),
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            } else {
                if (asset.notes.isNotBlank() || asset.tags.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = semanticColors.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, semanticColors.cardStroke),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            asset.notes.takeIf { it.isNotBlank() }?.let { notes ->
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semanticColors.subtext,
                                )
                            }
                            if (asset.tags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    asset.tags.forEach { tag ->
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ClearCutAccents.Teal,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { isEditingMetadata = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ClearCutAccents.Teal.copy(alpha = 0.25f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Teal),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.media_bin_edit_cd),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.media_bin_edit))
                }
            }

            asset.diagnostic?.let { diagnostic ->
                MediaDiagnosticCard(
                    diagnostic = diagnostic,
                    clipId = asset.usedInClipIds.firstOrNull(),
                    onJumpToSyncFrame = onJumpToSyncFrame,
                    onExportMetadataSidecar = { track, format ->
                        onExportMetadataSidecar(asset.uri, track, format)
                    },
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PremiumPanelPill(
                    text = usageLabel,
                    accent = if (asset.isAccessible) ClearCutAccents.Green else ClearCutAccents.Peach
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!asset.isAccessible) {
                        OutlinedButton(
                            onClick = { onRelinkMedia(asset.uri) },
                            shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ClearCutAccents.Blue.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Blue)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaDiagnosticCard(
    diagnostic: MediaDiagnostic,
    clipId: String?,
    onJumpToSyncFrame: (String, SyncFrameDirection) -> Unit,
    onExportMetadataSidecar: (MetadataSidecarTrack, MetadataSidecarFormat) -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    val colorLabel = when (diagnostic.colorConfidence) {
        MediaColorConfidence.HDR -> diagnostic.hdrFormats.sorted().joinToString().ifBlank { "HDR" }
        MediaColorConfidence.SDR -> listOfNotNull(diagnostic.colorStandard, diagnostic.colorTransfer)
            .joinToString()
            .ifBlank { "SDR" }
        MediaColorConfidence.UNKNOWN -> stringResource(R.string.media_diagnostics_color_unknown)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = semanticColors.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, semanticColors.cardStroke),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.media_diagnostics_title),
                style = MaterialTheme.typography.labelLarge,
                color = semanticColors.text,
                fontWeight = FontWeight.SemiBold,
            )
            diagnostic.probeError?.let { error ->
                Text(
                    text = stringResource(R.string.media_diagnostics_unavailable, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = ClearCutAccents.Peach,
                )
            } ?: run {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    diagnostic.containerMimeType?.let { mime ->
                        Text(
                            text = stringResource(R.string.media_diagnostics_container, mime),
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.subtext,
                        )
                    }
                    diagnostic.durationMs?.let { durationMs ->
                        Text(
                            text = stringResource(
                                R.string.media_diagnostics_duration,
                                formatDuration(durationMs),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.subtext,
                        )
                    }
                    diagnostic.rotationDegrees?.takeIf { it != 0 }?.let { rotation ->
                        Text(
                            text = stringResource(R.string.media_diagnostics_rotation, rotation),
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.subtext,
                        )
                    }
                    Text(
                        text = stringResource(R.string.media_diagnostics_color, colorLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (diagnostic.colorConfidence) {
                            MediaColorConfidence.HDR -> ClearCutAccents.Peach
                            MediaColorConfidence.SDR -> ClearCutAccents.Green
                            MediaColorConfidence.UNKNOWN -> semanticColors.subtext
                        },
                    )
                }

                diagnostic.tracks.forEach { track ->
                    val mediaType = when (track.mediaType) {
                        "video" -> stringResource(R.string.media_diagnostics_video_track)
                        "audio" -> stringResource(R.string.media_diagnostics_audio_track)
                        else -> track.mediaType
                    }
                    Text(
                        text = stringResource(
                            R.string.media_diagnostics_track,
                            mediaType,
                            track.codec ?: track.mimeType ?: stringResource(R.string.media_diagnostics_unknown),
                            track.language ?: stringResource(R.string.media_diagnostics_language_unknown),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.subtext,
                    )
                    if (track.isVideo) {
                        val dimensions = if (track.width != null && track.height != null) {
                            "${track.width}×${track.height}"
                        } else {
                            stringResource(R.string.media_diagnostics_unknown)
                        }
                        val frameRate = track.frameRate?.let { rate ->
                            stringResource(R.string.media_diagnostics_fps, rate)
                        }
                        val syncFrames = if (track.syncFrameScanTruncated) {
                            stringResource(R.string.media_diagnostics_sync_frames_approx, track.syncFrameCount)
                        } else {
                            stringResource(R.string.media_diagnostics_sync_frames, track.syncFrameCount)
                        }
                        Text(
                            text = stringResource(
                                R.string.media_diagnostics_video_details,
                                dimensions,
                                frameRate ?: stringResource(R.string.media_diagnostics_fps_unknown),
                                syncFrames,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.subtext,
                        )
                    }
                }

                if (diagnostic.metadataSidecars.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.media_sidecar_metadata_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = semanticColors.text,
                        fontWeight = FontWeight.SemiBold,
                    )
                    diagnostic.metadataSidecars.forEach { sidecar ->
                        val kindLabel = when (sidecar.kind) {
                            MetadataSidecarKind.GPS -> stringResource(R.string.media_sidecar_gps)
                            MetadataSidecarKind.SUBTITLE -> stringResource(R.string.media_sidecar_subtitle)
                            MetadataSidecarKind.OTHER -> stringResource(R.string.media_sidecar_other)
                        }
                        Text(
                            text = stringResource(
                                R.string.media_sidecar_track,
                                kindLabel,
                                sidecar.mimeType ?: stringResource(R.string.media_diagnostics_unknown),
                                sidecar.language ?: stringResource(R.string.media_diagnostics_language_unknown),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.subtext,
                        )
                        if (sidecar.supportedFormats.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                sidecar.supportedFormats
                                    .sortedBy(MetadataSidecarFormat::ordinal)
                                    .forEach { format ->
                                        OutlinedButton(
                                            onClick = { onExportMetadataSidecar(sidecar, format) },
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                ClearCutAccents.Teal.copy(alpha = 0.25f),
                                            ),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = ClearCutAccents.Teal,
                                            ),
                                        ) {
                                            Text(text = format.name)
                                        }
                                    }
                            }
                        } else {
                            Text(
                                text = sidecar.unsupportedReason
                                    ?: stringResource(R.string.media_sidecar_unsupported),
                                style = MaterialTheme.typography.bodySmall,
                                color = ClearCutAccents.Peach,
                            )
                        }
                    }
                }

                diagnostic.timestampRisk?.let { risk ->
                    Text(
                        text = stringResource(R.string.media_diagnostics_timestamp_risk, risk),
                        style = MaterialTheme.typography.bodySmall,
                        color = ClearCutAccents.Peach,
                    )
                }
                diagnostic.colorRisk?.let { risk ->
                    Text(
                        text = stringResource(R.string.media_diagnostics_color_risk, risk),
                        style = MaterialTheme.typography.bodySmall,
                        color = ClearCutAccents.Peach,
                    )
                }
                if (clipId != null && diagnostic.keyframeCount > 0) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onJumpToSyncFrame(clipId, SyncFrameDirection.PREVIOUS) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ClearCutAccents.Blue.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Blue),
                        ) {
                            Text(text = stringResource(R.string.media_diagnostics_previous_sync))
                        }
                        OutlinedButton(
                            onClick = { onJumpToSyncFrame(clipId, SyncFrameDirection.NEXT) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ClearCutAccents.Blue.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Blue),
                        ) {
                            Text(text = stringResource(R.string.media_diagnostics_next_sync))
                        }
                    }
                }
            }
        }
    }
}

private fun analyzeMediaAssets(
    context: Context,
    tracks: List<Track>,
    persistedMediaAssets: List<ProjectMediaAsset>,
    relinkReports: Map<String, MediaRelinkProbe.ClipRelinkReport>,
    diagnosticsByUri: Map<String, MediaDiagnostic>,
): MediaScanResult {
    val clipsByUri = mutableMapOf<String, MutableList<Clip>>()
    val issues = mutableListOf<MediaScanIssue>()

    tracks.forEach { track ->
        track.clips.forEach { clip ->
            val key = clip.sourceUri.toString()
            clipsByUri.getOrPut(key) { mutableListOf() }.add(clip)
        }
    }

    val persistedByUri = persistedMediaAssets
        .flatMap { asset ->
            listOf(asset.managedUri, asset.originalUri)
                .filter { it.isNotBlank() }
                .map { uri -> uri to asset }
        }
        .toMap()

    val referenced = clipsByUri.map { (_, clips) ->
        val uri = clips.first().sourceUri
        val persisted = persistedByUri[uri.toString()]
        val probe = probeMediaAsset(context, uri)

        val relinkReport = clips.asSequence()
            .mapNotNull { relinkReports[it.id] }
            .sortedBy { report ->
                when (report.state) {
                    MediaRelinkProbe.RelinkState.MISSING -> 0
                    MediaRelinkProbe.RelinkState.UNKNOWN -> 1
                    MediaRelinkProbe.RelinkState.OK -> 2
                }
            }
            .firstOrNull()
        val relinkState = relinkReport?.state ?: if (probe.accessible) {
            MediaRelinkProbe.RelinkState.OK
        } else {
            MediaRelinkProbe.RelinkState.MISSING
        }
        val effectiveAccessible = relinkState == MediaRelinkProbe.RelinkState.OK

        val asset = MediaAsset(
            assetId = persisted?.assetId ?: uri.toString(),
            uri = uri,
            fileName = persisted?.displayName ?: probe.fileName,
            fileSize = probe.fileSize.takeIf { it > 0L } ?: persisted?.sizeBytes ?: 0L,
            durationMs = clips.first().sourceDurationMs,
            usedInClipIds = clips.map { it.id },
            isAccessible = effectiveAccessible,
            relinkState = relinkState,
            relinkMessage = relinkReport
                ?.takeIf { it.state != MediaRelinkProbe.RelinkState.OK }
                ?.userMessage,
            diagnostic = diagnosticsByUri[uri.toString()],
            notes = persisted?.notes.orEmpty(),
            tags = persisted?.tags.orEmpty(),
        )
        probe.issueKind?.let { issueKind ->
            issues += MediaScanIssue(
                assetId = asset.assetId,
                fileName = asset.fileName,
                kind = issueKind,
            )
        }
        asset
    }

    val referencedUris = clipsByUri.keys
    val unused = persistedMediaAssets
        .distinctBy { it.assetId }
        .filter { asset ->
            asset.managedUri !in referencedUris && asset.originalUri !in referencedUris
        }
        .map { persisted ->
            val uri = Uri.parse(persisted.managedUri.ifBlank { persisted.originalUri })
            val probe = probeMediaAsset(context, uri)
            val accessible = probe.accessible && persisted.importStatus != "missing"
            val asset = MediaAsset(
                assetId = persisted.assetId,
                uri = uri,
                fileName = persisted.displayName ?: probe.fileName,
                fileSize = probe.fileSize.takeIf { it > 0L } ?: persisted.sizeBytes,
                durationMs = persisted.durationMs ?: 0L,
                usedInClipIds = emptyList(),
                isAccessible = accessible,
                relinkState = if (accessible) {
                    MediaRelinkProbe.RelinkState.OK
                } else {
                    MediaRelinkProbe.RelinkState.MISSING
                },
                relinkMessage = if (accessible) null else persisted.importStatus,
                diagnostic = diagnosticsByUri[uri.toString()],
                notes = persisted.notes,
                tags = persisted.tags,
            )
            probe.issueKind?.let { issueKind ->
                issues += MediaScanIssue(
                    assetId = asset.assetId,
                    fileName = asset.fileName,
                    kind = issueKind,
                )
            }
            asset
        }

    return MediaScanResult(
        assets = (referenced + unused)
            .distinctBy { it.assetId }
            .sortedWith(compareBy<MediaAsset> { it.isAccessible }.thenByDescending { it.usedInClipIds.size }),
        issues = issues
            .distinctBy { issue -> issue.assetId to issue.kind }
            .sortedWith(compareBy<MediaScanIssue> { it.kind }.thenBy { it.fileName.lowercase(Locale.ROOT) }),
    )
}

private data class MediaAssetProbe(
    val fileName: String,
    val fileSize: Long,
    val accessible: Boolean,
    val issueKind: MediaScanIssueKind? = null,
)

private fun probeMediaAsset(context: Context, uri: Uri): MediaAssetProbe {
    var fileName = uri.lastPathSegment ?: "Unknown"
    var fileSize = 0L
    var accessible = false
    var issueKind: MediaScanIssueKind? = null

    try {
        if (uri.toString().isBlank()) {
            issueKind = MediaScanIssueKind.SKIPPED
        } else if (uri.scheme == "file") {
            val localFile = uri.path?.let(::File)
            if (localFile != null) {
                if (localFile.name.isNotBlank()) fileName = localFile.name
                accessible = localFile.exists()
                if (accessible) fileSize = localFile.length()
            } else {
                issueKind = MediaScanIssueKind.SKIPPED
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
                    if (fileSize <= 0L && descriptor.length > 0L) fileSize = descriptor.length
                }
            }
            if (!accessible) issueKind = MediaScanIssueKind.SKIPPED
        }
    } catch (_: Exception) {
        // The asset remains visible as missing so the user can search/filter it
        // and choose a relink action. The issue is surfaced separately so a
        // provider failure is not mistaken for an ordinary missing file.
        issueKind = MediaScanIssueKind.PROVIDER_FAILURE
    }

    return MediaAssetProbe(
        fileName = fileName,
        fileSize = fileSize,
        accessible = accessible,
        issueKind = issueKind,
    )
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1fKB", bytes / 1024f)
    bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1fMB", bytes / (1024f * 1024f))
    else -> String.format(Locale.getDefault(), "%.2fGB", bytes / (1024f * 1024f * 1024f))
}

@Composable
private fun MediaBinFilter.localizedLabel(): String = when (this) {
    MediaBinFilter.ALL -> stringResource(R.string.media_bin_filter_all)
    MediaBinFilter.MISSING -> stringResource(R.string.media_bin_filter_missing)
    MediaBinFilter.RELINK_NEEDED -> stringResource(R.string.media_bin_filter_relink)
    MediaBinFilter.USED -> stringResource(R.string.media_bin_filter_used)
    MediaBinFilter.UNUSED -> stringResource(R.string.media_bin_filter_unused)
    MediaBinFilter.TAGGED -> stringResource(R.string.media_bin_filter_tagged)
}

@Composable
private fun MediaScanIssueKind.localizedLabel(): String = when (this) {
    MediaScanIssueKind.PROVIDER_FAILURE -> stringResource(R.string.media_manager_scan_issue_provider)
    MediaScanIssueKind.SKIPPED -> stringResource(R.string.media_manager_scan_issue_skipped)
}

@Composable
private fun MediaBinSort.localizedLabel(): String = when (this) {
    MediaBinSort.STATUS -> stringResource(R.string.media_bin_sort_status)
    MediaBinSort.NAME -> stringResource(R.string.media_bin_sort_name)
    MediaBinSort.SIZE -> stringResource(R.string.media_bin_sort_size)
    MediaBinSort.DURATION -> stringResource(R.string.media_bin_sort_duration)
    MediaBinSort.USAGE -> stringResource(R.string.media_bin_sort_usage)
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
