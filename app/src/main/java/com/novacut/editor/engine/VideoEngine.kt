package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.Layout
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
                        buildVideoEffect(effect)?.let { add(it) }
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
                    clip.transition?.let { add(buildTransitionEffect(it)) }
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
                                    buildVideoEffect(effect)?.let { add(it) }
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
                buildVideoEffect(effect)?.let { add(it) }
            }
            // Color grading (lift/gamma/gain + HSL + LUT)
            addColorGradingEffects(clip)
            // Blend mode
            if (clip.blendMode != com.novacut.editor.model.BlendMode.NORMAL) {
                add(EffectShaders.blendMode(clip.blendMode, clip.opacity))
            }
            // Transition-in
            clip.transition?.let { add(buildTransitionEffect(it)) }
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

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildVideoEffect(effect: Effect): androidx.media3.common.Effect? {
        return when (effect.type) {
            EffectType.BRIGHTNESS -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                RgbMatrix { _, _ ->
                    val b = value
                    floatArrayOf(
                        1f, 0f, 0f, b,
                        0f, 1f, 0f, b,
                        0f, 0f, 1f, b,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.CONTRAST -> {
                val value = (effect.params["value"] ?: 1f).coerceIn(0f, 2f)
                Contrast(value - 1f)
            }
            EffectType.SATURATION -> {
                val value = (effect.params["value"] ?: 1f).coerceIn(0f, 3f)
                RgbMatrix { presentationTimeUs, useHdr ->
                    val s = value
                    val sr = (1 - s) * 0.2126f
                    val sg = (1 - s) * 0.7152f
                    val sb = (1 - s) * 0.0722f
                    floatArrayOf(
                        sr + s, sg, sb, 0f,
                        sr, sg + s, sb, 0f,
                        sr, sg, sb + s, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.GRAYSCALE -> {
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        0.2126f, 0.7152f, 0.0722f, 0f,
                        0.2126f, 0.7152f, 0.0722f, 0f,
                        0.2126f, 0.7152f, 0.0722f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.SEPIA -> {
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f,
                        0.349f, 0.686f, 0.168f, 0f,
                        0.272f, 0.534f, 0.131f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.INVERT -> {
                // Row-major 4x4: out.R = row0 dot [R,G,B,A]
                // Invert: out.rgb = 1 - in.rgb, using alpha (=1) as offset
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        -1f, 0f, 0f, 1f,
                        0f, -1f, 0f, 1f,
                        0f, 0f, -1f, 1f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.TEMPERATURE -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-5f, 5f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f + value * 0.1f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f,
                        0f, 0f, 1f - value * 0.1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.TINT -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f, 0f, 0f, 0f,
                        0f, 1f + value * 0.1f, 0f, 0f,
                        0f, 0f, 1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.EXPOSURE -> {
                // Approximate exposure: multiply all channels by 2^value
                val value = (effect.params["value"] ?: 0f).coerceIn(-2f, 2f)
                val mul = Math.pow(2.0, value.toDouble()).toFloat()
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        mul, 0f, 0f, 0f,
                        0f, mul, 0f, 0f,
                        0f, 0f, mul, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.GAMMA -> {
                // Gamma approximation: use linear scale (true gamma needs pow per-pixel)
                val value = (effect.params["value"] ?: 1f).coerceIn(0.2f, 5f)
                val inv = 1f / value
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        inv, 0f, 0f, 0f,
                        0f, inv, 0f, 0f,
                        0f, 0f, inv, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.HIGHLIGHTS -> {
                // Boost/reduce bright areas: scale toward white
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                val scale = 1f + value * 0.3f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        scale, 0f, 0f, 0f,
                        0f, scale, 0f, 0f,
                        0f, 0f, scale, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.SHADOWS -> {
                // Lift/crush shadow areas: offset toward black
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                val offset = value * 0.15f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f, 0f, 0f, offset,
                        0f, 1f, 0f, offset,
                        0f, 0f, 1f, offset,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.VIBRANCE -> {
                // Selective saturation: boost less-saturated colors more
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                val s = 1f + value * 0.5f
                val sr = (1 - s) * 0.2126f
                val sg = (1 - s) * 0.7152f
                val sb = (1 - s) * 0.0722f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        sr + s, sg, sb, 0f,
                        sr, sg + s, sb, 0f,
                        sr, sg, sb + s, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.POSTERIZE -> {
                // Approximate posterize by reducing contrast then boosting
                val levels = (effect.params["levels"] ?: 6f).coerceIn(2f, 16f)
                val scale = levels / 8f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        scale, 0f, 0f, (1f - scale) * 0.5f,
                        0f, scale, 0f, (1f - scale) * 0.5f,
                        0f, 0f, scale, (1f - scale) * 0.5f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.COOL_TONE -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f - intensity * 0.1f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f,
                        0f, 0f, 1f + intensity * 0.15f, intensity * 0.02f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.WARM_TONE -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f + intensity * 0.15f, 0f, 0f, intensity * 0.02f,
                        0f, 1f + intensity * 0.05f, 0f, 0f,
                        0f, 0f, 1f - intensity * 0.1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.CYBERPUNK -> {
                // Teal shadows + magenta highlights
                val intensity = (effect.params["intensity"] ?: 0.7f).coerceIn(0f, 1f)
                val s = 1f + intensity * 0.3f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        s, 0f, 0f, intensity * 0.05f,
                        0f, 1f - intensity * 0.1f, 0f, -intensity * 0.02f,
                        0f, 0f, s, intensity * 0.08f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.NOIR -> {
                // High contrast desaturated with slight warm tint
                val intensity = (effect.params["intensity"] ?: 0.7f).coerceIn(0f, 1f)
                val gray = intensity
                val tint = intensity * 0.03f
                val lr = 0.2126f * gray + (1f - gray)
                val lg = 0.7152f * gray
                val lb = 0.0722f * gray
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        lr, lg, lb, tint,
                        0.2126f * gray, 0.7152f * gray + (1f - gray), 0.0722f * gray, 0f,
                        0.2126f * gray, 0.7152f * gray, 0.0722f * gray + (1f - gray), -tint,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.VINTAGE -> {
                // Faded warm look: reduced contrast + sepia blend
                val intensity = (effect.params["intensity"] ?: 0.7f).coerceIn(0f, 1f)
                val i = intensity
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f - i * 0.3f + i * 0.393f * 0.5f, i * 0.769f * 0.5f, i * 0.189f * 0.5f, i * 0.03f,
                        i * 0.349f * 0.5f, 1f - i * 0.2f + i * 0.686f * 0.5f, i * 0.168f * 0.5f, i * 0.01f,
                        i * 0.272f * 0.5f, i * 0.534f * 0.5f, 1f - i * 0.4f + i * 0.131f * 0.5f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.MIRROR -> {
                ScaleAndRotateTransformation.Builder()
                    .setScale(-1f, 1f)
                    .build()
            }
            EffectType.VIGNETTE -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                val radius = (effect.params["radius"] ?: 0.7f).coerceIn(0f, 1f)
                EffectShaders.vignette(intensity, radius)
            }
            EffectType.SHARPEN -> {
                val strength = (effect.params["strength"] ?: 0.5f).coerceIn(0f, 3f)
                EffectShaders.sharpen(strength)
            }
            EffectType.FILM_GRAIN -> {
                val intensity = (effect.params["intensity"] ?: 0.1f).coerceIn(0f, 1f)
                EffectShaders.filmGrain(intensity)
            }
            EffectType.GAUSSIAN_BLUR -> {
                val radius = (effect.params["radius"] ?: 5f).coerceIn(1f, 25f)
                EffectShaders.gaussianBlur(radius)
            }
            EffectType.RADIAL_BLUR -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                EffectShaders.radialBlur(intensity)
            }
            EffectType.MOTION_BLUR -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                val angle = (effect.params["angle"] ?: 0f).coerceIn(0f, 360f)
                EffectShaders.motionBlur(intensity, angle)
            }
            EffectType.TILT_SHIFT -> {
                val focusY = (effect.params["focusY"] ?: 0.5f).coerceIn(0f, 1f)
                val width = (effect.params["width"] ?: 0.1f).coerceIn(0.01f, 0.5f)
                val blur = (effect.params["blur"] ?: 0.01f).coerceIn(0f, 1f)
                EffectShaders.tiltShift(focusY, width, blur)
            }
            EffectType.MOSAIC -> {
                val size = (effect.params["size"] ?: 15f).coerceIn(2f, 50f)
                EffectShaders.mosaic(size)
            }
            EffectType.FISHEYE -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                EffectShaders.fisheye(intensity)
            }
            EffectType.GLITCH -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                EffectShaders.glitch(intensity)
            }
            EffectType.PIXELATE -> {
                val size = (effect.params["size"] ?: 10f).coerceIn(2f, 50f)
                EffectShaders.pixelate(size)
            }
            EffectType.WAVE -> {
                val amplitude = (effect.params["amplitude"] ?: 0.02f).coerceIn(0f, 0.1f)
                val frequency = (effect.params["frequency"] ?: 10f).coerceIn(1f, 50f)
                EffectShaders.wave(amplitude, frequency)
            }
            EffectType.CHROMATIC_ABERRATION -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 2f)
                EffectShaders.chromaticAberration(intensity)
            }
            EffectType.CHROMA_KEY -> {
                val similarity = (effect.params["similarity"] ?: 0.4f).coerceIn(0f, 1f)
                val smoothness = (effect.params["smoothness"] ?: 0.1f).coerceIn(0f, 0.5f)
                // Default to green screen (0, 1, 0)
                val keyR = (effect.params["keyR"] ?: 0f).coerceIn(0f, 1f)
                val keyG = (effect.params["keyG"] ?: 1f).coerceIn(0f, 1f)
                val keyB = (effect.params["keyB"] ?: 0f).coerceIn(0f, 1f)
                EffectShaders.chromaKey(keyR, keyG, keyB, similarity, smoothness)
            }
            EffectType.BG_REMOVAL -> {
                val threshold = (effect.params["threshold"] ?: 0.5f).coerceIn(0.1f, 0.9f)
                if (segmentationEngine.isReady()) {
                    segmentationEngine.createExportEffect(threshold)
                } else null
            }
            EffectType.VHS_RETRO -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                EffectShaders.vhsRetro(intensity)
            }
            EffectType.LIGHT_LEAK -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                EffectShaders.lightLeak(intensity)
            }
            // Speed/Reverse handled separately in export pipeline, not as visual effects
            EffectType.SPEED, EffectType.REVERSE -> null
        }
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

    // --- Shared effect builders (used by both export and preview) ---

    /**
     * Build color grading effects (LGG, HSL qualification, LUT) for a clip.
     */
    private fun MutableList<androidx.media3.common.Effect>.addColorGradingEffects(clip: Clip) {
        clip.colorGrade?.let { grade ->
            if (!grade.enabled) return@let
            val hasLGG = grade.liftR != 0f || grade.liftG != 0f || grade.liftB != 0f ||
                grade.gammaR != 1f || grade.gammaG != 1f || grade.gammaB != 1f ||
                grade.gainR != 1f || grade.gainG != 1f || grade.gainB != 1f ||
                grade.offsetR != 0f || grade.offsetG != 0f || grade.offsetB != 0f
            if (hasLGG) {
                add(EffectShaders.colorGrade(
                    grade.liftR, grade.liftG, grade.liftB,
                    grade.gammaR, grade.gammaG, grade.gammaB,
                    grade.gainR, grade.gainG, grade.gainB,
                    grade.offsetR, grade.offsetG, grade.offsetB
                ))
            }
            grade.hslQualifier?.let { hsl ->
                add(EffectShaders.hslQualify(
                    hsl.hueCenter, hsl.hueWidth,
                    hsl.satMin, hsl.satMax,
                    hsl.lumMin, hsl.lumMax,
                    hsl.softness,
                    hsl.adjustHue, hsl.adjustSat, hsl.adjustLum
                ))
            }
            grade.lutPath?.let { path ->
                val lutFile = java.io.File(path)
                if (lutFile.exists()) {
                    val lut = when {
                        path.endsWith(".cube", true) -> LutEngine.parseCube(lutFile)
                        path.endsWith(".3dl", true) -> LutEngine.parse3dl(lutFile)
                        else -> null
                    }
                    lut?.let { add(LutEngine.createLutEffect(it, grade.lutIntensity)) }
                }
            }
        }
    }

    /**
     * Build transition-in effect for a clip.
     */
    private fun buildTransitionEffect(transition: Transition): androidx.media3.common.Effect {
        val durationUs = transition.durationMs * 1000f
        return when (transition.type) {
            TransitionType.DISSOLVE, TransitionType.FADE_BLACK ->
                EffectShaders.transitionFadeIn(durationUs)
            TransitionType.FADE_WHITE ->
                EffectShaders.transitionFadeIn(durationUs, fadeToWhite = true)
            TransitionType.WIPE_LEFT -> EffectShaders.transitionWipe(durationUs, -1f, 0f)
            TransitionType.WIPE_RIGHT -> EffectShaders.transitionWipe(durationUs, 1f, 0f)
            TransitionType.WIPE_UP -> EffectShaders.transitionWipe(durationUs, 0f, 1f)
            TransitionType.WIPE_DOWN -> EffectShaders.transitionWipe(durationUs, 0f, -1f)
            TransitionType.SLIDE_LEFT -> EffectShaders.transitionSlideIn(durationUs, 1f, 0f)
            TransitionType.SLIDE_RIGHT -> EffectShaders.transitionSlideIn(durationUs, -1f, 0f)
            TransitionType.ZOOM_IN -> EffectShaders.transitionZoomIn(durationUs)
            TransitionType.ZOOM_OUT -> EffectShaders.transitionZoomOut(durationUs)
            TransitionType.SPIN -> EffectShaders.transitionSpin(durationUs)
            TransitionType.FLIP -> EffectShaders.transitionFlip(durationUs)
            TransitionType.CUBE -> EffectShaders.transitionCube(durationUs)
            TransitionType.RIPPLE -> EffectShaders.transitionRipple(durationUs)
            TransitionType.PIXELATE -> EffectShaders.transitionPixelate(durationUs)
            TransitionType.DIRECTIONAL_WARP -> EffectShaders.transitionDirectionalWarp(durationUs)
            TransitionType.WIND -> EffectShaders.transitionWind(durationUs)
            TransitionType.MORPH -> EffectShaders.transitionMorph(durationUs)
            TransitionType.GLITCH -> EffectShaders.transitionGlitch(durationUs)
            TransitionType.CIRCLE_OPEN -> EffectShaders.transitionCircleOpen(durationUs)
            TransitionType.CROSS_ZOOM -> EffectShaders.transitionCrossZoom(durationUs)
            TransitionType.DREAMY -> EffectShaders.transitionDreamy(durationUs)
            TransitionType.HEART -> EffectShaders.transitionHeart(durationUs)
            TransitionType.SWIRL -> EffectShaders.transitionSwirl(durationUs)
            TransitionType.DOOR_OPEN -> EffectShaders.transitionDoorOpen(durationUs)
            TransitionType.BURN -> EffectShaders.transitionBurn(durationUs)
            TransitionType.RADIAL_WIPE -> EffectShaders.transitionRadialWipe(durationUs)
            TransitionType.MOSAIC_REVEAL -> EffectShaders.transitionMosaicReveal(durationUs)
            TransitionType.BOUNCE -> EffectShaders.transitionBounce(durationUs)
            TransitionType.LENS_FLARE -> EffectShaders.transitionLensFlare(durationUs)
            TransitionType.PAGE_CURL -> EffectShaders.transitionPageCurl(durationUs)
            TransitionType.CROSS_WARP -> EffectShaders.transitionCrossWarp(durationUs)
            TransitionType.ANGULAR -> EffectShaders.transitionAngular(durationUs)
            TransitionType.KALEIDOSCOPE -> EffectShaders.transitionKaleidoscope(durationUs)
            TransitionType.SQUARES_WIRE -> EffectShaders.transitionSquaresWire(durationUs)
            TransitionType.COLOR_PHASE -> EffectShaders.transitionColorPhase(durationUs)
        }
    }

    /**
     * Build opacity and transform effects (static or keyframe-animated) for a clip.
     */
    private fun MutableList<androidx.media3.common.Effect>.addOpacityAndTransformEffects(clip: Clip) {
        // Opacity
        val hasKeyframeOpacity = clip.keyframes.any { it.property == KeyframeProperty.OPACITY }
        if (hasKeyframeOpacity) {
            add(RgbMatrix { presentationTimeUs, _ ->
                val timeMs = presentationTimeUs / 1000L
                val opacity = KeyframeEngine.getValueAt(
                    clip.keyframes, KeyframeProperty.OPACITY, timeMs
                ) ?: 1f
                floatArrayOf(
                    opacity, 0f, 0f, 0f,
                    0f, opacity, 0f, 0f,
                    0f, 0f, opacity, 0f,
                    0f, 0f, 0f, 1f
                )
            })
        } else if (clip.opacity != 1f) {
            val o = clip.opacity.coerceIn(0f, 1f)
            add(RgbMatrix { _, _ ->
                floatArrayOf(
                    o, 0f, 0f, 0f,
                    0f, o, 0f, 0f,
                    0f, 0f, o, 0f,
                    0f, 0f, 0f, 1f
                )
            })
        }
        // Transform (rotation, scale, position) — keyframe-animated or static
        val hasKfScale = clip.keyframes.any {
            it.property == KeyframeProperty.SCALE_X || it.property == KeyframeProperty.SCALE_Y
        }
        val hasKfRotation = clip.keyframes.any { it.property == KeyframeProperty.ROTATION }
        val hasKfPosition = clip.keyframes.any {
            it.property == KeyframeProperty.POSITION_X || it.property == KeyframeProperty.POSITION_Y
        }
        val needsStaticTransform = clip.rotation != 0f || clip.scaleX != 1f || clip.scaleY != 1f ||
            clip.positionX != 0f || clip.positionY != 0f
        if (hasKfScale || hasKfRotation || hasKfPosition) {
            val kfs = clip.keyframes
            val staticSx = clip.scaleX; val staticSy = clip.scaleY
            val staticRot = clip.rotation
            val staticPx = clip.positionX; val staticPy = clip.positionY
            add(MatrixTransformation { presentationTimeUs ->
                val timeMs = presentationTimeUs / 1000L
                val sx = KeyframeEngine.getValueAt(kfs, KeyframeProperty.SCALE_X, timeMs) ?: staticSx
                val sy = KeyframeEngine.getValueAt(kfs, KeyframeProperty.SCALE_Y, timeMs) ?: staticSy
                val rot = KeyframeEngine.getValueAt(kfs, KeyframeProperty.ROTATION, timeMs) ?: staticRot
                val px = KeyframeEngine.getValueAt(kfs, KeyframeProperty.POSITION_X, timeMs) ?: staticPx
                val py = KeyframeEngine.getValueAt(kfs, KeyframeProperty.POSITION_Y, timeMs) ?: staticPy
                android.graphics.Matrix().apply {
                    postScale(sx, sy)
                    postRotate(rot)
                    postTranslate(px, -py)
                }
            })
        } else if (needsStaticTransform) {
            val m = android.graphics.Matrix().apply {
                postScale(clip.scaleX, clip.scaleY)
                postRotate(clip.rotation)
                postTranslate(clip.positionX, -clip.positionY)
            }
            add(MatrixTransformation { m })
        }
    }
}

enum class ExportState { IDLE, EXPORTING, COMPLETE, ERROR, CANCELLED }

/**
 * Audio processor that applies volume scaling and fade in/out envelope.
 * Operates on 16-bit PCM audio samples.
 */
@UnstableApi
private class VolumeAudioProcessor(
    private val volume: Float,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
    private val clipDurationMs: Long,
    private val keyframes: List<com.novacut.editor.model.Keyframe> = emptyList()
) : BaseAudioProcessor() {

    private var processedFrames: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.sampleRate == 0 ||
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val sampleRate = inputAudioFormat.sampleRate
        val channelCount = inputAudioFormat.channelCount

        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.short
            val frameIndex = processedFrames / channelCount
            val timeMs = frameIndex * 1000L / sampleRate

            // Use keyframe volume if available, otherwise static volume
            var gain = if (keyframes.isNotEmpty()) {
                KeyframeEngine.getValueAt(
                    keyframes, com.novacut.editor.model.KeyframeProperty.VOLUME, timeMs
                ) ?: volume
            } else {
                volume
            }

            // Fade in envelope
            if (fadeInMs > 0 && timeMs < fadeInMs) {
                gain *= timeMs.toFloat() / fadeInMs
            }

            // Fade out envelope
            if (fadeOutMs > 0 && timeMs > clipDurationMs - fadeOutMs) {
                val remaining = (clipDurationMs - timeMs).coerceAtLeast(0L)
                gain *= remaining.toFloat() / fadeOutMs
            }

            val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outputBuffer.putShort(scaled.toShort())
            processedFrames++
        }

        outputBuffer.flip()
    }

    override fun onReset() {
        super.onReset()
        processedFrames = 0L
    }
}

/**
 * Text overlay that renders within a specific time range during export.
 * Converts model TextOverlay properties to Media3 SpannableString styling.
 */
@UnstableApi
private class ExportTextOverlay(
    private val overlay: com.novacut.editor.model.TextOverlay,
    private val relStartMs: Long,
    private val relEndMs: Long
) : androidx.media3.effect.TextOverlay() {

    private val animDurationMs = 500L
    // Current alpha computed per-frame for text color modulation
    private var currentAlpha = 1f

    override fun getText(presentationTimeUs: Long): SpannableString {
        val timeMs = presentationTimeUs / 1000L
        if (timeMs < relStartMs || timeMs > relEndMs) {
            currentAlpha = 0f
            return SpannableString("")
        }

        // Compute animation alpha
        computeAnimationState(timeMs)

        val fullText = overlay.text
        // Typewriter animation: reveal characters progressively
        val displayText = if (overlay.animationIn == com.novacut.editor.model.TextAnimation.TYPEWRITER) {
            val elapsed = timeMs - relStartMs
            val charCount = ((elapsed.toFloat() / animDurationMs) * fullText.length)
                .toInt().coerceIn(0, fullText.length)
            fullText.substring(0, charCount)
        } else {
            fullText
        }
        val text = SpannableString(displayText)
        if (displayText.isNotEmpty()) {
            // Apply alpha to text color
            val baseColor = overlay.color.toInt()
            val alphaInt = (currentAlpha * 255f).toInt().coerceIn(0, 255)
            val alphaColor = (baseColor and 0x00FFFFFF) or (alphaInt shl 24)
            text.setSpan(
                ForegroundColorSpan(alphaColor),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.setSpan(
                AbsoluteSizeSpan(overlay.fontSize.toInt(), true),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val style = when {
                overlay.bold && overlay.italic -> Typeface.BOLD_ITALIC
                overlay.bold -> Typeface.BOLD
                overlay.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) {
                text.setSpan(StyleSpan(style), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            text.setSpan(
                TypefaceSpan(overlay.fontFamily),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (overlay.backgroundColor.toInt() and 0xFF000000.toInt() != 0) {
                val bgAlpha = (currentAlpha * ((overlay.backgroundColor.toInt() ushr 24) and 0xFF)).toInt().coerceIn(0, 255)
                val bgColor = (overlay.backgroundColor.toInt() and 0x00FFFFFF) or (bgAlpha shl 24)
                text.setSpan(
                    BackgroundColorSpan(bgColor),
                    0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val alignment = when (overlay.alignment) {
                com.novacut.editor.model.TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
                com.novacut.editor.model.TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                com.novacut.editor.model.TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            }
            text.setSpan(
                AlignmentSpan.Standard(alignment),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return text
    }

    /**
     * Returns a 4x4 column-major vertex transformation matrix for overlay positioning.
     * Encodes position, scale, and rotation computed from animation state.
     */
    override fun getVertexTransformation(presentationTimeUs: Long): FloatArray {
        val timeMs = presentationTimeUs / 1000L
        if (timeMs < relStartMs || timeMs > relEndMs) {
            // Zero-scale matrix to hide overlay
            return floatArrayOf(
                0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
        }

        computeAnimationState(timeMs)

        val tx = currentOffsetX + (overlay.positionX * 2f - 1f)
        val ty = currentOffsetY - (overlay.positionY * 2f - 1f)
        val sx = currentScale
        val sy = currentScale
        val rad = currentRotation * (kotlin.math.PI.toFloat() / 180f)
        val cos = kotlin.math.cos(rad)
        val sin = kotlin.math.sin(rad)

        // Column-major 4x4: scale * rotate, then translate
        return floatArrayOf(
            sx * cos, sx * sin, 0f, 0f,
            -sy * sin, sy * cos, 0f, 0f,
            0f, 0f, 1f, 0f,
            tx, ty, 0f, 1f
        )
    }

    // Cached animation state (computed once per frame, used by both getText and getVertexTransformation)
    private var lastComputedTimeMs = -1L
    private var currentOffsetX = 0f
    private var currentOffsetY = 0f
    private var currentScale = 1f
    private var currentRotation = 0f

    private fun computeAnimationState(timeMs: Long) {
        if (timeMs == lastComputedTimeMs) return
        lastComputedTimeMs = timeMs

        currentAlpha = 1f
        currentOffsetX = 0f
        currentOffsetY = 0f
        currentScale = 1f
        currentRotation = 0f

        val inProgress = if (overlay.animationIn != com.novacut.editor.model.TextAnimation.NONE) {
            ((timeMs - relStartMs).toFloat() / animDurationMs).coerceIn(0f, 1f)
        } else 1f

        val outProgress = if (overlay.animationOut != com.novacut.editor.model.TextAnimation.NONE) {
            ((relEndMs - timeMs).toFloat() / animDurationMs).coerceIn(0f, 1f)
        } else 1f

        // Animation in
        when (overlay.animationIn) {
            com.novacut.editor.model.TextAnimation.FADE -> currentAlpha *= easeOut(inProgress)
            com.novacut.editor.model.TextAnimation.SLIDE_UP -> currentOffsetY -= (1f - easeOut(inProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_DOWN -> currentOffsetY += (1f - easeOut(inProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_LEFT -> currentOffsetX -= (1f - easeOut(inProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_RIGHT -> currentOffsetX += (1f - easeOut(inProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SCALE -> currentScale *= easeOut(inProgress)
            com.novacut.editor.model.TextAnimation.SPIN -> currentRotation += (1f - easeOut(inProgress)) * 360f
            com.novacut.editor.model.TextAnimation.BOUNCE -> {
                val t = easeOut(inProgress)
                currentOffsetY -= (1f - bounceEase(t)) * 0.3f
            }
            com.novacut.editor.model.TextAnimation.TYPEWRITER -> { /* handled in getText() */ }
            com.novacut.editor.model.TextAnimation.NONE -> { }
            com.novacut.editor.model.TextAnimation.BLUR_IN -> currentAlpha *= easeOut(inProgress)
            com.novacut.editor.model.TextAnimation.GLITCH -> currentOffsetX += (1f - easeOut(inProgress)) * 0.05f * kotlin.math.sin(inProgress * 30f)
            com.novacut.editor.model.TextAnimation.WAVE -> currentOffsetY -= kotlin.math.sin(inProgress * 6.28f) * 0.05f
            com.novacut.editor.model.TextAnimation.ELASTIC -> {
                val t = easeOut(inProgress)
                currentScale *= if (t < 1f) (1f + 0.3f * kotlin.math.sin(t * 3.14f * 3f) * (1f - t)) else 1f
            }
            com.novacut.editor.model.TextAnimation.FLIP -> currentRotation += (1f - easeOut(inProgress)) * 180f
        }

        // Animation out
        when (overlay.animationOut) {
            com.novacut.editor.model.TextAnimation.FADE -> currentAlpha *= easeOut(outProgress)
            com.novacut.editor.model.TextAnimation.SLIDE_UP -> currentOffsetY += (1f - easeOut(outProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_DOWN -> currentOffsetY -= (1f - easeOut(outProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_LEFT -> currentOffsetX += (1f - easeOut(outProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_RIGHT -> currentOffsetX -= (1f - easeOut(outProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SCALE -> currentScale *= easeOut(outProgress)
            com.novacut.editor.model.TextAnimation.SPIN -> currentRotation -= (1f - easeOut(outProgress)) * 360f
            com.novacut.editor.model.TextAnimation.BOUNCE -> {
                val t = easeOut(outProgress)
                currentOffsetY += (1f - bounceEase(t)) * 0.3f
            }
            com.novacut.editor.model.TextAnimation.TYPEWRITER -> currentAlpha *= outProgress
            com.novacut.editor.model.TextAnimation.NONE -> { }
            com.novacut.editor.model.TextAnimation.BLUR_IN -> currentAlpha *= easeOut(outProgress)
            com.novacut.editor.model.TextAnimation.GLITCH -> currentOffsetX -= (1f - easeOut(outProgress)) * 0.05f * kotlin.math.sin(outProgress * 30f)
            com.novacut.editor.model.TextAnimation.WAVE -> currentOffsetY += kotlin.math.sin(outProgress * 6.28f) * 0.05f
            com.novacut.editor.model.TextAnimation.ELASTIC -> currentScale *= easeOut(outProgress)
            com.novacut.editor.model.TextAnimation.FLIP -> currentRotation -= (1f - easeOut(outProgress)) * 180f
        }
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun bounceEase(t: Float): Float {
        return when {
            t < 0.3636f -> 7.5625f * t * t
            t < 0.7273f -> 7.5625f * (t - 0.5455f) * (t - 0.5455f) + 0.75f
            t < 0.9091f -> 7.5625f * (t - 0.8182f) * (t - 0.8182f) + 0.9375f
            else -> 7.5625f * (t - 0.9545f) * (t - 0.9545f) + 0.984375f
        }
    }
}

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
