package com.novacut.editor.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Broadcast-quality DSP effects via Soundpipe (C library via NDK).
 *
 * Soundpipe: github.com/PaulBatchelor/Soundpipe
 * 100+ DSP modules: Moog filter, Schroeder/zitareverb, compressor, distortion,
 * delay lines, FM synthesis. Compiles as single static library.
 *
 * NDK integration:
 *   1. Clone Soundpipe repo
 *   2. Add to CMakeLists.txt as static library
 *   3. JNI bridge for each effect module
 *
 * Current fallback: Pure Kotlin implementations in AudioEffectsEngine
 */
@Singleton
class SoundpipeDspEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class ReverbAlgorithm(val displayName: String) {
        SCHROEDER("Schroeder (Classic)"),
        ZITA_REV1("Zita Rev1 (Concert Hall)"),
        FREEVERB("Freeverb (Room)"),
        PLATE("Plate Reverb")
    }

    data class ReverbConfig(
        val algorithm: ReverbAlgorithm = ReverbAlgorithm.FREEVERB,
        val roomSize: Float = 0.5f,    // 0-1
        val damping: Float = 0.5f,     // 0-1
        val wetDry: Float = 0.3f,      // 0=dry, 1=wet
        val preDelay: Float = 20f,     // ms
        val decay: Float = 2.0f        // seconds
    )

    data class FilterConfig(
        val type: FilterType = FilterType.MOOG_LADDER,
        val cutoffHz: Float = 1000f,
        val resonance: Float = 0.5f    // 0-1
    )

    enum class FilterType(val displayName: String) {
        MOOG_LADDER("Moog Ladder (Warm)"),
        BUTTERWORTH_LP("Butterworth Low-Pass"),
        BUTTERWORTH_HP("Butterworth High-Pass"),
        BUTTERWORTH_BP("Butterworth Band-Pass"),
        STATE_VARIABLE("State Variable (Flexible)")
    }

    data class DistortionConfig(
        val type: DistortionType = DistortionType.SOFT_CLIP,
        val drive: Float = 0.5f,       // 0-1
        val mix: Float = 0.5f          // 0=dry, 1=wet
    )

    enum class DistortionType(val displayName: String) {
        SOFT_CLIP("Soft Clip (Warm)"),
        HARD_CLIP("Hard Clip (Aggressive)"),
        TUBE("Tube Saturation"),
        BITCRUSH("Bitcrush")
    }

    /**
     * Apply reverb to audio samples.
     * When NDK is integrated: sp_revsc_compute() for each sample pair
     */
    suspend fun applyReverb(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        config: ReverbConfig = ReverbConfig()
    ): FloatArray = withContext(Dispatchers.Default) {
        // Schroeder reverb implementation (pure Kotlin fallback)
        val output = samples.copyOf()
        val delayLengths = intArrayOf(1557, 1617, 1491, 1422, 1277, 1356, 1188, 1116)
        val allPassDelays = intArrayOf(225, 556, 441, 341)

        // Create comb filters
        val combBuffers = delayLengths.map { FloatArray(it) }
        val combIndices = IntArray(delayLengths.size)
        val allPassBuffers = allPassDelays.map { FloatArray(it) }
        val allPassIndices = IntArray(allPassDelays.size)

        val feedback = config.roomSize * 0.85f + 0.1f
        val damp = config.damping

        for (i in samples.indices step channels) {
            if (i % (sampleRate * channels) == 0) ensureActive()

            val input = if (channels == 2) (samples[i] + samples[i + 1]) * 0.5f else samples[i]

            // Parallel comb filters
            var combSum = 0f
            for (c in combBuffers.indices) {
                val buf = combBuffers[c]
                val idx = combIndices[c]
                val delayed = buf[idx]
                buf[idx] = input + delayed * feedback * (1f - damp)
                combIndices[c] = (idx + 1) % buf.size
                combSum += delayed
            }
            combSum /= combBuffers.size

            // Series allpass filters
            var allPassOut = combSum
            for (a in allPassBuffers.indices) {
                val buf = allPassBuffers[a]
                val idx = allPassIndices[a]
                val delayed = buf[idx]
                val temp = allPassOut + delayed * 0.5f
                buf[idx] = allPassOut - delayed * 0.5f
                allPassIndices[a] = (idx + 1) % buf.size
                allPassOut = temp
            }

            // Mix wet/dry
            val wet = allPassOut * config.wetDry
            for (ch in 0 until channels) {
                output[i + ch] = samples[i + ch] * (1f - config.wetDry) + wet
            }
        }
        output
    }

    /**
     * Apply Moog ladder filter to audio samples.
     * When NDK is integrated: sp_moogladder_compute()
     */
    suspend fun applyFilter(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        config: FilterConfig = FilterConfig()
    ): FloatArray = withContext(Dispatchers.Default) {
        val output = FloatArray(samples.size)
        val fc = (config.cutoffHz / sampleRate).coerceIn(0.001f, 0.499f)
        val res = config.resonance * 4f // Scale resonance to feedback range

        // 4-pole Moog ladder filter approximation
        var s1 = 0f; var s2 = 0f; var s3 = 0f; var s4 = 0f

        for (i in samples.indices step channels) {
            val input = samples[i] - res * s4 // Feedback
            s1 += fc * (input - s1)
            s2 += fc * (s1 - s2)
            s3 += fc * (s2 - s3)
            s4 += fc * (s3 - s4)

            for (ch in 0 until channels) {
                output[i + ch] = when (config.type) {
                    FilterType.MOOG_LADDER, FilterType.BUTTERWORTH_LP -> s4
                    FilterType.BUTTERWORTH_HP -> samples[i + ch] - s4
                    FilterType.BUTTERWORTH_BP -> s2 - s4
                    FilterType.STATE_VARIABLE -> s2
                }
            }
        }
        output
    }

    /**
     * Apply distortion to audio samples.
     */
    suspend fun applyDistortion(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        config: DistortionConfig = DistortionConfig()
    ): FloatArray = withContext(Dispatchers.Default) {
        val output = FloatArray(samples.size)
        val drive = 1f + config.drive * 10f

        for (i in samples.indices) {
            val input = samples[i] * drive
            val distorted = when (config.type) {
                DistortionType.SOFT_CLIP -> (2f / Math.PI.toFloat()) * kotlin.math.atan(input)
                DistortionType.HARD_CLIP -> input.coerceIn(-1f, 1f)
                DistortionType.TUBE -> {
                    if (input >= 0) 1f - kotlin.math.exp(-input) else -(1f - kotlin.math.exp(input))
                }
                DistortionType.BITCRUSH -> {
                    val bits = (16f - config.drive * 12f).toInt().coerceAtLeast(2)
                    val levels = (1 shl bits).toFloat()
                    kotlin.math.round(input * levels) / levels
                }
            }
            output[i] = samples[i] * (1f - config.mix) + distorted * config.mix
        }
        output
    }

    /**
     * Check if native Soundpipe library is available.
     */
    fun isNativeAvailable(): Boolean {
        return try {
            System.loadLibrary("soundpipe_jni")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }
}
