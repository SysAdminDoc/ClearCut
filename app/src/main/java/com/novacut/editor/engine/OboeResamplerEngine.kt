package com.novacut.editor.engine

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine for high-quality sinc-based sample rate conversion via
 * Google's Oboe library. See ROADMAP.md Tier A.10.
 *
 * Used to mix 44.1 kHz music with 48 kHz video audio without the aliasing
 * artefacts of naive linear resampling. The fallback path (Media3's built-in
 * resampler) is correct but drops samples on long mixes; Oboe's resampler is
 * the supported alternative.
 *
 * ## Activation path
 *
 *   1. Add to gradle/libs.versions.toml:
 *        oboe = "1.9.0"
 *        oboe = { group = "com.google.oboe", name = "oboe", version.ref = "oboe" }
 *   2. Add `implementation(libs.oboe)` to app/build.gradle.kts.
 *   3. Implement [resample] by calling
 *      `MultiChannelResampler.make(channels, fromHz, toHz, qualityEnum)`
 *      and feeding the input in 1024-frame chunks. Oboe's resampler is C++ /
 *      JNI; bridge through a small `oboe-jni.so` if one is not already in
 *      the AAR.
 *   4. Flip [isAvailable] to do a reflection probe against the resampler's
 *      Java entry point so consumers can branch on presence.
 *
 * ## License + size
 *
 * Oboe is Apache-2.0. The AAR carries a ~700 KB arm64 native blob; verify
 * 16 KB alignment (R6.1) before pinning.
 */
@Singleton
class OboeResamplerEngine @Inject constructor() {

    enum class Quality {
        /** Linear interpolation. Lowest quality, zero cost. */
        FASTEST,
        /** 8-point sinc. Adequate for non-critical audio. */
        LOW,
        /** 16-point sinc. Default. */
        MEDIUM,
        /** 32-point sinc. Use for music tracks. */
        HIGH,
        /** 64-point sinc. Archival quality. */
        BEST
    }

    /**
     * Whether native Oboe resampler is available. Uses reflection so this
     * engine can be queried before the dep is wired — consumers can use the
     * gate to branch between Oboe and the Media3 fallback path without an
     * explicit feature flag.
     */
    fun isAvailable(): Boolean {
        cachedAvailability?.let { return it }
        val available = try {
            // Public Java entry point shipped with the Oboe AAR.
            Class.forName("com.google.oboe.MultiChannelResampler")
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (e: Throwable) {
            Log.w(TAG, "OboeResamplerEngine availability probe threw an unexpected error", e)
            false
        }
        cachedAvailability = available
        if (!available) Log.d(TAG, "isAvailable: Oboe dependency not present")
        return available
    }

    @Volatile private var cachedAvailability: Boolean? = null

    /**
     * Resample an interleaved float PCM buffer from [fromSampleRate] to [toSampleRate].
     * Returns null when native resampler is unavailable -- callers must fall back.
     */
    fun resample(
        input: FloatArray,
        channels: Int,
        fromSampleRate: Int,
        toSampleRate: Int,
        quality: Quality = Quality.MEDIUM
    ): FloatArray? {
        if (!isAvailable()) {
            Log.d(
                TAG,
                "resample: stub -- requires Oboe (${input.size} samples, " +
                    "${fromSampleRate}->${toSampleRate} Hz, quality=$quality)"
            )
            return null
        }
        // When the dep lands, replace this branch with the actual
        // MultiChannelResampler.make(...) call. Until then, even a present
        // class can't be invoked without the JNI bridge, so we conservatively
        // return null.
        Log.d(TAG, "resample: Oboe class present but engine not yet wired; returning null")
        return null
    }

    /**
     * Estimate the output buffer length [resample] will return for the given
     * inputs. Pure math, available even when the engine is stubbed — useful
     * for sizing intermediate buffers in the audio mix path today.
     */
    fun estimatedOutputFrames(
        inputFrames: Long,
        fromSampleRate: Int,
        toSampleRate: Int,
    ): Long {
        require(fromSampleRate > 0 && toSampleRate > 0) {
            "Sample rates must be positive: from=$fromSampleRate to=$toSampleRate"
        }
        if (inputFrames <= 0L) return 0L
        // Symmetric rounding: ceil((input * toHz) / fromHz)
        // Use BigInteger-style staging to avoid Long overflow for >24-hour buffers.
        val numerator = inputFrames * toSampleRate.toLong()
        val q = numerator / fromSampleRate
        val r = numerator % fromSampleRate
        return if (r == 0L) q else q + 1
    }

    companion object {
        private const val TAG = "OboeResampler"
        const val TARGET_OBOE_VERSION = "1.9.0"
        const val TARGET_MAVEN_GROUP = "com.google.oboe"
        const val TARGET_MAVEN_NAME = "oboe"
    }
}
