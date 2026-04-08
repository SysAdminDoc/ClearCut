package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

@Composable
fun SpeedPresetsPanel(
    onPresetSelected: (SpeedCurve) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = remember {
        speedPresetSections()
    }

    PremiumEditorPanel(
        title = "Speed Presets",
        subtitle = "Shape tempo, impact, and rhythm with reusable speed curves instead of rebuilding them point by point.",
        icon = Icons.Default.Speed,
        accent = Mocha.Peach,
        onClose = onClose,
        modifier = modifier.heightIn(max = 560.dp),
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Peach) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = "${SpeedPresetType.entries.size} presets",
                    accent = Mocha.Peach
                )
                PremiumPanelPill(
                    text = "Reusable curves",
                    accent = Mocha.Sapphire
                )
                PremiumPanelPill(
                    text = "Clip mode",
                    accent = Mocha.Green
                )
            }

            Text(
                text = "Speed language",
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Preset curves are best when you want a repeatable editorial feel: hero slow motion, rhythmic pulses, stutters, or bold fast-forward beats.",
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = section.accent) {
                Text(
                    text = section.title,
                    color = section.accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = section.subtitle,
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(section.presets) { presetType ->
                        SpeedPresetCard(
                            presetType = presetType,
                            accent = section.accent,
                            onClick = { onPresetSelected(generatePresetCurve(presetType)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedPresetCard(
    presetType: SpeedPresetType,
    accent: Color,
    onClick: () -> Unit
) {
    val curve = remember(presetType) { generatePresetCurve(presetType) }
    val minMax = remember(curve) {
        curve.points.map { it.speed }.let { speeds ->
            (speeds.minOrNull() ?: 1f) to (speeds.maxOrNull() ?: 1f)
        }
    }

    Surface(
        modifier = Modifier.width(164.dp),
        onClick = onClick,
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = accent.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Mocha.CardStroke)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .padding(10.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val minSpeed = 0.1f
                    val maxSpeed = 4.5f
                    val speedRange = maxSpeed - minSpeed

                    val referenceY = (1f - (1f - minSpeed) / speedRange) * height
                    drawLine(
                        color = Mocha.Surface2,
                        start = Offset(0f, referenceY),
                        end = Offset(width, referenceY),
                        strokeWidth = 1f
                    )

                    val path = Path()
                    val steps = 100
                    for (index in 0..steps) {
                        val t = index.toFloat() / steps
                        val speed = curve.getSpeedAt((t * 10000).toLong(), 10000L)
                        val x = t * width
                        val y = (1f - (speed - minSpeed) / speedRange) * height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path = path, color = accent, style = Stroke(3f))

                    curve.points.forEach { point ->
                        val px = point.position * width
                        val py = (1f - (point.speed - minSpeed) / speedRange) * height
                        drawCircle(
                            color = accent,
                            radius = 3.8f,
                            center = Offset(px, py)
                        )
                    }
                }
            }

            Text(
                text = presetType.displayName,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = presetType.description,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall,
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumPanelPill(
                    text = "${formatSpeed(minMax.first)}-${formatSpeed(minMax.second)}",
                    accent = accent
                )
                PremiumPanelPill(
                    text = speedPresetFeel(presetType),
                    accent = Mocha.Sky
                )
            }
        }
    }
}

private data class SpeedPresetSection(
    val title: String,
    val subtitle: String,
    val accent: Color,
    val presets: List<SpeedPresetType>
)

private fun speedPresetSections(): List<SpeedPresetSection> = listOf(
    SpeedPresetSection(
        title = "Cinematic ramps",
        subtitle = "Use these when you want entrance, release, or crescendo moments to feel composed and deliberate.",
        accent = Mocha.Peach,
        presets = listOf(
            SpeedPresetType.BULLET_TIME,
            SpeedPresetType.HERO_TIME,
            SpeedPresetType.SMOOTH_RAMP_UP,
            SpeedPresetType.SMOOTH_RAMP_DOWN,
            SpeedPresetType.CRESCENDO,
            SpeedPresetType.DREAMY
        )
    ),
    SpeedPresetSection(
        title = "Rhythm and pulse",
        subtitle = "Great for music-driven edits, montage pacing, and beats that need a more graphic editorial pattern.",
        accent = Mocha.Mauve,
        presets = listOf(
            SpeedPresetType.MONTAGE,
            SpeedPresetType.PULSE,
            SpeedPresetType.HEARTBEAT,
            SpeedPresetType.FILM_REEL
        )
    ),
    SpeedPresetSection(
        title = "Punch and disruption",
        subtitle = "Reach for these when the cut needs freeze moments, stutters, flashes, or aggressive tempo changes.",
        accent = Mocha.Sapphire,
        presets = listOf(
            SpeedPresetType.JUMP_CUT,
            SpeedPresetType.FLASH,
            SpeedPresetType.TIME_FREEZE,
            SpeedPresetType.REWIND
        )
    )
)

private fun speedPresetFeel(type: SpeedPresetType): String = when (type) {
    SpeedPresetType.BULLET_TIME -> "Hero"
    SpeedPresetType.HERO_TIME -> "Entrance"
    SpeedPresetType.MONTAGE -> "Rhythm"
    SpeedPresetType.JUMP_CUT -> "Punch"
    SpeedPresetType.SMOOTH_RAMP_UP -> "Lift"
    SpeedPresetType.SMOOTH_RAMP_DOWN -> "Ease"
    SpeedPresetType.PULSE -> "Pulse"
    SpeedPresetType.FLASH -> "Hit"
    SpeedPresetType.DREAMY -> "Float"
    SpeedPresetType.REWIND -> "Retro"
    SpeedPresetType.TIME_FREEZE -> "Freeze"
    SpeedPresetType.FILM_REEL -> "Stutter"
    SpeedPresetType.HEARTBEAT -> "Beat"
    SpeedPresetType.CRESCENDO -> "Build"
}

private fun formatSpeed(speed: Float): String = "%.1fx".format(speed)

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
