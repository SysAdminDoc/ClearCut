package com.novacut.editor.engine

import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType

/** Pure timeline selection used by both preview and transformer composition paths. */
internal data class CompositionTrackPlan(
    val visualTracks: List<Track>,
    val audioTracks: List<Track>,
    val soloTrackIds: Set<String>,
    val durationMs: Long,
)

internal object CompositionPlanBuilder {
    fun build(
        tracks: List<Track>,
        additionalDurationsMs: Iterable<Long> = emptyList(),
    ): CompositionTrackPlan {
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
        val timelineDurationMs = orderedTracks.maxOfOrNull { track ->
            track.clips.maxOfOrNull(Clip::timelineEndMs) ?: 0L
        } ?: 0L
        val additionalDurationMs = additionalDurationsMs.maxOfOrNull { it.coerceAtLeast(0L) } ?: 0L

        return CompositionTrackPlan(
            visualTracks = visualTracks,
            audioTracks = audioTracks,
            soloTrackIds = soloTrackIds,
            durationMs = maxOf(timelineDurationMs, additionalDurationMs),
        )
    }
}
