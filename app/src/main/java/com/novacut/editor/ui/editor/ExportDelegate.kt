package com.novacut.editor.ui.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.novacut.editor.engine.ExportService
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.BatchExportItem
import com.novacut.editor.model.BatchExportStatus
import com.novacut.editor.model.ExportConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Delegate handling export, batch export, render preview, share, and save-to-gallery.
 * Extracted from EditorViewModel to reduce its size.
 */
class ExportDelegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val videoEngine: VideoEngine,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val showToast: (String) -> Unit,
    private val pauseIfPlaying: () -> Unit,
    private val dismissedPanelState: (EditorState) -> EditorState,
    private val showExportSheet: () -> Unit
) {
    // --- Export ---
    fun cancelExport() {
        videoEngine.cancelExport()
    }

    fun startExport(outputDir: File) {
        val currentState = stateFlow.value
        if (currentState.tracks.flatMap { it.clips }.isEmpty()) {
            showToast("No clips to export")
            return
        }

        val config = currentState.exportConfig.copy(aspectRatio = currentState.project.aspectRatio)
        val tracks = currentState.tracks
        val textOverlays = currentState.textOverlays

        stateFlow.update { it.copy(
            exportStartTime = System.currentTimeMillis(),
            exportProgress = 0f,
            exportState = ExportState.EXPORTING,
            exportErrorMessage = null
        ) }

        scope.launch {
            val outputFile = File(outputDir, "NovaCut_${System.currentTimeMillis()}.mp4")
            withContext(Dispatchers.IO) { outputDir.mkdirs() }

            val serviceIntent = Intent(appContext, ExportService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }

            try {
                videoEngine.export(
                    tracks = tracks,
                    config = config,
                    outputFile = outputFile,
                    textOverlays = textOverlays,
                    onProgress = { progress ->
                        stateFlow.update { it.copy(exportProgress = progress) }
                    },
                    onComplete = {
                        appContext.stopService(serviceIntent)
                        stateFlow.update { it.copy(
                            exportState = ExportState.COMPLETE,
                            exportProgress = 1f,
                            lastExportedFilePath = outputFile.absolutePath
                        ) }
                        showToast("Export complete: ${outputFile.name}")
                    },
                    onError = { e ->
                        appContext.stopService(serviceIntent)
                        stateFlow.update { it.copy(
                            exportState = ExportState.ERROR,
                            exportErrorMessage = e.message ?: "Unknown error"
                        ) }
                    }
                )
            } catch (e: Exception) {
                appContext.stopService(serviceIntent)
                stateFlow.update { it.copy(
                    exportState = ExportState.ERROR,
                    exportErrorMessage = e.message ?: "Unknown error"
                ) }
            }
        }
    }

    fun getShareIntent(): Intent? {
        val filePath = stateFlow.value.lastExportedFilePath ?: run {
            showToast("No exported video to share")
            return null
        }
        val file = File(filePath)
        if (!file.exists()) {
            showToast("Export file no longer available")
            return null
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun saveToGallery() {
        val filePath = stateFlow.value.lastExportedFilePath ?: run {
            showToast("No exported video")
            return
        }
        val file = File(filePath)
        if (!file.exists()) {
            showToast("Export file not found")
            return
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/NovaCut")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                        val resolver = appContext.contentResolver
                        val contentUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        if (contentUri != null) {
                            resolver.openOutputStream(contentUri)?.use { out ->
                                file.inputStream().use { input -> input.copyTo(out) }
                            }
                            values.clear()
                            values.put(MediaStore.Video.Media.IS_PENDING, 0)
                            resolver.update(contentUri, values, null, null)
                        } else {
                            withContext(Dispatchers.Main) { showToast("Failed to save to gallery") }
                            return@withContext
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val moviesDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                            "NovaCut"
                        ).apply { mkdirs() }
                        file.copyTo(File(moviesDir, file.name), overwrite = true)
                    }
                    withContext(Dispatchers.Main) { showToast("Saved to gallery") }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showToast("Save failed: ${e.message}") }
                }
            }
        }
    }

    // --- Batch Export ---
    fun showBatchExport() {
        pauseIfPlaying()
        stateFlow.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(PanelId.BATCH_EXPORT)) }
    }

    fun hideBatchExport() {
        stateFlow.update { it.copy(panels = it.panels.close(PanelId.BATCH_EXPORT)) }
    }

    fun addBatchExportItem(config: ExportConfig, name: String) {
        val item = BatchExportItem(config = config, outputName = name)
        stateFlow.update { it.copy(batchExportQueue = it.batchExportQueue + item) }
    }

    fun removeBatchExportItem(id: String) {
        stateFlow.update { state -> state.copy(batchExportQueue = state.batchExportQueue.filter { it.id != id }) }
    }

    fun startBatchExport() {
        val queue = stateFlow.value.batchExportQueue
        if (queue.isEmpty()) {
            showToast("Add export items first")
            return
        }
        hideBatchExport()
        scope.launch {
            val outputDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: appContext.filesDir
            for ((index, item) in queue.withIndex()) {
                stateFlow.update { s ->
                    s.copy(batchExportQueue = s.batchExportQueue.map {
                        if (it.id == item.id) it.copy(status = BatchExportStatus.IN_PROGRESS) else it
                    })
                }
                showToast("Exporting ${index + 1}/${queue.size}: ${item.outputName}")
                stateFlow.update { it.copy(exportConfig = item.config) }
                startExport(outputDir)
                val progressJob = scope.launch {
                    videoEngine.exportProgress.collect { progress ->
                        stateFlow.update { s ->
                            s.copy(batchExportQueue = s.batchExportQueue.map {
                                if (it.id == item.id) it.copy(progress = progress) else it
                            })
                        }
                    }
                }
                val result = try {
                    videoEngine.exportState.first { it == ExportState.EXPORTING }
                    videoEngine.exportState.first { it != ExportState.EXPORTING }
                } finally {
                    progressJob.cancel()
                }
                val newStatus = if (result == ExportState.COMPLETE) BatchExportStatus.COMPLETED else BatchExportStatus.FAILED
                stateFlow.update { s ->
                    s.copy(batchExportQueue = s.batchExportQueue.map {
                        if (it.id == item.id) it.copy(status = newStatus) else it
                    })
                }
            }
            showToast("Batch export complete (${queue.size} items)")
        }
    }

    // --- Render Preview ---
    fun showRenderPreview() {
        pauseIfPlaying()
        val s = stateFlow.value
        val segments = SmartRenderEngine.analyzeTimeline(s.tracks, s.exportConfig, s.textOverlays)
        val summary = SmartRenderEngine.getSummary(segments)
        stateFlow.update { dismissedPanelState(it).copy(
            panels = it.panels.closeAll().open(PanelId.RENDER_PREVIEW),
            renderSegments = segments,
            renderSummary = summary
        ) }
    }

    fun hideRenderPreview() {
        stateFlow.update { it.copy(panels = it.panels.close(PanelId.RENDER_PREVIEW)) }
    }

    fun renderQuickPreview() {
        val savedConfig = stateFlow.value.exportConfig
        val previewConfig = savedConfig.copy(
            resolution = com.novacut.editor.model.Resolution.SD_480P,
            quality = com.novacut.editor.model.ExportQuality.LOW
        )
        stateFlow.update { it.copy(exportConfig = previewConfig, savedExportConfig = savedConfig) }
        hideRenderPreview()
        showExportSheet()
        showToast("Rendering preview at 480p...")
    }

}
