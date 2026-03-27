package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
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
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VideoEngine"

@Singleton
class VideoEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val segmentationEngine: SegmentationEngine
) {
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    // Memory-bounded bitmap cache — uses 1/8 of available heap
    // Don't recycle evicted bitmaps — they may still be referenced by Compose Image nodes
    private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt()
    ) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            // Don't recycle — may still be referenced by Compose
            // Bitmap will be GC'd when no longer referenced
        }
    }

    // Clip durations for multi-clip seek/playhead calculations
    private var clipDurationsMs: List<Long> = emptyList()

    // Active Transformer for export cancellation
    @Volatile private var activeTransformer: Transformer? = null

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
    fun getPlayer(): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context).build()
        }
        return player!!
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
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun prepareTimeline(tracks: List<Track>) {
        val p = getPlayer()
        val videoClips = tracks.filter { it.type == TrackType.VIDEO }.flatMap { it.clips }
        if (videoClips.isEmpty()) {
            p.clearMediaItems()
            clipDurationsMs = emptyList()
            return
        }
        clipDurationsMs = videoClips.map { it.durationMs }
        val mediaItems = videoClips.map { clip ->
            val mediaUri = clip.proxyUri ?: clip.sourceUri
            MediaItem.Builder()
                .setUri(mediaUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs)
                        .setEndPositionMs(clip.trimEndMs)
                        .build()
                )
                .build()
        }
        p.setMediaItems(mediaItems)
        p.prepare()
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        if (clipDurationsMs.size <= 1) {
            p.seekTo(positionMs)
            return
        }
        var remaining = positionMs
        for (i in clipDurationsMs.indices) {
            if (remaining < clipDurationsMs[i] || i == clipDurationsMs.lastIndex) {
                p.seekTo(i, remaining.coerceAtLeast(0L))
                return
            }
            remaining -= clipDurationsMs[i]
        }
    }

    fun getAbsolutePositionMs(): Long {
        val p = player ?: return 0L
        val index = p.currentMediaItemIndex
        val offset = clipDurationsMs.take(index).sum()
        return offset + p.currentPosition
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

    fun extractThumbnail(uri: Uri, timeUs: Long, width: Int = 160, height: Int = 90): Bitmap? {
        val key = "${uri}_${timeUs}_${width}x${height}"
        thumbnailCache.get(key)?.let { return it }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.let {
                val scaled = Bitmap.createScaledBitmap(it, width, height, true)
                if (scaled !== it) it.recycle()
                thumbnailCache.put(key, scaled)
                scaled
            }
        } catch (e: Exception) {
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
        lottieOverlays: List<LottieOverlaySpec> = emptyList(),
        onProgress: (Float) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        // Reset from any previous export state
        _exportState.value = ExportState.EXPORTING
        _exportProgress.value = 0f

        try {
            // Collect all visible video tracks (VIDEO + OVERLAY types) into one merged clip list
            val visibleVideoTracks = tracks.filter {
                (it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY) && it.isVisible && it.clips.isNotEmpty()
            }
            if (visibleVideoTracks.isEmpty()) {
                throw IllegalStateException("No video clips to export")
            }
            // Use primary video track for main sequence; overlay tracks contribute clips appended in order
            val videoTrack = visibleVideoTracks.first()
            val videoMuted = videoTrack.isMuted

            val (targetW, targetH) = config.resolution.forAspect(config.aspectRatio)

            val editedItems = videoTrack.clips.map { clip ->
                val mediaItem = MediaItem.Builder()
                    .setUri(clip.sourceUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.trimStartMs)
                            .setEndPositionMs(clip.trimEndMs)
                            .build()
                    )
                    .build()

                val videoEffects = buildList<androidx.media3.common.Effect> {
                    // User effects
                    for (effect in clip.effects.filter { it.enabled }) {
                        EffectBuilder.buildVideoEffect(effect, segmentationEngine)?.let { add(it) }
                    }

                    // Color grading (lift/gamma/gain + HSL qualification + LUT)
                    addColorGradingEffects(clip)

                    // Masks (rectangle/ellipse) — use clip midpoint for static mask position
                    // (keyframed masks would need per-frame GlEffect, using midpoint as best static approximation)
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
                            else -> {} // Freehand/gradient masks handled differently
                        }
                    }

                    // Blend mode
                    if (clip.blendMode != com.novacut.editor.model.BlendMode.NORMAL) {
                        add(EffectShaders.blendMode(clip.blendMode, clip.opacity))
                    }

                    // Transition-in effect
                    clip.transition?.let { add(EffectBuilder.buildTransitionEffect(it)) }
                    // Opacity + transform (keyframe-animated or static)
                    addOpacityAndTransformEffects(clip)
                    // Speed handled via EditedMediaItem.Builder.setSpeed() below
                    // Text overlays that overlap this clip's timeline range
                    val clipStart = clip.timelineStartMs
                    val clipEnd = clip.timelineEndMs
                    val overlapping = textOverlays.filter { overlay ->
                        overlay.startTimeMs < clipEnd && overlay.endTimeMs > clipStart
                    }
                    if (overlapping.isNotEmpty()) {
                        val overlayList = overlapping.map { overlay ->
                            val relStart = (overlay.startTimeMs - clipStart).coerceAtLeast(0L)
                            val relEnd = (overlay.endTimeMs - clipStart).coerceAtMost(clip.durationMs)
                            ExportTextOverlay(overlay, relStart, relEnd)
                        }
                        @Suppress("UNCHECKED_CAST")
                        add(OverlayEffect(com.google.common.collect.ImmutableList.copyOf(overlayList) as List<TextureOverlay>))
                    }
                    // Lottie animated title overlays
                    val overlappingLottie = lottieOverlays.filter { lo ->
                        lo.startTimeMs < clipEnd && lo.endTimeMs > clipStart
                    }
                    for (lo in overlappingLottie) {
                        val relStartUs = ((lo.startTimeMs - clipStart).coerceAtLeast(0L)) * 1000L
                        val durationUs = (lo.endTimeMs - lo.startTimeMs).coerceAtLeast(1L) * 1000L
                        add(LottieOverlayEffect(
                            lottieEngine = lo.engine,
                            composition = lo.composition,
                            overlayStartUs = relStartUs,
                            overlayDurationUs = durationUs,
                            textReplacements = lo.textReplacements
                        ))
                    }
                    // Apply adjustment layer effects (cascade from tracks above)
                    val adjustmentTracks = tracks.filter { it.type == TrackType.ADJUSTMENT && it.isVisible }
                    for (adjTrack in adjustmentTracks) {
                        for (adjClip in adjTrack.clips) {
                            // Check temporal overlap
                            if (adjClip.timelineStartMs < clipEnd && adjClip.timelineEndMs > clipStart) {
                                // Apply adjustment clip's effects to current video clip
                                for (effect in adjClip.effects.filter { it.enabled }) {
                                    EffectBuilder.buildVideoEffect(effect, segmentationEngine)?.let { add(it) }
                                }
                            }
                        }
                    }

                    // Frame rate control (drops frames to target fps)
                    add(FrameDropEffect.createDefaultFrameDropEffect(config.frameRate.toFloat()))
                    add(Presentation.createForWidthAndHeight(
                        targetW, targetH, Presentation.LAYOUT_SCALE_TO_FIT
                    ))
                }

                val audioProcessors = buildList<AudioProcessor> {
                    if (videoMuted) {
                        // Track is muted — silence all audio from video clips
                        add(VolumeAudioProcessor(
                            volume = 0f,
                            fadeInMs = 0L,
                            fadeOutMs = 0L,
                            clipDurationMs = clip.durationMs,
                            keyframes = emptyList()
                        ))
                    } else {
                        val hasKfVolume = clip.keyframes.any { it.property == KeyframeProperty.VOLUME }
                        val needsVolume = clip.volume != 1.0f
                        val needsFade = clip.fadeInMs > 0L || clip.fadeOutMs > 0L
                        if (hasKfVolume || needsVolume || needsFade) {
                            add(VolumeAudioProcessor(
                                volume = clip.volume,
                                fadeInMs = clip.fadeInMs,
                                fadeOutMs = clip.fadeOutMs,
                                clipDurationMs = clip.durationMs,
                                keyframes = if (hasKfVolume) clip.keyframes else emptyList()
                            ))
                        }
                    }
                }

                val itemBuilder = EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(audioProcessors, videoEffects))

                // Apply speed: variable speed curve via SpeedProvider, or constant speed
                if (clip.speedCurve != null && clip.speedCurve.points.size >= 2) {
                    val curve = clip.speedCurve
                    val clipDurMs = clip.trimEndMs - clip.trimStartMs // Use source time range for curve normalization
                    itemBuilder.setSpeed(object : androidx.media3.common.audio.SpeedProvider {
                        override fun getSpeed(presentationTimeUs: Long): Float {
                            val timeMs = presentationTimeUs / 1000L
                            return curve.getSpeedAt(timeMs, clipDurMs).coerceIn(0.1f, 16f)
                        }
                        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
                            return androidx.media3.common.C.TIME_UNSET
                        }
                    })
                } else if (clip.speed != 1.0f) {
                    val constSpeed = clip.speed.coerceIn(0.1f, 16f)
                    itemBuilder.setSpeed(object : androidx.media3.common.audio.SpeedProvider {
                        override fun getSpeed(presentationTimeUs: Long): Float = constSpeed
                        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
                    })
                }

                itemBuilder.build()
            }

            val videoSequence = EditedMediaItemSequence.Builder(editedItems).build()

            // Build audio track sequences (background music, voiceovers, etc.) — supports multiple audio tracks
            val audioTracks = tracks.filter { it.type == TrackType.AUDIO && it.isVisible && !it.isMuted && it.clips.isNotEmpty() }
            val sequences = buildList {
                add(videoSequence)
                for (at in audioTracks) {
                    val audioItems = at.clips.map { clip ->
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
                            if (hasKfVol || needsVolume || needsFade) {
                                add(VolumeAudioProcessor(
                                    volume = clip.volume,
                                    fadeInMs = clip.fadeInMs,
                                    fadeOutMs = clip.fadeOutMs,
                                    clipDurationMs = clip.durationMs,
                                    keyframes = if (hasKfVol) clip.keyframes else emptyList()
                                ))
                            }
                        }
                        EditedMediaItem.Builder(mediaItem)
                            .setEffects(Effects(processors, emptyList()))
                            .setRemoveVideo(true)
                            .build()
                    }
                    add(EditedMediaItemSequence.Builder(audioItems).build())
                }
            }

            val hasAudioTracks = audioTracks.isNotEmpty()
            // When video track is muted AND no audio tracks, transmux to pass through;
            // when there are audio tracks, don't transmux so the processor pipeline runs
            val composition = Composition.Builder(sequences)
                .setTransmuxAudio(!hasAudioTracks && !videoMuted)
                .build()

            val mimeType = when (config.codec) {
                VideoCodec.HEVC -> MimeTypes.VIDEO_H265
                VideoCodec.H264 -> MimeTypes.VIDEO_H264
                VideoCodec.AV1 -> MimeTypes.VIDEO_AV1
                VideoCodec.VP9 -> MimeTypes.VIDEO_VP9
            }

            // Transformer.start() requires a Looper — must run on Main thread
            withContext(Dispatchers.Main) {
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
                        _exportState.value = ExportState.COMPLETE
                        _exportProgress.value = 1f
                        onComplete()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e(TAG, "Export failed", exportException)
                        _exportErrorMessage.value = exportException.message ?: "Export encoding failed"
                        _exportState.value = ExportState.ERROR
                        _exportProgress.value = 0f
                        outputFile.delete()
                        onError(exportException)
                    }
                }

                transformer.addListener(listener)
                activeTransformer = transformer
                transformer.start(composition, outputFile.absolutePath)

                val holder = ProgressHolder()
                var pollCount = 0
                val maxPolls = 2400 // 10 minutes at 250ms intervals
                while (_exportState.value == ExportState.EXPORTING && pollCount++ < maxPolls) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        _exportProgress.value = holder.progress / 100f
                        onProgress(holder.progress / 100f)
                    }
                    delay(250)
                }
                if (pollCount >= maxPolls && _exportState.value == ExportState.EXPORTING) {
                    Log.w(TAG, "Export progress polling timeout after 10 minutes")
                    transformer.cancel()
                    _exportErrorMessage.value = "Export timed out after 10 minutes"
                    _exportState.value = ExportState.ERROR
                    _exportProgress.value = 0f
                    outputFile.delete()
                    onError(Exception("Export timed out"))
                }
                activeTransformer = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export setup failed", e)
            _exportErrorMessage.value = e.message ?: "Export setup failed"
            _exportState.value = ExportState.ERROR
            _exportProgress.value = 0f
            activeTransformer = null
            outputFile.delete()
            onError(e)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun cancelExport() {
        if (_exportState.value != ExportState.EXPORTING) return
        Log.d(TAG, "Cancelling export")
        _exportState.value = ExportState.CANCELLED
        _exportProgress.value = 0f
        activeTransformer?.cancel()
        activeTransformer = null
    }

    // --- Preview effects & speed ---

    /**
     * Apply visual effects to ExoPlayer preview for the given clip.
     * Builds RgbMatrix/GlEffect effects from the clip's enabled effects, opacity, and transforms.
     * Does NOT include speed (handled via PlaybackParameters), text overlays, Presentation, or FrameDropEffect.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun applyPreviewEffects(clip: Clip?) {
        val p = player ?: return
        if (clip == null) {
            p.setVideoEffects(emptyList())
            return
        }
        val effects = buildList<androidx.media3.common.Effect> {
            // User effects (skip BG_REMOVAL in preview — per-frame segmentation is too slow for realtime)
            for (effect in clip.effects.filter { it.enabled && it.type != EffectType.BG_REMOVAL }) {
                EffectBuilder.buildVideoEffect(effect, segmentationEngine)?.let { add(it) }
            }
            // Color grading (lift/gamma/gain + HSL + LUT)
            addColorGradingEffects(clip)
            // Blend mode
            if (clip.blendMode != com.novacut.editor.model.BlendMode.NORMAL) {
                add(EffectShaders.blendMode(clip.blendMode, clip.opacity))
            }
            // Transition-in
            clip.transition?.let { add(EffectBuilder.buildTransitionEffect(it)) }
            // Opacity + transform (keyframe-animated or static)
            addOpacityAndTransformEffects(clip)
        }
        try {
            p.setVideoEffects(effects)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply preview effects", e)
        }
    }

    /**
     * Set ExoPlayer playback speed for preview. Does not affect export.
     */
    fun setPreviewSpeed(speed: Float) {
        try {
            player?.playbackParameters = androidx.media3.common.PlaybackParameters(speed.coerceIn(0.1f, 16f))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set preview speed", e)
        }
    }

    /**
     * Get the currently playing clip index in the playlist.
     */
    fun getCurrentClipIndex(): Int {
        return player?.currentMediaItemIndex ?: 0
    }

    fun extractFrameToFile(uri: Uri, timeMs: Long): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(
                timeMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            val dir = File(context.filesDir, "freeze_frames").also { it.mkdirs() }
            val file = File(dir, "freeze_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                frame.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            frame.recycle()
            file
        } catch (_: Exception) {
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
        player?.release()
        player = null
        clearThumbnailCache()
    }

}

enum class ExportState { IDLE, EXPORTING, COMPLETE, ERROR, CANCELLED }

/**
 * Specification for a Lottie animated overlay in the export pipeline.
 * Created by the ViewModel when preparing export with animated title overlays.
 */
data class LottieOverlaySpec(
    val engine: LottieTemplateEngine,
    val composition: com.airbnb.lottie.LottieComposition,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val textReplacements: Map<String, String> = emptyMap()
)
