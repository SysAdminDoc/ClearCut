package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * LoudnessMeter against the published references: the EBU Tech 3341 minimum
 * requirement cases for integrated loudness (within 0.1 LU), the EBU Tech 3342
 * loudness range cases (within 1 LU), and the ITU-R BS.1770-4 K-weighting tables.
 */
class LoudnessMeterTest {

    @Test
    fun kWeightingAt48kHzMatchesTheBs1770Tables() {
        val (shelf, highPass) = LoudnessMeter.kWeighting(48_000)

        assertEquals(1.53512485958697, shelf.b0, 1e-9)
        assertEquals(-2.69169618940638, shelf.b1, 1e-9)
        assertEquals(1.19839281085285, shelf.b2, 1e-9)
        assertEquals(-1.69065929318241, shelf.a1, 1e-9)
        assertEquals(0.73248077421585, shelf.a2, 1e-9)
        assertEquals(-1.99004745483398, highPass.a1, 1e-9)
        assertEquals(0.99007225036621, highPass.a2, 1e-9)
    }

    @Test
    fun ebu3341Case1And2StereoToneReadsItsLevel() {
        assertEquals(-23.0, integrated(tone(-23.0 to 20.0)), 0.1)
        assertEquals(-33.0, integrated(tone(-33.0 to 20.0)), 0.1)
    }

    @Test
    fun ebu3341Case3RelativeGateDropsTheQuietEnds() {
        assertEquals(-23.0, integrated(tone(-36.0 to 10.0, -23.0 to 60.0, -36.0 to 10.0)), 0.1)
    }

    @Test
    fun ebu3341Case4AbsoluteGateDropsNearSilence() {
        val signal = tone(-72.0 to 10.0, -36.0 to 10.0, -23.0 to 60.0, -36.0 to 10.0, -72.0 to 10.0)

        assertEquals(-23.0, integrated(signal), 0.1)
    }

    @Test
    fun blocksUnderTheAbsoluteGateDontLowerTheRelativeGate() {
        // Case 4's near-silence is too short to move the result; a long quiet bed
        // under -70 LUFS would drag the relative gate down and let itself in.
        assertEquals(-60.0, integrated(tone(-60.0 to 10.0, -78.0 to 100.0)), 0.1)
    }

    @Test
    fun ebu3341Case5AveragesPowerNotDecibels() {
        assertEquals(-23.0, integrated(tone(-26.0 to 20.0, -20.0 to 20.1, -26.0 to 20.0)), 0.1)
    }

    @Test
    fun ebu3342LoudnessRangeCases() {
        assertEquals(10.0, range(tone(-20.0 to 20.0, -30.0 to 20.0)), 1.0)
        assertEquals(5.0, range(tone(-20.0 to 20.0, -15.0 to 20.0)), 1.0)
        assertEquals(20.0, range(tone(-40.0 to 20.0, -20.0 to 20.0)), 1.0)
        assertEquals(15.0, range(tone(-50.0 to 20.0, -35.0 to 20.0, -20.0 to 20.0, -35.0 to 20.0, -50.0 to 20.0)), 1.0)
    }

    @Test
    fun aSteadyToneHasNoLoudnessRangeAndShortTermMatchesIntegrated() {
        val measurement = LoudnessMeter.measure(tone(-23.0 to 20.0), channelCount = 2, sampleRate = 48_000)

        assertEquals(0.0, measurement.loudnessRange.toDouble(), 0.1)
        assertEquals(-23.0, measurement.momentaryMaxLufs.toDouble(), 0.1)
        assertEquals(-23.0, measurement.shortTermMaxLufs.toDouble(), 0.1)
    }

    @Test
    fun truePeakFindsTheCrestBetweenSamples() {
        // A 12 kHz tone at 48 kHz offset by 45 degrees never samples its crest:
        // every sample sits 3 dB below it.
        val amplitudeDb = -6.0
        val signal = tone(amplitudeDb to 1.0, frequency = 12_000.0, phase = PI / 4)

        val peak = LoudnessMeter.measure(signal, channelCount = 2, sampleRate = 48_000).truePeakDbfs.toDouble()

        assertTrue("true peak $peak", peak > amplitudeDb - 0.4 && peak < amplitudeDb + 0.2)
    }

    @Test
    fun monoIsNotSummedLikeStereo() {
        val signal = tone(-23.0 to 20.0, channels = 1, sampleRate = 44_100)

        assertEquals(-26.0, integrated(signal, channels = 1, sampleRate = 44_100), 0.1)
    }

