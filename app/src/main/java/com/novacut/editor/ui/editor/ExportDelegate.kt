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
import com.novacut.editor.model.ChapterMarker
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
        val configWithChapters = if (config.includeChapterMarkers && config.chapters.isEmpty()) {
            config.copy(chapters = currentState.timelineMarkers
                .sortedBy { it.timeMs }
                .map { ChapterMarker(timeMs = it.timeMs, title = it.label.ifBlank { "Chapter" }) }
            )
        } else config
        val tracks = currentState.tracks
        val textOverlays = currentState.textOverlays

        // GIF export path
        if (configWithChapters.exportAsGif) {
            stateFlow.update { it.copy(
                exportStartTime = System.currentTimeMillis(),
                exportProgress = 0f,
                exportState = ExportState.EXPORTING,
                exportErrorMessage = null
            ) }
            scope.launch {
                val frames = mutableListOf<android.graphics.Bitmap>()
                try {
                    val gifFile = File(outputDir, "NovaCut_${System.currentTimeMillis()}.gif")
                    withContext(Dispatchers.IO) { outputDir.mkdirs() }
                    val allClips = tracks.flatMap { it.clips }.sortedBy { it.timelineStartMs }
                    val totalDurationMs = allClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
                    val frameIntervalMs = (1000 / configWithChapters.gifFrameRate.coerceAtLeast(1)).toLong()
                    val frameCount = (totalDurationMs / frameIntervalMs).toInt().coerceIn(1, 300)
                    val maxWidth = configWithChapters.gifMaxWidth

                    for (i in 0 until frameCount) {
                        val timeMs = i * frameIntervalMs
                        val clip = allClips.lastOrNull { it.timelineStartMs <= timeMs } ?: continue
                        val clipTimeUs = (((timeMs - clip.timelineStartMs) * clip.speed).toLong() + clip.trimStartMs) * 1000
                        val bitmap = videoEngine.extractThumbnail(clip.sourceUri, clipTimeUs)
                        if (bitmap != null) {
                            val scaled = if (bitmap.width > maxWidth) {
                                val ratio = maxWidth.toFloat() / bitmap.width
                                val h = (bitmap.height * ratio).toInt()
                                android.graphics.Bitmap.createScaledBitmap(bitmap, maxWidth, h, true).also {
                                    if (it !== bitmap) bitmap.recycle()
                                }
                            } else bitmap
                            frames.add(scaled)
                        }
                        stateFlow.update { it.copy(exportProgress = (i + 1).toFloat() / frameCount * 0.9f) }
                    }

                    if (frames.isEmpty()) {
                        stateFlow.update { it.copy(exportState = ExportState.ERROR, exportErrorMessage = "No frames extracted") }
                        return@launch
                    }

                    try {
                        withContext(Dispatchers.IO) {
                            gifFile.outputStream().buffered().use { out ->
                                encodeGif(frames, frameIntervalMs.toInt(), out)
                            }
                        }
                    } finally {
                        frames.forEach { it.recycle() }
                    }

                    stateFlow.update { it.copy(
                        exportState = ExportState.COMPLETE,
                        exportProgress = 1f,
                        lastExportedFilePath = gifFile.absolutePath
                    ) }
                    showToast("GIF exported: ${gifFile.name}")
                } catch (e: Exception) {
                    android.util.Log.w("ExportDelegate", "GIF export failed", e)
                    stateFlow.update { it.copy(
                        exportState = ExportState.ERROR,
                        exportErrorMessage = e.message ?: "GIF export failed"
                    ) }
                }
            }
            return
        }

        stateFlow.update { it.copy(
            exportStartTime = System.currentTimeMillis(),
            exportProgress = 0f,
            exportState = ExportState.EXPORTING,
            exportErrorMessage = null
        ) }

        scope.launch {
            val ext = if (currentState.exportConfig.transparentBackground) "webm" else "mp4"
            val outputFile = File(outputDir, "NovaCut_${System.currentTimeMillis()}.$ext")
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
                    config = configWithChapters,
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
            type = if (filePath.endsWith(".gif", ignoreCase = true)) "image/gif"
                   else if (filePath.endsWith(".webm", ignoreCase = true)) "video/webm"
                   else "video/*"
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
                        val isGif = file.name.endsWith(".gif", ignoreCase = true)
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                            put(MediaStore.Video.Media.MIME_TYPE,
                                if (isGif) "image/gif"
                                else if (file.name.endsWith(".webm", ignoreCase = true)) "video/webm"
                                else "video/mp4"
                            )
                            put(MediaStore.Video.Media.RELATIVE_PATH,
                                if (isGif) "${Environment.DIRECTORY_PICTURES}/NovaCut"
                                else "${Environment.DIRECTORY_MOVIES}/NovaCut"
                            )
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                        val resolver = appContext.contentResolver
                        val collection = if (isGif) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                         else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        val contentUri = resolver.insert(collection, values)
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
            val originalConfig = stateFlow.value.exportConfig
            try {
                for ((index, item) in queue.withIndex()) {
                    stateFlow.update { s ->
                        s.copy(batchExportQueue = s.batchExportQueue.map {
                            if (it.id == item.id) it.copy(status = BatchExportStatus.IN_PROGRESS) else it
                        })
                    }
                    showToast("Exporting ${index + 1}/${queue.size}: ${item.outputName}")
                    videoEngine.resetExportState()
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
                        videoEngine.exportState.first { it != ExportState.IDLE && it != ExportState.EXPORTING }
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
            } finally {
                stateFlow.update { it.copy(exportConfig = originalConfig) }
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

    // --- GIF Encoder ---

    private fun encodeGif(frames: List<android.graphics.Bitmap>, delayMs: Int, output: java.io.OutputStream) {
        // GIF89a header
        output.write("GIF89a".toByteArray())
        val width = frames.first().width
        val height = frames.first().height
        // Logical screen descriptor
        output.write(width and 0xFF)
        output.write((width shr 8) and 0xFF)
        output.write(height and 0xFF)
        output.write((height shr 8) and 0xFF)
        output.write(0x00) // no global color table
        output.write(0x00) // background color
        output.write(0x00) // pixel aspect ratio
        // Netscape extension for looping
        output.write(0x21) // extension
        output.write(0xFF) // app extension
        output.write(0x0B) // block size
        output.write("NETSCAPE2.0".toByteArray())
        output.write(0x03) // sub-block size
        output.write(0x01) // loop sub-block id
        output.write(0x00) // loop count (0 = infinite)
        output.write(0x00)
        output.write(0x00) // block terminator

        for (frame in frames) {
            val pixels = IntArray(frame.width * frame.height)
            frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)

            // Build color table (simple quantization to 256 colors)
            val colorMap = mutableMapOf<Int, Int>()
            val palette = mutableListOf<Int>()
            for (pixel in pixels) {
                val rgb = pixel and 0x00FFFFFF
                val quantized = ((rgb shr 16 and 0xF0) shl 8) or ((rgb shr 8) and 0xF0) or ((rgb and 0xF0) shr 4)
                if (quantized !in colorMap && palette.size < 256) {
                    colorMap[quantized] = palette.size
                    palette.add(rgb)
                }
            }
            while (palette.size < 256) palette.add(0)

            val delayCentiseconds = delayMs / 10
            // Graphic control extension
            output.write(0x21)
            output.write(0xF9)
            output.write(0x04)
            output.write(0x00) // no transparency
            output.write(delayCentiseconds and 0xFF)
            output.write((delayCentiseconds shr 8) and 0xFF)
            output.write(0x00) // transparent color index
            output.write(0x00) // terminator

            // Image descriptor
            output.write(0x2C)
            output.write(0x00); output.write(0x00) // left
            output.write(0x00); output.write(0x00) // top
            output.write(frame.width and 0xFF); output.write((frame.width shr 8) and 0xFF)
            output.write(frame.height and 0xFF); output.write((frame.height shr 8) and 0xFF)
            output.write(0x87) // local color table, 256 entries

            // Local color table
            for (color in palette) {
                output.write((color shr 16) and 0xFF) // R
                output.write((color shr 8) and 0xFF) // G
                output.write(color and 0xFF) // B
            }

            // LZW-encode the image data
            val indexedPixels = ByteArray(pixels.size)
            for (i in pixels.indices) {
                val rgb = pixels[i] and 0x00FFFFFF
                val quantized = ((rgb shr 16 and 0xF0) shl 8) or ((rgb shr 8) and 0xF0) or ((rgb and 0xF0) shr 4)
                indexedPixels[i] = (colorMap[quantized] ?: 0).toByte()
            }

            // Simple LZW encoding
            lzwEncode(output, indexedPixels, 8)
        }

        output.write(0x3B) // GIF trailer
        output.flush()
    }

    private fun lzwEncode(output: java.io.OutputStream, pixels: ByteArray, minCodeSize: Int) {
        output.write(minCodeSize)
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1

        val buffer = java.io.ByteArrayOutputStream()
        var codeSize = minCodeSize + 1
        var nextCode = eoiCode + 1
        val codeTable = mutableMapOf<List<Byte>, Int>()
        // Initialize code table
        for (i in 0 until clearCode) {
            codeTable[listOf(i.toByte())] = i
        }

        var bitBuffer = 0
        var bitCount = 0

        fun writeBits(code: Int, bits: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += bits
            while (bitCount >= 8) {
                buffer.write(bitBuffer and 0xFF)
                bitBuffer = bitBuffer shr 8
                bitCount -= 8
            }
        }

        fun flushSubBlocks() {
            if (bitCount > 0) {
                buffer.write(bitBuffer and 0xFF)
                bitBuffer = 0
                bitCount = 0
            }
            val data = buffer.toByteArray()
            buffer.reset()
            var offset = 0
            while (offset < data.size) {
                val blockSize = minOf(255, data.size - offset)
                output.write(blockSize)
                output.write(data, offset, blockSize)
                offset += blockSize
            }
        }

        writeBits(clearCode, codeSize)

        if (pixels.isEmpty()) {
            writeBits(eoiCode, codeSize)
            flushSubBlocks()
            output.write(0x00)
            return
        }

        var current = listOf(pixels[0])
        for (i in 1 until pixels.size) {
            val next = current + pixels[i]
            if (next in codeTable) {
                current = next
            } else {
                writeBits(codeTable[current]!!, codeSize)
                if (nextCode < 4096) {
                    codeTable[next] = nextCode++
                    if (nextCode >= (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    writeBits(clearCode, codeSize)
                    codeTable.clear()
                    for (j in 0 until clearCode) {
                        codeTable[listOf(j.toByte())] = j
                    }
                    nextCode = eoiCode + 1
                    codeSize = minCodeSize + 1
                }
                current = listOf(pixels[i])
            }
        }
        writeBits(codeTable[current]!!, codeSize)
        writeBits(eoiCode, codeSize)
        flushSubBlocks()
        output.write(0x00) // block terminator
    }

}
