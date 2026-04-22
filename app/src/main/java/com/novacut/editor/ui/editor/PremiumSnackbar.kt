package com.novacut.editor.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.Motion
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing

/**
 * Severity for toast/snackbar messages. Infers icon and accent color so callers don't have to.
 * Inferred automatically from message text via [inferSeverity] when not explicitly specified.
 */
enum class ToastSeverity { Info, Success, Warning, Error }

/**
 * Premium snackbar/toast.
 *
 * Replaces the bare Material 3 Snackbar with a more refined treatment:
 *  - Animated slide-up + fade-in entrance, fade-out exit
 *  - Subtle severity-tinted vertical accent stripe (calm, not noisy)
 *  - Severity icon on the leading edge (not color-only — accessible)
 *  - PanelHighest surface + hairline border consistent with the editor's other floating chrome
 *
 * Use [PremiumSnackbarHost] from screen scaffolds; it handles AnimatedVisibility so that
 * messages cleanly come and go instead of popping in.
 */
@Composable
fun PremiumSnackbar(
    message: String,
    severity: ToastSeverity = ToastSeverity.Info,
    modifier: Modifier = Modifier
) {
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
        // wrapContentHeight on the Surface caps the snackbar to the intrinsic height
        // of its Row content. Without this, the accent stripe's `fillMaxHeight()`
        // below cascades the ancestor Box's screen-height constraint all the way
        // down, which stretched the snackbar to cover the full screen (and, being
        // opaque-ish, absorbed touch input to the play button underneath).
        modifier = modifier.wrapContentHeight(),
        color = Mocha.PanelHighest,
        contentColor = Mocha.Text,
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, Mocha.CardStroke),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // IntrinsicSize.Min tells children "you can ask for fillMaxHeight and
                // you'll get the row's intrinsic (wrap-content) height" rather than
                // the incoming constraint from above. This is what makes the accent
                // stripe match the text's line height instead of blowing up to screen.
                .height(IntrinsicSize.Min)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent stripe — vertical slim bar carrying the severity color.
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
                // Body text uses primary Text color, not Subtext, so messages are instantly
                // readable. Status meaning is carried by the icon + accent stripe (color is
                // never the only signal — important for accessibility).
                color = Mocha.Text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp, horizontal = Spacing.xs)
            )
            Spacer(Modifier.width(Spacing.lg))
        }
    }
}

/**
 * Animated host. Pass the current message + severity from EditorState; this composable handles
 * enter/exit timing so callers don't have to wrap each callsite in their own AnimatedVisibility.
 */
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

/**
 * Heuristic severity inference. Toast callsites are scattered through 30+ files and most
 * pass plain strings. Rather than refactor every single call to add a severity parameter,
 * this helper extracts a sensible severity from the message text — and callers that *want*
 * to be explicit can still pass [ToastSeverity] directly to [showToast]/[PremiumSnackbar].
 */
fun inferSeverity(message: String): ToastSeverity {
    val lower = message.lowercase()
    return when {
        lower.startsWith("failed") || lower.contains(" failed") ||
            lower.startsWith("error") || lower.contains(" error") ||
            lower.contains("could not") || lower.contains("couldn't") -> ToastSeverity.Error
        lower.startsWith("no ") || lower.contains("not available") ||
            lower.contains("select a clip") || lower.contains("first") &&
            !lower.contains("first run") -> ToastSeverity.Warning
        lower.contains("saved") || lower.contains("ready") ||
            lower.contains("complete") || lower.contains("imported") ||
            lower.contains("exported") || lower.contains("applied") -> ToastSeverity.Success
        else -> ToastSeverity.Info
    }
}

