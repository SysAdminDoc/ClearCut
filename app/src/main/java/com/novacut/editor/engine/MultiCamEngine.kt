package com.novacut.editor.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val TAG = "MultiCamEngine"

/**
 * Multi-camera sync engine. Aligns clips by cross-correlating their audio waveforms.
 * Finds the time offset between two clips so they play in sync.
 */
@Singleton
class MultiCamEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class SyncResult(
        val offsetMs: Long,
        val confidence: Float,
        val clipAUri: Uri,
        val clipBUri: Uri
    )

    /**
     * Find the sync offset between two clips by audio cross-correlation.
     * Returns the offset in ms that clipB should be shifted relative to clipA.
     * Positive = clipB starts after clipA, negative = clipB starts before.
     */
    suspend fun findSyncOffset(
        clipAUri: Uri,
        clipBUri: Uri,
        maxOffsetMs: Long = 30_000,
        onProgress: (Float) -> Unit = {}
    ): SyncResult = withContext(Dispatchers.Default) {
        onProgress(0.1f)

        // Extract audio fingerprints (downsampled mono PCM)
        val sampleRate = 8000 // Low rate for faster correlation
        val pcmA = extractMonoPcm(clipAUri, sampleRate)
        onProgress(0.3f)
        val pcmB = extractMonoPcm(clipBUri, sampleRate)
        onProgress(0.5f)

        if (pcmA.isEmpty() || pcmB.isEmpty()) {
            return@withContext SyncResult(0L, 0f, clipAUri, clipBUri)
        }

        // Cross-correlate to find best offset
        val maxOffsetSamples = (maxOffsetMs * sampleRate / 1000).toInt()
        val searchRange = min(maxOffsetSamples, min(pcmA.size, pcmB.size) / 2)

        var bestOffset = 0
        var bestCorrelation = -1f
        var totalChecked = 0
        val totalToCheck = searchRange * 2 + 1

        // Normalize signals
        val normA = normalize(pcmA)
        val normB = normalize(pcmB)

        for (offset in -searchRange..searchRange) {
            ensureActive()

            val correlation = crossCorrelation(normA, normB, offset)
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestOffset = offset
            }

            totalChecked++
            if (totalChecked % 100 == 0) {
                onProgress(0.5f + 0.4f * totalChecked / totalToCheck)
            }
        }

        onProgress(1f)

        val offsetMs = bestOffset.toLong() * 1000 / sampleRate
        Log.d(TAG, "Sync result: offset=${offsetMs}ms, confidence=$bestCorrelation")

        SyncResult(offsetMs, bestCorrelation, clipAUri, clipBUri)
    }

    /**
     * Sync multiple clips to a reference clip.
     */
    suspend fun syncMultipleClips(
        referenceUri: Uri,
        clipUris: List<Uri>,
        onProgress: (Float) -> Unit = {}
    ): List<SyncResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<SyncResult>()
        for ((index, clipUri) in clipUris.withIndex()) {
            val result = findSyncOffset(referenceUri, clipUri) { p ->
                onProgress((index + p) / clipUris.size)
            }
            results.add(result)
        }
        results
    }

    private fun crossCorrelation(a: FloatArray, b: FloatArray, offset: Int): Float {
        var sum = 0f
        var count = 0
        val startA = max(0, offset)
        val startB = max(0, -offset)
        val length = min(a.size - startA, b.size - startB)

        if (length <= 0) return 0f

        for (i in 0 until length) {
            sum += a[startA + i] * b[startB + i]
            count++
        }

        return if (count > 0) sum / count else 0f
    }

    private fun normalize(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val mean = samples.average().toFloat()
        val centered = FloatArray(samples.size) { samples[it] - mean }
        var sumSq = 0.0
        for (v in centered) sumSq += v.toDouble() * v
        val rms = sqrt((sumSq / centered.size).toFloat())
        return if (rms > 1e-6f) FloatArray(centered.size) { centered[it] / rms } else centered
    }

    private suspend fun extractMonoPcm(uri: Uri, targetSampleRate: Int): FloatArray =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                var audioIndex = -1
                var format: MediaFormat? = null

                for (i in 0 until extractor.trackCount) {
                    val tf = extractor.getTrackFormat(i)
                    if (tf.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        audioIndex = i
                        format = tf
                        break
                    }
                }

                if (audioIndex < 0 || format == null) return@withContext FloatArray(0)

                extractor.selectTrack(audioIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: return@withContext FloatArray(0)
                val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                val decoder = MediaCodec.createDecoderByType(mime)
                val samples = mutableListOf<Float>()

                try {
                    decoder.configure(format, null, null, 0)
                    decoder.start()

                    val bufferInfo = MediaCodec.BufferInfo()
                    var eos = false
                    val decimation = max(1, sourceSampleRate / targetSampleRate)

                    while (!eos) {
                        val inIdx = decoder.dequeueInputBuffer(10000)
                        if (inIdx >= 0) {
                            val buf = decoder.getInputBuffer(inIdx) ?: continue
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(
                                    inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                eos = true
                            } else {
                                decoder.queueInputBuffer(
                                    inIdx, 0, size, extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }

                        var outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                        while (outIdx >= 0) {
                            val outBuf = decoder.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                val shortBuf =
                                    outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val arr = ShortArray(shortBuf.remaining())
                                shortBuf.get(arr)

                                // Downsample + mono mix
                                var i = 0
                                while (i < arr.size) {
                                    var mono = 0f
                                    for (ch in 0 until min(channels, arr.size - i)) {
                                        mono += arr[i + ch].toFloat() / 32768f
                                    }
                                    mono /= channels
                                    samples.add(mono)
                                    i += channels * decimation
                                }
                            }
                            decoder.releaseOutputBuffer(outIdx, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                eos = true
                                break
                            }
                            outIdx = decoder.dequeueOutputBuffer(bufferInfo, 0)
                        }
                    }
                } finally {
                    try {
                        decoder.stop()
                    } catch (e: Exception) {
                        Log.w("MultiCamEngine", "Failed to stop decoder", e)
                    }
                    decoder.release()
                }

                samples.toFloatArray()
            } catch (e: Exception) {
                Log.e(TAG, "PCM extraction failed for $uri", e)
                FloatArray(0)
            } finally {
                extractor.release()
            }
        }
}
