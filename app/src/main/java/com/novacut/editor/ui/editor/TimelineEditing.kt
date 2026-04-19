package com.novacut.editor.ui.editor

import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import kotlin.math.abs
import kotlin.math.ceil

internal const val MIN_TIMELINE_CLIP_DURATION_MS = 100L

internal data class ClipLocation(
    val trackIndex: Int,
    val clipIndex: Int,
    val track: Track,
    val clip: Clip
)

internal data class SlideBounds(
    val currentStartMs: Long,
    val minStartMs: Long,
    val maxStartMs: Long
)

internal fun List<Track>.findClipLocation(clipId: String): ClipLocation? {
    forEachIndexed { trackIndex, track ->
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        if (clipIndex >= 0) {
            return ClipLocation(
                trackIndex = trackIndex,
                clipIndex = clipIndex,
                track = track,
                clip = track.clips[clipIndex]
            )
        }
    }
    return null
}

internal fun linkedClipIds(tracks: List<Track>, clipId: String): Set<String> {
    val clip = tracks.findClipLocation(clipId)?.clip ?: return setOf(clipId)
    val linkedId = clip.linkedClipId ?: return setOf(clipId)
    return if (tracks.findClipLocation(linkedId) != null) {
        setOf(clipId, linkedId)
    } else {
        setOf(clipId)
    }
}

internal fun Track.canFitClipRange(
    startMs: Long,
    endMs: Long,
    excludingClipIds: Set<String> = emptySet()
): Boolean {
    return clips
        .filterNot { it.id in excludingClipIds }
        .none { existing ->
            startMs < existing.timelineEndMs && endMs > existing.timelineStartMs
        }
}

internal fun preferredAudioTrackIndex(
    tracks: List<Track>,
    startMs: Long,
    endMs: Long
): Int? {
    return tracks
        .withIndex()
        .filter { (_, track) -> track.type == com.novacut.editor.model.TrackType.AUDIO }
        .firstOrNull { (_, track) -> track.canFitClipRange(startMs, endMs) }
        ?.index
}

internal fun canMergeAdjacentClips(first: Clip, second: Clip): Boolean {
    return first.sourceUri.toString() == second.sourceUri.toString() &&
        first.timelineEndMs == second.timelineStartMs &&
        first.trimEndMs == second.trimStartMs
}

internal fun trimClipOnTrack(
    track: Track,
    clipId: String,
    requestedTrimStartMs: Long? = null,
    requestedTrimEndMs: Long? = null
): Track {
    val clipIndex = track.clips.indexOfFirst { it.id == clipId }
    if (clipIndex < 0) return track

    val previousClip = track.clips.getOrNull(clipIndex - 1)
    val nextClip = track.clips.getOrNull(clipIndex + 1)
    var updatedClip = track.clips[clipIndex]

    requestedTrimStartMs?.let { requested ->
        val clampedRequest = requested.coerceIn(
            0L,
            updatedClip.trimEndMs - MIN_TIMELINE_CLIP_DURATION_MS
        )
        val desired = updatedClip.copy(trimStartMs = clampedRequest)
        val minStart = previousClip?.timelineEndMs ?: 0L
        val maxStart = updatedClip.timelineEndMs - MIN_TIMELINE_CLIP_DURATION_MS
        val desiredStart = (updatedClip.timelineEndMs - desired.durationMs)
            .coerceIn(minStart, maxStart)
        val resolvedTrimStart = trimStartForTimelineStart(
            clip = updatedClip,
            targetTimelineStartMs = desiredStart,
            fallbackTrimStartMs = clampedRequest
        )
        updatedClip = updatedClip.copy(
            timelineStartMs = desiredStart,
            trimStartMs = resolvedTrimStart
        )
    }

    requestedTrimEndMs?.let { requested ->
        val clampedRequest = requested.coerceIn(
            updatedClip.trimStartMs + MIN_TIMELINE_CLIP_DURATION_MS,
            updatedClip.sourceDurationMs
        )
        val desired = updatedClip.copy(trimEndMs = clampedRequest)
        val minEnd = updatedClip.timelineStartMs + MIN_TIMELINE_CLIP_DURATION_MS
        val maxEnd = nextClip?.timelineStartMs ?: Long.MAX_VALUE
        val desiredEnd = (updatedClip.timelineStartMs + desired.durationMs)
            .coerceIn(minEnd, maxEnd)
        val resolvedTrimEnd = trimEndForTimelineEnd(
            clip = updatedClip,
            targetTimelineEndMs = desiredEnd,
            fallbackTrimEndMs = clampedRequest
        )
        updatedClip = updatedClip.copy(trimEndMs = resolvedTrimEnd)
    }

    val updatedClips = track.clips.toMutableList()
    updatedClips[clipIndex] = updatedClip
    return track.copy(clips = updatedClips)
}

