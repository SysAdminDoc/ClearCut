package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * A.10 — Oboe resampler scaffold.
 *
 * The engine is a stub today and returns null from [OboeResamplerEngine.resample]
 * regardless of input. These tests cover the parts that don't depend on the
 * Oboe AAR being present: the availability probe, the stub-return contract,
 * the metadata constants, and the [OboeResamplerEngine.estimatedOutputFrames]
 * pure-math helper that audio mix sizing code can rely on today.
 */
class OboeResamplerEngineTest {

    private val engine = OboeResamplerEngine()

    @Test
    fun isAvailable_returnsFalseWhenDepNotWired() {
        // The Oboe AAR is not on the test classpath. Probe must return false.
        assertFalse(engine.isAvailable())
    }

    @Test
    fun resample_returnsNullWhenStubbed() {
        val pcm = FloatArray(1024) { it.toFloat() / 1024f }
        assertNull(
            engine.resample(
                input = pcm,
                channels = 2,
                fromSampleRate = 44_100,
                toSampleRate = 48_000
            )
        )
    }

    @Test
    fun metadataConstantsArePinned() {
        assertEquals("1.9.0", OboeResamplerEngine.TARGET_OBOE_VERSION)
        assertEquals("com.google.oboe", OboeResamplerEngine.TARGET_MAVEN_GROUP)
        assertEquals("oboe", OboeResamplerEngine.TARGET_MAVEN_NAME)
    }

    @Test
    fun estimatedOutputFrames_sameRate_isIdentity() {
        assertEquals(48_000L, engine.estimatedOutputFrames(48_000L, 48_000, 48_000))
    }

    @Test
    fun estimatedOutputFrames_upsample441to48_roundsUp() {
        // 1 second of 44.1 kHz → 48000 frames at 48 kHz exactly when input
        // is 44100; the math is exact.
        assertEquals(48_000L, engine.estimatedOutputFrames(44_100L, 44_100, 48_000))
        // 100 frames at 44.1 kHz → 100 * 48000 / 44100 = 108.84 → ceil 109.
        assertEquals(109L, engine.estimatedOutputFrames(100L, 44_100, 48_000))
    }

    @Test
    fun estimatedOutputFrames_downsample48to441_roundsUp() {
        // 100 frames at 48 kHz → 100 * 44100 / 48000 = 91.875 → ceil 92.
        assertEquals(92L, engine.estimatedOutputFrames(100L, 48_000, 44_100))
    }

    @Test
    fun estimatedOutputFrames_zeroOrNegativeInput_returnsZero() {
        assertEquals(0L, engine.estimatedOutputFrames(0L, 48_000, 48_000))
        assertEquals(0L, engine.estimatedOutputFrames(-5L, 48_000, 48_000))
    }

    @Test
    fun estimatedOutputFrames_zeroRate_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.estimatedOutputFrames(100L, 0, 48_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.estimatedOutputFrames(100L, 48_000, 0)
        }
    }

    @Test
    fun estimatedOutputFrames_longBufferDoesNotOverflow() {
        // 8 hours of 48 kHz audio at 5.1 channels = 138 Mframes.
        // Output at 44.1 kHz = 127 Mframes — well within Long range,
        // but the intermediate input * toHz would overflow Int. Verify
        // the engine routes through Long.
        val eightHourFrames = 8L * 3600L * 48_000L
        val expected = (eightHourFrames * 44_100L + 48_000L - 1L) / 48_000L
        assertEquals(expected, engine.estimatedOutputFrames(eightHourFrames, 48_000, 44_100))
    }
}
