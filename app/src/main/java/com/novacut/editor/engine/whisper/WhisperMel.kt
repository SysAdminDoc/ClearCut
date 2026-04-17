package com.novacut.editor.engine.whisper

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computes 80-channel log-mel spectrogram matching Whisper's preprocessing.
 * Audio must be 16kHz mono float32 PCM, padded/trimmed to 30 seconds (480000 samples).
 */
object WhisperMel {
    const val SAMPLE_RATE = 16000
    const val N_FFT = 400
    const val HOP_LENGTH = 160
    const val N_MELS = 80
    const val CHUNK_LENGTH_S = 30
    const val N_SAMPLES = SAMPLE_RATE * CHUNK_LENGTH_S // 480000
    const val N_FRAMES = N_SAMPLES / HOP_LENGTH // 3000

    private val FFT_SIZE = nextPow2(N_FFT) // 512
    private val hannWindow = FloatArray(N_FFT) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / N_FFT))).toFloat()
    }

    // Lazily computed mel filterbank
    private val melFilters: Array<FloatArray> by lazy { createMelFilterbank() }

    /**
     * Compute log-mel spectrogram from 16kHz audio.
     * Input: float32 PCM samples (any length, will be padded/trimmed to 30s)
     * Output: FloatArray of shape [80 * 3000] (row-major, 80 mel channels x 3000 time frames)
     */
    fun compute(audio: FloatArray): FloatArray {
        // Pad or trim to exactly 30 seconds
        val padded = FloatArray(N_SAMPLES)
        val copyLen = minOf(audio.size, N_SAMPLES)
        System.arraycopy(audio, 0, padded, 0, copyLen)

        // STFT: compute magnitude spectrogram
        val numFreqBins = N_FFT / 2 + 1 // 201
        val magnitudes = Array(N_FRAMES) { FloatArray(numFreqBins) }

        val fftReal = FloatArray(FFT_SIZE)
        val fftImag = FloatArray(FFT_SIZE)

        for (frame in 0 until N_FRAMES) {
            val start = frame * HOP_LENGTH
            // Apply Hann window and zero-pad to FFT size
            fftReal.fill(0f)
            fftImag.fill(0f)
            for (i in 0 until N_FFT) {
                val idx = start + i
                fftReal[i] = if (idx < N_SAMPLES) padded[idx] * hannWindow[i] else 0f
            }

            // In-place FFT
            fft(fftReal, fftImag, FFT_SIZE)

            // Magnitude squared
            for (k in 0 until numFreqBins) {
                magnitudes[frame][k] = fftReal[k] * fftReal[k] + fftImag[k] * fftImag[k]
            }
        }

        // Apply mel filterbank
        val melSpec = FloatArray(N_MELS * N_FRAMES)
        for (mel in 0 until N_MELS) {
            val filter = melFilters[mel]
            for (frame in 0 until N_FRAMES) {
                var sum = 0f
                for (k in filter.indices) {
                    sum += filter[k] * magnitudes[frame][k]
                }
                // Clamp and log scale (matching Whisper's np.log10(np.maximum(mel, 1e-10)))
                melSpec[mel * N_FRAMES + frame] = log10(max(sum, 1e-10f))
            }
        }

        // Normalize: max - 8.0 floor, then (x - max) / 4.0 + 1.0
        var maxVal = -Float.MAX_VALUE
        for (v in melSpec) maxVal = max(maxVal, v)
        val floor = maxVal - 8.0f
        for (i in melSpec.indices) {
            melSpec[i] = (max(melSpec[i], floor) - maxVal) / 4.0f + 1.0f
        }

        return melSpec
    }

    /**
     * Radix-2 Cooley-Tukey FFT (in-place).
     */
    private fun fft(real: FloatArray, imag: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp
            }
            var m = n / 2
            while (m >= 1 && j >= m) {
                j -= m
                m /= 2
            }
            j += m
        }

        // FFT butterfly
        var step = 1
        while (step < n) {
            val halfStep = step
            step *= 2
            val angle = -PI / halfStep
            val wR = cos(angle).toFloat()
            val wI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var curR = 1.0f
                var curI = 0.0f
                for (k in 0 until halfStep) {
                    val evenIdx = i + k
                    val oddIdx = i + k + halfStep
                    val tR = curR * real[oddIdx] - curI * imag[oddIdx]
                    val tI = curR * imag[oddIdx] + curI * real[oddIdx]
                    real[oddIdx] = real[evenIdx] - tR
                    imag[oddIdx] = imag[evenIdx] - tI
                    real[evenIdx] = real[evenIdx] + tR
                    imag[evenIdx] = imag[evenIdx] + tI
                    val newR = curR * wR - curI * wI
                    val newI = curR * wI + curI * wR
                    curR = newR
                    curI = newI
                }
                i += step
            }
        }
    }

    /**
     * Create 80-channel mel filterbank for Whisper (matching librosa default).
     * Returns Array[80] of FloatArray[201] (N_FFT/2 + 1 frequency bins).
     */
    private fun createMelFilterbank(): Array<FloatArray> {
        val numFreqBins = N_FFT / 2 + 1 // 201
        val fMin = 0.0
        val fMax = SAMPLE_RATE / 2.0 // 8000 Hz

        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)

        // N_MELS + 2 equally spaced points in mel scale
        val melPoints = DoubleArray(N_MELS + 2) { i ->
            melMin + i * (melMax - melMin) / (N_MELS + 1)
        }
        val hzPoints = DoubleArray(melPoints.size) { melToHz(melPoints[it]) }

        // Convert Hz to FFT bin indices
        val binPoints = DoubleArray(hzPoints.size) { i ->
            floor(hzPoints[i] * (N_FFT + 1).toDouble() / SAMPLE_RATE).coerceIn(0.0, (numFreqBins - 1).toDouble())
        }

        val filters = Array(N_MELS) { FloatArray(numFreqBins) }
        for (m in 0 until N_MELS) {
            val left = binPoints[m]
            val center = binPoints[m + 1]
            val right = binPoints[m + 2]

            for (k in 0 until numFreqBins) {
                val kd = k.toDouble()
                filters[m][k] = when {
                    kd < left -> 0f
                    kd <= center && center > left -> ((kd - left) / (center - left)).toFloat()
                    kd <= right && right > center -> ((right - kd) / (right - center)).toFloat()
                    else -> 0f
                }
            }

            // Slaney normalization (matching librosa norm='slaney').
            // Clamp denominator: at tiny sample rates / very-short-audio edge cases the mel
            // points can collapse so `hzPoints[m+2] == hzPoints[m]`, producing Infinity here
            // and poisoning the whole mel bank with NaN on the next multiply — Whisper then
            // produces zero-confidence garbage text with no visible error.
            val melSpan = (hzPoints[m + 2] - hzPoints[m]).coerceAtLeast(1e-8)
            val enorm = 2.0 / melSpan
            for (k in 0 until numFreqBins) {
                filters[m][k] = (filters[m][k] * enorm).toFloat()
            }
        }
        return filters
    }

    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

    private fun nextPow2(n: Int): Int {
        var v = n - 1
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        return v + 1
    }
}
