package com.novacut.editor.engine

import android.content.Context
import android.media.*
import android.net.Uri
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TAG = "AudioEngine"

/**
 * Audio processing engine for waveform extraction, mixing, and effects.
 */
@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * LRU cache for extracted waveforms keyed by "uri|sampleCount".
     * Avoids redundant PCM decoding when timeline recomposes.
     * Max 64 entries (~50KB total for 200-sample waveforms).
     */
    private val waveformCache = LruCache<String, FloatArray>(64)

    /**
     * Clear the waveform cache (e.g., when project changes).
     */
    fun clearWaveformCache() {
        waveformCache.evictAll()
    }

    /**
     * Extract audio waveform amplitude data from a media file.
     * Returns normalized amplitudes (0..1) at evenly spaced intervals.
     * Results are cached to avoid redundant decoding on timeline recomposition.
     */
    suspend fun extractWaveform(
        uri: Uri,
        sampleCount: Int = 200
    ): FloatArray = withContext(Dispatchers.IO) {
        if (sampleCount <= 0) return@withContext FloatArray(0)
        val boundedSampleCount = sampleCount.coerceAtMost(10_000)
        val cacheKey = "${uri}|${boundedSampleCount}"
        waveformCache.get(cacheKey)?.let { return@withContext it }
        if (isNonAudioVisualAsset(uri)) {
            val silent = FloatArray(boundedSampleCount) { 0f }
            waveformCache.put(cacheKey, silent)
            return@withContext silent
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)

            // Find audio track
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                return@withContext FloatArray(sampleCount) { 0f }
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext FloatArray(boundedSampleCount)

            // Decode audio to PCM
            var decoder: MediaCodec? = null
            val amplitudes: MutableList<Float>
            var maxAmplitude = 1f
            try {
                decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(format, null, null, 0)
                decoder.start()

                amplitudes = mutableListOf()
                val bufferInfo = MediaCodec.BufferInfo()
                var isEOS = false

                while (!isEOS) {
                    // Feed input
                    val inIndex = decoder.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }

                    // Drain output
                    var outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                    while (outIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val samples = readPcmSamples(outputBuffer, bufferInfo)

                            // Calculate RMS for this buffer
                            if (samples.isNotEmpty()) {
                                var sum = 0.0
                                for (sample in samples) {
                                    sum += sample.toDouble() * sample.toDouble()
                                }
                                val rms = Math.sqrt(sum / samples.size).toFloat()
                                amplitudes.add(rms)
                                maxAmplitude = max(maxAmplitude, rms)
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            isEOS = true
                            break
                        }
                        outIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } finally {
                try { decoder?.stop() } catch (_: Exception) { /* shutting down */ }
                try { decoder?.release() } catch (_: Exception) { /* best-effort */ }
            }

            // Resample to desired count and normalize
            if (amplitudes.isEmpty()) return@withContext FloatArray(boundedSampleCount) { 0f }

            val result = FloatArray(boundedSampleCount)
            val ratio = amplitudes.size.toFloat() / boundedSampleCount
            for (i in 0 until boundedSampleCount) {
                val start = (i * ratio).toInt()
                val end = min(max(((i + 1) * ratio).toInt(), start + 1), amplitudes.size)
                var peak = 0f
                for (j in start until end) {
                    peak = max(peak, amplitudes[j])
                }
                result[i] = peak / maxAmplitude
            }
            waveformCache.put(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Waveform extraction failed for $uri", e)
            FloatArray(sampleCount) { 0f }
        } finally {
            extractor.release()
        }
    }

    private fun isNonAudioVisualAsset(uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        if (!mimeType.isNullOrBlank()) {
            return mimeType.startsWith("image/")
        }
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?: return false
        return extension in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
    }

    /**
     * Mix multiple audio tracks into a single PCM buffer.
     * Each track has a volume level.
     */
    suspend fun mixAudioTracks(
        tracks: List<AudioTrackData>,
        outputSampleRate: Int = 44100,
        outputChannels: Int = 2
    ): ShortArray = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext ShortArray(0)

        val maxDuration = tracks.maxOf { it.durationMs }
        // Guard against Int overflow for ultra-long timelines. At 44.1 kHz
        // stereo, Int.MAX_VALUE samples caps at ~6h 45m; higher sample rates
        // or longer timelines silently wrap negative, producing a
        // NegativeArraySizeException on the FloatArray allocation. Clamp so
        // we fail gracefully with an empty mix rather than a hard crash.
        val rawSamples = (maxDuration / 1000.0 * outputSampleRate * outputChannels).toLong()
        if (rawSamples <= 0L || rawSamples > Int.MAX_VALUE.toLong()) {
            Log.w(TAG, "Timeline too long to mix in one pass: ${maxDuration / 1000}s — aborting mix")
            return@withContext ShortArray(0)
        }
        val totalSamples = rawSamples.toInt()
        val mixBuffer = FloatArray(totalSamples)

        for (track in tracks) {
            if (track.isMuted) continue
            val pcm = decodeToPCM(track.uri)
            val scaledVolume = track.volume

            for (i in pcm.indices) {
                if (i < mixBuffer.size) {
                    mixBuffer[i] += pcm[i].toFloat() * scaledVolume
                }
            }
        }

        // Normalize and convert to Short
        val maxVal = mixBuffer.maxOfOrNull { abs(it) } ?: 1f
        val normalizer = if (maxVal > Short.MAX_VALUE) Short.MAX_VALUE.toFloat() / maxVal else 1f

        ShortArray(mixBuffer.size) { i ->
            (mixBuffer[i] * normalizer).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Apply volume fade to PCM data.
     */
    fun applyFade(
        pcm: ShortArray,
        sampleRate: Int,
        channels: Int,
        fadeInMs: Long = 0,
        fadeOutMs: Long = 0
    ): ShortArray {
        val result = pcm.copyOf()
        if (channels <= 0) return pcm
        val totalSamples = result.size / channels

        // Fade in
        if (fadeInMs > 0) {
            val fadeSamples = (fadeInMs / 1000.0 * sampleRate).toInt()
            for (i in 0 until min(fadeSamples, totalSamples)) {
                val factor = i.toFloat() / fadeSamples
                for (ch in 0 until channels) {
                    val idx = i * channels + ch
                    if (idx < result.size) {
                        result[idx] = (result[idx] * factor).toInt().toShort()
                    }
                }
            }
        }

        // Fade out
        if (fadeOutMs > 0) {
            val fadeSamples = (fadeOutMs / 1000.0 * sampleRate).toInt()
            val startSample = max(0, totalSamples - fadeSamples)
            for (i in startSample until totalSamples) {
                val factor = (totalSamples - i).toFloat() / fadeSamples
                for (ch in 0 until channels) {
                    val idx = i * channels + ch
                    if (idx < result.size) {
                        result[idx] = (result[idx] * factor).toInt().toShort()
                    }
                }
            }
        }

        return result
    }

    private suspend fun decodeToPCM(uri: Uri): ShortArray = withContext(Dispatchers.IO) {
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

            if (audioIndex < 0 || format == null) return@withContext ShortArray(0)

            extractor.selectTrack(audioIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext ShortArray(0)
            var decoder: MediaCodec? = null

            // Collect chunks as ShortArrays to avoid boxing millions of Shorts
            val chunks = mutableListOf<ShortArray>()
            var totalSamples = 0

            try {
                decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(format, null, null, 0)
                decoder.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var eos = false

                while (!eos) {
                    val inIdx = decoder.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx) ?: continue
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eos = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }

                    var outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                    while (outIdx >= 0) {
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val arr = readPcmSamples(outBuf, bufferInfo)
                            if (arr.isNotEmpty()) {
                                if (totalSamples > Int.MAX_VALUE - arr.size) {
                                    Log.w(TAG, "Decoded PCM is too large to keep in memory")
                                    decoder.releaseOutputBuffer(outIdx, false)
                                    return@withContext ShortArray(0)
                                }
                                chunks.add(arr)
                                totalSamples += arr.size
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
                try { decoder?.stop() } catch (_: Exception) { /* shutting down */ }
                try { decoder?.release() } catch (_: Exception) { /* best-effort */ }
            }

            // Concatenate chunks into a single ShortArray without boxing
            val result = ShortArray(totalSamples)
            var offset = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.size)
                offset += chunk.size
            }
            result
        } finally {
            extractor.release()
        }
    }

    private fun readPcmSamples(outputBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo): ShortArray {
        if (bufferInfo.size < 2) return ShortArray(0)
        val buffer = outputBuffer.duplicate()
        val start = bufferInfo.offset.coerceIn(0, buffer.capacity())
        val unalignedEnd = (bufferInfo.offset + bufferInfo.size).coerceIn(start, buffer.capacity())
        val end = unalignedEnd - ((unalignedEnd - start) % 2)
        if (end <= start) return ShortArray(0)

        buffer.position(start)
        buffer.limit(end)
        val shortBuffer: ShortBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(shortBuffer.remaining())
        shortBuffer.get(samples)
        return samples
    }
}

data class AudioTrackData(
    val uri: Uri,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val durationMs: Long = 0L,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)
