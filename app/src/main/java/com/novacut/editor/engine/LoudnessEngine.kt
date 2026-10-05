package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * Loudness measurement and normalization engine: ITU-R BS.1770-4 loudness with
 * EBU R128 gating, EBU Tech 3342 loudness range and 4x oversampled true peak,
 * measured at the rate and channel layout the audio decodes to. [LoudnessMeter]
 * does the arithmetic.
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

    /** Measures the loudness of the first audio track in [uri]. */
    suspend fun measureLoudness(
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): LoudnessMeasurement = withContext(Dispatchers.Default) {
        onProgress(0.1f)
        val decoded = withContext(Dispatchers.IO) { audioEngine.decodeToPcmWithFormat(uri) }
        onProgress(0.3f)
        val measurement = LoudnessMeter.measure(decoded.samples, decoded.channelCount, decoded.sampleRate) {
            ensureActive()
        }
        onProgress(1f)
        measurement
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
}

/**
 * ITU-R BS.1770-4 loudness of interleaved 16-bit PCM: K-weighting designed for the
 * actual sample rate, per-channel weights (LFE left out, surrounds +1.5 dB), 400 ms
 * blocks on a 100 ms hop gated at -70 LUFS and then 10 LU below, EBU Tech 3342
 * loudness range over 3 s windows, and true peak from the Annex 2 4x interpolator.
 */
internal object LoudnessMeter {
    private const val SILENCE_LUFS = -70.0
    private const val RELATIVE_GATE_LU = -10.0
    private const val RANGE_RELATIVE_GATE_LU = -20.0
    private const val MOMENTARY_SEGMENTS = 4
    private const val SHORT_TERM_SEGMENTS = 30
    private const val SURROUND_WEIGHT = 1.41
    private const val MIN_SAMPLE_RATE = 8_000
    private const val MAX_SAMPLE_RATE = 192_000

