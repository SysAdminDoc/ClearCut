package com.novacut.editor.ui.editor

import com.novacut.editor.engine.KeyframeEngine
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionWord
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectKeyframe
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.Mask
import com.novacut.editor.model.MaskKeyframe
import com.novacut.editor.model.MotionTrackPoint
import com.novacut.editor.model.MotionTrackingData
import com.novacut.editor.model.Track
import com.novacut.editor.model.TimelineTimebase
import kotlin.math.abs
import kotlin.math.ceil

internal const val MIN_TIMELINE_CLIP_DURATION_MS = 100L

internal fun playbackStartPosition(playheadMs: Long, totalDurationMs: Long): Long {
    val clamped = playheadMs.coerceIn(0L, totalDurationMs.coerceAtLeast(0L))
    return if (totalDurationMs > 0L && clamped >= totalDurationMs) 0L else clamped
}

internal fun canSplitTimelineAtPlayhead(
    tracks: List<Track>,
    selectedClipId: String?,
    playheadMs: Long
): Boolean {
    val seedId = selectedClipId ?: tracks
        .sortedBy { it.index }
        .asSequence()
        .flatMap { it.clips.sortedBy(Clip::timelineStartMs).asSequence() }
        .firstOrNull { playheadMs in it.timelineStartMs until it.timelineEndMs }
        ?.id
        ?: return false
    return linkedSplitCandidateIds(tracks, setOf(seedId), playheadMs).isNotEmpty()
}

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

internal fun Clip.canSplitAtTimelinePosition(positionMs: Long): Boolean {
    if (positionMs <= timelineStartMs || positionMs >= timelineEndMs) return false
    // Mirror splitTimelineClip's guards exactly so this gate never reports a
    // clip as splittable when the executor would no-op. The executor enforces
    // the 100 ms minimum in TIMELINE space (durationMs), which is stricter than
    // source space for sped-up clips (timeline half = source half / speed) — a
    // source-space-only check here let a 3x clip pass the gate yet fail the
    // split, leaving a dangling selection and a false "Clip split" toast.
    val timelineOffsetMs = positionMs - timelineStartMs
    if (timelineOffsetMs < MIN_TIMELINE_CLIP_DURATION_MS ||
        durationMs - timelineOffsetMs < MIN_TIMELINE_CLIP_DURATION_MS
    ) return false
    val sourceSplitMs = timelineOffsetToSourceMs(timelineOffsetMs)
    return sourceSplitMs > trimStartMs && sourceSplitMs < trimEndMs
}

/** Return only complete linked closures that can all split at this position. */
internal fun linkedSplitCandidateIds(
    tracks: List<Track>,
    seedIds: Set<String>,
    positionMs: Long
): Set<String> {
    val closures = seedIds
        .map { seedId -> linkedClipIds(tracks, seedId) }
        .distinct()
    return closures.flatMapTo(mutableSetOf()) { closure ->
        val locations = closure.mapNotNull(tracks::findClipLocation)
        if (locations.size == closure.size && locations.all { it.clip.canSplitAtTimelinePosition(positionMs) }) {
            closure
        } else {
            emptySet()
        }
    }
}

internal fun regroupedClipIdsForSplit(
    tracks: List<Track>,
    splitClipIds: Set<String>,
    positionMs: Long
): Set<String> {
    val affectedGroupIds = splitClipIds.mapNotNull { tracks.findClipLocation(it)?.clip?.groupId }.toSet()
    if (affectedGroupIds.isEmpty()) return emptySet()
    return tracks.flatMap { it.clips }
        .filter { it.groupId in affectedGroupIds && it.timelineStartMs >= positionMs }
        .mapTo(mutableSetOf()) { it.id }
}

/** Convert detector/source timestamps into this clip's retimed project timeline. */
internal fun mapSourceMarkersToTimeline(clip: Clip, sourceMarkersMs: List<Long>): List<Long> {
    return sourceMarkersMs.mapNotNull { sourceMs ->
        clip.sourceTimeToTimelineOffsetMs(sourceMs, includeBoundaries = false)
            ?.let { clip.timelineStartMs + it }
    }.distinct().sorted()
}

/** Resolve every clip that must participate in an identity-linked edit. */
/**
 * Reorder [clipId] to [targetIndex] within one track's clip list while
 * preserving the leading offset and the gap that follows each layout slot.
 *
 * The previous implementation re-packed every clip gaplessly, destroying
 * intentional gaps (e.g. from lift-delete). This keeps the same set of gaps by
 * position, so a reorder changes only clip order, not the track's overall span
 * or its gap structure, and never produces overlaps.
 */
