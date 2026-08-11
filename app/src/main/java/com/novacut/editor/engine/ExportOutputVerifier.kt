package com.novacut.editor.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import com.novacut.editor.engine.AppLog
import java.io.File
import java.io.RandomAccessFile

enum class ExportContainer {
    MP4,
    WEBM,
    UNKNOWN,
}

data class ExportVerificationResult(
    val valid: Boolean,
    val reason: String? = null,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val trackCount: Int = 0,
    val videoMimeType: String? = null,
    val audioMimeType: String? = null,
    val frameRate: Float = 0f,
    val container: ExportContainer = ExportContainer.UNKNOWN,
    val fastStart: Boolean = false,
)

class ExportVerificationException(
    val verification: ExportVerificationResult,
) : IllegalStateException(verification.reason ?: "Export output contract rejected the artifact")

/** Return a failure only when an encoded artifact is materially shorter than requested. */
internal fun outputDurationFailureReason(
    expectedDurationMs: Long,
    actualDurationMs: Long,
    durationToleranceMs: Long,
): String? {
    if (expectedDurationMs <= 0L) return null
    val tolerance = durationToleranceMs.coerceAtLeast(0L)
    val minimumDurationMs = if (tolerance >= expectedDurationMs) {
        1L
    } else {
        expectedDurationMs - tolerance
    }
    return if (actualDurationMs < minimumDurationMs) {
        "Output is shorter than expected (${actualDurationMs}ms vs ${expectedDurationMs}ms)"
    } else {
        null
    }
}

object ExportOutputVerifier {

    private const val TAG = "ExportOutputVerifier"
    private const val FRAME_RATE_TOLERANCE = 0.5f

    fun verify(
        outputFile: File,
        expectVideo: Boolean = true,
        expectAudio: Boolean = false,
        expectedDurationMs: Long = 0L,
        durationToleranceMs: Long = 2000L,
        expectedVideoMimeType: String? = null,
        expectedAudioMimeType: String? = null,
        expectedVideoWidth: Int? = null,
        expectedVideoHeight: Int? = null,
        expectedFrameRate: Float? = null,
        expectedContainer: ExportContainer? = null,
        requireFastStart: Boolean = false,
    ): ExportVerificationResult {
        if (!outputFile.exists()) {
            return ExportVerificationResult(false, reason = "Output file does not exist")
        }
        if (outputFile.length() <= 0L) {
            return ExportVerificationResult(false, reason = "Output file is empty")
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(outputFile.absolutePath)
            val trackCount = extractor.trackCount
            if (trackCount <= 0) {
                return ExportVerificationResult(
                    false,
                    reason = "Output file has no media tracks",
                    trackCount = 0
                )
            }

            var hasVideo = false
            var hasAudio = false
            var maxDurationUs = 0L
            var width = 0
            var height = 0
            var videoMimeType: String? = null
            var audioMimeType: String? = null
            var frameRate = 0f

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mime.startsWith("video/") -> {
                        hasVideo = true
                        videoMimeType = mime
                        width = format.getIntSafe(MediaFormat.KEY_WIDTH)
                        height = format.getIntSafe(MediaFormat.KEY_HEIGHT)
                        frameRate = format.getFloatSafe(MediaFormat.KEY_FRAME_RATE)
                        val dur = format.getLongSafe(MediaFormat.KEY_DURATION)
                        if (dur > maxDurationUs) maxDurationUs = dur
                    }
                    mime.startsWith("audio/") -> {
                        hasAudio = true
                        audioMimeType = mime
                        val dur = format.getLongSafe(MediaFormat.KEY_DURATION)
                        if (dur > maxDurationUs) maxDurationUs = dur
                    }
                }
            }

            val durationMs = maxDurationUs / 1000L
            val container = detectExportContainer(outputFile)
            val fastStart = container == ExportContainer.MP4 && hasFastStartMp4Layout(outputFile)

            if (expectVideo && !hasVideo) {
                return ExportVerificationResult(
                    false,
                    reason = "Expected video track but output has none",
                    hasVideo = false, hasAudio = hasAudio,
                    durationMs = durationMs, width = width, height = height,
                    trackCount = trackCount
                )
            }

