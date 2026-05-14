package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Stub engine — requires SAM / MobileSAM ONNX model assets. See ROADMAP.md Tier A.7. */
@Singleton
class TapSegmentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TapSegmentEngine"
        const val SAM2_1_SOURCE_URL = "https://github.com/facebookresearch/sam2"
        const val SAM2_1_ONNX_MODEL_ID = "onnx-community/sam2.1-hiera-tiny-ONNX"
        const val PREMIUM_WORKING_SET_THRESHOLD_BYTES = 200L * 1024L * 1024L

        val DEFAULT_ON_DEVICE_MODEL: ModelVariant = ModelVariant.SAM2_1_HIERA_TINY_ONNX
        val FALLBACK_ON_DEVICE_MODEL: ModelVariant = ModelVariant.MOBILE_SAM_ONNX

        fun recommendedModelForDevice(
            availableRamMb: Int,
            allowPremiumModels: Boolean
        ): ModelVariant {
            val premium = DEFAULT_ON_DEVICE_MODEL
            return if (allowPremiumModels && premium.canRunOnDevice(availableRamMb)) {
                premium
            } else {
                FALLBACK_ON_DEVICE_MODEL
            }
        }
    }

    enum class ModelFamily {
        MOBILE_SAM,
        SAM2_1
    }

    enum class ModelVariant(
        val displayName: String,
        val family: ModelFamily,
        val modelPackageName: String,
        val modelBytes: Long,
        val stateCacheBytes: Long,
        val minimumRamMb: Int,
        val supportsVideoPropagation: Boolean
    ) {
        MOBILE_SAM_ONNX(
            displayName = "MobileSAM",
            family = ModelFamily.MOBILE_SAM,
            modelPackageName = "mobile-sam-onnx",
            modelBytes = 10L * 1024L * 1024L,
            stateCacheBytes = 24L * 1024L * 1024L,
            minimumRamMb = 3_072,
            supportsVideoPropagation = false
        ),
        SAM2_1_HIERA_TINY_ONNX(
            displayName = "SAM 2.1 Hiera Tiny",
            family = ModelFamily.SAM2_1,
            modelPackageName = SAM2_1_ONNX_MODEL_ID,
            modelBytes = 160L * 1024L * 1024L,
            stateCacheBytes = 96L * 1024L * 1024L,
            minimumRamMb = 6_144,
            supportsVideoPropagation = true
        );

        val workingSetBytes: Long
            get() = modelBytes + stateCacheBytes

        val requiresPremiumTier: Boolean
            get() = workingSetBytes > PREMIUM_WORKING_SET_THRESHOLD_BYTES

        fun canRunOnDevice(availableRamMb: Int): Boolean =
            availableRamMb >= minimumRamMb
    }

    /**
     * Result of a tap-to-segment operation.
     *
     * @param mask Alpha mask bitmap (same dimensions as input). White = selected, black = background.
     * @param confidence Model confidence score [0.0, 1.0] for this segmentation.
     * @param boundingBox Tight bounding box around the segmented object in pixel coordinates.
     * @param inferenceTimeMs Wall-clock time for the segmentation inference.
     */
    data class TapSegmentResult(
        val mask: Bitmap,
        val confidence: Float,
        val boundingBox: Rect,
        val inferenceTimeMs: Long
    )

    /**
     * Download and prepare the SAM / MobileSAM ONNX model files.
     *
     * @param modelSourceUri Optional URI to a bundled or pre-downloaded model archive.
     * @param onProgress Download progress callback [0.0, 1.0].
     * @return true if models are ready, false on failure.
     */
    suspend fun prepareModel(
        modelSourceUri: Uri? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "prepareModel: stub — requires explicit SAM ONNX model download")
        false
    }

    /** Check if a SAM / MobileSAM model is downloaded and ready. */
    fun isReady(): Boolean {
        Log.d(TAG, "isReady: stub — requires explicit SAM ONNX model download")
        return false
    }

    /**
     * Segment the object at the given point on the frame.
     *
     * @param bitmap The video frame to segment.
     * @param pointX X coordinate of the tap point in pixels.
     * @param pointY Y coordinate of the tap point in pixels.
     * @return TapSegmentResult with the alpha mask, confidence, and bounding box,
     *   or null if segmentation failed.
     */
    suspend fun segmentAtPoint(
        bitmap: Bitmap,
        pointX: Float,
        pointY: Float
    ): TapSegmentResult? = withContext(Dispatchers.Default) {
        Log.d(TAG, "segmentAtPoint: stub — requires explicit SAM ONNX model download")
        null
    }

    /**
     * Segment within a bounding box prompt.
     *
     * @param bitmap The video frame to segment.
     * @param boxRect Bounding box in bitmap pixel coordinates.
     * @return TapSegmentResult or null if segmentation failed.
     */
    suspend fun segmentWithBox(
        bitmap: Bitmap,
        boxRect: Rect
    ): TapSegmentResult? = withContext(Dispatchers.Default) {
        Log.d(TAG, "segmentWithBox: stub — requires explicit SAM ONNX model download")
        null
    }

    /**
     * Propagate a mask from a previous frame to the current frame using optical flow.
     *
     * @param previousMask The alpha mask from the previous frame.
     * @param currentFrame The current video frame bitmap.
     * @param refineWithSam If true, run SAM decoder using warped mask centroid as point prompt.
     * @return TapSegmentResult for the current frame, or null on failure.
     */
    suspend fun propagateMask(
        previousMask: Bitmap,
        currentFrame: Bitmap,
        refineWithSam: Boolean = false
    ): TapSegmentResult? = withContext(Dispatchers.Default) {
        Log.d(TAG, "propagateMask: stub — requires explicit SAM 2.1 video model download")
        null
    }

    /**
     * Release cached embeddings.
     * Call when the segmentation UI is dismissed.
     */
    fun release() {
        Log.d(TAG, "release: stub — no resources to release")
    }
}
