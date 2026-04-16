@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha
import java.util.Locale

// Pre-built text templates
val builtInTextTemplates = listOf(
    // Lower thirds
    TextTemplate(
        id = "lt_modern", name = "Modern Lower Third",
        category = TextTemplateCategory.LOWER_THIRD,
        layers = listOf(
            TextOverlay(text = "SPEAKER NAME", fontSize = 32f, color = 0xFFFFFFFF, bold = true,
                positionX = 0.15f, positionY = 0.82f, alignment = TextAlignment.LEFT,
                strokeWidth = 0f, backgroundColor = 0xCC1E1E2E,
                animationIn = TextAnimation.SLIDE_LEFT, animationOut = TextAnimation.SLIDE_LEFT),
            TextOverlay(text = "Title or Role", fontSize = 22f, color = 0xFFCBA6F7,
                positionX = 0.15f, positionY = 0.88f, alignment = TextAlignment.LEFT,
                animationIn = TextAnimation.SLIDE_LEFT, animationOut = TextAnimation.SLIDE_LEFT)
        ),
        durationMs = 4000L
    ),
    TextTemplate(
        id = "lt_minimal", name = "Minimal Lower Third",
        category = TextTemplateCategory.LOWER_THIRD,
        layers = listOf(
            TextOverlay(text = "Name Here", fontSize = 28f, color = 0xFFFFFFFF,
                positionX = 0.5f, positionY = 0.85f, alignment = TextAlignment.CENTER,
                animationIn = TextAnimation.FADE, animationOut = TextAnimation.FADE,
                letterSpacing = 4f)
        ),
        durationMs = 3000L
    ),
    TextTemplate(
        id = "lt_news", name = "News Banner",
        category = TextTemplateCategory.LOWER_THIRD,
        layers = listOf(
            TextOverlay(text = "BREAKING NEWS", fontSize = 20f, color = 0xFFFFFFFF, bold = true,
                positionX = 0.5f, positionY = 0.82f, backgroundColor = 0xFFF38BA8,
                animationIn = TextAnimation.SLIDE_UP, animationOut = TextAnimation.SLIDE_DOWN),
            TextOverlay(text = "Headline text goes here", fontSize = 26f, color = 0xFFFFFFFF,
                positionX = 0.5f, positionY = 0.88f, backgroundColor = 0xDD1E1E2E,
                animationIn = TextAnimation.SLIDE_UP, animationOut = TextAnimation.SLIDE_DOWN)
        ),
        durationMs = 5000L
    ),

    // Title cards
    TextTemplate(
        id = "tc_centered", name = "Centered Title",
        category = TextTemplateCategory.TITLE_CARD,
        layers = listOf(
            TextOverlay(text = "YOUR TITLE", fontSize = 56f, color = 0xFFFFFFFF, bold = true,
                positionX = 0.5f, positionY = 0.45f, letterSpacing = 6f,
                animationIn = TextAnimation.SCALE, animationOut = TextAnimation.FADE,
                shadowOffsetX = 2f, shadowOffsetY = 2f, shadowBlur = 8f),
            TextOverlay(text = "Subtitle or tagline", fontSize = 24f, color = 0xFFA6ADC8,
                positionX = 0.5f, positionY = 0.55f,
                animationIn = TextAnimation.FADE, animationOut = TextAnimation.FADE)
        ),
        durationMs = 4000L
    ),
    TextTemplate(
        id = "tc_glitch", name = "Glitch Title",
        category = TextTemplateCategory.TITLE_CARD,
        layers = listOf(
            TextOverlay(text = "GLITCH", fontSize = 64f, color = 0xFFF38BA8, bold = true,
                positionX = 0.5f, positionY = 0.5f, letterSpacing = 8f,
                animationIn = TextAnimation.GLITCH, animationOut = TextAnimation.GLITCH,
                glowColor = 0xFFF38BA8, glowRadius = 10f)
        ),
        durationMs = 3000L
    ),
    TextTemplate(
        id = "tc_cinematic", name = "Cinematic",
        category = TextTemplateCategory.TITLE_CARD,
        layers = listOf(
            TextOverlay(text = "CINEMATIC", fontSize = 48f, color = 0xFFF9E2AF, bold = true,
                positionX = 0.5f, positionY = 0.5f, letterSpacing = 12f,
                fontFamily = "serif",
                animationIn = TextAnimation.FADE, animationOut = TextAnimation.FADE,
                shadowOffsetY = 3f, shadowBlur = 12f)
        ),
        durationMs = 4000L
    ),

    // End screens
    TextTemplate(
        id = "es_subscribe", name = "Subscribe CTA",
        category = TextTemplateCategory.END_SCREEN,
        layers = listOf(
            TextOverlay(text = "SUBSCRIBE", fontSize = 40f, color = 0xFFF38BA8, bold = true,
                positionX = 0.5f, positionY = 0.4f,
                animationIn = TextAnimation.BOUNCE, animationOut = TextAnimation.FADE,
                glowColor = 0xFFF38BA8, glowRadius = 8f),
            TextOverlay(text = "for more content like this", fontSize = 22f, color = 0xFFCDD6F4,
                positionX = 0.5f, positionY = 0.5f,
                animationIn = TextAnimation.FADE, animationOut = TextAnimation.FADE)
        ),
        durationMs = 5000L
    ),
    TextTemplate(
        id = "es_thanks", name = "Thanks for Watching",
        category = TextTemplateCategory.END_SCREEN,
        layers = listOf(
            TextOverlay(text = "Thanks for watching!", fontSize = 44f, color = 0xFFFFFFFF,
                positionX = 0.5f, positionY = 0.45f, fontFamily = "cursive",
                animationIn = TextAnimation.SCALE, animationOut = TextAnimation.FADE)
        ),
        durationMs = 4000L
    ),

    // Call to action
    TextTemplate(
        id = "cta_link", name = "Link CTA",
        category = TextTemplateCategory.CALL_TO_ACTION,
        layers = listOf(
            TextOverlay(text = "LINK IN BIO", fontSize = 28f, color = 0xFF1E1E2E, bold = true,
                positionX = 0.5f, positionY = 0.85f, backgroundColor = 0xFFF9E2AF,
                animationIn = TextAnimation.SLIDE_UP, animationOut = TextAnimation.SLIDE_DOWN,
                strokeWidth = 0f)
        ),
        durationMs = 3000L
    ),

    // Social
    TextTemplate(
        id = "social_handle", name = "Social Handle",
        category = TextTemplateCategory.SOCIAL,
        layers = listOf(
            TextOverlay(text = "@yourhandle", fontSize = 30f, color = 0xFFCBA6F7,
                positionX = 0.5f, positionY = 0.9f,
                animationIn = TextAnimation.ELASTIC, animationOut = TextAnimation.FADE,
                letterSpacing = 2f)
        ),
        durationMs = 3000L
    ),

    // Minimal
    TextTemplate(
        id = "min_quote", name = "Quote",
        category = TextTemplateCategory.MINIMAL,
        layers = listOf(
            TextOverlay(text = "\"Your quote here\"", fontSize = 36f, color = 0xFFFFFFFF,
                positionX = 0.5f, positionY = 0.45f, fontFamily = "serif", italic = true,
                animationIn = TextAnimation.FADE, animationOut = TextAnimation.FADE),
            TextOverlay(text = "- Author Name", fontSize = 20f, color = 0xFFA6ADC8,
                positionX = 0.5f, positionY = 0.55f,
                animationIn = TextAnimation.FADE, animationOut = TextAnimation.FADE)
        ),
        durationMs = 4000L
    ),
    TextTemplate(
        id = "min_chapter", name = "Chapter Title",
        category = TextTemplateCategory.MINIMAL,
        layers = listOf(
            TextOverlay(text = "Chapter 1", fontSize = 20f, color = 0xFFA6ADC8,
                positionX = 0.5f, positionY = 0.42f, letterSpacing = 6f,
                animationIn = TextAnimation.FADE, animationOut = TextAnimation.FADE),
            TextOverlay(text = "THE BEGINNING", fontSize = 42f, color = 0xFFFFFFFF, bold = true,
                positionX = 0.5f, positionY = 0.5f, letterSpacing = 4f,
                animationIn = TextAnimation.FADE, animationOut = TextAnimation.FADE)
        ),
        durationMs = 3000L
    )
)

