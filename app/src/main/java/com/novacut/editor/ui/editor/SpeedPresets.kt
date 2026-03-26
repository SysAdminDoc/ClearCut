package com.novacut.editor.ui.editor

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

@Composable
fun SpeedPresetsPanel(
    onPresetSelected: (SpeedCurve) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Text(
                "Speed Presets",
                color = Mocha.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Horizontal scrolling preset cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpeedPresetType.entries.forEach { presetType ->
                SpeedPresetCard(
                    presetType = presetType,
                    onClick = { onPresetSelected(generatePresetCurve(presetType)) }
                )
            }
        }
    }
}

@Composable
private fun SpeedPresetCard(
    presetType: SpeedPresetType,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Mocha.Surface0)
            .clickable(onClick = onClick)
    ) {
        // Mini curve preview
        val curve = remember(presetType) { generatePresetCurve(presetType) }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(8.dp)
        ) {
            val w = size.width
            val h = size.height
            val minSpeed = 0.1f
            val maxSpeed = 4.5f
            val speedRange = maxSpeed - minSpeed

            // 1x reference line
            val refY = (1f - (1f - minSpeed) / speedRange) * h
            drawLine(
                Mocha.Surface2,
                Offset(0f, refY),
                Offset(w, refY),
                strokeWidth = 0.5f
            )

            // Draw the curve
            val path = Path()
            val steps = 100
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val speed = curve.getSpeedAt((t * 10000).toLong(), 10000L)
                val x = t * w
                val y = (1f - (speed - minSpeed) / speedRange) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Mocha.Peach, style = Stroke(2f))

            // Draw control points
            curve.points.forEach { point ->
                val px = point.position * w
                val py = (1f - (point.speed - minSpeed) / speedRange) * h
                drawCircle(Mocha.Peach, 3f, Offset(px, py))
            }
        }

        // Info
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                presetType.displayName,
                color = Mocha.Text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                presetType.description,
                color = Mocha.Subtext0,
                fontSize = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp
            )
        }

        Spacer(Modifier.height(4.dp))
    }
}

