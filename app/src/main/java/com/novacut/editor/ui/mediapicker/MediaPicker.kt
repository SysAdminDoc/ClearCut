package com.novacut.editor.ui.mediapicker

import android.Manifest
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.novacut.editor.R
import com.novacut.editor.engine.finalizePendingCameraCapture
import com.novacut.editor.engine.importUriToManagedMedia
import com.novacut.editor.engine.pendingCameraCaptureDir
import com.novacut.editor.engine.resolveManagedMediaExtension
import com.novacut.editor.ui.editor.PremiumEditorPanel
import com.novacut.editor.ui.editor.PremiumPanelCard
import com.novacut.editor.ui.editor.PremiumPanelPill
import com.novacut.editor.ui.editor.PremiumSnackbarHost
import com.novacut.editor.ui.editor.ToastSeverity
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.NovaCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    val sortedUris = withContext(Dispatchers.IO) {
                        sortMediaChronologically(context, uris)
                    }
                    sortedUris.forEach { uri ->
                        val mediaType = resolvePickedMediaType(context, uri, fallbackType = "video")
                        onMediaSelected(uri, mediaType)
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
                val localUri = withContext(Dispatchers.IO) {
                    importUriToManagedMedia(context, uri, mediaType)
                }
                if (localUri != null) {
                    onMediaSelected(localUri, mediaType)
                } else {
                    permissionMessage = context.getString(R.string.media_picker_local_copy_failed)
                }
            } finally {
                operationState = null
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
            onMediaSelected(uri, pendingMediaType)
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
        contract = ActivityResultContracts.CaptureVideo()
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
                        permissionMessage = context.getString(R.string.media_picker_local_copy_failed)
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
        val videoFile = File(cameraDir, "novacut_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            videoFile
        )
        cameraVideoFile = videoFile
        cameraVideoUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionMessage = null
            startCameraCapture()
        } else {
            permissionMessage = context.getString(R.string.media_picker_camera_permission_required)
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
        accent = Mocha.Blue,
        onClose = onClose,
        modifier = modifier.heightIn(min = 240.dp, max = 560.dp),
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

        PremiumPanelCard(accent = Mocha.Blue) {
            Text(
                text = stringResource(R.string.media_picker_library_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.media_picker_library_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PremiumPanelPill(text = librarySourceLabel, accent = Mocha.Blue)
                PremiumPanelPill(text = stringResource(R.string.media_picker_source_audio), accent = Mocha.Peach)
                PremiumPanelPill(
                    text = stringResource(R.string.media_picker_source_kept_local),
                    accent = Mocha.Teal
                )
            }

            MediaSourceActionCard(
                icon = Icons.Default.Videocam,
                label = stringResource(R.string.media_picker_video),
                description = stringResource(R.string.media_picker_video_description),
                color = Mocha.Blue,
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
                color = Mocha.Green,
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
                color = Mocha.Peach,
                enabled = actionsEnabled,
                onClick = {
                    pendingMediaType = "audio"
                    singlePickerLauncher.launch(arrayOf("audio/*"))
                }
            )

            NovaCutSecondaryButton(
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
                contentColor = Mocha.Mauve,
                enabled = actionsEnabled
            )
            Text(
                text = stringResource(R.string.media_picker_multi_description),
                style = MaterialTheme.typography.bodySmall,
                color = Mocha.Subtext0
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Red) {
            Text(
                text = stringResource(R.string.media_picker_capture_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.media_picker_capture_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )
            NovaCutSecondaryButton(
                text = stringResource(R.string.media_picker_record_video),
                icon = Icons.Default.CameraAlt,
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        startCameraCapture()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.minimum),
                contentColor = Mocha.Red,
                enabled = actionsEnabled
            )
        }
    }
}

@Composable
private fun MediaImportStatusCard(operation: MediaPickerOperationState) {
    PremiumPanelCard(
        accent = Mocha.Mauve,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Mocha.Mauve,
                strokeWidth = 2.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Mocha.Text
                )
                Text(
                    text = operation.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
            }
        }
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(Radius.pill)),
            color = Mocha.Mauve,
            trackColor = Mocha.Surface1
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
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Mocha.PanelHighest
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(Radius.xl)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.2f), Color.Transparent)
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
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        label,
                        color = if (enabled) Mocha.Text else Mocha.Subtext0,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = description,
                        color = if (enabled) Mocha.Subtext0 else Mocha.Overlay1,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (enabled) Mocha.Subtext0 else Mocha.Overlay1,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
