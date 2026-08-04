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
 * ## Dependency path
 *
 * ClearCut vendors a source-pinned LGPL-profile FFmpegKitNext 8.1.0 AAR
 * carrying FFmpeg 8.1.2. Local gates verify its checksum, source/build lock,
 * disabled-format advisory coverage, deterministic SBOMs, and 16 KB page
 * alignment.
 *
 * ## License note
 *
 * ClearCut itself is MIT-licensed; bundling an AAR whose packaged license
 * resources carry LGPL text does not relicense ClearCut's Kotlin source, but it
 * does require shipping the FFmpeg license addendum and source offer with
 * release artifacts. The shipped profile has no x264/GPL encoder and keeps
 * Media3 Transformer as the primary H.264/HEVC path.
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
 *
 * ## R6.16 — ffmpeg-kt evaluation spike (2026-06-13)
 *
 * ### What was evaluated
 *
 * "ffmpeg-kt" (`zt64/ffmpeg-kt`, `dev.zt64:ffmpeg-kt-*`) — a Kotlin
 * Multiplatform project wrapping FFmpeg's raw libav* C API (libavcodec,
 * libavformat, libswscale, etc.) directly via native bindings. Inspired by
 * PyAV, NOT by FFmpegKit's command-string execution model.
 *
 * ### API compatibility matrix
 *
 * | ClearCut call site                       | FFmpegKit (current)              | ffmpeg-kt (zt64)                  |
 * |-----------------------------------------|----------------------------------|-----------------------------------|
 * | `execute(command)`                       | `FFmpegKit.executeAsync(cmd,…)`  | No equivalent — raw libav* only   |
 * | `executeArguments(args)`                 | `FFmpegKit.executeWithArgumentsAsync(…)` | No equivalent              |
 * | SAF `content://` URIs                    | `FFmpegKitConfig.getSafParameterForRead()` | Not supported             |
 * | Progress via `StatisticsCallback`        | `stats.time` in callback         | Not documented                    |
 * | Session cancellation                     | `session.cancel()`               | Not documented                    |
 * | Return codes                             | `ReturnCode.isSuccess()`         | Not applicable (API-level calls)  |
 * | `concat` demuxer                        | Via command string               | Would require raw demuxer API     |
 * | `atempo` filter chain                   | Via command string               | Would require filter graph API    |
 * | `burnSubtitles` (libass)                | Via `-vf ass=` filter            | Would require filter graph API    |
 *
 * Verdict: **zero overlap** with current call sites. Adopting ffmpeg-kt
 * would require a complete rewrite of FFmpegEngine from command-string
 * dispatch to raw libav* API calls — a fundamentally different abstraction
 * level. Every typed entry point (extractAudioToWav, reverseClipToFile,
 * concat, burnSubtitles, normalizeLoudness, streamCopyTrim)
 * constructs FFmpeg CLI argument lists, which ffmpeg-kt cannot consume.
 *
 * ### 16KB page-size compliance
 *
 * Not documented. No published native .so artifacts to verify. The project
 * does not mention Android 16 / API 35+ 16KB page alignment anywhere.
 * ClearCut's FFmpegKitNext 8.1.0 build targets NDK r27d and is verified by the
 * local `scripts/check_16kb_alignment.py` gate.
 *
 * ### APK size comparison
 *
 * Cannot be measured — ffmpeg-kt has no published releases, no AARs, and
 * no artifacts on Maven Central or JitPack. The project README uses `x.y.z`
 * placeholder version numbers.
 *
 * ### License analysis
 *
 * ffmpeg-kt is GPL v3.0. ClearCut's current FFmpegKitNext profile is LGPL-only,
 * so adopting it would not improve the distribution posture. The alternative
 * JamaisMagic fork
 * (`io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-16kb:6.1.7`) offers an
 * LGPL-3.0 variant, which would be a license improvement if ClearCut ever
 * needs a no-GPL distribution channel.
 *
 * ### Adopt / defer criteria
 *
 * **DECISION: DEFER indefinitely.**
 *
 * ffmpeg-kt is pre-alpha (no stable release, "API subject to change"),
 * operates at a fundamentally different abstraction level (raw libav* vs
 * command strings), lacks SAF support critical for Android, has no 16KB
 * compliance documentation, and would require a full FFmpegEngine rewrite
 * with no functional benefit over the current fork.
 *
 * The upstream binary-supply risk is mitigated by ClearCut's vendored,
 * reproducibly built AAR and local provenance/advisory gates.
 *
 * Re-evaluate if ffmpeg-kt ships a 1.0 with command-string execution,
 * SAF support, and published Android AARs.
 */
