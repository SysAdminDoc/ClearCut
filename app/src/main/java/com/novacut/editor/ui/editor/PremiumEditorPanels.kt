package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget

@Composable
fun PremiumEditorPanel(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    closeContentDescription: String? = null,
    closeButtonTestTag: String? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalClearCutColors.current
    val scrollModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                colors.panel,
                RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl)
            )
            .then(scrollModifier)
            .padding(horizontal = Spacing.lg, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(3.dp)
                .background(
                    colors.cardStrokeStrong.copy(alpha = if (colors.highContrast) 0.55f else 0.28f),
                    RoundedCornerShape(Radius.sm)
                )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accent.copy(alpha = if (colors.highContrast) 0.24f else 0.14f),
                shape = RoundedCornerShape(Radius.md),
                border = BorderStroke(
                    1.dp,
                    accent.copy(alpha = if (colors.highContrast) 0.52f else 0.22f)
                )
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.text,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.subtext,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                headerActions()
                PremiumPanelIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = closeContentDescription ?: stringResource(R.string.tool_close),
                    onClick = onClose,
                    modifier = closeButtonTestTag?.let { Modifier.testTag(it) } ?: Modifier
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
fun PremiumPanelCard(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalClearCutColors.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.panelHighest,
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(
            1.dp,
            if (colors.highContrast) colors.cardStrokeStrong else colors.cardStrokeStrong.copy(alpha = 0.86f)
        )
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to accent.copy(alpha = if (colors.highContrast) 0.16f else 0.10f),
                        0.54f to colors.panelHighest,
                        1f to colors.panelRaised.copy(alpha = 0.96f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content
            )
        }
    }
}

@Composable
fun PremiumHairlineDivider(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val colors = LocalClearCutColors.current
    val resolvedColor = if (color == Color.Unspecified) colors.cardStroke else color

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(resolvedColor.copy(alpha = if (colors.highContrast) 0.9f else 0.6f))
    )
}

@Composable
fun PremiumPanelPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalClearCutColors.current

    Surface(
        modifier = modifier.defaultMinSize(minHeight = 30.dp),
        color = accent.copy(alpha = if (colors.highContrast) 0.22f else 0.12f),
        shape = RoundedCornerShape(Radius.xs),
        border = BorderStroke(1.dp, accent.copy(alpha = if (colors.highContrast) 0.48f else 0.2f))
    ) {
        Text(
            text = text,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun PremiumPanelIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    enabled: Boolean = true
) {
    val colors = LocalClearCutColors.current
    val resolvedContainer = if (containerColor == Color.Unspecified) colors.panelHighest else containerColor
    val resolvedTint = if (tint == Color.Unspecified) colors.subtext else tint

    Surface(
        modifier = modifier,
        color = resolvedContainer,
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(1.dp, if (colors.highContrast) colors.cardStrokeStrong else colors.cardStroke)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(TouchTarget.minimum)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) resolvedTint else colors.disabledText.copy(alpha = 0.72f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
