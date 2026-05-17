package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Engine for FFmpeg-backed export paths that Media3 Transformer does not cover.
 *
 * ## Dependency path (Tier A.9, refreshed in Round 6 R6.5)
 *
 * NovaCut pins `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`, the 16 KB
 * page-size rebuilt FFmpegKit successor used by R6.5. The AAR is verified by
 * the local `scripts/check_16kb_alignment.py` gate after every native dep
 * change and carries GPLv3/source-offer license resources in the packaged APK.
 *
 * ## License note
 *
 * NovaCut itself is MIT-licensed; bundling an AAR whose packaged license
 * resources carry GPLv3 text does not relicense NovaCut's Kotlin source, but it
 * does require shipping the FFmpeg license addendum and source offer with
 * release artifacts. If we need a no-GPL distribution channel, use a separate
 * LGPL-only/no-FFmpeg flavor and keep Media3 Transformer as the H.264/HEVC path.
 *
 * ## Use cases beyond Media3 Transformer
 *
 * - Reverse playback in export (unblocks B.3): `filter_complex [0:v]reverse[v]`
 * - libass ASS/SSA subtitle burn-in with full styling
 * - Two-pass `loudnorm` filter (EBU R128 with linear normalization, supersedes
 *   the current heuristic single-pass path)
 * - Sidechain compress audio ducking
 * - AV1 software encode fallback when MediaCodec lacks hardware AV1
 * - WebM / VP9 format conversion when target requires it
 * - Concat demuxer for seamless lossless joins
 * - atempo audio speed change with pitch correction
 */
