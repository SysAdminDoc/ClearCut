package com.novacut.editor.ui.mediapicker

import com.novacut.editor.ui.theme.ClearCutAccents
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.novacut.editor.R
import com.novacut.editor.engine.IngestResult
import com.novacut.editor.engine.checkFreeSpace
import com.novacut.editor.engine.finalizePendingCameraCapture
import com.novacut.editor.engine.importUriToManagedMedia
import com.novacut.editor.engine.importUriToManagedMediaWithProgress
import com.novacut.editor.engine.pendingCameraCaptureDir
import com.novacut.editor.engine.querySourceSize
import com.novacut.editor.engine.resolveManagedMediaExtension
import com.novacut.editor.ui.ClearCutTestTags
import com.novacut.editor.ui.editor.PremiumEditorPanel
import com.novacut.editor.ui.editor.PremiumPanelCard
import com.novacut.editor.ui.editor.PremiumPanelPill
import com.novacut.editor.ui.editor.PremiumSnackbarHost
import com.novacut.editor.ui.editor.ToastSeverity
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.ClearCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [CaptureVideo] replacement that explicitly grants read+write URI permissions.
 * Android 18 will stop auto-granting these for ACTION_VIDEO_CAPTURE / ACTION_IMAGE_CAPTURE
 * intents. Adding the flags now is a no-op on current versions but future-proofs
 * the camera handoff. See Android 17 behavior-changes-all → "Restrict implicit URI grants".
 */
private class CaptureVideoWithGrant : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == android.app.Activity.RESULT_OK
}

