package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilizationPolicyTest {

    @Test
    fun correctionTrajectoryRemovesCumulativeDriftWithoutChangingSampleTimes() {
        val samples = listOf(
            StabilizationPolicy.MotionSample(100L, 0.10f, 0f, 0.9f),
            StabilizationPolicy.MotionSample(200L, 0.10f, 0f, 0.9f),
            StabilizationPolicy.MotionSample(300L, 0.10f, 0f, 0.9f),
        )

        val corrections = StabilizationPolicy.correctionTrajectory(samples, windowSize = 3)

        assertEquals(samples.map { it.timestampMs }, corrections.map { it.timestampMs })
        assertTrue(corrections.first().dx > 0f)
        assertTrue(corrections.last().dx < 0f)
        assertTrue(corrections.all { it.dx in -1f..1f && it.dy in -1f..1f })
    }

    @Test
    fun cropScaleIsMonotonicAndBounded() {
        val calm = StabilizationPolicy.recommendedCropScale(emptyList(), cropPercentage = 0.1f)
        val moving = StabilizationPolicy.recommendedCropScale(
            listOf(StabilizationPolicy.Correction(100L, 0.2f, -0.1f, 1f)),
            cropPercentage = 0.1f,
        )

        assertEquals(1.1f, calm, 0.0001f)
        assertTrue(moving > calm)
        assertTrue(moving <= 1.3f)
    }

    @Test
    fun lowRamAndHighResolutionInputsUseLongerSamplingIntervals() {
        val normal = StabilizationPolicy.analysisIntervalMs(false, 1920, 1080)
        val highResolution = StabilizationPolicy.analysisIntervalMs(false, 3840, 2160)
        val lowRam = StabilizationPolicy.analysisIntervalMs(true, 1920, 1080)

        assertTrue(highResolution > normal)
        assertTrue(lowRam > normal)
        assertEquals(120_000L, StabilizationPolicy.maximumDurationMs(false))
        assertEquals(60_000L, StabilizationPolicy.maximumDurationMs(true))
    }
}