internal fun reorderClipsPreservingGaps(clips: List<Clip>, clipId: String, targetIndex: Int): List<Clip> {
    if (clips.isEmpty()) return clips
    val order = clips.sortedBy { it.timelineStartMs }
    val fromIndex = order.indexOfFirst { it.id == clipId }
    if (fromIndex < 0) return clips

    val leadingOffset = order.first().timelineStartMs.coerceAtLeast(0L)
    // Gap that follows each position in the ORIGINAL layout; the last position
    // has no trailing gap.
    val gapAfterPosition = order.indices.map { i ->
        if (i < order.lastIndex) {
            (order[i + 1].timelineStartMs - order[i].timelineEndMs).coerceAtLeast(0L)
        } else 0L
    }

    val reordered = order.toMutableList()
    val moved = reordered.removeAt(fromIndex)
    reordered.add(targetIndex.coerceIn(0, reordered.size), moved)

    var cursor = leadingOffset
    return reordered.mapIndexed { i, clip ->
        val placed = clip.copy(timelineStartMs = cursor)
        cursor = placed.timelineEndMs + gapAfterPosition.getOrElse(i) { 0L }
        placed
    }
}

/**
 * The single ripple offset to apply across every track when duplicating a
 * linked clip closure: the largest duration among the duplicated clips. Applying
 * one offset to all affected tracks keeps a linked video/audio pair (whose
 * duplicates may differ in duration) in sync, and using the max prevents the
 * shorter duplicate's track from overlapping its next clip.
 */
internal fun linkedClosureRippleOffset(tracks: List<Track>, duplicateIds: Set<String>): Long =
    tracks.asSequence()
        .flatMap { it.clips.asSequence() }
        .filter { it.id in duplicateIds }
        .maxOfOrNull { it.durationMs } ?: 0L

internal fun expandTimelineEditClipIds(tracks: List<Track>, seedIds: Set<String>): Set<String> {
    val allClips = tracks.flatMap { it.clips }
    val resolved = seedIds.filterTo(mutableSetOf()) { seed -> allClips.any { it.id == seed } }
    var changed: Boolean
    do {
        changed = false
        allClips.forEach { clip ->
            val linkedToResolved = clip.linkedClipId?.let { it in resolved } == true
            val sameGroup = clip.groupId?.let { groupId ->
                allClips.any { it.id in resolved && it.groupId == groupId }
            } == true
            val selectedClip = clip.id in resolved
            if (selectedClip || linkedToResolved || sameGroup) {
                if (resolved.add(clip.id)) changed = true
                clip.linkedClipId?.let { if (resolved.add(it)) changed = true }
            }
        }
    } while (changed)
    return resolved
}

/**
 * Ripple-remove only the requested clip intervals. Existing gaps and untouched
 * tracks retain their offsets; overlapping removed intervals are counted once.
 */
/**
 * Lift-remove the requested clips: delete them but leave the hole where they
 * were, so no later clip on any track moves and every pre-existing gap keeps
 * its exact duration. The non-rippling counterpart to [rippleDeleteClips].
 */
internal fun removeClipsWithoutRipple(tracks: List<Track>, clipIds: Set<String>): List<Track> {
    if (clipIds.isEmpty()) return tracks
    return tracks.map { track ->
        val filtered = track.clips.filterNot { it.id in clipIds }
        if (filtered.size == track.clips.size) track else track.copy(clips = filtered)
    }
}

internal fun rippleDeleteClips(
    tracks: List<Track>,
    clipIds: Set<String>,
    timebase: TimelineTimebase? = null,
): List<Track> {
    if (clipIds.isEmpty()) return tracks
    return tracks.map { track ->
        val removedRanges = track.clips
            .filter { it.id in clipIds }
            .map { timebase.toTimelineUnit(it.timelineStartMs) to timebase.toTimelineUnit(it.timelineEndMs) }
            .sortedBy { it.first }
        if (removedRanges.isEmpty()) return@map track

        track.copy(
            clips = track.clips
                .filterNot { it.id in clipIds }
                .map { clip ->
                    val shiftMs = mergedDurationEndingAtOrBefore(
                        ranges = removedRanges,
                        positionMs = timebase.toTimelineUnit(clip.timelineStartMs)
                    )
                    if (shiftMs == 0L) clip
                    else clip.copy(
                        timelineStartMs = timebase.fromTimelineUnit(
                            (timebase.toTimelineUnit(clip.timelineStartMs) - shiftMs).coerceAtLeast(0L)
                        )
                    )
                }
        )
    }
}

private fun mergedDurationEndingAtOrBefore(
    ranges: List<Pair<Long, Long>>,
    positionMs: Long
): Long {
    val eligible = ranges.filter { (_, end) -> end <= positionMs }
    if (eligible.isEmpty()) return 0L
    var total = 0L
    var currentStart = eligible.first().first
    var currentEnd = eligible.first().second
    eligible.drop(1).forEach { (start, end) ->
        if (start <= currentEnd) {
            currentEnd = maxOf(currentEnd, end)
        } else {
            total += (currentEnd - currentStart).coerceAtLeast(0L)
            currentStart = start
            currentEnd = end
        }
    }
    return total + (currentEnd - currentStart).coerceAtLeast(0L)
}

