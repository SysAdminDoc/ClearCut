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
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Loudness measurement and normalization engine.
 * Implements simplified EBU R128 / ITU-R BS.1770 loudness measurement.
 *
 * Full implementation: integrate libebur128 via NDK
 *   URL: github.com/jiixyj/libebur128
 *   Pure ANSI C, zero dependencies, trivial NDK cross-compile
 *
 * Platform loudness targets:
 *   YouTube/Spotify: -14 LUFS integrated, -1 dBTP
 *   Apple Podcasts:  -16 LUFS integrated, -1 dBTP
 *   Broadcast (EBU): -23 LUFS integrated, -1 dBTP
 *   TikTok:          -14 LUFS (same as YouTube)
 */
@Singleton
class LoudnessEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioEngine: AudioEngine
) {
    enum class LoudnessPreset(val displayName: String, val targetLufs: Float, val truePeakDbfs: Float) {
        YOUTUBE("YouTube / Spotify (-14 LUFS)", -14f, -1f),
        PODCAST("Podcast / Apple (-16 LUFS)", -16f, -1f),
        BROADCAST("Broadcast EBU R128 (-23 LUFS)", -23f, -1f),
        TIKTOK("TikTok (-14 LUFS)", -14f, -1f),
        LOUD("Loud Master (-9 LUFS)", -9f, -1f),
        CINEMA("Cinema (-24 LUFS)", -24f, -1f)
    }

    data class LoudnessMeasurement(
        val integratedLufs: Float,    // Overall loudness (EBU R128 integrated)
        val momentaryMaxLufs: Float,  // Peak momentary loudness (400ms window)
        val shortTermMaxLufs: Float,  // Peak short-term loudness (3s window)
        val truePeakDbfs: Float,      // True peak in dBFS
        val loudnessRange: Float      // LRA in LU (dynamic range measure)
    )

    /**
     * Measure loudness of audio from a media URI.
     * Implements simplified ITU-R BS.1770-4 measurement:
     * 1. K-frequency weighting (pre-filter)
     * 2. Mean square per channel per block (400ms blocks, 75% overlap)
     * 3. Gating: absolute gate at -70 LUFS, then relative gate at -10 LU below ungated
     * 4. Integrated loudness = weighted sum of gated blocks
     */
    suspend fun measureLoudness(
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): LoudnessMeasurement = withContext(Dispatchers.Default) {
        onProgress(0.1f)

        val sampleRate = 48000
        val waveform = withContext(Dispatchers.IO) {
            audioEngine.extractWaveform(uri, sampleRate)
        }
        onProgress(0.3f)

        if (waveform.isEmpty()) return@withContext LoudnessMeasurement(-70f, -70f, -70f, -70f, 0f)

        // Apply K-weighting (simplified: high-shelf boost + high-pass)
        val kWeighted = applyKWeighting(waveform, sampleRate)
        onProgress(0.5f)

        // Compute block loudness (400ms blocks with 75% overlap)
        val blockSize = (sampleRate * 0.4f).toInt()  // 400ms
        val hopSize = blockSize / 4  // 75% overlap
        val blockLoudness = mutableListOf<Float>()

        var pos = 0
        while (pos + blockSize <= kWeighted.size) {
            ensureActive()
            var sumSq = 0.0
            for (i in 0 until blockSize) {
                val s = kWeighted[pos + i].toDouble()
                sumSq += s * s
            }
            val meanSq = sumSq / blockSize
            val loudness = -0.691f + 10f * log10(maxOf(meanSq, 1e-10).toFloat())
            blockLoudness.add(loudness)
            pos += hopSize
        }
        onProgress(0.7f)

        if (blockLoudness.isEmpty()) return@withContext LoudnessMeasurement(-70f, -70f, -70f, -70f, 0f)

        // Absolute gating at -70 LUFS
        val absoluteGated = blockLoudness.filter { it > -70f }

        // Ungated integrated loudness
        val ungatedLoudness = if (absoluteGated.isNotEmpty()) {
            val sumPower = absoluteGated.sumOf { 10.0.pow(it / 10.0) }
            10f * log10((sumPower / absoluteGated.size).toFloat())
        } else -70f

        // Relative gating at ungatedLoudness - 10 LU
        val relativeThreshold = ungatedLoudness - 10f
        val relativeGated = absoluteGated.filter { it > relativeThreshold }

        val integratedLufs = if (relativeGated.isNotEmpty()) {
            val sumPower = relativeGated.sumOf { 10.0.pow(it / 10.0) }
            10f * log10((sumPower / relativeGated.size).toFloat())
        } else -70f

        // True peak (simplified: just find max absolute sample)
        val truePeak = waveform.maxOfOrNull { abs(it) } ?: 0f
        val truePeakDb = 20f * log10(maxOf(truePeak, 1e-10f))

        // Momentary max (from block loudness with 400ms window)
        val momentaryMax = blockLoudness.maxOrNull() ?: -70f

        // Short-term max (3s window = average of ~8 blocks). For clips shorter than 3s we have
        // fewer than 8 blocks; fall back to the momentary max rather than reporting -70 LUFS,
        // which would otherwise make the "short-term max" meaningless for short voiceovers/SFX.
        val shortTermBlocks = 8
        var shortTermMax = -70f
        if (blockLoudness.size >= shortTermBlocks) {
            for (i in 0..blockLoudness.size - shortTermBlocks) {
                val stPower = blockLoudness.subList(i, i + shortTermBlocks)
                    .sumOf { 10.0.pow(it / 10.0) }
                val stLoudness = 10f * log10((stPower / shortTermBlocks).toFloat())
                if (stLoudness > shortTermMax) shortTermMax = stLoudness
            }
        } else {
            shortTermMax = momentaryMax
        }

        // Loudness Range (simplified: 95th - 10th percentile of short-term)
        val sorted = blockLoudness.filter { it > -70f }.sorted()
        val lra = if (sorted.size >= 10) {
            sorted[(sorted.size * 0.95).toInt()] - sorted[(sorted.size * 0.1).toInt()]
        } else 0f

        onProgress(1f)
        LoudnessMeasurement(integratedLufs, momentaryMax, shortTermMax, truePeakDb, lra)
    }

    /**
     * Calculate gain adjustment to normalize audio to a target loudness.
     * @return gain in linear scale (multiply all samples by this value)
     */
    fun calculateNormalizationGain(
        measurement: LoudnessMeasurement,
        preset: LoudnessPreset
    ): Float {
        val gainDb = preset.targetLufs - measurement.integratedLufs

        // Check if applying gain would exceed true peak limit
        val adjustedPeak = measurement.truePeakDbfs + gainDb
        val finalGainDb = if (adjustedPeak > preset.truePeakDbfs) {
            // Reduce gain to stay within peak limit
            gainDb - (adjustedPeak - preset.truePeakDbfs)
        } else gainDb

        return 10f.pow(finalGainDb / 20f)
    }

    /**
     * Simplified K-frequency weighting filter.
     * ITU-R BS.1770 specifies a two-stage filter:
     * Stage 1: High-shelf boost (+4dB above 1.5kHz) — accounts for head diffraction
     * Stage 2: High-pass at 60Hz — removes DC/subsonic content
     *
     * This is a simplified single-stage approximation.
     */
    private fun applyKWeighting(samples: FloatArray, sampleRate: Int): FloatArray {
        val output = FloatArray(samples.size)
        // Clamp to the practical range for audio export — 8 kHz (telephony low
        // bound) to 192 kHz (studio high bound). `coerceAtLeast(1)` kept
        // `fc = 1500 / safeSampleRate` from dividing by zero but let extreme
        // values (e.g. a bogus 2 Hz from a malformed MediaFormat) produce a
        // near-1.0 `alpha` that effectively disables the K-weighting filter.
        // Clamping to a reasonable band keeps the coefficients sensible under
        // all realistic inputs.
        val safeSampleRate = sampleRate.coerceIn(8_000, 192_000)

        // Simple high-shelf approximation using first-order IIR
        // Boosts high frequencies by ~4dB
        val fc = 1500f / safeSampleRate  // Normalized cutoff
        val alpha = fc / (fc + 1f)
        var prev = 0f

        for (i in samples.indices) {
            val highPassed = samples[i] - prev
            prev = samples[i] * alpha + prev * (1f - alpha)
            // Boost highs by mixing original + high-frequency content
            output[i] = samples[i] + highPassed * 0.58f  // ~+4dB shelf
        }

        // Second pass: 60Hz high-pass to remove DC
        val hpAlpha = 1f - (60f / safeSampleRate * 2f * Math.PI.toFloat()).let { it / (it + 1f) }
        prev = 0f
        var prevOut = 0f
        for (i in output.indices) {
            val filtered = hpAlpha * (prevOut + output[i] - prev)
            prev = output[i]
            prevOut = filtered
            output[i] = filtered
        }

        return output
    }
}
