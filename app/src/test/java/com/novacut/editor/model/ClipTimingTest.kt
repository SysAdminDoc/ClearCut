package com.novacut.editor.model

import android.net.FakeUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTimingTest {

    @Test
    fun `infinite clip speed falls back to normal playback instead of collapsing duration`() {
        val clip = clip(speed = Float.POSITIVE_INFINITY)

        assertEquals(1_000L, clip.durationMs)
        assertEquals(500L, clip.timelineOffsetToSourceMs(500L))
        assertEquals(500L, clip.sourceTimeToTimelineOffsetMs(500L))
    }

    @Test
    fun `speed curve ignores non finite points and handles`() {
        val clip = clip(
            speedCurve = SpeedCurve(
                listOf(
                    SpeedPoint(0f, 1f, handleOutY = Float.NaN),
                    SpeedPoint(Float.NaN, 0.5f),
                    SpeedPoint(1f, Float.POSITIVE_INFINITY, handleInY = Float.NEGATIVE_INFINITY)
                )
            )
        )

        assertEquals(1_000L, clip.durationMs)
        assertTrue(clip.getEffectiveSpeed(500L).isFinite())
        assertWithin(500L, clip.timelineOffsetToSourceMs(500L), toleranceMs = 2L)
    }

    @Test
    fun `source time maps back to timeline offset for speed curves`() {
        val clip = clip(speedCurve = SpeedCurve.constant(2f))

        assertEquals(500L, clip.durationMs)
        assertWithin(250L, clip.sourceTimeToTimelineOffsetMs(500L) ?: -1L, toleranceMs = 2L)
        assertWithin(500L, clip.timelineOffsetToSourceMs(250L), toleranceMs = 2L)
    }

    private fun assertWithin(expected: Long, actual: Long, toleranceMs: Long) {
        assertTrue(
            "expected $actual to be within ${toleranceMs}ms of $expected",
            kotlin.math.abs(actual - expected) <= toleranceMs
        )
    }

    private fun clip(
        speed: Float = 1f,
        speedCurve: SpeedCurve? = null
    ): Clip {
        return Clip(
            sourceUri = FakeUri,
            sourceDurationMs = 1_000L,
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 1_000L,
            speed = speed,
            speedCurve = speedCurve
        )
    }
}
