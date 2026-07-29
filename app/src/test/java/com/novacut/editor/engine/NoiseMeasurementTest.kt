package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The SNR the editor shows must be measured from the audio, not assumed. These
 * fixtures are deterministic (fixed-seed noise, exact tones) so the estimator's
 * behaviour is pinned rather than sampled.
 */
class NoiseMeasurementTest {

    private val sampleRate = 48_000

    @Test
    fun pcmRoundTripsThroughDecodeAndEncode() {
        val samples = floatArrayOf(0f, 0.5f, -0.5f, 0.999f, -0.999f)
        val bytes = ByteArray(samples.size * 2)
        encodePcm16le(samples, bytes)
        val decoded = FloatArray(samples.size)
        decodePcm16le(bytes, samples.size, decoded)

        for (i in samples.indices) {
            assertEquals("sample $i", samples[i], decoded[i], 1e-3f)
        }
    }

    @Test
    fun steadyToneHasNoUsableHeadroomOverItsOwnFloor() {
        // Constant-amplitude signal: every frame has the same power, so the
        // measured SNR is ~0 dB. This is the estimator being honest about a file
        // with no quiet stretches, not a bug.
        val snr = measureSnrDb(DoubleArray(64) { 0.25 })

        assertNotNull(snr)
        assertEquals(0f, snr!!, 0.01f)
    }

    @Test
    fun loudContentOverQuietFloorMeasuresPositiveSnr() {
        // 90% loud frames, 10% near-silent — a speech-like distribution.
        val powers = DoubleArray(100) { if (it < 10) 1e-6 else 1e-2 }

        val snr = measureSnrDb(powers)

        assertNotNull(snr)
        assertTrue("expected a large positive SNR, got $snr", snr!! > 30f)
    }

    @Test
    fun tooFewFramesCannotBeMeasured() {
        assertNull(measureSnrDb(DoubleArray(3) { 0.1 }))
    }

    @Test
    fun cleanAudioIsClassifiedCleanRegardlessOfZeroCrossings() {
        val (type, freq) = classifyNoise(quietFrameZeroCrossingRate = 0.9f, sampleRate = sampleRate, snrDb = 42f)

        assertEquals("clean", type)
        assertNull(freq)
    }

    @Test
    fun highZeroCrossingQuietFramesReadAsHiss() {
        val (type, freq) = classifyNoise(0.4f, sampleRate, snrDb = 12f)

        assertEquals("hiss", type)
        assertNull(freq)
    }

    @Test
    fun lowZeroCrossingQuietFramesReadAsHumWithAnImpliedFundamental() {
        val (type, freq) = classifyNoise(0.0025f, sampleRate, snrDb = 12f)

        assertEquals("hum", type)
        assertNotNull(freq)
        assertEquals(60f, freq!!, 1f)
    }

    @Test
    fun midRangeZeroCrossingsReadAsBroadband() {
        assertEquals("broadband", classifyNoise(0.12f, sampleRate, snrDb = 12f).first)
    }

    @Test
    fun noisyFixtureMeasuresLowerSnrThanTheCleanOne() = withTempDir { dir ->
        val noisy = writePcm(File(dir, "noisy.pcm"), buildSpeechLike(noiseAmplitude = 0.20f))
        val clean = writePcm(File(dir, "clean.pcm"), buildSpeechLike(noiseAmplitude = 0.001f))

        val noisyProfile = measureNoiseProfile(noisy, sampleRate)
        val cleanProfile = measureNoiseProfile(clean, sampleRate)

        assertNotNull(noisyProfile)
        assertNotNull(cleanProfile)
        assertTrue(
            "clean (${cleanProfile!!.estimatedSnrDb}) should measure above noisy (${noisyProfile!!.estimatedSnrDb})",
            cleanProfile.estimatedSnrDb > noisyProfile.estimatedSnrDb
        )
    }

    @Test
    fun aFileTooShortToMeasureReturnsNullRatherThanAGuess() = withTempDir { dir ->
        val tiny = writePcm(File(dir, "tiny.pcm"), FloatArray(64) { 0.1f })

        assertNull(measureNoiseProfile(tiny, sampleRate))
    }

    @Test
    fun measurementIsDeterministicAcrossRuns() = withTempDir { dir ->
        val a = writePcm(File(dir, "a.pcm"), buildSpeechLike(noiseAmplitude = 0.1f))
        val b = writePcm(File(dir, "b.pcm"), buildSpeechLike(noiseAmplitude = 0.1f))

        val first = measureNoiseProfile(a, sampleRate)
        val second = measureNoiseProfile(b, sampleRate)

        assertNotNull(first)
        assertEquals(first!!.estimatedSnrDb, second!!.estimatedSnrDb, 0f)
        assertEquals(first.type, second.type)
    }

    /**
     * 2 s of alternating 200 ms "speech" bursts (a 300 Hz tone) and gaps that
     * carry only noise, so the estimator has a real floor to find.
     */
    private fun buildSpeechLike(noiseAmplitude: Float): FloatArray {
        val random = Random(seed = 20260729)
        val total = sampleRate * 2
        val burstSamples = sampleRate / 5
        return FloatArray(total) { i ->
            val inBurst = (i / burstSamples) % 2 == 0
            val noise = (random.nextFloat() * 2f - 1f) * noiseAmplitude
            val tone = if (inBurst) 0.6f * sin(2.0 * PI * 300.0 * i / sampleRate).toFloat() else 0f
            (tone + noise).coerceIn(-1f, 1f)
        }
    }

    private fun writePcm(file: File, samples: FloatArray): File {
        val bytes = ByteArray(samples.size * 2)
        encodePcm16le(samples, bytes)
        file.writeBytes(bytes)
        return file
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("noise-measure-").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
