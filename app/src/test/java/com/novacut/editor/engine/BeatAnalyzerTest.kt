package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** BeatAnalyzer on click tracks with a known tempo and known click times. */
class BeatAnalyzerTest {

    @Test
    fun a120BpmClickTrackReads120AndFindsEveryClick() {
        val analysis = BeatAnalyzer.analyze(clicks(bpm = 120.0, seconds = 12.0), channelCount = 2, sampleRate = 44_100)

        assertEquals(120f, analysis.bpm, 0.25f)
        assertClicksFound(analysis, bpm = 120.0, seconds = 12.0)
    }

    @Test
    fun tempoAndTimesFollowTheDecodedRateAndLayout() {
        val analysis = BeatAnalyzer.analyze(
            clicks(bpm = 120.0, seconds = 12.0, sampleRate = 48_000, channels = 1),
            channelCount = 1,
            sampleRate = 48_000,
        )

        assertEquals(120f, analysis.bpm, 0.25f)
        assertClicksFound(analysis, bpm = 120.0, seconds = 12.0)
    }

    @Test
    fun otherTempos() {
        // 97 BPM is a 618.6 ms beat, between two 10 ms histogram bins.
        for (bpm in listOf(90.0, 97.0, 128.0, 75.0)) {
            val analysis = BeatAnalyzer.analyze(clicks(bpm = bpm, seconds = 16.0), channelCount = 2, sampleRate = 44_100)
            assertEquals("at $bpm BPM", bpm.toFloat(), analysis.bpm, 0.25f)
        }
    }

    @Test
    fun everyFourthBeatIsADownbeat() {
        val beats = BeatAnalyzer.analyze(clicks(bpm = 120.0, seconds = 6.0), channelCount = 2, sampleRate = 44_100).beats

        assertEquals(beats.indices.map { it % 4 == 0 }, beats.map { it.isDownbeat })
    }

    @Test
    fun silenceHasNoBeatsAndNoTempo() {
        val analysis = BeatAnalyzer.analyze(ShortArray(44_100 * 2 * 5), channelCount = 2, sampleRate = 44_100)

        assertEquals(emptyList<BeatDetectionEngine.BeatInfo>(), analysis.beats)
        assertEquals(0f, analysis.bpm)
    }

    @Test
    fun twoClicksAreTooFewForATempo() {
        val analysis = BeatAnalyzer.analyze(clicks(bpm = 60.0, seconds = 1.5), channelCount = 2, sampleRate = 44_100)

        assertEquals(2, analysis.beats.size)
        assertEquals(0f, analysis.bpm)
    }

    @Test
    fun noAudioIsNoAnalysis() {
        val analysis = BeatAnalyzer.analyze(ShortArray(0), channelCount = 2, sampleRate = 44_100)

        assertEquals(BeatDetectionEngine.BeatAnalysis(emptyList(), 0f), analysis)
    }

    /** One detected beat within 12 ms of every click, and nothing else. */
    private fun assertClicksFound(analysis: BeatDetectionEngine.BeatAnalysis, bpm: Double, seconds: Double) {
        val expected = clickTimesMs(bpm, seconds)
        assertEquals("beats ${analysis.beats.map { it.timestampMs }}", expected.size, analysis.beats.size)
        val offsets = expected.zip(analysis.beats) { clickMs, beat -> (beat.timestampMs - clickMs).roundToInt() }
        assertTrue("beat minus click, ms: $offsets", offsets.all { abs(it) <= 12 })
    }

    private fun clickTimesMs(bpm: Double, seconds: Double): List<Double> {
        val periodMs = 60_000.0 / bpm
        return generateSequence(LEAD_IN_MS) { it + periodMs }.takeWhile { it + CLICK_MS < seconds * 1000 }.toList()
    }

    /** A click track: a 2 kHz ping decaying over 30 ms on every beat, after a short lead-in. */
    private fun clicks(
        bpm: Double,
        seconds: Double,
        sampleRate: Int = 44_100,
        channels: Int = 2,
    ): ShortArray {
        val frames = (seconds * sampleRate).roundToInt()
        val pcm = ShortArray(frames * channels)
        val clickFrames = (CLICK_MS * sampleRate / 1000).roundToInt()
        for (clickMs in clickTimesMs(bpm, seconds)) {
            val start = (clickMs * sampleRate / 1000).roundToInt()
            for (i in 0 until clickFrames) {
                val t = i.toDouble() / sampleRate
                val value = (0.5 * exp(-t / 0.008) * sin(2 * PI * 2_000.0 * t) * 32767).roundToInt().toShort()
                for (channel in 0 until channels) pcm[(start + i) * channels + channel] = value
            }
        }
        return pcm
    }

    private companion object {
        const val LEAD_IN_MS = 250.0
        const val CLICK_MS = 30.0
    }
}
