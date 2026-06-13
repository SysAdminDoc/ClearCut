package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.novacut.editor.model.ImageOverlay

@UnstableApi
internal class ExportAnimatedImageOverlay private constructor(
    private val movie: android.graphics.Movie,
    private val animDurationMs: Int,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val relStartUs: Long,
    private val relEndUs: Long,
    private val visibleSettings: StaticOverlaySettings,
) : BitmapOverlay() {

    private val hiddenSettings = StaticOverlaySettings.Builder()
        .setAlphaScale(0f)
        .build()

    private var frameBitmap: Bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
    private var lastFrameTimeMs = -1L

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val relativeUs = (presentationTimeUs - relStartUs).coerceAtLeast(0L)
        val frameTimeMs = (relativeUs / 1000L)
        val quantizedMs = (frameTimeMs / 33L) * 33L
        if (quantizedMs != lastFrameTimeMs) {
            lastFrameTimeMs = quantizedMs
            val loopedMs = if (animDurationMs > 0) (frameTimeMs % animDurationMs).toInt() else 0
            movie.setTime(loopedMs)
            val canvas = Canvas(frameBitmap)
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            val scaleX = frameWidth.toFloat() / movie.width().coerceAtLeast(1)
            val scaleY = frameHeight.toFloat() / movie.height().coerceAtLeast(1)
            canvas.scale(scaleX, scaleY)
            movie.draw(canvas, 0f, 0f)
        }
        return frameBitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return if (ExportImageOverlay.isActiveAt(presentationTimeUs, relStartUs, relEndUs)) {
            visibleSettings
        } else {
            hiddenSettings
        }
    }

    override fun release() {
        super.release()
        try { if (!frameBitmap.isRecycled) frameBitmap.recycle() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "ExportAnimatedOverlay"
        private const val MAX_OVERLAY_DIMENSION = 512

        @Suppress("DEPRECATION")
        fun create(
            context: Context,
            overlay: ImageOverlay,
            relStartMs: Long,
            relEndMs: Long,
            outputFrameWidth: Int,
        ): ExportAnimatedImageOverlay? {
            return try {
                val inputStream = context.contentResolver.openInputStream(overlay.sourceUri)
                    ?: run {
                        Log.w(TAG, "Cannot open animated overlay ${overlay.sourceUri}")
                        return null
                    }
                val movie = inputStream.use { android.graphics.Movie.decodeStream(it) }
                    ?: run {
                        Log.w(TAG, "Movie.decodeStream returned null for ${overlay.sourceUri}")
                        return null
                    }
                val movieW = movie.width().coerceAtLeast(1)
                val movieH = movie.height().coerceAtLeast(1)
                val targetW = ExportImageOverlay.outputWidthForFrame(overlay.scale, outputFrameWidth)
                    .coerceAtMost(MAX_OVERLAY_DIMENSION)
                val scale = targetW.toFloat() / movieW
                val targetH = (movieH * scale).toInt().coerceAtLeast(1).coerceAtMost(MAX_OVERLAY_DIMENSION)

                val settings = StaticOverlaySettings.Builder()
                    .setAlphaScale(overlay.opacity.coerceIn(0f, 1f))
                    .setOverlayFrameAnchor(0f, 0f)
                    .setBackgroundFrameAnchor(
                        ExportImageOverlay.backgroundAnchorX(overlay.positionX),
                        ExportImageOverlay.backgroundAnchorY(overlay.positionY),
                    )
                    .setRotationDegrees(overlay.rotation)
                    .build()

                val relStartUs = relStartMs.coerceAtLeast(0L) * 1000L
                val relEndUs = relEndMs.coerceAtLeast(relStartMs + 1L) * 1000L

                ExportAnimatedImageOverlay(
                    movie = movie,
                    animDurationMs = movie.duration().coerceAtLeast(0),
                    frameWidth = targetW,
                    frameHeight = targetH,
                    relStartUs = relStartUs,
                    relEndUs = relEndUs,
                    visibleSettings = settings,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode animated overlay ${overlay.sourceUri}", e)
                null
            }
        }

        fun isAnimatedSource(context: Context, uri: Uri): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val drawable = ImageDecoder.decodeDrawable(source)
                val result = drawable is AnimatedImageDrawable
                drawable.callback = null
                result
            } catch (_: Exception) {
                false
            }
        }
    }
}
