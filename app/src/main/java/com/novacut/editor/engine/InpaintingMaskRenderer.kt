package com.novacut.editor.engine

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.novacut.editor.model.Mask
import com.novacut.editor.model.MaskPoint
import com.novacut.editor.model.MaskType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Converts the editor's normalized mask geometry into a binary bitmap for LaMa.
 *
 * The preview and export paths use the same point conventions: rectangles are
 * two corners, ellipses are center/radius, and freehand masks are closed
 * polygons. Gradient masks are deliberately rejected because LaMa expects a
 * binary removal region, not a blend mask.
 */
object InpaintingMaskRenderer {

    enum class ShapeSupport {
        RECTANGLE,
        ELLIPSE,
        FREEHAND,
        UNSUPPORTED
    }

    fun supportFor(mask: Mask): ShapeSupport = when (mask.type) {
        MaskType.RECTANGLE -> if (mask.points.size >= 2) ShapeSupport.RECTANGLE else ShapeSupport.UNSUPPORTED
        MaskType.ELLIPSE -> if (mask.points.size >= 2) ShapeSupport.ELLIPSE else ShapeSupport.UNSUPPORTED
        MaskType.FREEHAND -> if (mask.points.size >= 3) ShapeSupport.FREEHAND else ShapeSupport.UNSUPPORTED
        MaskType.LINEAR_GRADIENT,
        MaskType.RADIAL_GRADIENT -> ShapeSupport.UNSUPPORTED
    }

    fun supports(mask: Mask): Boolean = supportFor(mask) != ShapeSupport.UNSUPPORTED

    /** Render a mask at [timeOffsetMs], or return null for invalid/unsupported geometry. */
    fun render(
        mask: Mask,
        timeOffsetMs: Long,
        width: Int,
        height: Int
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null

        val points = KeyframeEngine.interpolateMaskPoints(mask, timeOffsetMs)
        val support = supportFor(mask)
        if (support == ShapeSupport.UNSUPPORTED || points.size < minimumPointCount(support)) {
            return null
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            // Mask opacity is a preview concern. LaMa consumes a binary mask,
            // so a selected region must remain fully selected here.
            alpha = 255
            val featherPx = (mask.feather.coerceAtLeast(0f) / 100f) * min(width, height)
            if (featherPx > 0.5f) {
                maskFilter = BlurMaskFilter(featherPx.coerceAtMost(min(width, height) / 2f), BlurMaskFilter.Blur.NORMAL)
            }
        }

        val drawPoint = { point: MaskPoint ->
            android.graphics.PointF(
                normalized(point.x) * width,
                normalized(point.y) * height
            )
        }

        when (support) {
            ShapeSupport.RECTANGLE -> {
                val first = drawPoint(points[0])
                val second = drawPoint(points[1])
                val rect = RectF(
                    min(first.x, second.x),
                    min(first.y, second.y),
                    max(first.x, second.x),
                    max(first.y, second.y)
                )
                canvas.drawRect(rect, paint)
                drawExpansion(canvas, paint, rect, mask.expansion, width, height)
            }

            ShapeSupport.ELLIPSE -> {
                val center = drawPoint(points[0])
                val radius = drawPoint(points[1])
                val radiusX = abs(radius.x).coerceAtLeast(1f)
                val radiusY = abs(radius.y).coerceAtLeast(1f)
                val oval = RectF(
                    center.x - radiusX,
                    center.y - radiusY,
                    center.x + radiusX,
                    center.y + radiusY
                )
                canvas.drawOval(oval, paint)
                drawExpansion(canvas, paint, oval, mask.expansion, width, height)
            }

            ShapeSupport.FREEHAND -> {
                val path = Path()
                points.map(drawPoint).forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                path.close()
                canvas.drawPath(path, paint)
                drawExpansion(canvas, paint, path, mask.expansion, width, height)
            }

            ShapeSupport.UNSUPPORTED -> return null
        }

        if (mask.inverted) invert(output)
        return output
    }

    private fun minimumPointCount(support: ShapeSupport): Int = when (support) {
        ShapeSupport.RECTANGLE, ShapeSupport.ELLIPSE -> 2
        ShapeSupport.FREEHAND -> 3
        ShapeSupport.UNSUPPORTED -> Int.MAX_VALUE
    }

    private fun normalized(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    private fun drawExpansion(
        canvas: Canvas,
        sourcePaint: Paint,
        rect: RectF,
        expansion: Float,
        width: Int,
        height: Int
    ) {
        if (expansion <= 0f) return
        val stroke = (expansion / 100f * min(width, height)).coerceAtMost(min(width, height).toFloat())
        if (stroke <= 0f) return
        val paint = Paint(sourcePaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke * 2f
            maskFilter = null
        }
        canvas.drawRect(rect, paint)
    }

    private fun drawExpansion(
        canvas: Canvas,
        sourcePaint: Paint,
        path: Path,
        expansion: Float,
        width: Int,
        height: Int
    ) {
        if (expansion <= 0f) return
        val stroke = (expansion / 100f * min(width, height)).coerceAtMost(min(width, height).toFloat())
        if (stroke <= 0f) return
        val paint = Paint(sourcePaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke * 2f
            maskFilter = null
        }
        canvas.drawPath(path, paint)
    }

    private fun invert(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.indices.forEach { index ->
            pixels[index] = if (Color.red(pixels[index]) > 127) Color.BLACK else Color.WHITE
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
}
