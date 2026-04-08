package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.Mask
import com.novacut.editor.model.MaskPoint
import com.novacut.editor.model.MaskType
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
    val trackedMasks = masks.count { it.trackToMotion }
    var showAddMenu by remember { mutableStateOf(false) }

    PremiumEditorPanel(
        title = stringResource(R.string.mask_title),
        subtitle = "Shape attention, blur, and reveal areas directly over the frame without leaving the cut.",
        icon = Icons.Default.Gesture,
        accent = if (selectedMask != null) Mocha.Mauve else Mocha.Blue,
        onClose = onClose,
        modifier = modifier,
        scrollable = true,
        headerActions = {
            Box {
                PremiumPanelIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_mask),
                    onClick = { showAddMenu = true },
                    tint = Mocha.Green
                )
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    MaskType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                onMaskAdded(type)
                                showAddMenu = false
                            }
                        )
                    }
                }
            }
        }
    ) {
        PremiumPanelCard(accent = if (selectedMask != null) Mocha.Mauve else Mocha.Blue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mask stack",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create geometric or freehand masks, then tune feather, opacity, inversion, and motion behavior from one place.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(text = "${masks.size} masks", accent = Mocha.Blue)
                    PremiumPanelPill(
                        text = if (selectedMask != null) selectedMask.type.displayName else "No selection",
                        accent = if (selectedMask != null) Mocha.Mauve else Mocha.Subtext0
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaskMetric(
                    title = "Tracked",
                    value = trackedMasks.toString(),
                    accent = if (trackedMasks > 0) Mocha.Yellow else Mocha.Green,
                    modifier = Modifier.weight(1f)
                )
                MaskMetric(
                    title = "Selected",
                    value = selectedMask?.points?.size?.toString() ?: "0",
                    accent = Mocha.Mauve,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            Text(
                text = "Mask shapes",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = if (masks.isEmpty()) {
                    stringResource(R.string.mask_empty)
                } else {
                    "Select a mask to refine it, or add another shape for layered reveals and local adjustments."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaskType.entries.forEach { type ->
                    OutlinedButton(
                        onClick = { onMaskAdded(type) },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Mocha.Blue.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Blue)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = maskTypeIcon(type),
                            contentDescription = type.displayName
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = type.displayName)
                    }
                }
            }

            if (masks.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    masks.forEach { mask ->
                        MaskChip(
                            mask = mask,
                            isSelected = mask.id == selectedMaskId,
                            onClick = { onMaskSelected(if (mask.id == selectedMaskId) null else mask.id) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedMask != null) {
            PremiumPanelCard(accent = Mocha.Mauve) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selected mask",
                            style = MaterialTheme.typography.titleMedium,
                            color = Mocha.Text
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedMask.type.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mocha.Subtext0
                        )
                    }

                    OutlinedButton(
                        onClick = { onMaskDeleted(selectedMask.id) },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Mocha.Red.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Red)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_delete)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.remove))
                    }
                }

                MaskSliderRow(
                    label = "Feather",
                    value = selectedMask.feather,
                    min = 0f,
                    max = 100f,
                    accent = Mocha.Mauve,
                    onChanged = { onMaskUpdated(selectedMask.copy(feather = it)) }
                )
                MaskSliderRow(
                    label = "Opacity",
                    value = selectedMask.opacity,
                    min = 0f,
                    max = 1f,
                    accent = Mocha.Blue,
                    onChanged = { onMaskUpdated(selectedMask.copy(opacity = it)) },
                    valueFormatter = { "%.0f%%".format(it * 100f) }
                )
                MaskSliderRow(
                    label = "Expansion",
                    value = selectedMask.expansion,
                    min = -50f,
                    max = 50f,
                    accent = Mocha.Peach,
                    onChanged = { onMaskUpdated(selectedMask.copy(expansion = it)) }
                )

                MaskToggleRow(
                    label = stringResource(R.string.mask_invert),
                    subtitle = "Flip the inside and outside of the mask.",
                    checked = selectedMask.inverted,
                    accent = Mocha.Mauve,
                    onCheckedChange = { onMaskUpdated(selectedMask.copy(inverted = it)) }
                )
                MaskToggleRow(
                    label = stringResource(R.string.mask_track_to_motion),
                    subtitle = "Keep the mask attached to tracked movement when supported by the shot.",
                    checked = selectedMask.trackToMotion,
                    accent = Mocha.Yellow,
                    onCheckedChange = { onMaskUpdated(selectedMask.copy(trackToMotion = it)) }
                )
            }
        } else {
            PremiumPanelCard(accent = Mocha.Green) {
                Text(
                    text = "No mask selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                Text(
                    text = "Add a shape above or tap an existing mask to adjust feather, opacity, inversion, and motion behavior.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
            }
        }
    }
}

@Composable
private fun MaskMetric(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Mocha.Subtext0
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MaskChip(
    mask: Mask,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accent = if (isSelected) Mocha.Mauve else Mocha.Blue
    val invLabel = stringResource(R.string.mask_inv_label)

    Surface(
        color = if (isSelected) accent.copy(alpha = 0.12f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) accent.copy(alpha = 0.2f) else Mocha.CardStroke
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = maskTypeIcon(mask.type),
                contentDescription = mask.type.displayName,
                tint = accent
            )
            Column {
                Text(
                    text = mask.type.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) accent else Mocha.Text
                )
                Text(
                    text = buildString {
                        append("${mask.points.size} pts")
                        if (mask.inverted) append(" • $invLabel")
                        if (mask.trackToMotion) append(" • tracked")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Mocha.Subtext0
                )
            }
        }
    }
}

@Composable
private fun MaskSliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    accent: Color,
    onChanged: (Float) -> Unit,
    valueFormatter: (Float) -> String = { "%.1f".format(it) }
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Mocha.Subtext0
            )
            PremiumPanelPill(text = valueFormatter(value), accent = accent)
        }
        Slider(
            value = value,
            onValueChange = onChanged,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Mocha.Surface1
            )
        )
    }
}

@Composable
private fun MaskToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = Mocha.PanelRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Mocha.CardStroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = Mocha.Text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Mocha.Subtext0
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = accent)
            )
        }
    }
}

private fun maskTypeIcon(type: MaskType): ImageVector = when (type) {
    MaskType.RECTANGLE -> Icons.Default.CropSquare
    MaskType.ELLIPSE -> Icons.Default.Circle
    MaskType.FREEHAND -> Icons.Default.Gesture
    MaskType.LINEAR_GRADIENT -> Icons.Default.Gradient
    MaskType.RADIAL_GRADIENT -> Icons.Default.BlurCircular
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
                            val hitRadius = 30f
                            var bestIdx = -1
                            var bestDist = Float.MAX_VALUE
                            mask.points.forEachIndexed { idx, point ->
                                val px = point.x * size.width
                                val py = point.y * size.height
                                val dist =
                                    (startOffset.x - px) * (startOffset.x - px) +
                                        (startOffset.y - py) * (startOffset.y - py)
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
                                selectedMaskId,
                                idx,
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

                MaskType.LINEAR_GRADIENT,
                MaskType.RADIAL_GRADIENT -> {
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

        if (drawingPoints.size >= 2) {
            val path = Path()
            drawingPoints.forEachIndexed { idx, pt ->
                if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, Mocha.Mauve, style = Stroke(2f))
        }
    }
}