/** Move a global timeline marker with one track's ripple edit. */
internal fun rippleTimelinePosition(
    positionMs: Long,
    removedRanges: List<Pair<Long, Long>>,
    timebase: TimelineTimebase? = null,
): Long? {
    val normalized = removedRanges
        .map { (start, end) -> timebase.toTimelineUnit(start) to timebase.toTimelineUnit(end) }
        .filter { (start, end) -> end > start }
        .sortedBy { it.first }
    val position = timebase.toTimelineUnit(positionMs)
    if (normalized.any { (start, end) -> position >= start && position < end }) {
        return null
    }
    return timebase.fromTimelineUnit(
        (position - mergedDurationEndingAtOrBefore(normalized, position)).coerceAtLeast(0L)
    )
}

/** Keep preview playback attached to the edit point after a ripple delete. */
internal fun ripplePlaybackPosition(
    positionMs: Long,
    removedRanges: List<Pair<Long, Long>>,
    timebase: TimelineTimebase? = null,
): Long {
    val normalized = removedRanges
        .map { (start, end) -> timebase.toTimelineUnit(start) to timebase.toTimelineUnit(end) }
        .filter { (start, end) -> end > start }
        .sortedBy { it.first }
    val position = timebase.toTimelineUnit(positionMs)
    var removedBeforeMs = 0L
    var currentStartMs: Long? = null
    var currentEndMs = 0L

    fun consumeRange(startMs: Long, endMs: Long): Long? {
        if (position < startMs) return (position - removedBeforeMs).coerceAtLeast(0L)
        if (position < endMs) return (startMs - removedBeforeMs).coerceAtLeast(0L)
        removedBeforeMs += (endMs - startMs).coerceAtLeast(0L)
        return null
    }

    normalized.forEach { (startMs, endMs) ->
        if (currentStartMs == null) {
            currentStartMs = startMs
            currentEndMs = endMs
        } else if (startMs <= currentEndMs) {
            currentEndMs = maxOf(currentEndMs, endMs)
        } else {
            consumeRange(currentStartMs!!, currentEndMs)?.let { return timebase.fromTimelineUnit(it) }
            currentStartMs = startMs
            currentEndMs = endMs
        }
    }
    currentStartMs?.let { startMs ->
        consumeRange(startMs, currentEndMs)?.let { return timebase.fromTimelineUnit(it) }
    }
    return timebase.fromTimelineUnit((position - removedBeforeMs).coerceAtLeast(0L))
}

private fun TimelineTimebase?.toTimelineUnit(timeMs: Long): Long =
    this?.frameIndexAt(timeMs) ?: timeMs

private fun TimelineTimebase?.fromTimelineUnit(unit: Long): Long =
    this?.timeMsAt(unit) ?: unit

/** Verify that both split boundaries are safe for every linked member. */
internal fun canDeleteTimelineRangeAtomically(
    tracks: List<Track>,
    clipId: String,
    startMs: Long,
    endMs: Long
): Boolean {
    if (endMs <= startMs) return false
    val splitIds = linkedSplitCandidateIds(tracks, setOf(clipId), startMs)
    if (splitIds.isEmpty()) return false
    return splitIds.all { candidateId ->
        val clip = tracks.findClipLocation(candidateId)?.clip ?: return@all false
        val firstSplit = splitTimelineClip(
            clip = clip,
            playheadMs = startMs,
            newClipId = "validation-right-$candidateId",
            newLinkedClipId = null,
            rightGroupId = clip.groupId,
            idFactory = { "validation-owned" }
        ) ?: return@all false
        firstSplit.right.canSplitAtTimelinePosition(endMs)
    }
}

internal data class TimelineClipSplit(
    val left: Clip,
    val right: Clip,
    val timelineOffsetMs: Long,
    val sourceSplitMs: Long
)

/**
 * Split one timeline clip while preserving absolute animation/caption timing.
 * The left half retains owned IDs; the right half receives fresh nested IDs.
 */