            // An audio-only export that still carries a video track is a broken
            // artifact (the timeline's picture leaked into an "audio only" file).
            // Checked before the missing-audio guard so a mislabelled video is
            // reported as the video leak it is. Fail closed so the caller deletes
            // it instead of publishing a video mislabelled as audio.
            if (!expectVideo && hasVideo) {
                return ExportVerificationResult(
                    false,
                    reason = "Audio-only export unexpectedly contains a video track",
                    hasVideo = true, hasAudio = hasAudio,
                    durationMs = durationMs, width = width, height = height,
                    trackCount = trackCount
                )
            }

            if (expectAudio && !hasAudio) {
                return ExportVerificationResult(
                    false,
                    reason = "Expected audio track but output has none",
                    hasVideo = hasVideo, hasAudio = false,
                    durationMs = durationMs, width = width, height = height,
                    trackCount = trackCount
                )
            }

            if (durationMs <= 0L) {
                return ExportVerificationResult(
                    false,
                    reason = "Output has zero duration",
                    hasVideo = hasVideo, hasAudio = hasAudio,
                    durationMs = 0L, width = width, height = height,
                    trackCount = trackCount,
                    videoMimeType = videoMimeType,
                    audioMimeType = audioMimeType,
                    frameRate = frameRate,
                    container = container,
                    fastStart = fastStart,
                )
            }

            outputDurationFailureReason(
                expectedDurationMs = expectedDurationMs,
                actualDurationMs = durationMs,
                durationToleranceMs = durationToleranceMs,
            )?.let { reason ->
                return ExportVerificationResult(
                    false,
                    reason = reason,
                    hasVideo = hasVideo,
                    hasAudio = hasAudio,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    trackCount = trackCount,
                    videoMimeType = videoMimeType,
                    audioMimeType = audioMimeType,
                    frameRate = frameRate,
                    container = container,
                    fastStart = fastStart,
                )
            }

            val contractMismatch = when {
                expectedVideoMimeType != null && videoMimeType != expectedVideoMimeType ->
                    "Video codec mismatch (requested $expectedVideoMimeType, actual ${videoMimeType ?: "none"})"
                expectedAudioMimeType != null && (expectAudio || hasAudio) && audioMimeType != expectedAudioMimeType ->
                    "Audio codec mismatch (requested $expectedAudioMimeType, actual ${audioMimeType ?: "none"})"
                expectedVideoWidth != null && width != expectedVideoWidth ->
                    "Video width mismatch (requested $expectedVideoWidth, actual $width)"
                expectedVideoHeight != null && height != expectedVideoHeight ->
                    "Video height mismatch (requested $expectedVideoHeight, actual $height)"
                expectedFrameRate != null && frameRate > 0f && kotlin.math.abs(frameRate - expectedFrameRate) > FRAME_RATE_TOLERANCE ->
                    "Video frame-rate mismatch (requested ${expectedFrameRate}fps, actual ${frameRate}fps)"
                expectedFrameRate != null && frameRate <= 0f ->
                    "Video frame rate is missing from the output"
                expectedContainer != null && container != expectedContainer ->
                    "Container mismatch (requested $expectedContainer, actual $container)"
                requireFastStart && container == ExportContainer.MP4 && !fastStart ->
                    "Output is not fast-start compatible"
                else -> null
            }
            if (contractMismatch != null) {
                return ExportVerificationResult(
                    false,
                    reason = contractMismatch,
                    hasVideo = hasVideo,
                    hasAudio = hasAudio,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    trackCount = trackCount,
                    videoMimeType = videoMimeType,
                    audioMimeType = audioMimeType,
                    frameRate = frameRate,
                    container = container,
                    fastStart = fastStart,
                )
            }

            if (expectedDurationMs > 0L && durationToleranceMs > 0L) {
                val drift = kotlin.math.abs(durationMs - expectedDurationMs)
                if (drift > durationToleranceMs && drift > expectedDurationMs / 2) {
                    AppLog.w(TAG, "Duration drift: expected ${expectedDurationMs}ms, got ${durationMs}ms (drift ${drift}ms)")
                }
            }

