package com.novacut.editor.ui.mediapicker

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
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
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
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
            } catch (_: SecurityException) { }
            onMediaSelected(uri, "video")
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
            } catch (_: SecurityException) { }
            onMediaSelected(uri, pendingMediaType)
        }
    }

    // Photo Picker (Android 13+)
    val usePhotoPicker = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val photoPickerVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onMediaSelected(uri, "video")
        }
    }

    val photoPickerImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onMediaSelected(uri, "image")
        }
    }

    val photoPickerMultiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val uriStr = uri.toString().lowercase()
            val type = when {
                mimeType.startsWith("image") -> "image"
                mimeType.startsWith("audio") -> "audio"
                mimeType.startsWith("video") -> "video"
                uriStr.endsWith(".jpg") || uriStr.endsWith(".jpeg") || uriStr.endsWith(".png") || uriStr.endsWith(".webp") -> "image"
                uriStr.endsWith(".mp3") || uriStr.endsWith(".wav") || uriStr.endsWith(".aac") || uriStr.endsWith(".ogg") -> "audio"
                else -> "video"
            }
            onMediaSelected(uri, type)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            cameraVideoUri?.let { uri -> onMediaSelected(uri, "video") }
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
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.media_picker_title), color = Mocha.Text, fontSize = 18.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
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
            border = BorderStroke(1.dp, Mocha.Surface1)
        ) {
            Icon(Icons.Default.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.media_picker_select_multiple))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Record option
        OutlinedButton(
            onClick = {
                val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
                val videoFile = File(cameraDir, "novacut_${System.currentTimeMillis()}.mp4")
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", videoFile
                )
                cameraVideoUri = uri
                cameraLauncher.launch(uri)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Mocha.Red
            ),
            border = BorderStroke(1.dp, Mocha.Surface1)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.media_picker_record_video))
        }
    }
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
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = color, fontSize = 12.sp)
        }
    }
}
