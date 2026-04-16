package com.novacut.editor.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha
import kotlin.math.min

private val ScopeBg = Color(0xFF11111B)
private val ScopeCanvasBackground = Color(0xFF0A0A0A)
private val ScopeRed = Color(0xFFFF4444)
private val ScopeGreen = Color(0xFF44FF44)
private val ScopeBlue = Color(0xFF4488FF)
private val ScopeWhite = Color(0xFFCCCCCC)
private val ScopeGrid = Color(0xFF333333)

enum class ScopeType(val label: String) {
    HISTOGRAM("Histogram"),
    WAVEFORM("Waveform"),
    VECTORSCOPE("Vectorscope")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoScopesOverlay(
    frameBitmap: Bitmap?,
    activeScope: ScopeType,
    onScopeChanged: (ScopeType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scopeData by remember(frameBitmap, activeScope) { mutableStateOf<ScopeData?>(null) }
    val hasFrame = frameBitmap?.isRecycled == false
    val isAnalyzing = hasFrame && scopeData == null
    val scopeAccent = activeScope.accent()

    LaunchedEffect(frameBitmap, activeScope) {
        val bitmap = frameBitmap
        scopeData = if (bitmap != null && !bitmap.isRecycled) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                analyzeBitmap(bitmap, activeScope)
            }
        } else {
            null
        }
    }

