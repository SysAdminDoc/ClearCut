package com.novacut.editor.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeProperty

internal fun DrawScope.drawTimeRuler(
    scrollOffsetMs: Long,
    pixelsPerMs: Float,
    width: Float,
    height: Float,
    textMeasurer: TextMeasurer,
    labelColor: Color,
) {
    if (pixelsPerMs <= 0f || !pixelsPerMs.isFinite() || width <= 0f) return
    val intervalMs = when {
        pixelsPerMs > 0.5f -> 1000L
        pixelsPerMs > 0.1f -> 5000L
        pixelsPerMs > 0.02f -> 10000L
        else -> 30000L
    }

    val startMs = (scrollOffsetMs / intervalMs) * intervalMs
    var currentMs = startMs
    val labelStyle = TextStyle(
        color = labelColor,
        fontSize = 9.sp
    )

    while (true) {
        val x = (currentMs - scrollOffsetMs) * pixelsPerMs
        if (x > width) break

        if (x >= 0) {
            val isMajor = currentMs % (intervalMs * 5) == 0L
            val tickHeight = if (isMajor) height * 0.4f else height * 0.25f

            drawLine(
                color = if (isMajor) Color(0xFF7F849C) else Color(0xFF45475A),
                start = Offset(x, height - tickHeight),
                end = Offset(x, height),
                strokeWidth = if (isMajor) 1.5f else 0.5f
            )

            if (isMajor) {
                val totalSeconds = (currentMs / 1000).toInt()
                val min = totalSeconds / 60
                val sec = totalSeconds % 60
                val label = if (min > 0) "$min:${"%02d".format(sec)}" else "${sec}s"
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(x - measured.size.width / 2f, 1f)
                )
            }
        }
        currentMs += intervalMs
    }
}

internal fun DrawScope.drawTimelineWaveform(samples: List<Float>, color: Color) {
    if (samples.isEmpty()) return
    val steps = (size.width / 3f).toInt().coerceAtLeast(1)
    val samplesPerStep = samples.size.toFloat() / steps
    val centerY = size.height / 2f
    val maxAmp = size.height * 0.45f

    for (i in 0 until steps) {
        val sampleIndex = (i * samplesPerStep).toInt().coerceIn(0, samples.size - 1)
        val raw = samples[sampleIndex]
        val amplitude = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0f
        val barH = (amplitude * maxAmp).coerceAtLeast(1f)
        val x = i * 3f

        drawLine(
            color = color.copy(alpha = 0.7f),
            start = Offset(x, centerY - barH),
            end = Offset(x, centerY + barH),
            strokeWidth = 2f
        )
    }
}

internal fun volumeKeyframesSorted(clip: Clip): List<Keyframe> =
    clip.keyframes
        .filter { it.property == KeyframeProperty.VOLUME }
        .sortedBy { it.timeOffsetMs }

internal fun DrawScope.drawTimelineWaveformPlaceholder(color: Color) {
    val steps = (size.width / 4f).toInt().coerceAtLeast(1)
    val centerY = size.height / 2f
    for (i in 0 until steps) {
        val x = i * 4f
        val amplitude = (kotlin.math.sin(i * 0.7) * 0.3 + 0.4).toFloat()
        val barH = (amplitude * size.height * 0.45f).coerceAtLeast(1f)
        drawLine(
            color = color.copy(alpha = 0.4f),
            start = Offset(x, centerY - barH),
            end = Offset(x, centerY + barH),
            strokeWidth = 2f
        )
    }
}
