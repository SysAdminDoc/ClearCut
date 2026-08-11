package com.novacut.editor.ui.editor

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.novacut.editor.R
import com.novacut.editor.ai.AiFeatures
import com.novacut.editor.ai.CaptionEntry
import com.novacut.editor.ai.CaptionOutcome
import com.novacut.editor.ai.CaptionSource
import com.novacut.editor.engine.*

import com.novacut.editor.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    private val getSelectedMask: () -> Mask?,
    private val setClipTransform: (String, Float?, Float?, Float?, Float?, Float?) -> Unit,
    private val rebuildPlayerTimeline: () -> Unit,
    private val saveProject: () -> Unit,
    private val videoEngine: VideoEngine,
    private val recalculateDuration: (EditorState) -> EditorState,
    private val settingsRepo: SettingsRepository,
    private val recordAiUsage: (AiUsageLedger.Entry) -> Unit
) {
    private var aiJob: Job? = null

    private data class ObjectRemovalOutput(
        val outputUri: Uri,
        val framesProcessed: Int
    )

    private fun text(resId: Int, vararg args: Any): String =
        appContext.getString(resId, *args)

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

    private val strictRequirementTools = setOf(
        "frame_interp",
        "object_remove",
        "video_upscale",
        "ai_background",
        "ai_style_transfer"
    )

    // Whisper model state (exposed for UI binding)
    val whisperModelState get() = aiFeatures.whisperEngine.modelState
    val whisperDownloadProgress get() = aiFeatures.whisperEngine.downloadProgress
    val segmentationModelState get() = aiFeatures.segmentationEngine.modelState
    val segmentationDownloadProgress get() = aiFeatures.segmentationEngine.downloadProgress
    val inpaintingModelState get() = inpaintingEngine.modelState
    val inpaintingDownloadProgress get() = inpaintingEngine.downloadProgress

    fun downloadWhisperModel() {
        scope.launch {
            showToast(text(R.string.ai_whisper_downloading_toast))
            try {
                val success = aiFeatures.whisperEngine.downloadModel(
                    wifiOnly = settingsRepo.settings.first().aiModelWifiOnly
                )
                showToast(
                    text(
                        if (success) {
                            R.string.ai_whisper_ready_toast
                        } else {
                            R.string.ai_model_download_failed_toast
                        }
                    )
                )
            } catch (_: ModelDownloadManager.MeteredNetworkException) {
                showToast(text(R.string.settings_model_wifi_only_feedback))
            }
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
            showToast(text(R.string.ai_segmentation_downloading_toast))
            try {
                val success = aiFeatures.segmentationEngine.downloadModel(
                    wifiOnly = settingsRepo.settings.first().aiModelWifiOnly
                )
                showToast(
                    text(
                        if (success) {
                            R.string.ai_segmentation_ready_toast
                        } else {
                            R.string.ai_model_download_failed_toast
                        }
                    )
                )
            } catch (_: ModelDownloadManager.MeteredNetworkException) {
                showToast(text(R.string.settings_model_wifi_only_feedback))
            }
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

    fun downloadInpaintingModel() {
        scope.launch {
            showToast(text(R.string.ai_inpainting_downloading_toast))
            try {
                val success = inpaintingEngine.downloadModel(
                    wifiOnly = settingsRepo.settings.first().aiModelWifiOnly
                )
                showToast(
                    text(
                        if (success) {
                            R.string.ai_inpainting_ready_toast
                        } else {
                            R.string.ai_model_download_failed_toast
                        }
                    )
                )
            } catch (_: ModelDownloadManager.MeteredNetworkException) {
                showToast(text(R.string.settings_model_wifi_only_feedback))
            }
        }
    }

    fun deleteInpaintingModel() {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { inpaintingEngine.deleteModel() }.isSuccess
            }
            showToast(
                appContext.getString(
                    if (success) {
                        R.string.ai_inpainting_removed_toast
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
                showToast(text(R.string.ai_template_saved_toast, template.name))
            } catch (e: Exception) {
                Log.e("AiToolsDelegate", "Failed to save template", e)
                showToast(text(R.string.ai_template_save_failed_toast))
            }
        }
    }

    fun runAiTool(toolId: String) {
        runAiTool(toolId = toolId, requirePreflight = true)
    }

    /**
     * The requirement sheet's Run action. The tool is marked as already acknowledged so
     * that a still-unmet requirement reports itself rather than re-opening the sheet
     * the user just acted on.
     */
    fun runAiToolAfterRequirement(toolId: String) {
        acknowledgedRequirementToolId = toolId
        runAiTool(toolId = toolId, requirePreflight = false)
    }

    /** The tool whose requirement sheet the user has just pressed Run in, if any. */
    private var acknowledgedRequirementToolId: String? = null

    /** Name the specific thing that is still missing, in the user's language. */
    private fun unmetRequirementMessage(
        requirement: AiToolRequirements.ToolRequirement
    ): String = when (requirement.availability) {
        AiToolRequirements.Availability.MODEL_DOWNLOAD_REQUIRED ->
            text(R.string.ai_requirement_unmet_download, requirement.modelDisplayName)
        AiToolRequirements.Availability.DEPENDENCY_MISSING ->
            text(R.string.ai_requirement_unmet_dependency, requirement.modelDisplayName)
        else -> text(R.string.ai_requirement_unmet_generic, requirement.modelDisplayName)
    }

    fun showAiModelRequirement(toolId: String) {
        val requirement = resolveAiModelRequirement(toolId) ?: return
        showAiModelRequirement(requirement)
    }

    private fun runAiTool(toolId: String, requirePreflight: Boolean) {
        val clip = getSelectedClip()
        if (clip == null) {
            showToast(text(R.string.ai_select_clip_first_toast))
            return
        }
        getToolCompatibilityMessage(toolId, clip)?.let { incompatibilityMessage ->
            showToast(incompatibilityMessage)
            return
        }

        val clipId = clip.id

        if (requirePreflight && toolId in strictRequirementTools) {
            val requirement = resolveAiModelRequirement(toolId)
            if (requirement != null && requirement.availability != AiToolRequirements.Availability.READY) {
                showAiModelRequirement(requirement)
                return
            }
        }

        // EU AI Act Article 50: show a one-time per-session disclosure notice before the
        // first in-scope AI tool use. Exempt tools (captions, colour, denoise) skip this.
        if (AiDisclosurePolicy.isInScope(toolId) && !stateFlow.value.ai.hasShownArticle50Disclosure) {
            stateFlow.update { it.copyAi { ai -> ai.copy(hasShownArticle50Disclosure = true) } }
            showToast(text(R.string.ai_disclosure_notice))
        }

        // Cancel the previous job FIRST so its finally block (which clears aiProcessingTool)
        // runs before we publish our new state — otherwise a trailing `aiProcessingTool = null`
        // from the cancelled job could race-overwrite our own update and hide the progress indicator.
        aiJob?.cancel()
        stateFlow.update {
            it.copyAi { ai ->
                ai.copy(processingTool = toolId, processingProgress = 0f)
            }
        }

        lateinit var thisJob: kotlinx.coroutines.Job
        thisJob = scope.launch {
            try {
                // Re-validate clip still exists (user may have deleted it)
                val currentClip = stateFlow.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
                if (currentClip == null) {
                    showToast(text(R.string.ai_clip_missing_toast))
                    return@launch
                }
                when (toolId) {
                    "scene_detect" -> runSceneDetect(currentClip)
                    "auto_captions" -> runAutoCaptions(currentClip)
                    "smart_crop" -> runSmartCrop(currentClip)
                    "auto_color" -> runAutoColor(currentClip)
                    "stabilize" -> applyStabilization(currentClip)
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
                    else -> showToast(text(R.string.ai_unknown_tool_toast))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                showToast(text(R.string.ai_tool_cancelled_toast))
                throw e
            } catch (e: Exception) {
                Log.e("AiToolsDelegate", "AI tool failed", e)
                showToast(text(R.string.ai_tool_failed_toast))
            } finally {
                // Only clear progress state if we are still the active job — protects against
                // a stale cancelled job overwriting the newly-launched one's progress indicator.
                if (aiJob === thisJob) {
                    stateFlow.update {
                        it.copyAi { ai ->
                            ai.copy(processingTool = null, processingProgress = 0f)
                        }
                    }
                    aiJob = null
                }
            }
        }
        aiJob = thisJob
    }

    fun cancelAiTool() {
        aiJob?.cancel()
    }

    private fun recordAiUsageForClip(
        clip: Clip,
        effectKind: AiUsageLedger.EffectKind,
        modelName: String
    ) {
        recordAiUsage(AiUsageRecordFactory.forClip(clip, effectKind, modelName))
    }

    private fun getToolCompatibilityMessage(toolId: String, clip: Clip): String? {
        return when {
            toolId in audioRequiredTools && !videoEngine.hasAudioTrack(clip.sourceUri) -> {
                if (toolId == "auto_captions") {
                    text(R.string.ai_auto_captions_audio_required_toast)
                } else {
                    text(R.string.ai_denoise_audio_required_toast)
                }
            }
            toolId in motionVideoRequiredTools && !videoEngine.isMotionVideo(clip.sourceUri) -> {
                text(R.string.ai_video_clip_required_toast)
            }
            toolId in visualRequiredTools && !videoEngine.hasVisualTrack(clip.sourceUri) -> {
                text(R.string.ai_visual_clip_required_toast)
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
            showToast(text(R.string.ai_no_scene_changes_toast))
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
        showToast(text(R.string.ai_scene_split_toast, splitOffsets.size + 1))
    }

    private suspend fun runAutoCaptions(clip: Clip) {
        val useWhisper = aiFeatures.whisperEngine.isReady()
        if (useWhisper) showToast(text(R.string.ai_transcribing_whisper_toast))
        val outcome = withContext(Dispatchers.Default) { aiFeatures.generateAutoCaptions(clip.sourceUri) }
        // A transcription that crashed is not silence. Reporting the two the same way
        // told the user their audio was empty when in fact nothing was ever heard.
        val captions = when (outcome) {
            is CaptionOutcome.Failed -> {
                showToast(text(R.string.ai_caption_analysis_failed_toast, outcome.reason))
                return
            }
            is CaptionOutcome.Analyzed -> outcome.captions
        }
        if (captions.isEmpty()) {
            showToast(text(R.string.ai_no_speech_detected_toast))
            return
        }
        // Energy segmentation measures when the audio got loud. It produces no words
        // at all, and it used to fabricate numbered placeholder text to fill the gap.
        // That invented text was saved into the project, drawn in the preview, burned into the
        // exported video and written to the SRT. The timing is a real measurement, so
        // it is committed as timeline markers, and the missing transcript is said out
        // loud rather than papered over.
        if (captions.all { it.source == CaptionSource.TIMING_ONLY }) {
            commitSpeechTimingMarkers(captions)
            return
        }

        saveUndoState("AI auto captions")
        val overlays = withContext(Dispatchers.Default) { AiFeatures.captionsToOverlays(captions) }
        stateFlow.update { state ->
            val existing = state.textOverlays
            val deduped = overlays.filter { new ->
                existing.none { old ->
                    old.startTimeMs < new.endTimeMs && old.endTimeMs > new.startTimeMs
                }
            }
            state.copy(textOverlays = existing + deduped)
        }
        saveProject()
        val source = if (useWhisper) {
            text(R.string.ai_caption_source_whisper)
        } else {
            text(R.string.ai_caption_source_energy_detection)
        }
        showToast(text(R.string.ai_captions_added_toast, captions.size, source))
    }

    /**
     * Commit timing-only speech segments as timeline markers. This is everything the
     * energy detector actually knows: where speech starts. No caption, no overlay, no
     * subtitle cue — nothing that would read as a transcript downstream.
     */
    private fun commitSpeechTimingMarkers(captions: List<CaptionEntry>) {
        val label = text(R.string.ai_speech_marker_label)
        val existing = stateFlow.value.timelineMarkers
        val markers = captions
            .filter { caption -> existing.none { it.timeMs == caption.startMs && it.label == label } }
            .map { caption ->
                TimelineMarker(timeMs = caption.startMs, label = label, color = MarkerColor.YELLOW)
            }
        if (markers.isEmpty()) {
            showToast(text(R.string.ai_caption_timing_only_no_new_toast))
            return
        }
        saveUndoState("AI speech timing markers")
        stateFlow.update { state ->
            state.copy(
                timelineMarkers = (state.timelineMarkers + markers).sortedBy { it.timeMs }
            )
        }
        saveProject()
        showToast(text(R.string.ai_caption_timing_only_toast, markers.size))
    }

    private suspend fun runSmartCrop(clip: Clip) {
        val suggestion = withContext(Dispatchers.Default) { aiFeatures.suggestCrop(
            clip.sourceUri, stateFlow.value.project.aspectRatio.toFloat()
        ) }
        val confidence = safeConfidence(suggestion.confidence)
        if (confidence < 0.1f) {
            showToast(text(R.string.ai_crop_analyze_failed_toast))
            return
        }
        val centerX = safeAiFloat(suggestion.centerX, 0.5f, 0f, 1f)
        val centerY = safeAiFloat(suggestion.centerY, 0.5f, 0f, 1f)
        saveUndoState("AI smart crop")
        setClipTransform(clip.id, centerX - 0.5f, centerY - 0.5f, null, null, null)
        // setClipTransform no longer auto-saves (it's called per-tick from drag); AI
        // tool invocations are one-shot, so persist explicitly after the change.
        saveProject()
        showToast(text(R.string.ai_smart_crop_applied_toast, confidence * 100))
    }

    private suspend fun runAutoColor(clip: Clip) {
        val correction = withContext(Dispatchers.Default) { aiFeatures.autoColorCorrect(clip.sourceUri) }
        if (safeConfidence(correction.confidence) < 0.1f) {
            showToast(text(R.string.ai_color_analyze_failed_toast))
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
            showToast(text(R.string.ai_colors_ok_toast))
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
        showToast(text(R.string.ai_color_corrections_applied_toast, newEffects.size))
    }

    private suspend fun runDenoise(clip: Clip) {
        val profile = withContext(Dispatchers.Default) { aiFeatures.analyzeAudioNoise(clip.sourceUri) }
        val confidence = safeConfidence(profile.confidence)
        val signalToNoiseDb = safeAiFloat(profile.signalToNoiseDb, 60f, -120f, 120f)
        val recommendedReduction = safeAiFloat(profile.recommendedReduction, 0f, 0f, 1f)
        if (confidence < 0.1f) {
            showToast(text(R.string.ai_audio_noise_analyze_failed_toast))
            return
        }
        if (signalToNoiseDb > 40f) {
            showToast(text(R.string.ai_audio_clean_toast, signalToNoiseDb))
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
        showToast(text(R.string.ai_denoised_summary_toast, signalToNoiseDb, recommendedReduction * 100))
    }

    private suspend fun runRemoveBg(clip: Clip) {
        val segEngine = aiFeatures.segmentationEngine
        if (segEngine.isReady()) {
            val result = withContext(Dispatchers.Default) { segEngine.segmentVideoFrame(clip.sourceUri) }
            if (result == null || safeConfidence(result.confidence) < 0.05f) {
                showToast(text(R.string.ai_subject_not_detected_toast))
                return
            }
            saveUndoState("AI remove background")
            val bgEffect = Effect(type = EffectType.BG_REMOVAL, params = mapOf("threshold" to 0.5f))
            updateClipEffect(clip, bgEffect, setOf(EffectType.BG_REMOVAL, EffectType.CHROMA_KEY))
            recordAiUsageForClip(
                clip = clip,
                effectKind = AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL,
                modelName = "MediaPipe Selfie Segmenter"
            )
            showToast(text(R.string.ai_background_removed_toast, safeConfidence(result.confidence) * 100))
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
                recordAiUsageForClip(
                    clip = clip,
                    effectKind = AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL,
                    modelName = "MediaPipe Selfie Segmenter"
                )
                showToast(text(R.string.ai_background_replace_ready_toast))
            } else {
                showToast(text(R.string.ai_subject_not_detected_toast))
            }
        } else {
            applyChromaKeyFallback(clip, "replace")
        }
    }

    private suspend fun applyChromaKeyFallback(clip: Clip, action: String) {
        val analysis = withContext(Dispatchers.Default) { aiFeatures.analyzeBackground(clip.sourceUri) }
        val confidence = safeConfidence(analysis.confidence)
        if (confidence < 0.1f) {
            showToast(text(R.string.ai_background_not_detected_toast))
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
        recordAiUsageForClip(
            clip = clip,
            effectKind = AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL,
            modelName = "ClearCut background analyzer"
        )
        val bgType = when {
            analysis.isGreenScreen -> text(R.string.ai_bg_type_green_screen)
            analysis.isBlueScreen -> text(R.string.ai_bg_type_blue_screen)
            else -> text(R.string.ai_bg_type_background)
        }
        val actionText = when (action) {
            "replace" -> text(R.string.ai_bg_action_replace)
            else -> text(R.string.ai_bg_action_removal)
        }
        showToast(text(R.string.ai_background_fallback_applied_toast, bgType, actionText, confidence * 100))
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
                showToast(text(R.string.ai_motion_tracking_empty_toast))
                return
            }
            saveUndoState("AI motion track")
            val posKeyframes = buildTrackingKeyframes(results, clip, invertSign = false, yBaseline = 0.5f)
            addPositionKeyframes(clip, posKeyframes)
            showToast(text(R.string.ai_motion_tracking_applied_toast, results.size))
        } catch (e: Exception) {
            Log.e("AiToolsDelegate", "Motion tracking failed", e)
            showToast(text(R.string.ai_motion_tracking_failed_toast))
        }
    }

    private suspend fun runStyleTransfer(clip: Clip) {
        try {
            showToast(text(R.string.ai_style_analyzing_toast))
            val style = withContext(Dispatchers.Default) { aiFeatures.analyzeAndApplyStyle(clip.sourceUri) }
            if (safeConfidence(style.confidence) < 0.1f) {
                showToast(text(R.string.ai_frame_style_analyze_failed_toast))
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
                showToast(text(R.string.ai_style_adjustments_not_needed_toast, style.styleName))
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
            saveProject()
            recordAiUsageForClip(
                clip = clip,
                effectKind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL,
                modelName = "ClearCut style analyzer"
            )
            showToast(text(R.string.ai_style_applied_toast, style.styleName, newEffects.size))
        } catch (e: Exception) {
            Log.e("AiToolsDelegate", "Style transfer failed", e)
            showToast(text(R.string.ai_style_transfer_failed_toast))
        }
    }

    private suspend fun runFaceTrack(clip: Clip) {
        try {
            showToast(text(R.string.ai_face_tracking_detecting_toast))
            val region = com.novacut.editor.ai.TrackingRegion(centerX = 0.5f, centerY = 0.35f, width = 0.3f, height = 0.3f)
            val results = withContext(Dispatchers.Default) { aiFeatures.trackMotion(clip.sourceUri, region, clip.trimStartMs, clip.trimEndMs) }
            if (results.isNotEmpty()) {
                saveUndoState("AI face track")
                val posKeyframes = buildTrackingKeyframes(results, clip, invertSign = true, yBaseline = 0.35f)
                addPositionKeyframes(clip, posKeyframes)
                showToast(text(R.string.ai_face_tracked_toast, results.size))
            } else {
                showToast(text(R.string.ai_face_not_detected_toast))
            }
        } catch (e: Exception) {
            Log.e("AiToolsDelegate", "Face tracking failed", e)
            showToast(text(R.string.ai_face_tracking_failed_toast))
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
                showToast(text(R.string.ai_smart_reframe_applied_toast, confidence * 100))
            } else {
                showToast(text(R.string.ai_smart_reframe_region_failed_toast))
            }
        } catch (e: Exception) {
            Log.e("AiToolsDelegate", "Smart reframe failed", e)
            showToast(text(R.string.ai_smart_reframe_failed_toast))
        }
    }

    private suspend fun runUpscale(clip: Clip) {
        try {
            showToast(text(R.string.ai_upscale_analyzing_toast))
            val result = withContext(Dispatchers.Default) { aiFeatures.analyzeForUpscale(clip.sourceUri) }
            if (result.targetResolution == null) {
                showToast(text(R.string.ai_upscale_max_resolution_toast, result.sourceWidth, result.sourceHeight))
                return
            }
            saveUndoState("AI upscale")
            stateFlow.update { it.copy(project = it.project.copy(resolution = result.targetResolution)) }
            val sharpenEffect = Effect(
                type = EffectType.SHARPEN,
                params = mapOf("strength" to safeAiFloat(result.sharpenStrength, 0.5f, 0f, 1f))
            )
            updateClipEffect(clip, sharpenEffect, setOf(EffectType.SHARPEN))
            recordAiUsageForClip(
                clip = clip,
                effectKind = AiUsageLedger.EffectKind.UPSCALING_LOCAL,
                modelName = "ClearCut upscale assistant"
            )
            showToast(text(R.string.ai_upscale_applied_toast, result.targetResolution.label))
        } catch (e: Exception) {
            Log.e("AiToolsDelegate", "Upscale failed", e)
            showToast(text(R.string.ai_upscale_failed_toast))
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

    private fun resolveAiModelRequirement(toolId: String): AiToolRequirements.ToolRequirement? {
        val requirement = AiToolRequirements.requirementFor(toolId) ?: return null
        val availability = when (toolId) {
            "frame_interp" -> if (frameInterpolationEngine.isModelReady()) {
                AiToolRequirements.Availability.READY
            } else {
                requirement.availability
            }
            "object_remove" -> if (inpaintingEngine.isModelReady()) {
                AiToolRequirements.Availability.READY
            } else {
                requirement.availability
            }
            "video_upscale" -> if (upscaleEngine.isModelReady()) {
                AiToolRequirements.Availability.READY
            } else {
                requirement.availability
            }
            "ai_stabilize" -> AiToolRequirements.Availability.READY
            else -> requirement.availability
        }
        return requirement.copy(availability = availability)
    }

    private fun showAiModelRequirement(requirement: AiToolRequirements.ToolRequirement) {
        stateFlow.update {
            it.copyAi { ai ->
                ai.copy(
                    modelRequirement = requirement,
                    requirementPrompt = null
                )
            }
        }
    }

    /**
     * Present the requirement for [toolId].
     *
     * Every caller names a tool the registry knows, so the registry sheet is what the
     * user sees. The title/body/model/size text these calls used to pass was hardcoded
     * English that the registry path never read -- it is gone rather than translated.
     * The generic prompt below remains the fallback for a tool id the registry does not
     * cover, and now says so in a localized string.
     */
    private fun showAiRequirementPrompt(toolId: String) {
        val requirement = resolveAiModelRequirement(toolId)
        if (requirement != null) {
            // The requirement sheet's Run button routes back here. Re-presenting the
            // same sheet made Run a loop with no exit: press it, get the sheet you
            // just pressed Run in. When the user has already seen this sheet, say
            // which requirement is still unmet instead of asking again.
            if (acknowledgedRequirementToolId == toolId) {
                acknowledgedRequirementToolId = null
                showToast(unmetRequirementMessage(requirement))
                return
            }
            showAiModelRequirement(requirement)
            return
        }
        stateFlow.update {
            it.copyAi { ai ->
                ai.copy(
                    modelRequirement = null,
                    requirementPrompt = AiRequirementPrompt(
                        title = text(R.string.ai_requirement_unknown_tool_title),
                        body = text(R.string.ai_requirement_unknown_tool_body),
                        modelName = text(R.string.ai_model_size_not_available),
                        estimatedSize = text(R.string.ai_model_size_not_available),
                        actionLabel = text(R.string.ai_requirement_review_models)
                    )
                )
            }
        }
    }

    // --- Tier 3: ML Engine Wrapper Methods ---

    private suspend fun applyFrameInterpolation(clip: Clip) {
        showAiRequirementPrompt(toolId = "frame_interp")
    }

    private suspend fun applyObjectRemoval(clip: Clip) {
        if (!inpaintingEngine.isModelReady()) {
            showAiRequirementPrompt(toolId = "object_remove")
            return
        }

        val mask = getSelectedMask()
        if (mask == null) {
            showToast(text(R.string.ai_object_removal_mask_required_toast))
            return
        }
        if (!InpaintingMaskRenderer.supports(mask)) {
            showToast(text(R.string.ai_object_removal_unsupported_mask_toast))
            return
        }

        showToast(text(R.string.ai_object_removal_processing_toast))
        val output = processObjectRemoval(clip, mask)
        if (output == null) {
            showToast(text(R.string.ai_object_removal_failed_toast))
            return
        }

        saveUndoState("AI object removal")
        stateFlow.update { state ->
            state.copy(
                selectedMaskId = null,
                tracks = state.tracks.map { track ->
                    track.copy(clips = track.clips.map { current ->
                        if (current.id == clip.id) {
                            current.copy(
                                sourceUri = output.outputUri,
                                proxyUri = null,
                                // The selected geometry has been baked into the
                                // new source. Keeping it would apply a second
                                // mask during preview/export.
                                masks = current.masks.filterNot { it.id == mask.id }
                            )
                        } else {
                            current
                        }
                    })
                }
            )
        }
        rebuildPlayerTimeline()
        saveProject()
        recordAiUsageForClip(
            clip = clip,
            effectKind = AiUsageLedger.EffectKind.INPAINTING_LOCAL_LARGE,
            modelName = "LaMa-Dilated"
        )
        showToast(text(R.string.ai_object_removal_applied_toast, output.framesProcessed))
    }

    private suspend fun processObjectRemoval(clip: Clip, mask: Mask): ObjectRemovalOutput? =
        withContext(Dispatchers.IO) {
            val outputDir = managedMediaDir(appContext).also { it.mkdirs() }
            if (videoEngine.isMotionVideo(clip.sourceUri)) {
                val outputFile = File.createTempFile("clearcut-object-removal-", ".mp4", outputDir)
                val outputUri = Uri.fromFile(outputFile)
                val result = inpaintingEngine.inpaintVideo(
                    uri = clip.sourceUri,
                    mask = mask,
                    outputUri = outputUri
                )
                if (result == null) {
                    outputFile.delete()
                    null
                } else {
                    ObjectRemovalOutput(result.outputUri, result.framesProcessed)
                }
            } else {
                val source = decodeBitmap(clip.sourceUri) ?: return@withContext null
                val maskBitmap = InpaintingMaskRenderer.render(
                    mask = mask,
                    timeOffsetMs = 0L,
                    width = source.width,
                    height = source.height
                ) ?: run {
                    source.recycle()
                    return@withContext null
                }
                val outputFile = File.createTempFile("clearcut-object-removal-", ".png", outputDir)
                var outputBitmap: android.graphics.Bitmap? = null
                try {
                    val result = inpaintingEngine.inpaintFrame(source, maskBitmap)
                    if (result == null) {
                        outputFile.delete()
                        return@withContext null
                    }
                    val renderedBitmap = result.outputBitmap
                    outputBitmap = renderedBitmap
                    val written = outputFile.outputStream().use { stream ->
                        renderedBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    if (!written || outputFile.length() <= 0L) {
                        outputFile.delete()
                        return@withContext null
                    }
                    ObjectRemovalOutput(Uri.fromFile(outputFile), 1)
                } finally {
                    outputBitmap?.recycle()
                    maskBitmap.recycle()
                    source.recycle()
                }
            }
        }

    private fun decodeBitmap(uri: Uri): android.graphics.Bitmap? {
        return runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull() ?: uri.path?.let(BitmapFactory::decodeFile)
    }

    private suspend fun applyVideoUpscale(clip: Clip) {
        showAiRequirementPrompt(toolId = "video_upscale")
    }

    private suspend fun applyAiBackground(clip: Clip) {
        showAiRequirementPrompt(toolId = "ai_background")
    }

    private suspend fun applyStabilization(clip: Clip) {
        val config = StabilizationEngine.StabilizationConfig(
            smoothingStrength = 0.5f, cropPercentage = 0.15f,
            algorithm = StabilizationEngine.StabilizationConfig.Algorithm.LK_OPTICAL_FLOW
        )
        val capability = withContext(Dispatchers.Default) {
            stabilizationEngine.capability(clip.sourceUri, config)
        }
        if (!capability.supported) {
            showToast(
                text(
                    R.string.ai_stabilization_unavailable_reason,
                    capability.reason ?: text(R.string.ai_stabilization_unavailable_toast),
                )
            )
            return
        }
        showToast(text(R.string.ai_camera_motion_analyzing_toast))
        val motionData = stabilizationEngine.analyzeMotion(
            uri = clip.sourceUri,
            config = config,
            onProgress = { progress ->
                stateFlow.update { state ->
                    state.copyAi { ai -> ai.copy(processingProgress = progress.coerceIn(0f, 1f)) }
                }
            },
        )
        if (motionData == null) {
            showToast(text(R.string.ai_motion_analysis_failed_toast))
            return
        }
        if (motionData.frameCount < 2 || motionData.smoothedTransforms.isEmpty()) {
            showToast(text(R.string.ai_motion_analysis_failed_toast))
            return
        }
        stateFlow.update {
            it.copyAi { ai ->
                ai.copy(
                    stabilizationPreview = StabilizationPreview(
                        clipId = clip.id,
                        sourceName = clip.name ?: clip.sourceUri.lastPathSegment ?: "clip",
                        motionData = motionData,
                        config = config,
                    ),
                    processingProgress = 1f,
                )
            }
        }
        showToast(text(R.string.ai_stabilization_preview_ready_toast))
    }

    fun dismissStabilizationPreview() {
        stateFlow.update { it.copyAi { ai -> ai.copy(stabilizationPreview = null) } }
    }

    fun applyStabilizationPreview() {
        val preview = stateFlow.value.ai.stabilizationPreview ?: return
        val clip = stateFlow.value.tracks
            .asSequence()
            .flatMap { it.clips.asSequence() }
            .firstOrNull { it.id == preview.clipId }
        if (clip == null) {
            dismissStabilizationPreview()
            showToast(text(R.string.ai_clip_missing_toast))
            return
        }

        val stabilizationData = StabilizationData(
            motion = preview.motionData.smoothedTransforms.map { transform ->
                StabilizationMotionPoint(
                    timestampMs = transform.timestampMs,
                    dx = transform.dx.coerceIn(-1f, 1f),
                    dy = transform.dy.coerceIn(-1f, 1f),
                    confidence = transform.confidence.coerceIn(0f, 1f),
                )
            },
            lensProfile = StabilizationLensProfile(
                name = preview.motionData.lensProfile.name,
                focalLengthMm = preview.motionData.lensProfile.focalLengthMm,
                distortionK1 = preview.motionData.lensProfile.distortionK1,
                distortionK2 = preview.motionData.lensProfile.distortionK2,
            ),
            syncOffsetMs = preview.motionData.syncOffsetMs,
            cropScale = preview.motionData.recommendedCropScale.coerceIn(1f, 1.3f),
            sourceDurationMs = preview.motionData.sourceDurationMs.coerceAtLeast(0L),
        )
        if (!stabilizationData.isUsable) {
            dismissStabilizationPreview()
            showToast(text(R.string.ai_motion_analysis_failed_toast))
            return
        }

        saveUndoState("Apply offline stabilization")
        stateFlow.update { state ->
            val updatedTracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { current ->
                    if (current.id == clip.id) current.copy(stabilizationData = stabilizationData) else current
                })
            }
            recalculateDuration(
                state.copyAi { ai -> ai.copy(stabilizationPreview = null) }
                    .copy(tracks = updatedTracks)
            )
        }
        rebuildPlayerTimeline()
        saveProject()
        recordAiUsageForClip(
            clip = clip,
            effectKind = AiUsageLedger.EffectKind.STABILIZATION_LOCAL,
            modelName = "ClearCut offline motion stabilization",
        )
        showToast(
            text(
                R.string.ai_stabilized_summary_toast,
                preview.motionData.averageShakeMagnitude * 100f,
                (preview.motionData.recommendedCropScale - 1f) * 100f,
            )
        )
    }

    private suspend fun applyStyleTransfer(clip: Clip) {
        showAiRequirementPrompt(toolId = "ai_style_transfer")
    }

}

private fun safeAiFloat(value: Float, fallback: Float, min: Float, max: Float): Float {
    val safeFallback = if (fallback.isFinite()) fallback.coerceIn(min, max) else min
    return if (value.isFinite()) value.coerceIn(min, max) else safeFallback
}

private fun safeConfidence(value: Float): Float = safeAiFloat(value, 0f, 0f, 1f)
