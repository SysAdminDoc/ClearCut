package com.novacut.editor.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.novacut.editor.ui.theme.Mocha
import kotlin.math.*

private const val HANDLE_RADIUS = 10f
private const val ROTATE_HANDLE_DISTANCE = 40f

/**
 * On-screen transform handles overlaid on the video preview.
 * Supports drag-to-move, corner-drag-to-scale, rotation handle, and anchor point.
 */
@Composable
fun TransformOverlay(
    positionX: Float,
    positionY: Float,
    scaleX: Float,
    scaleY: Float,
    rotation: Float,
    anchorX: Float,
    anchorY: Float,
    opacity: Float,
    previewWidth: Float,
    previewHeight: Float,
    onPositionChanged: (Float, Float) -> Unit,
    onScaleChanged: (Float, Float) -> Unit,
    onRotationChanged: (Float) -> Unit,
    onAnchorChanged: (Float, Float) -> Unit,
    onTransformStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableStateOf(HandleType.NONE) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var startScaleX by remember { mutableFloatStateOf(1f) }
    var startScaleY by remember { mutableFloatStateOf(1f) }
    var startRotation by remember { mutableFloatStateOf(0f) }

    // Compute bounding box in screen space
    val baseWidth = previewWidth * 0.6f * scaleX
    val baseHeight = previewHeight * 0.6f * scaleY
    val centerX = previewWidth / 2f + positionX * previewWidth / 2f
    val centerY = previewHeight / 2f + positionY * previewHeight / 2f

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(positionX, positionY, scaleX, scaleY, rotation) {
                detectTransformGestures { _, pan, zoom, gestureRotation ->
                    if (!isDragging) {
                        isDragging = true
                        onTransformStarted()
                    }

                    // Apply pan (move)
                    val dx = pan.x / (size.width / 2f)
                    val dy = pan.y / (size.height / 2f)
                    onPositionChanged(
                        (positionX + dx).coerceIn(-1f, 1f),
                        (positionY + dy).coerceIn(-1f, 1f)
                    )

                    // Apply pinch zoom
                    if (abs(zoom - 1f) > 0.001f) {
                        onScaleChanged(
                            (scaleX * zoom).coerceIn(0.1f, 5f),
                            (scaleY * zoom).coerceIn(0.1f, 5f)
                        )
                    }

                    // Apply rotation
                    if (abs(gestureRotation) > 0.1f) {
                        onRotationChanged(rotation + gestureRotation)
                    }
                }
            }
            .pointerInput(positionX, positionY, scaleX, scaleY, rotation) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onTransformStarted()
                        isDragging = true
                        dragStartOffset = offset
                        startScaleX = scaleX
                        startScaleY = scaleY
                        startRotation = rotation

                        // Determine which handle was hit
                        val hw = baseWidth / 2f
                        val hh = baseHeight / 2f
                        val corners = listOf(
                            Offset(centerX - hw, centerY - hh), // TL
                            Offset(centerX + hw, centerY - hh), // TR
                            Offset(centerX + hw, centerY + hh), // BR
                            Offset(centerX - hw, centerY + hh), // BL
                        )
                        val rotatePos = Offset(centerX, centerY - hh - ROTATE_HANDLE_DISTANCE)

                        activeHandle = when {
                            offset.distTo(corners[0]) < HANDLE_RADIUS * 3 -> HandleType.SCALE_TL
                            offset.distTo(corners[1]) < HANDLE_RADIUS * 3 -> HandleType.SCALE_TR
                            offset.distTo(corners[2]) < HANDLE_RADIUS * 3 -> HandleType.SCALE_BR
                            offset.distTo(corners[3]) < HANDLE_RADIUS * 3 -> HandleType.SCALE_BL
                            offset.distTo(rotatePos) < HANDLE_RADIUS * 3 -> HandleType.ROTATE
                            offset.distTo(Offset(centerX, centerY)) < HANDLE_RADIUS * 3 -> HandleType.ANCHOR
                            isInsideBounds(offset, centerX, centerY, hw, hh) -> HandleType.MOVE
                            else -> HandleType.NONE
                        }
                    },
                    onDrag = { change, dragAmount ->
                        when (activeHandle) {
                            HandleType.MOVE -> {
                                val dx = dragAmount.x / (size.width / 2f)
                                val dy = dragAmount.y / (size.height / 2f)
                                onPositionChanged(
                                    (positionX + dx).coerceIn(-1f, 1f),
                                    (positionY + dy).coerceIn(-1f, 1f)
                                )
                            }
                            HandleType.SCALE_TL, HandleType.SCALE_TR,
                            HandleType.SCALE_BR, HandleType.SCALE_BL -> {
                                val dx = (change.position.x - dragStartOffset.x) / size.width
                                val dy = (change.position.y - dragStartOffset.y) / size.height
                                val scaleFactor = when (activeHandle) {
                                    HandleType.SCALE_BR, HandleType.SCALE_TR -> 1f + dx * 2f
                                    HandleType.SCALE_TL, HandleType.SCALE_BL -> 1f - dx * 2f
                                    else -> 1f
                                }
                                val scaleFactorY = when (activeHandle) {
                                    HandleType.SCALE_BR, HandleType.SCALE_BL -> 1f + dy * 2f
                                    HandleType.SCALE_TL, HandleType.SCALE_TR -> 1f - dy * 2f
                                    else -> 1f
                                }
                                onScaleChanged(
                                    (startScaleX * scaleFactor).coerceIn(0.1f, 5f),
                                    (startScaleY * scaleFactorY).coerceIn(0.1f, 5f)
                                )
                            }
                            HandleType.ROTATE -> {
                                val angle = atan2(
                                    change.position.x - centerX,
                                    -(change.position.y - centerY)
                                ) * 180f / PI.toFloat()
                                onRotationChanged(angle)
                            }
                            HandleType.ANCHOR -> {
                                val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                onAnchorChanged(nx, ny)
                            }
                            HandleType.NONE -> {}
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        activeHandle = HandleType.NONE
                    }
                )
            }
    ) {
        val hw = baseWidth / 2f
        val hh = baseHeight / 2f

        // Center guides (crosshair when near center)
        if (abs(positionX) < 0.02f) {
            drawLine(GuideColor.copy(alpha = 0.4f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1f)
        }
        if (abs(positionY) < 0.02f) {
            drawLine(GuideColor.copy(alpha = 0.4f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
        }

        // Draw within rotation context
        rotate(rotation, pivot = Offset(centerX, centerY)) {
            // Bounding box
            drawRect(
                Mocha.Mauve.copy(alpha = 0.6f),
                topLeft = Offset(centerX - hw, centerY - hh),
                size = androidx.compose.ui.geometry.Size(baseWidth, baseHeight),
                style = Stroke(width = 1.5f)
            )

            // Dashed diagonals (when scaling)
            if (activeHandle.isScale()) {
                drawLine(
                    Mocha.Mauve.copy(alpha = 0.2f),
                    Offset(centerX - hw, centerY - hh),
                    Offset(centerX + hw, centerY + hh),
                    1f
                )
                drawLine(
                    Mocha.Mauve.copy(alpha = 0.2f),
                    Offset(centerX + hw, centerY - hh),
                    Offset(centerX - hw, centerY + hh),
                    1f
                )
            }

            // Corner handles
            val corners = listOf(
                Offset(centerX - hw, centerY - hh),
                Offset(centerX + hw, centerY - hh),
                Offset(centerX + hw, centerY + hh),
                Offset(centerX - hw, centerY + hh),
            )
            corners.forEach { corner ->
                drawCircle(Color.White, HANDLE_RADIUS + 1f, corner)
                drawCircle(Mocha.Mauve, HANDLE_RADIUS, corner)
            }

            // Edge midpoint handles
            val midpoints = listOf(
                Offset(centerX, centerY - hh),
                Offset(centerX + hw, centerY),
                Offset(centerX, centerY + hh),
                Offset(centerX - hw, centerY),
            )
            midpoints.forEach { mid ->
                drawCircle(Color.White, HANDLE_RADIUS * 0.6f + 1f, mid)
                drawCircle(Mocha.Mauve.copy(alpha = 0.7f), HANDLE_RADIUS * 0.6f, mid)
            }

            // Rotation handle (line + circle above top center)
            val rotateStart = Offset(centerX, centerY - hh)
            val rotateEnd = Offset(centerX, centerY - hh - ROTATE_HANDLE_DISTANCE)
            drawLine(RotateMocha.Mauve.copy(alpha = 0.6f), rotateStart, rotateEnd, 1.5f)
            drawCircle(Color.White, HANDLE_RADIUS + 1f, rotateEnd)
            drawCircle(RotateMocha.Mauve, HANDLE_RADIUS, rotateEnd)
            // Rotation arrow icon
            val arrowPath = Path().apply {
                moveTo(rotateEnd.x - 5f, rotateEnd.y - 2f)
                lineTo(rotateEnd.x, rotateEnd.y - 6f)
                lineTo(rotateEnd.x + 5f, rotateEnd.y - 2f)
            }
            drawPath(arrowPath, RotateMocha.Mauve, style = Stroke(1.5f))

            // Anchor point (center crosshair)
            val anchorPos = Offset(
                centerX - hw + anchorX * baseWidth,
                centerY - hh + anchorY * baseHeight
            )
            drawCircle(AnchorColor.copy(alpha = 0.5f), 4f, anchorPos)
            drawLine(AnchorColor.copy(alpha = 0.5f), Offset(anchorPos.x - 8f, anchorPos.y), Offset(anchorPos.x + 8f, anchorPos.y), 1f)
            drawLine(AnchorColor.copy(alpha = 0.5f), Offset(anchorPos.x, anchorPos.y - 8f), Offset(anchorPos.x, anchorPos.y + 8f), 1f)
        }

        // Info label
        if (isDragging) {
            val infoText = when (activeHandle) {
                HandleType.MOVE -> "X:%.2f Y:%.2f".format(positionX, positionY)
                HandleType.ROTATE -> "%.1f°".format(rotation)
                HandleType.SCALE_TL, HandleType.SCALE_TR, HandleType.SCALE_BR, HandleType.SCALE_BL ->
                    "%.0f%% x %.0f%%".format(scaleX * 100, scaleY * 100)
                else -> ""
            }
            // Info rendered via drawContext if needed (keeping simple for now)
        }
    }
}

private enum class HandleType {
    NONE, MOVE, SCALE_TL, SCALE_TR, SCALE_BR, SCALE_BL, ROTATE, ANCHOR;

    fun isScale() = this == SCALE_TL || this == SCALE_TR || this == SCALE_BR || this == SCALE_BL
}

private fun Offset.distTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}

private fun isInsideBounds(point: Offset, cx: Float, cy: Float, hw: Float, hh: Float): Boolean {
    return point.x in (cx - hw)..(cx + hw) && point.y in (cy - hh)..(cy + hh)
}
