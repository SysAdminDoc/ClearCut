package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

data class PipPreset(
    val name: String,
    val posX: Float,
    val posY: Float,
    val scaleX: Float,
    val scaleY: Float
)

val pipPresets = listOf(
    PipPreset("Top Left", -0.55f, -0.55f, 0.35f, 0.35f),
    PipPreset("Top Right", 0.55f, -0.55f, 0.35f, 0.35f),
    PipPreset("Bottom Left", -0.55f, 0.55f, 0.35f, 0.35f),
    PipPreset("Bottom Right", 0.55f, 0.55f, 0.35f, 0.35f),
    PipPreset("Center Small", 0f, 0f, 0.4f, 0.4f),
    PipPreset("Left Half", -0.5f, 0f, 0.5f, 1f),
    PipPreset("Right Half", 0.5f, 0f, 0.5f, 1f),
    PipPreset("Top Half", 0f, -0.5f, 1f, 0.5f),
    PipPreset("Bottom Half", 0f, 0.5f, 1f, 0.5f),
    PipPreset("Full Screen", 0f, 0f, 1f, 1f),
    PipPreset("Lower Third", 0f, 0.6f, 0.8f, 0.25f),
    PipPreset("Circle Cam", 0.6f, -0.6f, 0.25f, 0.25f)
)

@Composable
fun PipPresetsPanel(
    onPresetSelected: (PipPreset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = rememberPipSections()

    PremiumEditorPanel(
        title = stringResource(R.string.pip_title),
        subtitle = "Stage facecam, commentary, and split-screen layouts without manually repositioning every shot.",
        icon = Icons.Default.PictureInPicture,
        accent = Mocha.Sapphire,
        onClose = onClose,
        modifier = modifier.heightIn(max = 520.dp),
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Sapphire) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = "${pipPresets.size} layouts",
                    accent = Mocha.Sapphire
                )
                PremiumPanelPill(
                    text = "Corners, splits, hero",
                    accent = Mocha.Teal
                )
            }

            Text(
                text = "Layout presets",
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Choose a starting composition, then fine-tune transform and crop only if the shot needs something custom.",
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = section.accent) {
                Text(
                    text = section.title,
                    color = section.accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = section.subtitle,
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    section.presets.forEach { preset ->
                        PipPresetCard(
                            preset = preset,
                            accent = section.accent,
                            onClick = { onPresetSelected(preset) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChromaKeyPanel(
    similarity: Float,
    smoothness: Float,
    spillSuppression: Float,
    keyColorR: Float,
    keyColorG: Float,
    keyColorB: Float,
    onSimilarityChanged: (Float) -> Unit,
    onSmoothnessChanged: (Float) -> Unit,
    onSpillChanged: (Float) -> Unit,
    onKeyColorChanged: (Float, Float, Float) -> Unit,
    onShowAlphaMatte: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyColor = Color(
        red = keyColorR.coerceIn(0f, 1f),
        green = keyColorG.coerceIn(0f, 1f),
        blue = keyColorB.coerceIn(0f, 1f)
    )

    PremiumEditorPanel(
        title = stringResource(R.string.panel_chroma_key_title),
        subtitle = "Cleanly isolate keyed footage, reduce spill, and refine the matte before you composite it over the timeline.",
        icon = Icons.Default.Visibility,
        accent = Mocha.Green,
        onClose = onClose,
        modifier = modifier.heightIn(max = 560.dp),
        scrollable = true,
        headerActions = {
            PremiumPanelIconButton(
                icon = Icons.Default.Visibility,
                contentDescription = stringResource(R.string.panel_chroma_key_alpha_matte),
                onClick = onShowAlphaMatte,
                tint = Mocha.Peach
            )
        }
    ) {
        PremiumPanelCard(accent = Mocha.Green) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = "Similarity ${formatUnit(similarity)}",
                    accent = Mocha.Green
                )
                PremiumPanelPill(
                    text = "Smoothness ${formatUnit(smoothness)}",
                    accent = Mocha.Sapphire
                )
                PremiumPanelPill(
                    text = "Spill ${formatUnit(spillSuppression)}",
                    accent = Mocha.Yellow
                )
            }

            Text(
                text = "Key source",
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = keyColor.copy(alpha = 0.22f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, keyColor.copy(alpha = 0.36f))
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(keyColor, CircleShape)
                    )
                }
                Text(
                    text = "Use a clean screen color first, then open the matte view if edges or spill need more attention.",
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Peach) {
            Text(
                text = stringResource(R.string.panel_chroma_key_color),
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyColorSwatch(
                    label = "Green",
                    color = Color(0xFF00FF00),
                    selected = keyColorG > 0.8f && keyColorR < 0.3f && keyColorB < 0.3f,
                    onClick = { onKeyColorChanged(0f, 1f, 0f) }
                )
                KeyColorSwatch(
                    label = "Blue",
                    color = Color(0xFF0044FF),
                    selected = keyColorB > 0.8f && keyColorR < 0.3f && keyColorG < 0.3f,
                    onClick = { onKeyColorChanged(0f, 0f, 1f) }
                )
                KeyColorSwatch(
                    label = "Red",
                    color = Color(0xFFFF0000),
                    selected = keyColorR > 0.8f && keyColorG < 0.3f && keyColorB < 0.3f,
                    onClick = { onKeyColorChanged(1f, 0f, 0f) }
                )
            }

            ChromaSlider(
                label = stringResource(R.string.chroma_red),
                value = keyColorR,
                color = Color(0xFFF38BA8),
                onChanged = { onKeyColorChanged(it, keyColorG, keyColorB) }
            )
            ChromaSlider(
                label = stringResource(R.string.chroma_green),
                value = keyColorG,
                color = Color(0xFFA6E3A1),
                onChanged = { onKeyColorChanged(keyColorR, it, keyColorB) }
            )
            ChromaSlider(
                label = stringResource(R.string.chroma_blue),
                value = keyColorB,
                color = Color(0xFF89B4FA),
                onChanged = { onKeyColorChanged(keyColorR, keyColorG, it) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Sapphire) {
            Text(
                text = stringResource(R.string.panel_chroma_key_refinement),
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Raise similarity to catch more of the screen, add smoothness to soften harsh edges, and suppress spill once the matte feels stable.",
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )

            ChromaSlider(
                label = stringResource(R.string.chroma_similarity),
                value = similarity,
                color = Mocha.Green,
                onChanged = onSimilarityChanged
            )
            ChromaSlider(
                label = stringResource(R.string.chroma_smoothness),
                value = smoothness,
                color = Mocha.Sapphire,
                onChanged = onSmoothnessChanged
            )
            ChromaSlider(
                label = stringResource(R.string.chroma_spill_suppress),
                value = spillSuppression,
                color = Mocha.Yellow,
                onChanged = onSpillChanged
            )
        }
    }
}

@Composable
private fun PipPresetCard(
    preset: PipPreset,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.width(148.dp),
        onClick = onClick,
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .height(84.dp)
                    .background(Mocha.Base, RoundedCornerShape(18.dp))
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(10.dp)
                ) {
                    drawRect(
                        color = Mocha.Subtext0.copy(alpha = 0.18f),
                        topLeft = Offset(4f, 4f),
                        size = Size(size.width - 8f, size.height - 8f),
                        style = Stroke(1.3f)
                    )

                    val pipWidth = size.width * preset.scaleX * 0.72f
                    val pipHeight = size.height * preset.scaleY * 0.72f
                    val pipX = size.width / 2f + preset.posX * size.width / 2f * 0.78f - pipWidth / 2f
                    val pipY = size.height / 2f + preset.posY * size.height / 2f * 0.78f - pipHeight / 2f

                    drawRect(
                        color = accent.copy(alpha = 0.24f),
                        topLeft = Offset(pipX, pipY),
                        size = Size(pipWidth, pipHeight)
                    )
                    drawRect(
                        color = accent,
                        topLeft = Offset(pipX, pipY),
                        size = Size(pipWidth, pipHeight),
                        style = Stroke(1.5f)
                    )
                }
            }

            Text(
                text = preset.name,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pipPresetDescription(preset.name),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall,
                minLines = 2
            )
        }
    }
}

