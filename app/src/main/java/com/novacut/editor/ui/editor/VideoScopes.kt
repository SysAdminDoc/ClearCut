package com.novacut.editor.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

import com.novacut.editor.ui.theme.Mocha

// Scope-specific display colors (not theme colors)
private val ScopeBg = Color(0xCC111111)
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

/**
 * Floating video scopes overlay for color grading.
 * Analyzes the current frame and displays histogram, waveform, or vectorscope.
 */
@Composable
fun VideoScopesOverlay(
    frameBitmap: Bitmap?,
    activeScope: ScopeType,
    onScopeChanged: (ScopeType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scopeData by produceState<ScopeData?>(initialValue = null, key1 = frameBitmap, key2 = activeScope) {
        val bitmap = frameBitmap
        value = if (bitmap != null && !bitmap.isRecycled) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                analyzeBitmap(bitmap, activeScope)
            }
        } else null
    }

    Column(
        modifier = modifier
            .width(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ScopeBg)
            .padding(6.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scope type tabs
            ScopeType.entries.forEach { scope ->
                val selected = scope == activeScope
                Text(
                    scope.label.take(4),
                    color = if (selected) Mocha.Mauve else Mocha.Subtext0.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clickable { onScopeChanged(scope) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Icon(
                Icons.Default.Close, "Close",
                tint = Mocha.Subtext0.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onClose)
            )
        }

        Spacer(Modifier.height(4.dp))

        // Scope canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0A0A0A))
        ) {
            when (activeScope) {
                ScopeType.HISTOGRAM -> drawHistogram(scopeData as? HistogramData)
                ScopeType.WAVEFORM -> drawWaveformScope(scopeData as? WaveformData)
                ScopeType.VECTORSCOPE -> drawVectorscope(scopeData as? VectorscopeData)
            }
        }
    }
}

// --- Scope data types ---

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
    val redMin: Int, val redMax: Int,
    val greenMin: Int, val greenMax: Int,
    val blueMin: Int, val blueMax: Int
)

data class VectorscopeData(
    val points: List<VectorscopePoint>
) : ScopeData()

data class VectorscopePoint(val cb: Float, val cr: Float, val intensity: Float)

// --- Analysis ---

private fun analyzeBitmap(bitmap: Bitmap, type: ScopeType): ScopeData {
    // Downsample for performance
    val scale = minOf(1f, 100f / maxOf(bitmap.width, bitmap.height))
    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
    val pixels = IntArray(w * h)
    scaled.getPixels(pixels, 0, w, 0, 0, w, h)
    if (scaled != bitmap) scaled.recycle()

    return when (type) {
        ScopeType.HISTOGRAM -> analyzeHistogram(pixels)
        ScopeType.WAVEFORM -> analyzeWaveform(pixels, w, h)
        ScopeType.VECTORSCOPE -> analyzeVectorscope(pixels)
    }
}

private fun analyzeHistogram(pixels: IntArray): HistogramData {
    val r = IntArray(256)
    val g = IntArray(256)
    val b = IntArray(256)
    val l = IntArray(256)

    for (pixel in pixels) {
        val pr = (pixel shr 16) and 0xFF
        val pg = (pixel shr 8) and 0xFF
        val pb = pixel and 0xFF
        r[pr]++
        g[pg]++
        b[pb]++
        l[((pr * 299 + pg * 587 + pb * 114) / 1000).coerceIn(0, 255)]++
    }
    return HistogramData(r, g, b, l)
}

private fun analyzeWaveform(pixels: IntArray, w: Int, h: Int): WaveformData {
    val columns = mutableListOf<WaveformColumn>()
    val step = maxOf(1, w / 100)

    for (x in 0 until w step step) {
        var rMin = 255; var rMax = 0
        var gMin = 255; var gMax = 0
        var bMin = 255; var bMax = 0

        for (y in 0 until h) {
            val pixel = pixels[y * w + x]
            val pr = (pixel shr 16) and 0xFF
            val pg = (pixel shr 8) and 0xFF
            val pb = pixel and 0xFF
            rMin = minOf(rMin, pr); rMax = maxOf(rMax, pr)
            gMin = minOf(gMin, pg); gMax = maxOf(gMax, pg)
            bMin = minOf(bMin, pb); bMax = maxOf(bMax, pb)
        }
        columns.add(WaveformColumn(rMin, rMax, gMin, gMax, bMin, bMax))
    }
    return WaveformData(columns)
}