internal fun splitTimelineClip(
    clip: Clip,
    playheadMs: Long,
    newClipId: String,
    newLinkedClipId: String?,
    rightGroupId: String?,
    rightTrackedObjectIds: Map<String, String> = emptyMap(),
    idFactory: () -> String
): TimelineClipSplit? {
    if (playheadMs <= clip.timelineStartMs || playheadMs >= clip.timelineEndMs) return null
    val timelineOffsetMs = playheadMs - clip.timelineStartMs
    if (timelineOffsetMs < MIN_TIMELINE_CLIP_DURATION_MS ||
        clip.durationMs - timelineOffsetMs < MIN_TIMELINE_CLIP_DURATION_MS
    ) return null

    val sourceSplitMs = clip.timelineOffsetToSourceMs(timelineOffsetMs)
    if (sourceSplitMs <= clip.trimStartMs || sourceSplitMs >= clip.trimEndMs) return null
    val sourceRangeMs = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1L)
    val splitFraction = ((sourceSplitMs - clip.trimStartMs).toFloat() / sourceRangeMs)
        .coerceIn(0f, 1f)

    val (leftKeyframes, rightKeyframes) = splitKeyframes(clip.keyframes, timelineOffsetMs)
    val (leftEffects, rightEffects) = splitEffects(
        clip.effects,
        timelineOffsetMs,
        rightTrackedObjectIds,
        idFactory
    )
    val (leftMasks, rightMasks) = splitMasks(clip.masks, timelineOffsetMs, idFactory)
    val (leftCaptions, rightCaptions) = splitCaptions(clip.captions, timelineOffsetMs, idFactory)
    val (leftMotion, rightMotion) = splitMotionTracking(
        clip.motionTrackingData,
        timelineOffsetMs,
        idFactory
    )

    val leftCandidate = clip.copy(
        trimEndMs = sourceSplitMs,
        tailTransition = null,
        fadeOutMs = 0L,
        keyframes = leftKeyframes,
        speedCurve = clip.speedCurve?.restrictTo(0f, splitFraction, sourceRangeMs),
        effects = leftEffects,
        masks = leftMasks,
        captions = leftCaptions,
        motionTrackingData = leftMotion
    )
    val rightCandidate = clip.copy(
        id = newClipId,
        timelineStartMs = playheadMs,
        trimStartMs = sourceSplitMs,
        headTransition = null,
        fadeInMs = 0L,
        linkedClipId = newLinkedClipId,
        groupId = rightGroupId,
        keyframes = rightKeyframes,
        speedCurve = clip.speedCurve?.restrictTo(splitFraction, 1f, sourceRangeMs),
        effects = rightEffects,
        masks = rightMasks,
        captions = rightCaptions,
        motionTrackingData = rightMotion,
        audioEffects = clip.audioEffects.map { it.copy(id = idFactory()) }
    )
    val left = leftCandidate.copy(fadeInMs = minOf(leftCandidate.fadeInMs, leftCandidate.durationMs))
    // Keep the two halves exactly abutting. leftCandidate.durationMs is a fresh
    // integration of the restricted speed curve, so for a curved clip it can
    // round a millisecond away from timelineOffsetMs and leave a sub-frame
    // gap/overlap against the right half (pinned to playheadMs). Snapping the
    // right start to left.timelineEndMs preserves the abutment invariant that
    // merge/coalesce rely on (left.timelineEndMs == right.timelineStartMs). For
    // constant-speed clips left.timelineEndMs already equals playheadMs, so this
    // is a no-op there.
    val right = rightCandidate.copy(
        timelineStartMs = left.timelineEndMs,
        fadeOutMs = minOf(rightCandidate.fadeOutMs, rightCandidate.durationMs)
    )
    return TimelineClipSplit(left, right, timelineOffsetMs, sourceSplitMs)
}

private fun splitKeyframes(
    keyframes: List<Keyframe>,
    splitMs: Long
): Pair<List<Keyframe>, List<Keyframe>> {
    val boundary = keyframes.map { it.property }.distinct().mapNotNull { property ->
        KeyframeEngine.getValueAt(keyframes, property, splitMs)?.let { value ->
            (keyframes.lastOrNull { it.property == property && it.timeOffsetMs <= splitMs }
                ?: keyframes.firstOrNull { it.property == property })
                ?.copy(timeOffsetMs = splitMs, value = value)
        }
    }
    val left = (keyframes.filter { it.timeOffsetMs < splitMs } + boundary)
        .distinctBy { it.property to it.timeOffsetMs }
        .sortedBy { it.timeOffsetMs }
    val rightBoundary = boundary.map { it.copy(timeOffsetMs = 0L) }
    val right = (rightBoundary + keyframes.filter { it.timeOffsetMs > splitMs }
        .map { it.copy(timeOffsetMs = it.timeOffsetMs - splitMs) })
        .distinctBy { it.property to it.timeOffsetMs }
        .sortedBy { it.timeOffsetMs }
    return left to right
}

