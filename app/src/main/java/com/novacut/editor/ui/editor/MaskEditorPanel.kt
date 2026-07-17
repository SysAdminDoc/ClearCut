package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
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

@OptIn(ExperimentalLayoutApi::class)
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
    val semanticColors = LocalClearCutColors.current
    val selectedMask = masks.find { it.id == selectedMaskId }
    val trackedMasks = masks.count { it.trackToMotion }
    var showAddMenu by remember { mutableStateOf(false) }

    PremiumEditorPanel(
        title = stringResource(R.string.mask_title),
        subtitle = stringResource(R.string.panel_mask_subtitle),
        icon = Icons.Default.Gesture,
        accent = if (selectedMask != null) ClearCutAccents.Mauve else ClearCutAccents.Blue,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.cd_close_mask_editor),
        modifier = modifier,
        scrollable = true,
        headerActions = {
            Box {
                PremiumPanelIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_mask),
                    onClick = { showAddMenu = true },
                    tint = ClearCutAccents.Green
                )
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    MaskType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(localizedMaskTypeName(type)) },
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
        PremiumPanelCard(accent = if (selectedMask != null) ClearCutAccents.Mauve else ClearCutAccents.Blue) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactLayout = maxWidth < 420.dp
                if (isCompactLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(
                                text = stringResource(R.string.mask_stack_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = semanticColors.text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.mask_stack_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = semanticColors.subtext
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(
                                text = stringResource(R.string.mask_summary_total, masks.size),
                                accent = ClearCutAccents.Blue
                            )
                            PremiumPanelPill(
                                text = if (selectedMask != null) localizedMaskTypeName(selectedMask.type) else stringResource(R.string.mask_selection_none),
                                accent = if (selectedMask != null) ClearCutAccents.Mauve else semanticColors.subtext
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
                                text = stringResource(R.string.mask_stack_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = semanticColors.text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.mask_stack_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = semanticColors.subtext
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(
                                text = stringResource(R.string.mask_summary_total, masks.size),
                                accent = ClearCutAccents.Blue
                            )
                            PremiumPanelPill(
                                text = if (selectedMask != null) localizedMaskTypeName(selectedMask.type) else stringResource(R.string.mask_selection_none),
                                accent = if (selectedMask != null) ClearCutAccents.Mauve else semanticColors.subtext
                            )
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaskMetric(
                    title = stringResource(R.string.mask_metric_tracked),
                    value = trackedMasks.toString(),
                    accent = if (trackedMasks > 0) ClearCutAccents.Yellow else ClearCutAccents.Green,
                    modifier = Modifier.width(140.dp)
                )
                MaskMetric(
                    title = stringResource(R.string.mask_metric_points),
                    value = selectedMask?.points?.size?.toString() ?: "0",
                    accent = ClearCutAccents.Mauve,
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Blue) {
            Text(
                text = stringResource(R.string.mask_shapes_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = if (masks.isEmpty()) {
                    stringResource(R.string.mask_empty)
                } else {
                    stringResource(R.string.mask_shapes_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaskType.entries.forEach { type ->
                    OutlinedButton(
                        onClick = { onMaskAdded(type) },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, ClearCutAccents.Blue.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Blue)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = maskTypeIcon(type),
                            contentDescription = localizedMaskTypeName(type)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = localizedMaskTypeName(type))
                    }
                }
            }

            if (masks.isNotEmpty()) {
                FlowRow(
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
            PremiumPanelCard(accent = ClearCutAccents.Mauve) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isCompactLayout = maxWidth < 420.dp
                    if (isCompactLayout) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column {
                                Text(
                                    text = stringResource(R.string.mask_selected_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = semanticColors.text
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = localizedMaskTypeName(selectedMask.type),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = semanticColors.subtext
                                )
                            }
                            OutlinedButton(
                                onClick = { onMaskDeleted(selectedMask.id) },
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, ClearCutAccents.Red.copy(alpha = 0.25f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Red)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_delete)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.remove))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.mask_selected_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = semanticColors.text
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = localizedMaskTypeName(selectedMask.type),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = semanticColors.subtext
                                )
                            }

                            OutlinedButton(
                                onClick = { onMaskDeleted(selectedMask.id) },
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, ClearCutAccents.Red.copy(alpha = 0.25f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Red)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_delete)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.remove))
                            }
                        }
                    }
                }

                MaskSliderRow(
                    label = stringResource(R.string.mask_feather),
                    value = selectedMask.feather,
                    min = 0f,
                    max = 100f,
                    accent = ClearCutAccents.Mauve,
                    onChanged = { onMaskUpdated(selectedMask.copy(feather = it)) }
                )
                MaskSliderRow(
                    label = stringResource(R.string.tool_opacity),
                    value = selectedMask.opacity,
                    min = 0f,
                    max = 1f,
                    accent = ClearCutAccents.Blue,
                    onChanged = { onMaskUpdated(selectedMask.copy(opacity = it)) },
                    valueFormatter = { "%.0f%%".format(it * 100f) }
                )
                MaskSliderRow(
                    label = stringResource(R.string.mask_expansion),
                    value = selectedMask.expansion,
                    min = -50f,
                    max = 50f,
                    accent = ClearCutAccents.Peach,
                    onChanged = { onMaskUpdated(selectedMask.copy(expansion = it)) }
                )

                if (selectedMask.type != MaskType.FREEHAND && selectedMask.points.isNotEmpty()) {
                    MaskPointCoordinateEditor(
                        mask = selectedMask,
                        onMaskUpdated = onMaskUpdated
                    )
                }

                MaskToggleRow(
                    label = stringResource(R.string.mask_invert),
                    subtitle = stringResource(R.string.mask_invert_description),
                    checked = selectedMask.inverted,
                    accent = ClearCutAccents.Mauve,
                    onCheckedChange = { onMaskUpdated(selectedMask.copy(inverted = it)) }
                )
                MaskToggleRow(
                    label = stringResource(R.string.mask_track_to_motion),
                    subtitle = stringResource(R.string.mask_track_description),
                    checked = selectedMask.trackToMotion,
                    accent = ClearCutAccents.Yellow,
                    onCheckedChange = { onMaskUpdated(selectedMask.copy(trackToMotion = it)) }
                )
            }
        } else {
            PremiumPanelCard(accent = ClearCutAccents.Green) {
                Text(
                    text = stringResource(R.string.mask_none_selected_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = semanticColors.text
                )
                Text(
                    text = stringResource(R.string.mask_none_selected_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
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
    val semanticColors = LocalClearCutColors.current
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
                color = semanticColors.subtext
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
    val semanticColors = LocalClearCutColors.current
    val accent = if (isSelected) ClearCutAccents.Mauve else ClearCutAccents.Blue
    val invLabel = stringResource(R.string.mask_inv_label)
    val pointsLabel = stringResource(R.string.mask_chip_points_format, mask.points.size)
    val trackedLabel = stringResource(R.string.mask_chip_tracked)

    Surface(
        color = if (isSelected) accent.copy(alpha = 0.12f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) accent.copy(alpha = 0.2f) else semanticColors.cardStroke
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
                contentDescription = localizedMaskTypeName(mask.type),
                tint = accent
            )
            Column {
                Text(
                    text = localizedMaskTypeName(mask.type),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) accent else semanticColors.text
                )
                Text(
                    text = buildString {
                        append(pointsLabel)
                        if (mask.inverted) append(" • $invLabel")
                        if (mask.trackToMotion) append(" • $trackedLabel")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext
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
    val semanticColors = LocalClearCutColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = semanticColors.subtext
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
                inactiveTrackColor = semanticColors.surface
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
    val semanticColors = LocalClearCutColors.current
    val switchState = stringResource(if (checked) R.string.settings_on else R.string.settings_off)
    Surface(
        color = semanticColors.panelRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, semanticColors.cardStroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = switchState }
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = semanticColors.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = accent)
            )
        }
    }
}

@Composable
private fun MaskPointCoordinateEditor(
    mask: Mask,
    onMaskUpdated: (Mask) -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    val pointLabels = when (mask.type) {
        MaskType.RECTANGLE -> if (mask.points.size >= 2) {
            listOf(stringResource(R.string.mask_point_top_left), stringResource(R.string.mask_point_bottom_right))
        } else emptyList()
        MaskType.ELLIPSE -> if (mask.points.size >= 2) {
            listOf(stringResource(R.string.mask_point_center), stringResource(R.string.mask_point_radius))
        } else emptyList()
        MaskType.LINEAR_GRADIENT, MaskType.RADIAL_GRADIENT -> if (mask.points.size >= 2) {
            listOf(stringResource(R.string.mask_point_start), stringResource(R.string.mask_point_end))
        } else emptyList()
        else -> emptyList()
    }
    if (pointLabels.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.mask_control_points),
            style = MaterialTheme.typography.labelMedium,
            color = semanticColors.subtext
        )
        pointLabels.forEachIndexed { index, label ->
            if (index >= mask.points.size) return@forEachIndexed
            val point = mask.points[index]
            val xDescription = stringResource(R.string.mask_point_x_coordinate_cd, label)
            val yDescription = stringResource(R.string.mask_point_y_coordinate_cd, label)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ClearCutAccents.Mauve,
                    modifier = Modifier.width(72.dp)
                )
                OutlinedTextField(
                    value = formatEditorDecimal(point.x.toDouble() * 100.0, 1),
                    onValueChange = { text ->
                        val parsed = parseEditorDecimal(text) ?: return@OutlinedTextField
                        val newX = (parsed / 100.0).toFloat().coerceIn(0f, 1f)
                        val updatedPoints = mask.points.toMutableList()
                        updatedPoints[index] = updatedPoints[index].copy(x = newX)
                        onMaskUpdated(mask.copy(points = updatedPoints))
                    },
                    label = { Text(stringResource(R.string.mask_point_x_percent)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .semantics { contentDescription = xDescription },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = semanticColors.text,
                        unfocusedTextColor = semanticColors.subtext,
                        focusedBorderColor = ClearCutAccents.Mauve,
                        unfocusedBorderColor = semanticColors.surface
                    )
                )
                OutlinedTextField(
                    value = formatEditorDecimal(point.y.toDouble() * 100.0, 1),
                    onValueChange = { text ->
                        val parsed = parseEditorDecimal(text) ?: return@OutlinedTextField
                        val newY = (parsed / 100.0).toFloat().coerceIn(0f, 1f)
                        val updatedPoints = mask.points.toMutableList()
                        updatedPoints[index] = updatedPoints[index].copy(y = newY)
                        onMaskUpdated(mask.copy(points = updatedPoints))
                    },
                    label = { Text(stringResource(R.string.mask_point_y_percent)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .semantics { contentDescription = yDescription },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = semanticColors.text,
                        unfocusedTextColor = semanticColors.subtext,
                        focusedBorderColor = ClearCutAccents.Mauve,
                        unfocusedBorderColor = semanticColors.surface
                    )
                )
            }
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

@Composable
private fun localizedMaskTypeName(type: MaskType): String = stringResource(
    when (type) {
        MaskType.RECTANGLE -> R.string.mask_type_rectangle
        MaskType.ELLIPSE -> R.string.mask_type_ellipse
        MaskType.FREEHAND -> R.string.mask_type_freehand
        MaskType.LINEAR_GRADIENT -> R.string.mask_type_linear_gradient
        MaskType.RADIAL_GRADIENT -> R.string.mask_type_radial_gradient
    }
)

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
    // The gesture coroutine launches once per selection; `masks` captured at
    // launch would be a stale snapshot — after the first handle move, every
    // later onDragStart hit-tested against the point's ORIGINAL position, so
    // grabbing a handle where it is drawn silently failed.
    val currentMasks by rememberUpdatedState(masks)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(selectedMaskId) {
                if (selectedMaskId == null) return@pointerInput
                val maskType = currentMasks.find { it.id == selectedMaskId }?.type
                    ?: return@pointerInput

                if (maskType == MaskType.FREEHAND) {
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
                            val mask = currentMasks.find { it.id == selectedMaskId }
                                ?: return@detectDragGestures
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
                        val mask = currentMasks.find { it.id == selectedMaskId }
                        if (mask != null && idx >= 0 && idx < mask.points.size) {
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
            val color = if (isSelected) ClearCutAccents.Mauve else ClearCutAccents.Mauve.copy(alpha = 0.3f)

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
            drawPath(path, ClearCutAccents.Mauve, style = Stroke(2f))
        }
    }
}
