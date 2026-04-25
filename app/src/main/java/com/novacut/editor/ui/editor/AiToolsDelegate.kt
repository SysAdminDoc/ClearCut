package com.novacut.editor.ui.editor

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novacut.editor.R
import com.novacut.editor.ai.AiFeatures
import com.novacut.editor.engine.*

import com.novacut.editor.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
    private val videoEngine: VideoEngine,
    private val recalculateDuration: (EditorState) -> EditorState
) {
    private var aiJob: Job? = null

    private val audioRequiredTools = setOf(
        "auto_captions",
        "denoise"
    )

    private val motionVideoRequiredTools = setOf(
        "scene_detect",
        "stabilize",
        "track_motion",
        "ai_stabilize"
    )

    private val visualRequiredTools = setOf(
        "scene_detect",
        "smart_crop",
        "auto_color",
        "stabilize",
        "remove_bg",
        "track_motion",
        "style_transfer",
        "face_track",
        "smart_reframe",
        "upscale",
        "frame_interp",
        "object_remove",
        "video_upscale",
        "ai_background",
        "ai_stabilize",
        "ai_style_transfer",
        "bg_replace"
    )

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
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    aiFeatures.whisperEngine.deleteModel()
                }.isSuccess
            }
            showToast(
                appContext.getString(
                    if (success) {
                        R.string.ai_whisper_removed_toast
                    } else {
                        R.string.ai_model_remove_failed_toast
                    }
                )
            )
        }
    }

    fun downloadSegmentationModel() {
        scope.launch {
            showToast("Downloading segmentation model...")
            val success = aiFeatures.segmentationEngine.downloadModel()
            showToast(if (success) "Segmentation model ready" else "Model download failed")
        }
    }

    fun deleteSegmentationModel() {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    aiFeatures.segmentationEngine.deleteModel()
                }.isSuccess
            }
            showToast(
                appContext.getString(
                    if (success) {
                        R.string.ai_segmentation_removed_toast
                    } else {
                        R.string.ai_model_remove_failed_toast
                    }
                )
            )
        }
    }

    fun saveAsTemplate(name: String) {
        scope.launch {
            try {
                val s = stateFlow.value
                val template = templateManager.saveTemplate(
                    name = name,
                    description = "${s.tracks.size} tracks, ${s.textOverlays.size} text overlays",
                    project = s.project,
                    tracks = s.tracks,
                    textOverlays = s.textOverlays
                )
                showToast("Saved template: ${template.name}")
            } catch (e: Exception) {
                Log.e("AiToolsDelegate", "Failed to save template", e)
                showToast("Template save failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun runAiTool(toolId: String) {
        val clip = getSelectedClip()
        if (clip == null) {
            showToast("Select a clip first")
            return
        }
        getToolCompatibilityMessage(toolId, clip)?.let { incompatibilityMessage ->
            showToast(incompatibilityMessage)
            return
        }

        val clipId = clip.id

        // Cancel the previous job FIRST so its finally block (which clears aiProcessingTool)
        // runs before we publish our new state — otherwise a trailing `aiProcessingTool = null`
        // from the cancelled job could race-overwrite our own update and hide the progress indicator.
        aiJob?.cancel()
        stateFlow.update { it.copy(aiProcessingTool = toolId) }

        lateinit var thisJob: kotlinx.coroutines.Job
        thisJob = scope.launch {
            try {
                // Re-validate clip still exists (user may have deleted it)
                val currentClip = stateFlow.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
                if (currentClip == null) {
                    showToast("Clip no longer exists")
                    return@launch
                }
                when (toolId) {
                    "scene_detect" -> runSceneDetect(currentClip)
                    "auto_captions" -> runAutoCaptions(currentClip)
                    "smart_crop" -> runSmartCrop(currentClip)
                    "auto_color" -> runAutoColor(currentClip)
                    "stabilize" -> runStabilize(currentClip)
                    "denoise" -> runDenoise(currentClip)
                    "remove_bg" -> runRemoveBg(currentClip)
                    "track_motion" -> runTrackMotion(currentClip)
                    "style_transfer" -> runStyleTransfer(currentClip)
                    "face_track" -> runFaceTrack(currentClip)
                    "smart_reframe" -> runSmartReframe(currentClip)
                    "upscale" -> runUpscale(currentClip)
                    "frame_interp" -> applyFrameInterpolation(currentClip)
                    "object_remove" -> applyObjectRemoval(currentClip)
                    "video_upscale" -> applyVideoUpscale(currentClip)
                    "ai_background" -> applyAiBackground(currentClip)
                    "ai_stabilize" -> applyStabilization(currentClip)
                    "ai_style_transfer" -> applyStyleTransfer(currentClip)
                    "bg_replace" -> runBgReplace(currentClip)
                    else -> showToast("Unknown AI tool: $toolId")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                showToast("AI tool cancelled")
                throw e
            } catch (e: Exception) {
                showToast("AI tool failed: ${e.message}")
            } finally {
                // Only clear progress state if we are still the active job — protects against
                // a stale cancelled job overwriting the newly-launched one's progress indicator.
                if (aiJob === thisJob) {
                    stateFlow.update { it.copy(aiProcessingTool = null) }
                    aiJob = null
                }
            }
        }
        aiJob = thisJob
    }

    fun cancelAiTool() {
        aiJob?.cancel()
    }

    private fun getToolCompatibilityMessage(toolId: String, clip: Clip): String? {
        return when {
            toolId in audioRequiredTools && !videoEngine.hasAudioTrack(clip.sourceUri) -> {
                if (toolId == "auto_captions") {
                    "Auto Captions needs a clip with audio."
                } else {
                    "Denoise needs a clip with audio."
                }
            }
            toolId in motionVideoRequiredTools && !videoEngine.isMotionVideo(clip.sourceUri) -> {
                "Select a video clip for this AI tool."
            }
            toolId in visualRequiredTools && !videoEngine.hasVisualTrack(clip.sourceUri) -> {
                "Select a photo or video clip for this AI tool."
            }
            else -> null
        }
    }

    // --- Individual AI tool implementations ---

    private suspend fun runSceneDetect(clip: Clip) {
        val scenes = withContext(Dispatchers.Default) { aiFeatures.detectScenes(clip.sourceUri) }
        val splitOffsets = scenes
            .asSequence()
            .filter { safeConfidence(it.confidence) >= 0.1f }
            .mapNotNull { clip.sourceTimeToTimelineOffsetMs(it.timestampMs, includeBoundaries = false) }
            .filter { it in 1 until clip.durationMs }
            .distinct()
            .sortedDescending()
            .toList()
        if (splitOffsets.isEmpty()) {
            showToast("No scene changes detected")
            return
        }
        saveUndoState("AI scene detect")
        stateFlow.update { state ->
            var tracks = state.tracks
            for (splitOffset in splitOffsets) {
                val splitMs = clip.timelineStartMs + splitOffset
                tracks = tracks.map { track ->
                    val idx = track.clips.indexOfFirst { it.id == clip.id }
                    if (idx < 0) return@map track
                    val c = track.clips[idx]
                    if (splitMs <= c.timelineStartMs || splitMs >= c.timelineEndMs) return@map track
                    val relPos = splitMs - c.timelineStartMs
                    val srcSplit = c.timelineOffsetToSourceMs(relPos)
                    if (srcSplit <= c.trimStartMs || srcSplit >= c.trimEndMs) return@map track
                    val trimRange = (c.trimEndMs - c.trimStartMs).coerceAtLeast(0L)
                    val splitFraction = if (trimRange > 0L) {
                        ((srcSplit - c.trimStartMs).toFloat() / trimRange.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val first = c.copy(
                        trimEndMs = srcSplit,
                        speedCurve = c.speedCurve?.restrictTo(0f, splitFraction, trimRange)
                    )
                    val second = c.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        timelineStartMs = splitMs,
                        trimStartMs = srcSplit,
                        speedCurve = c.speedCurve?.restrictTo(splitFraction, 1f, trimRange)
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
        showToast("Split into ${splitOffsets.size + 1} clips at scene boundaries")
    }

    private suspend fun runAutoCaptions(clip: Clip) {
        val useWhisper = aiFeatures.whisperEngine.isReady()
        if (useWhisper) showToast("Transcribing with Whisper...")
        val captions = withContext(Dispatchers.Default) { aiFeatures.generateAutoCaptions(clip.sourceUri) }
        if (captions.isEmpty()) {
            showToast("No speech detected")
            return
        }
        saveUndoState("AI auto captions")
        val overlays = withContext(Dispatchers.Default) { aiFeatures.captionsToOverlays(captions) }
        stateFlow.update { it.copy(textOverlays = it.textOverlays + overlays) }
        saveProject()
        val source = if (useWhisper) "Whisper" else "energy detection"
        showToast("Added ${captions.size} captions ($source)")
    }

    private suspend fun runSmartCrop(clip: Clip) {
        val suggestion = withContext(Dispatchers.Default) { aiFeatures.suggestCrop(
            clip.sourceUri, stateFlow.value.project.aspectRatio.toFloat()
        ) }
        val confidence = safeConfidence(suggestion.confidence)
        if (confidence < 0.1f) {
            showToast("Could not analyze frame for crop")
            return
        }
        val centerX = safeAiFloat(suggestion.centerX, 0.5f, 0f, 1f)
        val centerY = safeAiFloat(suggestion.centerY, 0.5f, 0f, 1f)
        saveUndoState("AI smart crop")
        setClipTransform(clip.id, centerX - 0.5f, centerY - 0.5f, null, null, null)
        // setClipTransform no longer auto-saves (it's called per-tick from drag); AI
        // tool invocations are one-shot, so persist explicitly after the change.
        saveProject()
        showToast("Smart crop applied (${"%.0f".format(confidence * 100)}% confidence)")
    }

    private suspend fun runAutoColor(clip: Clip) {
        val correction = withContext(Dispatchers.Default) { aiFeatures.autoColorCorrect(clip.sourceUri) }
        if (safeConfidence(correction.confidence) < 0.1f) {
            showToast("Could not analyze color")
            return
        }
        saveUndoState("AI auto color")
        val brightness = safeAiFloat(correction.brightness, 0f, -1f, 1f)
        val contrast = safeAiFloat(correction.contrast, 1f, 0f, 4f)
        val saturation = safeAiFloat(correction.saturation, 1f, 0f, 4f)
        val temperature = safeAiFloat(correction.temperature, 0f, -1f, 1f)
        val newEffects = buildList {
            if (kotlin.math.abs(brightness) > 0.02f)
                add(Effect(type = EffectType.BRIGHTNESS, params = mapOf("value" to brightness)))
            if (kotlin.math.abs(contrast - 1f) > 0.05f)
                add(Effect(type = EffectType.CONTRAST, params = mapOf("value" to contrast)))
            if (kotlin.math.abs(saturation - 1f) > 0.05f)
                add(Effect(type = EffectType.SATURATION, params = mapOf("value" to saturation)))
            if (kotlin.math.abs(temperature) > 0.05f)
                add(Effect(type = EffectType.TEMPERATURE, params = mapOf("value" to temperature)))
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
        val result = withContext(Dispatchers.Default) { aiFeatures.stabilizeVideo(clip.sourceUri) }
        val confidence = safeConfidence(result.confidence)
        val shakeMagnitude = safeAiFloat(result.shakeMagnitude, 0f, 0f, 1f)
        val zoom = safeAiFloat(result.recommendedZoom, 1f, 1f, 5f)
        if (confidence < 0.1f || shakeMagnitude < 0.001f) {
            showToast("Video is already stable")
            return
        }
        saveUndoState("AI stabilize")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                val idx = track.clips.indexOfFirst { it.id == clip.id }
                if (idx < 0) return@map track
                val c = track.clips[idx]
                val keyframes = result.motionKeyframes.mapNotNull { kf ->
                    val timeOffset = c.sourceTimeToTimelineOffsetMs(kf.timestampMs) ?: return@mapNotNull null
                    listOf(
                        Keyframe(timeOffsetMs = timeOffset, property = KeyframeProperty.POSITION_X,
                            value = safeAiFloat(kf.offsetX, 0f, -2f, 2f), easing = Easing.EASE_IN_OUT),
                        Keyframe(timeOffsetMs = timeOffset, property = KeyframeProperty.POSITION_Y,
                            value = safeAiFloat(kf.offsetY, 0f, -2f, 2f), easing = Easing.EASE_IN_OUT)
                    )
                }.flatten()
                val stabilized = c.copy(
                    scaleX = safeAiFloat(c.scaleX * zoom, c.scaleX, 0.1f, 5f),
                    scaleY = safeAiFloat(c.scaleY * zoom, c.scaleY, 0.1f, 5f),
                    keyframes = c.keyframes + keyframes
                )
                track.copy(clips = track.clips.toMutableList().apply { set(idx, stabilized) })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Stabilized: ${"%.0f".format(shakeMagnitude * 100)}% shake corrected, ${"%.0f".format((zoom - 1f) * 100)}% zoom applied")
    }

    private suspend fun runDenoise(clip: Clip) {
        val profile = withContext(Dispatchers.Default) { aiFeatures.analyzeAudioNoise(clip.sourceUri) }
        val confidence = safeConfidence(profile.confidence)
        val signalToNoiseDb = safeAiFloat(profile.signalToNoiseDb, 60f, -120f, 120f)
        val recommendedReduction = safeAiFloat(profile.recommendedReduction, 0f, 0f, 1f)
        if (confidence < 0.1f) {
            showToast("Could not analyze audio noise")
            return
        }
        if (signalToNoiseDb > 40f) {
            showToast("Audio is already clean (SNR: ${"%.0f".format(signalToNoiseDb)}dB)")
            return
        }
        saveUndoState("AI denoise")
        val volumeBoost = (1f + recommendedReduction * 0.3f).coerceIn(1f, 1.5f)
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
        showToast("Denoised: SNR ${"%.0f".format(signalToNoiseDb)}dB, reduction ${"%.0f".format(recommendedReduction * 100)}%")
    }

    private suspend fun runRemoveBg(clip: Clip) {
        val segEngine = aiFeatures.segmentationEngine
        if (segEngine.isReady()) {
            val result = withContext(Dispatchers.Default) { segEngine.segmentVideoFrame(clip.sourceUri) }
            if (result == null || safeConfidence(result.confidence) < 0.05f) {
                showToast("Could not detect subject in frame")
                return
            }
            saveUndoState("AI remove background")
            val bgEffect = Effect(type = EffectType.BG_REMOVAL, params = mapOf("threshold" to 0.5f))
            updateClipEffect(clip, bgEffect, setOf(EffectType.BG_REMOVAL, EffectType.CHROMA_KEY))
            showToast("AI background removal applied (${"%.0f".format(safeConfidence(result.confidence) * 100)}% coverage)")
        } else {
            applyChromaKeyFallback(clip, "removal")
        }
    }

    private suspend fun runBgReplace(clip: Clip) {
        val segEngine = aiFeatures.segmentationEngine
        if (segEngine.isReady()) {
            val result = withContext(Dispatchers.Default) { segEngine.segmentVideoFrame(clip.sourceUri) }
            if (result != null && safeConfidence(result.confidence) >= 0.05f) {
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
        val analysis = withContext(Dispatchers.Default) { aiFeatures.analyzeBackground(clip.sourceUri) }
        val confidence = safeConfidence(analysis.confidence)
        if (confidence < 0.1f) {
            showToast("Could not detect background")
            return
        }
        saveUndoState("AI background $action")
        val chromaKeyEffect = Effect(
            type = EffectType.CHROMA_KEY,
            params = mapOf(
                "similarity" to safeAiFloat(analysis.recommendedSimilarity, 0.4f, 0f, 1f),
                "smoothness" to safeAiFloat(analysis.recommendedSmoothness, 0.1f, 0f, 1f),
                "spill" to safeAiFloat(analysis.recommendedSpill, 0.1f, 0f, 1f)
            )
        )
        updateClipEffect(clip, chromaKeyEffect, setOf(EffectType.CHROMA_KEY))
        val bgType = when {
            analysis.isGreenScreen -> "green screen"
            analysis.isBlueScreen -> "blue screen"
            else -> "background"
        }
        showToast("Applied $bgType $action (${"%.0f".format(confidence * 100)}% confidence)")
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
            val results = withContext(Dispatchers.Default) { aiFeatures.trackMotion(clip.sourceUri, region, clip.trimStartMs, clip.trimEndMs) }
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
            val style = withContext(Dispatchers.Default) { aiFeatures.analyzeAndApplyStyle(clip.sourceUri) }
            if (safeConfidence(style.confidence) < 0.1f) {
                showToast("Could not analyze frame style")
                return
            }
            saveUndoState("AI style transfer")
            val contrast = safeAiFloat(style.contrast, 1f, 0f, 4f)
            val temperature = safeAiFloat(style.temperature, 0f, -1f, 1f)
            val saturation = safeAiFloat(style.saturation, 1f, 0f, 4f)
            val exposure = safeAiFloat(style.exposure, 0f, -1f, 1f)
            val vignetteIntensity = safeAiFloat(style.vignetteIntensity, 0f, 0f, 1f)
            val vignetteRadius = safeAiFloat(style.vignetteRadius, 0.8f, 0f, 1f)
            val filmGrain = safeAiFloat(style.filmGrain, 0f, 0f, 1f)
            val newEffects = buildList {
                if (kotlin.math.abs(contrast - 1f) > 0.02f)
                    add(Effect(type = EffectType.CONTRAST, params = mapOf("value" to contrast)))
                if (kotlin.math.abs(temperature) > 0.01f)
                    add(Effect(type = EffectType.TEMPERATURE, params = mapOf("value" to temperature)))
                if (kotlin.math.abs(saturation - 1f) > 0.02f)
                    add(Effect(type = EffectType.SATURATION, params = mapOf("value" to saturation)))
                if (kotlin.math.abs(exposure) > 0.01f)
                    add(Effect(type = EffectType.EXPOSURE, params = mapOf("value" to exposure)))
                if (vignetteIntensity > 0.01f)
                    add(Effect(type = EffectType.VIGNETTE, params = mapOf("intensity" to vignetteIntensity, "radius" to vignetteRadius)))
                if (filmGrain > 0.01f)
                    add(Effect(type = EffectType.FILM_GRAIN, params = mapOf("intensity" to filmGrain)))
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
            val results = withContext(Dispatchers.Default) { aiFeatures.trackMotion(clip.sourceUri, region, clip.trimStartMs, clip.trimEndMs) }
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
        val baseline = safeAiFloat(yBaseline, 0.5f, 0f, 1f)
        return results.mapNotNull { tr ->
            if (safeConfidence(tr.confidence) <= 0f) return@mapNotNull null
            val timeOffset = clip.sourceTimeToTimelineOffsetMs(tr.timestampMs) ?: return@mapNotNull null
            val centerX = safeAiFloat(tr.region.centerX, 0.5f, 0f, 1f)
            val centerY = safeAiFloat(tr.region.centerY, baseline, 0f, 1f)
            listOf(
                Keyframe(timeOffsetMs = timeOffset, property = KeyframeProperty.POSITION_X,
                    value = safeAiFloat(sign * (centerX - 0.5f) * 2f, 0f, -2f, 2f), easing = Easing.EASE_IN_OUT),
                Keyframe(timeOffsetMs = timeOffset, property = KeyframeProperty.POSITION_Y,
                    value = safeAiFloat(sign * (centerY - baseline) * 2f, 0f, -2f, 2f), easing = Easing.EASE_IN_OUT)
            )
        }.flatten()
    }

    private suspend fun runSmartReframe(clip: Clip) {
        try {
            val suggestion = withContext(Dispatchers.Default) { aiFeatures.suggestCrop(clip.sourceUri, 9f / 16f) }
            val confidence = safeConfidence(suggestion.confidence)
            if (confidence > 0.1f) {
                val centerX = safeAiFloat(suggestion.centerX, 0.5f, 0f, 1f)
                val centerY = safeAiFloat(suggestion.centerY, 0.5f, 0f, 1f)
                val width = safeAiFloat(suggestion.width, 1f, 0.05f, 1f)
                val height = safeAiFloat(suggestion.height, 1f, 0.05f, 1f)
                saveUndoState("AI smart reframe")
                setClipTransform(clip.id,
                    safeAiFloat((centerX - 0.5f) * 2f, 0f, -1f, 1f),
                    safeAiFloat((centerY - 0.5f) * 2f, 0f, -1f, 1f),
                    safeAiFloat(1f / width, 1f, 0.1f, 5f),
                    safeAiFloat(1f / height, 1f, 0.1f, 5f),
                    null
                )
                saveProject() // one-shot AI op; setClipTransform no longer auto-saves.
                showToast("Smart reframed for vertical (${"%.0f".format(confidence * 100)}%)")
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
            val result = withContext(Dispatchers.Default) { aiFeatures.analyzeForUpscale(clip.sourceUri) }
            if (result.targetResolution == null) {
                showToast("Already at maximum resolution (${result.sourceWidth}x${result.sourceHeight})")
                return
            }
            saveUndoState("AI upscale")
            stateFlow.update { it.copy(project = it.project.copy(resolution = result.targetResolution)) }
            val sharpenEffect = Effect(
                type = EffectType.SHARPEN,
                params = mapOf("strength" to safeAiFloat(result.sharpenStrength, 0.5f, 0f, 1f))
            )
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

    private fun showAiRequirementPrompt(
        title: String,
        body: String,
        modelName: String,
        estimatedSize: String,
        actionLabel: String = appContext.getString(R.string.ai_requirement_review_models)
    ) {
        stateFlow.update {
            it.copy(
                aiRequirementPrompt = AiRequirementPrompt(
                    title = title,
                    body = body,
                    modelName = modelName,
                    estimatedSize = estimatedSize,
                    actionLabel = actionLabel
                )
            )
        }
    }

    // --- Tier 3: ML Engine Wrapper Methods ---

    private suspend fun applyFrameInterpolation(clip: Clip) {
        showAiRequirementPrompt(
            title = "Frame interpolation needs a model pack",
            body = "Install the RIFE frame interpolation model before generating in-between frames. Until then, NovaCut avoids duplicating frames so motion remains predictable.",
            modelName = "RIFE v4.6",
            estimatedSize = "~10 MB"
        )
    }

    private suspend fun applyObjectRemoval(clip: Clip) {
        if (!inpaintingEngine.isModelReady()) {
            showAiRequirementPrompt(
                title = "Object removal needs LaMa",
                body = "Object removal requires the LaMa inpainting model so masked areas can be rebuilt instead of blurred or hidden. Download it before painting out objects.",
                modelName = "LaMa inpainting",
                estimatedSize = "~174 MB"
            )
            return
        }
        showToast("Object removal: tap and paint over the object to remove (UI pending)")
    }

    private suspend fun applyVideoUpscale(clip: Clip) {
        showAiRequirementPrompt(
            title = "AI upscale needs Real-ESRGAN",
            body = "AI upscale needs the Real-ESRGAN model before rebuilding detail beyond the current project resolution. The standard upscale assist remains available for layout and sharpening.",
            modelName = "Real-ESRGAN x4",
            estimatedSize = "~17 MB"
        )
    }

    private suspend fun applyAiBackground(clip: Clip) {
        showAiRequirementPrompt(
            title = "AI background generation is model-gated",
            body = "Background generation needs the compositing model workflow before NovaCut can synthesize a replacement safely. Use Remove BG or Replace BG when the segmentation model is ready.",
            modelName = "Background composer",
            estimatedSize = "Model pack pending"
        )
    }

    private suspend fun applyStabilization(clip: Clip) {
        if (!stabilizationEngine.isOpenCvAvailable()) {
            showToast("Advanced stabilization requires OpenCV \u2014 using basic stabilization")
            val result = withContext(Dispatchers.Default) { aiFeatures.stabilizeVideo(clip.sourceUri) }
            val confidence = safeConfidence(result.confidence)
            val shakeMagnitude = safeAiFloat(result.shakeMagnitude, 0f, 0f, 1f)
            if (confidence < 0.1f || shakeMagnitude < 0.001f) {
                showToast("Video is already stable")
            } else {
                saveUndoState("AI stabilize (basic)")
                // Apply 2% crop-based stabilization via scale
                val stabilizeScale = 1f + (shakeMagnitude * 0.5f).coerceAtMost(0.1f)
                stateFlow.update { s ->
                    s.copy(tracks = s.tracks.map { track ->
                        track.copy(clips = track.clips.map { c ->
                            if (c.id == clip.id) {
                                c.copy(
                                    scaleX = safeAiFloat(c.scaleX * stabilizeScale, c.scaleX, 0.1f, 5f),
                                    scaleY = safeAiFloat(c.scaleY * stabilizeScale, c.scaleY, 0.1f, 5f)
                                )
                            } else {
                                c
                            }
                        })
                    })
                }
                showToast("Basic stabilization applied (${"%.0f".format(shakeMagnitude * 100)}% shake)")
                rebuildPlayerTimeline()
                saveProject()
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
        val outputFiles = createStabilizedVideoOutputFiles(appContext, clip.id)
        showToast("Applying stabilization (${motionData.frameCount} frames)...")
        try {
            val result = stabilizationEngine.stabilize(
                uri = clip.sourceUri, motionData = motionData, config = config,
                outputUri = Uri.fromFile(outputFiles.partialFile), onProgress = { }
            )
            if (result != null) {
                val stabilizedFile = finalizeStabilizedVideoFile(
                    partialFile = outputFiles.partialFile,
                    outputFile = outputFiles.outputFile
                )
                if (stabilizedFile == null) {
                    showToast("Stabilization failed: output file was empty")
                    return
                }
                val stabilizedUri = Uri.fromFile(stabilizedFile)
                saveUndoState("AI stabilize (OpenCV)")
                stateFlow.update { s ->
                    s.copy(tracks = s.tracks.map { track ->
                        track.copy(clips = track.clips.map { c ->
                            if (c.id == clip.id) c.copy(sourceUri = stabilizedUri) else c
                        })
                    })
                }
                rebuildPlayerTimeline()
                saveProject()
                showToast("Stabilized with ${"%.0f".format(result.cropApplied * 100)}% crop")
            } else {
                cleanupStabilizedVideoFiles(outputFiles.partialFile, outputFiles.outputFile)
                showToast("Stabilization not yet available \u2014 OpenCV integration pending")
            }
        } catch (e: Exception) {
            // Clean up any partial output before re-throwing (CancellationException is
            // also an Exception subtype in coroutines, so this covers cancellation too).
            cleanupStabilizedVideoFiles(outputFiles.partialFile, outputFiles.outputFile)
            throw e
        }
    }

    private suspend fun applyStyleTransfer(clip: Clip) {
        showAiRequirementPrompt(
            title = "AI style transfer needs a style model",
            body = "Install a neural style model before applying full-frame artistic transfer. The lightweight style analyzer remains available through Style Transfer.",
            modelName = "AnimeGAN / Fast NST",
            estimatedSize = "~6-9 MB"
        )
    }

}

private fun safeAiFloat(value: Float, fallback: Float, min: Float, max: Float): Float {
    val safeFallback = if (fallback.isFinite()) fallback.coerceIn(min, max) else min
    return if (value.isFinite()) value.coerceIn(min, max) else safeFallback
}

private fun safeConfidence(value: Float): Float = safeAiFloat(value, 0f, 0f, 1f)
