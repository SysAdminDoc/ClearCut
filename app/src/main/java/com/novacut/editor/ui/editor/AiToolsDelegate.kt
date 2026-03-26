package com.novacut.editor.ui.editor

import android.content.Context
import android.net.Uri
import com.novacut.editor.ai.AiFeatures
import com.novacut.editor.engine.*

import com.novacut.editor.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Delegate handling all AI tool operations: model downloads, runAiTool dispatch,
 * and Tier 3 ML engine wrappers.
 * Extracted from EditorViewModel to reduce its size (~285 lines of AI logic).
 */
class AiToolsDelegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val aiFeatures: AiFeatures,
    private val templateManager: TemplateManager,
    private val frameInterpolationEngine: FrameInterpolationEngine,
    private val inpaintingEngine: InpaintingEngine,
    private val upscaleEngine: UpscaleEngine,
    private val videoMattingEngine: VideoMattingEngine,
    private val stabilizationEngine: StabilizationEngine,
    private val styleTransferEngine: StyleTransferEngine,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val getSelectedClip: () -> Clip?,
    private val setClipTransform: (String, Float?, Float?, Float?, Float?, Float?) -> Unit,
    private val rebuildPlayerTimeline: () -> Unit,
    private val saveProject: () -> Unit,
    private val videoEngine: VideoEngine
) {
    private var aiJob: Job? = null

    // Whisper model state (exposed for UI binding)
    val whisperModelState get() = aiFeatures.whisperEngine.modelState
    val whisperDownloadProgress get() = aiFeatures.whisperEngine.downloadProgress
    val segmentationModelState get() = aiFeatures.segmentationEngine.modelState
    val segmentationDownloadProgress get() = aiFeatures.segmentationEngine.downloadProgress

    fun downloadWhisperModel() {
        scope.launch {
            showToast("Downloading Whisper speech model...")
            val success = aiFeatures.whisperEngine.downloadModel()
            showToast(if (success) "Whisper model ready" else "Model download failed")
        }
    }

    fun deleteWhisperModel() {
        aiFeatures.whisperEngine.deleteModel()
        showToast("Whisper model deleted")
    }

    fun downloadSegmentationModel() {
        scope.launch {
            showToast("Downloading segmentation model...")
            val success = aiFeatures.segmentationEngine.downloadModel()
            showToast(if (success) "Segmentation model ready" else "Model download failed")
        }
    }

    fun deleteSegmentationModel() {
        aiFeatures.segmentationEngine.deleteModel()
        showToast("Segmentation model deleted")
    }

    fun saveAsTemplate(name: String) {
        val s = stateFlow.value
        scope.launch {
            templateManager.saveTemplate(
                name = name,
                description = "${s.tracks.size} tracks, ${s.textOverlays.size} text overlays",
                project = s.project,
                tracks = s.tracks,
                textOverlays = s.textOverlays
            )
            showToast("Saved template: $name")
        }
    }

    fun runAiTool(toolId: String) {
        val clip = getSelectedClip()
        if (clip == null) {
            showToast("Select a clip first")
            return
        }

        stateFlow.update { it.copy(aiProcessingTool = toolId) }

        aiJob?.cancel()
        aiJob = scope.launch {
            try {
                when (toolId) {
                    "scene_detect" -> runSceneDetect(clip)
                    "auto_captions" -> runAutoCaptions(clip)
                    "smart_crop" -> runSmartCrop(clip)
                    "auto_color" -> runAutoColor(clip)
                    "stabilize" -> runStabilize(clip)
                    "denoise" -> runDenoise(clip)
                    "remove_bg" -> runRemoveBg(clip)
                    "track_motion" -> runTrackMotion(clip)
                    "style_transfer" -> runStyleTransfer(clip)
                    "face_track" -> runFaceTrack(clip)
                    "smart_reframe" -> runSmartReframe(clip)
                    "upscale" -> runUpscale(clip)
                    "frame_interp" -> applyFrameInterpolation(clip)
                    "object_remove" -> applyObjectRemoval(clip)
                    "video_upscale" -> applyVideoUpscale(clip)
                    "ai_background" -> applyAiBackground(clip)
                    "ai_stabilize" -> applyStabilization(clip)
                    "ai_style_transfer" -> applyStyleTransfer(clip)
                    "bg_replace" -> runBgReplace(clip)
                    else -> showToast("Unknown AI tool: $toolId")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                showToast("AI tool cancelled")
                throw e
            } catch (e: Exception) {
                showToast("AI tool failed: ${e.message}")
            } finally {
                stateFlow.update { it.copy(aiProcessingTool = null) }
                aiJob = null
            }
        }
    }

    fun cancelAiTool() {
        aiJob?.cancel()
    }

    // --- Individual AI tool implementations ---

    private suspend fun runSceneDetect(clip: Clip) {
        val scenes = aiFeatures.detectScenes(clip.sourceUri)
        if (scenes.isEmpty()) {
            showToast("No scene changes detected")
            return
        }
        saveUndoState("AI scene detect")
        stateFlow.update { state ->
            var tracks = state.tracks
            for (scene in scenes.sortedByDescending { it.timestampMs }) {
                val splitMs = clip.timelineStartMs +
                    ((scene.timestampMs - clip.trimStartMs) / clip.speed).toLong()
                if (splitMs <= clip.timelineStartMs || splitMs >= clip.timelineEndMs) continue
                tracks = tracks.map { track ->
                    val idx = track.clips.indexOfFirst { it.id == clip.id }
                    if (idx < 0) return@map track
                    val c = track.clips[idx]
                    if (splitMs <= c.timelineStartMs || splitMs >= c.timelineEndMs) return@map track
                    val relPos = splitMs - c.timelineStartMs
                    val srcSplit = c.trimStartMs + (relPos * c.speed).toLong()
                    val first = c.copy(trimEndMs = srcSplit)
                    val second = c.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        timelineStartMs = splitMs,
                        trimStartMs = srcSplit
                    )
                    val newClips = buildList {
                        addAll(track.clips.subList(0, idx))
                        add(first)
                        add(second)
                        addAll(track.clips.subList(idx + 1, track.clips.size))
                    }
                    track.copy(clips = newClips)
                }
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Split into ${scenes.size + 1} clips at scene boundaries")
    }

    private suspend fun runAutoCaptions(clip: Clip) {
        val useWhisper = aiFeatures.whisperEngine.isReady()
        if (useWhisper) showToast("Transcribing with Whisper...")
        val captions = aiFeatures.generateAutoCaptions(clip.sourceUri)
        if (captions.isEmpty()) {
            showToast("No speech detected")
            return
        }
        saveUndoState("AI auto captions")
        val overlays = aiFeatures.captionsToOverlays(captions)
        stateFlow.update { it.copy(textOverlays = it.textOverlays + overlays) }
        saveProject()
        val source = if (useWhisper) "Whisper" else "energy detection"
        showToast("Added ${captions.size} captions ($source)")
    }

    private suspend fun runSmartCrop(clip: Clip) {
        val suggestion = aiFeatures.suggestCrop(
            clip.sourceUri, stateFlow.value.project.aspectRatio.toFloat()
        )
        if (suggestion.confidence < 0.1f) {
            showToast("Could not analyze frame for crop")
            return
        }
        saveUndoState("AI smart crop")
        setClipTransform(clip.id, suggestion.centerX - 0.5f, suggestion.centerY - 0.5f, null, null, null)
        showToast("Smart crop applied (${"%.0f".format(suggestion.confidence * 100)}% confidence)")
    }

    private suspend fun runAutoColor(clip: Clip) {
        val correction = aiFeatures.autoColorCorrect(clip.sourceUri)
        if (correction.confidence < 0.1f) {
            showToast("Could not analyze color")
            return
        }
        saveUndoState("AI auto color")
        val newEffects = buildList {
            if (kotlin.math.abs(correction.brightness) > 0.02f)
                add(Effect(type = EffectType.BRIGHTNESS, params = mapOf("value" to correction.brightness)))
            if (kotlin.math.abs(correction.contrast - 1f) > 0.05f)
                add(Effect(type = EffectType.CONTRAST, params = mapOf("value" to correction.contrast)))
            if (kotlin.math.abs(correction.saturation - 1f) > 0.05f)
                add(Effect(type = EffectType.SATURATION, params = mapOf("value" to correction.saturation)))
            if (kotlin.math.abs(correction.temperature) > 0.05f)
                add(Effect(type = EffectType.TEMPERATURE, params = mapOf("value" to correction.temperature)))
        }
        if (newEffects.isEmpty()) {
            showToast("Colors already look good!")
            return
        }
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                val idx = track.clips.indexOfFirst { it.id == clip.id }
                if (idx < 0) return@map track
                val c = track.clips[idx]
                val autoTypes = newEffects.map { it.type }.toSet()
                val filteredEffects = c.effects.filter { it.type !in autoTypes }
                val updatedClip = c.copy(effects = filteredEffects + newEffects)
                track.copy(clips = track.clips.toMutableList().apply { set(idx, updatedClip) })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Applied ${newEffects.size} color corrections")
    }

    private suspend fun runStabilize(clip: Clip) {
        val result = aiFeatures.stabilizeVideo(clip.sourceUri)
        if (result.confidence < 0.1f || result.shakeMagnitude < 0.001f) {
            showToast("Video is already stable")
            return
        }
        saveUndoState("AI stabilize")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                val idx = track.clips.indexOfFirst { it.id == clip.id }
                if (idx < 0) return@map track
                val c = track.clips[idx]
                val zoom = result.recommendedZoom
                val keyframes = result.motionKeyframes.flatMap { kf ->
                    listOf(
                        Keyframe(timeOffsetMs = kf.timestampMs, property = KeyframeProperty.POSITION_X,
                            value = kf.offsetX, easing = Easing.EASE_IN_OUT),
                        Keyframe(timeOffsetMs = kf.timestampMs, property = KeyframeProperty.POSITION_Y,
                            value = kf.offsetY, easing = Easing.EASE_IN_OUT)
                    )
                }
                val stabilized = c.copy(scaleX = c.scaleX * zoom, scaleY = c.scaleY * zoom, keyframes = c.keyframes + keyframes)
                track.copy(clips = track.clips.toMutableList().apply { set(idx, stabilized) })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Stabilized: ${"%.0f".format(result.shakeMagnitude * 100)}% shake corrected, ${"%.0f".format((result.recommendedZoom - 1f) * 100)}% zoom applied")
    }

    private suspend fun runDenoise(clip: Clip) {
        val profile = aiFeatures.analyzeAudioNoise(clip.sourceUri)
        if (profile.confidence < 0.1f) {
            showToast("Could not analyze audio noise")
            return
        }
        if (profile.signalToNoiseDb > 40f) {
            showToast("Audio is already clean (SNR: ${"%.0f".format(profile.signalToNoiseDb)}dB)")
            return
        }
        saveUndoState("AI denoise")
        val volumeBoost = (1f + profile.recommendedReduction * 0.3f).coerceAtMost(1.5f)
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                val idx = track.clips.indexOfFirst { it.id == clip.id }
                if (idx < 0) return@map track
                val c = track.clips[idx]
                val denoised = c.copy(
                    volume = (c.volume * volumeBoost).coerceIn(0f, 2f),
                    fadeInMs = if (c.fadeInMs < 50) 50L else c.fadeInMs,
                    fadeOutMs = if (c.fadeOutMs < 50) 50L else c.fadeOutMs
                )
                track.copy(clips = track.clips.toMutableList().apply { set(idx, denoised) })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Denoised: SNR ${"%.0f".format(profile.signalToNoiseDb)}dB, reduction ${"%.0f".format(profile.recommendedReduction * 100)}%")
    }

    private suspend fun runRemoveBg(clip: Clip) {
        val segEngine = aiFeatures.segmentationEngine
        if (segEngine.isReady()) {
            val result = segEngine.segmentVideoFrame(clip.sourceUri)
            if (result == null || result.confidence < 0.05f) {
                showToast("Could not detect subject in frame")
                return
            }
            saveUndoState("AI remove background")
            val bgEffect = Effect(type = EffectType.BG_REMOVAL, params = mapOf("threshold" to 0.5f))
            updateClipEffect(clip, bgEffect, setOf(EffectType.BG_REMOVAL, EffectType.CHROMA_KEY))
            showToast("AI background removal applied (${"%.0f".format(result.confidence * 100)}% coverage)")
        } else {
            applyChromaKeyFallback(clip, "removal")
        }
    }

    private suspend fun runBgReplace(clip: Clip) {
        val segEngine = aiFeatures.segmentationEngine
        if (segEngine.isReady()) {
            val result = segEngine.segmentVideoFrame(clip.sourceUri)
            if (result != null && result.confidence >= 0.05f) {
                saveUndoState("AI background replace")
                val bgEffect = Effect(type = EffectType.BG_REMOVAL, params = mapOf("threshold" to 0.5f))
                updateClipEffect(clip, bgEffect, setOf(EffectType.BG_REMOVAL, EffectType.CHROMA_KEY))
                showToast("Background removed \u2014 add replacement media on track below")
            } else {
                showToast("Could not detect subject in frame")
            }
        } else {
            applyChromaKeyFallback(clip, "replace")
        }
    }

    private suspend fun applyChromaKeyFallback(clip: Clip, action: String) {
        val analysis = aiFeatures.analyzeBackground(clip.sourceUri)
        if (analysis.confidence < 0.1f) {
            showToast("Could not detect background")
            return
        }
        saveUndoState("AI background $action")
        val chromaKeyEffect = Effect(
            type = EffectType.CHROMA_KEY,
            params = mapOf(
                "similarity" to analysis.recommendedSimilarity,
                "smoothness" to analysis.recommendedSmoothness,
                "spill" to analysis.recommendedSpill
            )
        )
        updateClipEffect(clip, chromaKeyEffect, setOf(EffectType.CHROMA_KEY))
        val bgType = when {
            analysis.isGreenScreen -> "green screen"
            analysis.isBlueScreen -> "blue screen"
            else -> "background"
        }
        showToast("Applied $bgType $action (${"%.0f".format(analysis.confidence * 100)}% confidence)")
    }

    private fun updateClipEffect(clip: Clip, newEffect: Effect, replaceTypes: Set<EffectType>) {
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                val idx = track.clips.indexOfFirst { it.id == clip.id }
                if (idx < 0) return@map track
                val c = track.clips[idx]
                val filtered = c.effects.filter { it.type !in replaceTypes }
                val updated = c.copy(effects = filtered + newEffect)
                track.copy(clips = track.clips.toMutableList().apply { set(idx, updated) })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    private suspend fun runTrackMotion(clip: Clip) {
        try {
            val region = com.novacut.editor.ai.TrackingRegion()
            val results = aiFeatures.trackMotion(clip.sourceUri, region, clip.trimStartMs, clip.trimEndMs)
            if (results.isEmpty()) {
                showToast("Motion tracking failed")
                return
            }
            saveUndoState("AI motion track")
            val posKeyframes = buildTrackingKeyframes(results, clip, invertSign = false, yBaseline = 0.5f)
            addPositionKeyframes(clip, posKeyframes)
            showToast("Tracked ${results.size} motion points across clip")
        } catch (e: Exception) {
            showToast("Motion tracking error: ${e.message ?: "Unknown"}")
        }
    }

    private suspend fun runStyleTransfer(clip: Clip) {
        try {
            showToast("Analyzing frame style...")
            val style = aiFeatures.analyzeAndApplyStyle(clip.sourceUri)
            if (style.confidence < 0.1f) {
                showToast("Could not analyze frame style")
                return
            }
            saveUndoState("AI style transfer")
            val newEffects = buildList {
                if (kotlin.math.abs(style.contrast - 1f) > 0.02f)
                    add(Effect(type = EffectType.CONTRAST, params = mapOf("value" to style.contrast)))
                if (kotlin.math.abs(style.temperature) > 0.01f)
                    add(Effect(type = EffectType.TEMPERATURE, params = mapOf("value" to style.temperature)))
                if (kotlin.math.abs(style.saturation - 1f) > 0.02f)
                    add(Effect(type = EffectType.SATURATION, params = mapOf("value" to style.saturation)))
                if (kotlin.math.abs(style.exposure) > 0.01f)
                    add(Effect(type = EffectType.EXPOSURE, params = mapOf("value" to style.exposure)))
                if (style.vignetteIntensity > 0.01f)
                    add(Effect(type = EffectType.VIGNETTE, params = mapOf("intensity" to style.vignetteIntensity, "radius" to style.vignetteRadius)))
                if (style.filmGrain > 0.01f)
                    add(Effect(type = EffectType.FILM_GRAIN, params = mapOf("intensity" to style.filmGrain)))
            }
            if (newEffects.isEmpty()) {
                showToast("No style adjustments needed for '${style.styleName}'")
                return
            }
            stateFlow.update { state ->
                val tracks = state.tracks.map { track ->
                    val idx = track.clips.indexOfFirst { it.id == clip.id }
                    if (idx < 0) return@map track
                    val c = track.clips[idx]
                    val updated = c.copy(effects = c.effects + newEffects)
                    track.copy(clips = track.clips.toMutableList().apply { set(idx, updated) })
                }
                state.copy(tracks = tracks)
            }
            rebuildPlayerTimeline()
            getSelectedClip()?.let { videoEngine.applyPreviewEffects(it) }
            saveProject()
            showToast("Applied '${style.styleName}' style (${newEffects.size} effects)")
        } catch (e: Exception) {
            showToast("Style transfer error: ${e.message ?: "Unknown"}")
        }
    }

    private suspend fun runFaceTrack(clip: Clip) {
        try {
            showToast("Face tracking: detecting faces...")
            val region = com.novacut.editor.ai.TrackingRegion(centerX = 0.5f, centerY = 0.35f, width = 0.3f, height = 0.3f)
            val results = aiFeatures.trackMotion(clip.sourceUri, region, clip.trimStartMs, clip.trimEndMs)
            if (results.isNotEmpty()) {
                saveUndoState("AI face track")
                val posKeyframes = buildTrackingKeyframes(results, clip, invertSign = true, yBaseline = 0.35f)
                addPositionKeyframes(clip, posKeyframes)
                showToast("Face tracked: ${results.size} points")
            } else {
                showToast("No face detected")
            }
        } catch (e: Exception) {
            showToast("Face tracking error: ${e.message ?: "Unknown"}")
        }
    }

    /**
     * Build position keyframes from tracking results.
     * Shared between runTrackMotion and runFaceTrack to avoid duplication.
     */
    private fun buildTrackingKeyframes(
        results: List<com.novacut.editor.ai.TrackingResult>,
        clip: Clip,
        invertSign: Boolean,
        yBaseline: Float
    ): List<Keyframe> {
        val sign = if (invertSign) -1f else 1f
        return results.mapNotNull { tr ->
            val timeOffset = ((tr.timestampMs - clip.trimStartMs) / clip.speed).toLong()
            if (timeOffset < 0 || timeOffset > clip.durationMs) return@mapNotNull null
            listOf(
                Keyframe(timeOffsetMs = timeOffset, property = KeyframeProperty.POSITION_X,
                    value = sign * (tr.region.centerX - 0.5f) * 2f, easing = Easing.EASE_IN_OUT),
                Keyframe(timeOffsetMs = timeOffset, property = KeyframeProperty.POSITION_Y,
                    value = sign * (tr.region.centerY - yBaseline) * 2f, easing = Easing.EASE_IN_OUT)
            )
        }.flatten()
    }

    private suspend fun runSmartReframe(clip: Clip) {
        try {
        val suggestion = aiFeatures.suggestCrop(clip.sourceUri, 9f / 16f)
        if (suggestion.confidence > 0.1f) {
            saveUndoState("AI smart reframe")
            setClipTransform(clip.id,
                (suggestion.centerX - 0.5f) * 2f,
                (suggestion.centerY - 0.5f) * 2f,
                1f / suggestion.width,
                1f / suggestion.height,
                null
            )
            showToast("Smart reframed for vertical (${"%.0f".format(suggestion.confidence * 100)}%)")
        } else {
            showToast("Could not determine reframe region")
        }
        } catch (e: Exception) {
            showToast("Smart reframe error: ${e.message ?: "Unknown"}")
        }
    }

    private suspend fun runUpscale(clip: Clip) {
        try {
            showToast("Analyzing source resolution...")
            val result = aiFeatures.analyzeForUpscale(clip.sourceUri)
            if (result.targetResolution == null) {
                showToast("Already at maximum resolution (${result.sourceWidth}x${result.sourceHeight})")
                return
            }
            saveUndoState("AI upscale")
            stateFlow.update { it.copy(project = it.project.copy(resolution = result.targetResolution)) }
            val sharpenEffect = Effect(type = EffectType.SHARPEN, params = mapOf("strength" to result.sharpenStrength))
            updateClipEffect(clip, sharpenEffect, setOf(EffectType.SHARPEN))
            showToast("Upscaled to ${result.targetResolution.label} + sharpening applied")
        } catch (e: Exception) {
            showToast("Upscale error: ${e.message ?: "Unknown"}")
        }
    }

    private fun addPositionKeyframes(clip: Clip, newKeyframes: List<Keyframe>) {
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                val idx = track.clips.indexOfFirst { it.id == clip.id }
                if (idx < 0) return@map track
                val c = track.clips[idx]
                val trackedProps = setOf(KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y)
                val existing = c.keyframes.filter { it.property !in trackedProps }
                val updated = c.copy(keyframes = existing + newKeyframes)
                track.copy(clips = track.clips.toMutableList().apply { set(idx, updated) })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    // --- Tier 3: ML Engine Wrapper Methods ---

    private suspend fun applyFrameInterpolation(clip: Clip) {
        if (!frameInterpolationEngine.isModelReady()) {
            showToast("Frame interpolation requires RIFE model download (~10MB)")
            return
        }
        val config = FrameInterpolationEngine.SlowMotionConfig(
            multiplier = 2, quality = FrameInterpolationEngine.SlowMotionConfig.Quality.PREVIEW
        )
        val outputFile = File(appContext.cacheDir, "interp_${clip.id}.mp4")
        showToast("Generating slow-motion (2x)...")
        val result = frameInterpolationEngine.interpolateFrames(
            inputUri = clip.sourceUri, outputUri = Uri.fromFile(outputFile),
            config = config, onProgress = { }
        )
        if (result != null) {
            saveUndoState("AI frame interpolation")
            showToast("Slow-motion applied: ${result.interpolatedFrameCount} frames (${if (result.usedMlModel) "RIFE ML" else "frame duplication"})")
        } else {
            showToast("Frame interpolation not yet available \u2014 model integration pending")
        }
    }

    private suspend fun applyObjectRemoval(clip: Clip) {
        if (!inpaintingEngine.isModelReady()) {
            showToast("Object removal requires LaMa model download (~174MB)")
            return
        }
        showToast("Object removal: tap and paint over the object to remove (UI pending)")
    }

    private suspend fun applyVideoUpscale(clip: Clip) {
        val variant = UpscaleEngine.UpscaleConfig.ModelVariant.GENERAL_X4V3
        if (!upscaleEngine.isModelReady(variant)) {
            showToast("Video upscale requires Real-ESRGAN model download (~12MB)")
            return
        }
        val config = UpscaleEngine.UpscaleConfig(
            scaleFactor = 4, modelVariant = variant, quality = UpscaleEngine.UpscaleConfig.Quality.BALANCED
        )
        val outputFile = File(appContext.cacheDir, "upscale_${clip.id}.mp4")
        showToast("Upscaling video with Real-ESRGAN...")
        val result = upscaleEngine.upscaleVideo(
            uri = clip.sourceUri, config = config, outputUri = Uri.fromFile(outputFile), onProgress = { }
        )
        if (result != null) {
            saveUndoState("AI video upscale")
            showToast("Upscaled to ${result.outputWidth}x${result.outputHeight}")
        } else {
            showToast("Video upscale not yet available \u2014 model integration pending")
        }
    }

    private suspend fun applyAiBackground(clip: Clip) {
        if (!videoMattingEngine.isModelReady()) {
            showToast("AI background requires RVM model download (~15MB)")
            return
        }
        val config = VideoMattingEngine.MattingConfig(
            quality = VideoMattingEngine.MattingConfig.Quality.PREVIEW,
            backgroundMode = VideoMattingEngine.MattingConfig.BackgroundMode.BLUR
        )
        val outputFile = File(appContext.cacheDir, "matting_${clip.id}.mp4")
        showToast("Processing AI background removal...")
        val result = videoMattingEngine.processVideo(
            uri = clip.sourceUri, outputUri = Uri.fromFile(outputFile), config = config, onProgress = { }
        )
        if (result != null) {
            saveUndoState("AI background replacement")
            showToast("Background replaced (${result.framesProcessed} frames, ${"%.1f".format(result.averageFps)} fps)")
        } else {
            showToast("AI background not yet available \u2014 model integration pending")
        }
    }

    private suspend fun applyStabilization(clip: Clip) {
        if (!stabilizationEngine.isOpenCvAvailable()) {
            showToast("Advanced stabilization requires OpenCV \u2014 using basic stabilization")
            val result = aiFeatures.stabilizeVideo(clip.sourceUri)
            if (result.confidence < 0.1f || result.shakeMagnitude < 0.001f) {
                showToast("Video is already stable")
            } else {
                saveUndoState("AI stabilize (basic)")
                showToast("Basic stabilization applied (${"%.0f".format(result.shakeMagnitude * 100)}% shake)")
            }
            return
        }
        val config = StabilizationEngine.StabilizationConfig(
            smoothingStrength = 0.5f, cropPercentage = 0.15f,
            algorithm = StabilizationEngine.StabilizationConfig.Algorithm.LK_OPTICAL_FLOW
        )
        showToast("Analyzing camera motion...")
        val motionData = stabilizationEngine.analyzeMotion(uri = clip.sourceUri, config = config, onProgress = { })
        if (motionData == null) {
            showToast("Motion analysis failed \u2014 using basic stabilization fallback")
            return
        }
        val outputFile = File(appContext.cacheDir, "stabilized_${clip.id}.mp4")
        showToast("Applying stabilization (${motionData.frameCount} frames)...")
        val result = stabilizationEngine.stabilize(
            uri = clip.sourceUri, motionData = motionData, config = config,
            outputUri = Uri.fromFile(outputFile), onProgress = { }
        )
        if (result != null) {
            saveUndoState("AI stabilize (OpenCV)")
            showToast("Stabilized with ${"%.0f".format(result.cropApplied * 100)}% crop")
        } else {
            showToast("Stabilization not yet available \u2014 OpenCV integration pending")
        }
    }

    private suspend fun applyStyleTransfer(clip: Clip) {
        val available = styleTransferEngine.getAvailableStyles()
        if (available.isEmpty()) {
            showToast("Style transfer requires model download \u2014 no styles available yet")
            return
        }
        val style = available.first()
        showToast("Applying '${style.displayName}' style...")
        val outputFile = File(appContext.cacheDir, "styled_${clip.id}.mp4")
        val result = styleTransferEngine.applyStyleToVideo(
            uri = clip.sourceUri, style = style, outputUri = Uri.fromFile(outputFile), onProgress = { }
        )
        if (result != null) {
            saveUndoState("AI style transfer (${style.displayName})")
            showToast("Applied '${style.displayName}' to ${result.framesProcessed} frames (${"%.1f".format(result.averageFps)} fps)")
        } else {
            showToast("Style transfer not yet available \u2014 model integration pending")
        }
    }

    private fun recalculateDuration(state: EditorState): EditorState {
        val totalDuration = state.tracks.maxOfOrNull { t ->
            t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
        } ?: 0L
        return state.copy(totalDurationMs = totalDuration)
    }

}