fun generatePresetCurve(type: SpeedPresetType): SpeedCurve = when (type) {
    SpeedPresetType.BULLET_TIME -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 1.0f, handleOutY = 0.6f),
            SpeedPoint(0.3f, 0.2f, handleInY = 0.4f, handleOutY = 0.2f),
            SpeedPoint(0.7f, 0.2f, handleInY = 0.2f, handleOutY = 0.4f),
            SpeedPoint(1f, 1.0f, handleInY = 0.6f)
        )
    )
    SpeedPresetType.HERO_TIME -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 0.3f, handleOutY = 0.3f),
            SpeedPoint(0.4f, 0.3f, handleInY = 0.3f, handleOutY = 0.5f),
            SpeedPoint(0.6f, 1.0f, handleInY = 0.7f, handleOutY = 1.0f),
            SpeedPoint(1f, 1.0f, handleInY = 1.0f)
        )
    )
    SpeedPresetType.MONTAGE -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 2.0f, handleOutY = 2.0f),
            SpeedPoint(0.25f, 0.5f, handleInY = 0.5f, handleOutY = 0.5f),
            SpeedPoint(0.5f, 2.0f, handleInY = 2.0f, handleOutY = 2.0f),
            SpeedPoint(0.75f, 0.5f, handleInY = 0.5f, handleOutY = 0.5f),
            SpeedPoint(1f, 2.0f, handleInY = 2.0f)
        )
    )
    SpeedPresetType.JUMP_CUT -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.24f, 1.0f, handleInY = 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.25f, 4.0f, handleInY = 4.0f, handleOutY = 4.0f),
            SpeedPoint(0.49f, 4.0f, handleInY = 4.0f, handleOutY = 4.0f),
            SpeedPoint(0.5f, 1.0f, handleInY = 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.74f, 1.0f, handleInY = 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.75f, 4.0f, handleInY = 4.0f, handleOutY = 4.0f),
            SpeedPoint(1f, 4.0f, handleInY = 4.0f)
        )
    )
    SpeedPresetType.SMOOTH_RAMP_UP -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 0.5f, handleOutY = 0.55f),
            SpeedPoint(0.33f, 0.75f, handleInY = 0.65f, handleOutY = 0.85f),
            SpeedPoint(0.66f, 1.0f, handleInY = 0.9f, handleOutY = 1.3f),
            SpeedPoint(1f, 2.0f, handleInY = 1.6f)
        )
    )
    SpeedPresetType.SMOOTH_RAMP_DOWN -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 2.0f, handleOutY = 1.6f),
            SpeedPoint(0.33f, 1.0f, handleInY = 1.3f, handleOutY = 0.9f),
            SpeedPoint(0.66f, 0.75f, handleInY = 0.85f, handleOutY = 0.65f),
            SpeedPoint(1f, 0.5f, handleInY = 0.55f)
        )
    )
    SpeedPresetType.PULSE -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 1.0f, handleOutY = 0.8f),
            SpeedPoint(0.25f, 0.5f, handleInY = 0.7f, handleOutY = 0.8f),
            SpeedPoint(0.5f, 1.5f, handleInY = 1.2f, handleOutY = 1.2f),
            SpeedPoint(0.75f, 0.5f, handleInY = 0.8f, handleOutY = 0.7f),
            SpeedPoint(1f, 1.0f, handleInY = 0.8f)
        )
    )
    SpeedPresetType.FLASH -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.4f, 1.0f, handleInY = 1.0f, handleOutY = 2.0f),
            SpeedPoint(0.55f, 4.0f, handleInY = 3.0f, handleOutY = 3.0f),
            SpeedPoint(0.7f, 1.0f, handleInY = 2.0f, handleOutY = 1.0f),
            SpeedPoint(1f, 1.0f, handleInY = 1.0f)
        )
    )
    SpeedPresetType.DREAMY -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 0.5f, handleOutY = 0.55f),
            SpeedPoint(0.25f, 0.7f, handleInY = 0.65f, handleOutY = 0.65f),
            SpeedPoint(0.5f, 0.5f, handleInY = 0.55f, handleOutY = 0.55f),
            SpeedPoint(0.75f, 0.7f, handleInY = 0.65f, handleOutY = 0.65f),
            SpeedPoint(1f, 0.5f, handleInY = 0.55f)
        )
    )
    SpeedPresetType.REWIND -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 2.0f, handleOutY = 1.8f),
            SpeedPoint(0.33f, 1.5f, handleInY = 1.6f, handleOutY = 1.3f),
            SpeedPoint(0.66f, 1.0f, handleInY = 1.1f, handleOutY = 0.8f),
            SpeedPoint(1f, 0.5f, handleInY = 0.6f)
        )
    )
    // Time Freeze: speed drops to near-zero at 50%, holds briefly, then resumes
    SpeedPresetType.TIME_FREEZE -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.4f, 1.0f, handleInY = 1.0f, handleOutY = 0.3f),
            SpeedPoint(0.48f, 0.01f, handleInY = 0.01f, handleOutY = 0.01f),
            SpeedPoint(0.52f, 0.01f, handleInY = 0.01f, handleOutY = 0.01f),
            SpeedPoint(0.6f, 1.0f, handleInY = 0.3f, handleOutY = 1.0f),
            SpeedPoint(1f, 1.0f, handleInY = 1.0f)
        )
    )
    // Film Reel: alternating 2x and 1x speed to simulate 24fps stutter
    SpeedPresetType.FILM_REEL -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 2.0f, handleOutY = 2.0f),
            SpeedPoint(0.12f, 2.0f, handleInY = 2.0f, handleOutY = 2.0f),
            SpeedPoint(0.13f, 1.0f, handleInY = 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.25f, 1.0f, handleInY = 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.26f, 2.0f, handleInY = 2.0f, handleOutY = 2.0f),
            SpeedPoint(0.38f, 2.0f, handleInY = 2.0f, handleOutY = 2.0f),
            SpeedPoint(0.39f, 1.0f, handleInY = 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.5f, 1.0f, handleInY = 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.51f, 2.0f, handleInY = 2.0f, handleOutY = 2.0f),
            SpeedPoint(0.63f, 2.0f, handleInY = 2.0f, handleOutY = 2.0f),
            SpeedPoint(0.64f, 1.0f, handleInY = 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.75f, 1.0f, handleInY = 1.0f, handleOutY = 1.0f),
            SpeedPoint(0.76f, 2.0f, handleInY = 2.0f, handleOutY = 2.0f),
            SpeedPoint(0.88f, 2.0f, handleInY = 2.0f, handleOutY = 2.0f),
            SpeedPoint(0.89f, 1.0f, handleInY = 1.0f, handleOutY = 1.0f),
            SpeedPoint(1f, 1.0f, handleInY = 1.0f)
        )
    )
    // Heartbeat: repeating 1.5x → 0.5x → 1.5x → 0.5x pattern
    SpeedPresetType.HEARTBEAT -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 1.5f, handleOutY = 1.3f),
            SpeedPoint(0.25f, 0.5f, handleInY = 0.7f, handleOutY = 0.7f),
            SpeedPoint(0.5f, 1.5f, handleInY = 1.3f, handleOutY = 1.3f),
            SpeedPoint(0.75f, 0.5f, handleInY = 0.7f, handleOutY = 0.7f),
            SpeedPoint(1f, 1.5f, handleInY = 1.3f)
        )
    )
    // Crescendo: exponential ramp from 0.5x to 3x
    SpeedPresetType.CRESCENDO -> SpeedCurve(
        listOf(
            SpeedPoint(0f, 0.5f, handleOutY = 0.5f),
            SpeedPoint(0.25f, 0.6f, handleInY = 0.55f, handleOutY = 0.7f),
            SpeedPoint(0.5f, 0.9f, handleInY = 0.8f, handleOutY = 1.2f),
            SpeedPoint(0.75f, 1.8f, handleInY = 1.5f, handleOutY = 2.3f),
            SpeedPoint(1f, 3.0f, handleInY = 2.6f)
        )
    )
}
