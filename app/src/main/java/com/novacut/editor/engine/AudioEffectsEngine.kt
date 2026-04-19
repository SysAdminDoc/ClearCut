package com.novacut.editor.engine

import com.novacut.editor.model.*
import kotlin.math.*

/**
 * DSP engine for real-time audio effects processing.
 * Processes PCM audio buffers through effect chains.
 */
object AudioEffectsEngine {
    private const val DEFAULT_SAMPLE_RATE = 44_100
    private const val MIN_SAMPLE_RATE = 8_000
    private const val MAX_SAMPLE_RATE = 384_000
    private const val MAX_CHANNELS = 8


    /**
     * Process a PCM buffer through an audio effect chain.
     * @param pcm 16-bit PCM samples (interleaved stereo)
     * @param sampleRate sample rate in Hz
     * @param channels number of audio channels
     * @param effects list of audio effects to apply in order
     * @return processed PCM buffer
     */
    fun processChain(
        pcm: ShortArray,
        sampleRate: Int,
        channels: Int,
        effects: List<AudioEffect>
    ): ShortArray {
        if (pcm.isEmpty()) return ShortArray(0)

        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val safeChannels = sanitizedChannels(channels)
        var buffer = FloatArray(pcm.size) { pcm[it].toFloat() / 32768f }

        for (effect in effects) {
            if (!effect.enabled) continue
            buffer = when (effect.type) {
                AudioEffectType.PARAMETRIC_EQ -> applyParametricEQ(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.COMPRESSOR -> applyCompressor(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.LIMITER -> applyLimiter(buffer, effect.params)
                AudioEffectType.NOISE_GATE -> applyNoiseGate(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.REVERB -> applyReverb(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.DELAY -> applyDelay(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.DE_ESSER -> applyDeEsser(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.CHORUS -> applyChorus(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.FLANGER -> applyFlanger(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.PITCH_SHIFT -> applyPitchShift(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.NORMALIZER -> applyNormalizer(buffer, effect.params)
                AudioEffectType.HIGH_PASS -> applyHighPass(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.LOW_PASS -> applyLowPass(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.BAND_PASS -> applyBandPass(buffer, safeSampleRate, safeChannels, effect.params)
                AudioEffectType.NOTCH -> applyNotch(buffer, safeSampleRate, safeChannels, effect.params)
            }
        }

        return ShortArray(buffer.size) { (sanitizeSample(buffer[it]) * 32767f).toInt().toShort() }
    }

    // --- Biquad filter foundation ---

    private data class BiquadCoeffs(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float)

    private class BiquadState {
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
    }

    private fun sanitizedSampleRate(sampleRate: Int): Int {
        return if (sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) sampleRate else DEFAULT_SAMPLE_RATE
    }

    private fun sanitizedChannels(channels: Int): Int = channels.coerceIn(1, MAX_CHANNELS)

    private fun finiteParam(params: Map<String, Float>, key: String, default: Float): Float {
        return params[key]?.takeIf { it.isFinite() } ?: default
    }

    private fun sanitizeSample(sample: Float): Float {
        return if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
    }

    private fun sanitizeUnitParam(value: Float, fallback: Float): Float {
        return if (value.isFinite()) value.coerceIn(0f, 1f) else fallback.coerceIn(0f, 1f)
    }

    private fun sanitizeFrequency(frequency: Float, sampleRate: Int, fallback: Float): Float {
        val maxFrequency = (sampleRate / 2f - 1f).coerceAtLeast(21f)
        val safeFrequency = if (frequency.isFinite()) frequency else fallback
        return safeFrequency.coerceIn(20f, maxFrequency)
    }

    private fun biquadProcess(input: FloatArray, channels: Int, coeffs: BiquadCoeffs): FloatArray {
        val safeChannels = sanitizedChannels(channels)
        val output = FloatArray(input.size)
        val states = Array(safeChannels) { BiquadState() }

        for (i in input.indices) {
            val ch = i % safeChannels
            val s = states[ch]
            val x = if (input[i].isFinite()) input[i] else 0f
            val rawY = coeffs.b0 * x + coeffs.b1 * s.x1 + coeffs.b2 * s.x2 - coeffs.a1 * s.y1 - coeffs.a2 * s.y2
            val y = if (rawY.isFinite()) rawY.coerceIn(-16f, 16f) else 0f
            s.x2 = s.x1; s.x1 = x
            s.y2 = s.y1; s.y1 = y
            output[i] = y
        }
        return output
    }

    private fun lowPassCoeffs(sampleRate: Int, frequency: Float, q: Float): BiquadCoeffs {
        // Q <= 0 would produce alpha = ±Infinity and poison every coefficient with NaN,
        // which the IIR state machine then feeds into itself forever — whole track becomes silence/garbage.
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val safeFrequency = sanitizeFrequency(frequency, safeSampleRate, 12_000f)
        val safeQ = if (q.isFinite()) q.coerceAtLeast(0.01f) else 0.7f
        val w0 = 2f * PI.toFloat() * safeFrequency / safeSampleRate
        val alpha = sin(w0) / (2f * safeQ)
        val cosW0 = cos(w0)
        val a0 = 1f + alpha
        return BiquadCoeffs(
            b0 = ((1f - cosW0) / 2f) / a0,
            b1 = (1f - cosW0) / a0,
            b2 = ((1f - cosW0) / 2f) / a0,
            a1 = (-2f * cosW0) / a0,
            a2 = (1f - alpha) / a0
        )
    }

    private fun highPassCoeffs(sampleRate: Int, frequency: Float, q: Float): BiquadCoeffs {
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val safeFrequency = sanitizeFrequency(frequency, safeSampleRate, 80f)
        val safeQ = if (q.isFinite()) q.coerceAtLeast(0.01f) else 0.7f
        val w0 = 2f * PI.toFloat() * safeFrequency / safeSampleRate
        val alpha = sin(w0) / (2f * safeQ)
        val cosW0 = cos(w0)
        val a0 = 1f + alpha
        return BiquadCoeffs(
            b0 = ((1f + cosW0) / 2f) / a0,
            b1 = (-(1f + cosW0)) / a0,
            b2 = ((1f + cosW0) / 2f) / a0,
            a1 = (-2f * cosW0) / a0,
            a2 = (1f - alpha) / a0
        )
    }

    private fun bandPassCoeffs(sampleRate: Int, frequency: Float, bandwidth: Float): BiquadCoeffs {
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val freq = sanitizeFrequency(frequency, safeSampleRate, 1_000f)
        val safeBandwidth = if (bandwidth.isFinite()) bandwidth.coerceIn(0.01f, 8f) else 1f
        val w0 = 2f * PI.toFloat() * freq / safeSampleRate
        val sinW0 = sin(w0).takeIf { abs(it) > 1e-6f } ?: 1e-6f
        val alpha = sinW0 * sinh(ln(2f) / 2f * safeBandwidth * w0 / sinW0)
        val a0 = 1f + alpha
        return BiquadCoeffs(
            b0 = alpha / a0,
            b1 = 0f,
            b2 = -alpha / a0,
            a1 = (-2f * cos(w0)) / a0,
            a2 = (1f - alpha) / a0
        )
    }

    private fun notchCoeffs(sampleRate: Int, frequency: Float, bandwidth: Float): BiquadCoeffs {
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val freq = sanitizeFrequency(frequency, safeSampleRate, 1_000f)
        val safeBandwidth = if (bandwidth.isFinite()) bandwidth.coerceIn(0.01f, 8f) else 0.5f
        val w0 = 2f * PI.toFloat() * freq / safeSampleRate
        val sinW0 = sin(w0).takeIf { abs(it) > 1e-6f } ?: 1e-6f
        val alpha = sinW0 * sinh(ln(2f) / 2f * safeBandwidth * w0 / sinW0)
        val cosW0 = cos(w0)
        val a0 = 1f + alpha
        return BiquadCoeffs(
            b0 = 1f / a0,
            b1 = (-2f * cosW0) / a0,
            b2 = 1f / a0,
            a1 = (-2f * cosW0) / a0,
            a2 = (1f - alpha) / a0
        )
    }

    private fun peakEqCoeffs(sampleRate: Int, frequency: Float, gain: Float, q: Float): BiquadCoeffs {
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val safeFrequency = sanitizeFrequency(frequency, safeSampleRate, 1_000f)
        val safeQ = if (q.isFinite()) q.coerceAtLeast(0.01f) else 1f
        val safeGain = if (gain.isFinite()) gain.coerceIn(-36f, 36f) else 0f
        val a = 10f.pow(safeGain / 40f)
        val w0 = 2f * PI.toFloat() * safeFrequency / safeSampleRate
        val alpha = sin(w0) / (2f * safeQ)
        val cosW0 = cos(w0)
        val a0 = 1f + alpha / a
        return BiquadCoeffs(
            b0 = (1f + alpha * a) / a0,
            b1 = (-2f * cosW0) / a0,
            b2 = (1f - alpha * a) / a0,
            a1 = (-2f * cosW0) / a0,
            a2 = (1f - alpha / a) / a0
        )
    }

    // --- Effect implementations ---

    private fun applyParametricEQ(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        var result = buffer
        for (band in 1..5) {
            val rawFreq = params["band${band}_freq"]?.takeIf { it.isFinite() } ?: continue
            val freq = sanitizeFrequency(rawFreq, sampleRate, 1_000f)
            val gain = finiteParam(params, "band${band}_gain", 0f).coerceIn(-36f, 36f)
            val q = finiteParam(params, "band${band}_q", 1f).coerceIn(0.01f, 20f)
            if (abs(gain) > 0.1f) {
                result = biquadProcess(result, channels, peakEqCoeffs(sampleRate, freq, gain, q))
            }
        }
        return result
    }

    private fun applyCompressor(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val safeChannels = sanitizedChannels(channels)
        val threshold = 10f.pow(finiteParam(params, "threshold", -20f).coerceIn(-100f, 24f) / 20f)
        val ratio = finiteParam(params, "ratio", 4f).coerceIn(1f, 40f)
        // Floor attack/release at 0.1 ms so a stale/corrupt 0 (or negative) doesn't produce
        // exp(-Infinity)=0 (instant peak follow) or exp(+Infinity)=NaN (silent corruption).
        val attackMs = finiteParam(params, "attack", 10f).coerceIn(0.1f, 5_000f)
        val releaseMs = finiteParam(params, "release", 100f).coerceIn(0.1f, 5_000f)
        val knee = finiteParam(params, "knee", 6f).coerceIn(0.01f, 60f)
        val makeupGain = 10f.pow(finiteParam(params, "makeupGain", 0f).coerceIn(-60f, 60f) / 20f)
        val safeSampleRate = sampleRate.coerceAtLeast(1)

        val attackCoeff = exp(-1f / (attackMs * safeSampleRate / 1000f))
        val releaseCoeff = exp(-1f / (releaseMs * safeSampleRate / 1000f))

        val output = buffer.copyOf()
        var envelope = 0f

        for (i in 0 until buffer.size - safeChannels + 1 step safeChannels) {
            var peak = 0f
            for (ch in 0 until safeChannels) {
                peak = maxOf(peak, abs(buffer[i + ch]))
            }

            // Attack = fast rise when signal exceeds envelope, Release = slow decay when signal drops
            val coeff = if (peak > envelope) attackCoeff else releaseCoeff
            envelope = coeff * envelope + (1f - coeff) * peak

            val gain = if (envelope <= threshold) {
                1f
            } else {
                val kneeDb = knee / 2f
                val threshDb = 20f * log10(maxOf(threshold, 1e-10f))
                val envDb = 20f * log10(maxOf(envelope, 1e-10f))
                val over = envDb - threshDb

                val compressedDb = if (over < kneeDb) {
                    threshDb + over - over * over / (4f * kneeDb)
                } else {
                    threshDb + over / ratio
                }
                10f.pow((compressedDb - envDb) / 20f)
            }

            for (ch in 0 until safeChannels) {
                if (i + ch < output.size) {
                    output[i + ch] = buffer[i + ch] * gain * makeupGain
                }
            }
        }
        return output
    }

    private fun applyLimiter(buffer: FloatArray, params: Map<String, Float>): FloatArray {
        val ceiling = 10f.pow(finiteParam(params, "ceiling", -1f).coerceIn(-60f, 0f) / 20f)
        return FloatArray(buffer.size) { i ->
            val sample = if (buffer[i].isFinite()) buffer[i] else 0f
            if (abs(sample) > ceiling) {
                ceiling * sample.sign
            } else sample
        }
    }

    private fun applyNoiseGate(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val safeChannels = sanitizedChannels(channels)
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val threshold = 10f.pow(finiteParam(params, "threshold", -40f).coerceIn(-100f, 0f) / 20f)
        val attackMs = finiteParam(params, "attack", 1f).coerceIn(0.1f, 5_000f)
        val holdMs = finiteParam(params, "hold", 50f).coerceIn(0f, 5_000f)
        val releaseMs = finiteParam(params, "release", 100f).coerceIn(0.1f, 5_000f)

        val attackSamples = (attackMs * safeSampleRate / 1000f).toInt().coerceAtLeast(1)
        val holdSamples = (holdMs * safeSampleRate / 1000f).toInt().coerceAtLeast(0)
        val releaseSamples = (releaseMs * safeSampleRate / 1000f).toInt().coerceAtLeast(1)

        val output = buffer.copyOf()
        var gateOpen = false
        var holdCounter = 0
        var gain = 0f

        for (i in 0 until buffer.size - safeChannels + 1 step safeChannels) {
            var peak = 0f
            for (ch in 0 until safeChannels) peak = maxOf(peak, abs(buffer[i + ch]))

            if (peak >= threshold) {
                gateOpen = true
                holdCounter = holdSamples
            } else if (holdCounter > 0) {
                holdCounter--
            } else {
                gateOpen = false
            }

            val targetGain = if (gateOpen) 1f else 0f
            val step = if (targetGain > gain) 1f / attackSamples else 1f / releaseSamples
            gain = if (targetGain > gain) {
                minOf(gain + step, targetGain)
            } else {
                maxOf(gain - step, targetGain)
            }

            for (ch in 0 until safeChannels) {
                if (i + ch < output.size) output[i + ch] = buffer[i + ch] * gain
            }
        }
        return output
    }

    private fun applyReverb(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val safeChannels = sanitizedChannels(channels)
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val roomSize = sanitizeUnitParam(finiteParam(params, "roomSize", 0.5f), 0.5f)
        val damping = sanitizeUnitParam(finiteParam(params, "damping", 0.5f), 0.5f)
        val wetDry = sanitizeUnitParam(finiteParam(params, "wetDry", 0.3f), 0.3f)
        val decay = finiteParam(params, "decay", 2f).coerceIn(0f, 3f)

        val delaySamples = intArrayOf(
            (0.0297f * safeSampleRate * roomSize).toInt(),
            (0.0371f * safeSampleRate * roomSize).toInt(),
            (0.0411f * safeSampleRate * roomSize).toInt(),
            (0.0437f * safeSampleRate * roomSize).toInt()
        )

        val buffers = Array(4) { FloatArray(delaySamples[it].coerceAtLeast(1)) }
        val indices = IntArray(4)
        val feedback = (decay * 0.3f).coerceIn(0f, 0.95f)

        val output = FloatArray(buffer.size)

        // feedback > 0.5 with a DC-biased or sustained-tone input lets the 4-tap comb filter
        // accumulate indefinitely. Over long clips, the delay buffers either saturate into
        // NaN (via Inf * anything) or underflow into denormal floats that tank CPU by 10-100x
        // on ARM. Hard-clamp the written sample and flush denormals to zero so a pathological
        // input can't poison the reverb state for the rest of the render.
        val dampingCoeff = 1f - damping * 0.5f
        for (i in 0 until buffer.size - safeChannels + 1 step safeChannels) {
            var mono = 0f
            for (ch in 0 until safeChannels) mono += buffer[i + ch] / safeChannels

            var wet = 0f
            for (tap in 0 until 4) {
                val delayed = buffers[tap][indices[tap]]
                wet += delayed
                var next = mono + delayed * feedback * dampingCoeff
                if (!next.isFinite()) next = 0f
                else if (kotlin.math.abs(next) < 1e-20f) next = 0f
                else next = next.coerceIn(-4f, 4f)
                buffers[tap][indices[tap]] = next
                indices[tap] = (indices[tap] + 1) % buffers[tap].size
            }
            wet /= 4f

            for (ch in 0 until safeChannels) {
                if (i + ch < output.size) {
                    output[i + ch] = buffer[i + ch] * (1f - wetDry) + wet * wetDry
                }
            }
        }
        return output
    }

    private fun applyDelay(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val safeChannels = sanitizedChannels(channels)
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val delayMs = finiteParam(params, "delayMs", 250f).coerceIn(1f, 2_000f)
        val feedback = finiteParam(params, "feedback", 0.3f).coerceIn(-0.95f, 0.95f)
        val wetDry = sanitizeUnitParam(finiteParam(params, "wetDry", 0.3f), 0.3f)
        val pingPong = (params["pingPong"] ?: 0f) > 0.5f

        val delaySamples = (delayMs * safeSampleRate / 1000f).toInt().coerceAtLeast(1)
        val delayBuffer = FloatArray(delaySamples * safeChannels)
        var writePos = 0

        val output = FloatArray(buffer.size)

        for (i in 0 until buffer.size - safeChannels + 1 step safeChannels) {
            // Read delayed values and compute output first
            val delayedValues = FloatArray(safeChannels)
            for (ch in 0 until safeChannels) {
                val readIdx = writePos * safeChannels + ch
                delayedValues[ch] = if (readIdx < delayBuffer.size) delayBuffer[readIdx] else 0f
                if (i + ch < buffer.size) {
                    output[i + ch] = buffer[i + ch] * (1f - wetDry) + delayedValues[ch] * wetDry
                }
            }
            // Write feedback after reading all channels to avoid clobbering
            for (ch in 0 until safeChannels) {
                if (i + ch >= buffer.size) continue
                val feedbackCh = if (pingPong && safeChannels == 2) (ch + 1) % 2 else ch
                val feedbackIdx = writePos * safeChannels + feedbackCh
                if (feedbackIdx < delayBuffer.size) {
                    delayBuffer[feedbackIdx] = (buffer[i + ch] + delayedValues[ch] * feedback).coerceIn(-4f, 4f)
                }
            }
            writePos = (writePos + 1) % delaySamples
        }
        return output
    }

    private fun applyDeEsser(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val safeChannels = sanitizedChannels(channels)
        val frequency = sanitizeFrequency(finiteParam(params, "frequency", 6_000f), sampleRate, 6_000f)
        val threshold = 10f.pow(finiteParam(params, "threshold", -20f).coerceIn(-100f, 0f) / 20f)
        val ratio = finiteParam(params, "ratio", 3f).coerceIn(1f, 40f)

        val bpCoeffs = bandPassCoeffs(sampleRate, frequency, 1f)
        val sibilanceDetect = biquadProcess(buffer, safeChannels, bpCoeffs)

        val output = buffer.copyOf()
        for (i in 0 until buffer.size - safeChannels + 1 step safeChannels) {
            var sibilance = 0f
            for (ch in 0 until safeChannels) sibilance = maxOf(sibilance, abs(sibilanceDetect[i + ch]))

            if (sibilance > threshold) {
                val reduction = threshold + (sibilance - threshold) / ratio
                val gain = reduction / maxOf(sibilance, 1e-10f)
                for (ch in 0 until safeChannels) {
                    if (i + ch < output.size) {
                        output[i + ch] = buffer[i + ch] * gain
                    }
                }
            }
        }
        return output
    }

    private fun applyChorus(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val safeChannels = sanitizedChannels(channels)
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val rate = finiteParam(params, "rate", 1.5f).coerceIn(0.01f, 20f)
        val depth = sanitizeUnitParam(finiteParam(params, "depth", 0.5f), 0.5f)
        val wetDry = sanitizeUnitParam(finiteParam(params, "wetDry", 0.3f), 0.3f)

        val maxDelay = (0.03f * safeSampleRate).toInt().coerceAtLeast(2)
        val delayBuf = FloatArray(maxDelay * safeChannels)
        var writeIdx = 0
        var phase = 0f

        val output = FloatArray(buffer.size)
        val phaseInc = rate / safeSampleRate

        for (i in 0 until buffer.size - safeChannels + 1 step safeChannels) {
            phase += phaseInc
            val modDelay = (maxDelay * 0.5f * (1f + sin(2f * PI.toFloat() * phase) * depth)).toInt()
                .coerceIn(1, maxDelay - 1)

            for (ch in 0 until safeChannels) {
                val readIdx = ((writeIdx - modDelay + maxDelay) % maxDelay) * safeChannels + ch
                val delayed = if (readIdx < delayBuf.size) delayBuf[readIdx] else 0f
                output[i + ch] = buffer[i + ch] * (1f - wetDry) + delayed * wetDry
                val wIdx = writeIdx * safeChannels + ch
                if (wIdx < delayBuf.size) delayBuf[wIdx] = buffer[i + ch]
            }
            writeIdx = (writeIdx + 1) % maxDelay
        }
        return output
    }

    private fun applyFlanger(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val safeChannels = sanitizedChannels(channels)
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val rate = finiteParam(params, "rate", 0.5f).coerceIn(0.01f, 20f)
        val depth = sanitizeUnitParam(finiteParam(params, "depth", 0.5f), 0.5f)
        val feedback = finiteParam(params, "feedback", 0.3f).coerceIn(-0.95f, 0.95f)
        val wetDry = sanitizeUnitParam(finiteParam(params, "wetDry", 0.3f), 0.3f)

        val maxDelay = (0.01f * safeSampleRate).toInt().coerceAtLeast(2)
        val delayBuf = FloatArray(maxDelay * safeChannels)
        var writeIdx = 0
        var phase = 0f

        val output = FloatArray(buffer.size)

        for (i in 0 until buffer.size - safeChannels + 1 step safeChannels) {
            phase += rate / safeSampleRate
            val modDelay = (maxDelay * 0.5f * (1f + sin(2f * PI.toFloat() * phase) * depth)).toInt()
                .coerceIn(1, maxDelay - 1)

            for (ch in 0 until safeChannels) {
                val readIdx = ((writeIdx - modDelay + maxDelay) % maxDelay) * safeChannels + ch
                val delayed = if (readIdx < delayBuf.size) delayBuf[readIdx] else 0f
                output[i + ch] = buffer[i + ch] * (1f - wetDry) + delayed * wetDry
                val wIdx = writeIdx * safeChannels + ch
                if (wIdx < delayBuf.size) delayBuf[wIdx] = (buffer[i + ch] + delayed * feedback).coerceIn(-4f, 4f)
            }
            writeIdx = (writeIdx + 1) % maxDelay
        }
        return output
    }

    private fun applyPitchShift(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val safeChannels = sanitizedChannels(channels)
        val semitones = finiteParam(params, "semitones", 0f).coerceIn(-24f, 24f)
        val cents = finiteParam(params, "cents", 0f).coerceIn(-100f, 100f)
        val totalSemitones = semitones + cents / 100f
        if (abs(totalSemitones) < 0.01f) return buffer

        val ratio = 2f.pow(totalSemitones / 12f)
        val output = FloatArray(buffer.size)
        val frameCount = buffer.size / safeChannels
        if (frameCount <= 0) return buffer.copyOf()

        for (ch in 0 until safeChannels) {
            var readPos = 0f
            for (frame in 0 until frameCount) {
                val readFrame = readPos.toInt()
                val frac = readPos - readFrame
                val idx0 = readFrame * safeChannels + ch
                val idx1 = (readFrame + 1).coerceAtMost(frameCount - 1) * safeChannels + ch
                output[frame * safeChannels + ch] = if (idx0 < buffer.size && idx1 < buffer.size) {
                    buffer[idx0] * (1f - frac) + buffer[idx1] * frac
                } else 0f
                readPos += ratio
                if (readPos >= frameCount) readPos -= frameCount
            }
        }
        return output
    }

    private fun applyNormalizer(buffer: FloatArray, params: Map<String, Float>): FloatArray {
        val targetPeakDb = finiteParam(params, "targetPeakDb", -14f).coerceIn(-60f, 0f)
        val peak = buffer.maxOfOrNull { if (it.isFinite()) abs(it) else 0f } ?: return buffer
        if (peak < 1e-10f) return buffer

        val currentDb = 20f * log10(peak)
        val gainDb = targetPeakDb - currentDb
        val gain = 10f.pow(gainDb / 20f).coerceIn(0.1f, 10f)

        return FloatArray(buffer.size) { (buffer[it] * gain).coerceIn(-1f, 1f) }
    }

    private fun applyHighPass(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val freq = sanitizeFrequency(finiteParam(params, "frequency", 80f), sampleRate, 80f)
        val q = finiteParam(params, "resonance", 0.7f).coerceIn(0.01f, 20f)
        return biquadProcess(buffer, channels, highPassCoeffs(sampleRate, freq, q))
    }

    private fun applyLowPass(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val freq = sanitizeFrequency(finiteParam(params, "frequency", 12_000f), sampleRate, 12_000f)
        val q = finiteParam(params, "resonance", 0.7f).coerceIn(0.01f, 20f)
        return biquadProcess(buffer, channels, lowPassCoeffs(sampleRate, freq, q))
    }

    private fun applyBandPass(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val freq = sanitizeFrequency(finiteParam(params, "frequency", 1_000f), sampleRate, 1_000f)
        val bw = finiteParam(params, "bandwidth", 1f).coerceIn(0.01f, 8f)
        return biquadProcess(buffer, channels, bandPassCoeffs(sampleRate, freq, bw))
    }

    private fun applyNotch(buffer: FloatArray, sampleRate: Int, channels: Int, params: Map<String, Float>): FloatArray {
        val freq = sanitizeFrequency(finiteParam(params, "frequency", 1_000f), sampleRate, 1_000f)
        val bw = finiteParam(params, "bandwidth", 0.5f).coerceIn(0.01f, 8f)
        return biquadProcess(buffer, channels, notchCoeffs(sampleRate, freq, bw))
    }

    // --- Beat Detection ---

    /**
     * Detect beat positions in a PCM audio buffer.
     * Returns list of beat positions in milliseconds.
     */
    fun detectBeats(
        pcm: ShortArray,
        sampleRate: Int,
        channels: Int
    ): List<Long> {
        if (pcm.isEmpty()) return emptyList()
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val safeChannels = sanitizedChannels(channels)
        val frameSamples = (safeSampleRate / 10).coerceAtLeast(1) // 100ms frames
        val frameCount = pcm.size / safeChannels / frameSamples
        if (frameCount < 3) return emptyList()

        // Compute energy per frame
        val energies = FloatArray(frameCount) { frame ->
            var sum = 0f
            val start = frame * frameSamples * safeChannels
            val end = minOf(start + frameSamples * safeChannels, pcm.size)
            for (i in start until end) {
                val s = pcm[i].toFloat() / 32768f
                sum += s * s
            }
            sum / (end - start).coerceAtLeast(1)
        }

        // Find peaks (local maxima above average energy * 1.5)
        val avgEnergy = energies.average().toFloat()
        val threshold = avgEnergy * 1.5f
        val beats = mutableListOf<Long>()
        val minBeatGap = 3 // 300ms minimum between beats

        var lastBeat = -minBeatGap
        for (i in 1 until frameCount - 1) {
            if (energies[i] > threshold &&
                energies[i] > energies[i - 1] &&
                energies[i] > energies[i + 1] &&
                i - lastBeat >= minBeatGap
            ) {
                beats.add(i * 100L) // 100ms per frame
                lastBeat = i
            }
        }
        return beats
    }

    /**
     * Analyze audio for ducking regions (where dialogue/speech is detected).
     * Returns list of time ranges in ms where audio should be ducked.
     */
    fun detectSpeechRegions(
        pcm: ShortArray,
        sampleRate: Int,
        channels: Int
    ): List<Pair<Long, Long>> {
        if (pcm.isEmpty()) return emptyList()
        val safeSampleRate = sanitizedSampleRate(sampleRate)
        val safeChannels = sanitizedChannels(channels)
        // Simple energy + zero-crossing rate based speech detection
        val frameSamples = (safeSampleRate / 20).coerceAtLeast(1) // 50ms frames
        val frameCount = pcm.size / safeChannels / frameSamples
        if (frameCount < 2) return emptyList()

        val regions = mutableListOf<Pair<Long, Long>>()
        var speechStart = -1L

        for (frame in 0 until frameCount) {
            val start = frame * frameSamples * safeChannels
            val end = minOf(start + frameSamples * safeChannels, pcm.size)

            // Energy
            var energy = 0f
            for (i in start until end) {
                val s = pcm[i].toFloat() / 32768f
                energy += s * s
            }
            energy /= (end - start).coerceAtLeast(1)

            // Zero crossing rate (speech has moderate ZCR) — step by channels to avoid cross-channel comparison
            var zcr = 0
            for (i in start + safeChannels until end step safeChannels) {
                if ((pcm[i] >= 0) != (pcm[i - safeChannels] >= 0)) zcr++
            }
            val zcrRate = zcr.toFloat() / (((end - start) / safeChannels).coerceAtLeast(1))

            val isSpeech = energy > 0.001f && zcrRate > 0.01f && zcrRate < 0.3f

            if (isSpeech && speechStart < 0) {
                speechStart = frame * 50L
            } else if (!isSpeech && speechStart >= 0) {
                regions.add(speechStart to frame * 50L)
                speechStart = -1
            }
        }
        if (speechStart >= 0) {
            regions.add(speechStart to frameCount * 50L)
        }

        return regions
    }

    /**
     * Compute RMS levels for VU meter display.
     * Returns pair of (left, right) RMS values normalized 0..1.
     */
    fun computeVULevels(pcm: ShortArray, channels: Int): Pair<Float, Float> {
        if (pcm.isEmpty()) return 0f to 0f
        val safeChannels = sanitizedChannels(channels)

        var leftSum = 0f
        var rightSum = 0f
        var count = 0

        for (i in pcm.indices step safeChannels) {
            val left = pcm[i].toFloat() / 32768f
            leftSum += left * left
            if (safeChannels > 1 && i + 1 < pcm.size) {
                val right = pcm[i + 1].toFloat() / 32768f
                rightSum += right * right
            }
            count++
        }

        val leftRms = if (count > 0) sqrt(leftSum / count) else 0f
        val rightRms = if (count > 0) {
            if (safeChannels > 1) sqrt(rightSum / count) else leftRms
        } else 0f

        return leftRms.coerceIn(0f, 1f) to rightRms.coerceIn(0f, 1f)
    }
}
