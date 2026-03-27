package com.novacut.editor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.novacut.editor.engine.AudioEffectsEngine
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.engine.whisper.WhisperEngine
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.TextAlignment
import com.novacut.editor.model.TextAnimation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "AiFeatures"

/**
 * AI-powered features for NovaCut.
 * Uses on-device analysis algorithms for real-time video/audio intelligence.
 *
 * Implemented:
 * - Scene detection (frame difference analysis)
 * - Auto color correction (histogram analysis)
 * - Video stabilization (motion vector estimation)
 * - Audio denoise (noise floor analysis + gating)
 * - Auto captions (audio energy segmentation)
 * - Background removal (luminance/chroma keying)
 * - Motion tracking (template matching)
 * - Smart crop (visual weight analysis)
 */
@Singleton
class AiFeatures @Inject constructor(
    @ApplicationContext private val context: Context,
    val whisperEngine: WhisperEngine,
    val segmentationEngine: SegmentationEngine
) {
    // ---- Auto Captions ----

    /**
     * Generates timed caption segments. Uses Whisper ONNX for real speech-to-text
     * when the model is downloaded, otherwise falls back to audio energy segmentation.
     */
    suspend fun generateAutoCaptions(
        videoUri: Uri,
        languageCode: String = "en",
        onProgress: (Float) -> Unit = {}
    ): List<CaptionEntry> {
        // Use Whisper when model is available
        if (whisperEngine.isReady()) {
            return generateWhisperCaptions(videoUri, onProgress)
        }
        // Fallback to energy segmentation
        return generateEnergyCaptions(videoUri, onProgress)
    }

    private suspend fun generateWhisperCaptions(
        videoUri: Uri,
        onProgress: (Float) -> Unit
    ): List<CaptionEntry> = withContext(Dispatchers.IO) {
        try {
            val segments = whisperEngine.transcribe(videoUri, onProgress)
            segments.map { seg ->
                CaptionEntry(
                    startMs = seg.startMs,
                    endMs = seg.endMs,
                    text = seg.text,
                    confidence = 0.95f
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Whisper caption generation failed", e)
            emptyList()
        }
    }

    private suspend fun generateEnergyCaptions(
        videoUri: Uri,
        onProgress: (Float) -> Unit
    ): List<CaptionEntry> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, videoUri, null)

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
            if (audioIndex < 0 || format == null) return@withContext emptyList()

            extractor.selectTrack(audioIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()

            // Decode audio to PCM amplitudes
            val decoder = MediaCodec.createDecoderByType(mime)
            val amplitudeChunks = mutableListOf<FloatArray>()
            var totalSamples = 0

            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                val bufferInfo = MediaCodec.BufferInfo()
                var eos = false

                while (!eos) {
                    ensureActive()
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
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val samples = ShortArray(shortBuf.remaining())
                            shortBuf.get(samples)
                            // Convert to mono float amplitude
                            val mono = FloatArray(samples.size / channels)
                            for (i in mono.indices) {
                                var sum = 0f
                                for (ch in 0 until channels) {
                                    val idx = i * channels + ch
                                    if (idx < samples.size) sum += abs(samples[idx].toFloat())
                                }
                                mono[i] = sum / channels / 32768f
                            }
                            amplitudeChunks.add(mono)
                            totalSamples += mono.size
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            eos = true; break
                        }
                        outIdx = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } finally {
                try { decoder.stop() } catch (_: Exception) {}
                decoder.release()
            }

            if (totalSamples == 0) return@withContext emptyList()

            // Flatten into single array
            val allSamples = FloatArray(totalSamples)
            var offset = 0
            for (chunk in amplitudeChunks) {
                System.arraycopy(chunk, 0, allSamples, offset, chunk.size)
                offset += chunk.size
            }

            // Compute RMS energy in 100ms windows
            val windowSamples = (sampleRate / 10).coerceAtLeast(1)
            val windowCount = totalSamples / windowSamples
            if (windowCount == 0) return@withContext emptyList()
            val energy = FloatArray(windowCount)
            var maxEnergy = 0f
            for (w in 0 until windowCount) {
                var sum = 0.0
                val base = w * windowSamples
                for (s in 0 until windowSamples) {
                    val v = allSamples[base + s]
                    sum += v * v
                }
                energy[w] = sqrt(sum / windowSamples).toFloat()
                maxEnergy = max(maxEnergy, energy[w])
            }

            if (maxEnergy < 0.001f) return@withContext emptyList()

            // Normalize and find speech segments (above 15% threshold)
            val threshold = maxEnergy * 0.15f
            val captions = mutableListOf<CaptionEntry>()
            var segmentStart = -1
            var captionIndex = 1

            for (w in 0 until windowCount) {
                val isSpeech = energy[w] > threshold
                if (isSpeech && segmentStart < 0) {
                    segmentStart = w
                } else if (!isSpeech && segmentStart >= 0) {
                    val startMs = segmentStart * 100L
                    val endMs = w * 100L
                    if (endMs - startMs >= 300) { // Min 300ms segment
                        captions.add(CaptionEntry(
                            startMs = startMs,
                            endMs = endMs,
                            text = "[Speech segment $captionIndex]",
                            confidence = energy.slice(segmentStart until w).let { if (it.isEmpty()) 0.0 else it.average() }.toFloat() / maxEnergy
                        ))
                        captionIndex++
                    }
                    segmentStart = -1
                }
            }
            // Close final segment
            if (segmentStart >= 0) {
                val startMs = segmentStart * 100L
                val endMs = windowCount * 100L
                if (endMs - startMs >= 300) {
                    captions.add(CaptionEntry(
                        startMs = startMs,
                        endMs = endMs,
                        text = "[Speech segment $captionIndex]",
                        confidence = energy.slice(segmentStart until windowCount).let { if (it.isEmpty()) 0.0 else it.average() }.toFloat() / maxEnergy
                    ))
                }
            }

            captions
        } catch (e: Exception) {
            Log.w(TAG, "Energy-based caption generation failed", e)
            emptyList()
        } finally {
            extractor.release()
        }
    }

    fun cleanCaptionText(text: String): String {
        val fillerPatterns = listOf(
            "\\b[Uu]h\\b",
            "\\b[Uu]m\\b",
            "\\b[Uu]mm\\b",
            "\\b[Uu]hh\\b",
            "\\b[Ll]ike\\b",
            "\\b[Yy]ou know\\b",
            "\\b[Ss]o\\b(?=\\s*,)",
            "\\b[Aa]ctually\\b(?=\\s*,)",
            "\\b[Bb]asically\\b(?=\\s*,)",
            "\\b[Ll]iterally\\b(?=\\s*,)",
            "\\b[Rr]ight\\b(?=\\s*,)",
            "\\b[Ii] mean\\b"
        )
        var result = text
        for (pattern in fillerPatterns) {
            result = result.replace(Regex(pattern), "")
        }
        result = result.replace(Regex(",\\s*,"), ",")
        result = result.replace(Regex("\\s{2,}"), " ")
        result = result.replace(Regex("^\\s*,\\s*"), "")
        result = result.replace(Regex("\\s*,\\s*$"), "")
        return result.trim()
    }

    /**
     * Convert caption entries to TextOverlay objects for the timeline.
     */
    fun captionsToOverlays(
        captions: List<CaptionEntry>,
        style: CaptionStyle = CaptionStyle()
    ): List<TextOverlay> {
        return captions.mapNotNull { caption ->
            val cleaned = cleanCaptionText(caption.text)
            if (cleaned.isBlank()) return@mapNotNull null
            TextOverlay(
                text = cleaned,
                fontSize = style.fontSize,
                color = style.textColor,
                backgroundColor = style.backgroundColor,
                strokeColor = style.strokeColor,
                strokeWidth = style.strokeWidth,
                bold = style.bold,
                alignment = TextAlignment.CENTER,
                positionX = 0.5f,
                positionY = style.positionY,
                startTimeMs = caption.startMs,
                endTimeMs = caption.endMs,
                animationIn = TextAnimation.FADE,
                animationOut = TextAnimation.FADE
            )
        }
    }

    // ---- Auto Color Correction ----

    /**
     * Analyzes multiple frames from the video to compute optimal color corrections.
     * Uses histogram analysis across R/G/B channels to determine:
     * - Brightness offset (shift mid-tone toward 128)
     * - Contrast multiplier (expand histogram to fill 0-255 range)
     * - Saturation adjustment (based on chroma spread)
     * - Temperature bias (R/B channel imbalance)
     */
    suspend fun autoColorCorrect(
        videoUri: Uri
    ): ColorCorrection = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext ColorCorrection()

            // Sample 5 frames evenly across the clip
            val sampleCount = 5
            val histR = IntArray(256)
            val histG = IntArray(256)
            val histB = IntArray(256)
            var totalPixels = 0L

            for (i in 0 until sampleCount) {
                val timeMs = durationMs * (i * 2 + 1) / (sampleCount * 2)
                val frame = retriever.getFrameAtTime(
                    timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: continue

                val scaled = Bitmap.createScaledBitmap(frame, 128, 72, true)
                if (scaled !== frame) frame.recycle()

                val pixelCount = scaled.width * scaled.height
                val pixels = IntArray(pixelCount)
                scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                for (pixel in pixels) {
                    histR[Color.red(pixel)]++
                    histG[Color.green(pixel)]++
                    histB[Color.blue(pixel)]++
                }
                totalPixels += pixelCount
                scaled.recycle()
            }

            if (totalPixels == 0L) return@withContext ColorCorrection()

            // Calculate channel statistics
            val avgR = channelAverage(histR, totalPixels)
            val avgG = channelAverage(histG, totalPixels)
            val avgB = channelAverage(histB, totalPixels)
            val overallAvg = (avgR + avgG + avgB) / 3f

            // Brightness: how far the average luminance is from mid-gray (128)
            val brightnessOffset = (128f - overallAvg) / 255f // -1..1

            // Contrast: find 1st and 99th percentile across all channels
            val low1 = percentile(histR, histG, histB, totalPixels, 0.01f)
            val high99 = percentile(histR, histG, histB, totalPixels, 0.99f)
            val range = (high99 - low1).coerceAtLeast(1f)
            val contrastMult = (220f / range).coerceIn(0.5f, 2.5f) // Target 220 of 256 range

            // Saturation: analyze chroma spread
            val chromaSpread = abs(avgR - avgG) + abs(avgG - avgB) + abs(avgR - avgB)
            val saturationAdj = if (chromaSpread < 15f) 1.2f // Undersaturated
                else if (chromaSpread > 80f) 0.85f // Oversaturated
                else 1f

            // Temperature: R/B imbalance
            val tempBias = ((avgR - avgB) / 255f).coerceIn(-1f, 1f)
            val tempCorrection = -tempBias * 0.3f // Counter the bias

            ColorCorrection(
                brightness = brightnessOffset.coerceIn(-0.5f, 0.5f),
                contrast = contrastMult,
                saturation = saturationAdj,
                temperature = tempCorrection,
                confidence = min(1f, totalPixels / 40000f)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Auto color correction failed", e)
            ColorCorrection()
        } finally {
            retriever.release()
        }
    }

    private fun channelAverage(hist: IntArray, total: Long): Float {
        var sum = 0L
        for (i in hist.indices) sum += i.toLong() * hist[i]
        return sum.toFloat() / total
    }

    private fun percentile(hR: IntArray, hG: IntArray, hB: IntArray, total: Long, p: Float): Float {
        val target = (total * 3 * p).toLong()
        var cumulative = 0L
        for (i in 0..255) {
            cumulative += hR[i] + hG[i] + hB[i]
            if (cumulative >= target) return i.toFloat()
        }
        return 255f
    }

    // ---- Video Stabilization ----

    /**
     * Analyzes consecutive frames to estimate camera motion (shake).
     * Computes motion vectors by comparing frame regions, then returns
     * counter-motion transform parameters to stabilize the video.
     *
     * Strategy: sample frames at ~10fps, compute XY offset between consecutive
     * frames using block matching on downscaled versions. Return smoothed
     * stabilization transforms.
     */
    suspend fun stabilizeVideo(
        videoUri: Uri
    ): StabilizationResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext StabilizationResult()

            val intervalMs = 100L // 10fps analysis
            val motionX = mutableListOf<Float>()
            val motionY = mutableListOf<Float>()
            var prevFrame: Bitmap? = null
            var currentMs = 0L
            val analysisWidth = 64
            val analysisHeight = 36

            val maxAnalysisMs = minOf(durationMs, 120_000L) // Analyze up to 2 minutes
            while (currentMs < maxAnalysisMs) {
                ensureActive()
                val frame = retriever.getFrameAtTime(
                    currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, analysisWidth, analysisHeight, true)
                    if (scaled !== frame) frame.recycle()

                    if (prevFrame != null) {
                        val (dx, dy) = estimateMotion(prevFrame, scaled)
                        motionX.add(dx)
                        motionY.add(dy)
                        prevFrame.recycle()
                    }
                    prevFrame = scaled
                }
                currentMs += intervalMs
            }
            prevFrame?.recycle()

            if (motionX.isEmpty()) return@withContext StabilizationResult()

            // Calculate shake magnitude (standard deviation of motion)
            val avgX = motionX.average().toFloat()
            val avgY = motionY.average().toFloat()
            val stdX = sqrt(motionX.map { (it - avgX) * (it - avgX) }.average()).toFloat()
            val stdY = sqrt(motionY.map { (it - avgY) * (it - avgY) }.average()).toFloat()
            val shakeMagnitude = sqrt(stdX * stdX + stdY * stdY)

            // Recommended zoom to compensate (more shake = more zoom needed)
            val zoomFactor = 1f + (shakeMagnitude * 3f).coerceIn(0.02f, 0.15f)

            // Generate smoothed counter-motion keyframes
            val smoothed = smoothMotion(motionX, motionY, windowSize = 5)

            StabilizationResult(
                shakeMagnitude = shakeMagnitude,
                recommendedZoom = zoomFactor,
                motionKeyframes = smoothed.mapIndexed { i, (sx, sy) ->
                    StabilizationKeyframe(
                        timestampMs = i * intervalMs,
                        offsetX = -sx * 0.5f, // Counter-motion at 50% strength
                        offsetY = -sy * 0.5f
                    )
                },
                confidence = min(1f, motionX.size / 50f)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Video stabilization analysis failed", e)
            StabilizationResult()
        } finally {
            retriever.release()
        }
    }

    /**
     * Block-matching motion estimation between two downscaled frames.
     * Returns estimated XY pixel offset (normalized 0..1).
     */
    private fun estimateMotion(prev: Bitmap, curr: Bitmap): Pair<Float, Float> {
        val w = min(prev.width, curr.width)
        val h = min(prev.height, curr.height)
        if (w < 8 || h < 8) return 0f to 0f

        // Compare center region with shifted versions
        val blockSize = min(w, h) / 4
        val cx = w / 2
        val cy = h / 2
        val searchRange = 4

        var bestDx = 0
        var bestDy = 0
        var bestDiff = Long.MAX_VALUE

        for (dy in -searchRange..searchRange) {
            for (dx in -searchRange..searchRange) {
                var diff = 0L
                var count = 0
                for (by in -blockSize..blockSize step 2) {
                    for (bx in -blockSize..blockSize step 2) {
                        val px = cx + bx
                        val py = cy + by
                        val sx = px + dx
                        val sy = py + dy
                        if (px in 0 until w && py in 0 until h &&
                            sx in 0 until w && sy in 0 until h) {
                            val p1 = prev.getPixel(px, py)
                            val p2 = curr.getPixel(sx, sy)
                            diff += abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF)) +
                                    abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF)) +
                                    abs((p1 and 0xFF) - (p2 and 0xFF))
                            count++
                        }
                    }
                }
                if (count > 0) {
                    val avgDiff = diff / count
                    if (avgDiff < bestDiff) {
                        bestDiff = avgDiff
                        bestDx = dx
                        bestDy = dy
                    }
                }
            }
        }

        return bestDx.toFloat() / w to bestDy.toFloat() / h
    }

    /**
     * Smooth motion vectors with a moving average window.
     */
    private fun smoothMotion(
        mx: List<Float>, my: List<Float>, windowSize: Int
    ): List<Pair<Float, Float>> {
        val half = windowSize / 2
        return mx.indices.map { i ->
            val startIdx = max(0, i - half)
            val endIdx = min(mx.size, i + half + 1)
            val sx = mx.subList(startIdx, endIdx).average().toFloat()
            val sy = my.subList(startIdx, endIdx).average().toFloat()
            sx to sy
        }
    }

    // ---- Audio Denoise ----

    /**
     * Analyzes audio to detect noise characteristics.
     * Samples the first and last 500ms (typically ambient noise) to build a noise profile.
     * Returns a noise reduction profile with recommended settings.
     */
    suspend fun analyzeAudioNoise(
        videoUri: Uri
    ): NoiseProfile = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, videoUri, null)

            var audioIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val tf = extractor.getTrackFormat(i)
                if (tf.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioIndex = i; format = tf; break
                }
            }
            if (audioIndex < 0 || format == null) return@withContext NoiseProfile()

            extractor.selectTrack(audioIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext NoiseProfile()

            // Decode full audio
            val decoder = MediaCodec.createDecoderByType(mime)
            val chunks = mutableListOf<ShortArray>()
            var totalSamples = 0

            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                val bufferInfo = MediaCodec.BufferInfo()
                var eos = false

                while (!eos) {
                    ensureActive()
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
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val arr = ShortArray(shortBuf.remaining())
                            shortBuf.get(arr)
                            chunks.add(arr)
                            totalSamples += arr.size
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            eos = true; break
                        }
                        outIdx = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } finally {
                try { decoder.stop() } catch (_: Exception) {}
                decoder.release()
            }

            if (totalSamples == 0) return@withContext NoiseProfile()

            // Flatten
            val allSamples = ShortArray(totalSamples)
            var off = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, allSamples, off, chunk.size)
                off += chunk.size
            }

            val monoSamples = totalSamples / channels

            // Analyze first and last 500ms for noise floor
            val noiseSampleCount = min(sampleRate / 2, monoSamples / 4) // 500ms or 25% of clip
            var noiseRmsSum = 0.0
            var noiseCount = 0

            // First 500ms
            for (i in 0 until min(noiseSampleCount, monoSamples)) {
                val v = allSamples[i * channels].toFloat() / 32768f
                noiseRmsSum += v * v
                noiseCount++
            }
            // Last 500ms
            val lastStart = max(0, monoSamples - noiseSampleCount)
            for (i in lastStart until monoSamples) {
                val idx = i * channels
                if (idx < allSamples.size) {
                    val v = allSamples[idx].toFloat() / 32768f
                    noiseRmsSum += v * v
                    noiseCount++
                }
            }

            val noiseFloorRms = if (noiseCount > 0) sqrt(noiseRmsSum / noiseCount).toFloat() else 0f

            // Calculate overall signal RMS
            var signalRmsSum = 0.0
            for (i in 0 until min(monoSamples, sampleRate * 10)) { // Sample up to 10s
                val idx = i * channels
                if (idx < allSamples.size) {
                    val v = allSamples[idx].toFloat() / 32768f
                    signalRmsSum += v * v
                }
            }
            val signalSampleCount = min(monoSamples, sampleRate * 10).coerceAtLeast(1)
            val signalRms = sqrt(signalRmsSum / signalSampleCount).toFloat()

            val snrDb = if (noiseFloorRms > 0.0001f) {
                20f * kotlin.math.log10(signalRms / noiseFloorRms)
            } else 60f // Very clean audio

            // Determine reduction strength based on SNR
            val reductionStrength = when {
                snrDb < 10f -> 0.8f  // Very noisy
                snrDb < 20f -> 0.6f  // Noisy
                snrDb < 30f -> 0.4f  // Moderate noise
                snrDb < 40f -> 0.2f  // Light noise
                else -> 0.1f         // Clean
            }

            NoiseProfile(
                noiseFloorDb = 20f * kotlin.math.log10(max(noiseFloorRms, 0.0001f)),
                signalToNoiseDb = snrDb,
                recommendedReduction = reductionStrength,
                noiseGateThreshold = noiseFloorRms * 1.5f,
                confidence = if (noiseCount > sampleRate / 4) 0.9f else 0.5f
            )
        } catch (e: Exception) {
            Log.w(TAG, "Audio noise analysis failed", e)
            NoiseProfile()
        } finally {
            extractor.release()
        }
    }

    // ---- Background Removal ----

    /**
     * Analyzes a frame to determine the dominant background color and returns
     * chroma key parameters to remove it. Samples edge regions (top/bottom/left/right
     * border strips) to identify the background color.
     *
     * @return BackgroundAnalysis with detected bg color and recommended chroma key settings
     */
    suspend fun analyzeBackground(
        videoUri: Uri
    ): BackgroundAnalysis = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext BackgroundAnalysis()

            // Sample frame from middle of clip
            val frame = retriever.getFrameAtTime(
                (durationMs / 2) * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext BackgroundAnalysis()

            val scaled = Bitmap.createScaledBitmap(frame, 128, 72, true)
            if (scaled !== frame) frame.recycle()
            val w = scaled.width
            val h = scaled.height

            // Sample edge pixels (15% border strips)
            val borderW = max(1, (w * 0.15f).toInt())
            val borderH = max(1, (h * 0.15f).toInt())
            var sumR = 0L; var sumG = 0L; var sumB = 0L; var count = 0L

            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            scaled.recycle()
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (x < borderW || x >= w - borderW || y < borderH || y >= h - borderH) {
                        val p = pixels[y * w + x]
                        sumR += Color.red(p)
                        sumG += Color.green(p)
                        sumB += Color.blue(p)
                        count++
                    }
                }
            }

            if (count == 0L) return@withContext BackgroundAnalysis()

            val avgR = (sumR / count).toInt()
            val avgG = (sumG / count).toInt()
            val avgB = (sumB / count).toInt()

            // Detect if background is a solid-ish color (low variance)
            val bgColor = Color.rgb(avgR, avgG, avgB)

            // Determine if green screen, blue screen, or general background
            val isGreenScreen = avgG > avgR * 1.3f && avgG > avgB * 1.3f && avgG > 80
            val isBlueScreen = avgB > avgR * 1.3f && avgB > avgG * 1.1f && avgB > 80

            val similarity = when {
                isGreenScreen -> 0.35f
                isBlueScreen -> 0.35f
                else -> 0.45f // General background needs wider tolerance
            }

            BackgroundAnalysis(
                backgroundColor = bgColor,
                isGreenScreen = isGreenScreen,
                isBlueScreen = isBlueScreen,
                recommendedSimilarity = similarity,
                recommendedSmoothness = 0.12f,
                recommendedSpill = 0.15f,
                confidence = if (isGreenScreen || isBlueScreen) 0.9f else 0.5f
            )
        } catch (e: Exception) {
            Log.w(TAG, "Background analysis failed", e)
            BackgroundAnalysis()
        } finally {
            retriever.release()
        }
    }

    // ---- Scene Detection ----

    /**
     * Analyzes video to detect scene changes (cuts, transitions).
     * Uses frame difference analysis with adaptive thresholding.
     */
    suspend fun detectScenes(
        videoUri: Uri,
        sensitivity: Float = 0.5f
    ): List<SceneChange> = withContext(Dispatchers.IO) {
        val scenes = mutableListOf<SceneChange>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext emptyList()

            val intervalMs = 500L
            var previousFrame: Bitmap? = null
            var currentMs = 0L

            while (currentMs < durationMs) {
                val frame = retriever.getFrameAtTime(
                    currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null && previousFrame != null) {
                    val difference = calculateFrameDifference(previousFrame, frame)
                    val threshold = 0.3f + (1f - sensitivity) * 0.5f
                    if (difference > threshold) {
                        scenes.add(SceneChange(
                            timestampMs = currentMs,
                            confidence = difference,
                            type = if (difference > 0.8f) SceneChangeType.HARD_CUT
                                   else SceneChangeType.TRANSITION
                        ))
                    }
                    previousFrame.recycle()
                }
                previousFrame = frame
                currentMs += intervalMs
            }
            previousFrame?.recycle()
        } finally {
            retriever.release()
        }

        scenes
    }

    // ---- Motion Tracking ----

    /**
     * Tracks a region of interest across video frames using template matching.
     * Extracts the initial region as a template, then searches for it in
     * subsequent frames using block matching with adaptive search window.
     */
    suspend fun trackMotion(
        videoUri: Uri,
        initialRegion: TrackingRegion,
        startMs: Long,
        endMs: Long
    ): List<TrackingResult> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val results = mutableListOf<TrackingResult>()
            val stepMs = 100L // 10fps tracking
            val analysisW = 128
            val analysisH = 72

            var prevCenterX = initialRegion.centerX
            var prevCenterY = initialRegion.centerY
            var prevFrame: Bitmap? = null
            var currentMs = startMs

            while (currentMs <= endMs) {
                val frame = retriever.getFrameAtTime(
                    currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, analysisW, analysisH, true)
                    if (scaled !== frame) frame.recycle()

                    if (prevFrame != null) {
                        // Estimate motion from previous frame
                        val (dx, dy) = estimateRegionMotion(
                            prevFrame, scaled,
                            (prevCenterX * analysisW).toInt(),
                            (prevCenterY * analysisH).toInt(),
                            (initialRegion.width * analysisW / 2).toInt().coerceAtLeast(4)
                        )
                        prevCenterX = (prevCenterX + dx).coerceIn(0f, 1f)
                        prevCenterY = (prevCenterY + dy).coerceIn(0f, 1f)
                        prevFrame.recycle()
                    }

                    results.add(TrackingResult(
                        timestampMs = currentMs,
                        region = TrackingRegion(
                            centerX = prevCenterX,
                            centerY = prevCenterY,
                            width = initialRegion.width,
                            height = initialRegion.height
                        ),
                        confidence = 0.9f - (currentMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L) * 0.3f
                    ))
                    prevFrame = scaled
                }
                currentMs += stepMs
            }
            prevFrame?.recycle()
            results
        } catch (e: Exception) {
            Log.w(TAG, "Scene detection failed", e)
            emptyList()
        } finally {
            retriever.release()
        }
    }

    /**
     * Estimate motion of a specific region between frames using block matching.
     */
    private fun estimateRegionMotion(
        prev: Bitmap, curr: Bitmap,
        centerX: Int, centerY: Int, radius: Int
    ): Pair<Float, Float> {
        val w = min(prev.width, curr.width)
        val h = min(prev.height, curr.height)
        if (w < 8 || h < 8) return 0f to 0f
        val searchRange = max(2, radius / 2)

        var bestDx = 0
        var bestDy = 0
        var bestDiff = Long.MAX_VALUE

        for (dy in -searchRange..searchRange) {
            for (dx in -searchRange..searchRange) {
                var diff = 0L
                var count = 0
                for (by in -radius..radius step 2) {
                    for (bx in -radius..radius step 2) {
                        val px = centerX + bx
                        val py = centerY + by
                        val sx = px + dx
                        val sy = py + dy
                        if (px in 0 until w && py in 0 until h &&
                            sx in 0 until w && sy in 0 until h) {
                            val p1 = prev.getPixel(px, py)
                            val p2 = curr.getPixel(sx, sy)
                            diff += abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF)) +
                                    abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF)) +
                                    abs((p1 and 0xFF) - (p2 and 0xFF))
                            count++
                        }
                    }
                }
                if (count > 0 && diff / count < bestDiff) {
                    bestDiff = diff / count
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        return bestDx.toFloat() / w to bestDy.toFloat() / h
    }

    // ---- Smart Crop ----

    /**
     * Analyzes the video to find the visual center of interest using
     * edge density and luminance weighting (saliency approximation).
     */
    suspend fun suggestCrop(
        videoUri: Uri,
        targetAspectRatio: Float
    ): CropSuggestion = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext CropSuggestion()

            // Analyze 3 frames
            var weightedX = 0f
            var weightedY = 0f
            var totalWeight = 0f

            for (i in 0 until 3) {
                val timeMs = durationMs * (i * 2 + 1) / 6
                val frame = retriever.getFrameAtTime(
                    timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: continue

                val scaled = Bitmap.createScaledBitmap(frame, 64, 36, true)
                if (scaled !== frame) frame.recycle()

                // Compute edge density per region (3x3 grid)
                val gridW = scaled.width / 3
                val gridH = scaled.height / 3
                for (gy in 0 until 3) {
                    for (gx in 0 until 3) {
                        var edgeSum = 0f
                        var count = 0
                        for (y in gy * gridH + 1 until min((gy + 1) * gridH, scaled.height - 1)) {
                            for (x in gx * gridW + 1 until min((gx + 1) * gridW, scaled.width - 1)) {
                                // Simple Sobel edge detection approximation
                                val c = luminance(scaled.getPixel(x, y))
                                val l = luminance(scaled.getPixel(x - 1, y))
                                val r = luminance(scaled.getPixel(x + 1, y))
                                val t = luminance(scaled.getPixel(x, y - 1))
                                val b = luminance(scaled.getPixel(x, y + 1))
                                edgeSum += abs(r - l) + abs(b - t)
                                count++
                            }
                        }
                        if (count > 0) {
                            val density = edgeSum / count
                            val regionCenterX = (gx + 0.5f) / 3f
                            val regionCenterY = (gy + 0.5f) / 3f
                            // Apply rule-of-thirds weighting (center-bias + thirds intersections)
                            val thirdsBoost = when {
                                gx == 1 && gy == 1 -> 1.2f
                                (gx == 0 || gx == 2) && (gy == 0 || gy == 2) -> 1.1f
                                else -> 1.0f
                            }
                            val weight = density * thirdsBoost
                            weightedX += regionCenterX * weight
                            weightedY += regionCenterY * weight
                            totalWeight += weight
                        }
                    }
                }
                scaled.recycle()
            }

            val safeRatio = targetAspectRatio.coerceAtLeast(0.01f)

            if (totalWeight < 0.001f) {
                return@withContext CropSuggestion(
                    centerX = 0.5f, centerY = 0.5f,
                    width = 1f, height = (1f / safeRatio).coerceAtMost(1f),
                    confidence = 0.3f
                )
            }

            val cx = (weightedX / totalWeight).coerceIn(0.2f, 0.8f)
            val cy = (weightedY / totalWeight).coerceIn(0.2f, 0.8f)

            CropSuggestion(
                centerX = cx,
                centerY = cy,
                width = 1f,
                height = (1f / safeRatio).coerceAtMost(1f),
                confidence = min(1f, totalWeight / 3f)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Smart crop analysis failed", e)
            val safeRatio = targetAspectRatio.coerceAtLeast(0.01f)
            CropSuggestion(
                centerX = 0.5f, centerY = 0.5f,
                width = 1f, height = (1f / safeRatio).coerceAtMost(1f),
                confidence = 0.3f
            )
        } finally {
            retriever.release()
        }
    }

    private fun luminance(pixel: Int): Float {
        return (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f) / 255f
    }

    // ---- Style Transfer ----

    /**
     * Analyzes a video frame and generates a cinematic color grade based on
     * the frame's existing color characteristics. Returns a set of effects
     * that transform the look into a professional color-graded style.
     */
    suspend fun analyzeAndApplyStyle(
        videoUri: Uri
    ): StyleTransferResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext StyleTransferResult()

            // Sample 3 frames across the clip for stable analysis
            val frames = listOf(durationMs / 4, durationMs / 2, durationMs * 3 / 4)
            var avgLum = 0f; var avgSat = 0f; var avgTemp = 0f; var frameCount = 0

            for (ms in frames) {
                val frame = retriever.getFrameAtTime(
                    ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: continue
                val scaled = Bitmap.createScaledBitmap(frame, 64, 36, true)
                if (scaled !== frame) frame.recycle()

                var lumSum = 0f; var satSum = 0f; var tempSum = 0f
                val n = scaled.width * scaled.height
                val pixels = IntArray(n)
                scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                scaled.recycle()
                for (p in pixels) {
                    val r = Color.red(p) / 255f
                    val g = Color.green(p) / 255f
                    val b = Color.blue(p) / 255f
                    lumSum += r * 0.299f + g * 0.587f + b * 0.114f
                    val cMax = max(r, max(g, b))
                    val cMin = min(r, min(g, b))
                    satSum += if (cMax > 0f) (cMax - cMin) / cMax else 0f
                    tempSum += (r - b)
                }
                avgLum += lumSum / n
                avgSat += satSum / n
                avgTemp += tempSum / n
                frameCount++
            }

            if (frameCount == 0) return@withContext StyleTransferResult()
            avgLum /= frameCount
            avgSat /= frameCount
            avgTemp /= frameCount

            // Determine cinematic adjustments based on analysis
            val effects = mutableListOf<Pair<String, Float>>()

            // Contrast: boost if flat, reduce if too harsh
            val contrastAdj = when {
                avgLum in 0.35f..0.65f -> 1.15f // mid-tone: slight boost
                avgLum < 0.35f -> 1.10f // dark: gentle boost
                else -> 0.95f // bright: slight reduce
            }
            effects.add("contrast" to contrastAdj)

            // Temperature: push toward cinematic teal/orange split
            val tempAdj = when {
                avgTemp > 0.1f -> -0.08f // already warm: cool shadows slightly
                avgTemp < -0.1f -> 0.05f // already cool: warm highlights slightly
                else -> -0.03f // neutral: slight cool shift (cinematic)
            }
            effects.add("temperature" to tempAdj)

            // Saturation: cinematic = slightly desaturated
            val satAdj = when {
                avgSat > 0.4f -> 0.82f // over-saturated: pull back
                avgSat < 0.15f -> 1.1f // too flat: slight boost
                else -> 0.92f // normal: slight desat for cinema look
            }
            effects.add("saturation" to satAdj)

            // Exposure: normalize mid-tones
            val exposureAdj = when {
                avgLum < 0.3f -> 0.15f // dark: lift slightly
                avgLum > 0.7f -> -0.1f // bright: pull down
                else -> 0f
            }
            if (abs(exposureAdj) > 0.01f) effects.add("exposure" to exposureAdj)

            // Vignette for cinematic framing
            effects.add("vignette_intensity" to 0.3f)
            effects.add("vignette_radius" to 0.8f)

            // Film grain for organic texture
            effects.add("film_grain" to 0.04f)

            val styleName = when {
                avgTemp < -0.1f && avgSat < 0.25f -> "Noir"
                avgTemp > 0.15f && avgSat > 0.35f -> "Warm Cinematic"
                avgLum < 0.35f -> "Moody"
                avgSat > 0.4f -> "Vibrant Film"
                else -> "Cinematic"
            }

            StyleTransferResult(
                styleName = styleName,
                contrast = contrastAdj,
                temperature = tempAdj,
                saturation = satAdj,
                exposure = exposureAdj,
                vignetteIntensity = 0.3f,
                vignetteRadius = 0.8f,
                filmGrain = 0.04f,
                confidence = 0.85f
            )
        } catch (e: Exception) {
            Log.w(TAG, "Style transfer analysis failed", e)
            StyleTransferResult()
        } finally {
            retriever.release()
        }
    }

    // ---- Neural Upscale ----

    /**
     * Analyzes source video resolution and returns recommended upscale settings.
     * Applies sharpening to compensate for upscaling artifacts.
     */
    suspend fun analyzeForUpscale(
        videoUri: Uri
    ): UpscaleResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: return@withContext UpscaleResult()
            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: return@withContext UpscaleResult()

            val sourcePixels = width * height
            val targetResolution = when {
                sourcePixels <= 480 * 360 -> com.novacut.editor.model.Resolution.HD_720P
                sourcePixels <= 1280 * 720 -> com.novacut.editor.model.Resolution.FHD_1080P
                sourcePixels <= 1920 * 1080 -> com.novacut.editor.model.Resolution.QHD_1440P
                sourcePixels <= 2560 * 1440 -> com.novacut.editor.model.Resolution.UHD_4K
                else -> null // Already 4K+
            }

            // Sharpen strength inversely proportional to source resolution
            val sharpenStrength = when {
                sourcePixels <= 480 * 360 -> 0.8f
                sourcePixels <= 1280 * 720 -> 0.6f
                sourcePixels <= 1920 * 1080 -> 0.4f
                else -> 0.3f
            }

            UpscaleResult(
                sourceWidth = width,
                sourceHeight = height,
                targetResolution = targetResolution,
                sharpenStrength = sharpenStrength,
                confidence = if (targetResolution != null) 0.9f else 0f
            )
        } catch (e: Exception) {
            Log.w(TAG, "Upscale analysis failed", e)
            UpscaleResult()
        } finally {
            retriever.release()
        }
    }

    // ---- Shared Utilities ----

    /**
     * Calculate the visual difference between two frames.
     * Returns a value between 0 (identical) and 1 (completely different).
     */
    private fun calculateFrameDifference(frame1: Bitmap, frame2: Bitmap): Float {
        val width = minOf(frame1.width, frame2.width, 64)
        val height = minOf(frame1.height, frame2.height, 36)

        val scaled1 = Bitmap.createScaledBitmap(frame1, width, height, true)
        val scaled2 = try {
            Bitmap.createScaledBitmap(frame2, width, height, true)
        } catch (e: Exception) {
            if (scaled1 !== frame1) scaled1.recycle()
            throw e
        }

        try {
            val totalPixels = width * height
            val pixels1 = IntArray(totalPixels)
            val pixels2 = IntArray(totalPixels)
            scaled1.getPixels(pixels1, 0, width, 0, 0, width, height)
            scaled2.getPixels(pixels2, 0, width, 0, 0, width, height)

            var totalDiff = 0L
            for (i in 0 until totalPixels) {
                val p1 = pixels1[i]
                val p2 = pixels2[i]
                val dr = abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF))
                val dg = abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF))
                val db = abs((p1 and 0xFF) - (p2 and 0xFF))
                totalDiff += (dr + dg + db) / 3
            }

            return (totalDiff.toFloat() / totalPixels / 255f).coerceIn(0f, 1f)
        } finally {
            if (scaled1 !== frame1) scaled1.recycle()
            if (scaled2 !== frame2) scaled2.recycle()
        }
    }

    // ---- Filler Word / Silence Removal ----

    /**
     * Detects filler words and long silences in a video's audio track.
     * Uses Whisper ONNX for word-level timestamp detection of common filler words
     * ("um", "uh", "like", "you know", etc.). Falls back to energy-based silence
     * detection when Whisper model is not available.
     *
     * @param videoUri URI of the video/audio to analyze
     * @param onProgress progress callback (0..1)
     * @return list of regions to remove, each tagged as FILLER_WORD or SILENCE
     */
    suspend fun detectFillerAndSilence(
        videoUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): List<RemovalRegion> = withContext(Dispatchers.IO) {
        val regions = mutableListOf<RemovalRegion>()

        // Phase 1: Whisper-based filler word detection
        if (whisperEngine.isReady()) {
            onProgress(0.05f)
            try {
                val segments = whisperEngine.transcribe(videoUri) { p ->
                    onProgress(p * 0.6f)
                }
                val fillerPatterns = setOf(
                    "um", "uh", "like", "you know", "so", "basically",
                    "actually", "literally", "right", "i mean"
                )
                for (seg in segments) {
                    ensureActive()
                    val words = seg.text.trim().lowercase()
                    // Check if the entire segment is a filler word/phrase
                    if (words in fillerPatterns) {
                        regions.add(RemovalRegion(
                            startMs = seg.startMs,
                            endMs = seg.endMs,
                            type = RemovalType.FILLER_WORD
                        ))
                    } else {
                        // Check if segment starts or ends with a filler
                        for (filler in fillerPatterns) {
                            if (words == filler) continue // already handled
                            if (words.startsWith("$filler ") || words.endsWith(" $filler")) {
                                // Approximate filler position within the segment
                                val segDuration = seg.endMs - seg.startMs
                                val wordCount = words.split(" ").size.coerceAtLeast(1)
                                val fillerWordCount = filler.split(" ").size
                                val msPerWord = segDuration / wordCount
                                if (words.startsWith("$filler ")) {
                                    regions.add(RemovalRegion(
                                        startMs = seg.startMs,
                                        endMs = seg.startMs + msPerWord * fillerWordCount,
                                        type = RemovalType.FILLER_WORD
                                    ))
                                }
                                if (words.endsWith(" $filler")) {
                                    regions.add(RemovalRegion(
                                        startMs = seg.endMs - msPerWord * fillerWordCount,
                                        endMs = seg.endMs,
                                        type = RemovalType.FILLER_WORD
                                    ))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "Filler word detection failed, falling through to silence detection", e) }
        }

        onProgress(0.6f)

        // Phase 2: Energy-based silence detection (< -40dB threshold, > 500ms duration)
        try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, videoUri, null)

                var audioIndex = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val tf = extractor.getTrackFormat(i)
                    if (tf.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        audioIndex = i; format = tf; break
                    }
                }
                if (audioIndex < 0 || format == null) {
                    onProgress(1f)
                    return@withContext regions
                }

                extractor.selectTrack(audioIndex)
                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
                    onProgress(1f)
                    return@withContext regions
                }

                // Decode audio to PCM
                val decoder = MediaCodec.createDecoderByType(mime)
                val chunks = mutableListOf<ShortArray>()
                var totalSamples = 0

                try {
                    decoder.configure(format, null, null, 0)
                    decoder.start()
                    val bufferInfo = MediaCodec.BufferInfo()
                    var eos = false

                    while (!eos) {
                        ensureActive()
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
                                val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val arr = ShortArray(shortBuf.remaining())
                                shortBuf.get(arr)
                                chunks.add(arr)
                                totalSamples += arr.size
                            }
                            decoder.releaseOutputBuffer(outIdx, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                eos = true; break
                            }
                            outIdx = decoder.dequeueOutputBuffer(bufferInfo, 0)
                        }
                    }
                } finally {
                    try { decoder.stop() } catch (_: Exception) {}
                    decoder.release()
                }

                onProgress(0.8f)

                if (totalSamples == 0) {
                    onProgress(1f)
                    return@withContext regions
                }

                // Flatten
                val allSamples = ShortArray(totalSamples)
                var off = 0
                for (chunk in chunks) {
                    System.arraycopy(chunk, 0, allSamples, off, chunk.size)
                    off += chunk.size
                }

                val monoSamples = totalSamples / channels
                // -40dB threshold = 10^(-40/20) = 0.01 in linear amplitude
                val silenceThreshold = 0.01f
                val minSilenceMs = 500L
                // Analyze in 50ms windows
                val windowSamples = (sampleRate / 20).coerceAtLeast(1)
                val windowCount = monoSamples / windowSamples

                var silenceStart = -1L

                for (w in 0 until windowCount) {
                    ensureActive()
                    var rmsSum = 0.0
                    val base = w * windowSamples * channels
                    for (s in 0 until windowSamples) {
                        val idx = base + s * channels
                        if (idx < allSamples.size) {
                            val v = allSamples[idx].toFloat() / 32768f
                            rmsSum += v * v
                        }
                    }
                    val rms = sqrt(rmsSum / windowSamples).toFloat()
                    val windowMs = w * 50L

                    if (rms < silenceThreshold) {
                        if (silenceStart < 0) silenceStart = windowMs
                    } else {
                        if (silenceStart >= 0 && windowMs - silenceStart >= minSilenceMs) {
                            // Don't add if it overlaps a filler word region
                            val overlaps = regions.any { r ->
                                r.type == RemovalType.FILLER_WORD &&
                                    silenceStart < r.endMs && windowMs > r.startMs
                            }
                            if (!overlaps) {
                                regions.add(RemovalRegion(
                                    startMs = silenceStart,
                                    endMs = windowMs,
                                    type = RemovalType.SILENCE
                                ))
                            }
                        }
                        silenceStart = -1
                    }
                }
                // Close trailing silence
                if (silenceStart >= 0) {
                    val endMs = windowCount * 50L
                    if (endMs - silenceStart >= minSilenceMs) {
                        regions.add(RemovalRegion(
                            startMs = silenceStart,
                            endMs = endMs,
                            type = RemovalType.SILENCE
                        ))
                    }
                }
            } finally {
                extractor.release()
            }
        } catch (e: Exception) { Log.w(TAG, "Silence detection failed", e) }

        onProgress(1f)
        regions.sortedBy { it.startMs }
    }

    // ---- Beat Sync Automation ----

    /**
     * Generates beat-synced edit points by analyzing audio beats and distributing
     * clips evenly across detected beat positions.
     *
     * Uses [AudioEffectsEngine.detectBeats] to find beat positions, then maps
     * each clip to a beat boundary for rhythmic editing.
     *
     * @param audioUri URI of the audio/music track to analyze for beats
     * @param clipDurations list of available clip durations in milliseconds
     * @param onProgress progress callback (0..1)
     * @return list of beat-synced cut points mapping beats to clip indices
     */
    suspend fun generateBeatSyncEdits(
        audioUri: Uri,
        clipDurations: List<Long>,
        onProgress: (Float) -> Unit = {}
    ): List<BeatSyncCut> = withContext(Dispatchers.IO) {
        if (clipDurations.isEmpty()) return@withContext emptyList()

        onProgress(0.05f)

        // Decode audio to PCM for beat detection
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, audioUri, null)

            var audioIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val tf = extractor.getTrackFormat(i)
                if (tf.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioIndex = i; format = tf; break
                }
            }
            if (audioIndex < 0 || format == null) return@withContext emptyList()

            extractor.selectTrack(audioIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()

            val decoder = MediaCodec.createDecoderByType(mime)
            val chunks = mutableListOf<ShortArray>()
            var totalSamples = 0

            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                val bufferInfo = MediaCodec.BufferInfo()
                var eos = false

                while (!eos) {
                    ensureActive()
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
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val arr = ShortArray(shortBuf.remaining())
                            shortBuf.get(arr)
                            chunks.add(arr)
                            totalSamples += arr.size
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            eos = true; break
                        }
                        outIdx = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } finally {
                try { decoder.stop() } catch (_: Exception) {}
                decoder.release()
            }

            onProgress(0.4f)

            if (totalSamples == 0) return@withContext emptyList()

            val allSamples = ShortArray(totalSamples)
            var off = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, allSamples, off, chunk.size)
                off += chunk.size
            }

            // Detect beats using AudioEffectsEngine
            val beats = AudioEffectsEngine.detectBeats(allSamples, sampleRate, channels)
            onProgress(0.7f)

            if (beats.isEmpty()) return@withContext emptyList()

            // Distribute clips across beats evenly
            val results = mutableListOf<BeatSyncCut>()
            val clipCount = clipDurations.size

            if (clipCount >= beats.size) {
                // More clips than beats: assign each beat a clip, round-robin
                for ((beatIdx, beatMs) in beats.withIndex()) {
                    val clipIdx = beatIdx % clipCount
                    // Offset into the clip based on how far through the beat cycle we are
                    val cyclePos = beatIdx / clipCount
                    val clipDur = clipDurations[clipIdx]
                    val startOffset = if (clipDur > 0) {
                        (cyclePos * clipDur / max(1, beats.size / clipCount)).coerceIn(0, clipDur - 100)
                    } else 0L
                    results.add(BeatSyncCut(
                        beatTimeMs = beatMs,
                        clipIndex = clipIdx,
                        startOffsetMs = startOffset
                    ))
                }
            } else {
                // More beats than clips: distribute clips evenly across beat positions
                val beatsPerClip = beats.size.toFloat() / clipCount
                for (clipIdx in 0 until clipCount) {
                    ensureActive()
                    val beatIdx = (clipIdx * beatsPerClip).toInt().coerceIn(0, beats.size - 1)
                    results.add(BeatSyncCut(
                        beatTimeMs = beats[beatIdx],
                        clipIndex = clipIdx,
                        startOffsetMs = 0L
                    ))
                }
            }

            onProgress(1f)
            results
        } catch (e: Exception) {
            Log.w(TAG, "Beat sync edit generation failed", e)
            emptyList()
        } finally {
            extractor.release()
        }
    }

    // ---- Smart Reframe ----

    /**
     * Generates pan/zoom keyframes to keep the visual subject centered when
     * converting between aspect ratios (e.g. 16:9 -> 9:16).
     *
     * Samples frames at 500ms intervals and uses saliency analysis (edge density +
     * luminance weighting from [analyzeVisualWeight]) to find the subject center.
     * Returns position keyframes that keep the subject in the new aspect ratio's frame.
     *
     * @param videoUri source video URI
     * @param sourceAspect original aspect ratio
     * @param targetAspect desired output aspect ratio
     * @param onProgress progress callback (0..1)
     * @return list of reframe keyframes with pan/zoom values per timestamp
     */
    suspend fun smartReframe(
        videoUri: Uri,
        sourceAspect: AspectRatio,
        targetAspect: AspectRatio,
        onProgress: (Float) -> Unit = {}
    ): List<ReframeKeyframe> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext emptyList()

            val intervalMs = 500L
            val keyframes = mutableListOf<ReframeKeyframe>()
            var currentMs = 0L

            val srcRatio = sourceAspect.toFloat()
            val tgtRatio = targetAspect.toFloat()
            // Zoom needed to fill target aspect from source
            val baseZoom = if (tgtRatio < srcRatio) {
                // Target is taller (e.g. 16:9 -> 9:16): zoom in to fill width
                srcRatio / tgtRatio
            } else if (tgtRatio > srcRatio) {
                // Target is wider: zoom in to fill height
                tgtRatio / srcRatio
            } else {
                1f // Same aspect
            }

            val totalFrames = ((durationMs + intervalMs - 1) / intervalMs).toInt()
            var frameIdx = 0

            while (currentMs <= durationMs) {
                ensureActive()
                val frame = retriever.getFrameAtTime(
                    currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, 64, 36, true)
                    if (scaled !== frame) frame.recycle()

                    val (cx, cy) = analyzeVisualWeight(scaled)
                    scaled.recycle()

                    // Convert subject center to pan offsets (-1..1 range)
                    // 0.5 = center = no pan, 0 = full left, 1 = full right
                    val panX = (cx - 0.5f) * 2f
                    val panY = (cy - 0.5f) * 2f

                    // Clamp pan so the reframed window stays within source bounds
                    val maxPanX = if (baseZoom > 1f) 1f - (1f / baseZoom) else 0f
                    val maxPanY = if (baseZoom > 1f) 1f - (1f / baseZoom) else 0f

                    keyframes.add(ReframeKeyframe(
                        timeMs = currentMs,
                        panX = panX.coerceIn(-maxPanX, maxPanX),
                        panY = panY.coerceIn(-maxPanY, maxPanY),
                        zoom = baseZoom
                    ))
                }

                frameIdx++
                if (totalFrames > 0) onProgress(frameIdx.toFloat() / totalFrames)
                currentMs += intervalMs
            }

            // Smooth the keyframes to avoid jerky camera movement
            val smoothed = smoothReframeKeyframes(keyframes, windowSize = 5)

            onProgress(1f)
            smoothed
        } catch (e: Exception) {
            Log.w(TAG, "Smart reframe analysis failed", e)
            emptyList()
        } finally {
            retriever.release()
        }
    }

    /**
     * Analyzes visual weight (saliency) of a frame using edge density and
     * luminance weighting. Returns the weighted center of interest as (x, y)
     * normalized to 0..1.
     */
    private fun analyzeVisualWeight(frame: Bitmap): Pair<Float, Float> {
        val w = frame.width
        val h = frame.height
        if (w < 4 || h < 4) return 0.5f to 0.5f

        var weightedX = 0f
        var weightedY = 0f
        var totalWeight = 0f

        val gridCols = 4
        val gridRows = 4
        val cellW = w / gridCols
        val cellH = h / gridRows

        for (gy in 0 until gridRows) {
            for (gx in 0 until gridCols) {
                var edgeSum = 0f
                var count = 0
                for (y in gy * cellH + 1 until min((gy + 1) * cellH, h - 1)) {
                    for (x in gx * cellW + 1 until min((gx + 1) * cellW, w - 1)) {
                        val c = luminance(frame.getPixel(x, y))
                        val l = luminance(frame.getPixel(x - 1, y))
                        val r = luminance(frame.getPixel(x + 1, y))
                        val t = luminance(frame.getPixel(x, y - 1))
                        val b = luminance(frame.getPixel(x, y + 1))
                        edgeSum += abs(r - l) + abs(b - t)
                        count++
                    }
                }
                if (count > 0) {
                    val density = edgeSum / count
                    val regionCenterX = (gx + 0.5f) / gridCols
                    val regionCenterY = (gy + 0.5f) / gridRows
                    // Center bias — subjects tend to be near center
                    val centerDist = sqrt(
                        (regionCenterX - 0.5f) * (regionCenterX - 0.5f) +
                            (regionCenterY - 0.5f) * (regionCenterY - 0.5f)
                    )
                    val centerBoost = 1f + (0.5f - centerDist).coerceAtLeast(0f) * 0.5f
                    val weight = density * centerBoost
                    weightedX += regionCenterX * weight
                    weightedY += regionCenterY * weight
                    totalWeight += weight
                }
            }
        }

        return if (totalWeight > 0.001f) {
            (weightedX / totalWeight).coerceIn(0.1f, 0.9f) to
                (weightedY / totalWeight).coerceIn(0.1f, 0.9f)
        } else {
            0.5f to 0.5f
        }
    }

    /**
     * Smooth reframe keyframes with a moving average to prevent jerky camera motion.
     */
    private fun smoothReframeKeyframes(
        keyframes: List<ReframeKeyframe>,
        windowSize: Int
    ): List<ReframeKeyframe> {
        if (keyframes.size < 3) return keyframes
        val half = windowSize / 2
        return keyframes.mapIndexed { i, kf ->
            val start = max(0, i - half)
            val end = min(keyframes.size, i + half + 1)
            val window = keyframes.subList(start, end)
            kf.copy(
                panX = window.map { it.panX }.average().toFloat(),
                panY = window.map { it.panY }.average().toFloat()
            )
        }
    }

    // ---- AI Auto-Edit / Highlight Reel ----

    /**
     * Generates an automatic highlight reel from a set of clips.
     * Analyzes each clip for visual quality (sharpness), motion level, and face
     * presence (skin-tone detection). Ranks clips by quality, optionally syncs
     * cuts to music beats, and selects the best segments to fit the target duration.
     *
     * @param clips list of source clips with URIs and durations
     * @param musicUri optional music track URI for beat-synced cuts
     * @param targetDurationMs desired output duration in milliseconds
     * @param onProgress progress callback (0..1)
     * @return auto-edit result with selected segments and transition points
     */
    fun parseScriptToSegments(script: String, clipCount: Int, targetDurationMs: Long): List<ScriptSegment> {
        if (script.isBlank() || clipCount <= 0 || targetDurationMs <= 0) return emptyList()
        val sentences = script.split(Regex("[.!?]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.isEmpty()) return emptyList()
        val durationPerSegment = targetDurationMs / sentences.size
        return sentences.mapIndexed { index, sentence ->
            val keyword = sentence.split(" ").firstOrNull { it.length > 3 } ?: sentence.take(20)
            ScriptSegment(
                keyword = keyword,
                durationMs = durationPerSegment.coerceIn(1000L, 15000L),
                clipIndex = index % clipCount
            )
        }
    }

    suspend fun generateAutoEdit(
        clips: List<AutoEditClip>,
        musicUri: Uri?,
        targetDurationMs: Long,
        script: String? = null,
        onProgress: (Float) -> Unit = {}
    ): AutoEditResult = withContext(Dispatchers.IO) {
        if (clips.isEmpty() || targetDurationMs <= 0) {
            return@withContext AutoEditResult()
        }

        onProgress(0.05f)

        // Phase 1: Analyze each clip for quality metrics
        val scored = mutableListOf<Pair<Int, Float>>() // clipIndex to score
        val retriever = MediaMetadataRetriever()

        for ((idx, clip) in clips.withIndex()) {
            ensureActive()
            var qualityScore = 0f
            var motionScore = 0f
            var faceScore = 0f

            try {
                retriever.setDataSource(context, clip.uri)
                val midTime = clip.durationMs / 2

                val frame = retriever.getFrameAtTime(
                    midTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, 64, 36, true)
                    if (scaled !== frame) frame.recycle()

                    // Sharpness via Laplacian variance approximation
                    qualityScore = computeSharpness(scaled)

                    // Face presence via skin-tone pixel ratio
                    faceScore = detectSkinToneRatio(scaled)

                    // Motion: compare two frames
                    val frame2 = retriever.getFrameAtTime(
                        (midTime + 500) * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (frame2 != null) {
                        val scaled2 = Bitmap.createScaledBitmap(frame2, 64, 36, true)
                        if (scaled2 !== frame2) frame2.recycle()
                        motionScore = calculateFrameDifference(scaled, scaled2)
                        scaled2.recycle()
                    }

                    scaled.recycle()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto-edit clip quality scoring failed", e)
                // Score remains 0 — clip will be ranked low
            }

            // Combined score: sharpness (40%), motion (30%), face (30%)
            val combinedScore = qualityScore * 0.4f + motionScore * 0.3f + faceScore * 0.3f
            scored.add(idx to combinedScore)

            onProgress(0.05f + 0.5f * (idx + 1) / clips.size)
        }

        // Phase 2: Optionally detect beats for synced cuts
        var beatPositions: List<Long> = emptyList()
        if (musicUri != null) {
            try {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(context, musicUri, null)
                    var audioIndex = -1
                    var format: MediaFormat? = null
                    for (i in 0 until extractor.trackCount) {
                        val tf = extractor.getTrackFormat(i)
                        if (tf.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                            audioIndex = i; format = tf; break
                        }
                    }
                    if (audioIndex >= 0 && format != null) {
                        extractor.selectTrack(audioIndex)
                        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val mime = format.getString(MediaFormat.KEY_MIME)

                        if (mime != null) {
                            val decoder = MediaCodec.createDecoderByType(mime)
                            val pcmChunks = mutableListOf<ShortArray>()
                            var totalPcm = 0
                            try {
                                decoder.configure(format, null, null, 0)
                                decoder.start()
                                val bufferInfo = MediaCodec.BufferInfo()
                                var eos = false
                                while (!eos) {
                                    ensureActive()
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
                                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                            val arr = ShortArray(shortBuf.remaining())
                                            shortBuf.get(arr)
                                            pcmChunks.add(arr)
                                            totalPcm += arr.size
                                        }
                                        decoder.releaseOutputBuffer(outIdx, false)
                                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                            eos = true; break
                                        }
                                        outIdx = decoder.dequeueOutputBuffer(bufferInfo, 0)
                                    }
                                }
                            } finally {
                                try { decoder.stop() } catch (_: Exception) {}
                                decoder.release()
                            }
                            if (totalPcm > 0) {
                                val allPcm = ShortArray(totalPcm)
                                var pcmOff = 0
                                for (chunk in pcmChunks) {
                                    System.arraycopy(chunk, 0, allPcm, pcmOff, chunk.size)
                                    pcmOff += chunk.size
                                }
                                beatPositions = AudioEffectsEngine.detectBeats(allPcm, sampleRate, channels)
                                    .filter { it <= targetDurationMs }
                            }
                        }
                    }
                } finally {
                    extractor.release()
                }
            } catch (e: Exception) { Log.w(TAG, "Beat detection for auto-edit failed, proceeding without beats", e) }
        }

        onProgress(0.75f)

        // Phase 3: Select best segments to fill target duration
        val rankedClips = scored.sortedByDescending { it.second }
        val segments = mutableListOf<AutoEditSegment>()
        val transitionPoints = mutableListOf<Long>()
        var timelinePos = 0L

        // Script-based ordering: use script segments to determine clip order/duration
        if (!script.isNullOrBlank()) {
            val scriptSegments = parseScriptToSegments(script, clips.size, targetDurationMs)
            for (seg in scriptSegments) {
                ensureActive()
                if (timelinePos >= targetDurationMs) break
                val clipIdx = seg.clipIndex ?: continue
                if (clipIdx >= clips.size) continue
                val segDur = min(seg.durationMs, clips[clipIdx].durationMs)
                if (segDur <= 0) continue

                segments.add(AutoEditSegment(
                    clipIndex = clipIdx,
                    trimStartMs = 0L,
                    trimEndMs = segDur,
                    timelineStartMs = timelinePos
                ))

                if (timelinePos > 0) transitionPoints.add(timelinePos)
                timelinePos += segDur
            }

            onProgress(1f)
            return@withContext AutoEditResult(
                segments = segments,
                transitionPoints = transitionPoints
            )
        }

        if (beatPositions.size >= 2) {
            // Beat-synced: place clips at beat intervals
            val beatIntervals = (0 until beatPositions.size - 1).map {
                beatPositions[it + 1] - beatPositions[it]
            }
            var beatIdx = 0
            var rankIdx = 0

            while (timelinePos < targetDurationMs && rankIdx < rankedClips.size) {
                ensureActive()
                val (clipIdx, _) = rankedClips[rankIdx % rankedClips.size]
                val clipDur = clips[clipIdx].durationMs
                val segDur = if (beatIdx < beatIntervals.size) {
                    beatIntervals[beatIdx].coerceAtMost(clipDur)
                } else {
                    (targetDurationMs - timelinePos).coerceAtMost(clipDur)
                }

                if (segDur <= 0) break

                segments.add(AutoEditSegment(
                    clipIndex = clipIdx,
                    trimStartMs = 0L,
                    trimEndMs = segDur,
                    timelineStartMs = timelinePos
                ))

                if (timelinePos > 0) transitionPoints.add(timelinePos)
                timelinePos += segDur
                beatIdx++
                rankIdx++
            }
        } else {
            // No beats: distribute evenly by quality ranking
            val avgSegDur = if (rankedClips.isNotEmpty()) {
                (targetDurationMs / rankedClips.size).coerceIn(1000L, 10000L)
            } else return@withContext AutoEditResult()

            for ((clipIdx, _) in rankedClips) {
                ensureActive()
                if (timelinePos >= targetDurationMs) break
                val remaining = targetDurationMs - timelinePos
                val segDur = min(avgSegDur, min(remaining, clips[clipIdx].durationMs))
                if (segDur <= 0) continue

                segments.add(AutoEditSegment(
                    clipIndex = clipIdx,
                    trimStartMs = 0L,
                    trimEndMs = segDur,
                    timelineStartMs = timelinePos
                ))

                if (timelinePos > 0) transitionPoints.add(timelinePos)
                timelinePos += segDur
            }
        }

        onProgress(1f)
        AutoEditResult(
            segments = segments,
            transitionPoints = transitionPoints
        )
    }

    /**
     * Compute sharpness score via Laplacian variance approximation.
     * Higher values = sharper image. Returns 0..1 normalized.
     */
    private fun computeSharpness(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return 0f

        var varianceSum = 0.0
        var count = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                // Laplacian kernel: center*4 - top - bottom - left - right
                val c = luminance(bitmap.getPixel(x, y))
                val t = luminance(bitmap.getPixel(x, y - 1))
                val b = luminance(bitmap.getPixel(x, y + 1))
                val l = luminance(bitmap.getPixel(x - 1, y))
                val r = luminance(bitmap.getPixel(x + 1, y))
                val laplacian = 4f * c - t - b - l - r
                varianceSum += laplacian * laplacian
                count++
            }
        }

        val variance = if (count > 0) varianceSum / count else 0.0
        // Normalize: typical sharp image has variance ~0.01-0.05
        return (variance / 0.05).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Detect skin-tone pixel ratio as a simple face presence heuristic.
     * Uses YCbCr color space skin-tone ranges.
     * Returns 0..1 where higher = more skin-tone pixels detected.
     */
    private fun detectSkinToneRatio(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        var skinPixels = 0
        var total = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Simple skin-tone detection in RGB space
                // Skin pixels: R > 95, G > 40, B > 20, max-min > 15, R > G, R > B
                if (r > 95 && g > 40 && b > 20 &&
                    max(r, max(g, b)) - min(r, min(g, b)) > 15 &&
                    r > g && r > b &&
                    abs(r - g) > 15
                ) {
                    skinPixels++
                }
                total++
            }
        }

        // Face typically occupies 5-30% of frame
        val ratio = if (total > 0) skinPixels.toFloat() / total else 0f
        // Normalize: 0.05 ratio = low confidence, 0.2+ = high confidence
        return (ratio / 0.2f).coerceIn(0f, 1f)
    }

    // ---- AI Noise Reduction Analysis ----

    /**
     * Performs spectral analysis of audio noise characteristics.
     * Extracts PCM from the first 2 seconds of audio, computes a frequency-domain
     * noise profile via DFT, and classifies the noise type (hiss, hum, broadband, clean).
     * Returns recommended DSP effect parameters for noise reduction.
     *
     * @param videoUri URI of the video/audio to analyze
     * @param onProgress progress callback (0..1)
     * @return spectral noise profile with noise type and recommended effects
     */
    suspend fun analyzeNoiseProfile(
        videoUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): SpectralNoiseProfile = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, videoUri, null)

            var audioIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val tf = extractor.getTrackFormat(i)
                if (tf.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioIndex = i; format = tf; break
                }
            }
            if (audioIndex < 0 || format == null) return@withContext SpectralNoiseProfile()

            extractor.selectTrack(audioIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext SpectralNoiseProfile()

            onProgress(0.1f)

            // Decode first 2 seconds of audio
            val maxSamples = sampleRate * 2 // 2 seconds of mono
            val decoder = MediaCodec.createDecoderByType(mime)
            val chunks = mutableListOf<ShortArray>()
            var totalSamples = 0

            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                val bufferInfo = MediaCodec.BufferInfo()
                var eos = false

                while (!eos && totalSamples / channels < maxSamples) {
                    ensureActive()
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
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val arr = ShortArray(shortBuf.remaining())
                            shortBuf.get(arr)
                            chunks.add(arr)
                            totalSamples += arr.size
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            eos = true; break
                        }
                        outIdx = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } finally {
                try { decoder.stop() } catch (_: Exception) {}
                decoder.release()
            }

            onProgress(0.4f)

            if (totalSamples == 0) return@withContext SpectralNoiseProfile()

            // Flatten and convert to mono float
            val allSamples = ShortArray(totalSamples)
            var off = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, allSamples, off, chunk.size)
                off += chunk.size
            }
            val monoCount = min(totalSamples / channels, maxSamples)
            val mono = FloatArray(monoCount) { i ->
                val idx = i * channels
                if (idx < allSamples.size) allSamples[idx].toFloat() / 32768f else 0f
            }

            onProgress(0.5f)

            // Compute DFT magnitude spectrum (use power-of-2 window)
            val fftSize = findNextPowerOf2(min(mono.size, 4096))
            if (fftSize < 64) return@withContext SpectralNoiseProfile()

            val window = FloatArray(fftSize) { i ->
                if (i < mono.size) mono[i] else 0f
            }
            // Apply Hann window
            for (i in 0 until fftSize) {
                window[i] *= (0.5f * (1f - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
            }

            val (real, imag) = computeDft(window)

            onProgress(0.7f)

            // Compute magnitude spectrum in dB
            val halfSize = fftSize / 2
            val magnitudeDb = FloatArray(halfSize) { i ->
                val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
                val db = 20f * log10(max(mag, 1e-10f))
                db
            }

            // Compute noise floor (average of lowest 25% of bins)
            val sorted = magnitudeDb.sorted()
            val noiseFloorDb = sorted.take(max(1, halfSize / 4)).average().toFloat()

            // Classify noise type by spectral shape
            val lowBand = magnitudeDb.slice(1..min(halfSize - 1, halfSize / 8)) // DC to ~550Hz
            val midBand = magnitudeDb.slice(halfSize / 8..min(halfSize - 1, halfSize / 2)) // ~550Hz to ~5.5kHz
            val highBand = magnitudeDb.slice(halfSize / 2 until halfSize) // ~5.5kHz+

            val lowAvg = if (lowBand.isNotEmpty()) lowBand.average().toFloat() else -60f
            val midAvg = if (midBand.isNotEmpty()) midBand.average().toFloat() else -60f
            val highAvg = if (highBand.isNotEmpty()) highBand.average().toFloat() else -60f

            // Check for hum (strong peaks at 50/60Hz and harmonics)
            val humBinWidth = sampleRate.toFloat() / fftSize
            val humBin50 = (50f / humBinWidth).roundToInt().coerceIn(1, halfSize - 1)
            val humBin60 = (60f / humBinWidth).roundToInt().coerceIn(1, halfSize - 1)
            val humPeak = max(magnitudeDb[humBin50], magnitudeDb[humBin60])
            val humAboveFloor = humPeak - noiseFloorDb

            val noiseType = when {
                humAboveFloor > 20f -> NoiseType.HUM
                highAvg - lowAvg > 10f -> NoiseType.HISS
                noiseFloorDb > -30f -> NoiseType.BROADBAND
                else -> NoiseType.CLEAN
            }

            onProgress(0.85f)

            // Generate recommended effects based on noise type
            val recommendedEffects = mutableListOf<RecommendedEffect>()

            when (noiseType) {
                NoiseType.HUM -> {
                    // Notch filter at detected hum frequency
                    val humFreq = if (magnitudeDb[humBin60] > magnitudeDb[humBin50]) 60f else 50f
                    recommendedEffects.add(RecommendedEffect(
                        type = AudioEffectType.NOTCH,
                        params = mapOf("frequency" to humFreq, "bandwidth" to 0.5f)
                    ))
                    // Also notch harmonics
                    recommendedEffects.add(RecommendedEffect(
                        type = AudioEffectType.NOTCH,
                        params = mapOf("frequency" to humFreq * 2f, "bandwidth" to 0.5f)
                    ))
                    recommendedEffects.add(RecommendedEffect(
                        type = AudioEffectType.NOISE_GATE,
                        params = mapOf(
                            "threshold" to (noiseFloorDb + 6f),
                            "attack" to 2f, "hold" to 50f, "release" to 100f
                        )
                    ))
                }
                NoiseType.HISS -> {
                    // Low-pass filter to cut high-frequency hiss
                    val cutoff = when {
                        highAvg - midAvg > 15f -> 6000f
                        highAvg - midAvg > 8f -> 8000f
                        else -> 10000f
                    }
                    recommendedEffects.add(RecommendedEffect(
                        type = AudioEffectType.LOW_PASS,
                        params = mapOf("frequency" to cutoff, "resonance" to 0.7f)
                    ))
                    recommendedEffects.add(RecommendedEffect(
                        type = AudioEffectType.NOISE_GATE,
                        params = mapOf(
                            "threshold" to (noiseFloorDb + 6f),
                            "attack" to 1f, "hold" to 30f, "release" to 80f
                        )
                    ))
                    // De-esser to tame sibilance alongside hiss
                    recommendedEffects.add(RecommendedEffect(
                        type = AudioEffectType.DE_ESSER,
                        params = mapOf("frequency" to 6000f, "threshold" to -15f, "ratio" to 3f)
                    ))
                }
                NoiseType.BROADBAND -> {
                    // Noise gate + EQ sculpting
                    recommendedEffects.add(RecommendedEffect(
                        type = AudioEffectType.NOISE_GATE,
                        params = mapOf(
                            "threshold" to (noiseFloorDb + 10f),
                            "attack" to 2f, "hold" to 80f, "release" to 150f
                        )
                    ))
                    recommendedEffects.add(RecommendedEffect(
                        type = AudioEffectType.HIGH_PASS,
                        params = mapOf("frequency" to 80f, "resonance" to 0.7f)
                    ))
                    recommendedEffects.add(RecommendedEffect(
                        type = AudioEffectType.COMPRESSOR,
                        params = mapOf(
                            "threshold" to -20f, "ratio" to 3f,
                            "attack" to 10f, "release" to 100f,
                            "knee" to 6f, "makeupGain" to 3f
                        )
                    ))
                }
                NoiseType.CLEAN -> {
                    // Minimal processing: gentle high-pass to remove rumble
                    recommendedEffects.add(RecommendedEffect(
                        type = AudioEffectType.HIGH_PASS,
                        params = mapOf("frequency" to 60f, "resonance" to 0.5f)
                    ))
                }
            }

            onProgress(1f)
            SpectralNoiseProfile(
                noiseType = noiseType,
                noiseFloorDb = noiseFloorDb,
                recommendedEffects = recommendedEffects
            )
        } catch (e: Exception) {
            Log.w(TAG, "Spectral noise profile analysis failed", e)
            SpectralNoiseProfile()
        } finally {
            extractor.release()
        }
    }

    /**
     * Simple DFT computation. Returns (realPart, imagPart) arrays.
     * Uses direct computation — suitable for small windows (up to 4096).
     */
    private fun computeDft(input: FloatArray): Pair<FloatArray, FloatArray> {
        val n = input.size
        val real = FloatArray(n)
        val imag = FloatArray(n)

        for (k in 0 until n / 2) {
            var sumR = 0f
            var sumI = 0f
            for (t in 0 until n) {
                val angle = (2.0 * PI * k * t / n).toFloat()
                sumR += input[t] * cos(angle)
                sumI -= input[t] * sin(angle)
            }
            real[k] = sumR
            imag[k] = sumI
        }

        return real to imag
    }

    /**
     * Find the next power of 2 >= n.
     */
    private fun findNextPowerOf2(n: Int): Int {
        var v = n - 1
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        return v + 1
    }
}

