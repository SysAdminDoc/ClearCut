package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.*
import kotlin.math.*

enum class ColorGradingTab {
    WHEELS,
    CURVES,
    HSL,
    LUT
}

@Composable
private fun ColorGradingTab.displayLabel(): String = when (this) {
    ColorGradingTab.WHEELS -> stringResource(R.string.color_tab_wheels)
    ColorGradingTab.CURVES -> stringResource(R.string.color_tab_curves)
    ColorGradingTab.HSL -> stringResource(R.string.color_tab_hsl)
    ColorGradingTab.LUT -> stringResource(R.string.color_tab_lut)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorGradingPanel(
    colorGrade: ColorGrade,
    onColorGradeChanged: (ColorGrade) -> Unit,
    modifier: Modifier = Modifier,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {},
    onLutImport: () -> Unit,
    onClose: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    var activeTab by remember { mutableStateOf(ColorGradingTab.WHEELS) }

    PremiumEditorPanel(
        title = stringResource(R.string.color_grading_title),
        subtitle = stringResource(R.string.panel_color_grading_subtitle),
        icon = Icons.Default.Palette,
        accent = ClearCutAccents.Peach,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.cd_close_color_grading),
        modifier = modifier,
        scrollable = true,
        headerActions = {
            PremiumPanelIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.cd_reset),
                onClick = {
                    // One-shot commit: undo boundary + persist, like a drag.
                    onDragStarted()
                    onColorGradeChanged(ColorGrade())
                    onDragEnded()
                },
                tint = ClearCutAccents.Peach
            )
        }
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Peach) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactLayout = maxWidth < 420.dp
                if (isCompactLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(
                                text = stringResource(R.string.color_grading_summary_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = semanticColors.text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = colorGradeSummary(colorGrade),
                                style = MaterialTheme.typography.bodyMedium,
                                color = semanticColors.subtext
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(text = activeTab.displayLabel(), accent = ClearCutAccents.Peach)
                            PremiumPanelPill(
                                text = if (colorGrade.hslQualifier != null) {
                                    stringResource(R.string.color_grading_qualifier_on)
                                } else {
                                    stringResource(R.string.color_grading_qualifier_off)
                                },
                                accent = if (colorGrade.hslQualifier != null) ClearCutAccents.Mauve else semanticColors.overlayStrong
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.color_grading_summary_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = semanticColors.text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = colorGradeSummary(colorGrade),
                                style = MaterialTheme.typography.bodyMedium,
                                color = semanticColors.subtext
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(text = activeTab.displayLabel(), accent = ClearCutAccents.Peach)
                            PremiumPanelPill(
                                text = if (colorGrade.hslQualifier != null) {
                                    stringResource(R.string.color_grading_qualifier_on)
                                } else {
                                    stringResource(R.string.color_grading_qualifier_off)
                                },
                                accent = if (colorGrade.hslQualifier != null) ClearCutAccents.Mauve else semanticColors.overlayStrong
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorGradingTab.entries.forEach { tab ->
                ColorGradingTabChip(
                    tab = tab,
                    selected = activeTab == tab,
                    onClick = { activeTab = tab }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (activeTab) {
            ColorGradingTab.WHEELS -> ColorWheelsContent(colorGrade, onColorGradeChanged, onDragStarted, onDragEnded)
            ColorGradingTab.CURVES -> CurvesContent(colorGrade, onColorGradeChanged, onDragStarted, onDragEnded)
            ColorGradingTab.HSL -> HslContent(colorGrade, onColorGradeChanged, onDragStarted, onDragEnded)
            ColorGradingTab.LUT -> LutContent(colorGrade, onColorGradeChanged, onLutImport, onDragStarted, onDragEnded)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorWheelsContent(
    grade: ColorGrade,
    onChange: (ColorGrade) -> Unit,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {}
) {
    val semanticColors = LocalClearCutColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Rosewater) {
            Text(
                text = stringResource(R.string.color_grading_tone_wheels_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.color_grading_tone_wheels_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = if (maxWidth < 420.dp) {
                    ((maxWidth - 12.dp) / 2).coerceAtLeast(0.dp)
                } else {
                    ((maxWidth - 24.dp) / 3).coerceAtLeast(0.dp)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ColorWheel(
                        label = stringResource(R.string.color_wheel_lift),
                        r = grade.liftR, g = grade.liftG, b = grade.liftB,
                        onChanged = { r, g, b -> onChange(grade.copy(liftR = r, liftG = g, liftB = b)) },
                        onDragStarted = onDragStarted,
                        onDragEnded = onDragEnded,
                        modifier = Modifier.width(itemWidth)
                    )
                    ColorWheel(
                        label = stringResource(R.string.color_wheel_gamma),
                        r = grade.gammaR - 1f, g = grade.gammaG - 1f, b = grade.gammaB - 1f,
                        onChanged = { r, g, b -> onChange(grade.copy(gammaR = r + 1f, gammaG = g + 1f, gammaB = b + 1f)) },
                        onDragStarted = onDragStarted,
                        onDragEnded = onDragEnded,
                        modifier = Modifier.width(itemWidth)
                    )
                    ColorWheel(
                        label = stringResource(R.string.color_wheel_gain),
                        r = grade.gainR - 1f, g = grade.gainG - 1f, b = grade.gainB - 1f,
                        onChanged = { r, g, b -> onChange(grade.copy(gainR = r + 1f, gainG = g + 1f, gainB = b + 1f)) },
                        onDragStarted = onDragStarted,
                        onDragEnded = onDragEnded,
                        modifier = Modifier.width(itemWidth)
                    )
                }
            }
        }

        PremiumPanelCard(accent = ClearCutAccents.Sapphire) {
            Text(
                text = stringResource(R.string.color_grading_offset),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.color_grading_offset_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )
            GradingSlider("R", grade.offsetR, -0.5f, 0.5f, ClearCutAccents.Red, onDragStarted, onDragEnded) { onChange(grade.copy(offsetR = it)) }
            GradingSlider("G", grade.offsetG, -0.5f, 0.5f, ClearCutAccents.Green, onDragStarted, onDragEnded) { onChange(grade.copy(offsetG = it)) }
            GradingSlider("B", grade.offsetB, -0.5f, 0.5f, ClearCutAccents.Blue, onDragStarted, onDragEnded) { onChange(grade.copy(offsetB = it)) }
        }
    }
}

@Composable
private fun ColorWheel(
    label: String,
    r: Float, g: Float, b: Float,
    onChanged: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {}
) {
    val semanticColors = LocalClearCutColors.current
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PremiumPanelPill(text = label, accent = ClearCutAccents.Peach)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(98.dp)
                .clip(CircleShape)
                .background(semanticColors.panelRaised)
                .drawBehind {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension / 2

                    for (angle in 0 until 360 step 3) {
                        val rad = angle * PI.toFloat() / 180f
                        val hue = angle.toFloat()
                        val color = Color.hsv(hue, 0.6f, 0.4f)
                        drawLine(
                            color = color,
                            start = center,
                            end = Offset(
                                center.x + cos(rad) * radius,
                                center.y + sin(rad) * radius
                            ),
                            strokeWidth = 4f
                        )
                    }

                    val dotX = center.x + r * radius
                    val dotY = center.y + g * radius
                    drawCircle(Color.White, 6f, Offset(dotX, dotY))
                    drawCircle(Color.Black, 6f, Offset(dotX, dotY), style = Stroke(2f))
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStarted() },
                        onDragEnd = { onDragEnded() },
                        onDragCancel = { onDragEnded() }
                    ) { change, _ ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = ((change.position.x - cx) / cx).coerceIn(-0.5f, 0.5f)
                        val dy = ((change.position.y - cy) / cy).coerceIn(-0.5f, 0.5f)
                        // Map X to R offset, Y to G offset, diagonal to B offset
                        val bOffset = (-dx - dy).coerceIn(-0.5f, 0.5f) / 2f
                        onChanged(dx, dy, bOffset)
                    }
                },
            contentAlignment = Alignment.Center
        ) {}

        Text(
            text = stringResource(R.string.cd_reset),
            color = ClearCutAccents.Peach,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clickable {
                    onDragStarted()
                    onChanged(0f, 0f, 0f)
                    onDragEnded()
                }
                .padding(top = 6.dp)
        )
    }
}

@Composable
private fun GradingSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    color: Color,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {},
    onChanged: (Float) -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    // Slider has no explicit press callback; capture the undo boundary on the
    // first value change of an interaction, and persist on change-finished.
    var interacting by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
            Text(
                text = "%.2f".format(value),
                color = color,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Slider(
            value = value,
            onValueChange = {
                if (!interacting) {
                    interacting = true
                    onDragStarted()
                }
                onChanged(it)
            },
            onValueChangeFinished = {
                interacting = false
                onDragEnded()
            },
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.6f),
                inactiveTrackColor = semanticColors.surface
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurvesContent(
    grade: ColorGrade,
    onChange: (ColorGrade) -> Unit,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {}
) {
    val semanticColors = LocalClearCutColors.current
    var activeCurve by remember { mutableStateOf("master") }
    val curves = grade.curves

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val points = when (activeCurve) {
            "red" -> curves.red
            "green" -> curves.green
            "blue" -> curves.blue
            else -> curves.master
        }
        val curveColor = when (activeCurve) {
            "red" -> ClearCutAccents.Red
            "green" -> ClearCutAccents.Green
            "blue" -> ClearCutAccents.Blue
            else -> semanticColors.text
        }

        PremiumPanelCard(accent = curveColor) {
            Text(
                text = stringResource(R.string.color_grading_curve_response_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.color_grading_curve_response_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("master", semanticColors.text, stringResource(R.string.color_curve_master)),
                    Triple("red", ClearCutAccents.Red, stringResource(R.string.color_curve_red)),
                    Triple("green", ClearCutAccents.Green, stringResource(R.string.color_curve_green)),
                    Triple("blue", ClearCutAccents.Blue, stringResource(R.string.color_curve_blue))
                ).forEach { (id, color, channelLabel) ->
                    val selected = activeCurve == id
                    Surface(
                        color = if (selected) color.copy(alpha = 0.16f) else semanticColors.panelRaised,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (selected) color.copy(alpha = 0.24f) else semanticColors.cardStroke)
                    ) {
                        Text(
                            text = channelLabel,
                            color = if (selected) color else semanticColors.subtext,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clickable { activeCurve = id }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }
            CurveEditor(
                points = points,
                color = curveColor,
                onDragStarted = onDragStarted,
                onDragEnded = onDragEnded,
                onPointsChanged = { newPoints ->
                    val newCurves = when (activeCurve) {
                        "red" -> curves.copy(red = newPoints)
                        "green" -> curves.copy(green = newPoints)
                        "blue" -> curves.copy(blue = newPoints)
                        else -> curves.copy(master = newPoints)
                    }
                    onChange(grade.copy(curves = newCurves))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(semanticColors.panelRaised, RoundedCornerShape(20.dp))
            )
        }
    }
}

@Composable
private fun CurveEditor(
    points: List<CurvePoint>,
    color: Color,
    onPointsChanged: (List<CurvePoint>) -> Unit,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    var dragIndex by remember { mutableIntStateOf(-1) }
    // pointerInput must NOT key on `points`: every onPointsChanged emit
    // recomposed with a new list and cancelled the gesture coroutine after
    // one frame — dragging a curve point died immediately, and touch-to-add
    // (which mutates in onDragStart) never received its onDrag stream.
    val currentPoints by rememberUpdatedState(points)
    val currentOnPointsChanged by rememberUpdatedState(onPointsChanged)
    val currentOnDragStarted by rememberUpdatedState(onDragStarted)
    val currentOnDragEnded by rememberUpdatedState(onDragEnded)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentOnDragStarted()
                        val x = offset.x / size.width
                        val y = 1f - offset.y / size.height
                        // Find nearest point or create new one
                        val nearest = currentPoints.withIndex().minByOrNull {
                            val dx = it.value.x - x
                            val dy = it.value.y - y
                            dx * dx + dy * dy
                        }
                        if (nearest != null && abs(nearest.value.x - x) < 0.1f && abs(nearest.value.y - y) < 0.1f) {
                            dragIndex = nearest.index
                        } else {
                            // Add new point
                            val newPoints = currentPoints.toMutableList()
                            newPoints.add(CurvePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)))
                            newPoints.sortBy { it.x }
                            currentOnPointsChanged(newPoints)
                            dragIndex = newPoints.indexOfFirst { it.x == x.coerceIn(0f, 1f) }
                        }
                    },
                    onDrag = { change, _ ->
                        if (dragIndex in currentPoints.indices) {
                            val requestedX = (change.position.x / size.width).coerceIn(0f, 1f)
                            val x = clampCurvePointX(currentPoints, dragIndex, requestedX)
                            val y = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            val newPoints = currentPoints.toMutableList()
                            newPoints[dragIndex] = newPoints[dragIndex].copy(x = x, y = y)
                            currentOnPointsChanged(newPoints)
                        }
                    },
                    onDragEnd = {
                        dragIndex = -1
                        currentOnDragEnded()
                    },
                    onDragCancel = {
                        dragIndex = -1
                        currentOnDragEnded()
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height

        // Grid lines
        for (i in 1..3) {
            val pos = i / 4f
            drawLine(semanticColors.surfaceHigh, Offset(pos * w, 0f), Offset(pos * w, h), 1f)
            drawLine(semanticColors.surfaceHigh, Offset(0f, pos * h), Offset(w, pos * h), 1f)
        }

        // Diagonal reference line
        drawLine(semanticColors.surface, Offset(0f, h), Offset(w, 0f), 1f)

        // Draw curve
        if (points.size >= 2) {
            val path = Path()
            val steps = 100
            for (i in 0..steps) {
                val x = i.toFloat() / steps
                val y = evaluateCurveSmooth(points, x)
                val px = x * w
                val py = (1f - y) * h
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = Stroke(2f))
        }

        // Draw control points
        points.forEach { point ->
            drawCircle(
                color,
                6f,
                Offset(point.x * w, (1f - point.y) * h)
            )
            drawCircle(
                Color.White,
                4f,
                Offset(point.x * w, (1f - point.y) * h)
            )
        }
    }
}

private fun evaluateCurveSmooth(points: List<CurvePoint>, x: Float): Float {
    if (points.isEmpty()) return x
    if (points.size == 1) return points[0].y
    val sorted = points.sortedBy { it.x }
    if (x <= sorted.first().x) return sorted.first().y
    if (x >= sorted.last().x) return sorted.last().y

    for (i in 0 until sorted.size - 1) {
        if (x >= sorted[i].x && x <= sorted[i + 1].x) {
            // Guard duplicate-x points (added points have no min-spacing, and legacy
            // saves can carry them): a zero span divides to NaN and blanks the curve.
            val span = sorted[i + 1].x - sorted[i].x
            if (span <= 0f) return sorted[i].y
            val t = (x - sorted[i].x) / span
            // Smooth hermite interpolation
            val t2 = t * t
            val t3 = t2 * t
            val h1 = 2f * t3 - 3f * t2 + 1f
            val h2 = -2f * t3 + 3f * t2
            return h1 * sorted[i].y + h2 * sorted[i + 1].y
        }
    }
    return x
}

private fun clampCurvePointX(points: List<CurvePoint>, index: Int, requestedX: Float): Float {
    if (points.isEmpty()) return requestedX
    if (index == 0) return 0f
    if (index == points.lastIndex) return 1f

    val previous = points.getOrNull(index - 1)?.x ?: 0f
    val next = points.getOrNull(index + 1)?.x ?: 1f
    return requestedX.coerceIn(previous + 0.02f, next - 0.02f)
}

@Composable
private fun HslContent(
    grade: ColorGrade,
    onChange: (ColorGrade) -> Unit,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {}
) {
    val semanticColors = LocalClearCutColors.current
    val hsl = grade.hslQualifier ?: HslQualifier()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Mauve) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.color_grading_hsl_qualifier),
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.color_grading_hsl_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext
                    )
                }
                Switch(
                    checked = grade.hslQualifier != null,
                    onCheckedChange = { enabled ->
                        onDragStarted()
                        onChange(grade.copy(hslQualifier = if (enabled) HslQualifier() else null))
                        onDragEnded()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = ClearCutAccents.Mauve)
                )
            }
        }

        if (grade.hslQualifier != null) {
            PremiumPanelCard(accent = ClearCutAccents.Yellow) {
                Text(
                    text = stringResource(R.string.color_grading_selection),
                    style = MaterialTheme.typography.titleMedium,
                    color = semanticColors.text
                )
                GradingSlider(stringResource(R.string.color_hsl_hue), hsl.hueCenter, 0f, 360f, ClearCutAccents.Yellow, onDragStarted, onDragEnded) {
                    onChange(grade.copy(hslQualifier = hsl.copy(hueCenter = it)))
                }
                GradingSlider(stringResource(R.string.color_hsl_width), hsl.hueWidth, 1f, 180f, ClearCutAccents.Yellow, onDragStarted, onDragEnded) {
                    onChange(grade.copy(hslQualifier = hsl.copy(hueWidth = it)))
                }
                GradingSlider(stringResource(R.string.color_hsl_sat_min), hsl.satMin, 0f, 1f, ClearCutAccents.Mauve, onDragStarted, onDragEnded) {
                    onChange(grade.copy(hslQualifier = hsl.copy(satMin = it)))
                }
                GradingSlider(stringResource(R.string.color_hsl_sat_max), hsl.satMax, 0f, 1f, ClearCutAccents.Mauve, onDragStarted, onDragEnded) {
                    onChange(grade.copy(hslQualifier = hsl.copy(satMax = it)))
                }
                GradingSlider(stringResource(R.string.color_hsl_lum_min), hsl.lumMin, 0f, 1f, semanticColors.text, onDragStarted, onDragEnded) {
                    onChange(grade.copy(hslQualifier = hsl.copy(lumMin = it)))
                }
                GradingSlider(stringResource(R.string.color_hsl_lum_max), hsl.lumMax, 0f, 1f, semanticColors.text, onDragStarted, onDragEnded) {
                    onChange(grade.copy(hslQualifier = hsl.copy(lumMax = it)))
                }
                GradingSlider(stringResource(R.string.color_hsl_soft), hsl.softness, 0f, 0.5f, ClearCutAccents.Peach, onDragStarted, onDragEnded) {
                    onChange(grade.copy(hslQualifier = hsl.copy(softness = it)))
                }
            }

            PremiumPanelCard(accent = ClearCutAccents.Sapphire) {
                Text(
                    text = stringResource(R.string.color_grading_adjustment),
                    style = MaterialTheme.typography.titleMedium,
                    color = semanticColors.text
                )
                GradingSlider(stringResource(R.string.color_hsl_hue), hsl.adjustHue, -180f, 180f, ClearCutAccents.Yellow, onDragStarted, onDragEnded) {
                    onChange(grade.copy(hslQualifier = hsl.copy(adjustHue = it)))
                }
                GradingSlider(stringResource(R.string.color_hsl_sat), hsl.adjustSat, -1f, 1f, ClearCutAccents.Mauve, onDragStarted, onDragEnded) {
                    onChange(grade.copy(hslQualifier = hsl.copy(adjustSat = it)))
                }
                GradingSlider(stringResource(R.string.color_hsl_lum), hsl.adjustLum, -1f, 1f, semanticColors.text, onDragStarted, onDragEnded) {
                    onChange(grade.copy(hslQualifier = hsl.copy(adjustLum = it)))
                }
            }
        }
    }
}

