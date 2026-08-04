package com.novacut.editor.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.novacut.editor.model.Mask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image/video inpainting engine powered by LaMa (Large Mask Inpainting) with dilated convolutions.
 *
 * ## Open Source Project
 * - **LaMa**: https://github.com/advimman/lama
 * - License: Apache 2.0
 * - Paper: "Resolution-robust Large Mask Inpainting with Fourier Convolutions" (WACV 2022)
 * - Qualcomm AI Hub optimized variant: https://aihub.qualcomm.com/models/lama_dilated
 *
 * ## Model Details
 * - Architecture: LaMa with dilated convolutions (replaces FFT convolutions for mobile)
 * - Model size: ~174MB (ONNX/TFLite quantized)
 * - Input: RGB image + binary mask (white = region to inpaint)
 * - Output: Inpainted RGB image with masked region filled
 * - Performance: ~40ms/frame @ 512x512 on Qualcomm Snapdragon 8 Gen 2 (NPU)
 * - Handles arbitrary mask shapes including large irregular regions
 *
 * ## Android Integration Path
 * Two options for on-device inference:
 *
 * ### Option A: Qualcomm AI Hub (recommended for Snapdragon devices)
 * 1. Export model via `qai_hub.submit_compile_job(model, device=Device("Samsung Galaxy S24"))`
 * 2. Deploy via Qualcomm AI Engine Direct SDK
 * 3. Leverages Hexagon NPU for optimal performance
 *
 * ### Option B: ONNX Runtime (cross-device, currently active)
 * 1. `implementation("com.microsoft.onnxruntime:onnxruntime-android:1.26.0")` is already in
 *    [gradle/libs.versions.toml](../../../../../../gradle/libs.versions.toml).
 * 2. Load LaMa ONNX model from the cache populated by `ModelDownloadManager`.
 * 3. Run inference via `OrtSession` with execution providers in this order:
 *    XNNPACK (Arm CPU SIMD, ships with onnxruntime-android), then CPU fallback.
 *    The legacy NNAPI EP is intentionally **not** added — NNAPI is deprecated in
 *    Android 15 (API 35) and removed from Google's recommended path. See
 *    https://developer.android.com/ndk/guides/neuralnetworks/migration-guide.
 *    For Qualcomm NPU acceleration on supported Snapdragon devices, the QNN EP
 *    or Option A (Qualcomm AI Engine Direct SDK) is the forward path. For a
 *    future TFLite-backed engine, target LiteRT's CompiledModel API instead.
 *
 * ## Dependencies (already present in build.gradle.kts)
 * ```
 * implementation("com.microsoft.onnxruntime:onnxruntime-android:1.26.0")
 * ```
 *
 * See ROADMAP.md R6.2 (LiteRT migration / NNAPI deprecation surface).
 */
enum class InpaintingModelState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    ERROR
}

