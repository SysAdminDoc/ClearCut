package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine for video stabilization. See ROADMAP.md Tier A.3 and R4.4 / R6.9.
 *
 * Primary target: OpenCV Android (`org.opencv:opencv:4.10.0+`) with the
 * Lucas-Kanade sparse optical flow + RANSAC + Kalman smoothing pipeline
 * documented in [research/](../../../../../../research/) — ~5-10 ms per frame
 * on Snapdragon 8 Gen 2 / Adreno 740.
 *
 * Round 6 (R6.9): **prefer Gyroflow sidecar import before reimplementing**
 * gyro math from scratch. The MediaImportEngine should detect a sibling
 * `.gyroflow` JSON file on import and apply the resulting per-frame
 * transforms via MatrixTransformation — that covers ~80% of the creator
 * value at ~10% of the engineering cost compared to a from-scratch gyro
 * pipeline. The OpenCV optical-flow path remains the fallback when no gyro
 * metadata is available.
 *
 * ## Activation path (OpenCV)
 *
 *   1. Add to gradle/libs.versions.toml:
 *        opencv = "4.10.0"
 *        opencv = { group = "org.opencv", name = "opencv", version.ref = "opencv" }
 *   2. Add `implementation(libs.opencv)` to app/build.gradle.kts.
 *   3. OpenCV ships arm64-only; ABI-split the release APK to keep the base
 *      AAB under the 200 MB Play Store ceiling. Universal builds bloat past
 *      150 MB on their own.
 *   4. Verify the AAR's `.so` files are 16 KB page-size aligned with
 *      `scripts/check_16kb_alignment.py` before pinning (R6.1).
 *   5. Replace [analyzeStability] with `cv::goodFeaturesToTrack` +
 *      `cv::calcOpticalFlowPyrLK` + `cv::findHomography(RANSAC)` per frame,
 *      then Kalman-smooth the trajectory and emit warp matrices.
 *   6. Apply the warp matrices in the export pipeline via
 *      `Media3 MatrixTransformation` per frame so the GPU does the actual
 *      crop + rotate, not the OpenCV `warpAffine` (which is CPU-bound and
 *      would crater export speed).
 *
 * ## License
 *
 * OpenCV is Apache-2.0; redistributable. The Gyroflow `.gyroflow` JSON
 * format is open and the sample project files distributed with Gyroflow are
 * CC0.
 */
