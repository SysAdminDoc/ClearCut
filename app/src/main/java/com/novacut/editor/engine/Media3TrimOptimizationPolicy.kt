package com.novacut.editor.engine

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
 * samples, or describe an MP4 trim with an edit list. Both optimizations are
 * deliberately opt-in here: a timeline must still be a single MP4 asset with
 * no edit that changes its decoded content. Media3 remains responsible for
 * checking codec/profile compatibility and falling back to the normal encoder
 * when the source cannot be optimized.
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
        val mp4EditListTrimEligible: Boolean = false,
    )

    /**
     * The edit-list mode is stricter than trim optimization: it is only safe
     * when the Transformer can transmux every track without decoding. The
     * caller supplies the probed source format and the requested output shape
     * so this remains a pure, JVM-testable decision.
     */
    data class InputFormat(
        val videoMimeType: String,
        val videoWidth: Int,
        val videoHeight: Int,
        val videoFrameRate: Float?,
        val audioMimeType: String?,
    )

    fun evaluate(
        tracks: List<Track>,
        config: ExportConfig,
        inputMimeType: String? = null,
        inputFormat: InputFormat? = null,
        outputExtension: String = "mp4",
        outputWidth: Int? = null,
        outputHeight: Int? = null,
        outputFrameRate: Int? = null,
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
            config.includeChapterMarkers || config.hdr10PlusMetadata
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
            clip.positionX != 0f || clip.positionY != 0f ||
            clip.anchorX != 0.5f || clip.anchorY != 0.5f ||
            clip.blendMode != BlendMode.NORMAL
        ) {
            return Decision(false, Reason.VIDEO_EDIT)
        }
        if (!clip.rotation.isFinite() || clip.rotation % 90f != 0f) {
            return Decision(false, Reason.UNSUPPORTED_ROTATION)
        }

        val editListTrimEligible = inputFormat != null &&
            inputFormat.videoMimeType.equals(config.codec.mimeType, ignoreCase = true) &&
            inputFormat.videoWidth > 0 && inputFormat.videoWidth == outputWidth &&
            inputFormat.videoHeight > 0 && inputFormat.videoHeight == outputHeight &&
            inputFormat.videoFrameRate != null && outputFrameRate != null &&
            inputFormat.videoFrameRate <= outputFrameRate.toFloat() + 0.01f &&
            (inputFormat.audioMimeType == null ||
                inputFormat.audioMimeType.equals(config.audioCodec.mimeType, ignoreCase = true)) &&
            clip.rotation == 0f

        return Decision(
            eligible = true,
            reason = Reason.ELIGIBLE,
            mp4EditListTrimEligible = editListTrimEligible,
        )
    }
}