    @Test
    fun aToneReadsTheSameAtEverySampleRate() {
        // On the K-weighting's shelf slope a filter designed for the wrong rate is
        // off by more than 0.1 LU; designed for each rate the readings agree.
        val at48k = integrated(tone(-23.0 to 10.0, frequency = 3_000.0), sampleRate = 48_000)
        for (rate in listOf(44_100, 32_000, 96_000)) {
            val reading = integrated(tone(-23.0 to 10.0, frequency = 3_000.0, sampleRate = rate), sampleRate = rate)
            assertEquals("at $rate Hz", at48k, reading, 0.05)
        }
        // A stereo tone reads its peak level plus the K-weighting gain, less 0.691.
        assertEquals(-23.0 + kGainDb(3_000.0) - 0.691, at48k, 0.05)
    }

    @Test
    fun surroundChannelsWeighMoreAndLfeNotAtAll() {
        val lfeOnly = tone(-20.0 to 10.0, channels = 6, activeChannel = 3)
        val leftSurroundOnly = tone(-23.0 to 10.0, channels = 6, activeChannel = 4)
        val centerOnly = tone(-23.0 to 10.0, channels = 6, activeChannel = 2)

        assertEquals(-70.0, integrated(lfeOnly, channels = 6), 0.0)
        assertEquals(-26.0 + 10 * log10(1.41), integrated(leftSurroundOnly, channels = 6), 0.1)
        assertEquals(-26.0, integrated(centerOnly, channels = 6), 0.1)
    }

    @Test
    fun audioTooShortForABlockReadsAsSilenceButKeepsItsPeak() {
        val measurement = LoudnessMeter.measure(tone(-6.0 to 0.3), channelCount = 2, sampleRate = 48_000)

        assertEquals(-70f, measurement.integratedLufs)
        assertEquals(-70f, measurement.momentaryMaxLufs)
        assertEquals(0f, measurement.loudnessRange)
        assertEquals(-6.0, measurement.truePeakDbfs.toDouble(), 0.2)
    }

    @Test
    fun noAudioReadsAsSilence() {
        val measurement = LoudnessMeter.measure(ShortArray(0), channelCount = 2, sampleRate = 48_000)

        assertEquals(LoudnessEngine.LoudnessMeasurement(-70f, -70f, -70f, -70f, 0f), measurement)
    }

    @Test
    fun cancellationIsCheckedWhileMeasuring() {
        var checks = 0
        LoudnessMeter.measure(tone(-23.0 to 2.0), channelCount = 2, sampleRate = 48_000) { checks++ }

        assertEquals(20, checks)
    }

    private fun integrated(pcm: ShortArray, channels: Int = 2, sampleRate: Int = 48_000): Double =
        LoudnessMeter.measure(pcm, channels, sampleRate).integratedLufs.toDouble()

    private fun range(pcm: ShortArray): Double =
        LoudnessMeter.measure(pcm, channelCount = 2, sampleRate = 48_000).loudnessRange.toDouble()

    /** The 48 kHz K-weighting's gain at [frequency], from its BS.1770 coefficients. */
    private fun kGainDb(frequency: Double): Double {
        val (shelf, highPass) = LoudnessMeter.kWeighting(48_000)
        val w = 2 * PI * frequency / 48_000
        fun LoudnessMeter.Biquad.magnitude(): Double {
            val numerator = hypot(b0 + b1 * cos(w) + b2 * cos(2 * w), -(b1 * sin(w) + b2 * sin(2 * w)))
            val denominator = hypot(1 + a1 * cos(w) + a2 * cos(2 * w), -(a1 * sin(w) + a2 * sin(2 * w)))
            return numerator / denominator
        }
        return 20 * log10(shelf.magnitude() * highPass.magnitude())
    }

    /**
     * Interleaved 16-bit sine segments, each given as (peak dBFS, seconds), on every
     * channel or only [activeChannel].
     */
    private fun tone(
        vararg segments: Pair<Double, Double>,
        channels: Int = 2,
        sampleRate: Int = 48_000,
        frequency: Double = 1_000.0,
        phase: Double = 0.0,
        activeChannel: Int? = null,
    ): ShortArray {
        val frames = segments.sumOf { (it.second * sampleRate).roundToInt() }
        val pcm = ShortArray(frames * channels)
        var n = 0
        for ((levelDb, seconds) in segments) {
            val amplitude = 10.0.pow(levelDb / 20.0) * 32767.0
            repeat((seconds * sampleRate).roundToInt()) {
                val sample = (amplitude * sin(2 * PI * frequency * n / sampleRate + phase)).roundToInt().toShort()
                for (channel in 0 until channels) {
                    if (activeChannel == null || channel == activeChannel) pcm[n * channels + channel] = sample
                }
                n++
            }
        }
        return pcm
    }
}