@Singleton
class StabilizationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "StabilizationEngine"
        const val TARGET_OPENCV_VERSION = "4.10.0"
        const val TARGET_OPENCV_GROUP = "org.opencv"
        const val TARGET_OPENCV_NAME = "opencv"
        const val GYROFLOW_PROJECT_FILE_EXTENSION = "gyroflow"
        const val GYROFLOW_PROJECT_SOURCE_URL = "https://github.com/gyroflow/gyroflow"
        /** OpenCV ships arm64-only — ABI-split the release APK. */
        const val OPENCV_REQUIRES_ARM64_ONLY_SPLIT = true
        /** OpenCV AAR footprint (arm64-v8a). */
        const val OPENCV_ARM64_AAR_BYTES = 40_000_000L
    }

    /**
     * Configuration for video stabilization.
     *
     * @param smoothingStrength How aggressively to smooth camera motion [0.0, 1.0].
     *   0.0 = no smoothing (original motion), 1.0 = maximum smoothing (tripod-like).
     * @param cropPercentage How much to crop edges to hide stabilization borders [0.0, 0.3].
     * @param algorithm Which feature detection/tracking algorithm to use.
     * @param maxFeatures Maximum number of feature points to track per frame.
     * @param useAffine If true, estimate full affine (translation + rotation + scale).
     *   If false, estimate translation only (faster, sufficient for most handheld shake).
     */
    data class StabilizationConfig(
        val smoothingStrength: Float = 0.5f,
        val cropPercentage: Float = 0.15f,
        val algorithm: Algorithm = Algorithm.LK_OPTICAL_FLOW,
        val maxFeatures: Int = 300,
        val useAffine: Boolean = true
    ) {
        init {
            require(smoothingStrength in 0f..1f) { "Smoothing strength must be in [0, 1]" }
            require(cropPercentage in 0f..0.3f) { "Crop percentage must be in [0, 0.3]" }
        }

        enum class Algorithm(val description: String) {
            /** Lucas-Kanade sparse optical flow. Best balance of speed and accuracy. ~5ms/frame. */
            LK_OPTICAL_FLOW("L-K Sparse Optical Flow"),
            /** ORB feature matching between frames. More robust to large motion. ~8ms/frame. */
            ORB_FEATURES("ORB Feature Matching")
        }
    }

    /**
     * Per-frame motion data containing the estimated camera transform.
     *
     * @param frameIndex Zero-based frame index
     * @param timestampMs Frame timestamp in milliseconds
     * @param dx Horizontal translation in pixels
     * @param dy Vertical translation in pixels
     * @param dAngle Rotation angle in radians
     * @param dScale Scale change (1.0 = no change)
     * @param confidence RANSAC inlier ratio [0, 1]. Low confidence = unreliable transform.
     */
    data class FrameTransform(
        val frameIndex: Int,
        val timestampMs: Long,
        val dx: Float,
        val dy: Float,
        val dAngle: Float = 0f,
        val dScale: Float = 1f,
        val confidence: Float = 1f
    )

    /**
     * Complete motion analysis data for a video.
     *
     * @param transforms Per-frame transforms (raw, before smoothing)
     * @param smoothedTransforms Per-frame transforms after Kalman/averaging smoothing
     * @param averageShakeMagnitude Overall shake magnitude [0, 1]
     * @param maxShakeMagnitude Peak shake magnitude
     * @param analysisTimeMs Time taken for motion analysis
     * @param frameCount Total frames analyzed
     * @param fps Video frame rate
     */
    data class MotionData(
        val transforms: List<FrameTransform>,
        val smoothedTransforms: List<FrameTransform>,
        val averageShakeMagnitude: Float,
        val maxShakeMagnitude: Float,
        val analysisTimeMs: Long,
        val frameCount: Int,
        val fps: Float
    )

    /**
     * Result of applying stabilization to a video.
     *
     * @param outputUri URI to the stabilized video
     * @param cropApplied Actual crop percentage that was applied
     * @param processingTimeMs Total processing time
     */
    data class StabilizationResult(
        val outputUri: Uri,
        val cropApplied: Float,
        val processingTimeMs: Long
    )

    /** Whether OpenCV is available on this device. */
    fun isOpenCvAvailable(): Boolean {
        return false
    }

    /**
     * Analyze camera motion in a video by tracking feature points across frames.
     *
     * @param uri Source video URI
     * @param config Stabilization configuration
     * @param onProgress Progress callback in [0.0, 1.0]
     * @return MotionData with per-frame transforms, or null on failure
     */
    suspend fun analyzeMotion(
        uri: Uri,
        config: StabilizationConfig = StabilizationConfig(),
        onProgress: (Float) -> Unit = {}
    ): MotionData? = withContext(Dispatchers.IO) {
        Log.d(TAG, "analyzeMotion: stub — requires OpenCV Android SDK")
        null
    }

    /**
     * Apply smoothed stabilization transforms to produce a stabilized video.
     *
     * @param uri Source video URI
     * @param motionData Motion analysis data from [analyzeMotion]
     * @param config Stabilization configuration (crop percentage, etc.)
     * @param outputUri Destination URI for the stabilized video
     * @param onProgress Progress callback in [0.0, 1.0]
     * @return StabilizationResult, or null on failure
     */
    suspend fun stabilize(
        uri: Uri,
        motionData: MotionData,
        config: StabilizationConfig = StabilizationConfig(),
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): StabilizationResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "stabilize: stub — requires OpenCV Android SDK")
        null
    }
}
