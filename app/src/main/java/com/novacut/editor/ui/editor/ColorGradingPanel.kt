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

@Composable
fun ColorGradingPanel(
    colorGrade: ColorGrade,
    onColorGradeChanged: (ColorGrade) -> Unit,
    onDragStarted: () -> Unit = {},
    onLutImport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(ColorGradingTab.WHEELS) }

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
            Text(stringResource(R.string.color_grading_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = {
                    onColorGradeChanged(ColorGrade())
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Reset", tint = Mocha.Peach, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mocha.Surface0, RoundedCornerShape(8.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ColorGradingTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) Mocha.Mauve.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { activeTab = tab }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.label,
                        color = if (selected) Mocha.Mauve else Mocha.Subtext0,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 350.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (activeTab) {
                ColorGradingTab.WHEELS -> ColorWheelsContent(colorGrade, onColorGradeChanged, onDragStarted)
                ColorGradingTab.CURVES -> CurvesContent(colorGrade, onColorGradeChanged, onDragStarted)
                ColorGradingTab.HSL -> HslContent(colorGrade, onColorGradeChanged, onDragStarted)
                ColorGradingTab.LUT -> LutContent(colorGrade, onColorGradeChanged, onLutImport)
            }
        }
    }
}

@Composable
private fun ColorWheelsContent(
    grade: ColorGrade,
    onChange: (ColorGrade) -> Unit,
    onDragStarted: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Three color wheels: Lift, Gamma, Gain
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ColorWheel(
                label = "Lift",
                r = grade.liftR, g = grade.liftG, b = grade.liftB,
                onChanged = { r, g, b -> onChange(grade.copy(liftR = r, liftG = g, liftB = b)) },
                onDragStarted = onDragStarted,
                modifier = Modifier.weight(1f)
            )
            ColorWheel(
                label = "Gamma",
                r = grade.gammaR - 1f, g = grade.gammaG - 1f, b = grade.gammaB - 1f,
                onChanged = { r, g, b -> onChange(grade.copy(gammaR = r + 1f, gammaG = g + 1f, gammaB = b + 1f)) },
                onDragStarted = onDragStarted,
                modifier = Modifier.weight(1f)
            )
            ColorWheel(
                label = "Gain",
                r = grade.gainR - 1f, g = grade.gainG - 1f, b = grade.gainB - 1f,
                onChanged = { r, g, b -> onChange(grade.copy(gainR = r + 1f, gainG = g + 1f, gainB = b + 1f)) },
                onDragStarted = onDragStarted,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Offset sliders
        Text(stringResource(R.string.color_grading_offset), color = Mocha.Subtext0, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        GradingSlider("R", grade.offsetR, -0.5f, 0.5f, Mocha.Red) { onChange(grade.copy(offsetR = it)) }
        GradingSlider("G", grade.offsetG, -0.5f, 0.5f, Mocha.Green) { onChange(grade.copy(offsetG = it)) }
        GradingSlider("B", grade.offsetB, -0.5f, 0.5f, Mocha.Blue) { onChange(grade.copy(offsetB = it)) }
    }
}

@Composable
private fun ColorWheel(
    label: String,
    r: Float, g: Float, b: Float,
    onChanged: (Float, Float, Float) -> Unit,
    onDragStarted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Mocha.Subtext0, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Mocha.Surface0)
                .drawBehind {
                    // Draw color wheel background
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension / 2

                    // Simple color wheel: draw concentric rainbow
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

                    // Indicator dot
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

        // Reset button
        Text(
            "Reset",
            color = Mocha.Peach.copy(alpha = 0.7f),
            fontSize = 9.sp,
            modifier = Modifier
                .clickable { onChanged(0f, 0f, 0f) }
                .padding(2.dp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = color, fontSize = 11.sp, modifier = Modifier.width(16.dp))
        Slider(
            value = value,
            onValueChange = onChanged,
            valueRange = min..max,
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.6f),
                inactiveTrackColor = Mocha.Surface1
            )
        )
        Text(
            "%.2f".format(value),
            color = Mocha.Subtext0,
            fontSize = 10.sp,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun CurvesContent(
    grade: ColorGrade,
    onChange: (ColorGrade) -> Unit,
    onDragStarted: () -> Unit = {}
) {
    var activeCurve by remember { mutableStateOf("master") }
    val curves = grade.curves

    Column(modifier = Modifier.fillMaxWidth()) {
        // Curve channel selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("master" to Mocha.Text, "red" to Mocha.Red, "green" to Mocha.Green, "blue" to Mocha.Blue).forEach { (id, color) ->
                val selected = activeCurve == id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) color.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { activeCurve = id }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        id.replaceFirstChar { it.uppercase() },
                        color = if (selected) color else Mocha.Subtext0,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Curve canvas
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
                .height(200.dp)
                .background(Mocha.Surface0, RoundedCornerShape(8.dp))
        )
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
                            val x = (change.position.x / size.width).coerceIn(0f, 1f)
                            val y = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            val newPoints = points.toMutableList()
                            newPoints[dragIndex] = newPoints[dragIndex].copy(x = x, y = y)
                            newPoints.sortBy { it.x }
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

@Composable
private fun HslContent(
    grade: ColorGrade,
    onChange: (ColorGrade) -> Unit,
    onDragStarted: () -> Unit = {}
) {
    val hsl = grade.hslQualifier ?: HslQualifier()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.color_grading_hsl_qualifier), color = Mocha.Text, fontSize = 13.sp)
            Switch(
                checked = grade.hslQualifier != null,
                onCheckedChange = { enabled ->
                    onChange(grade.copy(hslQualifier = if (enabled) HslQualifier() else null))
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Mocha.Mauve)
            )
        }

        if (grade.hslQualifier != null) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.color_grading_selection), color = Mocha.Subtext0, fontSize = 11.sp)
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

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.color_grading_adjustment), color = Mocha.Subtext0, fontSize = 11.sp)
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

@Composable
private fun LutContent(
    grade: ColorGrade,
    onChange: (ColorGrade) -> Unit,
    onLutImport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (grade.lutPath != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.color_grading_active_lut), color = Mocha.Text, fontSize = 13.sp)
                    Text(
                        grade.lutPath.substringAfterLast("/"),
                        color = Mocha.Subtext0,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = {
                    onChange(grade.copy(lutPath = null, lutIntensity = 1f))
                }) {
                    Icon(Icons.Default.Delete, "Remove LUT", tint = Mocha.Red)
                }
            }

            Spacer(Modifier.height(8.dp))
            GradingSlider("Intensity", grade.lutIntensity, 0f, 1f, Mocha.Mauve) {
                onChange(grade.copy(lutIntensity = it))
            }
        } else {
            Text(stringResource(R.string.color_grading_no_lut_loaded), color = Mocha.Subtext0, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onLutImport,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.FileOpen, "Import", tint = Mocha.Mauve, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.color_grading_import_lut), color = Mocha.Mauve)
        }
    }
}
