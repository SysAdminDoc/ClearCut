package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.model.DrawingPath
import com.novacut.editor.ui.theme.ClearCutChromeIconButton
import com.novacut.editor.ui.theme.ClearCutPrimaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget

private val drawingColors = listOf(
    0xFFF38BA8L to R.string.drawing_color_red,
    0xFF89B4FAL to R.string.drawing_color_blue,
    0xFFA6E3A1L to R.string.drawing_color_green,
    0xFFF9E2AFL to R.string.drawing_color_yellow,
    0xFFFAB387L to R.string.drawing_color_peach,
    0xFFCBA6F7L to R.string.drawing_color_mauve
)

@Composable
fun DrawingOverlayPanel(
    drawingColor: Long,
    drawingStrokeWidth: Float,
    onColorChanged: (Long) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    var isEraser by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(semanticColors.backgroundMid, RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl))
            .padding(Spacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.panel_drawing_title), color = semanticColors.text, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                @Suppress("DEPRECATION")
                ClearCutChromeIconButton(
                    icon = Icons.Default.Undo,
                    contentDescription = stringResource(R.string.cd_drawing_undo),
                    onClick = onUndo,
                    iconSize = 20.dp
                )
                ClearCutChromeIconButton(
                    icon = Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.cd_drawing_clear),
                    onClick = onClear,
                    tint = ClearCutAccents.Red,
                    containerColor = ClearCutAccents.Red.copy(alpha = 0.10f),
                    borderColor = ClearCutAccents.Red.copy(alpha = 0.20f),
                    iconSize = 20.dp
                )
                ClearCutPrimaryButton(
                    text = stringResource(R.string.panel_drawing_done),
                    onClick = onDone
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            drawingColors.forEach { (color, nameRes) ->
                val isSelected = drawingColor == color && !isEraser
                val colorName = stringResource(nameRes)
                val swatchDescription = stringResource(R.string.cd_drawing_color, colorName)
                val swatchState = stringResource(if (isSelected) R.string.state_on else R.string.state_off)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(TouchTarget.minimum)
                        .semantics {
                            contentDescription = swatchDescription
                            stateDescription = swatchState
                        }
                        .clickable(role = Role.Button) {
                            isEraser = false
                            onColorChanged(color)
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(color.toULong()), CircleShape)
                            .then(
                                if (isSelected) Modifier.border(2.dp, semanticColors.text, CircleShape)
                                else Modifier.border(1.dp, semanticColors.cardStroke.copy(alpha = 0.55f), CircleShape)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.xs))

            ClearCutChromeIconButton(
                icon = Icons.Default.SquareFoot,
                contentDescription = stringResource(R.string.cd_drawing_eraser),
                onClick = { isEraser = !isEraser },
                tint = if (isEraser) semanticColors.text else semanticColors.subtext,
                containerColor = if (isEraser) semanticColors.surface else semanticColors.panelHighest,
                borderColor = if (isEraser) ClearCutAccents.Mauve.copy(alpha = 0.55f) else semanticColors.cardStroke,
                iconSize = 20.dp
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.panel_drawing_size), color = semanticColors.subtext, fontSize = 12.sp, modifier = Modifier.width(36.dp))
            Slider(
                value = drawingStrokeWidth,
                onValueChange = onStrokeWidthChanged,
                valueRange = 2f..20f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = ClearCutAccents.Mauve,
                    activeTrackColor = ClearCutAccents.Mauve,
                    inactiveTrackColor = semanticColors.surface
                )
            )
            Text("${drawingStrokeWidth.toInt()}dp", color = semanticColors.subtext, fontSize = 12.sp, modifier = Modifier.width(36.dp))
        }
    }
}

@Composable
fun DrawingCanvas(
    paths: List<DrawingPath>,
    isDrawingMode: Boolean,
    drawingColor: Long,
    drawingStrokeWidth: Float,
    onPathAdded: (DrawingPath) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPoints by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    var currentPressure by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .then(
                if (isDrawingMode) Modifier.pointerInput(drawingColor, drawingStrokeWidth) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPoints = if (offset.x.isFinite() && offset.y.isFinite()) {
                                listOf(offset.x to offset.y)
                            } else emptyList()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val x = change.position.x
                            val y = change.position.y
                            if (x.isFinite() && y.isFinite()) {
                                currentPoints = currentPoints + (x to y)
                            }
                            val pressure = change.pressure
                            if (pressure.isFinite() && pressure > 0f && change.type == androidx.compose.ui.input.pointer.PointerType.Stylus) {
                                currentPressure = pressure.coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            val effectiveStroke = if (currentPressure > 0f) {
                                drawingStrokeWidth * (0.3f + 0.7f * currentPressure)
                            } else drawingStrokeWidth
                            currentPressure = 0f
                            if (currentPoints.size >= 2) {
                                onPathAdded(
                                    DrawingPath(
                                        points = currentPoints,
                                        color = drawingColor,
                                        strokeWidth = effectiveStroke
                                    )
                                )
                            }
                            currentPoints = emptyList()
                        },
                        onDragCancel = {
                            currentPoints = emptyList()
                        }
                    )
                } else Modifier
            )
    ) {
        fun drawPathPoints(points: List<Pair<Float, Float>>, color: Long, strokeWidth: Float) {
            if (points.size < 2) return
            val path = Path()
            path.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                path.lineTo(points[i].first, points[i].second)
            }
            drawPath(
                path = path,
                color = Color(color.toULong()),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        paths.forEach { dp ->
            drawPathPoints(dp.points, dp.color, dp.strokeWidth)
        }

        if (currentPoints.size >= 2) {
            drawPathPoints(currentPoints, drawingColor, drawingStrokeWidth)
        }
    }
}