internal fun calculateSlideBounds(track: Track, clipId: String): SlideBounds? {
    val sortedClips = track.clips.sortedBy { it.timelineStartMs }
    val clipIndex = sortedClips.indexOfFirst { it.id == clipId }
    if (clipIndex < 0) return null

    val clip = sortedClips[clipIndex]
    val previousClip = sortedClips.getOrNull(clipIndex - 1)
    val nextClip = sortedClips.getOrNull(clipIndex + 1)

    var minStart = 0L
    var maxStart = Long.MAX_VALUE

    previousClip?.let { previous ->
        minStart = maxOf(minStart, previous.timelineStartMs + minimumSlideDurationMs(previous))
        maxStart = minOf(maxStart, previous.timelineStartMs + maximumPreviousDurationMs(previous))
    }
    nextClip?.let { next ->
        minStart = maxOf(
            minStart,
            next.timelineEndMs - maximumNextDurationMs(next) - clip.durationMs
        )
        maxStart = minOf(
            maxStart,
            next.timelineEndMs - minimumSlideDurationMs(next) - clip.durationMs
        )
    }

    if (maxStart < minStart) return null
    return SlideBounds(
        currentStartMs = clip.timelineStartMs,
        minStartMs = minStart,
        maxStartMs = maxStart
    )
}

internal fun slideClipOnTrack(
    track: Track,
    clipId: String,
    newStartMs: Long
): Track {
    val sortedClips = track.clips.sortedBy { it.timelineStartMs }.toMutableList()
    val clipIndex = sortedClips.indexOfFirst { it.id == clipId }
    if (clipIndex < 0) return track

    val clip = sortedClips[clipIndex]
    if (newStartMs == clip.timelineStartMs) return track.copy(clips = sortedClips)

    val previousClip = sortedClips.getOrNull(clipIndex - 1)
    val nextClip = sortedClips.getOrNull(clipIndex + 1)
    val newEndMs = newStartMs + clip.durationMs

    previousClip?.let { previous ->
        val desiredDurationMs = (newStartMs - previous.timelineStartMs)
            .coerceAtLeast(minimumSlideDurationMs(previous))
        val fallbackTrimEnd = previous.timelineOffsetToSourceMs(desiredDurationMs)
        val newTrimEnd = trimEndForTimelineEnd(
            clip = previous,
            targetTimelineEndMs = previous.timelineStartMs + desiredDurationMs,
            fallbackTrimEndMs = fallbackTrimEnd
        )
        sortedClips[clipIndex - 1] = previous.copy(trimEndMs = newTrimEnd)
    }

    sortedClips[clipIndex] = clip.copy(timelineStartMs = newStartMs)

    nextClip?.let { next ->
        val desiredDurationMs = (next.timelineEndMs - newEndMs)
            .coerceAtLeast(minimumSlideDurationMs(next))
        val currentTrimOffset = (next.durationMs - desiredDurationMs).coerceAtLeast(0L)
        val fallbackTrimStart = next.timelineOffsetToSourceMs(currentTrimOffset)
        val newTrimStart = trimStartForTimelineStart(
            clip = next,
            targetTimelineStartMs = newEndMs,
            fallbackTrimStartMs = fallbackTrimStart
        )
        sortedClips[clipIndex + 1] = next.copy(
            timelineStartMs = newEndMs,
            trimStartMs = newTrimStart
        )
    }

    return track.copy(clips = sortedClips)
}

