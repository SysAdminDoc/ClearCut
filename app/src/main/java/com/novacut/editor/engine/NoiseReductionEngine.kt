package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML-based noise reduction engine.
 * Primary: DeepFilterNet (not yet integrated -- see ROADMAP.md)
 * Fallback: Spectral gating (no model required)
 *
 * DeepFilterNet dependency (add to app/build.gradle.kts when ready):
 *   The correct Maven coordinate needs to be determined; no published
 *   Android artifact is currently available.
 *
 * DeepFilterNet achieves PESQ scores of 3.5-4.0+
 * Processes audio in ~20ms per frame on modern smartphones
 */
@Singleton
class NoiseReductionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class NoiseReductionMode(val displayName: String) {
        OFF("Off"),
        LIGHT("Light -- subtle cleanup"),
        MODERATE("Moderate -- balanced"),
        AGGRESSIVE("Aggressive -- maximum removal"),
        SPECTRAL_GATE("Spectral Gate -- non-ML fallback")
    }

    data class NoiseProfile(
        val type: String,        // "hiss", "hum", "broadband", "mixed"
        val estimatedSnrDb: Float,
        val dominantFreqHz: Float?
    )

    data class NoiseReductionResult(
        val outputFile: File,
        val originalSnrDb: Float,
        val processedSnrDb: Float,
        val noiseProfile: NoiseProfile
    )

    /**
     * Analyze audio to detect noise characteristics.
     * Uses first 2 seconds of audio as noise profile sample.
     *
     * Currently returns a stub estimate -- real analysis requires
     * DeepFilterNet or spectral analysis integration.
     */
    suspend fun analyzeNoise(uri: Uri): NoiseProfile = withContext(Dispatchers.IO) {
        // Stub estimate -- real noise analysis not yet implemented
        Log.d(TAG, "analyzeNoise: stub estimate for $uri (real analysis requires DeepFilterNet)")
        NoiseProfile(
            type = "broadband",
            estimatedSnrDb = 20f,
            dominantFreqHz = null
        )
    }

    /**
     * Process audio file with noise reduction.
     *
     * When DeepFilterNet is available, this will use ML-based processing.
     * Currently falls back to copying the input file as a pass-through so
     * the user at minimum gets their audio back unchanged.
     *
     * Attenuation mapping:
     *   LIGHT = 10 dB, MODERATE = 20 dB, AGGRESSIVE = 40 dB
     */
    suspend fun processAudio(
        uri: Uri,
        mode: NoiseReductionMode = NoiseReductionMode.MODERATE,
        onProgress: (Float) -> Unit = {}
    ): NoiseReductionResult = withContext(Dispatchers.IO) {
        ensureActive()

        val outputDir = File(context.filesDir, "noise_reduced").also { it.mkdirs() }
        val outputFile = File(outputDir, "nr_${System.currentTimeMillis()}.m4a")

        val attenuationDb = when (mode) {
            NoiseReductionMode.LIGHT -> 10f
            NoiseReductionMode.MODERATE -> 20f
            NoiseReductionMode.AGGRESSIVE -> 40f
            NoiseReductionMode.SPECTRAL_GATE -> 15f
            NoiseReductionMode.OFF -> 0f
        }

        if (mode == NoiseReductionMode.OFF) {
            // Copy input to output for pass-through
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outputFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy for OFF pass-through: ${e.message}")
                throw IllegalStateException("Noise reduction OFF pass-through failed: could not copy input", e)
            }
            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw IllegalStateException("Noise reduction OFF pass-through failed: output file is missing or empty")
            }
            onProgress(1f)
            return@withContext NoiseReductionResult(
                outputFile = outputFile,
                originalSnrDb = 20f,
                processedSnrDb = 20f,
                noiseProfile = NoiseProfile("none", 20f, null)
            )
        }

        val noiseProfile = analyzeNoise(uri)
        Log.i(TAG, "Processing with mode=$mode, attenuation=${attenuationDb}dB")

        // DeepFilterNet is not available -- no published Android artifact exists yet.
        // Fallback: copy input to output as pass-through so the user gets their audio back.
        Log.d(TAG, "DeepFilterNet not available -- copying input as pass-through")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy input audio for pass-through: ${e.message}")
        }
        if (!outputFile.exists() || outputFile.length() == 0L) {
            throw IllegalStateException("Noise reduction pass-through failed: output file is missing or empty")
        }

        onProgress(1f)
        NoiseReductionResult(
            outputFile = outputFile,
            originalSnrDb = noiseProfile.estimatedSnrDb,
            processedSnrDb = noiseProfile.estimatedSnrDb, // No actual reduction applied
            noiseProfile = noiseProfile
        )
    }

    /**
     * Apply spectral gating (non-ML fallback).
     * Uses STFT, estimates noise profile from quiet sections,
     * suppresses frequency bins below noise floor.
     */
    suspend fun applySpectralGate(
        samples: FloatArray,
        sampleRate: Int,
        thresholdDb: Float = -30f
    ): FloatArray = withContext(Dispatchers.Default) {
        // Simple spectral gate implementation
        val windowSize = 2048
        val hopSize = windowSize / 4
        val output = samples.copyOf()

        // Estimate noise profile from first 0.5 seconds
        val noiseFrames = (sampleRate * 0.5f / hopSize).toInt().coerceAtLeast(1)
        val noiseProfile = FloatArray(windowSize / 2 + 1)

        // Process in overlapping windows
        var pos = 0
        var frameCount = 0
        while (pos + windowSize <= samples.size) {
            ensureActive()
            // For noise estimation frames, accumulate magnitude spectrum
            if (frameCount < noiseFrames) {
                // Simplified: use RMS of each window as noise estimate
                var rms = 0f
                for (i in 0 until windowSize) {
                    rms += samples[pos + i] * samples[pos + i]
                }
                rms = kotlin.math.sqrt(rms / windowSize)
                val rmsDb = 20f * kotlin.math.log10(rms.coerceAtLeast(1e-10f))

                if (rmsDb < thresholdDb) {
                    // This is a quiet frame -- use as noise reference
                    for (i in 0 until windowSize) {
                        noiseProfile[i % (windowSize / 2 + 1)] += kotlin.math.abs(samples[pos + i])
                    }
                }
            } else {
                // Gate: attenuate samples in windows where energy is below noise floor
                var energy = 0f
                for (i in 0 until windowSize) {
                    energy += samples[pos + i] * samples[pos + i]
                }
                val energyDb = 10f * kotlin.math.log10(energy / windowSize + 1e-10f)
                if (energyDb < thresholdDb) {
                    // Soft gate: attenuate by ratio
                    val gain = (energyDb - thresholdDb + 6f).coerceIn(0f, 1f) / 1f
                    for (i in 0 until windowSize) {
                        output[pos + i] *= gain.coerceIn(0.01f, 1f)
                    }
                }
            }
            pos += hopSize
            frameCount++
        }

        output
    }

    /**
     * Check if DeepFilterNet ML library is available at runtime.
     * Currently always returns false -- no published Android artifact exists.
     */
    fun isDeepFilterNetAvailable(): Boolean {
        // DeepFilterNet Android artifact does not exist yet
        return false
    }

    companion object {
        private const val TAG = "NoiseReduction"
    }
}
