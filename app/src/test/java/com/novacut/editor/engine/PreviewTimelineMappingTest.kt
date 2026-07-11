package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.SpeedCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTimelineMappingTest {

    @Test
    fun `double speed seek maps timeline time to the correct source frame`() {
        val clip = clip(speed = 2f)

        assertEquals(500L, clip.durationMs)
        assertEquals(500L, previewMediaPositionForTimelinePosition(clip, 1_250L))
        assertEquals(1_250L, previewTimelinePositionForMediaPosition(clip, 500L))
        assertEquals(2f, previewSpeedForMediaPosition(clip, 500L))
    }

    @Test
    fun `half speed media position advances twice as far on the timeline`() {
        val clip = clip(speed = 0.5f)

        assertEquals(2_000L, clip.durationMs)
        assertEquals(500L, previewMediaPositionForTimelinePosition(clip, 2_000L))
        assertEquals(2_000L, previewTimelinePositionForMediaPosition(clip, 500L))
    }

    @Test
    fun `speed ramp preview mapping round trips timeline positions`() {
        val clip = clip(speedCurve = SpeedCurve.rampUp(from = 0.5f, to = 2f))

        for (timelineOffsetMs in listOf(0L, clip.durationMs / 4L, clip.durationMs / 2L, clip.durationMs - 1L)) {
            val timelinePositionMs = clip.timelineStartMs + timelineOffsetMs
            val mediaPositionMs = previewMediaPositionForTimelinePosition(clip, timelinePositionMs)
            val mappedTimelineMs = previewTimelinePositionForMediaPosition(clip, mediaPositionMs)
            assertWithin(timelinePositionMs, mappedTimelineMs, toleranceMs = 4L)
        }

        val startSpeed = previewSpeedForMediaPosition(clip, 0L)
        val endSpeed = previewSpeedForMediaPosition(clip, 999L)
        assertTrue(endSpeed > startSpeed)
    }

    @Test
    fun `preview mapping clamps before and after clip boundaries`() {
        val clip = clip(speed = 2f)

        assertEquals(0L, previewMediaPositionForTimelinePosition(clip, 0L))
        assertEquals(999L, previewMediaPositionForTimelinePosition(clip, 10_000L))
        assertEquals(clip.timelineStartMs, previewTimelinePositionForMediaPosition(clip, -100L))
        assertEquals(clip.timelineEndMs, previewTimelinePositionForMediaPosition(clip, 10_000L))
    }

    private fun clip(speed: Float = 1f, speedCurve: SpeedCurve? = null): Clip {
        return Clip(
            id = "clip",
            sourceUri = FakeUri,
            sourceDurationMs = 1_400L,
            timelineStartMs = 1_000L,
            trimStartMs = 200L,
            trimEndMs = 1_200L,
            speed = speed,
            speedCurve = speedCurve
        )
    }

    private fun assertWithin(expected: Long, actual: Long, toleranceMs: Long) {
        assertTrue(
            "expected $actual to be within ${toleranceMs}ms of $expected",
            kotlin.math.abs(actual - expected) <= toleranceMs
        )
    }
}
