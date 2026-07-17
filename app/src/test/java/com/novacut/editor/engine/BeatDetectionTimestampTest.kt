package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract tests for the spectral-flux frame timestamp math. detectBeats used to
 * hardcode 44100 Hz stereo, but AudioEngine.decodeToPCM returns source-native
 * PCM — a 48 kHz mono source must produce 48 kHz timestamps, not 44.1 kHz ones
 * (~8.8% slow beats and a matching BPM skew).
 */
class BeatDetectionTimestampTest {

    @Test
    fun timestampTracks48kMonoSource() {
        // hop 512 at 48000 Hz: frame N sits at N*512/48 ms.
        assertEquals(0L, beatFrameTimestampMs(0, 512, 48_000))
        assertEquals(10L, beatFrameTimestampMs(1, 512, 48_000)) // 10.66ms truncated
        assertEquals(1_066L, beatFrameTimestampMs(100, 512, 48_000))
        assertEquals(10_666L, beatFrameTimestampMs(1_000, 512, 48_000))
    }

    @Test
    fun timestampTracks44k1StereoSource() {
        // Channel count only affects the mono downmix, not the frame clock:
        // hop 512 at 44100 Hz puts frame 100 at ~1161 ms (truncated to 1160).
        assertEquals(1_160L, beatFrameTimestampMs(100, 512, 44_100))
        assertEquals(11_609L, beatFrameTimestampMs(1_000, 512, 44_100))
    }

    @Test
    fun ratesDivergeAsTheOldHardcodeDid() {
        // The old 44100 assumption made a 48 kHz source read ~8.8% late.
        val at48k = beatFrameTimestampMs(1_000, 512, 48_000)
        val at44k1 = beatFrameTimestampMs(1_000, 512, 44_100)
        assertEquals(943L, at44k1 - at48k)
    }

    @Test
    fun nonPositiveSampleRateIsSafe() {
        assertEquals(0L, beatFrameTimestampMs(100, 512, 0))
        assertEquals(0L, beatFrameTimestampMs(100, 512, -1))
    }

    @Test
    fun largeFrameIndexDoesNotOverflow() {
        // 10 hours of 512-hop frames at 48 kHz: i*hop*1000 must run in Long math.
        val frames = 48_000 * 36_000 / 512
        assertEquals(36_000_000L, beatFrameTimestampMs(frames, 512, 48_000))
    }
}