@Composable
fun TextTemplateGallery(
    playheadMs: Long,
    onTemplateSelected: (TextTemplate) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<TextTemplateCategory?>(null) }
    var showAnimated by remember { mutableStateOf(false) }
    val animatedTemplates = remember { animatedTextTemplates() }
    val visibleStaticTemplates = remember(selectedCategory) {
        if (selectedCategory == null) {
            builtInTextTemplates
        } else {
            builtInTextTemplates.filter { it.category == selectedCategory }
        }
    }
    val visibleAnimatedTemplates = remember(selectedCategory) {
        if (selectedCategory == null) {
            animatedTemplates
        } else {
            animatedTemplates.filter { it.category == selectedCategory }
        }
    }
    val accent = if (showAnimated) Mocha.Yellow else Mocha.Sapphire

    PremiumEditorPanel(
        title = stringResource(R.string.panel_text_template_title),
        subtitle = stringResource(R.string.panel_text_template_subtitle),
        icon = Icons.Default.Dashboard,
        accent = accent,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.panel_text_template_close_cd),
        modifier = modifier.heightIn(max = 560.dp)
    ) {
        PremiumPanelCard(accent = accent) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactLayout = maxWidth < 420.dp
                if (isCompactLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(
                                text = stringResource(R.string.panel_text_template_modes_title),
                                color = Mocha.Text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.panel_text_template_modes_description),
                                color = Mocha.Subtext0,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(
                                text = pluralStringResource(
                                    R.plurals.panel_text_template_looks,
                                    builtInTextTemplates.size + animatedTemplates.size,
                                    builtInTextTemplates.size + animatedTemplates.size
                                ),
                                accent = accent
                            )
                            PremiumPanelPill(
                                text = stringResource(
                                    R.string.panel_text_template_insert_at,
                                    formatTemplateTime(playheadMs)
                                ),
                                accent = Mocha.Sky
                            )
                            PremiumPanelPill(
                                text = if (showAnimated) {
                                    stringResource(R.string.panel_text_template_animated)
                                } else {
                                    stringResource(R.string.panel_text_template_static)
                                },
                                accent = Mocha.Pink
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
                                text = stringResource(R.string.panel_text_template_modes_title),
                                color = Mocha.Text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.panel_text_template_modes_description),
                                color = Mocha.Subtext0,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(
                                text = pluralStringResource(
                                    R.plurals.panel_text_template_looks,
                                    builtInTextTemplates.size + animatedTemplates.size,
                                    builtInTextTemplates.size + animatedTemplates.size
                                ),
                                accent = accent
                            )
                            PremiumPanelPill(
                                text = stringResource(
                                    R.string.panel_text_template_insert_at,
                                    formatTemplateTime(playheadMs)
                                ),
                                accent = Mocha.Sky
                            )
                            PremiumPanelPill(
                                text = if (showAnimated) {
                                    stringResource(R.string.panel_text_template_animated)
                                } else {
                                    stringResource(R.string.panel_text_template_static)
                                },
                                accent = Mocha.Pink
                            )
                        }
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactLayout = maxWidth < 420.dp
                if (isCompactLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TemplateModeCard(
                            title = stringResource(R.string.panel_text_template_static),
                            subtitle = stringResource(R.string.panel_text_template_static_subtitle),
                            selected = !showAnimated,
                            accent = Mocha.Sapphire,
                            onClick = { showAnimated = false },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TemplateModeCard(
                            title = stringResource(R.string.panel_text_template_animated),
                            subtitle = stringResource(R.string.panel_text_template_animated_subtitle),
                            selected = showAnimated,
                            accent = Mocha.Yellow,
                            onClick = { showAnimated = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TemplateModeCard(
                            title = stringResource(R.string.panel_text_template_static),
                            subtitle = stringResource(R.string.panel_text_template_static_subtitle),
                            selected = !showAnimated,
                            accent = Mocha.Sapphire,
                            onClick = { showAnimated = false },
                            modifier = Modifier.weight(1f)
                        )
                        TemplateModeCard(
                            title = stringResource(R.string.panel_text_template_animated),
                            subtitle = stringResource(R.string.panel_text_template_animated_subtitle),
                            selected = showAnimated,
                            accent = Mocha.Yellow,
                            onClick = { showAnimated = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TemplateCategoryChip(
                    label = stringResource(R.string.panel_text_template_all),
                    selected = selectedCategory == null,
                    accent = accent,
                    onClick = { selectedCategory = null }
                )
                TextTemplateCategory.entries.forEach { category ->
                    TemplateCategoryChip(
                        label = category.displayName,
                        selected = selectedCategory == category,
                        accent = templateCategoryAccent(category),
                        onClick = { selectedCategory = category }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = if (showAnimated) Mocha.Peach else Mocha.Blue) {
            Text(
                text = if (showAnimated) {
                    stringResource(R.string.panel_text_template_collection_animated_title)
                } else {
                    stringResource(R.string.panel_text_template_collection_static_title)
                },
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = if (showAnimated) {
                    stringResource(R.string.panel_text_template_collection_animated_description)
                } else {
                    stringResource(R.string.panel_text_template_collection_static_description)
                },
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = pluralStringResource(
                        R.plurals.panel_text_template_results,
                        if (showAnimated) visibleAnimatedTemplates.size else visibleStaticTemplates.size,
                        if (showAnimated) visibleAnimatedTemplates.size else visibleStaticTemplates.size
                    ),
                    accent = if (showAnimated) Mocha.Yellow else Mocha.Sapphire
                )
                PremiumPanelPill(
                    text = stringResource(
                        R.string.panel_text_template_category_format,
                        selectedCategory?.displayName ?: stringResource(R.string.panel_text_template_all)
                    ),
                    accent = selectedCategory?.let(::templateCategoryAccent) ?: Mocha.Sky
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (showAnimated) {
            if (visibleAnimatedTemplates.isEmpty()) {
                TemplateEmptyState(accent = Mocha.Peach)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 332.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleAnimatedTemplates, key = { it.id }) { template ->
                        AnimatedTemplateCard(
                            template = template,
                            onClick = { onTemplateSelected(template.toTemplate()) }
                        )
                    }
                }
            }
        } else {
            if (visibleStaticTemplates.isEmpty()) {
                TemplateEmptyState(accent = Mocha.Blue)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 372.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleStaticTemplates, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            onClick = { onTemplateSelected(template) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateEmptyState(
    accent: Color,
    modifier: Modifier = Modifier
) {
    PremiumPanelCard(accent = accent, modifier = modifier) {
        Text(
            text = stringResource(R.string.panel_text_template_empty_title),
            color = Mocha.Text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.panel_text_template_empty_body),
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun TemplateCard(
    template: TextTemplate,
    onClick: () -> Unit
) {
    val accent = templateCategoryAccent(template.category)

    Surface(
        onClick = onClick,
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Mocha.CardStrokeStrong.copy(alpha = 0.9f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.24f), Color(0xFF181825), Mocha.Panel)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    template.layers.take(2).forEach { layer ->
                        val previewFontSize = (layer.fontSize * 0.33f).coerceIn(10f, 20f)
                        Text(
                            text = layer.text,
                            color = Color(layer.color),
                            fontSize = previewFontSize.sp,
                            fontWeight = if (layer.bold) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                shadow = if (layer.shadowBlur > 0) {
                                    Shadow(
                                        color = Color(layer.shadowColor),
                                        offset = Offset(layer.shadowOffsetX, layer.shadowOffsetY),
                                        blurRadius = layer.shadowBlur
                                    )
                                } else {
                                    null
                                },
                                letterSpacing = (layer.letterSpacing * 0.3f).sp,
                                fontFamily = when (layer.fontFamily) {
                                    "serif" -> FontFamily.Serif
                                    "monospace" -> FontFamily.Monospace
                                    "cursive" -> FontFamily.Cursive
                                    else -> FontFamily.SansSerif
                                }
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = template.name,
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = templateCategorySummary(template.category),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 2
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = template.category.displayName,
                        accent = accent
                    )
                    PremiumPanelPill(
                        text = stringResource(
                            R.string.panel_text_template_duration_format,
                            template.durationMs / 1000L
                        ),
                        accent = Mocha.Sky
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateModeCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) accent.copy(alpha = 0.14f) else Mocha.PanelHighest,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.28f) else Mocha.CardStroke
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = if (selected) accent else Mocha.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TemplateCategoryChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) accent.copy(alpha = 0.14f) else Mocha.PanelHighest,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.28f) else Mocha.CardStroke
        )
    ) {
        Text(
            text = label,
            color = if (selected) accent else Mocha.Subtext0,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun AnimatedTemplateCard(
    template: AnimatedTextTemplateDefinition,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, template.accent.copy(alpha = 0.24f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                template.accent.copy(alpha = 0.26f),
                                Color(0xFF181825),
                                Mocha.Panel
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = template.previewText,
                        color = template.accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = template.previewNote,
                        color = Mocha.Text,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = template.name,
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = template.description,
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 2
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = template.category.displayName,
                        accent = template.accent
                    )
                    PremiumPanelPill(
                        text = template.animation.displayName,
                        accent = Mocha.Pink
                    )
                }
            }
        }
    }
}

private data class AnimatedTextTemplateDefinition(
    val id: String,
    val name: String,
    val category: TextTemplateCategory,
    val previewText: String,
    val previewNote: String,
    val description: String,
    val accent: Color,
    val animation: TextAnimation,
    val durationMs: Long
) {
    fun toTemplate(): TextTemplate {
        val accentArgb = accent.toArgb().toLong()
        return TextTemplate(
            id = id,
            name = name,
            category = category,
            layers = listOf(
                TextOverlay(
                    text = previewText,
                    fontSize = 52f,
                    color = accentArgb,
                    bold = true,
                    positionX = 0.5f,
                    positionY = 0.46f,
                    letterSpacing = 6f,
                    animationIn = animation,
                    animationOut = TextAnimation.FADE,
                    glowColor = accentArgb,
                    glowRadius = 8f
                ),
                TextOverlay(
                    text = previewNote,
                    fontSize = 22f,
                    color = 0xFFCDD6F4,
                    positionX = 0.5f,
                    positionY = 0.57f,
                    animationIn = TextAnimation.FADE,
                    animationOut = TextAnimation.FADE
                )
            ),
            durationMs = durationMs
        )
    }
}

private fun animatedTextTemplates(): List<AnimatedTextTemplateDefinition> = listOf(
    AnimatedTextTemplateDefinition(
        id = "anim_lower_third_slide",
        name = "Slide In Lower Third",
        category = TextTemplateCategory.LOWER_THIRD,
        previewText = "HOST NAME",
        previewNote = "New episode",
        description = "A polished host intro with strong lateral movement.",
        accent = Mocha.Sapphire,
        animation = TextAnimation.SLIDE_LEFT,
        durationMs = 3500L
    ),
    AnimatedTextTemplateDefinition(
        id = "anim_promo_countdown",
        name = "Countdown Burst",
        category = TextTemplateCategory.TITLE_CARD,
        previewText = "3 2 1",
        previewNote = "Launch",
        description = "Great for cold opens, beats, and punchy scene intros.",
        accent = Mocha.Yellow,
        animation = TextAnimation.BOUNCE,
        durationMs = 3000L
    ),
    AnimatedTextTemplateDefinition(
        id = "anim_neon_title",
        name = "Neon Glow",
        category = TextTemplateCategory.TITLE_CARD,
        previewText = "NEON",
        previewNote = "Night drive",
        description = "A vivid hero title with glow-led reveal energy.",
        accent = Mocha.Pink,
        animation = TextAnimation.SCALE,
        durationMs = 3200L
    ),
    AnimatedTextTemplateDefinition(
        id = "anim_subscribe",
        name = "Subscribe Push",
        category = TextTemplateCategory.CALL_TO_ACTION,
        previewText = "SUBSCRIBE",
        previewNote = "Weekly drops",
        description = "A clean CTA for end cards and creator reminders.",
        accent = Mocha.Red,
        animation = TextAnimation.ELASTIC,
        durationMs = 3600L
    ),
    AnimatedTextTemplateDefinition(
        id = "anim_social_handle",
        name = "Handle Reveal",
        category = TextTemplateCategory.SOCIAL,
        previewText = "@NOVA",
        previewNote = "Follow along",
        description = "Fast social ID tag for reels, shorts, and cutdowns.",
        accent = Mocha.Mauve,
        animation = TextAnimation.SLIDE_UP,
        durationMs = 2800L
    ),
    AnimatedTextTemplateDefinition(
        id = "anim_end_screen",
        name = "Thanks Outro",
        category = TextTemplateCategory.END_SCREEN,
        previewText = "THANK YOU",
        previewNote = "See you next cut",
        description = "A softer sign-off treatment for polished endings.",
        accent = Mocha.Teal,
        animation = TextAnimation.FADE,
        durationMs = 4000L
    ),
    AnimatedTextTemplateDefinition(
        id = "anim_quote",
        name = "Quote Drift",
        category = TextTemplateCategory.MINIMAL,
        previewText = "\"BREATHE\"",
        previewNote = "Scene note",
        description = "A restrained pull-quote for narrative or documentary edits.",
        accent = Mocha.Lavender,
        animation = TextAnimation.SLIDE_RIGHT,
        durationMs = 3600L
    ),
    AnimatedTextTemplateDefinition(
        id = "anim_cta_link",
        name = "Link Pulse",
        category = TextTemplateCategory.CALL_TO_ACTION,
        previewText = "LINK IN BIO",
        previewNote = "Open now",
        description = "A bold conversion card designed for vertical social posts.",
        accent = Mocha.Peach,
        animation = TextAnimation.BOUNCE,
        durationMs = 2800L
    )
)

private fun templateCategoryAccent(category: TextTemplateCategory): Color = when (category) {
    TextTemplateCategory.LOWER_THIRD -> Mocha.Sapphire
    TextTemplateCategory.TITLE_CARD -> Mocha.Yellow
    TextTemplateCategory.END_SCREEN -> Mocha.Teal
    TextTemplateCategory.CALL_TO_ACTION -> Mocha.Red
    TextTemplateCategory.SOCIAL -> Mocha.Mauve
    TextTemplateCategory.MINIMAL -> Mocha.Lavender
}

@Composable
private fun templateCategorySummary(category: TextTemplateCategory): String = when (category) {
    TextTemplateCategory.LOWER_THIRD -> stringResource(R.string.panel_text_template_category_lower_third)
    TextTemplateCategory.TITLE_CARD -> stringResource(R.string.panel_text_template_category_title_card)
    TextTemplateCategory.END_SCREEN -> stringResource(R.string.panel_text_template_category_end_screen)
    TextTemplateCategory.CALL_TO_ACTION -> stringResource(R.string.panel_text_template_category_call_to_action)
    TextTemplateCategory.SOCIAL -> stringResource(R.string.panel_text_template_category_social)
    TextTemplateCategory.MINIMAL -> stringResource(R.string.panel_text_template_category_minimal)
}

private fun formatTemplateTime(playheadMs: Long): String {
    val totalSeconds = (playheadMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
