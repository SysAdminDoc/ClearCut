package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import com.novacut.editor.engine.EffectBuilder.addColorGradingEffects
import com.novacut.editor.engine.EffectBuilder.addOpacityAndTransformEffects
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VideoEngine"
private const val DEFAULT_STILL_IMAGE_DURATION_MS = 3_000L

@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class VideoEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val segmentationEngine: SegmentationEngine,
    private val streamCopyEngine: StreamCopyExportEngine,
    private val ffmpegEngine: FFmpegEngine,
    private val fontRegistry: FontRegistry,
    memoryTrimRegistry: MemoryTrimRegistry,
) {
    private data class MediaCharacteristics(
        val isStillImage: Boolean,
        val hasVisual: Boolean,
        val hasAudio: Boolean
    )

    private data class PreviewSeekTarget(
        val mediaItemIndex: Int,
        val mediaPositionMs: Long
    )

    private data class PreviewSegment(
        val clip: Clip?,
        val timelineStartMs: Long,
        val durationMs: Long,
        val mediaUri: Uri
    ) {
        val timelineEndMs: Long get() = timelineStartMs + durationMs
    }

    private data class VisualTrackSequence(
        val sequence: EditedMediaItemSequence,
        val hasEmbeddedAudio: Boolean,
        val compositorLayer: ClearCutCompositorLayer
    )

    private data class LottieBackendPlan(
        val overlay: LottieOverlaySpec,
        val overlayStartUs: Long,
        val overlayDurationUs: Long,
        val decision: LottieOverlayBackendDecision
    )

    private data class TransformerExportPlan(
        val composition: Composition,
        val mimeType: String
    )

    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var transitionListener: Player.Listener? = null

    // Clips for per-clip effect switching during playback
    private var videoClips: List<Clip> = emptyList()
    private var previewSegments: List<PreviewSegment> = emptyList()
    private var previewTrackedObjects: List<TrackedObject> = emptyList()
    @Volatile private var previewGapUri: Uri? = null
    // Memory-bounded bitmap cache — uses 1/8 of available heap
    // Don't recycle evicted bitmaps — they may still be referenced by Compose Image nodes
    private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    ) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            // Don't recycle — may still be referenced by Compose
            // Bitmap will be GC'd when no longer referenced
        }
    }

    init {
        memoryTrimRegistry.register(
            MemoryTrimAction.CLEAR_THUMBNAILS,
            "video.thumbnailCache",
        ) {
            clearThumbnailCache()
        }
    }

    // Clip durations for multi-clip seek/playhead calculations
    private var clipDurationsMs: List<Long> = emptyList()

    // Active Transformer for export cancellation
    @Volatile private var activeTransformer: Transformer? = null
    @Volatile private var activeExportOutputFile: File? = null

    private val mediaCharacteristicsCache = ConcurrentHashMap<String, MediaCharacteristics>()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress

    private val _exportState = MutableStateFlow(ExportState.IDLE)
    val exportState: StateFlow<ExportState> = _exportState

    private val _exportErrorMessage = MutableStateFlow<String?>(null)
    val exportErrorMessage: StateFlow<String?> = _exportErrorMessage

    /**
     * Get or create ExoPlayer. Must be called from main thread.
     * ExoPlayer requires a Looper for creation and all API calls.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun getPlayer(): ExoPlayer {
        if (player == null) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs */ 5_000,
                    /* maxBufferMs */ 50_000,
                    /* bufferForPlaybackMs */ 1_500,
                    /* bufferForPlaybackAfterRebufferMs */ 3_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            val renderersFactory = DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
            val previewAudioAttributes = ClearCutAudioFocusPolicy.buildPreviewAttributes()
            player = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setRenderersFactory(renderersFactory)
                .build()
                .apply {
                    setAudioAttributes(previewAudioAttributes, true)
                    setHandleAudioBecomingNoisy(true)
                }
        }
        return requireNotNull(player) { "ExoPlayer failed to initialize" }
    }

    /**
     * Enable/disable scrubbing mode for optimized frequent seeking (e.g., timeline dragging).
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun setScrubbingMode(enabled: Boolean) {
        player?.setScrubbingModeEnabled(enabled)
    }

    fun setPlayerListener(listener: Player.Listener) {
        playerListener?.let { player?.removeListener(it) }
        playerListener = listener
        player?.addListener(listener)
    }

    fun removePlayerListener() {
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
    }

    fun prepareClip(uri: Uri) {
        val p = getPlayer()
        val mediaItem = if (isImageUri(uri)) {
            MediaItem.Builder()
                .setUri(uri)
                .setImageDurationMs(DEFAULT_STILL_IMAGE_DURATION_MS)
                .build()
        } else {
            MediaItem.fromUri(uri)
        }
        p.setMediaItem(mediaItem)
        p.prepare()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun prepareTimeline(tracks: List<Track>, missingClipIds: Set<String> = emptySet()) {
        val p = getPlayer()
        videoClips = collectPreviewClips(tracks)
        val timelineEndMs = tracks.maxOfOrNull { track ->
            track.clips.maxOfOrNull { clip -> clip.timelineEndMs } ?: 0L
        } ?: 0L
        previewSegments = buildPreviewSegments(videoClips, timelineEndMs, missingClipIds)
        if (previewSegments.isEmpty()) {
            p.clearMediaItems()
            clipDurationsMs = emptyList()
            return
        }
        clipDurationsMs = previewSegments.map { it.durationMs }
        val mediaItems = previewSegments.map(::buildMediaItemForPreviewSegment)
        p.setMediaItems(mediaItems)
        p.prepare()

        // Install per-clip effect switching listener
        transitionListener?.let { p.removeListener(it) }
        transitionListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyEffectsForCurrentClip()
            }
        }
        transitionListener?.let(p::addListener)

        // Apply effects for the initial clip
        applyEffectsForCurrentClip()
    }

    fun getPreviewClipAt(index: Int): Clip? = previewSegments.getOrNull(index)?.clip

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        val target = resolvePreviewSeekTarget(positionMs)
        if (target != null) {
            p.seekTo(target.mediaItemIndex, target.mediaPositionMs)
        } else {
            p.seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    fun getAbsolutePositionMs(): Long {
        val p = player ?: return 0L
        val segment = previewSegments.getOrNull(p.currentMediaItemIndex) ?: return p.currentPosition
        return segment.clip?.let { clip ->
            previewTimelinePositionForMediaPosition(clip, p.currentPosition)
        } ?: (segment.timelineStartMs + p.currentPosition)
            .coerceIn(segment.timelineStartMs, segment.timelineEndMs)
    }

    fun play() { player?.play() }
    fun pause() { player?.pause() }
    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun getVideoDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    fun getMediaDuration(uri: Uri): Long {
        return if (isImageUri(uri)) DEFAULT_STILL_IMAGE_DURATION_MS else getVideoDuration(uri)
    }

    fun isStillImage(uri: Uri): Boolean = getMediaCharacteristics(uri).isStillImage

    fun hasVisualTrack(uri: Uri): Boolean = getMediaCharacteristics(uri).hasVisual

    fun hasAudioTrack(uri: Uri): Boolean = getMediaCharacteristics(uri).hasAudio

    fun isMotionVideo(uri: Uri): Boolean {
        val media = getMediaCharacteristics(uri)
        return media.hasVisual && !media.isStillImage
    }

    fun getVideoResolution(uri: Uri): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            w to h
        } catch (e: Exception) {
            0 to 0
        } finally {
            retriever.release()
        }
    }

    fun getVideoFrameRate(uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            // Try CAPTURE_FRAMERATE first (camera recordings), fall back to parsing bitrate
            val rate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            rate?.toFloatOrNull()?.toInt()?.coerceIn(1, 120) ?: 30
        } catch (e: Exception) {
            30
        } finally {
            retriever.release()
        }
    }

    /**
     * Cached SDR thumbnail path.
     *
     * R6.10c keeps this on MediaMetadataRetriever per [FrameExtractionPolicy].
     * Migrate to media3-inspector-frame only for HDR/effect-aware thumbnails or
     * custom decoder selection.
     */
    fun extractThumbnail(uri: Uri, timeUs: Long, width: Int = 160, height: Int = 90): Bitmap? {
        val key = "${uri}_${timeUs}_${width}x${height}"
        thumbnailCache.get(key)?.let { return it }

        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        return try {
            retriever.setDataSource(context, uri)
            frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val original = frame ?: return null
            // createScaledBitmap allocates natively and may throw OOM (an Error,
            // not an Exception) or IllegalArgumentException for zero-area sizes —
            // catch Throwable so the source `frame` is always recycled before
            // returning null. Previously OOM here leaked a full-resolution frame.
            val scaled = try {
                Bitmap.createScaledBitmap(original, width, height, true)
            } catch (t: Throwable) {
                Log.w(TAG, "Thumbnail scale failed at ${timeUs}us for $uri", t)
                null
            }
            if (scaled == null) {
                // Original frame is the only reference we own; recycle and bail.
                original.recycle()
                frame = null
                return null
            }
            if (scaled !== original) {
                original.recycle()
                frame = null
            }
            thumbnailCache.put(key, scaled)
            scaled
        } catch (e: Exception) {
            // Cooperative cancellation isn't possible here (sync API), but any
            // IO / setDataSource failure must still recycle the partial frame
            // before we return so we don't accumulate native bitmaps.
            frame?.recycle()
            Log.w(TAG, "Thumbnail extract failed at ${timeUs}us for $uri", e)
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun extractThumbnailStrip(
        uri: Uri,
        count: Int,
        width: Int = 80,
        height: Int = 45
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val duration = getVideoDuration(uri)
        if (duration <= 0 || count <= 0) return@withContext emptyList()

        val interval = duration * 1000L / count
        (0 until count).mapNotNull { i ->
            extractThumbnail(uri, i * interval, width, height)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun export(
        tracks: List<Track>,
        config: ExportConfig,
        outputFile: File,
        textOverlays: List<com.novacut.editor.model.TextOverlay> = emptyList(),
        imageOverlays: List<ImageOverlay> = emptyList(),
        lottieOverlays: List<LottieOverlaySpec> = emptyList(),
        trackedObjects: List<TrackedObject> = emptyList(),
        globalTransitions: List<GlobalTransition> = emptyList(),
        onProgress: (Float) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        // Atomic check-and-set to prevent two concurrent exports from racing
        synchronized(this) {
            if (_exportState.value == ExportState.EXPORTING) {
                Log.w(TAG, "Export already in progress")
                return
            }
            _exportState.value = ExportState.EXPORTING
            activeExportOutputFile = outputFile
        }
        _exportProgress.value = 0f
        _exportErrorMessage.value = null

        val reversedTempFiles = mutableListOf<File>()
        try {
            val processedTracks = preRenderReversedClips(tracks, reversedTempFiles, onProgress)
            ensureExportActive("reversed-clip pre-render")

            val transformerPlan = buildTransformerExportPlan(
                tracks = processedTracks,
                config = config,
                textOverlays = textOverlays,
                imageOverlays = imageOverlays,
                lottieOverlays = lottieOverlays,
                trackedObjects = trackedObjects,
                globalTransitions = globalTransitions
            )

            startTransformerWithPolling(
                composition = transformerPlan.composition,
                mimeType = transformerPlan.mimeType,
                config = config,
                outputFile = outputFile,
                onProgress = onProgress,
                onComplete = {
                    reversedTempFiles.forEach { it.delete() }
                    onComplete()
                },
                onError = { e ->
                    reversedTempFiles.forEach { it.delete() }
                    onError(e)
                }
            )
        } catch (e: CancellationException) {
            // User cancelled while pre-rendering or before the transformer
            // started. cancelExport() had no transformer to tear down in that
            // window, so the scratch files are cleaned up here instead. Not an
            // error — don't invoke onError; rethrow so the launching coroutine
            // finishes as cancelled (ExportDelegate handles this contract).
            Log.d(TAG, "Export cancelled during setup", e)
            reversedTempFiles.forEach { it.delete() }
            if (_exportState.value == ExportState.EXPORTING) {
                _exportState.value = ExportState.CANCELLED
            }
            _exportProgress.value = 0f
            activeTransformer = null
            activeExportOutputFile = null
            runCatching { outputFile.delete() }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Export setup failed", e)
            reversedTempFiles.forEach { it.delete() }
            _exportErrorMessage.value = e.message ?: "Export setup failed"
            _exportState.value = ExportState.ERROR
            _exportProgress.value = 0f
            activeTransformer = null
            activeExportOutputFile = null
            outputFile.delete()
            onError(e)
        }
    }

    private suspend fun preRenderReversedClips(
        tracks: List<Track>,
        tempFiles: MutableList<File>,
        onProgress: (Float) -> Unit
    ): List<Track> {
        if (!ffmpegEngine.isAvailable()) return tracks

        val reversedClips = tracks.flatMap { track ->
            track.clips.filter { it.isReversed }.map { track to it }
        }
        if (reversedClips.isEmpty()) return tracks

        val maxReverseDurationMs = 5L * 60 * 1000
        val clipReplacements = mutableMapOf<String, Clip>()

        for ((index, pair) in reversedClips.withIndex()) {
            // cancelExport() has no transformer to cancel while FFmpeg owns
            // this phase — honor the CANCELLED state between clips so a
            // cancelled export stops queueing minutes of reverse renders.
            if (_exportState.value != ExportState.EXPORTING) break
            val (_, clip) = pair
            val clipDurationMs = clip.trimEndMs - clip.trimStartMs
            if (clipDurationMs > maxReverseDurationMs) {
                Log.w(TAG, "Reversed clip ${clip.id} exceeds ${maxReverseDurationMs / 1000}s limit, exporting forward")
                continue
            }

            val tempFile = File(context.cacheDir, "reverse_${clip.id}_${System.nanoTime()}.mp4")
            val success = ffmpegEngine.reverseClipToFile(
                inputUri = clip.sourceUri,
                outputFile = tempFile,
                trimStartMs = clip.trimStartMs,
                trimEndMs = clip.trimEndMs,
                onProgress = { p ->
                    val base = index.toFloat() / reversedClips.size
                    val weight = 1f / reversedClips.size
                    onProgress((base + p * weight) * 0.1f)
                }
            )

            if (success && tempFile.exists() && tempFile.length() > 0) {
                tempFiles.add(tempFile)
                val reversedDurationMs = getVideoDuration(android.net.Uri.fromFile(tempFile))
                    .takeIf { it > 0 } ?: clipDurationMs
                clipReplacements[clip.id] = clip.copy(
                    sourceUri = android.net.Uri.fromFile(tempFile),
                    trimStartMs = 0L,
                    trimEndMs = reversedDurationMs,
                    sourceDurationMs = reversedDurationMs,
                    isReversed = false
                )
                Log.d(TAG, "Pre-rendered reversed clip ${clip.id} → ${tempFile.name}")
            } else {
                tempFile.delete()
                Log.w(TAG, "Failed to pre-render reversed clip ${clip.id}, exporting forward")
            }
        }

        if (clipReplacements.isEmpty()) return tracks

        return tracks.map { track ->
            track.copy(clips = track.clips.map { clip ->
                clipReplacements[clip.id] ?: clip
            })
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun exportMixed(
        plan: MixedRenderComposer.CompositionPlan,
        tracks: List<Track>,
        config: ExportConfig,
        outputFile: File,
        textOverlays: List<com.novacut.editor.model.TextOverlay> = emptyList(),
        imageOverlays: List<ImageOverlay> = emptyList(),
        lottieOverlays: List<LottieOverlaySpec> = emptyList(),
        trackedObjects: List<TrackedObject> = emptyList(),
        onProgress: (Float) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ): Boolean {
        if (plan.benefit != MixedRenderComposer.Benefit.Mixed || !plan.needsConcat) return false
        if (textOverlays.isNotEmpty() || imageOverlays.isNotEmpty() ||
            lottieOverlays.isNotEmpty() || trackedObjects.any { it.isEnabled }
        ) {
            Log.d(TAG, "Mixed export skipped: overlays or tracked objects require whole-timeline Transformer")
            return false
        }
        if (!ffmpegEngine.isAvailable()) {
            Log.d(TAG, "Mixed export skipped: FFmpeg concat unavailable")
            return false
        }

        preflightMixedStreamCopyRuns(plan, tracks)?.let { reason ->
            Log.d(TAG, "Mixed export skipped: $reason")
            return false
        }

        synchronized(this) {
            if (_exportState.value == ExportState.EXPORTING) {
                Log.w(TAG, "Export already in progress")
                return true
            }
            _exportState.value = ExportState.EXPORTING
            activeExportOutputFile = outputFile
        }
        _exportProgress.value = 0f
        _exportErrorMessage.value = null

        val parentDir = outputFile.parentFile ?: context.cacheDir
        val tempDir = File(
            parentDir,
            ".clearcut-mixed-${MixedRenderComposer.sanitiseStem(outputFile.nameWithoutExtension)}-" +
                System.currentTimeMillis()
        )
        val runWeightSum = plan.runs.sumOf { it.run.durationMs.coerceAtLeast(1L) }
        val concatWeight = (runWeightSum / 20L).coerceAtLeast(1L)
        val totalWeight = (runWeightSum + concatWeight).coerceAtLeast(1L)
        var completedWeight = 0L

        fun publishMixedProgress(baseWeight: Long, stepWeight: Long, progress: Float) {
            val mixedProgress = (
                baseWeight.toDouble() + stepWeight.toDouble() * progress.coerceIn(0f, 1f)
            ) / totalWeight.toDouble()
            val clamped = mixedProgress.toFloat().coerceIn(0f, 0.99f)
            _exportProgress.value = clamped
            onProgress(clamped)
        }

        return try {
            withContext(Dispatchers.IO) { tempDir.mkdirs() }
            val outputsByName = mutableMapOf<String, File>()

            for (execution in plan.runs.sortedBy { it.index }) {
                ensureExportActive("mixed run ${execution.index}")
                val stepWeight = execution.run.durationMs.coerceAtLeast(1L)
                val runOutput = File(tempDir, execution.outputFileName)
                when (execution.engine) {
                    MixedRenderComposer.Engine.STREAM_COPY -> {
                        val runTracks = MixedRenderExportPlanner.sliceTracksForRun(
                            tracks = tracks,
                            run = execution.run,
                            normaliseTimelineStart = false
                        )
                        val eligibility = streamCopyEngine.analyze(runTracks, hasEffectsOrOverlays = false)
                        if (!eligibility.eligible) {
                            throw IllegalStateException(
                                "Mixed stream-copy run ${execution.index} is not eligible: ${eligibility.reason}"
                            )
                        }
                        val ok = streamCopyEngine.execute(
                            e = eligibility,
                            outputPath = runOutput.absolutePath,
                            onProgress = { progress ->
                                publishMixedProgress(completedWeight, stepWeight, progress)
                            }
                        )
                        if (!ok) {
                            throw IllegalStateException("Mixed stream-copy run ${execution.index} failed")
                        }
                    }
                    MixedRenderComposer.Engine.TRANSFORMER -> {
                        val runTracks = MixedRenderExportPlanner.sliceTracksForRun(
                            tracks = tracks,
                            run = execution.run,
                            normaliseTimelineStart = true
                        )
                        val transformerPlan = buildTransformerExportPlan(
                            tracks = runTracks,
                            config = config,
                            textOverlays = emptyList(),
                            imageOverlays = emptyList(),
                            lottieOverlays = emptyList(),
                            trackedObjects = emptyList()
                        )
                        var segmentError: Exception? = null
                        startTransformerWithPolling(
                            composition = transformerPlan.composition,
                            mimeType = transformerPlan.mimeType,
                            config = config,
                            outputFile = runOutput,
                            onProgress = { progress ->
                                publishMixedProgress(completedWeight, stepWeight, progress)
                            },
                            onComplete = {
                                publishMixedProgress(completedWeight, stepWeight, 1f)
                            },
                            onError = { error -> segmentError = error },
                            markCompleteOnFinish = false
                        )
                        segmentError?.let { throw it }
                    }
                }
                ensureNonEmptyExportOutput(runOutput, "Mixed run ${execution.index}")
                outputsByName[execution.outputFileName] = runOutput
                completedWeight += stepWeight
                publishMixedProgress(completedWeight, 1L, 0f)
            }

            ensureExportActive("mixed concat")
            val concat = plan.concat ?: return false
            val concatInputs = concat.inputs.map { name ->
                outputsByName[name] ?: throw IllegalStateException("Mixed concat input missing: $name")
            }
            activeExportOutputFile = outputFile
            val concatOk = ffmpegEngine.concat(
                inputFiles = concatInputs,
                outputFile = outputFile,
                onProgress = { progress ->
                    publishMixedProgress(completedWeight, concatWeight, progress)
                }
            )
            if (!concatOk) {
                throw IllegalStateException("Mixed FFmpeg concat failed")
            }
            ensureNonEmptyExportOutput(outputFile, "Mixed concat")

            _exportState.value = ExportState.COMPLETE
            _exportProgress.value = 1f
            activeExportOutputFile = null
            onProgress(1f)
            onComplete()
            true
        } catch (e: CancellationException) {
            Log.d(TAG, "Mixed export cancelled", e)
            if (_exportState.value == ExportState.EXPORTING) {
                _exportState.value = ExportState.CANCELLED
            }
            _exportProgress.value = 0f
            activeTransformer = null
            activeExportOutputFile = null
            runCatching { outputFile.delete() }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Mixed export failed", e)
            _exportErrorMessage.value = e.message ?: "Mixed export failed"
            _exportState.value = ExportState.ERROR
            _exportProgress.value = 0f
            activeTransformer = null
            activeExportOutputFile = null
            runCatching { outputFile.delete() }
            onError(e)
            true
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

    private fun preflightMixedStreamCopyRuns(
        plan: MixedRenderComposer.CompositionPlan,
        tracks: List<Track>
    ): String? {
        for (execution in plan.runs) {
            if (execution.engine != MixedRenderComposer.Engine.STREAM_COPY) continue
            val runTracks = MixedRenderExportPlanner.sliceTracksForRun(
                tracks = tracks,
                run = execution.run,
                normaliseTimelineStart = false
            )
            val eligibility = streamCopyEngine.analyze(runTracks, hasEffectsOrOverlays = false)
            if (!eligibility.eligible) {
                return "stream-copy run ${execution.index} is not eligible: ${eligibility.reason}"
            }
        }
        return null
    }

    private fun ensureExportActive(step: String) {
        when (_exportState.value) {
            ExportState.EXPORTING -> Unit
            ExportState.CANCELLED -> throw CancellationException("Export cancelled during $step")
            ExportState.ERROR -> throw IllegalStateException(
                _exportErrorMessage.value ?: "Export failed during $step"
            )
            else -> throw CancellationException("Export stopped during $step")
        }
    }

    private fun ensureNonEmptyExportOutput(outputFile: File, label: String) {
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            throw IllegalStateException("$label produced an empty output file")
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildTransformerExportPlan(
        tracks: List<Track>,
        config: ExportConfig,
        textOverlays: List<com.novacut.editor.model.TextOverlay>,
        imageOverlays: List<ImageOverlay>,
        lottieOverlays: List<LottieOverlaySpec>,
        trackedObjects: List<TrackedObject>,
        globalTransitions: List<GlobalTransition> = emptyList()
    ): TransformerExportPlan {
        val visibleVideoTracks = tracks
            .sortedBy { it.index }
            .filter {
                (it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY) &&
                    it.isVisible &&
                    it.clips.any { clip -> clip.durationMs > 0L }
            }
        if (visibleVideoTracks.isEmpty()) {
            throw IllegalStateException("No video clips to export")
        }
        val soloTrackIds = tracks.filter { it.isSolo }.map { it.id }.toSet()
        val (targetW, targetH) = config.resolution.forAspect(config.aspectRatio)

        val totalTimelineDurationMs = maxOf(
            tracks.maxOfOrNull { track ->
                track.clips.maxOfOrNull { clip -> clip.timelineEndMs } ?: 0L
            } ?: 0L,
            textOverlays.maxOfOrNull { it.endTimeMs } ?: 0L,
            imageOverlays.maxOfOrNull { it.endTimeMs } ?: 0L,
            lottieOverlays.maxOfOrNull { it.endTimeMs } ?: 0L
        )
        val reversedCount = visibleVideoTracks.sumOf { track -> track.clips.count { it.isReversed } }
        if (reversedCount > 0) {
            Log.w(TAG, "Export: $reversedCount reversed clip(s) not pre-rendered (FFmpeg unavailable or over limit)")
        }
        val visualTrackSequences = buildVideoSequences(
            visibleVideoTracks = visibleVideoTracks,
            soloTrackIds = soloTrackIds,
            tracks = tracks,
            totalTimelineDurationMs = totalTimelineDurationMs,
            config = config,
            targetW = targetW,
            globalTransitions = globalTransitions,
            targetH = targetH,
            textOverlays = textOverlays,
            imageOverlays = imageOverlays,
            lottieOverlays = lottieOverlays,
            trackedObjects = trackedObjects
        )
        val unsupportedTrackBlendModes = visualTrackSequences
            .count { it.compositorLayer.blendMode != BlendMode.NORMAL }
        if (unsupportedTrackBlendModes > 0) {
            Log.w(
                TAG,
                "Export: $unsupportedTrackBlendModes track blend mode(s) render with normal alpha " +
                    "because Media3's public compositor settings expose alpha/transform only"
            )
        }

        val audioSequences = buildAudioSequences(tracks, soloTrackIds)
        val allSequences = buildList {
            visualTrackSequences.forEach { add(it.sequence) }
            addAll(audioSequences)
        }
        val hasEmbeddedVisualAudio = visualTrackSequences.any { it.hasEmbeddedAudio }

        val preserveHdr = config.hdr10PlusMetadata && config.codec != VideoCodec.H264
        val composition = buildComposition(
            allSequences,
            audioSequences.isNotEmpty(),
            hasEmbeddedVisualAudio,
            targetWidth = targetW,
            targetHeight = targetH,
            hasMultipleVideoSequences = visualTrackSequences.size > 1,
            preserveHdr = preserveHdr,
            compositorLayers = visualTrackSequences.map { it.compositorLayer }
        )

        val mimeType = if (config.transparentBackground) {
            MimeTypes.VIDEO_VP9
        } else when (config.codec) {
            VideoCodec.HEVC -> MimeTypes.VIDEO_H265
            VideoCodec.H264 -> MimeTypes.VIDEO_H264
            VideoCodec.AV1 -> MimeTypes.VIDEO_AV1
            VideoCodec.VP9 -> MimeTypes.VIDEO_VP9
        }

        return TransformerExportPlan(composition, mimeType)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildVideoSequences(
        visibleVideoTracks: List<Track>,
        soloTrackIds: Set<String>,
        tracks: List<Track>,
        totalTimelineDurationMs: Long,
        config: ExportConfig,
        targetW: Int,
        globalTransitions: List<GlobalTransition> = emptyList(),
        targetH: Int,
        textOverlays: List<com.novacut.editor.model.TextOverlay>,
        imageOverlays: List<ImageOverlay>,
        lottieOverlays: List<LottieOverlaySpec>,
        trackedObjects: List<TrackedObject>
    ): List<VisualTrackSequence> {
        return visibleVideoTracks.mapIndexed { inputId, track ->
            val includesEmbeddedAudio = track.clips.any { clip ->
                clip.durationMs > 0L && hasAudioTrack(clip.sourceUri)
            }
            val trackAudioGain = if (includesEmbeddedAudio && isTrackAudibleForMix(track, soloTrackIds)) {
                track.volume.coerceIn(0f, 2f)
            } else {
                0f
            }
            val hasEmbeddedAudio = trackAudioGain > 0f
            VisualTrackSequence(
                sequence = buildVideoSequence(
                    clips = track.clips,
                    totalTimelineDurationMs = totalTimelineDurationMs,
                    videoMuted = !hasEmbeddedAudio,
                    trackAudioGain = trackAudioGain,
                    tracks = tracks,
                    config = config,
                    targetW = targetW,
                    targetH = targetH,
                    textOverlays = textOverlays,
                    imageOverlays = imageOverlays,
                    lottieOverlays = lottieOverlays,
                    trackedObjects = trackedObjects,
                    globalTransitions = globalTransitions
                ),
                hasEmbeddedAudio = hasEmbeddedAudio,
                compositorLayer = ClearCutCompositorLayer(
                    inputId = inputId,
                    trackId = track.id,
                    trackIndex = track.index,
                    opacity = track.opacity,
                    blendMode = track.blendMode
                )
            )
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildVideoSequence(
        clips: List<Clip>,
        totalTimelineDurationMs: Long,
        videoMuted: Boolean,
        trackAudioGain: Float,
        tracks: List<Track>,
        config: ExportConfig,
        targetW: Int,
        targetH: Int,
        textOverlays: List<com.novacut.editor.model.TextOverlay>,
        imageOverlays: List<ImageOverlay>,
        lottieOverlays: List<LottieOverlaySpec>,
        trackedObjects: List<TrackedObject>,
        globalTransitions: List<GlobalTransition> = emptyList()
    ): EditedMediaItemSequence {
        val sortedClips = clips.filter { it.durationMs > 0L }.sortedBy { it.timelineStartMs }
        val trackTypes = if (videoMuted) {
            setOf(C.TRACK_TYPE_VIDEO)
        } else {
            setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
        }
        val builder = EditedMediaItemSequence.Builder(trackTypes)
        var clipIndex = 0

        for (step in buildTimelineSequenceSteps(sortedClips, totalTimelineDurationMs)) {
            when (step) {
                is TimelineSequenceStep.GapStep -> {
                    builder.addGap(durationMsToUs(step.durationMs))
                }
                is TimelineSequenceStep.ClipStep -> {
                    val clip = step.clip
                    val nextClip = sortedClips.getOrNull(clipIndex + 1)
                    val nextTransition = clip.tailTransition
                        ?: nextClip
                            ?.takeIf { it.timelineStartMs <= clip.timelineEndMs }
                            ?.headTransition
                    builder.addItem(
                        buildEditedMediaItem(
                            clip = clip,
                            videoMuted = videoMuted,
                            trackAudioGain = trackAudioGain,
                            tracks = tracks,
                            config = config,
                            targetW = targetW,
                            targetH = targetH,
                            textOverlays = textOverlays,
                            imageOverlays = imageOverlays,
                            lottieOverlays = lottieOverlays,
                            trackedObjects = trackedObjects,
                            nextClipTransition = nextTransition,
                            globalTransitions = globalTransitions
                        )
                    )
                    clipIndex++
                }
            }
        }

        return builder.build()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildEditedMediaItem(
        clip: Clip,
        videoMuted: Boolean,
        trackAudioGain: Float,
        tracks: List<Track>,
        config: ExportConfig,
        targetW: Int,
        targetH: Int,
        textOverlays: List<com.novacut.editor.model.TextOverlay>,
        imageOverlays: List<ImageOverlay>,
        lottieOverlays: List<LottieOverlaySpec>,
        trackedObjects: List<TrackedObject>,
        nextClipTransition: Transition? = null,
        globalTransitions: List<GlobalTransition> = emptyList()
    ): EditedMediaItem {
        val mediaItem = buildMediaItemForClip(clip, clip.sourceUri)
        val linkedAudioTrackPresent = clip.linkedClipId?.let { linkedId ->
            tracks.any { track ->
                track.type == TrackType.AUDIO && track.clips.any { it.id == linkedId }
            }
        } == true

        val videoEffects = buildList<androidx.media3.common.Effect> {
            val clipTrackedObjects = trackedObjects.filter { it.sourceClipId == clip.id && it.isEnabled }
            for (effect in clip.effects.filter { it.enabled }) {
                EffectBuilder.buildVideoEffect(
                    effect = effect,
                    segmentationEngine = segmentationEngine,
                    trackedObjects = clipTrackedObjects,
                    sourceTimeOffsetMs = clip.trimStartMs
                )?.let { add(it) }
            }
            addColorGradingEffects(clip)

            val maskTimeMs = clip.durationMs / 2
            for (mask in clip.masks) {
                val points = KeyframeEngine.interpolateMaskPoints(mask, maskTimeMs)
                when (mask.type) {
                    com.novacut.editor.model.MaskType.RECTANGLE -> {
                        if (points.size >= 2) {
                            val cx = (points[0].x + points[1].x) / 2f
                            val cy = (points[0].y + points[1].y) / 2f
                            val w = kotlin.math.abs(points[1].x - points[0].x)
                            val h = kotlin.math.abs(points[1].y - points[0].y)
                            add(EffectShaders.rectangleMask(cx, cy, w, h, mask.feather / 100f, if (mask.inverted) 1f else 0f))
                        }
                    }
                    com.novacut.editor.model.MaskType.ELLIPSE -> {
                        if (points.size >= 2) {
                            add(EffectShaders.ellipseMask(
                                points[0].x, points[0].y,
                                points[1].x, points[1].y,
                                mask.feather / 100f, if (mask.inverted) 1f else 0f
                            ))
                        }
                    }
                    else -> {}
                }
            }

            if (clip.blendMode != com.novacut.editor.model.BlendMode.NORMAL) {
                add(EffectShaders.blendMode(clip.blendMode, clip.opacity))
            }

            clip.headTransition?.let { add(EffectBuilder.buildTransitionEffect(it)) }
            nextClipTransition?.let { add(EffectBuilder.buildTransitionOutEffect(it, clip.durationMs)) }

            GlobalTransitionEffect.forClip(globalTransitions, clip.timelineStartMs, clip.timelineEndMs)
                ?.let { add(it) }

            addOpacityAndTransformEffects(clip)

            val clipStart = clip.timelineStartMs
            val clipEnd = clip.timelineEndMs
            val overlapping = textOverlays.filter { overlay ->
                overlay.startTimeMs < clipEnd && overlay.endTimeMs > clipStart
            }
            val overlappingImages = imageOverlays.filter { overlay ->
                overlay.startTimeMs < clipEnd && overlay.endTimeMs > clipStart
            }
            val overlappingLottie = lottieOverlays.filter { lo ->
                lo.startTimeMs < clipEnd && lo.endTimeMs > clipStart
            }
            val preserveLottieHdr = config.hdr10PlusMetadata && config.codec != VideoCodec.H264
            val lottieBackendPlans = overlappingLottie.map { lo ->
                val relStartUs = ((lo.startTimeMs - clipStart).coerceAtLeast(0L)) * 1000L
                val durationUs = (lo.endTimeMs - lo.startTimeMs).coerceAtLeast(1L) * 1000L
                val decision = chooseLottieOverlayBackend(
                    preserveHdr = preserveLottieHdr,
                    overlayDurationUs = durationUs,
                    compositionDurationUs = lottieCompositionDurationUs(lo.composition)
                )
                LottieBackendPlan(lo, relStartUs, durationUs, decision)
            }
            // Build a combined overlay list for text/image overlays and the optional
            // brand watermark. Keeping them in one OverlayEffect
            // (vs. two consecutive effects) lets Media3 composite them in a
            // single GL pass, so a project-wide watermark has no extra cost
            // when no timeline overlays overlap this clip.
            val overlayList = buildList<TextureOverlay> {
                overlapping.forEach { overlay ->
                    val relStart = (overlay.startTimeMs - clipStart).coerceAtLeast(0L)
                    val relEnd = (overlay.endTimeMs - clipStart).coerceAtMost(clip.durationMs)
                    // Stroke-width > 0 requires Canvas rendering with a
                    // distinct stroke+fill color pair, which SpannableString
                    // cannot express. Fall through to the bitmap-based path
                    // only when strokes are active so the cheap text path is
                    // unchanged for the vast majority of overlays.
                    if (overlay.strokeWidth > 0f) {
                        add(StrokedTextBitmapOverlay(overlay, relStart, relEnd))
                    } else {
                        add(ExportTextOverlay(overlay, relStart, relEnd, fontRegistry))
                    }
                }
                overlappingImages.forEach { overlay ->
                    val relStart = (overlay.startTimeMs - clipStart).coerceAtLeast(0L)
                    val relEnd = (overlay.endTimeMs - clipStart).coerceAtMost(clip.durationMs)
                    val animated = ExportAnimatedImageOverlay.isAnimatedSource(context, overlay.sourceUri)
                    if (animated) {
                        ExportAnimatedImageOverlay.create(
                            context = context,
                            overlay = overlay,
                            relStartMs = relStart,
                            relEndMs = relEnd,
                            outputFrameWidth = targetW,
                        )?.let { add(it) }
                    } else {
                        ExportImageOverlay.create(
                            context = context,
                            overlay = overlay,
                            relStartMs = relStart,
                            relEndMs = relEnd,
                            outputFrameWidth = targetW,
                        )?.let { add(it) }
                    }
                }
                config.watermark?.let { watermark ->
                    ExportWatermarkOverlay.create(
                        context = context,
                        watermark = watermark,
                        outputFrameWidth = targetW
                    )?.let { add(it) }
                }
            }
            if (overlayList.isNotEmpty()) {
                add(OverlayEffect(com.google.common.collect.ImmutableList.copyOf(overlayList)))
            }

            for (plan in lottieBackendPlans) {
                val lo = plan.overlay
                when (plan.decision.backend) {
                    LottieOverlayBackend.MEDIA3_LOTTIE -> add(
                        OverlayEffect(
                            listOf<TextureOverlay>(
                                Media3LottieTextureOverlay(
                                    composition = lo.composition,
                                    overlayStartUs = plan.overlayStartUs,
                                    overlayDurationUs = plan.overlayDurationUs,
                                    textReplacements = lo.textReplacements
                                )
                            )
                        )
                    )
                    LottieOverlayBackend.CLEARCUT_SHADER -> {
                        Log.d(TAG, "Export: keeping custom Lottie shader path (${plan.decision.reason})")
                        add(LottieOverlayEffect(
                            lottieEngine = lo.engine,
                            composition = lo.composition,
                            overlayStartUs = plan.overlayStartUs,
                            overlayDurationUs = plan.overlayDurationUs,
                            textReplacements = lo.textReplacements
                        ))
                    }
                }
            }

            val adjustmentTracks = tracks.filter { it.type == TrackType.ADJUSTMENT && it.isVisible }
            for (adjTrack in adjustmentTracks) {
                for (adjClip in adjTrack.clips) {
                    if (adjClip.timelineStartMs < clipEnd && adjClip.timelineEndMs > clipStart) {
                        for (effect in adjClip.effects.filter { it.enabled }) {
                            EffectBuilder.buildVideoEffect(effect, segmentationEngine)?.let { add(it) }
                        }
                    }
                }
            }

            add(FrameDropEffect.createDefaultFrameDropEffect(config.frameRate.toFloat()))
            add(Presentation.createForWidthAndHeight(targetW, targetH, Presentation.LAYOUT_SCALE_TO_FIT))
        }

        val audioProcessors = buildList<AudioProcessor> {
            if (videoMuted || linkedAudioTrackPresent) {
                add(VolumeAudioProcessor(
                    volume = 0f, fadeInMs = 0L, fadeOutMs = 0L,
                    clipDurationMs = clip.durationMs, keyframes = emptyList()
                ))
            } else {
                val hasKfVolume = clip.keyframes.any { it.property == KeyframeProperty.VOLUME }
                val needsVolume = clip.volume != 1.0f
                val needsFade = clip.fadeInMs > 0L || clip.fadeOutMs > 0L
                val needsTrackGain = trackAudioGain != 1.0f
                if (hasKfVolume || needsVolume || needsFade || needsTrackGain) {
                    add(VolumeAudioProcessor(
                        volume = clip.volume, fadeInMs = clip.fadeInMs, fadeOutMs = clip.fadeOutMs,
                        clipDurationMs = clip.durationMs,
                        keyframes = if (hasKfVolume) clip.keyframes else emptyList(),
                        postGain = trackAudioGain
                    ))
                }
            }
        }

        val itemBuilder = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(audioProcessors, videoEffects))

        if (clip.speedCurve != null && clip.speedCurve.points.size >= 2) {
            val curve = clip.speedCurve
            val clipDurMs = clip.trimEndMs - clip.trimStartMs
            itemBuilder.setSpeed(object : androidx.media3.common.audio.SpeedProvider {
                override fun getSpeed(presentationTimeUs: Long): Float {
                    val timeMs = presentationTimeUs / 1000L
                    return curve.getSpeedAt(timeMs, clipDurMs).coerceIn(0.1f, 100f)
                }
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
            })
        } else if (clip.speed != 1.0f) {
            val constSpeed = clip.speed.coerceIn(0.1f, 100f)
            itemBuilder.setSpeed(object : androidx.media3.common.audio.SpeedProvider {
                override fun getSpeed(presentationTimeUs: Long): Float = constSpeed
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
            })
        }

        return itemBuilder.build()
    }

    private fun buildMediaItemForClip(
        clip: Clip,
        mediaUri: Uri
    ): MediaItem {
        val builder = MediaItem.Builder().setUri(mediaUri)
        return if (isImageUri(mediaUri)) {
            builder
                .setImageDurationMs(clip.durationMs.coerceAtLeast(1L))
                .build()
        } else {
            builder
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs)
                        .setEndPositionMs(clip.trimEndMs)
                        .build()
                )
                .build()
        }
    }

    private fun buildMediaItemForPreviewSegment(segment: PreviewSegment): MediaItem {
        val clip = segment.clip
        return if (clip != null) {
            buildMediaItemForClip(clip, segment.mediaUri)
        } else {
            MediaItem.Builder()
                .setUri(segment.mediaUri)
                .setImageDurationMs(segment.durationMs.coerceAtLeast(1L))
                .build()
        }
    }

    private fun isImageUri(uri: Uri): Boolean {
        val mimeType = resolveMimeType(uri)
        if (!mimeType.isNullOrBlank()) {
            return mimeType.startsWith("image/")
        }
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?: return false
        return extension in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
    }

    private fun resolveMimeType(uri: Uri): String? {
        context.contentResolver.getType(uri)?.let { return it }
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun getMediaCharacteristics(uri: Uri): MediaCharacteristics {
        val key = uri.toString()
        mediaCharacteristicsCache[key]?.let { return it }

        val probed = probeMediaCharacteristics(uri)
        mediaCharacteristicsCache.putIfAbsent(key, probed)
        return mediaCharacteristicsCache[key] ?: probed
    }

    private fun probeMediaCharacteristics(uri: Uri): MediaCharacteristics {
        if (isImageUri(uri)) {
            return MediaCharacteristics(
                isStillImage = true,
                hasVisual = true,
                hasAudio = false
            )
        }

        val mimeType = resolveMimeType(uri)
        val fallbackHasVisual = mimeType?.startsWith("video/") == true
        val fallbackHasAudio = mimeType?.startsWith("audio/") == true
        val extractor = MediaExtractor()

        return try {
            extractor.setDataSource(context, uri, emptyMap())
            var hasVisual = false
            var hasAudio = false

            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val trackMimeType = format.getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    trackMimeType.startsWith("video/") -> hasVisual = true
                    trackMimeType.startsWith("audio/") -> hasAudio = true
                }
            }

            MediaCharacteristics(
                isStillImage = false,
                hasVisual = hasVisual || fallbackHasVisual,
                hasAudio = hasAudio || fallbackHasAudio
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to probe media characteristics for $uri", e)
            MediaCharacteristics(
                isStillImage = false,
                hasVisual = fallbackHasVisual,
                hasAudio = fallbackHasAudio
            )
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildAudioSequences(
        tracks: List<Track>,
        soloTrackIds: Set<String>
    ): List<EditedMediaItemSequence> {
        val audioTracks = tracks
            .sortedBy { it.index }
            .filter { it.type == TrackType.AUDIO && it.clips.isNotEmpty() && isTrackAudibleForMix(it, soloTrackIds) }
        return audioTracks.map { at ->
            val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            for (step in buildTimelineSequenceSteps(at.clips)) {
                when (step) {
                    is TimelineSequenceStep.GapStep -> {
                        builder.addGap(durationMsToUs(step.durationMs))
                    }
                    is TimelineSequenceStep.ClipStep -> {
                        val clip = step.clip
                        val mediaItem = MediaItem.Builder()
                            .setUri(clip.sourceUri)
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(clip.trimStartMs)
                                    .setEndPositionMs(clip.trimEndMs)
                                    .build()
                            )
                            .build()
                        val processors = buildList<AudioProcessor> {
                            val hasKfVol = clip.keyframes.any { it.property == KeyframeProperty.VOLUME }
                            val needsVolume = clip.volume != 1.0f
                            val needsFade = clip.fadeInMs > 0L || clip.fadeOutMs > 0L
                            val needsTrackGain = at.volume != 1.0f
                            if (hasKfVol || needsVolume || needsFade || needsTrackGain) {
                                add(VolumeAudioProcessor(
                                    volume = clip.volume,
                                    fadeInMs = clip.fadeInMs,
                                    fadeOutMs = clip.fadeOutMs,
                                    clipDurationMs = clip.durationMs,
                                    keyframes = if (hasKfVol) clip.keyframes else emptyList(),
                                    postGain = at.volume.coerceIn(0f, 2f)
                                ))
                            }
                        }
                        builder.addItem(
                            EditedMediaItem.Builder(mediaItem)
                                .setEffects(Effects(processors, emptyList()))
                                .setRemoveVideo(true)
                                .build()
                        )
                    }
                }
            }
            builder.build()
        }
    }

    private fun collectPreviewClips(tracks: List<Track>): List<Clip> {
        val primaryVisualTrack = tracks
            .sortedBy { it.index }
            .firstOrNull { (it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY) && it.isVisible && it.clips.isNotEmpty() }
            ?: return emptyList()
        return primaryVisualTrack.clips.sortedBy { it.timelineStartMs }
    }

    private fun buildPreviewSegments(
        clips: List<Clip>,
        totalTimelineDurationMs: Long,
        missingClipIds: Set<String> = emptySet()
    ): List<PreviewSegment> {
        if (clips.isEmpty()) return emptyList()

        val segments = mutableListOf<PreviewSegment>()
        val gapUri = getPreviewGapUri()
        var cursorMs = 0L

        clips.forEach { clip ->
            if (clip.timelineStartMs > cursorMs) {
                segments += PreviewSegment(
                    clip = null,
                    timelineStartMs = cursorMs,
                    durationMs = clip.timelineStartMs - cursorMs,
                    mediaUri = gapUri
                )
            }
            if (clip.id in missingClipIds) {
                segments += PreviewSegment(
                    clip = clip,
                    timelineStartMs = clip.timelineStartMs,
                    durationMs = clip.durationMs,
                    mediaUri = gapUri
                )
            } else {
                segments += PreviewSegment(
                    clip = clip,
                    timelineStartMs = clip.timelineStartMs,
                    durationMs = clip.durationMs,
                    mediaUri = resolvePreviewMediaUri(clip)
                )
            }
            cursorMs = clip.timelineEndMs
        }

        val timelineEndMs = maxOf(totalTimelineDurationMs, cursorMs)
        if (timelineEndMs > cursorMs) {
            segments += PreviewSegment(
                clip = null,
                timelineStartMs = cursorMs,
                durationMs = timelineEndMs - cursorMs,
                mediaUri = gapUri
            )
        }

        return segments.filter { it.durationMs > 0L }
    }

    private fun resolvePreviewMediaUri(clip: Clip): Uri {
        val proxyUri = clip.proxyUri ?: return clip.sourceUri
        if (isReadableMediaUri(proxyUri)) {
            return proxyUri
        }
        Log.w(TAG, "Ignoring unreadable proxy for clip ${clip.id}: $proxyUri")
        return clip.sourceUri
    }

    private fun isReadableMediaUri(uri: Uri): Boolean {
        if (uri.scheme == "file") {
            val file = uri.path?.let(::File) ?: return false
            return file.isFile && file.length() > 0L
        }

        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length != 0L
            } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun getPreviewGapUri(): Uri {
        previewGapUri?.let { return it }
        synchronized(this) {
            previewGapUri?.let { return it }

            val dir = File(context.filesDir, "preview")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "gap_frame.png")
            if (!file.exists() || file.length() == 0L) {
                val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.BLACK)
                    writeFileAtomically(file, requireNonEmpty = true) { tempFile ->
                        tempFile.outputStream().use { output ->
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                throw IllegalStateException("Gap-frame encoder returned no data")
                            }
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            return Uri.fromFile(file).also { previewGapUri = it }
        }
    }

    private fun resolvePreviewSeekTarget(positionMs: Long): PreviewSeekTarget? {
        if (previewSegments.isEmpty()) return null

        val targetMs = positionMs.coerceAtLeast(0L)

        previewSegments.forEachIndexed { index, segment ->
            if (targetMs < segment.timelineEndMs) {
                return PreviewSeekTarget(
                    mediaItemIndex = index,
                    mediaPositionMs = segment.clip?.let { clip ->
                        previewMediaPositionForTimelinePosition(clip, targetMs)
                    } ?: (targetMs - segment.timelineStartMs)
                        .coerceIn(0L, (segment.durationMs - 1L).coerceAtLeast(0L))
                )
            }
        }

        val lastClip = previewSegments.last()
        return PreviewSeekTarget(
            mediaItemIndex = previewSegments.lastIndex,
            mediaPositionMs = (lastClip.durationMs - 1L).coerceAtLeast(0L)
        )
    }

    private fun isTrackAudibleForMix(track: Track, soloTrackIds: Set<String>): Boolean {
        return track.isVisible && !track.isMuted && (soloTrackIds.isEmpty() || track.id in soloTrackIds)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildComposition(
        sequences: List<EditedMediaItemSequence>,
        hasAudioTracks: Boolean,
        hasEmbeddedVisualAudio: Boolean,
        targetWidth: Int,
        targetHeight: Int,
        hasMultipleVideoSequences: Boolean = false,
        preserveHdr: Boolean = false,
        compositorLayers: List<ClearCutCompositorLayer> = emptyList()
    ): Composition {
        val builder = Composition.Builder(sequences)
            .setTransmuxAudio(!hasAudioTracks && hasEmbeddedVisualAudio && !hasMultipleVideoSequences)
        if (hasMultipleVideoSequences) {
            builder.setVideoCompositorSettings(
                ClearCutVideoCompositorSettings(
                    outputWidth = targetWidth,
                    outputHeight = targetHeight,
                    layers = compositorLayers
                )
            )
        }
        if (preserveHdr) {
            // HDR_MODE_KEEP_HDR preserves HDR metadata through the pipeline
            // rather than tone-mapping to SDR. Honoured only when the source
            // track advertises HDR and the device's encoder supports an HDR
            // profile for the chosen codec. On non-HDR sources or devices
            // without HDR encode support, Media3 silently falls back to SDR
            // and the output is identical to the default path — so setting
            // this flag is always safe.
            try {
                builder.setHdrMode(Composition.HDR_MODE_KEEP_HDR)
            } catch (e: Throwable) {
                Log.w(TAG, "setHdrMode unavailable on this Media3 build", e)
            }
        }
        return builder.build()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun startTransformerWithPolling(
        composition: Composition,
        mimeType: String,
        config: ExportConfig,
        outputFile: File,
        onProgress: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit,
        markCompleteOnFinish: Boolean = true
    ) {
        withContext(Dispatchers.Main) {
            // Cancelled before the transformer was built: starting it anyway
            // would run a detached full encode whose state-guarded listener
            // never fires — leaking scratch files and burning CPU/battery
            // until the encode finishes on its own.
            if (_exportState.value != ExportState.EXPORTING) {
                throw CancellationException("Export cancelled before encoding started")
            }
            var terminalReached = false
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(mimeType)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder()
                                .setBitrate(config.videoBitrate)
                                .build()
                        )
                        .setRequestedAudioEncoderSettings(
                            AudioEncoderSettings.Builder()
                                .setBitrate(config.audioBitrate)
                                .build()
                        )
                        .build()
                )
                .build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    // Guard against callbacks arriving after cancellation or timeout
                    if (_exportState.value != ExportState.EXPORTING) return
                    // Defensive: a 0-byte file means encoding silently produced nothing usable
                    // (can happen on certain hardware-encoder edge cases when input is malformed).
                    // Reporting COMPLETE for a 0-byte file would let the user share / save an
                    // unplayable artifact and trust that it succeeded. Surface as ERROR instead.
                    if (!outputFile.exists() || outputFile.length() <= 0L) {
                        Log.e(TAG, "Transformer reported COMPLETE but output file is empty: ${outputFile.absolutePath}")
                        _exportErrorMessage.value = "Export produced an empty file"
                        _exportState.value = ExportState.ERROR
                        _exportProgress.value = 0f
                        activeExportOutputFile = null
                        runCatching { outputFile.delete() }
                        onError(IllegalStateException("Empty output file"))
                        return
                    }
                    val verification = ExportOutputVerifier.verify(
                        outputFile = outputFile,
                        expectVideo = !config.exportAudioOnly && !config.exportStemsOnly
                    )
                    if (!verification.valid) {
                        Log.e(TAG, "Post-export verification failed: ${verification.reason}")
                        _exportErrorMessage.value = verification.reason ?: "Export verification failed"
                        _exportState.value = ExportState.ERROR
                        _exportProgress.value = 0f
                        activeExportOutputFile = null
                        runCatching { outputFile.delete() }
                        onError(IllegalStateException(verification.reason ?: "Export verification failed"))
                        return
                    }
                    terminalReached = true
                    if (markCompleteOnFinish) {
                        _exportState.value = ExportState.COMPLETE
                        _exportProgress.value = 1f
                        activeExportOutputFile = null
                    }
                    onComplete()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    // Guard against callbacks arriving after cancellation or timeout
                    if (_exportState.value != ExportState.EXPORTING) return
                    Log.e(TAG, "Export failed", exportException)
                    _exportErrorMessage.value = exportException.message ?: "Export encoding failed"
                    _exportState.value = ExportState.ERROR
                    _exportProgress.value = 0f
                    activeExportOutputFile = null
                    outputFile.delete()
                    terminalReached = true
                    onError(exportException)
                }
            }

            transformer.addListener(listener)
            activeTransformer = transformer
            transformer.start(composition, outputFile.absolutePath)

            val holder = ProgressHolder()
            var pollCount = 0
            val maxPolls = 2400 // 10 minutes at 250ms intervals
            while (_exportState.value == ExportState.EXPORTING && !terminalReached && pollCount++ < maxPolls) {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _exportProgress.value = holder.progress / 100f
                    onProgress(holder.progress / 100f)
                }
                delay(250)
            }
            if (pollCount >= maxPolls && _exportState.value == ExportState.EXPORTING && !terminalReached) {
                Log.w(TAG, "Export progress polling timeout after 10 minutes")
                transformer.cancel()
                _exportErrorMessage.value = "Export timed out after 10 minutes"
                _exportState.value = ExportState.ERROR
                _exportProgress.value = 0f
                activeExportOutputFile = null
                outputFile.delete()
                terminalReached = true
                onError(Exception("Export timed out"))
            }
            if (_exportState.value == ExportState.ERROR && !terminalReached) {
                val message = _exportErrorMessage.value ?: "Export failed"
                outputFile.delete()
                activeExportOutputFile = null
                terminalReached = true
                onError(Exception(message))
            }
            if (_exportState.value == ExportState.CANCELLED && !terminalReached) {
                // transformer.cancel() (already invoked by cancelExport()) fires
                // no listener callback, so neither onComplete nor onError runs.
                // Signal the caller so per-export scratch files (reversed-clip
                // pre-renders, mixed-run segments) still get cleaned up.
                activeTransformer = null
                throw CancellationException("Export cancelled")
            }
            activeTransformer = null
            // Ensure the file-handle mirror is always nulled when the transformer
            // reference is cleared, regardless of which branch above set the
            // terminal state. Previously the only nulls lived inside the listener
            // callbacks, so an early-return path (e.g. timeout where the listener
            // fires late or not at all) would leave `activeExportOutputFile`
            // pointing at a deleted file — a subsequent `cancelExport()` would
            // then try to delete that stale path and log an IO error.
            activeExportOutputFile = null
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun cancelExport() {
        // Synchronize to match the check-and-set in export(). Without this, cancelExport()
        // could read activeExportOutputFile as null (stale) in the narrow window after
        // _exportState was set to EXPORTING but before activeExportOutputFile was assigned —
        // both happen inside the same synchronized block in export(), but non-synchronized
        // reads have no formal happens-before guarantee for the non-volatile field.
        synchronized(this) {
            if (_exportState.value != ExportState.EXPORTING) return
            Log.d(TAG, "Cancelling export")
            _exportState.value = ExportState.CANCELLED
            activeTransformer?.cancel()
            activeTransformer = null
            activeExportOutputFile?.delete()
            activeExportOutputFile = null
        }
        _exportProgress.value = 0f
    }

    fun failExportDueToForegroundServiceTimeout(message: String): Boolean {
        synchronized(this) {
            if (_exportState.value != ExportState.EXPORTING) return false
            Log.w(TAG, "Failing export after foreground service media-processing timeout")
            _exportErrorMessage.value = message
            _exportState.value = ExportState.ERROR
            activeTransformer?.cancel()
            activeTransformer = null
            activeExportOutputFile?.delete()
            activeExportOutputFile = null
        }
        _exportProgress.value = 0f
        return true
    }

    // --- Preview effects & speed ---

    /**
     * Apply visual effects to ExoPlayer preview for the given clip.
     * Builds RgbMatrix/GlEffect effects from the clip's enabled effects, opacity, and transforms.
     * Includes transition-in for this clip and transition-out if the next clip has a transition.
     * Does NOT include speed (handled via PlaybackParameters), text overlays, Presentation, or FrameDropEffect.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun applyPreviewEffects(
        clip: Clip?,
        trackedObjects: List<TrackedObject> = previewTrackedObjects
    ) {
        previewTrackedObjects = trackedObjects
        val p = player ?: return
        if (clip == null) {
            p.setVideoEffects(emptyList())
            return
        }
        val nextClipTransition = nextPreviewTransitionForClip(clip)

        val effects = buildPreviewEffectsForClip(clip, nextClipTransition, trackedObjects)
        try {
            p.setVideoEffects(effects)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply preview effects", e)
        }
    }

    // v3.69 color-blind preview — a single-mode post-effect appended to every
    // clip's preview chain. Never touches the export path.
    @Volatile
    private var colorBlindMode: ColorBlindPreviewEngine.Mode = ColorBlindPreviewEngine.Mode.OFF

    fun setColorBlindMode(mode: ColorBlindPreviewEngine.Mode) {
        if (mode == colorBlindMode) return
        colorBlindMode = mode
        // Re-apply effects so the preview updates without the user having to
        // scrub. We target the currently visible clip; if there isn't one
        // (e.g. empty project), the next applyPreviewEffects() call will pick
        // up the new mode.
        applyEffectsForCurrentClip()
    }

    /**
     * Apply effects for the currently playing clip during playback.
     * Called automatically by the media item transition listener.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun applyEffectsForCurrentClip() {
        val p = player ?: return
        val index = p.currentMediaItemIndex
        if (index < 0 || index >= previewSegments.size) return
        val clip = previewSegments[index].clip
        if (clip == null) {
            p.setVideoEffects(emptyList())
            setPreviewSpeed(1f)
            return
        }
        val nextClipTransition = nextPreviewTransitionForClip(clip)

        val effects = buildPreviewEffectsForClip(clip, nextClipTransition, previewTrackedObjects)
        try {
            p.setVideoEffects(effects)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply effects for clip $index", e)
        }
        syncPreviewSpeedForCurrentPosition()
    }

    /**
     * Build the complete effect chain for a clip preview, including:
     * - User effects (filters, color grading, blend modes)
     * - Transition-in (if this clip has a transition)
     * - Transition-out (if the next clip has a transition)
     * - Opacity and transform
     */
    @UnstableApi
    private fun buildPreviewEffectsForClip(
        clip: Clip,
        nextClipTransition: Transition?,
        trackedObjects: List<TrackedObject>
    ): List<androidx.media3.common.Effect> = buildList {
        val clipTrackedObjects = trackedObjects.filter { it.sourceClipId == clip.id && it.isEnabled }
        // User effects (skip BG_REMOVAL in preview — per-frame segmentation is too slow for realtime)
        for (effect in clip.effects.filter { it.enabled && it.type != EffectType.BG_REMOVAL }) {
            EffectBuilder.buildVideoEffect(
                effect = effect,
                segmentationEngine = segmentationEngine,
                trackedObjects = clipTrackedObjects,
                sourceTimeOffsetMs = clip.trimStartMs
            )?.let { add(it) }
        }
        // Color grading (lift/gamma/gain + HSL + LUT)
        addColorGradingEffects(clip)
        // Blend mode
        if (clip.blendMode != com.novacut.editor.model.BlendMode.NORMAL) {
            add(EffectShaders.blendMode(clip.blendMode, clip.opacity))
        }
        clip.headTransition?.let { add(EffectBuilder.buildTransitionEffect(it)) }
        nextClipTransition?.let {
            add(EffectBuilder.buildTransitionOutEffect(it, clip.durationMs))
        }
        // Opacity + transform (keyframe-animated or static)
        addOpacityAndTransformEffects(clip)
        // Color-blind preview simulation — applied last so the user sees the
        // final composited frame under the simulated CVD. Only added when
        // the mode is non-OFF so OFF has zero overhead.
        ColorBlindGlEffect.create(colorBlindMode)?.let { add(it) }
    }

    /**
     * Set ExoPlayer playback speed for preview. Does not affect export.
     */
    fun setPreviewVolume(volume: Float) {
        try {
            player?.volume = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 1f
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set preview volume", e)
        }
    }

    fun setPreviewSpeed(speed: Float) {
        try {
            val safeSpeed = if (speed.isFinite() && speed > 0f) speed.coerceIn(0.1f, 100f) else 1f
            player?.playbackParameters = androidx.media3.common.PlaybackParameters(safeSpeed)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set preview speed", e)
        }
    }

    /** Keep speed-ramp preview playback aligned with the current source frame. */
    fun syncPreviewSpeedForCurrentPosition() {
        val p = player ?: return
        val clip = previewSegments.getOrNull(p.currentMediaItemIndex)?.clip
        val speed = clip?.let { previewSpeedForMediaPosition(it, p.currentPosition) } ?: 1f
        setPreviewSpeed(speed)
    }

    /**
     * Get the currently playing clip index in the playlist.
     */
    fun getCurrentClipIndex(): Int {
        return player?.currentMediaItemIndex ?: 0
    }

    private fun nextPreviewTransitionForClip(clip: Clip): Transition? {
        if (clip.tailTransition != null) return clip.tailTransition
        val clipIndex = videoClips.indexOfFirst { it.id == clip.id }
        if (clipIndex < 0) return null
        val nextClip = videoClips.getOrNull(clipIndex + 1) ?: return null
        return if (nextClip.timelineStartMs <= clip.timelineEndMs) {
            nextClip.headTransition
        } else {
            null
        }
    }

    /**
     * JPEG freeze-frame export path.
     *
     * Current output is SDR JPEG, so MediaMetadataRetriever is still adequate.
     * Future HDR still export should use [FrameExtractionPolicy] and Media3's
     * `androidx.media3.inspector.frame.FrameExtractor`.
     */
    fun extractFrameToFile(uri: Uri, timeMs: Long): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(
                timeMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            val outputFiles = createFreezeFrameOutputFiles(context)
            try {
                outputFiles.partialFile.outputStream().use { out ->
                    if (!frame.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                        throw IllegalStateException("Freeze frame encoder returned no data")
                    }
                }
                finalizeFrameOutputFile(outputFiles.partialFile, outputFiles.outputFile)
                    ?: throw IllegalStateException("Freeze frame output was empty")
            } catch (e: Exception) {
                cleanupFrameOutputFiles(outputFiles.partialFile, outputFiles.outputFile)
                throw e
            } finally {
                frame.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame extraction failed", e)
            null
        } finally {
            retriever.release()
        }
    }

    fun clearThumbnailCache() {
        thumbnailCache.evictAll()
    }

    fun resetExportState() {
        _exportState.value = ExportState.IDLE
        _exportProgress.value = 0f
    }

    fun release() {
        removePlayerListener()
        transitionListener?.let { player?.removeListener(it) }
        transitionListener = null
        player?.release()
        player = null
        videoClips = emptyList()
        previewSegments = emptyList()
        clearThumbnailCache()
    }

}

enum class ExportState { IDLE, EXPORTING, COMPLETE, ERROR, CANCELLED }
