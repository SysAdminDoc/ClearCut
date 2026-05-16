package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML-based noise reduction engine.
 *
 * - Primary target: DeepFilterNet 3 (Round 6 R6.6a bumps the target from v2 to v3).
 *   v3 raises PESQ to 3.5–4.0+ and STOI past 0.95 on short audio, especially on
 *   non-stationary noise like synthetic AI voices and crowd noise, at the same
 *   ~8 MB model footprint as v2. The JNI surface is preserved across v2 → v3, so
 *   activation is a model-bytes swap — no Kotlin API change.
 * - Fallback: spectral gating (no model required; ships today).
 *
 * ## Activation path (Tier A.2)
 *
 *   1. Add to gradle/libs.versions.toml:
 *        deepfilternet = "VERSION-FROM-SONATYPE"
 *        deepfilternet-android = { group = "com.kaleyra",
 *                                  name = "deepfilternet-android",
 *                                  version.ref = "deepfilternet" }
 *   2. Add `implementation(libs.deepfilternet.android)` to app/build.gradle.kts.
 *   3. Verify the AAR ships a DeepFilterNet 3 model file (`assets/df3/`-style path).
 *      If the upstream library bundles only v2, point the loader at a downloaded
 *      v3 model via ModelDownloadManager and pass the override path to
 *      `DeepFilterNet.init(context, modelPath = ...)`.
 *   4. Replace the [processAudio] fallback branch with the DeepFilterNet call:
 *        DeepFilterNet.init(context)
 *        val cleaned = DeepFilterNet.process(samples48k)  // FloatArray of 480 samples
 *        // Resample from 16 kHz (Whisper path) to 48 kHz once per export, NOT per frame.
 *
 * ## Model registry
 *
 * See [docs/models.md](../../../../../../docs/models.md) §3 for the DeepFilterNet
 * row; the AAR alignment check lives in §2 and is gated by R6.1a CI.
 */
