package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Beat and onset detection engine. [BeatAnalyzer] does the analysis in pure Kotlin.
 *
 * Usage:
 *   val beats = beatDetectionEngine.detectBeats(uri)
 *   // beats = list of timestamps in ms where beats occur
 */
@Singleton
class BeatDetectionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioEngine: AudioEngine
) {
    data class BeatInfo(
        val timestampMs: Long,
        val strength: Float,      // 0-1 beat strength/confidence
        val isDownbeat: Boolean = false  // true for bar-start beats
    )

    data class BeatAnalysis(
        val beats: List<BeatInfo>,
        val bpm: Float,
        val timeSignature: Int = 4  // beats per bar (usually 4)
    )

    /** Detects beats and tempo in the first audio track of [uri]. */
    suspend fun detectBeats(
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): BeatAnalysis = withContext(Dispatchers.Default) {
        onProgress(0.1f)
        // The decoder's own rate and layout: the container's can differ (HE-AAC
        // decodes at twice its stored rate), which would stretch every timestamp.
        val decoded = withContext(Dispatchers.IO) { audioEngine.decodeToPcmWithFormat(uri) }
        onProgress(0.3f)
        val analysis = BeatAnalyzer.analyze(decoded.samples, decoded.channelCount, decoded.sampleRate) {
            ensureActive()
        }
        onProgress(1f)
        analysis
    }
}

/**
 * Spectral-flux onset detection and tempo estimation on interleaved 16-bit PCM:
 * 1. Downmix to mono and take a Hann-windowed 1024-point FFT every 512 samples
 * 2. Spectral flux: the sum of positive magnitude differences between frames
 * 3. Adaptive threshold: a moving median plus an offset
 * 4. Local flux peaks above the threshold are onsets
 * 5. Tempo from the most common inter-onset interval
 */
internal object BeatAnalyzer {
    private const val HOP_SIZE = 512
    private const val WINDOW_SIZE = HOP_SIZE * 2
    private const val MEDIAN_WINDOW = 15
    private const val THRESHOLD_OFFSET = 0.1f

    fun analyze(
        pcm: ShortArray,
        channelCount: Int,
        sampleRate: Int,
        checkpoint: () -> Unit = {},
    ): BeatDetectionEngine.BeatAnalysis {
        if (pcm.isEmpty() || sampleRate <= 0) return BeatDetectionEngine.BeatAnalysis(emptyList(), 0f)
        val channels = channelCount.coerceAtLeast(1)
        val frameCount = pcm.size / channels
        val waveform = FloatArray(frameCount) { i ->
            var sum = 0f
            for (ch in 0 until channels) sum += pcm[i * channels + ch] / 32768f
            sum / channels
        }

        val flux = computeSpectralFlux(waveform, checkpoint)
        val onsets = mutableListOf<BeatDetectionEngine.BeatInfo>()
        for (i in flux.indices) {
            if (i % 256 == 0) checkpoint()
            val start = maxOf(0, i - MEDIAN_WINDOW / 2)
            val end = minOf(flux.size, i + MEDIAN_WINDOW / 2 + 1)
            val window = flux.slice(start until end).sorted()
            val threshold = window[window.size / 2] + THRESHOLD_OFFSET

            if (flux[i] > threshold && flux[i] > 0.01f) {
                // A local peak, not just a frame above the threshold.
                val isPeak = (i == 0 || flux[i] >= flux[i - 1]) &&
                    (i == flux.size - 1 || flux[i] >= flux[i + 1])
                if (isPeak) {
                    // An onset peaks when it reaches the middle of the window, one hop
                    // after the frame starts (the window is two hops), so the frame's
                    // start time put every beat about 12 ms early.
                    val timestampMs = beatFrameTimestampMs(i + 1, HOP_SIZE, sampleRate)
                    onsets.add(BeatDetectionEngine.BeatInfo(timestampMs, flux[i].coerceIn(0f, 1f)))
                }
            }
        }

        val bpm = estimateBpm(onsets, sampleRate)
        // Mark downbeats (every fourth beat)
        val beats = if (onsets.size >= 4) {
            onsets.mapIndexed { idx, beat -> beat.copy(isDownbeat = idx % 4 == 0) }
        } else {
            onsets
        }
        return BeatDetectionEngine.BeatAnalysis(beats, bpm)
    }