@Composable
private fun KeyColorSwatch(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (selected) Mocha.Mauve else color.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                color = if (selected) Mocha.Mauve else Mocha.Text,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ChromaSlider(
    label: String,
    value: Float,
    color: Color,
    onChanged: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Mocha.Subtext1,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatUnit(value),
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onChanged,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.68f),
                inactiveTrackColor = Mocha.Surface0
            )
        )
    }
}

private data class PipPresetSection(
    val title: String,
    val subtitle: String,
    val accent: Color,
    val presets: List<PipPreset>
)

@Composable
private fun rememberPipSections(): List<PipPresetSection> = listOf(
    PipPresetSection(
        title = "Corners and cams",
        subtitle = "Use these for facecam reactions, webcam inserts, and creator commentary.",
        accent = Mocha.Sapphire,
        presets = pipPresets.filter { it.name in setOf("Top Left", "Top Right", "Bottom Left", "Bottom Right", "Circle Cam", "Center Small") }
    ),
    PipPresetSection(
        title = "Split layouts",
        subtitle = "Balanced side-by-side and stacked frames for interviews, explainers, and comparisons.",
        accent = Mocha.Green,
        presets = pipPresets.filter { it.name in setOf("Left Half", "Right Half", "Top Half", "Bottom Half") }
    ),
    PipPresetSection(
        title = "Hero treatments",
        subtitle = "Larger overlays for lower-thirds, focus windows, and full takeover layouts.",
        accent = Mocha.Peach,
        presets = pipPresets.filter { it.name in setOf("Lower Third", "Full Screen") }
    )
)

private fun pipPresetDescription(name: String): String = when (name) {
    "Top Left" -> "Classic reaction-cam position with the frame out of the subtitle lane."
    "Top Right" -> "Great when lower-third graphics or captions sit on the left."
    "Bottom Left" -> "Keeps the inset near the presenter while leaving the top clean."
    "Bottom Right" -> "A familiar commentary layout for tutorials and gaming edits."
    "Center Small" -> "Floating inset for quick comparisons or cutaway emphasis."
    "Left Half" -> "Balanced split for side-by-side demos or dual interviews."
    "Right Half" -> "Use when the main subject should remain left-weighted."
    "Top Half" -> "Stacked layout for narration over reference footage."
    "Bottom Half" -> "Ideal for screen records with presenter footage beneath."
    "Full Screen" -> "Take over the frame and reset the clip to a neutral full-size layout."
    "Lower Third" -> "Wide overlay band for presenter boxes and callout plates."
    "Circle Cam" -> "Compact round-cam style framing for creator and stream looks."
    else -> "Start with this layout, then refine scale and transform if needed."
}

private fun formatUnit(value: Float): String = "%.2f".format(value)
