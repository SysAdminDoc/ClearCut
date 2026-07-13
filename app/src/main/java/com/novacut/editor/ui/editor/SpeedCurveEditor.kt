package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.SpeedCurve
import com.novacut.editor.model.SpeedPoint
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
    val semanticColors = LocalClearCutColors.current
    var curveMode by remember { mutableStateOf(speedCurve != null) }
    var selectedCurvePointIndex by remember { mutableIntStateOf(-1) }
    val activeCurve = speedCurve ?: SpeedCurve.constant(constantSpeed)
    val averageCurveSpeed = activeCurve.averageSpeed(clipDurationMs).coerceIn(0.1f, 100f)
    val peakCurveSpeed = activeCurve.points.maxOfOrNull { it.speed }?.coerceIn(0.1f, 100f) ?: constantSpeed

    PremiumEditorPanel(
        title = stringResource(R.string.speed_title),
        subtitle = stringResource(R.string.panel_speed_subtitle),
        icon = Icons.Default.FastForward,
        accent = if (curveMode) ClearCutAccents.Mauve else ClearCutAccents.Peach,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.cd_close_speed_curve),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = if (curveMode) ClearCutAccents.Mauve else ClearCutAccents.Peach) {
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
                color = semanticColors.panelRaised,
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
                                    if (curveMode == isCurve) ClearCutAccents.Mauve.copy(alpha = 0.18f) else Color.Transparent
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
                                color = if (curveMode == isCurve) ClearCutAccents.Mauve else semanticColors.subtext
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (curveMode) {
            PremiumPanelCard(accent = ClearCutAccents.Mauve) {
                Text(
                    text = stringResource(R.string.speed_presets),
                    style = MaterialTheme.typography.titleMedium,
                    color = semanticColors.text
                )
                Text(
                    text = stringResource(R.string.speed_curve_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
                )
                // Grouped presets — Ramps (time-varying curves) and Constants (uniform speeds).
                // Sub-headers make presets more discoverable; users who don't know "ramp up" vs.
                // "constant 2x" can skim the category label first.
                Text(
                    text = stringResource(R.string.speed_preset_group_ramps),
                    style = MaterialTheme.typography.labelMedium,
                    color = semanticColors.subtext
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        stringResource(R.string.speed_preset_ramp_up) to SpeedCurve.rampUp(),
                        stringResource(R.string.speed_preset_ramp_down) to SpeedCurve.rampDown(),
                        stringResource(R.string.speed_preset_pulse) to SpeedCurve.pulse()
                    ).forEach { (label, preset) ->
                        FilterChip(
                            selected = false,
                            onClick = { onSpeedCurveChanged(preset) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = semanticColors.text,
                                containerColor = semanticColors.panelRaised
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.speed_preset_group_constants),
                    style = MaterialTheme.typography.labelMedium,
                    color = semanticColors.subtext
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        stringResource(R.string.speed_preset_slow_mo) to SpeedCurve.constant(0.25f),
                        stringResource(R.string.speed_preset_double) to SpeedCurve.constant(2f),
                        stringResource(R.string.speed_preset_quad) to SpeedCurve.constant(4f)
                    ).forEach { (label, preset) ->
                        FilterChip(
                            selected = false,
                            onClick = { onSpeedCurveChanged(preset) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = semanticColors.text,
                                containerColor = semanticColors.panelRaised
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
                        accent = ClearCutAccents.Sapphire
                    )
                    PremiumPanelPill(
                        text = stringResource(R.string.speed_curve_average_label, averageCurveSpeed),
                        accent = ClearCutAccents.Mauve
                    )
                    PremiumPanelPill(
                        text = stringResource(R.string.speed_curve_peak_label, peakCurveSpeed),
                        accent = ClearCutAccents.Peach
                    )
                }
                SpeedCurveCanvas(
                    curve = activeCurve,
                    onCurveChanged = { onSpeedCurveChanged(it) },
                    selectedIndex = selectedCurvePointIndex,
                    onPointSelected = { selectedCurvePointIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(semanticColors.surfaceLow)
                )
                SpeedPointNumericEditor(
                    curve = activeCurve,
                    selectedIndex = selectedCurvePointIndex,
                    onCurveChanged = { onSpeedCurveChanged(it) },
                    onDeletePoint = {
                        if (selectedCurvePointIndex > 0 && selectedCurvePointIndex < activeCurve.points.lastIndex) {
                            val updated = activeCurve.points.toMutableList()
                            updated.removeAt(selectedCurvePointIndex)
                            selectedCurvePointIndex = -1
                            onSpeedCurveChanged(SpeedCurve(updated))
                        }
                    }
                )
            }
        } else {
            PremiumPanelCard(accent = ClearCutAccents.Peach) {
                Text(
                    text = stringResource(R.string.speed_label, constantSpeed),
                    style = MaterialTheme.typography.titleMedium,
                    color = semanticColors.text
                )
                Text(
                    text = stringResource(R.string.speed_constant_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
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
                                selectedContainerColor = ClearCutAccents.Mauve.copy(alpha = 0.2f),
                                selectedLabelColor = ClearCutAccents.Mauve,
                                labelColor = semanticColors.subtext,
                                containerColor = semanticColors.panelRaised
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
                        thumbColor = ClearCutAccents.Mauve,
                        activeTrackColor = ClearCutAccents.Mauve.copy(alpha = 0.6f),
                        inactiveTrackColor = semanticColors.surface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = if (isReversed) ClearCutAccents.Peach else ClearCutAccents.Sapphire) {
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
                        tint = if (isReversed) ClearCutAccents.Peach else ClearCutAccents.Sapphire,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.speed_reverse_playback),
                            style = MaterialTheme.typography.titleSmall,
                            color = semanticColors.text
                        )
                        Text(
                            text = if (isReversed) {
                                stringResource(R.string.panel_speed_reverse_hint_on)
                            } else {
                                stringResource(R.string.panel_speed_reverse_hint_off)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.subtext
                        )
                    }
                }
                Switch(
                    checked = isReversed,
                    onCheckedChange = onReversedChanged,
                    colors = SwitchDefaults.colors(checkedTrackColor = ClearCutAccents.Peach)
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
            accent = if (curveMode) ClearCutAccents.Mauve else ClearCutAccents.Peach
        )
        PremiumPanelPill(
            text = if (curveMode) {
                stringResource(R.string.speed_curve_average_label, averageCurveSpeed)
            } else {
                stringResource(R.string.speed_current_label, constantSpeed)
            },
            accent = if (curveMode) ClearCutAccents.Mauve else ClearCutAccents.Peach
        )
        PremiumPanelPill(
            text = if (isReversed) stringResource(R.string.panel_speed_reverse_on) else stringResource(R.string.panel_speed_reverse_off),
            accent = if (isReversed) ClearCutAccents.Peach else ClearCutAccents.Sapphire
        )
        PremiumPanelPill(
            text = stringResource(R.string.speed_clip_duration_label, formatTimestamp(clipDurationMs)),
            accent = ClearCutAccents.Blue
        )
    }
}

@Composable
private fun SpeedSummaryText(curveMode: Boolean) {
    val semanticColors = LocalClearCutColors.current
    Column {
        Text(
            text = stringResource(R.string.speed_summary_title),
            style = MaterialTheme.typography.titleMedium,
            color = semanticColors.text
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (curveMode) {
                stringResource(R.string.speed_mode_curve_description)
            } else {
                stringResource(R.string.speed_mode_constant_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = semanticColors.subtext
        )
    }
}

@Composable
private fun SpeedPointNumericEditor(
    curve: SpeedCurve,
    selectedIndex: Int,
    onCurveChanged: (SpeedCurve) -> Unit,
    onDeletePoint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    if (selectedIndex !in curve.points.indices) return
    val point = curve.points[selectedIndex]
    val positionDescription = stringResource(R.string.speed_point_position_cd)
    val multiplierDescription = stringResource(R.string.speed_point_multiplier_cd)
    val deleteDescription = stringResource(R.string.speed_point_delete_cd)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.speed_point_number, selectedIndex + 1),
            style = MaterialTheme.typography.labelMedium,
            color = ClearCutAccents.Peach
        )
        OutlinedTextField(
            value = formatEditorDecimal(point.position.toDouble() * 100.0, 1),
            onValueChange = { text ->
                val parsed = parseEditorDecimal(text) ?: return@OutlinedTextField
                val newPosition = (parsed / 100.0).toFloat().coerceIn(0f, 1f)
                val clamped = clampSpeedPointPosition(curve.points, selectedIndex, newPosition)
                val updated = curve.points.toMutableList()
                updated[selectedIndex] = updated[selectedIndex].copy(position = clamped)
                onCurveChanged(SpeedCurve(updated))
            },
            label = { Text(stringResource(R.string.speed_point_position)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = positionDescription },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = semanticColors.text,
                unfocusedTextColor = semanticColors.subtext,
                focusedBorderColor = ClearCutAccents.Peach,
                unfocusedBorderColor = semanticColors.surface
            )
        )
        OutlinedTextField(
            value = formatEditorDecimal(point.speed.toDouble(), 2),
            onValueChange = { text ->
                val parsed = parseEditorDecimal(text) ?: return@OutlinedTextField
                val newSpeed = parsed.toFloat().coerceIn(0.1f, 8f)
                val updated = curve.points.toMutableList()
                updated[selectedIndex] = updated[selectedIndex].copy(speed = newSpeed)
                onCurveChanged(SpeedCurve(updated))
            },
            label = { Text(stringResource(R.string.speed_title)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = multiplierDescription },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = semanticColors.text,
                unfocusedTextColor = semanticColors.subtext,
                focusedBorderColor = ClearCutAccents.Peach,
                unfocusedBorderColor = semanticColors.surface
            )
        )
        if (selectedIndex > 0 && selectedIndex < curve.points.lastIndex) {
            IconButton(
                onClick = onDeletePoint,
                modifier = Modifier.semantics { contentDescription = deleteDescription }
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = ClearCutAccents.Red)
            }
        }
    }
}

@Composable
private fun SpeedCurveCanvas(
    curve: SpeedCurve,
    onCurveChanged: (SpeedCurve) -> Unit,
    selectedIndex: Int = -1,
    onPointSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dragPointIndex by remember { mutableIntStateOf(-1) }
    val maxSpeed = 8f
    val minSpeed = 0.1f
    val currentCurve by rememberUpdatedState(curve)
    val currentOnCurveChanged by rememberUpdatedState(onCurveChanged)
    val currentOnPointSelected by rememberUpdatedState(onPointSelected)

    val canvasDescription = stringResource(R.string.speed_curve_canvas_cd)
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .semantics { contentDescription = canvasDescription }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val hitRadius = 30f
                        var bestIdx = -1
                        var bestDist = hitRadius * hitRadius
                        for (i in currentCurve.points.indices) {
                            val px = currentCurve.points[i].position * size.width
                            val py = (1f - (currentCurve.points[i].speed - minSpeed) / (maxSpeed - minSpeed)) * size.height
                            val dx = offset.x - px
                            val dy = offset.y - py
                            val dist = dx * dx + dy * dy
                            if (dist < bestDist) {
                                bestDist = dist
                                bestIdx = i
                            }
                        }
                        currentOnPointSelected(bestIdx)
                    },
                    onDoubleTap = { offset ->
                        val position = (offset.x / size.width).coerceIn(0.02f, 0.98f)
                        val speed = (minSpeed + (1f - offset.y / size.height) * (maxSpeed - minSpeed))
                            .coerceIn(minSpeed, maxSpeed)
                        val newPoints = currentCurve.points.toMutableList()
                        newPoints.add(SpeedPoint(position, speed))
                        newPoints.sortBy { it.position }
                        currentOnCurveChanged(SpeedCurve(newPoints))
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val hitRadius = 30f
                        var bestIdx = -1
                        var bestDist = hitRadius * hitRadius
                        for (i in currentCurve.points.indices) {
                            val px = currentCurve.points[i].position * size.width
                            val py = (1f - (currentCurve.points[i].speed - minSpeed) / (maxSpeed - minSpeed)) * size.height
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
                        if (dragPointIndex in currentCurve.points.indices) {
                            val requestedPosition = (change.position.x / size.width).coerceIn(0f, 1f)
                            val position = clampSpeedPointPosition(currentCurve.points, dragPointIndex, requestedPosition)
                            val speed = (minSpeed + (1f - change.position.y / size.height) * (maxSpeed - minSpeed))
                                .coerceIn(minSpeed, maxSpeed)
                            val newPoints = currentCurve.points.toMutableList()
                            newPoints[dragPointIndex] = newPoints[dragPointIndex].copy(position = position, speed = speed)
                            currentOnCurveChanged(SpeedCurve(newPoints))
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
        drawPath(path, ClearCutAccents.Peach, style = Stroke(2.5f))

        curve.points.forEachIndexed { i, point ->
            val px = point.position * w
            val py = (1f - (point.speed - minSpeed) / speedRange) * h
            if (i == selectedIndex) {
                drawCircle(ClearCutAccents.Yellow, 14f, Offset(px, py))
            }
            drawCircle(ClearCutAccents.Peach, 8f, Offset(px, py))
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
