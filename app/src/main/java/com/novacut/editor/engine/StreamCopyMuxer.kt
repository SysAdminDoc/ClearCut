package com.novacut.editor.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Stream-copy trim using Android's built-in [MediaExtractor] + [MediaMuxer].
 *
 * Two public entry points:
 *   * [trim] — single `startMs..endMs` window from a single source. Classic
 *     LosslessCut fast-trim.
 *   * [concat] — multiple windows from the **same** source muxed into a
 *     single output file, end-to-end. Matches the multi-clip-same-source
 *     case where a creator has sliced a single recording into keepers.
 *     Assumes all ranges share the same source codec/resolution/sample-rate —
 *     StreamCopyExportEngine is responsible for enforcing that precondition.
 *
 * Sample packets are never decoded; the work is ~filesystem-bound. ~50×
 * faster than Transformer on eligible timelines.
 *
 * Keyframe caveat: `MediaExtractor.seekTo(SEEK_TO_PREVIOUS_SYNC)` snaps the
 * start of every range to the nearest keyframe at or before the requested
 * time. On sparse-GOP sources the trim can land up to one GOP earlier than
 * requested — documented in the export UI when eligibility is announced.
 */
@Singleton
class StreamCopyMuxer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** A single sample-time window inside one source. */
    data class Range(val startMs: Long, val endMs: Long)

    suspend fun trim(
        inputUri: Uri,
        startMs: Long,
        endMs: Long,
        outputPath: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = concat(inputUri, listOf(Range(startMs, endMs)), outputPath, onProgress)

    /**
     * Mux a list of non-overlapping time windows from `inputUri` into a
     * single output file, preserving the original's sample codecs verbatim.
     * Ranges are applied in order; each per-track cursor advances by the
     * MAX presentation time actually written to that track inside the
     * previous range (not by the nominal range duration) so keyframe-snap
     * pre-roll cannot produce overlapping, non-monotonic timestamps that
     * MediaMuxer would reject.
     */
    suspend fun concat(
        inputUri: Uri,
        ranges: List<Range>,
        outputPath: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val windows = ranges
            .filter { it.endMs > it.startMs }
            .sortedBy { it.startMs }
        if (windows.isEmpty()) {
            Log.w(TAG, "no non-empty ranges")
            return@withContext false
        }
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        val trackMap = HashMap<Int, Int>() // src track index → dst track index
        try {
            extractor.setDataSource(context, inputUri, null)
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var maxSampleSize = 0
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (!(mime.startsWith("video/") || mime.startsWith("audio/"))) continue
                val dstIdx = muxer.addTrack(fmt)
                trackMap[i] = dstIdx
                val size = fmt.safeInt(MediaFormat.KEY_MAX_INPUT_SIZE, 1_048_576)
                if (size > maxSampleSize) maxSampleSize = size
            }
            if (trackMap.isEmpty()) {
                Log.w(TAG, "no audio/video tracks in $inputUri")
                return@withContext false
            }
            if (maxSampleSize <= 0) maxSampleSize = 1_048_576

            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(maxSampleSize)
            val info = MediaCodec.BufferInfo()
            val totalWindowUs = windows.sumOf { (it.endMs - it.startMs) * 1_000L }
                .coerceAtLeast(1L)

            // Progress is a weighted sum across all tracks — video dominates
            // the byte budget but we advance the counter from each track's
            // writes so a quick audio loop doesn't leave the bar stuck at
            // "almost done" while video is still copying.
            var writtenTotalUs = 0L

            for ((srcIdx, dstIdx) in trackMap) {
                coroutineContext.ensureActive()
                extractor.selectTrack(srcIdx)
                var outCursorUs = 0L
                for (window in windows) {
                    coroutineContext.ensureActive()
                    val startUs = window.startMs * 1_000L
                    val endUs = window.endMs * 1_000L
                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val seekedStartUs = extractor.sampleTime.coerceAtLeast(0L)
                    var maxEmittedWithinRangeUs = -1L
                    while (true) {
                        coroutineContext.ensureActive()
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val srcTime = extractor.sampleTime
                        if (srcTime > endUs) break
                        if (srcTime < 0L) { extractor.advance(); continue }
                        val withinRangeUs = (srcTime - seekedStartUs).coerceAtLeast(0L)
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = outCursorUs + withinRangeUs
                        info.flags = extractor.sampleFlagsForMuxer()
                        muxer.writeSampleData(dstIdx, buffer, info)
                        if (withinRangeUs > maxEmittedWithinRangeUs) {
                            maxEmittedWithinRangeUs = withinRangeUs
                        }
                        extractor.advance()
                    }
                    // Advance the cursor by what we *actually* wrote to this
                    // track (plus a minimal step) so the next range's first
                    // sample is strictly after the last sample from the
                    // previous range. The previous implementation advanced by
                    // the nominal `endMs - startMs`, which produced
                    // overlapping timestamps whenever keyframe snap gave us
                    // pre-roll — MediaMuxer rejects non-monotonic samples.
                    val actualWrittenUs = (maxEmittedWithinRangeUs + 1L).coerceAtLeast(0L)
                    outCursorUs += actualWrittenUs
                    writtenTotalUs += actualWrittenUs
                    onProgress((writtenTotalUs.toDouble() / totalWindowUs).toFloat().coerceIn(0f, 1f))
                }
                extractor.unselectTrack(srcIdx)
            }
            onProgress(1f)
            true
        } catch (e: CancellationException) {
            // Surface cancellation to the caller so it can tell the difference
            // between "user cancelled" and "mux failed" — the fallback path in
            // ExportDelegate would otherwise try Transformer when the user
            // actually asked to stop.
            runCatching { outputFile.delete() }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "stream-copy failed for $inputUri", e)
            runCatching { outputFile.delete() }
            false
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.safeInt(key: String, default: Int): Int =
        try { if (containsKey(key)) getInteger(key) else default } catch (_: Exception) { default }

    private fun MediaExtractor.sampleFlagsForMuxer(): Int {
        var out = 0
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            out = out or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            out = out or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return out
    }

    companion object { private const val TAG = "StreamCopyMuxer" }
}
