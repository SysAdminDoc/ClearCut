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
import kotlin.math.roundToInt
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
     * Decimated mono PCM plus the rate it actually landed on. Integer decimation
     * (max(1, source/target)) rarely hits the requested target exactly — 44100 Hz
     * decimated by 5 yields 8820 Hz — so offset math must use this rate, never the
     * requested one.
     */
    data class MonoPcm(
        val samples: FloatArray,
        val effectiveSampleRate: Int
    ) {
        override fun equals(other: Any?): Boolean =
            other is MonoPcm && effectiveSampleRate == other.effectiveSampleRate &&
                samples.contentEquals(other.samples)
        override fun hashCode(): Int = 31 * samples.contentHashCode() + effectiveSampleRate
    }

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
        val targetSampleRate = 8000 // Low rate for faster correlation
        val pcmA = extractMonoPcm(clipAUri, targetSampleRate)
        onProgress(0.3f)
        val pcmB = extractMonoPcm(clipBUri, targetSampleRate)
        onProgress(0.5f)

        if (pcmA.samples.isEmpty() || pcmB.samples.isEmpty()) {
            return@withContext SyncResult(0L, 0f, clipAUri, clipBUri)
        }

        // Mixed source rates decimate to different effective rates (e.g. 44100→8820
        // vs 48000→8000); correlating those directly compares time-stretched signals.
        // Resample the higher-rate signal down to the common (smaller) rate first.
        val commonRate = min(pcmA.effectiveSampleRate, pcmB.effectiveSampleRate)
        ensureActive()
        val samplesA = resampleLinear(pcmA.samples, pcmA.effectiveSampleRate, commonRate)
        ensureActive()
        val samplesB = resampleLinear(pcmB.samples, pcmB.effectiveSampleRate, commonRate)

        // Cross-correlate to find best offset (all sample math at the common rate)
        val maxOffsetSamples = (maxOffsetMs * commonRate / 1000).toInt()
        val searchRange = min(maxOffsetSamples, min(samplesA.size, samplesB.size) / 2)

        var bestOffset = 0
        var bestCorrelation = -1f
        var totalChecked = 0
        val totalToCheck = searchRange * 2 + 1

        // Normalize signals
        val normA = normalize(samplesA)
        val normB = normalize(samplesB)

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

        val offsetMs = syncOffsetMs(bestOffset, commonRate)
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

    private suspend fun extractMonoPcm(uri: Uri, targetSampleRate: Int): MonoPcm =
        withContext(Dispatchers.IO) {
            val empty = MonoPcm(FloatArray(0), targetSampleRate.coerceAtLeast(1))
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

                if (audioIndex < 0 || format == null) return@withContext empty

                extractor.selectTrack(audioIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: return@withContext empty
                val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                // Malformed or synthetic MediaFormats can report 0 channels; coerce
                // so the mono-mix divide below never produces Float.Inf / NaN.
                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

                // Guard against a caller passing 0 as targetSampleRate (would produce
                // ArithmeticException on integer division before max() runs).
                val safeTargetRate = targetSampleRate.coerceAtLeast(1)
                val decimation = max(1, sourceSampleRate / safeTargetRate)
                // Integer decimation lands on source/decimation, not the requested
                // target (44100/5 = 8820 Hz) — report it so callers convert
                // sample offsets to ms with the rate the samples are actually at.
                val effectiveRate = effectiveDecimatedRate(sourceSampleRate, safeTargetRate)

                val decoder = MediaCodec.createDecoderByType(mime)
                val samples = mutableListOf<Float>()

                try {
                    decoder.configure(format, null, null, 0)
                    decoder.start()

                    val bufferInfo = MediaCodec.BufferInfo()
                    var eos = false

                    while (!eos) {
                        ensureActive()
                        if (AudioDecodeBudget.exceedsBudget(samples.size, 1)) {
                            android.util.Log.w("MultiCamEngine", "Mono PCM exceeds the in-memory budget; stopping decode")
                            break
                        }
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

                MonoPcm(samples.toFloatArray(), effectiveRate)
            } catch (e: Exception) {
                Log.e(TAG, "PCM extraction failed for $uri", e)
                empty
            } finally {
                extractor.release()
            }
        }
}

/**
 * Rate the mono fingerprint actually lands on after integer decimation of
 * [sourceRate] toward [targetRate]: source / max(1, source/target). Pure so the
 * decimation contract (44100→8000 request yields 8820 Hz) is unit-testable.
 */
internal fun effectiveDecimatedRate(sourceRate: Int, targetRate: Int): Int {
    val safeSource = sourceRate.coerceAtLeast(1)
    val safeTarget = targetRate.coerceAtLeast(1)
    return safeSource / max(1, safeSource / safeTarget)
}

/**
 * Convert a best-correlation sample offset to milliseconds at the effective rate
 * the correlated signals share. Dividing by the *requested* target rate instead
 * (the old behaviour) skewed a 44100 Hz pair's offset by ~10% (8820 vs 8000).
 */
internal fun syncOffsetMs(offsetSamples: Int, effectiveSampleRate: Int): Long {
    if (effectiveSampleRate <= 0) return 0L
    return offsetSamples.toLong() * 1000 / effectiveSampleRate
}

/**
 * Linear-interpolation resample of [input] from [srcRate] to [dstRate]. Identity
 * when the rates match or are non-positive. Pure and allocation-bounded: the
 * output is at most `input.size` samples because callers only downsample to the
 * common (smaller) effective rate.
 */
internal fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
    if (srcRate == dstRate || srcRate <= 0 || dstRate <= 0) return input
    val ratio = dstRate.toDouble() / srcRate.toDouble()
    // roundToInt (not toInt) so e.g. 8820 * (8000/8820) can't truncate to 7999.
    val outLen = (input.size * ratio).roundToInt()
    if (outLen <= 0) return FloatArray(0)
    val output = FloatArray(outLen)
    for (i in 0 until outLen) {
        val srcPos = i / ratio
        val srcIdx = srcPos.toInt()
        val frac = (srcPos - srcIdx).toFloat()
        val s0 = input.getOrElse(srcIdx) { 0f }
        val s1 = input.getOrElse(srcIdx + 1) { s0 }
        output[i] = s0 + frac * (s1 - s0)
    }
    return output
}
