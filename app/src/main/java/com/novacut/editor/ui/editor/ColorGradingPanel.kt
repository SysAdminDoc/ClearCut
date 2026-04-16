package com.novacut.editor.ui.editor

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
import com.novacut.editor.ui.theme.Mocha
import kotlin.math.*

enum class ColorGradingTab(val label: String) {
    WHEELS("Wheels"),
    CURVES("Curves"),
    HSL("HSL"),
    LUT("LUT")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorGradingPanel(
    colorGrade: ColorGrade,
    onColorGradeChanged: (ColorGrade) -> Unit,
    modifier: Modifier = Modifier,
    onDragStarted: () -> Unit = {},
    onLutImport: () -> Unit,
    onClose: () -> Unit
) {
    var activeTab by remember { mutableStateOf(ColorGradingTab.WHEELS) }

    PremiumEditorPanel(
        title = stringResource(R.string.color_grading_title),
        subtitle = stringResource(R.string.panel_color_grading_subtitle),
        icon = Icons.Default.Palette,
        accent = Mocha.Peach,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.cd_close_color_grading),
        modifier = modifier,
        scrollable = true,
        headerActions = {
            PremiumPanelIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.cd_reset),
                onClick = { onColorGradeChanged(ColorGrade()) },
                tint = Mocha.Peach
            )
        }
    ) {
        PremiumPanelCard(accent = Mocha.Peach) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactLayout = maxWidth < 420.dp
                if (isCompactLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(
                                text = stringResource(R.string.color_grading_summary_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = Mocha.Text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = colorGradeSummary(colorGrade),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Mocha.Subtext0
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(text = activeTab.label, accent = Mocha.Peach)
                            PremiumPanelPill(
                                text = if (colorGrade.hslQualifier != null) {
                                    stringResource(R.string.color_grading_qualifier_on)
                                } else {
                                    stringResource(R.string.color_grading_qualifier_off)
                                },
                                accent = if (colorGrade.hslQualifier != null) Mocha.Mauve else Mocha.Overlay1
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
                                color = Mocha.Text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = colorGradeSummary(colorGrade),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Mocha.Subtext0
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(text = activeTab.label, accent = Mocha.Peach)
                            PremiumPanelPill(
                                text = if (colorGrade.hslQualifier != null) {
                                    stringResource(R.string.color_grading_qualifier_on)
                                } else {
                                    stringResource(R.string.color_grading_qualifier_off)
                                },
                                accent = if (colorGrade.hslQualifier != null) Mocha.Mauve else Mocha.Overlay1
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
            ColorGradingTab.WHEELS -> ColorWheelsContent(colorGrade, onColorGradeChanged, onDragStarted)
            ColorGradingTab.CURVES -> CurvesContent(colorGrade, onColorGradeChanged, onDragStarted)
            ColorGradingTab.HSL -> HslContent(colorGrade, onColorGradeChanged, onDragStarted)
            ColorGradingTab.LUT -> LutContent(colorGrade, onColorGradeChanged, onLutImport)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorWheelsContent(
    grade: ColorGrade,
    onChange: (ColorGrade) -> Unit,
    onDragStarted: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PremiumPanelCard(accent = Mocha.Rosewater) {
            Text(
                text = stringResource(R.string.color_grading_tone_wheels_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.color_grading_tone_wheels_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
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
                        modifier = Modifier.width(itemWidth)
                    )
                    ColorWheel(
                        label = stringResource(R.string.color_wheel_gamma),
                        r = grade.gammaR - 1f, g = grade.gammaG - 1f, b = grade.gammaB - 1f,
                        onChanged = { r, g, b -> onChange(grade.copy(gammaR = r + 1f, gammaG = g + 1f, gammaB = b + 1f)) },
                        onDragStarted = onDragStarted,
                        modifier = Modifier.width(itemWidth)
                    )
                    ColorWheel(
                        label = stringResource(R.string.color_wheel_gain),
                        r = grade.gainR - 1f, g = grade.gainG - 1f, b = grade.gainB - 1f,
                        onChanged = { r, g, b -> onChange(grade.copy(gainR = r + 1f, gainG = g + 1f, gainB = b + 1f)) },
                        onDragStarted = onDragStarted,
                        modifier = Modifier.width(itemWidth)
                    )
                }
            }
        }

        PremiumPanelCard(accent = Mocha.Sapphire) {
            Text(
                text = stringResource(R.string.color_grading_offset),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.color_grading_offset_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )
            GradingSlider("R", grade.offsetR, -0.5f, 0.5f, Mocha.Red) { onChange(grade.copy(offsetR = it)) }
            GradingSlider("G", grade.offsetG, -0.5f, 0.5f, Mocha.Green) { onChange(grade.copy(offsetG = it)) }
            GradingSlider("B", grade.offsetB, -0.5f, 0.5f, Mocha.Blue) { onChange(grade.copy(offsetB = it)) }
        }
    }
}

@Composable
private fun ColorWheel(
    label: String,
    r: Float, g: Float, b: Float,
    onChanged: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragStarted: () -> Unit = {}
) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PremiumPanelPill(text = label, accent = Mocha.Peach)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(98.dp)
                .clip(CircleShape)
                .background(Mocha.PanelRaised)
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
                        onDragStart = { onDragStarted() }
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
            color = Mocha.Peach,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clickable { onChanged(0f, 0f, 0f) }
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
    onChanged: (Float) -> Unit
) {
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
            onValueChange = onChanged,
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.6f),
                inactiveTrackColor = Mocha.Surface1
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurvesContent(
    grade: ColorGrade,
    onChange: (ColorGrade) -> Unit,
    onDragStarted: () -> Unit = {}
) {
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
            "red" -> Mocha.Red
            "green" -> Mocha.Green
            "blue" -> Mocha.Blue
            else -> Mocha.Text
        }

        PremiumPanelCard(accent = curveColor) {
            Text(
                text = stringResource(R.string.color_grading_curve_response_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.color_grading_curve_response_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("master" to Mocha.Text, "red" to Mocha.Red, "green" to Mocha.Green, "blue" to Mocha.Blue).forEach { (id, color) ->
                    val selected = activeCurve == id
                    Surface(
                        color = if (selected) color.copy(alpha = 0.16f) else Mocha.PanelRaised,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (selected) color.copy(alpha = 0.24f) else Mocha.CardStroke)
                    ) {
                        Text(
                            text = id.replaceFirstChar { it.uppercase() },
                            color = if (selected) color else Mocha.Subtext0,
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
                    .background(Mocha.PanelRaised, RoundedCornerShape(20.dp))
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
    modifier: Modifier = Modifier
) {
    var dragIndex by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onDragStarted()
                        val x = offset.x / size.width
                        val y = 1f - offset.y / size.height
                        // Find nearest point or create new one
                        val nearest = points.withIndex().minByOrNull {
                            val dx = it.value.x - x
                            val dy = it.value.y - y
                            dx * dx + dy * dy
                        }
                        if (nearest != null && abs(nearest.value.x - x) < 0.1f && abs(nearest.value.y - y) < 0.1f) {
                            dragIndex = nearest.index
                        } else {
                            // Add new point
                            val newPoints = points.toMutableList()
                            newPoints.add(CurvePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)))
                            newPoints.sortBy { it.x }
                        onPointsChanged(newPoints)
                        dragIndex = newPoints.indexOfFirst { it.x == x.coerceIn(0f, 1f) }
                    }
                },
                onDrag = { change, _ ->
                    if (dragIndex in points.indices) {
                        val requestedX = (change.position.x / size.width).coerceIn(0f, 1f)
                        val x = clampCurvePointX(points, dragIndex, requestedX)
                        val y = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        val newPoints = points.toMutableList()
                        newPoints[dragIndex] = newPoints[dragIndex].copy(x = x, y = y)
                        onPointsChanged(newPoints)
                    }
                },
                    onDragEnd = { dragIndex = -1 }
                )
            }
    ) {
        val w = size.width
        val h = size.height

        // Grid lines
        for (i in 1..3) {
            val pos = i / 4f
            drawLine(Mocha.Surface2, Offset(pos * w, 0f), Offset(pos * w, h), 1f)
            drawLine(Mocha.Surface2, Offset(0f, pos * h), Offset(w, pos * h), 1f)
        }

        // Diagonal reference line
        drawLine(Mocha.Surface1, Offset(0f, h), Offset(w, 0f), 1f)

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
            val t = (x - sorted[i].x) / (sorted[i + 1].x - sorted[i].x)
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
    onDragStarted: () -> Unit = {}
) {
    val hsl = grade.hslQualifier ?: HslQualifier()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PremiumPanelCard(accent = Mocha.Mauve) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.color_grading_hsl_qualifier),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Isolate a color range before nudging hue, saturation, or luminance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }
                Switch(
                    checked = grade.hslQualifier != null,
                    onCheckedChange = { enabled ->
                        onChange(grade.copy(hslQualifier = if (enabled) HslQualifier() else null))
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Mocha.Mauve)
                )
            }
        }

        if (grade.hslQualifier != null) {
            PremiumPanelCard(accent = Mocha.Yellow) {
                Text(
                    text = stringResource(R.string.color_grading_selection),
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                GradingSlider("Hue", hsl.hueCenter, 0f, 360f, Mocha.Yellow) {
                    onChange(grade.copy(hslQualifier = hsl.copy(hueCenter = it)))
                }
                GradingSlider("Width", hsl.hueWidth, 1f, 180f, Mocha.Yellow) {
                    onChange(grade.copy(hslQualifier = hsl.copy(hueWidth = it)))
                }
                GradingSlider("Sat Min", hsl.satMin, 0f, 1f, Mocha.Mauve) {
                    onChange(grade.copy(hslQualifier = hsl.copy(satMin = it)))
                }
                GradingSlider("Sat Max", hsl.satMax, 0f, 1f, Mocha.Mauve) {
                    onChange(grade.copy(hslQualifier = hsl.copy(satMax = it)))
                }
                GradingSlider("Lum Min", hsl.lumMin, 0f, 1f, Mocha.Text) {
                    onChange(grade.copy(hslQualifier = hsl.copy(lumMin = it)))
                }
                GradingSlider("Lum Max", hsl.lumMax, 0f, 1f, Mocha.Text) {
                    onChange(grade.copy(hslQualifier = hsl.copy(lumMax = it)))
                }
                GradingSlider("Soft", hsl.softness, 0f, 0.5f, Mocha.Peach) {
                    onChange(grade.copy(hslQualifier = hsl.copy(softness = it)))
                }
            }

            PremiumPanelCard(accent = Mocha.Sapphire) {
                Text(
                    text = stringResource(R.string.color_grading_adjustment),
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                GradingSlider("Hue", hsl.adjustHue, -180f, 180f, Mocha.Yellow) {
                    onChange(grade.copy(hslQualifier = hsl.copy(adjustHue = it)))
                }
                GradingSlider("Sat", hsl.adjustSat, -1f, 1f, Mocha.Mauve) {
                    onChange(grade.copy(hslQualifier = hsl.copy(adjustSat = it)))
                }
                GradingSlider("Lum", hsl.adjustLum, -1f, 1f, Mocha.Text) {
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
    onLutImport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PremiumPanelCard(accent = Mocha.Mauve) {
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
                            color = Mocha.Text
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = grade.lutPath.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mocha.Subtext0
                        )
                    }
                    PremiumPanelIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_remove_lut),
                        onClick = { onChange(grade.copy(lutPath = null, lutIntensity = 1f)) },
                        tint = Mocha.Red
                    )
                }

                GradingSlider("Intensity", grade.lutIntensity, 0f, 1f, Mocha.Mauve) {
                    onChange(grade.copy(lutIntensity = it))
                }
            } else {
                Text(
                    text = stringResource(R.string.color_grading_no_lut_loaded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
            }
        }

        Button(
            onClick = onLutImport,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Mocha.Mauve.copy(alpha = 0.18f),
                contentColor = Mocha.Mauve
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
    val accent = when (tab) {
        ColorGradingTab.WHEELS -> Mocha.Peach
        ColorGradingTab.CURVES -> Mocha.Sapphire
        ColorGradingTab.HSL -> Mocha.Mauve
        ColorGradingTab.LUT -> Mocha.Lavender
    }

    Surface(
        color = if (selected) accent.copy(alpha = 0.16f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.24f) else Mocha.CardStroke)
    ) {
        Text(
            text = tab.label,
            color = if (selected) accent else Mocha.Subtext0,
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
