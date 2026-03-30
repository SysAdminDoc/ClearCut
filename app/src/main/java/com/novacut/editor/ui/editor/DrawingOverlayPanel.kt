package com.novacut.editor.ui.editor

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.model.DrawingPath
import com.novacut.editor.ui.theme.Mocha

private val drawingColors = listOf(
    0xFFF38BA8L to "Red",
    0xFF89B4FAL to "Blue",
    0xFFA6E3A1L to "Green",
    0xFFF9E2AFL to "Yellow",
    0xFFFAB387L to "Peach",
    0xFFCBA6F7L to "Mauve"
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
    var isEraser by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.panel_drawing_title), color = Mocha.Text, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onUndo, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Undo, contentDescription = stringResource(R.string.cd_drawing_undo), tint = Mocha.Subtext0, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.cd_drawing_clear), tint = Mocha.Subtext0, modifier = Modifier.size(20.dp))
                }
                FilledTonalButton(
                    onClick = onDone,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Mocha.Mauve,
                        contentColor = Mocha.Crust
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.panel_drawing_done), fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            drawingColors.forEach { (color, name) ->
                val isSelected = drawingColor == color && !isEraser
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(color.toULong()), CircleShape)
                        .then(
                            if (isSelected) Modifier.border(2.dp, Mocha.Text, CircleShape)
                            else Modifier
                        )
                        .clickable {
                            isEraser = false
                            onColorChanged(color)
                        }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { isEraser = !isEraser },
                modifier = Modifier
                    .size(36.dp)
                    .then(
                        if (isEraser) Modifier.background(Mocha.Surface1, CircleShape)
                        else Modifier
                    )
            ) {
                Icon(
                    Icons.Default.SquareFoot,
                    contentDescription = stringResource(R.string.cd_drawing_eraser),
                    tint = if (isEraser) Mocha.Text else Mocha.Subtext0,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.panel_drawing_size), color = Mocha.Subtext0, fontSize = 12.sp, modifier = Modifier.width(36.dp))
            Slider(
                value = drawingStrokeWidth,
                onValueChange = onStrokeWidthChanged,
                valueRange = 2f..20f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Mocha.Mauve,
                    activeTrackColor = Mocha.Mauve,
                    inactiveTrackColor = Mocha.Surface1
                )
            )
            Text("${drawingStrokeWidth.toInt()}dp", color = Mocha.Subtext0, fontSize = 12.sp, modifier = Modifier.width(36.dp))
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

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .then(
                if (isDrawingMode) Modifier.pointerInput(drawingColor, drawingStrokeWidth) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPoints = listOf(offset.x to offset.y)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPoints = currentPoints + (change.position.x to change.position.y)
                        },
                        onDragEnd = {
                            if (currentPoints.size >= 2) {
                                onPathAdded(
                                    DrawingPath(
                                        points = currentPoints,
                                        color = drawingColor,
                                        strokeWidth = drawingStrokeWidth
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
