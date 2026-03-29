package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha
import kotlin.math.abs


@Composable
fun SpeedCurveEditor(
    speedCurve: SpeedCurve?,
    constantSpeed: Float,
    clipDurationMs: Long,
    onSpeedCurveChanged: (SpeedCurve?) -> Unit,
    onConstantSpeedChanged: (Float) -> Unit,
    isReversed: Boolean,
    onReversedChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var curveMode by remember { mutableStateOf(speedCurve != null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.speed_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Mode toggle: Constant vs Curve
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mocha.Surface0, RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            listOf(stringResource(R.string.panel_speed_constant) to false, stringResource(R.string.panel_speed_ramp) to true).forEach { (label, isCurve) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (curveMode == isCurve) Mocha.Mauve.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable {
                            curveMode = isCurve
                            if (isCurve && speedCurve == null) {
                                onSpeedCurveChanged(SpeedCurve.constant(constantSpeed))
                            } else if (!isCurve) {
                                // Preserve the curve's average speed as the constant speed
                                if (speedCurve != null) {
                                    onConstantSpeedChanged(constantSpeed)
                                }
                                onSpeedCurveChanged(null)
                            }
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (curveMode == isCurve) Mocha.Mauve else Mocha.Subtext0,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (curveMode) {
            // Presets
            Text(stringResource(R.string.speed_presets), color = Mocha.Subtext0, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Ramp Up" to SpeedCurve.rampUp(),
                    "Ramp Down" to SpeedCurve.rampDown(),
                    "Pulse" to SpeedCurve.pulse(),
                    "Slow Mo" to SpeedCurve.constant(0.25f),
                    "2x" to SpeedCurve.constant(2f),
                    "4x" to SpeedCurve.constant(4f)
                ).forEach { (label, preset) ->
                    FilterChip(
                        selected = false,
                        onClick = { onSpeedCurveChanged(preset) },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(labelColor = Mocha.Text)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Curve editor canvas
            val curve = speedCurve ?: SpeedCurve.constant(constantSpeed)
            SpeedCurveCanvas(
                curve = curve,
                onCurveChanged = { onSpeedCurveChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Mocha.Surface0, RoundedCornerShape(8.dp))
            )
        } else {
            // Constant speed controls
            Text(stringResource(R.string.speed_label, constantSpeed), color = Mocha.Text, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))

            // Quick presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 4f, 8f, 16f, 50f, 100f).forEach { speed ->
                    val selected = abs(constantSpeed - speed) < 0.01f
                    FilterChip(
                        selected = selected,
                        onClick = { onConstantSpeedChanged(speed) },
                        label = { Text("${speed}x", fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mocha.Mauve.copy(alpha = 0.2f),
                            selectedLabelColor = Mocha.Mauve,
                            labelColor = Mocha.Subtext0
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Fine control slider (logarithmic mapping for perceptually uniform speed control)
            val logMin = kotlin.math.ln(0.1f)
            val logMax = kotlin.math.ln(100f)
            val sliderPosition = (kotlin.math.ln(constantSpeed.coerceIn(0.1f, 100f)) - logMin) / (logMax - logMin)
            Slider(
                value = sliderPosition,
                onValueChange = { pos ->
                    val logSpeed = logMin + pos * (logMax - logMin)
                    val newSpeed = kotlin.math.exp(logSpeed).coerceIn(0.1f, 100f)
                    onConstantSpeedChanged(newSpeed)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Mocha.Mauve,
                    activeTrackColor = Mocha.Mauve.copy(alpha = 0.6f),
                    inactiveTrackColor = Mocha.Surface1
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        // Reverse toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Mocha.Surface0)
                .clickable { onReversedChanged(!isReversed) }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SwapHoriz, "Reverse", tint = if (isReversed) Mocha.Peach else Mocha.Subtext0, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.speed_reverse_playback), color = Mocha.Text, fontSize = 13.sp)
            }
            Switch(
                checked = isReversed,
                onCheckedChange = onReversedChanged,
                colors = SwitchDefaults.colors(checkedTrackColor = Mocha.Peach)
            )
        }
    }
}

@Composable
private fun SpeedCurveCanvas(
    curve: SpeedCurve,
    onCurveChanged: (SpeedCurve) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragPointIndex by remember { mutableIntStateOf(-1) }
    val maxSpeed = 8f
    val minSpeed = 0.1f

    Canvas(
        modifier = modifier
            .pointerInput(curve) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        // Add new control point
                        val position = (offset.x / size.width).coerceIn(0f, 1f)
                        val speed = (minSpeed + (1f - offset.y / size.height) * (maxSpeed - minSpeed))
                            .coerceIn(minSpeed, maxSpeed)
                        val newPoints = curve.points.toMutableList()
                        newPoints.add(SpeedPoint(position, speed))
                        newPoints.sortBy { it.position }
                        onCurveChanged(SpeedCurve(newPoints))
                    }
                )
            }
            .pointerInput(curve) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val hitRadius = 30f
                        var bestIdx = -1
                        var bestDist = hitRadius * hitRadius
                        for (i in curve.points.indices) {
                            val px = curve.points[i].position * size.width
                            val py = (1f - (curve.points[i].speed - minSpeed) / (maxSpeed - minSpeed)) * size.height
                            val dx = offset.x - px
                            val dy = offset.y - py
                            val dist = dx * dx + dy * dy
                            if (dist < bestDist) { bestDist = dist; bestIdx = i }
                        }
                        dragPointIndex = bestIdx
                    },
                    onDrag = { change, _ ->
                        if (dragPointIndex in curve.points.indices) {
                            val position = (change.position.x / size.width).coerceIn(0.01f, 0.99f)
                            val speed = (minSpeed + (1f - change.position.y / size.height) * (maxSpeed - minSpeed))
                                .coerceIn(minSpeed, maxSpeed)
                            val newPoints = curve.points.toMutableList()
                            newPoints[dragPointIndex] = newPoints[dragPointIndex].copy(
                                position = position, speed = speed
                            )
                            onCurveChanged(SpeedCurve(newPoints))
                        }
                    },
                    onDragEnd = { dragPointIndex = -1 }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val speedRange = maxSpeed - minSpeed

        // Grid
        for (speed in listOf(0.5f, 1f, 2f, 4f)) {
            val y = (1f - (speed - minSpeed) / speedRange) * h
            drawLine(Color(0xFF45475A), Offset(0f, y), Offset(w, y), 0.5f)
        }

        // 1x reference line
        val refY = (1f - (1f - minSpeed) / speedRange) * h
        drawLine(Color(0xFF585B70), Offset(0f, refY), Offset(w, refY), 1.5f)

        // Draw speed curve
        val path = Path()
        val steps = 200
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val speed = curve.getSpeedAt((t * 10000).toLong(), 10000L)
            val x = t * w
            val y = (1f - (speed - minSpeed) / speedRange) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Mocha.Peach, style = Stroke(2.5f))

        // Draw control points
        curve.points.forEach { point ->
            val px = point.position * w
            val py = (1f - (point.speed - minSpeed) / speedRange) * h
            drawCircle(Mocha.Peach, 8f, Offset(px, py))
            drawCircle(Color.White, 5f, Offset(px, py))
        }
    }
}
