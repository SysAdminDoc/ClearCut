package com.novacut.editor.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ClearCutScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalClearCutColors.current
    Box(
        modifier = modifier
            .background(colors.background)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.background,
                            colors.backgroundMid.copy(alpha = 0.98f),
                            colors.background
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Mocha.Rosewater.copy(alpha = if (colors.highContrast) 0.12f else 0.055f),
                            0.18f to Color.Transparent,
                            0.76f to Color.Transparent,
                            1f to Mocha.Teal.copy(alpha = if (colors.highContrast) 0.10f else 0.045f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to colors.panelRaised.copy(alpha = 0.32f),
                            0.5f to Color.Transparent,
                            1f to colors.panelRaised.copy(alpha = 0.26f)
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun ClearCutHeroCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.xxl),
    accent: Color = Mocha.Mauve,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xl),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalClearCutColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.panel,
        shape = shape,
        border = BorderStroke(1.dp, if (colors.highContrast) colors.cardStrokeStrong else colors.cardStrokeStrong.copy(alpha = 0.72f))
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to accent.copy(alpha = 0.075f),
                        0.2f to colors.panelHighest.copy(alpha = 0.92f),
                        0.68f to colors.panel.copy(alpha = 0.98f),
                        1f to colors.panelRaised.copy(alpha = 0.98f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content
            )
        }
    }
}

@Composable
fun ClearCutPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val colors = LocalClearCutColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = Mocha.Rosewater,
            contentColor = Mocha.Midnight,
            disabledContainerColor = Mocha.Surface1.copy(alpha = 0.5f),
            disabledContentColor = colors.disabledText
        ),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        modifier = modifier
            .semantics { contentDescription = text }
            .defaultMinSize(minHeight = TouchTarget.minimum)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Mocha.Midnight else colors.disabledText,
                modifier = Modifier.size(18.dp)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(Spacing.sm))
        }
        Text(
            text = text,
            color = if (enabled) Mocha.Midnight else colors.disabledText,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ClearCutSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentColor: Color = Mocha.Text,
    enabled: Boolean = true
) {
    val colors = LocalClearCutColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(1.dp, colors.cardStrokeStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.panelHighest.copy(alpha = if (colors.highContrast) 0.88f else 0.42f),
            contentColor = contentColor,
            disabledContainerColor = Mocha.Surface1.copy(alpha = 0.28f),
            disabledContentColor = colors.disabledText
        ),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        modifier = modifier.defaultMinSize(minHeight = TouchTarget.minimum)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) contentColor else colors.disabledText,
                modifier = Modifier.size(18.dp)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(Spacing.sm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ClearCutMetricPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val colors = LocalClearCutColors.current
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = if (colors.highContrast) 0.26f else 0.12f),
        shape = RoundedCornerShape(Radius.xs),
        border = BorderStroke(1.dp, accent.copy(alpha = if (colors.highContrast) 0.95f else 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ClearCutFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Mocha.Mauve,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val colors = LocalClearCutColors.current
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = TouchTarget.minimum),
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(Radius.sm),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.panelHighest,
            labelColor = colors.subtext,
            selectedContainerColor = if (colors.highContrast) accent else accent.copy(alpha = 0.16f),
            selectedLabelColor = if (colors.highContrast) Mocha.Crust else accent,
            selectedLeadingIconColor = if (colors.highContrast) Mocha.Crust else accent
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = colors.cardStroke,
            selectedBorderColor = if (colors.highContrast) colors.cardStrokeStrong else accent.copy(alpha = 0.34f)
        )
    )
}

@Composable
fun ClearCutChromeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Mocha.Subtext1,
    containerColor: Color = Mocha.PanelHighest,
    borderColor: Color = Mocha.CardStroke,
    shape: Shape = RoundedCornerShape(Radius.md),
    size: Dp = TouchTarget.minimum,
    enabled: Boolean = true
) {
    val colors = LocalClearCutColors.current
    Surface(
        modifier = modifier,
        color = if (enabled) containerColor else colors.panelRaised.copy(alpha = 0.46f),
        shape = shape,
        border = BorderStroke(
            1.dp,
            if (!enabled) {
                colors.cardStroke.copy(alpha = 0.46f)
            } else if (colors.highContrast && borderColor == Mocha.CardStroke) {
                colors.cardStrokeStrong
            } else {
                borderColor
            }
        )
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(size)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else colors.disabledText.copy(alpha = 0.72f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ClearCutDialogIcon(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalClearCutColors.current
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = if (colors.highContrast) 0.26f else 0.14f),
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(1.dp, accent.copy(alpha = if (colors.highContrast) 0.95f else 0.24f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .padding(Spacing.md)
                .size(22.dp)
        )
    }
}

@Composable
fun ClearCutSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalClearCutColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    color = colors.subtext,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.defaultMinSize(minHeight = TouchTarget.minimum),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing
        )
    }
}
