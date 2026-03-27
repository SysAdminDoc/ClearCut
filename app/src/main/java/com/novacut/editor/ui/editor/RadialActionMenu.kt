package com.novacut.editor.ui.editor

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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.novacut.editor.ui.theme.Mocha
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class RadialAction(
    val id: String,
    val icon: ImageVector,
    val label: String
)

private val noClipActions = listOf(
    RadialAction("add_media", Icons.Default.Add, "Add Media"),
    RadialAction("add_text", Icons.Default.TextFields, "Add Text"),
    RadialAction("add_audio", Icons.Default.MusicNote, "Add Audio"),
    RadialAction("record", Icons.Default.FiberManualRecord, "Record"),
    RadialAction("snapshot", Icons.Default.CameraAlt, "Snapshot")
)

private val clipActions = listOf(
    RadialAction("split", Icons.Default.ContentCut, "Split"),
    RadialAction("duplicate", Icons.Default.ContentCopy, "Duplicate"),
    RadialAction("effects", Icons.Default.AutoFixHigh, "Effects"),
    RadialAction("speed", Icons.Default.Speed, "Speed"),
    RadialAction("transform", Icons.Default.Transform, "Transform"),
    RadialAction("delete", Icons.Default.Delete, "Delete")
)

@Composable
fun RadialActionMenu(
    position: Offset,
    hasClipSelected: Boolean,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = if (hasClipSelected) clipActions else noClipActions
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
                .background(Mocha.Mauve)
        )

        actions.forEachIndexed { index, action ->
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
                    .background(Mocha.Surface0)
                    .clickable { onAction(action.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    action.icon,
                    contentDescription = action.label,
                    tint = Mocha.Subtext1,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
