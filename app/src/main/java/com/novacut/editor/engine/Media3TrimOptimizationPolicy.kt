package com.novacut.editor.engine

import androidx.media3.transformer.ExportResult
import androidx.media3.common.util.UnstableApi
import com.novacut.editor.model.AudioCodec
import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType

/**
 * Conservative gate for Media3's trim-only optimizations.
 *
 * Media3 can stitch a small re-encoded trim boundary to the original encoded
 * samples. The optimization is deliberately opt-in here: a timeline must
 * still be a single MP4 asset with no edit that changes its decoded content.
 * Media3 remains responsible for checking codec/profile compatibility and
 * falling back to the normal encoder when the source cannot be optimized.
 */
object Media3TrimOptimizationPolicy {
    enum class Reason {
        ELIGIBLE,
        NOT_MP4_OUTPUT,
        NOT_MP4_INPUT,
        NOT_SINGLE_VIDEO_ASSET,
        NO_TRIM,
        TIMELINE_NOT_CONTINUOUS,
        SPECIAL_EXPORT,
        OVERLAYS_OR_AUTOMATION,
        SPEED_CHANGE,
        AUDIO_EDIT,
        VIDEO_EDIT,
        UNSUPPORTED_ROTATION,
        RESUME_REQUESTED,
    }

    data class Decision(
        val eligible: Boolean,
        val reason: Reason,
    )

    /** The seven result codes emitted by Media3's trim optimizer. */
    enum class OptimizationOutcome {
        NONE,
        SUCCEEDED,
        ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM,
        ABANDONED_TRIM_AND_TRANSCODING_TRANSFORMATION_REQUESTED,
        ABANDONED_OTHER,
        FAILED_EXTRACTION_FAILED,
        FAILED_FORMAT_MISMATCH,
        UNKNOWN,
    }

    enum class Strategy {
        SMART_TRIM,
        FULL_TRANSCODE,
    }

    /** State shown in the export sheet before and after Transformer completes. */
    data class Disclosure(
        val strategy: Strategy,
        val reason: Reason,
        val outcome: OptimizationOutcome? = null,
    )

    fun disclosureFor(decision: Decision): Disclosure = Disclosure(
        strategy = if (decision.eligible) Strategy.SMART_TRIM else Strategy.FULL_TRANSCODE,
        reason = decision.reason,
    )

    /** Map every current Media3 result code without leaking an integer into the UI. */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun optimizationOutcome(result: Int): OptimizationOutcome = when (result) {
        ExportResult.OPTIMIZATION_NONE -> OptimizationOutcome.NONE
        ExportResult.OPTIMIZATION_SUCCEEDED -> OptimizationOutcome.SUCCEEDED
        ExportResult.OPTIMIZATION_ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM ->
            OptimizationOutcome.ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM
        ExportResult.OPTIMIZATION_ABANDONED_TRIM_AND_TRANSCODING_TRANSFORMATION_REQUESTED ->
            OptimizationOutcome.ABANDONED_TRIM_AND_TRANSCODING_TRANSFORMATION_REQUESTED
        ExportResult.OPTIMIZATION_ABANDONED_OTHER -> OptimizationOutcome.ABANDONED_OTHER
        ExportResult.OPTIMIZATION_FAILED_EXTRACTION_FAILED -> OptimizationOutcome.FAILED_EXTRACTION_FAILED
        ExportResult.OPTIMIZATION_FAILED_FORMAT_MISMATCH -> OptimizationOutcome.FAILED_FORMAT_MISMATCH
        else -> OptimizationOutcome.UNKNOWN
    }

