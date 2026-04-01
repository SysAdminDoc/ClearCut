package com.novacut.editor.engine

import android.graphics.Bitmap
import android.view.PixelCopy
import android.view.SurfaceView
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import android.util.Log
import kotlin.coroutines.resume

/**
 * Captures the current frame from an ExoPlayer SurfaceView for scope analysis.
 * Uses PixelCopy API (requires API 24+).
 */
object FrameCapture {

    /**
     * Capture the current frame from a SurfaceView.
     * Returns a downscaled bitmap suitable for scope analysis.
     */
    suspend fun captureFrame(surfaceView: SurfaceView, maxSize: Int = 200): Bitmap? {
        if (surfaceView.width <= 0 || surfaceView.height <= 0) return null
        if (!surfaceView.holder.surface.isValid) return null

        return try {
            val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
            val result = suspendCancellableCoroutine<Boolean> { cont ->
                cont.invokeOnCancellation { bitmap.recycle() }
                try {
                    PixelCopy.request(
                        surfaceView, bitmap,
                        { copyResult ->
                            cont.resume(copyResult == PixelCopy.SUCCESS)
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (e: Exception) {
                    cont.resume(false)
                }
            }

            if (result) {
                // Downscale for performance
                val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
                if (scale < 1f) {
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    bitmap.recycle()
                    scaled
                } else bitmap
            } else {
                bitmap.recycle()
                null
            }
        } catch (e: Exception) {
            Log.w("FrameCapture", "Frame capture failed", e)
            null
        }
    }
}
