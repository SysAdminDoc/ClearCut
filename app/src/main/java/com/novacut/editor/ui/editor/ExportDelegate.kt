package com.novacut.editor.ui.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.novacut.editor.engine.AppLog
import androidx.core.content.FileProvider
import com.novacut.editor.BuildConfig
import com.novacut.editor.R
import com.novacut.editor.engine.AiUsageLedger
import com.novacut.editor.engine.BatchExportPlanContext
import com.novacut.editor.engine.BatchExportPlanStore
import com.novacut.editor.engine.C2paExportEngine
import com.novacut.editor.engine.ContactSheetExporter
import com.novacut.editor.engine.ExportHistoryEntry
import com.novacut.editor.engine.ExportHistoryStatus
import com.novacut.editor.engine.ExportHistoryStore
import com.novacut.editor.engine.ExportIncidentBuilder
import com.novacut.editor.engine.ExportIncidentStore
import com.novacut.editor.engine.ExportOutputVerifier
import com.novacut.editor.engine.ExportVerificationException
import com.novacut.editor.engine.expectedContainerForExtension
import com.novacut.editor.engine.ExportService
import com.novacut.editor.engine.ExportStoragePolicy
import com.novacut.editor.engine.ExportStageException
import com.novacut.editor.engine.RenderDegradationException
import com.novacut.editor.engine.ExportStorageException
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.ExportResumePolicy
import com.novacut.editor.engine.HdrOverlayPolicy
import com.novacut.editor.engine.HdrOverlaySummary
import com.novacut.editor.engine.GifStreamEncoder
import com.novacut.editor.engine.HdrOverlayAssetInspector
import com.novacut.editor.engine.MAX_REVERSE_CLIP_DURATION_MS
import com.novacut.editor.engine.MediaHealthReport
import com.novacut.editor.engine.Media3ExportRobustnessPolicy
import com.novacut.editor.engine.MixedRenderExportPlanner
import com.novacut.editor.engine.ProjectDependencyManifest
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.engine.StreamCopyExportEngine
import com.novacut.editor.engine.TimelineRangeExportEngine
import com.novacut.editor.engine.TrackBlendModeCapability
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.reverseRenderFallbackMessage
import com.novacut.editor.engine.gifLogicalScreenSize
import com.novacut.editor.engine.buildExportHistoryEntry
import com.novacut.editor.engine.exportMimeTypeFor
import com.novacut.editor.engine.exportUsesAudioCollection
import com.novacut.editor.engine.exportUsesImageCollection
import com.novacut.editor.engine.exportStorageFailureMessage
import com.novacut.editor.engine.exportConfigFingerprint
import com.novacut.editor.engine.finalizeFilenameSize
import com.novacut.editor.engine.querySourceSize
import com.novacut.editor.engine.reorderBatchExportItems
import com.novacut.editor.engine.sanitizeFileName
import com.novacut.editor.engine.writeFileAtomically
import com.novacut.editor.engine.writeUtf8TextAtomically
import com.novacut.editor.model.BatchExportItem
import com.novacut.editor.model.BatchExportSourceRange
import com.novacut.editor.model.BatchExportStatus
import com.novacut.editor.model.ChapterMarker
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import com.novacut.editor.engine.RedactedLog

internal class ExportSaveGate {
    private val inFlight = AtomicBoolean(false)

    fun tryEnter(): Boolean = inFlight.compareAndSet(false, true)

