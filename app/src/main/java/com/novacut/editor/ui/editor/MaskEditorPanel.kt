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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha


@Composable
fun MaskEditorPanel(
    masks: List<Mask>,
    selectedMaskId: String?,
    onMaskSelected: (String?) -> Unit,
    onMaskAdded: (MaskType) -> Unit,
    onMaskUpdated: (Mask) -> Unit,
    onMaskDeleted: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedMask = masks.find { it.id == selectedMaskId }

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
            Text(stringResource(R.string.mask_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row {
                var showAddMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showAddMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, "Add Mask", tint = Mocha.Green, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        MaskType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName, fontSize = 13.sp) },
                                onClick = {
                                    onMaskAdded(type)
                                    showAddMenu = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Mask list
        if (masks.isEmpty()) {
            Text(stringResource(R.string.mask_empty), color = Mocha.Subtext0, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                masks.forEach { mask ->
                    val selected = mask.id == selectedMaskId
                    MaskChip(
                        mask = mask,
                        isSelected = selected,
                        onClick = { onMaskSelected(if (selected) null else mask.id) },
                        onDelete = { onMaskDeleted(mask.id) }
                    )
                }
            }
        }

        // Selected mask controls
        selectedMask?.let { mask ->
            Spacer(Modifier.height(12.dp))

            // Feather
            MaskSlider("Feather", mask.feather, 0f, 100f) {
                onMaskUpdated(mask.copy(feather = it))
            }

            // Opacity
            MaskSlider("Opacity", mask.opacity, 0f, 1f) {
                onMaskUpdated(mask.copy(opacity = it))
            }

            // Expansion
            MaskSlider("Expansion", mask.expansion, -50f, 50f) {
                onMaskUpdated(mask.copy(expansion = it))
            }

            Spacer(Modifier.height(8.dp))

            // Invert toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Mocha.Surface0)
                    .clickable { onMaskUpdated(mask.copy(inverted = !mask.inverted)) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.mask_invert), color = Mocha.Text, fontSize = 13.sp)
                Switch(
                    checked = mask.inverted,
                    onCheckedChange = { onMaskUpdated(mask.copy(inverted = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Mocha.Mauve)
                )
            }

            Spacer(Modifier.height(4.dp))

            // Track to motion toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Mocha.Surface0)
                    .clickable { onMaskUpdated(mask.copy(trackToMotion = !mask.trackToMotion)) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.mask_track_to_motion), color = Mocha.Text, fontSize = 13.sp)
                Switch(
                    checked = mask.trackToMotion,
                    onCheckedChange = { onMaskUpdated(mask.copy(trackToMotion = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Mocha.Yellow)
                )
            }
        }
    }
}

@Composable
private fun MaskChip(
    mask: Mask,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Mocha.Mauve.copy(alpha = 0.2f) else Mocha.Surface0)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            when (mask.type) {
                MaskType.RECTANGLE -> Icons.Default.CropSquare
                MaskType.ELLIPSE -> Icons.Default.Circle
                MaskType.FREEHAND -> Icons.Default.Gesture
                MaskType.LINEAR_GRADIENT -> Icons.Default.Gradient
                MaskType.RADIAL_GRADIENT -> Icons.Default.BlurCircular
            },
            mask.type.displayName,
            tint = if (isSelected) Mocha.Mauve else Mocha.Subtext0,
            modifier = Modifier.size(16.dp)
        )
        Text(
            mask.type.displayName,
            color = if (isSelected) Mocha.Mauve else Mocha.Text,
            fontSize = 11.sp
        )
        if (mask.inverted) {
            Text(stringResource(R.string.mask_inv_label), color = Mocha.Yellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Icon(
            Icons.Default.Close,
            "Delete",
            tint = Mocha.Subtext0.copy(alpha = 0.5f),
            modifier = Modifier
                .size(14.dp)
                .clickable(onClick = onDelete)
        )
    }
}

@Composable
private fun MaskSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChanged: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Mocha.Subtext0, fontSize = 11.sp, modifier = Modifier.width(70.dp))
        Slider(
            value = value,
            onValueChange = onChanged,
            valueRange = min..max,
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Mocha.Mauve,
                activeTrackColor = Mocha.Mauve.copy(alpha = 0.6f),
                inactiveTrackColor = Mocha.Surface1
            )
        )
        Text(
            "%.1f".format(value),
            color = Mocha.Subtext0,
            fontSize = 10.sp,
            modifier = Modifier.width(36.dp)
        )
    }
}

/**
 * Preview overlay for drawing masks on the video preview.
 * This is drawn on top of the ExoPlayer surface.
 */
