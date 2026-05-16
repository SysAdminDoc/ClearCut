package com.novacut.editor.engine

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine for FFmpeg-backed export paths that Media3 Transformer does not cover.
 *
 * ## Activation path (Tier A.9, refreshed in Round 6 R6.5)
 *
 * The recommended FFmpeg distribution for NovaCut is now
 * `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1` on Maven Central — it is the
 * actively maintained successor to the archived arthenica/ffmpeg-kit, ships with
 * 16 KB page-size aligned native libs (mandatory for Play Store on `targetSdk = 36`,
 * see [docs/models.md](../../../../../../docs/models.md) §2), and is built with
 * NDK r27d, Full-GPL, and MediaCodec support.
 *
 * To activate this engine:
 *   1. Add to `gradle/libs.versions.toml`:
 *        ffmpegKit = "6.1.1"
 *        ffmpeg-kit-16kb = { group = "com.moizhassan.ffmpeg",
 *                            name = "ffmpeg-kit-16kb",
 *                            version.ref = "ffmpegKit" }
 *   2. Add `implementation(libs.ffmpeg.kit.16kb)` to `app/build.gradle.kts`.
 *   3. Replace the bodies of [execute], [streamCopyTrim], [concat], [changeSpeed],
 *      [extractAudioToWav], [burnSubtitles], [normalizeLoudness] with the
 *      corresponding `FFmpegKit.executeAsync(...)` / `MediaInformation.fromUri(...)`
 *      bridges. Wire progress callbacks through `FFmpegKitConfig.enableStatisticsCallback`.
 *   4. Add the LGPL/GPL notice + offer-of-source to LICENSE per FFmpeg's license
 *      (Full-GPL build).
 *
 * ## License note
 *
 * NovaCut itself is MIT-licensed; bundling a Full-GPL `.so` does not relicense
 * NovaCut's Kotlin source but does require shipping the FFmpeg license addendum
 * with release artifacts. If we want to dodge that obligation, the LGPL-only
 * `ffmpeg-kit` build variant exists at the cost of losing libx264/libx265/libfdk —
 * we would have to fall back to MediaCodec for H.264/HEVC encoding (which is
 * fine because Media3 Transformer already covers those codecs).
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
        Log.d(TAG, "execute: stub -- requires FFmpeg Android dependency")
        -1
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
        Log.d(TAG, "extractAudioToWav: stub -- requires FFmpeg Android dependency")
        false
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
        Log.d(TAG, "burnSubtitles: stub -- requires FFmpeg Android dependency")
        false
    }

    /**
     * Two-pass loudness normalization via FFmpeg loudnorm filter.
     */
    suspend fun normalizeLoudness(
        inputFile: File,
        outputFile: File,
        targetLufs: Float = -14f,
        truePeakDb: Float = -1f,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "normalizeLoudness: stub -- requires FFmpeg Android dependency")
        false
    }

    /**
     * Check if an FFmpeg Android library is available at runtime.
     *
     * Uses reflection so this engine can be queried before the dep is wired
     * (see class docstring for the `ffmpeg-kit-16kb:6.1.1` activation path).
     * Once wired, callers can use this gate to choose between Media3 Transformer
     * and FFmpeg paths without an explicit feature flag.
     */
    fun isAvailable(): Boolean {
        if (cachedAvailability != null) return cachedAvailability == true
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
        inputUri: android.net.Uri,
        startMs: Long,
        endMs: Long,
        outputPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "streamCopyTrim: stub $inputUri [$startMs..$endMs] -> $outputPath")
        false
    }

    /**
     * Concatenate multiple video files losslessly using the concat demuxer.
     */
    suspend fun concat(
        inputFiles: List<File>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "concat: stub -- requires FFmpeg Android dependency")
        false
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
        Log.d(TAG, "changeSpeed: stub -- requires FFmpeg Android dependency")
        false
    }

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
        parts.add("atempo=${"%.4f".format(remaining)}")
        return parts.joinToString(",")
    }

    companion object {
        private const val TAG = "FFmpegEngine"
    }
}