private data class MediaPickerOperationState(
    val title: String,
    val description: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaPickerSheet(
    onMediaSelected: (Uri, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingMediaType by remember { mutableStateOf("video") }
    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    var cameraVideoFile by remember { mutableStateOf<File?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var operationState by remember { mutableStateOf<MediaPickerOperationState?>(null) }
    val actionsEnabled = operationState == null

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                // Take persistent permission
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    android.util.Log.w("MediaPicker", "Failed to persist URI permission", e)
                }
            }
            coroutineScope.launch {
                operationState = MediaPickerOperationState(
                    title = context.getString(R.string.media_picker_importing_batch_title),
                    description = context.getString(R.string.media_picker_importing_batch_description)
                )
                try {
                    val importResult = withContext(Dispatchers.IO) {
                        val sortedUris = sortMediaChronologically(context, uris)
                        var spaceChecked = false
                        sortedUris.mapNotNull { uri ->
                            val mediaType = resolvePickedMediaType(context, uri, fallbackType = "video")
                            try {
                                if (!spaceChecked) {
                                    val totalSize = sortedUris.sumOf { u ->
                                        querySourceSize(context, u).coerceAtLeast(0L)
                                    }
                                    if (totalSize > 0L && !checkFreeSpace(context, totalSize)) {
                                        return@withContext emptyList<Pair<Uri, String>>() to sortedUris.size
                                    }
                                    spaceChecked = true
                                }
                                importUriToManagedMedia(context, uri, mediaType)?.let { localUri ->
                                    localUri to mediaType
                                }
                            } finally {
                                releasePersistedReadPermission(context, uri)
                            }
                        } to sortedUris.size
                    }
                    val (mediaItems, requestedCount) = importResult
                    mediaItems.forEach { (localUri, mediaType) -> onMediaSelected(localUri, mediaType) }
                    if (mediaItems.size < requestedCount) {
                        permissionMessage = if (mediaItems.isEmpty()) {
                            context.getString(R.string.media_picker_local_copy_failed)
                        } else {
                            context.getString(R.string.media_picker_some_imports_failed)
                        }
                    }
                } finally {
                    operationState = null
                }
            }
        }
    }

    fun importPickedMedia(uri: Uri, mediaType: String, title: String, description: String) {
        coroutineScope.launch {
            operationState = MediaPickerOperationState(title = title, description = description)
            try {
                val result = withContext(Dispatchers.IO) {
                    try {
                        importUriToManagedMediaWithProgress(context, uri, mediaType)
                    } finally {
                        releasePersistedReadPermission(context, uri)
                    }
                }
                when (result) {
                    is IngestResult.Success -> onMediaSelected(result.managedUri, mediaType)
                    is IngestResult.InsufficientSpace ->
                        permissionMessage = context.getString(R.string.media_picker_insufficient_space)
                    else ->
                        permissionMessage = context.getString(R.string.media_picker_local_copy_failed)
                }
            } finally {
                operationState = null
            }
        }
    }

    fun importDroppedMedia(uris: List<Uri>) {
        if (uris.isEmpty() || !actionsEnabled) return
        coroutineScope.launch {
            operationState = MediaPickerOperationState(
                title = context.getString(R.string.media_picker_importing_batch_title),
                description = context.getString(R.string.media_picker_importing_batch_description)
            )
            try {
                val imported = withContext(Dispatchers.IO) {
                    val sorted = sortMediaChronologically(context, uris)
                    val totalSize = sorted.sumOf { querySourceSize(context, it).coerceAtLeast(0L) }
                    if (totalSize > 0L && !checkFreeSpace(context, totalSize)) {
                        return@withContext emptyList<Pair<Uri, String>>()
                    }
                    sorted.mapNotNull { uri ->
                        val type = resolvePickedMediaType(context, uri, fallbackType = "video")
                        importUriToManagedMedia(context, uri, type)?.let { localUri -> localUri to type }
                    }
                }
                imported.forEach { (localUri, type) -> onMediaSelected(localUri, type) }
                if (imported.size < uris.size) {
                    permissionMessage = if (imported.isEmpty()) {
                        context.getString(R.string.media_picker_local_copy_failed)
                    } else {
                        context.getString(R.string.media_picker_some_imports_failed)
                    }
                }
            } finally {
                operationState = null
            }
        }
    }

    fun droppedUris(event: DragAndDropEvent): List<Uri> {
        val clipData = event.toAndroidDragEvent().clipData ?: return emptyList()
        return (0 until clipData.itemCount).mapNotNull { index ->
            clipData.getItemAt(index).uri
        }
    }

    val mediaDropTarget = remember(actionsEnabled) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val uris = droppedUris(event)
                if (uris.isEmpty() || !actionsEnabled) return false
                importDroppedMedia(uris)
                return true
            }
        }
    }

    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                android.util.Log.w("MediaPicker", "Failed to persist URI permission", e)
            }
            // The ACTION_OPEN_DOCUMENT MIME filter is advisory — on some devices
            // the system picker still allows selecting items from other categories.
            // Verify the resolver's reported MIME before routing an audio pick to
            // the audio track; a mis-routed video or image here would silently add
            // a broken clip to the AUDIO track and fail playback later.
            if (pendingMediaType == "audio") {
                val mimeType = context.contentResolver.getType(uri).orEmpty()
                if (!mimeType.startsWith("audio/") && mimeType != "application/ogg") {
                    permissionMessage = context.getString(R.string.media_picker_audio_only)
                    return@rememberLauncherForActivityResult
                }
            }
            importPickedMedia(
                uri = uri,
                mediaType = pendingMediaType,
                title = context.getString(R.string.media_picker_importing_batch_title),
                description = context.getString(R.string.media_picker_importing_batch_description)
            )
        }
    }

    // Photo Picker (Android 13+)
    val usePhotoPicker = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val photoPickerVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            importPickedMedia(
                uri = uri,
                mediaType = "video",
                title = context.getString(R.string.media_picker_importing_video_title),
                description = context.getString(R.string.media_picker_importing_video_description)
            )
        }
    }

    val photoPickerImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            importPickedMedia(
                uri = uri,
                mediaType = "image",
                title = context.getString(R.string.media_picker_importing_image_title),
                description = context.getString(R.string.media_picker_importing_image_description)
            )
        }
    }

    val photoPickerMultiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                operationState = MediaPickerOperationState(
                    title = context.getString(R.string.media_picker_importing_batch_title),
                    description = context.getString(R.string.media_picker_importing_batch_description)
                )
                try {
                    val imported = withContext(Dispatchers.IO) {
                        sortMediaChronologically(context, uris).mapNotNull { uri ->
                            val type = resolvePickedMediaType(context, uri, fallbackType = "video")
                            importUriToManagedMedia(context, uri, type)?.let { localUri ->
                                localUri to type
                            }
                        }
                    }
                    imported.forEach { (localUri, type) -> onMediaSelected(localUri, type) }
                    if (imported.size < uris.size) {
                        permissionMessage = if (imported.isEmpty()) {
                            context.getString(R.string.media_picker_local_copy_failed)
                        } else {
                            context.getString(R.string.media_picker_some_imports_failed)
                        }
                    }
                } finally {
                    operationState = null
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = CaptureVideoWithGrant()
    ) { success ->
        val capturedFile = cameraVideoFile
        cameraVideoUri = null
        cameraVideoFile = null
        if (success) {
            coroutineScope.launch {
                operationState = MediaPickerOperationState(
                    title = context.getString(R.string.media_picker_importing_capture_title),
                    description = context.getString(R.string.media_picker_importing_capture_description)
                )
                try {
                    val finalizedUri = withContext(Dispatchers.IO) {
                        capturedFile?.let { finalizePendingCameraCapture(context, it, "video") }
                    }
                    if (finalizedUri != null) {
                        onMediaSelected(finalizedUri, "video")
                    } else {
                        permissionMessage = context.getString(R.string.media_picker_camera_empty_capture)
                        withContext(Dispatchers.IO) { capturedFile?.delete() }
                    }
                } finally {
                    operationState = null
                }
            }
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                capturedFile?.delete()
            }
        }
    }

    fun startCameraCapture() {
        val cameraDir = pendingCameraCaptureDir(context).apply { mkdirs() }
        val videoFile = File(cameraDir, "clearcut_${System.currentTimeMillis()}.mp4")
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                videoFile
            )
        }.onFailure { error ->
            Log.w("MediaPicker", "Failed to create camera capture content URI", error)
            videoFile.delete()
        }.getOrNull()

        if (uri == null) {
            permissionMessage = context.getString(R.string.media_picker_camera_handoff_failed)
            return
        }

        cameraVideoFile = videoFile
        cameraVideoUri = uri
        runCatching { cameraLauncher.launch(uri) }
            .onFailure { error ->
                Log.w("MediaPicker", "Failed to launch camera capture", error)
                cameraVideoFile = null
                cameraVideoUri = null
                videoFile.delete()
                permissionMessage = context.getString(R.string.media_picker_camera_handoff_failed)
            }
    }

    // Clean up stale, unfinalized camera captures without touching imported media that
    // projects already depend on.
    LaunchedEffect(Unit) {
        val cameraDir = pendingCameraCaptureDir(context)
        if (cameraDir.exists()) {
            val cutoff = System.currentTimeMillis() - 3_600_000L
            cameraDir.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }
                ?.forEach { runCatching { it.delete() } }
        }
    }

    val librarySourceLabel = if (usePhotoPicker) {
        stringResource(R.string.media_picker_source_photo_picker)
    } else {
        stringResource(R.string.media_picker_source_files)
    }

    LaunchedEffect(permissionMessage) {
        if (permissionMessage != null) {
            delay(3500L)
            permissionMessage = null
        }
    }

    PremiumEditorPanel(
        title = stringResource(R.string.media_picker_title),
        subtitle = stringResource(R.string.media_picker_subtitle),
        icon = Icons.Default.PermMedia,
        accent = ClearCutAccents.Blue,
        onClose = onClose,
        closeButtonTestTag = ClearCutTestTags.MEDIA_PICKER_CLOSE,
        modifier = modifier
            .heightIn(min = 240.dp, max = 560.dp)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> actionsEnabled && droppedUris(event).isNotEmpty() },
                target = mediaDropTarget
            ),
        scrollable = true
    ) {
        PremiumSnackbarHost(
            message = permissionMessage,
            severity = ToastSeverity.Warning,
            modifier = Modifier.fillMaxWidth()
        )
        if (permissionMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
        }
        operationState?.let { operation ->
            MediaImportStatusCard(operation = operation)
            Spacer(modifier = Modifier.height(12.dp))
        }

        PremiumPanelCard(accent = ClearCutAccents.Blue) {
            Text(
                text = stringResource(R.string.media_picker_library_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.media_picker_library_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PremiumPanelPill(text = librarySourceLabel, accent = ClearCutAccents.Blue)
                PremiumPanelPill(text = stringResource(R.string.media_picker_source_audio), accent = ClearCutAccents.Peach)
                PremiumPanelPill(
                    text = stringResource(R.string.media_picker_source_kept_local),
                    accent = ClearCutAccents.Teal
                )
            }

            MediaSourceActionCard(
                icon = Icons.Default.Videocam,
                label = stringResource(R.string.media_picker_video),
                description = stringResource(R.string.media_picker_video_description),
                color = ClearCutAccents.Blue,
                enabled = actionsEnabled,
                onClick = {
                    if (usePhotoPicker) {
                        photoPickerVideoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    } else {
                        pendingMediaType = "video"
                        singlePickerLauncher.launch(arrayOf("video/*"))
                    }
                }
            )

            MediaSourceActionCard(
                icon = Icons.Default.Image,
                label = stringResource(R.string.media_picker_image),
                description = stringResource(R.string.media_picker_image_description),
                color = ClearCutAccents.Green,
                enabled = actionsEnabled,
                onClick = {
                    if (usePhotoPicker) {
                        photoPickerImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } else {
                        pendingMediaType = "image"
                        singlePickerLauncher.launch(arrayOf("image/*"))
                    }
                }
            )

            MediaSourceActionCard(
                icon = Icons.Default.MusicNote,
                label = stringResource(R.string.media_picker_audio),
                description = stringResource(R.string.media_picker_audio_description),
                color = ClearCutAccents.Peach,
                enabled = actionsEnabled,
                onClick = {
                    pendingMediaType = "audio"
                    // Include application/ogg so Opus files saved with the legacy
                    // Ogg container MIME (which some Android pickers still report
                    // as application/ogg rather than audio/ogg or audio/opus) are
                    // visible in the picker. The resolver-side MIME check above
                    // already accepts both labels for the same reason.
                    // See ROADMAP.md R6.21.
                    singlePickerLauncher.launch(arrayOf("audio/*", "application/ogg"))
                }
            )

            ClearCutSecondaryButton(
                text = stringResource(R.string.media_picker_select_multiple),
                icon = Icons.Default.LibraryAdd,
                onClick = {
                    if (usePhotoPicker) {
                        photoPickerMultiLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    } else {
                        videoPickerLauncher.launch(arrayOf("video/*", "image/*"))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.minimum),
                contentColor = ClearCutAccents.Mauve,
                enabled = actionsEnabled
            )
            Text(
                text = stringResource(R.string.media_picker_multi_description),
                style = MaterialTheme.typography.bodySmall,
                color = semanticColors.subtext
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Red) {
            Text(
                text = stringResource(R.string.media_picker_capture_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.media_picker_capture_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )
            ClearCutSecondaryButton(
                text = stringResource(R.string.media_picker_record_video),
                icon = Icons.Default.CameraAlt,
                onClick = {
                    startCameraCapture()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.minimum),
                contentColor = ClearCutAccents.Red,
                enabled = actionsEnabled
            )
        }
    }
}

@Composable
private fun MediaImportStatusCard(operation: MediaPickerOperationState) {
    val semanticColors = LocalClearCutColors.current
    PremiumPanelCard(
        accent = ClearCutAccents.Mauve,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = ClearCutAccents.Mauve,
                strokeWidth = 2.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = semanticColors.text
                )
                Text(
                    text = operation.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
                )
            }
        }
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(Radius.sm)),
            color = ClearCutAccents.Mauve,
            trackColor = semanticColors.surface
        )
    }
}

