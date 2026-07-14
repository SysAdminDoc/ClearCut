package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ReframeModelState {
    NOT_DOWNLOADED, DOWNLOADING, READY, ERROR
}

@Singleton
class SmartReframeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
    private val mediaPipeGate: MediaPipeUsageGate
) {
    enum class ReframeStrategy {
        STATIONARY,
        PAN,
        TRACK
    }

    data class ReframeConfig(
        val targetAspectRatio: Float = 9f / 16f,
        val smoothingFactor: Float = 0.08f,
        val strategy: ReframeStrategy = ReframeStrategy.PAN,
        val padding: Float = 0.1f
    )

    data class CropWindow(
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float
    )

    data class ReframeResult(
        val cropWindows: List<CropWindow>,
        val frameRate: Float,
        val strategy: ReframeStrategy
    )

    private val modelDir = File(context.filesDir, "mediapipe")
    private val faceModelFile = File(modelDir, "blaze_face_short_range.tflite")

    private val _modelState = MutableStateFlow(
        if (hasDownloadedModelFile()) ReframeModelState.READY else ReframeModelState.NOT_DOWNLOADED
    )
    val modelState: StateFlow<ReframeModelState> = _modelState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    @Volatile private var faceDetector: FaceDetector? = null

    init {
        // Revoking MediaPipe consent must tear down any live face detector.
        mediaPipeGate.registerRevocationHandler(::closeDetectorOnRevoke)
    }

    @Synchronized
    private fun closeDetectorOnRevoke() {
        faceDetector?.close()
        faceDetector = null
    }

    private fun hasDownloadedModelFile(): Boolean =
        faceModelFile.exists() && faceModelFile.length() >= MIN_MODEL_BYTES

    private fun hasVerifiedModelFile(): Boolean =
        ModelDownloadManager.verifyChecksumOrDelete(
            file = faceModelFile,
            minimumBytes = MIN_MODEL_BYTES,
            expectedSha256 = MODEL_SHA256,
        )

    fun refreshModelState(): ReframeModelState {
        val state = when {
            !hasDownloadedModelFile() -> ReframeModelState.NOT_DOWNLOADED
            hasVerifiedModelFile() -> ReframeModelState.READY
            else -> ReframeModelState.ERROR
        }
        _modelState.value = state
        return state
    }

    fun isReady(): Boolean =
        _modelState.value == ReframeModelState.READY && hasVerifiedModelFile()

    suspend fun downloadModel(
        wifiOnly: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _modelState.value = ReframeModelState.DOWNLOADING
            _downloadProgress.value = 0f

            modelDir.mkdirs()

            val modelFile = ModelDownloadManager.ModelFile(
                url = MODEL_URL,
                targetFile = faceModelFile,
                minimumBytes = MIN_MODEL_BYTES,
                estimatedBytes = MODEL_ESTIMATED_BYTES,
                maxBytes = MODEL_ESTIMATED_BYTES,
                displayName = "BlazeFace face detector",
                sha256 = MODEL_SHA256,
                checksumRequired = true
            )

            modelDownloadManager.downloadFiles(
                files = listOf(modelFile),
                wifiOnly = wifiOnly,
                onProgress = { progress ->
                    _downloadProgress.value = progress
                    onProgress(progress)
                }
            )

            val verified = hasVerifiedModelFile()
            _modelState.value = if (verified) ReframeModelState.READY else ReframeModelState.ERROR
            _downloadProgress.value = if (verified) 1f else 0f
            verified
        } catch (e: Exception) {
            Log.e(TAG, "Face detector model download failed", e)
            _modelState.value = ReframeModelState.ERROR
            _downloadProgress.value = 0f
            false
        }
    }

    @Synchronized
    private fun getOrCreateDetector(): FaceDetector? {
        faceDetector?.let { return it }
        // P0: never construct a MediaPipe Tasks object before explicit consent.
        if (!mediaPipeGate.isConsented()) return null
        if (!hasVerifiedModelFile()) return null
        return try {
            val modelBytes = faceModelFile.readBytes()
            val baseOptions = BaseOptions.builder()
                .setModelAssetBuffer(java.nio.ByteBuffer.wrap(modelBytes))
                .build()
            val options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)
                .setMinDetectionConfidence(0.5f)
                .build()
            FaceDetector.createFromOptions(context, options).also { faceDetector = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create FaceDetector", e)
            null
        }
    }

    suspend fun analyzeForReframe(
        uri: Uri,
        config: ReframeConfig = ReframeConfig(),
        onProgress: (Float) -> Unit = {}
    ): ReframeResult? = withContext(Dispatchers.Default) {
        val detector = getOrCreateDetector()
        if (detector == null) {
            Log.w(TAG, "analyzeForReframe: face detector model not available")
            return@withContext null
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext null

            val videoWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: return@withContext null

            val videoHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: return@withContext null

            if (videoWidth <= 0 || videoHeight <= 0 || durationMs <= 0) return@withContext null

            val fps = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )?.toFloatOrNull() ?: 30f

            val sampleIntervalMs = 500L
            val sampleCount = ((durationMs / sampleIntervalMs) + 1).toInt().coerceIn(2, 600)

            val rawCenters = ArrayList<Pair<Float, Float>>(sampleCount)

            for (i in 0 until sampleCount) {
                ensureActive()
                val timeMs = (i * sampleIntervalMs).coerceAtMost(durationMs)
                onProgress(i.toFloat() / sampleCount)

                val frame = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue

                val scaled = if (frame.width > ANALYSIS_SIZE || frame.height > ANALYSIS_SIZE) {
                    val scale = ANALYSIS_SIZE.toFloat() / maxOf(frame.width, frame.height)
                    val w = (frame.width * scale).toInt().coerceAtLeast(1)
                    val h = (frame.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(frame, w, h, true).also {
                        if (it !== frame) frame.recycle()
                    }
                } else frame

                try {
                    val mpImage = BitmapImageBuilder(scaled).build()
                    val result = detector.detect(mpImage)

                    val center = if (result.detections().isNotEmpty()) {
                        val best = result.detections().maxBy { it.categories().firstOrNull()?.score() ?: 0f }
                        val box = best.boundingBox()
                        val cx = (box.centerX() / scaled.width).coerceIn(0f, 1f)
                        val cy = (box.centerY() / scaled.height).coerceIn(0f, 1f)
                        Pair(cx, cy)
                    } else {
                        Pair(0.5f, 0.5f)
                    }
                    rawCenters.add(center)
                } catch (e: Exception) {
                    Log.w(TAG, "Face detection failed at ${timeMs}ms", e)
                    rawCenters.add(Pair(0.5f, 0.5f))
                } finally {
                    if (scaled !== frame) scaled.recycle()
                }
            }

            if (rawCenters.size < 2) return@withContext null

            onProgress(0.9f)

            val smoothed = smoothCropTrajectory(rawCenters, config.smoothingFactor)

            val sourceAspect = videoWidth.toFloat() / videoHeight
            val targetAspect = config.targetAspectRatio
            val cropW: Float
            val cropH: Float
            if (targetAspect < sourceAspect) {
                cropH = 1f - config.padding * 2
                cropW = cropH * targetAspect / sourceAspect
            } else {
                cropW = 1f - config.padding * 2
                cropH = cropW * sourceAspect / targetAspect
            }

            val effectiveStrategy = resolveStrategy(rawCenters, config.strategy)

            val cropWindows = when (effectiveStrategy) {
                ReframeStrategy.STATIONARY -> {
                    val avgX = smoothed.map { it.first }.average().toFloat()
                    val avgY = smoothed.map { it.second }.average().toFloat()
                    val cx = avgX.coerceIn(cropW / 2, 1f - cropW / 2)
                    val cy = avgY.coerceIn(cropH / 2, 1f - cropH / 2)
                    smoothed.map { CropWindow(cx, cy, cropW, cropH) }
                }
                ReframeStrategy.PAN, ReframeStrategy.TRACK -> {
                    smoothed.map { (sx, sy) ->
                        CropWindow(
                            centerX = sx.coerceIn(cropW / 2, 1f - cropW / 2),
                            centerY = sy.coerceIn(cropH / 2, 1f - cropH / 2),
                            width = cropW,
                            height = cropH
                        )
                    }
                }
            }

            onProgress(1f)
            ReframeResult(
                cropWindows = cropWindows,
                frameRate = fps.coerceIn(1f, 120f),
                strategy = effectiveStrategy
            )
        } catch (e: Exception) {
            Log.e(TAG, "analyzeForReframe failed", e)
            null
        } finally {
            retriever.release()
        }
    }

    fun smoothCropTrajectory(
        centers: List<Pair<Float, Float>>,
        alpha: Float = 0.08f
    ): List<Pair<Float, Float>> = Companion.smoothCropTrajectory(centers, alpha)

    fun release() {
        faceDetector?.close()
        faceDetector = null
    }

    private fun resolveStrategy(
        rawCenters: List<Pair<Float, Float>>,
        preferred: ReframeStrategy
    ): ReframeStrategy = Companion.resolveStrategy(rawCenters, preferred)

    companion object {
        private const val TAG = "SmartReframe"
        private const val ANALYSIS_SIZE = 256

        fun smoothCropTrajectory(
            centers: List<Pair<Float, Float>>,
            alpha: Float = 0.08f
        ): List<Pair<Float, Float>> {
            if (centers.size < 2) return centers
            val a = if (alpha.isFinite()) alpha.coerceIn(0f, 1f) else 0.08f
            val smoothed = ArrayList<Pair<Float, Float>>(centers.size)
            smoothed.add(centers.first())
            for (i in 1 until centers.size) {
                val prevX = smoothed.last().first
                val prevY = smoothed.last().second
                val newX = prevX + a * (centers[i].first - prevX)
                val newY = prevY + a * (centers[i].second - prevY)
                smoothed.add(Pair(newX, newY))
            }
            return smoothed
        }

        fun resolveStrategy(
            rawCenters: List<Pair<Float, Float>>,
            preferred: SmartReframeEngine.ReframeStrategy
        ): SmartReframeEngine.ReframeStrategy {
            if (preferred != SmartReframeEngine.ReframeStrategy.PAN) return preferred
            val maxDrift = rawCenters.zipWithNext().maxOfOrNull { (a, b) ->
                val dx = b.first - a.first
                val dy = b.second - a.second
                kotlin.math.sqrt(dx * dx + dy * dy)
            } ?: 0f
            return if (maxDrift < 0.02f) SmartReframeEngine.ReframeStrategy.STATIONARY
                else SmartReframeEngine.ReframeStrategy.PAN
        }
        private const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/blaze_face_short_range.tflite"
        private const val MODEL_SHA256 =
            "d1ac21e4a9430097e4c272c8e1af6e6b1ecbf997a49e26e52c18e77b0c5bd80f"
        private const val MIN_MODEL_BYTES = 32L * 1024L
        private const val MODEL_ESTIMATED_BYTES = 198_832L
    }
}
