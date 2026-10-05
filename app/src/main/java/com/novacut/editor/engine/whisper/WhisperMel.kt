package com.novacut.editor.engine.whisper

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/**
 * Computes the 80-channel log-mel spectrogram Whisper was trained on, step for
 * step with the reference preprocessing (whisper.audio.log_mel_spectrogram and
 * Hugging Face's WhisperFeatureExtractor): a centered, reflect-padded STFT with a
 * 400-point periodic Hann window, librosa's Slaney mel filters, log10, an 8-unit
 * dynamic range floor, then (x + 4) / 4.
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

    private const val N_BINS = N_FFT / 2 + 1 // 201
    private const val PAD = N_FFT / 2

    private val hannWindow = FloatArray(N_FFT) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / N_FFT))).toFloat()
    }

    // A true 400-point transform, so bin k sits at k * 40 Hz as the mel filters
    // expect. Zero-padding to a 512-point FFT would put it at k * 31.25 Hz instead.
    // 400 = 16 * 25: sixteen direct 25-point DFTs, then four radix-2 stages.
    private const val LEAVES = 16
    private const val LEAF = N_FFT / LEAVES // 25
    private val twiddleCos = FloatArray(N_FFT) { cos(2.0 * PI * it / N_FFT).toFloat() }
    private val twiddleSin = FloatArray(N_FFT) { sin(2.0 * PI * it / N_FFT).toFloat() }
    private val leafCos = leafTable(::cos)
    private val leafSin = leafTable(::sin)

    private val melFilters: Array<FloatArray> by lazy { createMelFilterbank() }

    /**
     * Compute log-mel spectrogram from 16kHz audio.
     * Input: float32 PCM samples (any length, will be padded/trimmed to 30s)
     * Output: FloatArray of shape [80 * 3000] (row-major, 80 mel channels x 3000 time frames)
     */
    fun compute(audio: FloatArray): FloatArray {
        // Pad or trim to exactly 30 seconds, then reflect half a window onto each
        // end so frame f is centered on sample f * HOP_LENGTH.
        val padded = FloatArray(N_SAMPLES + 2 * PAD)
        System.arraycopy(audio, 0, padded, PAD, minOf(audio.size, N_SAMPLES))
        for (i in 1..PAD) {
            padded[PAD - i] = padded[PAD + i]
            padded[PAD + N_SAMPLES - 1 + i] = padded[PAD + N_SAMPLES - 1 - i]
        }

        val filters = melFilters
        val windowed = FloatArray(N_FFT)
        val power = FloatArray(N_BINS)
        val scratch = Array(4) { FloatArray(N_FFT) }
        val melSpec = FloatArray(N_MELS * N_FRAMES)

        for (frame in 0 until N_FRAMES) {
            val start = frame * HOP_LENGTH
            var silent = true
            for (n in 0 until N_FFT) {
                val v = padded[start + n] * hannWindow[n]
                windowed[n] = v
                if (v != 0f) silent = false
            }
            if (silent) power.fill(0f) else powerSpectrum(windowed, power, scratch)
            for (mel in 0 until N_MELS) {
                val filter = filters[mel]
                var sum = 0f
                for (k in 0 until N_BINS) sum += filter[k] * power[k]
                melSpec[mel * N_FRAMES + frame] = log10(max(sum, 1e-10f))
            }
        }

        var maxVal = -Float.MAX_VALUE
        for (v in melSpec) maxVal = max(maxVal, v)
        // Non-finite input (NaN or infinite samples) would otherwise poison every
        // value; return a neutral spectrogram rather than garbage features.
        if (!maxVal.isFinite()) {
            java.util.Arrays.fill(melSpec, 0f)
            return melSpec
        }
        val floor = maxVal - 8.0f
        for (i in melSpec.indices) {
            val v = (max(melSpec[i], floor) + 4.0f) / 4.0f
            melSpec[i] = if (v.isFinite()) v else 0f
        }

        return melSpec
    }

    /** |X[k]|^2 for k in 0..200 of the 400-point DFT of [x]. */
    private fun powerSpectrum(x: FloatArray, power: FloatArray, scratch: Array<FloatArray>) {
        var srcRe = scratch[0]
        var srcIm = scratch[1]
        var dstRe = scratch[2]
        var dstIm = scratch[3]
        // Block r holds the DFT of x[r], x[r + 16], x[r + 32], ...
        for (r in 0 until LEAVES) {
            for (k in 0 until LEAF) {
                var re = 0f
                var im = 0f
                for (j in 0 until LEAF) {
                    val v = x[r + LEAVES * j]
                    re += v * leafCos[k * LEAF + j]
                    im -= v * leafSin[k * LEAF + j]
                }
                srcRe[r * LEAF + k] = re
                srcIm[r * LEAF + k] = im
            }
        }
        // Each stage merges block r (the even samples) with block r + half (the odd
        // ones) into a block twice as long, until one block covers all 400.
        var len = LEAF
        var blocks = LEAVES
        while (blocks > 1) {
            val half = blocks / 2
            val step = N_FFT / (2 * len)
            for (r in 0 until half) {
                val even = r * len
                val odd = (r + half) * len
                val out = r * 2 * len
                for (k in 0 until len) {
                    val wRe = twiddleCos[k * step]
                    val wIm = -twiddleSin[k * step]
                    val oRe = srcRe[odd + k]
                    val oIm = srcIm[odd + k]
                    val tRe = wRe * oRe - wIm * oIm
                    val tIm = wRe * oIm + wIm * oRe
                    dstRe[out + k] = srcRe[even + k] + tRe
                    dstIm[out + k] = srcIm[even + k] + tIm
                    dstRe[out + k + len] = srcRe[even + k] - tRe
                    dstIm[out + k + len] = srcIm[even + k] - tIm
                }
            }
            srcRe = dstRe.also { dstRe = srcRe }
            srcIm = dstIm.also { dstIm = srcIm }
            len *= 2
            blocks = half
        }
        for (k in 0 until N_BINS) power[k] = srcRe[k] * srcRe[k] + srcIm[k] * srcIm[k]
    }

    private fun leafTable(trig: (Double) -> Double): FloatArray =
        FloatArray(LEAF * LEAF) { i ->
            // k * j mod 25 keeps the angle small, so the float table stays exact.
            val phase = ((i / LEAF) * (i % LEAF)) % LEAF
            trig(2.0 * PI * phase / LEAF).toFloat()
        }

    /**
     * librosa.filters.mel(sr = 16000, n_fft = 400, n_mels = 80), the defaults
     * Whisper's mel_filters.npz was made with: Slaney mel scale and Slaney area
     * normalization, triangles drawn on the exact bin frequencies.
     */
    private fun createMelFilterbank(): Array<FloatArray> {
        val melMin = hzToMel(0.0)
        val melMax = hzToMel(SAMPLE_RATE / 2.0)
        val melHz = DoubleArray(N_MELS + 2) { melToHz(melMin + it * (melMax - melMin) / (N_MELS + 1)) }
        return Array(N_MELS) { m ->
            val lowerWidth = melHz[m + 1] - melHz[m]
            val upperWidth = melHz[m + 2] - melHz[m + 1]
            val enorm = 2.0 / (melHz[m + 2] - melHz[m])
            FloatArray(N_BINS) { k ->
                val hz = k * SAMPLE_RATE.toDouble() / N_FFT
                val rising = (hz - melHz[m]) / lowerWidth
                val falling = (melHz[m + 2] - hz) / upperWidth
                (maxOf(0.0, minOf(rising, falling)) * enorm).toFloat()
            }
        }
    }

    // Slaney's mel scale: linear below 1 kHz, logarithmic above.
    private const val MEL_HZ_STEP = 200.0 / 3.0
    private const val LOG_REGION_HZ = 1000.0
    private const val LOG_REGION_MEL = LOG_REGION_HZ / MEL_HZ_STEP
    private val LOG_STEP = ln(6.4) / 27.0

    private fun hzToMel(hz: Double): Double =
        if (hz >= LOG_REGION_HZ) LOG_REGION_MEL + ln(hz / LOG_REGION_HZ) / LOG_STEP else hz / MEL_HZ_STEP

    private fun melToHz(mel: Double): Double =
        if (mel >= LOG_REGION_MEL) LOG_REGION_HZ * exp(LOG_STEP * (mel - LOG_REGION_MEL)) else MEL_HZ_STEP * mel
}