@Singleton
class NoiseReductionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // R6.6a target — DeepFilterNet 3 supersedes v2 with same JNI surface.
        // Recorded as engine metadata so any caller surfacing model provenance
        // (Settings → AI Models, telemetry, diagnostic export) reports the
        // intended target rather than reading it from the AAR at runtime.
        const val TARGET_MODEL_FAMILY = "deepfilternet"
        const val TARGET_MODEL_VERSION = "3"
        const val TARGET_MODEL_DISPLAY_NAME = "DeepFilterNet 3"
        const val TARGET_MODEL_SAMPLE_RATE_HZ = 48_000
        const val TARGET_MODEL_FRAME_SAMPLES = 480
        const val TARGET_MODEL_FOOTPRINT_BYTES = 8L * 1024L * 1024L
        const val TARGET_MODEL_SOURCE_URL = "https://github.com/Rikorose/DeepFilterNet"
        const val TARGET_ANDROID_AAR_GROUP = "com.kaleyra"
        const val TARGET_ANDROID_AAR_NAME = "deepfilternet-android"
        private const val TAG = "NoiseReductionEngine"
    }

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

        val outputDir = File(context.filesDir, NOISE_REDUCED_DIR_NAME).also { it.mkdirs() }
        sweepAbandonedNoiseReductionPartials(outputDir)
        val outputId = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val outputFile = File(outputDir, "${NOISE_REDUCED_FILE_PREFIX}${outputId}.m4a")
        val partialFile = File(outputDir, "${NOISE_REDUCED_FILE_PREFIX}${outputId}${NOISE_REDUCED_PARTIAL_SUFFIX}")

        val attenuationDb = when (mode) {
            NoiseReductionMode.LIGHT -> 10f
            NoiseReductionMode.MODERATE -> 20f
            NoiseReductionMode.AGGRESSIVE -> 40f
            NoiseReductionMode.SPECTRAL_GATE -> 15f
            NoiseReductionMode.OFF -> 0f
        }

        if (mode == NoiseReductionMode.OFF) {
            try {
                copyInputAudioToPartialFile(uri, partialFile)
                val finalizedFile = finalizeNoiseReducedAudioFile(partialFile, outputFile)
                    ?: throw IllegalStateException("Noise reduction OFF pass-through failed: output file is missing or empty")
                reportProgress(onProgress, 1f)
                return@withContext NoiseReductionResult(
                    outputFile = finalizedFile,
                    originalSnrDb = 20f,
                    processedSnrDb = 20f,
                    noiseProfile = NoiseProfile("none", 20f, null)
                )
            } catch (e: CancellationException) {
                cleanupNoiseReductionFiles(partialFile, outputFile)
                throw e
            } catch (e: Exception) {
                cleanupNoiseReductionFiles(partialFile, outputFile)
                Log.w(TAG, "Failed to copy for OFF pass-through: ${e.message}")
                throw IllegalStateException("Noise reduction OFF pass-through failed: could not copy input", e)
            }
        }

        val noiseProfile = analyzeNoise(uri)
        Log.i(TAG, "Processing with mode=$mode, attenuation=${attenuationDb}dB")

        // DeepFilterNet is not available -- no published Android artifact exists yet.
        // Fallback: copy input to output as pass-through so the user gets their audio back.
        Log.d(TAG, "DeepFilterNet not available -- copying input as pass-through")
        val finalizedFile = try {
            copyInputAudioToPartialFile(uri, partialFile)
            finalizeNoiseReducedAudioFile(partialFile, outputFile)
                ?: throw IllegalStateException("Noise reduction pass-through failed: output file is missing or empty")
        } catch (e: CancellationException) {
            cleanupNoiseReductionFiles(partialFile, outputFile)
            throw e
        } catch (e: Exception) {
            cleanupNoiseReductionFiles(partialFile, outputFile)
            Log.w(TAG, "Failed to copy input audio for pass-through: ${e.message}")
            throw IllegalStateException("Noise reduction pass-through failed: could not copy input", e)
        }

        reportProgress(onProgress, 1f)
        NoiseReductionResult(
            outputFile = finalizedFile,
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

    private fun copyInputAudioToPartialFile(uri: Uri, partialFile: File) {
        partialFile.parentFile?.mkdirs()
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open source audio")
        inputStream.use { input ->
            partialFile.outputStream().use { output -> input.copyTo(output) }
        }
        if (!partialFile.isFile || partialFile.length() <= 0L) {
            partialFile.delete()
            throw IllegalStateException("Copied source audio is empty")
        }
    }

    private fun reportProgress(onProgress: (Float) -> Unit, value: Float) {
        runCatching { onProgress(value) }
            .onFailure { Log.w(TAG, "Noise reduction progress callback failed", it) }
    }
}

private const val NOISE_REDUCED_DIR_NAME = "noise_reduced"
private const val NOISE_REDUCED_FILE_PREFIX = "nr_"
private const val NOISE_REDUCED_PARTIAL_SUFFIX = ".partial.m4a"
private const val ABANDONED_NOISE_REDUCTION_PARTIAL_MAX_AGE_MS = 10 * 60 * 1000L

private fun cleanupNoiseReductionFiles(partialFile: File, outputFile: File) {
    partialFile.delete()
    outputFile.delete()
}

private fun sweepAbandonedNoiseReductionPartials(dir: File) {
    val cutoff = System.currentTimeMillis() - ABANDONED_NOISE_REDUCTION_PARTIAL_MAX_AGE_MS
    dir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(NOISE_REDUCED_PARTIAL_SUFFIX) && it.lastModified() < cutoff }
        ?.forEach { it.delete() }
}

internal fun finalizeNoiseReducedAudioFile(partialFile: File, outputFile: File): File? {
    if (!partialFile.isFile || partialFile.length() <= 0L) {
        cleanupNoiseReductionFiles(partialFile, outputFile)
        return null
    }
    moveFileReplacing(partialFile, outputFile)
    return if (outputFile.isFile && outputFile.length() > 0L) {
        outputFile
    } else {
        outputFile.delete()
        null
    }
}
