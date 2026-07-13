package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.ui.theme.ClearCutPrimaryButton
import com.novacut.editor.ui.theme.ClearCutSecondaryButton
import com.novacut.editor.ui.theme.Motion
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing

@Composable
fun AiSuggestionBanner(
    suggestion: AiSuggestion?,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    AnimatedVisibility(
        visible = suggestion != null,
        enter = slideInVertically(
            animationSpec = tween(Motion.DurationMedium, easing = Motion.DecelerateEasing),
            initialOffsetY = { -it / 2 }
        ) + fadeIn(tween(Motion.DurationMedium, easing = Motion.DecelerateEasing)),
        exit = slideOutVertically(
            animationSpec = tween(Motion.DurationFast, easing = Motion.AccelerateEasing),
            targetOffsetY = { -it / 2 }
        ) + fadeOut(tween(Motion.DurationFast, easing = Motion.AccelerateEasing)),
        modifier = modifier
    ) {
        suggestion?.let { s ->
            Surface(
                color = semanticColors.panel,
                shape = RoundedCornerShape(Radius.xl),
                border = BorderStroke(1.dp, ClearCutAccents.Mauve.copy(alpha = 0.22f)),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    ClearCutAccents.Mauve.copy(alpha = 0.12f),
                                    semanticColors.panelHighest.copy(alpha = 0.8f),
                                    semanticColors.panel
                                )
                            )
                        )
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    Surface(
                        color = ClearCutAccents.Mauve.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(Radius.md),
                        border = BorderStroke(1.dp, ClearCutAccents.Mauve.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_ai_suggestion),
                            tint = ClearCutAccents.Rosewater,
                            modifier = Modifier
                                .padding(Spacing.sm)
                                .size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Text(
                        text = s.message,
                        color = semanticColors.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    ClearCutPrimaryButton(
                        text = stringResource(R.string.ai_apply),
                        onClick = { onApply(s.actionId) }
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    ClearCutSecondaryButton(
                        text = stringResource(R.string.ai_suggestion_not_now),
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}
