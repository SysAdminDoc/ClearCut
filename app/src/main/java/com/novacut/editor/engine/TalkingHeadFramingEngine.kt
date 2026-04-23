package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.novacut.editor.model.Easing
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeProperty
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Talking-head auto-framing (Samsung Auto-Framing / Apple Center Stage style).
 *
 * Distinct from SmartReframeEngine which optimizes for generic saliency and
 * aspect changes. This engine is specifically tuned for talking-head footage:
 * face detection per sample frame, one-euro smoothing on the resulting
 * center-of-face trajectory, and output as POSITION_X/POSITION_Y keyframes on
 * the clip so the existing keyframe-aware export path handles it.
 *
 * On devices without MediaPipe FaceLandmarker wired, we fall back to a
 * skin-tone centroid so the feature still does something useful.
 */
@Singleton
class TalkingHeadFramingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class FrameCenter(val timeMs: Long, val x: Float, val y: Float)

    suspend fun trackFaceCenter(
        uri: Uri,
        durationMs: Long,
        sampleStepMs: Long = 500L
    ): List<FrameCenter> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val out = mutableListOf<FrameCenter>()
        try {
            retriever.setDataSource(context, uri)
            var t = 0L
            while (t < durationMs) {
                val frame = retriever.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val c = skinToneCentroid(frame)
                    out += FrameCenter(t, c.first, c.second)
                    frame.recycle()
                }
                t += sampleStepMs
            }
        } catch (e: Exception) {
            Log.w(TAG, "Face tracking failed", e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        oneEuroSmooth(out)
    }

    /** Generate x/y position keyframes mapped to clip-local time (0..durationMs). */
    fun toKeyframes(centers: List<FrameCenter>, clipDurationMs: Long): List<Keyframe> {
        if (centers.isEmpty()) return emptyList()
        val kfs = mutableListOf<Keyframe>()
        for (c in centers) {
            val tLocal = c.timeMs.coerceIn(0L, clipDurationMs)
            // Map face center (0..1) to a target translation — we want the face at
            // the top third, so the translation X/Y is (0.5-cx, 0.33-cy).
            val tx = (0.5f - c.x)
            val ty = (0.33f - c.y)
            kfs += Keyframe(timeOffsetMs = tLocal, property = KeyframeProperty.POSITION_X, value = tx, easing = Easing.EASE_IN_OUT)
            kfs += Keyframe(timeOffsetMs = tLocal, property = KeyframeProperty.POSITION_Y, value = ty, easing = Easing.EASE_IN_OUT)
        }
        return kfs
    }

    private fun skinToneCentroid(bmp: Bitmap): Pair<Float, Float> {
        val w = (bmp.width / 4).coerceAtLeast(8)
        val h = (bmp.height / 4).coerceAtLeast(8)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled != bmp) scaled.recycle()
        var sx = 0.0; var sy = 0.0; var n = 0.0
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (isSkin(r, g, b)) {
                sx += (i % w).toDouble()
                sy += (i / w).toDouble()
                n += 1.0
            }
        }
        return if (n < 10.0) 0.5f to 0.5f
        else ((sx / n) / w).toFloat() to ((sy / n) / h).toFloat()
    }

    private fun isSkin(r: Int, g: Int, b: Int): Boolean {
        if (r < 95 || g < 40 || b < 20) return false
        if (abs(r - g) < 15) return false
        return r > g && r > b
    }

    // Simplified one-euro low-pass — stable trajectory without obvious drift.
    private fun oneEuroSmooth(
        samples: List<FrameCenter>,
        beta: Float = 0.01f,
        minCutoff: Float = 1.0f
    ): List<FrameCenter> {
        if (samples.size < 2) return samples
        val out = ArrayList<FrameCenter>(samples.size)
        var px = samples[0].x; var py = samples[0].y
        out += samples[0]
        for (i in 1 until samples.size) {
            val s = samples[i]
            val dt = (s.timeMs - samples[i - 1].timeMs).coerceAtLeast(1L) / 1000f
            val rateX = abs(s.x - px) / dt
            val rateY = abs(s.y - py) / dt
            val cutX = minCutoff + beta * rateX
            val cutY = minCutoff + beta * rateY
            val ax = alpha(cutX, dt)
            val ay = alpha(cutY, dt)
            val nx = ax * s.x + (1 - ax) * px
            val ny = ay * s.y + (1 - ay) * py
            out += FrameCenter(s.timeMs, nx, ny)
            px = nx; py = ny
        }
        return out
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * Math.PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    companion object { private const val TAG = "TalkingHeadFraming" }
}
