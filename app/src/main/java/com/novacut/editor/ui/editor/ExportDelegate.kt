package com.novacut.editor.ui.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.novacut.editor.engine.ContactSheetExporter
import com.novacut.editor.engine.ExportService
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.engine.StreamCopyExportEngine
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.exportMimeTypeFor
import com.novacut.editor.engine.exportUsesImageCollection
import com.novacut.editor.engine.sanitizeFileName
import com.novacut.editor.engine.writeUtf8TextAtomically
import com.novacut.editor.model.BatchExportItem
import com.novacut.editor.model.BatchExportStatus
import com.novacut.editor.model.ChapterMarker
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

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
    private val showExportSheet: () -> Unit,
    private val streamCopyEngine: StreamCopyExportEngine? = null
) {
    // --- Export ---
    // Holder for the GIF-style / contact-sheet / any other non-Transformer
    // export coroutine. The Transformer-based video export is cancelled via
    // `videoEngine.cancelExport()` directly; this job covers the paths that
    // run outside VideoEngine. Named broadly because the two current callers
    // (GIF encode, contact-sheet render) + any future CPU-only export paths
    // all need the same cancel/teardown plumbing.
    @Volatile private var nonVideoExportJob: kotlinx.coroutines.Job? = null

    /**
     * Expand filename template tokens. Supported tokens:
     *   {name}          project/base name
     *   {date}          YYYY-MM-DD (device local)
     *   {time}          HHmm (device local, 24h)
     *   {res}           resolution label (e.g. 1080p)
     *   {codec}         codec label (e.g. H.264)
     *   {fps}           frame rate
     *   {preset}        platform preset display name (if any) or aspect ratio
     *   {duration}      timeline duration formatted MMmSSs (e.g. 01m34s)
     *   {projectFolder} sanitized project name (directory-safe, collapses spaces)
     *   {clipCount}     number of clips across all tracks
     *   {sizeMB}        post-export placeholder — left literal here and filled in
     *                   after the encoder finishes knowing the final file size
     */
    private fun applyFilenameTemplate(
        template: String,
        baseName: String,
        config: com.novacut.editor.model.ExportConfig
    ): String {
        val now = java.util.Calendar.getInstance()
        val date = "%04d-%02d-%02d".format(
            now.get(java.util.Calendar.YEAR),
            now.get(java.util.Calendar.MONTH) + 1,
            now.get(java.util.Calendar.DAY_OF_MONTH)
        )
        val time = "%02d%02d".format(
            now.get(java.util.Calendar.HOUR_OF_DAY),
            now.get(java.util.Calendar.MINUTE)
        )
        val preset = config.platformPreset?.displayName ?: config.aspectRatio.label
        val state = stateFlow.value
        val totalDurationMs = state.tracks
            .flatMap { it.clips }
            .maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
        val durationToken = formatDurationToken(totalDurationMs)
        val clipCount = state.tracks.sumOf { it.clips.size }
        // projectFolder is a dir-safe flavour of the base name: spaces→_, drop
        // anything outside [A-Za-z0-9._-]. Empty fallback to `baseName` so the
        // token never collapses a template like `{projectFolder}/{name}` into
        // `/`. The filename sanitizer runs downstream anyway.
        val projectFolder = baseName
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^A-Za-z0-9._-]"), "")
            .ifBlank { baseName }
        return template
            .replace("{name}", baseName)
            .replace("{date}", date)
            .replace("{time}", time)
            .replace("{res}", config.resolution.label)
            .replace("{codec}", config.codec.label)
            .replace("{fps}", config.frameRate.toString())
            .replace("{preset}", preset)
            .replace("{duration}", durationToken)
            .replace("{projectFolder}", projectFolder)
            .replace("{clipCount}", clipCount.toString())
            // {sizeMB} is post-export — leave literal; `finalizeFilenameSize`
            // replaces it once the file is written.
            .trim()
            .ifBlank { baseName }
    }

    private fun formatDurationToken(ms: Long): String {
        if (ms <= 0L) return "0m00s"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02dm%02ds".format(m, s)
    }

    /**
     * Post-rename helper: if the finalized filename still contains `{sizeMB}`,
     * replace it with the actual output file size in MB (rounded) and rename
     * on disk. No-op if the token wasn't used. Returns the final File (possibly
     * renamed) so the caller can update `lastExportedFilePath`.
     */
    /**
     * Attempt a zero-transcode stream-copy export. Returns true when the
     * muxer succeeded and the export has been finalised (state → COMPLETE);
     * returns false when not eligible or when the muxer failed — in which
     * case the caller should fall through to the Transformer path.
     */
    private suspend fun trySteamCopy(
        tracks: List<com.novacut.editor.model.Track>,
        config: ExportConfig,
        textOverlays: List<com.novacut.editor.model.TextOverlay>,
        state: EditorState,
        outputFile: File
    ): Boolean {
        val engine = streamCopyEngine ?: return false
        if (!config.allowStreamCopy) return false
        // Any overlay / chapter / subtitle / transparent-output / GIF mode
        // disqualifies — the muxer can only copy sample packets.
        if (textOverlays.isNotEmpty()) return false
        if (state.imageOverlays.isNotEmpty()) return false
        if (config.chapters.isNotEmpty()) return false
        if (config.subtitleFormat != null) return false
        if (config.transparentBackground) return false
        if (config.exportAsGif || config.captureFrameOnly || config.exportAsContactSheet) return false
        if (config.exportAudioOnly || config.exportStemsOnly) return false
        if (config.watermark != null) return false
        val hasOverlays = textOverlays.isNotEmpty() || state.imageOverlays.isNotEmpty()
        val eligibility = engine.analyze(tracks, hasOverlays)
        if (!eligibility.eligible) return false
        val ok = engine.execute(eligibility, outputFile.absolutePath) { progress ->
            stateFlow.update { it.copy(exportProgress = progress) }
        }
        if (!ok) {
            android.util.Log.w("ExportDelegate", "stream-copy failed, falling back to Transformer")
            runCatching { outputFile.delete() }
            return false
        }
        stateFlow.update { it.copy(
            exportState = ExportState.COMPLETE,
            exportProgress = 1f,
            lastExportedFilePath = outputFile.absolutePath
        ) }
        showToast("Stream-copy export complete: ${outputFile.name}")
        return true
    }

    private fun finalizeFilenameSize(outputFile: File): File {
        if (!outputFile.name.contains("{sizeMB}")) return outputFile
        val mb = (outputFile.length() + 524_288L) / 1_048_576L  // round to nearest MB
        val renamedName = outputFile.name.replace("{sizeMB}", "${mb}MB")
        val renamed = File(outputFile.parentFile, renamedName)
        if (outputFile.renameTo(renamed)) return renamed
        // Rename failed — fall back to the unrenamed file rather than losing it.
        return outputFile
    }

    fun cancelExport() {
        // Cancel GIF export coroutine if one is running
        nonVideoExportJob?.cancel()
        nonVideoExportJob = null
        videoEngine.cancelExport()
        // Always push CANCELLED to the UI. The if-guard was only needed when we worried about
        // overwriting a COMPLETE state, but cancelExport() is only called by explicit user
        // action, so CANCELLED is always the right terminal state to show here.
        stateFlow.update { it.copy(
            exportState = ExportState.CANCELLED,
            exportProgress = 0f
        ) }
    }

    fun startExport(outputDir: File, preferredOutputName: String? = null) {
        val currentState = stateFlow.value
        if (currentState.tracks.flatMap { it.clips }.isEmpty()) {
            showToast("No clips to export")
            return
        }

        val totalDurationMs = currentState.tracks
            .flatMap { it.clips }
            .maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
        val config = currentState.exportConfig
            .copy(aspectRatio = currentState.project.aspectRatio)
            .resolveTargetSize(totalDurationMs)
        val configWithChapters = if (config.includeChapterMarkers && config.chapters.isEmpty()) {
            config.copy(chapters = currentState.timelineMarkers
                .sortedBy { it.timeMs }
                .map { ChapterMarker(timeMs = it.timeMs, title = it.label.ifBlank { "Chapter" }) }
            )
        } else config
        val tracks = currentState.tracks
        val textOverlays = currentState.textOverlays

        // Contact-sheet export path — renders one PNG grid of clip thumbnails.
        // Short path because there's no Transformer, no foreground service, no audio.
        if (configWithChapters.exportAsContactSheet) {
            stateFlow.update { it.copy(
                exportStartTime = System.currentTimeMillis(),
                exportProgress = 0f,
                exportState = ExportState.EXPORTING,
                exportErrorMessage = null,
                lastExportedFilePath = null
            ) }
            nonVideoExportJob = scope.launch {
                var sheetFile: File? = null
                try {
                    withContext(Dispatchers.IO) { outputDir.mkdirs() }
                    sheetFile = createOutputFile(
                        outputDir = outputDir,
                        extension = "png",
                        preferredOutputName = (preferredOutputName ?: currentState.project.name) + "_contact"
                    )
                    val targetSheetFile = sheetFile ?: return@launch
                    val allClips = tracks
                        .filter { it.type == com.novacut.editor.model.TrackType.VIDEO || it.type == com.novacut.editor.model.TrackType.OVERLAY }
                        .flatMap { it.clips }
                        .sortedBy { it.timelineStartMs }
                    if (allClips.isEmpty()) {
                        stateFlow.update { it.copy(exportState = ExportState.ERROR, exportErrorMessage = "No video clips") }
                        return@launch
                    }
                    val ok = ContactSheetExporter.export(
                        clips = allClips,
                        columns = configWithChapters.contactSheetColumns,
                        outputFile = targetSheetFile,
                        extractThumb = { uri, timeUs, w, h -> videoEngine.extractThumbnail(uri, timeUs, w, h) },
                        onProgress = { p -> stateFlow.update { it.copy(exportProgress = p) } }
                    )
                    if (ok) {
                        stateFlow.update { it.copy(
                            exportState = ExportState.COMPLETE,
                            exportProgress = 1f,
                            lastExportedFilePath = targetSheetFile.absolutePath
                        ) }
                        showToast("Contact sheet exported: ${targetSheetFile.name}")
                    } else {
                        stateFlow.update { it.copy(
                            exportState = ExportState.ERROR,
                            exportErrorMessage = "Contact sheet render failed"
                        ) }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    stateFlow.update {
                        it.copy(exportState = ExportState.CANCELLED, exportProgress = 0f, lastExportedFilePath = null)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ExportDelegate", "Contact sheet export failed", e)
                    sheetFile?.delete()
                    stateFlow.update { it.copy(
                        exportState = ExportState.ERROR,
                        exportErrorMessage = e.message ?: "Contact sheet export failed",
                        lastExportedFilePath = null
                    ) }
                } finally {
                    nonVideoExportJob = null
                }
            }
            return
        }

        // GIF export path
        if (configWithChapters.exportAsGif) {
            stateFlow.update { it.copy(
                exportStartTime = System.currentTimeMillis(),
                exportProgress = 0f,
                exportState = ExportState.EXPORTING,
                exportErrorMessage = null,
                lastExportedFilePath = null
            ) }
            nonVideoExportJob = scope.launch {
                val frames = mutableListOf<android.graphics.Bitmap>()
                var gifFile: File? = null
                try {
                    withContext(Dispatchers.IO) { outputDir.mkdirs() }
                    gifFile = createOutputFile(
                        outputDir = outputDir,
                        extension = "gif",
                        preferredOutputName = preferredOutputName ?: currentState.project.name
                    )
                    val targetGifFile = gifFile ?: return@launch
                    val allClips = tracks
                        .filter { it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY }
                        .flatMap { it.clips }
                        .sortedBy { it.timelineStartMs }
                    if (allClips.isEmpty()) {
                        stateFlow.update { it.copy(exportState = ExportState.ERROR, exportErrorMessage = "No video clips") }
                        return@launch
                    }
                    val totalDurationMs = allClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
                    // Cap frameRate at 60 fps (sane GIF limit) and floor frameInterval at 1 ms so
                    // a misconfigured >1000 fps value can't produce a 0-ms interval, infinite frame
                    // count, OOM, and an export loop that never terminates.
                    val gifFps = configWithChapters.gifFrameRate.coerceIn(1, 60)
                    val frameIntervalMs = (1000L / gifFps).coerceAtLeast(1L)
                    // Clamp in Long space BEFORE narrowing to Int. A pathologically long
                    // totalDurationMs (corrupt state or duration math bug) divided by a 1ms
                    // interval can exceed Int.MAX_VALUE, and `.toInt()` silently wraps to a
                    // negative value which `coerceIn` then clamps to 1 — skipping a real
                    // export instead of capping it at 300 frames.
                    val frameCount = (totalDurationMs / frameIntervalMs).coerceIn(1L, 300L).toInt()
                    val maxWidth = configWithChapters.gifMaxWidth

                    for (i in 0 until frameCount) {
                        // Check for cancellation between frames
                        ensureActive()
                        val timeMs = i * frameIntervalMs
                        val clip = allClips.firstOrNull { clip ->
                            timeMs >= clip.timelineStartMs && timeMs < clip.timelineStartMs + clip.durationMs
                        }
                        if (clip == null) {
                            frames.add(createGapGifFrame(maxWidth, configWithChapters.aspectRatio))
                            stateFlow.update { it.copy(exportProgress = (i + 1).toFloat() / frameCount * 0.9f) }
                            continue
                        }
                        // Respect speedCurve — `timelineOffsetToSourceMs` integrates the
                        // curve when present and falls back to `* speed` for constant
                        // speed, so static clips still produce the same frame mapping
                        // as before this change.
                        val timelineOffsetInClip = timeMs - clip.timelineStartMs
                        val clipTimeUs = clip.timelineOffsetToSourceMs(timelineOffsetInClip) * 1000
                        val bitmap = videoEngine.extractThumbnail(clip.sourceUri, clipTimeUs)
                        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                            val scaled = if (bitmap.width > maxWidth) {
                                val ratio = maxWidth.toFloat() / bitmap.width
                                // Clamp height to >= 1 — createScaledBitmap throws IllegalArgumentException
                                // on zero/negative dimensions, which would abort the entire GIF export
                                // on any single-pixel-tall source frame.
                                val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                                android.graphics.Bitmap.createScaledBitmap(bitmap, maxWidth, h, true).also {
                                    if (it !== bitmap) bitmap.recycle()
                                }
                            } else bitmap
                            frames.add(scaled)
                        } else {
                            bitmap?.recycle()
                        }
                        stateFlow.update { it.copy(exportProgress = (i + 1).toFloat() / frameCount * 0.9f) }
                    }

                    if (frames.isEmpty()) {
                        stateFlow.update { it.copy(exportState = ExportState.ERROR, exportErrorMessage = "No frames extracted") }
                        return@launch
                    }

                    withContext(Dispatchers.IO) {
                        targetGifFile.outputStream().buffered().use { out ->
                            encodeGif(frames, frameIntervalMs.toInt(), out)
                        }
                    }

                    stateFlow.update { it.copy(
                        exportState = ExportState.COMPLETE,
                        exportProgress = 1f,
                        lastExportedFilePath = targetGifFile.absolutePath
                    ) }
                    showToast("GIF exported: ${targetGifFile.name}")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    android.util.Log.d("ExportDelegate", "GIF export cancelled")
                    gifFile?.delete()
                    stateFlow.update { it.copy(
                        exportState = ExportState.CANCELLED,
                        exportProgress = 0f,
                        lastExportedFilePath = null
                    ) }
                } catch (e: Exception) {
                    android.util.Log.w("ExportDelegate", "GIF export failed", e)
                    gifFile?.delete()
                    stateFlow.update { it.copy(
                        exportState = ExportState.ERROR,
                        exportErrorMessage = e.message ?: "GIF export failed",
                        lastExportedFilePath = null
                    ) }
                } finally {
                    nonVideoExportJob = null
                    frames.forEach { bitmap ->
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                    frames.clear()
                }
            }
            return
        }

        stateFlow.update { it.copy(
            exportStartTime = System.currentTimeMillis(),
            exportProgress = 0f,
            exportState = ExportState.EXPORTING,
            exportErrorMessage = null,
            lastExportedFilePath = null
        ) }

        scope.launch {
            val ext = if (currentState.exportConfig.transparentBackground) "webm" else "mp4"
            withContext(Dispatchers.IO) { outputDir.mkdirs() }
            val outputFile = createOutputFile(
                outputDir = outputDir,
                extension = ext,
                preferredOutputName = preferredOutputName ?: currentState.project.name
            )

            val serviceIntent = Intent(appContext, ExportService::class.java).apply {
                putExtra(ExportService.EXTRA_OUTPUT_PATH, outputFile.absolutePath)
            }
            appContext.startForegroundService(serviceIntent)

            try {
                // v3.69 stream-copy fast-path. Only runs when the caller opted
                // in via `allowStreamCopy` AND the timeline is a single
                // unmodified clip with only head/tail cuts. Falls back to the
                // Transformer path below on any failure so we never leave the
                // user stuck if the MediaMuxer rejects the source.
                if (trySteamCopy(
                        tracks, configWithChapters, textOverlays, currentState, outputFile
                    )
                ) {
                    return@launch
                }
                videoEngine.export(
                    tracks = tracks,
                    config = configWithChapters,
                    outputFile = outputFile,
                    textOverlays = textOverlays,
                    onProgress = { progress ->
                        stateFlow.update { it.copy(exportProgress = progress) }
                    },
                    onComplete = {
                        // If the project carries scratchpad notes, drop them next to the render
                        // as a `.txt` sidecar. Runs on IO to avoid blocking the Transformer
                        // callback thread; failure is logged but doesn't taint the export.
                        val notes = currentState.project.notes
                        if (notes.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val sidecar = File(
                                        outputFile.parentFile,
                                        "${outputFile.nameWithoutExtension}.notes.txt"
                                    )
                                    writeUtf8TextAtomically(sidecar, notes)
                                } catch (e: Exception) {
                                    android.util.Log.w("ExportDelegate", "Scratchpad sidecar write failed", e)
                                }
                            }
                        }
                        // Subtitle sidecar. Written next to the video with a matching
                        // basename so the pair travels together through `saveToGallery`
                        // (image-collection fallback path) and share intents. Sequential
                        // with the state → COMPLETE transition: we block on the write
                        // before the UI gets Share/Save-to-Gallery buttons, so a user
                        // tapping Share can't race a half-written .srt. Runs on IO with
                        // runBlocking only because the Transformer callback lands on the
                        // Main thread where `launch`/`await` would defer past the
                        // state update.
                        val subtitleFormat = configWithChapters.subtitleFormat
                        if (subtitleFormat != null) {
                            try {
                                val captions = tracks
                                    .flatMap { t -> t.clips }
                                    .flatMap { clip ->
                                        clip.captions.map { c ->
                                            c.copy(
                                                startTimeMs = c.startTimeMs + clip.timelineStartMs,
                                                endTimeMs = c.endTimeMs + clip.timelineStartMs
                                            )
                                        }
                                    }
                                if (captions.isNotEmpty()) {
                                    val sidecar = File(
                                        outputFile.parentFile,
                                        "${outputFile.nameWithoutExtension}.${subtitleFormat.extension}"
                                    )
                                    com.novacut.editor.engine.SubtitleExporter.export(
                                        captions, subtitleFormat, sidecar
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("ExportDelegate", "Subtitle sidecar write failed", e)
                            }
                        }
                        // Finalize the `{sizeMB}` filename token (if used) by
                        // renaming the output to include the actual MB count.
                        // No-op when the template didn't reference the token,
                        // so existing templates are unaffected.
                        val finalizedFile = finalizeFilenameSize(outputFile)
                        stateFlow.update { it.copy(
                            exportState = ExportState.COMPLETE,
                            exportProgress = 1f,
                            lastExportedFilePath = finalizedFile.absolutePath
                        ) }
                        showToast("Export complete: ${finalizedFile.name}")
                    },
                    onError = { e ->
                        outputFile.delete()
                        stateFlow.update { it.copy(
                            exportState = ExportState.ERROR,
                            exportErrorMessage = e.message ?: "Unknown error",
                            lastExportedFilePath = null
                        ) }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // The user actively cancelled — do not surface as ERROR.
                // VideoEngine's transformer listener handles the CANCELLED
                // state transition; we just clean up the partial file and
                // let cancellation propagate so the launched job finishes.
                runCatching { outputFile.delete() }
                throw e
            } catch (e: Exception) {
                outputFile.delete()
                stateFlow.update { it.copy(
                    exportState = ExportState.ERROR,
                    exportErrorMessage = e.message ?: "Unknown error",
                    lastExportedFilePath = null
                ) }
            }
        }
    }

    fun getShareIntent(): Intent? {
        val filePath = stateFlow.value.lastExportedFilePath ?: run {
            showToast("No exported media to share")
            return null
        }
        val file = File(filePath)
        if (!file.exists()) {
            showToast("Export file no longer available")
            return null
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = exportMimeTypeFor(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun saveToGallery() {
        val filePath = stateFlow.value.lastExportedFilePath ?: run {
            showToast("No exported media")
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
                    val savedMessage = saveExportedFile(file)
                    withContext(Dispatchers.Main) { showToast(savedMessage) }
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
        // Snapshot the queue and per-item configs up front so UI-side config
        // changes that happen while exports are running can't corrupt the batch.
        val queue = stateFlow.value.batchExportQueue.toList()
        if (queue.isEmpty()) {
            showToast("Add export items first")
            return
        }
        hideBatchExport()
        scope.launch {
            val outputDir = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: appContext.filesDir,
                "NovaCut"
            ).apply { mkdirs() }
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
                    // Reset exportState to IDLE in the delegate state as well. Without this,
                    // the wait loop below immediately sees the previous item's COMPLETE/ERROR
                    // state and advances before the new export has started, causing two items
                    // to export concurrently and the batch queue to report incorrect statuses.
                    stateFlow.update { it.copy(exportConfig = item.config, exportState = ExportState.IDLE, exportProgress = 0f) }
                    startExport(outputDir, item.outputName)
                    val progressJob = scope.launch {
                        stateFlow.map { it.exportProgress }
                            .distinctUntilChanged()
                            .collect { progress ->
                            stateFlow.update { s ->
                                s.copy(batchExportQueue = s.batchExportQueue.map {
                                    if (it.id == item.id) it.copy(progress = progress) else it
                                })
                            }
                        }
                    }
                    val result = try {
                        stateFlow.map { it.exportState }
                            .distinctUntilChanged()
                            .first { it != ExportState.IDLE && it != ExportState.EXPORTING }
                    } finally {
                        progressJob.cancel()
                        // Wait for the collector to fully stop before starting the next item.
                        // cancel() is non-blocking; without join() the old collector can still
                        // be running stateFlow.update calls when the next iteration launches,
                        // causing races on the batch queue state.
                        progressJob.join()
                    }
                    val newStatus = when (result) {
                        ExportState.COMPLETE -> BatchExportStatus.COMPLETED
                        ExportState.CANCELLED -> BatchExportStatus.CANCELLED
                        else -> BatchExportStatus.FAILED
                    }
                    // Normalize the per-item progress to 100% on success and 0% on failure /
                    // cancel. Without this, the queue UI would show "85% FAILED" on a job that
                    // errored partway through, and "99% COMPLETED" on a job whose progress
                    // collector got cancelled before observing the final 1.0 tick.
                    val finalProgress = if (result == ExportState.COMPLETE) 1f else 0f
                    stateFlow.update { s ->
                        s.copy(batchExportQueue = s.batchExportQueue.map {
                            if (it.id == item.id) it.copy(status = newStatus, progress = finalProgress) else it
                        })
                    }
                    // Stop the batch when the user explicitly cancels — continuing onto the
                    // next item would feel like the cancel button was ignored. Failures don't
                    // break the batch (each item is independent and the user may want
                    // partial-success behaviour for a long queue).
                    if (result == ExportState.CANCELLED) break
                }
            } finally {
                stateFlow.update { it.copy(exportConfig = originalConfig) }
            }
            val finalQueue = stateFlow.value.batchExportQueue
            val completedCount = finalQueue.count { it.status == BatchExportStatus.COMPLETED }
            val failedCount = finalQueue.count { it.status == BatchExportStatus.FAILED }
            val summary = when {
                failedCount == 0 -> "Batch export complete ($completedCount items)"
                completedCount == 0 -> "Batch export failed ($failedCount items)"
                else -> "Batch export finished ($completedCount succeeded, $failedCount failed)"
            }
            showToast(summary)
        }
    }

    private fun createOutputFile(
        outputDir: File,
        extension: String,
        preferredOutputName: String?
    ): File {
        val trimmedOutputName = preferredOutputName?.trim().orEmpty()
        val baseName = trimmedOutputName
            .substringBeforeLast('.', missingDelimiterValue = trimmedOutputName)
            .takeIf { it.isNotBlank() }
            ?: "NovaCut"
        val template = stateFlow.value.exportConfig.filenameTemplate.ifBlank { "{name}" }
        val templated = applyFilenameTemplate(template, baseName, stateFlow.value.exportConfig)
        // Reserve space for an auto-increment suffix like ` (999)` so repeated
        // collisions don't force the base to shrink with every retry (which
        // would produce a different filename on each iteration and could even
        // miss a previously-created number by hopping across lengths).
        val suffixReserve = 6
        val baseBudget = 64 - suffixReserve
        val sanitizedBase = sanitizeFileName(templated, fallback = "NovaCut", maxLength = baseBudget)
        var candidate = File(outputDir, "$sanitizedBase.$extension")
        if (!candidate.exists()) {
            return candidate
        }

        var index = 2
        while (candidate.exists()) {
            val numberedBase = sanitizeFileName("$sanitizedBase ($index)", fallback = sanitizedBase, maxLength = 64)
            candidate = File(outputDir, "$numberedBase.$extension")
            index++
        }
        return candidate
    }

    private fun saveExportedFile(file: File): String {
        val usesImageCollection = exportUsesImageCollection(file.name)
        val relativeDirectory = if (usesImageCollection) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        val mimeType = exportMimeTypeFor(file.name)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDirectory/NovaCut")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = if (usesImageCollection) {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val contentUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to create media destination")

            try {
                resolver.openOutputStream(contentUri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Failed to open media destination")

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                // If MediaStore reports zero rows updated, the file remains marked pending
                // and stays invisible in Gallery / Photos apps. Treat as a failure rather
                // than silently lying to the user that the save succeeded. Some devices
                // transiently return 0 while an indexer run is in flight; retry a couple
                // of times with short backoff before surfacing the error.
                var updated = 0
                val backoffsMs = longArrayOf(0L, 100L, 400L)
                for (delayMs in backoffsMs) {
                    if (delayMs > 0L) {
                        try { Thread.sleep(delayMs) } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                    updated = resolver.update(contentUri, values, null, null)
                    if (updated >= 1) break
                }
                if (updated < 1) {
                    throw IllegalStateException("MediaStore failed to clear IS_PENDING (rows=$updated)")
                }
                "Saved to gallery: ${file.name}"
            } catch (e: Exception) {
                resolver.delete(contentUri, null, null)
                throw e
            }
        } else {
            val externalRoot = appContext.getExternalFilesDir(relativeDirectory)
                ?: File(appContext.filesDir, relativeDirectory.lowercase())
            val destinationDir = File(externalRoot, "NovaCut").apply { mkdirs() }
            val destinationFile = createOutputFile(
                destinationDir,
                file.extension.ifBlank { if (usesImageCollection) "png" else "mp4" },
                file.name
            )
            file.copyTo(destinationFile, overwrite = true)
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(destinationFile.absolutePath),
                arrayOf(mimeType),
                null
            )
            "Saved to app media folder: ${destinationFile.name}"
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

    private fun createGapGifFrame(
        maxWidth: Int,
        aspectRatio: com.novacut.editor.model.AspectRatio
    ): android.graphics.Bitmap {
        val width = maxWidth.coerceAtLeast(1)
        val height = (width / aspectRatio.toFloat()).roundToInt().coerceAtLeast(1)
        return android.graphics.Bitmap
            .createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.BLACK) }
    }

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
