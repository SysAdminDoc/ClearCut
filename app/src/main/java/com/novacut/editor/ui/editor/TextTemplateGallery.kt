package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

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
    val filteredTemplates = if (selectedCategory != null) {
        builtInTextTemplates.filter { it.category == selectedCategory }
    } else builtInTextTemplates

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.panel_text_template_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Static / Animated tab selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !showAnimated,
                onClick = { showAnimated = false },
                label = { Text(stringResource(R.string.panel_text_template_static), fontSize = 11.sp) }
            )
            FilterChip(
                selected = showAnimated,
                onClick = { showAnimated = true },
                label = { Text(stringResource(R.string.panel_text_template_animated), fontSize = 11.sp) }
            )
        }

        // Category filter
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text(stringResource(R.string.panel_text_template_all), fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.2f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
            items(TextTemplateCategory.entries.toList()) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.displayName, fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.2f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (showAnimated) {
            // Animated Lottie templates
            val lottieTemplates = listOf(
                "Slide In Lower Third" to "lower_third",
                "Modern Lower Third" to "lower_third",
                "Bounce Title" to "full_screen",
                "Typewriter" to "full_screen",
                "Glitch Reveal" to "full_screen",
                "Neon Glow" to "full_screen",
                "Fade Subtitle" to "subtitle",
                "Circle Logo Reveal" to "logo_reveal",
                "3-2-1 Countdown" to "full_screen",
                "Subscribe Button" to "lower_third"
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(lottieTemplates.size) { idx ->
                    val (name, category) = lottieTemplates[idx]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clickable {
                                onTemplateSelected(TextTemplate(
                                    name = name,
                                    category = TextTemplateCategory.TITLE_CARD,
                                    layers = listOf(
                                        TextOverlay(
                                            text = "Your Text",
                                            fontSize = 48f,
                                            color = 0xFFFFFFFF,
                                            backgroundColor = 0x00000000,
                                            animationIn = TextAnimation.FADE,
                                            animationOut = TextAnimation.FADE
                                        )
                                    ),
                                    durationMs = 3000L
                                ))
                            },
                        colors = CardDefaults.cardColors(containerColor = Mocha.Surface0)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(name, color = Mocha.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                            Text(category, color = Mocha.Subtext0, fontSize = 9.sp)
                        }
                    }
                }
            }
        } else {
            // Static template grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTemplates, key = { it.id }) { template ->
                    TemplateCard(
                        template = template,
                        onClick = { onTemplateSelected(template) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: TextTemplate,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Mocha.Surface0)
            .clickable(onClick = onClick)
    ) {
        // Preview area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF181825), Mocha.Base)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Render template preview
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                template.layers.forEach { layer ->
                    val previewFontSize = (layer.fontSize * 0.35f).coerceIn(8f, 18f)
                    Text(
                        layer.text,
                        color = Color(layer.color),
                        fontSize = previewFontSize.sp,
                        fontWeight = if (layer.bold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            shadow = if (layer.shadowBlur > 0) Shadow(
                                Color(layer.shadowColor),
                                Offset(layer.shadowOffsetX, layer.shadowOffsetY),
                                layer.shadowBlur
                            ) else null,
                            letterSpacing = (layer.letterSpacing * 0.3f).sp,
                            fontFamily = when (layer.fontFamily) {
                                "serif" -> FontFamily.Serif
                                "monospace" -> FontFamily.Monospace
                                "cursive" -> FontFamily.Cursive
                                else -> FontFamily.SansSerif
                            }
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // Info
        Column(modifier = Modifier.padding(6.dp)) {
            Text(template.name, color = Mocha.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "${template.category.displayName} | ${template.durationMs / 1000}s",
                color = Mocha.Subtext0,
                fontSize = 9.sp
            )
        }
    }
}
