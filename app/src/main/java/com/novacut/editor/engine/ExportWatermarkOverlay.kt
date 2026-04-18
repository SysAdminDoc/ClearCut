package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.novacut.editor.model.Watermark
import com.novacut.editor.model.WatermarkPosition

/**
 * Brand-watermark overlay applied across every clip during export. Decodes
 * the user's image once in `load()`, scales it to the requested percentage
 * of the output frame width, then hands it to Media3 as a static
 * `BitmapOverlay`. Position maps to normalised anchor coordinates
 * (see [overlayAnchorFor]) so the same watermark lands consistently across
 * every resolution / aspect ratio.
 *
 * Failure modes are intentionally silent + non-fatal: a missing or corrupt
 * image resolves to a 1×1 transparent bitmap so the overlay effect still
 * attaches cleanly (no broken GL pipeline) but contributes nothing visible.
 * Callers can check the returned `bitmap.width == 1 && .height == 1` if they
 * want to surface a warning.
 */
@UnstableApi
internal object ExportWatermarkOverlay {

    private const val TAG = "ExportWatermarkOverlay"
    private const val TRANSPARENT_PLACEHOLDER_PX = 1

    /**
     * Build a Media3 BitmapOverlay for the given watermark spec. Returns null
     * when the watermark's bitmap can't be loaded — caller should treat null
     * as "no watermark for this export" rather than erroring out the whole
     * render, since the user's brand asset being unreadable is a
     * user-facing content issue, not an engine failure.
     */
    fun create(
        context: Context,
        watermark: Watermark,
        outputFrameWidth: Int
    ): BitmapOverlay? {
        val bitmap = loadBitmap(context, watermark.sourceUri) ?: return null
        // Scale the watermark to the requested % of the *output* width. Using
        // output width (not input) means the watermark visually occupies the
        // same fraction of the final video regardless of source resolution
        // variation across clips.
        val targetWidth = (outputFrameWidth * watermark.scalePercent / 100)
            .coerceAtLeast(1)
        val scale = targetWidth.toFloat() / bitmap.width.toFloat()
        val scaled = if (scale != 1f) {
            val matrix = Matrix().apply { postScale(scale, scale) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        } else bitmap

        val settings = StaticOverlaySettings.Builder()
            .setAlphaScale(watermark.opacity)
            .setOverlayFrameAnchor(0f, 0f)  // bitmap's own center
            .setBackgroundFrameAnchor(
                overlayAnchorXFor(watermark.position),
                overlayAnchorYFor(watermark.position)
            )
            .build()
        return BitmapOverlay.createStaticBitmapOverlay(scaled, settings)
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode watermark $uri", e)
            null
        }
    }

    // Media3 anchor coords: x ∈ [-1, 1] left→right, y ∈ [-1, 1] bottom→top
    // (OpenGL convention — +y is up). We offset slightly from the edges so the
    // watermark doesn't kiss the frame border; 0.85 ≈ 7.5% margin from the
    // nearest edge, which matches the most common professional guide-safe
    // placement.
    private fun overlayAnchorXFor(p: WatermarkPosition): Float = when (p) {
        WatermarkPosition.TOP_LEFT, WatermarkPosition.BOTTOM_LEFT -> -0.85f
        WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT -> 0.85f
        WatermarkPosition.CENTER -> 0f
    }

    private fun overlayAnchorYFor(p: WatermarkPosition): Float = when (p) {
        WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT -> 0.85f
        WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT -> -0.85f
        WatermarkPosition.CENTER -> 0f
    }
}
