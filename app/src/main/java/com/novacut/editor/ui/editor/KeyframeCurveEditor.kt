package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha
import kotlin.math.abs


private val PROPERTY_COLORS = mapOf(
    KeyframeProperty.POSITION_X to Mocha.Red,
    KeyframeProperty.POSITION_Y to Mocha.Green,
    KeyframeProperty.SCALE_X to Mocha.Blue,
    KeyframeProperty.SCALE_Y to Mocha.Teal,
    KeyframeProperty.ROTATION to Mocha.Yellow,
    KeyframeProperty.OPACITY to Mocha.Mauve,
    KeyframeProperty.VOLUME to Mocha.Peach,
    KeyframeProperty.ANCHOR_X to Mocha.Red.copy(alpha = 0.5f),
    KeyframeProperty.ANCHOR_Y to Mocha.Green.copy(alpha = 0.5f),
    KeyframeProperty.MASK_FEATHER to Mocha.Teal.copy(alpha = 0.5f),
    KeyframeProperty.MASK_EXPANSION to Mocha.Blue.copy(alpha = 0.5f),
    KeyframeProperty.MASK_OPACITY to Mocha.Mauve.copy(alpha = 0.5f)
)

@Composable
fun KeyframeCurveEditor(
    keyframes: List<Keyframe>,
    clipDurationMs: Long,
    playheadMs: Long,
    activeProperties: Set<KeyframeProperty>,
    onKeyframesChanged: (List<Keyframe>) -> Unit,
    onPropertyToggled: (KeyframeProperty) -> Unit,
    onAddKeyframe: (KeyframeProperty, Long, Float) -> Unit,
    onDeleteKeyframe: (Keyframe) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedKeyframe by remember { mutableStateOf<Keyframe?>(null) }
    var dragKeyframeIndex by remember { mutableIntStateOf(-1) }

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
            Text(stringResource(R.string.panel_keyframes_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row {
                // Preset button
                var showPresets by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showPresets = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.AutoAwesome, stringResource(R.string.cd_keyframe_presets), tint = Mocha.Yellow, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showPresets, onDismissRequest = { showPresets = false }) {
                        listOf(
                            stringResource(R.string.keyframe_preset_ken_burns) to "kenburns",
                            stringResource(R.string.keyframe_preset_fade_in) to "fadein",
                            stringResource(R.string.keyframe_preset_fade_out) to "fadeout",
                            stringResource(R.string.keyframe_preset_pulse) to "pulse",
                            stringResource(R.string.keyframe_preset_shake) to "shake",
                            stringResource(R.string.keyframe_preset_drift) to "drift",
                            stringResource(R.string.keyframe_preset_spin) to "spin",
                            stringResource(R.string.keyframe_preset_zoom) to "zoominout"
                        ).forEach { (label, id) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 13.sp) },
                                onClick = {
                                    val preset = when (id) {
                                        "kenburns" -> com.novacut.editor.engine.KeyframeEngine.createKenBurnsKeyframes(clipDurationMs)
                                        "fadein" -> com.novacut.editor.engine.KeyframeEngine.createFadeIn()
                                        "fadeout" -> com.novacut.editor.engine.KeyframeEngine.createFadeOut(clipDurationMs)
                                        "pulse" -> com.novacut.editor.engine.KeyframeEngine.createPulse(clipDurationMs)
                                        "shake" -> com.novacut.editor.engine.KeyframeEngine.createShake(clipDurationMs)
                                        "drift" -> com.novacut.editor.engine.KeyframeEngine.createDrift(clipDurationMs)
                                        "spin" -> com.novacut.editor.engine.KeyframeEngine.createSpin360(clipDurationMs)
                                        "zoominout" -> com.novacut.editor.engine.KeyframeEngine.createZoomInOut(clipDurationMs)
                                        else -> emptyList()
                                    }
                                    onKeyframesChanged(keyframes + preset)
                                    showPresets = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Property toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val coreProperties = listOf(
                KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y,
                KeyframeProperty.SCALE_X, KeyframeProperty.SCALE_Y,
                KeyframeProperty.ROTATION, KeyframeProperty.OPACITY,
                KeyframeProperty.VOLUME
            )
            coreProperties.forEach { prop ->
                val active = prop in activeProperties
                val color = PROPERTY_COLORS[prop] ?: Mocha.Text
                FilterChip(
                    selected = active,
                    onClick = { onPropertyToggled(prop) },
                    label = { Text(prop.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = color.copy(alpha = 0.2f),
                        selectedLabelColor = color,
                        labelColor = Mocha.Subtext0
                    ),
                    leadingIcon = if (active) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color, CircleShape)
                            )
                        }
                    } else null
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Curve canvas
        CurveCanvas(
            keyframes = keyframes,
            clipDurationMs = clipDurationMs,
            playheadMs = playheadMs,
            activeProperties = activeProperties,
            selectedKeyframe = selectedKeyframe,
            onKeyframeSelected = { selectedKeyframe = it },
            onKeyframeMoved = { kf, newTime, newValue ->
                val updated = keyframes.toMutableList()
                val idx = updated.indexOf(kf)
                if (idx >= 0) {
                    updated[idx] = kf.copy(
                        timeOffsetMs = newTime.coerceIn(0L, clipDurationMs),
                        value = newValue
                    )
                    onKeyframesChanged(updated)
                }
            },
            onAddKeyframe = { prop, time, value -> onAddKeyframe(prop, time, value) },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Mocha.Surface0, RoundedCornerShape(8.dp))
        )

        // Selected keyframe controls
        selectedKeyframe?.let { kf ->
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Mocha.Surface0, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${kf.property.name}: %.2f".format(kf.value),
                        color = PROPERTY_COLORS[kf.property] ?: Mocha.Text,
                        fontSize = 12.sp
                    )
                    Text(
                        "@ ${kf.timeOffsetMs}ms",
                        color = Mocha.Subtext0,
                        fontSize = 10.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Interpolation type selector
                    KeyframeInterpolation.entries.forEach { interp ->
                        val selected = kf.interpolation == interp
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) Mocha.Mauve.copy(alpha = 0.2f) else Mocha.Surface1)
                                .clickable {
                                    val updated = keyframes.toMutableList()
                                    val idx = updated.indexOf(kf)
                                    if (idx >= 0) {
                                        updated[idx] = kf.copy(interpolation = interp)
                                        onKeyframesChanged(updated)
                                        selectedKeyframe = updated[idx]
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                interp.name.take(3),
                                color = if (selected) Mocha.Mauve else Mocha.Subtext0,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Delete keyframe
                    IconButton(
                        onClick = {
                            onDeleteKeyframe(kf)
                            selectedKeyframe = null
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, stringResource(R.string.cd_keyframe_delete), tint = Mocha.Red, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CurveCanvas(
    keyframes: List<Keyframe>,
    clipDurationMs: Long,
    playheadMs: Long,
    activeProperties: Set<KeyframeProperty>,
    selectedKeyframe: Keyframe?,
    onKeyframeSelected: (Keyframe?) -> Unit,
    onKeyframeMoved: (Keyframe, Long, Float) -> Unit,
    onAddKeyframe: (KeyframeProperty, Long, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (clipDurationMs <= 0L) return

    Canvas(
        modifier = modifier
            .pointerInput(keyframes, activeProperties) {
                detectTapGestures(
                    onTap = { offset ->
                        // Find nearest keyframe
                        val hitRadius = 20f
                        var nearest: Keyframe? = null
                        var nearestDist = Float.MAX_VALUE

                        for (kf in keyframes) {
                            if (kf.property !in activeProperties) continue
                            val x = (kf.timeOffsetMs.toFloat() / clipDurationMs) * size.width
                            val range = getPropertyRange(kf.property)
                            val y = (1f - (kf.value - range.first) / (range.second - range.first)) * size.height
                            val dist = kotlin.math.sqrt(
                                (offset.x - x) * (offset.x - x) + (offset.y - y) * (offset.y - y)
                            )
                            if (dist < hitRadius && dist < nearestDist) {
                                nearest = kf
                                nearestDist = dist
                            }
                        }

                        if (nearest != null) {
                            onKeyframeSelected(nearest)
                        } else {
                            // Single tap on empty space just deselects
                            onKeyframeSelected(null)
                        }
                    },
                    onDoubleTap = { offset ->
                        val firstActive = activeProperties.firstOrNull() ?: return@detectTapGestures
                        val time = (offset.x / size.width * clipDurationMs).toLong()
                        val range = getPropertyRange(firstActive)
                        val value = range.first + (1f - offset.y / size.height) * (range.second - range.first)
                        onAddKeyframe(firstActive, time, value)
                    }
                )
            }
            .pointerInput(keyframes, activeProperties) {
                detectDragGestures { change, _ ->
                    // Move selected keyframe
                    val kf = selectedKeyframe ?: return@detectDragGestures
                    val time = (change.position.x / size.width * clipDurationMs).toLong()
                    val range = getPropertyRange(kf.property)
                    val value = range.first + (1f - change.position.y / size.height) * (range.second - range.first)
                    onKeyframeMoved(kf, time, value.coerceIn(range.first, range.second))
                }
            }
    ) {
        val w = size.width
        val h = size.height

        // Grid
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(Color(0xFF45475A), Offset(0f, y), Offset(w, y), 0.5f)
        }
        for (i in 1..9) {
            val x = w * i / 10f
            drawLine(Color(0xFF45475A), Offset(x, 0f), Offset(x, h), 0.5f)
        }

        // Draw curves per active property
        activeProperties.forEach { prop ->
            val propKfs = keyframes.filter { it.property == prop }.sortedBy { it.timeOffsetMs }
            if (propKfs.size < 2) return@forEach

            val color = PROPERTY_COLORS[prop] ?: Mocha.Text
            val range = getPropertyRange(prop)
            val rangeSpan = range.second - range.first

            val path = Path()
            val steps = 200
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val timeMs = (t * clipDurationMs).toLong()
                val value = com.novacut.editor.engine.KeyframeEngine.getValueAt(propKfs, prop, timeMs) ?: continue
                val x = t * w
                val y = (1f - (value - range.first) / rangeSpan) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(2f))
        }

        // Draw keyframe diamonds
        keyframes.forEach { kf ->
            if (kf.property !in activeProperties) return@forEach
            val color = PROPERTY_COLORS[kf.property] ?: Mocha.Text
            val range = getPropertyRange(kf.property)
            val x = (kf.timeOffsetMs.toFloat() / clipDurationMs) * w
            val y = (1f - (kf.value - range.first) / (range.second - range.first)) * h

            val isSelected = kf == selectedKeyframe

            // Diamond shape
            val diamondPath = Path().apply {
                moveTo(x, y - 6f)
                lineTo(x + 6f, y)
                lineTo(x, y + 6f)
                lineTo(x - 6f, y)
                close()
            }
            drawPath(diamondPath, if (isSelected) Color.White else color)
            if (isSelected) {
                drawPath(diamondPath, color, style = Stroke(2f))
            }
        }

        // Playhead
        val playheadX = (playheadMs.toFloat() / clipDurationMs) * w
        drawLine(Color(0xFFF38BA8), Offset(playheadX, 0f), Offset(playheadX, h), 2f)
    }
}

private fun getPropertyRange(property: KeyframeProperty): Pair<Float, Float> {
    return when (property) {
        KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y -> -1f to 1f
        KeyframeProperty.SCALE_X, KeyframeProperty.SCALE_Y -> 0.1f to 5f
        KeyframeProperty.ROTATION -> -360f to 360f
        KeyframeProperty.OPACITY, KeyframeProperty.MASK_OPACITY -> 0f to 1f
        KeyframeProperty.VOLUME -> 0f to 2f
        KeyframeProperty.ANCHOR_X, KeyframeProperty.ANCHOR_Y -> 0f to 1f
        KeyframeProperty.MASK_FEATHER -> 0f to 100f
        KeyframeProperty.MASK_EXPANSION -> -50f to 50f
    }
}
