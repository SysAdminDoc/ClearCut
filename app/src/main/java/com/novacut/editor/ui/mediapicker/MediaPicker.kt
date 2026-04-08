package com.novacut.editor.ui.mediapicker

import android.Manifest
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha
import java.io.File

@Composable
fun MediaPickerSheet(
    onMediaSelected: (Uri, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingMediaType by remember { mutableStateOf("video") }
    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
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
            val mediaType = resolvePickedMediaType(context, uri, fallbackType = "video")
            onMediaSelected(uri, mediaType)
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
            onMediaSelected(uri, pendingMediaType)
        }
    }

    // Photo Picker (Android 13+)
    val usePhotoPicker = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val photoPickerVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localUri = copyToLocalMedia(context, uri, "video")
            onMediaSelected(localUri, "video")
        }
    }

    val photoPickerImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localUri = copyToLocalMedia(context, uri, "image")
            onMediaSelected(localUri, "image")
        }
    }

    val photoPickerMultiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            val type = resolvePickedMediaType(context, uri, fallbackType = "video")
            val localUri = copyToLocalMedia(context, uri, type)
            onMediaSelected(localUri, type)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            cameraVideoUri?.let { uri -> onMediaSelected(uri, "video") }
        }
    }

    fun startCameraCapture() {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val videoFile = File(cameraDir, "novacut_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            videoFile
        )
        cameraVideoUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraCapture()
        } else {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.media_picker_camera_permission_required),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Clean up stale camera temp files (older than 1 hour)
    LaunchedEffect(Unit) {
        val cameraDir = File(context.cacheDir, "camera")
        if (cameraDir.exists()) {
            val cutoff = System.currentTimeMillis() - 3_600_000L
            cameraDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 400.dp)
            .background(Mocha.Panel, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Mocha.Surface2.copy(alpha = 0.8f))
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.media_picker_title),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.media_picker_subtitle),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Surface(
                color = Mocha.PanelHighest,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Mocha.CardStroke)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.cd_close_media_picker), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Media type buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaTypeButton(
                icon = Icons.Default.Videocam,
                label = stringResource(R.string.media_picker_video),
                color = Mocha.Blue,
                modifier = Modifier.weight(1f)
            ) {
                if (usePhotoPicker) {
                    photoPickerVideoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                } else {
                    pendingMediaType = "video"
                    singlePickerLauncher.launch(arrayOf("video/*"))
                }
            }

            MediaTypeButton(
                icon = Icons.Default.Image,
                label = stringResource(R.string.media_picker_image),
                color = Mocha.Green,
                modifier = Modifier.weight(1f)
            ) {
                if (usePhotoPicker) {
                    photoPickerImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } else {
                    pendingMediaType = "image"
                    singlePickerLauncher.launch(arrayOf("image/*"))
                }
            }

            MediaTypeButton(
                icon = Icons.Default.MusicNote,
                label = stringResource(R.string.media_picker_audio),
                color = Mocha.Peach,
                modifier = Modifier.weight(1f)
            ) {
                pendingMediaType = "audio"
                singlePickerLauncher.launch(arrayOf("audio/*"))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Multi-select button
        OutlinedButton(
            onClick = {
                if (usePhotoPicker) {
                    photoPickerMultiLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                } else {
                    videoPickerLauncher.launch(arrayOf("video/*", "image/*"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Mocha.Mauve
            ),
            border = BorderStroke(1.dp, Mocha.CardStrokeStrong),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.LibraryAdd, contentDescription = stringResource(R.string.cd_library_add), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.media_picker_select_multiple), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Record option
        OutlinedButton(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCameraCapture()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Mocha.Red
            ),
            border = BorderStroke(1.dp, Mocha.CardStrokeStrong),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.cd_camera), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.media_picker_record_video), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Copy a picker URI to app-local storage so it survives across sessions.
 * Photo Picker URIs lose permission on app restart.
 */
private fun copyToLocalMedia(context: android.content.Context, uri: Uri, mediaType: String): Uri {
    return try {
        if (uri.scheme == "file") {
            val path = uri.path.orEmpty()
            if (path.startsWith(context.filesDir.absolutePath) || path.startsWith(context.cacheDir.absolutePath)) {
                return uri
            }
        }

        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        val ext = resolveFileExtension(context, uri, mediaType)
        var destFile = File(mediaDir, "${System.currentTimeMillis()}_${uri.lastPathSegment?.hashCode()?.toUInt() ?: 0}$ext")
        while (destFile.exists()) {
            destFile = File(mediaDir, "${System.currentTimeMillis()}_${(0..9999).random()}$ext")
        }
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) {
            android.util.Log.w("MediaPicker", "Cannot open input stream for $uri")
            return uri
        }
        input.use { src ->
            destFile.outputStream().use { dst -> src.copyTo(dst) }
        }
        if (destFile.length() == 0L) {
            destFile.delete()
            return uri
        }
        Uri.fromFile(destFile)
    } catch (e: Exception) {
        android.util.Log.w("MediaPicker", "Failed to copy URI to local storage, using original", e)
        uri
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
            when (resolveFileExtension(context, uri, fallbackType).removePrefix(".").lowercase()) {
                "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif" -> "image"
                "mp3", "wav", "m4a", "aac", "ogg", "flac", "opus" -> "audio"
                else -> fallbackType
            }
        }
    }
}

private fun resolveFileExtension(
    context: android.content.Context,
    uri: Uri,
    mediaType: String
): String {
    val mimeType = context.contentResolver.getType(uri)
    val mimeExt = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    if (!mimeExt.isNullOrBlank()) {
        return ".${mimeExt.lowercase()}"
    }

    val displayName = resolveDisplayName(context, uri)
    val nameExt = displayName?.substringAfterLast('.', "")
    if (!nameExt.isNullOrBlank()) {
        return ".${nameExt.lowercase()}"
    }

    return when (mediaType) {
        "image" -> ".jpg"
        "audio" -> ".m4a"
        else -> ".mp4"
    }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    if (uri.scheme == "file") {
        return uri.lastPathSegment
    }

    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
    }.getOrNull() ?: uri.lastPathSegment
}

@Composable
private fun MediaTypeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        colors = CardDefaults.cardColors(
            containerColor = Mocha.PanelHighest
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(label, color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
