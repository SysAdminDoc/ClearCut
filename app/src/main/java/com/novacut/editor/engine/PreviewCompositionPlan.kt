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
            val orderedTracks = tracks.sortedBy { it.index }
            val soloTrackIds = orderedTracks.filter { it.isSolo }.mapTo(linkedSetOf()) { it.id }
            val visualTracks = orderedTracks
                .filter { track ->
                    track.type in setOf(TrackType.VIDEO, TrackType.OVERLAY) &&
                        track.isVisible && track.clips.any { it.durationMs > 0L }
                }
                .sortedByDescending { it.index }
            val audioTracks = orderedTracks.filter { track ->
                track.type == TrackType.AUDIO &&
                    track.clips.any { it.durationMs > 0L } &&
                    track.isVisible && !track.isMuted &&
                    (soloTrackIds.isEmpty() || track.id in soloTrackIds)
            }
            val durationMs = orderedTracks.maxOfOrNull { track ->
                track.clips.maxOfOrNull(Clip::timelineEndMs) ?: 0L
            } ?: 0L
            return PreviewCompositionPlan(
                visualTracks = visualTracks,
                audioTracks = audioTracks,
                soloTrackIds = soloTrackIds,
                durationMs = durationMs,
            )
        }
    }
}