/**
 * Sort a batch of picked media URIs into chronological order so GoPro / DJI /
 * Insta360 chapter-split clips import onto the timeline in playback order
 * rather than URI-list order (which many Android file managers return
 * reverse-chronologically or in name-sort). Sort key prefers the resolver's
 * DISPLAY_NAME padded numeric, falling back to the raw URI toString().
 *
 * Common chapter patterns handled by the padded numeric sort:
 *   - GoPro:     GH010100.MP4, GH020100.MP4 (chapter prefix 01, 02, …)
 *   - GoPro HERO: GX010001.MP4, GX020001.MP4
 *   - DJI:       DJI_0001.MP4, DJI_0002.MP4
 *   - Insta360:  VID_20250101_120000_1.MP4 (trailing _N)
 *   - Samsung:   20250101_120000.mp4 (YYYYMMDD_HHMMSS natural-sorts by date)
 *   - iPhone:    IMG_0001.MOV (sequential counter)
 *
 * Non-destructive: returns a new list; the original `uris` is not modified.
 * Silent: no toast on no-op — if the batch has 1 item or the names don't
 * parse into a clean sequence, we just return name-sorted, which is always
 * at least as good as the input order.
 */
private fun sortMediaChronologically(
    context: android.content.Context,
    uris: List<Uri>
): List<Uri> {
    if (uris.size <= 1) return uris
    // Pull DISPLAY_NAME once per URI. One cursor query per URI is unavoidable
    // without caching at import time; for a 20-clip batch this is ~40 ms on
    // mid-range devices and runs in the picker callback (not the critical
    // path for playback).
    val keyed: List<Pair<Uri, String>> = uris.map { u ->
        val displayName = runCatching {
            context.contentResolver.query(
                u,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                } else null
            }
        }.getOrNull() ?: u.lastPathSegment.orEmpty()
        u to displayName
    }
    // Natural sort: pad every digit run to 10 chars so "GH020100" sorts after
    // "GH010100" even when the chapter prefix varies in length. Avoids a full
    // locale-sensitive comparator (overkill for camera filenames which are
    // ASCII) while matching every camera pattern we've seen in the wild.
    val digitPadRegex = Regex("\\d+")
    fun naturalKey(name: String): String =
        digitPadRegex.replace(name) { it.value.padStart(10, '0') }
    return keyed.sortedBy { naturalKey(it.second) }.map { it.first }
}

