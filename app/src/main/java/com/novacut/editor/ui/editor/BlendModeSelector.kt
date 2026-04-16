package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.BlendMode
import com.novacut.editor.ui.theme.Mocha

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlendModeSelector(
    currentMode: BlendMode,
    onModeSelected: (BlendMode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = blendModeSections()
    val currentSection = sections.firstOrNull { currentMode in it.modes }

    PremiumEditorPanel(
        title = stringResource(R.string.blend_mode_title),
        subtitle = stringResource(R.string.blend_mode_subtitle),
        icon = Icons.Default.AutoFixHigh,
        accent = currentSection?.accent ?: Mocha.Peach,
        onClose = onClose,
        modifier = modifier,
        scrollable = true,
        closeContentDescription = stringResource(R.string.blend_mode_close_cd)
    ) {
        PremiumPanelCard(accent = currentSection?.accent ?: Mocha.Peach) {
            Text(
                text = stringResource(R.string.blend_mode_current_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = blendModeDescription(currentMode),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = currentMode.displayName,
                    accent = currentSection?.accent ?: Mocha.Peach
                )
                PremiumPanelPill(
                    text = pluralStringResource(
                        R.plurals.blend_mode_modes_count,
                        sections.sumOf { it.modes.size },
                        sections.sumOf { it.modes.size }
                    ),
                    accent = Mocha.Sky
                )
                currentSection?.let { section ->
                    PremiumPanelPill(
                        text = section.title,
                        accent = section.accent
                    )
                }
            }
        }

        sections.forEach { section ->
            Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = section.accent) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = section.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
                PremiumPanelPill(
                    text = pluralStringResource(
                        R.plurals.blend_mode_modes_count,
                        section.modes.size,
                        section.modes.size
                    ),
                    accent = section.accent
                )

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isCompactLayout = maxWidth < 420.dp
                    val cardWidth = if (isCompactLayout) {
                        maxWidth
                    } else {
                        ((maxWidth - 10.dp) / 2).coerceAtLeast(0.dp)
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        section.modes.forEach { mode ->
                            BlendModeOptionCard(
                                mode = mode,
                                selected = mode == currentMode,
                                accent = section.accent,
                                modifier = Modifier.width(cardWidth),
                                onClick = { onModeSelected(mode) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlendModeOptionCard(
    mode: BlendMode,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) accent.copy(alpha = 0.14f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.24f) else Mocha.CardStroke
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) accent else Mocha.Text,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
                Text(
                    text = blendModeShortHint(mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mocha.Subtext0
                )
            }

            if (selected) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.cd_check_mark),
                    tint = accent,
                    modifier = Modifier.padding(end = 14.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(14.dp))
            }
        }
    }
}

private data class BlendModeSection(
    val title: String,
    val subtitle: String,
    val accent: androidx.compose.ui.graphics.Color,
    val modes: List<BlendMode>
)

private fun blendModeSections(): List<BlendModeSection> = listOf(
    BlendModeSection(
        title = "Foundation",
        subtitle = "The most-used blend modes for everyday compositing, contrast, and soft overlays.",
        accent = Mocha.Peach,
        modes = listOf(
            BlendMode.NORMAL,
            BlendMode.MULTIPLY,
            BlendMode.SCREEN,
            BlendMode.OVERLAY
        )
    ),
    BlendModeSection(
        title = "Light and Shadow",
        subtitle = "Push exposure up or down, then lean into harsher additive or subtractive blends.",
        accent = Mocha.Yellow,
        modes = listOf(
            BlendMode.DARKEN,
            BlendMode.LIGHTEN,
            BlendMode.COLOR_DODGE,
            BlendMode.COLOR_BURN,
            BlendMode.ADD,
            BlendMode.SUBTRACT
        )
    ),
    BlendModeSection(
        title = "Contrast",
        subtitle = "Use these when you want the layer interaction to feel punchier, more graphic, or more unpredictable.",
        accent = Mocha.Mauve,
        modes = listOf(
            BlendMode.HARD_LIGHT,
            BlendMode.SOFT_LIGHT,
            BlendMode.DIFFERENCE,
            BlendMode.EXCLUSION
        )
    ),
    BlendModeSection(
        title = "Color Channels",
        subtitle = "Borrow hue, saturation, or luminance from one layer while keeping the rest from another.",
        accent = Mocha.Blue,
        modes = listOf(
            BlendMode.HUE,
            BlendMode.SATURATION_BLEND,
            BlendMode.COLOR,
            BlendMode.LUMINOSITY
        )
    )
)

private fun blendModeDescription(mode: BlendMode): String = when (mode) {
    BlendMode.NORMAL -> "Leaves the clip untouched so it reads exactly as shot."
    BlendMode.MULTIPLY -> "Deepens shadows and adds density, especially useful for texture and shadow passes."
    BlendMode.SCREEN -> "Brightens footage by favoring highlights and softening dark areas."
    BlendMode.OVERLAY -> "Combines multiply and screen for a punchier, contrast-heavy composite."
    BlendMode.DARKEN -> "Keeps the darkest values from each layer."
    BlendMode.LIGHTEN -> "Keeps the brightest values from each layer."
    BlendMode.COLOR_DODGE -> "Pushes highlights hard for a glowy, high-energy finish."
    BlendMode.COLOR_BURN -> "Adds a darker, more dramatic burn into the underlying image."
    BlendMode.HARD_LIGHT -> "Creates a bold, high-contrast interaction that can feel graphic fast."
    BlendMode.SOFT_LIGHT -> "Gives you a gentler contrast lift with a more natural finish."
    BlendMode.DIFFERENCE -> "Subtracts the layers from each other for an edgy, inverted feel."
    BlendMode.EXCLUSION -> "A softer variation of difference with less severe contrast."
    BlendMode.HUE -> "Borrows just the hue from the selected clip."
    BlendMode.SATURATION_BLEND -> "Borrows only the saturation, keeping brightness and hue from below."
    BlendMode.COLOR -> "Applies hue and saturation while respecting the underlying luminance."
    BlendMode.LUMINOSITY -> "Uses the selected clip for brightness while keeping underlying color."
    BlendMode.ADD -> "Stacks light values together for energetic glows and overlays."
    BlendMode.SUBTRACT -> "Pulls brightness out for darker, stylized interactions."
}

private fun blendModeShortHint(mode: BlendMode): String = when (mode) {
    BlendMode.NORMAL -> "Original"
    BlendMode.MULTIPLY -> "Darken"
    BlendMode.SCREEN -> "Brighten"
    BlendMode.OVERLAY -> "Punch"
    BlendMode.DARKEN -> "Shadow"
    BlendMode.LIGHTEN -> "Highlight"
    BlendMode.COLOR_DODGE -> "Glow"
    BlendMode.COLOR_BURN -> "Burn"
    BlendMode.HARD_LIGHT -> "Graphic"
    BlendMode.SOFT_LIGHT -> "Soft contrast"
    BlendMode.DIFFERENCE -> "Invert"
    BlendMode.EXCLUSION -> "Soft invert"
    BlendMode.HUE -> "Hue only"
    BlendMode.SATURATION_BLEND -> "Saturation"
    BlendMode.COLOR -> "Colorize"
    BlendMode.LUMINOSITY -> "Luma"
    BlendMode.ADD -> "Add light"
    BlendMode.SUBTRACT -> "Remove light"
}
