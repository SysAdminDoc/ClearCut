package com.novacut.editor.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Multicam audio sync on synthetic recordings with a known offset between them. */
class AudioSyncCorrelatorTest {

    private val rate = 8_000

    @Test
    fun aCameraThatStartedLaterHasAPositiveLag() {
        val scene = noise(seconds = 20.0, seed = 1)
        val laterStart = 12_345
        val a = normalized(scene.copyOfRange(0, 15 * rate))
        val b = normalized(scene.copyOfRange(laterStart, laterStart + 15 * rate))

        val match = AudioSyncCorrelator.bestLag(a, b, maxLagSamples = 5 * rate)

        assertEquals(laterStart, match.lagSamples)
        assertEquals(1f, match.score, 0.02f)
    }

    @Test
    fun aCameraThatStartedEarlierHasANegativeLag() {
        val scene = noise(seconds = 20.0, seed = 2)
        val a = normalized(scene.copyOfRange(4_000, 4_000 + 12 * rate))
        val b = normalized(scene.copyOfRange(0, 15 * rate))

        assertEquals(-4_000, AudioSyncCorrelator.bestLag(a, b, maxLagSamples = 5 * rate).lagSamples)
    }

    @Test
    fun eachMicrophoneHearsItsOwnRoomNoiseToo() {
        // Same scene, independent noise as loud as the scene on each recording.
        val scene = noise(seconds = 20.0, seed = 3)
        val roomA = noise(seconds = 15.0, seed = 4)
        val roomB = noise(seconds = 15.0, seed = 5)
        val a = FloatArray(15 * rate) { scene[it] + roomA[it] }
        val b = FloatArray(15 * rate) { scene[it + 6_000] + roomB[it] }

        val match = AudioSyncCorrelator.bestLag(normalized(a), normalized(b), maxLagSamples = 5 * rate)

        assertEquals(6_000, match.lagSamples)
        assertEquals(0.5f, match.score, 0.05f)
    }

    @Test
    fun theSearchStaysInsideTheMaximumOffset() {
        val scene = noise(seconds = 20.0, seed = 6)
        val a = normalized(scene.copyOfRange(0, 15 * rate))
        val b = normalized(scene.copyOfRange(3 * rate, 18 * rate))

        val match = AudioSyncCorrelator.bestLag(a, b, maxLagSamples = 2 * rate)

        assertTrue("lag ${match.lagSamples}", match.lagSamples in -2 * rate..2 * rate)
        assertTrue("score ${match.score}", match.score < 0.1f)
    }

    @Test
    fun matchesTheDirectSumAtEveryLag() {
        // The FFT result against the plain definition, on uneven lengths.
        val a = normalized(noise(seconds = 0.2, seed = 7))
        val hum = noise(seconds = 0.2, seed = 8)
        val b = normalized(FloatArray(1_234) { sin(2 * PI * 440 * it / rate).toFloat() + hum[it] })
        val range = min(a.size, b.size) / 2

        val direct = (-range..range).maxByOrNull { lag -> directScore(a, b, lag) }!!
        val match = AudioSyncCorrelator.bestLag(a, b, maxLagSamples = range)

        assertEquals(direct, match.lagSamples)
        assertEquals(directScore(a, b, direct), match.score, 1e-4f)
    }

    @Test
    fun anEmptyRecordingHasNoMatch() {
        assertEquals(AudioSyncCorrelator.Match(0, 0f), AudioSyncCorrelator.bestLag(FloatArray(0), noise(1.0, 9), 8_000))
    }

    @Test
    fun cancellationIsChecked() {
        var checks = 0
        AudioSyncCorrelator.bestLag(noise(1.0, 10), noise(1.0, 11), maxLagSamples = 4_000) { checks++ }

        assertTrue(checks > 0)
    }

    @Test
    fun decimationMixesToMonoAndAveragesEachBlock() {
        // 48 kHz stereo to 8 kHz: six frames per sample, left and right averaged.
        val decimator = MonoDecimator.create(sourceRate = 48_000, channels = 2, targetSampleRate = 8_000, maxSeconds = 10)
        val pcm = ShortArray(24) { i -> if (i % 2 == 0) (1_000 * (i / 2)).toShort() else 0 }

        // Split mid-frame, as a decoder buffer boundary could.
        decimator.add(pcm.copyOfRange(0, 7))
        decimator.add(pcm.copyOfRange(7, pcm.size))
        val result = decimator.result()

        assertEquals(8_000, result.effectiveSampleRate)
        val left = (0 until 12).map { 1_000.0 * it / 32_768 }
        val expected = left.chunked(6) { block -> (block.average() / 2).toFloat() }
        assertArrayEquals(expected.toFloatArray(), result.samples, 1e-6f)
    }

    @Test
    fun decimationKeepsTheRateItLandsOnAndStopsAtItsCap() {
        val decimator = MonoDecimator.create(sourceRate = 44_100, channels = 1, targetSampleRate = 8_000, maxSeconds = 1)

        decimator.add(ShortArray(44_100 * 3) { 100 })
        val result = decimator.result()

        assertEquals(8_820, result.effectiveSampleRate)
        assertEquals(8_820, result.samples.size)
        assertTrue(decimator.isFull)
    }

    private fun directScore(a: FloatArray, b: FloatArray, lag: Int): Float {
        val startA = max(0, lag)
        val startB = max(0, -lag)
        val length = min(a.size - startA, b.size - startB)
        var sum = 0.0
        for (i in 0 until length) sum += a[startA + i] * b[startB + i]
        return (sum / length).toFloat()
    }

    private fun normalized(x: FloatArray): FloatArray {
        val mean = x.average().toFloat()
        val rms = sqrt(x.sumOf { ((it - mean) * (it - mean)).toDouble() } / x.size).toFloat()
        return FloatArray(x.size) { (x[it] - mean) / rms }
    }

    private fun noise(seconds: Double, seed: Int): FloatArray {
        val random = Random(seed)
        return FloatArray((seconds * rate).toInt()) { (random.nextFloat() * 2 - 1) }
    }
}