    Surface(
        modifier = modifier.widthIn(min = 264.dp, max = 320.dp),
        color = ScopeBg.copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Mocha.CardStrokeStrong.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Mocha.Surface2.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.video_scopes_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(activeScope.descriptionRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                PremiumPanelIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.video_scopes_close_cd),
                    onClick = onClose,
                    tint = Mocha.Subtext0,
                    containerColor = Mocha.PanelRaised
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = activeScope.label,
                    accent = scopeAccent
                )
                PremiumPanelPill(
                    text = stringResource(
                        when {
                            !hasFrame -> R.string.video_scopes_status_waiting
                            isAnalyzing -> R.string.video_scopes_status_analyzing
                            else -> R.string.video_scopes_status_ready
                        }
                    ),
                    accent = when {
                        !hasFrame -> Mocha.Overlay1
                        isAnalyzing -> Mocha.Yellow
                        else -> Mocha.Green
                    }
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScopeType.entries.forEach { scope ->
                    FilterChip(
                        selected = scope == activeScope,
                        onClick = { onScopeChanged(scope) },
                        label = {
                            Text(
                                text = scope.label,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = scope.accent().copy(alpha = 0.18f),
                            selectedLabelColor = scope.accent(),
                            labelColor = Mocha.Subtext0
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ScopeCanvasBackground),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !hasFrame -> ScopeStateMessage(
                        title = stringResource(R.string.video_scopes_waiting_title),
                        body = stringResource(R.string.video_scopes_waiting_body)
                    )

                    isAnalyzing -> ScopeLoadingState()

                    else -> Canvas(modifier = Modifier.fillMaxSize()) {
                        when (activeScope) {
                            ScopeType.HISTOGRAM -> drawHistogram(scopeData as? HistogramData)
                            ScopeType.WAVEFORM -> drawWaveformScope(scopeData as? WaveformData)
                            ScopeType.VECTORSCOPE -> drawVectorscope(scopeData as? VectorscopeData)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeStateMessage(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = Mocha.Text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ScopeLoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Mocha.Yellow,
            strokeWidth = 2.dp
        )
        ScopeStateMessage(
            title = stringResource(R.string.video_scopes_loading_title),
            body = stringResource(R.string.video_scopes_loading_body)
        )
    }
}

private fun ScopeType.accent(): Color = when (this) {
    ScopeType.HISTOGRAM -> Mocha.Peach
    ScopeType.WAVEFORM -> Mocha.Blue
    ScopeType.VECTORSCOPE -> Mocha.Mauve
}

private fun ScopeType.descriptionRes(): Int = when (this) {
    ScopeType.HISTOGRAM -> R.string.video_scopes_histogram_description
    ScopeType.WAVEFORM -> R.string.video_scopes_waveform_description
    ScopeType.VECTORSCOPE -> R.string.video_scopes_vectorscope_description
}

sealed class ScopeData

data class HistogramData(
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    val luma: IntArray
) : ScopeData()

data class WaveformData(
    val columns: List<WaveformColumn>
) : ScopeData()

data class WaveformColumn(
    val redMin: Int,
    val redMax: Int,
    val greenMin: Int,
    val greenMax: Int,
    val blueMin: Int,
    val blueMax: Int
)

data class VectorscopeData(
    val points: List<VectorscopePoint>
) : ScopeData()

data class VectorscopePoint(val cb: Float, val cr: Float, val intensity: Float)

private fun analyzeBitmap(bitmap: Bitmap, type: ScopeType): ScopeData {
    val scale = minOf(1f, 100f / maxOf(bitmap.width, bitmap.height))
    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)
    if (scaled != bitmap) scaled.recycle()

    return when (type) {
        ScopeType.HISTOGRAM -> analyzeHistogram(pixels)
        ScopeType.WAVEFORM -> analyzeWaveform(pixels, width, height)
        ScopeType.VECTORSCOPE -> analyzeVectorscope(pixels)
    }
}

private fun analyzeHistogram(pixels: IntArray): HistogramData {
    val red = IntArray(256)
    val green = IntArray(256)
    val blue = IntArray(256)
    val luma = IntArray(256)

    for (pixel in pixels) {
        val pixelRed = (pixel shr 16) and 0xFF
        val pixelGreen = (pixel shr 8) and 0xFF
        val pixelBlue = pixel and 0xFF
        red[pixelRed]++
        green[pixelGreen]++
        blue[pixelBlue]++
        luma[((pixelRed * 299 + pixelGreen * 587 + pixelBlue * 114) / 1000).coerceIn(0, 255)]++
    }
    return HistogramData(red, green, blue, luma)
}

private fun analyzeWaveform(pixels: IntArray, width: Int, height: Int): WaveformData {
    val columns = mutableListOf<WaveformColumn>()
    val step = maxOf(1, width / 100)

    for (x in 0 until width step step) {
        var redMin = 255
        var redMax = 0
        var greenMin = 255
        var greenMax = 0
        var blueMin = 255
        var blueMax = 0

        for (y in 0 until height) {
            val pixel = pixels[y * width + x]
            val pixelRed = (pixel shr 16) and 0xFF
            val pixelGreen = (pixel shr 8) and 0xFF
            val pixelBlue = pixel and 0xFF
            redMin = minOf(redMin, pixelRed)
            redMax = maxOf(redMax, pixelRed)
            greenMin = minOf(greenMin, pixelGreen)
            greenMax = maxOf(greenMax, pixelGreen)
            blueMin = minOf(blueMin, pixelBlue)
            blueMax = maxOf(blueMax, pixelBlue)
        }
        columns.add(WaveformColumn(redMin, redMax, greenMin, greenMax, blueMin, blueMax))
    }
    return WaveformData(columns)
}

private fun analyzeVectorscope(pixels: IntArray): VectorscopeData {
    val points = mutableListOf<VectorscopePoint>()
    val step = maxOf(1, pixels.size / 5000)

    for (index in pixels.indices step step) {
        val pixel = pixels[index]
        val red = ((pixel shr 16) and 0xFF) / 255f
        val green = ((pixel shr 8) and 0xFF) / 255f
        val blue = (pixel and 0xFF) / 255f

        val luma = 0.299f * red + 0.587f * green + 0.114f * blue
        val cb = (blue - luma) * 0.565f
        val cr = (red - luma) * 0.713f

        points.add(VectorscopePoint(cb, cr, luma))
    }
    return VectorscopeData(points)
}

private fun DrawScope.drawHistogram(data: HistogramData?) {
    if (data == null) return
    val width = size.width
    val height = size.height

    for (index in 1..3) {
        val y = height * index / 4f
        drawLine(ScopeGrid, Offset(0f, y), Offset(width, y), 0.5f)
    }

    val maxRed = data.red.max().coerceAtLeast(1).toFloat()
    val maxGreen = data.green.max().coerceAtLeast(1).toFloat()
    val maxBlue = data.blue.max().coerceAtLeast(1).toFloat()
    val maxLuma = data.luma.max().coerceAtLeast(1).toFloat()
    val barWidth = width / 256f

    for (index in 0 until 256) {
        val x = index * barWidth
        val lumaHeight = (data.luma[index] / maxLuma) * height
        drawRect(
            ScopeWhite.copy(alpha = 0.15f),
            Offset(x, height - lumaHeight),
            androidx.compose.ui.geometry.Size(barWidth, lumaHeight)
        )
    }

    for (index in 0 until 256) {
        val x = index * barWidth
        val redHeight = (data.red[index] / maxRed) * height * 0.8f
        val greenHeight = (data.green[index] / maxGreen) * height * 0.8f
        val blueHeight = (data.blue[index] / maxBlue) * height * 0.8f

        drawRect(
            ScopeRed.copy(alpha = 0.4f),
            Offset(x, height - redHeight),
            androidx.compose.ui.geometry.Size(barWidth, redHeight)
        )
        drawRect(
            ScopeGreen.copy(alpha = 0.4f),
            Offset(x, height - greenHeight),
            androidx.compose.ui.geometry.Size(barWidth, greenHeight)
        )
        drawRect(
            ScopeBlue.copy(alpha = 0.4f),
            Offset(x, height - blueHeight),
            androidx.compose.ui.geometry.Size(barWidth, blueHeight)
        )
    }
}

private fun DrawScope.drawWaveformScope(data: WaveformData?) {
    if (data == null || data.columns.isEmpty()) return
    val width = size.width
    val height = size.height

    for (index in 0..4) {
        val y = height * index / 4f
        drawLine(ScopeGrid, Offset(0f, y), Offset(width, y), 0.5f)
    }

    val columnWidth = width / data.columns.size
    data.columns.forEachIndexed { index, column ->
        val x = index * columnWidth

        val redTop = (1f - column.redMax / 255f) * height
        val redBottom = (1f - column.redMin / 255f) * height
        drawRect(
            ScopeRed.copy(alpha = 0.5f),
            Offset(x, redTop),
            androidx.compose.ui.geometry.Size(columnWidth, redBottom - redTop)
        )

        val greenTop = (1f - column.greenMax / 255f) * height
        val greenBottom = (1f - column.greenMin / 255f) * height
        drawRect(
            ScopeGreen.copy(alpha = 0.5f),
            Offset(x, greenTop),
            androidx.compose.ui.geometry.Size(columnWidth, greenBottom - greenTop)
        )

        val blueTop = (1f - column.blueMax / 255f) * height
        val blueBottom = (1f - column.blueMin / 255f) * height
        drawRect(
            ScopeBlue.copy(alpha = 0.5f),
            Offset(x, blueTop),
            androidx.compose.ui.geometry.Size(columnWidth, blueBottom - blueTop)
        )
    }
}

private fun DrawScope.drawVectorscope(data: VectorscopeData?) {
    if (data == null) return
    val width = size.width
    val height = size.height
    val centerX = width / 2f
    val centerY = height / 2f
    val radius = min(centerX, centerY) * 0.9f

    drawCircle(ScopeGrid, radius, Offset(centerX, centerY), style = Stroke(1f))
    drawCircle(ScopeGrid, radius * 0.5f, Offset(centerX, centerY), style = Stroke(0.5f))

    drawLine(ScopeGrid, Offset(centerX - radius, centerY), Offset(centerX + radius, centerY), 0.5f)
    drawLine(ScopeGrid, Offset(centerX, centerY - radius), Offset(centerX, centerY + radius), 0.5f)

    val targets = listOf(
        Triple(0.5f, 0.35f, ScopeRed),
        Triple(-0.17f, -0.33f, ScopeGreen),
        Triple(-0.33f, 0.5f, ScopeBlue),
        Triple(-0.5f, -0.35f, Color.Cyan),
        Triple(0.17f, 0.33f, Color.Magenta),
        Triple(0.33f, -0.5f, Color.Yellow)
    )
    targets.forEach { (targetCr, targetCb, color) ->
        val targetX = centerX + targetCb * radius * 2f
        val targetY = centerY - targetCr * radius * 2f
        drawCircle(color.copy(alpha = 0.3f), 4f, Offset(targetX, targetY))
    }

    data.points.forEach { point ->
        val pointX = centerX + point.cb * radius * 2f
        val pointY = centerY - point.cr * radius * 2f
        if (pointX in 0f..width && pointY in 0f..height) {
            drawCircle(
                ScopeWhite.copy(alpha = (0.05f + point.intensity * 0.2f).coerceAtMost(0.3f)),
                1.5f,
                Offset(pointX, pointY)
            )
        }
    }
}