// Data classes for AI features

data class CaptionEntry(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float = 1f,
    val languageCode: String = "en"
)

data class CaptionStyle(
    val fontSize: Float = 36f,
    val textColor: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0xCC000000,
    val strokeColor: Long = 0xFF000000,
    val strokeWidth: Float = 1.5f,
    val bold: Boolean = true,
    val positionY: Float = 0.85f
)

data class SceneChange(
    val timestampMs: Long,
    val confidence: Float,
    val type: SceneChangeType
)

enum class SceneChangeType {
    HARD_CUT, TRANSITION, FADE
}

data class TrackingRegion(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 0.3f,
    val height: Float = 0.3f
)

data class TrackingResult(
    val timestampMs: Long,
    val region: TrackingRegion,
    val confidence: Float
)

data class CropSuggestion(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 1f,
    val height: Float = 1f,
    val confidence: Float = 0.5f
)

data class ColorCorrection(
    val brightness: Float = 0f,      // -0.5..0.5
    val contrast: Float = 1f,        // 0.5..2.5
    val saturation: Float = 1f,      // 0.5..2.0
    val temperature: Float = 0f,     // -1..1
    val confidence: Float = 0f
)

data class StabilizationResult(
    val shakeMagnitude: Float = 0f,
    val recommendedZoom: Float = 1f,
    val motionKeyframes: List<StabilizationKeyframe> = emptyList(),
    val confidence: Float = 0f
)

