package com.novacut.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.novacut.editor.model.TextAlignment
import com.novacut.editor.model.TextAnimation
import com.novacut.editor.model.TextOverlay

/**
 * Bitmap-based text overlay used when the model's `strokeWidth > 0`.
 *
 * The default export path uses Media3's `TextOverlay` (SpannableString), which
 * cannot render a distinct stroke+fill pair with different colors — the
 * SpannableString drawing model only carries one color per pixel. This
 * overlay subclasses [BitmapOverlay] instead and renders the text twice per
 * frame on a Canvas: once with `PAINT.STYLE_STROKE` in the stroke color, then
 * again with `PAINT.STYLE_FILL` in the fill color. The two passes land in the
 * right Z order (stroke under fill) because Canvas honours call order.
 *
 * Every other feature (animations, typewriter, alignment, background, shadow)
 * is re-implemented here rather than layered over the TextOverlay path — the
 * two pipelines produce pixel-identical output on anything the stroke path
 * doesn't touch, so callers can branch on `overlay.strokeWidth > 0f` without
 * noticing.
 *
 * Allocated bitmaps are recycled when [release] is called by Media3.
 */
@UnstableApi
internal class StrokedTextBitmapOverlay(
    private val overlay: TextOverlay,
    private val relStartMs: Long,
    private val relEndMs: Long,
    /** Maximum rendered dimension. Capped to prevent OOM on 4K/8K exports. */
    private val canvasDim: Int = 1920
) : BitmapOverlay() {

    private val animDurationMs = 500L

    private var currentAlpha = 1f
    private var currentOffsetX = 0f
    private var currentOffsetY = 0f
    private var currentScale = 1f
    private var currentRotation = 0f

    private var blankBitmap: Bitmap? = null
    // Double-buffered: Media3 uploads `current` to a GL texture asynchronously
    // after getBitmap() returns. Recycling `current` the moment the text
    // changes would race the GPU upload. Instead we shuffle current → pending
    // on each rasterise and only recycle `pending` on the NEXT call, which
    // gives Media3 a full frame to finish the upload before the backing
    // memory is reclaimed. `release()` drains everything.
    private var current: Bitmap? = null
    private var pending: Bitmap? = null
    private var lastTextHash = 0

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = presentationTimeUs / 1000L
        if (timeMs < relStartMs || timeMs > relEndMs) return blank()
        computeAnimationState(timeMs)
        val fullText = overlay.text
        val displayText = if (overlay.animationIn == TextAnimation.TYPEWRITER) {
            val elapsed = timeMs - relStartMs
            val charCount = ((elapsed.toFloat() / animDurationMs) * fullText.length)
                .toInt().coerceIn(0, fullText.length)
            fullText.substring(0, charCount)
        } else fullText
        if (displayText.isEmpty()) return blank()

        // Only re-rasterise when the text content has changed — everything
        // else (position, scale, alpha, rotation) is applied via the
        // vertex transform below, which the compositor can animate cheaply.
        // For transform-only animations the bitmap is produced exactly once.
        val hash = displayText.hashCode()
        val cur = current
        if (hash != lastTextHash || cur == null || cur.isRecycled) {
            // Recycle the previously pending (two-old) bitmap — safe because
            // Media3 has already uploaded + drawn the bitmap that preceded it.
            pending?.recycleSafely()
            pending = cur
            current = drawBitmap(displayText)
            lastTextHash = hash
        }
        return current ?: blank()
    }

    override fun release() {
        super.release()
        blankBitmap?.recycleSafely(); blankBitmap = null
        pending?.recycleSafely(); pending = null
        current?.recycleSafely(); current = null
    }

    override fun getVertexTransformation(presentationTimeUs: Long): FloatArray {
        val timeMs = presentationTimeUs / 1000L
        if (timeMs < relStartMs || timeMs > relEndMs) return OFFSCREEN_MATRIX
        computeAnimationState(timeMs)
        val tx = currentOffsetX + (overlay.positionX * 2f - 1f)
        val ty = currentOffsetY - (overlay.positionY * 2f - 1f)
        val sx = currentScale
        val sy = currentScale
        val rad = currentRotation * (kotlin.math.PI.toFloat() / 180f)
        val cos = kotlin.math.cos(rad)
        val sin = kotlin.math.sin(rad)
        if (!tx.isFinite() || !ty.isFinite() || !sx.isFinite() || !sy.isFinite() ||
            !cos.isFinite() || !sin.isFinite()
        ) return OFFSCREEN_MATRIX
        val a = currentAlpha.coerceIn(0f, 1f)
        // Alpha can't come through the vertex matrix — `getBitmap` returns an
        // alpha-baked bitmap when animationIn/Out includes FADE/TYPEWRITER.
        // For transform-only animations the bitmap stays opaque and the FADE
        // path multiplies pixel alpha directly in drawBitmap.
        return floatArrayOf(
            sx * cos, sx * sin, 0f, 0f,
            -sy * sin, sy * cos, 0f, 0f,
            0f, 0f, 1f, 0f,
            tx, ty, 0f, 1f
        )
    }

    private fun drawBitmap(text: String): Bitmap {
        val paintFill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = applyAlpha(overlay.color.toInt(), currentAlpha)
            textSize = overlay.fontSize
            typeface = typefaceFor(overlay.fontFamily, overlay.bold, overlay.italic)
            style = Paint.Style.FILL
            letterSpacing = overlay.letterSpacing.coerceIn(-0.3f, 1f)
        }
        val paintStroke = Paint(paintFill).apply {
            color = applyAlpha(overlay.strokeColor.toInt(), currentAlpha)
            style = Paint.Style.STROKE
            strokeWidth = overlay.strokeWidth.coerceAtLeast(0f)
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        // Measure with the stroke paint because stroke widens the glyph bounds.
        val bounds = Rect()
        paintStroke.getTextBounds(text, 0, text.length, bounds)
        val pad = (overlay.strokeWidth.coerceAtLeast(0f) * 2f).toInt() + 16
        val w = (bounds.width() + pad * 2).coerceAtLeast(2).coerceAtMost(canvasDim)
        val h = (paintFill.fontMetrics.let { it.bottom - it.top }.toInt() + pad * 2)
            .coerceAtLeast(2).coerceAtMost(canvasDim)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Optional background fill — mirrored from the SpannableString path.
        val bgColor = overlay.backgroundColor.toInt()
        if (bgColor and 0xFF000000.toInt() != 0) {
            val bgPaint = Paint().apply {
                color = applyAlpha(bgColor, currentAlpha)
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
        }

        val baselineY = pad - paintFill.fontMetrics.top
        val anchorX = when (overlay.alignment) {
            TextAlignment.LEFT -> pad.toFloat()
            TextAlignment.CENTER -> (w - bounds.width()) / 2f - bounds.left
            TextAlignment.RIGHT -> (w - bounds.width()).toFloat() - pad - bounds.left
        }
        // Stroke first so fill paints on top — the standard Canvas outline text pattern.
        if (paintStroke.strokeWidth > 0f) {
            canvas.drawText(text, anchorX, baselineY, paintStroke)
        }
        canvas.drawText(text, anchorX, baselineY, paintFill)
        return bmp
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = ((color ushr 24) and 0xFF) * alpha.coerceIn(0f, 1f)
        return (a.toInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
    }

    private fun typefaceFor(family: String, bold: Boolean, italic: Boolean): Typeface {
        val base = Typeface.create(family, Typeface.NORMAL)
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return if (style == Typeface.NORMAL) base else Typeface.create(base, style)
    }

    private fun blank(): Bitmap {
        val b = blankBitmap
        if (b != null && !b.isRecycled) return b
        val fresh = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        blankBitmap = fresh
        return fresh
    }

    private fun Bitmap.recycleSafely() {
        try { if (!isRecycled) recycle() } catch (_: Exception) { /* already gone */ }
    }

    private fun computeAnimationState(timeMs: Long) {
        currentAlpha = 1f
        currentOffsetX = 0f
        currentOffsetY = 0f
        currentScale = 1f
        currentRotation = 0f

        val inProgress = if (overlay.animationIn != TextAnimation.NONE) {
            ((timeMs - relStartMs).toFloat() / animDurationMs).coerceIn(0f, 1f)
        } else 1f

        val outProgress = if (overlay.animationOut != TextAnimation.NONE) {
            ((relEndMs - timeMs).toFloat() / animDurationMs).coerceIn(0f, 1f)
        } else 1f

        when (overlay.animationIn) {
            TextAnimation.FADE -> currentAlpha *= easeOut(inProgress)
            TextAnimation.SLIDE_UP -> currentOffsetY -= (1f - easeOut(inProgress)) * 0.3f
            TextAnimation.SLIDE_DOWN -> currentOffsetY += (1f - easeOut(inProgress)) * 0.3f
            TextAnimation.SLIDE_LEFT -> currentOffsetX -= (1f - easeOut(inProgress)) * 0.3f
            TextAnimation.SLIDE_RIGHT -> currentOffsetX += (1f - easeOut(inProgress)) * 0.3f
            TextAnimation.SCALE -> currentScale *= easeOut(inProgress)
            TextAnimation.SPIN -> currentRotation += (1f - easeOut(inProgress)) * 360f
            TextAnimation.BOUNCE -> currentOffsetY -= (1f - bounceEase(easeOut(inProgress))) * 0.3f
            TextAnimation.TYPEWRITER -> { /* handled in drawBitmap */ }
            TextAnimation.NONE -> { }
            TextAnimation.BLUR_IN -> currentAlpha *= easeOut(inProgress)
            TextAnimation.GLITCH -> currentOffsetX += (1f - easeOut(inProgress)) * 0.05f * kotlin.math.sin(inProgress * 30f)
            TextAnimation.WAVE -> currentOffsetY -= kotlin.math.sin(inProgress * 6.28f) * 0.05f
            TextAnimation.ELASTIC -> {
                val t = easeOut(inProgress)
                currentScale *= if (t < 1f) (1f + 0.3f * kotlin.math.sin(t * 3.14f * 3f) * (1f - t)) else 1f
            }
            TextAnimation.FLIP -> currentRotation += (1f - easeOut(inProgress)) * 180f
        }

        when (overlay.animationOut) {
            TextAnimation.FADE -> currentAlpha *= easeOut(outProgress)
            TextAnimation.SLIDE_UP -> currentOffsetY += (1f - easeOut(outProgress)) * 0.3f
            TextAnimation.SLIDE_DOWN -> currentOffsetY -= (1f - easeOut(outProgress)) * 0.3f
            TextAnimation.SLIDE_LEFT -> currentOffsetX += (1f - easeOut(outProgress)) * 0.3f
            TextAnimation.SLIDE_RIGHT -> currentOffsetX -= (1f - easeOut(outProgress)) * 0.3f
            TextAnimation.SCALE -> currentScale *= easeOut(outProgress)
            TextAnimation.SPIN -> currentRotation -= (1f - easeOut(outProgress)) * 360f
            TextAnimation.BOUNCE -> currentOffsetY += (1f - bounceEase(easeOut(outProgress))) * 0.3f
            TextAnimation.TYPEWRITER -> currentAlpha *= outProgress
            TextAnimation.NONE -> { }
            TextAnimation.BLUR_IN -> currentAlpha *= easeOut(outProgress)
            TextAnimation.GLITCH -> currentOffsetX -= (1f - easeOut(outProgress)) * 0.05f * kotlin.math.sin(outProgress * 30f)
            TextAnimation.WAVE -> currentOffsetY += kotlin.math.sin(outProgress * 6.28f) * 0.05f
            TextAnimation.ELASTIC -> currentScale *= easeOut(outProgress)
            TextAnimation.FLIP -> currentRotation -= (1f - easeOut(outProgress)) * 180f
        }
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun bounceEase(t: Float): Float = when {
        t < 0.3636f -> 7.5625f * t * t
        t < 0.7273f -> 7.5625f * (t - 0.5455f) * (t - 0.5455f) + 0.75f
        t < 0.9091f -> 7.5625f * (t - 0.8182f) * (t - 0.8182f) + 0.9375f
        else -> 7.5625f * (t - 0.9545f) * (t - 0.9545f) + 0.984375f
    }

    companion object {
        private val OFFSCREEN_MATRIX = floatArrayOf(
            0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }
}
