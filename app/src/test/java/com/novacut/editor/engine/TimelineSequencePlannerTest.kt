package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineSequencePlannerTest {

    @Test
    fun buildTimelineSequenceSteps_preservesLeadingMiddleAndTrailingGaps() {
        val first = clip(id = "first", timelineStartMs = 1_000L, durationMs = 2_000L)
        val second = clip(id = "second", timelineStartMs = 5_000L, durationMs = 1_000L)

        val steps = buildTimelineSequenceSteps(
            clips = listOf(second, first),
            totalDurationMs = 7_000L
        )

        assertEquals(
            listOf(
                "gap:0:1000",
                "clip:first:1000:2000",
                "gap:3000:2000",
                "clip:second:5000:1000",
                "gap:6000:1000"
            ),
            steps.map(::describeStep)
        )
    }

    @Test
    fun buildTimelineSequenceSteps_ignoresZeroLengthClips() {
        val empty = clip(id = "empty", timelineStartMs = 0L, durationMs = 0L)
        val valid = clip(id = "valid", timelineStartMs = 500L, durationMs = 500L)

        val steps = buildTimelineSequenceSteps(listOf(empty, valid))

        assertEquals(
            listOf("gap:0:500", "clip:valid:500:500"),
            steps.map(::describeStep)
        )
    }

    @Test
    fun durationMsToUs_clampsBeforeOverflow() {
        assertEquals(1_500_000L, durationMsToUs(1_500L))
        assertEquals(Long.MAX_VALUE / 1_000L * 1_000L, durationMsToUs(Long.MAX_VALUE))
    }

    private fun clip(id: String, timelineStartMs: Long, durationMs: Long): Clip {
        val sourceDurationMs = durationMs.coerceAtLeast(1L)
        return Clip(
            id = id,
            sourceUri = FakeUri,
            sourceDurationMs = sourceDurationMs,
            timelineStartMs = timelineStartMs,
            trimStartMs = 0L,
            trimEndMs = durationMs.coerceIn(0L, sourceDurationMs)
        )
    }

    private fun describeStep(step: TimelineSequenceStep): String {
        return when (step) {
            is TimelineSequenceStep.ClipStep ->
                "clip:${step.clip.id}:${step.timelineStartMs}:${step.durationMs}"
            is TimelineSequenceStep.GapStep ->
                "gap:${step.timelineStartMs}:${step.durationMs}"
        }
    }
}
