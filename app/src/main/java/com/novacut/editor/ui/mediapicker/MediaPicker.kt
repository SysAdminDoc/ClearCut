package com.novacut.editor.ui.mediapicker

import android.app.Activity
import com.novacut.editor.ui.theme.ClearCutAccents
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.novacut.editor.engine.AppLog
import android.view.DragAndDropPermissions
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
import androidx.compose.ui.platform.LocalResources
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
import com.novacut.editor.engine.MediaSequenceOrder
import com.novacut.editor.engine.finalizePendingCameraCapture
import com.novacut.editor.engine.importUriToManagedMediaWithProgress
import com.novacut.editor.engine.insufficientSpaceFor
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
import java.util.Locale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val description: String,
    /**
     * Determinate progress for batch imports. A forty-file import previously showed a
     * static card with a spinner for minutes, so a slow import and a stuck one looked
     * the same. Null for single-item work, where there is nothing to count.
     */
    val completed: Int? = null,
    val total: Int? = null,
)

private data class MediaPickerBatchImportResult(
    val imported: List<MediaPickerSelection>,
    val insufficientSpace: IngestResult.InsufficientSpace? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaPickerSheet(
    onMediaSelected: (Uri, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onMediaBatchSelected: ((List<MediaPickerSelection>) -> Unit)? = null,
) {
    val semanticColors = LocalClearCutColors.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val importingBatchTitle = stringResource(R.string.media_picker_importing_batch_title)
    val importingBatchDescription = stringResource(R.string.media_picker_importing_batch_description)
    val localCopyFailed = stringResource(R.string.media_picker_local_copy_failed)
    val someImportsFailed = stringResource(R.string.media_picker_some_imports_failed)
    val audioOnly = stringResource(R.string.media_picker_audio_only)
    val importingVideoTitle = stringResource(R.string.media_picker_importing_video_title)
    val importingVideoDescription = stringResource(R.string.media_picker_importing_video_description)
    val importingImageTitle = stringResource(R.string.media_picker_importing_image_title)
    val importingImageDescription = stringResource(R.string.media_picker_importing_image_description)
    val importingCaptureTitle = stringResource(R.string.media_picker_importing_capture_title)
    val importingCaptureDescription = stringResource(R.string.media_picker_importing_capture_description)
    val cameraEmptyCapture = stringResource(R.string.media_picker_camera_empty_capture)
    val cameraHandoffFailed = stringResource(R.string.media_picker_camera_handoff_failed)
    val coroutineScope = rememberCoroutineScope()
    var pendingMediaType by remember { mutableStateOf("video") }
    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    var cameraVideoFile by remember { mutableStateOf<File?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var operationState by remember { mutableStateOf<MediaPickerOperationState?>(null) }
    var sequenceReview by remember { mutableStateOf<List<MediaPickerSelection>?>(null) }
    var sequenceOrder by remember { mutableStateOf(MediaSequenceOrder.CAPTURE_TIME) }
    var sequenceDragPermissions by remember { mutableStateOf<DragAndDropPermissions?>(null) }
    var sequencePersistedUris by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var activeOperationJob by remember { mutableStateOf<Job?>(null) }
    val actionsEnabled = operationState == null && sequenceReview == null

    fun launchImportOperation(block: suspend () -> Unit) {
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                operationState = null
                activeOperationJob = null
            }
        }
        activeOperationJob = job
        job.start()
    }

    fun cancelActiveOperation() {
        activeOperationJob?.cancel()
    }

    fun insufficientSpaceMessage(failure: IngestResult.InsufficientSpace): String =
        resources.getString(
            R.string.media_picker_insufficient_space_format,
            formatMediaPickerBytes(failure.requiredBytes),
            formatMediaPickerBytes(failure.availableBytes),
        )

    fun cancelSequenceReview() {
        val selections = sequenceReview
        val persistedUris = sequencePersistedUris
        if (selections == null && persistedUris.isEmpty()) return
        sequenceReview = null
        sequenceOrder = MediaSequenceOrder.CAPTURE_TIME
        sequencePersistedUris = emptySet()
        coroutineScope.launch(NonCancellable + Dispatchers.IO) {
            releasePersistedReadPermissions(context, persistedUris)
        }
        sequenceDragPermissions?.release()
        sequenceDragPermissions = null
    }

    fun stageSequenceReview(
        uris: List<Uri>,
        dragPermissions: DragAndDropPermissions? = null,
        persistedUris: List<Uri> = emptyList(),
    ) {
        if (uris.isEmpty()) {
            coroutineScope.launch(NonCancellable + Dispatchers.IO) {
                releasePersistedReadPermissions(context, persistedUris)
            }
            dragPermissions?.release()
            return
        }
        launchImportOperation {
            operationState = MediaPickerOperationState(
                title = importingBatchTitle,
                description = importingBatchDescription,
            )
            var retainedPermissions = false
            try {
                val selections = withContext(Dispatchers.IO) {
                    uris.mapIndexed { index, uri ->
                        buildMediaPickerSelection(
                            context = context,
                            uri = uri,
                            mediaType = resolvePickedMediaType(context, uri, fallbackType = "video"),
                            id = "$index:${uri}",
                        )
                    }
                }
                if (selections.isNotEmpty()) {
                    sequenceReview = orderedMediaPickerSelections(
                        selections = selections,
                        order = MediaSequenceOrder.CAPTURE_TIME,
                    )
                    sequenceOrder = MediaSequenceOrder.CAPTURE_TIME
                    sequenceDragPermissions = dragPermissions
                    sequencePersistedUris = persistedUris.toSet()
                    retainedPermissions = true
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.w("MediaPicker", "Could not prepare sequence review", error)
                permissionMessage = localCopyFailed
            } finally {
                if (!retainedPermissions) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        releasePersistedReadPermissions(context, persistedUris)
                    }
                    dragPermissions?.release()
                }
            }
        }
    }

    fun confirmSequenceReview() {
        val selections = sequenceReview ?: return
        sequenceReview = null
        sequenceOrder = MediaSequenceOrder.CAPTURE_TIME
        val dragPermissions = sequenceDragPermissions
        sequenceDragPermissions = null
        val persistedUris = sequencePersistedUris
        sequencePersistedUris = emptySet()
        launchImportOperation {
            operationState = MediaPickerOperationState(
                title = importingBatchTitle,
                description = importingBatchDescription,
                completed = 0,
                total = selections.size,
            )
            val operationContext = currentCoroutineContext()
            val importResult = try {
                withContext(Dispatchers.IO) {
                    val totalSize = selections.sumOf { selection ->
                        querySourceSize(context, selection.uri).coerceAtLeast(0L)
                    }
                    insufficientSpaceFor(context, totalSize)?.let { failure ->
                        return@withContext MediaPickerBatchImportResult(
                            imported = emptyList(),
                            insufficientSpace = failure,
                        )
                    }
                    val imported = mutableListOf<MediaPickerSelection>()
                    for ((index, selection) in selections.withIndex()) {
                        if (!operationContext.isActive) {
                            throw CancellationException("Media import cancelled")
                        }
                        withContext(Dispatchers.Main.immediate) {
                            operationState = operationState?.copy(
                                completed = index,
                                total = selections.size,
                            )
                        }
                        when (
                            val result = importUriToManagedMediaWithProgress(
                                context = context,
                                uri = selection.uri,
                                mediaType = selection.mediaType,
                                isCancelled = { !operationContext.isActive },
                            )
                        ) {
                            is IngestResult.Success -> imported += selection.copy(uri = result.managedUri)
                            is IngestResult.InsufficientSpace -> {
                                return@withContext MediaPickerBatchImportResult(
                                    imported = imported.toList(),
                                    insufficientSpace = result,
                                )
                            }
                            is IngestResult.Cancelled ->
                                throw CancellationException("Media import cancelled")
                            is IngestResult.Failed -> Unit
                        }
                        withContext(Dispatchers.Main.immediate) {
                            operationState = operationState?.copy(
                                completed = index + 1,
                                total = selections.size,
                            )
                        }
                    }
                    MediaPickerBatchImportResult(imported = imported)
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    releasePersistedReadPermissions(context, persistedUris)
                }
                dragPermissions?.release()
            }
            importResult.insufficientSpace?.let { failure ->
                permissionMessage = insufficientSpaceMessage(failure)
            }
            if (importResult.imported.isNotEmpty()) {
                onMediaBatchSelected?.invoke(importResult.imported)
                    ?: importResult.imported.forEach { selection ->
                        onMediaSelected(selection.uri, selection.mediaType)
                    }
                if (importResult.imported.size < selections.size &&
                    importResult.insufficientSpace == null
                ) {
                    permissionMessage = someImportsFailed
                }
            } else if (importResult.insufficientSpace == null) {
                permissionMessage = localCopyFailed
            }
        }
    }

    fun reorderSequenceItem(itemId: String, targetIndex: Int) {
        val current = sequenceReview ?: return
        val fromIndex = current.indexOfFirst { it.id == itemId }
        if (fromIndex !in current.indices || targetIndex !in current.indices) return
        sequenceReview = moveMediaPickerSelection(
            selections = current,
            fromIndex = fromIndex,
            toIndex = targetIndex,
        )
        sequenceOrder = MediaSequenceOrder.MANUAL
    }

    fun changeSequenceOrder(order: MediaSequenceOrder) {
        val current = sequenceReview ?: return
        sequenceReview = orderedMediaPickerSelections(current, order)
        sequenceOrder = order
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val persistedUris = mutableListOf<Uri>()
            uris.forEach { uri ->
                if (takePersistableReadPermission(context, uri)) {
                    persistedUris += uri
                }
            }
            stageSequenceReview(uris, persistedUris = persistedUris)
        }
    }

    fun importPickedMedia(uri: Uri, mediaType: String, title: String, description: String) {
        launchImportOperation {
            operationState = MediaPickerOperationState(title = title, description = description)
            val operationContext = currentCoroutineContext()
            val result = withContext(Dispatchers.IO) {
                try {
                    importUriToManagedMediaWithProgress(
                        context = context,
                        uri = uri,
                        mediaType = mediaType,
                        isCancelled = { !operationContext.isActive },
                    )
                } finally {
                    withContext(NonCancellable) {
                        releasePersistedReadPermission(context, uri)
                    }
                }
            }
            when (result) {
                is IngestResult.Success -> onMediaSelected(result.managedUri, mediaType)
                is IngestResult.InsufficientSpace -> permissionMessage = insufficientSpaceMessage(result)
                is IngestResult.Cancelled -> throw CancellationException("Media import cancelled")
                is IngestResult.Failed -> permissionMessage = localCopyFailed
            }
        }
    }

    fun importDroppedMedia(uris: List<Uri>, dragPermissions: DragAndDropPermissions) {
        if (uris.isEmpty() || !actionsEnabled) {
            dragPermissions.release()
            return
        }
        if (uris.size > 1) {
            stageSequenceReview(uris, dragPermissions)
            return
        }
        launchImportOperation {
            operationState = MediaPickerOperationState(
                title = importingBatchTitle,
                description = importingBatchDescription
            )
            val operationContext = currentCoroutineContext()
            try {
                val result = withContext(Dispatchers.IO) {
                    val sorted = sortMediaChronologically(context, uris)
                    val uri = sorted.single()
                    val type = resolvePickedMediaType(context, uri, fallbackType = "video")
                    val totalSize = querySourceSize(context, uri).coerceAtLeast(0L)
                    val ingestResult = insufficientSpaceFor(context, totalSize)
                        ?: importUriToManagedMediaWithProgress(
                            context = context,
                            uri = uri,
                            mediaType = type,
                            isCancelled = { !operationContext.isActive },
                        )
                    ingestResult to type
                }
                when (val ingestResult = result.first) {
                    is IngestResult.Success -> onMediaSelected(ingestResult.managedUri, result.second)
                    is IngestResult.InsufficientSpace -> permissionMessage = insufficientSpaceMessage(ingestResult)
                    is IngestResult.Cancelled -> throw CancellationException("Media import cancelled")
                    is IngestResult.Failed -> permissionMessage = localCopyFailed
                }
            } finally {
                dragPermissions.release()
            }
        }
    }

    fun droppedUris(event: DragAndDropEvent): List<Uri> {
        val clipData = event.toAndroidDragEvent().clipData ?: return emptyList()
        return (0 until clipData.itemCount).mapNotNull { index ->
            clipData.getItemAt(index).uri
        }
    }

    fun canStartMediaDrop(event: DragAndDropEvent): Boolean {
        val clipDescription = event.toAndroidDragEvent().clipDescription ?: return false
        return (0 until clipDescription.mimeTypeCount).any { index ->
            isSupportedMediaDropMimeType(clipDescription.getMimeType(index))
        }
    }

    val mediaDropTarget = remember(actionsEnabled) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val androidEvent = event.toAndroidDragEvent()
                val dragPermissions = runCatching {
                    context.findActivity()?.requestDragAndDropPermissions(androidEvent)
                }.getOrNull() ?: return false
                val uris = droppedUris(event)
                if (uris.isEmpty() || !actionsEnabled) {
                    dragPermissions.release()
                    return false
                }
                importDroppedMedia(uris, dragPermissions)
                return true
            }
        }
    }

    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val persisted = takePersistableReadPermission(context, uri)
            // The ACTION_OPEN_DOCUMENT MIME filter is advisory — on some devices
            // the system picker still allows selecting items from other categories.
            // Verify the resolver's reported MIME before routing an audio pick to
            // the audio track; a mis-routed video or image here would silently add
            // a broken clip to the AUDIO track and fail playback later.
            if (pendingMediaType == "audio") {
                val mimeType = context.contentResolver.getType(uri).orEmpty()
                if (!mimeType.startsWith("audio/") && mimeType != "application/ogg") {
                    if (persisted) {
                        releasePersistedReadPermissions(context, listOf(uri))
                    }
                    permissionMessage = audioOnly
                    return@rememberLauncherForActivityResult
                }
            }
            importPickedMedia(
                uri = uri,
                mediaType = pendingMediaType,
                title = importingBatchTitle,
                description = importingBatchDescription
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
                title = importingVideoTitle,
                description = importingVideoDescription
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
                title = importingImageTitle,
                description = importingImageDescription
            )
        }
    }

    val photoPickerMultiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            stageSequenceReview(uris)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = CaptureVideoWithGrant()
    ) { success ->
        val capturedFile = cameraVideoFile
        cameraVideoUri = null
        cameraVideoFile = null
        if (success) {
            launchImportOperation {
                operationState = MediaPickerOperationState(
                    title = importingCaptureTitle,
                    description = importingCaptureDescription
                )
                val finalizedUri = withContext(Dispatchers.IO) {
                    capturedFile?.let { finalizePendingCameraCapture(context, it, "video") }
                }
                if (finalizedUri != null) {
                    onMediaSelected(finalizedUri, "video")
                } else {
                    permissionMessage = cameraEmptyCapture
                    withContext(Dispatchers.IO) { capturedFile?.delete() }
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
            AppLog.w("MediaPicker", "Failed to create camera capture content URI", error)
            videoFile.delete()
        }.getOrNull()

        if (uri == null) {
            permissionMessage = cameraHandoffFailed
            return
        }

        cameraVideoFile = videoFile
        cameraVideoUri = uri
        runCatching { cameraLauncher.launch(uri) }
            .onFailure { error ->
                AppLog.w("MediaPicker", "Failed to launch camera capture", error)
                cameraVideoFile = null
                cameraVideoUri = null
                videoFile.delete()
                permissionMessage = cameraHandoffFailed
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

    sequenceReview?.let { selections ->
        MediaSequenceReviewDialog(
            selections = selections,
            order = sequenceOrder,
            onOrderChange = ::changeSequenceOrder,
            onMove = ::reorderSequenceItem,
            onConfirm = ::confirmSequenceReview,
            onCancel = ::cancelSequenceReview,
        )
    }

    PremiumEditorPanel(
        title = stringResource(R.string.media_picker_title),
        subtitle = stringResource(R.string.media_picker_subtitle),
        icon = Icons.Default.PermMedia,
        accent = ClearCutAccents.Blue,
        onClose = {
            cancelSequenceReview()
            cancelActiveOperation()
            onClose()
        },
        closeButtonTestTag = ClearCutTestTags.MEDIA_PICKER_CLOSE,
        modifier = modifier
            .heightIn(min = 240.dp, max = 560.dp)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> actionsEnabled && canStartMediaDrop(event) },
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
            MediaImportStatusCard(
                operation = operation,
                onCancel = ::cancelActiveOperation,
            )
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

private fun android.content.Context.findActivity(): Activity? {
    var current: android.content.Context? = this
    while (current != null) {
        if (current is Activity) return current
        val next = (current as? ContextWrapper)?.baseContext ?: return null
        if (next === current) return null
        current = next
    }
    return null
}

@Composable
private fun MediaImportStatusCard(
    operation: MediaPickerOperationState,
    onCancel: () -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    val total = operation.total
    val completed = operation.completed
    val batchProgress = if (total != null && completed != null && total > 1) {
        (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    PremiumPanelCard(
        accent = ClearCutAccents.Mauve,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (batchProgress != null) {
                CircularProgressIndicator(
                    progress = { batchProgress },
                    modifier = Modifier.size(24.dp),
                    color = ClearCutAccents.Mauve,
                    strokeWidth = 2.dp,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = ClearCutAccents.Mauve,
                    strokeWidth = 2.dp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = semanticColors.text
                )
                if (total != null && completed != null && total > 1) {
                    Text(
                        text = stringResource(
                            R.string.media_picker_importing_progress,
                            (completed + 1).coerceAtMost(total),
                            total
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.text
                    )
                }
                Text(
                    text = operation.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
                )
            }
        }
        if (batchProgress != null) {
            LinearProgressIndicator(
                progress = { batchProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(Radius.sm)),
                color = ClearCutAccents.Mauve,
                trackColor = semanticColors.surface,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(Radius.sm)),
                color = ClearCutAccents.Mauve,
                trackColor = semanticColors.surface,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.media_picker_cancel_import))
            }
        }
    }
}

private fun formatMediaPickerBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val bytesPerKilobyte = 1024.0
    val bytesPerMegabyte = bytesPerKilobyte * 1024.0
    val bytesPerGigabyte = bytesPerMegabyte * 1024.0
    return when {
        safeBytes >= bytesPerGigabyte ->
            String.format(Locale.getDefault(), "%.1f GB", safeBytes / bytesPerGigabyte)
        safeBytes >= bytesPerMegabyte ->
            String.format(Locale.getDefault(), "%.1f MB", safeBytes / bytesPerMegabyte)
        safeBytes >= bytesPerKilobyte ->
            String.format(Locale.getDefault(), "%.0f KB", safeBytes / bytesPerKilobyte)
        else -> "$safeBytes B"
    }
}

/**
 * Read stable display metadata before the review is shown. The original source
 * name and capture time travel with the selection even after the source is
 * copied into app-managed media.
 */
private fun buildMediaPickerSelection(
    context: android.content.Context,
    uri: Uri,
    mediaType: String,
    id: String,
): MediaPickerSelection {
    var displayName = uri.lastPathSegment.orEmpty().ifBlank { uri.toString() }
    var captureTimeMs: Long? = null
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                android.provider.OpenableColumns.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_MODIFIED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex >= 0) {
                    displayName = cursor.getString(displayNameIndex).orEmpty()
                        .ifBlank { displayName }
                }
                val dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateModifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val dateTaken = if (dateTakenIndex >= 0) cursor.getLong(dateTakenIndex) else 0L
                val dateModifiedSeconds = if (dateModifiedIndex >= 0) cursor.getLong(dateModifiedIndex) else 0L
                captureTimeMs = dateTaken.takeIf { it > 0L }
                    ?: dateModifiedSeconds.takeIf { it > 0L }?.times(1000L)
            }
        }
    }
    return MediaPickerSelection(
        id = id,
        uri = uri,
        mediaType = mediaType,
        displayName = displayName,
        captureTimeMs = captureTimeMs,
    )
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

private fun releasePersistedReadPermissions(
    context: android.content.Context,
    uris: Iterable<Uri>,
) {
    uris.distinct().forEach { uri ->
        releasePersistedReadPermission(context, uri)
    }
}

private fun takePersistableReadPermission(
    context: android.content.Context,
    uri: Uri,
): Boolean {
    return try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        true
    } catch (error: SecurityException) {
        com.novacut.editor.engine.AppLog.w("MediaPicker", "Failed to persist URI permission", error)
        false
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
