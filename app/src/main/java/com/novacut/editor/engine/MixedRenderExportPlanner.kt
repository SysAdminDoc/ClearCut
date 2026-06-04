package com.novacut.editor.engine

import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType

/**
 * Pure planner for the Android mixed-render export path.
 *
 * The composer can describe any copy/re-encode run shape, but FFmpeg concat is
 * only safe to attempt for a narrow runtime envelope today: one visible video
 * track, no separate audio/overlay/adjustment tracks, and no export-side
 * overlays/sidecar formats that would be lost by stream-copy runs. Anything
 * outside that shape falls back to the existing whole-timeline Transformer path.
 */
object MixedRenderExportPlanner {

    fun rejectionReason(
        tracks: List<Track>,
        config: ExportConfig,
        textOverlays: List<TextOverlay> = emptyList(),
        hasImageOverlays: Boolean = false,
        hasLottieOverlays: Boolean = false,
        hasTrackedObjects: Boolean = false,
    ): String? {
        if (!config.allowStreamCopy) return "stream-copy disabled"
        if (textOverlays.isNotEmpty()) return "text overlays present"
        if (hasImageOverlays) return "image overlays present"
        if (hasLottieOverlays) return "Lottie overlays present"
        if (hasTrackedObjects) return "tracked-object overlays present"
        if (config.chapters.isNotEmpty()) return "chapter markers requested"
        if (config.subtitleFormat != null) return "subtitle sidecar requested"
        if (config.transparentBackground) return "transparent export requested"
        if (config.exportAsGif) return "GIF export requested"
        if (config.captureFrameOnly) return "frame capture requested"
        if (config.exportAsContactSheet) return "contact-sheet export requested"
        if (config.exportAudioOnly) return "audio-only export requested"
        if (config.exportStemsOnly) return "stem export requested"
        if (config.watermark != null) return "watermark requested"
        if (config.targetSizeBytes != null) return "target-size bitrate requested"

        val visibleVideoTracks = tracks.filter {
            it.type == TrackType.VIDEO && it.isVisible && it.clips.any { clip -> clip.durationMs > 0L }
        }
        if (visibleVideoTracks.size != 1) return "mixed render requires one visible video track"

        val hasVisibleOverlayTracks = tracks.any {
            it.type == TrackType.OVERLAY && it.isVisible && it.clips.any { clip -> clip.durationMs > 0L }
        }
        if (hasVisibleOverlayTracks) return "overlay track present"

        val hasVisibleAdjustmentTracks = tracks.any {
            it.type == TrackType.ADJUSTMENT && it.isVisible && it.clips.any { clip -> clip.durationMs > 0L }
        }
        if (hasVisibleAdjustmentTracks) return "adjustment track present"

        val hasVisibleAudioTracks = tracks.any {
            it.type == TrackType.AUDIO && it.isVisible && it.clips.any { clip -> clip.durationMs > 0L }
        }
        if (hasVisibleAudioTracks) return "separate audio track present"

        return null
    }

    fun buildPlan(
        tracks: List<Track>,
        config: ExportConfig,
        finalOutputName: String,
        projectStem: String,
        textOverlays: List<TextOverlay> = emptyList(),
        hasImageOverlays: Boolean = false,
        hasLottieOverlays: Boolean = false,
        hasTrackedObjects: Boolean = false,
    ): MixedRenderComposer.CompositionPlan? {
        val rejection = rejectionReason(
            tracks = tracks,
            config = config,
            textOverlays = textOverlays,
            hasImageOverlays = hasImageOverlays,
            hasLottieOverlays = hasLottieOverlays,
            hasTrackedObjects = hasTrackedObjects,
        )
        if (rejection != null) return null

        val visualTracks = tracks.filter {
            it.type == TrackType.VIDEO && it.isVisible && it.clips.any { clip -> clip.durationMs > 0L }
        }
        val segments = SmartRenderEngine.analyzeTimeline(
            tracks = visualTracks,
            config = config,
            textOverlays = textOverlays,
        )
        val runs = SmartRenderEngine.planRuns(segments)
        val finalExtension = finalOutputName
            .substringAfterLast('.', missingDelimiterValue = "")
            .ifBlank { "mp4" }

        return MixedRenderComposer.plan(
            runs = runs,
            projectStem = projectStem,
            finalOutputName = finalOutputName,
            finalExtension = finalExtension,
        ).takeIf { it.benefit == MixedRenderComposer.Benefit.Mixed && it.needsConcat }
    }

    fun sliceTracksForRun(
        tracks: List<Track>,
        run: SmartRenderEngine.RenderRun,
        normaliseTimelineStart: Boolean,
    ): List<Track> {
        val runClipIds = run.clipIds.toSet()
        return tracks.mapNotNull { track ->
            val clips = track.clips
                .filter { it.id in runClipIds && it.durationMs > 0L }
                .sortedBy { it.timelineStartMs }
                .map { clip ->
                    if (normaliseTimelineStart) {
                        clip.copy(timelineStartMs = (clip.timelineStartMs - run.startMs).coerceAtLeast(0L))
                    } else {
                        clip
                    }
                }
            if (clips.isEmpty()) null else track.copy(clips = clips)
        }
    }
}
