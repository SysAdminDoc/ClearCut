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
 * Stub engine for smart reframing via face/subject detection.
 *
 * MediaPipe Tasks Vision (com.google.mediapipe:tasks-vision) is already a
 * project dependency — used by SegmentationEngine. This engine needs
 * FaceDetector/PoseLandmarker wiring and per-style model pinning before
 * [analyzeForReframe] can return real data. See ROADMAP.md.
 *
 * Backend targets: MediaPipe Face Detection (BlazeFace ~400KB, <1ms/frame)
 *                  + BlazePose (~3-8MB) for full body tracking
 *
 * Algorithm (replicates YouTube Shorts / Instagram Reels auto-crop):
 * 1. Detect faces/poses per frame at low resolution
 * 2. Compute saliency-weighted bounding box (face center = highest weight)
 * 3. Smooth crop window trajectory using EMA (exponential moving average)
 * 4. Choose strategy: stationary (no motion), pan (slow follow), track (fast follow)
 */
@Singleton
class SmartReframeEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class ReframeStrategy {
        STATIONARY,  // Fixed crop, centered on average subject position
        PAN,         // Slow smooth pan following subject
        TRACK        // Fast tracking, keeps subject centered
    }

    data class ReframeConfig(
        val targetAspectRatio: Float = 9f / 16f,  // 9:16 for vertical
        val smoothingFactor: Float = 0.08f,        // EMA alpha (lower = smoother)
        val strategy: ReframeStrategy = ReframeStrategy.PAN,
        val padding: Float = 0.1f                  // Extra padding around detected subject
    )

    data class CropWindow(
        val centerX: Float,  // 0-1 normalized
        val centerY: Float,
        val width: Float,
        val height: Float
    )

    data class ReframeResult(
        val cropWindows: List<CropWindow>,  // One per frame
        val frameRate: Float,
        val strategy: ReframeStrategy
    )

    /**
     * Analyze video and compute per-frame crop windows.
     * Returns null until FaceDetector/PoseLandmarker wiring is complete.
     */
    suspend fun analyzeForReframe(
        uri: Uri,
        config: ReframeConfig = ReframeConfig(),
        onProgress: (Float) -> Unit = {}
    ): ReframeResult? = withContext(Dispatchers.Default) {
        Log.d(TAG, "analyzeForReframe: stub — face/pose detection not yet wired")
        null
    }

    /**
     * Apply EMA smoothing to a series of crop centers.
     * Prevents jittery crop movement.
     */
    fun smoothCropTrajectory(
        centers: List<Pair<Float, Float>>,
        alpha: Float = 0.08f
    ): List<Pair<Float, Float>> {
        if (centers.size < 2) return centers
        // An out-of-range alpha overshoots the target and makes the EMA oscillate or diverge;
        // NaN destroys every subsequent element via the feedback term.
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

    companion object {
        private const val TAG = "SmartReframe"
    }
}
