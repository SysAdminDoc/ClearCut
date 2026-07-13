package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.CaptionAccessibilityPreset
import com.novacut.editor.model.CaptionStyleTemplate
import com.novacut.editor.model.CaptionTemplateType
import com.novacut.editor.model.TextAnimation
import com.novacut.editor.model.isAccessibilityPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptionStyleGallery(
    onStyleSelected: (CaptionStyleTemplate) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    installedStyles: List<CaptionStyleTemplate> = emptyList(),
) {
    val semanticColors = LocalClearCutColors.current
    val templates = remember(installedStyles) { defaultTemplates() + installedStyles }
    val accessibilityTemplates = remember(templates) {
        templates.filter { it.isAccessibilityPreset }
    }
    val karaokeTemplates = remember(templates) {
        templates.filter { !it.isAccessibilityPreset && (it.wordByWord || it.type == CaptionTemplateType.KARAOKE) }
    }
    val editorialTemplates = remember(templates) {
        templates.filter { !it.isAccessibilityPreset && !it.wordByWord && it.type != CaptionTemplateType.KARAOKE }
    }

    PremiumEditorPanel(
        title = stringResource(R.string.caption_styles_title),
        subtitle = stringResource(R.string.caption_styles_subtitle),
        icon = Icons.Default.Subtitles,
        accent = ClearCutAccents.Mauve,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.caption_styles_close_cd),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Mauve) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactLayout = maxWidth < 420.dp
                if (isCompactLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(
                                text = stringResource(R.string.caption_styles_library_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = semanticColors.text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.caption_styles_library_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = semanticColors.subtext
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(
                                text = stringResource(R.string.caption_styles_looks_format, templates.size),
                                accent = ClearCutAccents.Blue
                            )
                            PremiumPanelPill(
                                text = stringResource(R.string.caption_styles_motion_format, karaokeTemplates.size),
                                accent = ClearCutAccents.Yellow
                            )
                            PremiumPanelPill(
                                text = stringResource(R.string.caption_styles_accessible_format, accessibilityTemplates.size),
                                accent = ClearCutAccents.Green
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.caption_styles_library_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = semanticColors.text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.caption_styles_library_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = semanticColors.subtext
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(
                                text = stringResource(R.string.caption_styles_looks_format, templates.size),
                                accent = ClearCutAccents.Blue
                            )
                            PremiumPanelPill(
                                text = stringResource(R.string.caption_styles_motion_format, karaokeTemplates.size),
                                accent = ClearCutAccents.Yellow
                            )
                            PremiumPanelPill(
                                text = stringResource(R.string.caption_styles_accessible_format, accessibilityTemplates.size),
                                accent = ClearCutAccents.Green
                            )
                        }
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactLayout = maxWidth < 360.dp
                val columns = when {
                    isCompactLayout -> 1
                    maxWidth < 560.dp -> 2
                    else -> 3
                }
                val metricWidth = (maxWidth - (8 * (columns - 1)).dp) / columns.toFloat()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StyleMetric(
                        title = "Karaoke",
                        value = karaokeTemplates.size.toString(),
                        accent = ClearCutAccents.Yellow,
                        modifier = Modifier.width(metricWidth.coerceAtLeast(0.dp))
                    )
                    StyleMetric(
                        title = "Editorial",
                        value = editorialTemplates.size.toString(),
                        accent = ClearCutAccents.Mauve,
                        modifier = Modifier.width(metricWidth.coerceAtLeast(0.dp))
                    )
                    StyleMetric(
                        title = "Accessible",
                        value = accessibilityTemplates.size.toString(),
                        accent = ClearCutAccents.Green,
                        modifier = Modifier.width(metricWidth.coerceAtLeast(0.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        CaptionStyleSection(
            title = stringResource(R.string.caption_accessibility_title),
            subtitle = stringResource(R.string.caption_accessibility_subtitle),
            accent = ClearCutAccents.Green,
            sectionContentDescription = stringResource(R.string.cd_caption_accessibility_section),
            templates = accessibilityTemplates,
            onStyleSelected = onStyleSelected
        )

        Spacer(modifier = Modifier.height(12.dp))

        CaptionStyleSection(
            title = stringResource(R.string.caption_karaoke_title),
            subtitle = stringResource(R.string.caption_styles_karaoke_subtitle),
            accent = ClearCutAccents.Yellow,
            sectionContentDescription = stringResource(R.string.cd_karaoke_section),
            templates = karaokeTemplates,
            onStyleSelected = onStyleSelected
        )

        Spacer(modifier = Modifier.height(12.dp))

        CaptionStyleSection(
            title = stringResource(R.string.caption_editorial_title),
            subtitle = stringResource(R.string.caption_editorial_subtitle),
            accent = ClearCutAccents.Blue,
            sectionContentDescription = stringResource(R.string.cd_caption_styles_section),
            templates = editorialTemplates,
            onStyleSelected = onStyleSelected
        )
    }
}

@Composable
private fun StyleMetric(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = semanticColors.subtext
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptionStyleSection(
    title: String,
    subtitle: String,
    accent: Color,
    sectionContentDescription: String,
    templates: List<CaptionStyleTemplate>,
    onStyleSelected: (CaptionStyleTemplate) -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    PremiumPanelCard(accent = accent) {
        Column(
            modifier = Modifier.semantics { contentDescription = sectionContentDescription },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (accent == ClearCutAccents.Yellow) Icons.Default.MusicNote else Icons.Default.Subtitles,
                    contentDescription = title,
                    tint = accent
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactLayout = maxWidth < 560.dp
                val cardWidth = if (isCompactLayout) {
                    maxWidth
                } else {
                    (maxWidth - 10.dp) / 2
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    templates.forEach { template ->
                        CaptionStyleCard(
                            template = template,
                            accent = accent,
                            modifier = Modifier.width(cardWidth.coerceAtLeast(0.dp)),
                            onClick = { onStyleSelected(template) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptionStyleCard(
    template: CaptionStyleTemplate,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    val accessibilityLabel = if (template.isAccessibilityPreset) {
        stringResource(R.string.caption_style_accessibility_label, template.accessibilityPreset.displayName)
    } else {
        null
    }
    Surface(
        modifier = modifier
            .semantics {
                contentDescription = listOfNotNull(
                    template.type.displayName,
                    accessibilityLabel,
                    template.animation.displayName
                ).joinToString(", ")
            }
            .clickable(onClick = onClick),
        color = semanticColors.panelRaised,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, semanticColors.cardStroke)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.28f),
                                semanticColors.panelHighest,
                                semanticColors.surfaceBase
                            )
                        )
                    ),
                contentAlignment = when {
                    template.positionY > 0.7f -> Alignment.BottomCenter
                    template.positionY < 0.3f -> Alignment.TopCenter
                    else -> Alignment.Center
                }
            ) {
                CaptionStylePreview(template = template)
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = template.type.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = semanticColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = template.animation.displayName,
                        accent = accent
                    )
                    Text(
                        text = if (template.wordByWord) {
                            stringResource(R.string.caption_style_word_by_word)
                        } else {
                            stringResource(R.string.caption_style_static_look)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = semanticColors.subtext
                    )
                    if (accessibilityLabel != null) {
                        Text(
                            text = accessibilityLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = ClearCutAccents.Green
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionStylePreview(template: CaptionStyleTemplate) {
    val previewText = when {
        template.wordByWord -> "Hello World"
        template.accessibilityPreset == CaptionAccessibilityPreset.REDUCED_MOTION -> "No Motion"
        template.accessibilityPreset == CaptionAccessibilityPreset.LARGE_TEXT -> "Large Text"
        template.isAccessibilityPreset -> "Readable Text"
        else -> "Sample Text"
    }
    val textColor = Color(template.textColor)
    val backgroundColor = Color(template.backgroundColor)
    val outlineColor = Color(template.outlineColor)
    val shadowColor = Color(template.shadowColor)
    val highlightColor = Color(template.highlightColor)
    val previewSize = (template.fontSize * 0.48f).coerceIn(10f, 22f)
    val fontFamily = when (template.fontFamily) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }

    if (template.wordByWord) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Hello ",
                color = highlightColor,
                fontSize = previewSize.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                style = TextStyle(
                    shadow = if (template.outlineWidth > 0f) {
                        Shadow(outlineColor, Offset(1f, 1f), 2f)
                    } else {
                        Shadow(shadowColor, Offset(1f, 1f), 3f)
                    }
                )
            )
            Text(
                text = "World",
                color = textColor,
                fontSize = previewSize.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                style = TextStyle(
                    shadow = if (template.outlineWidth > 0f) {
                        Shadow(outlineColor, Offset(1f, 1f), 2f)
                    } else {
                        Shadow(shadowColor, Offset(1f, 1f), 3f)
                    }
                )
            )
        }
    } else {
        val backgroundAlpha = (template.backgroundColor shr 24 and 0xFF) / 255f
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 12.dp)
                .then(
                    if (backgroundAlpha > 0.05f) {
                        Modifier
                            .background(backgroundColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = previewText,
                color = textColor,
                fontSize = previewSize.sp,
                fontWeight = if (
                    template.type == CaptionTemplateType.BOLD_CENTER ||
                    template.accessibilityPreset == CaptionAccessibilityPreset.LARGE_TEXT
                ) {
                    FontWeight.ExtraBold
                } else {
                    FontWeight.Medium
                },
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = if (template.outlineWidth > 0f) {
                        Shadow(outlineColor, Offset(1f, 1f), template.outlineWidth)
                    } else {
                        Shadow(shadowColor, Offset(1f, 1f), 3f)
                    }
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun defaultTemplates(): List<CaptionStyleTemplate> = listOf(
    CaptionStyleTemplate(
        type = CaptionTemplateType.HIGH_CONTRAST,
        fontFamily = "sans-serif-medium",
        fontSize = 28f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0xF0000000,
        outlineColor = 0xFF000000,
        outlineWidth = 3f,
        shadowColor = 0x00000000,
        positionY = 0.86f,
        animation = TextAnimation.NONE,
        accessibilityPreset = CaptionAccessibilityPreset.WCAG_AA_CONTRAST
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.LARGE_TEXT,
        fontFamily = "sans-serif-medium",
        fontSize = 40f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0xF0000000,
        outlineColor = 0xFF000000,
        outlineWidth = 4f,
        shadowColor = 0x00000000,
        positionY = 0.78f,
        animation = TextAnimation.NONE,
        accessibilityPreset = CaptionAccessibilityPreset.LARGE_TEXT
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.REDUCED_MOTION,
        fontFamily = "sans-serif",
        fontSize = 28f,
        textColor = 0xFFF9E2AF,
        backgroundColor = 0xF0000000,
        outlineColor = 0xFF000000,
        outlineWidth = 2f,
        shadowColor = 0x00000000,
        positionY = 0.86f,
        animation = TextAnimation.NONE,
        accessibilityPreset = CaptionAccessibilityPreset.REDUCED_MOTION
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.KARAOKE,
        fontFamily = "sans-serif",
        fontSize = 28f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0x00000000,
        outlineWidth = 2f,
        outlineColor = 0xFF000000,
        positionY = 0.85f,
        animation = TextAnimation.NONE,
        highlightColor = 0xFFFFD700,
        wordByWord = true
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.WORD_BY_WORD,
        fontFamily = "sans-serif",
        fontSize = 32f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0x00000000,
        outlineWidth = 0f,
        positionY = 0.5f,
        animation = TextAnimation.SCALE,
        highlightColor = 0xFFCBA6F7,
        wordByWord = true
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.BOUNCE,
        fontFamily = "sans-serif",
        fontSize = 30f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0x00000000,
        outlineWidth = 2f,
        outlineColor = 0xFF000000,
        positionY = 0.5f,
        animation = TextAnimation.BOUNCE,
        highlightColor = 0xFFF38BA8,
        wordByWord = true
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.CLASSIC,
        fontFamily = "sans-serif",
        fontSize = 24f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0xCC000000,
        positionY = 0.85f,
        animation = TextAnimation.FADE
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.GLOW,
        fontFamily = "sans-serif",
        fontSize = 26f,
        textColor = 0xFFCBA6F7,
        backgroundColor = 0x00000000,
        shadowColor = 0xFFCBA6F7,
        shadowOffsetX = 0f,
        shadowOffsetY = 0f,
        positionY = 0.5f,
        animation = TextAnimation.FADE
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.OUTLINE,
        fontFamily = "sans-serif",
        fontSize = 28f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0x00000000,
        outlineColor = 0xFF000000,
        outlineWidth = 3f,
        positionY = 0.85f,
        animation = TextAnimation.FADE
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.SHADOW_POP,
        fontFamily = "sans-serif",
        fontSize = 28f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0x00000000,
        shadowColor = 0xFF000000,
        shadowOffsetX = 4f,
        shadowOffsetY = 4f,
        positionY = 0.85f,
        animation = TextAnimation.SLIDE_UP
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.GRADIENT,
        fontFamily = "sans-serif",
        fontSize = 26f,
        textColor = 0xFFF9E2AF,
        backgroundColor = 0x00000000,
        outlineColor = 0xFFFAB387,
        outlineWidth = 1f,
        positionY = 0.5f,
        animation = TextAnimation.FADE
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.TYPEWRITER,
        fontFamily = "monospace",
        fontSize = 22f,
        textColor = 0xFFA6E3A1,
        backgroundColor = 0xCC000000,
        positionY = 0.85f,
        animation = TextAnimation.TYPEWRITER
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.NEON,
        fontFamily = "sans-serif",
        fontSize = 28f,
        textColor = 0xFF89DCEB,
        backgroundColor = 0x00000000,
        shadowColor = 0xFF89DCEB,
        shadowOffsetX = 0f,
        shadowOffsetY = 0f,
        outlineColor = 0xFF89DCEB,
        outlineWidth = 1f,
        positionY = 0.5f,
        animation = TextAnimation.FADE
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.COMIC,
        fontFamily = "cursive",
        fontSize = 30f,
        textColor = 0xFF1E1E2E,
        backgroundColor = 0xFFF9E2AF,
        outlineColor = 0xFF000000,
        outlineWidth = 2f,
        positionY = 0.4f,
        animation = TextAnimation.BOUNCE
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.MINIMAL,
        fontFamily = "sans-serif",
        fontSize = 20f,
        textColor = 0xFFCDD6F4,
        backgroundColor = 0x00000000,
        positionY = 0.9f,
        animation = TextAnimation.FADE
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.BOLD_CENTER,
        fontFamily = "sans-serif",
        fontSize = 36f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0x00000000,
        outlineColor = 0xFF000000,
        outlineWidth = 3f,
        positionY = 0.5f,
        animation = TextAnimation.SCALE
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.LOWER_THIRD,
        fontFamily = "sans-serif",
        fontSize = 22f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0xCC1E1E2E,
        positionY = 0.88f,
        animation = TextAnimation.SLIDE_LEFT
    ),
    CaptionStyleTemplate(
        type = CaptionTemplateType.SUBTITLE,
        fontFamily = "sans-serif",
        fontSize = 24f,
        textColor = 0xFFFFFFFF,
        backgroundColor = 0x80000000,
        positionY = 0.85f,
        animation = TextAnimation.FADE
    )
)