@Singleton
class InpaintingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
    private val ffmpegEngine: FFmpegEngine
) {
    private val modelDir = File(context.filesDir, "models/inpainting")
    private val modelFile = File(modelDir, MODEL_FILENAME)
    private val _modelState = MutableStateFlow(
        if (isModelReady()) InpaintingModelState.READY else InpaintingModelState.NOT_DOWNLOADED
    )
    val modelState: StateFlow<InpaintingModelState> = _modelState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    companion object {
        private const val TAG = "InpaintingEngine"
        private const val MODEL_FILENAME = "lama_dilated.onnx"
        private const val MODEL_SIZE_BYTES = 182_781_794L // ~174 MiB
        private const val MODEL_INPUT_SIZE = 512
        private const val MODEL_URL =
            "https://huggingface.co/qualcomm/LaMa-Dilated/resolve/ab898502c9bd764a50eb2719a309694b43eae658/LaMa-Dilated.onnx"
        private const val MODEL_SHA256 =
            "6f9e1d401eb67a63fb1be6c0cf3283d800bf4c20656028f96b044fedc382d762"
    }

    /**
     * Result of an inpainting operation on a single frame.
     *
     * @param outputBitmap The inpainted frame with the masked region filled
     * @param processingTimeMs Time taken for inference in milliseconds
     * @param inputResolution Original input resolution (width x height)
     * @param processedResolution Resolution used for inference (may differ if downscaled)
     */
    data class InpaintingResult(
        val outputBitmap: Bitmap,
        val processingTimeMs: Long,
        val inputResolution: Pair<Int, Int>,
        val processedResolution: Pair<Int, Int>
    )

    /**
     * Result of a video inpainting operation.
     *
     * @param outputUri URI to the inpainted video file
     * @param framesProcessed Number of frames that were inpainted
     * @param totalProcessingTimeMs Total wall-clock time for the operation
     * @param averageFrameTimeMs Average inference time per frame
     */
    data class VideoInpaintingResult(
        val outputUri: Uri,
        val framesProcessed: Int,
        val totalProcessingTimeMs: Long,
        val averageFrameTimeMs: Long
    )

    /** Whether the LaMa model is downloaded and ready for inference. */
    fun isModelReady(): Boolean {
        return modelFile.exists() && modelFile.length() > MODEL_SIZE_BYTES / 2
    }

    private fun isVerifiedModelReady(): Boolean {
        return ModelDownloadManager.verifyChecksumOrDelete(
            file = modelFile,
            minimumBytes = MODEL_SIZE_BYTES / 2,
            expectedSha256 = MODEL_SHA256,
        )
    }

    /**
     * Download the LaMa-Dilated ONNX model to device storage.
     * Warning: This model is ~174MB. Ensure sufficient storage and show download progress.
     *
     * @param onProgress Progress callback in [0.0, 1.0]
     */
    suspend fun downloadModel(
        wifiOnly: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _modelState.value = InpaintingModelState.DOWNLOADING
            _downloadProgress.value = 0f
            modelDir.mkdirs()
            if (isVerifiedModelReady()) {
                _modelState.value = InpaintingModelState.READY
                _downloadProgress.value = 1f
                onProgress(1f)
                return@withContext true
            }
            Log.d(TAG, "Downloading LaMa-Dilated model from $MODEL_URL")
            modelDownloadManager.downloadFiles(
                files = listOf(
                    ModelDownloadManager.ModelFile(
                        url = MODEL_URL,
                        targetFile = modelFile,
                        minimumBytes = MODEL_SIZE_BYTES / 2,
                        estimatedBytes = MODEL_SIZE_BYTES,
                        maxBytes = MODEL_SIZE_BYTES,
                        displayName = "LaMa inpainting model",
                        sha256 = MODEL_SHA256,
                        checksumRequired = true
                    )
                ),
                totalEstimateBytes = MODEL_SIZE_BYTES,
                connectTimeoutMs = 30_000,
                readTimeoutMs = 30_000,
                wifiOnly = wifiOnly,
                onProgress = { progress ->
                    _downloadProgress.value = progress.coerceIn(0f, 0.99f)
                    onProgress(_downloadProgress.value)
                }
            )
            Log.d(TAG, "LaMa model downloaded: ${modelFile.length()} bytes")
            _downloadProgress.value = 1f
            onProgress(1f)
            if (isVerifiedModelReady()) {
                _modelState.value = InpaintingModelState.READY
                true
            } else {
                _modelState.value = InpaintingModelState.ERROR
                false
            }
        } catch (e: ModelDownloadManager.MeteredNetworkException) {
            _modelState.value = if (isModelReady()) {
                InpaintingModelState.READY
            } else {
                InpaintingModelState.NOT_DOWNLOADED
            }
            _downloadProgress.value = 0f
            throw e
        } catch (e: CancellationException) {
            _modelState.value = if (isModelReady()) {
                InpaintingModelState.READY
            } else {
                InpaintingModelState.NOT_DOWNLOADED
            }
            _downloadProgress.value = 0f
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download LaMa model", e)
            _modelState.value = if (isModelReady()) {
                InpaintingModelState.READY
            } else {
                InpaintingModelState.ERROR
            }
            _downloadProgress.value = 0f
            false
        }
    }

    /** Delete the downloaded model to free storage (~174MB). */
    fun deleteModel() {
        modelDir.deleteRecursively()
        _modelState.value = InpaintingModelState.NOT_DOWNLOADED
        _downloadProgress.value = 0f
    }

    /** Get the size of the downloaded model in bytes, or 0 if not downloaded. */
    fun getModelSizeBytes(): Long {
        val modelFile = File(context.filesDir, "models/inpainting/$MODEL_FILENAME")
        return if (modelFile.exists()) modelFile.length() else 0L
    }

    /**
     * Inpaint a single frame by removing the masked region and filling it with
     * content-aware synthesis.
     *
     * The mask should be a bitmap of the same dimensions as the input, where:
     * - White pixels (255) indicate regions to be removed/inpainted
     * - Black pixels (0) indicate regions to preserve
     *
     * @param bitmap Input frame to inpaint
     * @param mask Binary mask indicating the region to remove
     * @param onProgress Progress callback in [0.0, 1.0]
     * @return InpaintingResult with the inpainted bitmap, or null on failure
     */
    suspend fun inpaintFrame(
        bitmap: Bitmap,
        mask: Bitmap,
        onProgress: (Float) -> Unit = {}
    ): InpaintingResult? = withContext(Dispatchers.IO) {
        val pixelBytes = bitmap.byteCount.toLong()
        if (pixelBytes > NativeProcessingPolicy.MAX_IMAGE_INPUT_BYTES) {
            Log.w(TAG, "inpaintFrame: bitmap $pixelBytes bytes exceeds limit ${NativeProcessingPolicy.MAX_IMAGE_INPUT_BYTES}")
            return@withContext null
        }

        if (!isVerifiedModelReady()) {
            Log.w(TAG, "LaMa model not downloaded")
            return@withContext null
        }

        runInpainting(bitmap, mask, onProgress)
    }

    /**
     * Perform one inference after the caller has verified the model. Video
     * processing uses this private path so a 174 MB checksum is not recomputed
     * for every decoded frame.
     */
    private fun runInpainting(
        bitmap: Bitmap,
        mask: Bitmap,
        onProgress: (Float) -> Unit
    ): InpaintingResult? {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Inpainting frame: ${bitmap.width}x${bitmap.height}")
        return try {
            // ONNX Runtime inference for LaMa-Dilated
            val env = OrtEnvironment.getEnvironment()
            var sessionHandle: OnnxSessionFactory.SessionHandle? = null
            var inputBitmap: Bitmap? = null
            var maskBitmap: Bitmap? = null
            var imageTensor: OnnxTensor? = null
            var maskTensor: OnnxTensor? = null
            try {
                val modelPath = File(context.filesDir, "models/inpainting/$MODEL_FILENAME").absolutePath
                sessionHandle = OnnxSessionFactory.createSession(env, modelPath)
                val session = requireNotNull(sessionHandle).session

                // Preprocess: resize to 512x512, normalize to [0,1]
                inputBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
                maskBitmap = Bitmap.createScaledBitmap(mask, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)

                imageTensor = bitmapToFloatTensor(env, inputBitmap, channels = 3)  // [1, 3, 512, 512]
                maskTensor = bitmapToFloatTensor(env, maskBitmap, channels = 1)     // [1, 1, 512, 512]

                onProgress(0.3f)

                val results = session.run(mapOf("image" to imageTensor, "mask" to maskTensor))
                try {
                    val outputData = (results[0].value as Array<*>)

                    onProgress(0.8f)

                    // Postprocess: convert output tensor back to bitmap, resize to original dimensions
                    @Suppress("UNCHECKED_CAST")
                    val outputBitmap = floatTensorToBitmap(
                        outputData as Array<Array<Array<FloatArray>>>,
                        bitmap.width, bitmap.height
                    )

                    onProgress(1f)
                    Log.d(TAG, "LaMa inference completed in ${System.currentTimeMillis() - startTime}ms")
                    return InpaintingResult(
                        outputBitmap = outputBitmap,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        inputResolution = bitmap.width to bitmap.height,
                        processedResolution = MODEL_INPUT_SIZE to MODEL_INPUT_SIZE
                    )
                } finally {
                    results.close()
                }
            } finally {
                imageTensor?.close()
                maskTensor?.close()
                sessionHandle?.close()
                if (inputBitmap != null && inputBitmap !== bitmap) inputBitmap.recycle()
                if (maskBitmap != null && maskBitmap !== mask) maskBitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ONNX inference failed", e)
            null
        }
    }

    /** Inpaint a video using caller-provided masks for individual frame indices. */
    suspend fun inpaintVideo(
        uri: Uri,
        maskFrames: Map<Int, Bitmap>,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): VideoInpaintingResult? = inpaintVideoInternal(
        uri = uri,
        outputUri = outputUri,
        maskProvider = { frameIndex, _, _, _ -> maskFrames[frameIndex] },
        recycleProvidedMasks = false,
        onProgress = onProgress
    )

    /**
     * Inpaint a video from one editor mask. The mask is rendered at every
     * decoded frame, so keyframed/tracked geometry follows the clip without
     * allocating a full video's masks up front.
     */
    suspend fun inpaintVideo(
        uri: Uri,
        mask: Mask,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): VideoInpaintingResult? {
        if (!InpaintingMaskRenderer.supports(mask)) {
            Log.w(TAG, "Unsupported mask geometry for video inpainting: ${mask.type}")
            return null
        }
        return inpaintVideoInternal(
            uri = uri,
            outputUri = outputUri,
            maskProvider = { _, timeMs, width, height ->
                InpaintingMaskRenderer.render(mask, timeMs, width, height)
            },
            recycleProvidedMasks = true,
            onProgress = onProgress
        )
    }

    private suspend fun inpaintVideoInternal(
        uri: Uri,
        outputUri: Uri,
        maskProvider: (frameIndex: Int, timeMs: Long, width: Int, height: Int) -> Bitmap?,
        recycleProvidedMasks: Boolean,
        onProgress: (Float) -> Unit
    ): VideoInpaintingResult? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val v = NativeProcessingPolicy.validateVideoUri(context, uri, "inpaintVideo")
        if (v != null) {
            NativeProcessingPolicy.logAndReject(v)
            return@withContext null
        }
        // Verify once per export. Calling the public frame method here would
        // hash the whole model for every frame.
        if (!isVerifiedModelReady()) {
            Log.w(TAG, "LaMa model not downloaded")
            return@withContext null
        }

        val retrieverLease = CodecInstanceBudget.acquireRetriever(context.contentResolver.getType(uri))
        val retriever = retrieverLease.resource
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext null
            val videoWidth = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: return@withContext null
            val videoHeight = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: return@withContext null
            val parsedFps = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )?.toFloatOrNull()
            val fps = parsedFps?.takeIf { it.isFinite() && it > 0f } ?: 30f

            if (durationMs <= 0 || videoWidth <= 0 || videoHeight <= 0) return@withContext null

            val frameIntervalMs = (1000f / fps.coerceIn(1f, 120f)).toLong().coerceAtLeast(16L)
            val totalFrames = ((durationMs / frameIntervalMs) + 1).toInt().coerceIn(1, 9000)
            val outputFile = outputUri.path?.let(::File) ?: return@withContext null
            outputFile.parentFile?.mkdirs()

            val tempDir = File(context.cacheDir, "inpaint-frames-${System.currentTimeMillis()}")
            tempDir.mkdirs()
            var inpaintedCount = 0

            try {
                for (frameIndex in 0 until totalFrames) {
                    ensureActive()
                    val timeMs = (frameIndex.toLong() * frameIntervalMs).coerceAtMost(durationMs)
                    val frame = retriever.getFrameAtTime(
                        timeMs * 1000L,
                        android.media.MediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: return@withContext null
                    val mask = maskProvider(frameIndex, timeMs, videoWidth, videoHeight)
                    var outputFrame: Bitmap = frame
                    try {
                        if (mask != null) {
                            val result = runInpainting(frame, mask) { progress ->
                                onProgress((frameIndex + progress) / totalFrames.toFloat())
                            } ?: return@withContext null
                            outputFrame = result.outputBitmap
                            inpaintedCount++
                        }

                        val frameFile = File(tempDir, "frame_%05d.png".format(frameIndex))
                        frameFile.outputStream().use { out ->
                            check(outputFrame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) {
                                "Could not encode frame $frameIndex"
                            }
                        }
                    } finally {
                        if (outputFrame !== frame) outputFrame.recycle()
                        frame.recycle()
                        if (recycleProvidedMasks) mask?.recycle()
                    }
                    onProgress((frameIndex + 1).toFloat() / totalFrames)
                }

                onProgress(0.95f)
                val pattern = File(tempDir, "frame_%05d.png").absolutePath
                val encodeOk = ffmpegEngine.encodeImageSequenceWithAudio(
                    inputUri = uri,
                    framePattern = pattern,
                    fps = fps.toInt().coerceIn(1, 120),
                    outputFile = outputFile,
                    onProgress = { progress -> onProgress(0.95f + progress * 0.05f) }
                )

                if (!encodeOk || !outputFile.isFile || outputFile.length() <= 0L) {
                    Log.w(TAG, "FFmpeg encode of inpainted frames failed")
                    return@withContext null
                }

                onProgress(1f)
                VideoInpaintingResult(
                    outputUri = outputUri,
                    framesProcessed = inpaintedCount,
                    totalProcessingTimeMs = System.currentTimeMillis() - startTime,
                    averageFrameTimeMs = if (inpaintedCount > 0) {
                        (System.currentTimeMillis() - startTime) / inpaintedCount
                    } else {
                        0L
                    }
                )
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video inpainting failed", e)
            null
        } finally {
            retrieverLease.close()
        }
    }

    /**
     * Convert a Bitmap to an ONNX float tensor in NCHW layout, normalized to [0, 1].
     *
     * @param env OrtEnvironment for tensor creation
     * @param bitmap Source bitmap (should already be resized to model input dimensions)
     * @param channels 3 for RGB image, 1 for grayscale mask
     * @return OnnxTensor shaped [1, channels, height, width]
     */
    private fun bitmapToFloatTensor(
        env: OrtEnvironment,
        bitmap: Bitmap,
        channels: Int
    ): OnnxTensor {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val bufferSize = 1 * channels * height * width
        val floatBuffer = FloatBuffer.allocate(bufferSize)

        if (channels == 3) {
            // NCHW layout: [1, 3, H, W] — R plane, then G plane, then B plane
            for (c in 0 until 3) {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val pixel = pixels[y * width + x]
                        val value = when (c) {
                            0 -> Color.red(pixel) / 255f
                            1 -> Color.green(pixel) / 255f
                            2 -> Color.blue(pixel) / 255f
                            else -> 0f
                        }
                        floatBuffer.put(value)
                    }
                }
            }
        } else {
            // Single channel mask: [1, 1, H, W] — use red channel, threshold to 0 or 1
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val value = if (Color.red(pixel) > 127) 1f else 0f
                    floatBuffer.put(value)
                }
            }
        }

        floatBuffer.rewind()
        val shape = longArrayOf(1L, channels.toLong(), height.toLong(), width.toLong())
        return OnnxTensor.createTensor(env, floatBuffer, shape)
    }

    /**
     * Convert an ONNX output tensor in NCHW layout back to a Bitmap.
     *
     * @param tensorData Output tensor data shaped [1, 3, H, W] with values in [0, 1]
     * @param targetWidth Desired output width (will scale from model resolution)
     * @param targetHeight Desired output height (will scale from model resolution)
     * @return Bitmap at the target resolution
     */
    private fun floatTensorToBitmap(
        tensorData: Array<Array<Array<FloatArray>>>,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        // tensorData shape: [1][3][H][W]
        val channelData = tensorData[0] // [3][H][W]
        val modelHeight = channelData[0].size
        val modelWidth = channelData[0][0].size

        val pixels = IntArray(modelWidth * modelHeight)
        for (y in 0 until modelHeight) {
            for (x in 0 until modelWidth) {
                val r = (channelData[0][y][x].coerceIn(0f, 1f) * 255f).toInt()
                val g = (channelData[1][y][x].coerceIn(0f, 1f) * 255f).toInt()
                val b = (channelData[2][y][x].coerceIn(0f, 1f) * 255f).toInt()
                pixels[y * modelWidth + x] = Color.argb(255, r, g, b)
            }
        }

        val modelBitmap = Bitmap.createBitmap(modelWidth, modelHeight, Bitmap.Config.ARGB_8888)
        modelBitmap.setPixels(pixels, 0, modelWidth, 0, 0, modelWidth, modelHeight)

        return if (targetWidth != modelWidth || targetHeight != modelHeight) {
            val scaled = Bitmap.createScaledBitmap(modelBitmap, targetWidth, targetHeight, true)
            modelBitmap.recycle()
            scaled
        } else {
            modelBitmap
        }
    }

}