private fun analyzeVectorscope(pixels: IntArray): VectorscopeData {
    val points = mutableListOf<VectorscopePoint>()
    val step = maxOf(1, pixels.size / 5000) // Limit to ~5000 points

    for (i in pixels.indices step step) {
        val pixel = pixels[i]
        val r = ((pixel shr 16) and 0xFF) / 255f
        val g = ((pixel shr 8) and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f

        // YCbCr conversion
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        val cb = (b - y) * 0.565f
        val cr = (r - y) * 0.713f

        points.add(VectorscopePoint(cb, cr, y))
    }
    return VectorscopeData(points)
}

/**
 * GPU-accelerated scope analysis (future improvement for ES 3.1+ devices).
 *
 * Waveform compute shader approach:
 *   layout(local_size_x = 16, local_size_y = 16) in;
 *   layout(binding = 0) readonly buffer InputImage { vec4 pixels[]; };
 *   layout(binding = 1) buffer WaveformBins { uint bins[]; };
 *   void main() {
 *     uvec2 pos = gl_GlobalInvocationID.xy;
 *     vec4 pixel = pixels[pos.y * width + pos.x];
 *     float luma = 0.2126 * pixel.r + 0.7152 * pixel.g + 0.0722 * pixel.b;
 *     uint bin = uint(luma * 255.0);
 *     uint col = pos.x * scopeWidth / width;
 *     atomicAdd(bins[col * 256 + bin], 1u);
 *   }
 *
 * Vectorscope compute shader:
 *   float Cb = -0.1687 * pixel.r - 0.3313 * pixel.g + 0.5 * pixel.b + 0.5;
 *   float Cr = 0.5 * pixel.r - 0.4187 * pixel.g - 0.0813 * pixel.b + 0.5;
 *   uint x = uint(Cb * scopeSize);
 *   uint y = uint(Cr * scopeSize);
 *   atomicAdd(bins[y * scopeSize + x], 1u);
 *
 * Benefits: Real-time scopes during playback (current CPU approach blocks composition).
 * Requires: OpenGL ES 3.1+ (SSBO + compute shaders), available on most devices since 2015.
 */

// --- Drawing ---

private fun DrawScope.drawHistogram(data: HistogramData?) {
    if (data == null) return
    val w = size.width
    val h = size.height

    // Grid lines
    for (i in 1..3) {
        val y = h * i / 4f
        drawLine(ScopeGrid, Offset(0f, y), Offset(w, y), 0.5f)
    }

    val maxR = data.red.max().coerceAtLeast(1).toFloat()
    val maxG = data.green.max().coerceAtLeast(1).toFloat()
    val maxB = data.blue.max().coerceAtLeast(1).toFloat()
    val maxL = data.luma.max().coerceAtLeast(1).toFloat()
    val barW = w / 256f

    // Draw luma first (background)
    for (i in 0 until 256) {
        val x = i * barW
        val lumaH = (data.luma[i] / maxL) * h
        drawRect(ScopeWhite.copy(alpha = 0.15f), Offset(x, h - lumaH), androidx.compose.ui.geometry.Size(barW, lumaH))
    }

    // RGB overlay
    for (i in 0 until 256) {
        val x = i * barW
        val rh = (data.red[i] / maxR) * h * 0.8f
        val gh = (data.green[i] / maxG) * h * 0.8f
        val bh = (data.blue[i] / maxB) * h * 0.8f

        drawRect(ScopeRed.copy(alpha = 0.4f), Offset(x, h - rh), androidx.compose.ui.geometry.Size(barW, rh))
        drawRect(ScopeGreen.copy(alpha = 0.4f), Offset(x, h - gh), androidx.compose.ui.geometry.Size(barW, gh))
        drawRect(ScopeBlue.copy(alpha = 0.4f), Offset(x, h - bh), androidx.compose.ui.geometry.Size(barW, bh))
    }
}

private fun DrawScope.drawWaveformScope(data: WaveformData?) {
    if (data == null || data.columns.isEmpty()) return
    val w = size.width
    val h = size.height

    // Grid
    for (i in 0..4) {
        val y = h * i / 4f
        drawLine(ScopeGrid, Offset(0f, y), Offset(w, y), 0.5f)
    }

    val colW = w / data.columns.size
    data.columns.forEachIndexed { i, col ->
        val x = i * colW

        // Red
        val rTop = (1f - col.redMax / 255f) * h
        val rBot = (1f - col.redMin / 255f) * h
        drawRect(ScopeRed.copy(alpha = 0.5f), Offset(x, rTop), androidx.compose.ui.geometry.Size(colW, rBot - rTop))

        // Green
        val gTop = (1f - col.greenMax / 255f) * h
        val gBot = (1f - col.greenMin / 255f) * h
        drawRect(ScopeGreen.copy(alpha = 0.5f), Offset(x, gTop), androidx.compose.ui.geometry.Size(colW, gBot - gTop))

        // Blue
        val bTop = (1f - col.blueMax / 255f) * h
        val bBot = (1f - col.blueMin / 255f) * h
        drawRect(ScopeBlue.copy(alpha = 0.5f), Offset(x, bTop), androidx.compose.ui.geometry.Size(colW, bBot - bTop))
    }
}

private fun DrawScope.drawVectorscope(data: VectorscopeData?) {
    if (data == null) return
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val radius = minOf(cx, cy) * 0.9f

    // Circle outline
    drawCircle(ScopeGrid, radius, Offset(cx, cy), style = Stroke(1f))
    drawCircle(ScopeGrid, radius * 0.5f, Offset(cx, cy), style = Stroke(0.5f))

    // Crosshair
    drawLine(ScopeGrid, Offset(cx - radius, cy), Offset(cx + radius, cy), 0.5f)
    drawLine(ScopeGrid, Offset(cx, cy - radius), Offset(cx, cy + radius), 0.5f)

    // Color target markers (R, G, B, C, M, Y at standard positions)
    val targets = listOf(
        Triple(0.5f, 0.35f, ScopeRed),      // Red
        Triple(-0.17f, -0.33f, ScopeGreen),  // Green
        Triple(-0.33f, 0.5f, ScopeBlue),     // Blue
        Triple(-0.5f, -0.35f, Color.Cyan),   // Cyan
        Triple(0.17f, 0.33f, Color.Magenta), // Magenta
        Triple(0.33f, -0.5f, Color.Yellow),  // Yellow
    )
    targets.forEach { (tcr, tcb, color) ->
        val tx = cx + tcb * radius * 2f
        val ty = cy - tcr * radius * 2f
        drawCircle(color.copy(alpha = 0.3f), 4f, Offset(tx, ty))
    }

    // Plot data points
    data.points.forEach { point ->
        val px = cx + point.cb * radius * 2f
        val py = cy - point.cr * radius * 2f
        if (px in 0f..w && py in 0f..h) {
            drawCircle(
                ScopeWhite.copy(alpha = (0.05f + point.intensity * 0.2f).coerceAtMost(0.3f)),
                1.5f,
                Offset(px, py)
            )
        }
    }
}
