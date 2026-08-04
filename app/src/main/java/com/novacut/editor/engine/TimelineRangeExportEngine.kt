package com.novacut.editor.engine

import com.novacut.editor.model.Caption
import com.novacut.editor.model.ChapterMarker
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectKeyframe
import com.novacut.editor.model.GlobalTransition
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.Mask
import com.novacut.editor.model.MaskKeyframe
import com.novacut.editor.model.MotionTrackPoint
import com.novacut.editor.model.MotionTrackingData
import com.novacut.editor.model.ResolvedTimelineExportRange
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackedObject
import com.novacut.editor.model.TrackedObjectKeyframe
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Non-destructively rebases the complete timeline model into an export range.
 * The exporter receives the returned copy; the editor state is never mutated.
 */
object TimelineRangeExportEngine {
    data class SlicedExport(
        val tracks: List<Track>,
        val textOverlays: List<TextOverlay>,
        val imageOverlays: List<ImageOverlay>,
        val trackedObjects: List<TrackedObject>,
        val globalTransitions: List<GlobalTransition>,
        val chapters: List<ChapterMarker>,
    )

    fun slice(
        tracks: List<Track>,
        range: ResolvedTimelineExportRange,
        textOverlays: List<TextOverlay> = emptyList(),
        imageOverlays: List<ImageOverlay> = emptyList(),
        trackedObjects: List<TrackedObject> = emptyList(),
        globalTransitions: List<GlobalTransition> = emptyList(),
        chapters: List<ChapterMarker> = emptyList(),
    ): SlicedExport {
        val originalClips = tracks.flatMap { it.clips }.associateBy { it.id }
        val slicedTracks = tracks.map { track ->
            track.copy(
                clips = track.clips.mapNotNull { clip -> sliceClip(clip, range) }
            )
        }
        val retainedClips = slicedTracks.flatMap { it.clips }.associateBy { it.id }

        return SlicedExport(
            tracks = slicedTracks,
            textOverlays = textOverlays.mapNotNull { sliceTextOverlay(it, range) },
            imageOverlays = imageOverlays.mapNotNull { sliceImageOverlay(it, range) },
            trackedObjects = trackedObjects.mapNotNull { tracked ->
                val originalClip = originalClips[tracked.sourceClipId] ?: return@mapNotNull null
                val retainedClip = retainedClips[tracked.sourceClipId] ?: return@mapNotNull null
                sliceTrackedObject(tracked, originalClip, retainedClip)
            },
            globalTransitions = globalTransitions.mapNotNull { sliceGlobalTransition(it, range) },
            chapters = chapters.mapNotNull { chapter ->
                chapter.takeIf { it.timeMs >= range.startMs && it.timeMs < range.endMs }
                    ?.copy(timeMs = chapter.timeMs - range.startMs)
            },
        )
    }

    private fun sliceClip(clip: Clip, range: ResolvedTimelineExportRange): Clip? {
        val clipStart = clip.timelineStartMs
        val clipEnd = clip.timelineEndMs
        val overlapStart = max(clipStart, range.startMs)
        val overlapEnd = min(clipEnd, range.endMs)
        if (overlapEnd <= overlapStart || clip.durationMs <= 0L) return null

        val localStart = (overlapStart - clipStart).coerceIn(0L, clip.durationMs)
        val localEnd = (overlapEnd - clipStart).coerceIn(localStart, clip.durationMs)
        val sourceA = clip.timelineOffsetToSourceMs(localStart)
        val sourceB = clip.timelineOffsetToSourceMs(localEnd)
        val newTrimStart = min(sourceA, sourceB).coerceIn(clip.trimStartMs, clip.trimEndMs)
        val newTrimEnd = max(sourceA, sourceB).coerceIn(newTrimStart, clip.trimEndMs)
        if (newTrimEnd <= newTrimStart) return null

        val originalSourceRange = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1L)
        val sourceStartFraction =
            ((newTrimStart - clip.trimStartMs).toFloat() / originalSourceRange).coerceIn(0f, 1f)
        val sourceEndFraction =
            ((newTrimEnd - clip.trimStartMs).toFloat() / originalSourceRange).coerceIn(0f, 1f)
        val restrictedSpeedCurve = clip.speedCurve?.restrictTo(
            startFraction = sourceStartFraction,
            endFraction = sourceEndFraction,
            clipDurationMs = originalSourceRange,
        )
        val newDuration = (overlapEnd - overlapStart).coerceAtLeast(1L)

