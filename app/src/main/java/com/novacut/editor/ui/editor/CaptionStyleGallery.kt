package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

@Composable
fun CaptionStyleGallery(
    onStyleSelected: (CaptionStyleTemplate) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val templates = remember { defaultTemplates() }
    val karaokeTemplates = remember { templates.filter { it.wordByWord || it.type == CaptionTemplateType.KARAOKE } }
    val otherTemplates = remember { templates.filter { !it.wordByWord && it.type != CaptionTemplateType.KARAOKE } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Caption Styles",
                color = Mocha.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Karaoke section header
            item(span = { GridItemSpan(2) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = stringResource(R.string.cd_karaoke_section),
                        tint = Mocha.Mauve,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Karaoke & Word Highlight",
                        color = Mocha.Mauve,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            items(karaokeTemplates, key = { it.id }) { template ->
                CaptionStyleCard(
                    template = template,
                    onClick = { onStyleSelected(template) }
                )
            }

            // Other styles header
            item(span = { GridItemSpan(2) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Subtitles,
                        contentDescription = stringResource(R.string.cd_caption_styles_section),
                        tint = Mocha.Subtext0,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Caption Styles",
                        color = Mocha.Subtext0,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            items(otherTemplates, key = { it.id }) { template ->
                CaptionStyleCard(
                    template = template,
                    onClick = { onStyleSelected(template) }
                )
            }
        }
    }
}

@Composable
private fun CaptionStyleCard(
    template: CaptionStyleTemplate,
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
                .height(72.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Mocha.Mantle, Mocha.Base)
                    )
                ),
            contentAlignment = when {
                template.positionY > 0.7f -> Alignment.BottomCenter
                template.positionY < 0.3f -> Alignment.TopCenter
                else -> Alignment.Center
            }
        ) {
            val previewText = if (template.wordByWord) "Hello World" else "Sample Text"
            val textColor = Color(template.textColor)
            val bgColor = Color(template.backgroundColor)
            val outlineCol = Color(template.outlineColor)
            val shadowCol = Color(template.shadowColor)
            val highlightCol = Color(template.highlightColor)
            val previewSize = (template.fontSize * 0.5f).coerceIn(10f, 20f)

            val fontFamily = when (template.fontFamily) {
                "serif" -> FontFamily.Serif
                "monospace" -> FontFamily.Monospace
                "cursive" -> FontFamily.Cursive
                else -> FontFamily.SansSerif
            }

            if (template.wordByWord) {
                // Word-by-word / karaoke preview: highlight first word
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Hello ",
                        color = highlightCol,
                        fontSize = previewSize.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        style = TextStyle(
                            shadow = if (template.outlineWidth > 0f) Shadow(
                                outlineCol, Offset(1f, 1f), 2f
                            ) else if (template.shadowOffsetX != 0f || template.shadowOffsetY != 0f) Shadow(
                                shadowCol,
                                Offset(template.shadowOffsetX * 0.5f, template.shadowOffsetY * 0.5f),
                                3f
                            ) else null
                        )
                    )
                    Text(
                        "World",
                        color = textColor,
                        fontSize = previewSize.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        style = TextStyle(
                            shadow = if (template.outlineWidth > 0f) Shadow(
                                outlineCol, Offset(1f, 1f), 2f
                            ) else if (template.shadowOffsetX != 0f || template.shadowOffsetY != 0f) Shadow(
                                shadowCol,
                                Offset(template.shadowOffsetX * 0.5f, template.shadowOffsetY * 0.5f),
                                3f
                            ) else null
                        )
                    )
                }
            } else {
                // Standard caption preview
                val bgAlpha = (template.backgroundColor shr 24 and 0xFF) / 255f
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .then(
                            if (bgAlpha > 0.05f) Modifier
                                .background(bgColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                            else Modifier
                        )
                ) {
                    Text(
                        previewText,
                        color = textColor,
                        fontSize = previewSize.sp,
                        fontWeight = if (template.type == CaptionTemplateType.BOLD_CENTER) FontWeight.ExtraBold else FontWeight.Medium,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            shadow = if (template.outlineWidth > 0f) Shadow(
                                outlineCol, Offset(1f, 1f), template.outlineWidth
                            ) else if (template.shadowOffsetX != 0f || template.shadowOffsetY != 0f) Shadow(
                                shadowCol,
                                Offset(template.shadowOffsetX * 0.5f, template.shadowOffsetY * 0.5f),
                                3f
                            ) else null
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Info row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                template.type.displayName,
                color = Mocha.Text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                template.animation.displayName,
                color = Mocha.Subtext0,
                fontSize = 9.sp
            )
        }
    }
}

fun defaultTemplates(): List<CaptionStyleTemplate> = listOf(
    // Karaoke / word-by-word styles
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
    // Standard styles
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
