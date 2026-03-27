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

/** Stub engine — requires MobileSAM ONNX Runtime dependency. See ROADMAP.md Tier 3. */
@Singleton
class TapSegmentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TapSegmentEngine"
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
     * Download and prepare the MobileSAM ONNX model files.
     *
     * @param modelSourceUri Optional URI to a bundled or pre-downloaded model archive.
     * @param onProgress Download progress callback [0.0, 1.0].
     * @return true if models are ready, false on failure.
     */
    suspend fun prepareModel(
        modelSourceUri: Uri? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "prepareModel: stub — requires MobileSAM ONNX model")
        false
    }

    /** Check if the MobileSAM model is downloaded and ready. */
    fun isReady(): Boolean {
        Log.d(TAG, "isReady: stub — requires MobileSAM ONNX model")
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
        Log.d(TAG, "segmentAtPoint: stub — requires MobileSAM ONNX model")
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
        Log.d(TAG, "segmentWithBox: stub — requires MobileSAM ONNX model")
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
        Log.d(TAG, "propagateMask: stub — requires MobileSAM ONNX model")
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
