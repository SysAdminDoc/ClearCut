package com.novacut.editor.engine

import com.novacut.editor.model.EffectType
import com.novacut.editor.model.AudioCodec
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType

/**
 * Conservative gate for Media3's partial-MP4 resume API.
 *
 * A partial file is only meaningful when the composition can be rebuilt from
 * the same single, gap-free A/V source. Keeping this policy pure makes the
 * boundary testable without a Transformer or a device codec.
 */
object ExportResumePolicy {
    enum class Reason {
        ELIGIBLE,
        NOT_MP4,
        NOT_SINGLE_AV_SEQUENCE,
        TIMELINE_NOT_CONTINUOUS,
        SPECIAL_EXPORT,
        OVERLAYS_OR_AUTOMATION,
        SPEED_CHANGE,
        UNSUPPORTED_AUDIO_CODEC,
    }

    data class Decision(
        val eligible: Boolean,
        val reason: Reason,
    )

    fun evaluate(
        tracks: List<Track>,
        config: ExportConfig,
        outputExtension: String = "mp4",
        textOverlayCount: Int = 0,
        imageOverlayCount: Int = 0,
        trackedObjectCount: Int = 0,
        globalTransitionCount: Int = 0,
    ): Decision {
        if (!outputExtension.equals("mp4", ignoreCase = true) || config.transparentBackground) {
            return Decision(false, Reason.NOT_MP4)
        }
        if (config.exportAudioOnly || config.exportStemsOnly || config.exportAsGif ||
            config.captureFrameOnly || config.exportAsContactSheet
        ) {
            return Decision(false, Reason.SPECIAL_EXPORT)
        }
        if (config.subtitleFormat != null || config.burnSubtitles || config.watermark != null ||
            config.timelineRange != null || config.chapters.isNotEmpty()
        ) {
            return Decision(false, Reason.SPECIAL_EXPORT)
        }
        if (config.audioCodec != AudioCodec.AAC) {
            return Decision(false, Reason.UNSUPPORTED_AUDIO_CODEC)
        }
        if (textOverlayCount > 0 || imageOverlayCount > 0 || trackedObjectCount > 0 ||
            globalTransitionCount > 0
        ) {
            return Decision(false, Reason.OVERLAYS_OR_AUTOMATION)
        }

        val clips = tracks.flatMap { it.clips }
        val visualTracks = tracks.filter { it.clips.isNotEmpty() }
        if (clips.size != 1 || visualTracks.size != 1 || visualTracks.single().type != TrackType.VIDEO) {
            return Decision(false, Reason.NOT_SINGLE_AV_SEQUENCE)
        }
        val track = visualTracks.single()
        val clip = clips.single()
        if (clip.timelineStartMs != 0L || clip.durationMs <= 0L || clip.isCompound ||
            clip.compoundClips.isNotEmpty()
        ) {
            return Decision(false, Reason.TIMELINE_NOT_CONTINUOUS)
        }
        if (track.pan != 0f || track.audioEffects.any { it.enabled } ||
            clip.audioEffects.any { it.enabled }
        ) {
            return Decision(false, Reason.OVERLAYS_OR_AUTOMATION)
        }
        if (clip.speed != 1f || clip.speedCurve != null || clip.isReversed ||
            clip.effects.any { it.enabled && it.type in setOf(EffectType.SPEED, EffectType.REVERSE) }
        ) {
            return Decision(false, Reason.SPEED_CHANGE)
        }
        return Decision(true, Reason.ELIGIBLE)
    }
}
