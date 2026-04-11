package com.novacut.editor.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
 * ### Option B: ONNX Runtime (cross-device)
 * 1. Add `implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17+")`
 * 2. Load LaMa ONNX model from assets or downloaded cache
 * 3. Run inference via OrtSession with NNAPI execution provider
 * 4. Falls back to CPU if NNAPI unavailable
 *
 * ## Dependencies (to be added to build.gradle.kts)
 * ```
 * // implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
 * // or
 * // implementation("com.qualcomm.qnn:qnn-runtime-android:2.+")
 * ```
 */
@Singleton
class InpaintingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "InpaintingEngine"
        private const val MODEL_FILENAME = "lama_dilated.onnx"
        private const val MODEL_SIZE_BYTES = 174_000_000L // ~174MB
        private const val MODEL_INPUT_SIZE = 512
        private const val MODEL_URL = "https://huggingface.co/novacut/lama-dilated-onnx/resolve/main/lama_dilated.onnx"
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
        val modelFile = File(context.filesDir, "models/inpainting/$MODEL_FILENAME")
        return modelFile.exists() && modelFile.length() > MODEL_SIZE_BYTES / 2
    }

    /**
     * Download the LaMa-Dilated ONNX model to device storage.
     * Warning: This model is ~174MB. Ensure sufficient storage and show download progress.
     *
     * @param onProgress Progress callback in [0.0, 1.0]
     */
    suspend fun downloadModel(
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "models/inpainting").also { it.mkdirs() }
        val outputFile = File(modelDir, MODEL_FILENAME)
        try {
            Log.d(TAG, "Downloading LaMa-Dilated model from $MODEL_URL")
            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            val tempFile = File(modelDir, "$MODEL_FILENAME.tmp")
            try {
                val contentLength = connection.contentLengthLong.let {
                    if (it > 0) it else MODEL_SIZE_BYTES
                }

                BufferedInputStream(connection.inputStream, 8192).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var totalBytesRead = 0L
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            onProgress((totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
            Log.d(TAG, "LaMa model downloaded: ${outputFile.length()} bytes")
            onProgress(1f)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download LaMa model", e)
            outputFile.delete()
            File(modelDir, "$MODEL_FILENAME.tmp").delete()
            false
        }
    }

    /** Delete the downloaded model to free storage (~174MB). */
    fun deleteModel() {
        val modelDir = File(context.filesDir, "models/inpainting")
        modelDir.deleteRecursively()
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
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Inpainting frame: ${bitmap.width}x${bitmap.height}")

        if (!isModelReady()) {
            Log.w(TAG, "LaMa model not downloaded")
            return@withContext null
        }

        try {
            // ONNX Runtime inference for LaMa-Dilated
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                // Try NNAPI first (Qualcomm NPU), fall back to CPU
                try { addNnapi() } catch (e: Exception) { Log.w(TAG, "NNAPI not available, falling back to CPU", e) }
            }
            var session: OrtSession? = null
            var inputBitmap: Bitmap? = null
            var maskBitmap: Bitmap? = null
            var imageTensor: OnnxTensor? = null
            var maskTensor: OnnxTensor? = null
            try {
                val modelPath = File(context.filesDir, "models/inpainting/$MODEL_FILENAME").absolutePath
                session = env.createSession(modelPath, sessionOptions)

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
                    return@withContext InpaintingResult(
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
                session?.close()
                sessionOptions.close()
                if (inputBitmap != null && inputBitmap !== bitmap) inputBitmap.recycle()
                if (maskBitmap != null && maskBitmap !== mask) maskBitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ONNX inference failed, falling back to pixel-averaging", e)
            // Fallback: simple pixel-averaging inpainting
            try {
                val fallbackBitmap = fallbackInpaint(bitmap, mask)
                onProgress(1f)
                return@withContext InpaintingResult(
                    outputBitmap = fallbackBitmap,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    inputResolution = bitmap.width to bitmap.height,
                    processedResolution = bitmap.width to bitmap.height
                )
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback inpainting also failed", fallbackError)
                null
            }
        }
    }

    /**
     * Inpaint a video by processing each frame with the provided per-frame masks.
     *
     * For object removal across a video, the caller should provide masks for each frame
     * (e.g., from object tracking or manual painting on keyframes with interpolation).
     *
     * @param uri Source video URI
     * @param maskFrames Map of frame index to mask bitmap. Frames without masks are passed through unchanged.
     * @param outputUri Destination URI for the inpainted video
     * @param onProgress Progress callback in [0.0, 1.0]
     * @return VideoInpaintingResult, or null on failure
     */
    suspend fun inpaintVideo(
        uri: Uri,
        maskFrames: Map<Int, Bitmap>,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): VideoInpaintingResult? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Inpainting video: ${maskFrames.size} masked frames")

        if (!isModelReady()) {
            Log.w(TAG, "LaMa model not downloaded")
            return@withContext null
        }

        try {
            // TODO: Video inpainting pipeline
            //
            // val decoder = MediaCodecDecoder(context, uri)
            // val encoder = MediaCodecEncoder(outputUri, decoder.width, decoder.height, decoder.frameRate)
            //
            // var frameIndex = 0
            // val totalFrames = decoder.frameCount
            // var inpaintedCount = 0
            //
            // while (decoder.hasNextFrame()) {
            //     val frame = decoder.nextFrame()
            //     val mask = maskFrames[frameIndex]
            //
            //     if (mask != null) {
            //         val result = inpaintFrame(frame, mask)
            //         if (result != null) {
            //             encoder.encodeFrame(result.outputBitmap)
            //             result.outputBitmap.recycle()
            //             inpaintedCount++
            //         } else {
            //             encoder.encodeFrame(frame) // fallback: use original
            //         }
            //     } else {
            //         encoder.encodeFrame(frame) // no mask for this frame
            //     }
            //
            //     frame.recycle()
            //     frameIndex++
            //     onProgress(frameIndex.toFloat() / totalFrames)
            // }
            //
            // encoder.finish()
            // decoder.release()
            //
            // return@withContext VideoInpaintingResult(
            //     outputUri = outputUri,
            //     framesProcessed = inpaintedCount,
            //     totalProcessingTimeMs = System.currentTimeMillis() - startTime,
            //     averageFrameTimeMs = if (inpaintedCount > 0) (System.currentTimeMillis() - startTime) / inpaintedCount else 0
            // )

            Log.d(TAG, "inpaintVideo stub — video pipeline not yet implemented")
            onProgress(1f)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Video inpainting failed", e)
            null
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

    /**
     * Fallback inpainting using simple pixel-averaging when ONNX inference is unavailable.
     * For each masked pixel, averages the nearest unmasked neighbor pixels.
     */
    private fun fallbackInpaint(bitmap: Bitmap, mask: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val scaledMask = if (mask.width != width || mask.height != height) {
            Bitmap.createScaledBitmap(mask, width, height, false)
        } else {
            mask
        }

        val srcPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        bitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)
        scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val outPixels = srcPixels.copyOf()
        val isMasked = BooleanArray(width * height) { Color.red(maskPixels[it]) > 127 }

        // Simple iterative averaging: sweep multiple passes to propagate fill inward
        val maxPasses = maxOf(width, height)
        val tempPixels = outPixels.copyOf()
        for (pass in 0 until minOf(maxPasses, 50)) {
            var changed = false
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    if (!isMasked[idx]) continue

                    var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
                    // Sample 4-connected neighbors
                    for ((dx, dy) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                        val nx = x + dx; val ny = y + dy
                        if (nx in 0 until width && ny in 0 until height) {
                            val nIdx = ny * width + nx
                            if (!isMasked[nIdx] || pass > 0) {
                                rSum += Color.red(outPixels[nIdx])
                                gSum += Color.green(outPixels[nIdx])
                                bSum += Color.blue(outPixels[nIdx])
                                count++
                            }
                        }
                    }
                    if (count > 0) {
                        tempPixels[idx] = Color.argb(255, rSum / count, gSum / count, bSum / count)
                        changed = true
                    }
                }
            }
            System.arraycopy(tempPixels, 0, outPixels, 0, outPixels.size)
            if (!changed) break
        }

        if (scaledMask !== mask) scaledMask.recycle()

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }
}