    fun evaluate(
        tracks: List<Track>,
        config: ExportConfig,
        inputMimeType: String? = null,
        outputExtension: String = "mp4",
        textOverlayCount: Int = 0,
        imageOverlayCount: Int = 0,
        lottieOverlayCount: Int = 0,
        trackedObjectCount: Int = 0,
        globalTransitionCount: Int = 0,
        resumeRequested: Boolean = false,
    ): Decision {
        if (!outputExtension.equals("mp4", ignoreCase = true)) {
            return Decision(false, Reason.NOT_MP4_OUTPUT)
        }
        if (resumeRequested) {
            return Decision(false, Reason.RESUME_REQUESTED)
        }

        val nonEmptyTracks = tracks.filter { it.clips.isNotEmpty() }
        val clips = nonEmptyTracks.flatMap { it.clips }
        if (clips.size != 1 || nonEmptyTracks.size != 1 ||
            nonEmptyTracks.single().type != TrackType.VIDEO
        ) {
            return Decision(false, Reason.NOT_SINGLE_VIDEO_ASSET)
        }

        val clip = clips.single()
        val resolvedInputMimeType = inputMimeType
            ?: clip.sourceUri.lastPathSegment
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.takeIf { it.equals("mp4", ignoreCase = true) }
                ?.let { "video/mp4" }
        if (!resolvedInputMimeType.equals("video/mp4", ignoreCase = true)) {
            return Decision(false, Reason.NOT_MP4_INPUT)
        }

        if (config.exportAudioOnly || config.exportStemsOnly || config.exportAsGif ||
            config.captureFrameOnly || config.exportAsContactSheet ||
            config.transparentBackground || config.timelineRange != null ||
            config.subtitleFormat != null || config.burnSubtitles ||
            config.watermark != null || config.chapters.isNotEmpty() ||
            config.includeChapterMarkers || config.hdr10PlusMetadata ||
            config.forceConstantFrameRate
        ) {
            return Decision(false, Reason.SPECIAL_EXPORT)
        }
        if (config.audioCodec != AudioCodec.AAC) {
            return Decision(false, Reason.SPECIAL_EXPORT)
        }
        if (textOverlayCount > 0 || imageOverlayCount > 0 || lottieOverlayCount > 0 ||
            trackedObjectCount > 0 || globalTransitionCount > 0
        ) {
            return Decision(false, Reason.OVERLAYS_OR_AUTOMATION)
        }

        val track = nonEmptyTracks.single()
        if (!track.isVisible || track.isMuted || track.volume != 1f || track.pan != 0f ||
            track.opacity != 1f || track.blendMode != BlendMode.NORMAL ||
            track.audioEffects.any { it.enabled }
        ) {
            return Decision(false, Reason.AUDIO_EDIT)
        }
        if (clip.timelineStartMs != 0L || clip.durationMs <= 0L ||
            clip.isCompound || clip.compoundClips.isNotEmpty() ||
            clip.trimStartMs == 0L && clip.trimEndMs == clip.sourceDurationMs
        ) {
            return Decision(false, if (clip.timelineStartMs != 0L || clip.durationMs <= 0L ||
                clip.isCompound || clip.compoundClips.isNotEmpty()
            ) Reason.TIMELINE_NOT_CONTINUOUS else Reason.NO_TRIM)
        }
        if (clip.speed != 1f || clip.speedCurve != null || clip.isReversed ||
            clip.effects.any { it.enabled && it.type in setOf(EffectType.SPEED, EffectType.REVERSE) }
        ) {
            return Decision(false, Reason.SPEED_CHANGE)
        }
        if (clip.volume != 1f || clip.fadeInMs != 0L || clip.fadeOutMs != 0L ||
            clip.audioEffects.any { it.enabled } || clip.linkedClipId != null
        ) {
            return Decision(false, Reason.AUDIO_EDIT)
        }
        if (clip.effects.any { it.enabled } || clip.colorGrade != null ||
            clip.masks.isNotEmpty() || clip.keyframes.isNotEmpty() ||
            clip.motionTrackingData != null || clip.headTransition != null ||
            clip.tailTransition != null || clip.captions.isNotEmpty() ||
            clip.opacity != 1f || clip.scaleX != 1f || clip.scaleY != 1f ||
            clip.flipHorizontal || clip.flipVertical ||
            clip.positionX != 0f || clip.positionY != 0f ||
            clip.anchorX != 0.5f || clip.anchorY != 0.5f ||
            clip.blendMode != BlendMode.NORMAL
        ) {
            return Decision(false, Reason.VIDEO_EDIT)
        }
        if (!clip.rotation.isFinite() || clip.rotation % 90f != 0f) {
            return Decision(false, Reason.UNSUPPORTED_ROTATION)
        }

        return Decision(
            eligible = true,
            reason = Reason.ELIGIBLE,
        )
    }
}
