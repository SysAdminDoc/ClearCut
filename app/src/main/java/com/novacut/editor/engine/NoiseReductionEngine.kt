package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kaleyra.noise_filter.DeepFilterNet
import com.rikorose.deepfilternet.NativeDeepFilterNet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

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
 * ClearCut pins `io.github.kaleyravideo:android-deepfilternet:0.0.8`, whose
 * bundled-model AAR ships an ~8 MB `deep_filter_mobile_model`, `libdf.so`
 * for Android ABIs, and the `NativeDeepFilterNet` JNI surface. `processAudio`
 * decodes source audio once to 48 kHz mono signed 16-bit PCM via [FFmpegEngine],
 * processes fixed-size DeepFilterNet frames, then re-encodes the cleaned PCM to
 * M4A. If FFmpeg or the native DeepFilterNet runtime is unavailable, the method
 * keeps the old pass-through behavior rather than failing the edit.
 *
 * ## Model registry
 *
 * See [docs/models.md](../../../../../../docs/models.md) §3 for the DeepFilterNet
 * row; the AAR alignment check lives in §2 and is gated by R6.1a CI.
 */
@Singleton
class NoiseReductionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ffmpegEngine: FFmpegEngine
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
        const val TARGET_ANDROID_AAR_GROUP = "io.github.kaleyravideo"
        const val TARGET_ANDROID_AAR_NAME = "android-deepfilternet"
        const val TARGET_ANDROID_AAR_VERSION = "0.0.8"
        const val TARGET_ANDROID_AAR_SHA256 =
            "6566a208fe476a71b20558f92d93a1c0db49fd93b36fcdaea17a10260189d167"
        private const val DEEPFILTERNET_CLASS_NAME = "com.rikorose.deepfilternet.NativeDeepFilterNet"
        private const val DEEPFILTERNET_INTERFACE_NAME = "com.kaleyra.noise_filter.DeepFilterNet"
        private const val DEEPFILTERNET_LOAD_TIMEOUT_MS = 15_000L
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
        /** "hiss", "hum", "broadband", or "clean" — see [classifyNoise]. */
        val type: String,
        val estimatedSnrDb: Float,
        val dominantFreqHz: Float?
    )

    /**
     * What actually happened. A caller must not treat anything except
     * [APPLIED] as a successful edit, and only [APPLIED] carries an output file.
     */
    enum class NoiseReductionOutcome {
        /** Audio was processed and measurably improved; [NoiseReductionResult.outputFile] is set. */
        APPLIED,

        /** Processing ran (or was not needed) but produced no measurable improvement. Nothing was written. */
        NO_OP,

        /** No usable backend on this device — FFmpeg and/or DeepFilterNet missing. Nothing was written. */
        UNAVAILABLE,

        /** A backend was available and failed. Nothing was written. */
        FAILED,
    }

    data class NoiseReductionResult(
        val outcome: NoiseReductionOutcome,
        /** Non-null only when [outcome] is [NoiseReductionOutcome.APPLIED]. */
        val outputFile: File? = null,
        /** Measured on the source audio; null when it could not be measured. */
        val originalSnrDb: Float? = null,
        /** Measured on the produced audio; null unless something was produced. */
        val processedSnrDb: Float? = null,
        val noiseProfile: NoiseProfile? = null,
        /** Human-readable reason, always populated. */
        val detail: String,
    ) {
        val improvementDb: Float?
            get() {
                val before = originalSnrDb ?: return null
                val after = processedSnrDb ?: return null
                return after - before
            }
    }

    /**
     * Measure the source audio's noise profile.
     *
     * This decodes the audio to 48 kHz mono PCM and measures it: SNR is the ratio
     * of overall frame power to the noise-floor power (the 10th-percentile frame),
     * and the noise type is classified from the zero-crossing rate of the quietest
     * frames. Returns null when the audio cannot be decoded or measured — callers
     * must not substitute a guess.
     */
    suspend fun analyzeNoise(uri: Uri): NoiseProfile? = withContext(Dispatchers.IO) {
        if (!ffmpegEngine.isAvailable()) {
            Log.d(TAG, "analyzeNoise: FFmpeg unavailable, cannot measure")
            return@withContext null
        }
        val workDir = File(context.cacheDir, NOISE_REDUCED_DIR_NAME).also { it.mkdirs() }
        val pcm = File.createTempFile("clearcut-nr-analyze-", ".pcm", workDir)
        try {
            val extracted = ffmpegEngine.extractAudioToPcm16le(
                inputUri = uri,
                outputFile = pcm,
                sampleRate = TARGET_MODEL_SAMPLE_RATE_HZ,
                channels = 1
            ) { }
            if (!extracted) return@withContext null
            measureNoiseProfile(pcm, TARGET_MODEL_SAMPLE_RATE_HZ)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "analyzeNoise failed: ${e.message}")
            null
        } finally {
            pcm.delete()
        }
    }

    /**
     * Process audio with noise reduction and report what actually happened.
     *
     * There is no pass-through: if no backend can run, or the run produced no
     * measurable improvement, nothing is written and the outcome says so. A
     * copy of the input is not a noise-reduced clip, and returning one as
     * success made the editor replace a clip and claim an SNR gain that never
     * happened.
     *
     * Attenuation mapping:
     *   LIGHT = 10 dB, MODERATE = 20 dB, AGGRESSIVE = 40 dB, SPECTRAL_GATE = 15 dB
     */
    suspend fun processAudio(
        uri: Uri,
        mode: NoiseReductionMode = NoiseReductionMode.MODERATE,
        onProgress: (Float) -> Unit = {}
    ): NoiseReductionResult = withContext(Dispatchers.IO) {
        ensureActive()

        if (mode == NoiseReductionMode.OFF) {
            reportProgress(onProgress, 1f)
            return@withContext NoiseReductionResult(
                outcome = NoiseReductionOutcome.NO_OP,
                detail = "Noise reduction is off; the clip was left unchanged."
            )
        }

        if (!ffmpegEngine.isAvailable()) {
            return@withContext NoiseReductionResult(
                outcome = NoiseReductionOutcome.UNAVAILABLE,
                detail = "Audio decoding is unavailable on this device, so no noise reduction ran."
            )
        }

        val useDeepFilterNet = mode != NoiseReductionMode.SPECTRAL_GATE && isDeepFilterNetAvailable()
        if (mode != NoiseReductionMode.SPECTRAL_GATE && !useDeepFilterNet) {
            Log.d(TAG, "DeepFilterNet unavailable; using the spectral-gate backend instead")
        }

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
        Log.i(TAG, "Processing with mode=$mode, attenuation=${attenuationDb}dB, dfn=$useDeepFilterNet")

        try {
            processMeasured(
                uri = uri,
                partialFile = partialFile,
                outputFile = outputFile,
                attenuationDb = attenuationDb,
                useDeepFilterNet = useDeepFilterNet,
                onProgress = onProgress
            )
        } catch (e: CancellationException) {
            cleanupNoiseReductionFiles(partialFile, outputFile)
            throw e
        } catch (e: Exception) {
            cleanupNoiseReductionFiles(partialFile, outputFile)
            Log.w(TAG, "Noise reduction failed: ${e.message}", e)
            NoiseReductionResult(
                outcome = NoiseReductionOutcome.FAILED,
                detail = "Noise reduction failed: ${e.message ?: e::class.java.simpleName}. " +
                    "The clip was left unchanged."
            )
        }
    }

    /**
     * Decode → filter → measure → encode. The produced audio is measured with the
     * same estimator as the source, so the reported improvement is a property of
     * the output rather than a claim from the model or a constant.
     */
    private suspend fun processMeasured(
        uri: Uri,
        partialFile: File,
        outputFile: File,
        attenuationDb: Float,
        useDeepFilterNet: Boolean,
        onProgress: (Float) -> Unit
    ): NoiseReductionResult {
        val workDir = partialFile.parentFile ?: context.cacheDir
        workDir.mkdirs()
        val sourcePcm = File.createTempFile("clearcut-nr-source-", ".pcm", workDir)
        val cleanedPcm = File.createTempFile("clearcut-nr-clean-", ".pcm", workDir)
        var deepFilterNet: DeepFilterNet? = null
        try {
            val extracted = ffmpegEngine.extractAudioToPcm16le(
                inputUri = uri,
                outputFile = sourcePcm,
                sampleRate = TARGET_MODEL_SAMPLE_RATE_HZ,
                channels = 1
            ) { progress -> reportProgress(onProgress, progress * 0.15f) }
            if (!extracted) {
                throw IllegalStateException("Could not decode source audio to 48 kHz PCM")
            }

            val sourceProfile = measureNoiseProfile(sourcePcm, TARGET_MODEL_SAMPLE_RATE_HZ)
                ?: throw IllegalStateException("Source audio was too short to measure")

            if (useDeepFilterNet) {
                deepFilterNet = loadDeepFilterNet()
                deepFilterNet.setAttenuationLimit(attenuationDb)
                filterPcmWithDeepFilterNet(
                    inputFile = sourcePcm,
                    outputFile = cleanedPcm,
                    deepFilterNet = deepFilterNet
                ) { progress -> reportProgress(onProgress, 0.15f + progress * 0.55f) }
            } else {
                filterPcmWithSpectralGate(
                    inputFile = sourcePcm,
                    outputFile = cleanedPcm,
                    thresholdDb = -attenuationDb
                ) { progress -> reportProgress(onProgress, 0.15f + progress * 0.55f) }
            }

            val cleanedProfile = measureNoiseProfile(cleanedPcm, TARGET_MODEL_SAMPLE_RATE_HZ)
                ?: throw IllegalStateException("Processed audio could not be measured")
            val improvementDb = cleanedProfile.estimatedSnrDb - sourceProfile.estimatedSnrDb

            if (improvementDb < MIN_REPORTABLE_IMPROVEMENT_DB) {
                // Honest no-op: the backend ran but the result is not better. Do
                // not mint a generated file or swap the clip for it.
                reportProgress(onProgress, 1f)
                return NoiseReductionResult(
                    outcome = NoiseReductionOutcome.NO_OP,
                    originalSnrDb = sourceProfile.estimatedSnrDb,
                    processedSnrDb = cleanedProfile.estimatedSnrDb,
                    noiseProfile = sourceProfile,
                    detail = "No measurable improvement (%.1f dB); the clip was left unchanged."
                        .format(improvementDb)
                )
            }

            val encoded = ffmpegEngine.encodePcm16leToM4a(
                inputFile = cleanedPcm,
                outputFile = partialFile,
                sampleRate = TARGET_MODEL_SAMPLE_RATE_HZ,
                channels = 1
            ) { progress -> reportProgress(onProgress, 0.70f + progress * 0.29f) }
            if (!encoded) {
                throw IllegalStateException("Could not encode cleaned PCM to M4A")
            }

            val finalizedFile = finalizeNoiseReducedAudioFile(partialFile, outputFile)
                ?: throw IllegalStateException("Noise reduction failed: output file is missing or empty")
            reportProgress(onProgress, 1f)
            return NoiseReductionResult(
                outcome = NoiseReductionOutcome.APPLIED,
                outputFile = finalizedFile,
                originalSnrDb = sourceProfile.estimatedSnrDb,
                processedSnrDb = cleanedProfile.estimatedSnrDb,
                noiseProfile = sourceProfile,
                detail = "Reduced %s noise; measured SNR %.1f dB → %.1f dB.".format(
                    sourceProfile.type,
                    sourceProfile.estimatedSnrDb,
                    cleanedProfile.estimatedSnrDb
                )
            )
        } finally {
            runCatching { deepFilterNet?.release() }
            sourcePcm.delete()
            cleanedPcm.delete()
        }
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
     *
     * Plain JVM unit tests intentionally return false because the AAR's native
     * `libdf.so` is Android-only. Android release flavors can still exclude the
     * dependency; the reflection probe lets callers keep graceful fallback.
     */
    fun isDeepFilterNetAvailable(): Boolean {
        if (cachedDeepFilterNetAvailability != null) return cachedDeepFilterNetAvailability == true
        if (!isAndroidRuntime()) {
            cachedDeepFilterNetAvailability = false
            return false
        }
        val loader = context.classLoader ?: NoiseReductionEngine::class.java.classLoader
        val available = try {
            Class.forName(DEEPFILTERNET_CLASS_NAME, false, loader)
            Class.forName(DEEPFILTERNET_INTERFACE_NAME, false, loader)
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (e: Throwable) {
            Log.w(TAG, "DeepFilterNet availability probe threw an unexpected error", e)
            false
        }
        cachedDeepFilterNetAvailability = available
        return available
    }

    @Volatile private var cachedDeepFilterNetAvailability: Boolean? = null

    /**
     * Stream 16-bit LE PCM through the spectral gate. Reads a bounded window at a
     * time so a long clip does not have to fit in memory.
     */
    private suspend fun filterPcmWithSpectralGate(
        inputFile: File,
        outputFile: File,
        thresholdDb: Float,
        onProgress: (Float) -> Unit
    ) {
        val totalBytes = inputFile.length().coerceAtLeast(1L)
        var processedBytes = 0L
        val chunkSamples = SPECTRAL_GATE_CHUNK_SAMPLES
        val chunkBytes = ByteArray(chunkSamples * 2)

        withContext(Dispatchers.IO) {
            inputFile.inputStream().buffered().use { input ->
                outputFile.outputStream().buffered().use { output ->
                    while (true) {
                        ensureActive()
                        val bytesRead = readPcmFrame(input, chunkBytes)
                        if (bytesRead <= 0) break
                        val sampleCount = bytesRead / 2
                        val samples = FloatArray(sampleCount)
                        decodePcm16le(chunkBytes, sampleCount, samples)
                        val gated = applySpectralGate(samples, TARGET_MODEL_SAMPLE_RATE_HZ, thresholdDb)
                        encodePcm16le(gated, chunkBytes)
                        output.write(chunkBytes, 0, sampleCount * 2)
                        processedBytes += bytesRead.toLong()
                        reportProgress(onProgress, processedBytes.toFloat() / totalBytes.toFloat())
                    }
                }
            }
        }

        if (!outputFile.isFile || outputFile.length() <= 0L) {
            throw IllegalStateException("Spectral gate produced empty PCM output")
        }
    }

    private suspend fun loadDeepFilterNet(): DeepFilterNet = withTimeout(DEEPFILTERNET_LOAD_TIMEOUT_MS) {
        val nativeDeepFilterNet = NativeDeepFilterNet(context.applicationContext)
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { nativeDeepFilterNet.release() }
            nativeDeepFilterNet.onModelLoaded { deepFilterNet ->
                if (continuation.isActive) continuation.resume(deepFilterNet)
            }
        }
    }

    private suspend fun filterPcmWithDeepFilterNet(
        inputFile: File,
        outputFile: File,
        deepFilterNet: DeepFilterNet,
        onProgress: (Float) -> Unit
    ) {
        val frameLengthBytes = deepFilterNet.frameLength.toInt()
        if (frameLengthBytes <= 0) {
            throw IllegalStateException("DeepFilterNet frame length is unavailable")
        }

        val totalBytes = inputFile.length().coerceAtLeast(1L)
        val frameBytes = ByteArray(frameLengthBytes)
        val frameBuffer = ByteBuffer.allocateDirect(frameLengthBytes).order(ByteOrder.LITTLE_ENDIAN)
        var processedBytes = 0L

        withContext(Dispatchers.IO) {
            inputFile.inputStream().buffered().use { input ->
                outputFile.outputStream().buffered().use { output ->
                    while (true) {
                        ensureActive()
                        val bytesRead = readPcmFrame(input, frameBytes)
                        if (bytesRead <= 0) break
                        if (bytesRead < frameLengthBytes) {
                            frameBytes.fill(0, fromIndex = bytesRead, toIndex = frameLengthBytes)
                        }

                        frameBuffer.clear()
                        frameBuffer.put(frameBytes, 0, frameLengthBytes)
                        frameBuffer.flip()
                        // The model's own per-frame SNR estimate is deliberately
                        // ignored: the number shown to the user is measured from
                        // the produced audio, not claimed by the backend.
                        deepFilterNet.processFrame(frameBuffer)
                        frameBuffer.rewind()
                        frameBuffer.get(frameBytes, 0, frameLengthBytes)
                        output.write(frameBytes, 0, bytesRead)

                        processedBytes += bytesRead.toLong()
                        reportProgress(onProgress, processedBytes.toFloat() / totalBytes.toFloat())
                    }
                }
            }
        }

        if (!outputFile.isFile || outputFile.length() <= 0L) {
            throw IllegalStateException("DeepFilterNet produced empty PCM output")
        }
    }

    private fun readPcmFrame(input: InputStream, target: ByteArray): Int {
        var total = 0
        while (total < target.size) {
            val read = input.read(target, total, target.size - total)
            if (read <= 0) break
            total += read
        }
        return total
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

    private fun isAndroidRuntime(): Boolean {
        return System.getProperty("java.vm.name")
            .orEmpty()
            .contains("dalvik", ignoreCase = true)
    }
}

/** Below this, a run is reported as a no-op rather than an improvement. */
internal const val MIN_REPORTABLE_IMPROVEMENT_DB = 0.5f

/** Window used by the streaming spectral-gate stage (0.5 s at 48 kHz). */
private const val SPECTRAL_GATE_CHUNK_SAMPLES = 24_000

/** Analysis window for the SNR estimator (10 ms at 48 kHz). */
internal const val SNR_FRAME_SAMPLES = 480

internal fun decodePcm16le(bytes: ByteArray, sampleCount: Int, target: FloatArray) {
    for (i in 0 until sampleCount) {
        val low = bytes[i * 2].toInt() and 0xFF
        val high = bytes[i * 2 + 1].toInt()
        target[i] = ((high shl 8) or low).toShort().toFloat() / 32768f
    }
}

internal fun encodePcm16le(samples: FloatArray, target: ByteArray) {
    for (i in samples.indices) {
        val clamped = (samples[i] * 32767f).coerceIn(-32768f, 32767f).toInt()
        target[i * 2] = (clamped and 0xFF).toByte()
        target[i * 2 + 1] = ((clamped shr 8) and 0xFF).toByte()
    }
}

/**
 * SNR measured from the audio itself.
 *
 * The signal level is the mean frame power; the noise floor is the
 * 10th-percentile frame power, which for speech or music tracks the quiet
 * stretches between content. `10*log10(signal / floor)` is therefore a real
 * measurement of this file rather than an assumption about it, and running the
 * same estimator on input and output makes the reported improvement meaningful.
 *
 * Returns null when there are too few frames to establish a floor.
 */
internal fun measureSnrDb(framePowers: DoubleArray): Float? {
    if (framePowers.size < MIN_SNR_FRAMES) return null
    val sorted = framePowers.sortedArray()
    val floorIndex = ((sorted.size - 1) * 0.10).toInt()
    val noisePower = sorted[floorIndex].coerceAtLeast(1e-12)
    val signalPower = framePowers.average().coerceAtLeast(1e-12)
    if (signalPower <= noisePower) return 0f
    return (10.0 * kotlin.math.log10(signalPower / noisePower)).toFloat()
}

/**
 * Coarse noise classification from the zero-crossing rate of the quietest
 * frames. High ZCR is characteristic of hiss, very low ZCR of a low-frequency
 * hum; anything between is reported as broadband. This is deliberately not
 * presented as spectral decomposition — the returned dominant frequency is the
 * ZCR-implied fundamental, and only for the hum case.
 */
internal fun classifyNoise(quietFrameZeroCrossingRate: Float, sampleRate: Int, snrDb: Float): Pair<String, Float?> {
    if (snrDb >= CLEAN_SNR_DB) return "clean" to null
    return when {
        quietFrameZeroCrossingRate > 0.25f -> "hiss" to null
        quietFrameZeroCrossingRate < 0.05f ->
            "hum" to (quietFrameZeroCrossingRate * sampleRate / 2f).takeIf { it > 0f }
        else -> "broadband" to null
    }
}

private const val MIN_SNR_FRAMES = 8
private const val CLEAN_SNR_DB = 30f

private const val NOISE_REDUCED_DIR_NAME = "noise_reduced"
private const val NOISE_REDUCED_FILE_PREFIX = "nr_"
private const val NOISE_REDUCED_PARTIAL_SUFFIX = ".partial.m4a"
private const val ABANDONED_NOISE_REDUCTION_PARTIAL_MAX_AGE_MS = 10 * 60 * 1000L

/**
 * Measure a 16-bit LE mono PCM file. Streams the file so a long clip does not
 * have to fit in memory. Returns null when the audio is too short to measure.
 */
internal fun measureNoiseProfile(pcmFile: File, sampleRate: Int): NoiseReductionEngine.NoiseProfile? {
    if (!pcmFile.isFile || pcmFile.length() < SNR_FRAME_SAMPLES * 2L * MIN_MEASURABLE_FRAMES) return null

    val frameBytes = ByteArray(SNR_FRAME_SAMPLES * 2)
    val samples = FloatArray(SNR_FRAME_SAMPLES)
    val powers = mutableListOf<Double>()
    val zeroCrossingRates = mutableListOf<Float>()

    pcmFile.inputStream().buffered().use { input ->
        while (true) {
            var read = 0
            while (read < frameBytes.size) {
                val n = input.read(frameBytes, read, frameBytes.size - read)
                if (n <= 0) break
                read += n
            }
            if (read < frameBytes.size) break
            decodePcm16le(frameBytes, SNR_FRAME_SAMPLES, samples)

            var power = 0.0
            var crossings = 0
            for (i in samples.indices) {
                power += samples[i].toDouble() * samples[i].toDouble()
                if (i > 0 && (samples[i] >= 0f) != (samples[i - 1] >= 0f)) crossings++
            }
            powers += power / SNR_FRAME_SAMPLES
            zeroCrossingRates += crossings.toFloat() / SNR_FRAME_SAMPLES
        }
    }

    val powerArray = powers.toDoubleArray()
    val snrDb = measureSnrDb(powerArray) ?: return null

    // Zero-crossing rate of the quietest decile — the frames the noise floor came from.
    val quietCount = (powers.size / 10).coerceAtLeast(1)
    val quietIndices = powers.indices.sortedBy { powers[it] }.take(quietCount)
    val quietZcr = quietIndices.map { zeroCrossingRates[it] }.average().toFloat()

    val (type, dominantFreqHz) = classifyNoise(quietZcr, sampleRate, snrDb)
    return NoiseReductionEngine.NoiseProfile(
        type = type,
        estimatedSnrDb = snrDb,
        dominantFreqHz = dominantFreqHz
    )
}

private const val MIN_MEASURABLE_FRAMES = 8

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
