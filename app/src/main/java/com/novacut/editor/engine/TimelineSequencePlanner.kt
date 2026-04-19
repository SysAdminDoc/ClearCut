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

internal fun buildTimelineSequenceSteps(
    clips: List<Clip>,
    totalDurationMs: Long? = null
): List<TimelineSequenceStep> {
    val sortedClips = clips
        .filter { it.durationMs > 0L }
        .sortedBy { it.timelineStartMs }

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
