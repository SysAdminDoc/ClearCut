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
    private val drawable: AnimatedImageDrawable,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val relStartUs: Long,
    private val relEndUs: Long,
    private val overlayDurationUs: Long,
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
            val canvas = Canvas(frameBitmap)
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            drawable.setBounds(0, 0, frameWidth, frameHeight)
            drawable.draw(canvas)
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

        fun create(
            context: Context,
            overlay: ImageOverlay,
            relStartMs: Long,
            relEndMs: Long,
            outputFrameWidth: Int,
        ): ExportAnimatedImageOverlay? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                Log.w(TAG, "AnimatedImageDrawable requires API 28+")
                return null
            }
            return try {
                val source = ImageDecoder.createSource(context.contentResolver, overlay.sourceUri)
                val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val targetW = ExportImageOverlay.outputWidthForFrame(overlay.scale, outputFrameWidth)
                        .coerceAtMost(MAX_OVERLAY_DIMENSION)
                    val scale = targetW.toFloat() / w.coerceAtLeast(1)
                    val targetH = (h * scale).toInt().coerceAtLeast(1).coerceAtMost(MAX_OVERLAY_DIMENSION)
                    decoder.setTargetSize(targetW, targetH)
                }
                if (drawable !is AnimatedImageDrawable) {
                    Log.w(TAG, "Source is not animated, falling back to static overlay")
                    return null
                }
                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                drawable.start()

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
                    drawable = drawable,
                    frameWidth = drawable.intrinsicWidth.coerceAtLeast(1),
                    frameHeight = drawable.intrinsicHeight.coerceAtLeast(1),
                    relStartUs = relStartUs,
                    relEndUs = relEndUs,
                    overlayDurationUs = relEndUs - relStartUs,
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