@Singleton
class FFmpegEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Execute an FFmpeg command.
     * Returns exit code (0 = success, -1 = unavailable).
     */
    suspend fun execute(
        command: String,
        onProgress: (Float) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        executeCommand(command, onProgress = onProgress)
    }

    /**
     * Extract audio from video to PCM WAV for processing.
     */
    suspend fun extractAudioToWav(
        inputUri: String,
        outputFile: File,
        sampleRate: Int = 16000,
        channels: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        executeArguments(
            listOf(
                "-y",
                "-i", inputUri,
                "-vn",
                "-ac", channels.coerceAtLeast(1).toString(),
                "-ar", sampleRate.coerceAtLeast(1).toString(),
                "-f", "wav",
                outputFile.absolutePath
            )
        ) == 0
    }

    /**
     * Extract audio from an Android Uri to PCM WAV for processing.
     */
    suspend fun extractAudioToWav(
        inputUri: Uri,
        outputFile: File,
        sampleRate: Int = 16000,
        channels: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        executeArguments(
            listOf(
                "-y",
                "-i", ffmpegInput(inputUri),
                "-vn",
                "-ac", channels.coerceAtLeast(1).toString(),
                "-ar", sampleRate.coerceAtLeast(1).toString(),
                "-f", "wav",
                outputFile.absolutePath
            )
        ) == 0
    }

    /**
     * Extract audio from an Android Uri to raw signed 16-bit little-endian PCM.
     */
    suspend fun extractAudioToPcm16le(
        inputUri: Uri,
        outputFile: File,
        sampleRate: Int,
        channels: Int = 1,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        executeArguments(
            listOf(
                "-y",
                "-i", ffmpegInput(inputUri),
                "-vn",
                "-ac", channels.coerceAtLeast(1).toString(),
                "-ar", sampleRate.coerceAtLeast(1).toString(),
                "-f", "s16le",
                outputFile.absolutePath
            ),
            onProgress = onProgress
        ) == 0
    }

    /**
     * Encode raw signed 16-bit little-endian PCM into an AAC M4A file.
     */
    suspend fun encodePcm16leToM4a(
        inputFile: File,
        outputFile: File,
        sampleRate: Int,
        channels: Int = 1,
        bitrate: String = "128k",
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (!inputFile.isFile || inputFile.length() <= 0L) return@withContext false
        executeArguments(
            listOf(
                "-y",
                "-f", "s16le",
                "-ar", sampleRate.coerceAtLeast(1).toString(),
                "-ac", channels.coerceAtLeast(1).toString(),
                "-i", inputFile.absolutePath,
                "-c:a", "aac",
                "-b:a", bitrate,
                outputFile.absolutePath
            ),
            onProgress = onProgress
        ) == 0
    }

    /**
     * Burn ASS/SSA subtitles into video.
     */
    suspend fun burnSubtitles(
        inputFile: File,
        subtitleFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val filter = "subtitles=${escapeFilterPath(subtitleFile.absolutePath)}"
        executeArguments(
            listOf(
                "-y",
                "-i", inputFile.absolutePath,
                "-vf", filter,
                "-c:a", "copy",
                outputFile.absolutePath
            ),
            progressDurationMs = mediaDurationMs(inputFile),
            onProgress = onProgress
        ) == 0
    }

    /**
     * Loudness normalization via FFmpeg loudnorm filter. The first wired path
     * uses FFmpeg's single-pass linear analysis; exact two-pass JSON analysis
     * can layer onto [execute] without changing callers.
     */
    suspend fun normalizeLoudness(
        inputFile: File,
        outputFile: File,
        targetLufs: Float = -14f,
        truePeakDb: Float = -1f,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        executeArguments(
            listOf(
                "-y",
                "-i", inputFile.absolutePath,
                "-af", "loudnorm=I=${targetLufs}:TP=${truePeakDb}:LRA=11",
                "-c:v", "copy",
                outputFile.absolutePath
            ),
            progressDurationMs = mediaDurationMs(inputFile),
            onProgress = onProgress
        ) == 0
    }

    /**
     * Check if an FFmpeg Android library is available at runtime.
     *
     * Uses reflection so this engine can still be queried if a release flavor
     * excludes FFmpeg. Plain JVM unit tests intentionally return false because
     * the native FFmpegKit libraries are Android-only.
     * Once wired, callers can use this gate to choose between Media3 Transformer
     * and FFmpeg paths without an explicit feature flag.
     */
    fun isAvailable(): Boolean {
        if (cachedAvailability != null) return cachedAvailability == true
        if (!isAndroidRuntime()) {
            cachedAvailability = false
            return false
        }
        val available = try {
            // Both arthenica/ffmpeg-kit and its 16 KB-aligned successor share the
            // `com.arthenica.ffmpegkit.FFmpegKit` entry point — checking either
            // covers any drop-in successor that preserves the API.
            Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (e: Throwable) {
            Log.w(TAG, "FFmpegEngine availability probe threw an unexpected error", e)
            false
        }
        cachedAvailability = available
        if (!available) Log.d(TAG, "isAvailable: FFmpeg Android dependency not present")
        return available
    }

    @Volatile private var cachedAvailability: Boolean? = null

    /**
     * Stream-copy trim (LosslessCut-style). When the timeline is a single
     * unmodified clip with only head/tail cuts, we skip transcode entirely
     * via `-c copy -ss -to`. Requires keyframe-aligned boundaries; otherwise
     * FFmpeg emits a warning but still succeeds. ~50x faster than Transformer.
     */
    suspend fun streamCopyTrim(
        inputUri: Uri,
        startMs: Long,
        endMs: Long,
        outputPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (endMs <= startMs) return@withContext false
        executeArguments(
            listOf(
                "-y",
                "-ss", msToSeconds(startMs),
                "-to", msToSeconds(endMs),
                "-i", ffmpegInput(inputUri),
                "-c", "copy",
                "-avoid_negative_ts", "make_zero",
                outputPath
            )
        ) == 0
    }

    /**
     * Concatenate multiple video files losslessly using the concat demuxer.
     */
    suspend fun concat(
        inputFiles: List<File>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (inputFiles.isEmpty()) return@withContext false
        val listFile = File.createTempFile("novacut-ffmpeg-concat-", ".txt", context.cacheDir)
        try {
            listFile.writeText(
                inputFiles.joinToString(separator = "\n") { file ->
                    "file '${escapeConcatPath(file.absolutePath)}'"
                }
            )
            executeArguments(
                listOf(
                    "-y",
                    "-f", "concat",
                    "-safe", "0",
                    "-i", listFile.absolutePath,
                    "-c", "copy",
                    outputFile.absolutePath
                ),
                onProgress = onProgress
            ) == 0
        } finally {
            listFile.delete()
        }
    }

    /**
     * Change video speed with audio pitch correction.
     */
    suspend fun changeSpeed(
        inputFile: File,
        outputFile: File,
        speedFactor: Float,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (speedFactor <= 0f) return@withContext false
        val setPts = String.format(Locale.US, "%.6f", 1f / speedFactor)
        val filter = "[0:v]setpts=${setPts}*PTS[v];[0:a]${buildAtempoChain(speedFactor)}[a]"
        executeArguments(
            listOf(
                "-y",
                "-i", inputFile.absolutePath,
                "-filter_complex", filter,
                "-map", "[v]",
                "-map", "[a]",
                outputFile.absolutePath
            ),
            progressDurationMs = mediaDurationMs(inputFile),
            onProgress = onProgress
        ) == 0
    }

    private suspend fun executeCommand(
        command: String,
        progressDurationMs: Long? = null,
        onProgress: (Float) -> Unit = {}
    ): Int {
        if (!isAvailable()) {
            Log.d(TAG, "executeCommand: FFmpeg Android dependency unavailable")
            return -1
        }
        return suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeAsync(
                command,
                { completed ->
                    val code = returnCodeValue(completed.returnCode)
                    if (code == 0) notifyProgress(onProgress, 1f)
                    if (continuation.isActive) continuation.resume(code)
                },
                { log ->
                    val message = log.message?.trim().orEmpty()
                    if (message.isNotEmpty()) Log.v(TAG, message)
                },
                { stats ->
                    progressFromStats(stats.time, progressDurationMs)?.let { notifyProgress(onProgress, it) }
                }
            )
            continuation.invokeOnCancellation { session.cancel() }
        }
    }

    private suspend fun executeArguments(
        arguments: List<String>,
        progressDurationMs: Long? = null,
        onProgress: (Float) -> Unit = {}
    ): Int {
        if (!isAvailable()) {
            Log.d(TAG, "executeArguments: FFmpeg Android dependency unavailable")
            return -1
        }
        return suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                arguments.toTypedArray(),
                { completed ->
                    val code = returnCodeValue(completed.returnCode)
                    if (code == 0) notifyProgress(onProgress, 1f)
                    if (continuation.isActive) continuation.resume(code)
                },
                { log ->
                    val message = log.message?.trim().orEmpty()
                    if (message.isNotEmpty()) Log.v(TAG, message)
                },
                { stats ->
                    progressFromStats(stats.time, progressDurationMs)?.let { notifyProgress(onProgress, it) }
                }
            )
            continuation.invokeOnCancellation { session.cancel() }
        }
    }

    private fun returnCodeValue(returnCode: ReturnCode?): Int = when {
        returnCode != null && ReturnCode.isSuccess(returnCode) -> 0
        returnCode != null -> returnCode.value
        else -> -1
    }

    private fun progressFromStats(timeMs: Double, durationMs: Long?): Float? {
        val duration = durationMs?.takeIf { it > 0L } ?: return null
        if (timeMs.isNaN() || timeMs.isInfinite() || timeMs <= 0.0) return null
        return (timeMs / duration.toDouble()).toFloat().coerceIn(0f, 0.99f)
    }

    private fun notifyProgress(onProgress: (Float) -> Unit, progress: Float) {
        runCatching { onProgress(progress.coerceIn(0f, 1f)) }
            .onFailure { Log.w(TAG, "FFmpeg progress callback failed", it) }
    }

    private fun isAndroidRuntime(): Boolean {
        return System.getProperty("java.vm.name")
            .orEmpty()
            .contains("dalvik", ignoreCase = true)
    }

    private fun ffmpegInput(uri: Uri): String = when (uri.scheme?.lowercase()) {
        "content" -> FFmpegKitConfig.getSafParameterForRead(context, uri)
        "file" -> uri.path ?: uri.toString()
        else -> uri.toString()
    }

    private fun mediaDurationMs(file: File): Long? {
        if (!file.exists()) return null
        // Duration probing will move to FFprobe once callers need precise
        // progress for every FFmpeg path. A null duration still gives
        // completion progress without risking slow preflight work.
        return null
    }

    private fun escapeFilterPath(path: String): String {
        return path
            .replace("\\", "\\\\")
            .replace(":", "\\:")
            .replace("'", "\\'")
    }

    private fun escapeConcatPath(path: String): String = path.replace("'", "'\\''")

    private fun msToSeconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)

    /**
     * Build atempo filter chain -- FFmpeg atempo only supports 0.5-100.0 per instance,
     * so chain multiple for extreme values.
     */
    private fun buildAtempoChain(speed: Float): String {
        val parts = mutableListOf<String>()
        var remaining = speed.toDouble().coerceIn(0.25, 16.0)
        while (remaining > 2.0) {
            parts.add("atempo=2.0")
            remaining /= 2.0
        }
        while (remaining < 0.5) {
            parts.add("atempo=0.5")
            remaining /= 0.5
        }
        parts.add("atempo=${String.format(Locale.US, "%.4f", remaining)}")
        return parts.joinToString(",")
    }

    companion object {
        private const val TAG = "FFmpegEngine"
    }
}
