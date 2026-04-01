package com.novacut.editor.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.novacut.editor.engine.KeyframeEngine
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Interactive volume envelope drawn over a clip's waveform on the timeline.
 * Users can tap to add keyframes, drag to move them, creating a volume automation curve.
 */
@Composable
fun VolumeEnvelopeEditor(
    keyframes: List<Keyframe>,
    clipDurationMs: Long,
    clipVolume: Float,
    onKeyframesChanged: (List<Keyframe>) -> Unit,
    onDragStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (clipDurationMs <= 0L) return

    var selectedKfIndex by remember { mutableIntStateOf(-1) }
    val volumeKeyframes = keyframes.filter { it.property == KeyframeProperty.VOLUME }
        .sortedBy { it.timeOffsetMs }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(keyframes, clipDurationMs) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        // Add new volume keyframe at tap position
                        val timeMs = (offset.x / size.width * clipDurationMs).toLong()
                            .coerceIn(0L, clipDurationMs)
                        val volume = ((1f - offset.y / size.height) * 2f)
                            .coerceIn(0f, 2f)

                        val newKf = Keyframe(
                            timeOffsetMs = timeMs,
                            property = KeyframeProperty.VOLUME,
                            value = volume,
                            easing = Easing.LINEAR
                        )
                        val otherKfs = keyframes.filter { it.property != KeyframeProperty.VOLUME }
                        onKeyframesChanged(otherKfs + (volumeKeyframes + newKf).sortedBy { it.timeOffsetMs })
                    },
                    onTap = { offset ->
                        // Select nearest keyframe
                        val hitRadius = 20f
                        var bestIdx = -1
                        var bestDist = hitRadius * hitRadius

                        volumeKeyframes.forEachIndexed { idx, kf ->
                            val kfX = (kf.timeOffsetMs.toFloat() / clipDurationMs) * size.width
                            val kfY = (1f - kf.value / 2f) * size.height
                            val dx = offset.x - kfX
                            val dy = offset.y - kfY
                            val dist = dx * dx + dy * dy
                            if (dist < bestDist) {
                                bestDist = dist
                                bestIdx = idx
                            }
                        }
                        selectedKfIndex = bestIdx
                    }
                )
            }
            .pointerInput(keyframes, clipDurationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onDragStarted()
                        // Find nearest keyframe to start dragging
                        val hitRadius = 25f
                        var bestIdx = -1
                        var bestDist = hitRadius * hitRadius

                        volumeKeyframes.forEachIndexed { idx, kf ->
                            val kfX = (kf.timeOffsetMs.toFloat() / clipDurationMs) * size.width
                            val kfY = (1f - kf.value / 2f) * size.height
                            val dx = offset.x - kfX
                            val dy = offset.y - kfY
                            val dist = dx * dx + dy * dy
                            if (dist < bestDist) {
                                bestDist = dist
                                bestIdx = idx
                            }
                        }
                        selectedKfIndex = bestIdx
                    },
                    onDrag = { change, _ ->
                        if (selectedKfIndex in volumeKeyframes.indices) {
                            val timeMs = (change.position.x / size.width * clipDurationMs).toLong()
                                .coerceIn(0L, clipDurationMs)
                            val volume = ((1f - change.position.y / size.height) * 2f)
                                .coerceIn(0f, 2f)

                            val updatedVolumeKfs = volumeKeyframes.toMutableList()
                            updatedVolumeKfs[selectedKfIndex] = updatedVolumeKfs[selectedKfIndex].copy(
                                timeOffsetMs = timeMs,
                                value = volume
                            )
                            val otherKfs = keyframes.filter { it.property != KeyframeProperty.VOLUME }
                            onKeyframesChanged(otherKfs + updatedVolumeKfs)
                        }
                    },
                    onDragEnd = { selectedKfIndex = -1 }
                )
            }
    ) {
        val w = size.width
        val h = size.height

        // Draw the volume envelope curve
        if (volumeKeyframes.size >= 2) {
            val path = Path()
            val steps = 100
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val timeMs = (t * clipDurationMs).toLong()
                val vol = KeyframeEngine.getValueAt(volumeKeyframes, KeyframeProperty.VOLUME, timeMs) ?: clipVolume
                val x = t * w
                val y = (1f - vol / 2f) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Mocha.Yellow.copy(alpha = 0.8f), style = Stroke(width = 2f))

            // Filled area under curve
            val fillPath = Path()
            fillPath.addPath(path)
            fillPath.lineTo(w, h)
            fillPath.lineTo(0f, h)
            fillPath.close()
            drawPath(fillPath, Mocha.Yellow.copy(alpha = 0.08f))
        } else {
            // Draw constant volume line
            val y = (1f - clipVolume / 2f) * h
            drawLine(Mocha.Yellow.copy(alpha = 0.4f), Offset(0f, y), Offset(w, y), 1f)
        }

        // Draw keyframe dots
        volumeKeyframes.forEachIndexed { idx, kf ->
            val x = (kf.timeOffsetMs.toFloat() / clipDurationMs) * w
            val y = (1f - kf.value / 2f) * h
            val isSelected = idx == selectedKfIndex

            drawCircle(Color.White, if (isSelected) 7f else 5f, Offset(x, y))
            drawCircle(
                if (isSelected) Mocha.Red else Mocha.Mauve,
                if (isSelected) 5f else 3.5f,
                Offset(x, y)
            )
        }

        // Reference lines
        val refY100 = (1f - 1f / 2f) * h // 100% volume
        drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, refY100), Offset(w, refY100), 0.5f)
    }
}