            return ExportVerificationResult(
                valid = true,
                hasVideo = hasVideo,
                hasAudio = hasAudio,
                durationMs = durationMs,
                width = width,
                height = height,
                trackCount = trackCount,
                videoMimeType = videoMimeType,
                audioMimeType = audioMimeType,
                frameRate = frameRate,
                container = container,
                fastStart = fastStart,
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "Verification failed for ${outputFile.redacted()}", e)
            return ExportVerificationResult(
                false,
                reason = "Cannot read output: ${e.javaClass.simpleName}: ${e.message}"
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.getIntSafe(key: String): Int {
        return try { getInteger(key) } catch (_: Exception) { 0 }
    }

    private fun MediaFormat.getFloatSafe(key: String): Float {
        return try { getFloat(key) } catch (_: Exception) {
            getIntSafe(key).toFloat()
        }
    }

    private fun MediaFormat.getLongSafe(key: String): Long {
        return try { getLong(key) } catch (_: Exception) { 0L }
    }
}

internal fun expectedContainerForExtension(extension: String): ExportContainer = when {
    extension.equals("mp4", ignoreCase = true) || extension.equals("m4a", ignoreCase = true) ->
        ExportContainer.MP4
    extension.equals("webm", ignoreCase = true) -> ExportContainer.WEBM
    else -> ExportContainer.UNKNOWN
}

internal fun detectExportContainer(file: File): ExportContainer {
    return runCatching {
        RandomAccessFile(file, "r").use { randomAccessFile ->
            if (randomAccessFile.length() >= WEBM_MAGIC.size) {
                val header = ByteArray(WEBM_MAGIC.size)
                randomAccessFile.readFully(header)
                if (header.contentEquals(WEBM_MAGIC)) return@use ExportContainer.WEBM
            }
            val layout = readMp4Layout(randomAccessFile)
            if (layout.hasMp4Atoms) ExportContainer.MP4 else ExportContainer.UNKNOWN
        }
    }.getOrDefault(ExportContainer.UNKNOWN)
}

internal fun hasFastStartMp4Layout(file: File): Boolean {
    return runCatching {
        RandomAccessFile(file, "r").use { randomAccessFile ->
            val layout = readMp4Layout(randomAccessFile)
            layout.moovOffset >= 0L && layout.mdatOffset >= 0L && layout.moovOffset < layout.mdatOffset
        }
    }.getOrDefault(false)
}

private data class Mp4Layout(
    val hasMp4Atoms: Boolean,
    val moovOffset: Long,
    val mdatOffset: Long,
)

private fun readMp4Layout(file: RandomAccessFile): Mp4Layout {
    val fileLength = file.length()
    var offset = 0L
    var hasMp4Atoms = false
    var moovOffset = -1L
    var mdatOffset = -1L
    var atomCount = 0

    while (offset + 8L <= fileLength && atomCount++ < MAX_TOP_LEVEL_ATOMS) {
        file.seek(offset)
        val declaredSize = file.readInt().toLong() and 0xffffffffL
        val typeBytes = ByteArray(4)
        file.readFully(typeBytes)
        val type = String(typeBytes, Charsets.US_ASCII)
        val headerSize: Long
        val atomSize: Long
        if (declaredSize == 1L) {
            if (offset + 16L > fileLength) break
            atomSize = file.readLong()
            headerSize = 16L
        } else {
            atomSize = if (declaredSize == 0L) fileLength - offset else declaredSize
            headerSize = 8L
        }
        if (atomSize < headerSize || atomSize > fileLength - offset) break

        when (type) {
            "ftyp", "styp", "moov", "mdat", "free", "wide" -> hasMp4Atoms = true
        }
        if (type == "moov" && moovOffset < 0L) moovOffset = offset
        if (type == "mdat" && mdatOffset < 0L) mdatOffset = offset
        offset += atomSize
    }

    return Mp4Layout(hasMp4Atoms, moovOffset, mdatOffset)
}

private val WEBM_MAGIC = byteArrayOf(0x1a, 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte())
private const val MAX_TOP_LEVEL_ATOMS = 1024
