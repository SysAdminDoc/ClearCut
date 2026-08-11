package com.novacut.editor.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.graphics.scale
import com.novacut.editor.model.Easing
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeInterpolation
import com.novacut.editor.model.KeyframeProperty
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Offline, bounded camera-motion analysis.
 *
 * The implementation deliberately keeps the motion data separate from the source
 * pixels. It samples small frames with the platform retriever, estimates a sparse
 * translation field, smooths the camera trajectory, and returns counter-motion
 * keyframes. The existing Media3 [EffectBuilder] consumes those keyframes in both
 * preview and export, so stabilization is reversible and never replaces the source
 * clip with a baked intermediate.
 */
@Singleton
class StabilizationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "StabilizationEngine"
        private const val ANALYSIS_WIDTH = 64
        private const val ANALYSIS_HEIGHT = 36
        private const val MAX_ANALYSIS_FRAMES = 1_800
        private const val THERMAL_BLOCKING_STATUS = 3

        const val GYROFLOW_PROJECT_FILE_EXTENSION = "gyroflow"
        const val GYROFLOW_PROJECT_SOURCE_URL = "https://github.com/gyroflow/gyroflow"
    }

    data class StabilizationConfig(
        val smoothingStrength: Float = 0.5f,
        val cropPercentage: Float = 0.15f,
        val algorithm: Algorithm = Algorithm.LK_OPTICAL_FLOW,
        val maxFeatures: Int = 300,
        val useAffine: Boolean = true,
        val analysisIntervalMs: Long? = null,
        val smoothingWindow: Int = 5,
    ) {
        init {
            require(smoothingStrength in 0f..1f) { "Smoothing strength must be in [0, 1]" }
            require(cropPercentage in 0f..0.3f) { "Crop percentage must be in [0, 0.3]" }
            require(maxFeatures > 0) { "maxFeatures must be positive" }
            require(smoothingWindow > 0) { "smoothingWindow must be positive" }
            analysisIntervalMs?.let { require(it > 0L) { "analysisIntervalMs must be positive" } }
        }

        enum class Algorithm(val description: String) {
            LK_OPTICAL_FLOW("Local sparse block flow"),
            ORB_FEATURES("Large-motion block flow"),
        }
    }

    data class LensProfile(
        val name: String = "android-identity",
        val focalLengthMm: Float? = null,
        val distortionK1: Float = 0f,
        val distortionK2: Float = 0f,
    )

    data class AnalysisCapability(
        val supported: Boolean,
        val reason: String? = null,
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val maxDurationMs: Long = 0L,
        val analysisIntervalMs: Long = 0L,
        val isLowRamDevice: Boolean = false,
        val thermalStatus: ThermalHeadroomPolicy.ThermalStatus = ThermalHeadroomPolicy.ThermalStatus.NONE,
    )

    data class FrameTransform(
        val frameIndex: Int,
        val timestampMs: Long,
        /** Normalized source-frame displacement, before trajectory smoothing. */
        val dx: Float,
        val dy: Float,
        val dAngle: Float = 0f,
        val dScale: Float = 1f,
        val confidence: Float = 1f,
    )

    data class MotionData(
        val transforms: List<FrameTransform>,
        /** Counter-motion transforms consumed by the shared render path. */
        val smoothedTransforms: List<FrameTransform>,
        val averageShakeMagnitude: Float,
        val maxShakeMagnitude: Float,
        val analysisTimeMs: Long,
        val frameCount: Int,
        val fps: Float,
        val sourceDurationMs: Long = 0L,
        val lensProfile: LensProfile = LensProfile(),
        val syncOffsetMs: Long = 0L,
        val recommendedCropScale: Float = 1f,
    )

    fun capability(
        uri: Uri? = null,
        config: StabilizationConfig = StabilizationConfig(),
    ): AnalysisCapability {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val isLowRam = activityManager?.isLowRamDevice == true
        val thermalStatus = currentThermalStatus()
        val maxDuration = StabilizationPolicy.maximumDurationMs(isLowRam)
        if (thermalStatus.osValue >= THERMAL_BLOCKING_STATUS) {
            return AnalysisCapability(
                supported = false,
                reason = "Device thermal status is ${thermalStatus.name.lowercase()}; let it cool before analysis.",
                maxDurationMs = maxDuration,
                isLowRamDevice = isLowRam,
                thermalStatus = thermalStatus,
            )
        }

        if (uri == null) {
            return AnalysisCapability(
                supported = true,
                maxDurationMs = maxDuration,
                analysisIntervalMs = config.analysisIntervalMs ?: 0L,
                isLowRamDevice = isLowRam,
                thermalStatus = thermalStatus,
            )
        }

        val metadata = readVideoMetadata(uri)
            ?: return AnalysisCapability(
                supported = false,
                reason = "The clip has no readable video metadata.",
                maxDurationMs = maxDuration,
                isLowRamDevice = isLowRam,
                thermalStatus = thermalStatus,
            )
        if (metadata.durationMs <= 0L || metadata.width <= 0 || metadata.height <= 0) {
            return AnalysisCapability(
                supported = false,
                reason = "The clip has no usable duration or video dimensions.",
                durationMs = metadata.durationMs,
                width = metadata.width,
                height = metadata.height,
                maxDurationMs = maxDuration,
                isLowRamDevice = isLowRam,
                thermalStatus = thermalStatus,
            )
        }
        if (metadata.durationMs > maxDuration) {
            val limitSeconds = maxDuration / 1_000L
            return AnalysisCapability(
                supported = false,
                reason = "This device limits offline stabilization analysis to ${limitSeconds}s per clip.",
                durationMs = metadata.durationMs,
                width = metadata.width,
                height = metadata.height,
                maxDurationMs = maxDuration,
                analysisIntervalMs = analysisInterval(config, isLowRam, metadata.width, metadata.height),
                isLowRamDevice = isLowRam,
                thermalStatus = thermalStatus,
            )
        }

        return AnalysisCapability(
            supported = true,
            durationMs = metadata.durationMs,
            width = metadata.width,
            height = metadata.height,
            maxDurationMs = maxDuration,
            analysisIntervalMs = analysisInterval(config, isLowRam, metadata.width, metadata.height),
            isLowRamDevice = isLowRam,
            thermalStatus = thermalStatus,
        )
    }

    suspend fun analyzeMotion(
        uri: Uri,
        config: StabilizationConfig = StabilizationConfig(),
        onProgress: (Float) -> Unit = {},
    ): MotionData? = withContext(Dispatchers.IO) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val capability = capability(uri, config)
        if (!capability.supported) {
            Log.i(TAG, "Stabilization unavailable: ${capability.reason}")
            return@withContext null
        }

        val retrieverLease = CodecInstanceBudget.acquireRetriever(context.contentResolver.getType(uri))
        val retriever = retrieverLease.resource
        var previousFrame: Bitmap? = null
        try {
            retriever.setDataSource(context, uri)
            val durationMs = capability.durationMs
            val intervalMs = capability.analysisIntervalMs
                .takeIf { it > 0L }
                ?: analysisInterval(
                    config,
                    capability.isLowRamDevice,
                    capability.width,
                    capability.height,
                )
            val sampleCount = ((durationMs + intervalMs - 1L) / intervalMs)
                .toInt()
                .coerceIn(2, MAX_ANALYSIS_FRAMES)
            val samples = ArrayList<StabilizationPolicy.MotionSample>(sampleCount)
            var decodedFrameCount = 0

            for (index in 0 until sampleCount) {
                currentCoroutineContext().ensureActive()
                val liveThermal = currentThermalStatus()
                if (liveThermal.osValue >= THERMAL_BLOCKING_STATUS) {
                    throw StabilizationUnavailableException(
                        "Device thermal status is ${liveThermal.name.lowercase()}; analysis stopped safely.",
                    )
                }
                val timestampMs = (index * intervalMs).coerceAtMost((durationMs - 1L).coerceAtLeast(0L))
                val decoded = retriever.getFrameAtTime(
                    timestampMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                ) ?: continue
                val frame = try {
                    decoded.scale(ANALYSIS_WIDTH, ANALYSIS_HEIGHT, true)
                } catch (error: Throwable) {
                    Log.w(TAG, "Could not scale stabilization frame", error)
                    null
                }
                if (frame == null) {
                    decoded.recycle()
                    continue
                }
                if (frame !== decoded) decoded.recycle()

                decodedFrameCount++
                previousFrame?.let { previous ->
                    val estimate = estimateMotion(
                        previous,
                        frame,
                        searchRange = if (config.algorithm == StabilizationConfig.Algorithm.ORB_FEATURES) 6 else 4,
                    )
                    samples += StabilizationPolicy.MotionSample(
                        timestampMs = timestampMs,
                        dx = estimate.dx,
                        dy = estimate.dy,
                        confidence = estimate.confidence,
                    )
                    previous.recycle()
                }
                previousFrame = frame
                onProgress((index + 1).toFloat() / sampleCount.toFloat() * 0.95f)
            }

            if (samples.isEmpty()) return@withContext null
            val corrections = StabilizationPolicy.correctionTrajectory(
                samples = samples,
                windowSize = config.smoothingWindow,
            )
            val rawTransforms = samples.mapIndexed { index, sample ->
                FrameTransform(
                    frameIndex = index + 1,
                    timestampMs = sample.timestampMs,
                    dx = sample.dx,
                    dy = sample.dy,
                    confidence = sample.confidence,
                )
            }
            val smoothedTransforms = corrections.mapIndexed { index, correction ->
                FrameTransform(
                    frameIndex = index + 1,
                    timestampMs = correction.timestampMs,
                    dx = correction.dx * config.smoothingStrength,
                    dy = correction.dy * config.smoothingStrength,
                    confidence = correction.confidence,
                )
            }
            val smoothedCorrections = corrections.mapIndexed { index, correction ->
                correction.copy(
                    dx = correction.dx * config.smoothingStrength,
                    dy = correction.dy * config.smoothingStrength,
                )
            }
            val fps = readFrameRate(retriever) ?: 30f
            onProgress(1f)
            MotionData(
                transforms = rawTransforms,
                smoothedTransforms = smoothedTransforms,
                averageShakeMagnitude = StabilizationPolicy.averageShakeMagnitude(samples),
                maxShakeMagnitude = StabilizationPolicy.maxShakeMagnitude(samples),
                analysisTimeMs = android.os.SystemClock.elapsedRealtime() - startedAt,
                frameCount = decodedFrameCount,
                fps = fps,
                sourceDurationMs = durationMs,
                recommendedCropScale = StabilizationPolicy.recommendedCropScale(
                    smoothedCorrections,
                    config.cropPercentage,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: StabilizationUnavailableException) {
            Log.i(TAG, error.message.orEmpty())
            null
        } catch (error: Exception) {
            Log.w(TAG, "Offline stabilization analysis failed", error)
            null
        } finally {
            previousFrame?.recycle()
            retrieverLease.close()
        }
    }

    /**
     * Convert motion data into the same normalized keyframe space used by the
     * editor's ordinary transform controls. This keeps preview and export on
     * [EffectBuilder.addOpacityAndTransformEffects].
     */
    fun keyframesFor(
        motionData: MotionData,
        sourceToTimelineOffsetMs: (Long) -> Long?,
        clipDurationMs: Long,
    ): List<Keyframe> = motionData.smoothedTransforms.flatMap { transform ->
        val timeOffsetMs = sourceToTimelineOffsetMs(transform.timestampMs)
            ?.coerceIn(0L, clipDurationMs)
            ?: return@flatMap emptyList()
        listOf(
            Keyframe(
                timeOffsetMs = timeOffsetMs,
                property = KeyframeProperty.POSITION_X,
                value = transform.dx.coerceIn(-1f, 1f),
                easing = Easing.LINEAR,
                interpolation = KeyframeInterpolation.LINEAR,
            ),
            Keyframe(
                timeOffsetMs = timeOffsetMs,
                property = KeyframeProperty.POSITION_Y,
                value = transform.dy.coerceIn(-1f, 1f),
                easing = Easing.LINEAR,
                interpolation = KeyframeInterpolation.LINEAR,
            ),
        )
    }.distinctBy { it.timeOffsetMs to it.property }

    private fun analysisInterval(
        config: StabilizationConfig,
        isLowRamDevice: Boolean,
        width: Int,
        height: Int,
    ): Long = config.analysisIntervalMs?.coerceIn(50L, 1_000L)
        ?: StabilizationPolicy.analysisIntervalMs(isLowRamDevice, width, height)

    private fun currentThermalStatus(): ThermalHeadroomPolicy.ThermalStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ThermalHeadroomPolicy.ThermalStatus.NONE
        }
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return ThermalHeadroomPolicy.ThermalStatus.NONE
        return runCatching {
            ThermalHeadroomPolicy.ThermalStatus.fromOs(powerManager.currentThermalStatus)
        }.getOrDefault(ThermalHeadroomPolicy.ThermalStatus.NONE)
    }

    private fun readVideoMetadata(uri: Uri): VideoMetadata? {
        val lease = CodecInstanceBudget.acquireRetrieverBlocking(context.contentResolver.getType(uri))
        val retriever = lease.resource
        return try {
            retriever.setDataSource(context, uri)
            VideoMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                    ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                    ?: 0,
            )
        } catch (error: Exception) {
            Log.w(TAG, "Could not inspect stabilization source", error)
            null
        } finally {
            lease.close()
        }
    }

    private fun readFrameRate(retriever: MediaMetadataRetriever): Float? =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it > 0f }

    private data class VideoMetadata(val durationMs: Long, val width: Int, val height: Int)

    private data class MotionEstimate(val dx: Float, val dy: Float, val confidence: Float)

    private fun estimateMotion(prev: Bitmap, curr: Bitmap, searchRange: Int): MotionEstimate {
        val width = minOf(prev.width, curr.width)
        val height = minOf(prev.height, curr.height)
        if (width < 8 || height < 8) return MotionEstimate(0f, 0f, 0f)
        val previousPixels = IntArray(width * height)
        val currentPixels = IntArray(width * height)
        prev.getPixels(previousPixels, 0, width, 0, 0, width, height)
        curr.getPixels(currentPixels, 0, width, 0, 0, width, height)

        val radius = (minOf(width, height) / 4).coerceAtLeast(2)
        val centerX = width / 2
        val centerY = height / 2
        var bestDx = 0
        var bestDy = 0
        var bestDiff = Long.MAX_VALUE
        var secondBestDiff = Long.MAX_VALUE
        for (dy in -searchRange..searchRange) {
            for (dx in -searchRange..searchRange) {
                var difference = 0L
                var count = 0
                for (blockY in -radius..radius step 2) {
                    for (blockX in -radius..radius step 2) {
                        val previousX = centerX + blockX
                        val previousY = centerY + blockY
                        val currentX = previousX + dx
                        val currentY = previousY + dy
                        if (previousX !in 0 until width || previousY !in 0 until height ||
                            currentX !in 0 until width || currentY !in 0 until height
                        ) continue
                        val previousPixel = previousPixels[previousY * width + previousX]
                        val currentPixel = currentPixels[currentY * width + currentX]
                        difference += abs((previousPixel shr 16 and 0xFF) - (currentPixel shr 16 and 0xFF))
                        difference += abs((previousPixel shr 8 and 0xFF) - (currentPixel shr 8 and 0xFF))
                        difference += abs((previousPixel and 0xFF) - (currentPixel and 0xFF))
                        count++
                    }
                }
                if (count == 0) continue
                val averageDifference = difference / count
                if (averageDifference < bestDiff) {
                    secondBestDiff = bestDiff
                    bestDiff = averageDifference
                    bestDx = dx
                    bestDy = dy
                } else if (averageDifference < secondBestDiff) {
                    secondBestDiff = averageDifference
                }
            }
        }
        val separation = if (secondBestDiff == Long.MAX_VALUE || secondBestDiff <= 0L) {
            0f
        } else {
            ((secondBestDiff - bestDiff).toFloat() / secondBestDiff.toFloat()).coerceIn(0f, 1f)
        }
        return MotionEstimate(
            dx = bestDx.toFloat() / width.toFloat(),
            dy = bestDy.toFloat() / height.toFloat(),
            confidence = separation,
        )
    }
}

class StabilizationUnavailableException(message: String) : IllegalStateException(message)
