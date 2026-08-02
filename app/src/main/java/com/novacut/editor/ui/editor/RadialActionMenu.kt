package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class RadialAction(
    val id: String,
    val icon: ImageVector,
    val labelResId: Int
)

private val noClipActions = listOf(
    RadialAction("add_media", Icons.Default.Add, R.string.radial_action_add_media),
    RadialAction("add_text", Icons.Default.TextFields, R.string.radial_action_add_text),
    RadialAction("add_audio", Icons.Default.MusicNote, R.string.radial_action_add_audio),
    RadialAction("record", Icons.Default.FiberManualRecord, R.string.radial_action_record),
    RadialAction("snapshot", Icons.Default.CameraAlt, R.string.radial_action_snapshot)
)

private fun clipActions(includeOpenCompound: Boolean): List<RadialAction> = buildList {
    if (includeOpenCompound) {
        add(RadialAction("open_compound", Icons.Default.FileOpen, R.string.radial_action_open))
    }
    add(RadialAction("split", Icons.Default.ContentCut, R.string.radial_action_split))
    add(RadialAction("duplicate", Icons.Default.ContentCopy, R.string.radial_action_duplicate))
    add(RadialAction("effects", Icons.Default.AutoFixHigh, R.string.radial_action_effects))
    add(RadialAction("speed", Icons.Default.Speed, R.string.radial_action_speed))
    add(RadialAction("transform", Icons.Default.Transform, R.string.radial_action_transform))
    add(RadialAction("delete", Icons.Default.Delete, R.string.radial_action_delete))
}

@Composable
fun RadialActionMenu(
    position: Offset,
    hasClipSelected: Boolean,
    hasOpenableCompoundClipSelected: Boolean = false,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    val actions = if (hasClipSelected) {
        clipActions(includeOpenCompound = hasOpenableCompoundClipSelected)
    } else {
        noClipActions
    }
    val radiusPx = with(LocalDensity.current) { 70.dp.toPx() }
    val buttonSizePx = with(LocalDensity.current) { 40.dp.toPx() }
    val centerDotSizePx = with(LocalDensity.current) { 20.dp.toPx() }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "radial_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (position.x - centerDotSizePx / 2).roundToInt(),
                        (position.y - centerDotSizePx / 2).roundToInt()
                    )
                }
                .size(20.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(ClearCutAccents.Mauve)
        )

        actions.forEachIndexed { index, action ->
            val actionLabel = stringResource(action.labelResId)
            val angleDeg = 360.0 / actions.size * index - 90.0
            val angleRad = Math.toRadians(angleDeg)
            val offsetX = (cos(angleRad) * radiusPx).toFloat()
            val offsetY = (sin(angleRad) * radiusPx).toFloat()

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (position.x + offsetX - buttonSizePx / 2).roundToInt(),
                            (position.y + offsetY - buttonSizePx / 2).roundToInt()
                        )
                    }
                    .size(40.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(semanticColors.surfaceLow)
                    .semantics { contentDescription = actionLabel }
                    .clickable { onAction(action.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    action.icon,
                    contentDescription = actionLabel,
                    tint = semanticColors.subtextStrong,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
