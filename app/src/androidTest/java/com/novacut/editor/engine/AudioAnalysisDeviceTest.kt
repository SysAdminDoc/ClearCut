package com.novacut.editor.engine

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Loudness, beat and multicam analysis through the device's real decoders, on WAV
 * files written with known content, so the rate and channel count each engine works
 * at come from MediaCodec rather than an assumption.
 */
@RunWith(AndroidJUnit4::class)
class AudioAnalysisDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workDir = File(context.cacheDir, "audio-analysis-${System.nanoTime()}").apply { mkdirs() }
    private val audioEngine = AudioEngine(context, MemoryTrimRegistry())

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun loudnessReadsTheEbuReferenceLevelAtTheDecodedLayout() = runBlocking {
        val loudness = LoudnessEngine(context, audioEngine)
        val stereo48k = wav("stereo48k", sampleRate = 48_000, channels = 2, seconds = 10.0) { t -> tone(t, -23.0) }
        val mono44k = wav("mono44k", sampleRate = 44_100, channels = 1, seconds = 10.0) { t -> tone(t, -23.0) }

        assertEquals(-23.0, loudness.measureLoudness(stereo48k).integratedLufs.toDouble(), 0.1)
        assertEquals(-26.0, loudness.measureLoudness(mono44k).integratedLufs.toDouble(), 0.1)
    }

    @Test
    fun beatsFollowTheDecodedRate() = runBlocking {
        val beats = BeatDetectionEngine(context, audioEngine)
        for (rate in listOf(44_100, 48_000)) {
            val track = wav("clicks$rate", sampleRate = rate, channels = 2, seconds = 10.0) { t ->
                val sinceBeat = (t - 0.25).mod(0.5)
                if (t >= 0.25 && sinceBeat < 0.03) 0.5 * exp(-sinceBeat / 0.008) * sin(2 * PI * 2_000 * sinceBeat) else 0.0
            }

            val analysis = beats.detectBeats(track)

            assertEquals("at $rate Hz", 120f, analysis.bpm, 0.25f)
            assertTrue("at $rate Hz first beat ${analysis.beats.firstOrNull()}", abs(analysis.beats.first().timestampMs - 250) <= 12)
        }
    }

    @Test
    fun multicamFindsTheOffsetBetweenRecordingsAtDifferentRates() = runBlocking {
        // The same scene, recorded by a 44.1 kHz stereo camera and by a 48 kHz mono
        // one that started 1.5 s later.
        val pings = Random(42).let { random -> List(40) { random.nextDouble(0.0, 22.0) to random.nextDouble(300.0, 3_000.0) } }
        fun scene(t: Double): Double = pings.sumOf { (at, frequency) ->
            val since = t - at
            if (since < 0 || since > 0.2) 0.0 else 0.3 * exp(-since / 0.05) * sin(2 * PI * frequency * since)
        }
        val a = wav("cameraA", sampleRate = 44_100, channels = 2, seconds = 20.0) { t -> scene(t) }
        val b = wav("cameraB", sampleRate = 48_000, channels = 1, seconds = 20.0) { t -> scene(t + 1.5) }

        val result = MultiCamEngine(context).findSyncOffset(a, b, maxOffsetMs = 5_000)

        assertEquals(1_500.0, result.offsetMs.toDouble(), 2.0)
        assertTrue("confidence ${result.confidence}", result.confidence > 0.8f)
    }

    private fun tone(t: Double, peakDbfs: Double): Double = 10.0.pow(peakDbfs / 20) * sin(2 * PI * 1_000 * t)

    /** A 16-bit PCM WAV with [signal] (time in seconds to -1..1) on every channel. */
    private fun wav(name: String, sampleRate: Int, channels: Int, seconds: Double, signal: (Double) -> Double): Uri {
        val frames = (seconds * sampleRate).roundToInt()
        val data = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (n in 0 until frames) {
            val sample = (signal(n.toDouble() / sampleRate) * 32767).roundToInt().coerceIn(-32768, 32767).toShort()
            repeat(channels) { data.putShort(sample) }
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels.toShort())
            putInt(sampleRate); putInt(sampleRate * channels * 2); putShort((channels * 2).toShort()); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        val file = File(workDir, "$name.wav")
        RandomAccessFile(file, "rw").use { out ->
            out.write(header.array())
            out.write(data.array())
        }
        return Uri.fromFile(file)
    }
}