@Singleton
class FFmpegEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Execute an FFmpeg command.
     * Returns exit code (0 = success, -1 = unavailable).
     *
     * Note: raw command execution has no policy gate — callers of this method
     * are responsible for pre-validating their own inputs. Prefer the typed
     * entry points (extractAudioToWav, burnSubtitles, etc.) which enforce
     * size/format/timeout policy.
     */
    suspend fun execute(
        command: String,
        onProgress: (Float) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        executeCommand(command, onProgress = onProgress)
    }

    /**
     * Encode a rendered image sequence while retaining the source audio.
     *
     * Frame-based effects generate a new video stream, but they must not turn
     * a normal editor clip into a silent clip. The optional audio map keeps
     * screen recordings and other video-only sources valid as well.
     */
    suspend fun encodeImageSequenceWithAudio(
        inputUri: Uri,
        framePattern: String,
        fps: Int,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val v = NativeProcessingPolicy.validateVideoUri(context, inputUri, "encodeImageSequenceWithAudio")
        if (v != null) return@withContext NativeProcessingPolicy.logAndReject(v)
        if (framePattern.isBlank()) return@withContext false
        outputFile.parentFile?.mkdirs()
        val encoder = preferredIntermediateEncoder()
        executeArguments(
            buildList {
                addAll(listOf("-y", "-framerate", fps.coerceIn(1, 120).toString()))
                addAll(listOf("-i", framePattern))
                addAll(listOf("-i", ffmpegInput(inputUri)))
                addAll(listOf("-map", "0:v:0", "-map", "1:a:0?"))
                addAll(listOf("-c:v", encoder.ffmpegName))
                addAll(intermediateQualityArgs(encoder))
                addAll(listOf("-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k"))
                addAll(listOf("-shortest", outputFile.absolutePath))
            },
            onProgress = onProgress
        ) == 0
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
        val v = validateInputPath(inputUri, "extractAudioToWav")
        if (v != null) return@withContext NativeProcessingPolicy.logAndReject(v)
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
        val v = NativeProcessingPolicy.validateVideoUri(context, inputUri, "extractAudioToWav")
        if (v != null) return@withContext NativeProcessingPolicy.logAndReject(v)
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

    suspend fun reverseClipToFile(
        inputUri: Uri,
        outputFile: File,
        trimStartMs: Long = 0L,
        trimEndMs: Long = Long.MAX_VALUE,
        // Sources with no audio stream (screen recordings, timelapses,
        // GIF-derived MP4s) have no [0:a] to map — mapping it unconditionally
        // aborts the whole session, so the caller must pass whether audio
        // exists and we branch the filter graph accordingly.
        hasAudio: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val v = NativeProcessingPolicy.validateVideoUri(context, inputUri, "reverseClipToFile")
        if (v != null) return@withContext NativeProcessingPolicy.logAndReject(v)
        val args = buildList {
            add("-y")
            if (trimStartMs > 0L) {
                add("-ss"); add(String.format(java.util.Locale.US, "%.3f", trimStartMs / 1000.0))
            }
            if (trimEndMs < Long.MAX_VALUE) {
                add("-to"); add(String.format(java.util.Locale.US, "%.3f", trimEndMs / 1000.0))
            }
            add("-i"); add(ffmpegInput(inputUri))
            if (hasAudio) {
                add("-filter_complex"); add("[0:v]reverse[v];[0:a]areverse[a]")
                add("-map"); add("[v]")
                add("-map"); add("[a]")
            } else {
                add("-filter_complex"); add("[0:v]reverse[v]")
                add("-map"); add("[v]")
            }
            // Explicit, probed encoder. This intermediate is consumed by the
            // Media3 composition and re-encoded there, so it only has to be
            // high-quality and MediaCodec-decodable — not any particular codec.
            val encoder = preferredIntermediateEncoder()
            add("-c:v"); add(encoder.ffmpegName)
            addAll(intermediateQualityArgs(encoder))
            if (hasAudio) {
                add("-c:a"); add("aac")
                add("-b:a"); add("192k")
            }
            add(outputFile.absolutePath)
        }
        executeArguments(args, onProgress = onProgress) == 0
    }

    /**
     * Materialize a constant-cadence intermediate for Media3 Transformer.
     *
     * Media3's public frame-rate effect drops frames but does not duplicate a
     * sparse input frame. The `fps` filter is therefore required before the
     * final Transformer pass when the user explicitly requests CFR. This file
     * is an intermediate: Transformer still owns the final resolution, codec,
     * overlays, metadata policy, and muxing decisions.
     */
    suspend fun normalizeVideoFrameRate(
        inputUri: Uri,
        outputFile: File,
        frameRate: Int,
        onProgress: (Float) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val v = NativeProcessingPolicy.validateVideoUri(context, inputUri, "normalizeVideoFrameRate")
        if (v != null) return@withContext NativeProcessingPolicy.logAndReject(v)
        val encoder = preferredIntermediateEncoder()
        val safeFrameRate = frameRate.coerceIn(1, 240)
        outputFile.parentFile?.mkdirs()
        executeArguments(
            listOf(
                "-y",
                "-i", ffmpegInput(inputUri),
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-vf", "fps=$safeFrameRate",
                "-fps_mode", "cfr",
                "-c:v", encoder.ffmpegName,
                *intermediateQualityArgs(encoder).toTypedArray(),
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "192k",
                "-map_metadata", "-1",
                "-shortest",
                outputFile.absolutePath,
            ),
            onProgress = onProgress,
        ) == 0 && outputFile.isFile && outputFile.length() > 0L
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
        val v = NativeProcessingPolicy.validateVideoUri(context, inputUri, "extractAudioToPcm16le")
        if (v != null) return@withContext NativeProcessingPolicy.logAndReject(v)
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
        val v = NativeProcessingPolicy.validateAudioFile(inputFile, "encodePcm16leToM4a")
        if (v != null) return@withContext NativeProcessingPolicy.logAndReject(v)
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
        val vv = NativeProcessingPolicy.validateVideoFile(inputFile, "burnSubtitles")
        if (vv != null) return@withContext NativeProcessingPolicy.logAndReject(vv)
        val vs = NativeProcessingPolicy.validateSubtitleFile(subtitleFile, "burnSubtitles")
        if (vs != null) return@withContext NativeProcessingPolicy.logAndReject(vs)
        val filter = subtitleFilter(subtitleFile.absolutePath)
        // No -c:v here previously meant FFmpeg picked the container default,
        // which could resolve to a build-dependent encoder. This pass writes
        // the file the user receives, so the encoder is named explicitly.
        val encoder = preferredIntermediateEncoder()
        executeArguments(
            buildList {
                addAll(listOf("-y", "-i", inputFile.absolutePath, "-vf", filter))
                add("-c:v"); add(encoder.ffmpegName)
                addAll(intermediateQualityArgs(encoder))
                addAll(listOf("-c:a", "copy", outputFile.absolutePath))
            },
            progressDurationMs = mediaDurationMs(inputFile),
            onProgress = onProgress
        ) == 0
    }

    /**
     * Convert one embedded subtitle stream into a portable local sidecar.
     *
     * The stream index comes from [MediaExtractor], so the mapping is explicit
     * and cannot accidentally export the first subtitle stream from a source
     * that contains several languages. Only text sidecar formats are accepted;
     * bitmap subtitles are reported as unsupported by the caller.
     */
    suspend fun extractSubtitleTrack(
        inputUri: Uri,
        trackIndex: Int,
        format: MetadataSidecarFormat,
        outputFile: File,
    ): Boolean = withContext(Dispatchers.IO) {
        if (trackIndex < 0 || format !in setOf(MetadataSidecarFormat.VTT, MetadataSidecarFormat.SRT)) {
            return@withContext false
        }
        val violation = NativeProcessingPolicy.validateVideoUri(
            context,
            inputUri,
            "extractSubtitleTrack",
        )
        if (violation != null) return@withContext NativeProcessingPolicy.logAndReject(violation)
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        val (codec, muxer) = when (format) {
            MetadataSidecarFormat.VTT -> "webvtt" to "webvtt"
            MetadataSidecarFormat.SRT -> "subrip" to "srt"
            else -> return@withContext false
        }
        executeArguments(
            listOf(
                "-y",
                "-i", ffmpegInput(inputUri),
                "-map", "0:$trackIndex",
                "-map_metadata", "-1",
                "-c:s", codec,
                "-f", muxer,
                outputFile.absolutePath,
            )
        ) == 0 && outputFile.isFile && outputFile.length() > 0L
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
        val v = NativeProcessingPolicy.validateVideoFile(inputFile, "normalizeLoudness")
        if (v != null) return@withContext NativeProcessingPolicy.logAndReject(v)
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
            // FFmpegKitNext preserves the original
            // `com.arthenica.ffmpegkit.FFmpegKit` entry point.
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
     * H.264 encoders an FFmpeg build may expose, best first.
     *
     * The final export is encoded by Media3 Transformer through Android
     * MediaCodec and never touches FFmpeg, so ClearCut's headline H.264 support
     * does not depend on any of these. These cover the FFmpeg-side passes —
     * reverse pre-render, subtitle burn-in, inpainted frame assembly — which
     * previously either hard-coded `libx264` or, worse, passed no `-c:v` at all
     * and silently inherited whatever the build's default happened to be.
     *
     * [MEDIACODEC] wraps the device's hardware encoder and is licence-neutral.
     * [X264] is retained only as a compatibility probe for a future artifact;
     * the locked ClearCut profile does not build it. [MPEG4] is FFmpeg's own
     * LGPL encoder and is the always-present floor — MPEG-4 Part 2 in MP4 is
     * decodable by Android MediaCodec, so an intermediate written with it still
     * feeds back into the Media3 composition.
     */
    enum class H264Encoder(val ffmpegName: String, val isGpl: Boolean) {
        MEDIACODEC("h264_mediacodec", isGpl = false),
        X264("libx264", isGpl = true),
        MPEG4("mpeg4", isGpl = false),
    }

    /**
     * The encoder FFmpeg passes should use for an Android-decodable
     * intermediate, chosen from what the linked build actually provides.
     *
     * Probed once against `-encoders` rather than assumed, because the answer
     * changes with the vendored AAR: the locked LGPL profile reports
     * `h264_mediacodec` and has no `libx264`. Selecting explicitly means
     * swapping the AAR does not silently change (or break) what these passes
     * encode.
     */
    suspend fun preferredIntermediateEncoder(): H264Encoder {
        cachedEncoder?.let { return it }
        val available = availableEncoderNames()
        val chosen = H264Encoder.entries.firstOrNull { it.ffmpegName in available }
            ?: H264Encoder.MPEG4
        cachedEncoder = chosen
        if (chosen.isGpl) {
            Log.i(
                TAG,
                "Intermediate encoder is ${chosen.ffmpegName}: this build exposes no " +
                    "licence-neutral H.264 encoder. Rebuilding FFmpeg with --enable-mediacodec " +
                    "provides h264_mediacodec and drops the GPL dependency."
            )
        } else {
            Log.d(TAG, "Intermediate encoder: ${chosen.ffmpegName}")
        }
        return chosen
    }

    private suspend fun availableEncoderNames(): Set<String> {
        if (!isAvailable()) return emptySet()
        val output = runCatching { encoderListOutput() }.getOrNull() ?: return emptySet()
        // `-encoders` prints " V....D h264_mediacodec  <description>"; take the
        // second whitespace-separated token of each entry line.
        return output.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("V")) return@mapNotNull null
                trimmed.split(Regex("\\s+")).getOrNull(1)
            }
            .toSet()
    }

    @Volatile private var cachedEncoder: H264Encoder? = null

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
        val v = NativeProcessingPolicy.validateVideoUri(context, inputUri, "streamCopyTrim")
        if (v != null) return@withContext NativeProcessingPolicy.logAndReject(v)
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
        for (f in inputFiles) {
            val v = NativeProcessingPolicy.validateVideoFile(f, "concat")
            if (v != null) return@withContext NativeProcessingPolicy.logAndReject(v)
        }
        val listFile = File.createTempFile("clearcut-ffmpeg-concat-", ".txt", context.cacheDir)
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
                    val code = returnCodeValue(completed.getReturnCode())
                    if (code == 0) notifyProgress(onProgress, 1f)
                    if (code != 0) {
                        NativeProcessingPolicy.unsupportedNativeFailure(
                            completed.getOutput(),
                            "FFmpeg",
                        )?.let { NativeProcessingPolicy.logAndReject(it) }
                    }
                    if (continuation.isActive) continuation.resume(code)
                },
                { log ->
                    val message = log.message.trim()
                    if (message.isNotEmpty()) Log.v(TAG, message)
                },
                { stats ->
                    progressFromStats(stats.time, progressDurationMs)?.let { notifyProgress(onProgress, it) }
                }
            )
            continuation.invokeOnCancellation { session.cancel() }
        }
    }

    /**
     * Quality settings for an intermediate, per encoder family.
     *
     * `-crf` is an x264/x265 rate-control knob; the MediaCodec wrapper and the
     * native MPEG-4 encoder ignore it and need an explicit bitrate instead, so
     * passing one set of flags to all three would silently produce a low-bitrate
     * intermediate on a non-GPL build.
     */
    internal fun intermediateQualityArgs(encoder: H264Encoder): List<String> = when (encoder) {
        H264Encoder.X264 -> listOf("-preset", "fast", "-crf", "18")
        // High constant bitrate stands in for near-visually-lossless CRF 18.
        H264Encoder.MEDIACODEC -> listOf("-b:v", "20M")
        H264Encoder.MPEG4 -> listOf("-b:v", "24M")
    }

    /** Run `-encoders` and return its combined log output for capability probing. */
    private suspend fun encoderListOutput(): String {
        return suspendCancellableCoroutine { continuation ->
            val builder = StringBuilder()
            val session = FFmpegKit.executeWithArgumentsAsync(
                arrayOf("-hide_banner", "-encoders"),
                { _ ->
                    if (continuation.isActive) continuation.resume(builder.toString())
                },
                { log -> builder.append(log.message.orEmpty()) },
                { }
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
                    val code = returnCodeValue(completed.getReturnCode())
                    if (code == 0) notifyProgress(onProgress, 1f)
                    if (code != 0) {
                        NativeProcessingPolicy.unsupportedNativeFailure(
                            completed.getOutput(),
                            "FFmpeg",
                        )?.let { NativeProcessingPolicy.logAndReject(it) }
                    }
                    if (continuation.isActive) continuation.resume(code)
                },
                { log ->
                    val message = log.message.trim()
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

    private fun validateInputPath(
        inputUri: String,
        operation: String
    ): NativeProcessingPolicy.PolicyViolation? {
        val inputFile = File(inputUri)
        return if (inputFile.isFile) {
            NativeProcessingPolicy.validateVideoFile(inputFile, operation)
        } else {
            NativeProcessingPolicy.validateVideoPath(inputUri, operation)
        }
    }

    private fun mediaDurationMs(file: File): Long? {
        if (!file.exists()) return null
        // Duration probing will move to FFprobe once callers need precise
        // progress for every FFmpeg path. A null duration still gives
        // completion progress without risking slow preflight work.
        return null
    }

    internal fun escapeFilterPath(path: String): String = Companion.escapeFilterPath(path)
    internal fun escapeConcatPath(path: String): String = Companion.escapeConcatPath(path)
    internal fun msToSeconds(ms: Long): String = Companion.msToSeconds(ms)

    companion object {
        private const val TAG = "FFmpegEngine"

        fun escapeFilterPath(path: String): String {
            return path
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")
        }

        fun subtitleFilter(subtitlePath: String): String =
            "subtitles=${escapeFilterPath(subtitlePath)}:" +
                "fontsdir=${escapeFilterPath(CaptionFontFallbackPolicy.ANDROID_SYSTEM_FONT_DIRECTORY)}"

        fun escapeConcatPath(path: String): String = path.replace("'", "'\\''")

        fun msToSeconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)

        fun buildAtempoChain(speed: Float): String {
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
    }
}
