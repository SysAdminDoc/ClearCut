package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.ClearCutContentColors
import com.novacut.editor.ui.theme.LocalClearCutColors
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R

enum class PipPresetId {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER_SMALL,
    LEFT_HALF,
    RIGHT_HALF,
    TOP_HALF,
    BOTTOM_HALF,
    FULL_SCREEN,
    LOWER_THIRD,
    CIRCLE_CAM
}

data class PipPreset(
    val id: PipPresetId,
    val posX: Float,
    val posY: Float,
    val scaleX: Float,
    val scaleY: Float
)

val pipPresets = listOf(
    PipPreset(PipPresetId.TOP_LEFT, -0.55f, -0.55f, 0.35f, 0.35f),
    PipPreset(PipPresetId.TOP_RIGHT, 0.55f, -0.55f, 0.35f, 0.35f),
    PipPreset(PipPresetId.BOTTOM_LEFT, -0.55f, 0.55f, 0.35f, 0.35f),
    PipPreset(PipPresetId.BOTTOM_RIGHT, 0.55f, 0.55f, 0.35f, 0.35f),
    PipPreset(PipPresetId.CENTER_SMALL, 0f, 0f, 0.4f, 0.4f),
    PipPreset(PipPresetId.LEFT_HALF, -0.5f, 0f, 0.5f, 1f),
    PipPreset(PipPresetId.RIGHT_HALF, 0.5f, 0f, 0.5f, 1f),
    PipPreset(PipPresetId.TOP_HALF, 0f, -0.5f, 1f, 0.5f),
    PipPreset(PipPresetId.BOTTOM_HALF, 0f, 0.5f, 1f, 0.5f),
    PipPreset(PipPresetId.FULL_SCREEN, 0f, 0f, 1f, 1f),
    PipPreset(PipPresetId.LOWER_THIRD, 0f, 0.6f, 0.8f, 0.25f),
    PipPreset(PipPresetId.CIRCLE_CAM, 0.6f, -0.6f, 0.25f, 0.25f)
)

