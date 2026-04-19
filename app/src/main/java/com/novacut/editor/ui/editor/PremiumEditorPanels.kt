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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha
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
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Mocha.Panel,
                RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl)
            )
            .then(scrollModifier)
            .padding(horizontal = Spacing.lg, vertical = 14.dp)
    ) {
        // Drag handle — slightly slimmer + dimmer than before. Premium sheets use a quiet,
        // single-pixel-feeling pill that suggests gesture without competing for attention.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(3.dp)
                .background(Mocha.Surface2.copy(alpha = 0.55f), RoundedCornerShape(Radius.pill))
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
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
                    color = Mocha.Text,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Mocha.Subtext0,
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
                    onClick = onClose
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Mocha.PanelHighest,
        // Slightly tighter radius (was 24) — feels more disciplined and matches the
        // shared Radius.xl token used elsewhere in the system.
        shape = RoundedCornerShape(Radius.xl),
        border = BorderStroke(1.dp, Mocha.CardStrokeStrong.copy(alpha = 0.9f))
    ) {
        Box(
            modifier = Modifier.background(
                // Restrained accent wash: just a hint of color at the top edge that fades out.
                // The previous 3-stop gradient produced a visible "fold" line in the middle of
                // every panel card; this single soft fade reads as premium tinted-glass instead.
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to accent.copy(alpha = 0.10f),
                        0.55f to Mocha.PanelHighest,
                        1f to Mocha.PanelHighest
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

/**
 * Thin hairline divider for separating sections inside a PremiumPanelCard.
 * Slightly translucent to layer cleanly over the card's accent gradient.
 */
@Composable
fun PremiumHairlineDivider(
    modifier: Modifier = Modifier,
    color: Color = Mocha.CardStroke
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color.copy(alpha = 0.6f))
    )
}

@Composable
fun PremiumPanelPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun PremiumPanelIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Mocha.Subtext0,
    containerColor: Color = Mocha.PanelHighest
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, Mocha.CardStroke)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(TouchTarget.minimum)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
