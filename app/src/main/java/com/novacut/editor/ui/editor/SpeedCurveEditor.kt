package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.SpeedCurve
import com.novacut.editor.model.SpeedPoint
import com.novacut.editor.ui.theme.Mocha
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

@OptIn(ExperimentalLayoutApi::class)
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
    modifier: Modifier = Modifier,
    onSpeedDragStarted: () -> Unit = {},
    onSpeedDragEnded: () -> Unit = {}
) {
    var curveMode by remember { mutableStateOf(speedCurve != null) }
    val activeCurve = speedCurve ?: SpeedCurve.constant(constantSpeed)
    val averageCurveSpeed = activeCurve.averageSpeed(clipDurationMs).coerceIn(0.1f, 100f)
    val peakCurveSpeed = activeCurve.points.maxOfOrNull { it.speed }?.coerceIn(0.1f, 100f) ?: constantSpeed

    PremiumEditorPanel(
        title = stringResource(R.string.speed_title),
        subtitle = stringResource(R.string.panel_speed_subtitle),
        icon = Icons.Default.FastForward,
        accent = if (curveMode) Mocha.Mauve else Mocha.Peach,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.cd_close_speed_curve),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = if (curveMode) Mocha.Mauve else Mocha.Peach) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactLayout = maxWidth < 420.dp
                if (isCompactLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpeedSummaryText(curveMode = curveMode)
                        SpeedSummaryPills(
                            curveMode = curveMode,
                            constantSpeed = constantSpeed,
                            averageCurveSpeed = averageCurveSpeed,
                            isReversed = isReversed,
                            clipDurationMs = clipDurationMs
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SpeedSummaryText(curveMode = curveMode)
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            SpeedSummaryPills(
                                curveMode = curveMode,
                                constantSpeed = constantSpeed,
                                averageCurveSpeed = averageCurveSpeed,
                                isReversed = isReversed,
                                clipDurationMs = clipDurationMs
                            )
                        }
                    }
                }
            }

            Surface(
                color = Mocha.PanelRaised,
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp)
                ) {
                    listOf(
                        stringResource(R.string.panel_speed_constant) to false,
                        stringResource(R.string.panel_speed_ramp) to true
                    ).forEach { (label, isCurve) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (curveMode == isCurve) Mocha.Mauve.copy(alpha = 0.18f) else Color.Transparent
                                )
                                .clickable {
                                    curveMode = isCurve
                                    if (isCurve && speedCurve == null) {
                                        onSpeedCurveChanged(SpeedCurve.constant(constantSpeed))
                                    } else if (!isCurve) {
                                        if (speedCurve != null) {
                                            onConstantSpeedChanged(speedCurve.averageSpeed(clipDurationMs).coerceIn(0.1f, 100f))
                                        }
                                        onSpeedCurveChanged(null)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (curveMode == isCurve) Mocha.Mauve else Mocha.Subtext0
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (curveMode) {
            PremiumPanelCard(accent = Mocha.Mauve) {
                Text(
                    text = stringResource(R.string.speed_presets),
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                Text(
                    text = stringResource(R.string.speed_curve_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        stringResource(R.string.speed_preset_ramp_up) to SpeedCurve.rampUp(),
                        stringResource(R.string.speed_preset_ramp_down) to SpeedCurve.rampDown(),
                        stringResource(R.string.speed_preset_pulse) to SpeedCurve.pulse(),
                        stringResource(R.string.speed_preset_slow_mo) to SpeedCurve.constant(0.25f),
                        stringResource(R.string.speed_preset_double) to SpeedCurve.constant(2f),
                        stringResource(R.string.speed_preset_quad) to SpeedCurve.constant(4f)
                    ).forEach { (label, preset) ->
                        FilterChip(
                            selected = false,
                            onClick = { onSpeedCurveChanged(preset) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Mocha.Text,
                                containerColor = Mocha.PanelRaised
                            )
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = stringResource(R.string.speed_curve_points_label, activeCurve.points.size),
                        accent = Mocha.Sapphire
                    )
                    PremiumPanelPill(
                        text = stringResource(R.string.speed_curve_average_label, averageCurveSpeed),
                        accent = Mocha.Mauve
                    )
                    PremiumPanelPill(
                        text = stringResource(R.string.speed_curve_peak_label, peakCurveSpeed),
                        accent = Mocha.Peach
                    )
                }
                SpeedCurveCanvas(
                    curve = activeCurve,
                    onCurveChanged = { onSpeedCurveChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Mocha.Surface0)
                )
            }
        } else {
            PremiumPanelCard(accent = Mocha.Peach) {
                Text(
                    text = stringResource(R.string.speed_label, constantSpeed),
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                Text(
                    text = stringResource(R.string.speed_constant_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 4f, 8f, 16f, 50f, 100f).forEach { speed ->
                        FilterChip(
                            selected = abs(constantSpeed - speed) < 0.01f,
                            onClick = {
                                onSpeedDragStarted()
                                onConstantSpeedChanged(speed)
                                onSpeedDragEnded()
                            },
                            label = { Text(formatSpeedChip(speed)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Mocha.Mauve.copy(alpha = 0.2f),
                                selectedLabelColor = Mocha.Mauve,
                                labelColor = Mocha.Subtext0,
                                containerColor = Mocha.PanelRaised
                            )
                        )
                    }
                }

                val logMin = ln(0.1f)
                val logMax = ln(100f)
                val sliderPosition = (ln(constantSpeed.coerceIn(0.1f, 100f)) - logMin) / (logMax - logMin)
                var sliderDragActive by remember { mutableStateOf(false) }
                Slider(
                    value = sliderPosition,
                    onValueChange = { pos ->
                        if (!sliderDragActive) {
                            sliderDragActive = true
                            onSpeedDragStarted()
                        }
                        val logSpeed = logMin + pos * (logMax - logMin)
                        onConstantSpeedChanged(exp(logSpeed).coerceIn(0.1f, 100f))
                    },
                    onValueChangeFinished = {
                        if (sliderDragActive) {
                            sliderDragActive = false
                            onSpeedDragEnded()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Mocha.Mauve,
                        activeTrackColor = Mocha.Mauve.copy(alpha = 0.6f),
                        inactiveTrackColor = Mocha.Surface1
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = if (isReversed) Mocha.Peach else Mocha.Sapphire) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReversedChanged(!isReversed) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = stringResource(R.string.cd_reverse_speed),
                        tint = if (isReversed) Mocha.Peach else Mocha.Sapphire,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.speed_reverse_playback),
                            style = MaterialTheme.typography.titleSmall,
                            color = Mocha.Text
                        )
                        Text(
                            text = if (isReversed) {
                                stringResource(R.string.panel_speed_reverse_hint_on)
                            } else {
                                stringResource(R.string.panel_speed_reverse_hint_off)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Mocha.Subtext0
                        )
                    }
                }
                Switch(
                    checked = isReversed,
                    onCheckedChange = onReversedChanged,
                    colors = SwitchDefaults.colors(checkedTrackColor = Mocha.Peach)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedSummaryPills(
    curveMode: Boolean,
    constantSpeed: Float,
    averageCurveSpeed: Float,
    isReversed: Boolean,
    clipDurationMs: Long
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PremiumPanelPill(
            text = if (curveMode) stringResource(R.string.panel_speed_ramp) else stringResource(R.string.panel_speed_constant),
            accent = if (curveMode) Mocha.Mauve else Mocha.Peach
        )
        PremiumPanelPill(
            text = if (curveMode) {
                stringResource(R.string.speed_curve_average_label, averageCurveSpeed)
            } else {
                stringResource(R.string.speed_current_label, constantSpeed)
            },
            accent = if (curveMode) Mocha.Mauve else Mocha.Peach
        )
        PremiumPanelPill(
            text = if (isReversed) stringResource(R.string.panel_speed_reverse_on) else stringResource(R.string.panel_speed_reverse_off),
            accent = if (isReversed) Mocha.Peach else Mocha.Sapphire
        )
        PremiumPanelPill(
            text = stringResource(R.string.speed_clip_duration_label, formatTimestamp(clipDurationMs)),
            accent = Mocha.Blue
        )
    }
}

@Composable
private fun SpeedSummaryText(curveMode: Boolean) {
    Column {
        Text(
            text = stringResource(R.string.speed_summary_title),
            style = MaterialTheme.typography.titleMedium,
            color = Mocha.Text
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (curveMode) {
                stringResource(R.string.speed_mode_curve_description)
            } else {
                stringResource(R.string.speed_mode_constant_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Mocha.Subtext0
        )
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

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .pointerInput(curve) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val position = (offset.x / size.width).coerceIn(0.02f, 0.98f)
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
                            if (dist < bestDist) {
                                bestDist = dist
                                bestIdx = i
                            }
                        }
                        dragPointIndex = bestIdx
                    },
                    onDrag = { change, _ ->
                        if (dragPointIndex in curve.points.indices) {
                            val requestedPosition = (change.position.x / size.width).coerceIn(0f, 1f)
                            val position = clampSpeedPointPosition(curve.points, dragPointIndex, requestedPosition)
                            val speed = (minSpeed + (1f - change.position.y / size.height) * (maxSpeed - minSpeed))
                                .coerceIn(minSpeed, maxSpeed)
                            val newPoints = curve.points.toMutableList()
                            newPoints[dragPointIndex] = newPoints[dragPointIndex].copy(position = position, speed = speed)
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

        for (speed in listOf(0.5f, 1f, 2f, 4f)) {
            val y = (1f - (speed - minSpeed) / speedRange) * h
            drawLine(Color(0xFF45475A), Offset(0f, y), Offset(w, y), 0.5f)
        }

        val refY = (1f - (1f - minSpeed) / speedRange) * h
        drawLine(Color(0xFF585B70), Offset(0f, refY), Offset(w, refY), 1.5f)

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

        curve.points.forEach { point ->
            val px = point.position * w
            val py = (1f - (point.speed - minSpeed) / speedRange) * h
            drawCircle(Mocha.Peach, 8f, Offset(px, py))
            drawCircle(Color.White, 5f, Offset(px, py))
        }
    }
}

private fun clampSpeedPointPosition(points: List<SpeedPoint>, index: Int, requestedPosition: Float): Float {
    if (points.isEmpty()) return requestedPosition
    if (index == 0) return 0f
    if (index == points.lastIndex) return 1f

    val previous = points.getOrNull(index - 1)?.position ?: 0f
    val next = points.getOrNull(index + 1)?.position ?: 1f
    return requestedPosition.coerceIn(previous + 0.02f, next - 0.02f)
}

private fun formatSpeedChip(speed: Float): String {
    return if (speed >= 10f || abs(speed - speed.toInt().toFloat()) < 0.01f) {
        "${speed.toInt()}x"
    } else {
        String.format(Locale.US, "%.2fx", speed)
    }
}
