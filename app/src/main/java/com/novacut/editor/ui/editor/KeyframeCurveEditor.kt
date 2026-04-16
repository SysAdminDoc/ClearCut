package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeInterpolation
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.ui.theme.Mocha
import java.util.Locale

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

private val CORE_KEYFRAME_PROPERTIES = listOf(
    KeyframeProperty.POSITION_X,
    KeyframeProperty.POSITION_Y,
    KeyframeProperty.SCALE_X,
    KeyframeProperty.SCALE_Y,
    KeyframeProperty.ROTATION,
    KeyframeProperty.OPACITY,
    KeyframeProperty.VOLUME
)

@OptIn(ExperimentalLayoutApi::class)
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
    var showPresets by remember { mutableStateOf(false) }

    LaunchedEffect(keyframes) {
        selectedKeyframe = selectedKeyframe?.takeIf { current ->
            keyframes.any { it == current }
        }
    }

    val selectionAccent = selectedKeyframe?.let { PROPERTY_COLORS[it.property] } ?: Mocha.Mauve
    val activeKeyframeCount = keyframes.count { it.property in activeProperties }
    val summaryBody = when {
        activeProperties.isEmpty() -> stringResource(R.string.panel_keyframes_summary_empty)
        selectedKeyframe != null -> stringResource(
            R.string.panel_keyframes_summary_selected,
            selectedKeyframe!!.property.displayLabel()
        )
        else -> stringResource(R.string.panel_keyframes_summary_ready)
    }

    PremiumEditorPanel(
        title = stringResource(R.string.panel_keyframes_title),
        subtitle = stringResource(R.string.panel_keyframes_subtitle),
        icon = Icons.Default.Tune,
        accent = selectionAccent,
        onClose = onClose,
        modifier = modifier,
        scrollable = true,
        closeContentDescription = stringResource(R.string.panel_keyframes_close_cd),
        headerActions = {
            androidx.compose.foundation.layout.Box {
                PremiumPanelIconButton(
                    icon = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.cd_keyframe_presets),
                    onClick = { showPresets = true },
                    tint = Mocha.Yellow
                )
                DropdownMenu(
                    expanded = showPresets,
                    onDismissRequest = { showPresets = false }
                ) {
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
                            text = { Text(text = label) },
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
        }
    ) {
        PremiumPanelCard(accent = selectionAccent) {
            Text(
                text = stringResource(R.string.panel_keyframes_summary_title),
                color = Mocha.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summaryBody,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = pluralStringResource(
                        R.plurals.panel_keyframes_curves,
                        activeProperties.size,
                        activeProperties.size
                    ),
                    accent = selectionAccent
                )
                PremiumPanelPill(
                    text = pluralStringResource(
                        R.plurals.panel_keyframes_keys,
                        activeKeyframeCount,
                        activeKeyframeCount
                    ),
                    accent = Mocha.Sky
                )
                PremiumPanelPill(
                    text = stringResource(
                        R.string.panel_keyframes_playhead_format,
                        formatEditorTimestamp(playheadMs)
                    ),
                    accent = Mocha.Blue
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Sapphire) {
            Text(
                text = stringResource(R.string.panel_keyframes_properties_title),
                color = Mocha.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (activeProperties.isEmpty()) {
                    stringResource(R.string.panel_keyframes_properties_empty)
                } else {
                    stringResource(R.string.panel_keyframes_properties_description)
                },
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CORE_KEYFRAME_PROPERTIES.forEach { property ->
                    val isActive = property in activeProperties
                    val chipAccent = PROPERTY_COLORS[property] ?: Mocha.Text
                    FilterChip(
                        selected = isActive,
                        onClick = { onPropertyToggled(property) },
                        label = {
                            Text(
                                text = property.displayLabel(),
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipAccent.copy(alpha = 0.18f),
                            selectedLabelColor = chipAccent,
                            labelColor = Mocha.Subtext0
                        ),
                        leadingIcon = if (isActive) {
                            {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(chipAccent, CircleShape)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            Text(
                text = stringResource(R.string.panel_keyframes_curve_title),
                color = Mocha.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (activeProperties.isEmpty()) {
                    stringResource(R.string.panel_keyframes_curve_description_empty)
                } else {
                    stringResource(R.string.panel_keyframes_curve_description)
                },
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
            if (clipDurationMs > 0L) {
                CurveCanvas(
                    keyframes = keyframes,
                    clipDurationMs = clipDurationMs,
                    playheadMs = playheadMs,
                    activeProperties = activeProperties,
                    selectedKeyframe = selectedKeyframe,
                    onKeyframeSelected = { selectedKeyframe = it },
                    onKeyframeMoved = { keyframe, newTime, newValue ->
                        val updated = keyframes.toMutableList()
                        val index = updated.indexOf(keyframe)
                        if (index >= 0) {
                            updated[index] = keyframe.copy(
                                timeOffsetMs = newTime.coerceIn(0L, clipDurationMs),
                                value = newValue
                            )
                            onKeyframesChanged(updated)
                        }
                    },
                    onAddKeyframe = onAddKeyframe,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(216.dp)
                        .background(Mocha.Surface0, RoundedCornerShape(18.dp))
                        .padding(8.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.panel_keyframes_curve_unavailable),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = selectionAccent) {
            if (selectedKeyframe == null) {
                Text(
                    text = stringResource(R.string.panel_keyframes_selection_empty_title),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.panel_keyframes_selection_empty_body),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val keyframe = selectedKeyframe!!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.panel_keyframes_selection_title),
                            color = Mocha.Text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.panel_keyframes_selection_description),
                            color = Mocha.Subtext0,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    PremiumPanelIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_keyframe_delete),
                        onClick = {
                            onDeleteKeyframe(keyframe)
                            selectedKeyframe = null
                        },
                        tint = Mocha.Red
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = stringResource(
                            R.string.panel_keyframes_value_format,
                            formatKeyframeValue(keyframe.value)
                        ),
                        accent = PROPERTY_COLORS[keyframe.property] ?: selectionAccent
                    )
                    PremiumPanelPill(
                        text = stringResource(
                            R.string.panel_keyframes_time_format,
                            formatEditorTimestamp(keyframe.timeOffsetMs)
                        ),
                        accent = Mocha.Sky
                    )
                    PremiumPanelPill(
                        text = stringResource(
                            R.string.panel_keyframes_interpolation_format,
                            keyframe.interpolation.displayLabel()
                        ),
                        accent = Mocha.Pink
                    )
                }

                Text(
                    text = keyframe.property.displayLabel(),
                    color = PROPERTY_COLORS[keyframe.property] ?: Mocha.Text,
                    style = MaterialTheme.typography.labelLarge
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KeyframeInterpolation.entries.forEach { interpolation ->
                        val isSelected = interpolation == keyframe.interpolation
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val updated = keyframes.toMutableList()
                                val index = updated.indexOf(keyframe)
                                if (index >= 0) {
                                    updated[index] = keyframe.copy(interpolation = interpolation)
                                    onKeyframesChanged(updated)
                                    selectedKeyframe = updated[index]
                                }
                            },
                            label = {
                                Text(
                                    text = interpolation.displayLabel(),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = selectionAccent.copy(alpha = 0.18f),
                                selectedLabelColor = selectionAccent,
                                labelColor = Mocha.Subtext0
                            )
                        )
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

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .pointerInput(keyframes, activeProperties) {
                detectTapGestures(
                    onTap = { offset ->
                        val hitRadius = 20f
                        var nearest: Keyframe? = null
                        var nearestDistance = Float.MAX_VALUE

                        for (keyframe in keyframes) {
                            if (keyframe.property !in activeProperties) continue
                            val x = (keyframe.timeOffsetMs.toFloat() / clipDurationMs) * size.width
                            val range = getPropertyRange(keyframe.property)
                            val y = (1f - (keyframe.value - range.first) / (range.second - range.first)) * size.height
                            val distance = kotlin.math.sqrt(
                                (offset.x - x) * (offset.x - x) + (offset.y - y) * (offset.y - y)
                            )
                            if (distance < hitRadius && distance < nearestDistance) {
                                nearest = keyframe
                                nearestDistance = distance
                            }
                        }

                        onKeyframeSelected(nearest)
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
            .pointerInput(keyframes, activeProperties, selectedKeyframe) {
                detectDragGestures { change, _ ->
                    val keyframe = selectedKeyframe ?: return@detectDragGestures
                    val time = (change.position.x / size.width * clipDurationMs).toLong()
                    val range = getPropertyRange(keyframe.property)
                    val value = range.first + (1f - change.position.y / size.height) * (range.second - range.first)
                    onKeyframeMoved(keyframe, time, value.coerceIn(range.first, range.second))
                }
            }
    ) {
        val width = size.width
        val height = size.height

        for (index in 1..3) {
            val y = height * index / 4f
            drawLine(Color(0xFF45475A), Offset(0f, y), Offset(width, y), 0.5f)
        }
        for (index in 1..9) {
            val x = width * index / 10f
            drawLine(Color(0xFF45475A), Offset(x, 0f), Offset(x, height), 0.5f)
        }

        activeProperties.forEach { property ->
            val propertyKeyframes = keyframes.filter { it.property == property }.sortedBy { it.timeOffsetMs }
            if (propertyKeyframes.size < 2) return@forEach

            val color = PROPERTY_COLORS[property] ?: Mocha.Text
            val range = getPropertyRange(property)
            val rangeSpan = range.second - range.first

            val path = Path()
            val steps = 200
            for (index in 0..steps) {
                val fraction = index.toFloat() / steps
                val timeMs = (fraction * clipDurationMs).toLong()
                val value = com.novacut.editor.engine.KeyframeEngine.getValueAt(propertyKeyframes, property, timeMs)
                    ?: continue
                val x = fraction * width
                val y = (1f - (value - range.first) / rangeSpan) * height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(2f))
        }

        keyframes.forEach { keyframe ->
            if (keyframe.property !in activeProperties) return@forEach
            val color = PROPERTY_COLORS[keyframe.property] ?: Mocha.Text
            val range = getPropertyRange(keyframe.property)
            val x = (keyframe.timeOffsetMs.toFloat() / clipDurationMs) * width
            val y = (1f - (keyframe.value - range.first) / (range.second - range.first)) * height
            val isSelected = keyframe == selectedKeyframe

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

        val playheadX = (playheadMs.toFloat() / clipDurationMs) * width
        drawLine(Color(0xFFF38BA8), Offset(playheadX, 0f), Offset(playheadX, height), 2f)
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

private fun KeyframeProperty.displayLabel(): String {
    return name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private fun KeyframeInterpolation.displayLabel(): String {
    return name.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private fun formatKeyframeValue(value: Float): String {
    return String.format(Locale.getDefault(), "%.2f", value)
}

private fun formatEditorTimestamp(timeMs: Long): String {
    val safeTime = timeMs.coerceAtLeast(0L)
    val totalSeconds = safeTime / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val milliseconds = safeTime % 1000L
    return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, milliseconds)
}