data class StabilizationKeyframe(
    val timestampMs: Long,
    val offsetX: Float,
    val offsetY: Float
)

data class NoiseProfile(
    val noiseFloorDb: Float = -60f,
    val signalToNoiseDb: Float = 60f,
    val recommendedReduction: Float = 0f,
    val noiseGateThreshold: Float = 0f,
    val confidence: Float = 0f
)

data class StyleTransferResult(
    val styleName: String = "Unknown",
    val contrast: Float = 1f,
    val temperature: Float = 0f,
    val saturation: Float = 1f,
    val exposure: Float = 0f,
    val vignetteIntensity: Float = 0f,
    val vignetteRadius: Float = 0.8f,
    val filmGrain: Float = 0f,
    val confidence: Float = 0f
)

data class UpscaleResult(
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val targetResolution: com.novacut.editor.model.Resolution? = null,
    val sharpenStrength: Float = 0.5f,
    val confidence: Float = 0f
)

data class BackgroundAnalysis(
    val backgroundColor: Int = 0,
    val isGreenScreen: Boolean = false,
    val isBlueScreen: Boolean = false,
    val recommendedSimilarity: Float = 0.4f,
    val recommendedSmoothness: Float = 0.1f,
    val recommendedSpill: Float = 0.1f,
    val confidence: Float = 0f
)