        return clip.copy(
            timelineStartMs = overlapStart - range.startMs,
            trimStartMs = newTrimStart,
            trimEndMs = newTrimEnd,
            headTransition = clip.headTransition.takeIf { localStart == 0L },
            tailTransition = clip.tailTransition.takeIf { localEnd >= clip.durationMs },
            fadeInMs = (clip.fadeInMs - localStart).coerceIn(0L, newDuration),
            fadeOutMs = (clip.fadeOutMs - (clip.durationMs - localEnd))
                .coerceIn(0L, newDuration),
            keyframes = rebaseKeyframes(clip.keyframes, localStart, localEnd),
            effects = clip.effects.map { effect -> rebaseEffect(effect, localStart, localEnd) },
            speedCurve = restrictedSpeedCurve,
            masks = clip.masks.map { mask -> rebaseMask(mask, localStart, localEnd) },
            motionTrackingData = clip.motionTrackingData?.let {
                rebaseMotionTracking(it, localStart, localEnd)
            },
            captions = clip.captions.mapNotNull {
                sliceCaption(it, localStart, localEnd)
            },
        )
    }

    private fun rebaseKeyframes(
        keyframes: List<Keyframe>,
        startMs: Long,
        endMs: Long,
    ): List<Keyframe> {
        if (keyframes.isEmpty()) return emptyList()
        return keyframes.groupBy { it.property }.values
            .flatMap { propertyKeyframes ->
                val startTemplate = propertyKeyframes.minBy { abs(it.timeOffsetMs - startMs) }
                val endTemplate = propertyKeyframes.minBy { abs(it.timeOffsetMs - endMs) }
                val startValue = KeyframeEngine.getValueAt(
                    keyframes,
                    startTemplate.property,
                    startMs,
                ) ?: startTemplate.value
                val endValue = KeyframeEngine.getValueAt(
                    keyframes,
                    endTemplate.property,
                    endMs,
                ) ?: endTemplate.value
                val boundaries = listOf(
                    startTemplate.copy(timeOffsetMs = 0L, value = startValue),
                    endTemplate.copy(timeOffsetMs = endMs - startMs, value = endValue),
                )
                val interior = propertyKeyframes
                    .filter { it.timeOffsetMs in startMs..endMs }
                    .map { it.copy(timeOffsetMs = it.timeOffsetMs - startMs) }
                (boundaries + interior)
                    .distinctBy { it.timeOffsetMs }
            }
            .sortedWith(compareBy<Keyframe> { it.property.name }.thenBy { it.timeOffsetMs })
    }

    private fun rebaseEffect(effect: Effect, startMs: Long, endMs: Long): Effect {
        if (effect.keyframes.isEmpty()) return effect
        val rebased = effect.keyframes.groupBy { it.paramName }
            .values
            .flatMap { parameterKeyframes ->
                val startTemplate = parameterKeyframes.minBy { abs(it.timeOffsetMs - startMs) }
                val endTemplate = parameterKeyframes.minBy { abs(it.timeOffsetMs - endMs) }
                val startValue = KeyframeEngine.getEffectParamAt(
                    effect.keyframes,
                    startTemplate.paramName,
                    startMs,
                ) ?: startTemplate.value
                val endValue = KeyframeEngine.getEffectParamAt(
                    effect.keyframes,
                    endTemplate.paramName,
                    endMs,
                ) ?: endTemplate.value
                val boundaries = listOf(
                    startTemplate.copy(timeOffsetMs = 0L, value = startValue),
                    endTemplate.copy(timeOffsetMs = endMs - startMs, value = endValue),
                )
                val interior = parameterKeyframes
                    .filter { it.timeOffsetMs in startMs..endMs }
                    .map { it.copy(timeOffsetMs = it.timeOffsetMs - startMs) }
                (boundaries + interior).distinctBy { it.timeOffsetMs }
            }
            .sortedWith(compareBy<EffectKeyframe> { it.paramName }.thenBy { it.timeOffsetMs })
        return effect.copy(keyframes = rebased)
    }

    private fun rebaseMask(mask: Mask, startMs: Long, endMs: Long): Mask {
        if (mask.keyframes.isEmpty()) return mask
        val startTemplate = mask.keyframes.minBy { abs(it.timeOffsetMs - startMs) }
        val endTemplate = mask.keyframes.minBy { abs(it.timeOffsetMs - endMs) }
        val boundaries = listOf(
            MaskKeyframe(
                timeOffsetMs = 0L,
                points = KeyframeEngine.interpolateMaskPoints(mask, startMs),
                easing = startTemplate.easing,
            ),
            MaskKeyframe(
                timeOffsetMs = endMs - startMs,
                points = KeyframeEngine.interpolateMaskPoints(mask, endMs),
                easing = endTemplate.easing,
            ),
        )
        val interior = mask.keyframes
            .filter { it.timeOffsetMs in startMs..endMs }
            .map { it.copy(timeOffsetMs = it.timeOffsetMs - startMs) }
        return mask.copy(
            keyframes = (boundaries + interior)
                .distinctBy { it.timeOffsetMs }
                .sortedBy { it.timeOffsetMs }
        )
    }

    private fun rebaseMotionTracking(
        data: MotionTrackingData,
        startMs: Long,
        endMs: Long,
    ): MotionTrackingData {
        if (data.trackPoints.isEmpty()) return data
        val sorted = data.trackPoints.sortedBy { it.timeOffsetMs }
        val boundaries = listOfNotNull(
            sampleMotionPoint(sorted, startMs),
            sampleMotionPoint(sorted, endMs),
        )
        val interior = sorted
            .filter { it.timeOffsetMs in startMs..endMs }
            .map { it.copy(timeOffsetMs = it.timeOffsetMs - startMs) }
        return data.copy(
            trackPoints = (boundaries.map { it.copy(timeOffsetMs = it.timeOffsetMs - startMs) } + interior)
                .distinctBy { it.timeOffsetMs }
                .sortedBy { it.timeOffsetMs }
        )
    }

    private fun sampleMotionPoint(
        points: List<MotionTrackPoint>,
        timeMs: Long,
    ): MotionTrackPoint? {
        if (points.isEmpty()) return null
        if (timeMs <= points.first().timeOffsetMs) return points.first().copy(timeOffsetMs = timeMs)
        if (timeMs >= points.last().timeOffsetMs) return points.last().copy(timeOffsetMs = timeMs)
        val nextIndex = points.indexOfFirst { it.timeOffsetMs >= timeMs }
        if (nextIndex <= 0) return points.first().copy(timeOffsetMs = timeMs)
        val previous = points[nextIndex - 1]
        val next = points[nextIndex]
        val fraction = ((timeMs - previous.timeOffsetMs).toFloat() /
            (next.timeOffsetMs - previous.timeOffsetMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float) = a + (b - a) * fraction
        return MotionTrackPoint(
            timeOffsetMs = timeMs,
            x = lerp(previous.x, next.x),
            y = lerp(previous.y, next.y),
            scaleX = lerp(previous.scaleX, next.scaleX),
            scaleY = lerp(previous.scaleY, next.scaleY),
            rotation = lerp(previous.rotation, next.rotation),
            confidence = lerp(previous.confidence, next.confidence),
        )
    }

    private fun sliceCaption(caption: Caption, startMs: Long, endMs: Long): Caption? {
        val clippedStart = max(caption.startTimeMs, startMs)
        val clippedEnd = min(caption.endTimeMs, endMs)
        if (clippedEnd <= clippedStart) return null
        return caption.copy(
            startTimeMs = clippedStart - startMs,
            endTimeMs = clippedEnd - startMs,
            words = caption.words.mapNotNull { word ->
                val wordStart = max(word.startTimeMs, clippedStart)
                val wordEnd = min(word.endTimeMs, clippedEnd)
                word.takeIf { wordEnd > wordStart }?.copy(
                    startTimeMs = wordStart - startMs,
                    endTimeMs = wordEnd - startMs,
                )
            },
        )
    }

    private fun sliceTextOverlay(
        overlay: TextOverlay,
        range: ResolvedTimelineExportRange,
    ): TextOverlay? {
        val overlapStart = max(overlay.startTimeMs, range.startMs)
        val overlapEnd = min(overlay.endTimeMs, range.endMs)
        if (overlapEnd <= overlapStart) return null
        val overlayLocalStart = overlapStart - overlay.startTimeMs
        val overlayLocalEnd = overlapEnd - overlay.startTimeMs
        return overlay.copy(
            startTimeMs = overlapStart - range.startMs,
            endTimeMs = overlapEnd - range.startMs,
            keyframes = rebaseKeyframes(overlay.keyframes, overlayLocalStart, overlayLocalEnd),
        )
    }

    private fun sliceImageOverlay(
        overlay: ImageOverlay,
        range: ResolvedTimelineExportRange,
    ): ImageOverlay? {
        val overlapStart = max(overlay.startTimeMs, range.startMs)
        val overlapEnd = min(overlay.endTimeMs, range.endMs)
        if (overlapEnd <= overlapStart) return null
        return overlay.copy(
            startTimeMs = overlapStart - range.startMs,
            endTimeMs = overlapEnd - range.startMs,
        )
    }

    private fun sliceGlobalTransition(
        transition: GlobalTransition,
        range: ResolvedTimelineExportRange,
    ): GlobalTransition? {
        val overlapStart = max(transition.timelineAnchorMs, range.startMs)
        val overlapEnd = min(transition.endMs, range.endMs)
        if (overlapEnd <= overlapStart) return null
        return transition.copy(
            timelineAnchorMs = overlapStart - range.startMs,
            durationMs = overlapEnd - overlapStart,
        )
    }

    /**
     * Tracked-object keyframes are sampled by the renderer in source-time
     * coordinates (the clip trim start is added before sampling). Preserve
     * those absolute source timestamps while trimming the active source span.
     */
    private fun sliceTrackedObject(
        tracked: TrackedObject,
        originalClip: Clip,
        retainedClip: Clip,
    ): TrackedObject {
        if (tracked.keyframes.isEmpty()) return tracked
        val startSourceMs = retainedClip.trimStartMs
        val endSourceMs = retainedClip.trimEndMs
        val sorted = tracked.keyframes.sortedBy { it.clipTimeMs }
        val boundaries = listOfNotNull(
            sampleTrackedObjectKeyframe(sorted, startSourceMs),
            sampleTrackedObjectKeyframe(sorted, endSourceMs),
        )
        val interior = sorted.filter { it.clipTimeMs in startSourceMs..endSourceMs }
        return tracked.copy(
            keyframes = (boundaries + interior)
                .distinctBy { it.clipTimeMs }
                .sortedBy { it.clipTimeMs },
            // Keep this explicit reference so a future change to the tracker
            // coordinate contract cannot silently drop the source association.
            sourceClipId = originalClip.id,
        )
    }

    private fun sampleTrackedObjectKeyframe(
        keyframes: List<TrackedObjectKeyframe>,
        timeMs: Long,
    ): TrackedObjectKeyframe? {
        if (keyframes.isEmpty()) return null
        if (timeMs <= keyframes.first().clipTimeMs) return keyframes.first().copy(clipTimeMs = timeMs)
        if (timeMs >= keyframes.last().clipTimeMs) return keyframes.last().copy(clipTimeMs = timeMs)
        val nextIndex = keyframes.indexOfFirst { it.clipTimeMs >= timeMs }
        if (nextIndex <= 0) return keyframes.first().copy(clipTimeMs = timeMs)
        val previous = keyframes[nextIndex - 1]
        val next = keyframes[nextIndex]
        val fraction = ((timeMs - previous.clipTimeMs).toFloat() /
            (next.clipTimeMs - previous.clipTimeMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float) = a + (b - a) * fraction
        return TrackedObjectKeyframe(
            clipTimeMs = timeMs,
            centerX = lerp(previous.centerX, next.centerX),
            centerY = lerp(previous.centerY, next.centerY),
            width = lerp(previous.width, next.width).coerceIn(0.001f, 1f),
            height = lerp(previous.height, next.height).coerceIn(0.001f, 1f),
            confidence = lerp(previous.confidence, next.confidence).coerceIn(0f, 1f),
            maskPolygon = if (fraction < 0.5f) previous.maskPolygon else next.maskPolygon,
        )
    }
}
