package com.novacut.editor.engine.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

enum class SegmentationModelState {
    NOT_DOWNLOADED, DOWNLOADING, READY, ERROR
}

data class SegmentationResult(
    val mask: ByteArray,
    val width: Int,
    val height: Int,
    val confidence: Float
) {
    override fun equals(other: Any?): Boolean =
        other is SegmentationResult && mask.contentEquals(other.mask)
    override fun hashCode(): Int = mask.contentHashCode()
}

@Singleton
class SegmentationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelDir = File(context.filesDir, "mediapipe")
    private val modelFile = File(modelDir, "selfie_segmenter.tflite")

    private val _modelState = MutableStateFlow(
        if (modelFile.exists() && modelFile.length() > 1000)
            SegmentationModelState.READY else SegmentationModelState.NOT_DOWNLOADED
    )
    val modelState: StateFlow<SegmentationModelState> = _modelState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    @Volatile private var segmenter: ImageSegmenter? = null

    companion object {
        private const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float32/latest/selfie_segmenter.tflite"

        fun estimateModelSizeMB(): Int = 1 // ~256KB
    }

    fun isReady(): Boolean = _modelState.value == SegmentationModelState.READY

    /**
     * Download the selfie segmenter model (~256KB) from Google's model storage.
     */
    suspend fun downloadModel(
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _modelState.value = SegmentationModelState.DOWNLOADING
            _downloadProgress.value = 0f
            modelDir.mkdirs()

            if (modelFile.exists() && modelFile.length() > 1000) {
                _modelState.value = SegmentationModelState.READY
                _downloadProgress.value = 1f
                onProgress(1f)
                return@withContext true
            }

            val tempFile = File(modelDir, "selfie_segmenter.tflite.tmp")
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "NovaCut/1.8.0")
            conn.connect()

            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode}")
            }

            val totalBytes = conn.contentLengthLong
            var downloaded = 0L

            conn.inputStream.buffered().use { input ->
                tempFile.outputStream().buffered().use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        val progress = if (totalBytes > 0) {
                            downloaded.toFloat() / totalBytes
                        } else 0.5f
                        _downloadProgress.value = progress.coerceIn(0f, 0.99f)
                        onProgress(_downloadProgress.value)
                    }
                }
            }

            tempFile.renameTo(modelFile)
            _downloadProgress.value = 1f
            onProgress(1f)
            _modelState.value = SegmentationModelState.READY
            true
        } catch (e: Exception) {
            _modelState.value = SegmentationModelState.ERROR
            _downloadProgress.value = 0f
            false
        }
    }

    /**
     * Run selfie segmentation on a Bitmap.
     * Returns a confidence mask (byte array, one byte per pixel, 0-255).
     */
    fun segment(bitmap: Bitmap): SegmentationResult? {
        if (!isReady()) return null
        val seg = getOrCreateSegmenter() ?: return null

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: ImageSegmenterResult = seg.segment(mpImage)
            val masks = result.confidenceMasks()
            if (!masks.isPresent || masks.get().isEmpty()) return null

            // First confidence mask = person mask
            val maskImage = masks.get()[0]
            val w = maskImage.width
            val h = maskImage.height

            // Extract float confidence values from MPImage via ByteBufferExtractor
            val floatBuffer = ByteBufferExtractor.extract(maskImage)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
            val maskBytes = ByteArray(w * h)
            var totalConfidence = 0f
            for (i in 0 until minOf(floatBuffer.remaining(), w * h)) {
                val confidence = floatBuffer.get()
                maskBytes[i] = (confidence * 255f).toInt().coerceIn(0, 255).toByte()
                totalConfidence += confidence
            }

            val avgConfidence = totalConfidence / (w * h)

            SegmentationResult(
                mask = maskBytes,
                width = w,
                height = h,
                confidence = avgConfidence
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Run segmentation on a video frame extracted from a URI.
     */
    suspend fun segmentVideoFrame(
        videoUri: Uri,
        timestampMs: Long = -1
    ): SegmentationResult? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext null

            val targetMs = if (timestampMs < 0) durationMs / 2 else timestampMs
            val frame = retriever.getFrameAtTime(
                targetMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext null

            // Downscale for faster segmentation
            val scale = 256f / maxOf(frame.width, frame.height)
            val scaledW = (frame.width * scale).toInt().coerceAtLeast(1)
            val scaledH = (frame.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(frame, scaledW, scaledH, true)
            if (scaled !== frame) frame.recycle()

            val result = segment(scaled)
            scaled.recycle()
            result
        } catch (e: Exception) {
            android.util.Log.w("SegmentationEngine", "Frame segmentation failed for $videoUri", e)
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Create a GlEffect for the Media3 export pipeline that applies per-frame segmentation.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun createExportEffect(threshold: Float = 0.5f): SegmentationGlEffect {
        return SegmentationGlEffect(this, threshold)
    }

    private fun getOrCreateSegmenter(): ImageSegmenter? {
        segmenter?.let { return it }
        if (!modelFile.exists()) return null

        return try {
            val modelBytes = modelFile.readBytes()
            val baseOptions = BaseOptions.builder()
                .setModelAssetBuffer(ByteBuffer.wrap(modelBytes))
                .build()

            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)
                .setOutputConfidenceMasks(true)
                .build()

            ImageSegmenter.createFromOptions(context, options).also {
                segmenter = it
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteModel() {
        segmenter?.close()
        segmenter = null
        modelDir.deleteRecursively()
        _modelState.value = SegmentationModelState.NOT_DOWNLOADED
        _downloadProgress.value = 0f
    }

    fun release() {
        segmenter?.close()
        segmenter = null
    }
}
