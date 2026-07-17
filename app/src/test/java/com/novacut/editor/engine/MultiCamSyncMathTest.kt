package com.novacut.editor.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Contract tests for multicam sync-offset math. extractMonoPcm decimates by an
 * integer factor, so a 44100 Hz source requested at 8000 Hz actually lands on
 * 8820 Hz — offset-to-ms conversion (and cross-correlation of mixed-rate pairs)
 * must use the effective rate, not the requested one (~10% offset error).
 */
class MultiCamSyncMathTest {

    @Test
    fun effectiveRateReflectsIntegerDecimation() {
        assertEquals(8_820, effectiveDecimatedRate(44_100, 8_000)) // 44100 / 5
        assertEquals(8_000, effectiveDecimatedRate(48_000, 8_000)) // 48000 / 6
        assertEquals(8_000, effectiveDecimatedRate(8_000, 8_000)) // no decimation
        assertEquals(11_025, effectiveDecimatedRate(22_050, 8_000)) // 22050 / 2
        assertEquals(8_000, effectiveDecimatedRate(16_000, 8_000)) // 16000 / 2
    }

    @Test
    fun effectiveRateBelowTargetPassesThrough() {
        // Source already below the target: decimation factor 1, keep source rate.
        assertEquals(4_000, effectiveDecimatedRate(4_000, 8_000))
    }

    @Test
    fun effectiveRateGuardsDegenerateInputs() {
        assertEquals(1, effectiveDecimatedRate(0, 8_000))
        // Target coerced to 1 Hz mirrors extractMonoPcm's safeTargetRate guard.
        assertEquals(1, effectiveDecimatedRate(8_000, 0))
    }

    @Test
    fun offsetMsUsesEffectiveRate() {
        // One second of 44100->8820 Hz samples is exactly 1000 ms; dividing by the
        // requested 8000 Hz (the old bug) would have claimed 1102 ms (~10% off).
        assertEquals(1_000L, syncOffsetMs(8_820, 8_820))
        assertEquals(1_102L, syncOffsetMs(8_820, 8_000)) // documents the old skew
        assertEquals(-500L, syncOffsetMs(-4_410, 8_820))
        assertEquals(0L, syncOffsetMs(0, 8_820))
        assertEquals(0L, syncOffsetMs(1_000, 0))
    }

    @Test
    fun resampleLinearIsIdentityAtSameRate() {
        val input = floatArrayOf(0f, 1f, 2f, 3f)
        assertSame(input, resampleLinear(input, 8_000, 8_000))
    }

    @Test
    fun resampleLinearHalvesExactly() {
        // 2:1 downsample of a ramp lands on the even-index samples (frac = 0).
        val input = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val out = resampleLinear(input, 4_000, 2_000)
        assertArrayEquals(floatArrayOf(0f, 2f, 4f, 6f), out, 1e-6f)
    }

    @Test
    fun resampleLinearMatchesMixedEffectiveRatePair() {
        // 8820 -> 8000 (the mixed 44100/48000-source case): output length scales
        // by the rate ratio so both correlated signals share a time base.
        val input = FloatArray(8_820) { it.toFloat() }
        val out = resampleLinear(input, 8_820, 8_000)
        assertEquals(8_000, out.size)
        // A linear ramp resamples onto itself: sample i sits at i * 8820 / 8000.
        assertEquals(0f, out[0], 1e-3f)
        assertEquals(4_000 * 8_820f / 8_000f, out[4_000], 1e-1f)
    }
}
