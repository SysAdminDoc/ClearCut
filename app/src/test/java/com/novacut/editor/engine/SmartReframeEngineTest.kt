package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartReframeEngineTest {

    @Test
    fun smoothCropTrajectory_singleElement_returnsSame() {
        val input = listOf(0.5f to 0.5f)
        val result = SmartReframeEngine.smoothCropTrajectory(input)
        assertEquals(1, result.size)
        assertEquals(0.5f, result[0].first, 1e-6f)
    }

    @Test
    fun smoothCropTrajectory_stationaryInput_returnsSame() {
        val input = List(10) { 0.5f to 0.5f }
        val result = SmartReframeEngine.smoothCropTrajectory(input)
        assertEquals(10, result.size)
        result.forEach {
            assertEquals(0.5f, it.first, 1e-6f)
            assertEquals(0.5f, it.second, 1e-6f)
        }
    }

    @Test
    fun smoothCropTrajectory_convergesToTarget() {
        val input = listOf(0f to 0f) + List(100) { 1f to 1f }
        val result = SmartReframeEngine.smoothCropTrajectory(input, alpha = 0.1f)
        val last = result.last()
        assertTrue("Should converge near 1.0, got ${last.first}", last.first > 0.95f)
        assertTrue("Should converge near 1.0, got ${last.second}", last.second > 0.95f)
    }

    @Test
    fun smoothCropTrajectory_lowerAlpha_smootherOutput() {
        val input = (0 until 20).map { if (it % 2 == 0) 0f to 0f else 1f to 1f }
        val smooth = SmartReframeEngine.smoothCropTrajectory(input, alpha = 0.05f)
        val rough = SmartReframeEngine.smoothCropTrajectory(input, alpha = 0.5f)

        val smoothVariance = variance(smooth.map { it.first })
        val roughVariance = variance(rough.map { it.first })
        assertTrue("Lower alpha should produce lower variance", smoothVariance < roughVariance)
    }

    @Test
    fun smoothCropTrajectory_nanAlpha_fallsBackToDefault() {
        val input = listOf(0f to 0f, 1f to 1f)
        val result = SmartReframeEngine.smoothCropTrajectory(input, alpha = Float.NaN)
        assertEquals(2, result.size)
        assertTrue(result[1].first.isFinite())
    }

    @Test
    fun smoothCropTrajectory_outOfRangeAlpha_clamped() {
        val input = listOf(0f to 0f, 1f to 1f, 1f to 1f)
        val result = SmartReframeEngine.smoothCropTrajectory(input, alpha = 2f)
        assertTrue(result[1].first <= 1f)
        assertTrue(result[1].first >= 0f)
    }

    @Test
    fun smoothCropTrajectory_alphaOne_noSmoothing() {
        val input = listOf(0f to 0f, 1f to 1f, 0.5f to 0.5f)
        val result = SmartReframeEngine.smoothCropTrajectory(input, alpha = 1f)
        assertEquals(1f, result[1].first, 1e-6f)
        assertEquals(0.5f, result[2].first, 1e-6f)
    }

    @Test
    fun smoothCropTrajectory_alphaZero_allFirstValue() {
        val input = listOf(0.2f to 0.3f, 0.8f to 0.9f, 0.5f to 0.5f)
        val result = SmartReframeEngine.smoothCropTrajectory(input, alpha = 0f)
        result.forEach {
            assertEquals(0.2f, it.first, 1e-6f)
            assertEquals(0.3f, it.second, 1e-6f)
        }
    }

    @Test
    fun resolveStrategy_stationaryInput_choosesStationary() {
        val input = List(10) { 0.5f to 0.5f }
        val result = SmartReframeEngine.resolveStrategy(
            input, SmartReframeEngine.ReframeStrategy.PAN
        )
        assertEquals(SmartReframeEngine.ReframeStrategy.STATIONARY, result)
    }

    @Test
    fun resolveStrategy_movingInput_keepsPan() {
        val input = (0 until 10).map { it / 10f to 0.5f }
        val result = SmartReframeEngine.resolveStrategy(
            input, SmartReframeEngine.ReframeStrategy.PAN
        )
        assertEquals(SmartReframeEngine.ReframeStrategy.PAN, result)
    }

    @Test
    fun resolveStrategy_nonPanPreferred_alwaysReturnsPreferred() {
        val input = List(10) { 0.5f to 0.5f }
        assertEquals(
            SmartReframeEngine.ReframeStrategy.TRACK,
            SmartReframeEngine.resolveStrategy(input, SmartReframeEngine.ReframeStrategy.TRACK)
        )
        assertEquals(
            SmartReframeEngine.ReframeStrategy.STATIONARY,
            SmartReframeEngine.resolveStrategy(input, SmartReframeEngine.ReframeStrategy.STATIONARY)
        )
    }

    @Test
    fun resolveStrategy_smallDrift_belowThreshold_choosesStationary() {
        val input = listOf(0.5f to 0.5f, 0.505f to 0.505f, 0.51f to 0.51f)
        val result = SmartReframeEngine.resolveStrategy(
            input, SmartReframeEngine.ReframeStrategy.PAN
        )
        assertEquals(SmartReframeEngine.ReframeStrategy.STATIONARY, result)
    }

    @Test
    fun resolveStrategy_largeDrift_aboveThreshold_keepsPan() {
        val input = listOf(0.1f to 0.1f, 0.5f to 0.5f, 0.9f to 0.9f)
        val result = SmartReframeEngine.resolveStrategy(
            input, SmartReframeEngine.ReframeStrategy.PAN
        )
        assertEquals(SmartReframeEngine.ReframeStrategy.PAN, result)
    }

    private fun variance(values: List<Float>): Float {
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }
}