    /** One second-order section with a0 normalized to 1. */
    class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    /**
     * The pre-filter (high shelf) and RLB high-pass at [sampleRate], derived from
     * their analog prototypes the way libebur128 does, so 48 kHz reproduces the
     * coefficient tables in BS.1770-4 and other rates get their own filters.
     */
    fun kWeighting(sampleRate: Int): Pair<Biquad, Biquad> {
        val shelfK = tan(PI * 1681.974450955533 / sampleRate)
        val shelfQ = 0.7071752369554196
        val vh = 10.0.pow(3.999843853973347 / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val shelfA0 = 1.0 + shelfK / shelfQ + shelfK * shelfK
        val shelf = Biquad(
            b0 = (vh + vb * shelfK / shelfQ + shelfK * shelfK) / shelfA0,
            b1 = 2.0 * (shelfK * shelfK - vh) / shelfA0,
            b2 = (vh - vb * shelfK / shelfQ + shelfK * shelfK) / shelfA0,
            a1 = 2.0 * (shelfK * shelfK - 1.0) / shelfA0,
            a2 = (1.0 - shelfK / shelfQ + shelfK * shelfK) / shelfA0,
        )
        val passK = tan(PI * 38.13547087602444 / sampleRate)
        val passQ = 0.5003270373238773
        val passA0 = 1.0 + passK / passQ + passK * passK
        val highPass = Biquad(
            b0 = 1.0,
            b1 = -2.0,
            b2 = 1.0,
            a1 = 2.0 * (passK * passK - 1.0) / passA0,
            a2 = (1.0 - passK / passQ + passK * passK) / passA0,
        )
        return shelf to highPass
    }

    /** BS.1770 channel weights in Android's channel order (FL, FR, FC, LFE, BL, BR, SL, SR). */
    fun channelWeights(channelCount: Int): DoubleArray = when (channelCount) {
        5 -> doubleArrayOf(1.0, 1.0, 1.0, SURROUND_WEIGHT, SURROUND_WEIGHT)
        6 -> doubleArrayOf(1.0, 1.0, 1.0, 0.0, SURROUND_WEIGHT, SURROUND_WEIGHT)
        8 -> doubleArrayOf(1.0, 1.0, 1.0, 0.0, SURROUND_WEIGHT, SURROUND_WEIGHT, SURROUND_WEIGHT, SURROUND_WEIGHT)
        else -> DoubleArray(channelCount) { 1.0 }
    }

    fun measure(
        pcm: ShortArray,
        channelCount: Int,
        sampleRate: Int,
        checkpoint: () -> Unit = {},
    ): LoudnessEngine.LoudnessMeasurement {
        val channels = channelCount.coerceAtLeast(1)
        val rate = sampleRate.coerceIn(MIN_SAMPLE_RATE, MAX_SAMPLE_RATE)
        val frames = pcm.size / channels
        val (shelf, highPass) = kWeighting(rate)
        val weights = channelWeights(channels)
        // Per channel: shelf z1, z2, then high-pass z1, z2 (transposed direct form II).
        val state = DoubleArray(channels * 4)
        val truePeak = TruePeakMeter(channels)

        val segmentFrames = rate / 10
        val segments = DoubleArray(frames / segmentFrames)
        var frame = 0
        for (segment in segments.indices) {
            checkpoint()
            var sum = 0.0
            repeat(segmentFrames) {
                val base = frame * channels
                for (channel in 0 until channels) {
                    val x = pcm[base + channel] / 32768.0
                    truePeak.add(channel, x)
                    val weight = weights[channel]
                    if (weight == 0.0) continue
                    val z = channel * 4
                    val shelved = shelf.b0 * x + state[z]
                    state[z] = shelf.b1 * x - shelf.a1 * shelved + state[z + 1]
                    state[z + 1] = shelf.b2 * x - shelf.a2 * shelved
                    val y = highPass.b0 * shelved + state[z + 2]
                    state[z + 2] = highPass.b1 * shelved - highPass.a1 * y + state[z + 3]
                    state[z + 3] = highPass.b2 * shelved - highPass.a2 * y
                    sum += weight * y * y
                }
                frame++
            }
            segments[segment] = sum / segmentFrames
        }
        // The tail shorter than one segment can't fill a block, but its peaks count.
        while (frame < frames) {
            val base = frame * channels
            for (channel in 0 until channels) truePeak.add(channel, pcm[base + channel] / 32768.0)
            frame++
        }

        val momentary = windowPowers(segments, MOMENTARY_SEGMENTS)
        val shortTerm = windowPowers(segments, SHORT_TERM_SEGMENTS)
        val momentaryMax = momentary.maxOrNull()?.let(::lufs) ?: SILENCE_LUFS
        return LoudnessEngine.LoudnessMeasurement(
            integratedLufs = gatedLoudness(momentary).toFloat(),
            momentaryMaxLufs = momentaryMax.toFloat(),
            // A clip shorter than 3 s has no short-term window; its loudest block stands in.
            shortTermMaxLufs = (shortTerm.maxOrNull()?.let(::lufs) ?: momentaryMax).toFloat(),
            truePeakDbfs = truePeak.dbtp().toFloat(),
            loudnessRange = loudnessRange(shortTerm).toFloat(),
        )
    }

    private fun lufs(power: Double): Double =
        if (power > 0.0) maxOf(SILENCE_LUFS, -0.691 + 10.0 * log10(power)) else SILENCE_LUFS

    /** Mean-square power of every [length]-segment window, one per 100 ms hop. */
    private fun windowPowers(segments: DoubleArray, length: Int): DoubleArray {
        if (segments.size < length) return DoubleArray(0)
        val powers = DoubleArray(segments.size - length + 1)
        var sum = 0.0
        for (i in segments.indices) {
            sum += segments[i]
            if (i >= length) sum -= segments[i - length]
            if (i >= length - 1) powers[i - length + 1] = sum / length
        }
        return powers
    }

    private fun gatedLoudness(blocks: DoubleArray): Double {
        val audible = blocks.filter { lufs(it) > SILENCE_LUFS }
        if (audible.isEmpty()) return SILENCE_LUFS
        val relativeGate = lufs(audible.average()) + RELATIVE_GATE_LU
        val gated = audible.filter { lufs(it) > relativeGate }
        return if (gated.isEmpty()) SILENCE_LUFS else lufs(gated.average())
    }

    /** EBU Tech 3342: the 10th to 95th percentile spread of gated short-term loudness. */
    private fun loudnessRange(shortTerm: DoubleArray): Double {
        val audible = shortTerm.filter { lufs(it) > SILENCE_LUFS }
        if (audible.isEmpty()) return 0.0
        val gate = lufs(audible.average()) + RANGE_RELATIVE_GATE_LU
        val levels = audible.map(::lufs).filter { it > gate }.sorted()
        if (levels.isEmpty()) return 0.0
        val low = levels[((levels.size - 1) * 0.10 + 0.5).toInt()]
        val high = levels[((levels.size - 1) * 0.95 + 0.5).toInt()]
        return high - low
    }

    /** BS.1770-4 Annex 2: 4x oversampling with a 48-tap interpolator, as four 12-tap phases. */
    private class TruePeakMeter(channels: Int) {
        private val history = Array(channels) { DoubleArray(TAPS * 2) }
        private val position = IntArray(channels)
        private var peak = 0.0

        fun add(channel: Int, x: Double) {
            val window = history[channel]
            val p = position[channel]
            // Each sample is written twice so the newest TAPS samples sit contiguous
            // at p + 1 .. p + TAPS, newest last.
            window[p] = x
            window[p + TAPS] = x
            for (phase in PHASES) {
                var y = 0.0
                for (k in 0 until TAPS) y += phase[k] * window[p + TAPS - k]
                if (abs(y) > peak) peak = abs(y)
            }
            if (abs(x) > peak) peak = abs(x)
            position[channel] = if (p + 1 == TAPS) 0 else p + 1
        }

        fun dbtp(): Double = if (peak > 0.0) maxOf(SILENCE_LUFS, 20.0 * log10(peak)) else SILENCE_LUFS

        private companion object {
            const val TAPS = 12
            val PHASE_0 = doubleArrayOf(
                0.0017089843750, 0.0109863281250, -0.0196533203125, 0.0332031250000,
                -0.0594482421875, 0.1373291015625, 0.9721679687500, -0.1022949218750,
                0.0476074218750, -0.0266113281250, 0.0148925781250, -0.0083007812500,
            )
            val PHASE_1 = doubleArrayOf(
                -0.0291748046875, 0.0292968750000, -0.0517578125000, 0.0891113281250,
                -0.1665039062500, 0.4650878906250, 0.7797851562500, -0.2003173828125,
                0.1015625000000, -0.0582275390625, 0.0330810546875, -0.0189208984375,
            )
            val PHASES = arrayOf(PHASE_0, PHASE_1, PHASE_1.reversedArray(), PHASE_0.reversedArray())
        }
    }
}
