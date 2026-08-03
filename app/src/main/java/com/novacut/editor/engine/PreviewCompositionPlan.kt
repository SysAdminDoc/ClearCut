package com.novacut.editor.engine

import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType

/** Pure, testable selection contract shared by live preview and export ordering. */
internal data class PreviewCompositionPlan(
    val visualTracks: List<Track>,
    val audioTracks: List<Track>,
    val soloTrackIds: Set<String>,
    val durationMs: Long,
) {
    fun primaryClipAt(positionMs: Long): Clip? {
        val clampedPosition = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        return visualTracks.firstNotNullOfOrNull { track ->
            track.clips.firstOrNull { clip ->
                clampedPosition >= clip.timelineStartMs &&
                    (clampedPosition < clip.timelineEndMs ||
                        (clampedPosition == durationMs && clip.timelineEndMs == durationMs))
            }
        }
    }

    companion object {
        fun create(tracks: List<Track>): PreviewCompositionPlan {
            val plan = CompositionPlanBuilder.build(tracks)
            return PreviewCompositionPlan(
                visualTracks = plan.visualTracks,
                audioTracks = plan.audioTracks,
                soloTrackIds = plan.soloTrackIds,
                durationMs = plan.durationMs,
            )
        }
    }
}