private fun trimStartForTimelineStart(
    clip: Clip,
    targetTimelineStartMs: Long,
    fallbackTrimStartMs: Long
): Long {
    val targetDurationMs = (clip.timelineEndMs - targetTimelineStartMs)
        .coerceAtLeast(MIN_TIMELINE_CLIP_DURATION_MS)
    var low = 0L
    var high = (clip.trimEndMs - MIN_TIMELINE_CLIP_DURATION_MS).coerceAtLeast(0L)
    if (high < low) return fallbackTrimStartMs.coerceIn(0L, clip.trimEndMs)
    var best = fallbackTrimStartMs.coerceIn(low, high)
    var bestDistance = Long.MAX_VALUE
    // Hard-cap the binary-search iterations so a pathological speedCurve that
    // makes `durationMs` non-monotonic (corrupt save, stale NaN handles coerced
    // into range) cannot spin here indefinitely. For any sane input, log2 of
    // a multi-hour trim range is ≤ 32, so 64 iterations is 2x headroom.
    var iter = 0
    while (low <= high && iter < 64) {
        iter++
        val mid = low + (high - low) / 2L
        val duration = clip.copy(trimStartMs = mid).durationMs
        // Guard: if `durationMs` returns 0 for a non-zero trim range, the curve
        // integration failed. Fall back to the caller's supplied trim rather
        // than letting the loop pick an arbitrary mid.
        if (duration <= 0L && clip.trimEndMs - mid > 0L) return best
        val distance = abs(duration - targetDurationMs)
        if (distance < bestDistance) {
            bestDistance = distance
            best = mid
        }
        if (duration > targetDurationMs) {
            low = mid + 1L
        } else {
            high = mid - 1L
        }
    }

    return best
}

private fun trimEndForTimelineEnd(
    clip: Clip,
    targetTimelineEndMs: Long,
    fallbackTrimEndMs: Long
): Long {
    val targetDurationMs = (targetTimelineEndMs - clip.timelineStartMs)
        .coerceAtLeast(MIN_TIMELINE_CLIP_DURATION_MS)
    var low = clip.trimStartMs + MIN_TIMELINE_CLIP_DURATION_MS
    var high = clip.sourceDurationMs
    if (high < low) return fallbackTrimEndMs.coerceIn(clip.trimStartMs, clip.sourceDurationMs)
    var best = fallbackTrimEndMs.coerceIn(low, high)
    var bestDistance = Long.MAX_VALUE
    var iter = 0
    while (low <= high && iter < 64) {
        iter++
        val mid = low + (high - low) / 2L
        val duration = clip.copy(trimEndMs = mid).durationMs
        if (duration <= 0L && mid - clip.trimStartMs > 0L) return best
        val distance = abs(duration - targetDurationMs)
        if (distance < bestDistance) {
            bestDistance = distance
            best = mid
        }
        if (duration < targetDurationMs) {
            low = mid + 1L
        } else {
            high = mid - 1L
        }
    }

    return best
}

private fun minimumSlideDurationMs(clip: Clip): Long {
    return ceil(100.0 / safeTimelineSpeed(clip.speed).toDouble())
        .toLong()
        .coerceAtLeast(1L)
}

private fun maximumPreviousDurationMs(clip: Clip): Long {
    return clip.copy(trimEndMs = clip.sourceDurationMs)
        .durationMs
        .coerceAtLeast(minimumSlideDurationMs(clip))
}

private fun maximumNextDurationMs(clip: Clip): Long {
    return clip.copy(trimStartMs = 0L)
        .durationMs
        .coerceAtLeast(minimumSlideDurationMs(clip))
}

private fun safeTimelineSpeed(speed: Float): Float {
    return if (speed.isFinite() && speed > 0f) speed.coerceAtLeast(0.01f) else 1f
}