private fun splitEffects(
    effects: List<Effect>,
    splitMs: Long,
    rightTrackedObjectIds: Map<String, String>,
    idFactory: () -> String
): Pair<List<Effect>, List<Effect>> {
    val left = mutableListOf<Effect>()
    val right = mutableListOf<Effect>()
    effects.forEach { effect ->
        val boundary = effect.keyframes.map { it.paramName }.distinct().mapNotNull { param ->
            KeyframeEngine.getEffectParamAt(effect.keyframes, param, splitMs)?.let { value ->
                (effect.keyframes.lastOrNull { it.paramName == param && it.timeOffsetMs <= splitMs }
                    ?: effect.keyframes.firstOrNull { it.paramName == param })
                    ?.copy(timeOffsetMs = splitMs, value = value)
            }
        }
        val leftFrames = (effect.keyframes.filter { it.timeOffsetMs < splitMs } + boundary)
            .distinctBy { it.paramName to it.timeOffsetMs }
            .sortedBy { it.timeOffsetMs }
        val rightFrames = (boundary.map { it.copy(timeOffsetMs = 0L) } +
            effect.keyframes.filter { it.timeOffsetMs > splitMs }
                .map { it.copy(timeOffsetMs = it.timeOffsetMs - splitMs) })
            .distinctBy { it.paramName to it.timeOffsetMs }
            .sortedBy { it.timeOffsetMs }
        left += effect.copy(keyframes = leftFrames)
        right += effect.copy(
            id = idFactory(),
            keyframes = rightFrames,
            targetTrackedObjectId = effect.targetTrackedObjectId?.let { trackedId ->
                rightTrackedObjectIds[trackedId] ?: trackedId
            }
        )
    }
    return left to right
}

private fun splitMasks(
    masks: List<Mask>,
    splitMs: Long,
    idFactory: () -> String
): Pair<List<Mask>, List<Mask>> {
    val left = mutableListOf<Mask>()
    val right = mutableListOf<Mask>()
    masks.forEach { mask ->
        val boundary = if (mask.keyframes.isEmpty()) null else MaskKeyframe(
            timeOffsetMs = splitMs,
            points = KeyframeEngine.interpolateMaskPoints(mask, splitMs),
            easing = mask.keyframes.lastOrNull { it.timeOffsetMs <= splitMs }?.easing
                ?: mask.keyframes.first().easing
        )
        val leftFrames = (mask.keyframes.filter { it.timeOffsetMs < splitMs } + listOfNotNull(boundary))
            .distinctBy { it.timeOffsetMs }
            .sortedBy { it.timeOffsetMs }
        val rightFrames = (listOfNotNull(boundary?.copy(timeOffsetMs = 0L)) +
            mask.keyframes.filter { it.timeOffsetMs > splitMs }
                .map { it.copy(timeOffsetMs = it.timeOffsetMs - splitMs) })
            .distinctBy { it.timeOffsetMs }
            .sortedBy { it.timeOffsetMs }
        left += mask.copy(keyframes = leftFrames)
        right += mask.copy(id = idFactory(), keyframes = rightFrames)
    }
    return left to right
}

private fun splitCaptions(
    captions: List<Caption>,
    splitMs: Long,
    idFactory: () -> String
): Pair<List<Caption>, List<Caption>> {
    val left = mutableListOf<Caption>()
    val right = mutableListOf<Caption>()
    captions.forEach { caption ->
        if (caption.startTimeMs < splitMs && caption.endTimeMs > 0L) {
            left += caption.copy(
                endTimeMs = minOf(caption.endTimeMs, splitMs),
                words = caption.words.mapNotNull { it.clippedTo(0L, splitMs, 0L) }
            )
        }
        if (caption.endTimeMs > splitMs) {
            right += caption.copy(
                id = idFactory(),
                startTimeMs = (maxOf(caption.startTimeMs, splitMs) - splitMs).coerceAtLeast(0L),
                endTimeMs = (caption.endTimeMs - splitMs).coerceAtLeast(0L),
                words = caption.words.mapNotNull { it.clippedTo(splitMs, Long.MAX_VALUE, splitMs) }
            )
        }
    }
    return left to right
}

private fun CaptionWord.clippedTo(
    rangeStartMs: Long,
    rangeEndMs: Long,
    rebaseMs: Long
): CaptionWord? {
    val clippedStart = maxOf(startTimeMs, rangeStartMs)
    val clippedEnd = minOf(endTimeMs, rangeEndMs)
    if (clippedEnd <= clippedStart) return null
    return copy(startTimeMs = clippedStart - rebaseMs, endTimeMs = clippedEnd - rebaseMs)
}

private fun splitMotionTracking(
    tracking: MotionTrackingData?,
    splitMs: Long,
    idFactory: () -> String
): Pair<MotionTrackingData?, MotionTrackingData?> {
    tracking ?: return null to null
    val boundary = tracking.trackPoints.closestBoundaryAt(splitMs)
    val leftPoints = (tracking.trackPoints.filter { it.timeOffsetMs < splitMs } + listOfNotNull(boundary))
        .distinctBy { it.timeOffsetMs }
        .sortedBy { it.timeOffsetMs }
    val rightPoints = (listOfNotNull(boundary?.copy(timeOffsetMs = 0L)) +
        tracking.trackPoints.filter { it.timeOffsetMs > splitMs }
            .map { it.copy(timeOffsetMs = it.timeOffsetMs - splitMs) })
        .distinctBy { it.timeOffsetMs }
        .sortedBy { it.timeOffsetMs }
    return tracking.copy(trackPoints = leftPoints) to
        tracking.copy(id = idFactory(), trackPoints = rightPoints)
}