// ---- Filler Word / Silence Removal ----

enum class RemovalType {
    FILLER_WORD, SILENCE
}

data class RemovalRegion(
    val startMs: Long,
    val endMs: Long,
    val type: RemovalType
)

// ---- Beat Sync Automation ----

data class BeatSyncCut(
    val beatTimeMs: Long,
    val clipIndex: Int,
    val startOffsetMs: Long
)

// ---- Smart Reframe ----

data class ReframeKeyframe(
    val timeMs: Long,
    val panX: Float,
    val panY: Float,
    val zoom: Float
)

// ---- Script-to-Video ----

data class ScriptSegment(
    val keyword: String,
    val durationMs: Long,
    val clipIndex: Int?
)

// ---- AI Auto-Edit / Highlight Reel ----

data class AutoEditClip(
    val uri: Uri,
    val durationMs: Long
)

data class AutoEditResult(
    val segments: List<AutoEditSegment> = emptyList(),
    val transitionPoints: List<Long> = emptyList()
)

data class AutoEditSegment(
    val clipIndex: Int,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val timelineStartMs: Long
)

// ---- AI Noise Reduction Analysis ----

enum class NoiseType {
    HISS, HUM, BROADBAND, CLEAN
}

data class RecommendedEffect(
    val type: AudioEffectType,
    val params: Map<String, Float>
)

data class SpectralNoiseProfile(
    val noiseType: NoiseType = NoiseType.CLEAN,
    val noiseFloorDb: Float = -60f,
    val recommendedEffects: List<RecommendedEffect> = emptyList()
)
