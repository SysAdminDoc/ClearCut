package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Stub engine — requires RIFE NCNN dependency. See ROADMAP.md Tier 2. */
@Singleton
class FrameInterpolationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FrameInterpEngine"
    }

    /**
     * Configuration for slow-motion frame interpolation.
     *
     * @param multiplier How many intermediate frames to generate between each pair.
     *   2x = 1 intermediate frame (doubles frame count), 4x = 3, 8x = 7.
     * @param quality Preview mode uses lower resolution for speed; Export uses full resolution.
     * @param resolutionCap Maximum resolution to process. Frames above this are downscaled
     *   before interpolation and upscaled after. Reduces VRAM usage and inference time.
     */
    data class SlowMotionConfig(
        val multiplier: Int = 2,
        val quality: Quality = Quality.PREVIEW,
        val resolutionCap: Int = 720
    ) {
        init {
            require(multiplier in setOf(2, 4, 8)) { "Multiplier must be 2, 4, or 8" }
        }

        enum class Quality {
            /** Lower resolution, faster processing. Good for timeline preview. */
            PREVIEW,
            /** Full resolution, slower processing. Used for final export. */
            EXPORT
        }
    }

    /**
     * Result of a frame interpolation operation.
     *
     * @param outputUri URI to the generated slow-motion video
     * @param originalFrameCount Number of frames in the source video
     * @param interpolatedFrameCount Total frames in the output (original + generated)
     * @param processingTimeMs Total wall-clock time for the operation
     * @param usedMlModel True if RIFE was used; false if frame duplication fallback was used
     */
    data class InterpolationResult(
        val outputUri: Uri,
        val originalFrameCount: Int,
        val interpolatedFrameCount: Int,
        val processingTimeMs: Long,
        val usedMlModel: Boolean
    )

    /** Whether the RIFE NCNN model is downloaded and ready for inference. */
    fun isModelReady(): Boolean {
        Log.d(TAG, "isModelReady: stub — requires RIFE NCNN model")
        return false
    }

    /**
     * Download the RIFE v4.6 NCNN model to device storage.
     *
     * @param onProgress Progress callback in [0.0, 1.0]
     */
    suspend fun downloadModel(
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadModel: stub — requires RIFE NCNN model")
        false
    }

    /**
     * Generate intermediate frames between each pair of frames in the input video
     * to produce a slow-motion output.
     *
     * @param inputUri Source video URI
     * @param outputUri Destination URI for the interpolated video
     * @param config Slow-motion configuration (multiplier, quality, resolution cap)
     * @param onProgress Progress callback in [0.0, 1.0]
     * @return InterpolationResult with output details, or null on failure
     */
    suspend fun interpolateFrames(
        inputUri: Uri,
        outputUri: Uri,
        config: SlowMotionConfig = SlowMotionConfig(),
        onProgress: (Float) -> Unit = {}
    ): InterpolationResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "interpolateFrames: stub — requires RIFE NCNN model")
        null
    }

    /**
     * Interpolate a single pair of frames, returning the intermediate bitmap.
     * Useful for preview/thumbnail generation.
     *
     * @param frame1 First frame bitmap
     * @param frame2 Second frame bitmap
     * @param timestep Position between frames in [0.0, 1.0] (0.5 = midpoint)
     * @return Interpolated bitmap, or null if model is not ready
     */
    suspend fun interpolatePair(
        frame1: Bitmap,
        frame2: Bitmap,
        timestep: Float = 0.5f
    ): Bitmap? = withContext(Dispatchers.IO) {
        Log.d(TAG, "interpolatePair: stub — requires RIFE NCNN model")
        null
    }
}