    /** Spectral flux per hop, normalized to the loudest frame, from a radix-2 FFT. */
    private fun computeSpectralFlux(samples: FloatArray, checkpoint: () -> Unit): FloatArray {
        val numFrames = (samples.size - WINDOW_SIZE) / HOP_SIZE
        if (numFrames <= 1) return floatArrayOf()

        val numBins = WINDOW_SIZE / 2 + 1
        val flux = FloatArray(numFrames)
        var prevMagnitudes = FloatArray(numBins)
        val hann = FloatArray(WINDOW_SIZE) { n -> 0.5f * (1f - cos(2f * Math.PI.toFloat() * n / WINDOW_SIZE)) }

        for (frame in 0 until numFrames) {
            if (frame % 256 == 0) checkpoint()
            val offset = frame * HOP_SIZE
            val real = FloatArray(WINDOW_SIZE) { n -> samples[offset + n] * hann[n] }
            val imag = FloatArray(WINDOW_SIZE)
            fft(real, imag)

            val magnitudes = FloatArray(numBins) { k -> sqrt(real[k] * real[k] + imag[k] * imag[k]) }
            var sf = 0f
            for (k in 0 until numBins) {
                val diff = magnitudes[k] - prevMagnitudes[k]
                if (diff > 0) sf += diff
            }
            flux[frame] = sf
            prevMagnitudes = magnitudes
        }

        val maxFlux = flux.maxOrNull() ?: 1f
        if (maxFlux > 0) {
            for (i in flux.indices) flux[i] /= maxFlux
        }
        return flux
    }

    /** Radix-2 Cooley-Tukey in-place FFT. Input arrays must have power-of-2 length. */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT input must have power-of-2 length, got $n" }
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }
        // Cooley-Tukey butterfly
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * Math.PI / len
            for (i in 0 until n step len) {
                for (k in 0 until halfLen) {
                    val theta = angle * k
                    val cos = Math.cos(theta).toFloat()
                    val sin = Math.sin(theta).toFloat()
                    val tReal = real[i + k + halfLen] * cos - imag[i + k + halfLen] * sin
                    val tImag = real[i + k + halfLen] * sin + imag[i + k + halfLen] * cos
                    real[i + k + halfLen] = real[i + k] - tReal
                    imag[i + k + halfLen] = imag[i + k] - tImag
                    real[i + k] += tReal
                    imag[i + k] += tImag
                }
            }
            len = len shl 1
        }
    }

    /**
     * Tempo from the most common inter-onset interval. Onset times are quantized to
     * the hop, so one tempo spreads over neighboring 10 ms bins: the tempo comes from
     * the mean of the intervals in and beside the winning bin, not the bin's floor,
     * which read 120 BPM as 122.4.
     */
    private fun estimateBpm(beats: List<BeatDetectionEngine.BeatInfo>, sampleRate: Int): Float {
        if (beats.size < 3) return 0f
        val intervals = beats.zipWithNext { a, b -> b.timestampMs - a.timestampMs }
            .filter { it in 200..2000 } // 30-300 BPM
        if (intervals.isEmpty()) return 0f
        val votes = intervals.groupingBy { it / 10 }.eachCount()
        val best = votes.maxByOrNull { it.value }?.key ?: return 0f
        // Onsets land on analysis hops, so one beat period shows up as two interval
        // lengths a hop apart, and those can round into 10 ms bins two apart. Every
        // interval within a hop of the winning bin counts, so neither length drops out.
        val center = intervals.filter { it / 10 == best }.average()
        val reachMs = HOP_SIZE * 1_000.0 / sampleRate + 2.0
        val period = intervals.filter { abs(it - center) <= reachMs }.average()
        if (period <= 0.0) return 0f
        return (60_000.0 / period).toFloat().coerceIn(30f, 300f)
    }
}

/**
 * Timestamp of spectral-flux frame [frameIndex] in milliseconds at the source
 * [sampleRate]. Pure so the hop→ms conversion is contract-testable — beat
 * timestamps (and the BPM derived from them) must track the real decode rate,
 * not an assumed 44100 Hz.
 */
internal fun beatFrameTimestampMs(frameIndex: Int, hopSize: Int, sampleRate: Int): Long {
    if (sampleRate <= 0) return 0L
    return (frameIndex.toLong() * hopSize * 1000L) / sampleRate
}
