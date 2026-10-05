package com.novacut.editor.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.novacut.editor.engine.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
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

        // Extract audio fingerprints (downsampled mono PCM). Within the search range
        // the clips overlap for at least the last OVERLAP_SECONDS of each fingerprint,
        // so nothing past that is read or correlated.
        val targetSampleRate = 8000 // Low rate for faster correlation
        val fingerprintSeconds = (maxOffsetMs / 1000L + OVERLAP_SECONDS).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val pcmA = extractMonoPcm(clipAUri, targetSampleRate, fingerprintSeconds)
        onProgress(0.3f)
        val pcmB = extractMonoPcm(clipBUri, targetSampleRate, fingerprintSeconds)
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
        val maxOffsetSamples = (maxOffsetMs * commonRate / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val match = AudioSyncCorrelator.bestLag(normalize(samplesA), normalize(samplesB), maxOffsetSamples) {
            ensureActive()
        }
        onProgress(1f)

        val offsetMs = syncOffsetMs(match.lagSamples, commonRate)
        AppLog.d(TAG, "Sync result: offset=${offsetMs}ms, confidence=${match.score}")

        SyncResult(offsetMs, match.score, clipAUri, clipBUri)
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

    private fun normalize(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val mean = samples.average().toFloat()
        val centered = FloatArray(samples.size) { samples[it] - mean }
        var sumSq = 0.0
        for (v in centered) sumSq += v.toDouble() * v
        val rms = sqrt((sumSq / centered.size).toFloat())
        return if (rms > 1e-6f) FloatArray(centered.size) { centered[it] / rms } else centered
    }

    private suspend fun extractMonoPcm(uri: Uri, targetSampleRate: Int, maxSeconds: Int): MonoPcm =
        withContext(Dispatchers.IO) {
            // Guard against a caller passing 0 as targetSampleRate (would produce
            // ArithmeticException on integer division).
            val safeTargetRate = targetSampleRate.coerceAtLeast(1)
            val empty = MonoPcm(FloatArray(0), safeTargetRate)
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

                val decoderLease = CodecInstanceBudget.acquireDecoder(mime)
                val decoder = decoderLease.resource
                // Sized from the decoder's output format, which can differ from the
                // container's (HE-AAC decodes at twice its stored rate).
                var mixer: MonoDecimator? = null

                try {
                    decoder.configure(format, null, null, 0)
                    decoder.start()

                    val bufferInfo = MediaCodec.BufferInfo()
                    var inputDone = false
                    var eos = false

                    // Runs until the decoder's own end of stream, so the tail it still
                    // holds when the input runs out isn't lost.
                    while (!eos && mixer?.isFull != true) {
                        ensureActive()
                        val inIdx = if (inputDone) -1 else decoder.dequeueInputBuffer(10000)
                        if (inIdx >= 0) {
                            val buf = decoder.getInputBuffer(inIdx) ?: continue
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(
                                    inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
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
                                val active = mixer ?: MonoDecimator.forFormats(
                                    output = decoder.outputFormat,
                                    input = format,
                                    targetSampleRate = safeTargetRate,
                                    maxSeconds = maxSeconds,
                                ).also { mixer = it }
                                active.add(readPcmSamples(outBuf, bufferInfo))
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
                    decoderLease.close()
                }

                mixer?.result() ?: empty
            } catch (e: Exception) {
                AppLog.e(TAG, "PCM extraction failed for ${uri.redacted()}", e)
                empty
            } finally {
                extractor.release()
            }
        }

    private companion object {
        /** Seconds of audio the clips are sure to share inside the search range. */
        const val OVERLAP_SECONDS = 90L
    }
}

/**
 * Mixes interleaved 16-bit PCM to mono and averages every [decimation] frames into one
 * sample, a box low-pass so the lower rate doesn't fold high frequencies back in. Keeps
 * at most [maxSamples], and frames split across buffers carry over.
 */
internal class MonoDecimator(
    private val channels: Int,
    private val decimation: Int,
    private val effectiveSampleRate: Int,
    private val maxSamples: Int,
) {
    private var samples = FloatArray(min(maxSamples, 1 shl 16).coerceAtLeast(1))
    private var size = 0
    private var channel = 0
    private var frameSum = 0.0
    private var blockSum = 0.0
    private var blockFrames = 0

    val isFull: Boolean get() = size >= maxSamples

    fun add(pcm: ShortArray) {
        for (value in pcm) {
            if (isFull) return
            frameSum += value / 32768.0
            if (++channel < channels) continue
            channel = 0
            blockSum += frameSum / channels
            frameSum = 0.0
            if (++blockFrames < decimation) continue
            if (size == samples.size) samples = samples.copyOf(min(maxSamples, samples.size * 2))
            samples[size++] = (blockSum / decimation).toFloat()
            blockSum = 0.0
            blockFrames = 0
        }
    }

    fun result(): MultiCamEngine.MonoPcm = MultiCamEngine.MonoPcm(samples.copyOf(size), effectiveSampleRate)

    companion object {
        fun forFormats(output: MediaFormat?, input: MediaFormat, targetSampleRate: Int, maxSeconds: Int): MonoDecimator {
            fun MediaFormat?.int(key: String): Int? =
                this?.takeIf { it.containsKey(key) }?.getInteger(key)?.takeIf { it > 0 }
            val sourceRate = output.int(MediaFormat.KEY_SAMPLE_RATE) ?: input.int(MediaFormat.KEY_SAMPLE_RATE) ?: 48_000
            // Malformed or synthetic MediaFormats can report 0 channels.
            val channels = output.int(MediaFormat.KEY_CHANNEL_COUNT) ?: input.int(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
            return create(sourceRate, channels, targetSampleRate, maxSeconds)
        }

        fun create(sourceRate: Int, channels: Int, targetSampleRate: Int, maxSeconds: Int): MonoDecimator {
            val effectiveRate = effectiveDecimatedRate(sourceRate, targetSampleRate)
            return MonoDecimator(
                channels = channels.coerceAtLeast(1),
                decimation = max(1, sourceRate.coerceAtLeast(1) / targetSampleRate.coerceAtLeast(1)),
                effectiveSampleRate = effectiveRate,
                maxSamples = (effectiveRate.toLong() * maxSeconds.coerceAtLeast(1))
                    .coerceAtMost(AudioDecodeBudget.MAX_PCM_SAMPLES.toLong()).toInt(),
            )
        }
    }
}

/**
 * The lag that best lines [b] up with [a], by cross-correlation through the FFT. The
 * score at each lag is the mean product over the overlap, so for zero-mean, unit-RMS
 * signals it reads like a correlation coefficient. A positive lag means [b]'s first
 * sample matches [a] at that lag: b started recording later. Lags are searched up to
 * [maxLagSamples] and never past half the shorter signal.
 */
internal object AudioSyncCorrelator {
    data class Match(val lagSamples: Int, val score: Float)

    fun bestLag(a: FloatArray, b: FloatArray, maxLagSamples: Int, checkpoint: () -> Unit = {}): Match {
        if (a.isEmpty() || b.isEmpty()) return Match(0, 0f)
        val range = min(maxLagSamples, min(a.size, b.size) / 2).coerceAtLeast(0)
        var n = 1
        while (n < a.size + b.size) n = n shl 1
        val aRe = a.copyOf(n)
        val aIm = FloatArray(n)
        val bRe = b.copyOf(n)
        val bIm = FloatArray(n)
        fft(aRe, aIm, inverse = false, checkpoint)
        fft(bRe, bIm, inverse = false, checkpoint)
        // A times conj(B): its inverse transform at m is the sum of a[i + m] * b[i].
        for (k in 0 until n) {
            val re = aRe[k] * bRe[k] + aIm[k] * bIm[k]
            val im = aIm[k] * bRe[k] - aRe[k] * bIm[k]
            aRe[k] = re
            aIm[k] = im
        }
        fft(aRe, aIm, inverse = true, checkpoint)

        var best = Match(0, -1f)
        for (lag in -range..range) {
            val overlap = min(a.size - max(0, lag), b.size - max(0, -lag))
            if (overlap <= 0) continue
            val score = aRe[if (lag >= 0) lag else n + lag] / n / overlap
            if (score > best.score) best = Match(lag, score)
        }
        return best
    }

    /** In-place radix-2 FFT; the inverse is left unscaled. */
    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean, checkpoint: () -> Unit) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var len = 2
        while (len <= n) {
            checkpoint()
            val half = len / 2
            val stepRe = cos(sign * 2 * PI / len)
            val stepIm = sin(sign * 2 * PI / len)
            for (start in 0 until n step len) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until half) {
                    val upper = start + k
                    val lower = upper + half
                    val tRe = (re[lower] * wRe - im[lower] * wIm).toFloat()
                    val tIm = (re[lower] * wIm + im[lower] * wRe).toFloat()
                    re[lower] = re[upper] - tRe
                    im[lower] = im[upper] - tIm
                    re[upper] += tRe
                    im[upper] += tIm
                    val nextRe = wRe * stepRe - wIm * stepIm
                    wIm = wRe * stepIm + wIm * stepRe
                    wRe = nextRe
                }
            }
            len = len shl 1
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