@Composable
private fun LutContent(
    grade: ColorGrade,
    onChange: (ColorGrade) -> Unit,
    onLutImport: () -> Unit,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {}
) {
    val semanticColors = LocalClearCutColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Mauve) {
            if (grade.lutPath != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.color_grading_active_lut),
                            style = MaterialTheme.typography.titleMedium,
                            color = semanticColors.text
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = grade.lutPath.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = semanticColors.subtext
                        )
                    }
                    PremiumPanelIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_remove_lut),
                        onClick = {
                            onDragStarted()
                            onChange(grade.copy(lutPath = null, lutIntensity = 1f))
                            onDragEnded()
                        },
                        tint = ClearCutAccents.Red
                    )
                }

                GradingSlider(stringResource(R.string.color_lut_intensity), grade.lutIntensity, 0f, 1f, ClearCutAccents.Mauve, onDragStarted, onDragEnded) {
                    onChange(grade.copy(lutIntensity = it))
                }
            } else {
                Text(
                    text = stringResource(R.string.color_grading_no_lut_loaded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
                )
            }
        }

        Button(
            onClick = onLutImport,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = ClearCutAccents.Mauve.copy(alpha = 0.18f),
                contentColor = ClearCutAccents.Mauve
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.FileOpen, stringResource(R.string.cd_import_lut), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.color_grading_import_lut))
        }
    }
}

