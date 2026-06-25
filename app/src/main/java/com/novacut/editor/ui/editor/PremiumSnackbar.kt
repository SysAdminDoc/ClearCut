package com.novacut.editor.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.ui.theme.Elevation
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.Motion
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing

enum class ToastSeverity { Info, Success, Warning, Error }

@Composable
fun PremiumSnackbar(
    message: String,
    severity: ToastSeverity = ToastSeverity.Info,
    modifier: Modifier = Modifier
) {
    val colors = LocalClearCutColors.current
    val accent = when (severity) {
        ToastSeverity.Info -> Mocha.Lavender
        ToastSeverity.Success -> Mocha.Green
        ToastSeverity.Warning -> Mocha.Peach
        ToastSeverity.Error -> Mocha.Red
    }
    val icon: ImageVector = when (severity) {
        ToastSeverity.Info -> Icons.Outlined.Info
        ToastSeverity.Success -> Icons.Outlined.CheckCircle
        ToastSeverity.Warning -> Icons.Outlined.WarningAmber
        ToastSeverity.Error -> Icons.Outlined.ErrorOutline
    }

    Surface(
        modifier = modifier
            .wrapContentHeight()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.panelHighest,
        contentColor = colors.text,
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, if (colors.highContrast) colors.cardStrokeStrong else colors.cardStroke),
        shadowElevation = Elevation.toast
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = if (colors.highContrast) 0.18f else 0.12f),
                            Color.Transparent
                        )
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Spacer(Modifier.width(Spacing.md))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = message,
                color = colors.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp, horizontal = Spacing.xs)
            )
            Spacer(Modifier.width(Spacing.lg))
        }
    }
}

@Composable
fun PremiumSnackbarHost(
    message: String?,
    severity: ToastSeverity = ToastSeverity.Info,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(
            animationSpec = tween(Motion.DurationMedium, easing = Motion.DecelerateEasing),
            initialOffsetY = { it / 3 }
        ) + fadeIn(tween(Motion.DurationMedium, easing = Motion.DecelerateEasing)),
        exit = fadeOut(tween(Motion.DurationFast, easing = Motion.AccelerateEasing)) +
            slideOutVertically(
                animationSpec = tween(Motion.DurationFast, easing = Motion.AccelerateEasing),
                targetOffsetY = { it / 3 }
            ),
        modifier = modifier
    ) {
        PremiumSnackbar(message = message ?: "", severity = severity)
    }
}

fun inferSeverity(message: String): ToastSeverity {
    val lower = message.lowercase()
    return when {
        lower.startsWith("failed") || lower.contains(" failed") ||
            lower.startsWith("error") || lower.contains(" error") ||
            lower.contains("could not") || lower.contains("couldn't") -> ToastSeverity.Error
        lower.startsWith("no ") || lower.contains("not available") ||
            lower.contains("select a clip") ||
            (lower.contains("first") && !lower.contains("first run")) -> ToastSeverity.Warning
        lower.contains("saved") || lower.contains("ready") ||
            lower.contains("complete") || lower.contains("imported") ||
            lower.contains("exported") || lower.contains("applied") -> ToastSeverity.Success
        else -> ToastSeverity.Info
    }
}