private fun releasePersistedReadPermission(
    context: android.content.Context,
    uri: Uri
) {
    try {
        context.contentResolver.releasePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
    } catch (_: IllegalArgumentException) {
    }
}

private fun resolvePickedMediaType(
    context: android.content.Context,
    uri: Uri,
    fallbackType: String
): String {
    val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
    return when {
        mimeType.startsWith("image/") -> "image"
        mimeType.startsWith("audio/") -> "audio"
        mimeType.startsWith("video/") -> "video"
        else -> {
            when (resolveManagedMediaExtension(context, uri, fallbackType).removePrefix(".").lowercase()) {
                "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif" -> "image"
                "mp3", "wav", "m4a", "aac", "ogg", "flac", "opus" -> "audio"
                else -> fallbackType
            }
        }
    }
}

@Composable
private fun MediaSourceActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalClearCutColors.current
    val semanticDescription = "$label. $description"
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .semantics { contentDescription = semanticDescription },
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) colors.panelHighest else colors.panelRaised.copy(alpha = 0.56f),
            disabledContainerColor = colors.panelRaised.copy(alpha = 0.56f)
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) {
                color.copy(alpha = if (colors.highContrast) 0.72f else 0.24f)
            } else {
                colors.cardStroke.copy(alpha = 0.56f)
            }
        ),
        shape = RoundedCornerShape(Radius.xl)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            color.copy(alpha = if (enabled) 0.18f else 0.07f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(color.copy(alpha = if (enabled) 0.16f else 0.07f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (enabled) color else colors.disabledText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        label,
                        color = if (enabled) colors.text else colors.disabledText,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = description,
                        color = if (enabled) colors.subtext else colors.disabledText.copy(alpha = 0.74f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (enabled) colors.subtext else colors.disabledText.copy(alpha = 0.74f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