private fun List<MotionTrackPoint>.closestBoundaryAt(splitMs: Long): MotionTrackPoint? {
    if (isEmpty()) return null
    return minByOrNull { abs(it.timeOffsetMs - splitMs) }?.copy(timeOffsetMs = splitMs)
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
        first.trimEndMs == second.trimStartMs &&
        // Merging collapses the two clips into the first's timing model, so they
        // must share retiming/reverse/volume. A speed-ramped clip's curve is
        // normalized over its own trim range and cannot be extended, so refuse
        // to merge when either side carries a speed curve.
        first.speed == second.speed &&
        first.isReversed == second.isReversed &&
        first.volume == second.volume &&
        first.speedCurve == null && second.speedCurve == null
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
        // Guard the coerceIn range: a sub-100ms clip makes the upper bound negative,
        // and Long.coerceIn(0, negative) throws "Cannot coerce value to an empty range".
        val startUpperBound = updatedClip.trimEndMs - MIN_TIMELINE_CLIP_DURATION_MS
        if (startUpperBound < 0L) return@let
        val clampedRequest = requested.coerceIn(0L, startUpperBound)
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
        // Guard the coerceIn range: when the source is shorter than trimStart + 100ms the
        // lower bound exceeds the upper bound, and coerceIn throws on the empty range.
        val endLowerBound = updatedClip.trimStartMs + MIN_TIMELINE_CLIP_DURATION_MS
        if (endLowerBound > updatedClip.sourceDurationMs) return@let
        val clampedRequest = requested.coerceIn(endLowerBound, updatedClip.sourceDurationMs)
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

/**
 * Trim identity-linked clips by one shared timeline delta. Each track may have
 * different neighbouring clips, so applying the same source trim request to
 * every member can clamp them to different timeline boundaries and break A/V
 * sync. This planner resolves the anchor request, intersects the admissible
 * delta across every linked member, then applies that shared delta atomically.
 */
internal fun trimLinkedClipsOnTimeline(
    tracks: List<Track>,
    anchorClipId: String,
    targetClipIds: Set<String>,
    requestedTrimStartMs: Long? = null,
    requestedTrimEndMs: Long? = null
): List<Track> {
    if (targetClipIds.isEmpty()) return tracks
    var updatedTracks = tracks
    if (requestedTrimStartMs != null) {
        updatedTracks = trimLinkedClipStarts(
            tracks = updatedTracks,
            anchorClipId = anchorClipId,
            targetClipIds = targetClipIds,
            requestedTrimStartMs = requestedTrimStartMs
        )
    }
    if (requestedTrimEndMs != null) {
        updatedTracks = trimLinkedClipEnds(
            tracks = updatedTracks,
            anchorClipId = anchorClipId,
            targetClipIds = targetClipIds,
            requestedTrimEndMs = requestedTrimEndMs
        )
    }
    return updatedTracks
}

internal fun hasSameClipTiming(left: List<Track>, right: List<Track>): Boolean {
    if (left.size != right.size) return false
    return left.zip(right).all { (leftTrack, rightTrack) ->
        leftTrack.id == rightTrack.id &&
            leftTrack.clips.size == rightTrack.clips.size &&
            leftTrack.clips.zip(rightTrack.clips).all { (leftClip, rightClip) ->
                leftClip.id == rightClip.id &&
                    leftClip.timelineStartMs == rightClip.timelineStartMs &&
                    leftClip.trimStartMs == rightClip.trimStartMs &&
                    leftClip.trimEndMs == rightClip.trimEndMs
            }
    }
}

internal fun slipLinkedClipsOnTimeline(
    tracks: List<Track>,
    targetClipIds: Set<String>,
    slipAmountMs: Long,
    timebase: TimelineTimebase? = null,
): List<Track> {
    val requestedFrameDelta = timebase?.let {
        val sign = if (slipAmountMs < 0L) -1L else 1L
        sign * it.frameIndexAt(kotlin.math.abs(slipAmountMs))
    }
    var changed = false
    val updatedTracks = tracks.map { track ->
        val updatedClips = track.clips.map clipMap@{ clip ->
            if (clip.id !in targetClipIds) return@clipMap clip
            val sourceWindow = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(100L)
            val maxTrimStart = (clip.sourceDurationMs - sourceWindow).coerceAtLeast(0L)
            val newTrimStart: Long
            val newTrimEnd: Long
            if (timebase != null && requestedFrameDelta != null) {
                val startFrame = timebase.frameIndexAt(clip.trimStartMs)
                val endFrame = timebase.frameIndexAt(clip.trimEndMs)
                val maximumEndFrame = timebase.frameIndexAtOrBefore(clip.sourceDurationMs)
                val appliedFrames = requestedFrameDelta.coerceIn(-startFrame, maximumEndFrame - endFrame)
                newTrimStart = timebase.timeMsAt(startFrame + appliedFrames)
                newTrimEnd = timebase.timeMsAt(endFrame + appliedFrames)
            } else {
                newTrimStart = (clip.trimStartMs + slipAmountMs).coerceIn(0L, maxTrimStart)
                newTrimEnd = newTrimStart + sourceWindow
            }
            if (newTrimStart == clip.trimStartMs && newTrimEnd == clip.trimEndMs) {
                return@clipMap clip
            }
            changed = true
            clip.copy(trimStartMs = newTrimStart, trimEndMs = newTrimEnd)
        }
        if (updatedClips.indices.all { updatedClips[it] === track.clips[it] }) track
        else track.copy(clips = updatedClips)
    }
    return if (changed) updatedTracks else tracks
}

private fun trimLinkedClipStarts(
    tracks: List<Track>,
    anchorClipId: String,
    targetClipIds: Set<String>,
    requestedTrimStartMs: Long
): List<Track> {
    val anchor = tracks.findClipLocation(anchorClipId) ?: return tracks
    val simulatedAnchorTrack = trimClipOnTrack(
        track = anchor.track,
        clipId = anchorClipId,
        requestedTrimStartMs = requestedTrimStartMs
    )
    val simulatedAnchor = simulatedAnchorTrack.clips.firstOrNull { it.id == anchorClipId }
        ?: return tracks
    val requestedDeltaMs = simulatedAnchor.timelineStartMs - anchor.clip.timelineStartMs
    return trimLinkedClipStartToTimelineTime(
        tracks,
        anchorClipId,
        targetClipIds,
        anchor.clip.timelineStartMs + requestedDeltaMs,
    )
}

internal fun trimLinkedClipStartToTimelineTime(
    tracks: List<Track>,
    anchorClipId: String,
    targetClipIds: Set<String>,
    requestedTimelineStartMs: Long,
    timebase: TimelineTimebase? = null,
): List<Track> {
    val anchor = tracks.findClipLocation(anchorClipId) ?: return tracks
    val requestedDelta = timebase?.let {
        it.frameIndexAt(requestedTimelineStartMs) - it.frameIndexAt(anchor.clip.timelineStartMs)
    } ?: (requestedTimelineStartMs - anchor.clip.timelineStartMs)
    if (requestedDelta == 0L) return tracks

    val resolvedDeltas = targetClipIds.mapNotNull { clipId ->
        val location = tracks.findClipLocation(clipId) ?: return@mapNotNull null
        val simulatedTrack = trimClipStartToTimelineStart(
            track = location.track,
            clipId = clipId,
            requestedTimelineStartMs = timebase?.addFrames(location.clip.timelineStartMs, requestedDelta)
                ?: location.clip.timelineStartMs + requestedDelta
        )
        val simulatedClip = simulatedTrack.clips.firstOrNull { it.id == clipId }
            ?: return@mapNotNull null
        timebase?.let {
            it.frameIndexAt(simulatedClip.timelineStartMs) - it.frameIndexAt(location.clip.timelineStartMs)
        } ?: (simulatedClip.timelineStartMs - location.clip.timelineStartMs)
    }
    if (resolvedDeltas.size != targetClipIds.size) return tracks
    val sharedDelta = mostRestrictiveSharedDelta(requestedDelta, resolvedDeltas)
    if (sharedDelta == 0L) return tracks

    return tracks.map { track ->
        targetClipIds.fold(track) { currentTrack, clipId ->
            val clip = currentTrack.clips.firstOrNull { it.id == clipId }
                ?: return@fold currentTrack
            trimClipStartToTimelineStart(
                track = currentTrack,
                clipId = clipId,
                requestedTimelineStartMs = timebase?.addFrames(clip.timelineStartMs, sharedDelta)
                    ?: clip.timelineStartMs + sharedDelta
            )
        }
    }
}

private fun trimLinkedClipEnds(
    tracks: List<Track>,
    anchorClipId: String,
    targetClipIds: Set<String>,
    requestedTrimEndMs: Long
): List<Track> {
    val anchor = tracks.findClipLocation(anchorClipId) ?: return tracks
    val simulatedAnchorTrack = trimClipOnTrack(
        track = anchor.track,
        clipId = anchorClipId,
        requestedTrimEndMs = requestedTrimEndMs
    )
    val simulatedAnchor = simulatedAnchorTrack.clips.firstOrNull { it.id == anchorClipId }
        ?: return tracks
    val requestedDeltaMs = simulatedAnchor.timelineEndMs - anchor.clip.timelineEndMs
    return trimLinkedClipEndToTimelineTime(
        tracks,
        anchorClipId,
        targetClipIds,
        anchor.clip.timelineEndMs + requestedDeltaMs,
    )
}

internal fun trimLinkedClipEndToTimelineTime(
    tracks: List<Track>,
    anchorClipId: String,
    targetClipIds: Set<String>,
    requestedTimelineEndMs: Long,
    timebase: TimelineTimebase? = null,
): List<Track> {
    val anchor = tracks.findClipLocation(anchorClipId) ?: return tracks
    val requestedDelta = timebase?.let {
        it.frameIndexAt(requestedTimelineEndMs) - it.frameIndexAt(anchor.clip.timelineEndMs)
    } ?: (requestedTimelineEndMs - anchor.clip.timelineEndMs)
    if (requestedDelta == 0L) return tracks

    val resolvedDeltas = targetClipIds.mapNotNull { clipId ->
        val location = tracks.findClipLocation(clipId) ?: return@mapNotNull null
        val simulatedTrack = trimClipEndToTimelineEnd(
            track = location.track,
            clipId = clipId,
            requestedTimelineEndMs = timebase?.addFrames(location.clip.timelineEndMs, requestedDelta)
                ?: location.clip.timelineEndMs + requestedDelta
        )
        val simulatedClip = simulatedTrack.clips.firstOrNull { it.id == clipId }
            ?: return@mapNotNull null
        timebase?.let {
            it.frameIndexAt(simulatedClip.timelineEndMs) - it.frameIndexAt(location.clip.timelineEndMs)
        } ?: (simulatedClip.timelineEndMs - location.clip.timelineEndMs)
    }
    if (resolvedDeltas.size != targetClipIds.size) return tracks
    val sharedDelta = mostRestrictiveSharedDelta(requestedDelta, resolvedDeltas)
    if (sharedDelta == 0L) return tracks

    return tracks.map { track ->
        targetClipIds.fold(track) { currentTrack, clipId ->
            val clip = currentTrack.clips.firstOrNull { it.id == clipId }
                ?: return@fold currentTrack
            trimClipEndToTimelineEnd(
                track = currentTrack,
                clipId = clipId,
                requestedTimelineEndMs = timebase?.addFrames(clip.timelineEndMs, sharedDelta)
                    ?: clip.timelineEndMs + sharedDelta
            )
        }
    }
}

private fun mostRestrictiveSharedDelta(requestedDeltaMs: Long, resolvedDeltas: List<Long>): Long {
    return when {
        requestedDeltaMs > 0L -> resolvedDeltas.minOrNull()?.coerceIn(0L, requestedDeltaMs) ?: 0L
        requestedDeltaMs < 0L -> resolvedDeltas.maxOrNull()?.coerceIn(requestedDeltaMs, 0L) ?: 0L
        else -> 0L
    }
}

private fun trimClipStartToTimelineStart(
    track: Track,
    clipId: String,
    requestedTimelineStartMs: Long
): Track {
    val clipIndex = track.clips.indexOfFirst { it.id == clipId }
    if (clipIndex < 0) return track
    val clip = track.clips[clipIndex]
    val previousClip = track.clips.getOrNull(clipIndex - 1)
    val minStartMs = previousClip?.timelineEndMs ?: 0L
    val maxStartMs = clip.timelineEndMs - MIN_TIMELINE_CLIP_DURATION_MS
    if (maxStartMs < minStartMs) return track
    val resolvedStartMs = requestedTimelineStartMs.coerceIn(minStartMs, maxStartMs)
    val fallbackTrimStartMs = clip.timelineOffsetToSourceMs(
        (resolvedStartMs - clip.timelineStartMs).coerceAtLeast(0L)
    )
    val resolvedTrimStartMs = trimStartForTimelineStart(
        clip = clip,
        targetTimelineStartMs = resolvedStartMs,
        fallbackTrimStartMs = fallbackTrimStartMs
    )
    val updatedClips = track.clips.toMutableList()
    updatedClips[clipIndex] = clip.copy(
        timelineStartMs = resolvedStartMs,
        trimStartMs = resolvedTrimStartMs
    )
    return track.copy(clips = updatedClips)
}

private fun trimClipEndToTimelineEnd(
    track: Track,
    clipId: String,
    requestedTimelineEndMs: Long
): Track {
    val clipIndex = track.clips.indexOfFirst { it.id == clipId }
    if (clipIndex < 0) return track
    val clip = track.clips[clipIndex]
    val nextClip = track.clips.getOrNull(clipIndex + 1)
    val minEndMs = clip.timelineStartMs + MIN_TIMELINE_CLIP_DURATION_MS
    val maxEndMs = nextClip?.timelineStartMs ?: Long.MAX_VALUE
    if (maxEndMs < minEndMs) return track
    val resolvedEndMs = requestedTimelineEndMs.coerceIn(minEndMs, maxEndMs)
    val fallbackTrimEndMs = clip.timelineOffsetToSourceMs(
        (resolvedEndMs - clip.timelineStartMs).coerceAtLeast(0L)
    )
    val resolvedTrimEndMs = trimEndForTimelineEnd(
        clip = clip,
        targetTimelineEndMs = resolvedEndMs,
        fallbackTrimEndMs = fallbackTrimEndMs
    )
    val updatedClips = track.clips.toMutableList()
    updatedClips[clipIndex] = clip.copy(trimEndMs = resolvedTrimEndMs)
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
