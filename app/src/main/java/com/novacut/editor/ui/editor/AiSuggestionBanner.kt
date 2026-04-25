package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.Motion
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget

@Composable
fun AiSuggestionBanner(
    suggestion: AiSuggestion?,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                color = Mocha.Panel,
                shape = RoundedCornerShape(Radius.xl),
                border = BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.22f)),
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
                                    Mocha.Mauve.copy(alpha = 0.12f),
                                    Mocha.PanelHighest.copy(alpha = 0.8f),
                                    Mocha.Panel
                                )
                            )
                        )
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    Surface(
                        color = Mocha.Mauve.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(Radius.md),
                        border = BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_ai_suggestion),
                            tint = Mocha.Rosewater,
                            modifier = Modifier
                                .padding(Spacing.sm)
                                .size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Text(
                        text = s.message,
                        color = Mocha.Text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Surface(
                        onClick = { onApply(s.actionId) },
                        color = Mocha.Rosewater,
                        contentColor = Mocha.Midnight,
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ai_apply),
                            color = Mocha.Midnight,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(TouchTarget.minimum)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_dismiss_suggestion),
                            tint = Mocha.Subtext0,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
