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
    fun buildTimelineSequenceSteps_appliesPositiveTrackOffset() {
        val clip = clip(id = "clip", timelineStartMs = 1_000L, durationMs = 2_000L)

        val steps = buildTimelineSequenceSteps(
            clips = listOf(clip),
            totalDurationMs = 5_000L,
            timelineOffsetMs = 500L,
        )

        assertEquals(
            listOf("gap:0:1500", "clip:clip:1500:2000", "gap:3500:1500"),
            steps.map(::describeStep),
        )
    }

    @Test
    fun buildTimelineSequenceSteps_cropsClipThatStartsBeforeTimelineZero() {
        val clip = clip(id = "clip", timelineStartMs = 500L, durationMs = 2_000L)

        val steps = buildTimelineSequenceSteps(
            clips = listOf(clip),
            timelineOffsetMs = -750L,
        )

        val clipStep = steps.single() as TimelineSequenceStep.ClipStep
        assertEquals(0L, clipStep.timelineStartMs)
        assertEquals(1_750L, clipStep.durationMs)
        assertEquals(250L, clipStep.clip.trimStartMs)
    }

    @Test
    fun buildTimelineSequenceSteps_zeroOffsetKeepsClipDocumentUnchanged() {
        val clip = clip(id = "clip", timelineStartMs = 1_000L, durationMs = 2_000L)

        val clipStep = buildTimelineSequenceSteps(listOf(clip)).single {
            it is TimelineSequenceStep.ClipStep
        } as TimelineSequenceStep.ClipStep

        assertEquals(clip, clipStep.clip)
    }

    @Test
    fun buildTimelineSequenceSteps_appliesPerClipAudioSyncOffsetAndClearsTransientOffset() {
        val clip = clip(id = "audio", timelineStartMs = 1_000L, durationMs = 2_000L)
            .copy(audioSyncOffsetMs = 500L)

        val steps = buildTimelineSequenceSteps(
            clips = listOf(clip),
            totalDurationMs = 4_000L,
            includeClipAudioSyncOffset = true,
        )

        assertEquals(
            listOf("gap:0:1500", "clip:audio:1500:2000", "gap:3500:500"),
            steps.map(::describeStep),
        )
        val clipStep = steps.filterIsInstance<TimelineSequenceStep.ClipStep>().single()
        assertEquals(0L, clipStep.clip.audioSyncOffsetMs)
        assertEquals(1_000L, clip.timelineStartMs)
        assertEquals(500L, clip.audioSyncOffsetMs)
    }

    @Test
    fun buildTimelineSequenceSteps_cropsNegativePerClipAudioSyncOffset() {
        val clip = clip(id = "audio", timelineStartMs = 500L, durationMs = 2_000L)
            .copy(audioSyncOffsetMs = -750L)

        val clipStep = buildTimelineSequenceSteps(
            clips = listOf(clip),
            includeClipAudioSyncOffset = true,
        ).single() as TimelineSequenceStep.ClipStep

        assertEquals(0L, clipStep.timelineStartMs)
        assertEquals(1_750L, clipStep.durationMs)
        assertEquals(250L, clipStep.clip.trimStartMs)
        assertEquals(0L, clipStep.clip.audioSyncOffsetMs)
    }

    @Test
    fun mismatchedVideoAndAudioDurationsShareTheSameTimelineEnd() {
        val video = clip(id = "video", timelineStartMs = 0L, durationMs = 3_000L)
        val audio = clip(id = "audio", timelineStartMs = 0L, durationMs = 3_030L)
        val timelineEndMs = maxOf(video.timelineEndMs, audio.timelineEndMs)

        val videoSteps = buildTimelineSequenceSteps(listOf(video), timelineEndMs)
        val audioSteps = buildTimelineSequenceSteps(listOf(audio), timelineEndMs)

        fun endMs(steps: List<TimelineSequenceStep>): Long =
            steps.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L

        assertEquals(timelineEndMs, endMs(videoSteps))
        assertEquals(timelineEndMs, endMs(audioSteps))
        assertEquals("gap:3000:30", describeStep(videoSteps.last()))
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
