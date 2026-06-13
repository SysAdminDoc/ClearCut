package com.novacut.editor.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File

data class ExportVerificationResult(
    val valid: Boolean,
    val reason: String? = null,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val trackCount: Int = 0
)

object ExportOutputVerifier {

    private const val TAG = "ExportOutputVerifier"

    fun verify(
        outputFile: File,
        expectVideo: Boolean = true,
        expectAudio: Boolean = false,
        expectedDurationMs: Long = 0L,
        durationToleranceMs: Long = 2000L
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

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mime.startsWith("video/") -> {
                        hasVideo = true
                        width = format.getIntSafe(MediaFormat.KEY_WIDTH)
                        height = format.getIntSafe(MediaFormat.KEY_HEIGHT)
                        val dur = format.getLongSafe(MediaFormat.KEY_DURATION)
                        if (dur > maxDurationUs) maxDurationUs = dur
                    }
                    mime.startsWith("audio/") -> {
                        hasAudio = true
                        val dur = format.getLongSafe(MediaFormat.KEY_DURATION)
                        if (dur > maxDurationUs) maxDurationUs = dur
                    }
                }
            }

            val durationMs = maxDurationUs / 1000L

            if (expectVideo && !hasVideo) {
                return ExportVerificationResult(
                    false,
                    reason = "Expected video track but output has none",
                    hasVideo = false, hasAudio = hasAudio,
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
                    trackCount = trackCount
                )
            }

            if (expectedDurationMs > 0L && durationToleranceMs > 0L) {
                val drift = kotlin.math.abs(durationMs - expectedDurationMs)
                if (drift > durationToleranceMs && drift > expectedDurationMs / 2) {
                    Log.w(TAG, "Duration drift: expected ${expectedDurationMs}ms, got ${durationMs}ms (drift ${drift}ms)")
                }
            }

            return ExportVerificationResult(
                valid = true,
                hasVideo = hasVideo,
                hasAudio = hasAudio,
                durationMs = durationMs,
                width = width,
                height = height,
                trackCount = trackCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Verification failed for ${outputFile.name}", e)
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

    private fun MediaFormat.getLongSafe(key: String): Long {
        return try { getLong(key) } catch (_: Exception) { 0L }
    }
}
