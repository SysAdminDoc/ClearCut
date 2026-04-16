package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
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
import android.util.Log
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
    private val segmentationEngine: SegmentationEngine
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

    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var transitionListener: Player.Listener? = null

    // Clips for per-clip effect switching during playback
    private var videoClips: List<Clip> = emptyList()
    private var previewSegments: List<PreviewSegment> = emptyList()
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
            player = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setRenderersFactory(renderersFactory)
                .build()
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
    fun prepareTimeline(tracks: List<Track>) {
        val p = getPlayer()
        videoClips = collectPreviewClips(tracks)
        val timelineEndMs = tracks.maxOfOrNull { track ->
            track.clips.maxOfOrNull { clip -> clip.timelineEndMs } ?: 0L
        } ?: 0L
        previewSegments = buildPreviewSegments(videoClips, timelineEndMs)
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
        return (segment.timelineStartMs + p.currentPosition)
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

        try {
            val visibleVideoTracks = tracks
                .sortedBy { it.index }
                .filter {
                (it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY) && it.isVisible && it.clips.isNotEmpty()
            }
            if (visibleVideoTracks.isEmpty()) {
                throw IllegalStateException("No video clips to export")
            }
            val videoTrack = visibleVideoTracks.first()
            val soloTrackIds = tracks.filter { it.isSolo }.map { it.id }.toSet()
            val videoTrackAudioGain = if (isTrackAudibleForMix(videoTrack, soloTrackIds)) {
                videoTrack.volume.coerceIn(0f, 2f)
            } else {
                0f
            }
            val (targetW, targetH) = config.resolution.forAspect(config.aspectRatio)

            val clips = videoTrack.clips.sortedBy { it.timelineStartMs }
            val editedItems = clips.mapIndexed { index, clip ->
                val nextTransition = clips.getOrNull(index + 1)?.transition
                buildEditedMediaItem(
                    clip = clip,
                    videoMuted = videoTrackAudioGain <= 0f,
                    trackAudioGain = videoTrackAudioGain,
                    tracks = tracks,
                    config = config,
                    targetW = targetW,
                    targetH = targetH,
                    textOverlays = textOverlays,
                    lottieOverlays = lottieOverlays,
                    nextClipTransition = nextTransition
                )
            }
            @Suppress("DEPRECATION")
            val videoSequence = EditedMediaItemSequence.Builder().addItems(editedItems).build()

            val audioSequences = buildAudioSequences(tracks, soloTrackIds)
            val allSequences = buildList {
                add(videoSequence)
                addAll(audioSequences)
            }

            val composition = buildComposition(allSequences, audioSequences.isNotEmpty(), videoTrackAudioGain <= 0f)

            val mimeType = if (config.transparentBackground) {
                MimeTypes.VIDEO_VP9
            } else when (config.codec) {
                VideoCodec.HEVC -> MimeTypes.VIDEO_H265
                VideoCodec.H264 -> MimeTypes.VIDEO_H264
                VideoCodec.AV1 -> MimeTypes.VIDEO_AV1
                VideoCodec.VP9 -> MimeTypes.VIDEO_VP9
            }

            startTransformerWithPolling(composition, mimeType, config, outputFile, onProgress, onComplete, onError)
        } catch (e: Exception) {
            Log.e(TAG, "Export setup failed", e)
            _exportErrorMessage.value = e.message ?: "Export setup failed"
            _exportState.value = ExportState.ERROR
            _exportProgress.value = 0f
            activeTransformer = null
            activeExportOutputFile = null
            outputFile.delete()
            onError(e)
        }
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
        lottieOverlays: List<LottieOverlaySpec>,
        nextClipTransition: Transition? = null
    ): EditedMediaItem {
        val mediaItem = buildMediaItemForClip(clip, clip.sourceUri)

        val videoEffects = buildList<androidx.media3.common.Effect> {
            for (effect in clip.effects.filter { it.enabled }) {
                EffectBuilder.buildVideoEffect(effect, segmentationEngine)?.let { add(it) }
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

            clip.transition?.let { add(EffectBuilder.buildTransitionEffect(it)) }
            // Transition-out if the next clip has a transition
            nextClipTransition?.let { add(EffectBuilder.buildTransitionOutEffect(it, clip.durationMs)) }
            addOpacityAndTransformEffects(clip)

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
            if (videoMuted) {
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
                .setImageDurationMs(clip.durationMs.coerceAtLeast(DEFAULT_STILL_IMAGE_DURATION_MS))
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
            val audioItems = at.clips
                .sortedBy { it.timelineStartMs }
                .map { clip ->
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
                            volume = clip.volume, fadeInMs = clip.fadeInMs, fadeOutMs = clip.fadeOutMs,
                            clipDurationMs = clip.durationMs,
                            keyframes = if (hasKfVol) clip.keyframes else emptyList(),
                            postGain = at.volume.coerceIn(0f, 2f)
                        ))
                    }
                }
                EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(processors, emptyList()))
                    .setRemoveVideo(true)
                    .build()
            }
            @Suppress("DEPRECATION")
            EditedMediaItemSequence.Builder().addItems(audioItems).build()
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
        totalTimelineDurationMs: Long
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
            segments += PreviewSegment(
                clip = clip,
                timelineStartMs = clip.timelineStartMs,
                durationMs = clip.durationMs,
                mediaUri = clip.proxyUri ?: clip.sourceUri
            )
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
                bitmap.eraseColor(Color.BLACK)
                file.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                bitmap.recycle()
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
                    mediaPositionMs = (targetMs - segment.timelineStartMs)
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
        videoMuted: Boolean
    ): Composition {
        return Composition.Builder(sequences)
            .setTransmuxAudio(!hasAudioTracks && !videoMuted)
            .build()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun startTransformerWithPolling(
        composition: Composition,
        mimeType: String,
        config: ExportConfig,
        outputFile: File,
        onProgress: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
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
                    if (_exportState.value == ExportState.CANCELLED) return
                    _exportState.value = ExportState.COMPLETE
                    _exportProgress.value = 1f
                    activeExportOutputFile = null
                    onComplete()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (_exportState.value == ExportState.CANCELLED) return
                    Log.e(TAG, "Export failed", exportException)
                    _exportErrorMessage.value = exportException.message ?: "Export encoding failed"
                    _exportState.value = ExportState.ERROR
                    _exportProgress.value = 0f
                    activeExportOutputFile = null
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
                activeExportOutputFile = null
                outputFile.delete()
                onError(Exception("Export timed out"))
            }
            activeTransformer = null
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
        activeExportOutputFile?.delete()
        activeExportOutputFile = null
    }

    // --- Preview effects & speed ---

    /**
     * Apply visual effects to ExoPlayer preview for the given clip.
     * Builds RgbMatrix/GlEffect effects from the clip's enabled effects, opacity, and transforms.
     * Includes transition-in for this clip and transition-out if the next clip has a transition.
     * Does NOT include speed (handled via PlaybackParameters), text overlays, Presentation, or FrameDropEffect.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun applyPreviewEffects(clip: Clip?) {
        val p = player ?: return
        if (clip == null) {
            p.setVideoEffects(emptyList())
            return
        }
        val nextClipTransition = nextPreviewTransitionForClip(clip)

        val effects = buildPreviewEffectsForClip(clip, nextClipTransition)
        try {
            p.setVideoEffects(effects)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply preview effects", e)
        }
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
            return
        }
        val nextClipTransition = nextPreviewTransitionForClip(clip)

        val effects = buildPreviewEffectsForClip(clip, nextClipTransition)
        try {
            p.setVideoEffects(effects)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply effects for clip $index", e)
        }
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
        nextClipTransition: Transition?
    ): List<androidx.media3.common.Effect> = buildList {
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
        // Transition-in for this clip
        clip.transition?.let { add(EffectBuilder.buildTransitionEffect(it)) }
        // Transition-out if the next clip has a transition (fade/wipe out at end of this clip)
        nextClipTransition?.let {
            add(EffectBuilder.buildTransitionOutEffect(it, clip.durationMs))
        }
        // Opacity + transform (keyframe-animated or static)
        addOpacityAndTransformEffects(clip)
    }

    /**
     * Set ExoPlayer playback speed for preview. Does not affect export.
     */
    fun setPreviewVolume(volume: Float) {
        try {
            player?.volume = volume.coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set preview volume", e)
        }
    }

    fun setPreviewSpeed(speed: Float) {
        try {
            player?.playbackParameters = androidx.media3.common.PlaybackParameters(speed.coerceIn(0.1f, 100f))
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

    private fun nextPreviewTransitionForClip(clip: Clip): Transition? {
        val clipIndex = videoClips.indexOfFirst { it.id == clip.id }
        if (clipIndex < 0) return null
        val nextClip = videoClips.getOrNull(clipIndex + 1) ?: return null
        return if (nextClip.timelineStartMs <= clip.timelineEndMs) {
            nextClip.transition
        } else {
            null
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
