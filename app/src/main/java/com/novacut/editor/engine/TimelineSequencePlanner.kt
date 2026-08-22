package com.novacut.editor.engine

import com.novacut.editor.model.Clip

internal sealed class TimelineSequenceStep {
    abstract val timelineStartMs: Long
    abstract val durationMs: Long

    data class ClipStep(
        val clip: Clip,
        override val timelineStartMs: Long,
        override val durationMs: Long
    ) : TimelineSequenceStep()

    data class GapStep(
        override val timelineStartMs: Long,
        override val durationMs: Long
    ) : TimelineSequenceStep()
}

internal fun shiftClipForTimelineOffset(clip: Clip, offsetMs: Long): Clip? {
    if (offsetMs == 0L) return clip
    val shiftedStartMs = clip.timelineStartMs + offsetMs
    if (shiftedStartMs >= 0L) {
        return clip.copy(timelineStartMs = shiftedStartMs)
    }

    val elapsedMs = -shiftedStartMs
    if (elapsedMs >= clip.durationMs) return null
    val shiftedTrimStartMs = clip.timelineOffsetToSourceMs(elapsedMs)
    if (shiftedTrimStartMs >= clip.trimEndMs) return null
    return clip.copy(
        timelineStartMs = 0L,
        trimStartMs = shiftedTrimStartMs,
    )
}

internal fun shiftedTimelineClips(
    clips: List<Clip>,
    offsetMs: Long,
    includeClipAudioSyncOffset: Boolean = false,
): List<Clip> {
    if (offsetMs == 0L && !includeClipAudioSyncOffset) {
        return clips.filter { it.durationMs > 0L }.sortedBy { it.timelineStartMs }
    }
    return clips
        .mapNotNull { clip ->
            if (clip.durationMs <= 0L) {
                null
            } else {
                val clipOffsetMs = if (includeClipAudioSyncOffset) clip.audioSyncOffsetMs else 0L
                shiftClipForTimelineOffset(clip, offsetMs + clipOffsetMs)
                    ?.let { shifted ->
                        if (includeClipAudioSyncOffset) shifted.copy(audioSyncOffsetMs = 0L) else shifted
                    }
            }
        }
        .filter { it.durationMs > 0L }
        .sortedBy { it.timelineStartMs }
}

internal fun buildTimelineSequenceSteps(
    clips: List<Clip>,
    totalDurationMs: Long? = null,
    timelineOffsetMs: Long = 0L,
    includeClipAudioSyncOffset: Boolean = false,
): List<TimelineSequenceStep> {
    val sortedClips = shiftedTimelineClips(clips, timelineOffsetMs, includeClipAudioSyncOffset)

    val steps = mutableListOf<TimelineSequenceStep>()
    var cursorMs = 0L

    for (clip in sortedClips) {
        if (clip.timelineStartMs > cursorMs) {
            steps += TimelineSequenceStep.GapStep(
                timelineStartMs = cursorMs,
                durationMs = clip.timelineStartMs - cursorMs
            )
        }

        steps += TimelineSequenceStep.ClipStep(
            clip = clip,
            timelineStartMs = clip.timelineStartMs,
            durationMs = clip.durationMs
        )
        cursorMs = maxOf(cursorMs, clip.timelineEndMs)
    }

    val requestedDurationMs = totalDurationMs?.coerceAtLeast(0L)
    if (requestedDurationMs != null && requestedDurationMs > cursorMs) {
        steps += TimelineSequenceStep.GapStep(
            timelineStartMs = cursorMs,
            durationMs = requestedDurationMs - cursorMs
        )
    }

    return steps
}

internal fun durationMsToUs(durationMs: Long): Long {
    return durationMs.coerceAtLeast(0L).coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
}
