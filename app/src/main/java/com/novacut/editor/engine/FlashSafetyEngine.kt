package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * WCAG / Harding-lite flash detector for photosensitive-epilepsy safety.
 *
 * We sample luminance at ~10 Hz and count pairs of opposite-direction
 * transitions whose luminance delta exceeds a Δ threshold. A 1 s sliding
 * window that records >3 such transitions triggers a warning. We also flag
 * "red flash" candidates separately as those are more dangerous per W3C.
 *
 * Conservative approximation — not a replacement for Harding FPA — but
 * catches the obvious strobe/glitch-transition cases that actually harm
 * viewers in practice.
 */
@Singleton
class FlashSafetyEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class Warning(val startMs: Long, val endMs: Long, val kind: Kind)
    enum class Kind { GENERAL_FLASH, RED_FLASH }

    suspend fun analyze(uri: Uri, durationMs: Long): List<Warning> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val warnings = mutableListOf<Warning>()
        try {
            retriever.setDataSource(context, uri)
            val stepMs = 100L
            val lum = FloatArray((durationMs / stepMs).toInt().coerceAtLeast(2))
            val red = FloatArray(lum.size)
            var t = 0L
            var i = 0
            while (i < lum.size) {
                val frame = retriever.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val stats = avgLumAndRed(frame)
                    lum[i] = stats.first
                    red[i] = stats.second
                    frame.recycle()
                }
                t += stepMs
                i++
            }
            warnings += scanTransitions(lum, stepMs, thresh = 0.2f, Kind.GENERAL_FLASH)
            warnings += scanTransitions(red, stepMs, thresh = 0.15f, Kind.RED_FLASH)
        } catch (e: Exception) {
            Log.w(TAG, "flash analysis failed", e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        warnings
    }

    private fun scanTransitions(sig: FloatArray, stepMs: Long, thresh: Float, kind: Kind): List<Warning> {
        val out = mutableListOf<Warning>()
        val windowSamples = (1000L / stepMs).toInt().coerceAtLeast(10)
        var dir = 0; var count = 0; var windowStart = 0
        for (i in 1 until sig.size) {
            val d = sig[i] - sig[i - 1]
            val newDir = if (abs(d) > thresh) (if (d > 0) 1 else -1) else 0
            if (newDir != 0 && newDir != dir) { count++; dir = newDir }
            if (i - windowStart > windowSamples) {
                if (count > 3) {
                    out += Warning(windowStart * stepMs, i * stepMs, kind)
                }
                windowStart = i
                count = 0
                dir = 0
            }
        }
        return out
    }

    private fun avgLumAndRed(bmp: Bitmap): Pair<Float, Float> {
        val w = (bmp.width / 8).coerceAtLeast(8)
        val h = (bmp.height / 8).coerceAtLeast(8)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled != bmp) scaled.recycle()
        var lum = 0.0; var red = 0.0
        for (p in px) {
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            lum += 0.2126 * r + 0.7152 * g + 0.0722 * b
            red += r - maxOf(g, b) * 0.5f
        }
        val n = px.size.toFloat()
        return (lum / n).toFloat() to (red / n).toFloat().coerceIn(0f, 1f)
    }

    companion object { private const val TAG = "FlashSafetyEngine" }
}
