package com.novacut.editor.engine

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.content.Context
import com.novacut.editor.model.ColorGrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Analyzes the color properties of a reference frame and generates
 * color grading parameters to match a target clip to a reference clip.
 */
object ColorMatchEngine {

    data class ColorStats(
        val avgR: Float, val avgG: Float, val avgB: Float,
        val stdR: Float, val stdG: Float, val stdB: Float,
        val avgLuma: Float, val stdLuma: Float,
        val avgSat: Float
    )

    /**
     * Analyze a frame at the given time from a video URI.
     */
    suspend fun analyzeFrame(
        context: Context,
        uri: Uri,
        timeMs: Long
    ): ColorStats? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            bitmap?.let { analyzeBitmap(it) }
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (e: Exception) { Log.w("ColorMatchEngine", "Failed to release retriever", e) }
        }
    }

    /**
     * Analyze color statistics of a bitmap.
     */
    fun analyzeBitmap(bitmap: Bitmap): ColorStats {
        val scale = minOf(1f, 100f / maxOf(bitmap.width, bitmap.height))
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled != bitmap) scaled.recycle()

        var sumR = 0f; var sumG = 0f; var sumB = 0f
        var sumLuma = 0f; var sumSat = 0f

        pixels.forEach { pixel ->
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            sumR += r; sumG += g; sumB += b

            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            sumLuma += luma

            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            sumSat += if (maxC > 0) (maxC - minC) / maxC else 0f
        }

        val n = pixels.size.toFloat()
        val avgR = sumR / n; val avgG = sumG / n; val avgB = sumB / n
        val avgLuma = sumLuma / n; val avgSat = sumSat / n

        // Standard deviations
        var varR = 0f; var varG = 0f; var varB = 0f; var varLuma = 0f
        pixels.forEach { pixel ->
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            val luma = 0.299f * r + 0.587f * g + 0.114f * b

            varR += (r - avgR).pow(2)
            varG += (g - avgG).pow(2)
            varB += (b - avgB).pow(2)
            varLuma += (luma - avgLuma).pow(2)
        }

        return ColorStats(
            avgR, avgG, avgB,
            sqrt(varR / n), sqrt(varG / n), sqrt(varB / n),
            avgLuma, sqrt(varLuma / n),
            avgSat
        )
    }

    /**
     * Generate color grading parameters to match a target's look to a reference.
     * Uses Reinhard color transfer approach adapted for lift/gamma/gain controls.
     */
    fun generateColorMatch(reference: ColorStats, target: ColorStats): ColorGrade {
        // Compute gain to match standard deviations (contrast matching)
        val gainR = if (target.stdR > 0.001f) reference.stdR / target.stdR else 1f
        val gainG = if (target.stdG > 0.001f) reference.stdG / target.stdG else 1f
        val gainB = if (target.stdB > 0.001f) reference.stdB / target.stdB else 1f

        // Compute offset to match means (brightness matching)
        val offsetR = reference.avgR - target.avgR * gainR
        val offsetG = reference.avgG - target.avgG * gainG
        val offsetB = reference.avgB - target.avgB * gainB

        // Map to lift/gamma/gain model
        // Gain controls highlights, Lift controls shadows, Gamma controls midtones
        return ColorGrade(
            enabled = true,
            gainR = gainR.coerceIn(0.5f, 2f),
            gainG = gainG.coerceIn(0.5f, 2f),
            gainB = gainB.coerceIn(0.5f, 2f),
            offsetR = offsetR.coerceIn(-0.3f, 0.3f),
            offsetG = offsetG.coerceIn(-0.3f, 0.3f),
            offsetB = offsetB.coerceIn(-0.3f, 0.3f),
            // Per-channel gamma adjustment
            gammaR = if (target.avgR > 0.01f) {
                (1f + (reference.avgR - target.avgR) * 0.5f).coerceIn(0.5f, 2f)
            } else 1f,
            gammaG = if (target.avgG > 0.01f) {
                (1f + (reference.avgG - target.avgG) * 0.5f).coerceIn(0.5f, 2f)
            } else 1f,
            gammaB = if (target.avgB > 0.01f) {
                (1f + (reference.avgB - target.avgB) * 0.5f).coerceIn(0.5f, 2f)
            } else 1f
        )
    }
}
