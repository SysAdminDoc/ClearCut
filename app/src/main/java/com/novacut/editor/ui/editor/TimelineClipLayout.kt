package com.novacut.editor.ui.editor

import com.novacut.editor.model.Clip
import com.novacut.editor.model.TimelineMarker
import com.novacut.editor.model.Track

internal data class TimelineClipLayout(
    val startPx: Float,
    val widthPx: Float
) {
    fun isVisibleIn(viewportWidthPx: Float): Boolean {
        return widthPx > 0f && startPx + widthPx > 0f && startPx < viewportWidthPx
    }
}

internal data class TimelineClipContentVisibility(
    val showTrackBadge: Boolean,
    val showSpeedBadge: Boolean,
    val showEffectsBadge: Boolean,
    val showClipName: Boolean,
    val showKeyframeBadge: Boolean,
    val compactBadges: Boolean
)

internal enum class TimelineClipGestureZone { TRIM_LEFT, TRIM_RIGHT, SLIDE, SLIP, NONE }

internal sealed class TimelineClipGestureAction {
    data class TrimLeft(val trimStartMs: Long) : TimelineClipGestureAction()
    data class TrimRight(val trimEndMs: Long) : TimelineClipGestureAction()
    data class Slip(val deltaMs: Long) : TimelineClipGestureAction()
    data class Slide(val deltaMs: Long) : TimelineClipGestureAction()
}

internal fun timelineClipLayout(
    clip: Clip,
    scrollOffsetMs: Long,
    pixelsPerMs: Float
): TimelineClipLayout {
    return TimelineClipLayout(
        startPx = (clip.timelineStartMs - scrollOffsetMs) * pixelsPerMs,
        widthPx = clip.durationMs * pixelsPerMs
    )
}

internal fun timelineClipContentVisibility(clipWidthPx: Float): TimelineClipContentVisibility {
    return TimelineClipContentVisibility(
        showTrackBadge = clipWidthPx > 132f,
        showSpeedBadge = clipWidthPx > 164f,
        showEffectsBadge = clipWidthPx > 152f,
        showClipName = clipWidthPx > 84f,
        showKeyframeBadge = clipWidthPx > 152f,
        compactBadges = clipWidthPx < 150f
    )
}

internal fun resolveTimelineClipGestureZone(
    touchXPx: Float,
    clipWidthPx: Float,
    trimHandleWidthPx: Float,
    isTrimMode: Boolean
): TimelineClipGestureZone {
    if (clipWidthPx <= 0f || trimHandleWidthPx <= 0f || !touchXPx.isFinite()) {
        return TimelineClipGestureZone.NONE
    }
    return when {
        touchXPx < trimHandleWidthPx -> TimelineClipGestureZone.TRIM_LEFT
        touchXPx > clipWidthPx - trimHandleWidthPx -> TimelineClipGestureZone.TRIM_RIGHT
        isTrimMode -> TimelineClipGestureZone.SLIP
        else -> TimelineClipGestureZone.SLIDE
    }
}

internal fun resolveTimelineClipGestureAction(
    zone: TimelineClipGestureZone,
    clip: Clip,
    deltaXPx: Float,
    pixelsPerMs: Float,
    minimumClipDurationMs: Long = MIN_TIMELINE_CLIP_DURATION_MS
): TimelineClipGestureAction? {
    if (zone == TimelineClipGestureZone.NONE || pixelsPerMs < 0.001f || !deltaXPx.isFinite()) {
        return null
    }
    val deltaMs = (deltaXPx / pixelsPerMs).toLong()
    return when (zone) {
        TimelineClipGestureZone.TRIM_LEFT -> {
            val maxTrimStart = clip.trimEndMs - minimumClipDurationMs
            if (maxTrimStart < 0L) {
                null
            } else {
                TimelineClipGestureAction.TrimLeft(
                    trimStartMs = (clip.trimStartMs + deltaMs)
                        .coerceAtLeast(0L)
                        .coerceAtMost(maxTrimStart)
                )
            }
        }
        TimelineClipGestureZone.TRIM_RIGHT -> {
            val minTrimEnd = clip.trimStartMs + minimumClipDurationMs
            if (minTrimEnd > clip.sourceDurationMs) {
                null
            } else {
                TimelineClipGestureAction.TrimRight(
                    trimEndMs = (clip.trimEndMs + deltaMs)
                        .coerceIn(minTrimEnd, clip.sourceDurationMs)
                )
            }
        }
        TimelineClipGestureZone.SLIP -> TimelineClipGestureAction.Slip(deltaMs)
        TimelineClipGestureZone.SLIDE -> TimelineClipGestureAction.Slide(deltaMs)
        TimelineClipGestureZone.NONE -> null
    }
}

internal fun timelineSlideSnapTargets(
    tracks: List<Track>,
    draggedClipId: String,
    excludedClipIds: Set<String> = setOf(draggedClipId),
    playheadMs: Long,
    beatMarkers: List<Long>,
    markers: List<TimelineMarker>,
    snapToBeat: Boolean,
    snapToMarker: Boolean
): List<Long> {
    val clipEdges = tracks.flatMap { track ->
        track.clips
            .filter { it.id !in excludedClipIds }
            .flatMap { listOf(it.timelineStartMs, it.timelineEndMs) }
    }
    return buildList {
        addAll(clipEdges)
        add(playheadMs)
        add(0L)
        if (snapToBeat) addAll(beatMarkers)
        if (snapToMarker) addAll(markers.map { it.timeMs })
    }
}

internal data class TimelineSlideSnap(
    val deltaMs: Long,
    val targetMs: Long?,
    val snappedEdgeMs: Long?
)

internal fun resolveTimelineSlideSnap(
    currentStartMs: Long,
    clipDurationMs: Long,
    deltaMs: Long,
    snapTargets: List<Long>,
    snapThresholdMs: Long
): TimelineSlideSnap {
    val proposedStart = currentStartMs + deltaMs
    val proposedEnd = proposedStart + clipDurationMs
    val candidates = buildList {
        findSnapTarget(proposedStart, snapTargets, snapThresholdMs)?.let { target ->
            add(Triple(kotlin.math.abs(target - proposedStart), target, proposedStart))
        }
        findSnapTarget(proposedEnd, snapTargets, snapThresholdMs)?.let { target ->
            add(Triple(kotlin.math.abs(target - proposedEnd), target, proposedEnd))
        }
    }
    val match = candidates.minByOrNull { it.first }
        ?: return TimelineSlideSnap(deltaMs, targetMs = null, snappedEdgeMs = null)
    val correctedDelta = deltaMs + (match.second - match.third)
    return TimelineSlideSnap(
        deltaMs = correctedDelta,
        targetMs = match.second,
        snappedEdgeMs = match.third + (match.second - match.third)
    )
}

internal fun shouldTriggerTimelineSlideSnapHaptic(
    currentStartMs: Long,
    deltaMs: Long,
    snapTargets: List<Long>,
    snapThresholdMs: Long
): Boolean {
    return resolveTimelineSlideSnap(
        currentStartMs = currentStartMs,
        clipDurationMs = 0L,
        deltaMs = deltaMs,
        snapTargets = snapTargets,
        snapThresholdMs = snapThresholdMs
    ).targetMs != null
}
