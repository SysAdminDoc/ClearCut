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
        const val SAM3_SOURCE_URL = "https://github.com/facebookresearch/sam3"
        const val PREMIUM_WORKING_SET_THRESHOLD_BYTES = 200L * 1024L * 1024L

        val DEFAULT_ON_DEVICE_MODEL: ModelVariant = ModelVariant.SAM2_1_HIERA_TINY_ONNX
        val FALLBACK_ON_DEVICE_MODEL: ModelVariant = ModelVariant.MOBILE_SAM_ONNX

        /**
         * R6.4 feature flag — SAM 3 placeholder is opt-in until a mobile-export
         * ONNX checkpoint ships. Default: off. Flip to true inside
         * [recommendedModelForDevice] to start recommending SAM 3 on premium
         * devices when the export exists.
         */
        const val SAM3_PLACEHOLDER_ENABLED = false

        /**
         * Returns the recommended on-device tracked-mask model for the given
         * device + setting state. SAM 3 is held back via [SAM3_PLACEHOLDER_ENABLED]
         * regardless of caller flags — the placeholder enum row only exists so the
         * API contract is forward-compatible.
         */
        fun recommendedModelForDevice(
            availableRamMb: Int,
            allowPremiumModels: Boolean
        ): ModelVariant {
            if (SAM3_PLACEHOLDER_ENABLED) {
                val sam3 = ModelVariant.SAM3_HIERA_TINY_ONNX_PLACEHOLDER
                if (allowPremiumModels && sam3.canRunOnDevice(availableRamMb)) {
                    return sam3
                }
            }
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
        SAM2_1,
        /**
         * Meta SAM 3 / SAM 3.1 (Nov 2025 + Mar 2026). 848M-parameter model with
         * text-prompted concept segmentation in addition to the point/box prompts
         * supported by SAM 2.1, plus video object multiplexing in 3.1 (16 objects
         * per forward pass, doubles video throughput). Currently feasible only on
         * H100-class GPUs; no mobile-viable ONNX export has shipped as of 2026-05.
         * Reserved here as a placeholder so the API contract is forward-compatible
         * — see ROADMAP.md R6.4.
         */
        SAM3
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
        ),

        /**
         * Placeholder for SAM 3 / SAM 3.1 (R6.4). Behaviour-disabled today because:
         *  - There is no Tiny-class ONNX export of SAM 3 / SAM 3.1 as of 2026-05.
         *  - The full 848M-parameter model targets H100 GPUs, not mobile NPUs.
         *  - The text-prompt concept-segmentation surface area is the part ClearCut
         *    most wants; it has no equivalent in SAM 2.1.
         * Sizes below are placeholder estimates derived from the SAM 2.1 Hiera Tiny
         * working set. Update both numbers and `canRunOnDevice()` policy when a
         * mobile-export ships. Until then, `recommendedModelForDevice()` will not
         * select this variant — see [SAM3_PLACEHOLDER_ENABLED].
         */
        SAM3_HIERA_TINY_ONNX_PLACEHOLDER(
            displayName = "SAM 3 Hiera Tiny (preview)",
            family = ModelFamily.SAM3,
            modelPackageName = "sam3-hiera-tiny-onnx-placeholder",
            modelBytes = 240L * 1024L * 1024L,
            stateCacheBytes = 128L * 1024L * 1024L,
            minimumRamMb = 8_192,
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
     * Segment by natural-language concept prompt (R6.4b).
     *
     * SAM 3 introduces text-prompted concept segmentation ("dog", "the person
     * in the red jacket", "the basketball"). ClearCut exposes this API shape now
     * so consumers and UI can integrate without waiting for the model export.
     * Today this method:
     *  - Returns null on the SAM 2.1 path (the default model) because SAM 2.1
     *    does not accept text prompts.
     *  - Returns null on the MobileSAM path for the same reason.
     *  - When a SAM 3 mobile ONNX export ships, the SAM3 implementation will
     *    parse the prompt and emit a mask without any consumer-side change.
     *
     * Callers should treat a null return as "concept-segmentation unavailable
     * on the current device/model" and fall back to a manual mask draw flow.
     *
     * @param bitmap The video frame to segment.
     * @param textPrompt Natural-language description of the target object.
     * @return TapSegmentResult or null if concept segmentation is unavailable.
     */
    suspend fun segmentByTextPrompt(
        bitmap: Bitmap,
        textPrompt: String
    ): TapSegmentResult? = withContext(Dispatchers.Default) {
        Log.d(
            TAG,
            "segmentByTextPrompt: stub — text prompts require SAM 3 mobile export, " +
                "not yet available (prompt='${textPrompt.take(40)}')"
        )
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