@Composable
fun PipPresetsPanel(
    onPresetSelected: (PipPreset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    val sections = rememberPipSections()

    PremiumEditorPanel(
        title = stringResource(R.string.pip_title),
        subtitle = stringResource(R.string.pip_panel_subtitle),
        icon = Icons.Default.PictureInPicture,
        accent = ClearCutAccents.Sapphire,
        onClose = onClose,
        modifier = modifier.heightIn(max = 520.dp),
        scrollable = true
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Sapphire) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = pluralStringResource(
                        R.plurals.pip_layout_count,
                        pipPresets.size,
                        pipPresets.size,
                    ),
                    accent = ClearCutAccents.Sapphire
                )
                PremiumPanelPill(
                    text = stringResource(R.string.pip_layout_categories),
                    accent = ClearCutAccents.Teal
                )
            }

            Text(
                text = stringResource(R.string.pip_layout_presets),
                color = ClearCutAccents.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = stringResource(R.string.pip_layout_presets_description),
                color = semanticColors.subtext,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = section.accent) {
                Text(
                    text = stringResource(section.titleRes),
                    color = section.accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(section.subtitleRes),
                    color = semanticColors.subtext,
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
    val semanticColors = LocalClearCutColors.current
    val keyColor = Color(
        red = keyColorR.coerceIn(0f, 1f),
        green = keyColorG.coerceIn(0f, 1f),
        blue = keyColorB.coerceIn(0f, 1f)
    )

    PremiumEditorPanel(
        title = stringResource(R.string.panel_chroma_key_title),
        subtitle = stringResource(R.string.panel_chroma_key_subtitle),
        icon = Icons.Default.Visibility,
        accent = ClearCutAccents.Green,
        onClose = onClose,
        modifier = modifier.heightIn(max = 560.dp),
        scrollable = true,
        headerActions = {
            PremiumPanelIconButton(
                icon = Icons.Default.Visibility,
                contentDescription = stringResource(R.string.panel_chroma_key_alpha_matte),
                onClick = onShowAlphaMatte,
                tint = ClearCutAccents.Peach
            )
        }
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Green) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = stringResource(R.string.chroma_similarity_format, formatUnit(similarity)),
                    accent = ClearCutAccents.Green
                )
                PremiumPanelPill(
                    text = stringResource(R.string.chroma_smoothness_format, formatUnit(smoothness)),
                    accent = ClearCutAccents.Sapphire
                )
                PremiumPanelPill(
                    text = stringResource(R.string.chroma_spill_format, formatUnit(spillSuppression)),
                    accent = ClearCutAccents.Yellow
                )
            }

            Text(
                text = stringResource(R.string.chroma_key_source_title),
                color = ClearCutAccents.Rosewater,
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
                    text = stringResource(R.string.chroma_key_source_description),
                    color = semanticColors.subtext,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Peach) {
            Text(
                text = stringResource(R.string.panel_chroma_key_color),
                color = ClearCutAccents.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyColorSwatch(
                    label = stringResource(R.string.chroma_green),
                    color = ClearCutContentColors.ChromaGreen,
                    selected = keyColorG > 0.8f && keyColorR < 0.3f && keyColorB < 0.3f,
                    onClick = { onKeyColorChanged(0f, 1f, 0f) }
                )
                KeyColorSwatch(
                    label = stringResource(R.string.chroma_blue),
                    color = ClearCutContentColors.ChromaBlue,
                    selected = keyColorB > 0.8f && keyColorR < 0.3f && keyColorG < 0.3f,
                    onClick = { onKeyColorChanged(0f, 0f, 1f) }
                )
                KeyColorSwatch(
                    label = stringResource(R.string.chroma_red),
                    color = ClearCutContentColors.ChromaRed,
                    selected = keyColorR > 0.8f && keyColorG < 0.3f && keyColorB < 0.3f,
                    onClick = { onKeyColorChanged(1f, 0f, 0f) }
                )
            }

            ChromaSlider(
                label = stringResource(R.string.chroma_red),
                value = keyColorR,
                color = ClearCutAccents.Red,
                onChanged = { onKeyColorChanged(it, keyColorG, keyColorB) }
            )
            ChromaSlider(
                label = stringResource(R.string.chroma_green),
                value = keyColorG,
                color = ClearCutAccents.Green,
                onChanged = { onKeyColorChanged(keyColorR, it, keyColorB) }
            )
            ChromaSlider(
                label = stringResource(R.string.chroma_blue),
                value = keyColorB,
                color = ClearCutAccents.Blue,
                onChanged = { onKeyColorChanged(keyColorR, keyColorG, it) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Sapphire) {
            Text(
                text = stringResource(R.string.panel_chroma_key_refinement),
                color = ClearCutAccents.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = stringResource(R.string.chroma_refinement_description),
                color = semanticColors.subtext,
                style = MaterialTheme.typography.bodyMedium
            )

            ChromaSlider(
                label = stringResource(R.string.chroma_similarity),
                value = similarity,
                color = ClearCutAccents.Green,
                onChanged = onSimilarityChanged
            )
            ChromaSlider(
                label = stringResource(R.string.chroma_smoothness),
                value = smoothness,
                color = ClearCutAccents.Sapphire,
                onChanged = onSmoothnessChanged
            )
            ChromaSlider(
                label = stringResource(R.string.chroma_spill_suppress),
                value = spillSuppression,
                color = ClearCutAccents.Yellow,
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
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = Modifier.width(148.dp),
        onClick = onClick,
        color = semanticColors.panelHighest,
        shape = RoundedCornerShape(12.dp),
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
                    .background(semanticColors.surfaceBase, RoundedCornerShape(12.dp))
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(10.dp)
                ) {
                    drawRect(
                        color = semanticColors.subtext.copy(alpha = 0.18f),
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
                text = stringResource(preset.id.nameRes()),
                color = semanticColors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(preset.id.descriptionRes()),
                color = semanticColors.subtext,
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
    val semanticColors = LocalClearCutColors.current
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (selected) ClearCutAccents.Mauve else color.copy(alpha = 0.28f)
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
                color = if (selected) ClearCutAccents.Mauve else semanticColors.text,
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
    val semanticColors = LocalClearCutColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = semanticColors.subtextStrong,
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
                inactiveTrackColor = semanticColors.surfaceLow
            )
        )
    }
}

private data class PipPresetSection(
    val titleRes: Int,
    val subtitleRes: Int,
    val accent: Color,
    val presets: List<PipPreset>
)

@Composable
private fun rememberPipSections(): List<PipPresetSection> = listOf(
    PipPresetSection(
        titleRes = R.string.pip_section_corners_title,
        subtitleRes = R.string.pip_section_corners_description,
        accent = ClearCutAccents.Sapphire,
        presets = pipPresets.filter {
            it.id in setOf(
                PipPresetId.TOP_LEFT,
                PipPresetId.TOP_RIGHT,
                PipPresetId.BOTTOM_LEFT,
                PipPresetId.BOTTOM_RIGHT,
                PipPresetId.CIRCLE_CAM,
                PipPresetId.CENTER_SMALL,
            )
        }
    ),
    PipPresetSection(
        titleRes = R.string.pip_section_split_title,
        subtitleRes = R.string.pip_section_split_description,
        accent = ClearCutAccents.Green,
        presets = pipPresets.filter {
            it.id in setOf(
                PipPresetId.LEFT_HALF,
                PipPresetId.RIGHT_HALF,
                PipPresetId.TOP_HALF,
                PipPresetId.BOTTOM_HALF,
            )
        }
    ),
    PipPresetSection(
        titleRes = R.string.pip_section_hero_title,
        subtitleRes = R.string.pip_section_hero_description,
        accent = ClearCutAccents.Peach,
        presets = pipPresets.filter { it.id in setOf(PipPresetId.LOWER_THIRD, PipPresetId.FULL_SCREEN) }
    )
)

private fun PipPresetId.nameRes(): Int = when (this) {
    PipPresetId.TOP_LEFT -> R.string.pip_preset_top_left_name
    PipPresetId.TOP_RIGHT -> R.string.pip_preset_top_right_name
    PipPresetId.BOTTOM_LEFT -> R.string.pip_preset_bottom_left_name
    PipPresetId.BOTTOM_RIGHT -> R.string.pip_preset_bottom_right_name
    PipPresetId.CENTER_SMALL -> R.string.pip_preset_center_small_name
    PipPresetId.LEFT_HALF -> R.string.pip_preset_left_half_name
    PipPresetId.RIGHT_HALF -> R.string.pip_preset_right_half_name
    PipPresetId.TOP_HALF -> R.string.pip_preset_top_half_name
    PipPresetId.BOTTOM_HALF -> R.string.pip_preset_bottom_half_name
    PipPresetId.FULL_SCREEN -> R.string.pip_preset_full_screen_name
    PipPresetId.LOWER_THIRD -> R.string.pip_preset_lower_third_name
    PipPresetId.CIRCLE_CAM -> R.string.pip_preset_circle_cam_name
}

private fun PipPresetId.descriptionRes(): Int = when (this) {
    PipPresetId.TOP_LEFT -> R.string.pip_preset_top_left_description
    PipPresetId.TOP_RIGHT -> R.string.pip_preset_top_right_description
    PipPresetId.BOTTOM_LEFT -> R.string.pip_preset_bottom_left_description
    PipPresetId.BOTTOM_RIGHT -> R.string.pip_preset_bottom_right_description
    PipPresetId.CENTER_SMALL -> R.string.pip_preset_center_small_description
    PipPresetId.LEFT_HALF -> R.string.pip_preset_left_half_description
    PipPresetId.RIGHT_HALF -> R.string.pip_preset_right_half_description
    PipPresetId.TOP_HALF -> R.string.pip_preset_top_half_description
    PipPresetId.BOTTOM_HALF -> R.string.pip_preset_bottom_half_description
    PipPresetId.FULL_SCREEN -> R.string.pip_preset_full_screen_description
    PipPresetId.LOWER_THIRD -> R.string.pip_preset_lower_third_description
    PipPresetId.CIRCLE_CAM -> R.string.pip_preset_circle_cam_description
}

private fun formatUnit(value: Float): String = "%.2f".format(value)
