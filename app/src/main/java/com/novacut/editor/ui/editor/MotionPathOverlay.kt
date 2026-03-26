package com.novacut.editor.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.novacut.editor.engine.KeyframeEngine
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.ui.theme.Mocha

/**
 * Draws the motion path (position X/Y keyframes) on the video preview as a bezier curve.
 * Shows keyframe dots along the path and the current position indicator.
 */
@Composable
fun MotionPathOverlay(
    keyframes: List<Keyframe>,
    clipDurationMs: Long,
    currentTimeMs: Long,
    previewWidth: Float,
    previewHeight: Float,
    modifier: Modifier = Modifier
) {
    val posXKeyframes = keyframes.filter { it.property == KeyframeProperty.POSITION_X }
    val posYKeyframes = keyframes.filter { it.property == KeyframeProperty.POSITION_Y }

    if (posXKeyframes.size < 2 && posYKeyframes.size < 2) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Draw the motion path as a smooth curve
        val path = Path()
        val steps = 100
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val timeMs = (t * clipDurationMs).toLong()
            val px = KeyframeEngine.getValueAt(keyframes, KeyframeProperty.POSITION_X, timeMs) ?: 0f
            val py = KeyframeEngine.getValueAt(keyframes, KeyframeProperty.POSITION_Y, timeMs) ?: 0f

            val screenX = w / 2f + px * w / 2f
            val screenY = h / 2f + py * h / 2f

            if (i == 0) path.moveTo(screenX, screenY) else path.lineTo(screenX, screenY)
        }
        drawPath(path, Mocha.Yellow.copy(alpha = 0.6f), style = Stroke(width = 2f))

        // Draw keyframe dots on the path
        val allPosKfs = keyframes.filter {
            it.property == KeyframeProperty.POSITION_X || it.property == KeyframeProperty.POSITION_Y
        }.map { it.timeOffsetMs }.distinct().sorted()

        allPosKfs.forEach { timeMs ->
            val px = KeyframeEngine.getValueAt(keyframes, KeyframeProperty.POSITION_X, timeMs) ?: 0f
            val py = KeyframeEngine.getValueAt(keyframes, KeyframeProperty.POSITION_Y, timeMs) ?: 0f
            val screenX = w / 2f + px * w / 2f
            val screenY = h / 2f + py * h / 2f

            drawCircle(Color.White, 5f, Offset(screenX, screenY))
            drawCircle(Mocha.Mauve, 3.5f, Offset(screenX, screenY))
        }

        // Draw current position indicator
        val currentPx = KeyframeEngine.getValueAt(keyframes, KeyframeProperty.POSITION_X, currentTimeMs) ?: 0f
        val currentPy = KeyframeEngine.getValueAt(keyframes, KeyframeProperty.POSITION_Y, currentTimeMs) ?: 0f
        val currentScreenX = w / 2f + currentPx * w / 2f
        val currentScreenY = h / 2f + currentPy * h / 2f

        drawCircle(Color.White, 8f, Offset(currentScreenX, currentScreenY))
        drawCircle(Mocha.Red, 6f, Offset(currentScreenX, currentScreenY))

        // Direction arrow at current position
        if (clipDurationMs > 0) {
            val nextTimeMs = (currentTimeMs + 100).coerceAtMost(clipDurationMs)
            val nextPx = KeyframeEngine.getValueAt(keyframes, KeyframeProperty.POSITION_X, nextTimeMs) ?: currentPx
            val nextPy = KeyframeEngine.getValueAt(keyframes, KeyframeProperty.POSITION_Y, nextTimeMs) ?: currentPy
            val nextScreenX = w / 2f + nextPx * w / 2f
            val nextScreenY = h / 2f + nextPy * h / 2f

            val dx = nextScreenX - currentScreenX
            val dy = nextScreenY - currentScreenY
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len > 1f) {
                val ndx = dx / len * 12f
                val ndy = dy / len * 12f
                val arrowPath = Path().apply {
                    moveTo(currentScreenX + ndx, currentScreenY + ndy)
                    lineTo(currentScreenX + ndx - ndy * 0.4f, currentScreenY + ndy + ndx * 0.4f)
                    moveTo(currentScreenX + ndx, currentScreenY + ndy)
                    lineTo(currentScreenX + ndx + ndy * 0.4f, currentScreenY + ndy - ndx * 0.4f)
                }
                drawPath(arrowPath, Mocha.Red, style = Stroke(2f))
            }
        }
    }
}