    fun exit() {
        inFlight.set(false)
    }
}

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
    private val streamCopyEngine: StreamCopyExportEngine? = null,
    private val c2paExportEngine: C2paExportEngine? = null,
    private val mediaHealthPreflight: (EditorState) -> MediaHealthReport? = { it.media.healthReport },
    private val projectDependencyManifest: (EditorState) -> ProjectDependencyManifest = {
        ProjectDependencyManifest(emptyList())
    },
    private val audioEngine: com.novacut.editor.engine.AudioEngine? = null,
    private val exportIncidentStore: ExportIncidentStore? = null,
    private val appVersion: String = "unknown",
    private val ffmpegEngine: com.novacut.editor.engine.FFmpegEngine? = null,
    private val includeDiagnosticRawErrorText: () -> Boolean = { false },
    private val projectFingerprint: (EditorState) -> String = { "" },
) {
    private fun text(resId: Int, vararg args: Any): String =
        appContext.getString(resId, *args)

    /**
     * Turn the engine's recorded failure cause into the sentence the user reads:
     * what went wrong, then what to do about it. Only [VideoEngine.ExportFailureCause.UNKNOWN]
     * and a missing cause may fall back to the generic copy.
     */
    private fun exportFailureText(cause: VideoEngine.ExportFailureCause?): String {
        val copy = exportFailureCopyFor(cause)
        return text(copy.messageRes) + " " + text(copy.remediationRes)
    }

    private fun renderDegradationFailureText(e: RenderDegradationException): String =
        text(R.string.export_failure_gpu_degraded) + " " +
            text(R.string.export_failure_gpu_degraded_fix) + "\n" + e.outcome.summary

    // --- Export ---
    private val progressSamples = mutableListOf<Float>()
    // Holder for the GIF-style / contact-sheet / any other non-Transformer
    // export coroutine. The Transformer-based video export is cancelled via
    // `videoEngine.cancelExport()` directly; this job covers the paths that
    // run outside VideoEngine. Named broadly because the two current callers
    // (GIF encode, contact-sheet render) + any future CPU-only export paths
    // all need the same cancel/teardown plumbing.
    @Volatile private var nonVideoExportJob: kotlinx.coroutines.Job? = null
    @Volatile private var activeVideoExportJob: kotlinx.coroutines.Job? = null
    private val saveToGalleryGate = ExportSaveGate()
    private data class ActiveResumeSession(
        val outputFile: File,
        val eligible: Boolean,
        val config: ExportConfig,
        val projectFingerprint: String,
        val configFingerprint: String,
        val sourcePartialFile: File? = null,
        val supersededHistoryId: String? = null,
    )
    @Volatile private var activeResumeSession: ActiveResumeSession? = null
    private val preservedResumeOutputPaths = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    // Set for the lifetime of an export the user allowed through preflight
    // warnings; appended to every history row that run produces.
    @Volatile private var acceptedFallbackNote: String? = null
    // Runtime encoder fallbacks and output-contract decisions are appended to
    // the same run's history rows so a successful-looking export remains
    // auditable after the Transformer has returned.
    @Volatile private var runtimeExportNote: String? = null
    private class NoGifFramesException : Exception()
    private val exportHistoryStore = ExportHistoryStore.forContext(appContext)
    private val batchExportPlanStore = BatchExportPlanStore.forContext(appContext)
    private val batchPlanWriteMutex = Mutex()
    private val batchPlanWriteRevision = AtomicLong(0L)
    @Volatile private var batchPlanContext: BatchExportPlanContext? = null
    @Volatile private var batchExportJob: kotlinx.coroutines.Job? = null
    @Volatile private var activeBatchItemId: String? = null
    @Volatile private var batchPauseRequested = false
    @Volatile private var batchCancelRequested = false
    @Volatile private var lastCancelledBatchResumePath: String? = null

    private fun noteRuntimeExport(note: String) {
        if (note.isBlank()) return
        synchronized(this) {
            val current = runtimeExportNote
            if (current == null) {
                runtimeExportNote = note
            } else if (!current.contains(note)) {
                runtimeExportNote = "$current $note"
            }
        }
    }

    private inline fun updateExport(transform: (EditorExportDomainState) -> EditorExportDomainState) {
        stateFlow.update { it.copyExport(transform) }
    }

    private suspend fun buildAudioConformance(state: EditorState): com.novacut.editor.engine.AudioConformanceReport? {
        val engine = audioEngine ?: return null
        val allClips = state.tracks.flatMap { it.clips }
        if (allClips.isEmpty()) return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val formats = mutableMapOf<String, com.novacut.editor.engine.AudioFormatInfo>()
            for (clip in allClips) {
                val extractor = android.media.MediaExtractor()
                try {
                    extractor.setDataSource(appContext, clip.sourceUri, null)
                    for (i in 0 until extractor.trackCount) {
                        val fmt = extractor.getTrackFormat(i)
                        val mime = fmt.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("audio/")) {
                            val sr = if (fmt.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE))
                                fmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) else 0
                            val ch = if (fmt.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT))
                                fmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) else 0
                            val dur = if (fmt.containsKey(android.media.MediaFormat.KEY_DURATION))
                                fmt.getLong(android.media.MediaFormat.KEY_DURATION) else 0L
                            if (sr > 0 && ch > 0) {
                                formats[clip.id] = com.novacut.editor.engine.AudioFormatInfo(sr, ch, mime, dur)
                            }
                            break
                        }
                    }
                } catch (e: Exception) {
                    AppLog.w("ExportDelegate", "Audio conformance probe failed for clip ${clip.id}", e)
                } finally {
                    extractor.release()
                }
            }
            if (formats.isEmpty()) return@withContext null
            engine.buildConformanceReport(formats)
        }
    }

    fun loadExportHistory() {
        scope.launch(Dispatchers.IO) {
            val history = exportHistoryStore.read()
            withContext(Dispatchers.Main) {
                updateExport { it.copy(history = history) }
            }
        }
    }

    private var lastProgressTime = 0L
    private var lastProgressValue = 0f

    private fun markExportStarted(startedAtMs: Long = System.currentTimeMillis()): Long {
        progressSamples.clear()
        lastProgressTime = startedAtMs
        lastProgressValue = 0f
        updateExport {
            it.copy(
                startTime = startedAtMs,
                progress = 0f,
                state = ExportState.EXPORTING,
                errorMessage = null,
                warningMessage = null,
                lastExportedFilePath = null,
                encoderName = null,
                etaMs = null,
                stallWarning = false
            )
        }
        return startedAtMs
    }

    fun setEncoderName(config: ExportConfig) {
        val mimeType = if (config.exportAudioOnly || config.exportStemsOnly) {
            config.audioCodec.mimeType
        } else config.codec.mimeType
        val name = try {
            val codecs = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
                .codecInfos.filter { it.isEncoder }
                .filter { info -> info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } }
            val hw = codecs.firstOrNull { !it.name.startsWith("c2.android.") }
            val sw = codecs.firstOrNull { it.name.startsWith("c2.android.") }
            when {
                hw != null -> "HW: ${hw.name}"
                sw != null -> "SW: ${sw.name}"
                codecs.isNotEmpty() -> codecs.first().name
                else -> "Unknown"
            }
        } catch (_: Exception) { "Unknown" }
        updateExport { it.copy(encoderName = name) }
    }

    private fun sampleProgress(progress: Float) {
        synchronized(progressSamples) {
            progressSamples.add(progress.coerceIn(0f, 1f))
        }
        val now = System.currentTimeMillis()
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped > 0.01f && clamped < 0.99f) {
            val startTime = stateFlow.value.export.startTime
            val elapsedMs = now - startTime
            val estimatedTotalMs = (elapsedMs / clamped).toLong()
            val remainingMs = (estimatedTotalMs - elapsedMs).coerceAtLeast(0L)
            updateExport { it.copy(etaMs = remainingMs) }
        }
        val stallThresholdMs = 30_000L
        if (now - lastProgressTime > stallThresholdMs && clamped <= lastProgressValue + 0.001f && clamped > 0f) {
            updateExport { it.copy(stallWarning = true) }
        } else {
            if (clamped > lastProgressValue + 0.001f) {
                lastProgressTime = now
                lastProgressValue = clamped
                val current = stateFlow.value.export
                if (current.stallWarning) {
                    updateExport { it.copy(stallWarning = false) }
                }
            }
        }
    }

    private fun recordExportIncident(
        sourceState: EditorState,
        failedPhase: String,
        error: Throwable?,
        errorMessage: String?,
        config: ExportConfig,
        timelineDurationMs: Long,
        startedAtMs: Long,
        streamCopyAttempted: Boolean = false,
        healthReport: MediaHealthReport? = sourceState.media.healthReport,
        subjectClipId: String? = null,
    ) {
        val store = exportIncidentStore ?: return
        val samples = synchronized(progressSamples) { progressSamples.toList() }
        val includeRawErrorText = includeDiagnosticRawErrorText()
        scope.launch(Dispatchers.IO) {
            runCatching {
                val bundle = ExportIncidentBuilder.build(
                    appVersion = appVersion,
                    projectId = sourceState.project.id,
                    projectName = sourceState.project.name,
                    failedPhase = failedPhase,
                    error = error,
                    errorMessage = errorMessage,
                    codecLabel = if (config.exportAudioOnly || config.exportStemsOnly) {
                        config.audioCodec.label
                    } else config.codec.label,
                    resolutionLabel = if (config.exportAudioOnly || config.exportStemsOnly) {
                        "Audio"
                    } else config.resolution.label,
                    frameRate = config.frameRate,
                    exportAudioOnly = config.exportAudioOnly,
                    hdrRequested = config.hdr10PlusMetadata,
                    streamCopyAttempted = streamCopyAttempted,
                    timelineDurationMs = timelineDurationMs,
                    startedAtMs = startedAtMs,
                    progressSamples = samples,
                    mediaWarningCount = healthReport?.warningCount ?: 0,
                    mediaBlockingCount = healthReport?.blockingCount ?: 0,
                    mediaHealthSummary = healthReport?.let {
                        "${it.totalReferences} refs, ${it.warningCount} warnings, ${it.blockingCount} blocking"
                    },
                    // Redacted before it is stored: the report must be able to say
                    // "this same clip again" without ever naming the file.
                    subjectAssetId = subjectClipId?.let { RedactedLog.assetId(it) },
                )
                store.save(bundle)
                // Surface the report where the failure is: the export error card.
                updateExport {
                    it.copy(lastIncidentReport = bundle.toCopyableReport(includeRawErrorText))
                }
            }
        }
    }

    private fun recordExportHistory(
        sourceState: EditorState,
        status: ExportHistoryStatus,
        startedAtMs: Long,
        outputFile: File?,
        config: ExportConfig,
        timelineDurationMs: Long,
        errorMessage: String? = null,
        diagnosticSummary: String? = null,
        healthReport: MediaHealthReport? = sourceState.media.healthReport,
        resumePartialFile: File? = null,
        resumeProjectFingerprint: String? = null,
        resumeConfigFingerprint: String? = null,
        supersededHistoryId: String? = null,
    ) {
        val finishedAtMs = System.currentTimeMillis()
        // An export the user let through despite preflight warnings produced a
        // file that differs from the timeline. Every history row for that run
        // carries the accepted list so the difference stays attributable.
        val summaryWithConsent = listOfNotNull(
            diagnosticSummary,
            acceptedFallbackNote,
            runtimeExportNote,
        )
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ")
        val resolvedRange = config.timelineRange?.resolve(
            timebase = sourceState.project.timelineTimebase,
            totalDurationMs = sourceState.tracks
                .flatMap { it.clips }
                .maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
        )
        val entry = buildExportHistoryEntry(
            projectId = sourceState.project.id,
            projectName = sourceState.project.name,
            status = status,
            startedAtEpochMs = startedAtMs,
            finishedAtEpochMs = finishedAtMs,
            outputFile = outputFile,
            config = config,
            timelineDurationMs = timelineDurationMs,
            resumePartialFile = resumePartialFile,
            resumeProjectFingerprint = resumeProjectFingerprint,
            resumeConfigFingerprint = resumeConfigFingerprint,
            resolvedRange = resolvedRange,
            errorMessage = errorMessage,
            diagnosticSummary = summaryWithConsent,
            mediaWarningCount = healthReport?.warningCount ?: 0,
            mediaBlockingCount = healthReport?.blockingCount ?: 0
        )
        scope.launch(Dispatchers.IO) {
            var history = exportHistoryStore.append(entry)
            if (supersededHistoryId != null && supersededHistoryId != entry.id) {
                history = exportHistoryStore.remove(supersededHistoryId)
            }
            withContext(Dispatchers.Main) {
                updateExport { it.copy(history = history) }
            }
        }
    }

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
        config: com.novacut.editor.model.ExportConfig,
        templateState: EditorState? = null,
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
        val state = templateState ?: stateFlow.value
        val projectDurationMs = state.tracks
            .flatMap { it.clips }
            .maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
        val totalDurationMs = config.timelineRange
            ?.resolve(state.project.timelineTimebase, projectDurationMs)
            ?.durationMs
            ?: projectDurationMs
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
    private suspend fun tryStreamCopy(
        tracks: List<com.novacut.editor.model.Track>,
        config: ExportConfig,
        textOverlays: List<com.novacut.editor.model.TextOverlay>,
        state: EditorState,
        outputFile: File,
        startedAtMs: Long,
        totalDurationMs: Long,
        healthReport: MediaHealthReport?,
        resumeFromFile: File? = null,
    ): Boolean {
        val engine = streamCopyEngine ?: return false
        if (!config.allowStreamCopy) return false
        // A copied packet stream preserves the source cadence. CFR requests
        // must take the rendered/pre-normalized path so the selected target is
        // actually present in the output samples.
        if (config.forceConstantFrameRate) return false
        if (resumeFromFile != null) return false
        // Stream-copy retains source metadata verbatim and cannot honor the
        // explicit scrub or opt-in metadata policy. Force the Transformer so
        // the same filtered muxer contract applies to every requested field.
        if (config.scrubMetadata) return false
        if (config.preserveSourceLocationMetadata || config.preserveSourceStreamMetadata) return false
        // A selected range must flow through the Transformer so every track,
        // overlay, caption, and effect is rebased consistently. Stream-copy
        // only understands the untouched single-source trim contract.
        if (config.timelineRange != null) return false
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
        val storageCheck = ExportStoragePolicy.check(
            request = ExportStoragePolicy.request(
                totalDurationMs,
                config,
                tracks,
                sourceSizeBytes = { clip -> querySourceSize(appContext, clip.sourceUri).takeIf { it > 0L } },
            ),
            outputDirectory = outputFile.parentFile ?: appContext.cacheDir,
            cacheDirectory = appContext.cacheDir,
        )
        if (!storageCheck.canProceed) {
            val message = appContext.exportStorageFailureMessage(requireNotNull(storageCheck.failure))
            updateExport { it.copy(state = ExportState.ERROR, progress = 0f, errorMessage = message) }
            recordExportHistory(
                sourceState = state,
                status = ExportHistoryStatus.BLOCKED,
                startedAtMs = startedAtMs,
                outputFile = null,
                config = config,
                timelineDurationMs = totalDurationMs,
                errorMessage = message,
                diagnosticSummary = "Storage changed before stream-copy output started.",
                healthReport = healthReport,
            )
            showToast(message)
            return true
        }
        val ok = engine.execute(eligibility, outputFile.absolutePath) { progress ->
            updateExport { it.copy(progress = progress) }
        }
        if (!ok) {
            com.novacut.editor.engine.AppLog.w("ExportDelegate", "stream-copy failed, falling back to Transformer")
            runCatching { outputFile.delete() }
            return false
        }
        val requestedDimensions = config.resolution.forAspect(config.aspectRatio)
        val safeDimensions = Media3ExportRobustnessPolicy.encoderSafeDimensions(
            requestedDimensions.first,
            requestedDimensions.second,
        )
        val verification = ExportOutputVerifier.verify(
            outputFile = outputFile,
            expectVideo = true,
            expectedAudioMimeType = com.novacut.editor.model.AudioCodec.AAC.mimeType,
            expectedVideoMimeType = config.codec.mimeType,
            expectedVideoWidth = safeDimensions.width,
            expectedVideoHeight = safeDimensions.height,
            expectedFrameRate = config.frameRate.toFloat(),
            expectedContainer = expectedContainerForExtension(outputFile.extension),
        )
        if (!verification.valid) {
            noteRuntimeExport(
                "Stream-copy output rejected; falling back to Transformer: " +
                    (verification.reason ?: "output contract mismatch")
            )
            com.novacut.editor.engine.AppLog.w(
                "ExportDelegate",
                "stream-copy output contract rejected: ${verification.reason}",
            )
            runCatching { outputFile.delete() }
            return false
        }
        val finalizedFile = finalizeFilenameSize(outputFile)
        reportSidecarOutcome(writeAiDisclosureSidecarIfRequested(finalizedFile, config, state))
        updateExport {
            it.copy(
                state = ExportState.COMPLETE,
                progress = 1f,
                lastExportedFilePath = finalizedFile.absolutePath
            )
        }
        recordExportHistory(
            sourceState = state,
            status = ExportHistoryStatus.COMPLETE,
            startedAtMs = startedAtMs,
            outputFile = finalizedFile,
            config = config,
            timelineDurationMs = totalDurationMs,
            diagnosticSummary = "Stream-copy export completed without transcoding.",
            healthReport = healthReport
        )
        showToast(appContext.getString(R.string.export_stream_copy_complete_toast, finalizedFile.name))
        return true
    }

    private fun buildMixedRenderPlan(
        tracks: List<com.novacut.editor.model.Track>,
        config: ExportConfig,
        textOverlays: List<com.novacut.editor.model.TextOverlay>,
        state: EditorState,
        outputFile: File
    ) = if (config.timelineRange == null && tracks.none { it.timelineOffsetMs != 0L }) MixedRenderExportPlanner.buildPlan(
        tracks = tracks,
        config = config,
        finalOutputName = outputFile.name,
        projectStem = outputFile.nameWithoutExtension,
        textOverlays = textOverlays,
        hasImageOverlays = state.imageOverlays.isNotEmpty(),
        hasTrackedObjects = state.trackedObjects.any { it.isEnabled },
    ) else null

    private fun resumeEligibility(
        state: EditorState,
        config: ExportConfig = state.exportConfig,
        outputExtension: String = "mp4",
    ): ExportResumePolicy.Decision = ExportResumePolicy.evaluate(
        tracks = state.tracks,
        config = config,
        outputExtension = outputExtension,
        textOverlayCount = state.textOverlays.size,
        imageOverlayCount = state.imageOverlays.size,
        trackedObjectCount = state.trackedObjects.count { it.isEnabled },
        globalTransitionCount = state.globalTransitions.size,
    )

    private fun isOwnedResumeFile(file: File): Boolean {
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return false
        val roots = listOfNotNull(
            appContext.filesDir,
            appContext.cacheDir,
            appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        ).mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
        return roots.any { root ->
            val rootPath = root.absolutePath.trimEnd(File.separatorChar) + File.separator
            candidate.absolutePath.startsWith(rootPath, ignoreCase = true)
        }
    }

    private fun deleteOwnedResumeFile(file: File?) {
        if (file != null && isOwnedResumeFile(file)) {
            runCatching { file.delete() }
        }
    }

    fun cancelExport() {
        val currentState = stateFlow.value
        val startedAtMs = currentState.exportStartTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val cancellingNonVideoExport = nonVideoExportJob != null
        val resumeSession = activeResumeSession
        // Cancel GIF export coroutine if one is running
        nonVideoExportJob?.cancel()
        nonVideoExportJob = null
        activeVideoExportJob?.cancel()
        activeVideoExportJob = null
        val preservedOutput = if (!cancellingNonVideoExport && resumeSession != null) {
            val preserve = resumeSession.eligible
            if (preserve) preservedResumeOutputPaths += resumeSession.outputFile.absolutePath
            videoEngine.cancelExport(preservePartial = preserve)
        } else {
            videoEngine.cancelExport()
        }
        val resumePartial = if (resumeSession?.eligible == true) {
            preservedOutput?.takeIf { it.isFile && it.length() > 0L }
                ?: resumeSession.sourcePartialFile
                ?: preservedOutput
                ?: resumeSession.outputFile
        } else {
            null
        }
        lastCancelledBatchResumePath = if (activeBatchItemId != null) {
            resumePartial?.absolutePath
        } else {
            null
        }
        if (resumeSession?.sourcePartialFile != null &&
            resumePartial != null &&
            resumePartial.absolutePath != resumeSession.sourcePartialFile.absolutePath
        ) {
            deleteOwnedResumeFile(resumeSession.sourcePartialFile)
        }
        if (resumeSession?.sourcePartialFile != null &&
            resumePartial?.absolutePath == resumeSession.sourcePartialFile.absolutePath
        ) {
            deleteOwnedResumeFile(resumeSession.outputFile)
        }
        activeResumeSession = null
        // Always push CANCELLED to the UI. The if-guard was only needed when we worried about
        // overwriting a COMPLETE state, but cancelExport() is only called by explicit user
        // action, so CANCELLED is always the right terminal state to show here.
        updateExport {
            it.copy(
                state = ExportState.CANCELLED,
                progress = 0f
            )
        }
        if (!cancellingNonVideoExport) {
            recordExportHistory(
                sourceState = currentState,
                status = ExportHistoryStatus.CANCELLED,
                startedAtMs = startedAtMs,
                outputFile = resumePartial,
                config = resumeSession?.config ?: currentState.exportConfig,
                timelineDurationMs = currentState.totalDurationMs,
                diagnosticSummary = "Export was cancelled by the user.",
                healthReport = currentState.media.healthReport,
                resumePartialFile = resumePartial,
                resumeProjectFingerprint = resumeSession?.projectFingerprint,
                resumeConfigFingerprint = resumeSession?.configFingerprint,
                supersededHistoryId = resumeSession?.supersededHistoryId,
            )
        }
    }

    fun startExport(
        outputDir: File,
        preferredOutputName: String? = null,
        currentStateOverride: EditorState? = null,
        outputFileOverride: File? = null,
        batchResumePartialPath: String? = null,
    ) {
        val currentState = currentStateOverride ?: stateFlow.value
        if (stateFlow.value.exportState == ExportState.EXPORTING) {
            showToast(appContext.getString(R.string.export_already_in_progress_toast))
            return
        }
        if (currentState.tracks.flatMap { it.clips }.isEmpty()) {
            showToast(appContext.getString(R.string.export_no_clips_toast))
            return
        }
        var exportJob: kotlinx.coroutines.Job? = null
        exportJob = scope.launch {
            try {
                startExportAsync(
                    outputDir = outputDir,
                    preferredOutputName = preferredOutputName,
                    currentState = currentState,
                    outputFileOverride = outputFileOverride,
                    batchResumePartialPath = batchResumePartialPath,
                )
            } finally {
                if (activeVideoExportJob === exportJob) activeVideoExportJob = null
            }
        }
        activeVideoExportJob = exportJob
    }

    fun resumeExport(entry: ExportHistoryEntry) {
        val currentState = stateFlow.value
        if (currentState.exportState == ExportState.EXPORTING) {
            showToast(appContext.getString(R.string.export_already_in_progress_toast))
            return
        }
        val partialFile = entry.resumePartialPath?.let(::File)
        val decision = partialFile?.let { resumeEligibility(currentState, outputExtension = it.extension) }
        val fingerprintsMatch = entry.projectId == currentState.project.id &&
            entry.resumeProjectFingerprint == projectFingerprint(currentState) &&
            entry.resumeConfigFingerprint == exportConfigFingerprint(currentState.exportConfig)
        val canResume = entry.status == ExportHistoryStatus.CANCELLED &&
            partialFile != null &&
            decision?.eligible == true &&
            fingerprintsMatch &&
            isOwnedResumeFile(partialFile) &&
            partialFile.isFile &&
            partialFile.length() > 0L
        if (!canResume) {
            deleteOwnedResumeFile(partialFile)
            val message = text(R.string.export_resume_unavailable)
            updateExport {
                it.copy(
                    state = ExportState.ERROR,
                    progress = 0f,
                    errorMessage = message,
                    lastExportedFilePath = null,
                )
            }
            scope.launch(Dispatchers.IO) {
                val history = exportHistoryStore.remove(entry.id)
                withContext(Dispatchers.Main) {
                    updateExport { it.copy(history = history) }
                }
            }
            showToast(message)
            return
        }
        showToast(text(R.string.export_resume_started))
        scope.launch {
            startExportAsync(
                outputDir = requireNotNull(partialFile.parentFile),
                preferredOutputName = partialFile.nameWithoutExtension,
                currentState = currentState,
                resumeCandidate = entry,
            )
        }
    }

    /**
     * Proceed with an export whose preflight warnings the user just accepted.
     * The accepted set is recorded so the produced file's history says which
     * render intents were traded away.
     */
    fun confirmPendingExport() {
        val request = stateFlow.value.export.pendingConfirmation ?: return
        updateExport { it.copy(pendingConfirmation = null) }
        val currentState = stateFlow.value
        scope.launch {
            startExportAsync(
                outputDir = File(request.outputDirPath),
                preferredOutputName = request.preferredOutputName,
                currentState = currentState,
                acceptedConfirmation = request,
            )
        }
    }

    /** Abandon a held-back export. Nothing was rendered, so nothing is cleaned up. */
    fun dismissPendingExport() {
        val request = stateFlow.value.export.pendingConfirmation ?: return
        updateExport { it.copy(pendingConfirmation = null) }
        recordExportHistory(
            sourceState = stateFlow.value,
            status = ExportHistoryStatus.CANCELLED,
            startedAtMs = System.currentTimeMillis(),
            outputFile = null,
            config = stateFlow.value.exportConfig,
            timelineDurationMs = stateFlow.value.totalDurationMs,
            diagnosticSummary = "User declined ${request.warnings.size} export warning(s) before work started.",
        )
    }

    /**
     * Render-intent fallbacks this export already knows about. Reversed clips are
     * the current case: an unavailable backend or an over-length clip exports
     * forward video, which is a different result than the timeline shows.
     */
    private fun reverseIntentFallbacks(state: EditorState): List<ExportIntentFallback> {
        val reversedClips = state.tracks.flatMap { track -> track.clips.filter { it.isReversed } }
        if (reversedClips.isEmpty()) return emptyList()

        if (!videoEngine.isReverseRenderAvailable()) {
            return reversedClips.map { clip ->
                ExportIntentFallback(
                    stage = "reverse-render",
                    subjectId = clip.id,
                    message = requireNotNull(
                        reverseRenderFallbackMessage(
                            clipId = clip.id,
                            clipDurationMs = clip.trimEndMs - clip.trimStartMs,
                            reverseRenderAvailable = false,
                        )
                    ),
                )
            }
        }

        return reversedClips.mapNotNull { clip ->
            val clipDurationMs = clip.trimEndMs - clip.trimStartMs
            val fallbackMessage = reverseRenderFallbackMessage(
                clipId = clip.id,
                clipDurationMs = clipDurationMs,
                reverseRenderAvailable = true,
                maxDurationMs = MAX_REVERSE_CLIP_DURATION_MS,
            ) ?: return@mapNotNull null
            ExportIntentFallback(
                stage = "reverse-render",
                subjectId = clip.id,
                message = fallbackMessage,
            )
        }
    }

    private suspend fun startExportAsync(
        outputDir: File,
        preferredOutputName: String?,
        currentState: EditorState,
        acceptedConfirmation: ExportConfirmationRequest? = null,
        resumeCandidate: ExportHistoryEntry? = null,
        outputFileOverride: File? = null,
        batchResumePartialPath: String? = null,
    ) {
        acceptedFallbackNote = acceptedConfirmation?.acceptedFallbackSummary()
        runtimeExportNote = null
        val healthReport = mediaHealthPreflight(currentState)
        val audioConformance = buildAudioConformance(currentState)
        val hdrOverlaySummary = withContext(Dispatchers.IO) {
            HdrOverlayAssetInspector.inspect(
                context = appContext,
                textOverlays = currentState.textOverlays,
                imageOverlays = currentState.imageOverlays,
                watermark = currentState.exportConfig.watermark,
            )
        }
        val hdrOverlayDisclosure = HdrOverlayPolicy.evaluate(
            hdrRequested = currentState.exportConfig.hdr10PlusMetadata,
            codec = currentState.exportConfig.codec,
            overlays = hdrOverlaySummary,
        ).disclosure
        val unsupportedTrackBlendCount = TrackBlendModeCapability
            .unsupportedTracks(currentState.tracks)
            .size
        val renderWarnings = buildList {
            hdrOverlayDisclosure?.let(::add)
            if (unsupportedTrackBlendCount > 0) {
                add(text(R.string.export_warning_track_blend_unsupported, unsupportedTrackBlendCount))
            }
        }
        val mediaPreflight = ExportMediaPreflight.evaluate(
            healthReport = healthReport,
            relinkReports = currentState.media.relinkReports,
            audioConformance = audioConformance,
            dependencies = projectDependencyManifest(currentState),
            additionalWarnings = renderWarnings,
            intentFallbacks = reverseIntentFallbacks(currentState),
        )
        stateFlow.update { state ->
            state.copyMedia { media -> media.copy(healthReport = healthReport) }
        }
        if (!mediaPreflight.canExport) {
            stateFlow.update { state ->
                dismissedPanelState(state)
                    .copyExport { export ->
                        export.copy(
                            state = ExportState.ERROR,
                            progress = 0f,
                            errorMessage = mediaPreflight.message,
                            lastExportedFilePath = null
                        )
                    }
                    .copyPanel { panel ->
                        panel.copy(panels = panel.panels.closeAll().open(PanelId.MEDIA_MANAGER))
                    }
            }
            recordExportHistory(
                sourceState = currentState,
                status = ExportHistoryStatus.BLOCKED,
                startedAtMs = System.currentTimeMillis(),
                outputFile = null,
                config = currentState.exportConfig,
                timelineDurationMs = currentState.totalDurationMs,
                errorMessage = mediaPreflight.message,
                diagnosticSummary = "Media preflight blocked export: ${mediaPreflight.message}",
                healthReport = healthReport
            )
            showToast(mediaPreflight.message)
            return
        }

        // Fail closed on warnings: work only starts once the user has seen every
        // warning and accepted it. Without this the preflight result was computed
        // and then discarded, and reversed clips that could not be rendered were
        // exported forward with nothing but a log line.
        if (mediaPreflight.requiresConsent && acceptedConfirmation == null) {
            updateExport { export ->
                export.copy(
                    pendingConfirmation = ExportConfirmationRequest(
                        outputDirPath = outputDir.absolutePath,
                        preferredOutputName = preferredOutputName,
                        summary = mediaPreflight.message,
                        warnings = mediaPreflight.warnings,
                        intentFallbacks = mediaPreflight.intentFallbacks,
                    )
                )
            }
            return
        }

        val projectDurationMs = currentState.tracks
            .flatMap { it.clips }
            .maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
        val requestedRange = currentState.exportConfig.timelineRange
        val resolvedRange = requestedRange?.resolve(
            timebase = currentState.project.timelineTimebase,
            totalDurationMs = projectDurationMs,
        )
        if (requestedRange != null && resolvedRange == null) {
            val message = text(R.string.export_range_invalid)
            val invalidConfig = currentState.exportConfig.copy(
                aspectRatio = currentState.exportConfig.outputAspectRatio(currentState.project.aspectRatio),
            )
            updateExport {
                it.copy(
                    state = ExportState.ERROR,
                    progress = 0f,
                    errorMessage = message,
                    lastExportedFilePath = null,
                )
            }
            recordExportHistory(
                sourceState = currentState,
                status = ExportHistoryStatus.BLOCKED,
                startedAtMs = System.currentTimeMillis(),
                outputFile = null,
                config = invalidConfig,
                timelineDurationMs = projectDurationMs,
                errorMessage = message,
                diagnosticSummary = "Export range was incomplete or outside the project timeline.",
                healthReport = healthReport,
            )
            showToast(message)
            return
        }

        val totalDurationMs = resolvedRange?.durationMs ?: projectDurationMs
        val outputAspectRatio = currentState.exportConfig.outputAspectRatio(currentState.project.aspectRatio)
        val config = currentState.exportConfig
            .copy(aspectRatio = outputAspectRatio)
            .resolveTargetSize(totalDurationMs)
        val baseChapters = if (config.includeChapterMarkers && config.chapters.isEmpty()) {
            currentState.timelineMarkers
                .sortedBy { it.timeMs }
                .map { ChapterMarker(timeMs = it.timeMs, title = it.label.ifBlank { "Chapter" }) }
        } else config.chapters
        val slicedExport = resolvedRange?.let { range ->
            TimelineRangeExportEngine.slice(
                tracks = currentState.tracks,
                range = range,
                textOverlays = currentState.textOverlays,
                imageOverlays = currentState.imageOverlays,
                trackedObjects = currentState.trackedObjects,
                globalTransitions = currentState.globalTransitions,
                chapters = baseChapters,
            )
        }
        val configWithChapters = config.copy(
            chapters = slicedExport?.chapters ?: baseChapters,
        )
        val tracks = slicedExport?.tracks ?: currentState.tracks
        val textOverlays = slicedExport?.textOverlays ?: currentState.textOverlays
        val imageOverlays = slicedExport?.imageOverlays ?: currentState.imageOverlays
        val trackedObjects = slicedExport?.trackedObjects ?: currentState.trackedObjects
        val globalTransitions = slicedExport?.globalTransitions ?: currentState.globalTransitions

        val resumeSourceFile = (
            resumeCandidate?.resumePartialPath ?: batchResumePartialPath
        )?.let(::File)
        if (resumeCandidate != null) {
            val resumeValid = resumeSourceFile != null &&
                resumeEligibility(currentState, outputExtension = "mp4").eligible &&
                resumeCandidate.projectId == currentState.project.id &&
                resumeCandidate.resumeProjectFingerprint == projectFingerprint(currentState) &&
                resumeCandidate.resumeConfigFingerprint == exportConfigFingerprint(currentState.exportConfig) &&
                isOwnedResumeFile(resumeSourceFile) &&
                resumeSourceFile.isFile &&
                resumeSourceFile.length() > 0L
            if (!resumeValid) {
                deleteOwnedResumeFile(resumeSourceFile)
                val message = text(R.string.export_resume_unavailable)
                updateExport {
                    it.copy(
                        state = ExportState.ERROR,
                        progress = 0f,
                        errorMessage = message,
                        lastExportedFilePath = null,
                    )
                }
                recordExportHistory(
                    sourceState = currentState,
                    status = ExportHistoryStatus.FAILED,
                    startedAtMs = System.currentTimeMillis(),
                    outputFile = null,
                    config = currentState.exportConfig,
                    timelineDurationMs = totalDurationMs,
                    errorMessage = message,
                    diagnosticSummary = "Media3 resume was no longer eligible or its partial file was unavailable.",
                    healthReport = healthReport,
                    supersededHistoryId = resumeCandidate.id,
                )
                showToast(message)
                return
            }
        }

        withContext(Dispatchers.IO) { outputDir.mkdirs() }
        val storageCheck = ExportStoragePolicy.check(
            request = ExportStoragePolicy.request(
                totalDurationMs,
                configWithChapters,
                tracks,
                sourceSizeBytes = { clip -> querySourceSize(appContext, clip.sourceUri).takeIf { it > 0L } },
            ),
            outputDirectory = outputDir,
            cacheDirectory = appContext.cacheDir,
        )
        if (!storageCheck.canProceed) {
            val message = appContext.exportStorageFailureMessage(requireNotNull(storageCheck.failure))
            updateExport {
                it.copy(
                    state = ExportState.ERROR,
                    progress = 0f,
                    errorMessage = message,
                    lastExportedFilePath = null,
                )
            }
            recordExportHistory(
                sourceState = currentState,
                status = ExportHistoryStatus.BLOCKED,
                startedAtMs = System.currentTimeMillis(),
                outputFile = null,
                config = configWithChapters,
                timelineDurationMs = totalDurationMs,
                errorMessage = message,
                diagnosticSummary = "Storage preflight blocked export before output work started.",
                healthReport = healthReport,
            )
            showToast(message)
            return
        }

        // Contact-sheet export path — renders one PNG grid of clip thumbnails.
        // Short path because there's no Transformer, no foreground service, no audio.
        if (configWithChapters.exportAsContactSheet) {
            val startedAtMs = markExportStarted()
            nonVideoExportJob = scope.launch {
                var sheetFile: File? = null
                try {
                    withContext(Dispatchers.IO) { outputDir.mkdirs() }
                    sheetFile = createOutputFile(
                        outputDir = outputDir,
                        extension = "png",
                        preferredOutputName = (preferredOutputName ?: currentState.project.name) + "_contact",
                        configOverride = configWithChapters,
                        stateOverride = currentState,
                    )
                    val targetSheetFile = sheetFile ?: return@launch
                    val allClips = tracks
                        .filter { it.type == com.novacut.editor.model.TrackType.VIDEO || it.type == com.novacut.editor.model.TrackType.OVERLAY }
                        .flatMap { it.clips }
                        .sortedBy { it.timelineStartMs }
                    if (allClips.isEmpty()) {
                        val message = "No video clips"
                        updateExport {
                            it.copy(
                                state = ExportState.ERROR,
                                errorMessage = message
                            )
                        }
                        recordExportHistory(
                            sourceState = currentState,
                            status = ExportHistoryStatus.FAILED,
                            startedAtMs = startedAtMs,
                            outputFile = null,
                            config = configWithChapters,
                            timelineDurationMs = totalDurationMs,
                            errorMessage = message,
                            diagnosticSummary = "Contact sheet export had no video clips to render.",
                            healthReport = healthReport
                        )
                        return@launch
                    }
                    val ok = ContactSheetExporter.export(
                        clips = allClips,
                        columns = configWithChapters.contactSheetColumns,
                        outputFile = targetSheetFile,
                        extractThumb = { uri, timeUs, w, h -> videoEngine.extractThumbnail(uri, timeUs, w, h) },
                        onProgress = { p -> updateExport { it.copy(progress = p) } }
                    )
                    if (ok) {
                        val finalizedSheetFile = finalizeFilenameSize(targetSheetFile)
                        sheetFile = finalizedSheetFile
                        updateExport {
                            it.copy(
                                state = ExportState.COMPLETE,
                                progress = 1f,
                                lastExportedFilePath = finalizedSheetFile.absolutePath
                            )
                        }
                        recordExportHistory(
                            sourceState = currentState,
                            status = ExportHistoryStatus.COMPLETE,
                            startedAtMs = startedAtMs,
                            outputFile = finalizedSheetFile,
                            config = configWithChapters,
                            timelineDurationMs = totalDurationMs,
                            diagnosticSummary = "Contact sheet export completed.",
                            healthReport = healthReport
                        )
                        showToast(appContext.getString(R.string.export_contact_sheet_toast, finalizedSheetFile.name))
                    } else {
                        val message = "Contact sheet render failed"
                        updateExport {
                            it.copy(
                                state = ExportState.ERROR,
                                errorMessage = message
                            )
                        }
                        recordExportHistory(
                            sourceState = currentState,
                            status = ExportHistoryStatus.FAILED,
                            startedAtMs = startedAtMs,
                            outputFile = targetSheetFile,
                            config = configWithChapters,
                            timelineDurationMs = totalDurationMs,
                            errorMessage = message,
                            diagnosticSummary = "Contact sheet renderer returned no output.",
                            healthReport = healthReport
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    updateExport {
                        it.copy(
                            state = ExportState.CANCELLED,
                            progress = 0f,
                            lastExportedFilePath = null
                        )
                    }
                    recordExportHistory(
                        sourceState = currentState,
                        status = ExportHistoryStatus.CANCELLED,
                        startedAtMs = startedAtMs,
                        outputFile = null,
                        config = configWithChapters,
                        timelineDurationMs = totalDurationMs,
                        diagnosticSummary = "Contact sheet export was cancelled.",
                        healthReport = healthReport
                    )
                } catch (e: Exception) {
                    com.novacut.editor.engine.AppLog.w("ExportDelegate", "Contact sheet export failed", e)
                    sheetFile?.delete()
                    val message = e.message ?: "Contact sheet export failed"
                    updateExport {
                        it.copy(
                            state = ExportState.ERROR,
                            errorMessage = message,
                            lastExportedFilePath = null
                        )
                    }
                    recordExportHistory(
                        sourceState = currentState,
                        status = ExportHistoryStatus.FAILED,
                        startedAtMs = startedAtMs,
                        outputFile = null,
                        config = configWithChapters,
                        timelineDurationMs = totalDurationMs,
                        errorMessage = message,
                        diagnosticSummary = "Contact sheet export failed during thumbnail extraction or file write.",
                        healthReport = healthReport
                    )
                } finally {
                    nonVideoExportJob = null
                }
            }
            return
        }

        // GIF export path
        if (configWithChapters.exportAsGif) {
            val startedAtMs = markExportStarted()
            nonVideoExportJob = scope.launch {
                var gifFile: File? = null
                var encodedFrameCount = 0
                try {
                    withContext(Dispatchers.IO) { outputDir.mkdirs() }
                    gifFile = createOutputFile(
                        outputDir = outputDir,
                        extension = "gif",
                        preferredOutputName = preferredOutputName ?: currentState.project.name,
                        configOverride = configWithChapters,
                        stateOverride = currentState,
                    )
                    val targetGifFile = gifFile ?: return@launch
                    val videoTrackClips = tracks
                        .filter { it.type == TrackType.VIDEO }
                        .flatMap { it.clips }
                    val allClips = (videoTrackClips.ifEmpty {
                        tracks.filter { it.type == TrackType.OVERLAY }.flatMap { it.clips }
                    }).sortedBy { it.timelineStartMs }
                    if (allClips.isEmpty()) {
                        val message = "No video clips"
                        updateExport {
                            it.copy(
                                state = ExportState.ERROR,
                                errorMessage = message
                            )
                        }
                        recordExportHistory(
                            sourceState = currentState,
                            status = ExportHistoryStatus.FAILED,
                            startedAtMs = startedAtMs,
                            outputFile = null,
                            config = configWithChapters,
                            timelineDurationMs = totalDurationMs,
                            errorMessage = message,
                            diagnosticSummary = "GIF export had no video clips to sample.",
                            healthReport = healthReport
                        )
                        return@launch
                    }
                    // Keep the resolved range duration, including leading or
                    // trailing gaps. The sliced clips alone cannot describe a
                    // selected range that ends after the last media item.
                    val gifDurationMs = totalDurationMs
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
                    val frameCount = (gifDurationMs / frameIntervalMs).coerceIn(1L, 300L).toInt()
                    val maxWidth = configWithChapters.gifMaxWidth

                    // The GIF header must precede its frames, so determine the maximum
                    // canvas from source metadata before opening the atomic output. This
                    // keeps the encoder one-pass without retaining sampled bitmaps.
                    val sourceSizes = withContext(Dispatchers.IO) {
                        allClips.map { it.sourceUri }
                            .distinct()
                            .associateWith { uri -> videoEngine.getVideoResolution(uri) }
                            .values
                    }
                    val (logicalWidth, logicalHeight) = gifLogicalScreenSize(
                        targetWidth = maxWidth,
                        aspectRatio = configWithChapters.aspectRatio.toFloat(),
                        sourceSizes = sourceSizes,
                    )

                    withContext(Dispatchers.IO) {
                        writeFileAtomically(targetGifFile, requireNonEmpty = true) { tempFile ->
                            tempFile.outputStream().buffered().use { out ->
                                val encoder = GifStreamEncoder(
                                    output = out,
                                    logicalWidth = logicalWidth,
                                    logicalHeight = logicalHeight,
                                    delayMs = frameIntervalMs.toInt(),
                                )
                                for (i in 0 until frameCount) {
                                    ensureActive()
                                    val timeMs = i * frameIntervalMs
                                    val clip = allClips.firstOrNull { clip ->
                                        timeMs >= clip.timelineStartMs &&
                                            timeMs < clip.timelineStartMs + clip.durationMs
                                    }
                                    var frameBitmap: android.graphics.Bitmap? = null
                                    try {
                                        if (clip == null) {
                                            frameBitmap = createGapGifFrame(
                                                maxWidth,
                                                configWithChapters.aspectRatio,
                                            )
                                        } else {
                                            // Respect speedCurve — `timelineOffsetToSourceMs`
                                            // integrates the curve when present and falls back
                                            // to `* speed` for constant speed.
                                            val timelineOffsetInClip = timeMs - clip.timelineStartMs
                                            val clipTimeUs = clip.timelineOffsetToSourceMs(
                                                timelineOffsetInClip
                                            ) * 1000
                                            val bitmap = videoEngine.extractThumbnail(
                                                clip.sourceUri,
                                                clipTimeUs,
                                                maxWidth,
                                            )
                                            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                                                // `bitmap` is owned by VideoEngine's thumbnail
                                                // cache. Only recycle this frames-owned copy.
                                                val targetW = minOf(maxWidth, bitmap.width).coerceAtLeast(1)
                                                val ratio = targetW.toFloat() / bitmap.width
                                                val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                                                frameBitmap = if (
                                                    targetW == bitmap.width && targetH == bitmap.height
                                                ) {
                                                    bitmap.copy(
                                                        bitmap.config
                                                            ?: android.graphics.Bitmap.Config.ARGB_8888,
                                                        false,
                                                    )
                                                } else {
                                                    android.graphics.Bitmap.createScaledBitmap(
                                                        bitmap,
                                                        targetW,
                                                        targetH,
                                                        true,
                                                    )
                                                }
                                            }
                                        }
                                        frameBitmap?.let { frame ->
                                            encoder.addFrame(frame)
                                            encodedFrameCount++
                                        }
                                    } finally {
                                        frameBitmap?.takeUnless { it.isRecycled }?.recycle()
                                    }
                                    updateExport {
                                        it.copy(progress = (i + 1).toFloat() / frameCount * 0.9f)
                                    }
                                }
                                if (encodedFrameCount == 0) throw NoGifFramesException()
                                encoder.finish()
                            }
                        }
                    }

                    val finalizedGifFile = finalizeFilenameSize(targetGifFile)
                    gifFile = finalizedGifFile
                    updateExport {
                        it.copy(
                            state = ExportState.COMPLETE,
                            progress = 1f,
                            lastExportedFilePath = finalizedGifFile.absolutePath
                        )
                    }
                    recordExportHistory(
                        sourceState = currentState,
                        status = ExportHistoryStatus.COMPLETE,
                        startedAtMs = startedAtMs,
                        outputFile = finalizedGifFile,
                        config = configWithChapters,
                        timelineDurationMs = totalDurationMs,
                        diagnosticSummary = "GIF export completed with $frameCount sampled frame(s).",
                        healthReport = healthReport
                    )
                    showToast(appContext.getString(R.string.export_gif_complete_toast, finalizedGifFile.name))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    com.novacut.editor.engine.AppLog.d("ExportDelegate", "GIF export cancelled")
                    gifFile?.delete()
                    updateExport {
                        it.copy(
                            state = ExportState.CANCELLED,
                            progress = 0f,
                            lastExportedFilePath = null
                        )
                    }
                    recordExportHistory(
                        sourceState = currentState,
                        status = ExportHistoryStatus.CANCELLED,
                        startedAtMs = startedAtMs,
                        outputFile = null,
                        config = configWithChapters,
                        timelineDurationMs = totalDurationMs,
                        diagnosticSummary = "GIF export was cancelled.",
                        healthReport = healthReport
                    )
                } catch (e: Exception) {
                    val noFramesExtracted = e is NoGifFramesException
                    if (!noFramesExtracted) {
                        com.novacut.editor.engine.AppLog.w("ExportDelegate", "GIF export failed", e)
                    }
                    gifFile?.delete()
                    val message = if (noFramesExtracted) "No frames extracted" else {
                        e.message ?: "GIF export failed"
                    }
                    updateExport {
                        it.copy(
                            state = ExportState.ERROR,
                            errorMessage = message,
                            lastExportedFilePath = null
                        )
                    }
                    recordExportHistory(
                        sourceState = currentState,
                        status = ExportHistoryStatus.FAILED,
                        startedAtMs = startedAtMs,
                        outputFile = null,
                        config = configWithChapters,
                        timelineDurationMs = totalDurationMs,
                        errorMessage = message,
                        diagnosticSummary = if (noFramesExtracted) {
                            "GIF export could not extract any usable frames."
                        } else {
                            "GIF export failed during frame extraction or encoding."
                        },
                        healthReport = healthReport
                    )
                } finally {
                    nonVideoExportJob = null
                }
            }
            return
        }

        // Audio-only / stems export path — produces standalone `.m4a`
        // (`audio/mp4`) artifacts with no video track. It drives the shared
        // Transformer state machine (so the foreground service and cancel path
        // behave exactly like a video export) but builds an audio-only
        // composition, and never falls back to a video file.
        if (configWithChapters.exportAudioOnly || configWithChapters.exportStemsOnly) {
            val startedAtMs = markExportStarted()
            appContext.startForegroundService(Intent(appContext, ExportService::class.java))
            val baseName = preferredOutputName ?: currentState.project.name
            nonVideoExportJob = scope.launch {
                try {
                    withContext(Dispatchers.IO) { outputDir.mkdirs() }
                    if (configWithChapters.exportStemsOnly) {
                        videoEngine.exportAudioStems(
                            tracks = tracks,
                            config = configWithChapters,
                            timelineDurationMsOverride = totalDurationMs,
                            outputFileFor = { index, trackName ->
                                createOutputFile(
                                    outputDir = outputDir,
                                    extension = "m4a",
                                    preferredOutputName = "${baseName}_stem${index + 1}_$trackName",
                                    configOverride = configWithChapters,
                                    stateOverride = currentState,
                                )
                            },
                            onProgress = { p -> sampleProgress(p); updateExport { it.copy(progress = p) } },
                            onComplete = { files ->
                                val primary = files.firstOrNull()
                                updateExport {
                                    it.copy(
                                        state = ExportState.COMPLETE,
                                        progress = 1f,
                                        lastExportedFilePath = primary?.absolutePath
                                    )
                                }
                                recordExportHistory(
                                    sourceState = currentState,
                                    status = ExportHistoryStatus.COMPLETE,
                                    startedAtMs = startedAtMs,
                                    outputFile = primary,
                                    config = configWithChapters,
                                    timelineDurationMs = totalDurationMs,
                                    diagnosticSummary = "Stem export wrote ${files.size} audio track file(s): " +
                                        files.joinToString { it.name },
                                    healthReport = healthReport
                                )
                                primary?.let {
                                    showToast(appContext.getString(R.string.export_complete_toast, it.name))
                                }
                            },
                            onError = { e ->
                                recordAudioExportFailure(e, currentState, configWithChapters, totalDurationMs, startedAtMs, healthReport)
                            },
                            onFallbackApplied = ::noteRuntimeExport,
                        )
                    } else {
                        val outputFile = createOutputFile(
                            outputDir = outputDir,
                            extension = "m4a",
                            preferredOutputName = baseName,
                            configOverride = configWithChapters,
                            stateOverride = currentState,
                        )
                        videoEngine.exportAudio(
                            tracks = tracks,
                            config = configWithChapters,
                            outputFile = outputFile,
                            timelineDurationMsOverride = totalDurationMs,
                            onProgress = { p -> sampleProgress(p); updateExport { it.copy(progress = p) } },
                            onComplete = {
                                val finalized = finalizeFilenameSize(outputFile)
                                reportSidecarOutcome(writeAiDisclosureSidecarIfRequested(finalized, configWithChapters, currentState))
                                updateExport {
                                    it.copy(
                                        state = ExportState.COMPLETE,
                                        progress = 1f,
                                        lastExportedFilePath = finalized.absolutePath
                                    )
                                }
                                recordExportHistory(
                                    sourceState = currentState,
                                    status = ExportHistoryStatus.COMPLETE,
                                    startedAtMs = startedAtMs,
                                    outputFile = finalized,
                                    config = configWithChapters,
                                    timelineDurationMs = totalDurationMs,
                                    diagnosticSummary = "Audio-only export completed.",
                                    healthReport = healthReport
                                )
                                showToast(appContext.getString(R.string.export_complete_toast, finalized.name))
                            },
                            onError = { e ->
                                recordAudioExportFailure(e, currentState, configWithChapters, totalDurationMs, startedAtMs, healthReport)
                            },
                            onFallbackApplied = ::noteRuntimeExport,
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    recordAudioExportFailure(e, currentState, configWithChapters, totalDurationMs, startedAtMs, healthReport)
                } finally {
                    nonVideoExportJob = null
                }
            }
            return
        }

        val startedAtMs = markExportStarted()

        scope.launch {
            val ext = if (currentState.exportConfig.transparentBackground) "webm" else "mp4"
            withContext(Dispatchers.IO) { outputDir.mkdirs() }
            val outputFile = outputFileOverride ?: createOutputFile(
                outputDir = outputDir,
                extension = ext,
                preferredOutputName = preferredOutputName ?: currentState.project.name,
                configOverride = configWithChapters,
                stateOverride = currentState,
            )

            fun handleVideoExportComplete() {
              val completedResumeId = activeResumeSession?.supersededHistoryId
              activeResumeSession = null
              // The Transformer completion callback lands on the Main thread.
              // Finalization here (subtitle burn-in especially, which re-encodes
              // the whole video) can run for seconds to minutes, so run all of it
              // on IO to avoid an ANR. StateFlow updates and showToast are
              // thread-safe; ordering is preserved because the COMPLETE state that
              // exposes the Share/Save buttons is set only after the sidecars and
              // burn-in finish inside this same coroutine.
              scope.launch(Dispatchers.IO) {
                // If the project carries scratchpad notes, drop them next to the
                // render as a `.txt` sidecar; failure is logged, never fatal.
                val notes = currentState.project.notes
                if (notes.isNotBlank()) {
                    try {
                        val sidecar = File(
                            outputFile.parentFile,
                            "${outputFile.nameWithoutExtension}.notes.txt"
                        )
                        writeUtf8TextAtomically(sidecar, notes)
                    } catch (e: Exception) {
                        com.novacut.editor.engine.AppLog.w("ExportDelegate", "Scratchpad sidecar write failed", e)
                    }
                }
                // Subtitle sidecar. Written next to the video with a matching
                // basename so the pair travels together through `saveToGallery`
                // (image-collection fallback path) and share intents. Sequential
                // with the state → COMPLETE transition: the write completes before
                // the UI gets Share/Save-to-Gallery buttons, so a user tapping
                // Share can't race a half-written .srt.
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
                        com.novacut.editor.engine.AppLog.w("ExportDelegate", "Subtitle sidecar write failed", e)
                    }
                }

                if (configWithChapters.burnSubtitles) {
                    val burnCaptions = tracks.flatMap { t -> t.clips }.flatMap { clip ->
                        clip.captions.map { c ->
                            c.copy(
                                startTimeMs = c.startTimeMs + clip.timelineStartMs,
                                endTimeMs = c.endTimeMs + clip.timelineStartMs
                            )
                        }
                    }
                    if (burnCaptions.isNotEmpty()) {
                        var assFile: java.io.File? = null
                        var burnedFile: java.io.File? = null
                        try {
                            val engine = ffmpegEngine?.takeIf { it.isAvailable() }
                                ?: error("Subtitle burn-in engine is unavailable")
                            assFile = java.io.File(
                                outputFile.parentFile,
                                "${outputFile.nameWithoutExtension}_burn.ass"
                            )
                            check(com.novacut.editor.engine.SubtitleExporter.export(
                                burnCaptions, com.novacut.editor.model.SubtitleFormat.ASS, assFile
                            )) { "Could not create the ASS burn-in document" }
                            burnedFile = java.io.File(
                                outputFile.parentFile,
                                "${outputFile.nameWithoutExtension}_burned.mp4"
                            )
                            check(engine.burnSubtitles(outputFile, assFile, burnedFile)) {
                                "libass subtitle rendering failed"
                            }
                            check(burnedFile.isFile && burnedFile.length() > 0L) {
                                "Subtitle renderer produced no output"
                            }
                            check(outputFile.delete() && burnedFile.renameTo(outputFile)) {
                                "Could not replace the uncaptioned export"
                            }
                        } catch (e: Exception) {
                            com.novacut.editor.engine.AppLog.e("ExportDelegate", "Requested subtitle burn-in failed", e)
                            assFile?.delete()
                            burnedFile?.delete()
                            outputFile.delete()
                            val message = exportFailureText(
                                VideoEngine.ExportFailureCause.SUBTITLE_BURN_IN_FAILED
                            )
                            updateExport {
                                it.copy(
                                    state = ExportState.ERROR,
                                    errorMessage = message,
                                    lastExportedFilePath = null
                                )
                            }
                            recordExportHistory(
                                sourceState = currentState,
                                status = ExportHistoryStatus.FAILED,
                                startedAtMs = startedAtMs,
                                outputFile = null,
                                config = configWithChapters,
                                timelineDurationMs = totalDurationMs,
                                errorMessage = message,
                                diagnosticSummary = "Requested subtitle burn-in failed.",
                                healthReport = healthReport
                            )
                            recordExportIncident(
                                sourceState = currentState,
                                failedPhase = "subtitle-burn",
                                error = e,
                                errorMessage = e.message ?: e::class.java.simpleName,
                                config = configWithChapters,
                                timelineDurationMs = totalDurationMs,
                                startedAtMs = startedAtMs,
                                healthReport = healthReport
                            )
                            return@launch
                        } finally {
                            assFile?.delete()
                        }
                    }
                }

                // Finalize the `{sizeMB}` filename token (if used) by
                // renaming the output to include the actual MB count.
                // No-op when the template didn't reference the token,
                // so existing templates are unaffected.
                val finalizedFile = finalizeFilenameSize(outputFile)
                reportSidecarOutcome(writeAiDisclosureSidecarIfRequested(finalizedFile, configWithChapters, currentState))
                updateExport {
                    it.copy(
                        state = ExportState.COMPLETE,
                        progress = 1f,
                        lastExportedFilePath = finalizedFile.absolutePath
                    )
                }
                recordExportHistory(
                    sourceState = currentState,
                    status = ExportHistoryStatus.COMPLETE,
                    startedAtMs = startedAtMs,
                    outputFile = finalizedFile,
                    config = configWithChapters,
                    timelineDurationMs = totalDurationMs,
                    diagnosticSummary = "Video export completed.",
                    healthReport = healthReport,
                    supersededHistoryId = completedResumeId,
                )
                showToast(appContext.getString(R.string.export_complete_toast, finalizedFile.name))
              }
            }

            fun handleVideoExportError(e: Exception) {
                val failedResumeId = activeResumeSession?.supersededHistoryId
                activeResumeSession = null
                outputFile.delete()
                val message = when (e) {
                    is ExportStorageException -> appContext.exportStorageFailureMessage(e.failure)
                    // A stage that refused to change render intent already knows
                    // which clip and stage failed — say so instead of collapsing
                    // it into the generic "export failed" copy.
                    is ExportStageException -> e.message
                    is RenderDegradationException -> renderDegradationFailureText(e)
                    // The engine recorded exactly why it stopped. Turning that into
                    // its own sentence plus a remediation line is the whole point of
                    // the typed cause; the generic string is the last resort, not the
                    // default.
                    else -> exportFailureText(videoEngine.exportFailureCause.value)
                }
                val technicalMessage = e.message ?: e::class.java.simpleName
                updateExport {
                    it.copy(
                        state = ExportState.ERROR,
                        errorMessage = message,
                        lastExportedFilePath = null
                    )
                }
                recordExportHistory(
                    sourceState = currentState,
                    status = ExportHistoryStatus.FAILED,
                    startedAtMs = startedAtMs,
                    outputFile = null,
                    config = configWithChapters,
                    timelineDurationMs = totalDurationMs,
                    errorMessage = message,
                    diagnosticSummary = when (e) {
                        is ExportVerificationException ->
                            "Output contract rejected the artifact: " +
                                (e.verification.reason ?: "invalid output") + "."
                        is ExportStageException ->
                            "Video export failed in the ${e.stage} stage" +
                                (e.subjectId?.let { " on clip $it" } ?: "") + "."
                        else -> "Video export failed in the encoder pipeline."
                    },
                    healthReport = healthReport,
                    supersededHistoryId = failedResumeId,
                )
                recordExportIncident(
                    sourceState = currentState,
                    failedPhase = (e as? ExportStageException)?.stage ?: "encoder",
                    subjectClipId = (e as? ExportStageException)?.subjectId,
                    error = e,
                    errorMessage = technicalMessage,
                    config = configWithChapters,
                    timelineDurationMs = totalDurationMs,
                    startedAtMs = startedAtMs,
                    healthReport = healthReport
                )
            }

            try {
                // v3.69 stream-copy fast-path. Only runs when the caller opted
                // in via `allowStreamCopy` AND the timeline is a single
                // unmodified clip with only head/tail cuts. Falls back to the
                // Transformer path below on any failure so we never leave the
                // user stuck if the MediaMuxer rejects the source.
                //
                // The foreground ExportService observes ONLY videoEngine's export
                // state, which the stream-copy path never touches. Starting it
                // before this fast-path would leave the service (and its ongoing
                // notification) pinned forever on every successful stream-copy.
                // So start the service only when we fall through to the Transformer.
                if (tryStreamCopy(
                        tracks = tracks,
                        config = configWithChapters,
                        textOverlays = textOverlays,
                        state = currentState,
                        outputFile = outputFile,
                        startedAtMs = startedAtMs,
                        totalDurationMs = totalDurationMs,
                        healthReport = healthReport,
                        resumeFromFile = resumeSourceFile,
                    )
                ) {
                    return@launch
                }
                val serviceIntent = Intent(appContext, ExportService::class.java).apply {
                    putExtra(ExportService.EXTRA_OUTPUT_PATH, outputFile.absolutePath)
                }
                appContext.startForegroundService(serviceIntent)
                setEncoderName(configWithChapters)
                val mixedPlan = if (resumeSourceFile == null) buildMixedRenderPlan(
                    tracks = tracks,
                    config = configWithChapters,
                    textOverlays = textOverlays,
                    state = currentState,
                    outputFile = outputFile
                ) else null
                if (mixedPlan != null && videoEngine.exportMixed(
                        plan = mixedPlan,
                        tracks = tracks,
                        config = configWithChapters,
                        outputFile = outputFile,
                        textOverlays = textOverlays,
                        imageOverlays = imageOverlays,
                        trackedObjects = trackedObjects,
                        onProgress = { progress ->
                            sampleProgress(progress)
                            updateExport { it.copy(progress = progress) }
                        },
                        onComplete = ::handleVideoExportComplete,
                        onError = ::handleVideoExportError,
                        onFallbackApplied = ::noteRuntimeExport,
                    )
                ) {
                    return@launch
                }
                val resumeDecision = resumeEligibility(currentState, outputExtension = ext)
                activeResumeSession = ActiveResumeSession(
                    outputFile = outputFile,
                    eligible = resumeDecision.eligible,
                    config = configWithChapters,
                    projectFingerprint = projectFingerprint(currentState),
                    configFingerprint = exportConfigFingerprint(currentState.exportConfig),
                    sourcePartialFile = resumeSourceFile,
                    supersededHistoryId = resumeCandidate?.id,
                )
                videoEngine.export(
                    tracks = tracks,
                    config = configWithChapters,
                    outputFile = outputFile,
                    timelineDurationMsOverride = totalDurationMs,
                    resumeFromFile = resumeSourceFile,
                    textOverlays = textOverlays,
                    imageOverlays = imageOverlays,
                    trackedObjects = trackedObjects,
                    globalTransitions = globalTransitions,
                    onProgress = { progress ->
                        sampleProgress(progress)
                        updateExport { it.copy(progress = progress) }
                    },
                    onComplete = ::handleVideoExportComplete,
                    onError = ::handleVideoExportError,
                    onFallbackApplied = ::noteRuntimeExport,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // The user actively cancelled — do not surface as ERROR.
                // VideoEngine's transformer listener handles the CANCELLED
                // state transition; we just clean up the partial file and
                // let cancellation propagate so the launched job finishes.
                val preserved = preservedResumeOutputPaths.remove(outputFile.absolutePath)
                activeResumeSession = null
                if (!preserved) {
                    runCatching { outputFile.delete() }
                    deleteOwnedResumeFile(resumeSourceFile)
                }
                throw e
            } catch (e: Exception) {
                val failedResumeId = activeResumeSession?.supersededHistoryId ?: resumeCandidate?.id
                activeResumeSession = null
                outputFile.delete()
                deleteOwnedResumeFile(resumeSourceFile)
                val message = when (e) {
                    is RenderDegradationException -> renderDegradationFailureText(e)
                    else -> exportFailureText(videoEngine.exportFailureCause.value)
                }
                val technicalMessage = e.message ?: e::class.java.simpleName
                updateExport {
                    it.copy(
                        state = ExportState.ERROR,
                        errorMessage = message,
                        lastExportedFilePath = null
                    )
                }
                recordExportHistory(
                    sourceState = currentState,
                    status = ExportHistoryStatus.FAILED,
                    startedAtMs = startedAtMs,
                    outputFile = null,
                    config = configWithChapters,
                    timelineDurationMs = totalDurationMs,
                    errorMessage = message,
                    diagnosticSummary = "Video export failed before the encoder could finish.",
                    healthReport = healthReport,
                    supersededHistoryId = failedResumeId,
                )
                recordExportIncident(
                    sourceState = currentState,
                    failedPhase = "setup",
                    error = e,
                    errorMessage = technicalMessage,
                    config = configWithChapters,
                    timelineDurationMs = totalDurationMs,
                    startedAtMs = startedAtMs,
                    healthReport = healthReport
                )
            }
        }
    }

    private fun aiDisclosureEntries(
        config: ExportConfig,
        state: EditorState
    ): List<AiUsageLedger.Entry> {
        if (!config.discloseAiUse) return emptyList()
        return AiUsageLedger.mergeOverlaps(state.aiUsageLedger)
    }

    private fun aiDisclosureText(
        config: ExportConfig,
        state: EditorState
    ): String? {
        val entries = aiDisclosureEntries(config, state)
        if (entries.isEmpty()) return null
        return AiUsageLedger.summaryLine(entries)
    }

    /**
     * Write the AI-use disclosure sidecar the user asked for.
     *
     * Returns false when the write failed. This used to swallow the exception and let
     * the export report success, in a file whose own KDoc cites EU AI Act Art. 50 as
     * the reason it exists -- the user would have shipped an undisclosed AI-assisted
     * export believing the disclosure was attached.
     */
    private fun writeAiDisclosureSidecarIfRequested(
        outputFile: File,
        config: ExportConfig,
        state: EditorState
    ): Boolean {
        if (!config.writeAiUseSidecar) return true
        val entries = aiDisclosureEntries(config, state)
        if (entries.isEmpty()) return true
        try {
            val sidecar = File(
                outputFile.parentFile,
                "${outputFile.nameWithoutExtension}.ai-use.json"
            )
            val declaration = AiUsageLedger.toDisclosureDeclaration(
                entries = entries,
                projectName = state.project.name,
                exportedFileName = outputFile.name,
                generatedAtEpochMs = System.currentTimeMillis()
            )
            writeUtf8TextAtomically(sidecar, declaration.toString(2))
            writeC2paManifestSidecar(outputFile, entries, state)
            return true
        } catch (e: Exception) {
            com.novacut.editor.engine.AppLog.w("ExportDelegate", "AI disclosure sidecar write failed", e)
            return false
        }
    }

    /**
     * Report a disclosure sidecar that could not be written. The video itself is fine,
     * so the export is not failed -- but the user must know the disclosure they asked
     * for is not next to the file before they publish it.
     */
    private fun reportSidecarOutcome(written: Boolean) {
        if (written) return
        showToast(text(R.string.export_ai_disclosure_sidecar_failed))
    }

    private fun writeC2paManifestSidecar(
        outputFile: File,
        entries: List<AiUsageLedger.Entry>,
        state: EditorState
    ) {
        val engine = c2paExportEngine ?: return
        val sidecar = File(
            outputFile.parentFile,
            "${outputFile.nameWithoutExtension}.c2pa-draft-manifest.json"
        )
        val generatedAt = System.currentTimeMillis()
        val manifest = engine.buildManifest(
            projectTitle = state.project.name,
            novaCutVersionName = BuildConfig.VERSION_NAME,
            signingMode = C2paExportEngine.SigningMode.ANDROID_KEYSTORE,
            ledger = entries,
            exporterCreationTimeMs = generatedAt
        )
        val availability = engine.signingAvailability(C2paExportEngine.SigningMode.ANDROID_KEYSTORE)
        writeUtf8TextAtomically(
            sidecar,
            engine.draftSidecarToJson(
                manifest = manifest,
                availability = availability,
                exportedFileName = outputFile.name
            ).toString(2)
        )
    }

    fun getShareIntent(): Intent? {
        val state = stateFlow.value
        val filePath = state.lastExportedFilePath ?: run {
            showToast(appContext.getString(R.string.export_no_media_share_toast))
            return null
        }
        val file = File(filePath)
        if (!file.exists()) {
            showToast(appContext.getString(R.string.export_file_unavailable_toast))
            return null
        }
        val uri = runCatching {
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        }.getOrElse { error ->
            AppLog.w("ExportDelegate", "Export share FileProvider handoff failed for ${RedactedLog.path(filePath)}", error)
            showToast(appContext.getString(com.novacut.editor.R.string.editor_share_location_failed))
            return null
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = exportMimeTypeFor(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            aiDisclosureText(state.exportConfig, state)?.let { disclosure ->
                putExtra(Intent.EXTRA_TEXT, "AI disclosure: $disclosure")
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun saveToGallery() {
        if (!saveToGalleryGate.tryEnter()) {
            showToast(text(R.string.export_save_in_progress_toast))
            return
        }
        val filePath = stateFlow.value.lastExportedFilePath ?: run {
            saveToGalleryGate.exit()
            showToast(appContext.getString(R.string.export_no_media_toast))
            return
        }

        scope.launch {
            try {
                val savedMessage = withContext(Dispatchers.IO) {
                    val file = File(filePath)
                    if (!file.isFile) null else saveExportedFile(file)
                }
                withContext(Dispatchers.Main) {
                    showToast(savedMessage ?: text(R.string.export_file_not_found_toast))
                }
            } catch (e: Exception) {
                AppLog.e("ExportDelegate", "Save exported media failed", e)
                withContext(Dispatchers.Main) { showToast(text(R.string.export_save_failed_toast)) }
            } finally {
                saveToGalleryGate.exit()
            }
        }
    }

    // --- Batch Export ---
    private fun currentBatchPlanContext(state: EditorState = stateFlow.value): BatchExportPlanContext {
        return BatchExportPlanContext(
            projectId = state.project.id,
            projectFingerprint = projectFingerprint(state),
        )
    }

    private fun persistBatchPlan(
        items: List<BatchExportItem> = stateFlow.value.batchExportQueue,
        context: BatchExportPlanContext = batchPlanContext ?: currentBatchPlanContext().also {
            batchPlanContext = it
        },
    ) {
        val revision = batchPlanWriteRevision.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            batchPlanWriteMutex.withLock {
                // A progress/status burst can enqueue several writes. Only the
                // newest snapshot should reach disk; each write is still atomic.
                if (revision != batchPlanWriteRevision.get()) return@withLock
                runCatching { batchExportPlanStore.saveFor(context, items) }
                    .onFailure { error ->
                        AppLog.w("ExportDelegate", "Batch export plan persistence failed", error)
                    }
            }
        }
    }

    private suspend fun persistBatchPlanNow(
        context: BatchExportPlanContext = batchPlanContext ?: currentBatchPlanContext().also {
            batchPlanContext = it
        },
    ) {
        val revision = batchPlanWriteRevision.incrementAndGet()
        val items = stateFlow.value.batchExportQueue
        withContext(Dispatchers.IO) {
            batchPlanWriteMutex.withLock {
                if (revision == batchPlanWriteRevision.get()) {
                    runCatching { batchExportPlanStore.saveFor(context, items) }
                        .onFailure { error ->
                            AppLog.w("ExportDelegate", "Batch export plan persistence failed", error)
                        }
                }
            }
        }
    }

    private fun updateBatchQueue(
        persist: Boolean = true,
        transform: (List<BatchExportItem>) -> List<BatchExportItem>,
    ) {
        stateFlow.update { state ->
            state.copyExport { export -> export.copy(batchQueue = transform(state.batchExportQueue)) }
        }
        if (persist) persistBatchPlan()
    }

    /** Restore unfinished work after the Room/autosave project is known. */
    fun restoreBatchExportQueue(context: BatchExportPlanContext) {
        batchPlanContext = context
        val restored = batchExportPlanStore.readFor(context)
        if (restored.isEmpty()) return
        val effectiveQueue = if (stateFlow.value.batchExportQueue.isEmpty()) {
            stateFlow.update { state ->
                state.copyExport { export -> export.copy(batchQueue = restored) }
            }
            restored
        } else {
            stateFlow.value.batchExportQueue
        }
        // Persist the normalized statuses so a second restart sees the same
        // interrupted/review-required explanation even before opening the panel.
        persistBatchPlan(effectiveQueue, context)
    }

    fun showBatchExport() {
        pauseIfPlaying()
        stateFlow.update {
            dismissedPanelState(it).copyPanel { panel ->
                panel.copy(panels = panel.panels.closeAll().open(PanelId.BATCH_EXPORT))
            }
        }
    }

    fun hideBatchExport() {
        stateFlow.update {
            it.copyPanel { panel -> panel.copy(panels = panel.panels.close(PanelId.BATCH_EXPORT)) }
        }
    }

    fun addBatchExportItem(config: ExportConfig, name: String) {
        if (stateFlow.value.batchExportQueue.size >= BatchExportPlanStore.MAX_ITEMS) {
            showToast(
                appContext.resources.getQuantityString(
                    R.plurals.batch_export_queue_limit,
                    BatchExportPlanStore.MAX_ITEMS,
                    BatchExportPlanStore.MAX_ITEMS,
                )
            )
            return
        }
        val context = batchPlanContext ?: currentBatchPlanContext().also { batchPlanContext = it }
        val item = BatchExportItem(
            config = config,
            outputName = name,
            projectId = context.projectId,
            projectFingerprint = context.projectFingerprint,
            configFingerprint = exportConfigFingerprint(config),
        )
        updateBatchQueue { queue -> queue + item }
    }

    /** Queue one source-file cut while retaining the current export settings. */
    fun addBatchExportSourceCut(config: ExportConfig, sourceRange: BatchExportSourceRange) {
        if (stateFlow.value.batchExportQueue.size >= BatchExportPlanStore.MAX_ITEMS) {
            showToast(
                appContext.resources.getQuantityString(
                    R.plurals.batch_export_queue_limit,
                    BatchExportPlanStore.MAX_ITEMS,
                    BatchExportPlanStore.MAX_ITEMS,
                )
            )
            return
        }
        val context = batchPlanContext ?: currentBatchPlanContext().also { batchPlanContext = it }
        // The source range is the cut boundary for this item. A project-level
        // timeline range would otherwise be applied a second time to the
        // isolated one-clip export state.
        val itemConfig = config.copy(timelineRange = null)
        val item = BatchExportItem(
            config = itemConfig,
            outputName = sourceRange.displayName,
            projectId = context.projectId,
            projectFingerprint = context.projectFingerprint,
            configFingerprint = exportConfigFingerprint(itemConfig),
            sourceRange = sourceRange,
        )
        updateBatchQueue { queue -> queue + item }
    }

    private fun batchExportState(
        baseState: EditorState,
        item: BatchExportItem,
    ): EditorState {
        val sourceRange = item.sourceRange ?: return baseState.copyExport { export ->
            export.copy(
                config = item.config,
                state = ExportState.IDLE,
                progress = 0f,
                errorMessage = null,
                pendingConfirmation = null,
            )
        }
        val sourceClip = sourceRange.toClip("batch-${item.id}-${sourceRange.clipId}")
        val sourceTrack = Track(
            id = "batch-${item.id}-track",
            type = sourceRange.trackType,
            index = 0,
            clips = listOf(sourceClip),
        )
        return baseState.copy(
            tracks = listOf(sourceTrack),
            selectedClipId = sourceClip.id,
            selectedTrackId = sourceTrack.id,
            selectedClipIds = setOf(sourceClip.id),
            totalDurationMs = sourceClip.durationMs,
            textOverlays = emptyList(),
            imageOverlays = emptyList(),
            timelineMarkers = emptyList(),
            globalTransitions = emptyList(),
            trackedObjects = emptyList(),
        ).copyExport { export ->
            export.copy(
                config = item.config.copy(timelineRange = null),
                state = ExportState.IDLE,
                progress = 0f,
                errorMessage = null,
                pendingConfirmation = null,
            )
        }
    }

    private fun batchVideoOutputExtension(config: ExportConfig): String? = when {
        config.exportAudioOnly || config.exportStemsOnly ||
            config.exportAsGif || config.exportAsContactSheet -> null
        config.transparentBackground -> "webm"
        else -> "mp4"
    }

    private data class BatchOutputPlan(
        val outputFile: File,
        val resumePartialFile: File? = null,
        val alreadyComplete: Boolean = false,
    )

    private fun planBatchVideoOutput(
        outputDir: File,
        item: BatchExportItem,
        itemState: EditorState,
    ): BatchOutputPlan? {
        val extension = batchVideoOutputExtension(item.config) ?: return null
        val persistedPath = item.resumePartialPath ?: item.outputPath
        val persistedFile = persistedPath?.let(::File)
        if (persistedFile != null && isOwnedResumeFile(persistedFile)) {
            if (persistedFile.isFile && persistedFile.length() > 0L) {
                val complete = ExportOutputVerifier.verify(
                    outputFile = persistedFile,
                    expectVideo = true,
                    expectAudio = false,
                    expectedVideoMimeType = item.config.codec.mimeType,
                    expectedDurationMs = itemState.tracks.flatMap { it.clips }
                        .maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
                    expectedContainer = expectedContainerForExtension(persistedFile.extension),
                )
                if (complete.valid) {
                    return BatchOutputPlan(outputFile = persistedFile, alreadyComplete = true)
                }
            }
            if (resumeEligibility(
                    state = itemState,
                    config = item.config,
                    outputExtension = persistedFile.extension.ifBlank { extension },
                ).eligible && persistedFile.isFile && persistedFile.length() > 0L
            ) {
                return BatchOutputPlan(
                    outputFile = persistedFile,
                    resumePartialFile = persistedFile,
                )
            }
            runCatching { persistedFile.delete() }
        }
        return BatchOutputPlan(
            outputFile = createOutputFile(
                outputDir = outputDir,
                extension = extension,
                preferredOutputName = item.outputName,
                configOverride = item.config,
                stateOverride = itemState,
            )
        )
    }

    fun moveBatchExportItem(id: String, targetIndex: Int) {
        updateBatchQueue { queue -> reorderBatchExportItems(queue, id, targetIndex) }
    }

    fun removeBatchExportItem(id: String) {
        updateBatchQueue { queue -> queue.filter { it.id != id } }
    }

    /** Retry is always an explicit user action and refreshes both fingerprints. */
    fun retryBatchExportItem(id: String) {
        val context = currentBatchPlanContext().also { batchPlanContext = it }
        updateBatchQueue { queue ->
            queue.map { item ->
                if (item.id == id && item.status in setOf(
                        BatchExportStatus.FAILED,
                        BatchExportStatus.CANCELLED,
                        BatchExportStatus.PAUSED,
                        BatchExportStatus.INTERRUPTED,
                        BatchExportStatus.REVIEW_REQUIRED,
                    )
                ) {
                    if (item.status == BatchExportStatus.REVIEW_REQUIRED) {
                        deleteOwnedResumeFile(item.resumePartialPath?.let(::File))
                        deleteOwnedResumeFile(item.outputPath?.let(::File))
                    }
                    item.copy(
                        projectId = context.projectId,
                        projectFingerprint = context.projectFingerprint,
                        configFingerprint = exportConfigFingerprint(item.config),
                        status = BatchExportStatus.QUEUED,
                        progress = 0f,
                        errorMessage = null,
                        outputPath = if (item.status == BatchExportStatus.REVIEW_REQUIRED) {
                            null
                        } else {
                            item.outputPath
                        },
                        resumePartialPath = if (item.status == BatchExportStatus.REVIEW_REQUIRED) {
                            null
                        } else {
                            item.resumePartialPath
                        },
                    )
                } else {
                    item
                }
            }
        }
    }

    fun pauseBatchExport() {
        if (batchExportJob?.isActive != true) return
        batchPauseRequested = true
        cancelExport()
    }

    fun cancelBatchExport() {
        if (batchExportJob?.isActive != true) return
        batchCancelRequested = true
        cancelExport()
    }

    fun startBatchExport() {
        if (batchExportJob?.isActive == true) {
            showToast(text(R.string.export_already_in_progress_toast))
            return
        }
        batchPauseRequested = false
        batchCancelRequested = false
        lastCancelledBatchResumePath = null
        val currentContext = currentBatchPlanContext().also { batchPlanContext = it }
        val currentQueue = stateFlow.value.batchExportQueue
        val staleIds = currentQueue
            .filter {
                it.status == BatchExportStatus.QUEUED ||
                    it.status == BatchExportStatus.PAUSED ||
                    it.status == BatchExportStatus.INTERRUPTED
            }
            .filter { item ->
                item.projectId != currentContext.projectId ||
                    item.projectFingerprint != currentContext.projectFingerprint ||
                    item.configFingerprint != exportConfigFingerprint(item.config)
            }
            .map { it.id }
        if (staleIds.isNotEmpty()) {
            updateBatchQueue { queue ->
                queue.map { item ->
                    if (item.id in staleIds) {
                        item.copy(
                            status = BatchExportStatus.REVIEW_REQUIRED,
                            progress = 0f,
                            errorMessage = "The project or export settings changed after this job was queued.",
                        )
                    } else {
                        item
                    }
                }
            }
            showToast(text(R.string.batch_export_review_required_toast))
            return
        }
        // Snapshot the queue and per-item configs up front so UI-side config
        // changes that happen while exports are running can't corrupt the batch.
        val queue = stateFlow.value.batchExportQueue
            .filter {
                it.status == BatchExportStatus.QUEUED ||
                    it.status == BatchExportStatus.PAUSED ||
                    it.status == BatchExportStatus.INTERRUPTED
            }
            .toList()
        if (queue.isEmpty()) {
            showToast(text(R.string.batch_export_no_queued_items_toast))
            return
        }
        hideBatchExport()
        batchExportJob = scope.launch {
            val outputDir = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: appContext.filesDir,
                "ClearCut"
            ).apply { mkdirs() }
            val batchState = stateFlow.value
            val itemStates = queue.associateWith { item -> batchExportState(batchState, item) }
            val storageCheck = ExportStoragePolicy.checkBatch(
                requests = queue.map { item ->
                    val itemState = requireNotNull(itemStates[item])
                    val durationMs = itemState.tracks.flatMap { it.clips }
                        .maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
                    ExportStoragePolicy.request(
                        durationMs,
                        item.config,
                        itemState.tracks,
                        sourceSizeBytes = { clip -> querySourceSize(appContext, clip.sourceUri).takeIf { it > 0L } },
                    )
                },
                outputDirectory = outputDir,
                cacheDirectory = appContext.cacheDir,
            )
            if (!storageCheck.canProceed) {
                val message = appContext.exportStorageFailureMessage(requireNotNull(storageCheck.failure))
                updateExport { export ->
                    export.copy(
                        state = ExportState.ERROR,
                        errorMessage = message,
                    )
                }
                showToast(message)
                return@launch
            }
            val originalConfig = stateFlow.value.exportConfig
            try {
                for ((index, item) in queue.withIndex()) {
                    if (batchCancelRequested || batchPauseRequested) break
                    activeBatchItemId = item.id
                    lastCancelledBatchResumePath = null
                    val itemState = requireNotNull(itemStates[item])
                    val outputPlan = planBatchVideoOutput(
                        outputDir = outputDir,
                        item = item,
                        itemState = itemState,
                    )
                    if (outputPlan?.alreadyComplete == true) {
                        updateBatchQueue { items ->
                            items.map {
                                if (it.id == item.id) {
                                    it.copy(
                                        status = BatchExportStatus.COMPLETED,
                                        progress = 1f,
                                        errorMessage = null,
                                        outputPath = outputPlan.outputFile.absolutePath,
                                        resumePartialPath = null,
                                    )
                                } else {
                                    it
                                }
                            }
                        }
                        activeBatchItemId = null
                        continue
                    }
                    updateBatchQueue { items ->
                        items.map {
                            if (it.id == item.id) {
                                it.copy(
                                    status = BatchExportStatus.IN_PROGRESS,
                                    progress = 0f,
                                    errorMessage = null,
                                    outputPath = outputPlan?.outputFile?.absolutePath ?: it.outputPath,
                                    resumePartialPath = outputPlan?.resumePartialFile?.absolutePath,
                                )
                            } else {
                                it
                            }
                        }
                    }
                    persistBatchPlanNow()
                    showToast(
                        appContext.getString(
                            R.string.export_batch_progress_toast,
                            (index + 1).toString(),
                            queue.size.toString(),
                            item.outputName
                        )
                    )
                    videoEngine.resetExportState()
                    // Reset exportState to IDLE in the delegate state as well. Without this,
                    // the wait loop below immediately sees the previous item's COMPLETE/ERROR
                    // state and advances before the new export has started, causing two items
                    // to export concurrently and the batch queue to report incorrect statuses.
                    updateExport {
                        it.copy(
                            config = itemState.exportConfig,
                            state = ExportState.IDLE,
                            progress = 0f
                        )
                    }
                    startExport(
                        outputDir = outputDir,
                        preferredOutputName = item.outputName,
                        currentStateOverride = itemState,
                        outputFileOverride = outputPlan?.outputFile,
                        batchResumePartialPath = outputPlan?.resumePartialFile?.absolutePath,
                    )
                    val progressJob = scope.launch {
                        var lastPersistedProgress = -1f
                        stateFlow.map { it.exportProgress }
                            .distinctUntilChanged()
                            .collect { progress ->
                                updateBatchQueue(persist = false) { items ->
                                    items.map {
                                        if (it.id == item.id) it.copy(progress = progress) else it
                                    }
                                }
                                if (
                                    lastPersistedProgress < 0f ||
                                        progress >= 1f ||
                                        progress - lastPersistedProgress >= 0.05f
                                ) {
                                    lastPersistedProgress = progress
                                    persistBatchPlan()
                                }
                            }
                        }
                    val outcome = try {
                        stateFlow
                            .map { it.exportState to (it.export.pendingConfirmation != null) }
                            .distinctUntilChanged()
                            .first { (state, pendingConfirmation) ->
                                pendingConfirmation || (state != ExportState.IDLE && state != ExportState.EXPORTING)
                            }
                    } finally {
                        progressJob.cancel()
                        // Wait for the collector to fully stop before starting the next item.
                        // cancel() is non-blocking; without join() the old collector can still
                        // be running stateFlow.update calls when the next iteration launches,
                        // causing races on the batch queue state.
                        progressJob.join()
                    }
                    if (outcome.second) {
                        updateBatchQueue { items ->
                            items.map {
                                if (it.id == item.id) {
                                    it.copy(
                                        status = BatchExportStatus.REVIEW_REQUIRED,
                                        progress = 0f,
                                        errorMessage = "This batch item needs export-warning confirmation before it can run.",
                                    )
                                } else {
                                    it
                                }
                            }
                        }
                        activeBatchItemId = null
                        continue
                    }
                    val result = outcome.first
                    val newStatus = when (result) {
                        ExportState.COMPLETE -> BatchExportStatus.COMPLETED
                        ExportState.CANCELLED -> if (batchPauseRequested) {
                            BatchExportStatus.PAUSED
                        } else {
                            BatchExportStatus.CANCELLED
                        }
                        else -> BatchExportStatus.FAILED
                    }
                    // Normalize the per-item progress to 100% on success and 0% on failure /
                    // cancel. Without this, the queue UI would show "85% FAILED" on a job that
                    // errored partway through, and "99% COMPLETED" on a job whose progress
                    // collector got cancelled before observing the final 1.0 tick.
                    val finalProgress = if (result == ExportState.COMPLETE) 1f else 0f
                    val resumePartialPath = lastCancelledBatchResumePath
                    updateBatchQueue { items ->
                        items.map {
                            if (it.id == item.id) {
                                it.copy(
                                    status = newStatus,
                                    progress = finalProgress,
                                    errorMessage = when (newStatus) {
                                        BatchExportStatus.FAILED -> stateFlow.value.exportErrorMessage
                                        BatchExportStatus.PAUSED -> if (resumePartialPath != null) {
                                            "Paused because the encoder cannot pause mid-item. Resume to continue from the saved partial output."
                                        } else {
                                            "Paused because the encoder cannot pause mid-item. Resume will restart this item."
                                        }
                                        BatchExportStatus.CANCELLED -> if (resumePartialPath != null) {
                                            "Cancelled by the user. Retry to resume this item from its saved partial output."
                                        } else {
                                            "Cancelled by the user. Retry to run this item again."
                                        }
                                        else -> null
                                    },
                                    outputPath = if (newStatus == BatchExportStatus.COMPLETED) {
                                        stateFlow.value.lastExportedFilePath ?: it.outputPath
                                    } else {
                                        it.outputPath
                                    },
                                    resumePartialPath = when (newStatus) {
                                        BatchExportStatus.PAUSED,
                                        BatchExportStatus.CANCELLED -> resumePartialPath
                                        else -> null
                                    },
                                )
                            } else {
                                it
                            }
                        }
                    }
                    persistBatchPlanNow()
                    // Stop the batch when the user explicitly cancels — continuing onto the
                    // next item would feel like the cancel button was ignored. Failures don't
                    // break the batch (each item is independent and the user may want
                    // partial-success behaviour for a long queue).
                    if (result == ExportState.CANCELLED) break
                    activeBatchItemId = null
                }
            } finally {
                activeBatchItemId = null
                updateExport { it.copy(config = originalConfig) }
                batchExportJob = null
            }
            val finalQueue = stateFlow.value.batchExportQueue
            val completedCount = finalQueue.count { it.status == BatchExportStatus.COMPLETED }
            val failedCount = finalQueue.count { it.status == BatchExportStatus.FAILED }
            val pausedCount = finalQueue.count { it.status == BatchExportStatus.PAUSED }
            val cancelledCount = finalQueue.count { it.status == BatchExportStatus.CANCELLED }
            val summary = when {
                pausedCount > 0 -> "Batch paused ($completedCount items completed)"
                cancelledCount > 0 && completedCount == 0 -> "Batch cancelled"
                failedCount == 0 -> "Batch export complete ($completedCount items)"
                completedCount == 0 -> "Batch export failed ($failedCount items)"
                else -> "Batch export finished ($completedCount succeeded, $failedCount failed)"
            }
            showToast(summary)
        }
    }

    private fun recordAudioExportFailure(
        e: Exception,
        currentState: EditorState,
        config: ExportConfig,
        totalDurationMs: Long,
        startedAtMs: Long,
        healthReport: MediaHealthReport?,
    ) {
        val message = when (e) {
            is ExportStorageException -> appContext.exportStorageFailureMessage(e.failure)
            else -> exportFailureText(
                videoEngine.exportFailureCause.value ?: VideoEngine.ExportFailureCause.AUDIO_ENCODE_FAILED
            )
        }
        val technicalMessage = e.message ?: e::class.java.simpleName
        com.novacut.editor.engine.AppLog.w("ExportDelegate", "Audio export failed", e)
        updateExport {
            it.copy(
                state = ExportState.ERROR,
                errorMessage = message,
                lastExportedFilePath = null,
            )
        }
        recordExportHistory(
            sourceState = currentState,
            status = ExportHistoryStatus.FAILED,
            startedAtMs = startedAtMs,
            outputFile = null,
            config = config,
            timelineDurationMs = totalDurationMs,
            errorMessage = technicalMessage,
            diagnosticSummary = if (e is ExportVerificationException) {
                "Output contract rejected the audio artifact: " +
                    (e.verification.reason ?: "invalid output") + "."
            } else {
                "Audio export failed in the encoder pipeline."
            },
            healthReport = healthReport,
        )
        recordExportIncident(
            sourceState = currentState,
            failedPhase = "audio-encoder",
            error = e,
            errorMessage = technicalMessage,
            config = config,
            timelineDurationMs = totalDurationMs,
            startedAtMs = startedAtMs,
            healthReport = healthReport,
        )
    }

    private fun createOutputFile(
        outputDir: File,
        extension: String,
        preferredOutputName: String?,
        configOverride: ExportConfig? = null,
        stateOverride: EditorState? = null,
    ): File {
        val trimmedOutputName = preferredOutputName?.trim().orEmpty()
        val baseName = trimmedOutputName
            .substringBeforeLast('.', missingDelimiterValue = trimmedOutputName)
            .takeIf { it.isNotBlank() }
            ?: "ClearCut"
        val namingConfig = configOverride ?: stateFlow.value.exportConfig
        val template = namingConfig.filenameTemplate.ifBlank { "{name}" }
        val templated = applyFilenameTemplate(
            template = template,
            baseName = baseName,
            config = namingConfig,
            templateState = stateOverride,
        )
        // Reserve space for an auto-increment suffix like ` (999)` so repeated
        // collisions don't force the base to shrink with every retry (which
        // would produce a different filename on each iteration and could even
        // miss a previously-created number by hopping across lengths).
        val suffixReserve = 6
        val baseBudget = 64 - suffixReserve
        val sanitizedBase = sanitizeFileName(templated, fallback = "ClearCut", maxLength = baseBudget)
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

    private suspend fun saveExportedFile(file: File): String {
        val usesImageCollection = exportUsesImageCollection(file.name)
        val usesAudioCollection = exportUsesAudioCollection(file.name)
        val relativeDirectory = when {
            usesImageCollection -> Environment.DIRECTORY_PICTURES
            usesAudioCollection -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_MOVIES
        }
        val mimeType = exportMimeTypeFor(file.name)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveExportedFileToMediaStore(appContext, file)
        } else {
            val externalRoot = appContext.getExternalFilesDir(relativeDirectory)
                ?: File(appContext.filesDir, relativeDirectory.lowercase())
            val destinationDir = File(externalRoot, "ClearCut").apply { mkdirs() }
            val destinationFile = createOutputFile(
                destinationDir,
                file.extension.ifBlank { if (usesImageCollection) "png" else "mp4" },
                file.name
            )
            writeFileAtomically(destinationFile, requireNonEmpty = true) { tempFile ->
                file.inputStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
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
        stateFlow.update {
            val dismissed = dismissedPanelState(it)
            dismissed.copy(
                panel = dismissed.panel.copy(
                    panels = dismissed.panels.closeAll().open(PanelId.RENDER_PREVIEW)
                ),
                export = dismissed.export.copy(
                    renderSegments = segments,
                    renderSummary = summary
                )
            )
        }
    }

    fun hideRenderPreview() {
        stateFlow.update {
            it.copyPanel { panel -> panel.copy(panels = panel.panels.close(PanelId.RENDER_PREVIEW)) }
        }
    }

    fun renderQuickPreview() {
        val savedConfig = stateFlow.value.exportConfig
        val previewConfig = savedConfig.copy(
            resolution = com.novacut.editor.model.Resolution.SD_480P,
            quality = com.novacut.editor.model.ExportQuality.LOW
        )
        updateExport {
            it.copy(
                config = previewConfig,
                savedConfig = savedConfig
            )
        }
        hideRenderPreview()
        showExportSheet()
        showToast(appContext.getString(R.string.export_rendering_preview_toast))
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

}

/**
 * Publishes an export through the matching MediaStore collection. Kept as a
 * small internal boundary so the device contract can query the exact row that
 * Gallery / Photos will see after the pending transaction is committed.
 */
internal suspend fun saveExportedFileToMediaStore(context: Context, file: File): String {
    check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "MediaStore pending exports require Android 10 or newer"
    }
    val usesImageCollection = exportUsesImageCollection(file.name)
    val usesAudioCollection = exportUsesAudioCollection(file.name)
    val relativeDirectory = when {
        usesImageCollection -> Environment.DIRECTORY_PICTURES
        usesAudioCollection -> Environment.DIRECTORY_MUSIC
        else -> Environment.DIRECTORY_MOVIES
    }
    val mimeType = exportMimeTypeFor(file.name)
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDirectory/ClearCut")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val collection = when {
        usesImageCollection -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        usesAudioCollection -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    val contentUri = resolver.insert(collection, values)
        ?: throw IllegalStateException("Failed to create media destination")

    return try {
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
                kotlinx.coroutines.delay(delayMs)
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
}

/** The two lines a terminal export failure must produce: the cause, then the fix. */
internal data class ExportFailureCopy(
    @androidx.annotation.StringRes val messageRes: Int,
    @androidx.annotation.StringRes val remediationRes: Int,
)

/**
 * Every terminal export failure maps to its own pair of strings. Seven distinct
 * failures used to collapse into one "export failed" sentence with no remediation,
 * which left the user with nothing to act on and the triager with nothing to read.
 */
internal fun exportFailureCopyFor(cause: VideoEngine.ExportFailureCause?): ExportFailureCopy =
    when (cause) {
        VideoEngine.ExportFailureCause.SETUP_FAILED -> ExportFailureCopy(
            R.string.export_failure_setup, R.string.export_failure_setup_fix
        )
        VideoEngine.ExportFailureCause.ENCODER_FAILED -> ExportFailureCopy(
            R.string.export_failure_encoder, R.string.export_failure_encoder_fix
        )
        VideoEngine.ExportFailureCause.EMPTY_OUTPUT -> ExportFailureCopy(
            R.string.export_failure_empty_output, R.string.export_failure_empty_output_fix
        )
        VideoEngine.ExportFailureCause.VERIFICATION_FAILED -> ExportFailureCopy(
            R.string.export_failure_verification, R.string.export_failure_verification_fix
        )
        VideoEngine.ExportFailureCause.STALLED -> ExportFailureCopy(
            R.string.export_failure_stalled, R.string.export_failure_stalled_fix
        )
        VideoEngine.ExportFailureCause.SERVICE_TIMEOUT -> ExportFailureCopy(
            R.string.export_failure_service_timeout, R.string.export_failure_service_timeout_fix
        )
        VideoEngine.ExportFailureCause.STORAGE -> ExportFailureCopy(
            R.string.export_failure_storage, R.string.export_failure_storage_fix
        )
        VideoEngine.ExportFailureCause.AUDIO_ENCODE_FAILED -> ExportFailureCopy(
            R.string.export_failure_audio_encode, R.string.export_failure_audio_encode_fix
        )
        VideoEngine.ExportFailureCause.MIXED_RENDER_FAILED -> ExportFailureCopy(
            R.string.export_failure_mixed_render, R.string.export_failure_mixed_render_fix
        )
        VideoEngine.ExportFailureCause.SUBTITLE_BURN_IN_FAILED -> ExportFailureCopy(
            R.string.export_failure_subtitle_burn, R.string.export_failure_subtitle_burn_fix
        )
        VideoEngine.ExportFailureCause.STAGE_REFUSED -> ExportFailureCopy(
            R.string.export_failure_stage_refused, R.string.export_failure_stage_refused_fix
        )
        VideoEngine.ExportFailureCause.GPU_EFFECT_DEGRADED -> ExportFailureCopy(
            R.string.export_failure_gpu_degraded, R.string.export_failure_gpu_degraded_fix
        )
        VideoEngine.ExportFailureCause.UNKNOWN, null -> ExportFailureCopy(
            R.string.export_video_failed_message, R.string.export_failure_unknown_fix
        )
    }
