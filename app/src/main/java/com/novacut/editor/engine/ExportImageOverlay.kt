package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.novacut.editor.model.ImageOverlay

/**
 * Media3 overlay adapter for still sticker/image overlays.
 *
 * Geometry matches the Compose preview path:
 * - positionX/positionY are normalized offsets from frame center;
 * - scale is the fraction of output frame width occupied by the bitmap;
 * - rotation is degrees; opacity is alpha.
 */
@UnstableApi
internal class ExportImageOverlay private constructor(
    private val bitmap: Bitmap,
    private val relStartUs: Long,
    private val relEndUs: Long,
    private val visibleSettings: StaticOverlaySettings,
) : BitmapOverlay() {

    private val hiddenSettings = StaticOverlaySettings.Builder()
        .setAlphaScale(0f)
        .build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return if (isActiveAt(presentationTimeUs, relStartUs, relEndUs)) {
            visibleSettings
        } else {
            hiddenSettings
        }
    }

    override fun release() {
        super.release()
        try {
            if (!bitmap.isRecycled) bitmap.recycle()
        } catch (_: Exception) {
            // Already detached by the platform.
        }
    }

    companion object {
        private const val TAG = "ExportImageOverlay"

        fun create(
            context: Context,
            overlay: ImageOverlay,
            relStartMs: Long,
            relEndMs: Long,
            outputFrameWidth: Int,
        ): ExportImageOverlay? {
            val bitmap = loadBitmap(context, overlay.sourceUri) ?: return null
            val scaled = scaleBitmapToOutputWidth(bitmap, overlay.scale, outputFrameWidth)
            val settings = StaticOverlaySettings.Builder()
                .setAlphaScale(overlay.opacity.coerceIn(0f, 1f))
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(
                    backgroundAnchorX(overlay.positionX),
                    backgroundAnchorY(overlay.positionY),
                )
                .setRotationDegrees(overlay.rotation)
                .build()
            return ExportImageOverlay(
                bitmap = scaled,
                relStartUs = relStartMs.coerceAtLeast(0L) * 1000L,
                relEndUs = relEndMs.coerceAtLeast(relStartMs + 1L) * 1000L,
                visibleSettings = settings,
            )
        }

        fun isActiveAt(presentationTimeUs: Long, relStartUs: Long, relEndUs: Long): Boolean =
            presentationTimeUs >= relStartUs && presentationTimeUs <= relEndUs

        fun outputWidthForFrame(overlayScale: Float, outputFrameWidth: Int): Int {
            val safeFrameWidth = outputFrameWidth.coerceAtLeast(1)
            return (safeFrameWidth * overlayScale.coerceIn(0.01f, 2f))
                .toInt()
                .coerceAtLeast(1)
        }

        fun backgroundAnchorX(positionX: Float): Float =
            positionX.takeIf { it.isFinite() }?.coerceIn(-5f, 5f) ?: 0f

        fun backgroundAnchorY(positionY: Float): Float =
            -(positionY.takeIf { it.isFinite() }?.coerceIn(-5f, 5f) ?: 0f)

        private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
            return try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode image overlay $uri", e)
                null
            }
        }

        private fun scaleBitmapToOutputWidth(
            bitmap: Bitmap,
            overlayScale: Float,
            outputFrameWidth: Int,
        ): Bitmap {
            val targetWidth = outputWidthForFrame(overlayScale, outputFrameWidth)
            if (bitmap.width == targetWidth) return bitmap
            val scale = targetWidth.toFloat() / bitmap.width.coerceAtLeast(1).toFloat()
            val matrix = Matrix().apply { postScale(scale, scale) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it !== bitmap) bitmap.recycleSafely() }
        }

        private fun Bitmap.recycleSafely() {
            try {
                if (!isRecycled) recycle()
            } catch (_: Exception) {
                // Already detached by the platform.
            }
        }
    }
}