@Composable
fun MaskPreviewOverlay(
    masks: List<Mask>,
    selectedMaskId: String?,
    previewWidth: Float,
    previewHeight: Float,
    onMaskPointMoved: (String, Int, Float, Float) -> Unit,
    onFreehandDraw: (String, List<MaskPoint>) -> Unit,
    modifier: Modifier = Modifier
) {
    val drawingPoints = remember { mutableStateListOf<Offset>() }
    var draggedPointIndex by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(selectedMaskId) {
                if (selectedMaskId == null) return@pointerInput
                val mask = masks.find { it.id == selectedMaskId } ?: return@pointerInput

                if (mask.type == MaskType.FREEHAND) {
                    detectDragGestures(
                        onDragStart = { drawingPoints.clear() },
                        onDrag = { change, _ ->
                            drawingPoints.add(change.position)
                        },
                        onDragEnd = {
                            val points = drawingPoints.map {
                                MaskPoint(it.x / size.width, it.y / size.height)
                            }
                            onFreehandDraw(selectedMaskId, points)
                            drawingPoints.clear()
                        }
                    )
                } else {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            // Find the closest point at drag start
                            val hitRadius = 30f
                            var bestIdx = -1
                            var bestDist = Float.MAX_VALUE
                            mask.points.forEachIndexed { idx, point ->
                                val px = point.x * size.width
                                val py = point.y * size.height
                                val dist = (startOffset.x - px) * (startOffset.x - px) + (startOffset.y - py) * (startOffset.y - py)
                                if (dist < hitRadius * hitRadius && dist < bestDist) {
                                    bestDist = dist
                                    bestIdx = idx
                                }
                            }
                            draggedPointIndex = bestIdx
                        }
                    ) { change, _ ->
                        val idx = draggedPointIndex
                        if (idx >= 0 && idx < mask.points.size) {
                            onMaskPointMoved(
                                selectedMaskId, idx,
                                (change.position.x / size.width).coerceIn(0f, 1f),
                                (change.position.y / size.height).coerceIn(0f, 1f)
                            )
                        }
                    }
                }
            }
    ) {
        masks.forEach { mask ->
            val isSelected = mask.id == selectedMaskId
            val color = if (isSelected) Mocha.Mauve else Mocha.Mauve.copy(alpha = 0.3f)

            when (mask.type) {
                MaskType.RECTANGLE -> {
                    if (mask.points.size >= 2) {
                        val tl = mask.points[0]
                        val br = mask.points[1]
                        drawRect(
                            color.copy(alpha = 0.15f),
                            topLeft = Offset(tl.x * size.width, tl.y * size.height),
                            size = androidx.compose.ui.geometry.Size(
                                (br.x - tl.x) * size.width,
                                (br.y - tl.y) * size.height
                            )
                        )
                        drawRect(
                            color,
                            topLeft = Offset(tl.x * size.width, tl.y * size.height),
                            size = androidx.compose.ui.geometry.Size(
                                (br.x - tl.x) * size.width,
                                (br.y - tl.y) * size.height
                            ),
                            style = Stroke(if (isSelected) 2f else 1f)
                        )
                    }
                }
                MaskType.ELLIPSE -> {
                    if (mask.points.size >= 2) {
                        val center = mask.points[0]
                        val radius = mask.points[1]
                        drawOval(
                            color.copy(alpha = 0.15f),
                            topLeft = Offset(
                                (center.x - radius.x) * size.width,
                                (center.y - radius.y) * size.height
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                radius.x * 2f * size.width,
                                radius.y * 2f * size.height
                            )
                        )
                        drawOval(
                            color,
                            topLeft = Offset(
                                (center.x - radius.x) * size.width,
                                (center.y - radius.y) * size.height
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                radius.x * 2f * size.width,
                                radius.y * 2f * size.height
                            ),
                            style = Stroke(if (isSelected) 2f else 1f)
                        )
                    }
                }
                MaskType.FREEHAND -> {
                    if (mask.points.size >= 2) {
                        val path = Path()
                        mask.points.forEachIndexed { idx, pt ->
                            val px = pt.x * size.width
                            val py = pt.y * size.height
                            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        drawPath(path, color.copy(alpha = 0.15f))
                        drawPath(path, color, style = Stroke(if (isSelected) 2f else 1f))
                    }
                }
                MaskType.LINEAR_GRADIENT, MaskType.RADIAL_GRADIENT -> {
                    if (mask.points.size >= 2) {
                        val start = mask.points[0]
                        val end = mask.points[1]
                        drawLine(
                            color,
                            Offset(start.x * size.width, start.y * size.height),
                            Offset(end.x * size.width, end.y * size.height),
                            strokeWidth = if (isSelected) 2f else 1f
                        )
                    }
                }
            }

            // Draw control points for selected mask
            if (isSelected) {
                mask.points.forEach { point ->
                    drawCircle(
                        Color.White,
                        6f,
                        Offset(point.x * size.width, point.y * size.height)
                    )
                    drawCircle(
                        color,
                        4f,
                        Offset(point.x * size.width, point.y * size.height)
                    )
                }
            }
        }

        // Draw in-progress freehand path
        if (drawingPoints.size >= 2) {
            val path = Path()
            drawingPoints.forEachIndexed { idx, pt ->
                if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, Mocha.Mauve, style = Stroke(2f))
        }
    }
}