@Composable
private fun ColorGradingTabChip(
    tab: ColorGradingTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    val accent = when (tab) {
        ColorGradingTab.WHEELS -> ClearCutAccents.Peach
        ColorGradingTab.CURVES -> ClearCutAccents.Sapphire
        ColorGradingTab.HSL -> ClearCutAccents.Mauve
        ColorGradingTab.LUT -> ClearCutAccents.Lavender
    }

    Surface(
        color = if (selected) accent.copy(alpha = 0.16f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.24f) else semanticColors.cardStroke)
    ) {
        Text(
            text = tab.displayLabel(),
            color = if (selected) accent else semanticColors.subtext,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

private fun colorGradeSummary(grade: ColorGrade): String {
    return when {
        grade.lutPath != null -> "A LUT is loaded and ready to blend with the current primary correction."
        grade.hslQualifier != null -> "The qualifier is active, so secondary hue and luma refinements are available."
        grade.hasPrimaryAdjustments() -> "Primary corrections are active across the tone wheels or channel offsets."
        else -> "No correction has been pushed yet, so this clip is ready for a clean starting grade."
    }
}

private fun ColorGrade.hasPrimaryAdjustments(): Boolean {
    return liftR != 0f ||
        liftG != 0f ||
        liftB != 0f ||
        gammaR != 1f ||
        gammaG != 1f ||
        gammaB != 1f ||
        gainR != 1f ||
        gainG != 1f ||
        gainB != 1f ||
        offsetR != 0f ||
        offsetG != 0f ||
        offsetB != 0f
}
